package org.github.tess1o.geopulse.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MapMatchingProviderTestResponse(
        boolean success,
        int statusCode,
        String provider,
        String url,
        String detail) {
}
