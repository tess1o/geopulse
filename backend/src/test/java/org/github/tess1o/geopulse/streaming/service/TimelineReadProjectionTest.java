package org.github.tess1o.geopulse.streaming.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.favorites.model.*;
import org.github.tess1o.geopulse.gps.mapper.GpsPointMapper;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.streaming.model.dto.*;
import org.github.tess1o.geopulse.streaming.model.entity.*;
import org.github.tess1o.geopulse.streaming.repository.*;
import org.github.tess1o.geopulse.streaming.service.converters.StreamingTimelineConverter;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineReadProjectionTest {
    @Inject EntityManager em;
    @Inject TimelineReadRepository reads;
    @Inject TimelineStayRepository stays;
    @Inject TimelineTripRepository trips;
    @Inject TimelineDataGapRepository gaps;
    @Inject GpsPointRepository gps;
    @Inject StreamingTimelineConverter converter;
    @Inject GpsPointMapper mapper;
    @Inject StreamingTimelineAggregator aggregator;
    private static final Instant START = Instant.parse("2026-11-02T00:00:00Z");
    private static final Instant END = START.plusSeconds(86400);
    private final GeometryFactory geometry = new GeometryFactory();

    @Test
    @Transactional
    void timelineProjectionsMatchEntityConversionsWithoutManagingEntities() {
        UserEntity user = user();
        var favorite = FavoritesEntity.builder().user(user).name("Home").city("Kyiv").country("Ukraine")
                .type(FavoriteLocationType.POINT).geometry(point()).build();
        em.persist(favorite);
        var explanation = new LinkedHashMap<String, Object>();
        explanation.put("start", Map.of("code", "FIRST_POINT", "params", Map.of()));
        for (Instant time : List.of(START.minusSeconds(600), START, END, END.plusSeconds(1))) {
            em.persist(TimelineStayEntity.builder().user(user).timestamp(time).stayDuration(1200)
                    .location(point()).locationName("Home").visitName("Visit").favoriteLocation(favorite)
                    .explanation(explanation).build());
            em.persist(TimelineTripEntity.builder().user(user).timestamp(time).tripDuration(1200)
                    .startPoint(point()).endPoint(point()).distanceMeters(1234).movementType("WALK")
                    .explanation(explanation).build());
            em.persist(TimelineDataGapEntity.builder().user(user).startTime(time).endTime(time.plusSeconds(1200))
                    .explanation(explanation).build());
        }
        var geocoding = new org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity();
        geocoding.setUser(user);
        geocoding.setRequestCoordinates(point());
        geocoding.setDisplayName("Geocoded place");
        geocoding.setProviderName("test");
        geocoding.setCity("Other city");
        geocoding.setCountry("Other country");
        em.persist(geocoding);
        em.persist(TimelineStayEntity.builder().user(user).timestamp(START.plusSeconds(1)).stayDuration(60)
                .location(point()).locationName("Geocoded place").geocodingLocation(geocoding).build());
        // A favorite with no city must not fall back to geocoding city when both references are present.
        favorite.setCity(null);
        em.persist(TimelineStayEntity.builder().user(user).timestamp(START.plusSeconds(2)).stayDuration(60)
                .location(point()).locationName("Favorite").favoriteLocation(favorite).geocodingLocation(geocoding).build());
        em.flush();
        var expectedStays = stays.findByUserIdAndTimeRangeWithExpansion(user.getId(), START, END).stream()
                .map(converter::convertStayEntityToDto).toList();
        var expectedTrips = trips.findByUserIdAndTimeRangeWithExpansion(user.getId(), START, END).stream()
                .map(converter::convertTripEntityToDto).toList();
        var expectedGapIds = gaps.findByUserIdAndTimeRangeWithExpansion(user.getId(), START, END).stream()
                .map(TimelineDataGapEntity::getId).toList();
        UUID id = user.getId();
        em.clear();
        assertThat(reads.findStays(id, START, END)).isEqualTo(expectedStays);
        assertThat(reads.findTrips(id, START, END)).isEqualTo(expectedTrips);
        var projectedGaps = reads.findGaps(id, START, END, START);
        assertThat(projectedGaps).extracting(TimelineDataGapDTO::getId).containsExactlyElementsOf(expectedGapIds);
        assertThat(projectedGaps).filteredOn(TimelineDataGapDTO::isOngoing)
                .extracting(TimelineDataGapDTO::getStartTime).containsExactly(START);
        assertThat(reads.findStays(id, START, END, 1)).hasSize(1);
        assertThat(reads.findTrips(id, START, END, 0)).isEmpty();
        assertThat(reads.findStays(UUID.randomUUID(), START, END)).isEmpty();
        assertThat(em.unwrap(Session.class).getStatistics().getEntityCount()).isZero();
        assertThat(aggregator.getTimelineFromDb(id, START, END).getStays()).hasSize(expectedStays.size());
    }

    @Test
    @Transactional
    void denseGpsProjectionPreservesScalarsAndTelemetryWithoutEntitySnapshots() {
        UserEntity user = user();
        for (int i = 0; i < 1000; i++) {
            var point = new GpsPointEntity();
            point.setUser(user);
            point.setCoordinates(point());
            point.setTimestamp(START.plusSeconds(i));
            point.setAccuracy(i % 2 == 0 ? null : 100.0);
            point.setSourceType(GpsSourceType.OWNTRACKS);
            point.setTelemetry(new LinkedHashMap<>(Map.of("nested", Map.of("value", 12))));
            em.persist(point);
        }
        em.flush();
        var entities = gps.findEligibleByUserIdAndTimePeriod(user.getId(), START, END, 50.0);
        var expected = mapper.toPathPoints(entities);
        var allEntities = gps.findByUserIdAndTimePeriod(user.getId(), START, END);
        var expectedPath = mapper.toPathPoints(allEntities);
        var expectedRaw = mapper.toRawGpsPointMapPointDTOs(entities.subList(0, 123));
        UUID id = user.getId();
        em.clear();
        assertThat(gps.findPathPoints(id, START, END)).isEqualTo(expectedPath);
        assertThat(gps.findRawMapPoints(id, START, END, 123, 50.0)).isEqualTo(expectedRaw);
        var matching = gps.findMapMatchingPoints(id, START, END, 50.0);
        assertThat(matching).hasSize(expected.size());
        for (int i = 0; i < matching.size(); i++) {
            assertThat(matching.get(i).getLatitude()).isEqualTo(expected.get(i).getLatitude());
            assertThat(matching.get(i).getLongitude()).isEqualTo(expected.get(i).getLongitude());
            assertThat(matching.get(i).getTimestamp()).isEqualTo(expected.get(i).getTimestamp());
            assertThat(matching.get(i).getAccuracy()).isEqualTo(expected.get(i).getAccuracy());
        }
        var history = gps.findLocationHistory(id, START, END);
        assertThat(history).hasSize(1000);
        assertThat(history.getFirst().getLatitude()).isEqualTo(expectedPath.getFirst().getLatitude());
        assertThat(gps.findLatestLocation(id).getTimestamp()).isEqualTo(START.plusSeconds(999));
        var rows = gps.findPathRows(id, START, END, 50.0);
        assertThat(rows).hasSize(500);
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            var old = expected.get(i);
            assertThat(row.id()).isEqualTo(old.getId());
            assertThat(row.longitude()).isEqualTo(old.getLongitude());
            assertThat(row.latitude()).isEqualTo(old.getLatitude());
            assertThat(row.timestamp()).isEqualTo(old.getTimestamp());
            assertThat(row.accuracy()).isEqualTo(old.getAccuracy());
            assertThat(row.altitude()).isNull();
            assertThat(row.velocity()).isNull();
            assertThat(row.telemetry()).containsEntry("nested", Map.of("value", 12));
        }
        assertThat(gps.findPathRows(id, START, END, null)).hasSize(1000);
        assertThat(gps.findPathRows(UUID.randomUUID(), START, END, null)).isEmpty();
        assertThat(gps.findLatestTimestamp(id)).isEqualTo(START.plusSeconds(999));
        assertThat(em.unwrap(Session.class).getStatistics().getEntityCount()).isZero();
    }

    @Test
    @Transactional
    void correctionMetadataUsesCountsAndIdsAndExcludesMigratedOverrides() {
        var user = user();
        var stay = TimelineStayEntity.builder().user(user).timestamp(START).stayDuration(1200)
                .location(point()).locationName("Home").build();
        em.persist(stay);
        for (int i = 0; i < 2; i++) {
            var edit = TimelineEditEntity.builder().user(user).seq(i + 1)
                    .kind(org.github.tess1o.geopulse.streaming.edits.TimelineEditKind.SET_VISIT_NAME)
                    .anchorStart(START).anchorEnd(END).affectedFrom(START).affectedTo(END).build();
            em.persist(edit);
            em.persist(TimelineEditApplicationEntity.builder().edit(edit).targetType("STAY")
                    .targetId(stay.getId()).build());
        }
        var active = TimelineDataGapStayOverrideEntity.builder().user(user).stay(stay)
                .locationStrategy(org.github.tess1o.geopulse.streaming.model.shared.DataGapStayOverrideLocationStrategy.LATEST_POINT)
                .sourceGapStartTime(START).sourceGapEndTime(END).sourceGapDurationSeconds(86400).build();
        em.persist(active);
        em.persist(TimelineDataGapStayOverrideEntity.builder().user(user).stay(stay)
                .locationStrategy(org.github.tess1o.geopulse.streaming.model.shared.DataGapStayOverrideLocationStrategy.LATEST_POINT)
                .sourceGapStartTime(START.plusSeconds(1)).sourceGapEndTime(END).sourceGapDurationSeconds(86399).migratedToTimelineEdits(true).build());
        var split = TimelineTripStaySplitOverrideEntity.builder().user(user).stay(stay)
                .stayStartTime(START).stayEndTime(END).anchorTimestamp(START)
                .stayLocationName("Home").sourceTripTimestamp(START).sourceTripDurationSeconds(86400).build();
        em.persist(split);
        em.flush();
        UUID userId = user.getId();
        Long activeId = active.getId();
        Long splitId = split.getId();
        em.clear();
        var timeline = aggregator.getTimelineFromDb(userId, START, END);
        assertThat(timeline.getStays()).hasSize(1);
        assertThat(timeline.getStays().getFirst().getActiveEditCount()).isEqualTo(2);
        assertThat(timeline.getStays().getFirst().getDataGapOverrideIds()).containsExactly(activeId);
        assertThat(timeline.getStays().getFirst().getTripSplitOverrideId()).isEqualTo(splitId);
        assertThat(em.unwrap(Session.class).getStatistics().getEntityCount()).isZero();
    }

    private UserEntity user() {
        var user = new UserEntity();
        user.setEmail(TestIds.uniqueEmail("read-projection"));
        user.setFullName("Projection Test");
        user.setPasswordHash("test");
        em.persist(user);
        return user;
    }

    private org.locationtech.jts.geom.Point point() {
        var point = geometry.createPoint(new Coordinate(30.52, 50.45));
        point.setSRID(4326);
        return point;
    }
}
