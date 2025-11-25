package org.github.tess1o.geopulse.immich.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TestImmichConnectionResponse {
    private boolean success;
    private String message;
    private String details; // Optional additional error details
}
