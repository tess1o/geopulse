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

    // Timestamps from data (captured during validation for use in clear mode)
    private Instant dataFirstTimestamp;
    private Instant dataLastTimestamp;

    // Temporary file path for large files (memory optimization)
    // If set, use this instead of zipData
    private String tempFilePath;

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

    /**
     * Get the import data as an InputStream, abstracting whether it's from memory or file.
     * This allows transparent handling of both small (in-memory) and large (file-based) imports.
     */
    public java.io.InputStream getDataStream() throws java.io.IOException {
        if (tempFilePath != null) {
            // Large file mode: stream from disk
            return java.nio.file.Files.newInputStream(java.nio.file.Paths.get(tempFilePath));
        } else if (zipData != null) {
            // Small file mode: stream from memory
            return new java.io.ByteArrayInputStream(zipData);
        } else {
            throw new IllegalStateException("ImportJob has neither tempFilePath nor zipData");
        }
    }

    /**
     * Check if this job uses a temporary file (vs in-memory data)
     */
    public boolean hasTempFile() {
        return tempFilePath != null;
    }
}