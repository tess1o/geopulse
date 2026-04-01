#!/usr/bin/env bash

set -Eeuo pipefail

cd "$(dirname "$0")"

export JAVA_HOME="/opt/homebrew/Cellar/openjdk/25/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
JAVA_OPEN_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED"
export MAVEN_OPTS="${MAVEN_OPTS:-} ${JAVA_OPEN_OPTS}"

export GEOPULSE_OIDC_PROVIDER_POCKETID_CLIENT_SECRET="tkXtWTJHL0fTzeUMRIB84rJMts0xgx4d"
export GEOPULSE_GEOCODING_NOMINATIM_ENABLED=true
export GEOPULSE_OIDC_PROVIDER_POCKETID_ENABLED=true
export GEOPULSE_GEOCODING_PHOTON_URL="https://photon.komoot.io"
export GEOPULSE_OIDC_PROVIDER_POCKETID_DISCOVERY_URL="https://pocketid.tessio.cc/.well-known/openid-configuration"
export GEOPULSE_GEOCODING_PHOTON_ENABLED=true
export GEOPULSE_OIDC_PROVIDER_POCKETID_ICON="https://1cdn.jsdelivr.net/gh/selfhst/icons@main/svg/authentik.svg"
export GEOPULSE_OIDC_PROVIDER_POCKETID_NAME="PocketID"
export GEOPULSE_GEOCODING_PRIMARY_PROVIDER=photon
export GEOPULSE_OIDC_CALLBACK_BASE_URL="http://localhost:5555"
export GEOPULSE_GEOCODING_FALLBACK_PROVIDER=nominatim
export GEOPULSE_OIDC_PROVIDER_POCKETID_CLIENT_ID="51670362-3655-4b27-b3b5-f787346c0976"

MAVEN_PID=""

cleanup() {
  if [[ -n "$MAVEN_PID" ]] && kill -0 "$MAVEN_PID" 2>/dev/null; then
    echo ""
    echo "Stopping backend..."
    pkill -TERM -P "$MAVEN_PID" 2>/dev/null || true
    kill -TERM "$MAVEN_PID" 2>/dev/null || true
    sleep 1
    pkill -KILL -P "$MAVEN_PID" 2>/dev/null || true
    kill -KILL "$MAVEN_PID" 2>/dev/null || true
    wait "$MAVEN_PID" 2>/dev/null || true
  fi
}

trap 'cleanup; exit 130' INT
trap 'cleanup; exit 143' TERM

echo "Starting backend (Quarkus dev mode)..."
./mvnw -pl backend quarkus:dev "-Djvm.args=${JAVA_OPEN_OPTS}" "$@" &
MAVEN_PID=$!

wait "$MAVEN_PID"
