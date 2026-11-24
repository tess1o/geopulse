package org.github.tess1o.geopulse.geocoding.repository;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geocoding.model.GeonamesNormalizedLocation;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nearest-city normalization against a real PostGIS database, with a few synthetic GeoNames rows in northern Norway.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class GeonamesCityRepositoryTest {

    private static final long GEONAME_ID_BASE = 990_000_000L;

    @Inject
    GeonamesCityRepository geonamesCityRepository;

    @Inject
    EntityManager entityManager;

    @BeforeEach
    @Transactional
    void setUp() {
        deleteSeededCities();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        deleteSeededCities();
    }

    @Test
    @Transactional
    void returnsNearestCityBySphereDistance() {
        // At 70°N a degree of longitude is about 38 km, so East is nearer although North is fewer degrees away.
        insertCity(201, "East", 70.0, 20.5, 1_000);
        insertCity(202, "North", 70.3, 20.0, 1_000);

        Optional<GeonamesNormalizedLocation> result =
                geonamesCityRepository.findNearestNormalizedLocation(70.0, 20.0, 50_000.0);

        assertThat(result).hasValueSatisfying(location -> {
            assertThat(location.geonameId()).isEqualTo(GEONAME_ID_BASE + 201);
            assertThat(location.city()).isEqualTo("East");
            assertThat(location.countryCode()).isEqualTo("NO");
            assertThat(location.distanceMeters()).isBetween(18_000.0, 20_000.0);
        });
    }

    @Test
    @Transactional
    void appliesMaxDistance() {
        insertCity(201, "East", 70.0, 20.5, 1_000);

        assertThat(geonamesCityRepository.findNearestNormalizedLocation(70.0, 20.0, 10_000.0)).isEmpty();
        assertThat(geonamesCityRepository.findNearestNormalizedLocation(70.0, 20.0, null))
                .map(GeonamesNormalizedLocation::city)
                .hasValue("East");
    }

    @Test
    @Transactional
    void prefersLargerPopulationAtSameDistance() {
        insertCity(201, "Village", 70.0, 20.5, 600);
        insertCity(202, "Town", 70.0, 20.5, 9_000);

        assertThat(geonamesCityRepository.findNearestNormalizedLocation(70.0, 20.0, 50_000.0))
                .map(GeonamesNormalizedLocation::city)
                .hasValue("Town");
    }

    @Test
    @Transactional
    void findsCityWithinRadiusBeyondEightPlanarCandidates() {
        for (int i = 1; i <= 8; i++) {
            insertCity(i, "North " + i, 70.46 + i * 0.001, 20.0, 1_000);
        }
        insertCity(9, "East", 70.0, 20.6, 1_000);

        assertThat(geonamesCityRepository.findNearestNormalizedLocation(70.0, 20.0, 50_000.0))
                .hasValueSatisfying(location -> {
                    assertThat(location.city()).isEqualTo("East");
                    assertThat(location.distanceMeters()).isBetween(22_000.0, 23_000.0);
                });
        assertThat(geonamesCityRepository.findNearestNormalizedLocation(70.0, 20.0, null))
                .map(GeonamesNormalizedLocation::city).hasValue("East");
    }

    @Test
    @Transactional
    void findsNearestCityAcrossDateLine() {
        for (int i = 1; i <= 8; i++) {
            insertCity(i, "West " + i, 0.0, 179.0 - i * 0.01, 1_000);
        }
        insertCity(9, "Across date line", 0.0, -179.9, 1_000);

        assertThat(geonamesCityRepository.findNearestNormalizedLocation(0.0, 179.9, 50_000.0))
                .map(GeonamesNormalizedLocation::city).hasValue("Across date line");
    }

    @Test
    @Transactional
    void considersAllCitiesAtTheSameDistanceForPopulationTieBreak() {
        for (int i = 1; i <= 12; i++) {
            insertCity(i, "Village " + i, 70.0, 20.5, 600);
        }
        insertCity(13, "Town", 70.0, 20.5, 9_000);

        assertThat(geonamesCityRepository.findNearestNormalizedLocation(70.0, 20.0, 50_000.0))
                .map(GeonamesNormalizedLocation::city).hasValue("Town");
    }

    private void deleteSeededCities() {
        entityManager.createNativeQuery("DELETE FROM geonames_city WHERE geonameid > :base")
                .setParameter("base", GEONAME_ID_BASE)
                .executeUpdate();
    }

    private void insertCity(int offset, String name, double lat, double lon, long population) {
        entityManager.createNativeQuery("""
                        INSERT INTO geonames_city (geonameid, name, latitude, longitude, country_code, population)
                        VALUES (:id, :name, :lat, :lon, 'NO', :population)
                        """)
                .setParameter("id", GEONAME_ID_BASE + offset)
                .setParameter("name", name)
                .setParameter("lat", lat)
                .setParameter("lon", lon)
                .setParameter("population", population)
                .executeUpdate();
    }
}
