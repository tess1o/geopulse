package org.github.tess1o.geopulse.streaming.rebuild;

import static org.github.tess1o.geopulse.testsupport.CorrectionTestPayloads.put;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.edits.ActivityCorrectionService;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditService;
import org.github.tess1o.geopulse.streaming.edits.TimelineCorrectionStatus;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionWaypointDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class ActivityCorrectionIntegrationTest {
    @Inject TimelineReplayHarness harness;
    @Inject ManualEditsSupport transfer;
    @Inject ActivityCorrectionService service;
    @Inject TimelineEditService edits;
    @Inject TimelineStayRepository stays;
    @Inject TimelineTripRepository trips;
    @Inject StreamingTimelineGenerationService generation;
    @Inject org.github.tess1o.geopulse.streaming.service.StreamingTimelineAggregator aggregator;
    @Inject org.github.tess1o.geopulse.testsupport.TestUserFactory users;
    @BeforeEach void setup() { GeocodingTestMocks.install(); }

    @Test void editOnlyHistoryIsExactAndGroupUndoRedoDoesNotCreateGps() {
        var user = harness.createUser("activity-only");
        var saved = service.save(user, request(stay("14:24", "14:27"), travel("14:30", "15:00")));
        assertThat(saved.edits()).hasSize(2).allSatisfy(edit -> assertThat(edit.groupId()).isEqualTo(saved.groupId()));
        harness.fullRebuild(user);
        assertSaved(user);
        generation.generateTimelineFromTimestamp(user, at("14:25"));
        assertSaved(user);
        service.changeStatus(user, saved.groupId(), false);
        harness.fullRebuild(user);
        QuarkusTransaction.requiringNew().run(() -> {
            assertThat(stays.count("user.id", user)).isZero(); assertThat(trips.count("user.id", user)).isZero();
        });
        service.changeStatus(user, saved.groupId(), true);
        harness.fullRebuild(user);
        assertSaved(user);
        assertThat(harness.pointIds(user, at("00:00"), at("23:59"))).isEmpty();
    }

    @Test void reviewShowsUnfilledPartsOfTheOriginalGap() {
        var user = harness.createUser("activity-preview");
        var preview = service.preview(user, new ActivityCorrectionService.Request(null,
                List.of(stay("14:24", "14:27")), at("14:00"), at("15:00")));
        assertThat(preview.events()).extracting(event -> event.type()).containsExactly("DATA_GAP", "STAY", "DATA_GAP");
        assertThat(edits.list(user)).isEmpty();
    }

    @Test void aFillOverAnExistingEventCarvesItAndInvalidBatchesWriteNothing() {
        var day = new CommuteWithStops(); var user = day.ingest(harness, "activity-occupied");
        var request = request(stay("05:00", "05:03"), stay("10:00", "10:03"));
        var preview = service.preview(user, request);
        assertThat(preview.events()).isNotEmpty();
        var saved = service.save(user, request);
        assertThat(saved.edits()).hasSize(2);
        var before = edits.list(user).size();

        assertThatThrownBy(() -> service.save(user, request(stay("14:24", "14:27"), stay("14:25", "14:28"))))
                .isInstanceOf(GeoPulseException.class);
        assertThat(edits.list(user)).hasSize(before);
    }

    @Test void overlappingActivitiesAndCorrectionsAreRejected() {
        var user = harness.createUser("activity-overlap");
        assertThatThrownBy(() -> service.save(user, request(stay("14:24", "14:27"), stay("14:25", "14:28"))))
                .isInstanceOf(GeoPulseException.class);
        assertThat(edits.list(user)).isEmpty();
        service.save(user, request(stay("14:24", "14:27")));
        assertThatThrownBy(() -> service.save(user, request(stay("14:24", "14:27"))))
                .isInstanceOf(GeoPulseException.class);
    }

    @Test void groupOwnershipIsEnforced() {
        var owner = harness.createUser("activity-owner"); var other = harness.createUser("activity-other");
        var saved = service.save(owner, request(stay("14:24", "14:27")));
        assertThatThrownBy(() -> service.changeStatus(other, saved.groupId(), false)).isInstanceOf(GeoPulseException.class);
        assertThat(edits.list(owner).getFirst().status()).isEqualTo(TimelineCorrectionStatus.IN_EFFECT);
    }

    @Test void nativeBackupPreservesGroupAndManualGeometry() {
        var source = harness.createUser("activity-export");
        var saved = service.save(source, request(stay("14:24", "14:27"), travel("14:30", "15:00")));
        harness.fullRebuild(source);
        var archive = transfer.exportOf(source, at("00:00"), at("23:59"));
        var user = harness.createUser("activity-import");
        transfer.importInto(user, archive); harness.fullRebuild(user);
        assertSaved(user);
        assertThat(edits.list(user)).allSatisfy(edit -> assertThat(edit.groupId()).isEqualTo(saved.groupId()));
        service.changeStatus(user, saved.groupId(), false); harness.fullRebuild(user);
        QuarkusTransaction.requiringNew().run(() -> assertThat(stays.count("user.id", user)).isZero());
    }

    @Test void closedActivitiesAfterTheLastRecordingDoNotOverlapTheOngoingGap() {
        var user = harness.createUser("activity-after-gps"); var day = new CommuteWithStops();
        harness.ingest(user, day.track.stay(day.home, at("10:00"), at("11:00"), 5).messages());
        harness.fullRebuild(user);
        var original = harness.pointIds(user, at("00:00"), at("23:59"));
        service.save(user, request(stay("14:24", "14:27"), travel("14:30", "15:00")));
        harness.fullRebuild(user);
        var timeline = aggregator.getTimelineFromDb(user, at("00:00"), at("23:59"));
        assertThat(timeline.getTrips().getFirst().getManualRoute()).hasSize(3);
        assertThat(timeline.getDataGaps()).anySatisfy(gap -> {
            assertThat(gap.getStartTime()).isEqualTo(at("15:00")); assertThat(gap.isOngoing()).isTrue();
        });
        assertThat(timeline.getDataGaps()).noneSatisfy(gap -> assertThat(
                gap.getStartTime().isBefore(at("14:27")) && gap.getEndTime().isAfter(at("14:24"))).isTrue());
        assertThat(harness.pointIds(user, at("00:00"), at("23:59"))).containsExactlyElementsOf(original);
    }

    @Test void failedGroupRestoreLeavesEveryMemberReverted() {
        var user = harness.createUser("activity-restore-conflict");
        var saved = service.save(user, request(stay("14:24", "14:27"), travel("14:30", "15:00")));
        harness.fullRebuild(user); service.changeStatus(user, saved.groupId(), false); harness.fullRebuild(user);
        service.save(user, request(stay("14:40", "15:15")));
        assertThatThrownBy(() -> service.changeStatus(user, saved.groupId(), true)).isInstanceOf(GeoPulseException.class);
        assertThat(edits.list(user).stream().filter(edit -> saved.groupId().equals(edit.groupId())))
                .allSatisfy(edit -> assertThat(edit.status()).isEqualTo(TimelineCorrectionStatus.UNDONE));
    }

    @Test void httpEndpointsEnforceGroupOwnership() {
        var actor = users.createAuthenticatedUser("activity-http");
        var body = request(stay("14:24", "14:27"));
        io.restassured.RestAssured.given().contentType(io.restassured.http.ContentType.JSON)
                .header("Authorization", "Bearer " + actor.accessToken()).body(body)
                .post("/api/v1/timeline/corrections/preview").then().statusCode(200);
        var response = io.restassured.RestAssured.given().contentType(io.restassured.http.ContentType.JSON)
                .header("Authorization", "Bearer " + actor.accessToken()).body(body)
                .post("/api/v1/timeline/corrections");
        assertThat(response.statusCode()).isEqualTo(200);
        var job = response.jsonPath().getString("timelineJobId");
        if (job != null) transfer.awaitJob(UUID.fromString(job));
        var groupId = response.jsonPath().getString("groupId");
        var other = users.createAuthenticatedUser("activity-http-other");
        io.restassured.RestAssured.given().contentType(io.restassured.http.ContentType.JSON).header("Authorization", "Bearer " + other.accessToken())
                .post("/api/v1/timeline/corrections/" + groupId + "/undo").then().statusCode(404);
        io.restassured.RestAssured.given().contentType(io.restassured.http.ContentType.JSON).header("Authorization", "Bearer " + actor.accessToken())
                .post("/api/v1/timeline/corrections/" + groupId + "/undo").then().statusCode(200);
        assertThat(harness.pointIds(actor.userId(), at("00:00"), at("23:59"))).isEmpty();
    }

    @Test void anExplicitReplacementReplacesTheWholeGroup() {
        var user = harness.createUser("activity-structure");
        var saved = service.save(user, request(stay("14:24", "14:27"), travel("14:30", "15:00")));
        harness.fullRebuild(user);

        var replacement = new ActivityCorrectionService.Request(null, List.of(stay("14:24", "14:27")),
                null, null, null, saved.groupId());
        var replacing = service.save(user, replacement);
        assertThat(edits.list(user)).filteredOn(edit -> saved.groupId().equals(edit.groupId()))
                .as("a replacement replaces every member of the group")
                .allSatisfy(edit -> assertThat(edit.status()).isEqualTo(TimelineCorrectionStatus.REPLACED));
        assertThat(edits.list(user)).filteredOn(edit -> replacing.groupId().equals(edit.groupId()))
                .allSatisfy(edit -> assertThat(edit.status()).isEqualTo(TimelineCorrectionStatus.IN_EFFECT));

        service.changeStatus(user, replacing.groupId(), false); harness.fullRebuild(user);
        assertThat(edits.list(user)).filteredOn(edit -> saved.groupId().equals(edit.groupId()))
                .as("undoing the replacement restores the replaced group")
                .allSatisfy(edit -> assertThat(edit.status()).isEqualTo(TimelineCorrectionStatus.IN_EFFECT));
        assertSaved(user);
    }

    @Test void groupedActivitiesKeepPlaceLabelAndMovementEditsAcrossRebuilds() {
        var user = harness.createUser("activity-details");
        var saved = service.save(user, request(stay("14:24", "14:27"), travel("14:30", "15:00")));
        harness.fullRebuild(user);
        var place = editRequest("SET_PLACE", "14:24", "14:27");
        place.setLatitude(49.84); place.setLongitude(24.04); place.setPlaceName("Corrected place");
        edits.create(user, place);
        var label = editRequest("SET_VISIT_NAME", "14:24", "14:27");
        put(label, "visitName", "Lunch"); edits.create(user, label);
        var movement = editRequest("SET_MOVEMENT_TYPE", "14:30", "15:00");
        put(movement, "movementType", "BICYCLE"); edits.create(user, movement);
        harness.fullRebuild(user);
        QuarkusTransaction.requiringNew().run(() -> {
            var stay = stays.find("user.id", user).singleResult();
            assertThat(stay.getLocationName()).isEqualTo("Corrected place");
            assertThat(stay.getVisitName()).isEqualTo("Lunch");
            var trip = trips.find("user.id", user).singleResult();
            assertThat(trip.getMovementType()).isEqualTo("BICYCLE");
            assertThat(trip.getManualRoute()).hasSize(3);
        });
        service.changeStatus(user, saved.groupId(), false, true); harness.fullRebuild(user);
        service.changeStatus(user, saved.groupId(), true); harness.fullRebuild(user);
        QuarkusTransaction.requiringNew().run(() -> {
            assertThat(stays.find("user.id", user).singleResult().getVisitName()).isEqualTo("Lunch");
            assertThat(trips.find("user.id", user).singleResult().getMovementType()).isEqualTo("BICYCLE");
        });
    }

    private org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest editRequest(String kind, String from, String to) {
        var command = new org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest();
        command.setKind(org.github.tess1o.geopulse.streaming.edits.TimelineEditKind.valueOf(kind));
        command.setAnchorStart(at(from)); command.setAnchorEnd(at(to));
        command.setAffectedFrom(at(from)); command.setAffectedTo(at(to));
        return command;
    }

    private void assertSaved(UUID user) {
        QuarkusTransaction.requiringNew().run(() -> {
            var stay = stays.find("user.id", user).singleResult();
            assertThat(stay.getTimestamp()).isEqualTo(at("14:24")); assertThat(stay.getStayDuration()).isEqualTo(180);
            var trip = trips.find("user.id", user).singleResult();
            assertThat(trip.getTimestamp()).isEqualTo(at("14:30")); assertThat(trip.getTripDuration()).isEqualTo(1800);
            assertThat(trip.getManualRoute()).hasSize(3); assertThat(trip.getDistanceMeters()).isPositive();
            assertThat(trip.getAvgGpsSpeed()).isNull(); assertThat(trip.getMaxGpsSpeed()).isNull();
        });
    }
    private ActivityCorrectionService.Request request(ActivityCorrectionService.Activity... activities) {
        return new ActivityCorrectionService.Request(null, List.of(activities), null, null);
    }
    private ActivityCorrectionService.Activity stay(String from, String to) {
        var a = activity("STAY", from, to); a.setLatitude(49.82); a.setLongitude(24.01); a.setLocationName("Short visit"); return a;
    }
    private ActivityCorrectionService.Activity travel(String from, String to) {
        var a = activity("TRIP", from, to); a.setMovementType("WALK");
        a.setStartPlace(new ActivityCorrectionService.Place(null, null, 49.82, 24.01, "Start"));
        a.setEndPlace(new ActivityCorrectionService.Place(null, null, 49.83, 24.03, "End"));
        a.setWaypoints(List.of(new TripReconstructionWaypointDto(49.825, 24.025))); return a;
    }
    private ActivityCorrectionService.Activity activity(String type, String from, String to) {
        var a = new ActivityCorrectionService.Activity(); a.setSegmentType(type); a.setStartTime(at(from)); a.setEndTime(at(to)); return a;
    }
    private static Instant at(String time) { return CommuteWithStops.at(time); }
}
