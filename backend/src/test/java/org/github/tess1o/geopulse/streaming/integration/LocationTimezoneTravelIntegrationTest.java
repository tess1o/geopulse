package org.github.tess1o.geopulse.streaming.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the web app's "Local time at each place" day boundaries rely on, against a real PostGIS database.
 * <p>
 * The app widens the requested range by 26 hours (the largest gap between two UTC offsets), then dates every item by
 * the local clock at its start and end. That only works if the timeline returns items overlapping the widened range
 * and every trip and data gap carries the zones of both of its ends, including flights between continents.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class LocationTimezoneTravelIntegrationTest {

    private static final long GEONAME_ID_BASE = 990_000_000L;
    /** Must match LOCATION_DAY_FETCH_PADDING_HOURS in frontend/src/composables/useTimezone.js. */
    private static final Duration LOCAL_DAY_PADDING = Duration.ofHours(26);

    private static final double[] PARIS = {48.8600, 2.3400};
    private static final double[] LOS_ANGELES = {34.0500, -118.2500};
    private static final double[] NEW_YORK = {40.7589, -73.9851};
    private static final double[] TOKYO = {35.6900, 139.7000};
    private static final double[] HONOLULU = {21.2800, -157.8300};

    @Inject
    StreamingTimelineAggregator aggregator;

    @Inject
    EntityManager entityManager;

    private UserEntity user;

    @BeforeEach
    @Transactional
    void setUp() {
        deleteSeededCities();
        insertCity(101, "Paris", "FR", "Europe/Paris", 48.8566, 2.3522);
        insertCity(102, "Los Angeles", "US", "America/Los_Angeles", 34.0522, -118.2437);
        insertCity(103, "New York", "US", "America/New_York", 40.7128, -74.0060);
        insertCity(104, "Tokyo", "JP", "Asia/Tokyo", 35.6895, 139.6917);
        insertCity(105, "Honolulu", "US", "Pacific/Honolulu", 21.3069, -157.8583);

        user = UserEntity.builder()
                .email(TestIds.uniqueEmail("it-location-travel"))
                .fullName("Location Travel")
                .timezone("Europe/Paris")
                .isActive(true)
                .build();
        entityManager.persist(user);
    }

    @AfterEach
    @Transactional
    void tearDown() {
        deleteSeededCities();
    }

    @Test
    @Transactional
    void westboundFlight_EveningInCaliforniaNeedsThePaddedRange() {
        // Paris 08:00-13:00 CEST, fly 11.5 h, Los Angeles from 15:30 PDT (00:30 CEST the next day) until 08:00 PDT
        createStay(Instant.parse("2025-09-21T06:00:00Z"), Duration.ofHours(5), PARIS);
        createTrip(Instant.parse("2025-09-21T11:00:00Z"), Duration.ofMinutes(690), PARIS, LOS_ANGELES);
        createStay(Instant.parse("2025-09-21T22:30:00Z"), Duration.ofMinutes(990), LOS_ANGELES);
        entityManager.flush();

        // Sep 21 in the Paris profile timezone
        Instant dayStart = Instant.parse("2025-09-20T22:00:00Z");
        Instant dayEnd = Instant.parse("2025-09-21T21:59:59.999Z");

        MovementTimelineDTO profileDay = aggregator.getTimelineFromDb(user.getId(), dayStart, dayEnd, true);
        assertThat(profileDay.getStays()).hasSize(1);

        MovementTimelineDTO padded = aggregator.getTimelineFromDb(
                user.getId(), dayStart.minus(LOCAL_DAY_PADDING), dayEnd.plus(LOCAL_DAY_PADDING), true);

        List<TimelineStayLocationDTO> stays = sortedStays(padded);
        assertThat(stays).hasSize(2);
        assertThat(stays.get(0).getLocationTimezone().getTimezone()).isEqualTo("Europe/Paris");
        assertThat(stays.get(1).getLocationTimezone().getTimezone()).isEqualTo("America/Los_Angeles");

        TimelineTripDTO flight = padded.getTrips().getFirst();
        assertThat(flight.getStartLocationTimezone().getTimezone()).isEqualTo("Europe/Paris");
        assertThat(flight.getEndLocationTimezone().getTimezone()).isEqualTo("America/Los_Angeles");
    }

    @Test
    @Transactional
    void eastboundOvernightFlight_CarriesDepartureAndArrivalZones() {
        // Los Angeles 17:00 PDT -> Paris 12:30 CEST the next day
        createStay(Instant.parse("2025-09-21T16:00:00Z"), Duration.ofHours(7), LOS_ANGELES);
        createTrip(Instant.parse("2025-09-22T00:00:00Z"), Duration.ofMinutes(630), LOS_ANGELES, PARIS);
        createStay(Instant.parse("2025-09-22T10:30:00Z"), Duration.ofHours(10), PARIS);
        entityManager.flush();

        // Sep 22 in a Los Angeles profile timezone, padded: the flight started the evening before
        Instant dayStart = Instant.parse("2025-09-22T07:00:00Z");
        Instant dayEnd = Instant.parse("2025-09-23T06:59:59.999Z");
        MovementTimelineDTO padded = aggregator.getTimelineFromDb(
                user.getId(), dayStart.minus(LOCAL_DAY_PADDING), dayEnd.plus(LOCAL_DAY_PADDING), true);

        TimelineTripDTO flight = padded.getTrips().getFirst();
        assertThat(flight.getTimestamp()).isEqualTo(Instant.parse("2025-09-22T00:00:00Z"));
        assertThat(flight.getStartLocationTimezone().getTimezone()).isEqualTo("America/Los_Angeles");
        assertThat(flight.getEndLocationTimezone().getTimezone()).isEqualTo("Europe/Paris");
        assertThat(sortedStays(padded))
                .extracting(stay -> stay.getLocationTimezone().getTimezone())
                .containsExactly("America/Los_Angeles", "Europe/Paris");
    }

    @Test
    @Transactional
    void dataGapDuringAFlight_TakesBothZonesOnlyWhenItsNeighboursAreLoaded() {
        // New York until 18:00 EDT, no data during the flight, Paris from 10:00 CEST the next day
        createStay(Instant.parse("2025-09-21T14:00:00Z"), Duration.ofHours(8), NEW_YORK);
        createGap(Instant.parse("2025-09-21T22:00:00Z"), Instant.parse("2025-09-22T08:00:00Z"));
        createStay(Instant.parse("2025-09-22T08:00:00Z"), Duration.ofHours(4), PARIS);
        entityManager.flush();

        // A range inside the gap returns the gap alone, so it has no neighbour to take zones from
        Instant midFlightStart = Instant.parse("2025-09-22T00:00:00Z");
        Instant midFlightEnd = Instant.parse("2025-09-22T07:00:00Z");
        MovementTimelineDTO narrow = aggregator.getTimelineFromDb(user.getId(), midFlightStart, midFlightEnd, true);
        assertThat(narrow.getStays()).isEmpty();
        TimelineDataGapDTO isolatedGap = narrow.getDataGaps().getFirst();
        assertThat(isolatedGap.getStartLocationTimezone()).isNull();
        assertThat(isolatedGap.getEndLocationTimezone()).isNull();

        // The padded range brings in the stays on both sides
        MovementTimelineDTO padded = aggregator.getTimelineFromDb(
                user.getId(), midFlightStart.minus(LOCAL_DAY_PADDING), midFlightEnd.plus(LOCAL_DAY_PADDING), true);
        TimelineDataGapDTO gap = padded.getDataGaps().getFirst();
        assertThat(gap.getStartLocationTimezone().getTimezone()).isEqualTo("America/New_York");
        assertThat(gap.getEndLocationTimezone().getTimezone()).isEqualTo("Europe/Paris");
    }

    @Test
    @Transactional
    void flightAcrossTheDateLine_ArrivesInHonoluluTime() {
        // Tokyo 10:00 JST on Sep 21 -> Honolulu 22:00 HST on Sep 20, an earlier calendar date
        createStay(Instant.parse("2025-09-21T00:00:00Z"), Duration.ofHours(1), TOKYO);
        createTrip(Instant.parse("2025-09-21T01:00:00Z"), Duration.ofHours(7), TOKYO, HONOLULU);
        createStay(Instant.parse("2025-09-21T08:00:00Z"), Duration.ofHours(10), HONOLULU);
        entityManager.flush();

        // Sep 21 in a Tokyo profile timezone, padded
        Instant dayStart = Instant.parse("2025-09-20T15:00:00Z");
        Instant dayEnd = Instant.parse("2025-09-21T14:59:59.999Z");
        MovementTimelineDTO padded = aggregator.getTimelineFromDb(
                user.getId(), dayStart.minus(LOCAL_DAY_PADDING), dayEnd.plus(LOCAL_DAY_PADDING), true);

        TimelineTripDTO flight = padded.getTrips().getFirst();
        assertThat(flight.getStartLocationTimezone().getTimezone()).isEqualTo("Asia/Tokyo");
        assertThat(flight.getEndLocationTimezone().getTimezone()).isEqualTo("Pacific/Honolulu");
        assertThat(sortedStays(padded))
                .extracting(stay -> stay.getLocationTimezone().getTimezone())
                .containsExactly("Asia/Tokyo", "Pacific/Honolulu");
    }

    private static List<TimelineStayLocationDTO> sortedStays(MovementTimelineDTO timeline) {
        return timeline.getStays().stream()
                .sorted(Comparator.comparing(TimelineStayLocationDTO::getTimestamp))
                .toList();
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

    private void createStay(Instant start, Duration duration, double[] place) {
        TimelineStayEntity stay = new TimelineStayEntity();
        stay.setUser(user);
        stay.setTimestamp(start);
        stay.setLocation(GeoUtils.createPoint(place[1], place[0]));
        stay.setStayDuration(duration.toSeconds());
        stay.setLocationName("Stay " + place[0] + "," + place[1]);
        stay.setLocationSource(LocationSource.HISTORICAL);
        stay.setLastUpdated(Instant.now());
        stay.setCreatedAt(Instant.now());
        entityManager.persist(stay);
    }

    private void createTrip(Instant start, Duration duration, double[] from, double[] to) {
        TimelineTripEntity trip = new TimelineTripEntity();
        trip.setUser(user);
        trip.setTimestamp(start);
        trip.setTripDuration(duration.toSeconds());
        trip.setDistanceMeters(9_000_000);
        trip.setMovementType("FLIGHT");
        trip.setStartPoint(GeoUtils.createPoint(from[1], from[0]));
        trip.setEndPoint(GeoUtils.createPoint(to[1], to[0]));
        trip.setLastUpdated(Instant.now());
        trip.setCreatedAt(Instant.now());
        entityManager.persist(trip);
    }

    private void createGap(Instant start, Instant end) {
        TimelineDataGapEntity gap = new TimelineDataGapEntity();
        gap.setUser(user);
        gap.setStartTime(start);
        gap.setEndTime(end);
        gap.setDurationSeconds(end.getEpochSecond() - start.getEpochSecond());
        gap.setCreatedAt(Instant.now());
        entityManager.persist(gap);
    }
}
