package org.github.tess1o.geopulse.geocoding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BulkUpdateGeocodingResult {

    private int totalRequested;
    private int successCount;
    private int failedCount;
    private Map<Long, String> failures; // geocodingId -> error message
}
