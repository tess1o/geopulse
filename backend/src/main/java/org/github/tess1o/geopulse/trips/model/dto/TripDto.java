package org.github.tess1o.geopulse.trips.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.trips.model.entity.TripStatus;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripDto {
    private Long id;
    private UUID userId;
    private Long periodTagId;
    private String name;
    private Instant startTime;
    private Instant endTime;
    private TripStatus status;
    private String color;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}

