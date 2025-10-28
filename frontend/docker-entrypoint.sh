#!/bin/sh

# Generate config.js with fixed relative path
echo "window.VUE_APP_CONFIG = {" > /usr/share/nginx/html/config.js
echo "  API_BASE_URL: '/api'" >> /usr/share/nginx/html/config.js
echo "};" >> /usr/share/nginx/html/config.js

echo "Generated config.js with API_BASE_URL=/api"

# Detect Docker's internal DNS (127.0.0.11) from /etc/resolv.conf if present
if grep -q "127.0.0.11" /etc/resolv.conf; then
  DEFAULT_RESOLVER="127.0.0.11 8.8.8.8"
else
  # We're likely in Kubernetes or bare metal — use system nameservers + fallback
  SYSTEM_NAMESERVERS=$(grep '^nameserver' /etc/resolv.conf | awk '{print $2}' | paste -sd " " -)
  DEFAULT_RESOLVER="${SYSTEM_NAMESERVERS:-8.8.8.8}"
fi

# Allow override from env
: "${OSM_RESOLVER:=${DEFAULT_RESOLVER}}"

# Replace BACKEND_URL_PLACEHOLDER in nginx.conf with the actual backend URL
sed -i "s|BACKEND_URL_PLACEHOLDER|${GEOPULSE_BACKEND_URL:-http://geopulse-backend:8080}|g" /etc/nginx/conf.d/default.conf
sed -i "s|CLIENT_MAX_BODY_SIZE_PLACEHOLDER|${CLIENT_MAX_BODY_SIZE:-200M}|g" /etc/nginx/conf.d/default.conf
sed -i "s|OSM_RESOLVER_PLACEHOLDER|${OSM_RESOLVER}|g" /etc/nginx/conf.d/default.conf

echo "Updated nginx configuration:"
echo "  - backend: ${GEOPULSE_BACKEND_URL:-http://geopulse-backend:8080}"
echo "  - client max body size: ${CLIENT_MAX_BODY_SIZE:-200M}"
echo "  - OSM resolver: ${OSM_RESOLVER}"

# Start Nginx
exec nginx -g "daemon off;"
