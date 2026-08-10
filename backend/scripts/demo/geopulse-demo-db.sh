#!/usr/bin/env bash
set -euo pipefail

SCRIPT_NAME="$(basename "$0")"

COMPOSE_PROJECT_DIR="${COMPOSE_PROJECT_DIR:-/srv/projects/demo/geopulse}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
POSTGRES_SERVICE="${POSTGRES_SERVICE:-geopulse-postgres}"
BACKEND_SERVICE="${BACKEND_SERVICE:-geopulse-backend}"
UI_SERVICE="${UI_SERVICE:-geopulse-ui}"
DEMO_APP_SERVICES="${DEMO_APP_SERVICES:-}"
DEMO_BACKEND_HEALTH_TIMEOUT_SECONDS="${DEMO_BACKEND_HEALTH_TIMEOUT_SECONDS:-120}"

DEMO_SNAPSHOT="${DEMO_SNAPSHOT:-$COMPOSE_PROJECT_DIR/demo-seed/geopulse-demo.snapshot.dump}"
DEMO_MAX_USERS="${DEMO_MAX_USERS:-10}"
DEMO_TARGET_DATE="${DEMO_TARGET_DATE:-$(date +%F)}"
DEMO_RESET_LOCK_FILE="${DEMO_RESET_LOCK_FILE:-/tmp/geopulse-demo-reset.lock}"
DEMO_RESET_LOG_FILE="${DEMO_RESET_LOG_FILE:-/var/log/geopulse-demo-reset.log}"
DEMO_CRON_FILE="${DEMO_CRON_FILE:-/etc/cron.d/geopulse-demo-reset}"

DB_HOST="${GEOPULSE_POSTGRES_HOST:-geopulse-postgres}"
DB_PORT="${GEOPULSE_POSTGRES_PORT:-5432}"
DB_NAME="${GEOPULSE_POSTGRES_DB:-geopulse}"
DB_USER="${GEOPULSE_POSTGRES_USERNAME:-geopulse-user}"
DB_PASSWORD="${GEOPULSE_POSTGRES_PASSWORD:-}"

usage() {
  cat <<USAGE
Usage:
  $SCRIPT_NAME reset
  $SCRIPT_NAME snapshot
  $SCRIPT_NAME install-cron

Environment:
  COMPOSE_PROJECT_DIR   Docker Compose project directory. Default: /srv/projects/demo/geopulse
  COMPOSE_FILE          Compose file name/path. Default: docker-compose.yml
  POSTGRES_SERVICE      PostgreSQL service name. Default: geopulse-postgres
  BACKEND_SERVICE       Backend service name. Default: geopulse-backend
  UI_SERVICE            UI service name. Default: geopulse-ui
  DEMO_APP_SERVICES     App services stopped during reset. Default: "\$BACKEND_SERVICE \$UI_SERVICE"
  DEMO_BACKEND_HEALTH_TIMEOUT_SECONDS
                        Seconds to wait for backend health after restart. Default: 120
  DEMO_SNAPSHOT         Snapshot path. Default: \$COMPOSE_PROJECT_DIR/demo-seed/geopulse-demo.snapshot.dump
  DEMO_MAX_USERS        Maximum users kept after restore. Default: 10
  DEMO_TARGET_DATE      Date latest GPS data should land on. Default: host date, YYYY-MM-DD
  DEMO_RESET_LOG_FILE   Cron log path. Default: /var/log/geopulse-demo-reset.log

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
  if [[ ! -f "$env_file" ]]; then
    return
  fi

  set -a
  # shellcheck disable=SC1090
  source "$env_file"
  set +a

  DB_HOST="${GEOPULSE_POSTGRES_HOST:-$DB_HOST}"
  DB_PORT="${GEOPULSE_POSTGRES_PORT:-$DB_PORT}"
  DB_NAME="${GEOPULSE_POSTGRES_DB:-$DB_NAME}"
  DB_USER="${GEOPULSE_POSTGRES_USERNAME:-$DB_USER}"
  DB_PASSWORD="${GEOPULSE_POSTGRES_PASSWORD:-$DB_PASSWORD}"
}

read_app_services() {
  local services="${DEMO_APP_SERVICES:-$BACKEND_SERVICE $UI_SERVICE}"
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

psql_exec() {
  compose exec -T \
    -e PGPASSWORD="$DB_PASSWORD" \
    "$POSTGRES_SERVICE" \
    psql -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" "$@"
}

pg_dump_exec() {
  compose exec -T \
    -e PGPASSWORD="$DB_PASSWORD" \
    "$POSTGRES_SERVICE" \
    pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" "$@"
}

pg_restore_exec() {
  compose exec -T \
    -e PGPASSWORD="$DB_PASSWORD" \
    "$POSTGRES_SERVICE" \
    pg_restore -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" "$@"
}

stop_app_services() {
  read_app_services
  log "Stopping demo app services: ${APP_SERVICES[*]}"
  compose stop "${APP_SERVICES[@]}" >/dev/null
}

start_app_services() {
  read_app_services
  log "Recreating backend app service: $BACKEND_SERVICE"
  compose up -d --force-recreate "$BACKEND_SERVICE" >/dev/null

  local additional_services=()
  local service
  for service in "${APP_SERVICES[@]}"; do
    if [[ "$service" != "$BACKEND_SERVICE" ]]; then
      additional_services+=("$service")
    fi
  done

  if [[ "${#additional_services[@]}" -gt 0 ]]; then
    log "Starting remaining demo app services: ${additional_services[*]}"
    compose up -d "${additional_services[@]}" >/dev/null
  fi

  wait_for_backend_health
}

wait_for_backend_health() {
  [[ "$DEMO_BACKEND_HEALTH_TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] || fail "DEMO_BACKEND_HEALTH_TIMEOUT_SECONDS must be a positive integer"
  [[ "$DEMO_BACKEND_HEALTH_TIMEOUT_SECONDS" -gt 0 ]] || fail "DEMO_BACKEND_HEALTH_TIMEOUT_SECONDS must be greater than 0"

  log "Waiting for backend app health"
  for _ in $(seq 1 "$DEMO_BACKEND_HEALTH_TIMEOUT_SECONDS"); do
    local container_id
    container_id="$(compose ps -q "$BACKEND_SERVICE" 2>/dev/null || true)"
    if [[ -n "$container_id" ]]; then
      local status
      status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id" 2>/dev/null || true)"
      if [[ "$status" == "healthy" || "$status" == "running" ]]; then
        log "Backend app is $status"
        return
      fi
    fi
    sleep 1
  done

  fail "Backend app did not become healthy"
}

wait_for_postgres() {
  log "Waiting for PostgreSQL"
  for _ in $(seq 1 60); do
    if compose exec -T "$POSTGRES_SERVICE" pg_isready -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then
      return
    fi
    sleep 2
  done
  fail "PostgreSQL did not become ready"
}

create_snapshot() {
  mkdir -p "$(dirname "$DEMO_SNAPSHOT")"
  log "Creating demo snapshot: $DEMO_SNAPSHOT"

  pg_dump_exec --format=custom --no-owner --no-acl --compress=9 > "$DEMO_SNAPSHOT"
  chmod 600 "$DEMO_SNAPSHOT"

  log "Snapshot created"
}

restore_snapshot() {
  [[ -f "$DEMO_SNAPSHOT" ]] || fail "Snapshot file not found: $DEMO_SNAPSHOT"

  log "Dropping public schema"
  psql_exec <<'SQL'
DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO PUBLIC;
SQL

  log "Restoring demo snapshot: $DEMO_SNAPSHOT"
  pg_restore_exec --no-owner --no-acl --clean --if-exists < "$DEMO_SNAPSHOT"
}

trim_users() {
  [[ "$DEMO_MAX_USERS" =~ ^[0-9]+$ ]] || fail "DEMO_MAX_USERS must be a positive integer"
  [[ "$DEMO_MAX_USERS" -gt 0 ]] || fail "DEMO_MAX_USERS must be greater than 0"

  log "Trimming users to at most $DEMO_MAX_USERS"
  psql_exec -v max_users="$DEMO_MAX_USERS" <<'SQL'
WITH ranked_users AS (
  SELECT
    id,
    row_number() OVER (
      ORDER BY
        CASE WHEN role = 'ADMIN' THEN 1 ELSE 0 END,
        created_at,
        id
    ) AS rn
  FROM users
)
DELETE FROM users u
USING ranked_users r
WHERE u.id = r.id
  AND r.rn > :'max_users'::int;
SQL
}

shift_demo_dates_to_today() {
  log "Shifting demo dates so latest GPS day is $DEMO_TARGET_DATE"

  psql_exec -v target_date="$DEMO_TARGET_DATE" <<'SQL'
CREATE TEMP TABLE demo_reset_params(target_date date) ON COMMIT DROP;
INSERT INTO demo_reset_params(target_date) VALUES (:'target_date'::date);

DO $$
DECLARE
  anchor_date date;
  target_date date;
  day_delta integer;
  c record;
BEGIN
  SELECT p.target_date
    INTO target_date
    FROM demo_reset_params p;

  SELECT max(timestamp)::date
    INTO anchor_date
    FROM gps_points;

  IF anchor_date IS NULL THEN
    RAISE NOTICE 'No GPS points found; skipping date shift.';
    RETURN;
  END IF;

  day_delta := target_date - anchor_date;

  IF day_delta = 0 THEN
    RAISE NOTICE 'Demo data already ends on target date (%).', target_date;
    RETURN;
  END IF;

  RAISE NOTICE 'Shifting demo date/time columns by % day(s). Anchor date: %, target date: %.',
    day_delta, anchor_date, target_date;

  FOR c IN
    SELECT table_schema, table_name, column_name, data_type
      FROM information_schema.columns
     WHERE table_schema = 'public'
       AND table_name NOT IN ('flyway_schema_history', 'spatial_ref_sys')
       AND data_type IN ('timestamp with time zone', 'timestamp without time zone', 'date')
     ORDER BY table_name, ordinal_position
  LOOP
    IF c.data_type = 'date' THEN
      EXECUTE format(
        'UPDATE %I.%I SET %I = %I + %L::integer WHERE %I IS NOT NULL',
        c.table_schema, c.table_name, c.column_name, c.column_name, day_delta, c.column_name
      );
    ELSE
      EXECUTE format(
        'UPDATE %I.%I SET %I = %I + (%L::integer * interval ''1 day'') WHERE %I IS NOT NULL',
        c.table_schema, c.table_name, c.column_name, c.column_name, day_delta, c.column_name
      );
    END IF;
  END LOOP;
END $$;
SQL
}

clear_runtime_noise() {
  log "Clearing runtime-only data"
  psql_exec <<'SQL'
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

show_summary() {
  psql_exec <<'SQL'
SELECT
  (SELECT count(*) FROM users) AS users,
  (SELECT count(*) FROM gps_points) AS gps_points,
  (SELECT min(timestamp)::date FROM gps_points) AS gps_start_date,
  (SELECT max(timestamp)::date FROM gps_points) AS gps_end_date;
SQL
}

reset_demo_db() {
  (
    flock -n 9 || fail "Another demo reset is already running"
    local app_services_stopped=false
    local reset_succeeded=false

    finish_reset() {
      local exit_code=$?
      if [[ "$app_services_stopped" == "true" ]]; then
        if [[ "$reset_succeeded" == "true" ]]; then
          start_app_services || exit_code=$?
          if [[ "$exit_code" -eq 0 ]]; then
            log "Demo DB reset completed and backend app restarted"
          fi
        else
          log "Reset failed before completion; leaving demo app services stopped so a partial DB is not served"
        fi
      fi
      exit "$exit_code"
    }
    trap finish_reset EXIT

    cd "$COMPOSE_PROJECT_DIR"
    require_tools
    load_env_file
    require_db_password

    wait_for_postgres
    stop_app_services
    app_services_stopped=true

    restore_snapshot
    trim_users
    shift_demo_dates_to_today
    clear_runtime_noise
    show_summary
    reset_succeeded=true
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

install_cron() {
  local script_path
  script_path="$(cd "$(dirname "$0")" && pwd)/$SCRIPT_NAME"
  local cron_line="0 0 * * * root env $(env_assignment COMPOSE_PROJECT_DIR "$COMPOSE_PROJECT_DIR") $(env_assignment COMPOSE_FILE "$COMPOSE_FILE") $(env_assignment POSTGRES_SERVICE "$POSTGRES_SERVICE") $(env_assignment BACKEND_SERVICE "$BACKEND_SERVICE") $(env_assignment UI_SERVICE "$UI_SERVICE") $(env_assignment DEMO_APP_SERVICES "$DEMO_APP_SERVICES") $(env_assignment DEMO_SNAPSHOT "$DEMO_SNAPSHOT") $(env_assignment DEMO_MAX_USERS "$DEMO_MAX_USERS") $(env_assignment DEMO_BACKEND_HEALTH_TIMEOUT_SECONDS "$DEMO_BACKEND_HEALTH_TIMEOUT_SECONDS") $(shell_quote "$script_path") reset >> $(shell_quote "$DEMO_RESET_LOG_FILE") 2>&1"

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
      reset_demo_db
      ;;
    snapshot)
      snapshot_demo_db
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
