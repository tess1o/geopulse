package org.github.tess1o.geopulse.streaming.rebuild;

import static org.github.tess1o.geopulse.testsupport.CorrectionTestPayloads.put;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.streaming.edits.ActivityCorrectionService;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditService;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditStatus;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditPreviewDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionWaypointDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Issue 665: an existing stay or trip interval can be rebuilt with the activities that actually happened. The
 * untouched time keeps the original event, and undo restores it.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class StayIntervalReconstructionIntegrationTest {

    @Inject TimelineReplayHarness harness;
    @Inject ActivityCorrectionService corrections;
    @Inject TimelineEditService edits;
    @Inject TimelineStayRepository stays;
    @Inject TimelineTripRepository trips;
    @Inject EntityManager em;

    @BeforeEach
    void setup() {
        GeocodingTestMocks.install();
    }

    /** The reporter's 665 case: a converted gap stay shortened by hand, then a trip inserted into it. */
    @Test
    void aTripCanBeInsertedIntoAShortenedForcedStay() {
        var user = harness.createUser("rebuild-forced");
        var original = corrections.save(user, new ActivityCorrectionService.Request(null,
                List.of(stay("14:24", "15:24", 49.82, 24.01, "Knowle Lane")), at("14:00"), at("16:00")));
        harness.fullRebuild(user);
        var forcedEdit = original.edits().getFirst();

        setTimes(user, at("14:24"), at("15:24"), at("14:24"), at("15:24"), at("14:24"), at("14:44"));
        harness.fullRebuild(user);
        // A migrated v1 gap stay can be active without an application row; rebuilding must not depend on one.
        deleteApplication(forcedEdit.id());

        var request = new ActivityCorrectionService.Request(null, List.of(travel("14:30", "14:34")),
                at("14:24"), at("14:44"));
        var preview = corrections.preview(user, request);
        assertThat(preview.events()).extracting(TimelineEditPreviewDTO.Event::type)
                .containsExactly("STAY", "TRIP", "STAY");

        var rebuilt = corrections.save(user, request);
        assertThat(rebuilt.affectedFrom()).isEqualTo(at("14:24"));
        harness.regenerateFrom(user, rebuilt.affectedFrom());

        var interval = staysBetween(user, at("14:24"), at("14:44"));
        assertThat(interval).extracting(stay -> stay.getTimestamp() + "/" + stay.getStayDuration())
                .containsExactly(at("14:24") + "/360", at("14:34") + "/600");
        assertThat(interval).allSatisfy(stay -> {
            assertThat(stay.getLocationName()).isEqualTo("Knowle Lane");
            assertThat(stay.getLocation().getY()).isEqualTo(49.82);
        });
        var inserted = onlyTrip(user);
        assertThat(inserted.getTimestamp()).isEqualTo(at("14:30"));
        assertThat(inserted.getTripDuration()).isEqualTo(240L);
        assertThat(inserted.getManualRoute()).hasSize(3);
        var corrected = harness.snapshot(user).contentAcrossUsers();

        corrections.changeStatus(user, rebuilt.groupId(), false);
        harness.fullRebuild(user);
        assertThat(staysBetween(user, at("14:24"), at("14:44")))
                .extracting(stay -> stay.getTimestamp() + "/" + stay.getStayDuration())
                .containsExactly(at("14:24") + "/1200");
        assertThat(QuarkusTransaction.requiringNew().call(() -> trips.count("user.id", user))).isZero();

        corrections.changeStatus(user, rebuilt.groupId(), true);
        harness.fullRebuild(user);
        assertThat(staysBetween(user, at("14:24"), at("14:44"))).hasSize(2);
        assertThat(onlyTrip(user).getTimestamp()).isEqualTo(at("14:30"));
        assertThat(harness.snapshot(user).contentAcrossUsers()).containsExactlyElementsOf(corrected);
    }

    @Test
    void anAutomaticStayKeepsTheTimeAroundSeveralActivities() {
        var user = new CommuteWithStops().ingest(harness, "rebuild-auto");
        TimelineStayEntity homeStay = staysBetween(user, at("06:00"), at("08:00")).getFirst();
        var homeEnd = homeStay.getTimestamp().plusSeconds(homeStay.getStayDuration());
        var request = new ActivityCorrectionService.Request(null, List.of(
                stay("06:30", "06:45", 49.8420, 24.0310, "Home stop"),
                travel("07:00", "07:10")), homeStay.getTimestamp(), homeEnd);

        var rebuilt = corrections.save(user, request);
        harness.fullRebuild(user);

        var home = staysBetween(user, at("06:00"), homeEnd);
        assertThat(home).extracting(stay -> stay.getTimestamp() + "/" + stay.getStayDuration())
                .containsExactly(
                        at("06:00") + "/1800",
                        at("06:30") + "/900",
                        at("06:45") + "/900",
                        at("07:10") + "/" + Duration.between(at("07:10"), homeEnd).toSeconds());
        List<TimelineTripEntity> insertedTrip = QuarkusTransaction.requiringNew().call(() -> trips
                .find("user.id = ?1 and timestamp = ?2", user, at("07:00")).list());
        assertThat(insertedTrip).singleElement()
                .satisfies(trip -> assertThat(trip.getTripDuration()).isEqualTo(600L));

        corrections.changeStatus(user, rebuilt.groupId(), false);
        harness.fullRebuild(user);
        assertThat(staysBetween(user, at("06:00"), homeEnd)).hasSize(1);
        assertThat(QuarkusTransaction.requiringNew().call(() -> trips
                .find("user.id = ?1 and timestamp = ?2", user, at("07:00")).count())).isZero();
    }

    @Test
    void aTripIntervalCanBeRebuiltWithAStayAndATrip() {
        var user = new CommuteWithStops().ingest(harness, "rebuild-trip");
        TimelineTripEntity trip = QuarkusTransaction.requiringNew()
                .call(() -> trips.find("user.id = ?1", user).singleResult());
        var end = trip.getTimestamp().plusSeconds(trip.getTripDuration());

        corrections.save(user, new ActivityCorrectionService.Request(null, List.of(
                stay("08:10", "08:20", 49.8385, 24.0530, "Bakery"),
                travel("08:20", "08:30")), trip.getTimestamp(), end));
        harness.fullRebuild(user);

        List<TimelineStayEntity> insertedStay = QuarkusTransaction.requiringNew().call(() -> stays
                .find("user.id = ?1 and timestamp = ?2", user, at("08:10")).list());
        assertThat(insertedStay).singleElement()
                .satisfies(stay -> assertThat(stay.getStayDuration()).isEqualTo(600L));
        List<TimelineTripEntity> legs = QuarkusTransaction.requiringNew().call(() -> trips
                .find("user.id = ?1 and timestamp >= ?2 and timestamp < ?3 order by timestamp",
                        user, trip.getTimestamp(), end).list());
        assertThat(legs)
                .extracting(TimelineTripEntity::getTimestamp)
                .containsExactly(trip.getTimestamp(), at("08:20"), at("08:30"));
    }

    @Test
    void activitiesOutsideTheContextAreAllowedButOverlapsAreRejectedWithoutWrites() {
        var user = harness.createUser("rebuild-invalid");
        corrections.save(user, new ActivityCorrectionService.Request(null,
                List.of(stay("14:24", "15:24", 49.82, 24.01, "Knowle Lane")), at("14:00"), at("16:00")));
        harness.fullRebuild(user);

        // Context only bounds the preview; a fill may extend outside it and carve whatever is there.
        var outside = new ActivityCorrectionService.Request(null, List.of(travel("15:30", "15:40")),
                at("14:24"), at("14:44"));
        var saved = corrections.save(user, outside);
        assertThat(saved.edits()).hasSize(1);

        var after = edits.list(user).size();
        var overlap = new ActivityCorrectionService.Request(null, List.of(
                stay("15:30", "15:40", 49.82, 24.01, "One"),
                stay("15:35", "15:44", 49.82, 24.01, "Two")), at("14:24"), at("14:44"));
        assertThatThrownBy(() -> corrections.save(user, overlap))
                .isInstanceOf(GeoPulseException.class);

        assertThat(edits.list(user)).hasSize(after);
    }

    @Test
    void theRebuildStartsAtTheEarliestCorrectionInTheChain() {
        var user = harness.createUser("rebuild-from");
        corrections.save(user, new ActivityCorrectionService.Request(null,
                List.of(stay("14:24", "15:24", 49.82, 24.01, "Knowle Lane")), at("14:00"), at("16:00")));
        harness.fullRebuild(user);
        setTimes(user, at("14:24"), at("15:24"), at("14:24"), at("15:24"), at("14:24"), at("14:44"));
        harness.fullRebuild(user);
        assertThat(harness.pendingRebuild(user)).isEmpty();

        var rebuilt = corrections.save(user, new ActivityCorrectionService.Request(null, List.of(travel("14:30", "14:34")),
                at("14:24"), at("14:44")));

        assertThat(rebuilt.affectedFrom()).isEqualTo(at("14:24"));
        assertThat(rebuilt.edits()).singleElement().satisfies(edit -> assertThat(edit.beforeValues()).isNull());
        assertThat(harness.pendingRebuild(user).orElseThrow().rebuildFrom()).isEqualTo(at("14:24"));
    }

    @Test
    void undoingTheOriginalGroupPreservesTheIndependentNestedTrip() {
        var user = harness.createUser("rebuild-dependent");
        var original = corrections.save(user, new ActivityCorrectionService.Request(null,
                List.of(stay("14:24", "15:24", 49.82, 24.01, "Knowle Lane")), at("14:00"), at("16:00")));
        harness.fullRebuild(user);
        var rebuilt = corrections.save(user, new ActivityCorrectionService.Request(null,
                List.of(travel("14:30", "14:34")), at("14:24"), at("15:24")));
        harness.fullRebuild(user);

        corrections.changeStatus(user, original.groupId(), false);
        harness.fullRebuild(user);
        assertThat(QuarkusTransaction.requiringNew().call(() -> stays.count("user.id", user))).isZero();
        assertThat(onlyTrip(user).getTimestamp()).isEqualTo(at("14:30"));
        assertThat(edits.list(user)).filteredOn(edit -> original.groupId().equals(edit.groupId()))
                .singleElement().satisfies(edit -> assertThat(edit.status())
                        .isEqualTo(org.github.tess1o.geopulse.streaming.edits.TimelineCorrectionStatus.UNDONE));
        assertThat(edits.list(user)).filteredOn(edit -> rebuilt.groupId().equals(edit.groupId()))
                .singleElement().satisfies(edit -> {
                    assertThat(edit.status()).isEqualTo(org.github.tess1o.geopulse.streaming.edits.TimelineCorrectionStatus.IN_EFFECT);
                    assertThat(edit.applied()).isTrue();
                });
    }

    @ParameterizedTest(name = "Undo multi-activity rebuild, cascade={0}")
    @ValueSource(booleans = {false, true})
    void undoingSeveralActivitiesOnACorrectedIntervalRestoresTheOriginalStay(boolean cascade) {
        var user = harness.createUser("rebuild-group-undo");
        var original = corrections.save(user, new ActivityCorrectionService.Request(null,
                List.of(stay("14:24", "15:24", 49.82, 24.01, "Original stay")),
                at("14:24"), at("15:24")));
        harness.fullRebuild(user);

        var request = new ActivityCorrectionService.Request(null, List.of(
                stay("14:30", "14:35", 49.83, 24.03, "Inserted stop"),
                travel("14:40", "14:44")), at("14:24"), at("15:24"));
        corrections.preview(user, request);
        var rebuilt = corrections.save(user, request);
        harness.fullRebuild(user);

        assertThat(edits.list(user)).filteredOn(edit -> rebuilt.groupId().equals(edit.groupId()))
                .hasSize(2).allSatisfy(edit -> {
                    assertThat(edit.status()).isEqualTo(org.github.tess1o.geopulse.streaming.edits.TimelineCorrectionStatus.IN_EFFECT);
                    assertThat(edit.applied()).isTrue();
                });
        assertThat(staysBetween(user, at("14:24"), at("15:24"))).hasSize(4);
        assertThat(onlyTrip(user).getTimestamp()).isEqualTo(at("14:40"));

        assertThatCode(() -> corrections.changeStatus(user, rebuilt.groupId(), false, cascade))
                .as("Undo should revert the whole rebuilt group while keeping its original stay")
                .doesNotThrowAnyException();
        harness.fullRebuild(user);

        assertThat(edits.list(user)).filteredOn(edit -> rebuilt.groupId().equals(edit.groupId()))
                .allSatisfy(edit -> assertThat(edit.status()).isEqualTo(org.github.tess1o.geopulse.streaming.edits.TimelineCorrectionStatus.UNDONE));
        assertThat(edits.list(user)).filteredOn(edit -> original.groupId().equals(edit.groupId()))
                .allSatisfy(edit -> assertThat(edit.status()).isEqualTo(org.github.tess1o.geopulse.streaming.edits.TimelineCorrectionStatus.IN_EFFECT));
        assertThat(staysBetween(user, at("14:24"), at("15:24"))).singleElement().satisfies(stay -> {
            assertThat(stay.getTimestamp()).isEqualTo(at("14:24"));
            assertThat(stay.getStayDuration()).isEqualTo(3600L);
            assertThat(stay.getLocationName()).isEqualTo("Original stay");
        });
        assertThat(QuarkusTransaction.requiringNew().call(() -> trips.count("user.id", user))).isZero();

        corrections.changeStatus(user, rebuilt.groupId(), true);
        harness.fullRebuild(user);

        assertThat(edits.list(user)).filteredOn(edit -> rebuilt.groupId().equals(edit.groupId()))
                .hasSize(2).allSatisfy(edit -> {
                    assertThat(edit.status()).isEqualTo(org.github.tess1o.geopulse.streaming.edits.TimelineCorrectionStatus.IN_EFFECT);
                    assertThat(edit.applied()).isTrue();
                });
        assertThat(staysBetween(user, at("14:24"), at("15:24"))).hasSize(4);
        assertThat(onlyTrip(user).getTimestamp()).isEqualTo(at("14:40"));
    }

    private void setTimes(UUID user, Instant anchorStart, Instant anchorEnd,
                          Instant affectedFrom, Instant affectedTo, Instant start, Instant end) {
        var request = new TimelineEditRequest();
        request.setKind(TimelineEditKind.SET_TIMES);
        request.setAnchorStart(anchorStart);
        request.setAnchorEnd(anchorEnd);
        request.setAffectedFrom(affectedFrom);
        request.setAffectedTo(affectedTo);
        put(request, "start", start.toString());
        put(request, "end", end.toString());
        edits.create(user, request);
    }

    private void deleteApplication(Long editId) {
        QuarkusTransaction.requiringNew().run(() -> em
                .createNativeQuery("DELETE FROM timeline_correction_effects WHERE correction_id = ?1")
                .setParameter(1, editId).executeUpdate());
    }

    private List<TimelineStayEntity> staysBetween(UUID user, Instant from, Instant to) {
        return QuarkusTransaction.requiringNew().call(() -> stays
                .find("user.id = ?1 and timestamp >= ?2 and timestamp < ?3 order by timestamp", user, from, to)
                .list());
    }

    private TimelineTripEntity onlyTrip(UUID user) {
        return QuarkusTransaction.requiringNew().call(() -> trips.find("user.id = ?1", user).singleResult());
    }

    private ActivityCorrectionService.Activity stay(String from, String to, double lat, double lon, String name) {
        var activity = new ActivityCorrectionService.Activity();
        activity.setSegmentType("STAY");
        activity.setStartTime(at(from));
        activity.setEndTime(at(to));
        activity.setLatitude(lat);
        activity.setLongitude(lon);
        activity.setLocationName(name);
        return activity;
    }

    private ActivityCorrectionService.Activity travel(String from, String to) {
        var activity = new ActivityCorrectionService.Activity();
        activity.setSegmentType("TRIP");
        activity.setStartTime(at(from));
        activity.setEndTime(at(to));
        activity.setMovementType("WALK");
        activity.setStartPlace(new ActivityCorrectionService.Place(null, null, 49.82, 24.01, "Start"));
        activity.setEndPlace(new ActivityCorrectionService.Place(null, null, 49.83, 24.03, "End"));
        activity.setWaypoints(List.of(new TripReconstructionWaypointDto(49.825, 24.025)));
        return activity;
    }

    private static Instant at(String time) {
        return CommuteWithStops.at(time);
    }
}
