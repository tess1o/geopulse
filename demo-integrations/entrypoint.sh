#!/bin/sh
# Runs the fake servers; if any exits, stop the others so Docker restarts the container.
# The fake OIDC provider signs tokens for any email, so it only runs when FAKE_OIDC_ENABLED=true (e2e tests).
set -u

PORT="${IMMICH_PORT:-2283}" fake-immich &
pids="$!"
PORT="${MEMOS_PORT:-5230}" fake-memos &
pids="$pids $!"
if [ "${FAKE_OIDC_ENABLED:-false}" = "true" ]; then
  PORT="${OIDC_PORT:-9400}" fake-oidc &
  pids="$pids $!"
fi

trap 'kill -TERM $pids 2>/dev/null' TERM INT

all_running() {
  for pid in $pids; do
    kill -0 "$pid" 2>/dev/null || return 1
  done
}

while all_running; do
  sleep 2
done

kill -TERM $pids 2>/dev/null
wait
exit 1
