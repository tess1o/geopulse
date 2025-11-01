package org.github.tess1o.geopulse.export.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExportJobResponse {
    private boolean success;
    private UUID exportJobId;
    private String status;
    private Integer progress;
    private String progressMessage;
    private Instant createdAt;
    private Instant completedAt;
    private String downloadUrl;
    private Instant expiresAt;
    private List<String> dataTypes;
    private ExportDateRange dateRange;
    private Long fileSizeBytes;
    private String error;
    private String message;
    private Instant estimatedCompletionTime;
}