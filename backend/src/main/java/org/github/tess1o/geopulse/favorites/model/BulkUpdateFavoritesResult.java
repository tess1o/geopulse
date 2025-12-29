package org.github.tess1o.geopulse.favorites.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BulkUpdateFavoritesResult {

    private int totalRequested;
    private int successCount;
    private int failedCount;
    private Map<Long, String> failures; // favoriteId -> error message
}
