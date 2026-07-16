#!/usr/bin/env bash

set -Eeuo pipefail

cd "$(dirname "$0")"

BACKEND_PID=""
FRONTEND_PID=""

kill_tree() {
  local signal="$1"
  local pid="$2"
  local child

  if [[ -z "$pid" ]] || ! kill -0 "$pid" 2>/dev/null; then
    return
  fi

  while IFS= read -r child; do
    if [[ -n "$child" ]]; then
      kill_tree "$signal" "$child"
    fi
  done < <(pgrep -P "$pid" 2>/dev/null || true)

  kill "-$signal" "$pid" 2>/dev/null || true
}

is_running() {
  local pid="$1"
  [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null
}

cleanup() {
  echo ""

  if is_running "$BACKEND_PID"; then
    echo "Stopping backend..."
    kill_tree TERM "$BACKEND_PID"
  fi

  if is_running "$FRONTEND_PID"; then
    echo "Stopping frontend..."
    kill_tree TERM "$FRONTEND_PID"
  fi

  sleep 1

  if is_running "$BACKEND_PID"; then
    kill_tree KILL "$BACKEND_PID"
  fi

  if is_running "$FRONTEND_PID"; then
    kill_tree KILL "$FRONTEND_PID"
  fi

  wait "$BACKEND_PID" 2>/dev/null || true
  wait "$FRONTEND_PID" 2>/dev/null || true
}

trap 'cleanup; exit 130' INT
trap 'cleanup; exit 143' TERM

echo "Starting backend and frontend..."

./run-backend-dev.sh "$@" &
BACKEND_PID=$!

(
  cd frontend
  npm run dev
) &
FRONTEND_PID=$!

wait "$BACKEND_PID" "$FRONTEND_PID"
