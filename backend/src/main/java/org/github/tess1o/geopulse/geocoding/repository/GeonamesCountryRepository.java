package org.github.tess1o.geopulse.geocoding.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.geocoding.model.GeonamesCountryRecord;
import org.hibernate.Session;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

@ApplicationScoped
public class GeonamesCountryRepository {

    private static final String STAGING_TABLE = "geonames_country_import_staging";

    private static final String UPSERT_SQL = """
            INSERT INTO geonames_country (
                iso_alpha2, iso_alpha3, iso_numeric, fips_code, country_name, capital,
                area_sq_km, population, continent, tld, currency_code, currency_name,
                phone, postal_code_format, postal_code_regex, languages, geonameid,
                neighbors, equivalent_fips_code
            ) VALUES (
                ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?
            )
            ON CONFLICT (iso_alpha2) DO UPDATE SET
                iso_alpha3 = EXCLUDED.iso_alpha3,
                iso_numeric = EXCLUDED.iso_numeric,
                fips_code = EXCLUDED.fips_code,
                country_name = EXCLUDED.country_name,
                capital = EXCLUDED.capital,
                area_sq_km = EXCLUDED.area_sq_km,
                population = EXCLUDED.population,
                continent = EXCLUDED.continent,
                tld = EXCLUDED.tld,
                currency_code = EXCLUDED.currency_code,
                currency_name = EXCLUDED.currency_name,
                phone = EXCLUDED.phone,
                postal_code_format = EXCLUDED.postal_code_format,
                postal_code_regex = EXCLUDED.postal_code_regex,
                languages = EXCLUDED.languages,
                geonameid = EXCLUDED.geonameid,
                neighbors = EXCLUDED.neighbors,
                equivalent_fips_code = EXCLUDED.equivalent_fips_code
            """;

    private static final String UPSERT_STAGING_SQL = """
            INSERT INTO geonames_country_import_staging (
                iso_alpha2, iso_alpha3, iso_numeric, fips_code, country_name, capital,
                area_sq_km, population, continent, tld, currency_code, currency_name,
                phone, postal_code_format, postal_code_regex, languages, geonameid,
                neighbors, equivalent_fips_code
            ) VALUES (
                ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?
            )
            ON CONFLICT (iso_alpha2) DO UPDATE SET
                iso_alpha3 = EXCLUDED.iso_alpha3,
                iso_numeric = EXCLUDED.iso_numeric,
                fips_code = EXCLUDED.fips_code,
                country_name = EXCLUDED.country_name,
                capital = EXCLUDED.capital,
                area_sq_km = EXCLUDED.area_sq_km,
                population = EXCLUDED.population,
                continent = EXCLUDED.continent,
                tld = EXCLUDED.tld,
                currency_code = EXCLUDED.currency_code,
                currency_name = EXCLUDED.currency_name,
                phone = EXCLUDED.phone,
                postal_code_format = EXCLUDED.postal_code_format,
                postal_code_regex = EXCLUDED.postal_code_regex,
                languages = EXCLUDED.languages,
                geonameid = EXCLUDED.geonameid,
                neighbors = EXCLUDED.neighbors,
                equivalent_fips_code = EXCLUDED.equivalent_fips_code
            """;

    private final EntityManager entityManager;

    @Inject
    public GeonamesCountryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public long countCountries() {
        Number result = (Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM geonames_country")
                .getSingleResult();
        return result.longValue();
    }

    @Transactional
    public void truncateAll() {
        entityManager.createNativeQuery("TRUNCATE TABLE geonames_country").executeUpdate();
    }

    @Transactional
    public int upsertBatch(List<GeonamesCountryRecord> batch) {
        if (batch == null || batch.isEmpty()) {
            return 0;
        }

        Session session = entityManager.unwrap(Session.class);
        session.doWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
                for (GeonamesCountryRecord record : batch) {
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
                CREATE TABLE IF NOT EXISTS geonames_country_import_staging
                (LIKE geonames_country INCLUDING ALL)
                """).executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE " + STAGING_TABLE).executeUpdate();
    }

    @Transactional
    public int upsertBatchToStaging(List<GeonamesCountryRecord> batch) {
        if (batch == null || batch.isEmpty()) {
            return 0;
        }

        Session session = entityManager.unwrap(Session.class);
        session.doWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_STAGING_SQL)) {
                for (GeonamesCountryRecord record : batch) {
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
    public long countStagingCountries() {
        Number result = (Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM " + STAGING_TABLE)
                .getSingleResult();
        return result.longValue();
    }

    @Transactional
    public void replaceMainFromStagingAtomic() {
        entityManager.createNativeQuery("TRUNCATE TABLE geonames_country").executeUpdate();
        entityManager.createNativeQuery("INSERT INTO geonames_country SELECT * FROM " + STAGING_TABLE).executeUpdate();
    }

    private void bind(PreparedStatement statement, GeonamesCountryRecord record) throws SQLException {
        int index = 1;
        statement.setString(index++, record.isoAlpha2());
        setNullableString(statement, index++, record.isoAlpha3());
        setNullableInteger(statement, index++, record.isoNumeric());
        setNullableString(statement, index++, record.fipsCode());
        statement.setString(index++, record.countryName());
        setNullableString(statement, index++, record.capital());
        setNullableDouble(statement, index++, record.areaSqKm());
        setNullableLong(statement, index++, record.population());
        setNullableString(statement, index++, record.continent());
        setNullableString(statement, index++, record.tld());
        setNullableString(statement, index++, record.currencyCode());
        setNullableString(statement, index++, record.currencyName());
        setNullableString(statement, index++, record.phone());
        setNullableString(statement, index++, record.postalCodeFormat());
        setNullableString(statement, index++, record.postalCodeRegex());
        setNullableString(statement, index++, record.languages());
        setNullableLong(statement, index++, record.geonameId());
        setNullableString(statement, index++, record.neighbors());
        setNullableString(statement, index, record.equivalentFipsCode());
    }

    private void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
            return;
        }
        statement.setString(index, value);
    }

    private void setNullableInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
            return;
        }
        statement.setInt(index, value);
    }

    private void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
            return;
        }
        statement.setLong(index, value);
    }

    private void setNullableDouble(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DOUBLE);
            return;
        }
        statement.setDouble(index, value);
    }
}
