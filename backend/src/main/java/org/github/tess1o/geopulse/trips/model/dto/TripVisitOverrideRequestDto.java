package org.github.tess1o.geopulse.trips.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripVisitOverrideRequestDto {
    private String action; // CONFIRM_VISITED, REJECT_VISIT, RESET_TO_AUTO
    private Instant visitedAt;
}

