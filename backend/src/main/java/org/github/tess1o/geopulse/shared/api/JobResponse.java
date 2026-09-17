package org.github.tess1o.geopulse.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobResponse(UUID jobId) {
}
