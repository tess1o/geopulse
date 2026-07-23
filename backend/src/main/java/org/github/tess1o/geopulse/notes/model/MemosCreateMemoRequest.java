package org.github.tess1o.geopulse.notes.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemosCreateMemoRequest {
    private String content;
    private MemosVisibility visibility;
    private Instant createTime;
    private MemosLocation location;
}
