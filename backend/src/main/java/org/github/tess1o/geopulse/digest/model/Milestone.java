package org.github.tess1o.geopulse.digest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Milestone {
    private String id;
    private String title;
    private String description;
    private String icon;
    private String tier; // bronze, silver, gold, diamond
    private String category; // distance, places, trips, activeDays, epicJourney
}
