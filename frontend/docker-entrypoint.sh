#!/bin/sh

# Generate config.js with environment variables
echo "window.VUE_APP_CONFIG = {" > /usr/share/nginx/html/config.js
echo "  API_BASE_URL: '${API_BASE_URL:-/api}'" >> /usr/share/nginx/html/config.js
echo "};" >> /usr/share/nginx/html/config.js

echo "Generated config.js with API_BASE_URL=${API_BASE_URL:-/api}"

# Replace API_BASE_URL_PLACEHOLDER in nginx.conf
sed -i "s|API_BASE_URL_PLACEHOLDER|${API_BASE_URL:-/api}|g" /etc/nginx/conf.d/default.conf

echo "Updated nginx configuration with API_BASE_URL=${API_BASE_URL:-/api}"

# Start Nginx
exec nginx -g "daemon off;"
