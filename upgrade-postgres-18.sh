#!/usr/bin/env bash
#
# Upgrades the GeoPulse database from PostgreSQL 17 / PostGIS 3.5 to PostgreSQL 18 / PostGIS 3.6
# for Docker Compose and Unraid installations. Only the docker CLI (with the compose plugin) is needed.
#
# PostgreSQL 18 cannot open PostgreSQL 17 data files, so the database is copied with pg_dump into a new
# PostgreSQL 18 cluster that is built next to the old one on the same volume (or Unraid appdata folder).
# Nothing is switched until the copy has been validated. The PostgreSQL 17 files are kept in
# pg17-pre-upgrade-<timestamp>/ for rollback, and the dump is never deleted.
#
# Guide: https://geopulse.cc/docs/system-administration/maintenance/postgresql-18-upgrade
#
#   ./upgrade-postgres-18.sh              upgrade (asks before every change)
#   ./upgrade-postgres-18.sh --rollback   go back to PostgreSQL 17
#   ./upgrade-postgres-18.sh --cleanup    delete the kept PostgreSQL 17 files once you are happy
#
set -Eeuo pipefail
umask 077

PG_CONTAINER=geopulse-postgres
BACKEND_CONTAINER=""
APP_CONTAINERS=""
BACKUP_DIR=./postgres-upgrade-backups
TARGET_IMAGE=""
DATA_SOURCE=""
EDIT_COMPOSE=1
ASSUME_YES=0
HEALTH_TIMEOUT=600
MODE=upgrade
MODE_ARG=""

SOURCE_TAG=17-3.5
TARGET_TAG=18-3.6
OLD_TARGET=/var/lib/postgresql/data
NEW_TARGET=/var/lib/postgresql
STATE_DIR=.geopulse-pg-upgrade

usage() {
    cat <<'EOF'
Usage: upgrade-postgres-18.sh [options]

Upgrades the GeoPulse PostgreSQL 17 database to PostgreSQL 18 (dump, restore, validate, switch).

Modes:
  (default)               Upgrade. Safe to run again: it resumes or cleans up an interrupted run.
  --rollback [TIMESTAMP]  Switch back to the kept PostgreSQL 17 cluster.
  --cleanup [TIMESTAMP]   Delete a kept PostgreSQL 17 cluster after a successful upgrade.

Options:
  --container NAME        PostgreSQL container (default: geopulse-postgres)
  --backend NAME          GeoPulse backend container (default: the *backend* service of the same compose project)
  --app-containers LIST   Containers stopped during the upgrade (default: the other running containers of the
                          PostgreSQL container's compose project, so other GeoPulse instances on the host are
                          never touched)
  --backup-dir DIR        Where the dump and reports are written (default: ./postgres-upgrade-backups)
  --target-image IMAGE    PostgreSQL 18 image (default: same repository as today, tag 17-3.5 -> 18-3.6)
  --data-source SPEC      Volume name or host path holding the data, if the container no longer exists
  --no-compose-edit       Do not edit the compose file; print the required edits instead
  --health-timeout SEC    How long to wait for the backend to become healthy (default: 600)
  -y, --yes               Do not ask for confirmation
  -h, --help              Show this help
EOF
}

log() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
info() { printf '    %s\n' "$*"; }
warn() { printf '\033[1;33mWARNING:\033[0m %s\n' "$*" >&2; }
die() { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

confirm() {
    [ "$ASSUME_YES" = 1 ] && return 0
    [ -r /dev/tty ] || die "No terminal to confirm \"$1\". Re-run with --yes to proceed without prompts."
    local answer
    printf '%s [y/N] ' "$1" > /dev/tty
    read -r answer < /dev/tty || answer=""
    case "$answer" in y|Y|yes|YES) return 0 ;; *) return 1 ;; esac
}

# Options repeated in the --rollback / --cleanup hints printed at the end.
HINT_ARGS=""
for arg in "$@"; do
    case "$arg" in --rollback|--cleanup|-y|--yes) ;; *) HINT_ARGS="$HINT_ARGS $(printf '%q' "$arg")" ;; esac
done

while [ $# -gt 0 ]; do
    case "$1" in
        --rollback|--cleanup)
            MODE=${1#--}
            if [ $# -gt 1 ] && [[ "$2" != -* ]]; then MODE_ARG=$2; shift; fi ;;
        --container) PG_CONTAINER=$2; shift ;;
        --backend) BACKEND_CONTAINER=$2; shift ;;
        --app-containers) APP_CONTAINERS=$2; shift ;;
        --backup-dir) BACKUP_DIR=$2; shift ;;
        --target-image) TARGET_IMAGE=$2; shift ;;
        --data-source) DATA_SOURCE=$2; shift ;;
        --no-compose-edit) EDIT_COMPOSE=0 ;;
        --health-timeout) HEALTH_TIMEOUT=$2; shift ;;
        -y|--yes) ASSUME_YES=1 ;;
        -h|--help) usage; exit 0 ;;
        *) usage >&2; die "Unknown option: $1" ;;
    esac
    shift
done

command -v docker >/dev/null || die "docker is not installed or not in PATH"
docker info >/dev/null 2>&1 || die "Cannot talk to the Docker daemon (try sudo, or add your user to the docker group)"

sha256() { if command -v sha256sum >/dev/null; then sha256sum "$1" | awk '{print $1}'; else shasum -a 256 "$1" | awk '{print $1}'; fi; }
human() { awk -v b="$1" 'BEGIN { split("B KB MB GB TB", u); i = 1; while (b >= 1024 && i < 5) { b /= 1024; i++ } printf (i == 1 ? "%d %s" : "%.1f %s"), b, u[i] }'; }

# ---------------------------------------------------------------------------------------------------------------
# Container and volume helpers
# ---------------------------------------------------------------------------------------------------------------

container_exists() { docker inspect "$1" >/dev/null 2>&1; }
container_running() { [ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null)" = true ]; }
container_env() { docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$1" | sed -n "s/^$2=//p" | head -n 1; }
label() { docker inspect -f "{{index .Config.Labels \"$2\"}}" "$1" 2>/dev/null; }

# Runs a shell script as root in a throwaway container with the data volume mounted at /mnt/gp.
on_volume() {
    local script=$1; shift
    docker run --rm -i --network none --entrypoint sh -v "$DATA_SOURCE:/mnt/gp" "$HELPER_IMAGE" -c "$script" sh "$@"
}

# psql in a PostgreSQL container as its superuser over the local socket. $1 = container, $2 = database.
psql_in() {
    local container=$1 database=$2; shift 2
    docker exec -i -u postgres "$container" sh -c \
        'db=$1; shift; PGPASSWORD="${POSTGRES_PASSWORD:-}" exec psql -X -q -At -v ON_ERROR_STOP=1 -U "${POSTGRES_USER:-postgres}" -d "$db" "$@"' \
        sh "$database" "$@"
}

wait_for_postgres() {
    local container=$1 i
    # TCP rather than the socket: during first-time init the entrypoint runs a socket-only temporary server.
    for i in $(seq 1 300); do
        container_running "$container" || { docker logs --tail 30 "$container" >&2 || true; return 1; }
        if docker exec "$container" sh -c 'pg_isready -q -h 127.0.0.1 -p 5432 -U "${POSTGRES_USER:-postgres}"' >/dev/null 2>&1; then return 0; fi
        sleep 2
    done
    docker logs --tail 30 "$container" >&2 || true
    return 1
}

server_major() { psql_in "$1" postgres -c 'SHOW server_version_num' | awk '{ print int($1 / 10000) }'; }

# ---------------------------------------------------------------------------------------------------------------
# State, kept in the volume itself (and mirrored to the backup directory) so a re-run finds it.
# ---------------------------------------------------------------------------------------------------------------

read_state() {
    STATE_PHASE=""; RUN_ID=""; OLD_DIR=""; STOPPED=""; COMPOSE_BACKUPS=""; SOURCE_IMAGE_STATE=""; DUMP_FILE=""
    local content
    content=$(on_volume "cat /mnt/gp/$STATE_DIR/state 2>/dev/null || true")
    [ -n "$content" ] || return 0
    STATE_PHASE=$(printf '%s\n' "$content" | sed -n 's/^PHASE=//p')
    RUN_ID=$(printf '%s\n' "$content" | sed -n 's/^RUN_ID=//p')
    OLD_DIR=$(printf '%s\n' "$content" | sed -n 's/^OLD_DIR=//p')
    STOPPED=$(printf '%s\n' "$content" | sed -n 's/^STOPPED=//p')
    COMPOSE_BACKUPS=$(printf '%s\n' "$content" | sed -n 's/^COMPOSE_BACKUPS=//p')
    SOURCE_IMAGE_STATE=$(printf '%s\n' "$content" | sed -n 's/^SOURCE_IMAGE=//p')
    DUMP_FILE=$(printf '%s\n' "$content" | sed -n 's/^DUMP_FILE=//p')
    if [ -n "$DUMP_FILE" ]; then BACKUP_DIR=$(dirname "$DUMP_FILE"); fi
}

write_state() {
    STATE_PHASE=$1
    local content
    content=$(printf 'PHASE=%s\nRUN_ID=%s\nOLD_DIR=%s\nSTOPPED=%s\nCOMPOSE_BACKUPS=%s\nSOURCE_IMAGE=%s\nDUMP_FILE=%s\nUPDATED=%s\n' \
        "$STATE_PHASE" "$RUN_ID" "$OLD_DIR" "$STOPPED" "$COMPOSE_BACKUPS" "$SOURCE_IMAGE" "$DUMP_FILE" "$(date -u +%Y-%m-%dT%H:%M:%SZ)")
    printf '%s\n' "$content" | on_volume "mkdir -p /mnt/gp/$STATE_DIR && cat > /mnt/gp/$STATE_DIR/state"
    [ -n "$RUN_ID" ] && [ -d "$BACKUP_DIR" ] && printf '%s\n' "$content" > "$BACKUP_DIR/geopulse-$RUN_ID.state" || true
}

# ---------------------------------------------------------------------------------------------------------------
# Snapshot used to prove that the PostgreSQL 18 copy holds exactly the same data. Extension-owned objects
# (PostGIS tables, functions and views in public) are excluded because they legitimately change with PostGIS 3.6,
# and PostgreSQL 18's NOT NULL constraint rows (contype 'n') are excluded because PostgreSQL 17 has none.
# ---------------------------------------------------------------------------------------------------------------

SNAPSHOT_SQL=$(cat <<'SQL'
CREATE TEMP VIEW user_rel AS
  SELECT c.oid, c.relname, c.relkind FROM pg_class c
  WHERE c.relnamespace = 'public'::regnamespace
    AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.classid = 'pg_class'::regclass AND d.objid = c.oid AND d.deptype = 'e');
SELECT 'ext:' || extname FROM pg_extension ORDER BY extname;
SELECT 'flyway:' || count(*) || ' rows, last rank ' || coalesce(max(installed_rank), 0) || ', all succeeded ' || coalesce(bool_and(success), true)
  FROM flyway_schema_history;
SELECT 'rows:' || relname || '=' || (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM public.%I', relname), false, true, '')))[1]::text
  FROM user_rel WHERE relkind IN ('r', 'p') ORDER BY relname;
SELECT 'seq:' || sequencename || '=' || coalesce(last_value::text, 'unused') FROM pg_sequences WHERE schemaname = 'public' ORDER BY sequencename;
SELECT 'indexes:' || count(*) || ', invalid ' || count(*) FILTER (WHERE NOT i.indisvalid) FROM pg_index i JOIN user_rel r ON r.oid = i.indrelid;
SELECT 'constraints:' || count(*) || ', not validated ' || count(*) FILTER (WHERE NOT convalidated)
  FROM pg_constraint WHERE connamespace = 'public'::regnamespace AND contype <> 'n'
   AND (conrelid = 0 OR conrelid IN (SELECT oid FROM user_rel));
SELECT 'views:' || count(*) FROM user_rel WHERE relkind IN ('v', 'm');
SELECT 'functions:' || count(*) FROM pg_proc p WHERE p.pronamespace = 'public'::regnamespace
   AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.classid = 'pg_proc'::regclass AND d.objid = p.oid AND d.deptype = 'e');
SELECT 'latest-point:' || u.id || '=' || coalesce((SELECT extract(epoch FROM max(g.timestamp))::text FROM gps_points g WHERE g.user_id = u.id), 'none')
  FROM users u ORDER BY u.id;
SELECT 'points-near-latest:' || u.id || '=' || (SELECT count(*) FROM gps_points g WHERE g.user_id = u.id AND ST_DWithin(g.coordinates, l.coordinates, 0.005))
  FROM users u CROSS JOIN LATERAL (SELECT coordinates FROM gps_points WHERE user_id = u.id ORDER BY timestamp DESC LIMIT 1) l ORDER BY u.id;
SELECT 'stays-last-30-days:' || u.id || '=' || (SELECT count(*) FROM timeline_stays s WHERE s.user_id = u.id
         AND s.timestamp > (SELECT max(timestamp) FROM timeline_stays WHERE user_id = u.id) - interval '30 days')
  FROM users u ORDER BY u.id;
SQL
)

snapshot() { printf '%s\n' "$SNAPSHOT_SQL" | psql_in "$1" "$DB_NAME"; }

# Lines that must still match on the real container, before GeoPulse starts writing again.
quick_snapshot() { snapshot "$1" | grep -E '^(ext:|flyway:|latest-point:|indexes:)'; }

client_connections() {
    psql_in "$PG_CONTAINER" postgres -v db="$DB_NAME" <<'SQL'
SELECT format('pid %s, user %s, application "%s", from %s', pid, usename, application_name, coalesce(client_addr::text, 'local socket'))
  FROM pg_stat_activity WHERE datname = :'db' AND backend_type = 'client backend' AND pid <> pg_backend_pid();
SQL
}

# ---------------------------------------------------------------------------------------------------------------
# Discovery
# ---------------------------------------------------------------------------------------------------------------

discover() {
    if container_exists "$PG_CONTAINER"; then
        local mount
        mount=$(docker inspect -f '{{range .Mounts}}{{if or (eq .Destination "'"$OLD_TARGET"'") (eq .Destination "'"$NEW_TARGET"'")}}{{.Type}}|{{.Name}}|{{.Source}}|{{.Destination}}{{println}}{{end}}{{end}}' "$PG_CONTAINER" | head -n 1)
        [ -n "$mount" ] || die "$PG_CONTAINER has no volume at $OLD_TARGET or $NEW_TARGET. Pass --data-source."
        IFS='|' read -r MOUNT_TYPE MOUNT_NAME MOUNT_SOURCE MOUNT_TARGET <<< "$mount"
        if [ -z "$DATA_SOURCE" ]; then
            if [ "$MOUNT_TYPE" = volume ]; then DATA_SOURCE=$MOUNT_NAME; else DATA_SOURCE=$MOUNT_SOURCE; fi
        fi
        SOURCE_IMAGE=$(docker inspect -f '{{.Config.Image}}' "$PG_CONTAINER")
        DB_USER=$(container_env "$PG_CONTAINER" POSTGRES_USER)
        DB_USER=${DB_USER:-postgres}
        DB_NAME=$(container_env "$PG_CONTAINER" POSTGRES_DB)
        DB_NAME=${DB_NAME:-$DB_USER}
        COMPOSE_PROJECT=$(label "$PG_CONTAINER" com.docker.compose.project)
        COMPOSE_SERVICE=$(label "$PG_CONTAINER" com.docker.compose.service)
        COMPOSE_WORKDIR=$(label "$PG_CONTAINER" com.docker.compose.project.working_dir)
        COMPOSE_FILES=$(label "$PG_CONTAINER" com.docker.compose.project.config_files)
        discover_app_containers
    else
        [ -n "$DATA_SOURCE" ] || die "Container $PG_CONTAINER not found. Start GeoPulse, or pass --container / --data-source."
        SOURCE_IMAGE=""; COMPOSE_PROJECT=""; MOUNT_TARGET=""
    fi
    if [ -z "$TARGET_IMAGE" ]; then
        local image=${SOURCE_IMAGE:-${SOURCE_IMAGE_STATE:-}}
        image=${image%@sha256:*}
        case "$image" in
            *:"$SOURCE_TAG"*) TARGET_IMAGE=${image/:$SOURCE_TAG/:$TARGET_TAG} ;;
            *:"$TARGET_TAG"*) TARGET_IMAGE=$image ;;
            *) TARGET_IMAGE="" ;;
        esac
    fi
    HELPER_IMAGE=${TARGET_IMAGE:-postgis/postgis:$TARGET_TAG}
    TEMP_CONTAINER=$PG_CONTAINER-pg18-upgrade
}

# Defaults come from the PostgreSQL container's own compose project, so a host with several GeoPulse
# instances (prod, dev, demo) only ever has the instance being upgraded stopped.
discover_app_containers() {
    if [ -n "$COMPOSE_PROJECT" ]; then
        local filter="label=com.docker.compose.project=$COMPOSE_PROJECT"
        [ -n "$APP_CONTAINERS" ] || APP_CONTAINERS=$(docker ps --filter "$filter" --format '{{.Names}}' | grep -vx "$PG_CONTAINER" | sort | tr '\n' ' ' || true)
        [ -n "$BACKEND_CONTAINER" ] || BACKEND_CONTAINER=$(docker ps -a --filter "$filter" \
            --format '{{.Names}} {{.Label "com.docker.compose.service"}}' | awk '$2 ~ /backend/ { print $1; exit }')
    elif [ -z "$APP_CONTAINERS" ]; then
        # Without compose labels only the standard container names are safe to assume.
        [ "$PG_CONTAINER" = geopulse-postgres ] || die "$PG_CONTAINER is not managed by Docker Compose. Pass --app-containers (and --backend) with this instance's containers."
        APP_CONTAINERS="geopulse-backend geopulse-ui"
        BACKEND_CONTAINER=${BACKEND_CONTAINER:-geopulse-backend}
    fi
    APP_CONTAINERS=$(printf '%s' "$APP_CONTAINERS" | sed 's/ *$//')
}

compose() {
    local args=() f
    IFS=',' read -r -a files <<< "$COMPOSE_FILES"
    for f in "${files[@]}"; do args+=(-f "$f"); done
    docker compose --project-directory "$COMPOSE_WORKDIR" -p "$COMPOSE_PROJECT" "${args[@]}" "$@"
}

ensure_image() {
    if ! docker image inspect "$1" >/dev/null 2>&1; then
        log "Pulling $1"
        docker pull "$1" >/dev/null || die "Cannot pull $1. Check the name, or pass --target-image."
    fi
    local arch
    arch=$(docker image inspect -f '{{.Architecture}}' "$1")
    [ "$arch" = "$(docker info -f '{{.Architecture}}' | sed 's/x86_64/amd64/; s/aarch64/arm64/')" ] \
        || warn "$1 is built for $arch, not for this host; it will run under emulation (slow). See the guide for ARM images."
}

# ---------------------------------------------------------------------------------------------------------------
# Compose file edits: image 17-3.5 -> 18-3.6 and mount /var/lib/postgresql/data -> /var/lib/postgresql,
# only inside the PostgreSQL service block. Every edited file keeps a .bak copy.
# ---------------------------------------------------------------------------------------------------------------

patch_compose_file() { # file -> prints "<image edits> <mount edits>" and writes file.new
    awk -v svc="$COMPOSE_SERVICE" -v src="$SOURCE_IMAGE" -v tgt="$TARGET_IMAGE" '
        function indent(s) { match(s, /^ */); return RLENGTH }
        BEGIN { insvc = 0; inservices = 0; svcind = -1; img = 0; vol = 0 }
        {
            line = $0
            # Service keys sit one level below "services:"; a "geopulse-postgres:" under depends_on is not one.
            if (line ~ /^services:[ ]*(#.*)?$/) { inservices = 1; print line; next }
            if (line !~ /^[ ]*(#.*)?$/) {
                if (indent(line) == 0) { inservices = 0; insvc = 0 }
                else if (inservices && svcind < 0) svcind = indent(line)
                if (inservices && indent(line) == svcind) insvc = (line ~ ("^[ ]*" svc ":[ ]*(#.*)?$"))
                if (insvc && indent(line) == svcind) { print line; next }
            }
            if (insvc) {
                if (line ~ /^[ ]*image:[ ]*/) {
                    value = line; sub(/^[ ]*image:[ ]*/, "", value); sub(/[ ]+#.*$/, "", value); gsub(/["\047 ]/, "", value)
                    if (value == src) { match(line, /^[ ]*image:[ ]*/); line = substr(line, 1, RLENGTH) tgt; img++ }
                } else if (line ~ /^[ ]*#[ ]*image:.*17-3\.5/) {
                    gsub(/17-3\.5/, "18-3.6", line)
                }
                if (line ~ /^[ ]*-[ ].*:\/var\/lib\/postgresql\/data(:[a-z,]+)?["\047]?[ ]*(#.*)?$/) {
                    sub(/:\/var\/lib\/postgresql\/data/, ":/var/lib/postgresql", line); vol++
                } else if (line ~ /^[ ]*target:[ ]*["\047]?\/var\/lib\/postgresql\/data["\047]?[ ]*(#.*)?$/) {
                    sub(/\/var\/lib\/postgresql\/data/, "/var/lib/postgresql", line); vol++
                }
            }
            print line
        }
        END { print img, vol > "/dev/stderr" }
    ' "$1" > "$1.new" 2> "$1.counts"
    cat "$1.counts"; rm -f "$1.counts"
}

# Checks the effective compose configuration of the PostgreSQL service.
compose_config_ok() {
    compose config 2>/dev/null | awk -v svc="$COMPOSE_SERVICE" -v tgt="$TARGET_IMAGE" -v old="$OLD_TARGET" -v new="$NEW_TARGET" '
        function indent(s) { match(s, /^ */); return RLENGTH }
        $0 ~ ("^  " svc ":[ ]*$") { insvc = 1; next }
        insvc && $0 !~ /^[ ]*$/ && indent($0) <= 2 { insvc = 0 }
        insvc && $0 ~ /^[ ]*image:/ { v = $0; sub(/^[ ]*image:[ ]*/, "", v); gsub(/["\047]/, "", v); image = v }
        insvc && $0 ~ /^[ ]*target:/ { v = $0; sub(/^[ ]*target:[ ]*/, "", v); gsub(/["\047]/, "", v); if (v == new) n++; if (v == old) o++ }
        END { exit !(image == tgt && n == 1 && o == 0) }'
}

print_manual_compose_edits() {
    cat <<EOF

Edit the $PG_CONTAINER service in your compose file (or Unraid container template):

    image: $TARGET_IMAGE
    volumes:
      - <the same volume or appdata path>:$NEW_TARGET      # was ...:$OLD_TARGET

Then run this script again. It will start PostgreSQL 18 and GeoPulse and validate the upgrade.
Until then PostgreSQL refuses to start with the old settings; no empty database is created.
EOF
}

apply_compose_edits() {
    COMPOSE_BACKUPS=""
    if [ "$EDIT_COMPOSE" = 0 ] || [ -z "$COMPOSE_PROJECT" ] || [ -z "$COMPOSE_FILES" ]; then return 1; fi
    local f counts img=0 vol=0 changed=()
    IFS=',' read -r -a files <<< "$COMPOSE_FILES"
    for f in "${files[@]}"; do
        [ -w "$f" ] || { warn "Cannot write $f"; return 1; }
        counts=$(patch_compose_file "$f")
        if [ "$counts" != "0 0" ]; then changed+=("$f"); fi
        img=$((img + ${counts% *})); vol=$((vol + ${counts#* }))
    done
    if [ "$img" -ne 1 ] || [ "$vol" -ne 1 ]; then
        for f in "${files[@]}"; do rm -f "$f.new"; done
        warn "Could not find exactly one 'image: $SOURCE_IMAGE' and one '...:$OLD_TARGET' line in the $COMPOSE_SERVICE service."
        return 1
    fi
    for f in "${changed[@]}"; do
        cp -p "$f" "$f.pg17-pre-upgrade-$RUN_ID.bak"
        cat "$f.new" > "$f"
        COMPOSE_BACKUPS="${COMPOSE_BACKUPS:+$COMPOSE_BACKUPS,}$f"
        info "Edited $f (original saved as $f.pg17-pre-upgrade-$RUN_ID.bak)"
    done
    for f in "${files[@]}"; do rm -f "$f.new"; done
    if ! compose_config_ok; then
        restore_compose_files
        warn "The edited compose configuration did not validate; the original files were restored."
        return 1
    fi
}

restore_compose_files() {
    local f
    [ -n "$COMPOSE_BACKUPS" ] || return 0
    IFS=',' read -r -a files <<< "$COMPOSE_BACKUPS"
    for f in "${files[@]}"; do
        if [ -f "$f.pg17-pre-upgrade-$RUN_ID.bak" ]; then
            cat "$f.pg17-pre-upgrade-$RUN_ID.bak" > "$f"
            info "Restored $f"
        fi
    done
}

# ---------------------------------------------------------------------------------------------------------------
# Data directory moves, done as root in a throwaway container. Only PostgreSQL's own entries are moved;
# anything else at the top of the volume (lost+found, your own files) stays where it is.
# ---------------------------------------------------------------------------------------------------------------

move_pg17_aside() {
    on_volume '
        set -e
        cd /mnt/gp
        old=$1
        [ -s PG_VERSION ] || [ -s "$old/PG_VERSION" ] || { echo "No PostgreSQL 17 files found at the top of the volume" >&2; exit 1; }
        [ ! -e postmaster.pid ] || { echo "postmaster.pid present: PostgreSQL 17 was not shut down cleanly" >&2; exit 1; }
        mkdir -p "$old"
        for e in PG_VERSION base global pg_* postgresql.conf postgresql.auto.conf postmaster.opts current_logfiles log \
                 standby.signal recovery.signal backup_label* tablespace_map*; do
            [ -e "$e" ] || [ -L "$e" ] || continue
            mv "$e" "$old/"
        done
        [ ! -e PG_VERSION ] && [ -s "$old/PG_VERSION" ]
        for e in * .[!.]*; do
            case "$e" in 18|"$old"|pg17-pre-upgrade-*|pg18-failed-*|.geopulse-pg-upgrade|lost+found|"*"|".[!.]*") continue ;; esac
            echo "left in place: $e"
        done
    ' "$OLD_DIR"
}

move_pg17_back() {
    on_volume '
        set -e
        cd /mnt/gp
        old=$1 failed=$2
        [ -d "$old" ] || { echo "$old not found on the volume" >&2; exit 1; }
        if [ -d 18 ]; then mv 18 "$failed"; fi
        for e in "$old"/* "$old"/.[!.]*; do
            [ -e "$e" ] || [ -L "$e" ] || continue
            mv "$e" .
        done
        rmdir "$old"
        [ -s PG_VERSION ]
    ' "$OLD_DIR" "pg18-failed-$RUN_ID"
}

# ---------------------------------------------------------------------------------------------------------------
# Upgrade
# ---------------------------------------------------------------------------------------------------------------

stop_app_containers() {
    STOPPED=""
    local c
    for c in $APP_CONTAINERS; do
        if container_running "$c"; then
            info "Stopping $c"
            docker stop -t 60 "$c" >/dev/null
            STOPPED="${STOPPED:+$STOPPED }$c"
        fi
    done
}

start_app_containers() {
    local c
    for c in $STOPPED; do
        info "Starting $c"
        docker start "$c" >/dev/null || warn "Could not start $c"
    done
}

backend_healthy() {
    local status
    status=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "$BACKEND_CONTAINER" 2>/dev/null || true)
    if [ -n "$status" ]; then [ "$status" = healthy ]; return; fi
    docker exec "$BACKEND_CONTAINER" sh -c 'url=http://localhost:8080/api/v1/system/health
        curl -fsS -o /dev/null "$url" 2>/dev/null || wget -q -O /dev/null "$url"' >/dev/null 2>&1
}

remove_temp_container() { docker rm -f "$TEMP_CONTAINER" >/dev/null 2>&1 || true; }

# Undo everything before the switch: the PostgreSQL 17 files were never touched.
abort_before_switch() {
    # set -E hands the trap to $(...) subshells; only the main shell restores.
    [ "$BASH_SUBSHELL" -eq 0 ] || exit 1
    trap - ERR INT TERM
    set +e
    warn "Upgrade stopped before switching. Restoring PostgreSQL 17 and GeoPulse."
    remove_temp_container
    on_volume 'rm -rf /mnt/gp/18'
    write_state ABORTED
    if container_exists "$PG_CONTAINER" && ! container_running "$PG_CONTAINER"; then docker start "$PG_CONTAINER" >/dev/null; fi
    wait_for_postgres "$PG_CONTAINER" >/dev/null 2>&1
    start_app_containers
    if [ -n "$DUMP_FILE" ] && [ -f "$DUMP_FILE" ]; then warn "Nothing was changed. The dump $DUMP_FILE was kept."; else warn "Nothing was changed."; fi
    exit 1
}

preflight() {
    log "Checking the current installation"
    container_running "$PG_CONTAINER" || die "$PG_CONTAINER is not running. Start GeoPulse first."
    [ "$MOUNT_TARGET" = "$OLD_TARGET" ] || die "$PG_CONTAINER does not mount its data at $OLD_TARGET; this script handles the standard GeoPulse layout only."
    local pgdata major
    pgdata=$(container_env "$PG_CONTAINER" PGDATA)
    [ -z "$pgdata" ] || [ "$pgdata" = "$OLD_TARGET" ] || die "PGDATA is set to $pgdata; only the default PostgreSQL 17 layout is supported."
    [ -z "$(docker inspect -f '{{.Config.User}}' "$PG_CONTAINER")" ] \
        || die "$PG_CONTAINER runs with a custom 'user:'. The upgrade needs the image's default (root) entrypoint to set file ownership."
    [ -n "$(container_env "$PG_CONTAINER" POSTGRES_PASSWORD)" ] \
        || die "POSTGRES_PASSWORD is not set on $PG_CONTAINER (POSTGRES_PASSWORD_FILE is not supported by this script)."
    major=$(server_major "$PG_CONTAINER")
    [ "$major" = 17 ] || die "$PG_CONTAINER runs PostgreSQL $major; this script upgrades PostgreSQL 17."
    [ -n "$TARGET_IMAGE" ] || die "Cannot derive the PostgreSQL 18 image from '$SOURCE_IMAGE'. Pass --target-image."
    ensure_image "$TARGET_IMAGE"
    [ "$(docker run --rm --entrypoint sh "$TARGET_IMAGE" -c 'echo $PG_MAJOR')" = 18 ] || die "$TARGET_IMAGE is not a PostgreSQL 18 image"

    local backend_image
    if [ -n "$BACKEND_CONTAINER" ] && container_exists "$BACKEND_CONTAINER"; then
        backend_image=$(docker inspect -f '{{.Config.Image}}' "$BACKEND_CONTAINER")
        docker run --rm --entrypoint sh "$backend_image" -c 'test -d /usr/libexec/postgresql18 || test -d /usr/pgsql-18/bin' \
            || die "$backend_image cannot back up PostgreSQL 18. Update GeoPulse to a version with PostgreSQL 18 support first, then run this script (keep the same GeoPulse version during the upgrade)."
    else
        warn "No GeoPulse backend container found${BACKEND_CONTAINER:+ ($BACKEND_CONTAINER)}; skipping the backend compatibility check. Pass --backend if it has another name."
    fi

    local tablespaces roles databases db_size wal free_volume free_backup
    tablespaces=$(psql_in "$PG_CONTAINER" postgres -c "SELECT count(*) FROM pg_tablespace WHERE spcname NOT IN ('pg_default', 'pg_global')")
    [ "$tablespaces" = 0 ] || die "The database uses custom tablespaces, which this script does not migrate."
    roles=$(psql_in "$PG_CONTAINER" postgres -c "SELECT string_agg(rolname, ', ' ORDER BY rolname) FROM pg_roles WHERE rolname !~ '^pg_' AND rolname <> current_user")
    databases=$(printf "SELECT string_agg(datname, ', ' ORDER BY datname) FROM pg_database WHERE NOT datistemplate AND datname NOT IN (:'db', 'postgres');\n" | psql_in "$PG_CONTAINER" postgres -v db="$DB_NAME")
    db_size=$(psql_in "$PG_CONTAINER" "$DB_NAME" -c 'SELECT pg_database_size(current_database())')
    wal=$((2 * 1024 * 1024 * 1024))
    free_volume=$(( $(on_volume 'df -Pk /mnt/gp | awk "NR == 2 { print \$4 }"') * 1024 ))
    mkdir -p "$BACKUP_DIR"
    BACKUP_DIR=$(cd "$BACKUP_DIR" && pwd)
    free_backup=$(( $(df -Pk "$BACKUP_DIR" | awk 'NR == 2 { print $4 }') * 1024 ))

    info "PostgreSQL container: $PG_CONTAINER ($SOURCE_IMAGE), database \"$DB_NAME\", $(human "$db_size")"
    info "Data:                 $DATA_SOURCE ($MOUNT_TYPE), $(human "$free_volume") free"
    info "PostgreSQL 18 image:  $TARGET_IMAGE"
    info "Backup directory:     $BACKUP_DIR, $(human "$free_backup") free"
    if [ -n "$COMPOSE_PROJECT" ]; then info "Compose project:      $COMPOSE_PROJECT ($COMPOSE_FILES)"; else info "Compose project:      none (compose file edits will be printed)"; fi
    [ -z "$roles" ] || warn "Other database roles exist ($roles). Roles and their grants are not migrated; they are saved in the globals file so you can recreate them."
    [ -z "$databases" ] || warn "Only \"$DB_NAME\" is migrated. These other databases stay in the kept PostgreSQL 17 files: $databases"
    [ "$free_backup" -ge "$db_size" ] || warn "The backup directory may be too small for the dump (needs up to $(human "$db_size"))."
    [ "$free_volume" -ge $((db_size * 12 / 10 + wal)) ] || warn "The data volume may be too small for a second copy of the database (needs about $(human $((db_size * 12 / 10 + wal))))."
    DB_SIZE=$db_size
}

upgrade() {
    discover
    read_state
    case "$STATE_PHASE" in
        STARTING)
            # Interrupted before the copy was made: only the GeoPulse containers were stopped.
            warn "A previous run ($RUN_ID) stopped early. Starting what it stopped."
            container_running "$PG_CONTAINER" || docker start "$PG_CONTAINER" >/dev/null
            wait_for_postgres "$PG_CONTAINER" || die "$PG_CONTAINER did not start again."
            start_app_containers
            write_state ABORTED ;;
        DONE)
            log "This installation was already upgraded (run $RUN_ID)."
            info "Kept PostgreSQL 17 files: $OLD_DIR. Remove them with --cleanup once you are happy."
            exit 0 ;;
        RESTORING)
            warn "A previous run ($RUN_ID) stopped while building PostgreSQL 18. Removing its partial copy."
            remove_temp_container
            on_volume 'rm -rf /mnt/gp/18'
            write_state ABORTED
            container_running "$PG_CONTAINER" || docker start "$PG_CONTAINER" >/dev/null
            start_app_containers ;;
        DB_VALIDATED)
            if container_running "$PG_CONTAINER"; then
                # GeoPulse may have written data since the dump, so that copy is no longer trustworthy.
                warn "PostgreSQL 17 was started again after run $RUN_ID made its copy. Discarding that copy and starting over."
                on_volume 'rm -rf /mnt/gp/18'
                write_state ABORTED
            else
                log "Resuming run $RUN_ID: the PostgreSQL 18 copy is built and validated."
                switch_over
                return
            fi ;;
        SWITCHED)
            log "Resuming run $RUN_ID: data directories are switched."
            start_and_validate
            return ;;
    esac

    if container_exists "$PG_CONTAINER" && container_running "$PG_CONTAINER" && [ "$(server_major "$PG_CONTAINER")" = 18 ]; then
        log "$PG_CONTAINER already runs PostgreSQL 18. Nothing to do."
        exit 0
    fi

    preflight
    RUN_ID=$(date -u +%Y%m%d-%H%M%S)
    OLD_DIR="pg17-pre-upgrade-$RUN_ID"
    DUMP_FILE="$BACKUP_DIR/geopulse-$RUN_ID.dump"
    cat <<EOF

The upgrade will:
  1. stop: $APP_CONTAINERS
  2. dump "$DB_NAME" to $DUMP_FILE
  3. build PostgreSQL 18 in a new folder (18/) on the same volume and restore the dump into it
  4. compare row counts, sequences, indexes and sample queries with the original
  5. move the PostgreSQL 17 files to $OLD_DIR/ and point $PG_CONTAINER at PostgreSQL 18
  6. start GeoPulse and wait until it is healthy (automatic rollback if it is not)
GeoPulse is offline until the end. Nothing is deleted.

EOF
    confirm "Start the upgrade?" || die "Cancelled. Nothing was changed."

    log "Stopping GeoPulse"
    stop_app_containers
    write_state STARTING
    trap abort_before_switch ERR INT TERM
    local connections
    connections=$(client_connections)
    if [ -n "$connections" ]; then
        warn "Other clients are still connected to \"$DB_NAME\":"
        printf '    %s\n' "$connections" >&2
        die_after_abort "Stop them (for example Grafana or a database tool) and run the script again."
    fi

    log "Recording what the database contains"
    snapshot "$PG_CONTAINER" > "$BACKUP_DIR/geopulse-$RUN_ID.snapshot.txt"
    info "$(grep -c '^rows:' "$BACKUP_DIR/geopulse-$RUN_ID.snapshot.txt") tables, $(grep -c '^seq:' "$BACKUP_DIR/geopulse-$RUN_ID.snapshot.txt") sequences"

    log "Dumping \"$DB_NAME\" (this can take a while)"
    docker exec -u postgres "$PG_CONTAINER" sh -c 'PGPASSWORD="${POSTGRES_PASSWORD:-}" exec pg_dump -U "${POSTGRES_USER:-postgres}" -d "$1" -Fc' sh "$DB_NAME" > "$DUMP_FILE"
    docker exec -u postgres "$PG_CONTAINER" sh -c 'PGPASSWORD="${POSTGRES_PASSWORD:-}" exec pg_dumpall -U "${POSTGRES_USER:-postgres}" --globals-only' > "$BACKUP_DIR/geopulse-$RUN_ID.globals.sql"
    docker exec -i "$PG_CONTAINER" pg_restore --list < "$DUMP_FILE" > /dev/null
    printf '%s  %s\n' "$(sha256 "$DUMP_FILE")" "$(basename "$DUMP_FILE")" > "$DUMP_FILE.sha256"
    info "$(human "$(wc -c < "$DUMP_FILE" | tr -d ' ')"), sha256 $(cut -c1-16 "$DUMP_FILE.sha256")..."
    connections=$(client_connections)
    [ -z "$connections" ] || die_after_abort "A client connected during the dump ($connections). Run the script again once it is stopped."

    local locale
    locale=$(psql_in "$PG_CONTAINER" "$DB_NAME" -F '|' -c "SELECT pg_encoding_to_char(encoding), datcollate, datctype, datlocprovider, coalesce(datlocale, '') FROM pg_database WHERE datname = current_database()")

    log "Stopping PostgreSQL 17"
    docker stop -t 300 "$PG_CONTAINER" >/dev/null
    local free_volume
    free_volume=$(( $(on_volume 'df -Pk /mnt/gp | awk "NR == 2 { print \$4 }"') * 1024 ))
    [ "$free_volume" -ge $((DB_SIZE + 1024 * 1024 * 1024)) ] \
        || die_after_abort "Not enough free space on the data volume for PostgreSQL 18: $(human "$free_volume") free, about $(human $((DB_SIZE + 1024 * 1024 * 1024))) needed."
    write_state RESTORING

    restore_into_pg18 "$locale"
    validate_copy
    write_state DB_VALIDATED
    trap - ERR INT TERM
    switch_over
}

die_after_abort() { warn "$1"; abort_before_switch; }

restore_into_pg18() {
    local locale=$1 encoding collate ctype provider loc
    IFS='|' read -r encoding collate ctype provider loc <<< "$locale"
    log "Building PostgreSQL 18 in 18/ next to the PostgreSQL 17 files"
    remove_temp_container
    on_volume 'rm -rf /mnt/gp/18'
    local env_args=() initdb_args
    export POSTGRES_USER=$DB_USER POSTGRES_DB=$DB_NAME
    POSTGRES_PASSWORD=$(container_env "$PG_CONTAINER" POSTGRES_PASSWORD)
    export POSTGRES_PASSWORD
    env_args=(-e POSTGRES_USER -e POSTGRES_PASSWORD -e POSTGRES_DB -e PGDATA=/mnt/gp/18/docker)
    initdb_args=$(container_env "$PG_CONTAINER" POSTGRES_INITDB_ARGS)
    if [ -n "$initdb_args" ]; then export POSTGRES_INITDB_ARGS=$initdb_args; env_args+=(-e POSTGRES_INITDB_ARGS); fi
    # PGDATA is not the image default, so the entrypoint's "old data found" check does not apply to this build.
    docker run -d --name "$TEMP_CONTAINER" --network none --shm-size 256m "${env_args[@]}" \
        -v "$DATA_SOURCE:/mnt/gp" "$TARGET_IMAGE" \
        -c timezone=UTC -c maintenance_work_mem=256MB -c max_wal_size=2GB -c synchronous_commit=off >/dev/null
    wait_for_postgres "$TEMP_CONTAINER" || die_after_abort "PostgreSQL 18 did not start"
    info "$(docker exec "$TEMP_CONTAINER" postgres --version)"

    # The image's init script installs PostGIS into POSTGRES_DB; start from an empty database with the original
    # encoding and locale instead, so the dump recreates exactly the extensions it contains.
    local create="CREATE DATABASE :\"db\" OWNER :\"owner\" TEMPLATE template0 ENCODING :'enc' LC_COLLATE :'collate' LC_CTYPE :'ctype'"
    case "$provider" in
        i) create="$create LOCALE_PROVIDER icu ICU_LOCALE :'loc'" ;;
        b) create="$create LOCALE_PROVIDER builtin BUILTIN_LOCALE :'loc'" ;;
        *) create="$create LOCALE_PROVIDER libc" ;;
    esac
    printf 'DROP DATABASE IF EXISTS :"db";\n%s;\n' "$create" | psql_in "$TEMP_CONTAINER" template1 \
        -v db="$DB_NAME" -v owner="$DB_USER" -v enc="$encoding" -v collate="$collate" -v ctype="$ctype" -v loc="$loc"

    log "Restoring the dump into PostgreSQL 18 (this can take a while)"
    docker exec -i -u postgres "$TEMP_CONTAINER" sh -c \
        'PGPASSWORD="${POSTGRES_PASSWORD:-}" exec pg_restore -U "${POSTGRES_USER:-postgres}" -d "$1" --exit-on-error --no-owner --no-acl' \
        sh "$DB_NAME" < "$DUMP_FILE"
    psql_in "$TEMP_CONTAINER" "$DB_NAME" -c 'SET client_min_messages = warning' -c 'SELECT postgis_extensions_upgrade()' >/dev/null
    psql_in "$TEMP_CONTAINER" "$DB_NAME" -c 'ANALYZE'
}

validate_copy() {
    log "Validating the PostgreSQL 18 copy"
    snapshot "$TEMP_CONTAINER" > "$BACKUP_DIR/geopulse-$RUN_ID.validation.txt"
    if ! diff "$BACKUP_DIR/geopulse-$RUN_ID.snapshot.txt" "$BACKUP_DIR/geopulse-$RUN_ID.validation.txt" > "$BACKUP_DIR/geopulse-$RUN_ID.diff"; then
        warn "The PostgreSQL 18 copy differs from the original (< PostgreSQL 17, > PostgreSQL 18):"
        head -n 40 "$BACKUP_DIR/geopulse-$RUN_ID.diff" >&2
        die_after_abort "Validation failed. Full report: $BACKUP_DIR/geopulse-$RUN_ID.diff"
    fi
    rm -f "$BACKUP_DIR/geopulse-$RUN_ID.diff"
    local version
    version=$(psql_in "$TEMP_CONTAINER" "$DB_NAME" -c 'SELECT postgis_full_version()')
    case "$version" in *"need upgrade"*|*UNPACKAGED*) die_after_abort "PostGIS reports an incomplete upgrade: $version" ;; esac
    info "Row counts, sequences, indexes, constraints and sample queries match ($(grep -c '^rows:' "$BACKUP_DIR/geopulse-$RUN_ID.validation.txt") tables)"
    info "$(psql_in "$TEMP_CONTAINER" "$DB_NAME" -c "SELECT 'PostGIS ' || postgis_lib_version()")"
    docker stop -t 300 "$TEMP_CONTAINER" >/dev/null
    remove_temp_container
}

switch_over() {
    log "Switching $PG_CONTAINER to PostgreSQL 18"
    # Never move files under a running server.
    ! container_running "$PG_CONTAINER" || die "$PG_CONTAINER is running; it must be stopped before the switch."
    local moved
    moved=$(move_pg17_aside) || { write_state DB_VALIDATED; die "Could not move the PostgreSQL 17 files. Nothing was switched; run the script again or use --rollback."; }
    info "PostgreSQL 17 files moved to $OLD_DIR/"
    [ -z "$moved" ] || printf '    %s\n' "$moved"
    if apply_compose_edits; then
        write_state SWITCHED
        start_and_validate
    else
        write_state SWITCHED
        print_manual_compose_edits
        exit 0
    fi
}

start_and_validate() {
    if [ -n "$COMPOSE_PROJECT" ]; then
        if ! compose_config_ok; then
            warn "The compose configuration of $COMPOSE_SERVICE does not use $TARGET_IMAGE with a single mount at $NEW_TARGET yet."
            print_manual_compose_edits
            exit 1
        fi
        log "Starting PostgreSQL 18"
        compose up -d --no-deps "$COMPOSE_SERVICE"
    elif ! container_running "$PG_CONTAINER"; then
        print_manual_compose_edits
        exit 0
    fi
    if ! wait_for_postgres "$PG_CONTAINER"; then rollback_after_switch "PostgreSQL 18 did not start"; fi
    [ "$(server_major "$PG_CONTAINER")" = 18 ] || rollback_after_switch "$PG_CONTAINER is not running PostgreSQL 18"
    local target
    target=$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "'"$NEW_TARGET"'"}}{{.Name}}{{.Source}}{{end}}{{end}}' "$PG_CONTAINER")
    case "$target" in *"$DATA_SOURCE"*) ;; *) rollback_after_switch "$PG_CONTAINER does not mount $DATA_SOURCE at $NEW_TARGET" ;; esac
    if [ -f "$BACKUP_DIR/geopulse-$RUN_ID.snapshot.txt" ]; then
        quick_snapshot "$PG_CONTAINER" > "$BACKUP_DIR/geopulse-$RUN_ID.switch-check.txt"
        grep -E '^(ext:|flyway:|latest-point:|indexes:)' "$BACKUP_DIR/geopulse-$RUN_ID.snapshot.txt" \
            | diff - "$BACKUP_DIR/geopulse-$RUN_ID.switch-check.txt" >/dev/null \
            || rollback_after_switch "PostgreSQL 18 in $PG_CONTAINER does not show the restored data"
    fi

    log "Starting GeoPulse"
    local since flyway_before flyway_after i healthy=0
    since=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    flyway_before=$(grep '^flyway:' "$BACKUP_DIR/geopulse-$RUN_ID.snapshot.txt" 2>/dev/null || true)
    start_app_containers
    if [ -n "$BACKEND_CONTAINER" ] && container_exists "$BACKEND_CONTAINER"; then
        info "Waiting for $BACKEND_CONTAINER to report healthy (up to ${HEALTH_TIMEOUT}s)"
        for i in $(seq 1 $((HEALTH_TIMEOUT / 5))); do
            if backend_healthy; then healthy=1; break; fi
            sleep 5
        done
        [ "$healthy" = 1 ] || { docker logs --since "$since" --tail 15 "$BACKEND_CONTAINER" >&2 || true; rollback_after_switch "GeoPulse did not become healthy on PostgreSQL 18"; }
        flyway_after=$(snapshot "$PG_CONTAINER" | grep '^flyway:')
        if [ -n "$flyway_before" ] && [ "$flyway_before" != "$flyway_after" ]; then
            warn "Database migrations changed during the upgrade ($flyway_before -> $flyway_after). Was the GeoPulse version changed at the same time?"
        fi
        local errors
        errors=$(docker logs --since "$since" "$BACKEND_CONTAINER" 2>&1 | grep -E '(^|[[:space:]])ERROR[[:space:]]' | head -n 10 || true)
        [ -z "$errors" ] || { warn "GeoPulse logged errors while starting; check that they are expected:"; printf '    %s\n' "$errors" >&2; }
    fi
    write_state DONE

    log "PostgreSQL 18 upgrade completed successfully."
    cat <<EOF

    Open GeoPulse and check your timeline and map.
    Kept for rollback and safety (nothing was deleted):
      - PostgreSQL 17 files: $OLD_DIR/ on $DATA_SOURCE
      - dump:                $DUMP_FILE
      - roles:               ${DUMP_FILE%.dump}.globals.sql
    If something is wrong:  $0$HINT_ARGS --rollback   (data written since the switch is lost)
    When you are happy:     $0$HINT_ARGS --cleanup    (deletes the PostgreSQL 17 files; dumps stay)

EOF
}

rollback_after_switch() {
    trap - ERR INT TERM
    warn "$1. Rolling back to PostgreSQL 17."
    do_rollback
    die "Rolled back. GeoPulse runs on PostgreSQL 17 again. The dump and reports are in $BACKUP_DIR."
}

do_rollback() {
    local c
    for c in $APP_CONTAINERS; do container_running "$c" && docker stop -t 60 "$c" >/dev/null; done
    if container_running "$PG_CONTAINER"; then docker stop -t 300 "$PG_CONTAINER" >/dev/null; fi
    if ! move_pg17_back; then
        warn "Could not move the PostgreSQL 17 files back. Starting GeoPulse again on PostgreSQL 18."
        docker start "$PG_CONTAINER" >/dev/null || true
        wait_for_postgres "$PG_CONTAINER" || true
        STOPPED=$APP_CONTAINERS
        start_app_containers
        die "Rollback failed; nothing was switched. Check the folders on $DATA_SOURCE."
    fi
    info "PostgreSQL 17 files moved back; the PostgreSQL 18 folder was kept as pg18-failed-$RUN_ID/"
    restore_compose_files
    write_state ROLLED_BACK
    if [ -n "$COMPOSE_PROJECT" ] && [ -n "$COMPOSE_BACKUPS" ]; then
        compose up -d --no-deps "$COMPOSE_SERVICE"
    else
        warn "Restore image ${SOURCE_IMAGE_STATE:-postgis/postgis:$SOURCE_TAG} and the $OLD_TARGET mount in your compose file or template, then start $PG_CONTAINER."
        return 0
    fi
    wait_for_postgres "$PG_CONTAINER" || die "PostgreSQL 17 did not start; check 'docker logs $PG_CONTAINER'."
    [ -n "$STOPPED" ] || STOPPED=$APP_CONTAINERS
    start_app_containers
}

rollback() {
    discover
    read_state
    [ -n "$MODE_ARG" ] && [ "$MODE_ARG" != "$RUN_ID" ] && die "The volume's last upgrade run is $RUN_ID, not $MODE_ARG."
    case "$STATE_PHASE" in
        SWITCHED|DONE) ;;
        DB_VALIDATED|RESTORING) die "Run $RUN_ID never switched; run the script without --rollback to clean it up." ;;
        *) die "No switched upgrade found on $DATA_SOURCE (state: ${STATE_PHASE:-none})." ;;
    esac
    SOURCE_IMAGE=${SOURCE_IMAGE_STATE:-$SOURCE_IMAGE}
    [ -n "$OLD_DIR" ] && on_volume '[ -s "/mnt/gp/$1/PG_VERSION" ]' "$OLD_DIR" \
        || die "The kept PostgreSQL 17 files are gone (deleted with --cleanup), so there is nothing to roll back to. Nothing was changed."
    warn "Rolling back to PostgreSQL 17 from $OLD_DIR/."
    [ "$STATE_PHASE" = DONE ] && warn "GPS points and changes saved since the upgrade (run $RUN_ID) are lost. Export them first if you need them."
    confirm "Roll back to PostgreSQL 17?" || die "Cancelled."
    do_rollback
    log "Rolled back. GeoPulse runs on PostgreSQL 17 again."
}

cleanup() {
    discover
    read_state
    [ "$STATE_PHASE" = DONE ] || die "Cleanup is only possible after a completed upgrade (state: ${STATE_PHASE:-none})."
    container_running "$PG_CONTAINER" && [ "$(server_major "$PG_CONTAINER")" = 18 ] || die "$PG_CONTAINER must be running PostgreSQL 18."
    log "Kept folders on $DATA_SOURCE:"
    on_volume 'cd /mnt/gp && for d in pg17-pre-upgrade-* pg18-failed-*; do [ -d "$d" ] && du -sh "$d"; done; true' | sed 's/^/    /'
    local target=${MODE_ARG:+pg17-pre-upgrade-$MODE_ARG} failed
    target=${target:-$OLD_DIR}
    if on_volume '[ -d "/mnt/gp/$1" ]' "$target"; then
        if confirm "Delete $target permanently? GeoPulse keeps running on PostgreSQL 18."; then
            on_volume 'rm -rf "/mnt/gp/$1"' "$target"
            info "Deleted $target"
            if [ "$target" = "$OLD_DIR" ]; then
                OLD_DIR=""
                SOURCE_IMAGE=${SOURCE_IMAGE_STATE:-$SOURCE_IMAGE}
                write_state DONE
            fi
        fi
    else
        info "$target is already gone"
    fi
    for failed in $(on_volume 'cd /mnt/gp && for d in pg18-failed-*; do [ -d "$d" ] && echo "$d"; done; true'); do
        if confirm "Delete $failed (an unused PostgreSQL 18 copy from a rolled-back attempt)?"; then
            on_volume 'rm -rf "/mnt/gp/$1"' "$failed"
            info "Deleted $failed"
        fi
    done
    log "Cleanup finished. Dumps in the backup directory were not touched; delete them yourself when no longer needed."
}

case "$MODE" in
    upgrade) upgrade ;;
    rollback) rollback ;;
    cleanup) cleanup ;;
esac
