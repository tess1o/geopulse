package org.github.tess1o.geopulse.trips.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import org.github.tess1o.geopulse.friends.repository.FriendshipRepository;
import org.github.tess1o.geopulse.trips.model.entity.TripCollaboratorAccessRole;
import org.github.tess1o.geopulse.trips.model.entity.TripCollaboratorEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripEntity;
import org.github.tess1o.geopulse.trips.repository.TripCollaboratorRepository;
import org.github.tess1o.geopulse.trips.repository.TripRepository;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TripAccessService {

    private final TripRepository tripRepository;
    private final TripCollaboratorRepository tripCollaboratorRepository;
    private final FriendshipRepository friendshipRepository;

    public TripAccessService(TripRepository tripRepository,
                             TripCollaboratorRepository tripCollaboratorRepository,
                             FriendshipRepository friendshipRepository) {
        this.tripRepository = tripRepository;
        this.tripCollaboratorRepository = tripCollaboratorRepository;
        this.friendshipRepository = friendshipRepository;
    }

    public TripAccessContext requireReadAccess(UUID actorUserId, Long tripId) {
        return requireAccess(actorUserId, tripId, TripAccessLevel.READ);
    }

    public TripAccessContext requirePlanEditAccess(UUID actorUserId, Long tripId) {
        return requireAccess(actorUserId, tripId, TripAccessLevel.PLAN_EDIT);
    }

    public TripAccessContext requireOwnerAccess(UUID actorUserId, Long tripId) {
        return requireAccess(actorUserId, tripId, TripAccessLevel.OWNER_MANAGE);
    }

    public TripAccessContext requireAccess(UUID actorUserId, Long tripId, TripAccessLevel requiredLevel) {
        TripEntity trip = tripRepository.findByIdOptional(tripId)
                .orElseThrow(() -> new NotFoundException("Trip not found"));

        UUID ownerUserId = trip.getUser().getId();
        if (ownerUserId.equals(actorUserId)) {
            return new TripAccessContext(trip, true, TripCollaboratorAccessRole.EDIT);
        }

        Optional<TripCollaboratorEntity> membership = tripCollaboratorRepository
                .findByTripIdAndCollaboratorUserId(tripId, actorUserId);

        if (membership.isEmpty()) {
            throw new NotFoundException("Trip not found");
        }

        if (!friendshipRepository.existsFriendship(ownerUserId, actorUserId)) {
            throw new NotFoundException("Trip not found");
        }

        TripCollaboratorAccessRole role = membership.get().getAccessRole();
        if (!hasLevel(role, requiredLevel)) {
            throw new NotFoundException("Trip not found");
        }

        return new TripAccessContext(trip, false, role);
    }

    private boolean hasLevel(TripCollaboratorAccessRole role, TripAccessLevel requiredLevel) {
        if (requiredLevel == TripAccessLevel.READ) {
            return true;
        }
        if (requiredLevel == TripAccessLevel.PLAN_EDIT) {
            return role == TripCollaboratorAccessRole.EDIT;
        }
        return false;
    }
}
