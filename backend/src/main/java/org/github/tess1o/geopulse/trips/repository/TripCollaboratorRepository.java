package org.github.tess1o.geopulse.trips.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.trips.model.entity.TripCollaboratorEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TripCollaboratorRepository implements PanacheRepository<TripCollaboratorEntity> {

    public Optional<TripCollaboratorEntity> findByTripIdAndCollaboratorUserId(Long tripId, UUID collaboratorUserId) {
        return find("trip.id = ?1 and collaborator.id = ?2", tripId, collaboratorUserId).firstResultOptional();
    }

    public List<TripCollaboratorEntity> findByTripIdWithCollaborator(Long tripId) {
        return list("SELECT tc FROM TripCollaboratorEntity tc JOIN FETCH tc.collaborator WHERE tc.trip.id = ?1 ORDER BY tc.createdAt ASC", tripId);
    }

    public List<TripCollaboratorEntity> findByCollaboratorUserIdWithTrip(UUID collaboratorUserId) {
        return list("SELECT tc FROM TripCollaboratorEntity tc JOIN FETCH tc.trip t JOIN FETCH t.user WHERE tc.collaborator.id = ?1 ORDER BY t.startTime DESC", collaboratorUserId);
    }

    public long deleteByTripIdAndCollaboratorUserId(Long tripId, UUID collaboratorUserId) {
        return delete("trip.id = ?1 and collaborator.id = ?2", tripId, collaboratorUserId);
    }
}
