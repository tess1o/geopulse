package org.github.tess1o.geopulse.trips.service;

import org.github.tess1o.geopulse.trips.model.entity.TripCollaboratorAccessRole;
import org.github.tess1o.geopulse.trips.model.entity.TripEntity;

import java.util.UUID;

public record TripAccessContext(
        TripEntity trip,
        boolean owner,
        TripCollaboratorAccessRole collaboratorRole
) {

    public UUID ownerUserId() {
        return trip.getUser().getId();
    }

    public String resolvedAccessRole() {
        return owner ? "OWNER" : collaboratorRole.name();
    }
}
