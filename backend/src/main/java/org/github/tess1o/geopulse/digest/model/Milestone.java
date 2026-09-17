package org.github.tess1o.geopulse.digest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.shared.api.MessageDescriptor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Milestone {
    private String id;
    private MessageDescriptor title;
    private MessageDescriptor description;
    private String icon;
    private String tier; // bronze, silver, gold, diamond
    private String category; // distance, places, trips, activeDays, epicJourney
}
