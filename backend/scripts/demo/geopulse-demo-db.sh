#!/usr/bin/env bash
set -euo pipefail

SCRIPT_NAME="$(basename "$0")"

COMPOSE_PROJECT_DIR="${COMPOSE_PROJECT_DIR:-/srv/projects/demo/geopulse}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
POSTGRES_SERVICE="${POSTGRES_SERVICE:-geopulse-postgres}"
BACKEND_SERVICE="${BACKEND_SERVICE:-geopulse-backend}"
DEMO_APP_SERVICES="${DEMO_APP_SERVICES:-}"
DEMO_BACKEND_HEALTH_TIMEOUT_SECONDS="${DEMO_BACKEND_HEALTH_TIMEOUT_SECONDS:-120}"

DEMO_SNAPSHOT="${DEMO_SNAPSHOT:-$COMPOSE_PROJECT_DIR/demo-seed/geopulse-demo.snapshot.dump}"
DEMO_RESET_LOCK_FILE="${DEMO_RESET_LOCK_FILE:-/tmp/geopulse-demo-reset.lock}"
DEMO_RESET_LOG_FILE="${DEMO_RESET_LOG_FILE:-/var/log/geopulse-demo-reset.log}"
DEMO_CRON_FILE="${DEMO_CRON_FILE:-/etc/cron.d/geopulse-demo-reset}"
DEMO_CRON_SCHEDULE="${DEMO_CRON_SCHEDULE:-*/15 * * * *}"

DB_HOST="${GEOPULSE_POSTGRES_HOST:-geopulse-postgres}"
DB_PORT="${GEOPULSE_POSTGRES_PORT:-5432}"
DB_NAME="${GEOPULSE_POSTGRES_DB:-geopulse}"
DB_USER="${GEOPULSE_POSTGRES_USERNAME:-geopulse-user}"
DB_PASSWORD="${GEOPULSE_POSTGRES_PASSWORD:-}"
DB_MAINTENANCE_NAME="${DEMO_MAINTENANCE_DB:-postgres}"

usage() {
  cat <<USAGE
Usage:
  $SCRIPT_NAME snapshot          Save the current database as the demo seed
  $SCRIPT_NAME reset             Rebuild the demo database from the seed now
  $SCRIPT_NAME reset --if-due    Rebuild only when the data has fallen a full day behind
  $SCRIPT_NAME status            Show how old the live demo data is
  $SCRIPT_NAME install-cron      Install the periodic "reset --if-due" cron job

Environment:
  COMPOSE_PROJECT_DIR   Docker Compose project directory. Default: /srv/projects/demo/geopulse
  COMPOSE_FILE          Compose file name/path. Default: docker-compose.yml
  POSTGRES_SERVICE      PostgreSQL service name. Default: geopulse-postgres
  BACKEND_SERVICE       Backend service name. Default: geopulse-backend
  DEMO_APP_SERVICES     Services stopped while the databases are swapped. Default: "\$BACKEND_SERVICE"
  DEMO_BACKEND_HEALTH_TIMEOUT_SECONDS
                        Seconds to wait for backend health after restart. Default: 120
  DEMO_SNAPSHOT         Snapshot path. Default: \$COMPOSE_PROJECT_DIR/demo-seed/geopulse-demo.snapshot.dump
  DEMO_MAINTENANCE_DB   Database used for create/rename/drop. Default: postgres
  DEMO_CRON_SCHEDULE    Cron schedule for "reset --if-due". Default: */15 * * * *
  DEMO_RESET_LOG_FILE   Cron log path. Default: /var/log/geopulse-demo-reset.log

How reset works:
  The seed is restored into a staging database while the demo keeps serving the
  old data. All date/timestamp columns are shifted by whole days so the latest GPS
  point lands within the last 24 hours (time of day is kept, nothing ends up in the
  future). Only then is the backend stopped, the databases renamed, and the backend
  started again. If the backend does not become healthy, the previous database is
  swapped back.

Recommended flow:
  1. Generate/adjust demo data manually in the demo instance.
  2. Run: $SCRIPT_NAME snapshot
  3. Run: $SCRIPT_NAME reset
  4. Run: $SCRIPT_NAME install-cron
USAGE
}

log() {
  printf '[%s] %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*"
}

fail() {
  log "ERROR: $*" >&2
  exit 1
}

shell_quote() {
  printf '%q' "$1"
}

env_assignment() {
  printf '%s=%s' "$1" "$(shell_quote "$2")"
}

compose() {
  docker compose -f "$COMPOSE_FILE" "$@"
}

load_env_file() {
  local env_file="$COMPOSE_PROJECT_DIR/.env"
  if [[ -f "$env_file" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$env_file"
    set +a

    DB_HOST="${GEOPULSE_POSTGRES_HOST:-$DB_HOST}"
    DB_PORT="${GEOPULSE_POSTGRES_PORT:-$DB_PORT}"
    DB_NAME="${GEOPULSE_POSTGRES_DB:-$DB_NAME}"
    DB_USER="${GEOPULSE_POSTGRES_USERNAME:-$DB_USER}"
    DB_PASSWORD="${GEOPULSE_POSTGRES_PASSWORD:-$DB_PASSWORD}"
  fi

  STAGING_DB="${DB_NAME}_demo_next"
  PREVIOUS_DB="${DB_NAME}_demo_previous"
}

read_app_services() {
  local services="${DEMO_APP_SERVICES:-$BACKEND_SERVICE}"
  # shellcheck disable=SC2206
  APP_SERVICES=($services)
  [[ "${#APP_SERVICES[@]}" -gt 0 ]] || fail "No app services configured"
}

require_tools() {
  command -v docker >/dev/null 2>&1 || fail "docker is required"
  docker compose version >/dev/null 2>&1 || fail "docker compose plugin is required"
}

require_db_password() {
  [[ -n "$DB_PASSWORD" ]] || fail "GEOPULSE_POSTGRES_PASSWORD is not set"
}

# PGTZ keeps "now" comparisons against timestamp-without-time-zone columns in UTC.
psql_db() {
  local database="$1"
  shift
  compose exec -T \
    -e PGPASSWORD="$DB_PASSWORD" \
    -e PGTZ=UTC \
    "$POSTGRES_SERVICE" \
    psql -X -q -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$database" "$@"
}

pg_dump_exec() {
  compose exec -T \
    -e PGPASSWORD="$DB_PASSWORD" \
    "$POSTGRES_SERVICE" \
    pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" "$@"
}

pg_restore_exec() {
  local database="$1"
  shift
  compose exec -T \
    -e PGPASSWORD="$DB_PASSWORD" \
    "$POSTGRES_SERVICE" \
    pg_restore -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$database" "$@"
}

stop_app_services() {
  read_app_services
  log "Stopping demo app services: ${APP_SERVICES[*]}"
  compose stop "${APP_SERVICES[@]}" >/dev/null
}

start_app_services() {
  read_app_services
  log "Recreating demo app services: ${APP_SERVICES[*]}"
  compose up -d --no-deps --force-recreate "${APP_SERVICES[@]}" >/dev/null
}

wait_for_backend_health() {
  [[ "$DEMO_BACKEND_HEALTH_TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] || fail "DEMO_BACKEND_HEALTH_TIMEOUT_SECONDS must be a positive integer"
  [[ "$DEMO_BACKEND_HEALTH_TIMEOUT_SECONDS" -gt 0 ]] || fail "DEMO_BACKEND_HEALTH_TIMEOUT_SECONDS must be greater than 0"

  log "Waiting for backend app health"
  local deadline=$((SECONDS + DEMO_BACKEND_HEALTH_TIMEOUT_SECONDS))
  while [[ "$SECONDS" -lt "$deadline" ]]; do
    local container_id
    container_id="$(compose ps -q "$BACKEND_SERVICE" 2>/dev/null || true)"
    if [[ -n "$container_id" ]]; then
      local status
      status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id" 2>/dev/null || true)"
      if [[ "$status" == "healthy" || "$status" == "running" ]]; then
        log "Backend app is $status"
        return 0
      fi
    fi
    sleep 1
  done

  log "Backend app did not become healthy within ${DEMO_BACKEND_HEALTH_TIMEOUT_SECONDS}s"
  return 1
}

wait_for_postgres() {
  log "Waiting for PostgreSQL"
  for _ in $(seq 1 60); do
    if compose exec -T "$POSTGRES_SERVICE" pg_isready -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_MAINTENANCE_NAME" >/dev/null 2>&1; then
      return
    fi
    sleep 2
  done
  fail "PostgreSQL did not become ready"
}

# Prints "fresh" when the live data's latest GPS point is within the last 24 hours, otherwise "due".
live_data_freshness() {
  psql_db "$DB_NAME" -tA <<'SQL' 2>/dev/null || echo "due"
SELECT CASE
         WHEN max(timestamp) IS NULL THEN 'due'
         WHEN max(timestamp) > now() THEN 'due'
         WHEN max(timestamp) <= now() - interval '1 day' THEN 'due'
         ELSE 'fresh'
       END
  FROM gps_points;
SQL
}

show_summary() {
  local database="$1"
  psql_db "$database" <<'SQL'
SELECT
  (SELECT count(*) FROM users) AS users,
  (SELECT count(*) FROM gps_points) AS gps_points,
  (SELECT min(timestamp)::date FROM gps_points) AS gps_start_date,
  (SELECT max(timestamp) FROM gps_points) AS latest_gps_point_utc,
  date_trunc('minute', now() - (SELECT max(timestamp) FROM gps_points)) AS latest_point_age;
SQL
}

create_snapshot() {
  mkdir -p "$(dirname "$DEMO_SNAPSHOT")"
  log "Creating demo snapshot: $DEMO_SNAPSHOT"

  local temp_snapshot="$DEMO_SNAPSHOT.tmp"
  pg_dump_exec --format=custom --no-owner --no-acl --compress=9 > "$temp_snapshot"
  chmod 600 "$temp_snapshot"
  mv "$temp_snapshot" "$DEMO_SNAPSHOT"

  log "Snapshot created"
}

drop_database() {
  psql_db "$DB_MAINTENANCE_NAME" -v db="$1" <<'SQL'
DROP DATABASE IF EXISTS :"db" WITH (FORCE);
SQL
}

restore_snapshot_into_staging() {
  [[ -f "$DEMO_SNAPSHOT" ]] || fail "Snapshot file not found: $DEMO_SNAPSHOT"

  log "Creating staging database: $STAGING_DB"
  drop_database "$STAGING_DB"
  psql_db "$DB_MAINTENANCE_NAME" -v db="$STAGING_DB" -v owner="$DB_USER" <<'SQL'
CREATE DATABASE :"db" WITH TEMPLATE template0 OWNER :"owner";
SQL

  log "Restoring demo snapshot into $STAGING_DB: $DEMO_SNAPSHOT"
  pg_restore_exec "$STAGING_DB" --no-owner --no-acl --exit-on-error < "$DEMO_SNAPSHOT"
}

clear_staging_runtime_noise() {
  log "Clearing runtime-only data"
  psql_db "$STAGING_DB" <<'SQL'
DO $$
DECLARE
  table_name text;
BEGIN
  FOREACH table_name IN ARRAY ARRAY['oidc_session_states', 'mobile_auth_codes', 'user_api_tokens']
  LOOP
    IF to_regclass('public.' || table_name) IS NOT NULL THEN
      EXECUTE format('TRUNCATE TABLE public.%I CASCADE', table_name);
    END IF;
  END LOOP;
END $$;
SQL
}

# Whole-day shifts keep every stay and trip at its original time of day.
# The delta is always measured from the pristine snapshot, so repeated resets never drift.
shift_staging_dates() {
  log "Shifting demo dates so the latest GPS point is within the last 24 hours"
  psql_db "$STAGING_DB" <<'SQL'
DO $$
DECLARE
  anchor timestamp;
  day_delta integer;
  t record;
BEGIN
  SELECT max(timestamp)
    INTO anchor
    FROM gps_points;

  IF anchor IS NULL THEN
    RAISE NOTICE 'No GPS points found; skipping date shift.';
    RETURN;
  END IF;

  day_delta := floor(extract(epoch FROM (localtimestamp - anchor)) / 86400)::integer;

  IF day_delta = 0 THEN
    RAISE NOTICE 'Demo data is already current (latest GPS point: % UTC).', anchor;
    RETURN;
  END IF;

  RAISE NOTICE 'Shifting demo date/time columns by % day(s). Latest GPS point: % UTC -> % UTC.',
    day_delta, anchor, anchor + day_delta * interval '1 day';

  FOR t IN
    SELECT c.table_name,
           string_agg(
             CASE
               WHEN c.data_type = 'date' THEN format('%I = %I + %s', c.column_name, c.column_name, day_delta)
               ELSE format('%I = %I + %L::interval', c.column_name, c.column_name, day_delta || ' days')
             END,
             ', ' ORDER BY c.ordinal_position
           ) AS assignments
      FROM information_schema.columns c
      JOIN information_schema.tables tb
        ON tb.table_schema = c.table_schema
       AND tb.table_name = c.table_name
     WHERE c.table_schema = 'public'
       AND tb.table_type = 'BASE TABLE'
       AND c.table_name NOT IN ('flyway_schema_history', 'spatial_ref_sys')
       AND c.data_type IN ('timestamp with time zone', 'timestamp without time zone', 'date')
       AND c.is_generated = 'NEVER'
     GROUP BY c.table_name
     ORDER BY c.table_name
  LOOP
    EXECUTE format('UPDATE public.%I SET %s', t.table_name, t.assignments);
  END LOOP;
END $$;

ANALYZE;
SQL
}

# Atomically renames: live -> outgoing_name, incoming -> live.
# Rename fails while sessions are still disconnecting, so retry briefly.
swap_databases() {
  local incoming="$1"
  local outgoing_name="$2"

  for attempt in $(seq 1 15); do
    if psql_db "$DB_MAINTENANCE_NAME" -v live="$DB_NAME" -v incoming="$incoming" -v outgoing="$outgoing_name" <<'SQL'
BEGIN;
SELECT count(pg_terminate_backend(pid))
  FROM pg_stat_activity
 WHERE datname IN (:'live', :'incoming')
   AND pid <> pg_backend_pid()
\gset terminated_
ALTER DATABASE :"live" RENAME TO :"outgoing";
ALTER DATABASE :"incoming" RENAME TO :"live";
COMMIT;
SQL
    then
      log "Database swap completed: $incoming is now $DB_NAME, previous data kept as $outgoing_name"
      return 0
    fi
    log "Database swap attempt $attempt failed; retrying"
    sleep 1
  done
  return 1
}

reset_demo_db() {
  local if_due="$1"
  (
    if ! flock -n 9; then
      log "Another demo DB operation is already running; skipping"
      exit 0
    fi

    cd "$COMPOSE_PROJECT_DIR"
    require_tools
    load_env_file
    require_db_password
    wait_for_postgres

    if [[ "$if_due" == "true" && "$(live_data_freshness)" == "fresh" ]]; then
      log "Demo data is less than a day old; no reset needed"
      exit 0
    fi

    local app_services_stopped=false
    local databases_swapped=false
    local reset_succeeded=false

    finish_reset() {
      local exit_code=$?
      if [[ "$reset_succeeded" != "true" && "$app_services_stopped" == "true" ]]; then
        if [[ "$databases_swapped" == "true" ]]; then
          log "Rolling back to the previous demo database"
          compose stop "${APP_SERVICES[@]}" >/dev/null || true
          if swap_databases "$PREVIOUS_DB" "$STAGING_DB"; then
            log "Rolled back; the failed database is kept as $STAGING_DB for inspection"
          else
            log "Rollback swap failed; inspect databases $DB_NAME and $PREVIOUS_DB manually"
          fi
        fi
        start_app_services || true
        wait_for_backend_health || log "Backend is still unhealthy; check: docker compose logs $BACKEND_SERVICE"
      fi
      if [[ "$reset_succeeded" != "true" ]]; then
        log "Demo DB reset failed"
        [[ "$exit_code" -ne 0 ]] || exit_code=1
      fi
      exit "$exit_code"
    }
    trap finish_reset EXIT

    restore_snapshot_into_staging
    clear_staging_runtime_noise
    shift_staging_dates
    show_summary "$STAGING_DB"

    drop_database "$PREVIOUS_DB"
    stop_app_services
    app_services_stopped=true

    swap_databases "$STAGING_DB" "$PREVIOUS_DB" || fail "Could not swap demo databases; the previous data is still live"
    databases_swapped=true

    start_app_services
    wait_for_backend_health || fail "Backend did not become healthy on the new demo data"
    reset_succeeded=true

    drop_database "$PREVIOUS_DB"
    log "Demo DB reset completed"
  ) 9>"$DEMO_RESET_LOCK_FILE"
}

snapshot_demo_db() {
  (
    flock -n 9 || fail "Another demo DB operation is already running"

    cd "$COMPOSE_PROJECT_DIR"
    require_tools
    load_env_file
    require_db_password

    wait_for_postgres
    create_snapshot
  ) 9>"$DEMO_RESET_LOCK_FILE"
}

status_demo_db() {
  cd "$COMPOSE_PROJECT_DIR"
  require_tools
  load_env_file
  require_db_password

  show_summary "$DB_NAME"
  if [[ "$(live_data_freshness)" == "fresh" ]]; then
    log "Live demo data is fresh; 'reset --if-due' would do nothing"
  else
    log "Live demo data is due for a reset"
  fi
}

install_cron() {
  local script_path
  script_path="$(cd "$(dirname "$0")" && pwd)/$SCRIPT_NAME"
  local cron_line="$DEMO_CRON_SCHEDULE root env $(env_assignment COMPOSE_PROJECT_DIR "$COMPOSE_PROJECT_DIR") $(env_assignment COMPOSE_FILE "$COMPOSE_FILE") $(env_assignment POSTGRES_SERVICE "$POSTGRES_SERVICE") $(env_assignment BACKEND_SERVICE "$BACKEND_SERVICE") $(env_assignment DEMO_APP_SERVICES "$DEMO_APP_SERVICES") $(env_assignment DEMO_SNAPSHOT "$DEMO_SNAPSHOT") $(env_assignment DEMO_BACKEND_HEALTH_TIMEOUT_SECONDS "$DEMO_BACKEND_HEALTH_TIMEOUT_SECONDS") $(shell_quote "$script_path") reset --if-due >> $(shell_quote "$DEMO_RESET_LOG_FILE") 2>&1"

  if [[ "$(id -u)" -ne 0 ]]; then
    cat <<CRON
Run this as root, or add this line to root's crontab:

$cron_line
CRON
    return
  fi

  printf '%s\n' "$cron_line" > "$DEMO_CRON_FILE"
  chmod 644 "$DEMO_CRON_FILE"
  log "Installed cron job: $DEMO_CRON_FILE"
}

main() {
  local command="${1:-}"
  case "$command" in
    reset)
      case "${2:-}" in
        "") reset_demo_db false ;;
        --if-due) reset_demo_db true ;;
        *) usage >&2; exit 2 ;;
      esac
      ;;
    snapshot)
      snapshot_demo_db
      ;;
    status)
      status_demo_db
      ;;
    install-cron)
      install_cron
      ;;
    -h|--help|help|"")
      usage
      ;;
    *)
      usage >&2
      exit 2
      ;;
  esac
}

main "$@"
