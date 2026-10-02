package org.github.tess1o.geopulse.friends.model;

import jakarta.validation.constraints.NotNull;

public record UpdateLiveLocationPermissionRequest(@NotNull Boolean shareLiveLocation) {
}
