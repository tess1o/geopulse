package org.github.tess1o.geopulse.coverage.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record CoverageSettingsRequest(@Schema(examples = "true") Boolean enabled) {
}
