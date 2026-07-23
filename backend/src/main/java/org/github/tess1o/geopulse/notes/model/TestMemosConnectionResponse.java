package org.github.tess1o.geopulse.notes.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TestMemosConnectionResponse {
    private boolean success;
    private String message;
    private String details;
}
