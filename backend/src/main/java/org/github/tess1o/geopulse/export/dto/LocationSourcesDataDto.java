package org.github.tess1o.geopulse.export.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LocationSourcesDataDto {
    private String dataType;
    private Instant exportDate;
    private List<SourceDto> sources;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class SourceDto {
        private UUID id;
        private String type;
        private String username;
        private boolean active;
    }
}