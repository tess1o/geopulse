package org.github.tess1o.geopulse.geocoding.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.List;

/**
 * Repository for reverse geocoding locations using ultra-simplified schema.
 * Only stores essential fields: coordinates, bounding box, formatted display name, provider, timestamps.
 */
@ApplicationScoped
@Slf4j
public class ReverseGeocodingLocationRepository implements PanacheRepository<ReverseGeocodingLocationEntity> {
    
    /**
     * Find cached location using comprehensive spatial query.
     * Checks: 1) Request coordinates proximity, 2) Result coordinates proximity, 3) Bounding box containment
     * 
     * @param requestCoordinates The coordinates to search for
     * @param toleranceMeters Tolerance in meters for spatial matching
     * @return Best matching cached location or null
     */
    public ReverseGeocodingLocationEntity findByRequestCoordinates(Point requestCoordinates, double toleranceMeters) {
        String searchQuery = """
                SELECT *
                FROM reverse_geocoding_location
                WHERE ST_DWithin(result_coordinates::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :tolerance)
                    OR ST_DWithin(request_coordinates::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :tolerance)
                    OR ST_Contains(bounding_box, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326))
                ORDER BY last_accessed_at DESC
                LIMIT 1
                """;

        @SuppressWarnings("unchecked")
        List<ReverseGeocodingLocationEntity> results = getEntityManager()
                .createNativeQuery(searchQuery, ReverseGeocodingLocationEntity.class)
                .setParameter("lon", requestCoordinates.getX())
                .setParameter("lat", requestCoordinates.getY())
                .setParameter("tolerance", toleranceMeters)
                .getResultList();

        if (!results.isEmpty()) {
            ReverseGeocodingLocationEntity result = results.getFirst();
            // Update last accessed time
            result.setLastAccessedAt(Instant.now());
            persist(result);
            
            log.debug("Found cached location for coordinates: lon={}, lat={}, provider={}", 
                     requestCoordinates.getX(), requestCoordinates.getY(), result.getProviderName());
            return result;
        }
        
        log.debug("No cached location found for coordinates: lon={}, lat={} within {}m", 
                 requestCoordinates.getX(), requestCoordinates.getY(), toleranceMeters);
        return null;
    }
    
    /**
     * Find cached location by exact request coordinates.
     * 
     * @param requestCoordinates The exact coordinates to search for
     * @return Cached location or null
     */
    public ReverseGeocodingLocationEntity findByExactCoordinates(Point requestCoordinates) {
        return find("requestCoordinates", requestCoordinates).firstResult();
    }
    
    /**
     * Find reverse geocoding locations by their IDs.
     * Used for export functionality to collect referenced geocoding data.
     * 
     * @param ids List of reverse geocoding location IDs
     * @return List of found reverse geocoding locations
     */
    public List<ReverseGeocodingLocationEntity> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return find("id in ?1", ids).list();
    }
}