package org.github.tess1o.geopulse.geocoding.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.UUID;

public record GeocodingCandidateSelection(@NotNull UUID previewId, @NotNull @Min(0) @Max(9) Integer candidateId) {}
