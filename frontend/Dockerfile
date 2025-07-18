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

# Stage 2: Create a minimal runtime image with Nginx
FROM nginx:stable-alpine
WORKDIR /usr/share/nginx/html

# Remove default nginx static assets
RUN rm -rf ./*

# Copy the built assets from the build stage
COPY --from=build /app/dist /usr/share/nginx/html

RUN ls -lah /usr/share/nginx/html

# Copy custom nginx config
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf

# Copy entrypoint script
COPY frontend/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

# Expose port 80
EXPOSE 80

# Set environment variables
ENV API_BASE_URL=/api

# Use entrypoint script
ENTRYPOINT ["/docker-entrypoint.sh"]
