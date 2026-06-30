package org.github.tess1o.geopulse.streaming.service.boat;

import io.smallrye.common.annotation.Identifier;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.dto.BoatSetupStartResponseDTO;
import org.github.tess1o.geopulse.streaming.model.dto.BoatSetupStatusDTO;
import org.github.tess1o.geopulse.streaming.service.trips.GpsPointEnvironmentService;
import org.github.tess1o.geopulse.streaming.service.trips.TripReclassificationService;
import org.hibernate.Session;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.zip.GZIPInputStream;
import javax.sql.DataSource;

@ApplicationScoped
@Slf4j
public class BoatSetupService {

    public static final String DATASET_KEY = "water_surfaces_v1";
    public static final String DATASET_VERSION = "water_surfaces_v1";
    public static final String DOCS_URL = "https://tess1o.github.io/geopulse/docs/user-guide/timeline/boat-setup";
    private static final int ENRICH_BATCH_SIZE = 25_000;
    private static final long DATASET_LOCK_KEY = 0x600A7L;
    private static final long USER_LOCK_NAMESPACE = 0x600A7000L;

    @Inject
    EntityManager entityManager;

    @Inject
    DataSource dataSource;

    @Inject
    GpsPointEnvironmentService gpsPointEnvironmentService;

    @Inject
    TripReclassificationService tripReclassificationService;

    @Inject
    @Identifier("timeline-processing")
    ExecutorService executorService;

    @ConfigProperty(name = "geopulse.water-dataset.url")
    Optional<String> datasetUrl;

    @ConfigProperty(name = "geopulse.water-dataset.sha256")
    Optional<String> datasetSha256;

    @ConfigProperty(name = "geopulse.water-dataset.local-path")
    Optional<String> datasetLocalPath;

    @ConfigProperty(name = "geopulse.water-dataset.auto-import", defaultValue = "true")
    boolean autoImport;

    @ConfigProperty(name = "geopulse.water-dataset.connect-timeout-seconds", defaultValue = "30")
    long datasetConnectTimeoutSeconds;

    @ConfigProperty(name = "geopulse.water-dataset.download-timeout-hours", defaultValue = "6")
    long datasetDownloadTimeoutHours;

    @ConfigProperty(name = "geopulse.water-dataset.download-stall-timeout-seconds", defaultValue = "120")
    long datasetDownloadStallTimeoutSeconds;

    @ConfigProperty(name = "geopulse.water-dataset.setup-start-timeout-minutes", defaultValue = "5")
    long setupStartTimeoutMinutes;

    public boolean shouldUseBoatSetup(TimelineConfig config) {
        return config != null && Boolean.TRUE.equals(config.getBoatEnabled());
    }

    public BoatSetupStatusDTO getStatus(UUID userId) {
        failStaleStartingJobs(userId);
        Optional<BoatSetupStatusDTO> activeJob = getLatestJob(userId, true);
        if (activeJob.isPresent()) {
            return activeJob.get();
        }

        DatasetState datasetState = getDatasetState();
        String datasetVersion = gpsPointEnvironmentService.getCurrentEnvironmentDatasetVersion();
        boolean datasetReady = datasetVersion != null;
        long missing = datasetReady
                ? gpsPointEnvironmentService.countMissingOrStale(userId,
                GpsPointEnvironmentService.DEFAULT_START_DATE,
                datasetVersion)
                : 0;

        String environmentStatus = !datasetReady ? "PENDING" : (missing == 0 ? "READY" : "PENDING");
        BoatSetupStatusDTO currentStatus = BoatSetupStatusDTO.builder()
                .status(datasetReady && missing == 0 ? "READY" : "PENDING")
                .datasetStatus(datasetState.status())
                .userEnvironmentStatus(environmentStatus)
                .phase(datasetReady ? (missing == 0 ? "Boat setup is ready" : "GPS water evidence needs enrichment")
                        : "Water dataset is not imported")
                .progressPercentage(datasetReady && missing == 0 ? 100 : 0)
                .processedGpsPoints(datasetReady ? Math.max(0, gpsPointEnvironmentService.countClassified(userId, datasetVersion)) : null)
                .totalGpsPoints(datasetReady ? gpsPointEnvironmentService.countEligible(userId, GpsPointEnvironmentService.DEFAULT_START_DATE) : null)
                .docsUrl(DOCS_URL)
                .datasetVersion(datasetVersion)
                .featureCount(datasetState.featureCount())
                .updatedAt(datasetState.updatedAt())
                .errorCode(datasetState.errorCode())
                .errorMessage(datasetState.errorMessage())
                .build();

        Optional<BoatSetupStatusDTO> latestJob = getLatestJob(userId, false);
        if (latestJob.isPresent()
                && "FAILED".equals(latestJob.get().status())
                && (!"READY".equals(currentStatus.status())
                || "TRIP_RECLASSIFICATION_FAILED".equals(latestJob.get().errorCode()))) {
            return latestJob.get();
        }

        return currentStatus;
    }

    public BoatSetupStartResponseDTO startSetup(UUID userId) {
        BoatSetupStatusDTO currentStatus = getCurrentSetupStatus(userId);
        if (currentStatus != null && "READY".equals(currentStatus.status())) {
            return BoatSetupStartResponseDTO.builder()
                    .jobId(currentStatus.jobId())
                    .status(currentStatus)
                    .build();
        }

        failStaleStartingJobs(userId);
        Optional<BoatSetupStatusDTO> activeJob = getLatestJob(userId, true);
        if (activeJob.isPresent()) {
            return BoatSetupStartResponseDTO.builder()
                    .jobId(activeJob.get().jobId())
                    .status(activeJob.get())
                    .build();
        }

        UUID jobId = UUID.randomUUID();
        String datasetStatus = getDatasetState().status();
        inTx(() -> entityManager.createNativeQuery("""
                        INSERT INTO boat_environment_jobs (
                            job_id, user_id, status, dataset_status, user_environment_status,
                            phase, progress_percentage, docs_url, created_at, updated_at
                        )
                        VALUES (
                            :jobId, :userId, 'QUEUED', :datasetStatus, 'PENDING',
                            'Boat setup queued', 0, :docsUrl, NOW(), NOW()
                        )
                        """)
                .setParameter("jobId", jobId)
                .setParameter("userId", userId)
                .setParameter("datasetStatus", datasetStatus)
                .setParameter("docsUrl", DOCS_URL)
                .executeUpdate());

        CompletableFuture.runAsync(() -> runSetupJob(userId, jobId), executorService);
        return BoatSetupStartResponseDTO.builder()
                .jobId(jobId)
                .status(getJobStatus(userId, jobId).orElse(null))
                .build();
    }

    BoatSetupStatusDTO getCurrentSetupStatus(UUID userId) {
        return getStatus(userId);
    }

    public Optional<BoatSetupStatusDTO> getJobStatus(UUID userId, UUID jobId) {
        failStaleStartingJobs(userId);
        try {
            Object[] row = (Object[]) entityManager.createNativeQuery("""
                            SELECT
                                job_id, status, dataset_status, user_environment_status, phase,
                                progress_percentage, downloaded_bytes, total_bytes,
                                processed_gps_points, total_gps_points, error_code, error_message,
                                docs_url, updated_at
                            FROM boat_environment_jobs
                            WHERE user_id = :userId AND job_id = :jobId
                            """)
                    .setParameter("userId", userId)
                    .setParameter("jobId", jobId)
                    .getSingleResult();
            DatasetState datasetState = getDatasetState();
            return Optional.of(toStatus(row, datasetState));
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public String ensureReadyForUser(UUID userId, TimelineConfig config, BiConsumer<String, Integer> progressSink) {
        if (!shouldUseBoatSetup(config)) {
            return null;
        }

        String datasetVersion = gpsPointEnvironmentService.getCurrentEnvironmentDatasetVersion();
        if (datasetVersion == null) {
            if (!autoImport) {
                throw new IllegalStateException("Boat setup requires water dataset import. Enable auto import or configure "
                        + "GEOPULSE_WATER_DATASET_LOCAL_PATH. See " + DOCS_URL);
            }
            progress(progressSink, "Preparing Boat water dataset", 20);
            ensureDatasetReady(null);
            datasetVersion = gpsPointEnvironmentService.getCurrentEnvironmentDatasetVersion();
        }

        if (datasetVersion == null) {
            throw new IllegalStateException("Boat water dataset is not available after setup. See " + DOCS_URL);
        }

        progress(progressSink, "Preparing Boat water evidence", 25);
        ensureUserEnvironmentReady(userId, datasetVersion, null, progressSink);
        return datasetVersion;
    }

    public void ensureReadyForEnabledUserInBackground(UUID userId) {
        try {
            ensureReadyForUser(userId, TimelineConfig.builder().boatEnabled(true).build(), null);
        } catch (Exception e) {
            log.warn("Background Boat setup failed for user {}: {}", userId, e.getMessage());
        }
    }

    private void runSetupJob(UUID userId, UUID jobId) {
        try {
            updateJob(jobId, "RUNNING", null, null, "Starting Boat setup", 1, null);
            ensureDatasetReady(jobId);
            String datasetVersion = gpsPointEnvironmentService.getCurrentEnvironmentDatasetVersion();
            if (datasetVersion == null) {
                throw new IllegalStateException("Water dataset import completed without a usable dataset version");
            }
            ensureUserEnvironmentReady(userId, datasetVersion, jobId, null);
            if (!reclassifyExistingTrips(userId, jobId)) {
                return;
            }
            updateJob(jobId, "COMPLETED", "READY", "READY", "Boat setup completed", 100,
                    Map.of("completedAt", Instant.now()));
        } catch (Exception e) {
            log.error("Boat setup job {} failed for user {}: {}", jobId, userId, e.getMessage(), e);
            try {
                if (gpsPointEnvironmentService.getCurrentEnvironmentDatasetVersion() == null
                        && !"READY".equals(getDatasetState().status())) {
                    updateDatasetFailure("SETUP_FAILED", e.getMessage());
                }
            } catch (Exception failureUpdateError) {
                log.warn("Failed to update Boat dataset failure state for job {}", jobId, failureUpdateError);
            }
            try {
                failJob(jobId, "SETUP_FAILED", e.getMessage());
            } catch (Exception jobFailureUpdateError) {
                log.error("Failed to mark Boat setup job {} as failed", jobId, jobFailureUpdateError);
            }
        }
    }

    boolean reclassifyExistingTrips(UUID userId, UUID jobId) {
        try {
            updateJob(jobId, "RUNNING", null, "READY", "Reclassifying existing trips", 99, null);
            tripReclassificationService.reclassifyUserTrips(userId);
            return true;
        } catch (Exception e) {
            log.error("Boat setup job {} failed while reclassifying trips for user {}: {}",
                    jobId, userId, e.getMessage(), e);
            try {
                failJob(jobId, "TRIP_RECLASSIFICATION_FAILED",
                        "Boat setup prepared water evidence but failed to reclassify existing trips: " + e.getMessage());
            } catch (Exception jobFailureUpdateError) {
                log.error("Failed to mark Boat setup job {} as failed after trip reclassification failure",
                        jobId, jobFailureUpdateError);
            }
            return false;
        }
    }

    private void ensureDatasetReady(UUID jobId) {
        if (gpsPointEnvironmentService.getCurrentEnvironmentDatasetVersion() != null) {
            updateJob(jobId, null, "READY", null, "Water dataset ready", 25, null);
            return;
        }

        try (AdvisoryLock lock = tryAdvisoryLock(DATASET_LOCK_KEY)) {
            if (!lock.isLocked()) {
                waitForDatasetImport(jobId);
                return;
            }
            if (gpsPointEnvironmentService.getCurrentEnvironmentDatasetVersion() != null) {
                updateJob(jobId, null, "READY", null, "Water dataset ready", 25, null);
                return;
            }
            importDataset(jobId);
        }
    }

    private void importDataset(UUID jobId) {
        Path artifactPath = null;
        try {
            String sourceDescription = resolveSourceDescription();
            updateDatasetState("DOWNLOADING", "Preparing water dataset artifact", 1, null, null, null, null);
            updateJob(jobId, null, "DOWNLOADING", null, "Preparing water dataset artifact", 5, null);

            artifactPath = resolveArtifact(jobId);
            verifyChecksum(artifactPath);

            updateDatasetState("IMPORTING", "Importing water polygons", 60, null, null, null, null);
            updateJob(jobId, null, "IMPORTING", null, "Importing water polygons", 60, null);
            importArtifactIntoDatabase(artifactPath);

            int featureCount = countWaterSurfaces();
            inTx(() -> entityManager.createNativeQuery("""
                            INSERT INTO geo_dataset_metadata (
                                dataset_name, source_url, source_version, license, attribution,
                                feature_count, imported_at
                            )
                            VALUES (
                                'water_surface_polygons:geopulse_water_surfaces_v1',
                                :sourceUrl,
                                :datasetVersion,
                                'Mixed: HydroLAKES CC-BY 4.0; Natural Earth public domain',
                                'HydroLAKES, HydroSHEDS; Natural Earth',
                                :featureCount,
                                NOW()
                            )
                            ON CONFLICT (dataset_name) DO UPDATE SET
                                source_url = EXCLUDED.source_url,
                                source_version = EXCLUDED.source_version,
                                license = EXCLUDED.license,
                                attribution = EXCLUDED.attribution,
                                feature_count = EXCLUDED.feature_count,
                                imported_at = EXCLUDED.imported_at
                            """)
                    .setParameter("sourceUrl", sourceDescription)
                    .setParameter("datasetVersion", DATASET_VERSION)
                    .setParameter("featureCount", featureCount)
                    .executeUpdate());

            inTx(() -> entityManager.createNativeQuery("TRUNCATE TABLE gps_point_environment").executeUpdate());
            updateDatasetState("READY", "Water dataset ready", 100, null, null, DATASET_VERSION, featureCount);
            updateJob(jobId, null, "READY", null, "Water dataset ready", 70, null);
        } catch (Exception e) {
            updateDatasetFailure("IMPORT_FAILED", e.getMessage());
            throw new RuntimeException("Failed to import Boat water dataset: " + e.getMessage(), e);
        } finally {
            if (artifactPath != null && isTemporaryArtifact(artifactPath)) {
                try {
                    Files.deleteIfExists(artifactPath);
                } catch (IOException e) {
                    log.debug("Failed to delete temporary Boat dataset artifact {}", artifactPath, e);
                }
            }
        }
    }

    private Path resolveArtifact(UUID jobId) throws Exception {
        String localPathConfig = configuredDatasetLocalPath();
        if (!localPathConfig.isBlank()) {
            Path localPath = Path.of(localPathConfig);
            if (!Files.exists(localPath)) {
                throw new IllegalStateException("Configured Boat water dataset local file does not exist: " + localPath);
            }
            long size = Files.size(localPath);
            updateDatasetState("DOWNLOADING", "Using local water dataset file", 50, size, size, null, null);
            updateJob(jobId, null, "DOWNLOADING", null, "Using local water dataset file", 50,
                    Map.of("downloadedBytes", size, "totalBytes", size));
            return localPath;
        }

        String url = configuredDatasetUrl();
        if (url.isBlank()) {
            throw new IllegalStateException("Boat water dataset URL is not configured. Set GEOPULSE_WATER_DATASET_URL "
                    + "or GEOPULSE_WATER_DATASET_LOCAL_PATH. See " + DOCS_URL);
        }

        Path tempFile = Files.createTempFile("geopulse-water-surfaces-", ".copy.gz");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(configuredDownloadTimeout())
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Water dataset download failed with HTTP " + response.statusCode());
        }

        long totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        Duration stallTimeout = configuredDownloadStallTimeout();
        try (InputStream in = response.body();
             var out = Files.newOutputStream(tempFile)) {
            byte[] buffer = new byte[1024 * 1024];
            long downloaded = 0;
            Instant lastBytesAt = Instant.now();
            int read;
            while ((read = in.read(buffer)) != -1) {
                Instant now = Instant.now();
                if (Duration.between(lastBytesAt, now).compareTo(stallTimeout) > 0) {
                    throw new IllegalStateException("Water dataset download stalled for more than "
                            + stallTimeout.toSeconds() + " seconds");
                }
                lastBytesAt = now;
                out.write(buffer, 0, read);
                downloaded += read;
                int progress = totalBytes > 0
                        ? 5 + (int) Math.min(45, Math.floor(downloaded * 45.0d / totalBytes))
                        : 10;
                updateDatasetState("DOWNLOADING", "Downloading water dataset", progress, downloaded, totalBytes, null, null);
                updateJob(jobId, null, "DOWNLOADING", null, "Downloading water dataset", progress,
                        Map.of("downloadedBytes", downloaded, "totalBytes", totalBytes));
            }
        }
        return tempFile;
    }

    private void verifyChecksum(Path artifactPath) throws Exception {
        String expectedSha256 = configuredDatasetSha256();
        if (expectedSha256.isBlank()) {
            log.warn("GEOPULSE_WATER_DATASET_SHA256 is not configured; skipping checksum verification");
            return;
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(artifactPath);
             DigestInputStream digestIn = new DigestInputStream(in, digest)) {
            digestIn.transferTo(OutputStreamDiscard.INSTANCE);
        }

        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equalsIgnoreCase(expectedSha256)) {
            throw new IllegalStateException("Water dataset checksum mismatch. Expected "
                    + expectedSha256 + " but got " + actual);
        }
    }

    private void importArtifactIntoDatabase(Path artifactPath) {
        inTx(() -> {
            entityManager.createNativeQuery("TRUNCATE TABLE water_surface_polygons").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM geo_dataset_metadata WHERE dataset_name LIKE 'water_surface_polygons:%'")
                    .executeUpdate();
            entityManager.createNativeQuery("DROP TABLE IF EXISTS water_surface_polygons_stage").executeUpdate();
            entityManager.createNativeQuery("""
                    CREATE TEMP TABLE water_surface_polygons_stage (
                        source VARCHAR(100) NOT NULL,
                        source_id TEXT,
                        name TEXT,
                        water_type VARCHAR(50),
                        geom_ewkb BYTEA NOT NULL
                    ) ON COMMIT DROP
                    """).executeUpdate();

            Session session = entityManager.unwrap(Session.class);
            session.doWork(connection -> {
                PGConnection pgConnection = connection.unwrap(PGConnection.class);
                CopyManager copyManager = pgConnection.getCopyAPI();
                try (InputStream fileIn = Files.newInputStream(artifactPath);
                     InputStream gzipIn = new GZIPInputStream(fileIn)) {
                    copyManager.copyIn("""
                            COPY water_surface_polygons_stage (
                                source, source_id, name, water_type, geom_ewkb
                            ) FROM STDIN WITH (FORMAT binary)
                            """, gzipIn);
                } catch (IOException e) {
                    throw new SQLException("Failed to read Boat water dataset artifact", e);
                }
            });

            entityManager.createNativeQuery("""
                    INSERT INTO water_surface_polygons (source, source_id, name, water_type, geom)
                    SELECT
                        source,
                        source_id,
                        NULLIF(name, ''),
                        water_type,
                        ST_Multi((ST_Dump(ST_Subdivide(ST_MakeValid(ST_GeomFromEWKB(geom_ewkb)), 256))).geom)::geometry(MultiPolygon, 4326)
                    FROM water_surface_polygons_stage
                    WHERE geom_ewkb IS NOT NULL
                    """).executeUpdate();
            entityManager.createNativeQuery("ANALYZE water_surface_polygons").executeUpdate();
        });
    }

    private void ensureUserEnvironmentReady(UUID userId,
                                            String datasetVersion,
                                            UUID jobId,
                                            BiConsumer<String, Integer> progressSink) {
        long userLockKey = USER_LOCK_NAMESPACE + Math.abs(userId.hashCode());
        try (AdvisoryLock lock = tryAdvisoryLock(userLockKey)) {
            if (!lock.isLocked()) {
                waitForUserEnvironment(userId, datasetVersion, jobId, progressSink);
                return;
            }
            long missingCount = gpsPointEnvironmentService.countMissingOrStale(
                    userId,
                    GpsPointEnvironmentService.DEFAULT_START_DATE,
                    datasetVersion
            );
            long totalGpsPoints = gpsPointEnvironmentService.countEligible(
                    userId,
                    GpsPointEnvironmentService.DEFAULT_START_DATE
            );
            if (missingCount <= 0) {
                updateJob(jobId, null, null, "READY", "GPS water evidence ready", 100,
                        Map.of("processedGpsPoints", totalGpsPoints, "totalGpsPoints", totalGpsPoints));
                progress(progressSink, "GPS water evidence ready", 35);
                return;
            }

            updateJob(jobId, null, null, "RUNNING", "Classifying GPS water evidence", 72,
                    Map.of("processedGpsPoints", 0L, "totalGpsPoints", missingCount));
            progress(progressSink, "Classifying GPS water evidence", 28);

            long processed = 0;
            while (true) {
                int batchCount = gpsPointEnvironmentService.enrichNextBatch(
                        userId,
                        GpsPointEnvironmentService.DEFAULT_START_DATE,
                        datasetVersion,
                        ENRICH_BATCH_SIZE
                );
                if (batchCount <= 0) {
                    break;
                }
                processed += batchCount;
                int setupProgress = 72 + (int) Math.min(27, Math.floor(processed * 27.0d / missingCount));
                int timelineProgress = 28 + (int) Math.min(7, Math.floor(processed * 7.0d / missingCount));
                updateJob(jobId, null, null, "RUNNING", "Classifying GPS water evidence", setupProgress,
                        Map.of("processedGpsPoints", processed, "totalGpsPoints", missingCount));
                progress(progressSink, "Classifying GPS water evidence", timelineProgress);
            }

            updateJob(jobId, null, null, "READY", "GPS water evidence ready", 99,
                    Map.of("processedGpsPoints", processed, "totalGpsPoints", missingCount));
            progress(progressSink, "GPS water evidence ready", 35);
        }
    }

    private void waitForDatasetImport(UUID jobId) {
        updateJob(jobId, null, "IMPORTING", null, "Waiting for water dataset import", 20, null);
        Instant deadline = Instant.now().plus(Duration.ofHours(3));
        while (Instant.now().isBefore(deadline)) {
            String datasetVersion = gpsPointEnvironmentService.getCurrentEnvironmentDatasetVersion();
            if (datasetVersion != null) {
                updateJob(jobId, null, "READY", null, "Water dataset ready", 70, null);
                return;
            }
            DatasetState state = getDatasetState();
            if ("FAILED".equals(state.status())) {
                throw new IllegalStateException("Water dataset import failed: " + state.errorMessage());
            }
            sleep(Duration.ofSeconds(2));
        }
        throw new IllegalStateException("Timed out waiting for water dataset import");
    }

    private void waitForUserEnvironment(UUID userId,
                                        String datasetVersion,
                                        UUID jobId,
                                        BiConsumer<String, Integer> progressSink) {
        updateJob(jobId, null, null, "RUNNING", "Waiting for GPS water evidence", 72, null);
        progress(progressSink, "Waiting for GPS water evidence", 28);
        Instant deadline = Instant.now().plus(Duration.ofHours(3));
        while (Instant.now().isBefore(deadline)) {
            long missing = gpsPointEnvironmentService.countMissingOrStale(
                    userId,
                    GpsPointEnvironmentService.DEFAULT_START_DATE,
                    datasetVersion
            );
            if (missing == 0) {
                updateJob(jobId, null, null, "READY", "GPS water evidence ready", 99, null);
                progress(progressSink, "GPS water evidence ready", 35);
                return;
            }
            sleep(Duration.ofSeconds(2));
        }
        throw new IllegalStateException("Timed out waiting for GPS water evidence");
    }

    private Optional<BoatSetupStatusDTO> getLatestJob(UUID userId, boolean activeOnly) {
        try {
            String activePredicate = activeOnly ? "AND status IN ('QUEUED', 'RUNNING')" : "";
            Object[] row = (Object[]) entityManager.createNativeQuery("""
                            SELECT
                                job_id, status, dataset_status, user_environment_status, phase,
                                progress_percentage, downloaded_bytes, total_bytes,
                                processed_gps_points, total_gps_points, error_code, error_message,
                                docs_url, updated_at
                            FROM boat_environment_jobs
                            WHERE user_id = :userId
                            %s
                            ORDER BY updated_at DESC
                            LIMIT 1
                            """.formatted(activePredicate))
                    .setParameter("userId", userId)
                    .getSingleResult();
            return Optional.of(toStatus(row, getDatasetState()));
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    private void failStaleStartingJobs(UUID userId) {
        inTx(() -> entityManager.createNativeQuery("""
                        UPDATE boat_environment_jobs
                        SET status = 'FAILED',
                            dataset_status = CASE WHEN dataset_status = 'READY' THEN dataset_status ELSE 'FAILED' END,
                            user_environment_status = CASE WHEN user_environment_status = 'READY' THEN user_environment_status ELSE 'FAILED' END,
                            phase = 'Boat setup worker did not start',
                            error_code = 'SETUP_WORKER_STALE',
                            error_message = :errorMessage,
                            docs_url = :docsUrl,
                            completed_at = NOW(),
                            updated_at = NOW()
                        WHERE user_id = :userId
                          AND status IN ('QUEUED', 'RUNNING')
                          AND progress_percentage <= 1
                          AND updated_at < NOW() - (:timeoutMinutes * INTERVAL '1 minute')
                        """)
                .setParameter("userId", userId)
                .setParameter("timeoutMinutes", positiveOrDefault(setupStartTimeoutMinutes, 5))
                .setParameter("errorMessage", "Boat setup did not make progress after starting. Retry setup; if this repeats, check backend logs and offline setup instructions.")
                .setParameter("docsUrl", DOCS_URL)
                .executeUpdate());
    }

    private BoatSetupStatusDTO toStatus(Object[] row, DatasetState datasetState) {
        return BoatSetupStatusDTO.builder()
                .jobId((UUID) row[0])
                .status((String) row[1])
                .datasetStatus((String) row[2])
                .userEnvironmentStatus((String) row[3])
                .phase((String) row[4])
                .progressPercentage(((Number) row[5]).intValue())
                .downloadedBytes(toLong(row[6]))
                .totalBytes(toLong(row[7]))
                .processedGpsPoints(toLong(row[8]))
                .totalGpsPoints(toLong(row[9]))
                .errorCode((String) row[10])
                .errorMessage((String) row[11])
                .docsUrl((String) row[12])
                .updatedAt((Instant) row[13])
                .datasetVersion(datasetState.datasetVersion())
                .featureCount(datasetState.featureCount())
                .build();
    }

    private DatasetState getDatasetState() {
        return inTxResult(() -> {
            try {
                Object[] row = (Object[]) entityManager.createNativeQuery("""
                                SELECT status, dataset_version, feature_count, error_code, error_message, updated_at
                                FROM water_dataset_state
                                WHERE dataset_key = :datasetKey
                                """)
                        .setParameter("datasetKey", DATASET_KEY)
                        .getSingleResult();
                return new DatasetState(
                        (String) row[0],
                        (String) row[1],
                        row[2] == null ? 0 : ((Number) row[2]).intValue(),
                        (String) row[3],
                        (String) row[4],
                        (Instant) row[5]
                );
            } catch (NoResultException e) {
                String status = gpsPointEnvironmentService.getCurrentEnvironmentDatasetVersion() == null
                        ? "NOT_IMPORTED"
                        : "READY";
                return new DatasetState(status, null, 0, null, null, null);
            }
        });
    }

    private void updateDatasetState(String status,
                                    String phase,
                                    int progress,
                                    Long downloadedBytes,
                                    Long totalBytes,
                                    String datasetVersion,
                                    Integer featureCount) {
        int featureCountValue = featureCount == null ? 0 : featureCount;
        inTx(() -> entityManager.createNativeQuery("""
                        INSERT INTO water_dataset_state (
                            dataset_key, status, phase, progress_percentage,
                            downloaded_bytes, total_bytes, artifact_url, local_path, sha256,
                            dataset_version, feature_count, started_at, completed_at, updated_at
                        )
                        VALUES (
                            :datasetKey, :status, :phase, :progress,
                            :downloadedBytes, :totalBytes, :artifactUrl, :localPath, :sha256,
                            :datasetVersion, :featureCount, NOW(),
                            CASE WHEN :status = 'READY' THEN NOW() ELSE NULL END,
                            NOW()
                        )
                        ON CONFLICT (dataset_key) DO UPDATE SET
                            status = EXCLUDED.status,
                            phase = EXCLUDED.phase,
                            progress_percentage = EXCLUDED.progress_percentage,
                            downloaded_bytes = COALESCE(EXCLUDED.downloaded_bytes, water_dataset_state.downloaded_bytes),
                            total_bytes = COALESCE(EXCLUDED.total_bytes, water_dataset_state.total_bytes),
                            artifact_url = EXCLUDED.artifact_url,
                            local_path = EXCLUDED.local_path,
                            sha256 = EXCLUDED.sha256,
                            dataset_version = COALESCE(EXCLUDED.dataset_version, water_dataset_state.dataset_version),
                            feature_count = GREATEST(EXCLUDED.feature_count, water_dataset_state.feature_count),
                            error_code = NULL,
                            error_message = NULL,
                            completed_at = CASE WHEN EXCLUDED.status = 'READY' THEN NOW() ELSE water_dataset_state.completed_at END,
                            updated_at = NOW()
                        """)
                .setParameter("datasetKey", DATASET_KEY)
                .setParameter("status", status)
                .setParameter("phase", phase)
                .setParameter("progress", progress)
                .setParameter("downloadedBytes", downloadedBytes)
                .setParameter("totalBytes", totalBytes)
                .setParameter("artifactUrl", configuredDatasetUrl())
                .setParameter("localPath", configuredDatasetLocalPath())
                .setParameter("sha256", configuredDatasetSha256())
                .setParameter("datasetVersion", datasetVersion)
                .setParameter("featureCount", featureCountValue)
                .executeUpdate());
    }

    private void updateDatasetFailure(String errorCode, String errorMessage) {
        inTx(() -> entityManager.createNativeQuery("""
                        INSERT INTO water_dataset_state (
                            dataset_key, status, phase, progress_percentage,
                            artifact_url, local_path, sha256, error_code, error_message, updated_at
                        )
                        VALUES (
                            :datasetKey, 'FAILED', 'Water dataset setup failed', 0,
                            :artifactUrl, :localPath, :sha256, :errorCode, :errorMessage, NOW()
                        )
                        ON CONFLICT (dataset_key) DO UPDATE SET
                            status = 'FAILED',
                            phase = 'Water dataset setup failed',
                            error_code = EXCLUDED.error_code,
                            error_message = EXCLUDED.error_message,
                            updated_at = NOW()
                """)
                .setParameter("datasetKey", DATASET_KEY)
                .setParameter("artifactUrl", configuredDatasetUrl())
                .setParameter("localPath", configuredDatasetLocalPath())
                .setParameter("sha256", configuredDatasetSha256())
                .setParameter("errorCode", errorCode)
                .setParameter("errorMessage", errorMessage)
                .executeUpdate());
    }

    void updateJob(UUID jobId,
                   String status,
                   String datasetStatus,
                   String environmentStatus,
                   String phase,
                   int progress,
                   Map<String, Object> details) {
        if (jobId == null) {
            return;
        }
        String completedSql = "COMPLETED".equals(status) || "FAILED".equals(status) ? ", completed_at = NOW()" : "";
        inTx(() -> entityManager.createNativeQuery("""
                        UPDATE boat_environment_jobs
                        SET
                            status = COALESCE(:status, status),
                            dataset_status = COALESCE(:datasetStatus, dataset_status),
                            user_environment_status = COALESCE(:environmentStatus, user_environment_status),
                            phase = COALESCE(:phase, phase),
                            progress_percentage = GREATEST(progress_percentage, :progress),
                            downloaded_bytes = COALESCE(:downloadedBytes, downloaded_bytes),
                            total_bytes = COALESCE(:totalBytes, total_bytes),
                            processed_gps_points = COALESCE(:processedGpsPoints, processed_gps_points),
                            total_gps_points = COALESCE(:totalGpsPoints, total_gps_points),
                            updated_at = NOW()
                            %s
                        WHERE job_id = :jobId
                        """.formatted(completedSql))
                .setParameter("jobId", jobId)
                .setParameter("status", status)
                .setParameter("datasetStatus", datasetStatus)
                .setParameter("environmentStatus", environmentStatus)
                .setParameter("phase", phase)
                .setParameter("progress", progress)
                .setParameter("downloadedBytes", details == null ? null : details.get("downloadedBytes"))
                .setParameter("totalBytes", details == null ? null : details.get("totalBytes"))
                .setParameter("processedGpsPoints", details == null ? null : details.get("processedGpsPoints"))
                .setParameter("totalGpsPoints", details == null ? null : details.get("totalGpsPoints"))
                .executeUpdate());
    }

    void failJob(UUID jobId, String errorCode, String errorMessage) {
        if (jobId == null) {
            return;
        }
        inTx(() -> entityManager.createNativeQuery("""
                        UPDATE boat_environment_jobs
                        SET status = 'FAILED',
                            dataset_status = CASE WHEN dataset_status = 'READY' THEN dataset_status ELSE 'FAILED' END,
                            user_environment_status = CASE WHEN user_environment_status = 'READY' THEN user_environment_status ELSE 'FAILED' END,
                            phase = 'Boat setup failed',
                            error_code = :errorCode,
                            error_message = :errorMessage,
                            docs_url = :docsUrl,
                            completed_at = NOW(),
                            updated_at = NOW()
                        WHERE job_id = :jobId
                        """)
                .setParameter("jobId", jobId)
                .setParameter("errorCode", errorCode)
                .setParameter("errorMessage", errorMessage)
                .setParameter("docsUrl", DOCS_URL)
                .executeUpdate());
    }

    private AdvisoryLock tryAdvisoryLock(long key) {
        try {
            Connection connection = dataSource.getConnection();
            try (var statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                statement.setLong(1, key);
                try (var resultSet = statement.executeQuery()) {
                    resultSet.next();
                    if (resultSet.getBoolean(1)) {
                        return new AdvisoryLock(connection, key);
                    }
                }
            }
            connection.close();
            return new AdvisoryLock(null, key);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to acquire Boat setup advisory lock", e);
        }
    }

    private int countWaterSurfaces() {
        return inTxResult(() -> {
            Number count = (Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM water_surface_polygons")
                    .getSingleResult();
            return count == null ? 0 : count.intValue();
        });
    }

    private String resolveSourceDescription() {
        String localPath = configuredDatasetLocalPath();
        if (!localPath.isBlank()) {
            return "local:" + localPath;
        }
        String url = configuredDatasetUrl();
        return url.isBlank() ? "unconfigured" : url;
    }

    private boolean isTemporaryArtifact(Path artifactPath) {
        String localPath = configuredDatasetLocalPath();
        return localPath.isBlank() || !artifactPath.equals(Path.of(localPath));
    }

    private String configuredDatasetUrl() {
        return normalizedConfigValue(datasetUrl);
    }

    private String configuredDatasetSha256() {
        return normalizedConfigValue(datasetSha256);
    }

    private String configuredDatasetLocalPath() {
        return normalizedConfigValue(datasetLocalPath);
    }

    private String normalizedConfigValue(Optional<String> value) {
        return value.map(String::trim).filter(configValue -> !configValue.isBlank()).orElse("");
    }

    private HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(configuredConnectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private Duration configuredConnectTimeout() {
        return Duration.ofSeconds(positiveOrDefault(datasetConnectTimeoutSeconds, 30));
    }

    private Duration configuredDownloadTimeout() {
        return Duration.ofHours(positiveOrDefault(datasetDownloadTimeoutHours, 6));
    }

    private Duration configuredDownloadStallTimeout() {
        return Duration.ofSeconds(positiveOrDefault(datasetDownloadStallTimeoutSeconds, 120));
    }

    private long positiveOrDefault(long value, long defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private void progress(BiConsumer<String, Integer> progressSink, String phase, int percentage) {
        if (progressSink != null) {
            progressSink.accept(phase, percentage);
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Boat setup", e);
        }
    }

    private void inTx(Runnable runnable) {
        QuarkusTransaction.requiringNew().run(runnable);
    }

    private <T> T inTxResult(java.util.function.Supplier<T> supplier) {
        AtomicReference<T> result = new AtomicReference<>();
        inTx(() -> result.set(supplier.get()));
        return result.get();
    }

    private record DatasetState(
            String status,
            String datasetVersion,
            int featureCount,
            String errorCode,
            String errorMessage,
            Instant updatedAt
    ) {
    }

    private final class AdvisoryLock implements AutoCloseable {
        private final Connection connection;
        private final long key;

        private AdvisoryLock(Connection connection, long key) {
            this.connection = connection;
            this.key = key;
        }

        private boolean isLocked() {
            return connection != null;
        }

        @Override
        public void close() {
            if (connection == null) {
                return;
            }
            try (connection;
                 var statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                statement.setLong(1, key);
                statement.executeQuery();
            } catch (SQLException e) {
                log.warn("Failed to release Boat setup advisory lock {}", key, e);
            }
        }
    }

    private static final class OutputStreamDiscard extends java.io.OutputStream {
        private static final OutputStreamDiscard INSTANCE = new OutputStreamDiscard();

        @Override
        public void write(int b) {
        }

        @Override
        public void write(byte[] b, int off, int len) {
        }
    }
}
