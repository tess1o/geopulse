package org.github.tess1o.geopulse.importdata.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.model.ImportStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class ImportService {

    @Inject
    ImportDataService importDataService;

    private final ConcurrentHashMap<UUID, ImportJob> activeJobs = new ConcurrentHashMap<>();
    private volatile boolean processing = false;

    private static final int MAX_JOBS_PER_USER = 3;
    private static final long JOB_EXPIRY_HOURS = 24;

    public ImportJob createImportJob(UUID userId, ImportOptions options, String fileName, byte[] zipData) {
        // Check if user has too many active jobs
        long userActiveJobs = activeJobs.values().stream()
                .filter(job -> job.getUserId().equals(userId))
                .filter(job -> job.getStatus() != ImportStatus.FAILED && job.getStatus() != ImportStatus.COMPLETED)
                .count();

        if (userActiveJobs >= MAX_JOBS_PER_USER) {
            throw new IllegalStateException("Too many active import jobs. Please wait for existing jobs to complete.");
        }

        ImportJob job = new ImportJob(userId, options, fileName, zipData);
        activeJobs.put(job.getJobId(), job);
        
        log.info("Created import job {} for user {} with file: {}", 
                job.getJobId(), userId, fileName);
                
        return job;
    }

    public ImportJob createOwnTracksImportJob(UUID userId, ImportOptions options, String fileName, byte[] jsonData) {
        // Check if user has too many active jobs
        long userActiveJobs = activeJobs.values().stream()
                .filter(job -> job.getUserId().equals(userId))
                .filter(job -> job.getStatus() != ImportStatus.FAILED && job.getStatus() != ImportStatus.COMPLETED)
                .count();

        if (userActiveJobs >= MAX_JOBS_PER_USER) {
            throw new IllegalStateException("Too many active import jobs. Please wait for existing jobs to complete.");
        }

        // Create OwnTracks import job - we'll store JSON data in zipData field for simplicity
        ImportJob job = new ImportJob(userId, options, fileName, jsonData);
        
        // Pre-populate detected data types for OwnTracks (only GPS data)
        job.setDetectedDataTypes(List.of("rawgps"));
        
        activeJobs.put(job.getJobId(), job);
        
        log.info("Created OwnTracks import job {} for user {} with file: {}", 
                job.getJobId(), userId, fileName);
                
        return job;
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
    boolean schedulerEnabled;

    @Scheduled(every = "2s")
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
                .limit(1) // Process max 1 job at a time (imports can be resource intensive)
                .collect(Collectors.toList());

        for (ImportJob job : processingJobs) {
            try {
                log.debug("Processing import job {}", job.getJobId());
                
                importDataService.processImportData(job);
                
                job.setStatus(ImportStatus.COMPLETED);
                job.setCompletedAt(Instant.now());
                job.setProgress(100);
                
                log.info("Completed import job {}", job.getJobId());
                
            } catch (Exception e) {
                log.error("Failed to process import job {}: {}", job.getJobId(), e.getMessage(), e);
                
                job.setStatus(ImportStatus.FAILED);
                job.setError(e.getMessage());
                job.setProgress(0);
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

    public int getActiveJobCount() {
        return activeJobs.size();
    }

    public void clearAllJobs() {
        int count = activeJobs.size();
        activeJobs.clear();
        log.info("Cleared {} import jobs", count);
    }
}