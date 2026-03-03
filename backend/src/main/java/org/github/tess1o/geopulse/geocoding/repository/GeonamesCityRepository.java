package org.github.tess1o.geopulse.geocoding.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.geocoding.model.GeonamesCityRecord;
import org.github.tess1o.geopulse.geocoding.model.GeonamesNormalizedLocation;
import org.hibernate.Session;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class GeonamesCityRepository {

    private static final String STAGING_TABLE = "geonames_city_import_staging";

    private static final String UPSERT_SQL = """
            INSERT INTO geonames_city (
                geonameid, name, asciiname, alternatenames, latitude, longitude,
                feature_class, feature_code, country_code, cc2, admin1_code, admin2_code, admin3_code, admin4_code,
                population, elevation, dem, timezone, modification_date
            ) VALUES (
                ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?
            )
            ON CONFLICT (geonameid) DO UPDATE SET
                name = EXCLUDED.name,
                asciiname = EXCLUDED.asciiname,
                alternatenames = EXCLUDED.alternatenames,
                latitude = EXCLUDED.latitude,
                longitude = EXCLUDED.longitude,
                feature_class = EXCLUDED.feature_class,
                feature_code = EXCLUDED.feature_code,
                country_code = EXCLUDED.country_code,
                cc2 = EXCLUDED.cc2,
                admin1_code = EXCLUDED.admin1_code,
                admin2_code = EXCLUDED.admin2_code,
                admin3_code = EXCLUDED.admin3_code,
                admin4_code = EXCLUDED.admin4_code,
                population = EXCLUDED.population,
                elevation = EXCLUDED.elevation,
                dem = EXCLUDED.dem,
                timezone = EXCLUDED.timezone,
                modification_date = EXCLUDED.modification_date
            """;

    private static final String UPSERT_STAGING_SQL = """
            INSERT INTO geonames_city_import_staging (
                geonameid, name, asciiname, alternatenames, latitude, longitude,
                feature_class, feature_code, country_code, cc2, admin1_code, admin2_code, admin3_code, admin4_code,
                population, elevation, dem, timezone, modification_date
            ) VALUES (
                ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?
            )
            ON CONFLICT (geonameid) DO UPDATE SET
                name = EXCLUDED.name,
                asciiname = EXCLUDED.asciiname,
                alternatenames = EXCLUDED.alternatenames,
                latitude = EXCLUDED.latitude,
                longitude = EXCLUDED.longitude,
                feature_class = EXCLUDED.feature_class,
                feature_code = EXCLUDED.feature_code,
                country_code = EXCLUDED.country_code,
                cc2 = EXCLUDED.cc2,
                admin1_code = EXCLUDED.admin1_code,
                admin2_code = EXCLUDED.admin2_code,
                admin3_code = EXCLUDED.admin3_code,
                admin4_code = EXCLUDED.admin4_code,
                population = EXCLUDED.population,
                elevation = EXCLUDED.elevation,
                dem = EXCLUDED.dem,
                timezone = EXCLUDED.timezone,
                modification_date = EXCLUDED.modification_date
            """;

    private final EntityManager entityManager;

    @Inject
    public GeonamesCityRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public long countCities() {
        Number result = (Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM geonames_city")
                .getSingleResult();
        return result.longValue();
    }

    @Transactional
    public void truncateAll() {
        entityManager.createNativeQuery("TRUNCATE TABLE geonames_city").executeUpdate();
    }

    @Transactional
    public int upsertBatch(List<GeonamesCityRecord> batch) {
        if (batch == null || batch.isEmpty()) {
            return 0;
        }

        Session session = entityManager.unwrap(Session.class);
        session.doWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
                for (GeonamesCityRecord record : batch) {
                    bind(statement, record);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        });

        entityManager.clear();
        return batch.size();
    }

    @Transactional
    public void prepareStagingTable() {
        entityManager.createNativeQuery("""
                CREATE TABLE IF NOT EXISTS geonames_city_import_staging
                (LIKE geonames_city INCLUDING ALL)
                """).executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE " + STAGING_TABLE).executeUpdate();
    }

    @Transactional
    public int upsertBatchToStaging(List<GeonamesCityRecord> batch) {
        if (batch == null || batch.isEmpty()) {
            return 0;
        }

        Session session = entityManager.unwrap(Session.class);
        session.doWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_STAGING_SQL)) {
                for (GeonamesCityRecord record : batch) {
                    bind(statement, record);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        });

        entityManager.clear();
        return batch.size();
    }

    @Transactional
    public long countStagingCities() {
        Number result = (Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM " + STAGING_TABLE)
                .getSingleResult();
        return result.longValue();
    }

    @Transactional
    public void replaceMainFromStagingAtomic() {
        entityManager.createNativeQuery("TRUNCATE TABLE geonames_city").executeUpdate();
        entityManager.createNativeQuery("INSERT INTO geonames_city SELECT * FROM " + STAGING_TABLE).executeUpdate();
    }

    @Transactional
    public Optional<GeonamesNormalizedLocation> findNearestNormalizedLocation(
            double latitude,
            double longitude,
            Double maxDistanceMeters
    ) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT
                            gc.geonameid,
                            gc.name AS city_name,
                            COALESCE(gct.country_name, gc.country_code) AS country_name,
                            gc.country_code,
                            ST_DistanceSphere(
                                ST_SetSRID(ST_MakePoint(gc.longitude, gc.latitude), 4326),
                                ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)
                            ) AS distance_m
                        FROM geonames_city gc
                        LEFT JOIN geonames_country gct ON gct.iso_alpha2 = gc.country_code
                        WHERE gc.latitude IS NOT NULL
                          AND gc.longitude IS NOT NULL
                          AND (
                              :maxDistanceMeters IS NULL
                              OR ST_DistanceSphere(
                                  ST_SetSRID(ST_MakePoint(gc.longitude, gc.latitude), 4326),
                                  ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)
                              ) <= :maxDistanceMeters
                          )
                        ORDER BY distance_m ASC, COALESCE(gc.population, 0) DESC
                        LIMIT 1
                        """)
                .setParameter("lat", latitude)
                .setParameter("lon", longitude)
                .setParameter("maxDistanceMeters", maxDistanceMeters)
                .getResultList();

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        Object[] row = rows.getFirst();
        Long geonameId = row[0] != null ? ((Number) row[0]).longValue() : null;
        String city = row[1] != null ? row[1].toString() : null;
        String country = row[2] != null ? row[2].toString() : null;
        String countryCode = row[3] != null ? row[3].toString() : null;
        Double distanceMeters = row[4] != null ? ((Number) row[4]).doubleValue() : null;

        return Optional.of(new GeonamesNormalizedLocation(
                geonameId,
                city,
                country,
                countryCode,
                distanceMeters
        ));
    }

    private void bind(PreparedStatement statement, GeonamesCityRecord record) throws SQLException {
        int index = 1;
        statement.setLong(index++, record.geonameId());
        statement.setString(index++, record.name());
        setNullableString(statement, index++, record.asciiName());
        setNullableString(statement, index++, record.alternateNames());
        statement.setDouble(index++, record.latitude());
        statement.setDouble(index++, record.longitude());
        setNullableString(statement, index++, record.featureClass());
        setNullableString(statement, index++, record.featureCode());
        setNullableString(statement, index++, record.countryCode());
        setNullableString(statement, index++, record.cc2());
        setNullableString(statement, index++, record.admin1Code());
        setNullableString(statement, index++, record.admin2Code());
        setNullableString(statement, index++, record.admin3Code());
        setNullableString(statement, index++, record.admin4Code());
        setNullableLong(statement, index++, record.population());
        setNullableInteger(statement, index++, record.elevation());
        setNullableInteger(statement, index++, record.dem());
        setNullableString(statement, index++, record.timezone());
        setNullableDate(statement, index, record.modificationDate() != null ? Date.valueOf(record.modificationDate()) : null);
    }

    private void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
            return;
        }
        statement.setString(index, value);
    }

    private void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
            return;
        }
        statement.setLong(index, value);
    }

    private void setNullableInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
            return;
        }
        statement.setInt(index, value);
    }

    private void setNullableDate(PreparedStatement statement, int index, Date value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DATE);
            return;
        }
        statement.setDate(index, value);
    }
}
