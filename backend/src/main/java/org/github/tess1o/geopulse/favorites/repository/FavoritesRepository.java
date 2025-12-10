package org.github.tess1o.geopulse.favorites.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.locationtech.jts.geom.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;


@ApplicationScoped
public class FavoritesRepository implements PanacheRepository<FavoritesEntity> {

    private final EntityManager em;

    @Inject
    public FavoritesRepository(EntityManager em) {
        this.em = em;
    }

    public List<FavoritesEntity> findByUserId(UUID userId) {
        return list("user.id", userId);
    }

    public long countByUserId(UUID userId) {
        return count("user.id", userId);
    }

    public Optional<FavoritesEntity> findByIdAndUserId(Long id, UUID userId) {
        return find("id = ?1 and user.id = ?2", id, userId).firstResultOptional();
    }

    public Optional<FavoritesEntity> findByPoint(UUID userId, Point point, int maxDistanceFromPoint, int maxDistanceFromArea) {
        String query = """
                SELECT *
                FROM favorite_locations
                WHERE user_id = :userId AND ((
                    type = 'POINT'
                    AND ST_DWithin(
                        geometry::geography,
                        ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                        :maxDistanceFromPoint
                    )
                )
                OR (
                    type = 'AREA'
                    AND (
                        ST_Contains(geometry, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326))
                        OR ST_DWithin(
                            ST_Boundary(geometry)::geography,
                            ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                            :maxDistanceFromArea
                        )
                    )
                ));
                """;

        var resultList = em.createNativeQuery(query, FavoritesEntity.class)
                .setParameter("lon", point.getX())
                .setParameter("lat", point.getY())
                .setParameter("userId", userId)
                .setParameter("maxDistanceFromPoint", maxDistanceFromPoint)
                .setParameter("maxDistanceFromArea", maxDistanceFromArea)
                .getResultList();

        if (!resultList.isEmpty()) {
            return Optional.of((FavoritesEntity) resultList.getFirst());
        }
        return Optional.empty();
    }

    /**
     * Find existing favorites by user, name and location for duplicate detection during import.
     * Uses spatial tolerance to detect near-duplicates.
     * 
     * @param userId The user ID
     * @param name The favorite name
     * @param geometry The geometry (Point or Polygon)
     * @return List of potential duplicate favorites
     */
    public List<FavoritesEntity> findByUserAndNameAndLocation(UUID userId, String name, Point geometry) {
        // Use small spatial tolerance (~10 meters) for duplicate detection
        String query = """
                SELECT f.* FROM favorite_locations f
                WHERE f.user_id = :userId 
                AND f.name = :name
                AND ST_DWithin(f.geometry::geography, (:geometry)::geography, 10)
                """;
        
        @SuppressWarnings("unchecked")
        List<FavoritesEntity> results = em.createNativeQuery(query, FavoritesEntity.class)
                .setParameter("userId", userId)
                .setParameter("name", name)
                .setParameter("geometry", geometry)
                .getResultList();
                
        return results;
    }

    /**
     * Batch find favorites by multiple points with spatial matching.
     * This method executes a single SQL query instead of N individual queries.
     * 
     * @param userId User ID for favorite location lookup
     * @param points List of points to search for
     * @param maxDistanceFromPoint Maximum distance in meters for POINT favorites
     * @param maxDistanceFromArea Maximum distance in meters for AREA favorites
     * @return Map of coordinate string (lon,lat) to FavoritesEntity
     */
    public Map<String, FavoritesEntity> findByPointsBatch(UUID userId, List<Point> points,
                                                          int maxDistanceFromPoint, int maxDistanceFromArea) {
        if (points == null || points.isEmpty()) {
            return Map.of();
        }

        final int BATCH_SIZE = 10000;
        Map<String, FavoritesEntity> finalResultMap = new HashMap<>();
        int totalPoints = points.size();

        for (int i = 0; i < totalPoints; i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, totalPoints);
            List<Point> batchPoints = points.subList(i, end);
            finalResultMap.putAll(findFavoritesForBatch(userId, batchPoints, maxDistanceFromPoint, maxDistanceFromArea));
        }

        return finalResultMap;
    }

    private Map<String, FavoritesEntity> findFavoritesForBatch(UUID userId, List<Point> batchPoints,
                                                               int maxDistanceFromPoint, int maxDistanceFromArea) {
        // Build VALUES clause for input coordinates
        StringBuilder valuesClause = new StringBuilder();
        for (int j = 0; j < batchPoints.size(); j++) {
            if (j > 0) valuesClause.append(", ");
            valuesClause.append("(ST_SetSRID(ST_MakePoint(:lon").append(j)
                    .append(", :lat").append(j).append("), 4326), :lon").append(j)
                    .append(", :lat").append(j).append(")");
        }

        // Step 1: Get matching favorite IDs and their corresponding input coordinates
        String matchingQuery = """
                WITH input_coords AS (
                    SELECT input_point, input_lon, input_lat
                    FROM (VALUES %s) AS coords(input_point, input_lon, input_lat)
                )
                SELECT DISTINCT ON (ic.input_lon, ic.input_lat)
                       f.id, ic.input_lon, ic.input_lat
                FROM favorite_locations f
                CROSS JOIN input_coords ic
                WHERE f.user_id = :userId
                  AND (
                      (f.type = 'POINT'
                       AND ST_DWithin(f.geometry::geography, ic.input_point::geography, :maxDistanceFromPoint)
                      )
                      OR
                      (f.type = 'AREA'
                       AND (ST_Contains(f.geometry, ic.input_point)
                            OR ST_DWithin(ST_Boundary(f.geometry)::geography, ic.input_point::geography, :maxDistanceFromArea)
                           )
                      )
                  )
                ORDER BY ic.input_lon, ic.input_lat,
                         CASE WHEN f.type = 'AREA' AND ST_Contains(f.geometry, ic.input_point) THEN 1 ELSE 2 END,
                         ST_Distance(f.geometry::geography, ic.input_point::geography)
                """.formatted(valuesClause.toString());

        var matchingQueryExec = em.createNativeQuery(matchingQuery)
                .setParameter("userId", userId)
                .setParameter("maxDistanceFromPoint", maxDistanceFromPoint)
                .setParameter("maxDistanceFromArea", maxDistanceFromArea);

        // Set coordinate parameters
        for (int j = 0; j < batchPoints.size(); j++) {
            Point point = batchPoints.get(j);
            matchingQueryExec.setParameter("lon" + j, point.getX());
            matchingQueryExec.setParameter("lat" + j, point.getY());
        }

        @SuppressWarnings("unchecked")
        List<Object[]> matchingResults = matchingQueryExec.getResultList();

        if (matchingResults.isEmpty()) {
            return Map.of();
        }

        // Step 2: Get all matching entities in a single query and build coordinate mapping
        List<Long> favoriteIds = new ArrayList<>();
        Map<Long, List<String>> idToCoordListMap = new HashMap<>(); // One favorite can match multiple coordinates

        for (Object[] row : matchingResults) {
            Long id = ((Number) row[0]).longValue();
            Double inputLon = (Double) row[1];
            Double inputLat = (Double) row[2];
            String coordKey = inputLon + "," + inputLat;

            favoriteIds.add(id);
            idToCoordListMap.computeIfAbsent(id, k -> new ArrayList<>()).add(coordKey);
        }

        // Get all entities in a single IN query
        List<FavoritesEntity> entities = find("id in ?1", favoriteIds).list();

        // Build result map
        Map<String, FavoritesEntity> resultMap = new HashMap<>();
        for (FavoritesEntity entity : entities) {
            List<String> coordKeys = idToCoordListMap.get(entity.getId());
            if (coordKeys != null) {
                // One favorite can serve multiple input coordinates
                for (String coordKey : coordKeys) {
                    resultMap.put(coordKey, entity);
                }
            }
        }
        return resultMap;
    }

    /**
     * Find favorites by user ID and name containing a search term (for location analytics search).
     * Returns up to 'limit' results ordered by name.
     *
     * @param userId User ID
     * @param searchTerm Search term to match in name (case-insensitive)
     * @param limit Maximum number of results
     * @return List of matching favorites
     */
    public List<FavoritesEntity> findByUserIdAndNameContaining(UUID userId, String searchTerm, int limit) {
        return find("user.id = ?1 and LOWER(name) LIKE LOWER(?2) order by name",
                userId, "%" + searchTerm + "%")
                .page(0, limit)
                .list();
    }

}
