package org.github.tess1o.geopulse.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import org.github.tess1o.geopulse.model.entity.LocationEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.locationtech.jts.geom.LineString;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class LocationRepository implements PanacheRepository<LocationEntity> {
    private final EntityManager em;

    @Inject
    public LocationRepository(EntityManager em) {
        this.em = em;
    }

    /**
     * Find locations for a specific user within a time period.
     * Results are ordered by timestamp to ensure the path is in chronological order.
     *
     * @param userId    The ID of the user
     * @param startTime The start of the time period
     * @param endTime   The end of the time period
     * @return A list of location entities ordered by timestamp
     */
    public List<LocationEntity> findByUserIdAndTimePeriod(String userId, Instant startTime, Instant endTime) {
        return list("userId = ?1 AND timestamp >= ?2 AND timestamp <= ?3 ORDER BY timestamp ASC",
                userId, startTime, endTime);
    }

    /**
     * Generate a path as a LineString for a specific user within a time period using PostGIS.
     * This uses the ST_MakeLine PostGIS function to create a LineString from individual points.
     *
     * @param userId    The ID of the user
     * @param startTime The start of the time period
     * @param endTime   The end of the time period
     * @return A LineString representing the path
     */
    public LineString generatePathLineString(String userId, Instant startTime, Instant endTime) {
        String query = "SELECT ST_MakeLine(location ORDER BY timestamp) " +
                "FROM locations " +
                "WHERE userId = ?1 AND timestamp >= ?2 AND timestamp <= ?3";

        return (LineString) em.createNativeQuery(query)
                .setParameter(1, userId)
                .setParameter(2, startTime)
                .setParameter(3, endTime)
                .getSingleResult();
    }
}
