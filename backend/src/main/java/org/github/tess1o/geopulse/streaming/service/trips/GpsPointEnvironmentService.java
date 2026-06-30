package org.github.tess1o.geopulse.streaming.service.trips;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.query.NativeQuery;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Maintains cached water-surface evidence for GPS points.
 * <p>
 * The cache is populated lazily in reconciliation paths when boat detection is enabled.
 * This keeps PostGIS work out of trip finalization and avoids deploy-time backfills.
 */
@ApplicationScoped
@Slf4j
public class GpsPointEnvironmentService {

    public static final String WATER_DATASET_PREFIX = "water_surface_polygons:%";
    public static final Instant DEFAULT_START_DATE = Instant.parse("1970-01-01T00:00:00Z");

    @Inject
    EntityManager entityManager;

    @Transactional
    public String getCurrentEnvironmentDatasetVersion() {
        try {
            Object result = entityManager.createNativeQuery("""
                            SELECT string_agg(
                                       dataset_name || ':' ||
                                       COALESCE(source_version, 'unknown') || ':' ||
                                       feature_count || ':' ||
                                       EXTRACT(EPOCH FROM imported_at)::bigint,
                                       '|'
                                       ORDER BY dataset_name
                                   )
                            FROM geo_dataset_metadata
                            WHERE dataset_name LIKE :waterDatasetPrefix
                              AND feature_count > 0
                            """)
                    .setParameter("waterDatasetPrefix", WATER_DATASET_PREFIX)
                    .getSingleResult();
            return result != null ? result.toString() : null;
        } catch (NoResultException e) {
            return null;
        }
    }

    @Transactional
    public long countMissingOrStale(UUID userId, Instant fromTimestamp, String environmentDatasetVersion) {
        if (userId == null || fromTimestamp == null || environmentDatasetVersion == null) {
            return 0;
        }

        Number result = (Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*)
                        FROM gps_points gp
                        LEFT JOIN gps_point_environment env ON env.gps_point_id = gp.id
                        WHERE gp.user_id = :userId
                          AND gp.timestamp >= :fromTimestamp
                          AND gp.coordinates IS NOT NULL
                          AND (
                              env.gps_point_id IS NULL
                              OR env.environment_dataset_version <> :environmentDatasetVersion
                          )
                        """)
                .setParameter("userId", userId)
                .setParameter("fromTimestamp", fromTimestamp)
                .setParameter("environmentDatasetVersion", environmentDatasetVersion)
                .getSingleResult();
        return result != null ? result.longValue() : 0;
    }

    @Transactional
    public long countEligible(UUID userId, Instant fromTimestamp) {
        if (userId == null || fromTimestamp == null) {
            return 0;
        }

        Number result = (Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*)
                        FROM gps_points gp
                        WHERE gp.user_id = :userId
                          AND gp.timestamp >= :fromTimestamp
                          AND gp.coordinates IS NOT NULL
                        """)
                .setParameter("userId", userId)
                .setParameter("fromTimestamp", fromTimestamp)
                .getSingleResult();
        return result != null ? result.longValue() : 0;
    }

    @Transactional
    public long countClassified(UUID userId, String environmentDatasetVersion) {
        if (userId == null || environmentDatasetVersion == null) {
            return 0;
        }

        Number result = (Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*)
                        FROM gps_point_environment env
                        JOIN gps_points gp ON gp.id = env.gps_point_id
                        WHERE gp.user_id = :userId
                          AND env.environment_dataset_version = :environmentDatasetVersion
                        """)
                .setParameter("userId", userId)
                .setParameter("environmentDatasetVersion", environmentDatasetVersion)
                .getSingleResult();
        return result != null ? result.longValue() : 0;
    }

    @Transactional
    public int enrichPoints(UUID userId,
                            Collection<Long> gpsPointIds,
                            String environmentDatasetVersion) {
        if (userId == null || gpsPointIds == null || gpsPointIds.isEmpty() || environmentDatasetVersion == null) {
            return 0;
        }

        Query query = entityManager.createNativeQuery("""
                        WITH candidate AS (
                            SELECT gp.id, gp.coordinates
                            FROM gps_points gp
                            WHERE gp.user_id = :userId
                              AND gp.id IN (:gpsPointIds)
                              AND gp.coordinates IS NOT NULL
                        ),
                        classified AS (
                            SELECT
                                c.id AS gps_point_id,
                                water_match.source IS NOT NULL AS on_water,
                                water_match.source AS water_source
                            FROM candidate c
                            LEFT JOIN LATERAL (
                                SELECT water.source
                                FROM water_surface_polygons water
                                WHERE water.geom && c.coordinates
                                  AND ST_Covers(water.geom, c.coordinates)
                                ORDER BY CASE water.water_type WHEN 'ocean' THEN 2 ELSE 1 END
                                LIMIT 1
                            ) water_match ON true
                        )
                        INSERT INTO gps_point_environment (
                            gps_point_id,
                            environment_dataset_version,
                            on_water,
                            water_source,
                            classified_at
                        )
                        SELECT
                            gps_point_id,
                            :environmentDatasetVersion,
                            on_water,
                            water_source,
                            NOW()
                        FROM classified
                        ON CONFLICT (gps_point_id) DO UPDATE SET
                            environment_dataset_version = EXCLUDED.environment_dataset_version,
                            on_water = EXCLUDED.on_water,
                            water_source = EXCLUDED.water_source,
                            classified_at = EXCLUDED.classified_at
                        RETURNING gps_point_id
                        """)
                .setParameter("userId", userId)
                .setParameter("environmentDatasetVersion", environmentDatasetVersion);
        query.unwrap(NativeQuery.class).setParameterList("gpsPointIds", gpsPointIds);

        List<?> updatedRows = query.getResultList();
        int updatedCount = updatedRows.size();
        if (updatedCount > 0) {
            log.debug("Enriched {} newly saved GPS point environment rows for user {}", updatedCount, userId);
        }
        return updatedCount;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public int enrichNextBatch(UUID userId,
                               Instant fromTimestamp,
                               String environmentDatasetVersion,
                               int batchSize) {
        if (userId == null || fromTimestamp == null || environmentDatasetVersion == null || batchSize <= 0) {
            return 0;
        }

        Query query = entityManager.createNativeQuery("""
                        WITH candidate AS (
                            SELECT gp.id, gp.coordinates
                            FROM gps_points gp
                            LEFT JOIN gps_point_environment env ON env.gps_point_id = gp.id
                            WHERE gp.user_id = :userId
                              AND gp.timestamp >= :fromTimestamp
                              AND gp.coordinates IS NOT NULL
                              AND (
                                  env.gps_point_id IS NULL
                                  OR env.environment_dataset_version <> :environmentDatasetVersion
                              )
                            ORDER BY gp.timestamp ASC, gp.id ASC
                            LIMIT :batchSize
                        ),
                        classified AS (
                            SELECT
                                c.id AS gps_point_id,
                                water_match.source IS NOT NULL AS on_water,
                                water_match.source AS water_source
                            FROM candidate c
                            LEFT JOIN LATERAL (
                                SELECT water.source
                                FROM water_surface_polygons water
                                WHERE water.geom && c.coordinates
                                  AND ST_Covers(water.geom, c.coordinates)
                                ORDER BY CASE water.water_type WHEN 'ocean' THEN 2 ELSE 1 END
                                LIMIT 1
                            ) water_match ON true
                        )
                        INSERT INTO gps_point_environment (
                            gps_point_id,
                            environment_dataset_version,
                            on_water,
                            water_source,
                            classified_at
                        )
                        SELECT
                            gps_point_id,
                            :environmentDatasetVersion,
                            on_water,
                            water_source,
                            NOW()
                        FROM classified
                        ON CONFLICT (gps_point_id) DO UPDATE SET
                            environment_dataset_version = EXCLUDED.environment_dataset_version,
                            on_water = EXCLUDED.on_water,
                            water_source = EXCLUDED.water_source,
                            classified_at = EXCLUDED.classified_at
                        RETURNING gps_point_id
                        """)
                .setParameter("userId", userId)
                .setParameter("fromTimestamp", fromTimestamp)
                .setParameter("environmentDatasetVersion", environmentDatasetVersion)
                .setParameter("batchSize", batchSize);

        List<?> updatedRows = query.getResultList();
        int updatedCount = updatedRows.size();
        if (updatedCount > 0) {
            log.debug("Enriched {} GPS point environment rows for user {}", updatedCount, userId);
        }
        return updatedCount;
    }
}
