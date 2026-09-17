package org.github.tess1o.geopulse.notes.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TestMemosConnectionResponse {
    private boolean success;
    private MemosConnectionStatus status;
    private Integer memoCount;
    private String details;
}
