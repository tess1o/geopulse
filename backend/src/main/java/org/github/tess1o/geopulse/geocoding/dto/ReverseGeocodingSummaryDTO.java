package org.github.tess1o.geopulse.geocoding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for geocoding summary statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReverseGeocodingSummaryDTO {
    private Long totalResults;
    private Map<String, Long> resultsByProvider;
    private Long lastWeekResults;
    private Long lastMonthResults;
}
