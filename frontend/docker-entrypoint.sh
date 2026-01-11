#!/bin/sh
set -e

# Use /app/tmp for sed temp files
export TMPDIR=/app/tmp

cp /etc/nginx/conf.d/default.conf.template /etc/nginx/conf.d/default.conf

CURRENT_UID=$(id -u)

if [ "$CURRENT_UID" = "0" ]; then
  NGINX_PORT=80
  echo "Running as root (UID 0) - using port 80"
else
  NGINX_PORT=8080
  echo "Running as non-root (UID $CURRENT_UID) - using port 8080"
fi

# Generate config.js
echo "window.VUE_APP_CONFIG = {" > /app/config/config.js
echo "  API_BASE_URL: '/api'" >> /app/config/config.js
echo "};" >> /app/config/config.js

# Detect Resolver
if grep -q "127.0.0.11" /etc/resolv.conf; then
  DEFAULT_RESOLVER="127.0.0.11 8.8.8.8"
else
  SYSTEM_NAMESERVERS=$(grep '^nameserver' /etc/resolv.conf | awk '{if ($2 ~ ":") print "["$2"]"; else print $2}' | paste -sd " " -)
  DEFAULT_RESOLVER="${SYSTEM_NAMESERVERS:-8.8.8.8}"
fi
: "${OSM_RESOLVER:=${DEFAULT_RESOLVER}}"

# Replace placeholders
# We are modifying the fresh copy we just made from the template
sed -i "s|BACKEND_URL_PLACEHOLDER|${GEOPULSE_BACKEND_URL:-http://geopulse-backend:8080}|g" /etc/nginx/conf.d/default.conf
sed -i "s|CLIENT_MAX_BODY_SIZE_PLACEHOLDER|${CLIENT_MAX_BODY_SIZE:-200M}|g" /etc/nginx/conf.d/default.conf
sed -i "s|OSM_RESOLVER_PLACEHOLDER|${OSM_RESOLVER}|g" /etc/nginx/conf.d/default.conf
sed -i "s|NGINX_PORT_PLACEHOLDER|${NGINX_PORT}|g" /etc/nginx/conf.d/default.conf

echo "Starting Nginx on port ${NGINX_PORT}..."

# Start Nginx
exec nginx -g "daemon off;"