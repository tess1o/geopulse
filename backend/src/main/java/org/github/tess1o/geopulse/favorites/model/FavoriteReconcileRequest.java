package org.github.tess1o.geopulse.favorites.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteReconcileRequest {

    @NotBlank(message = "Provider name is required")
    @Schema(examples = "Nominatim")
    private String providerName;

    @NotNull(message = "Favorite IDs list is required")
    @Schema(examples = "[12, 15]")
    private List<Long> favoriteIds;

    /**
     * If true, reconcile all records matching the filter, ignoring favoriteIds
     */
    @Schema(examples = "false")
    private Boolean reconcileAll;

    /**
     * Optional filter by type when reconciling all
     */
    @Schema(examples = "POINT")
    private FavoriteLocationType filterByType;

    /**
     * Optional search text filter when reconciling all
     */
    @Schema(examples = "Kyiv")
    private String filterBySearchText;
}
