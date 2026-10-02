package org.github.tess1o.geopulse.timelinelabels.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TimelineLabelDto {
    private Long id;
    private UUID userId;
    private String name;
    private Instant startTime;
    private Instant endTime;
    private String source;
    private Boolean isActive;
    private String color;
    private Boolean showAsPreset;
    private Instant createdAt;
    private Instant updatedAt;
}
