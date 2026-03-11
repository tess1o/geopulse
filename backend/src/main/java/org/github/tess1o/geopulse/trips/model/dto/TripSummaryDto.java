package org.github.tess1o.geopulse.trips.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.trips.model.entity.TripStatus;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripSummaryDto {
    private Long tripId;
    private String tripName;
    private TripStatus status;
    private Instant startTime;
    private Instant endTime;

    private int planItemsTotal;
    private int planItemsVisited;
    private int planItemsVisitedAuto;
    private int planItemsVisitedManual;
    private double planCompletionRate;

    private long timelineStays;
    private long timelineTrips;
    private long timelineDataGaps;
    private long totalDistanceMeters;
    private long totalTripDurationSeconds;
    private Map<String, Long> movementTypeCounts;

    private int actualPlacesCount;
    private int actualCitiesCount;
    private int actualCountriesCount;
}

