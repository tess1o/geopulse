# Stage 1: Build the application
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /app

# Copy parent pom.xml first
COPY pom.xml .

# Create backend directory and copy its pom.xml
COPY backend/pom.xml backend/
# Copy backend source code
COPY backend/src backend/src

# Build the application
WORKDIR /app/backend
RUN mvn package -DskipTests -Dquarkus.profile=prod

# Stage 2: Create a minimal runtime image
FROM eclipse-temurin:25-jdk-alpine
WORKDIR /app

# Create directories for dumps and logs, and setup user
RUN mkdir -p /app/dumps /app/logs && \
    addgroup -S appgroup && adduser -S appuser -G appgroup && \
    chown -R appuser:appgroup /app

USER appuser

# Copy the built application from the build stage
COPY --from=build /app/backend/target/quarkus-app/lib/ /app/lib/
COPY --from=build /app/backend/target/quarkus-app/*.jar /app/
COPY --from=build /app/backend/target/quarkus-app/app/ /app/app/
COPY --from=build /app/backend/target/quarkus-app/quarkus/ /app/quarkus/

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