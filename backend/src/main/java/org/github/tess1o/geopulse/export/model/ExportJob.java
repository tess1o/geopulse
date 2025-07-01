package org.github.tess1o.geopulse.export.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ExportJob {
    private UUID jobId;
    private UUID userId;
    private ExportStatus status;
    private int progress;
    private List<String> dataTypes;
    private ExportDateRange dateRange;
    private String format;
    private Instant createdAt;
    private Instant completedAt;
    private byte[] zipData;
    private byte[] jsonData;
    private String error;
    private long fileSizeBytes;

    public ExportJob() {
        this.jobId = UUID.randomUUID();
        this.status = ExportStatus.PROCESSING;
        this.progress = 0;
        this.createdAt = Instant.now();
    }

    public ExportJob(UUID userId, List<String> dataTypes, ExportDateRange dateRange, String format) {
        this();
        this.userId = userId;
        this.dataTypes = dataTypes;
        this.dateRange = dateRange;
        this.format = format;
    }
}