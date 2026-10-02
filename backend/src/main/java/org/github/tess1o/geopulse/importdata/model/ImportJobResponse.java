package org.github.tess1o.geopulse.importdata.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.github.tess1o.geopulse.shared.api.MessageDescriptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImportJobResponse(
        UUID importJobId,
        ImportStatus status,
        ImportPhase phase,
        String uploadedFileName,
        long fileSizeBytes,
        List<String> detectedDataTypes,
        Instant estimatedProcessingTime,
        MessageDescriptor progressMessage,
        MessageDescriptor error,
        int progress,
        Instant createdAt,
        Instant completedAt,
        UUID timelineJobId
) {
    public static ImportJobResponse from(ImportJob job) {
        ImportPhase phase = phase(job);
        return new ImportJobResponse(
                job.getJobId(),
                job.getStatus(),
                phase,
                job.getUploadedFileName(),
                job.getFileSizeBytes(),
                job.getDetectedDataTypes(),
                job.getEstimatedProcessingTime(),
                descriptor("imports.phase." + phase.value(), job.getProgressMessage(), job.getProgress()),
                descriptor("imports.failed", job.getError(), job.getProgress()),
                job.getProgress(),
                job.getCreatedAt(),
                job.getCompletedAt(),
                job.getTimelineJobId());
    }

    private static ImportPhase phase(ImportJob job) {
        if (job.getStatus() == ImportStatus.COMPLETED) return ImportPhase.COMPLETED;
        if (job.getStatus() == ImportStatus.FAILED) return ImportPhase.FAILED;
        if (job.getStatus() == ImportStatus.VALIDATING) return ImportPhase.VALIDATING;
        return job.getPhase() == null ? ImportPhase.IMPORTING : job.getPhase();
    }

    private static MessageDescriptor descriptor(String key, String fallback, int progress) {
        return fallback == null || fallback.isBlank()
                ? null
                : new MessageDescriptor(key, Map.of("progress", progress), fallback);
    }
}
