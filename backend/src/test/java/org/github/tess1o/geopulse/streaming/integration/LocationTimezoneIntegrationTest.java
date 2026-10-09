package org.github.tess1o.geopulse.streaming.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geocoding.dto.LocationTimezoneDTO;
import org.github.tess1o.geopulse.geocoding.dto.LocationTimezoneDTO.Status;
import org.github.tess1o.geopulse.geocoding.repository.GeonamesTimezoneRepository;
import org.github.tess1o.geopulse.geocoding.service.LocationTimezoneService;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.model.domain.LocationSource;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineDataGapDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineTripDTO;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineAggregator;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Location-local timezones resolved from GeoNames, against a real PostGIS database.
 * Uses a handful of synthetic GeoNames rows around the Polish-Ukrainian border and in Bulgaria.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class LocationTimezoneIntegrationTest {

    private static final long GEONAME_ID_BASE = 990_000_000L;
    private static final Instant DAY = Instant.parse("2025-07-10T00:00:00Z");

    @Inject
    StreamingTimelineAggregator aggregator;

    @Inject
    LocationTimezoneService locationTimezoneService;

    @Inject
    GeonamesTimezoneRepository geonamesTimezoneRepository;

    @Inject
    EntityManager entityManager;

    @Inject
    ObjectMapper objectMapper;

    private UUID userId;

    @BeforeEach
    @Transactional
    void setUp() {
        deleteSeededCities();
        insertCity(1, "Kyiv", "UA", "Europe/Kyiv", 50.4501, 30.5234);
        insertCity(2, "Lviv", "UA", "Europe/Kyiv", 49.8397, 24.0297);
        insertCity(3, "Volodymyr", "UA", "Europe/Kyiv", 50.8500, 24.3200);
        insertCity(4, "Przemyśl", "PL", "Europe/Warsaw", 49.7838, 22.7678);
        insertCity(5, "Hrubieszów", "PL", "Europe/Warsaw", 50.8049, 23.8920);
        insertCity(6, "Sofia", "BG", "Europe/Sofia", 42.6977, 23.3219);
        insertCity(7, "Broken", "XX", "Not/AZone", -60.0, -60.0);

        UserEntity user = UserEntity.builder()
                .email(TestIds.uniqueEmail("it-location-tz"))
                .fullName("Location TZ")
                .timezone("Europe/Kyiv")
                .isActive(true)
                .build();
        entityManager.persist(user);
        userId = user.getId();

        // Kyiv stay -> trip Lviv to Przemyśl -> data gap -> Sofia stay -> mid-Atlantic stay
        createStay(user, DAY.plusSeconds(6 * 3600), 3600, 50.4510, 30.5200);
        createTrip(user, DAY.plusSeconds(8 * 3600), 7200, 49.8400, 24.0300, 49.7840, 22.7700);
        createGap(user, DAY.plusSeconds(10 * 3600), DAY.plusSeconds(14 * 3600));
        createStay(user, DAY.plusSeconds(14 * 3600), 3600, 42.6980, 23.3220);
        createStay(user, DAY.plusSeconds(16 * 3600), 3600, 0.0, -30.0);
        entityManager.flush();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        deleteSeededCities();
    }

    @Test
    void timelineWithoutFlag_HasNoTimezoneFieldsAndUnchangedJson() throws Exception {
        MovementTimelineDTO timeline = aggregator.getTimelineFromDb(userId, DAY, DAY.plusSeconds(86_400), false);

        assertThat(timeline.getStays()).hasSize(3).allSatisfy(stay -> assertThat(stay.getLocationTimezone()).isNull());
        assertThat(timeline.getTrips()).hasSize(1).allSatisfy(trip -> {
            assertThat(trip.getStartLocationTimezone()).isNull();
            assertThat(trip.getEndLocationTimezone()).isNull();
        });
        assertThat(timeline.getDataGaps()).hasSize(1).allSatisfy(gap -> {
            assertThat(gap.getStartLocationTimezone()).isNull();
            assertThat(gap.getEndLocationTimezone()).isNull();
        });

        String json = objectMapper.writeValueAsString(timeline);
        assertThat(json).doesNotContain("LocationTimezone").doesNotContain("locationTimezone");

        MovementTimelineDTO legacy = aggregator.getTimelineFromDb(userId, DAY, DAY.plusSeconds(86_400));
        legacy.setLastUpdated(timeline.getLastUpdated());
        assertThat(objectMapper.writeValueAsString(legacy)).isEqualTo(json);
    }

    @Test
    void timelineWithFlag_ResolvesStaysTripsAndGaps() {
        MovementTimelineDTO timeline = aggregator.getTimelineFromDb(userId, DAY, DAY.plusSeconds(86_400), true);

        List<TimelineStayLocationDTO> stays = timeline.getStays().stream()
                .sorted(Comparator.comparing(TimelineStayLocationDTO::getTimestamp))
                .toList();
        assertResolved(stays.get(0).getLocationTimezone(), "Europe/Kyiv", "Kyiv", "UA");
        assertResolved(stays.get(1).getLocationTimezone(), "Europe/Sofia", "Sofia", "BG");

        LocationTimezoneDTO atlantic = stays.get(2).getLocationTimezone();
        assertThat(atlantic.getStatus()).isEqualTo(Status.BEYOND_MAX_DISTANCE);
        assertThat(atlantic.getTimezone()).isNull();
        assertThat(atlantic.getNearestCity()).isNotNull();
        assertThat(atlantic.getDistanceKm()).isGreaterThan(locationTimezoneService.getMaxDistanceKm());

        TimelineTripDTO trip = timeline.getTrips().getFirst();
        assertResolved(trip.getStartLocationTimezone(), "Europe/Kyiv", "Lviv", "UA");
        assertResolved(trip.getEndLocationTimezone(), "Europe/Warsaw", "Przemyśl", "PL");

        TimelineDataGapDTO gap = timeline.getDataGaps().getFirst();
        assertThat(gap.getStartLocationTimezone().getTimezone()).isEqualTo("Europe/Warsaw");
        assertThat(gap.getEndLocationTimezone().getTimezone()).isEqualTo("Europe/Sofia");
    }

    @Test
    void resolve_PicksNearestCityAcrossBorder() {
        // Both points sit between Hrubieszów (PL) and Volodymyr (UA), about 30 km apart.
        List<LocationTimezoneDTO> zones = locationTimezoneService.resolve(
                new double[]{50.81, 50.85}, new double[]{23.95, 24.25}, "test");

        assertThat(zones.get(0).getTimezone()).isEqualTo("Europe/Warsaw");
        assertThat(zones.get(0).getNearestCity()).isEqualTo("Hrubieszów");
        assertThat(zones.get(1).getTimezone()).isEqualTo("Europe/Kyiv");
        assertThat(zones.get(1).getNearestCity()).isEqualTo("Volodymyr");
    }

    @Test
    void resolve_ReportsInvalidTimezoneFromDataset() {
        LocationTimezoneDTO zone = locationTimezoneService.resolve(-60.01, -60.01, "test");

        assertThat(zone.getStatus()).isEqualTo(Status.INVALID_TIMEZONE);
        assertThat(zone.getTimezone()).isNull();
        assertThat(zone.getNearestCity()).isEqualTo("Broken");
    }

    @Test
    void resolve_KeepsInputOrderWithDuplicatesAndEmptyInput() {
        List<LocationTimezoneDTO> zones = locationTimezoneService.resolve(
                new double[]{42.70, 50.45, 42.70}, new double[]{23.32, 30.52, 23.32}, "test");

        assertThat(zones).extracting(LocationTimezoneDTO::getTimezone)
                .containsExactly("Europe/Sofia", "Europe/Kyiv", "Europe/Sofia");
        assertThat(locationTimezoneService.resolve(new double[0], new double[0], "test")).isEmpty();
    }

    @Test
    void migration_CreatesSpatialIndexUsedByTheLookup() {
        List<?> index = entityManager.createNativeQuery("""
                        SELECT 1 FROM pg_indexes
                        WHERE tablename = 'geonames_city' AND indexname = 'idx_geonames_city_geom_gist'
                        """)
                .getResultList();

        assertThat(index).hasSize(1);
    }

    @Test
    void findCountryForTimezone_UsesMostCommonCountryAndKnownAliases() {
        assertThat(locationTimezoneService.findCountryForTimezone("Europe/Kyiv")).contains("UA");
        assertThat(locationTimezoneService.findCountryForTimezone("Europe/Kiev")).contains("UA");
        assertThat(locationTimezoneService.findCountryForTimezone("Europe/Warsaw")).contains("PL");
        assertThat(locationTimezoneService.findCountryForTimezone("UTC")).isEmpty();
        assertThat(locationTimezoneService.findCountryForTimezone(null)).isEmpty();
    }

    @Test
    void assign_SetsEachItemZoneInOrder() {
        List<double[]> points = List.of(new double[]{42.70, 23.32}, new double[]{49.78, 22.77});
        List<String> assigned = new java.util.ArrayList<>();

        locationTimezoneService.assign(points, p -> p[0], p -> p[1],
                (p, zone) -> assigned.add(zone.getTimezone()), "test");

        assertThat(assigned).containsExactly("Europe/Sofia", "Europe/Warsaw");
    }

    @Test
    void resolve_WithoutGeonamesData_ReportsNoData() {
        QuarkusTransaction.requiringNew().run(this::deleteSeededCities);
        if (geonamesTimezoneRepository.hasAnyCity()) {
            return; // another dataset is loaded in this database; nothing to assert
        }

        LocationTimezoneDTO zone = locationTimezoneService.resolve(50.45, 30.52, "test");

        assertThat(zone.getStatus()).isEqualTo(Status.NO_GEONAMES_DATA);
        assertThat(zone.getTimezone()).isNull();
        assertThat(locationTimezoneService.isAvailable()).isFalse();
    }

    private static void assertResolved(LocationTimezoneDTO zone, String timezone, String city, String countryCode) {
        assertThat(zone).isNotNull();
        assertThat(zone.getStatus()).isEqualTo(Status.RESOLVED);
        assertThat(zone.getTimezone()).isEqualTo(timezone);
        assertThat(zone.getNearestCity()).isEqualTo(city);
        assertThat(zone.getCountryCode()).isEqualTo(countryCode);
    }

    private void deleteSeededCities() {
        entityManager.createNativeQuery("DELETE FROM geonames_city WHERE geonameid > :base")
                .setParameter("base", GEONAME_ID_BASE)
                .executeUpdate();
    }

    private void insertCity(int offset, String name, String countryCode, String timezone, double lat, double lon) {
        entityManager.createNativeQuery("""
                        INSERT INTO geonames_city (geonameid, name, latitude, longitude, country_code, timezone)
                        VALUES (:id, :name, :lat, :lon, :cc, :tz)
                        """)
                .setParameter("id", GEONAME_ID_BASE + offset)
                .setParameter("name", name)
                .setParameter("lat", lat)
                .setParameter("lon", lon)
                .setParameter("cc", countryCode)
                .setParameter("tz", timezone)
                .executeUpdate();
    }

    private void createStay(UserEntity user, Instant start, long durationSeconds, double lat, double lon) {
        TimelineStayEntity stay = new TimelineStayEntity();
        stay.setUser(user);
        stay.setTimestamp(start);
        stay.setLocation(GeoUtils.createPoint(lon, lat));
        stay.setStayDuration(durationSeconds);
        stay.setLocationName("Stay " + lat + "," + lon);
        stay.setLocationSource(LocationSource.HISTORICAL);
        stay.setLastUpdated(Instant.now());
        stay.setCreatedAt(Instant.now());
        entityManager.persist(stay);
    }

    private void createTrip(UserEntity user, Instant start, long durationSeconds,
                            double startLat, double startLon, double endLat, double endLon) {
        TimelineTripEntity trip = new TimelineTripEntity();
        trip.setUser(user);
        trip.setTimestamp(start);
        trip.setTripDuration(durationSeconds);
        trip.setDistanceMeters(100_000);
        trip.setMovementType("CAR");
        trip.setStartPoint(GeoUtils.createPoint(startLon, startLat));
        trip.setEndPoint(GeoUtils.createPoint(endLon, endLat));
        trip.setLastUpdated(Instant.now());
        trip.setCreatedAt(Instant.now());
        entityManager.persist(trip);
    }

    private void createGap(UserEntity user, Instant start, Instant end) {
        TimelineDataGapEntity gap = new TimelineDataGapEntity();
        gap.setUser(user);
        gap.setStartTime(start);
        gap.setEndTime(end);
        gap.setDurationSeconds(end.getEpochSecond() - start.getEpochSecond());
        gap.setCreatedAt(Instant.now());
        entityManager.persist(gap);
    }
}
