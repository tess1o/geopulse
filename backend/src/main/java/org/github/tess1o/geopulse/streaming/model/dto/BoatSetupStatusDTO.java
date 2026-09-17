package org.github.tess1o.geopulse.streaming.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import org.github.tess1o.geopulse.shared.api.MessageDescriptor;

import java.time.Instant;
import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BoatSetupStatusDTO(
        UUID jobId,
        Status status,
        DatasetStatus datasetStatus,
        EnvironmentStatus userEnvironmentStatus,
        Phase phase,
        int progressPercentage,
        Long downloadedBytes,
        Long totalBytes,
        Long processedGpsPoints,
        Long totalGpsPoints,
        ErrorCode errorCode,
        MessageDescriptor error,
        String docsUrl,
        String datasetVersion,
        Integer featureCount,
        Instant updatedAt
) {
    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_");
    }

    public enum Status {
        PENDING, QUEUED, RUNNING, READY, FAILED, UNKNOWN;

        public static Status from(String value) {
            if ("COMPLETED".equals(normalize(value))) return READY;
            try { return valueOf(normalize(value)); } catch (Exception ignored) { return UNKNOWN; }
        }
    }

    public enum DatasetStatus {
        NOT_IMPORTED, DOWNLOADING, IMPORTING, READY, FAILED, UNKNOWN;

        public static DatasetStatus from(String value) {
            try { return valueOf(normalize(value)); } catch (Exception ignored) { return UNKNOWN; }
        }
    }

    public enum EnvironmentStatus {
        PENDING, RUNNING, READY, FAILED, UNKNOWN;

        public static EnvironmentStatus from(String value) {
            try { return valueOf(normalize(value)); } catch (Exception ignored) { return UNKNOWN; }
        }
    }

    public enum Phase {
        BOAT_SETUP_IS_READY,
        GPS_WATER_EVIDENCE_NEEDS_ENRICHMENT,
        WATER_DATASET_IS_NOT_IMPORTED,
        STARTING_BOAT_SETUP,
        BOAT_SETUP_COMPLETED,
        RECLASSIFYING_EXISTING_TRIPS,
        WATER_DATASET_READY,
        PREPARING_WATER_DATASET_ARTIFACT,
        IMPORTING_WATER_POLYGONS,
        USING_LOCAL_WATER_DATASET_FILE,
        DOWNLOADING_WATER_DATASET,
        GPS_WATER_EVIDENCE_READY,
        CLASSIFYING_GPS_WATER_EVIDENCE,
        WAITING_FOR_WATER_DATASET_IMPORT,
        WAITING_FOR_GPS_WATER_EVIDENCE,
        BOAT_SETUP_WORKER_DID_NOT_START,
        WATER_DATASET_SETUP_FAILED,
        BOAT_SETUP_FAILED,
        UNKNOWN;

        public static Phase from(String value) {
            try { return valueOf(normalize(value)); } catch (Exception ignored) { return UNKNOWN; }
        }
    }

    public enum ErrorCode {
        IMPORT_FAILED, SETUP_FAILED, TRIP_RECLASSIFICATION_FAILED, SETUP_WORKER_STALE, UNKNOWN;

        public static ErrorCode from(String value) {
            if (value == null || value.isBlank()) return null;
            try { return valueOf(normalize(value)); } catch (Exception ignored) { return UNKNOWN; }
        }
    }
}
