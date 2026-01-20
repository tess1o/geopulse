package org.github.tess1o.geopulse.importdata.service;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.model.ImportStatus;
import org.github.tess1o.geopulse.insight.service.BadgeRecalculationService;
import org.github.tess1o.geopulse.streaming.service.TimelineJobProgressService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class ImportJobService {

    @Inject
    ImportDataService importDataService;

    @Inject
    BadgeRecalculationService badgeRecalculationService;

    @Inject
    TimelineJobProgressService timelineJobProgressService;

    private final ConcurrentHashMap<UUID, ImportJob> activeJobs = new ConcurrentHashMap<>();
    private volatile boolean processing = false;

    private static final long JOB_EXPIRY_HOURS = 24;

    public boolean hasActiveImportJob(UUID userId) {
        return activeJobs.values().stream()
                .anyMatch(job -> job.getUserId().equals(userId) &&
                        (job.getStatus() == ImportStatus.VALIDATING || job.getStatus() == ImportStatus.PROCESSING));
    }

    public ImportJob createImportJob(UUID userId, ImportOptions options, String fileName, byte[] zipData) {
        ImportJob job = new ImportJob(userId, options, fileName, zipData);
        activeJobs.put(job.getJobId(), job);

        log.info("Created import job {} for user {} with file: {}",
                job.getJobId(), userId, fileName);

        return job;
    }

    /**
     * Register an already-created import job.
     * Automatically sets detected data types for non-geopulse formats (they only support rawgps).
     */
    public void registerJob(ImportJob job) {
        String format = job.getOptions().getImportFormat();

        // Non-geopulse formats only support rawgps data type
        if (!"geopulse".equals(format) && job.getDetectedDataTypes() == null) {
            job.setDetectedDataTypes(List.of("rawgps"));
        }

        activeJobs.put(job.getJobId(), job);

        log.info("Registered import job {} for user {} with format {}",
                job.getJobId(), job.getUserId(), format);
    }

    public ImportJob getImportJob(UUID jobId, UUID userId) {
        ImportJob job = activeJobs.get(jobId);
        if (job == null || !job.getUserId().equals(userId)) {
            return null;
        }
        return job;
    }

    public List<ImportJob> getUserImportJobs(UUID userId, int limit, int offset) {
        return activeJobs.values().stream()
                .filter(job -> job.getUserId().equals(userId))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
    }

    public boolean deleteImportJob(UUID jobId, UUID userId) {
        ImportJob job = activeJobs.get(jobId);
        if (job == null || !job.getUserId().equals(userId)) {
            return false;
        }

        activeJobs.remove(jobId);
        log.info("Deleted import job {} for user {}", jobId, userId);
        return true;
    }

    @ConfigProperty(name = "geopulse.import.scheduler.enabled", defaultValue = "true")
    @StaticInitSafe
    boolean schedulerEnabled;

    @Scheduled(every = "2s")
    @RunOnVirtualThread
    public void processImportJobs() {
        if (!schedulerEnabled) {
            log.info("Import scheduler is disabled. Skipping scheduled import jobs.");
            return;
        }

        if (processing) {
            return;
        }

        processing = true;
        try {
            processAvailableJobs();
            monitorTimelineJobsForImports();
            cleanupExpiredJobs();
        } finally {
            processing = false;
        }
    }

    private void processAvailableJobs() {
        // First, validate pending jobs
        List<ImportJob> validatingJobs = activeJobs.values().stream()
                .filter(job -> job.getStatus() == ImportStatus.VALIDATING)
                .limit(2) // Process max 2 jobs concurrently
                .collect(Collectors.toList());

        for (ImportJob job : validatingJobs) {
            try {
                log.debug("Validating import job {}", job.getJobId());

                List<String> detectedDataTypes = importDataService.validateAndDetectDataTypes(job);

                job.setDetectedDataTypes(detectedDataTypes);
                job.setStatus(ImportStatus.PROCESSING);
                job.setProgress(25);

                log.info("Validated import job {} - detected data types: {}", job.getJobId(), detectedDataTypes);

            } catch (Exception e) {
                log.error("Failed to validate import job {}: {}", job.getJobId(), e.getMessage(), e);

                job.setStatus(ImportStatus.FAILED);
                job.setError(e.getMessage());
                job.setProgress(0);
            }
        }

        // Then, process validated jobs
        List<ImportJob> processingJobs = activeJobs.values().stream()
                .filter(job -> job.getStatus() == ImportStatus.PROCESSING)
                .filter(job -> job.getDetectedDataTypes() != null) // Only process validated jobs
                .filter(job -> !job.isDataProcessingCompleted()) // Don't reprocess jobs that already completed data import
                .limit(1) // Process max 1 job at a time (imports can be resource intensive)
                .collect(Collectors.toList());

        for (ImportJob job : processingJobs) {
            try {
                log.debug("Processing import job {}", job.getJobId());

                importDataService.processImportData(job);

                // Mark data processing as completed to prevent reprocessing
                job.setDataProcessingCompleted(true);
                log.debug("Marked import job {} as data processing completed", job.getJobId());

                // Check if this import has a timeline job (imports with GPS data)
                if (job.getTimelineJobId() != null) {
                    // Import data processing is complete, but timeline generation is still in progress
                    // Keep job in PROCESSING state - it will be completed when timeline job finishes
                    log.info("Import job {} data processing completed, waiting for timeline job {} to complete",
                            job.getJobId(), job.getTimelineJobId());
                } else {
                    // No timeline job - mark import as completed immediately
                    job.setStatus(ImportStatus.COMPLETED);
                    job.setCompletedAt(Instant.now());
                    job.setProgress(100);

                    log.info("Completed import job {} (no timeline generation)", job.getJobId());

                    // Trigger badge recalculation after successful import
                    try {
                        badgeRecalculationService.recalculateAllBadgesForUser(job.getUserId());
                        log.info("Triggered badge recalculation for user {} after import completion", job.getUserId());
                    } catch (Exception e) {
                        log.error("Failed to recalculate badges for user {} after import: {}",
                                job.getUserId(), e.getMessage(), e);
                        // Don't fail the import job if badge calculation fails
                    }
                }

            } catch (Exception e) {
                log.error("Failed to process import job {}: {}", job.getJobId(), e.getMessage(), e);

                // Mark as processed even on failure to prevent infinite retries
                job.setDataProcessingCompleted(true);
                job.setStatus(ImportStatus.FAILED);
                job.setError(e.getMessage());
                job.setProgress(0);
            }
        }
    }

    /**
     * Monitor timeline generation jobs associated with imports.
     * Updates import progress based on timeline job status and marks imports as completed/failed.
     */
    private void monitorTimelineJobsForImports() {
        // Find all imports that are waiting for timeline generation to complete
        List<ImportJob> jobsWithTimeline = activeJobs.values().stream()
                .filter(job -> job.getStatus() == ImportStatus.PROCESSING)
                .filter(job -> job.getTimelineJobId() != null)
                .filter(job -> job.getDetectedDataTypes() != null) // Only monitor validated imports
                .collect(Collectors.toList());

        for (ImportJob importJob : jobsWithTimeline) {
            try {
                // Check timeline job status
                java.util.Optional<org.github.tess1o.geopulse.streaming.model.TimelineJobProgress> timelineJobOpt =
                        timelineJobProgressService.getJobProgress(importJob.getTimelineJobId());

                if (timelineJobOpt.isEmpty()) {
                    log.warn("Timeline job {} not found for import {}", importJob.getTimelineJobId(), importJob.getJobId());
                    continue;
                }

                org.github.tess1o.geopulse.streaming.model.TimelineJobProgress timelineJob = timelineJobOpt.get();

                // Map timeline progress (0-100%) to import progress (75-100%)
                int importProgress = 75 + (timelineJob.getProgressPercentage() * 25 / 100);
                importJob.setProgress(Math.min(100, importProgress));

                // Update progress message based on timeline step
                String progressMessage = "Timeline generation: " + timelineJob.getCurrentStep();
                importJob.setProgressMessage(progressMessage);

                // Check if timeline job is completed
                if (timelineJob.getStatus() == org.github.tess1o.geopulse.streaming.model.TimelineJobProgress.JobStatus.COMPLETED) {
                    importJob.setStatus(ImportStatus.COMPLETED);
                    importJob.setCompletedAt(Instant.now());
                    importJob.setProgress(100);
                    importJob.setProgressMessage("Import completed successfully");

                    log.info("Import job {} completed after timeline job {} finished", importJob.getJobId(), importJob.getTimelineJobId());

                    // Note: Badge recalculation already done in TimelineImportHelper.finishTimelineJob()
                    // No need to do it again here
                }
                // Check if timeline job failed
                else if (timelineJob.getStatus() == org.github.tess1o.geopulse.streaming.model.TimelineJobProgress.JobStatus.FAILED) {
                    // GPS data was imported successfully, but timeline generation failed
                    // Mark import as failed but include helpful message
                    String errorMessage = "GPS data imported successfully, but timeline generation failed: " +
                            (timelineJob.getErrorMessage() != null ? timelineJob.getErrorMessage() : "Unknown error");

                    importJob.setStatus(ImportStatus.FAILED);
                    importJob.setError(errorMessage);
                    importJob.setCompletedAt(Instant.now());
                    importJob.setProgressMessage("Timeline generation failed");

                    log.warn("Import job {} marked as failed because timeline job {} failed: {}",
                            importJob.getJobId(), importJob.getTimelineJobId(), timelineJob.getErrorMessage());
                }

            } catch (Exception e) {
                log.error("Error monitoring timeline job for import {}: {}", importJob.getJobId(), e.getMessage(), e);
            }
        }
    }

    private void cleanupExpiredJobs() {
        Instant cutoff = Instant.now().minus(JOB_EXPIRY_HOURS, ChronoUnit.HOURS);

        List<UUID> expiredJobIds = activeJobs.values().stream()
                .filter(job -> job.getCreatedAt().isBefore(cutoff))
                .map(ImportJob::getJobId)
                .collect(Collectors.toList());

        for (UUID jobId : expiredJobIds) {
            activeJobs.remove(jobId);
            log.debug("Cleaned up expired import job {}", jobId);
        }

        if (!expiredJobIds.isEmpty()) {
            log.info("Cleaned up {} expired import jobs", expiredJobIds.size());
        }
    }
}