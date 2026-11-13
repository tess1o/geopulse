package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.edits.ActivityCorrectionService;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditService;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditStatus;
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

    @Test void existingEventsRejectPreviewAndSaveWithoutPartialWrites() {
        var day = new CommuteWithStops(); var user = day.ingest(harness, "activity-occupied");
        var request = request(stay("05:00", "05:03"), stay("10:00", "10:03"));
        assertThatThrownBy(() -> service.preview(user, request)).isInstanceOf(GeoPulseException.class).hasMessageContaining("already contains");
        assertThatThrownBy(() -> service.save(user, request)).isInstanceOf(GeoPulseException.class);
        assertThat(edits.list(user)).isEmpty();
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
        assertThat(edits.list(owner).getFirst().status()).isEqualTo(TimelineEditStatus.ACTIVE);
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
        service.save(user, request(stay("14:40", "14:43")));
        assertThatThrownBy(() -> service.changeStatus(user, saved.groupId(), true)).isInstanceOf(GeoPulseException.class);
        assertThat(edits.list(user).stream().filter(edit -> saved.groupId().equals(edit.groupId())))
                .allSatisfy(edit -> assertThat(edit.status()).isEqualTo(TimelineEditStatus.REVERTED));
    }

    @Test void httpEndpointsEnforceGroupOwnershipAndSingleEditUndoGuard() {
        var actor = users.createAuthenticatedUser("activity-http");
        var body = request(stay("14:24", "14:27"));
        io.restassured.RestAssured.given().contentType(io.restassured.http.ContentType.JSON)
                .header("Authorization", "Bearer " + actor.accessToken()).body(body)
                .post("/api/v1/timeline/activity-corrections/preview").then().statusCode(200);
        var response = io.restassured.RestAssured.given().contentType(io.restassured.http.ContentType.JSON)
                .header("Authorization", "Bearer " + actor.accessToken()).body(body)
                .post("/api/v1/timeline/activity-corrections");
        assertThat(response.statusCode()).isEqualTo(200);
        var job = response.jsonPath().getString("timelineJobId");
        if (job != null) transfer.awaitJob(UUID.fromString(job));
        var groupId = response.jsonPath().getString("groupId");
        var editId = edits.list(actor.userId()).getFirst().id();
        io.restassured.RestAssured.given().contentType(io.restassured.http.ContentType.JSON).header("Authorization", "Bearer " + actor.accessToken())
                .post("/api/v1/timeline/edits/" + editId + "/revert").then().statusCode(400);
        var other = users.createAuthenticatedUser("activity-http-other");
        io.restassured.RestAssured.given().contentType(io.restassured.http.ContentType.JSON).header("Authorization", "Bearer " + other.accessToken())
                .post("/api/v1/timeline/activity-corrections/" + groupId + "/revert").then().statusCode(404);
        assertThat(harness.pointIds(actor.userId(), at("00:00"), at("23:59"))).isEmpty();
    }

    @Test void aGroupCannotBePartiallySuperseded() {
        var actor = users.createAuthenticatedUser("activity-structure");
        var user = actor.userId();
        var saved = service.save(user, request(stay("14:24", "14:27"), travel("14:30", "15:00")));
        harness.fullRebuild(user);
        for (var kind : List.of("SET_TIMES", "SPLIT_STAY", "REMOVE_EVENT", "MERGE", "FORCE_STAY", "FORCE_TRIP")) {
            var command = editRequest(kind, "14:24", "14:27");
            command.getPayload().put("start", at("14:23").toString());
            command.getPayload().put("end", at("14:28").toString());
            command.getPayload().put("splitAt", at("14:25").toString());
            command.getPayload().put("resolution", "GAP");
            command.getPayload().put("direction", "NEXT");
            command.setSupersede(true);
            for (var endpoint : List.of("/api/v1/timeline/edits/preview", "/api/v1/timeline/edits")) {
                io.restassured.RestAssured.given().contentType(io.restassured.http.ContentType.JSON)
                        .header("Authorization", "Bearer " + actor.accessToken()).body(command)
                        .post(endpoint).then().statusCode(409)
                        .body("detail", org.hamcrest.Matchers.containsString("cannot be partially replaced"));
            }
        }
        var misleadingRange = editRequest("FORCE_STAY", "14:24", "14:27");
        misleadingRange.setAffectedFrom(at("16:00")); misleadingRange.setAffectedTo(at("17:00"));
        misleadingRange.setSupersede(true);
        assertThatThrownBy(() -> edits.preview(user, misleadingRange))
                .isInstanceOf(GeoPulseException.class).hasMessageContaining("cannot be partially replaced");
        assertThat(edits.list(user)).hasSize(2).allSatisfy(edit -> {
            assertThat(edit.status()).isEqualTo(TimelineEditStatus.ACTIVE);
            assertThat(edit.groupId()).isEqualTo(saved.groupId());
        });
        assertSaved(user);
        service.changeStatus(user, saved.groupId(), false); harness.fullRebuild(user);
        QuarkusTransaction.requiringNew().run(() -> {
            assertThat(stays.count("user.id", user)).isZero();
            assertThat(trips.count("user.id", user)).isZero();
        });
        service.changeStatus(user, saved.groupId(), true); harness.fullRebuild(user);
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
        label.getPayload().put("visitName", "Lunch"); edits.create(user, label);
        var movement = editRequest("SET_MOVEMENT_TYPE", "14:30", "15:00");
        movement.getPayload().put("movementType", "BICYCLE"); edits.create(user, movement);
        harness.fullRebuild(user);
        QuarkusTransaction.requiringNew().run(() -> {
            var stay = stays.find("user.id", user).singleResult();
            assertThat(stay.getLocationName()).isEqualTo("Corrected place");
            assertThat(stay.getVisitName()).isEqualTo("Lunch");
            var trip = trips.find("user.id", user).singleResult();
            assertThat(trip.getMovementType()).isEqualTo("BICYCLE");
            assertThat(trip.getManualRoute()).hasSize(3);
        });
        service.changeStatus(user, saved.groupId(), false); harness.fullRebuild(user);
        service.changeStatus(user, saved.groupId(), true); harness.fullRebuild(user);
        QuarkusTransaction.requiringNew().run(() -> {
            assertThat(stays.find("user.id", user).singleResult().getVisitName()).isEqualTo("Lunch");
            assertThat(trips.find("user.id", user).singleResult().getMovementType()).isEqualTo("BICYCLE");
        });
    }

    @Test void ungroupedCorrectionsCanStillBeSuperseded() {
        var user = new CommuteWithStops().ingest(harness, "activity-ungrouped");
        var command = editRequest("FORCE_STAY", "10:00", "10:03");
        command.setLatitude(49.82); command.setLongitude(24.01); command.setPlaceName("Original");
        var original = edits.create(user, command); harness.fullRebuild(user);
        command.setSupersede(true); command.setPlaceName("Replacement");
        edits.create(user, command); harness.fullRebuild(user);
        assertThat(edits.list(user)).filteredOn(edit -> edit.id().equals(original.id())).hasSize(1)
                .allSatisfy(edit -> assertThat(edit.status()).isEqualTo(TimelineEditStatus.SUPERSEDED));
        QuarkusTransaction.requiringNew().run(() ->
                assertThat(stays.find("user.id = ?1 and timestamp = ?2", user, at("10:00")).singleResult().getLocationName()).isEqualTo("Replacement"));
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
