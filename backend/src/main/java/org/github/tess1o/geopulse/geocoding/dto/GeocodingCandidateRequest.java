package org.github.tess1o.geopulse.geocoding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GeocodingCandidateRequest(@NotBlank @Size(max = 100) String providerName) {}
