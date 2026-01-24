package org.github.tess1o.geopulse.export.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.export.model.ExportDateRange;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.export.model.ExportStatus;

import java.io.IOException;
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

    public ExportJob createExportJob(UUID userId, List<String> dataTypes, ExportDateRange dateRange, String format) {
        return createExportJob(userId, dataTypes, dateRange, format, null);
    }

    public ExportJob createExportJob(UUID userId, List<String> dataTypes, ExportDateRange dateRange, String format, Map<String, Object> options) {
        // Validate active jobs limit
        validateJobLimit(userId);

        ExportJob job = new ExportJob(userId, dataTypes, dateRange, format, options);
        activeJobs.put(job.getJobId(), job);

        log.info("Created {} export job {} for user {} with date range: {} to {}",
                format, job.getJobId(), userId, dateRange.getStartDate(), dateRange.getEndDate());

        return job;
    }

    private void validateJobLimit(UUID userId) {
        int maxJobsPerUser = settingsService.getInteger("export.max-jobs-per-user");
        long userActiveJobs = activeJobs.values().stream()
                .filter(job -> job.getUserId().equals(userId))
                .filter(job -> job.getStatus() != ExportStatus.FAILED)
                .count();

        if (userActiveJobs >= maxJobsPerUser) {
            throw new IllegalStateException("Too many active export jobs. Please wait for existing jobs to complete.");
        }
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

    private final Map<String, ExportJobProcessor> exportStrategies = new HashMap<>();

    @jakarta.annotation.PostConstruct
    public void init() {
        exportStrategies.put("owntracks", job -> exportDataGenerator.generateOwnTracksExport(job));
        exportStrategies.put("geojson", job -> exportDataGenerator.generateGeoJsonExport(job));
        exportStrategies.put("csv", job -> exportDataGenerator.generateCsvExport(job));
        exportStrategies.put("gpx", this::generateGpx);
        exportStrategies.put("geopulse", job -> exportDataGenerator.generateGeoPulseNativeExport(job));
    }

    private void generateGpx(ExportJob job) throws IOException {
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
        exportDataGenerator.generateGpxExport(job, zipPerTrip, zipGroupBy);
    }

    @FunctionalInterface
    interface ExportJobProcessor {
        void process(ExportJob job) throws Exception;
    }

    // ... existing methods ...

    @Scheduled(every = "{geopulse.export.scheduler-interval}")
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
                log.debug("Processing export job {} with format {}", job.getJobId(), job.getFormat());

                // Get format-specific processor - fail fast if unsupported
                ExportJobProcessor processor = exportStrategies.get(job.getFormat());
                if (processor == null) {
                    throw new IllegalArgumentException(
                            String.format("Unsupported export format: '%s'. Supported formats: %s",
                                    job.getFormat(),
                                    String.join(", ", exportStrategies.keySet()))
                    );
                }

                processor.process(job);

                // Status and progress are updated by the generator services,
                // but we finalize it here to ensure consistency
                job.setStatus(ExportStatus.COMPLETED);
                job.setCompletedAt(Instant.now());
                job.setProgress(100);

                log.info("Completed {} export job {} - {} bytes written to {}",
                        job.getFormat(), job.getJobId(), job.getFileSizeBytes(), job.getTempFilePath());

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
}