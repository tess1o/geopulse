package org.github.tess1o.geopulse.timeline.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineDataGapDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineDataSource;
import org.github.tess1o.geopulse.timeline.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that TimelineQueryService properly creates data gaps when no GPS data is available.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class TimelineDataGapTest {

    @Inject
    TimelineQueryService timelineQueryService;

    @Inject
    TimelineDataGapRepository timelineDataGapRepository;

    @Inject
    UserRepository userRepository;

    private UserEntity testUser;
    private UUID testUserId;

    @BeforeEach
    @Transactional
    void setUp() {
        cleanupTestData();
        testUser = createTestUser("datagap-test@geopulse.app", "Data Gap Test User");
        testUserId = testUser.getId();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        cleanupTestData();
    }

    @Test
    @Transactional
    void testGetTimeline_NoGpsData_ShouldCreateDataGap() {
        // Arrange - Request timeline for a past date where no GPS data exists
        LocalDate pastDate = LocalDate.of(2025, 8, 20);
        Instant startTime = pastDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endTime = pastDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);

        // Act - Request timeline for a period with no GPS data
        MovementTimelineDTO result = timelineQueryService.getTimeline(testUserId, startTime, endTime);

        // Assert
        assertNotNull(result, "Timeline should not be null");
        assertEquals(testUserId, result.getUserId(), "User ID should match");
        assertEquals(TimelineDataSource.CACHED, result.getDataSource(), "Data source should be CACHED");
        assertNotNull(result.getLastUpdated(), "Last updated should be set");

        // Should have no stays or trips
        assertTrue(result.getStays().isEmpty(), "Should have no stays when no GPS data");
        assertTrue(result.getTrips().isEmpty(), "Should have no trips when no GPS data");

        // Should have exactly one data gap covering the entire requested period
        assertEquals(1, result.getDataGaps().size(), "Should have exactly one data gap");
        
        TimelineDataGapDTO dataGap = result.getDataGaps().get(0);
        assertEquals(startTime, dataGap.getStartTime(), "Data gap should start at request start time");
        assertEquals(endTime, dataGap.getEndTime(), "Data gap should end at request end time");
        
        long expectedDurationMinutes = (endTime.getEpochSecond() - startTime.getEpochSecond()) / 60;
        assertEquals(expectedDurationMinutes, dataGap.getDurationMinutes(), 
                    "Data gap duration should match entire requested period");

        log.info("✅ Data gap test passed - Created gap from {} to {} ({} minutes)", 
                 dataGap.getStartTime(), dataGap.getEndTime(), dataGap.getDurationMinutes());
    }

    @Test
    @Transactional
    void testGetTimeline_MultiDayNoGpsData_ShouldCreateSingleDataGap() {
        // Arrange - Request timeline for multiple days where no GPS data exists
        LocalDate startDate = LocalDate.of(2025, 8, 18);
        LocalDate endDate = LocalDate.of(2025, 8, 20);
        Instant startTime = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endTime = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);

        // Act - Request multi-day timeline with no GPS data
        MovementTimelineDTO result = timelineQueryService.getTimeline(testUserId, startTime, endTime);

        // Assert
        assertNotNull(result, "Timeline should not be null");
        assertTrue(result.getStays().isEmpty(), "Should have no stays");
        assertTrue(result.getTrips().isEmpty(), "Should have no trips");
        assertEquals(1, result.getDataGaps().size(), "Should have exactly one data gap for entire period");
        
        TimelineDataGapDTO dataGap = result.getDataGaps().get(0);
        assertEquals(startTime, dataGap.getStartTime(), "Data gap should cover entire requested period");
        assertEquals(endTime, dataGap.getEndTime(), "Data gap should cover entire requested period");
        
        // Should be about 3 days worth of minutes (3 * 24 * 60 = 4320 minutes)
        long expectedDurationMinutes = (endTime.getEpochSecond() - startTime.getEpochSecond()) / 60;
        assertEquals(expectedDurationMinutes, dataGap.getDurationMinutes());

        log.info("✅ Multi-day data gap test passed - {} minutes gap covering {} to {}", 
                 dataGap.getDurationMinutes(), startDate, endDate);
    }

    private UserEntity createTestUser(String email, String fullName) {
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPasswordHash("test-hash");
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        userRepository.persist(user);
        return user;
    }

    @Transactional
    void cleanupTestData() {
        // Clean up data gaps first to avoid foreign key constraint violations
        timelineDataGapRepository.delete("user.email like ?1", "%@geopulse.app");
        // Then clean up users
        userRepository.delete("email like ?1", "%@geopulse.app");
    }
}