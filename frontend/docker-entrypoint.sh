#!/bin/sh

# Generate config.js with fixed relative path
echo "window.VUE_APP_CONFIG = {" > /usr/share/nginx/html/config.js
echo "  API_BASE_URL: '/api'" >> /usr/share/nginx/html/config.js
echo "};" >> /usr/share/nginx/html/config.js

echo "Generated config.js with API_BASE_URL=/api"

# Replace BACKEND_URL_PLACEHOLDER in nginx.conf with the actual backend URL
sed -i "s|BACKEND_URL_PLACEHOLDER|${GEOPULSE_BACKEND_URL:-http://geopulse-backend:8080}|g" /etc/nginx/conf.d/default.conf
sed -i "s|CLIENT_MAX_BODY_SIZE_PLACEHOLDER|${CLIENT_MAX_BODY_SIZE:-200M}|g" /etc/nginx/conf.d/default.conf

echo "Updated nginx configuration to proxy to backend: ${GEOPULSE_BACKEND_URL:-http://geopulse-backend:8080}"

# Start Nginx
exec nginx -g "daemon off;"
