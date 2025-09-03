package org.github.tess1o.geopulse.importdata.model;

import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
public class ImportJobResponse {
    private boolean success;
    private UUID importJobId;
    private String status;
    private String uploadedFileName;
    private long fileSizeBytes;
    private List<String> detectedDataTypes;
    private Instant estimatedProcessingTime;
    private String message;
    private String error;
    private int progress;
    private String progressMessage;
    private Instant createdAt;
    private Instant completedAt;
}