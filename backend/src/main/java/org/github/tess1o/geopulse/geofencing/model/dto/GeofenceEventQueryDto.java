package org.github.tess1o.geopulse.geofencing.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeofenceEventQueryDto {
    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int pageSize = 25;

    @Builder.Default
    private String sortBy = "occurredAt";

    @Builder.Default
    private String sortDir = "desc";

    @Builder.Default
    private boolean unreadOnly = false;

    private Instant dateFrom;
    private Instant dateTo;

    @Builder.Default
    private List<UUID> subjectUserIds = List.of();

    @Builder.Default
    private List<GeofenceEventType> eventTypes = List.of();
}
