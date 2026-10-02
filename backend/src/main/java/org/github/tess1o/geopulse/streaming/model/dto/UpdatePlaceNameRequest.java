package org.github.tess1o.geopulse.streaming.model.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdatePlaceNameRequest(@NotBlank String name) { }
