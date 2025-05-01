# GeoPulse

A Quarkus application for tracking and storing location data from OwnTracks. This application demonstrates the integration of Quarkus with PostgreSQL and PostGIS for spatial data storage and querying.

## Prerequisites

For running with Docker (recommended):
- Docker
- Docker Compose

For local development:
- JDK 23 or later
- Maven 3.8.1+
- Docker (for running PostgreSQL with PostGIS)
- GraalVM (optional, for native image compilation)

## Docker Setup

### Running with Docker Compose

The application can be run completely in Docker using the provided Docker Compose configuration:

```bash
# Build and start both the application and database
docker-compose up -d
```

This will:
1. Build the application using the multistage Dockerfile
2. Create a minimal runtime image based on Alpine Linux
3. Start the PostgreSQL database with PostGIS extension
4. Start the GeoPulse application
5. Start the GeoPulse Dashboard for visualizing location data

The application will be accessible at `http://localhost:8080/pub`, the dashboard at `http://localhost:3000`, and the database at `localhost:5432`.

You can stop all services when you're done:

```bash
docker-compose down
```

If you want to remove the persisted data as well:

```bash
docker-compose down -v
```

### Docker Image Details

The Docker image is built in two stages:

1. **Build Stage**:
   - Uses Maven 3.9 with Eclipse Temurin 23 (JDK)
   - Compiles the application
   - Packages the application with Quarkus

2. **Runtime Stage**:
   - Uses Eclipse Temurin 23 JRE Alpine (minimal size)
   - Runs as a non-root user
   - Contains only the necessary files to run the application

### Setting up the Database Only

If you want to run only the database and run the application locally:

```bash
# Start only the PostgreSQL database with PostGIS
docker-compose up -d geopulse-postgres
```

This will create a PostgreSQL database with the PostGIS extension installed. The database will be accessible at `localhost:5432` with the following credentials:
- Username: postgres
- Password: postgres
- Database: geopulse

## Configuration

The application is configured in `src/main/resources/application.properties`. You may need to adjust the database connection settings to match your environment.

## Running the Application in Development Mode

```bash
./mvnw compile quarkus:dev
```

This will start the application in development mode with hot reload enabled.

## Packaging and Running the Application

### JVM Mode

```bash
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

### Native Mode

To build a native executable:

```bash
./mvnw package -Pnative
```

Or, if you don't have GraalVM installed, you can use a container build:

```bash
./mvnw package -Pnative -Dquarkus.native.container-build=true
```

Then, you can run the native executable:

```bash
./target/geopulse-1.0-SNAPSHOT-runner
```

## API Endpoints

### POST /pub

Accepts OwnTracks JSON payloads and stores location data in the database.

Example payload:

```json
{
  "_type": "location",
  "tid": "device1",
  "lat": 51.5074,
  "lon": -0.1278,
  "tst": 1609459200,
  "acc": 10,
  "batt": 95,
  "vel": 0,
  "alt": 20
}
```

## Features

- Stores location data from OwnTracks in PostgreSQL with PostGIS
- Supports spatial queries for finding locations within areas
- Optimized for native compilation with GraalVM
- Minimal resource usage thanks to Quarkus

## Project Structure

- `src/main/java/org/github/tess1o/geopulse/Application.java`: Main application class with REST endpoint
- `src/main/java/org/github/tess1o/geopulse/model/entity/LocationEntity.java`: JPA entity for location data
- `src/main/java/org/github/tess1o/geopulse/repository/LocationRepository.java`: Repository for database operations
- `src/main/resources/application.properties`: Application configuration
- `src/main/resources/reflection-config.json`: GraalVM native image configuration
- `docker-compose.yml`: Docker Compose configuration for PostgreSQL with PostGIS

## License

This project is licensed under the MIT License - see the LICENSE file for details.
