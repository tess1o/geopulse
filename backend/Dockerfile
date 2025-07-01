# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-23 AS build
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
FROM eclipse-temurin:23-jre-alpine
WORKDIR /app

# Create a non-root user to run the application
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy the built application from the build stage
COPY --from=build /app/backend/target/quarkus-app/lib/ /app/lib/
COPY --from=build /app/backend/target/quarkus-app/*.jar /app/
COPY --from=build /app/backend/target/quarkus-app/app/ /app/app/
COPY --from=build /app/backend/target/quarkus-app/quarkus/ /app/quarkus/

# Expose the application port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]