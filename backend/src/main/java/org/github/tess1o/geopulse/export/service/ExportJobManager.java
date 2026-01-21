package org.github.tess1o.geopulse.export.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.export.model.ExportStatus;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class ExportJobManager {

    @Inject
    ExportDataGenerator exportDataGenerator;

    @Inject
    SystemSettingsService settingsService;

    @Inject
    ExportTempFileService tempFileService;

    private final ConcurrentHashMap<UUID, ExportJob> activeJobs = new ConcurrentHashMap<>();
    private volatile boolean processing = false;

    public ExportJob createExportJob(UUID userId, List<String> dataTypes,
                                   org.github.tess1o.geopulse.export.model.ExportDateRange dateRange,
                                   String format) {

        // Check if user has too many active jobs
        int maxJobsPerUser = settingsService.getInteger("export.max-jobs-per-user");
        long userActiveJobs = activeJobs.values().stream()
                .filter(job -> job.getUserId().equals(userId))
                .filter(job -> job.getStatus() != ExportStatus.FAILED)
                .count();

        if (userActiveJobs >= maxJobsPerUser) {
            throw new IllegalStateException("Too many active export jobs. Please wait for existing jobs to complete.");
        }

        ExportJob job = new ExportJob(userId, dataTypes, dateRange, format);
        activeJobs.put(job.getJobId(), job);
        
        log.info("Created export job {} for user {} with data types: {}", 
                job.getJobId(), userId, dataTypes);
                
        return job;
    }

    public ExportJob createOwnTracksExportJob(UUID userId, org.github.tess1o.geopulse.export.model.ExportDateRange dateRange) {

        // Check if user has too many active jobs
        int maxJobsPerUser = settingsService.getInteger("export.max-jobs-per-user");
        long userActiveJobs = activeJobs.values().stream()
                .filter(job -> job.getUserId().equals(userId))
                .filter(job -> job.getStatus() != ExportStatus.FAILED)
                .count();

        if (userActiveJobs >= maxJobsPerUser) {
            throw new IllegalStateException("Too many active export jobs. Please wait for existing jobs to complete.");
        }

        // Create export job for OwnTracks format with GPS data
        List<String> dataTypes = List.of(ExportImportConstants.DataTypes.RAW_GPS);
        ExportJob job = new ExportJob(userId, dataTypes, dateRange, "owntracks");
        activeJobs.put(job.getJobId(), job);

        log.info("Created OwnTracks export job {} for user {} with date range: {} to {}",
                job.getJobId(), userId, dateRange.getStartDate(), dateRange.getEndDate());

        return job;
    }

    public ExportJob createGeoJsonExportJob(UUID userId, org.github.tess1o.geopulse.export.model.ExportDateRange dateRange) {

        // Check if user has too many active jobs
        int maxJobsPerUser = settingsService.getInteger("export.max-jobs-per-user");
        long userActiveJobs = activeJobs.values().stream()
                .filter(job -> job.getUserId().equals(userId))
                .filter(job -> job.getStatus() != ExportStatus.FAILED)
                .count();

        if (userActiveJobs >= maxJobsPerUser) {
            throw new IllegalStateException("Too many active export jobs. Please wait for existing jobs to complete.");
        }

        // Create export job for GeoJSON format with GPS data
        List<String> dataTypes = List.of(ExportImportConstants.DataTypes.RAW_GPS);
        ExportJob job = new ExportJob(userId, dataTypes, dateRange, "geojson");
        activeJobs.put(job.getJobId(), job);

        log.info("Created GeoJSON export job {} for user {} with date range: {} to {}",
                job.getJobId(), userId, dateRange.getStartDate(), dateRange.getEndDate());

        return job;
    }

    public ExportJob createGpxExportJob(UUID userId, org.github.tess1o.geopulse.export.model.ExportDateRange dateRange,
                                        boolean zipPerTrip, String zipGroupBy) {

        // Check if user has too many active jobs
        int maxJobsPerUser = settingsService.getInteger("export.max-jobs-per-user");
        long userActiveJobs = activeJobs.values().stream()
                .filter(job -> job.getUserId().equals(userId))
                .filter(job -> job.getStatus() != ExportStatus.FAILED)
                .count();

        if (userActiveJobs >= maxJobsPerUser) {
            throw new IllegalStateException("Too many active export jobs. Please wait for existing jobs to complete.");
        }

        // Create export job for GPX format
        List<String> dataTypes = List.of(ExportImportConstants.DataTypes.RAW_GPS);
        java.util.Map<String, Object> options = new java.util.HashMap<>();
        options.put("zipPerTrip", zipPerTrip);
        options.put("zipGroupBy", zipGroupBy != null ? zipGroupBy : "individual");

        ExportJob job = new ExportJob(userId, dataTypes, dateRange, "gpx", options);
        activeJobs.put(job.getJobId(), job);

        log.info("Created GPX export job {} for user {} with date range: {} to {}, zipPerTrip={}, zipGroupBy={}",
                job.getJobId(), userId, dateRange.getStartDate(), dateRange.getEndDate(), zipPerTrip, zipGroupBy);

        return job;
    }

    public ExportJob createCsvExportJob(UUID userId, org.github.tess1o.geopulse.export.model.ExportDateRange dateRange) {

        // Check if user has too many active jobs
        int maxJobsPerUser = settingsService.getInteger("export.max-jobs-per-user");
        long userActiveJobs = activeJobs.values().stream()
                .filter(job -> job.getUserId().equals(userId))
                .filter(job -> job.getStatus() != ExportStatus.FAILED)
                .count();

        if (userActiveJobs >= maxJobsPerUser) {
            throw new IllegalStateException("Too many active export jobs. Please wait for existing jobs to complete.");
        }

        // Create export job for CSV format with GPS data
        List<String> dataTypes = List.of(ExportImportConstants.DataTypes.RAW_GPS);
        ExportJob job = new ExportJob(userId, dataTypes, dateRange, "csv");
        activeJobs.put(job.getJobId(), job);

        log.info("Created CSV export job {} for user {} with date range: {} to {}",
                job.getJobId(), userId, dateRange.getStartDate(), dateRange.getEndDate());

        return job;
    }

    public byte[] exportSingleTrip(UUID userId, Long tripId) throws Exception {
        return exportDataGenerator.generateSingleTripGpx(userId, tripId);
    }

    public byte[] exportSingleStay(UUID userId, Long stayId) throws Exception {
        return exportDataGenerator.generateSingleStayGpx(userId, stayId);
    }

    public ExportJob getExportJob(UUID jobId, UUID userId) {
        ExportJob job = activeJobs.get(jobId);
        if (job == null || !job.getUserId().equals(userId)) {
            return null;
        }
        return job;
    }

    public List<ExportJob> getUserExportJobs(UUID userId, int limit, int offset) {
        return activeJobs.values().stream()
                .filter(job -> job.getUserId().equals(userId))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
    }

    public boolean deleteExportJob(UUID jobId, UUID userId) {
        ExportJob job = activeJobs.get(jobId);
        if (job == null || !job.getUserId().equals(userId)) {
            return false;
        }

        // Clean up temp file if exists
        if (job.getTempFilePath() != null) {
            tempFileService.deleteTempFile(job.getTempFilePath());
        }

        activeJobs.remove(jobId);
        log.info("Deleted export job {} for user {}", jobId, userId);
        return true;
    }

    @Scheduled(every = "2s")
    public void processExportJobs() {
        if (processing) {
            return;
        }
        
        processing = true;
        try {
            processAvailableJobs();
            cleanupExpiredJobs();
        } finally {
            processing = false;
        }
    }

    private void processAvailableJobs() {
        int concurrentLimit = settingsService.getInteger("export.concurrent-jobs-limit");
        List<ExportJob> pendingJobs = activeJobs.values().stream()
                .filter(job -> job.getStatus() == ExportStatus.PROCESSING)
                .filter(job -> job.getTempFilePath() == null) // Not yet processed
                .limit(concurrentLimit)
                .collect(Collectors.toList());

        for (ExportJob job : pendingJobs) {
            try {
                log.debug("Processing export job {}", job.getJobId());

                byte[] data;
                String extension;
                String contentType;

                if ("owntracks".equals(job.getFormat())) {
                    // Generate JSON data for OwnTracks format
                    data = exportDataGenerator.generateOwnTracksExport(job);
                    extension = ".json";
                    contentType = "application/json";
                } else if ("geojson".equals(job.getFormat())) {
                    // Generate JSON data for GeoJSON format
                    data = exportDataGenerator.generateGeoJsonExport(job);
                    extension = ".geojson";
                    contentType = "application/geo+json";
                } else if ("gpx".equals(job.getFormat())) {
                    // Generate GPX data (either single file or zip)
                    boolean zipPerTrip = false;
                    String zipGroupBy = "individual"; // default

                    if (job.getOptions() != null) {
                        if (job.getOptions().containsKey("zipPerTrip")) {
                            zipPerTrip = Boolean.parseBoolean(job.getOptions().get("zipPerTrip").toString());
                        }
                        if (job.getOptions().containsKey("zipGroupBy")) {
                            zipGroupBy = job.getOptions().get("zipGroupBy").toString();
                        }
                    }

                    data = exportDataGenerator.generateGpxExport(job, zipPerTrip, zipGroupBy);

                    if (zipPerTrip) {
                        extension = ".zip";
                        contentType = "application/zip";
                    } else {
                        extension = ".gpx";
                        contentType = "application/gpx+xml";
                    }
                } else if ("csv".equals(job.getFormat())) {
                    // Generate CSV data
                    data = exportDataGenerator.generateCsvExport(job);
                    extension = ".csv";
                    contentType = "text/csv; charset=utf-8";
                } else {
                    // Generate ZIP data for GeoPulse format
                    data = exportDataGenerator.generateExportZip(job);
                    extension = ".zip";
                    contentType = "application/zip";
                }

                // Write data to temp file instead of storing in memory
                Path tempFile = tempFileService.createTempFile(job.getJobId(), extension);
                Files.write(tempFile, data);

                job.setTempFilePath(tempFile.toString());
                job.setContentType(contentType);
                job.setFileExtension(extension);
                job.setFileSizeBytes(Files.size(tempFile));

                job.setStatus(ExportStatus.COMPLETED);
                job.setCompletedAt(Instant.now());
                job.setProgress(100);

                log.info("Completed {} export job {} - {} bytes written to {}",
                        job.getFormat(), job.getJobId(), job.getFileSizeBytes(), tempFile.getFileName());

            } catch (Exception e) {
                log.error("Failed to process export job {}: {}", job.getJobId(), e.getMessage(), e);

                job.setStatus(ExportStatus.FAILED);
                job.setError(e.getMessage());
                job.setProgress(0);

                // Clean up temp file if it was created
                if (job.getTempFilePath() != null) {
                    tempFileService.deleteTempFile(job.getTempFilePath());
                    job.setTempFilePath(null);
                }
            }
        }
    }

    private void cleanupExpiredJobs() {
        int jobExpiryHours = settingsService.getInteger("export.job-expiry-hours");
        Instant cutoff = Instant.now().minus(jobExpiryHours, ChronoUnit.HOURS);

        List<ExportJob> expiredJobs = activeJobs.values().stream()
                .filter(job -> job.getCreatedAt().isBefore(cutoff))
                .collect(Collectors.toList());

        for (ExportJob job : expiredJobs) {
            // Clean up temp file if exists
            if (job.getTempFilePath() != null) {
                tempFileService.deleteTempFile(job.getTempFilePath());
            }
            activeJobs.remove(job.getJobId());
            log.debug("Cleaned up expired export job {}", job.getJobId());
        }

        if (!expiredJobs.isEmpty()) {
            log.info("Cleaned up {} expired export jobs", expiredJobs.size());
        }
    }

    public int getActiveJobCount() {
        return activeJobs.size();
    }

    public void clearAllJobs() {
        // Clean up temp files for all jobs
        for (ExportJob job : activeJobs.values()) {
            if (job.getTempFilePath() != null) {
                tempFileService.deleteTempFile(job.getTempFilePath());
            }
        }
        int count = activeJobs.size();
        activeJobs.clear();
        log.info("Cleared {} export jobs", count);
    }
}