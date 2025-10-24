package org.github.tess1o.geopulse.geocoding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for reconciling geocoding results with a specific provider
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReverseGeocodingReconcileRequest {

    @NotBlank(message = "Provider name is required")
    private String providerName;

    @NotNull(message = "Geocoding IDs list is required")
    private List<Long> geocodingIds;

    /**
     * If true, reconcile all records matching the filter, ignoring geocodingIds
     */
    private Boolean reconcileAll;

    /**
     * Optional filter by provider when reconciling all
     */
    private String filterByProvider;
}
