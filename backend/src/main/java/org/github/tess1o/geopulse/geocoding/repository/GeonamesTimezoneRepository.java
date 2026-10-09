package org.github.tess1o.geopulse.geocoding.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.geocoding.model.GeonamesNearestCity;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Nearest-city lookups on {@code geonames_city}, used to resolve the local timezone of a coordinate.
 *
 * <p>The lookup orders by {@code ST_SetSRID(ST_MakePoint(longitude, latitude), 4326) <-> point}, which matches the
 * {@code idx_geonames_city_geom_gist} expression index (V36.65.0). KNN on SRID 4326 ranks by planar degrees, so the
 * closest few candidates are re-ranked by true sphere distance.</p>
 */
@ApplicationScoped
public class GeonamesTimezoneRepository {

    /**
     * Resolves many coordinates in one round-trip. Inputs are snapped to a 0.01 degree grid (about 1 km) and each
     * distinct cell is looked up once, so repeated places (home, work) cost a single index probe. Reported distances
     * are from the cell center, so they can be off by up to about 1 km.
     */
    private static final String BATCH_NEAREST_SQL = """
            WITH input AS MATERIALIZED (
                SELECT i.ord,
                       round(i.lat::numeric, 2) AS cell_lat,
                       round(i.lon::numeric, 2) AS cell_lon
                FROM unnest(CAST(:latitudes AS float8[]), CAST(:longitudes AS float8[]))
                     WITH ORDINALITY AS i(lat, lon, ord)
            ),
            cells AS MATERIALIZED (
                SELECT d.cell_lat, d.cell_lon,
                       ST_SetSRID(ST_MakePoint(d.cell_lon::float8, d.cell_lat::float8), 4326) AS pt
                FROM (SELECT DISTINCT cell_lat, cell_lon FROM input) d
            ),
            resolved AS MATERIALIZED (
                SELECT c.cell_lat, c.cell_lon, n.geonameid, n.name, n.country_code, n.timezone, n.distance_m
                FROM cells c
                LEFT JOIN LATERAL (
                    SELECT k.*
                    FROM (
                        SELECT gc.geonameid, gc.name, gc.country_code, gc.timezone,
                               ST_DistanceSphere(ST_SetSRID(ST_MakePoint(gc.longitude, gc.latitude), 4326), c.pt)
                                   AS distance_m
                        FROM geonames_city gc
                        ORDER BY ST_SetSRID(ST_MakePoint(gc.longitude, gc.latitude), 4326) <-> c.pt
                        LIMIT 8
                    ) k
                    ORDER BY k.distance_m
                    LIMIT 1
                ) n ON TRUE
            )
            SELECT i.ord, r.geonameid, r.name, r.country_code, r.timezone, r.distance_m
            FROM input i
            JOIN resolved r ON r.cell_lat = i.cell_lat AND r.cell_lon = i.cell_lon
            ORDER BY i.ord
            """;

    private final EntityManager entityManager;

    @Inject
    public GeonamesTimezoneRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Nearest city for each coordinate, in input order. An element is {@code null} when no city was found
     * (the GeoNames table is empty).
     */
    @Transactional
    public List<GeonamesNearestCity> findNearestCities(double[] latitudes, double[] longitudes) {
        if (latitudes.length != longitudes.length) {
            throw new IllegalArgumentException("latitudes and longitudes must have the same length");
        }
        GeonamesNearestCity[] result = new GeonamesNearestCity[latitudes.length];
        if (latitudes.length == 0) {
            return Arrays.asList(result);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(BATCH_NEAREST_SQL)
                .setParameter("latitudes", latitudes)
                .setParameter("longitudes", longitudes)
                .getResultList();

        for (Object[] row : rows) {
            if (row[1] == null) {
                continue;
            }
            int index = ((Number) row[0]).intValue() - 1;
            result[index] = new GeonamesNearestCity(
                    ((Number) row[1]).longValue(),
                    row[2] != null ? row[2].toString() : null,
                    row[3] != null ? row[3].toString().trim() : null,
                    row[4] != null ? row[4].toString() : null,
                    ((Number) row[5]).doubleValue());
        }
        return Arrays.asList(result);
    }

    /** The country most GeoNames cities in these timezones belong to, e.g. UA for Europe/Kyiv. */
    @Transactional
    public Optional<String> findPrimaryCountryCode(List<String> timezones) {
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery("""
                        SELECT country_code
                        FROM geonames_city
                        WHERE timezone IN (:timezones) AND country_code IS NOT NULL
                        GROUP BY country_code
                        ORDER BY COUNT(*) DESC, country_code
                        LIMIT 1
                        """)
                .setParameter("timezones", timezones)
                .getResultList();
        return rows.stream().findFirst().map(value -> value.toString().trim());
    }

    @Transactional
    public boolean hasAnyCity() {
        return !entityManager.createNativeQuery("SELECT 1 FROM geonames_city LIMIT 1").getResultList().isEmpty();
    }
}
