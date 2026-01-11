# Stage 1: Build the application
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /app

# Copy parent pom.xml first
COPY pom.xml .

# Copy backend pom.xml
COPY backend/pom.xml backend/

# Download dependencies BEFORE copying source
# This layer only invalidates when POMs change, not when source changes
WORKDIR /app/backend
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline

# NOW copy backend source code
COPY backend/src src/

# Build the application with cached dependencies
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -DskipTests -Dquarkus.profile=prod

# Stage 2: Create a minimal runtime image
FROM eclipse-temurin:25-jdk-alpine
WORKDIR /app

# Copy the built application from the build stage
COPY --from=build /app/backend/target/quarkus-app/lib/ /app/lib/
COPY --from=build /app/backend/target/quarkus-app/*.jar /app/
COPY --from=build /app/backend/target/quarkus-app/app/ /app/app/
COPY --from=build /app/backend/target/quarkus-app/quarkus/ /app/quarkus/

# Create directories and set permissions for arbitrary UIDs and GIDs
# Supports both OpenShift pattern (any UID with GID 0) and standard docker-compose (any UID:GID)
# Uses UID 1001 to match native image for consistency
RUN mkdir -p /app/dumps /app/logs && \
    addgroup -g 1001 -S appgroup && adduser -u 1001 -G appgroup -S appuser && \
    chmod 777 /app/dumps /app/logs && \
    chown -R 1001:0 /app && \
    chmod -R g+rwX /app

# Default to appuser (UID 1001), but can be overridden with --user flag
USER appuser

# Expose the application port
EXPOSE 8080

# Default JVM options optimized for container environments
# Users can override by setting JAVA_OPTS environment variable
ENV JAVA_OPTS_DEFAULT="-XX:+UseContainerSupport \
                       -XX:InitialRAMPercentage=25 \
                       -XX:MaxRAMPercentage=75 \
                       -XX:+UseG1GC \
                       -XX:MaxGCPauseMillis=200 \
                       -XX:+HeapDumpOnOutOfMemoryError \
                       -XX:HeapDumpPath=/app/dumps/ \
                       -XX:+ExitOnOutOfMemoryError"

# Use JAVA_OPTS if provided by user, otherwise use container-aware defaults
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:-$JAVA_OPTS_DEFAULT} -jar /app/quarkus-run.jar"]