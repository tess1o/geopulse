package org.github.tess1o.geopulse.favorites.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class BulkAddFavoritesResult {
    private int totalRequested;
    private int successCount;
    private int failedCount;
    private List<Long> createdFavoriteIds;
    private Map<Integer, String> failures;  // index -> error message
    private String jobId;  // Timeline regeneration job ID
}
