#!/usr/bin/env bash
#
# Upgrades the PostgreSQL 17 / PostGIS 3.5 database bundled with the GeoPulse Helm chart to
# PostgreSQL 18 / PostGIS 3.6. Needs kubectl (and helm when --chart is given).
#
# The database is dumped onto its PVC and restored by a temporary pod into a new data directory next to the
# PostgreSQL 17 one (pgdata-18-new). Nothing is switched until the copy has been validated. The PostgreSQL 17
# directory is kept as pgdata-pg17-pre-upgrade-<timestamp> for rollback, and the dump is never deleted.
#
# Guide: https://geopulse.cc/docs/system-administration/maintenance/postgresql-18-upgrade
#
#   helm/upgrade-postgres-18.sh -r geopulse -n geopulse --chart geopulse/geopulse --chart-version <version>
#   helm/upgrade-postgres-18.sh ... --rollback   go back to PostgreSQL 17
#   helm/upgrade-postgres-18.sh ... --cleanup    delete the kept PostgreSQL 17 directory once you are happy
#
set -Eeuo pipefail
umask 077

RELEASE=geopulse
NAMESPACE=""
CHART=""
CHART_VERSION=""
TARGET_TAG=""
BACKUP_DIR=./postgres-upgrade-backups
LOCAL_COPY=1
ASSUME_YES=0
HEALTH_TIMEOUT=600
MODE=upgrade
MODE_ARG=""

DATA_MOUNT=/var/lib/postgresql/data
NEW_DIR=pgdata-18-new

usage() {
    cat <<'EOF'
Usage: helm/upgrade-postgres-18.sh [options]

Upgrades the bundled GeoPulse PostgreSQL 17 database to PostgreSQL 18 (dump, restore, validate, switch).

Modes:
  (default)               Upgrade. Safe to run again: it resumes or cleans up an interrupted run.
  --rollback [TIMESTAMP]  Switch back to the kept PostgreSQL 17 data directory.
  --cleanup [TIMESTAMP]   Delete the kept PostgreSQL 17 directory and the dump on the PVC.

Options:
  -r, --release NAME      Helm release (default: geopulse)
  -n, --namespace NS      Namespace (default: the current kubectl namespace)
  --chart REF             Chart to upgrade the release with, e.g. geopulse/geopulse or ./helm/geopulse.
                          Without it the StatefulSet image is changed with kubectl; set
                          postgres.image.tag in your values before the next helm upgrade.
  --chart-version VER     Chart version for --chart
  --target-tag TAG        PostGIS image tag to switch to (default: today's tag with 17-3.5 -> 18-3.6,
                          e.g. 17-3.5-alpine -> 18-3.6-alpine)
  --backup-dir DIR        Local directory for a copy of the dump (default: ./postgres-upgrade-backups)
  --skip-local-copy       Keep the dump only on the PVC
  --health-timeout SEC    How long to wait for the backend to become ready (default: 600)
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

HINT_ARGS=""
for arg in "$@"; do
    case "$arg" in --rollback|--cleanup|-y|--yes) ;; *) HINT_ARGS="$HINT_ARGS $(printf '%q' "$arg")" ;; esac
done

while [ $# -gt 0 ]; do
    case "$1" in
        --rollback|--cleanup)
            MODE=${1#--}
            if [ $# -gt 1 ] && [[ "$2" != -* ]]; then MODE_ARG=$2; shift; fi ;;
        -r|--release) RELEASE=$2; shift ;;
        -n|--namespace) NAMESPACE=$2; shift ;;
        --chart) CHART=$2; shift ;;
        --chart-version) CHART_VERSION=$2; shift ;;
        --target-tag) TARGET_TAG=$2; shift ;;
        --backup-dir) BACKUP_DIR=$2; shift ;;
        --skip-local-copy) LOCAL_COPY=0 ;;
        --health-timeout) HEALTH_TIMEOUT=$2; shift ;;
        -y|--yes) ASSUME_YES=1 ;;
        -h|--help) usage; exit 0 ;;
        *) usage >&2; die "Unknown option: $1" ;;
    esac
    shift
done

command -v kubectl >/dev/null || die "kubectl is not installed or not in PATH"
[ -z "$CHART" ] || command -v helm >/dev/null || die "helm is not installed or not in PATH"
NAMESPACE=${NAMESPACE:-$(kubectl config view --minify -o jsonpath='{..namespace}' 2>/dev/null || true)}
NAMESPACE=${NAMESPACE:-default}
k() { kubectl -n "$NAMESPACE" "$@"; }

human() { awk -v b="$1" 'BEGIN { split("B KB MB GB TB", u); i = 1; while (b >= 1024 && i < 5) { b /= 1024; i++ } printf (i == 1 ? "%d %s" : "%.1f %s"), b, u[i] }'; }
sha256() { if command -v sha256sum >/dev/null; then sha256sum "$1" | awk '{print $1}'; else shasum -a 256 "$1" | awk '{print $1}'; fi; }

# ---------------------------------------------------------------------------------------------------------------
# Discovery and helpers
# ---------------------------------------------------------------------------------------------------------------

discover() {
    STS=$(k get statefulset -l "app.kubernetes.io/instance=$RELEASE,app.kubernetes.io/component=database" -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)
    [ -n "$STS" ] || die "No bundled PostgreSQL StatefulSet for release $RELEASE in namespace $NAMESPACE. With external PostgreSQL only the GeoPulse update is needed."
    POD=$STS-0
    PVC=data-$STS-0
    BACKEND=$(k get deployment -l "app.kubernetes.io/instance=$RELEASE,app.kubernetes.io/component=backend" -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)
    STATE_CM=$STS-pg18-upgrade
    WORK_POD=$STS-pg18-upgrade
    local spec='{.spec.template.spec'
    CURRENT_IMAGE=$(k get sts "$STS" -o jsonpath="$spec.containers[0].image}")
    PGDATA_PATH=$(k get sts "$STS" -o jsonpath="$spec.containers[0].env[?(@.name==\"PGDATA\")].value}")
    ENV_DB=$(k get sts "$STS" -o jsonpath="$spec.containers[0].env[?(@.name==\"POSTGRES_DB\")]}")
    ENV_USER=$(k get sts "$STS" -o jsonpath="$spec.containers[0].env[?(@.name==\"POSTGRES_USER\")]}")
    ENV_PASSWORD=$(k get sts "$STS" -o jsonpath="$spec.containers[0].env[?(@.name==\"POSTGRES_PASSWORD\")]}")
    SECURITY_CONTEXT=$(k get sts "$STS" -o jsonpath="$spec.securityContext}")
    NODE_SELECTOR=$(k get sts "$STS" -o jsonpath="$spec.nodeSelector}")
    TOLERATIONS=$(k get sts "$STS" -o jsonpath="$spec.tolerations}")
    AFFINITY=$(k get sts "$STS" -o jsonpath="$spec.affinity}")
    PULL_SECRETS=$(k get sts "$STS" -o jsonpath="$spec.imagePullSecrets}")
    [ "$PGDATA_PATH" = "$DATA_MOUNT/pgdata" ] || die "Unexpected PGDATA '$PGDATA_PATH'; this script handles the GeoPulse chart layout ($DATA_MOUNT/pgdata)."
    k get pvc "$PVC" >/dev/null 2>&1 || die "PVC $PVC not found (postgres.persistence.enabled=false has no data to migrate)."
    DB_NAME=""
}

# POSTGRES_DB as the PostgreSQL container sees it.
ensure_db_name() {
    [ -n "$DB_NAME" ] || DB_NAME=$(k exec "$1" -- sh -c 'echo "${POSTGRES_DB:-${POSTGRES_USER:-postgres}}"')
}

# The PostgreSQL 18 image: same repository, tag 17-3.5[-suffix] -> 18-3.6[-suffix] unless --target-tag is given.
target_from_source() {
    local tag=${SOURCE_IMAGE##*:}
    case "$tag" in *17-3.5*) TARGET_TAG=${TARGET_TAG:-${tag/17-3.5/18-3.6}} ;; esac
    TARGET_IMAGE=${SOURCE_IMAGE%:*}:$TARGET_TAG
}

state_get() { k get configmap "$STATE_CM" -o jsonpath="{.data.$1}" 2>/dev/null || true; }

read_state() {
    STATE_PHASE=$(state_get PHASE); RUN_ID=$(state_get RUN_ID); OLD_DIR=$(state_get OLD_DIR)
    BACKEND_REPLICAS=$(state_get BACKEND_REPLICAS); SOURCE_IMAGE=$(state_get SOURCE_IMAGE)
    HELM_REVISION=$(state_get HELM_REVISION); LOCAL_DUMP=$(state_get LOCAL_DUMP)
}

write_state() {
    STATE_PHASE=$1
    k create configmap "$STATE_CM" --dry-run=client -o yaml \
        --from-literal=PHASE="$STATE_PHASE" --from-literal=RUN_ID="$RUN_ID" --from-literal=OLD_DIR="$OLD_DIR" \
        --from-literal=BACKEND_REPLICAS="${BACKEND_REPLICAS:-}" --from-literal=SOURCE_IMAGE="${SOURCE_IMAGE:-}" \
        --from-literal=HELM_REVISION="${HELM_REVISION:-}" --from-literal=LOCAL_DUMP="${LOCAL_DUMP:-}" \
        --from-literal=UPDATED="$(date -u +%Y-%m-%dT%H:%M:%SZ)" | k apply -f - >/dev/null
}

# psql as the database owner over the local socket of a running postgres pod. $1 = pod, $2 = database.
psql_in() {
    local pod=$1 database=$2; shift 2
    k exec -i "$pod" -- sh -c \
        'db=$1; shift; PGPASSWORD="${POSTGRES_PASSWORD:-}" exec psql -X -q -At -v ON_ERROR_STOP=1 -U "${POSTGRES_USER:-postgres}" -d "$db" "$@"' \
        sh "$database" "$@"
}

pod_running() { [ "$(k get pod "$1" -o jsonpath='{.status.phase}' 2>/dev/null)" = Running ]; }

wait_for_postgres() {
    local pod=$1 i
    for i in $(seq 1 300); do
        # -U: the pod may run as a UID without a passwd entry, where pg_isready cannot pick a default user.
        if pod_running "$pod" && k exec "$pod" -- sh -c 'pg_isready -q -h 127.0.0.1 -p 5432 -U "${POSTGRES_USER:-postgres}"' >/dev/null 2>&1; then return 0; fi
        case "$(k get pod "$pod" -o jsonpath='{.status.phase}' 2>/dev/null)" in Failed|Succeeded) break ;; esac
        sleep 2
    done
    k logs "$pod" --tail 30 >&2 2>/dev/null || true
    return 1
}

server_major() { psql_in "$1" postgres -c 'SHOW server_version_num' | awk '{ print int($1 / 10000) }'; }

scale_and_wait() { # kind name replicas
    k scale "$1/$2" --replicas="$3" >/dev/null
    if [ "$3" = 0 ]; then
        local selector
        selector=$(k get "$1" "$2" -o go-template='{{range $k, $v := .spec.selector.matchLabels}}{{$k}}={{$v}},{{end}}')
        k wait --for=delete pod -l "${selector%,}" --timeout=600s >/dev/null 2>&1 || true
    fi
}

# Writes a pod manifest. $1 = name, $2 = image, $3 = "postgres" or a shell script to run once.
# Values taken from the StatefulSet are compact JSON, which YAML accepts as flow values.
pod_manifest() {
    local name=$1 image=$2 script=$3
    cat <<EOF
apiVersion: v1
kind: Pod
metadata:
  name: $name
  labels:
    app.kubernetes.io/instance: $RELEASE
    app.kubernetes.io/component: database-upgrade
spec:
  restartPolicy: Never
${SECURITY_CONTEXT:+  securityContext: $SECURITY_CONTEXT
}${NODE_SELECTOR:+  nodeSelector: $NODE_SELECTOR
}${TOLERATIONS:+  tolerations: $TOLERATIONS
}${AFFINITY:+  affinity: $AFFINITY
}${PULL_SECRETS:+  imagePullSecrets: $PULL_SECRETS
}  containers:
    - name: postgres
      image: $image
EOF
    if [ "$script" = postgres ]; then
        cat <<EOF
      args: ["-c", "timezone=UTC", "-c", "maintenance_work_mem=256MB", "-c", "max_wal_size=2GB", "-c", "synchronous_commit=off"]
      env: [$ENV_DB, $ENV_USER, $ENV_PASSWORD, {"name": "PGDATA", "value": "$DATA_MOUNT/$NEW_DIR"}]
EOF
    else
        printf '      command:\n        - sh\n        - -ec\n        - |\n'
        printf '%s\n' "$script" | sed 's/^/          /'
    fi
    cat <<EOF
      volumeMounts:
        - name: data
          mountPath: $DATA_MOUNT
  volumes:
    - name: data
      persistentVolumeClaim:
        claimName: $PVC
EOF
}

# Runs a shell script once in a pod with the PVC mounted (PostgreSQL must be stopped). Prints its output.
on_pvc() {
    local script=$1 phase
    k delete pod "$WORK_POD-sh" --ignore-not-found --wait >/dev/null
    pod_manifest "$WORK_POD-sh" "$TARGET_IMAGE" "$script" | k apply -f - >/dev/null
    for _ in $(seq 1 300); do
        phase=$(k get pod "$WORK_POD-sh" -o jsonpath='{.status.phase}' 2>/dev/null || true)
        case "$phase" in Succeeded|Failed) break ;; esac
        sleep 2
    done
    k logs "$WORK_POD-sh" 2>/dev/null || true
    k delete pod "$WORK_POD-sh" --ignore-not-found --wait >/dev/null
    [ "$phase" = Succeeded ]
}

# ---------------------------------------------------------------------------------------------------------------
# Snapshot: keep in sync with upgrade-postgres-18.sh at the repository root.
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

client_connections() {
    psql_in "$POD" postgres -v db="$DB_NAME" <<'SQL'
SELECT format('pid %s, user %s, application "%s", from %s', pid, usename, application_name, coalesce(client_addr::text, 'local socket'))
  FROM pg_stat_activity WHERE datname = :'db' AND backend_type = 'client backend' AND pid <> pg_backend_pid();
SQL
}

# ---------------------------------------------------------------------------------------------------------------
# Image switch and rollback
# ---------------------------------------------------------------------------------------------------------------

set_postgres_image() { # tag
    local tag=$1
    if [ -n "$CHART" ]; then
        log "helm upgrade $RELEASE $CHART --reuse-values --set postgres.image.tag=$tag"
        helm upgrade "$RELEASE" "$CHART" -n "$NAMESPACE" --reuse-values --set "postgres.image.tag=$tag" \
            ${CHART_VERSION:+--version "$CHART_VERSION"} >/dev/null
    else
        log "Setting the StatefulSet image to ${CURRENT_IMAGE%:*}:$tag"
        warn "Set postgres.image.tag=$tag in your Helm values; otherwise the next helm upgrade switches the image back."
        k set image "statefulset/$STS" "postgres=${CURRENT_IMAGE%:*}:$tag" >/dev/null
        k scale "statefulset/$STS" --replicas=1 >/dev/null
        [ -z "$BACKEND" ] || k scale "deployment/$BACKEND" --replicas="${BACKEND_REPLICAS:-1}" >/dev/null
    fi
}

# Puts the PostgreSQL 17 directory back as pgdata and the PostgreSQL 18 one aside.
swap_back() {
    on_pvc "
        cd $DATA_MOUNT
        [ -d '$OLD_DIR' ] || { echo '$OLD_DIR not found on the PVC' >&2; exit 1; }
        if [ -d pgdata ]; then mv pgdata 'pgdata-18-failed-$RUN_ID'; fi
        mv '$OLD_DIR' pgdata
        test -s pgdata/PG_VERSION
    " >/dev/null
}

do_rollback() {
    [ -z "$BACKEND" ] || scale_and_wait deployment "$BACKEND" 0
    scale_and_wait statefulset "$STS" 0
    if ! swap_back; then
        warn "Could not move the PostgreSQL 17 data back. Starting GeoPulse again on PostgreSQL 18."
        k scale "statefulset/$STS" --replicas=1 >/dev/null
        [ -z "$BACKEND" ] || k scale "deployment/$BACKEND" --replicas="${BACKEND_REPLICAS:-1}" >/dev/null
        die "Rollback failed; nothing was switched. Check the directories on $PVC."
    fi
    info "PostgreSQL 17 data is back in pgdata; the PostgreSQL 18 directory was kept as pgdata-18-failed-$RUN_ID"
    write_state ROLLED_BACK
    if [ -n "$CHART" ] && [ -n "$HELM_REVISION" ] && [ "$1" = automatic ]; then
        log "helm rollback $RELEASE $HELM_REVISION"
        helm rollback "$RELEASE" "$HELM_REVISION" -n "$NAMESPACE" >/dev/null
    else
        set_postgres_image "${SOURCE_IMAGE##*:}"
    fi
    wait_for_postgres "$POD" || die "PostgreSQL 17 did not start; check 'kubectl -n $NAMESPACE logs $POD'."
    [ -n "$CHART" ] || [ -z "$BACKEND" ] || k scale "deployment/$BACKEND" --replicas="${BACKEND_REPLICAS:-1}" >/dev/null
}

rollback_after_switch() {
    trap - ERR INT TERM
    warn "$1. Rolling back to PostgreSQL 17."
    do_rollback automatic
    die "Rolled back. GeoPulse runs on PostgreSQL 17 again. Dump: $DATA_MOUNT/upgrade-$RUN_ID on $PVC${LOCAL_DUMP:+ and $LOCAL_DUMP}."
}

# Undo everything before the switch: the PostgreSQL 17 data directory was never touched.
abort_before_switch() {
    [ "$BASH_SUBSHELL" -eq 0 ] || exit 1
    trap - ERR INT TERM
    set +e
    warn "Upgrade stopped before switching. Restoring PostgreSQL 17 and GeoPulse."
    k delete pod "$WORK_POD" --ignore-not-found --wait >/dev/null 2>&1
    if [ "$STATE_PHASE" = RESTORING ]; then on_pvc "rm -rf $DATA_MOUNT/$NEW_DIR" >/dev/null; fi
    write_state ABORTED
    k scale "statefulset/$STS" --replicas=1 >/dev/null
    wait_for_postgres "$POD" >/dev/null 2>&1
    [ -z "$BACKEND" ] || k scale "deployment/$BACKEND" --replicas="${BACKEND_REPLICAS:-1}" >/dev/null
    warn "Nothing was changed."
    exit 1
}

die_after_abort() { warn "$1"; abort_before_switch; }

# ---------------------------------------------------------------------------------------------------------------
# Upgrade
# ---------------------------------------------------------------------------------------------------------------

preflight() {
    log "Checking the current installation"
    # A StatefulSet does not replace a pod stuck on a broken template by itself, hence the delete.
    local rollback_hint="run 'helm rollback $RELEASE -n $NAMESPACE' (or set postgres.image.tag=17-3.5) and 'kubectl -n $NAMESPACE delete pod $POD', wait until PostgreSQL is ready, then run this script"
    case "${CURRENT_IMAGE##*:}" in
        17*) ;;
        *) die "The StatefulSet already uses $CURRENT_IMAGE, which cannot open the PostgreSQL 17 data (\"database files are incompatible with server\"). Nothing was lost: $rollback_hint." ;;
    esac
    pod_running "$POD" && wait_for_postgres "$POD" || die "$POD is not running. Start PostgreSQL 17 first: $rollback_hint."
    ensure_db_name "$POD"
    local major
    major=$(server_major "$POD")
    [ "$major" = 17 ] || die "$POD runs PostgreSQL $major; this script upgrades PostgreSQL 17."
    SOURCE_IMAGE=$CURRENT_IMAGE
    target_from_source
    [ -n "$TARGET_TAG" ] || die "Cannot derive the PostgreSQL 18 tag from $CURRENT_IMAGE. Pass --target-tag."

    if [ -n "$BACKEND" ] && [ "$(k get deployment "$BACKEND" -o jsonpath='{.status.readyReplicas}')" -ge 1 ] 2>/dev/null; then
        k exec "deployment/$BACKEND" -- sh -c 'test -d /usr/libexec/postgresql18 || test -d /usr/pgsql-18/bin' \
            || die "The GeoPulse backend image cannot back up PostgreSQL 18. Update GeoPulse to a version with PostgreSQL 18 support first, then run this script (keep the same GeoPulse version during the upgrade)."
    else
        warn "The backend is not running; skipping the backend compatibility check."
    fi
    local tablespaces roles databases free
    tablespaces=$(psql_in "$POD" postgres -c "SELECT count(*) FROM pg_tablespace WHERE spcname NOT IN ('pg_default', 'pg_global')")
    [ "$tablespaces" = 0 ] || die "The database uses custom tablespaces, which this script does not migrate."
    roles=$(psql_in "$POD" postgres -c "SELECT string_agg(rolname, ', ' ORDER BY rolname) FROM pg_roles WHERE rolname !~ '^pg_' AND rolname <> current_user")
    databases=$(printf "SELECT string_agg(datname, ', ' ORDER BY datname) FROM pg_database WHERE NOT datistemplate AND datname NOT IN (:'db', 'postgres');\n" | psql_in "$POD" postgres -v db="$DB_NAME")
    DB_SIZE=$(psql_in "$POD" "$DB_NAME" -c 'SELECT pg_database_size(current_database())')
    free=$(( $(k exec "$POD" -- df -Pk "$DATA_MOUNT" | awk 'NR == 2 { print $4 }') * 1024 ))
    # An interrupted run may have left the backend at 0; keep the count it recorded.
    local replicas
    replicas=$( [ -z "$BACKEND" ] || k get deployment "$BACKEND" -o jsonpath='{.spec.replicas}')
    if [ "$replicas" != 0 ] || [ -z "$BACKEND_REPLICAS" ]; then BACKEND_REPLICAS=$replicas; fi

    info "PostgreSQL: $POD ($CURRENT_IMAGE), database \"$DB_NAME\", $(human "$DB_SIZE")"
    info "PVC:        $PVC, $(human "$free") free"
    info "New image:  $TARGET_IMAGE"
    if [ -n "$CHART" ]; then info "Image change: helm upgrade with $CHART${CHART_VERSION:+ $CHART_VERSION}"; else info "Image change: kubectl set image (no --chart given)"; fi
    [ -z "$roles" ] || warn "Other database roles exist ($roles). Roles and their grants are not migrated; they are saved in the globals file so you can recreate them."
    [ -z "$databases" ] || warn "Only \"$DB_NAME\" is migrated. These other databases stay in the kept PostgreSQL 17 directory: $databases"
    # The PVC holds the dump (at most the database size) and the new data directory (about the database size).
    [ "$free" -ge $((DB_SIZE * 22 / 10 + 2 * 1024 * 1024 * 1024)) ] \
        || warn "The PVC may be too small for the dump plus a second copy of the database (needs about $(human $((DB_SIZE * 22 / 10 + 2 * 1024 * 1024 * 1024))))."
}

upgrade() {
    discover
    read_state
    case "$STATE_PHASE" in
        STARTING|ABORTED)
            if [ -n "$BACKEND" ] && [ "$(k get deployment "$BACKEND" -o jsonpath='{.spec.replicas}')" = 0 ] && [ "${BACKEND_REPLICAS:-0}" != 0 ]; then
                warn "A previous run ($RUN_ID) stopped early. Scaling $BACKEND back to $BACKEND_REPLICAS."
                k scale "deployment/$BACKEND" --replicas="$BACKEND_REPLICAS" >/dev/null
            fi
            if [ "$(k get sts "$STS" -o jsonpath='{.spec.replicas}')" = 0 ]; then
                k scale "statefulset/$STS" --replicas=1 >/dev/null
                wait_for_postgres "$POD" || die "PostgreSQL 17 did not start again."
            fi ;;
        DONE)
            log "This release was already upgraded (run $RUN_ID)."
            info "Kept PostgreSQL 17 directory: $OLD_DIR. Remove it with --cleanup once you are happy."
            exit 0 ;;
        RESTORING)
            warn "A previous run ($RUN_ID) stopped while building PostgreSQL 18. Removing its partial copy."
            target_from_source
            k delete pod "$WORK_POD" --ignore-not-found --wait >/dev/null
            on_pvc "rm -rf $DATA_MOUNT/$NEW_DIR" >/dev/null
            write_state ABORTED
            k scale "statefulset/$STS" --replicas=1 >/dev/null
            wait_for_postgres "$POD" || die "PostgreSQL 17 did not start again."
            [ -z "$BACKEND" ] || k scale "deployment/$BACKEND" --replicas="${BACKEND_REPLICAS:-1}" >/dev/null ;;
        DB_VALIDATED)
            if pod_running "$POD"; then
                warn "PostgreSQL 17 was started again after run $RUN_ID made its copy. Discarding that copy and starting over."
                target_from_source
                write_state ABORTED
            else
                target_from_source
                log "Resuming run $RUN_ID: the PostgreSQL 18 copy is built and validated."
                switch_over
                return
            fi ;;
        SWITCHED)
            target_from_source
            log "Resuming run $RUN_ID: data directories are switched."
            start_and_validate
            return ;;
    esac

    if pod_running "$POD" && [ "$(server_major "$POD" 2>/dev/null)" = 18 ]; then
        log "$POD already runs PostgreSQL 18. Nothing to do."
        exit 0
    fi

    preflight
    RUN_ID=$(date -u +%Y%m%d-%H%M%S)
    OLD_DIR=pgdata-pg17-pre-upgrade-$RUN_ID
    DUMP_DIR=$DATA_MOUNT/upgrade-$RUN_ID
    if [ "$LOCAL_COPY" = 1 ]; then
        mkdir -p "$BACKUP_DIR"
        BACKUP_DIR=$(cd "$BACKUP_DIR" && pwd)
        LOCAL_DUMP=$BACKUP_DIR/geopulse-$RUN_ID.dump
    else
        LOCAL_DUMP=""
    fi
    cat <<EOF

The upgrade will:
  1. scale ${BACKEND:-the backend} to 0
  2. dump "$DB_NAME" to $DUMP_DIR on $PVC${LOCAL_DUMP:+ and copy it to $LOCAL_DUMP}
  3. scale PostgreSQL to 0 and restore the dump into $NEW_DIR on the same PVC with PostgreSQL 18
  4. compare row counts, sequences, indexes and sample queries with the original
  5. rename pgdata to $OLD_DIR and $NEW_DIR to pgdata, then switch the image to $TARGET_IMAGE
  6. start GeoPulse and wait until it is ready (automatic rollback if it is not)
GeoPulse is offline until the end. Nothing is deleted.

EOF
    confirm "Start the upgrade?" || die "Cancelled. Nothing was changed."

    log "Stopping GeoPulse"
    write_state STARTING
    trap abort_before_switch ERR INT TERM
    [ -z "$BACKEND" ] || scale_and_wait deployment "$BACKEND" 0
    local connections
    connections=$(client_connections)
    if [ -n "$connections" ]; then
        warn "Other clients are still connected to \"$DB_NAME\":"
        printf '    %s\n' "$connections" >&2
        die_after_abort "Stop them and run the script again."
    fi

    log "Recording what the database contains"
    SNAPSHOT=$(snapshot "$POD")
    info "$(printf '%s\n' "$SNAPSHOT" | grep -c '^rows:') tables, $(printf '%s\n' "$SNAPSHOT" | grep -c '^seq:') sequences"

    log "Dumping \"$DB_NAME\" onto the PVC (this can take a while)"
    k exec "$POD" -- sh -c 'mkdir -p "$1" && PGPASSWORD="${POSTGRES_PASSWORD:-}" pg_dump -U "${POSTGRES_USER:-postgres}" -d "$2" -Fc -f "$1/geopulse.dump" \
        && PGPASSWORD="${POSTGRES_PASSWORD:-}" pg_dumpall -U "${POSTGRES_USER:-postgres}" --globals-only -f "$1/geopulse.globals.sql" \
        && pg_restore --list "$1/geopulse.dump" > /dev/null && printf "%s\n" "$3" > "$1/snapshot.txt" && sha256sum "$1/geopulse.dump"' \
        sh "$DUMP_DIR" "$DB_NAME" "$SNAPSHOT" > /dev/null
    local remote_sum size
    remote_sum=$(k exec "$POD" -- sha256sum "$DUMP_DIR/geopulse.dump" | awk '{print $1}')
    size=$(k exec "$POD" -- sh -c 'wc -c < "$1"' sh "$DUMP_DIR/geopulse.dump" | tr -d ' ')
    info "$(human "$size"), sha256 ${remote_sum:0:16}..."
    if [ -n "$LOCAL_DUMP" ]; then
        k exec "$POD" -- cat "$DUMP_DIR/geopulse.dump" > "$LOCAL_DUMP"
        k exec "$POD" -- cat "$DUMP_DIR/geopulse.globals.sql" > "${LOCAL_DUMP%.dump}.globals.sql"
        printf '%s\n' "$SNAPSHOT" > "${LOCAL_DUMP%.dump}.snapshot.txt"
        [ "$(sha256 "$LOCAL_DUMP")" = "$remote_sum" ] || die_after_abort "The local copy of the dump does not match the one on the PVC."
        info "Local copy: $LOCAL_DUMP"
    fi
    connections=$(client_connections)
    [ -z "$connections" ] || die_after_abort "A client connected during the dump ($connections). Run the script again once it is stopped."
    local locale
    locale=$(psql_in "$POD" "$DB_NAME" -F '|' -c "SELECT pg_encoding_to_char(encoding), datcollate, datctype, datlocprovider, coalesce(datlocale, '') FROM pg_database WHERE datname = current_database()")

    log "Stopping PostgreSQL 17"
    scale_and_wait statefulset "$STS" 0
    write_state RESTORING
    restore_into_pg18 "$locale"
    validate_copy
    write_state DB_VALIDATED
    trap - ERR INT TERM
    switch_over
}

restore_into_pg18() {
    local encoding collate ctype provider loc
    IFS='|' read -r encoding collate ctype provider loc <<< "$1"
    log "Building PostgreSQL 18 in $NEW_DIR next to the PostgreSQL 17 data"
    k delete pod "$WORK_POD" --ignore-not-found --wait >/dev/null
    on_pvc "rm -rf $DATA_MOUNT/$NEW_DIR" >/dev/null
    pod_manifest "$WORK_POD" "$TARGET_IMAGE" postgres | k apply -f - >/dev/null
    wait_for_postgres "$WORK_POD" || die_after_abort "PostgreSQL 18 did not start (check the image name and that the PVC can be attached)."
    info "$(k exec "$WORK_POD" -- postgres --version)"

    # The image's init script installs PostGIS into POSTGRES_DB; start from an empty database with the original
    # encoding and locale instead, so the dump recreates exactly the extensions it contains.
    local create="CREATE DATABASE :\"db\" OWNER :\"owner\" TEMPLATE template0 ENCODING :'enc' LC_COLLATE :'collate' LC_CTYPE :'ctype'"
    case "$provider" in
        i) create="$create LOCALE_PROVIDER icu ICU_LOCALE :'loc'" ;;
        b) create="$create LOCALE_PROVIDER builtin BUILTIN_LOCALE :'loc'" ;;
        *) create="$create LOCALE_PROVIDER libc" ;;
    esac
    local owner
    owner=$(k exec "$WORK_POD" -- sh -c 'echo "${POSTGRES_USER:-postgres}"')
    printf 'DROP DATABASE IF EXISTS :"db";\n%s;\n' "$create" | psql_in "$WORK_POD" template1 \
        -v db="$DB_NAME" -v owner="$owner" -v enc="$encoding" -v collate="$collate" -v ctype="$ctype" -v loc="$loc"

    log "Restoring the dump into PostgreSQL 18 (this can take a while)"
    k exec "$WORK_POD" -- sh -c 'PGPASSWORD="${POSTGRES_PASSWORD:-}" exec pg_restore -U "${POSTGRES_USER:-postgres}" -d "$1" --jobs=2 --exit-on-error --no-owner --no-acl "$2/geopulse.dump"' \
        sh "$DB_NAME" "$DUMP_DIR"
    psql_in "$WORK_POD" "$DB_NAME" -c 'SET client_min_messages = warning' -c 'SELECT postgis_extensions_upgrade()' >/dev/null
    psql_in "$WORK_POD" "$DB_NAME" -c 'ANALYZE'
}

validate_copy() {
    log "Validating the PostgreSQL 18 copy"
    local copy differences
    copy=$(snapshot "$WORK_POD")
    differences=$(diff <(printf '%s\n' "$SNAPSHOT") <(printf '%s\n' "$copy") || true)
    if [ -n "$differences" ]; then
        warn "The PostgreSQL 18 copy differs from the original (< PostgreSQL 17, > PostgreSQL 18):"
        printf '%s\n' "$differences" | head -n 40 >&2
        die_after_abort "Validation failed."
    fi
    local version
    version=$(psql_in "$WORK_POD" "$DB_NAME" -c 'SELECT postgis_full_version()')
    case "$version" in *"need upgrade"*|*UNPACKAGED*) die_after_abort "PostGIS reports an incomplete upgrade: $version" ;; esac
    info "Row counts, sequences, indexes, constraints and sample queries match ($(printf '%s\n' "$copy" | grep -c '^rows:') tables)"
    info "$(psql_in "$WORK_POD" "$DB_NAME" -c "SELECT 'PostGIS ' || postgis_lib_version()")"
    # Stopping PostgreSQL ends the container (it is PID 1), which can cut this exec short.
    k exec "$WORK_POD" -- sh -c 'pg_ctl -D "$PGDATA" stop -m fast' >/dev/null 2>&1 || true
    for _ in $(seq 1 60); do pod_running "$WORK_POD" || break; sleep 2; done
    k delete pod "$WORK_POD" --ignore-not-found --wait >/dev/null
}

switch_over() {
    log "Switching to PostgreSQL 18"
    ! pod_running "$POD" || die "$POD is running; it must be stopped before the switch."
    on_pvc "
        cd $DATA_MOUNT
        [ ! -e pgdata/postmaster.pid ] || { echo 'pgdata/postmaster.pid present: PostgreSQL 17 was not shut down cleanly' >&2; exit 1; }
        if [ -s '$NEW_DIR/PG_VERSION' ]; then
            [ -d '$OLD_DIR' ] || mv pgdata '$OLD_DIR'
            mv '$NEW_DIR' pgdata
        fi
        grep -qx 18 pgdata/PG_VERSION
        grep -qx 17 '$OLD_DIR/PG_VERSION'
    " >/dev/null || { die "Could not switch the data directories. Nothing was started; run the script again or use --rollback."; }
    info "pgdata is now PostgreSQL 18; PostgreSQL 17 is kept as $OLD_DIR"
    if [ -n "$CHART" ]; then HELM_REVISION=$(helm history "$RELEASE" -n "$NAMESPACE" --max 1 | awk 'NR == 2 { print $1 }'); fi
    write_state SWITCHED
    start_and_validate
}

start_and_validate() {
    local since
    since=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    set_postgres_image "$TARGET_TAG"
    log "Waiting for PostgreSQL 18"
    k rollout status "statefulset/$STS" --timeout=600s >/dev/null 2>&1 || true
    wait_for_postgres "$POD" || rollback_after_switch "PostgreSQL 18 did not start"
    [ "$(server_major "$POD")" = 18 ] || rollback_after_switch "$POD is not running PostgreSQL 18"
    ensure_db_name "$POD"
    local before after
    before=$(k exec "$POD" -- cat "$DATA_MOUNT/upgrade-$RUN_ID/snapshot.txt" 2>/dev/null | grep -E '^(ext:|flyway:|indexes:)' || true)
    after=$(snapshot "$POD" | grep -E '^(ext:|flyway:|indexes:)')
    [ -z "$before" ] || [ "$before" = "$after" ] || rollback_after_switch "PostgreSQL 18 in $POD does not show the restored data"

    if [ -n "$BACKEND" ] && [ "${BACKEND_REPLICAS:-1}" != 0 ]; then
        log "Waiting for $BACKEND to become ready (up to ${HEALTH_TIMEOUT}s)"
        k rollout status "deployment/$BACKEND" --timeout="${HEALTH_TIMEOUT}s" >/dev/null 2>&1 \
            || { k logs "deployment/$BACKEND" --since-time="$since" --tail 15 >&2 2>/dev/null || true; rollback_after_switch "GeoPulse did not become ready on PostgreSQL 18"; }
        after=$(snapshot "$POD" | grep '^flyway:')
        before=$(printf '%s\n' "$before" | grep '^flyway:' || true)
        [ -z "$before" ] || [ "$before" = "$after" ] \
            || warn "Database migrations changed during the upgrade ($before -> $after). Was the GeoPulse version changed at the same time?"
        local errors
        errors=$(k logs "deployment/$BACKEND" --since-time="$since" 2>/dev/null | grep -E '"level":"ERROR"|(^|[[:space:]])ERROR[[:space:]]' | head -n 10 || true)
        [ -z "$errors" ] || { warn "GeoPulse logged errors while starting; check that they are expected:"; printf '    %s\n' "$errors" >&2; }
    fi
    write_state DONE

    log "PostgreSQL 18 upgrade completed successfully."
    cat <<EOF

    Open GeoPulse and check your timeline and map.
    Kept for rollback and safety on $PVC (nothing was deleted):
      - PostgreSQL 17 data: $DATA_MOUNT/$OLD_DIR
      - dump and roles:     $DATA_MOUNT/upgrade-$RUN_ID/${LOCAL_DUMP:+
      - local copy:         $LOCAL_DUMP}
    If something is wrong:  $0$HINT_ARGS --rollback   (data written since the switch is lost)
    When you are happy:     $0$HINT_ARGS --cleanup    (deletes the PostgreSQL 17 data and the dump on the PVC)
EOF
    [ -n "$CHART" ] || printf '\n    Remember to set postgres.image.tag=%s in your Helm values.\n' "$TARGET_TAG"
    echo
}

rollback() {
    discover
    read_state
    [ -n "$MODE_ARG" ] && [ "$MODE_ARG" != "$RUN_ID" ] && die "The last upgrade run is $RUN_ID, not $MODE_ARG."
    case "$STATE_PHASE" in
        SWITCHED|DONE) ;;
        *) die "No switched upgrade found for $STS (state: ${STATE_PHASE:-none})." ;;
    esac
    TARGET_IMAGE=$SOURCE_IMAGE
    if [ -z "$OLD_DIR" ] || { pod_running "$POD" && ! k exec "$POD" -- test -s "$DATA_MOUNT/$OLD_DIR/PG_VERSION" 2>/dev/null; }; then
        die "The kept PostgreSQL 17 data is gone (deleted with --cleanup), so there is nothing to roll back to. Nothing was changed."
    fi
    warn "Rolling back to PostgreSQL 17 from $OLD_DIR."
    [ "$STATE_PHASE" = DONE ] && warn "GPS points and changes saved since the upgrade (run $RUN_ID) are lost. Export them first if you need them."
    confirm "Roll back to PostgreSQL 17?" || die "Cancelled."
    do_rollback manual
    log "Rolled back. GeoPulse runs on PostgreSQL 17 again."
}

cleanup() {
    discover
    read_state
    [ "$STATE_PHASE" = DONE ] || die "Cleanup is only possible after a completed upgrade (state: ${STATE_PHASE:-none})."
    pod_running "$POD" && [ "$(server_major "$POD")" = 18 ] || die "$POD must be running PostgreSQL 18."
    log "Kept data on $PVC:"
    k exec "$POD" -- sh -c 'cd "$1" && for d in pgdata-pg17-pre-upgrade-* pgdata-18-failed-* upgrade-*; do [ -d "$d" ] && du -sh "$d"; done; true' sh "$DATA_MOUNT" | sed 's/^/    /'
    local run=${MODE_ARG:-$RUN_ID} entry
    for entry in "pgdata-pg17-pre-upgrade-$run" $(k exec "$POD" -- sh -c 'cd "$1" && ls -d upgrade-* pgdata-18-failed-* 2>/dev/null; true' sh "$DATA_MOUNT"); do
        k exec "$POD" -- test -d "$DATA_MOUNT/$entry" || continue
        if confirm "Delete $entry permanently? GeoPulse keeps running on PostgreSQL 18."; then
            k exec "$POD" -- rm -rf "$DATA_MOUNT/$entry"
            info "Deleted $entry"
            if [ "$entry" = "$OLD_DIR" ]; then OLD_DIR=""; write_state DONE; fi
        fi
    done
    log "Cleanup finished.${LOCAL_DUMP:+ The local copy $LOCAL_DUMP was not touched.}"
}

case "$MODE" in
    upgrade) upgrade ;;
    rollback) rollback ;;
    cleanup) cleanup ;;
esac
