package org.github.tess1o.geopulse.export.service;

import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Service for managing temporary files during exports.
 *
 * Export files are written to /tmp/geopulse/exports/ to avoid loading them into memory.
 * This prevents OutOfMemoryException with large exports.
 * Files are automatically cleaned up after download or on expiry.
 */
@ApplicationScoped
@Slf4j
@Startup
public class ExportTempFileService {

    @Inject
    SystemSettingsService settingsService;

    // Startup-only setting (directory paths require restart)
    @ConfigProperty(name = "geopulse.export.temp-directory", defaultValue = "/tmp/geopulse/exports")
    String tempDirectory;

    @PostConstruct
    void initTempDirectory() {
        try {
            Path tempDir = Paths.get(tempDirectory);

            // Create directory if it doesn't exist
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
                log.info("Created export temp directory: {}", tempDir);
            }

            // Verify it's writable
            if (!Files.isWritable(tempDir)) {
                throw new IllegalStateException("Temp directory is not writable: " + tempDir);
            }

            log.info("Export temp directory initialized: {}", tempDir);
            log.info("Temp file retention: {} hours", settingsService.getInteger("export.temp-file-retention-hours"));

            // Clean up any orphaned files from previous runs
            cleanupOrphanedFiles();

        } catch (IOException e) {
            log.error("Failed to initialize temp directory: {}", tempDirectory, e);
            throw new IllegalStateException("Cannot initialize temp directory", e);
        }
    }

    /**
     * Create a new temp file for an export job.
     *
     * @param jobId the export job ID
     * @param extension the file extension (e.g., ".json", ".zip", ".gpx", ".csv")
     * @return the path to the created temp file
     * @throws IOException if file creation fails
     */
    public Path createTempFile(UUID jobId, String extension) throws IOException {
        String safeFileName = jobId + extension;
        Path targetPath = Paths.get(tempDirectory, safeFileName);

        // Create parent directories if needed
        Files.createDirectories(targetPath.getParent());

        // Create the file
        Files.createFile(targetPath);

        log.debug("Created export temp file: {}", targetPath);
        return targetPath;
    }

    /**
     * Get an output stream to write to a temp file.
     *
     * @param jobId the export job ID
     * @param extension the file extension
     * @return output stream to write export data
     * @throws IOException if file creation fails
     */
    public OutputStream createOutputStream(UUID jobId, String extension) throws IOException {
        Path tempFile = createTempFile(jobId, extension);
        return Files.newOutputStream(tempFile);
    }

    /**
     * Get the path to an existing temp file for an export job.
     *
     * @param jobId the export job ID
     * @return Optional containing the path if found, empty otherwise
     */
    public Optional<Path> getTempFilePath(UUID jobId) {
        try {
            Path tempDir = Paths.get(tempDirectory);
            if (!Files.exists(tempDir)) {
                return Optional.empty();
            }

            try (Stream<Path> files = Files.list(tempDir)) {
                return files
                        .filter(Files::isRegularFile)
                        .filter(f -> f.getFileName().toString().startsWith(jobId.toString()))
                        .findFirst();
            }
        } catch (IOException e) {
            log.warn("Failed to find temp file for job {}", jobId, e);
            return Optional.empty();
        }
    }

    /**
     * Delete temp file for a completed/expired export job.
     *
     * @param jobId the export job ID
     */
    public void deleteTempFile(UUID jobId) {
        getTempFilePath(jobId).ifPresent(tempFile -> {
            try {
                if (Files.exists(tempFile)) {
                    long fileSize = Files.size(tempFile);
                    Files.delete(tempFile);
                    log.info("Deleted temp file for export job {}: {} ({} MB)",
                            jobId, tempFile, fileSize / (1024 * 1024));
                }
            } catch (IOException e) {
                log.warn("Failed to delete temp file for job {}: {}", jobId, tempFile, e);
            }
        });
    }

    /**
     * Delete temp file by path.
     *
     * @param tempFilePath the path to the temp file
     */
    public void deleteTempFile(String tempFilePath) {
        if (tempFilePath == null) {
            return;
        }

        try {
            Path tempFile = Paths.get(tempFilePath);
            if (Files.exists(tempFile)) {
                long fileSize = Files.size(tempFile);
                Files.delete(tempFile);
                log.info("Deleted export temp file: {} ({} MB)", tempFile, fileSize / (1024 * 1024));
            }
        } catch (IOException e) {
            log.warn("Failed to delete temp file: {}", tempFilePath, e);
        }
    }

    /**
     * Get the size of a temp file in bytes.
     *
     * @param tempFilePath the path to the temp file
     * @return file size in bytes, or -1 if file doesn't exist
     */
    public long getFileSize(String tempFilePath) {
        if (tempFilePath == null) {
            return -1;
        }

        try {
            Path tempFile = Paths.get(tempFilePath);
            if (Files.exists(tempFile)) {
                return Files.size(tempFile);
            }
        } catch (IOException e) {
            log.warn("Failed to get file size: {}", tempFilePath, e);
        }
        return -1;
    }

    /**
     * Check if a temp file exists.
     *
     * @param tempFilePath the path to the temp file
     * @return true if file exists
     */
    public boolean exists(String tempFilePath) {
        if (tempFilePath == null) {
            return false;
        }
        return Files.exists(Paths.get(tempFilePath));
    }

    /**
     * Clean up orphaned files from previous runs (called on startup)
     */
    private void cleanupOrphanedFiles() {
        try {
            Path tempDir = Paths.get(tempDirectory);
            if (!Files.exists(tempDir)) {
                return;
            }

            try (Stream<Path> files = Files.list(tempDir)) {
                long deletedCount = files
                        .filter(Files::isRegularFile)
                        .peek(f -> {
                            try {
                                long size = Files.size(f);
                                Files.delete(f);
                                log.info("Cleaned up orphaned export temp file: {} ({} MB)",
                                        f.getFileName(), size / (1024 * 1024));
                            } catch (IOException e) {
                                log.warn("Failed to delete orphaned file: {}", f, e);
                            }
                        })
                        .count();

                if (deletedCount > 0) {
                    log.info("Cleaned up {} orphaned export temp file(s) from previous run", deletedCount);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup orphaned files", e);
        }
    }

    /**
     * Scheduled cleanup of old temp files (safety net).
     * Runs every hour to catch any files that weren't properly cleaned up.
     */
    @Scheduled(every = "1h")
    void scheduledCleanup() {
        try {
            Path tempDir = Paths.get(tempDirectory);
            if (!Files.exists(tempDir)) {
                return;
            }

            int retentionHours = settingsService.getInteger("export.temp-file-retention-hours");
            Instant cutoff = Instant.now().minus(retentionHours, ChronoUnit.HOURS);

            try (Stream<Path> files = Files.list(tempDir)) {
                long deletedCount = files
                        .filter(Files::isRegularFile)
                        .filter(f -> isOlderThan(f, cutoff))
                        .peek(f -> {
                            try {
                                long size = Files.size(f);
                                Files.delete(f);
                                log.info("Cleaned up old export temp file: {} ({} MB, older than {} hours)",
                                        f.getFileName(), size / (1024 * 1024), retentionHours);
                            } catch (IOException e) {
                                log.warn("Failed to delete old file: {}", f, e);
                            }
                        })
                        .count();

                if (deletedCount > 0) {
                    log.info("Scheduled cleanup deleted {} old export temp file(s)", deletedCount);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to perform scheduled cleanup", e);
        }
    }

    private boolean isOlderThan(Path file, Instant cutoff) {
        try {
            Instant lastModified = Files.getLastModifiedTime(file).toInstant();
            return lastModified.isBefore(cutoff);
        } catch (IOException e) {
            return false;
        }
    }

    public String getTempDirectory() {
        return tempDirectory;
    }

    public int getRetentionHours() {
        return settingsService.getInteger("export.temp-file-retention-hours");
    }
}
