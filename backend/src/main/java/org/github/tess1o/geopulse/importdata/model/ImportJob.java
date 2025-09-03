package org.github.tess1o.geopulse.importdata.model;

import lombok.Data;
import lombok.ToString;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
public class ImportJob {
    private UUID jobId;
    private UUID userId;
    private ImportStatus status;
    private String uploadedFileName;
    private long fileSizeBytes;
    private List<String> detectedDataTypes;
    private List<String> dataTypesToImport;
    private ImportOptions options;
    private String error;
    private int progress;
    private String progressMessage;
    private Instant createdAt;
    private Instant completedAt;
    private Instant estimatedProcessingTime;
    @ToString.Exclude
    private byte[] zipData;

    public ImportJob(UUID userId, ImportOptions options, String fileName, byte[] zipData) {
        this.jobId = UUID.randomUUID();
        this.userId = userId;
        this.options = options;
        this.uploadedFileName = fileName;
        this.zipData = zipData;
        this.fileSizeBytes = zipData.length;
        this.status = ImportStatus.VALIDATING;
        this.progress = 0;
        this.progressMessage = "Validating file format...";
        this.createdAt = Instant.now();
        this.estimatedProcessingTime = Instant.now().plusSeconds(120); // 2 minutes estimate
    }
    
    public void updateProgress(int progress, String message) {
        this.progress = progress;
        this.progressMessage = message;
    }
}