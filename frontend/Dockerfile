FROM node:20-alpine AS build
WORKDIR /app

# Copy package.json and package-lock.json
COPY frontend/package*.json ./

# Install dependencies
RUN npm ci

# Copy the rest of the frontend code
COPY frontend/ ./

# Build the application
RUN npm run build

# Stage 2: Runtime
FROM nginx:stable-alpine
WORKDIR /usr/share/nginx/html

# Remove default nginx static assets
RUN rm -rf ./*

# Copy the built assets
COPY --from=build /app/dist /usr/share/nginx/html

# Copy custom configs
COPY frontend/00-cache.conf /etc/nginx/conf.d/00-cache.conf

# --- FIX 1: Copy directly to template ---
# We do NOT copy to default.conf here. The entrypoint will create it.
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf.template

# --- FIX 2: Global Config Tweaks ---
# Update main nginx.conf to use writable PID and disable 'user' directive
# This prevents the "duplicate PID" crash and "warn: user directive" logs
RUN sed -i 's|/var/run/nginx.pid|/tmp/nginx.pid|' /etc/nginx/nginx.conf && \
    sed -i 's/^user/#user/' /etc/nginx/nginx.conf

# --- FIX 3: Permissions ---
# Create directories and set permissions
RUN mkdir -p /var/cache/nginx/osm \
             /var/cache/nginx/client_temp \
             /var/cache/nginx/proxy_temp \
             /var/cache/nginx/fastcgi_temp \
             /var/cache/nginx/uwsgi_temp \
             /var/cache/nginx/scgi_temp \
             /var/run \
             /var/log/nginx \
             /app/config \
             /app/tmp && \
    chmod 1777 /tmp /app/tmp && \
    # Recursive 777 is sufficient. It allows the entrypoint to read the template
    # and write the new default.conf
    chmod -R 777 /var/cache/nginx /var/run /var/log/nginx /app/config /etc/nginx

COPY frontend/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

EXPOSE 80 8080
ENV API_BASE_URL=/api

ENTRYPOINT ["/docker-entrypoint.sh"]