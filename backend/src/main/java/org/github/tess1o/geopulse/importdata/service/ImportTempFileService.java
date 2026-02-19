package org.github.tess1o.geopulse.importdata.service;

import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.importdata.model.ImportJob;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Service for managing temporary files during imports.
 *
 * Large files are moved to /tmp/geopulse/imports/ to avoid loading them into memory.
 * Files are automatically cleaned up after import completion or on container restart.
 */
@ApplicationScoped
@Slf4j
@Startup
public class ImportTempFileService {

    @Inject
    SystemSettingsService settingsService;

    // Startup-only setting (directory paths require restart)
    @ConfigProperty(name = "geopulse.import.temp-directory", defaultValue = "/tmp/geopulse/imports")
    String tempDirectory;

    @PostConstruct
    void initTempDirectory() {
        try {
            Path tempDir = Paths.get(tempDirectory);

            // Create directory if it doesn't exist
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
                log.info("Created import temp directory: {}", tempDir);
            }

            // Verify it's writable
            if (!Files.isWritable(tempDir)) {
                throw new IllegalStateException("Temp directory is not writable: " + tempDir);
            }

            log.info("Import temp directory initialized: {}", tempDir);
            log.info("Large file threshold: {} MB", settingsService.getInteger("import.large-file-threshold-mb"));
            log.info("Temp file retention: {} hours", settingsService.getInteger("import.temp-file-retention-hours"));

            // Clean up any orphaned files from previous runs
            cleanupOrphanedFiles();

        } catch (IOException e) {
            log.error("Failed to initialize temp directory: {}", tempDirectory, e);
            throw new IllegalStateException("Cannot initialize temp directory", e);
        }
    }

    /**
     * Check if a file should be stored on disk vs in memory
     */
    public boolean shouldUseTempFile(long fileSizeBytes) {
        long thresholdBytes = settingsService.getInteger("import.large-file-threshold-mb") * 1024L * 1024L;
        return fileSizeBytes >= thresholdBytes;
    }

    /**
     * Move uploaded file to our temp directory and return the path.
     * This takes ownership of the file before Quarkus auto-deletes it.
     */
    public String moveUploadedFileToTemp(Path uploadedFile, UUID jobId, String originalFileName) throws IOException {
        // Generate safe filename
        String extension = getFileExtension(originalFileName);
        String safeFileName = jobId + extension;

        Path targetPath = Paths.get(tempDirectory, safeFileName);

        log.debug("Moving uploaded file to temp storage: {} -> {}", uploadedFile, targetPath);

        // Move file (instant on same filesystem, no I/O)
        Files.move(uploadedFile, targetPath, StandardCopyOption.REPLACE_EXISTING);

        log.info("Moved import file to temp storage: {} ({} bytes)",
                targetPath, Files.size(targetPath));

        return targetPath.toString();
    }

    /**
     * Copy a file to our temp directory and return the path.
     * Used for drop-folder imports where the original file should be preserved on failure.
     */
    public String copyFileToTemp(Path sourceFile, UUID jobId, String originalFileName) throws IOException {
        String extension = getFileExtension(originalFileName);
        String safeFileName = jobId + extension;

        Path targetPath = Paths.get(tempDirectory, safeFileName);

        log.debug("Copying file to temp storage: {} -> {}", sourceFile, targetPath);

        Files.copy(sourceFile, targetPath, StandardCopyOption.REPLACE_EXISTING);

        log.info("Copied file to temp storage: {} ({} bytes)",
                targetPath, Files.size(targetPath));

        return targetPath.toString();
    }

    /**
     * Delete temp file for a completed/failed import job
     */
    public void cleanupTempFile(ImportJob job) {
        if (job.getTempFilePath() == null) {
            return; // No temp file to clean up
        }

        try {
            Path tempFile = Paths.get(job.getTempFilePath());
            if (Files.exists(tempFile)) {
                long fileSize = Files.size(tempFile);
                Files.delete(tempFile);
                log.info("Deleted temp file for job {}: {} ({} MB)",
                        job.getJobId(), tempFile, fileSize / (1024 * 1024));
            }
        } catch (IOException e) {
            log.warn("Failed to delete temp file for job {}: {}",
                    job.getJobId(), job.getTempFilePath(), e);
        }
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
                                log.info("Cleaned up orphaned temp file: {} ({} MB)",
                                        f.getFileName(), size / (1024 * 1024));
                            } catch (IOException e) {
                                log.warn("Failed to delete orphaned file: {}", f, e);
                            }
                        })
                        .count();

                if (deletedCount > 0) {
                    log.info("Cleaned up {} orphaned temp file(s) from previous run", deletedCount);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup orphaned files", e);
        }
    }

    /**
     * Scheduled cleanup of old temp files (safety net)
     * Runs every hour to catch any files that weren't properly cleaned up
     */
    @Scheduled(every = "1h")
    void scheduledCleanup() {
        try {
            Path tempDir = Paths.get(tempDirectory);
            if (!Files.exists(tempDir)) {
                return;
            }

            int retentionHours = settingsService.getInteger("import.temp-file-retention-hours");
            Instant cutoff = Instant.now().minus(retentionHours, ChronoUnit.HOURS);

            try (Stream<Path> files = Files.list(tempDir)) {
                long deletedCount = files
                        .filter(Files::isRegularFile)
                        .filter(f -> isOlderThan(f, cutoff))
                        .peek(f -> {
                            try {
                                long size = Files.size(f);
                                Files.delete(f);
                                log.info("Cleaned up old temp file: {} ({} MB, older than {} hours)",
                                        f.getFileName(), size / (1024 * 1024), retentionHours);
                            } catch (IOException e) {
                                log.warn("Failed to delete old file: {}", f, e);
                            }
                        })
                        .count();

                if (deletedCount > 0) {
                    log.info("Scheduled cleanup deleted {} old temp file(s)", deletedCount);
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

    private String getFileExtension(String fileName) {
        if (fileName == null) {
            return ".dat";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot);
        }
        return ".dat";
    }

    public String getTempDirectory() {
        return tempDirectory;
    }

    public int getLargeFileThresholdMB() {
        return settingsService.getInteger("import.large-file-threshold-mb");
    }
}
