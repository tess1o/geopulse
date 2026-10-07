#!/bin/sh
# Runs both fake servers; if either exits, stop the other so Docker restarts the container.
set -u

PORT="${IMMICH_PORT:-2283}" fake-immich &
immich_pid=$!
PORT="${MEMOS_PORT:-5230}" fake-memos &
memos_pid=$!

trap 'kill -TERM "$immich_pid" "$memos_pid" 2>/dev/null' TERM INT

while kill -0 "$immich_pid" 2>/dev/null && kill -0 "$memos_pid" 2>/dev/null; do
  sleep 2
done

kill -TERM "$immich_pid" "$memos_pid" 2>/dev/null
wait
exit 1
