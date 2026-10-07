package org.github.tess1o.geopulse.geocoding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * DTO for reconciling geocoding results with a specific provider
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReverseGeocodingReconcileRequest {

    @NotBlank(message = "Provider name is required")
    @Schema(examples = "Nominatim")
    private String providerName;

    @NotNull(message = "Geocoding IDs list is required")
    @Schema(examples = "[301, 302]")
    private List<Long> geocodingIds;

    /**
     * If true, reconcile all records matching the filter, ignoring geocodingIds
     */
    @Schema(examples = "false")
    private Boolean reconcileAll;

    /**
     * Optional filter by provider when reconciling all
     */
    @Schema(examples = "Photon")
    private String filterByProvider;
}
