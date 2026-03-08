package org.github.tess1o.geopulse.insight.service.badge;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.CleanupHelper;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.insight.model.Badge;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class, restrictToAnnotatedClass = true)
@SerializedDatabaseTest
class BadgeTimestampIntegrationTest {

    @Inject
    LongHaulerBadgeCalculator longHaulerBadgeCalculator;

    @Inject
    SingleTripDistanceBadgeCalculator singleTripDistanceBadgeCalculator;

    @Inject
    FirstStepsBadgeCalculator firstStepsBadgeCalculator;

    @Inject
    TimeOfDayBadgeCalculator timeOfDayBadgeCalculator;

    @Inject
    FirstMonthBadgeCalculator firstMonthBadgeCalculator;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    @Inject
    CleanupHelper cleanupHelper;

    private UserEntity testUser;

    @BeforeEach
    @Transactional
    void setUp() {
        cleanupHelper.cleanupAll();

        testUser = new UserEntity();
        testUser.setEmail("badge-" + System.nanoTime() + "@geopulse.app");
        testUser.setFullName("Badge Test User");
        testUser.setPasswordHash("test-hash");
        testUser.setTimezone("Europe/Kyiv");
        userRepository.persist(testUser);

        createTrip("2026-03-06T03:15:00Z", 1_500, 3_600, "WALK");
        createTrip("2026-03-06T18:00:00Z", 5_000, 7_200, "WALK");
        createTrip("2026-03-06T20:30:00Z", 10_000, 14_400, "WALK");

        LocalDate startDate = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 30; i++) {
            Instant timestamp = startDate.plusDays(i).atTime(5, 30).toInstant(ZoneOffset.UTC);
            createGpsPoint(timestamp);
        }

        entityManager.flush();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        cleanupHelper.cleanupAll();
    }

    @Test
    @Transactional
    void longHaulerShouldReturnCorrectEarnedDateFromNativeTimestamp() {
        Badge badge = longHaulerBadgeCalculator.calculateBadge(testUser.getId());

        assertTrue(badge.isEarned());
        assertEquals("2026-03-06", badge.getEarnedDate());
    }

    @Test
    @Transactional
    void singleTripDistanceShouldReturnCorrectEarnedDateFromNativeTimestamp() {
        Badge badge = singleTripDistanceBadgeCalculator.calculateSingleTripDistanceBadge(
                testUser.getId(),
                "single_trip_test",
                "Single Trip Test",
                "runner",
                1_000,
                "Single trip threshold test"
        );

        assertTrue(badge.isEarned());
        assertEquals("2026-03-06", badge.getEarnedDate());
    }

    @Test
    @Transactional
    void firstStepsShouldReturnCorrectEarnedDateFromNativeTimestamp() {
        Badge badge = firstStepsBadgeCalculator.calculateBadge(testUser.getId());

        assertTrue(badge.isEarned());
        assertEquals("2026-03-06", badge.getEarnedDate());
    }

    @Test
    @Transactional
    void timeOfDayShouldReturnCorrectEarnedDateFromNativeTimestamp() {
        Badge badge = timeOfDayBadgeCalculator.calculateTimeOfDayBadge(
                testUser.getId(),
                "early_bird_test",
                "Early Bird Test",
                "bird",
                "< 6",
                "Test early trip badge"
        );

        assertTrue(badge.isEarned());
        assertEquals("2026-03-06", badge.getEarnedDate());
    }

    @Test
    @Transactional
    void firstMonthShouldReturnCorrectEarnedDateFromNativeTimestamp() {
        Badge badge = firstMonthBadgeCalculator.calculateBadge(testUser.getId());

        assertTrue(badge.isEarned());
        assertEquals("January 1, 2026", badge.getEarnedDate());
    }

    private void createTrip(String timestamp, long distanceMeters, long durationSeconds, String movementType) {
        TimelineTripEntity trip = new TimelineTripEntity();
        trip.setUser(testUser);
        trip.setTimestamp(Instant.parse(timestamp));
        trip.setDistanceMeters(distanceMeters);
        trip.setTripDuration(durationSeconds);
        trip.setMovementType(movementType);
        trip.setStartPoint(GeoUtils.createPoint(25.595304, 49.550959));
        trip.setEndPoint(GeoUtils.createPoint(25.595555, 49.547470));
        trip.setLastUpdated(Instant.now());
        trip.setCreatedAt(Instant.now());
        entityManager.persist(trip);
    }

    private void createGpsPoint(Instant timestamp) {
        GpsPointEntity point = new GpsPointEntity();
        point.setUser(testUser);
        point.setTimestamp(timestamp);
        point.setCoordinates(GeoUtils.createPoint(25.595304, 49.550959));
        point.setSourceType(GpsSourceType.OWNTRACKS);
        point.setCreatedAt(Instant.now());
        entityManager.persist(point);
    }
}
