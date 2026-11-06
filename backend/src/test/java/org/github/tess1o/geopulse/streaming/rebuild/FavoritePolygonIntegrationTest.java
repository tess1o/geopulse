package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.export.model.DebugExportRequest;
import org.github.tess1o.geopulse.export.service.DebugExportService;
import org.github.tess1o.geopulse.favorites.model.*;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.importdata.model.DebugImportRequest;
import org.github.tess1o.geopulse.importdata.service.DebugImportService;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.engine.StreamingTimelineProcessor;
import org.github.tess1o.geopulse.streaming.iterator.StreamingGpsIterable;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.model.UserEntity;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class FavoritePolygonIntegrationTest {
    @Inject TimelineReplayHarness harness;
    @Inject EntityManager em;
    @Inject GpsPointRepository gpsPoints;
    @Inject TimelineConfigurationProvider configuration;
    @Inject StreamingTimelineProcessor processor;
    @Inject DebugExportService exporter;
    @Inject DebugImportService importer;

    private AreaGeometry shape() {
        return new AreaGeometry("Polygon", List.of(List.of(
                List.of(30.,50.), List.of(30.03,50.), List.of(30.03,50.01), List.of(30.01,50.01),
                List.of(30.01,50.03), List.of(30.,50.03), List.of(30.,50.))));
    }
    private UUID setup() {
        GeocodingTestMocks.install();
        UUID user = harness.createUser("polygon-replay");
        QuarkusTransaction.requiringNew().run(() -> em.persist(FavoritesEntity.builder()
                .user(em.getReference(UserEntity.class, user)).type(FavoriteLocationType.AREA)
                .name("Private polygon").geometry(shape().toPolygon()).build()));
        List<OwnTracksLocationMessage> messages = new ArrayList<>();
        long start = Instant.parse("2026-09-03T08:00:00Z").getEpochSecond();
        for (int i = 0; i < 40; i++) {
            double lon = i < 10 ? 30.025 : i < 20 ? 30.005 : 30.02;
            double lat = i < 10 ? 50.005 : i < 20 ? 50.025 : 50.02;
            long time = start + i * 60L;
            messages.add(OwnTracksLocationMessage.builder().type("location").tid("polygon")
                    .tst(time).createdAt(time).lon(lon).lat(lat).acc(5.).vel(0.).alt(0.).build());
        }
        harness.ingest(user, messages);
        return user;
    }
    private List<TimelineEvent> events(UUID user, int pageSize) {
        return QuarkusTransaction.requiringNew().call(() -> processor.processPoints(
                new StreamingGpsIterable(gpsPoints, user, Instant.EPOCH, pageSize),
                configuration.getConfigurationForUser(user), user));
    }
    @Test void polygonStayAndMissingCornerRemainDistinctAcrossPagesAndRebuild() {
        UUID user = setup();
        var expected = events(user, 10000);
        var stays = expected.stream().filter(Stay.class::isInstance).map(Stay.class::cast).toList();
        assertThat(stays).hasSizeGreaterThanOrEqualTo(2);
        assertThat(stays).anySatisfy(stay -> {
            assertThat(stay.getFavoriteId()).isNotNull();
            assertThat(stay.getDuration().toMinutes()).isGreaterThanOrEqualTo(19);
            assertThat(shape().toPolygon().covers(org.github.tess1o.geopulse.shared.geo.GeoUtils.createPoint(stay.getLongitude(), stay.getLatitude()))).isTrue();
        });
        assertThat(stays).anySatisfy(stay -> {
            assertThat(stay.getLongitude()).isCloseTo(30.02, within(1e-6));
            assertThat(stay.getLatitude()).isCloseTo(50.02, within(1e-6));
            assertThat(stay.getFavoriteId()).isNull();
        });
        for (int page : new int[]{1,7,64}) assertThat(events(user,page)).usingRecursiveComparison().isEqualTo(expected);
        harness.process(user);
        harness.assertMatchesFullRebuild(user, "polygon with a missing corner");
    }
    @Test void debugRoundTripShiftsEveryVertexAndPreservesTimeline() throws Exception {
        UUID source = setup();
        harness.fullRebuild(source);
        var request = new DebugExportRequest();
        request.setStartTime(Instant.parse("2026-09-03T00:00:00Z"));
        request.setEndTime(Instant.parse("2026-09-04T00:00:00Z"));
        request.setLatitudeShift(0.);
        request.setLongitudeShift(10.);
        byte[] data = QuarkusTransaction.requiringNew().call(() -> exporter.generateDebugExport(source,request));
        var area = harness.readExportJson(data,"favorite_areas.json").get(0);
        assertThat(area.path("name").asText()).doesNotContain("Private");
        var ring = area.path("geometry").path("coordinates").get(0);
        for (int i=0;i<ring.size();i++) {
            assertThat(ring.get(i).get(0).asDouble()).isCloseTo(shape().getCoordinates().getFirst().get(i).getFirst()+10,within(1e-9));
            assertThat(ring.get(i).get(1).asDouble()).isEqualTo(shape().getCoordinates().getFirst().get(i).get(1));
        }
        UUID copy = harness.createUser("polygon-debug-copy");
        var report = importer.importDebugData(copy,data,new DebugImportRequest(true,true));
        assertThat(report.warnings()).extracting(w -> w.code()).doesNotContain("TIMELINE_DIFFERS");
        assertThat(harness.structure(copy,data)).isNotEmpty().containsExactlyElementsOf(harness.structure(source,data));
    }
}
