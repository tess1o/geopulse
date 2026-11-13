package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.streaming.edits.ActivityCorrectionService;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditService;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditStatus;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Issue 666: any structural edit may follow an earlier structural correction in the same range. The corrections form
 * an ordered chain; undo unwinds it and cascade undo removes the dependents with it.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineEditChainIntegrationTest {

    @Inject TimelineReplayHarness harness;
    @Inject TimelineEditService edits;
    @Inject ActivityCorrectionService corrections;
    @Inject TimelineStayRepository stays;
    @Inject TimelineTripRepository trips;
    @Inject TimelineDataGapRepository gaps;

    @BeforeEach
    void setup() {
        GeocodingTestMocks.install();
    }

    /** The reporter's screenshot 1: a stay is shortened, then the adjacent trip is retimed inside the same range. */
    @Test
    void retimingAStayThenAnAdjacentTripChains() {
        var user = new CommuteWithStops().ingest(harness, "chain-adjacent");
        var home = stayAt(user, at("06:00"));
        var first = setTimes(user, home, at("06:00"), at("07:30"));
        harness.fullRebuild(user);

        var trip = onlyTrip(user);
        assertThat(trip.getTimestamp()).isEqualTo(at("07:30"));

        var second = setTimes(user, trip.getTimestamp(),
                trip.getTimestamp().plusSeconds(trip.getTripDuration()),
                at("06:00"), trip.getTimestamp().plusSeconds(trip.getTripDuration()),
                at("07:30"), at("08:00"));

        assertThatCode(() -> harness.fullRebuild(user)).doesNotThrowAnyException();
        assertThat(edits.list(user)).filteredOn(edit -> edit.id().equals(first.id()) || edit.id().equals(second.id()))
                .allSatisfy(edit -> assertThat(edit.status()).isEqualTo(TimelineEditStatus.ACTIVE));

        assertThat(stayAt(user, at("06:00")).getStayDuration()).isEqualTo(Duration.ofMinutes(90).toSeconds());
        var retimed = onlyTrip(user);
        assertThat(retimed.getTimestamp()).isEqualTo(at("07:30"));
        assertThat(retimed.getTripDuration()).isEqualTo(Duration.ofMinutes(30).toSeconds());
    }

    @Test
    void splittingAStayAfterRetimingItChains() {
        var user = new CommuteWithStops().ingest(harness, "chain-split");
        var home = stayAt(user, at("06:00"));
        setTimes(user, home, at("06:00"), at("07:30"));
        harness.fullRebuild(user);

        var split = new TimelineEditRequest();
        split.setKind(TimelineEditKind.SPLIT_STAY);
        split.setAnchorStart(at("06:00"));
        split.setAnchorEnd(at("07:30"));
        split.setAffectedFrom(at("06:00"));
        split.setAffectedTo(at("07:30"));
        split.getPayload().put("splitAt", at("07:00").toString());
        split.getPayload().put("rightLatitude", 49.8420);
        split.getPayload().put("rightLongitude", 24.0310);
        split.getPayload().put("rightLocationName", "Home, second half");

        assertThatCode(() -> edits.create(user, split)).doesNotThrowAnyException();
        harness.fullRebuild(user);

        List<TimelineStayEntity> splitStays = QuarkusTransaction.requiringNew().call(() -> stays
                .find("user.id = ?1 and (timestamp = ?2 or timestamp = ?3) order by timestamp",
                        user, at("06:00"), at("07:00"))
                .list());
        assertThat(splitStays)
                .extracting(TimelineStayEntity::getStayDuration)
                .containsExactly(3600L, 1800L);
    }

    @Test
    void removingAnEventAfterRetimingItsNeighbourChains() {
        var user = new CommuteWithStops().ingest(harness, "chain-remove");
        var home = stayAt(user, at("06:00"));
        setTimes(user, home, at("06:00"), at("07:30"));
        harness.fullRebuild(user);

        var trip = onlyTrip(user);
        var remove = new TimelineEditRequest();
        remove.setKind(TimelineEditKind.REMOVE_EVENT);
        remove.setAnchorStart(trip.getTimestamp());
        remove.setAnchorEnd(trip.getTimestamp().plusSeconds(trip.getTripDuration()));
        remove.setAffectedFrom(at("06:00"));
        remove.setAffectedTo(trip.getTimestamp().plusSeconds(trip.getTripDuration()));
        remove.getPayload().put("resolution", "GAP");

        assertThatCode(() -> edits.create(user, remove)).doesNotThrowAnyException();
        harness.fullRebuild(user);

        assertThat(QuarkusTransaction.requiringNew().call(() -> trips.count("user.id", user))).isZero();
        assertThat(QuarkusTransaction.requiringNew().call(() -> gaps
                .find("user.id = ?1 and startTime = ?2", user, at("07:30")).firstResultOptional()))
                .isPresent();
    }

    /** The 2.3.0 lock is gone: a grouped correction may be followed by an ordinary structural edit. */
    @Test
    void aStructuralEditAfterAnActivityCorrectionGroupChains() {
        var user = harness.createUser("chain-group");
        var group = corrections.save(user, new ActivityCorrectionService.Request(null,
                List.of(stayActivity("14:24", "14:27")), at("14:00"), at("16:00")));
        harness.fullRebuild(user);

        var shortened = setTimes(user, at("14:24"), at("14:27"), at("14:24"), at("14:27"),
                at("14:24"), at("14:26"));

        assertThatCode(() -> harness.fullRebuild(user)).doesNotThrowAnyException();
        assertThat(stayAt(user, at("14:24")).getStayDuration()).isEqualTo(120L);

        assertThatThrownBy(() -> corrections.changeStatus(user, group.groupId(), false))
                .isInstanceOf(GeoPulseException.class)
                .hasMessageContaining("Undo the later correction first");

        corrections.changeStatus(user, group.groupId(), false, true);
        harness.fullRebuild(user);
        assertThat(QuarkusTransaction.requiringNew().call(() -> stays.count("user.id", user))).isZero();
        assertThat(edits.list(user)).filteredOn(edit -> edit.id().equals(shortened.id()))
                .allSatisfy(edit -> assertThat(edit.status()).isEqualTo(TimelineEditStatus.REVERTED));
    }

    @Test
    void cascadeUndoRevertsDependents() {
        var user = new CommuteWithStops().ingest(harness, "chain-cascade");
        var home = stayAt(user, at("06:00"));
        long originalDuration = home.getStayDuration();
        var first = setTimes(user, home, at("06:00"), at("07:30"));
        harness.fullRebuild(user);
        var trip = onlyTrip(user);
        var second = setTimes(user, trip.getTimestamp(),
                trip.getTimestamp().plusSeconds(trip.getTripDuration()),
                at("06:00"), trip.getTimestamp().plusSeconds(trip.getTripDuration()),
                at("07:30"), at("08:00"));

        assertThatThrownBy(() -> edits.revert(user, first.id(), false))
                .isInstanceOf(GeoPulseException.class)
                .hasMessageContaining("Undo the later correction first");

        edits.revert(user, first.id(), true);
        assertThat(edits.list(user))
                .filteredOn(edit -> edit.id().equals(first.id()) || edit.id().equals(second.id()))
                .allSatisfy(edit -> assertThat(edit.status()).isEqualTo(TimelineEditStatus.REVERTED));

        harness.fullRebuild(user);
        assertThat(stayAt(user, at("06:00")).getStayDuration()).isEqualTo(originalDuration);
        assertThat(edits.list(user)).filteredOn(edit -> edit.status() == TimelineEditStatus.ACTIVE).isEmpty();
    }

    @Test
    void aChainSurvivesLiveReplayAndMatchesAFullRebuild() {
        var user = new CommuteWithStops().ingest(harness, "chain-replay");
        var home = stayAt(user, at("06:00"));
        setTimes(user, home, at("06:00"), at("07:30"));
        harness.fullRebuild(user);
        var trip = onlyTrip(user);
        setTimes(user, trip.getTimestamp(), trip.getTimestamp().plusSeconds(trip.getTripDuration()),
                at("06:00"), trip.getTimestamp().plusSeconds(trip.getTripDuration()), at("07:30"), at("08:00"));

        harness.process(user);
        harness.assertMatchesFullRebuild(user, "structural chain");
    }

    private TimelineEditDTO setTimes(UUID user, TimelineStayEntity stay, Instant start, Instant end) {
        return setTimes(user, stay.getTimestamp(), stay.getTimestamp().plusSeconds(stay.getStayDuration()),
                stay.getTimestamp(), stay.getTimestamp().plusSeconds(stay.getStayDuration()), start, end);
    }

    private TimelineEditDTO setTimes(UUID user, Instant anchorStart, Instant anchorEnd,
                                     Instant affectedFrom, Instant affectedTo, Instant start, Instant end) {
        var request = new TimelineEditRequest();
        request.setKind(TimelineEditKind.SET_TIMES);
        request.setAnchorStart(anchorStart);
        request.setAnchorEnd(anchorEnd);
        request.setAffectedFrom(affectedFrom);
        request.setAffectedTo(affectedTo);
        request.getPayload().put("start", start.toString());
        request.getPayload().put("end", end.toString());
        return edits.create(user, request);
    }

    private TimelineStayEntity stayAt(UUID user, Instant start) {
        return QuarkusTransaction.requiringNew().call(() -> stays
                .find("user.id = ?1 and timestamp = ?2", user, start).singleResult());
    }

    private TimelineTripEntity onlyTrip(UUID user) {
        return QuarkusTransaction.requiringNew().call(() -> trips.find("user.id = ?1", user).singleResult());
    }

    private ActivityCorrectionService.Activity stayActivity(String from, String to) {
        var activity = new ActivityCorrectionService.Activity();
        activity.setSegmentType("STAY");
        activity.setStartTime(at(from));
        activity.setEndTime(at(to));
        activity.setLatitude(49.82);
        activity.setLongitude(24.01);
        activity.setLocationName("Short visit");
        return activity;
    }

    private static Instant at(String time) {
        return CommuteWithStops.at(time);
    }
}
