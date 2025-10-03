package org.github.tess1o.geopulse.digest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeriodInfo {
    private int year;
    private Integer month; // null for yearly digest
    private String displayName; // "January 2024" or "2024"
    private String type; // "monthly" or "yearly"
}
