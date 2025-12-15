package org.github.tess1o.geopulse.favorites.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteReconcileRequest {

    @NotBlank(message = "Provider name is required")
    private String providerName;

    @NotNull(message = "Favorite IDs list is required")
    private List<Long> favoriteIds;

    /**
     * If true, reconcile all records matching the filter, ignoring favoriteIds
     */
    private Boolean reconcileAll;

    /**
     * Optional filter by type when reconciling all
     */
    private FavoriteLocationType filterByType;

    /**
     * Optional search text filter when reconciling all
     */
    private String filterBySearchText;
}
