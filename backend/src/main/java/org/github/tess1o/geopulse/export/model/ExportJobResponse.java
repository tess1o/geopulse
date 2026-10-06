package org.github.tess1o.geopulse.export.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.github.tess1o.geopulse.shared.api.MessageDescriptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExportJobResponse {
    private UUID exportJobId;
    private ExportFormat format;
    private String status;
    private Integer progress;
    private MessageDescriptor progressMessage;
    private Instant createdAt;
    private Instant completedAt;
    private String downloadUrl;
    private Instant expiresAt;
    private List<String> dataTypes;
    private Instant startTime;
    private Instant endTime;
    private Long fileSizeBytes;
    private MessageDescriptor error;
}
