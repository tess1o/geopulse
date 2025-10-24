package org.github.tess1o.geopulse.geocoding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for reconciliation operation results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReverseGeocodingReconcileResult {
    private Integer totalProcessed;
    private Integer successfulUpdates;
    private Integer failedUpdates;

    @Builder.Default
    private List<ReconcileError> errors = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReconcileError {
        private Long geocodingId;
        private String errorMessage;
    }
}
