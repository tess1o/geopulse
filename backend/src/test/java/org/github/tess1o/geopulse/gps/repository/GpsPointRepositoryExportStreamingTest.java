package org.github.tess1o.geopulse.gps.repository;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.model.GpsPointFilterDTO;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.testsupport.ExportTestFixtures;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class GpsPointRepositoryExportStreamingTest {

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    UserRepository userRepository;

    private UserEntity testUser;
    private UUID testUserId;
    private final List<UUID> createdUserIds = new ArrayList<>();
    private int pointIndex;

    @BeforeEach
    @Transactional
    void setUp() {
        testUser = new UserEntity();
        testUser.setEmail(TestIds.uniqueEmail("gps-export-stream"));
        testUser.setFullName("GPS Export Stream Test");
        testUser.setPasswordHash("test-hash");
        testUser.setCreatedAt(Instant.now());
        userRepository.persist(testUser);
        testUserId = testUser.getId();
        createdUserIds.add(testUserId);
        pointIndex = 0;
    }

    @AfterEach
    @Transactional
    void tearDown() {
        for (UUID userId : createdUserIds) {
            gpsPointRepository.delete("user.id", userId);
            UserEntity user = userRepository.findById(userId);
            if (user != null) {
                userRepository.delete(user);
            }
        }
        createdUserIds.clear();
    }

    @Test
    @Transactional
    void streamByUserAndDateRangeForExportHandlesDuplicateTimestampsAcrossBatches() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        GpsPointEntity first = persistPoint(base, GpsSourceType.OWNTRACKS);
        GpsPointEntity second = persistPoint(base, GpsSourceType.OWNTRACKS);
        GpsPointEntity third = persistPoint(base, GpsSourceType.OWNTRACKS);
        GpsPointEntity fourth = persistPoint(base.plus(1, ChronoUnit.SECONDS), GpsSourceType.OWNTRACKS);
        GpsPointEntity fifth = persistPoint(base.plus(2, ChronoUnit.SECONDS), GpsSourceType.OWNTRACKS);
        persistPoint(null, GpsSourceType.OWNTRACKS);
        persistPointForOtherUser(base, GpsSourceType.OWNTRACKS);
        gpsPointRepository.flush();

        List<GpsPointEntity> streamed = new ArrayList<>();
        gpsPointRepository.streamByUserAndDateRangeForExport(
                testUserId,
                base.minus(1, ChronoUnit.SECONDS),
                base.plus(3, ChronoUnit.SECONDS),
                2,
                streamed::addAll);

        assertEquals(
                List.of(first.getId(), second.getId(), third.getId(), fourth.getId(), fifth.getId()),
                streamed.stream().map(GpsPointEntity::getId).toList());
    }

    @Test
    @Transactional
    void streamByUserAndFiltersUsesKeysetCursorAndHonorsFilters() {
        Instant base = Instant.parse("2026-01-02T00:00:00Z");

        GpsPointEntity firstOwnTracks = persistPoint(base, GpsSourceType.OWNTRACKS);
        GpsPointEntity gpsLogger = persistPoint(base, GpsSourceType.GPSLOGGER);
        GpsPointEntity secondOwnTracks = persistPoint(base, GpsSourceType.OWNTRACKS);
        GpsPointEntity laterOwnTracks = persistPoint(base.plus(1, ChronoUnit.SECONDS), GpsSourceType.OWNTRACKS);
        GpsPointEntity outsideRange = persistPoint(base.plus(2, ChronoUnit.SECONDS), GpsSourceType.OWNTRACKS);
        gpsPointRepository.flush();

        GpsPointFilterDTO sourceAndTimeFilters = GpsPointFilterDTO.builder()
                .startTime(base.minus(1, ChronoUnit.SECONDS))
                .endTime(base.plus(1, ChronoUnit.SECONDS))
                .sourceTypes(List.of(GpsSourceType.OWNTRACKS))
                .build();

        List<GpsPointEntity> sourceAndTimeResults = new ArrayList<>();
        gpsPointRepository.streamByUserAndFilters(testUserId, sourceAndTimeFilters, 1, sourceAndTimeResults::addAll);

        assertEquals(
                List.of(firstOwnTracks.getId(), secondOwnTracks.getId(), laterOwnTracks.getId()),
                sourceAndTimeResults.stream().map(GpsPointEntity::getId).toList());

        GpsPointFilterDTO idFilters = GpsPointFilterDTO.builder()
                .gpsPointIds(List.of(outsideRange.getId(), gpsLogger.getId()))
                .build();

        List<GpsPointEntity> idResults = new ArrayList<>();
        gpsPointRepository.streamByUserAndFilters(testUserId, idFilters, 1, idResults::addAll);

        assertEquals(
                List.of(gpsLogger.getId(), outsideRange.getId()),
                idResults.stream().map(GpsPointEntity::getId).toList());
    }

    private GpsPointEntity persistPoint(Instant timestamp, GpsSourceType sourceType) {
        int index = pointIndex++;
        GpsPointEntity point = ExportTestFixtures.gpsPoint(testUser, timestamp, 40.0 + index * 0.001, -74.0 - index * 0.001,
                100.0, 10.0, 5.0, 90.0, "test-device", sourceType);
        gpsPointRepository.persist(point);
        return point;
    }

    private void persistPointForOtherUser(Instant timestamp, GpsSourceType sourceType) {
        UserEntity otherUser = new UserEntity();
        otherUser.setEmail(TestIds.uniqueEmail("gps-export-stream-other"));
        otherUser.setFullName("Other GPS Export Stream Test");
        otherUser.setPasswordHash("test-hash");
        otherUser.setCreatedAt(Instant.now());
        userRepository.persist(otherUser);
        createdUserIds.add(otherUser.getId());

        GpsPointEntity point = ExportTestFixtures.gpsPoint(otherUser, timestamp, 41.0, -75.0,
                100.0, 10.0, 5.0, 90.0, "other-device", sourceType);
        gpsPointRepository.persist(point);
    }
}
