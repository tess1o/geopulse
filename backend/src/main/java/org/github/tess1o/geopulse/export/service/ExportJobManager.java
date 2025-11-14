package org.github.tess1o.geopulse.export.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.export.model.ExportStatus;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;

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

    private final ConcurrentHashMap<UUID, ExportJob> activeJobs = new ConcurrentHashMap<>();
    private volatile boolean processing = false;

    private static final int MAX_JOBS_PER_USER = 5;
    private static final long JOB_EXPIRY_HOURS = 24;

    public ExportJob createExportJob(UUID userId, List<String> dataTypes, 
                                   org.github.tess1o.geopulse.export.model.ExportDateRange dateRange, 
                                   String format) {
        
        // Check if user has too many active jobs
        long userActiveJobs = activeJobs.values().stream()
                .filter(job -> job.getUserId().equals(userId))
                .filter(job -> job.getStatus() != ExportStatus.FAILED)
                .count();

        if (userActiveJobs >= MAX_JOBS_PER_USER) {
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
        long userActiveJobs = activeJobs.values().stream()
                .filter(job -> job.getUserId().equals(userId))
                .filter(job -> job.getStatus() != ExportStatus.FAILED)
                .count();

        if (userActiveJobs >= MAX_JOBS_PER_USER) {
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
        long userActiveJobs = activeJobs.values().stream()
                .filter(job -> job.getUserId().equals(userId))
                .filter(job -> job.getStatus() != ExportStatus.FAILED)
                .count();

        if (userActiveJobs >= MAX_JOBS_PER_USER) {
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
        long userActiveJobs = activeJobs.values().stream()
                .filter(job -> job.getUserId().equals(userId))
                .filter(job -> job.getStatus() != ExportStatus.FAILED)
                .count();

        if (userActiveJobs >= MAX_JOBS_PER_USER) {
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
        long userActiveJobs = activeJobs.values().stream()
                .filter(job -> job.getUserId().equals(userId))
                .filter(job -> job.getStatus() != ExportStatus.FAILED)
                .count();

        if (userActiveJobs >= MAX_JOBS_PER_USER) {
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
        List<ExportJob> pendingJobs = activeJobs.values().stream()
                .filter(job -> job.getStatus() == ExportStatus.PROCESSING)
                .filter(job -> job.getZipData() == null && job.getJsonData() == null) // Not yet processed
                .limit(3) // Process max 3 jobs concurrently
                .collect(Collectors.toList());

        for (ExportJob job : pendingJobs) {
            try {
                log.debug("Processing export job {}", job.getJobId());

                if ("owntracks".equals(job.getFormat())) {
                    // Generate JSON data for OwnTracks format
                    byte[] jsonData = exportDataGenerator.generateOwnTracksExport(job);
                    job.setJsonData(jsonData);
                    job.setFileSizeBytes(jsonData.length);
                    log.info("Completed OwnTracks export job {} - {} bytes", job.getJobId(), jsonData.length);
                } else if ("geojson".equals(job.getFormat())) {
                    // Generate JSON data for GeoJSON format
                    byte[] jsonData = exportDataGenerator.generateGeoJsonExport(job);
                    job.setJsonData(jsonData);
                    job.setFileSizeBytes(jsonData.length);
                    log.info("Completed GeoJSON export job {} - {} bytes", job.getJobId(), jsonData.length);
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

                    byte[] gpxData = exportDataGenerator.generateGpxExport(job, zipPerTrip, zipGroupBy);

                    if (zipPerTrip) {
                        job.setZipData(gpxData);
                    } else {
                        job.setJsonData(gpxData); // Store GPX XML in jsonData field
                    }

                    job.setFileSizeBytes(gpxData.length);
                    log.info("Completed GPX export job {} - {} bytes, zipPerTrip={}, zipGroupBy={}",
                            job.getJobId(), gpxData.length, zipPerTrip, zipGroupBy);
                } else if ("csv".equals(job.getFormat())) {
                    // Generate CSV data
                    byte[] csvData = exportDataGenerator.generateCsvExport(job);
                    job.setJsonData(csvData); // Store CSV in jsonData field
                    job.setFileSizeBytes(csvData.length);
                    log.info("Completed CSV export job {} - {} bytes", job.getJobId(), csvData.length);
                } else {
                    // Generate ZIP data for GeoPulse format
                    byte[] zipData = exportDataGenerator.generateExportZip(job);
                    job.setZipData(zipData);
                    job.setFileSizeBytes(zipData.length);
                    log.info("Completed GeoPulse export job {} - {} bytes", job.getJobId(), zipData.length);
                }

                job.setStatus(ExportStatus.COMPLETED);
                job.setCompletedAt(Instant.now());
                job.setProgress(100);

            } catch (Exception e) {
                log.error("Failed to process export job {}: {}", job.getJobId(), e.getMessage(), e);

                job.setStatus(ExportStatus.FAILED);
                job.setError(e.getMessage());
                job.setProgress(0);
            }
        }
    }

    private void cleanupExpiredJobs() {
        Instant cutoff = Instant.now().minus(JOB_EXPIRY_HOURS, ChronoUnit.HOURS);
        
        List<UUID> expiredJobIds = activeJobs.values().stream()
                .filter(job -> job.getCreatedAt().isBefore(cutoff))
                .map(ExportJob::getJobId)
                .collect(Collectors.toList());

        for (UUID jobId : expiredJobIds) {
            activeJobs.remove(jobId);
            log.debug("Cleaned up expired export job {}", jobId);
        }
        
        if (!expiredJobIds.isEmpty()) {
            log.info("Cleaned up {} expired export jobs", expiredJobIds.size());
        }
    }

    public int getActiveJobCount() {
        return activeJobs.size();
    }

    public void clearAllJobs() {
        int count = activeJobs.size();
        activeJobs.clear();
        log.info("Cleared {} export jobs", count);
    }
}