package org.github.tess1o.geopulse.geofencing.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppriseTestResponse(boolean success, Integer statusCode, String detail) {
}
