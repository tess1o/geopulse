package org.github.tess1o.geopulse.streaming.model.dto;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record UpdatePlaceNameRequest(@Schema(examples = "Home") @NotBlank String name) { }
