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
    
    /**
     * Batch find cached locations for multiple coordinates.
     * Optimized for timeline assembly to reduce database round-trips.
     * 
     * @param coordinates List of coordinates to search for
     * @param toleranceMeters Tolerance in meters for spatial matching
     * @return Map of coordinate string (lon,lat) to cached location
     */
    public java.util.Map<String, ReverseGeocodingLocationEntity> findByCoordinatesBatch(
            List<Point> coordinates, double toleranceMeters) {
        
        if (coordinates == null || coordinates.isEmpty()) {
            return java.util.Map.of();
        }
        
        java.util.Map<String, ReverseGeocodingLocationEntity> resultMap = new java.util.HashMap<>();
        
        // Process in smaller batches to avoid query complexity issues
        int batchSize = 50;
        for (int i = 0; i < coordinates.size(); i += batchSize) {
            List<Point> batch = coordinates.subList(i, Math.min(i + batchSize, coordinates.size()));
            processBatch(batch, toleranceMeters, resultMap);
        }
        
        return resultMap;
    }
    
    private void processBatch(List<Point> batch, double toleranceMeters, 
                             java.util.Map<String, ReverseGeocodingLocationEntity> resultMap) {
        
        // Fallback to individual queries for now - this is safer and still batched at the service level
        // The batch processing happens at the service layer to avoid N+1 database calls
        java.time.Instant now = java.time.Instant.now();
        
        for (Point coord : batch) {
            String coordKey = coord.getX() + "," + coord.getY();
            ReverseGeocodingLocationEntity result = findByRequestCoordinatesWithoutUpdate(coord, toleranceMeters);
            if (result != null) {
                // Update timestamp for all results at once later
                result.setLastAccessedAt(now);
                resultMap.put(coordKey, result);
            }
        }
        
        // Batch update timestamps for found entities
        if (!resultMap.isEmpty()) {
            try {
                getEntityManager().createQuery(
                    "UPDATE ReverseGeocodingLocationEntity r SET r.lastAccessedAt = :now WHERE r.id IN :ids")
                    .setParameter("now", now)
                    .setParameter("ids", resultMap.values().stream()
                        .map(ReverseGeocodingLocationEntity::getId)
                        .collect(java.util.stream.Collectors.toList()))
                    .executeUpdate();
                    
            } catch (Exception e) {
                log.warn("Failed to batch update access timestamps: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Find cached location without updating access time (for batch processing).
     */
    private ReverseGeocodingLocationEntity findByRequestCoordinatesWithoutUpdate(Point requestCoordinates, double toleranceMeters) {
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
            return results.getFirst();
        }
        
        return null;
    }
}