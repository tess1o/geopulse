#!/usr/bin/env bash
# wait-for-it.sh - Wait for service to be available
# Usage: wait-for-it.sh host:port [-t timeout] [-- command args]

set -e

hostport="$1"
shift
cmd="$@"

host=$(echo $hostport | cut -d: -f1)
port=$(echo $hostport | cut -d: -f2)

timeout=15

while [[ $timeout -gt 0 ]]; do
    if nc -z "$host" "$port"; then
        echo "Service $host:$port is available!"
        break
    fi
    echo "Waiting for $host:$port... ($timeout seconds remaining)"
    sleep 1
    ((timeout--))
done

if [[ $timeout -eq 0 ]]; then
    echo "Timeout waiting for $host:$port"
    exit 1
fi

if [[ -n "$cmd" ]]; then
    exec $cmd
fi