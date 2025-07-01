package org.github.tess1o.geopulse.export.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExportMetadataDto {
    private UUID exportJobId;
    private UUID userId;
    private Instant exportDate;
    private List<String> dataTypes;
    private Instant startDate;
    private Instant endDate;
    private String format;
    private String version;
}