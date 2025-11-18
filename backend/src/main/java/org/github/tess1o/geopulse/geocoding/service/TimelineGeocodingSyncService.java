package org.github.tess1o.geopulse.geocoding.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Service responsible for synchronizing geocoding changes with timeline stays.
 * Handles updates to timeline_stays table when geocoding data is modified.
 */
@ApplicationScoped
@Slf4j
public class TimelineGeocodingSyncService {

    private final EntityManager entityManager;

    @Inject
    public TimelineGeocodingSyncService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Update timeline stays for user when modifying their own entity (in-place update).
     * Updates the location name for all timeline stays referencing the given geocoding ID.
     *
     * @param userId        The user who owns the timeline stays
     * @param geocodingId   The geocoding location ID
     * @param locationName  The new location name to set
     * @return Number of timeline stays updated
     */
    public int updateLocationNameForUser(UUID userId, Long geocodingId, String locationName) {
        int updatedCount = entityManager.createQuery(
                        "UPDATE TimelineStayEntity t " +
                                "SET t.locationName = :locationName " +
                                "WHERE t.geocodingLocation.id = :geocodingId " +
                                "  AND t.user.id = :userId"
                )
                .setParameter("geocodingId", geocodingId)
                .setParameter("userId", userId)
                .setParameter("locationName", locationName)
                .executeUpdate();

        log.debug("Updated {} timeline stays for user {} with new location name '{}'",
                updatedCount, userId, locationName);

        return updatedCount;
    }

    /**
     * Switch timeline stays from old geocoding reference to new copy (copy-on-write).
     * Changes stay references from old (original) to new (user-specific copy).
     *
     * @param userId          The user who owns the timeline stays
     * @param oldGeocodingId  The old geocoding location ID (original)
     * @param newGeocodingId  The new geocoding location ID (user copy)
     * @param locationName    The new location name to set
     * @return Number of timeline stays updated
     */
    public int switchToNewGeocodingReference(
            UUID userId,
            Long oldGeocodingId,
            Long newGeocodingId,
            String locationName) {

        // Need to use native query because JPA doesn't support updating FK directly
        String updateSql = """
                UPDATE timeline_stays
                SET geocoding_id = :newGeocodingId,
                    location_name = :locationName
                WHERE geocoding_id = :oldGeocodingId
                  AND user_id = :userId
                """;

        int updatedCount = entityManager.createNativeQuery(updateSql)
                .setParameter("oldGeocodingId", oldGeocodingId)
                .setParameter("newGeocodingId", newGeocodingId)
                .setParameter("userId", userId)
                .setParameter("locationName", locationName)
                .executeUpdate();

        log.info("Switched {} timeline stays for user {} from geocoding {} to {}",
                updatedCount, userId, oldGeocodingId, newGeocodingId);

        return updatedCount;
    }
}
