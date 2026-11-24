package org.github.tess1o.geopulse.streaming.service;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.shared.api.MessageDescriptor;
import org.github.tess1o.geopulse.streaming.model.TimelineJobMemory;
import org.github.tess1o.geopulse.streaming.model.TimelineJobProgress;
import org.github.tess1o.geopulse.streaming.model.TimelineJobStep;
import org.github.tess1o.geopulse.streaming.model.TimelineJobType;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineJobStatistics;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service for managing timeline generation job progress tracking.
 * Jobs are stored in memory and kept for {@link #JOB_RETENTION_PERIOD} after they finish.
 * <p>
 * Each job also keeps the steps it went through ({@link TimelineJobStep}): progress messages are grouped into steps
 * by {@link #phaseOf}, so a step with many messages (geocoding reports every external call) is one step. While a job
 * runs, a sampler records the highest memory of its current step about once a second.
 */
@ApplicationScoped
@Slf4j
public class TimelineJobProgressService {

    private static final int TOTAL_STEPS = 9;
    private static final int MAX_JOBS_IN_MEMORY = 1000;
    private static final Duration JOB_RETENTION_PERIOD = Duration.ofHours(24);

    /**
     * Frontend catalog namespace for {@link #step(String, String, Map)}. Keys live under
     * {@code timelineJobs.js}'s {@code progressMessages} block.
     */
    private static final String STEP_KEY_PREFIX = "timelineJobs.progressMessages.";

    /** Frontend catalog namespace of step titles; the fallbacks below are their English text. */
    private static final String PHASE_KEY_PREFIX = "timelineJobs.steps.";

    /** Progress message keys (without {@link #STEP_KEY_PREFIX}) by the step they belong to. */
    private static final Map<String, String> PHASE_BY_MESSAGE = Map.ofEntries(
            Map.entry("initializing", "queued"),
            Map.entry("regenerationQueued", "queued"),
            Map.entry("acquiringLock", "preparing"),
            Map.entry("cleaningUp", "preparing"),
            Map.entry("preparingGpsProcessing", "preparing"),
            Map.entry("preparingBoatSetup", "preparing"),
            Map.entry("boatEvidencePhase", "preparing"),
            Map.entry("boatEvidenceReady", "preparing"),
            Map.entry("readyToProcess", "preparing"),
            Map.entry("processingStateMachine", "processingPoints"),
            Map.entry("correctionWindow", "correctionWindow"),
            Map.entry("startingLocationResolution", "geocoding"),
            Map.entry("resolvedFromFavorites", "geocoding"),
            Map.entry("resolvedFromCache", "geocoding"),
            Map.entry("geocodingLocationProgress", "geocoding"),
            Map.entry("geocodingComplete", "geocoding"),
            Map.entry("allResolvedFromFavorites", "geocoding"),
            Map.entry("postProcessingTrips", "postProcessingTrips"),
            Map.entry("mergingTimeline", "merging"),
            Map.entry("applyingCorrections", "corrections"),
            Map.entry("persistingTimeline", "persisting"),
            Map.entry("finalizing", "finalizing"),
            Map.entry("autoMatchingVisits", "finalizing"),
            Map.entry("recalculatingBadges", "badges"));

    /** English titles of the steps. */
    private static final Map<String, String> PHASE_TITLES = Map.ofEntries(
            Map.entry("queued", "Waiting to start"),
            Map.entry("preparing", "Preparing"),
            Map.entry("processingPoints", "Processing GPS points"),
            Map.entry("correctionWindow", "Rebuilding the corrected period"),
            Map.entry("geocoding", "Naming stays"),
            Map.entry("postProcessingTrips", "Post-processing trips"),
            Map.entry("merging", "Merging stays"),
            Map.entry("corrections", "Applying corrections"),
            Map.entry("persisting", "Saving the timeline"),
            Map.entry("finalizing", "Finalizing"),
            Map.entry("badges", "Recalculating badges"));

    /** The steps a regeneration goes through; merging only when enabled. */
    private static final List<String> REGENERATION_STEPS = List.of("preparing", "processingPoints", "geocoding",
            "postProcessingTrips", "merging", "corrections", "persisting", "finalizing");

    /** Imports and campaigns also recalculate the user's badges before they finish. */
    private static final List<String> REGENERATION_WITH_BADGES_STEPS = java.util.stream.Stream
            .concat(REGENERATION_STEPS.stream(), java.util.stream.Stream.of("badges")).toList();

    /** The last message of a job: it ends the current step rather than starting one. */
    private static final String COMPLETED_MESSAGE = "completed";

    /** Interval of the memory sampler while a job runs. */
    private static final long SAMPLE_INTERVAL_MS = 1000;

    /**
     * Build a translatable step descriptor. {@code key} is a short suffix (e.g. {@code "acquiringLock"})
     * appended to {@link #STEP_KEY_PREFIX}; {@code fallback} is the English text shown when the active
     * locale has no translation for the key yet; {@code params} are the interpolation values (may be
     * {@code null} for a step with no placeholders).
     */
    public static MessageDescriptor step(String key, String fallback, Map<String, Object> params) {
        return new MessageDescriptor(STEP_KEY_PREFIX + key, params, fallback);
    }

    /**
     * In-memory storage of job progress indexed by job ID
     */
    private final ConcurrentHashMap<UUID, TimelineJobProgress> jobStore = new ConcurrentHashMap<>();

    /**
     * Index of active jobs by user ID for quick lookup
     */
    private final ConcurrentHashMap<UUID, UUID> userActiveJobIndex = new ConcurrentHashMap<>();

    /**
     * Create a new timeline generation job for a user
     *
     * @param userId The user ID
     * @return The job ID
     */
    public UUID createJob(UUID userId) {
        return createJob(userId, TimelineJobType.OTHER);
    }

    /**
     * Create a new timeline generation job for a user
     *
     * @param userId The user ID
     * @param type   What started the job
     * @return The job ID
     */
    public UUID createJob(UUID userId, TimelineJobType type) {
        UUID jobId = UUID.randomUUID();

        TimelineJobProgress job = TimelineJobProgress.builder()
                .jobId(jobId)
                .userId(userId)
                .type(type)
                .plannedSteps(plannedSteps(type))
                .resources(ProcessMemory.resources())
                .status(TimelineJobProgress.JobStatus.QUEUED)
                .currentStep(step("initializing", "Initializing timeline generation", null))
                .currentStepIndex(0)
                .totalSteps(TOTAL_STEPS)
                .progressPercentage(0)
                .startTime(Instant.now())
                .details(new HashMap<>())
                .build();

        jobStore.put(jobId, job);
        userActiveJobIndex.put(userId, jobId);

        log.info("Created timeline generation job {} for user {}", jobId, userId);

        // Enforce memory limit
        enforceMemoryLimit();

        return jobId;
    }

    /**
     * Update the progress of a job
     *
     * @param jobId The job ID
     * @param step Translatable description of the current step
     * @param stepIndex Step index (1-9)
     * @param percentage Progress percentage (0-100)
     * @param details Additional step-specific details
     */
    public void updateProgress(UUID jobId, MessageDescriptor step, int stepIndex, int percentage, Map<String, Object> details) {
        TimelineJobProgress job = jobStore.get(jobId);
        if (job == null) {
            log.warn("Attempted to update non-existent job {}", jobId);
            return;
        }

        int boundedPercentage = Math.clamp(percentage, 0, 100);

        trackStep(job, step, details);
        boolean starting = job.getStatus() != TimelineJobProgress.JobStatus.RUNNING;
        job.setStatus(TimelineJobProgress.JobStatus.RUNNING);
        if (starting) {
            // After the status change, so a sampler that is about to stop sees this job (see stopSampler)
            startSampler();
        }
        job.setCurrentStep(step);
        job.setCurrentStepIndex(stepIndex);
        job.setProgressPercentage(Math.max(job.getProgressPercentage(), boundedPercentage));

        if (details != null) {
            job.getDetails().putAll(details);
        }

        log.debug("Job {} progress: {}% - {}", jobId, percentage, step.fallback());
    }

    /**
     * Mark a job as completed successfully
     *
     * @param jobId The job ID
     */
    public void completeJob(UUID jobId) {
        TimelineJobProgress job = jobStore.get(jobId);
        if (job == null) {
            log.warn("Attempted to complete non-existent job {}", jobId);
            return;
        }

        finishSteps(job, false);
        job.setStatus(TimelineJobProgress.JobStatus.COMPLETED);
        job.setCurrentStep(step("completed", "Timeline generation completed", null));
        job.setProgressPercentage(100);
        job.setEndTime(Instant.now());

        // Remove from active job index
        userActiveJobIndex.remove(job.getUserId());

        log.info("Job {} completed successfully in {}ms", jobId, job.getDurationMs());
    }

    /**
     * Mark a job as failed with an error message
     *
     * @param jobId The job ID
     * @param errorMessage The error message
     */
    public void failJob(UUID jobId, String errorMessage) {
        TimelineJobProgress job = jobStore.get(jobId);
        if (job == null) {
            log.warn("Attempted to fail non-existent job {}", jobId);
            return;
        }

        finishSteps(job, true);
        job.setStatus(TimelineJobProgress.JobStatus.FAILED);
        job.setErrorMessage(errorMessage);
        job.setEndTime(Instant.now());

        // Remove from active job index
        userActiveJobIndex.remove(job.getUserId());

        log.error("Job {} failed after {}ms: {}", jobId, job.getDurationMs(), errorMessage);
    }

    /**
     * Records what a job worked on and produced, for its page and diagnostics (see
     * {@link TimelineJobProgress#getSummary()}). Ignored for a null or unknown job.
     */
    public void recordSummary(UUID jobId, Map<String, Object> values) {
        if (jobId == null) {
            return;
        }
        TimelineJobProgress job = jobStore.get(jobId);
        if (job == null) {
            return;
        }
        values.forEach((key, value) -> {
            if (key != null && value != null) {
                job.getSummary().put(key, value);
            }
        });
    }

    /** The step a progress message belongs to: its own key when it is not one of the known messages. */
    static String phaseOf(MessageDescriptor step) {
        String key = step == null || step.key() == null ? "" : step.key();
        String message = key.startsWith(STEP_KEY_PREFIX) ? key.substring(STEP_KEY_PREFIX.length()) : key;
        return PHASE_BY_MESSAGE.getOrDefault(message, message);
    }

    static List<String> plannedSteps(TimelineJobType type) {
        return switch (type) {
            case FULL_REBUILD, PARTIAL_REBUILD -> REGENERATION_STEPS;
            case IMPORT, CAMPAIGN -> REGENERATION_WITH_BADGES_STEPS;
            // A correction rebuilds only the corrected period, or falls back to a regeneration
            case CORRECTION -> List.of("correctionWindow");
            case OTHER -> List.of();
        };
    }

    /** Opens a new step when the message belongs to another step than the current one, and adds its counters. */
    private void trackStep(TimelineJobProgress job, MessageDescriptor message, Map<String, Object> details) {
        String phase = phaseOf(message);
        List<TimelineJobStep> steps = job.getSteps();
        TimelineJobStep current = steps.isEmpty() ? null : steps.getLast();
        if (COMPLETED_MESSAGE.equals(phase)) {
            if (current != null) {
                current.end(Instant.now(), ProcessMemory.sample(), false);
            }
            return;
        }
        if (current == null || current.getEndedAt() != null || !current.getPhase().equals(phase)) {
            Instant now = Instant.now();
            TimelineJobMemory memory = ProcessMemory.sample();
            if (current != null) {
                current.end(now, memory, false);
            }
            String fallback = PHASE_TITLES.getOrDefault(phase, message == null ? phase : message.fallback());
            current = new TimelineJobStep(phase, new MessageDescriptor(PHASE_KEY_PREFIX + phase, null, fallback), now,
                    memory);
            steps.add(current);
        }
        if (details != null) {
            current.addCounters(details);
        }
    }

    /** Ends the job's current step and records the kernel memory peaks at the end of the job. */
    private void finishSteps(TimelineJobProgress job, boolean failed) {
        List<TimelineJobStep> steps = job.getSteps();
        if (!steps.isEmpty()) {
            steps.getLast().end(Instant.now(), ProcessMemory.sample(), failed);
        }
        if (job.getResources() != null) {
            job.setResources(job.getResources().withEndPeaks(ProcessMemory.processPeak(), ProcessMemory.containerPeak()));
        }
    }

    private ScheduledExecutorService sampler;

    /** Starts the memory sampler unless it runs; it stops itself when no job is running. */
    private synchronized void startSampler() {
        if (sampler != null) {
            return;
        }
        sampler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "timeline-job-memory");
            thread.setDaemon(true);
            return thread;
        });
        sampler.scheduleWithFixedDelay(this::sampleRunningJobs, SAMPLE_INTERVAL_MS, SAMPLE_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
    }

    /** Adds one memory reading to the current step of every running job. */
    void sampleRunningJobs() {
        try {
            List<TimelineJobProgress> running = jobStore.values().stream()
                    .filter(job -> job.getStatus() == TimelineJobProgress.JobStatus.RUNNING)
                    .toList();
            if (running.isEmpty()) {
                stopSampler();
                return;
            }
            TimelineJobMemory memory = ProcessMemory.sample();
            for (TimelineJobProgress job : running) {
                List<TimelineJobStep> steps = job.getSteps();
                if (!steps.isEmpty()) {
                    steps.getLast().sample(memory);
                }
            }
        } catch (RuntimeException e) {
            // A failed reading must never stop the sampler or touch the job
            log.debug("Could not sample memory for running timeline jobs", e);
        }
    }

    /** Stops the sampler unless a job started running since it last looked. */
    private synchronized void stopSampler() {
        boolean running = jobStore.values().stream()
                .anyMatch(job -> job.getStatus() == TimelineJobProgress.JobStatus.RUNNING);
        if (sampler != null && !running) {
            sampler.shutdown();
            sampler = null;
        }
    }

    @PreDestroy
    synchronized void shutdown() {
        if (sampler != null) {
            sampler.shutdownNow();
            sampler = null;
        }
    }

    /**
     * Get the progress of a specific job
     *
     * @param jobId The job ID
     * @return The job progress, or empty if not found
     */
    public Optional<TimelineJobProgress> getJobProgress(UUID jobId) {
        return Optional.ofNullable(jobStore.get(jobId));
    }

    /**
     * Get the active (queued or running) job for a user, if any
     *
     * @param userId The user ID
     * @return The active job progress, or empty if no active job exists
     */
    public Optional<TimelineJobProgress> getUserActiveJob(UUID userId) {
        UUID jobId = userActiveJobIndex.get(userId);
        if (jobId == null) {
            return Optional.empty();
        }

        TimelineJobProgress job = jobStore.get(jobId);
        if (job == null || !job.isActive()) {
            // Clean up stale index entry
            userActiveJobIndex.remove(userId);
            return Optional.empty();
        }

        return Optional.of(job);
    }

    /**
     * Get historical (completed or failed) jobs for a user, sorted by start time descending
     *
     * @param userId The user ID
     * @return List of historical jobs (completed or failed), sorted by most recent first
     */
    public List<TimelineJobProgress> getUserHistoryJobs(UUID userId) {
        return jobStore.values().stream()
                .filter(job -> job.getUserId().equals(userId))
                .filter(TimelineJobProgress::isTerminal) // Only completed or failed jobs
                .sorted(Comparator.comparing(TimelineJobProgress::getStartTime).reversed()) // Most recent first
                .collect(Collectors.toList());
    }

    /**
     * Clean up old completed jobs to prevent memory leaks.
     * Called by the cleanup scheduler.
     *
     * @return The number of jobs removed
     */
    public int cleanupOldJobs() {
        Instant cutoffTime = Instant.now().minus(JOB_RETENTION_PERIOD);

        List<UUID> jobsToRemove = jobStore.values().stream()
                .filter(TimelineJobProgress::isTerminal)
                .filter(job -> job.getEndTime() != null && job.getEndTime().isBefore(cutoffTime))
                .map(TimelineJobProgress::getJobId)
                .collect(Collectors.toList());

        for (UUID jobId : jobsToRemove) {
            jobStore.remove(jobId);
        }

        if (!jobsToRemove.isEmpty()) {
            log.info("Cleaned up {} old timeline generation jobs", jobsToRemove.size());
        }

        return jobsToRemove.size();
    }

    /**
     * Get statistics about jobs in memory
     *
     * @return Map with statistics
     */
    public TimelineJobStatistics getStatistics() {
        long total = jobStore.size();
        long queued = jobStore.values().stream().filter(j -> j.getStatus() == TimelineJobProgress.JobStatus.QUEUED).count();
        long running = jobStore.values().stream().filter(j -> j.getStatus() == TimelineJobProgress.JobStatus.RUNNING).count();
        long completed = jobStore.values().stream().filter(j -> j.getStatus() == TimelineJobProgress.JobStatus.COMPLETED).count();
        long failed = jobStore.values().stream().filter(j -> j.getStatus() == TimelineJobProgress.JobStatus.FAILED).count();

        return new TimelineJobStatistics(total, queued, running, completed, failed, userActiveJobIndex.size());
    }

    /**
     * Enforce memory limit by removing oldest completed jobs if we exceed the limit
     */
    private void enforceMemoryLimit() {
        if (jobStore.size() <= MAX_JOBS_IN_MEMORY) {
            return;
        }

        // Remove oldest completed jobs first
        List<UUID> completedJobIds = jobStore.values().stream()
                .filter(TimelineJobProgress::isTerminal)
                .sorted(Comparator.comparing(TimelineJobProgress::getEndTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(TimelineJobProgress::getJobId)
                .limit(jobStore.size() - MAX_JOBS_IN_MEMORY)
                .collect(Collectors.toList());

        for (UUID jobId : completedJobIds) {
            jobStore.remove(jobId);
        }

        log.warn("Enforced memory limit: removed {} old jobs. Current size: {}", completedJobIds.size(), jobStore.size());
    }
}
