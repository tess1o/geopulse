package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.edits.*;
import org.github.tess1o.geopulse.streaming.model.dto.*;
import org.github.tess1o.geopulse.streaming.model.entity.*;
import org.github.tess1o.geopulse.streaming.repository.*;
import org.github.tess1o.geopulse.testsupport.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;

/** Matching is checked through committed edits and real GPS rebuilds, including real preference changes. */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineEditMatchingIntegrationTest {
    @Inject TimelineReplayHarness harness;
    @Inject TimelineEditService edits;
    @Inject TimelineStayRepository stays;
    @Inject TimelineTripRepository trips;
    @Inject EntityManager em;
    @Inject TestUserFactory users;
    private final CommuteWithStops day = new CommuteWithStops();
    @BeforeEach void mocks() { GeocodingTestMocks.install(); }

    static Stream<Arguments> kindsAndShifts() {
        return Arrays.stream(TimelineEditKind.values()).flatMap(kind -> Stream.of(-20, -10, 10, 20).map(shift -> Arguments.of(kind, shift)));
    }

    @ParameterizedTest(name = "{0} with {1} minute drift")
    @MethodSource("kindsAndShifts")
    void editsMatchShiftedTimelineAndKeepImmutableAnchors(TimelineEditKind kind, int minutes) {
        UUID user = day.ingest(harness, "unified-drift");
        harness.ingest(user, new Track().drive(day.office, day.home, at("12:01"), at("12:45"))
                .stay(day.home, at("12:46"), at("18:00"), 2).messages());
        harness.fullRebuild(user);
        var originalTrips = tripRows(user);
        var outbound = originalTrips.getFirst(); var inbound = originalTrips.getLast();
        var office = stayRows(user).stream().filter(row -> row.getTimestamp().equals(at("08:46"))).findFirst().orElseThrow();
        Instant officeEnd = office.getTimestamp().plusSeconds(office.getStayDuration());
        boolean tripTarget = kind == TimelineEditKind.SET_MOVEMENT_TYPE || kind == TimelineEditKind.MERGE;
        Instant anchorStart = tripTarget ? outbound.getTimestamp() : office.getTimestamp();
        Instant anchorEnd = tripTarget ? outbound.getTimestamp().plusSeconds(outbound.getTripDuration()) : officeEnd;
        Map<String, Object> payload = new LinkedHashMap<>();
        switch (kind) {
            case SET_MOVEMENT_TYPE -> payload.putAll(movementPayload(outbound));
            case SET_PLACE, FORCE_STAY -> payload.putAll(Map.of("locationName", "Corrected place", "latitude", day.bakery[0], "longitude", day.bakery[1]));
            case SET_VISIT_NAME -> payload.put("visitName", "Corrected visit");
            case SET_TIMES -> payload.putAll(Map.of("start", office.getTimestamp().plusSeconds(300).toString(), "end", officeEnd.minusSeconds(300).toString()));
            case SPLIT_STAY -> payload.putAll(Map.of("splitAt", at("10:00").toString(), "rightLocationName", "Second place", "rightLatitude", day.bakery[0], "rightLongitude", day.bakery[1]));
            case MERGE -> payload.put("direction", "NEXT");
            case REMOVE_EVENT -> payload.put("resolution", "GAP");
            case FORCE_TRIP -> payload.putAll(Map.of("movementType", "TRAIN", "startLatitude", day.bakery[0], "startLongitude", day.bakery[1], "endLatitude", day.office[0], "endLongitude", day.office[1]));
        }
        if (kind == TimelineEditKind.FORCE_STAY || kind == TimelineEditKind.FORCE_TRIP) { anchorStart = at("08:16"); anchorEnd = at("08:20"); }
        var saved = edits.create(user, request(kind, anchorStart, anchorEnd, payload));
        harness.fullRebuild(user);
        long oldTarget = current(user, saved.id()).target().id();
        shiftRecording(user, minutes);
        harness.fullRebuild(user);
        var applied = current(user, saved.id());
        assertThat(applied.status()).isEqualTo(TimelineEditStatus.ACTIVE);
        assertThat(applied.applied()).isTrue(); assertThat(applied.target()).isNotNull();
        assertThat(applied.target().id()).isNotEqualTo(oldTarget);
        assertThat(applied.anchorStart()).isEqualTo(anchorStart); assertThat(applied.anchorEnd()).isEqualTo(anchorEnd);
        Instant expectedStart = (tripTarget ? outbound.getTimestamp() : office.getTimestamp()).plusSeconds(minutes * 60L);
        Instant expectedEnd = (tripTarget ? outbound.getTimestamp().plusSeconds(outbound.getTripDuration()) : officeEnd).plusSeconds(minutes * 60L);
        switch (kind) {
            case SET_MOVEMENT_TYPE -> {
                var row = tripRows(user).getFirst();
                assertThat(row.getTimestamp()).isEqualTo(expectedStart);
                assertThat(row.getTimestamp().plusSeconds(row.getTripDuration())).isEqualTo(expectedEnd);
                assertThat(row.getMovementType()).isEqualTo("TRAIN"); assertThat(row.getMovementTypeSource().name()).isEqualTo("MANUAL");
                assertThat(row.getId()).isEqualTo(applied.target().id());
            }
            case SET_PLACE, SET_VISIT_NAME -> {
                var row = stayRows(user).stream().filter(stay -> stay.getId().equals(applied.target().id())).findFirst().orElseThrow();
                assertThat(row.getTimestamp()).isEqualTo(expectedStart);
                assertThat(row.getTimestamp().plusSeconds(row.getStayDuration())).isEqualTo(expectedEnd);
                if (kind == TimelineEditKind.SET_PLACE) {
                    assertThat(row.getLocationName()).isEqualTo("Corrected place"); assertThat(row.getLocation().getY()).isEqualTo(day.bakery[0]);
                } else assertThat(row.getVisitName()).isEqualTo("Corrected visit");
            }
            case SET_TIMES -> {
                var row = stayRows(user).stream().filter(stay -> stay.getId().equals(applied.target().id())).findFirst().orElseThrow();
                assertThat(row.getTimestamp()).isEqualTo(office.getTimestamp().plusSeconds(300));
                assertThat(row.getTimestamp().plusSeconds(row.getStayDuration())).isEqualTo(officeEnd.minusSeconds(300));
            }
            case SPLIT_STAY -> {
                var row = stayRows(user).stream().filter(stay -> stay.getId().equals(applied.target().id())).findFirst().orElseThrow();
                assertThat(row.getTimestamp()).isEqualTo(at("10:00"));
                assertThat(row.getTimestamp().plusSeconds(row.getStayDuration())).isEqualTo(expectedEnd);
                assertThat(row.getLocationName()).isEqualTo("Second place");
            }
            case MERGE -> {
                var rows = tripRows(user); assertThat(rows).hasSize(1);
                assertThat(rows.getFirst().getTimestamp()).isEqualTo(expectedStart);
                assertThat(rows.getFirst().getTimestamp().plusSeconds(rows.getFirst().getTripDuration()))
                        .isEqualTo(inbound.getTimestamp().plusSeconds(inbound.getTripDuration() + minutes * 60L));
            }
            case REMOVE_EVENT -> QuarkusTransaction.requiringNew().run(() -> {
                var gap = em.find(TimelineDataGapEntity.class, applied.target().id());
                assertThat(gap.getStartTime()).isEqualTo(expectedStart); assertThat(gap.getEndTime()).isEqualTo(expectedEnd);
            });
            case FORCE_STAY -> {
                var row = stayRows(user).stream().filter(stay -> stay.getId().equals(applied.target().id())).findFirst().orElseThrow();
                assertThat(row.getTimestamp()).isEqualTo(at("08:16")); assertThat(row.getStayDuration()).isEqualTo(240);
                assertThat(row.getLocationName()).isEqualTo("Corrected place");
            }
            case FORCE_TRIP -> {
                var row = tripRows(user).stream().filter(trip -> trip.getId().equals(applied.target().id())).findFirst().orElseThrow();
                assertThat(row.getTimestamp()).isEqualTo(at("08:16")); assertThat(row.getTripDuration()).isEqualTo(240);
                assertThat(row.getMovementType()).isEqualTo("TRAIN"); assertThat(row.getMovementTypeSource().name()).isEqualTo("MANUAL");
            }
        }
        var changed = snapshot(user);
        harness.fullRebuild(user); assertThat(snapshot(user)).containsExactlyElementsOf(changed);
        edits.revert(user, saved.id()).orElseThrow(); harness.fullRebuild(user);
        assertThat(current(user, saved.id()).applied()).isFalse();
        assertThat(stayRows(user)).hasSize(3); assertThat(tripRows(user)).hasSize(2);
        assertThat(stayRows(user)).allSatisfy(row -> assertThat(row.getVisitName()).isNull());
        edits.restore(user, saved.id()).orElseThrow(); harness.fullRebuild(user);
        assertThat(snapshot(user)).containsExactlyElementsOf(changed);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOMINANT_SUFFIX", "EVEN_SPLIT", "MERGED"})
    void movementMatchingFollowsActualStayDurationPreferenceChanges(String scenario) {
        UUID user = harness.createUser("preference-movement");
        boolean originallySplit = scenario.equals("MERGED");
        setMinimumStay(user, originallySplit ? 5 : 15);
        Track track = new Track(); double[] home = track.place(49.842, 24.031);
        double[] stop = track.place(49.8385, 24.053); double[] office = track.place(49.835, 24.101);
        String stopStart = scenario.equals("EVEN_SPLIT") ? "08:55" : "08:21";
        String stopEnd = scenario.equals("EVEN_SPLIT") ? "09:05" : "08:31";
        harness.ingest(user, track.stay(home, at("06:00"), at("08:00"), 2)
                .drive(home, stop, at("08:01"), at(stopStart).minusSeconds(60))
                .stay(stop, at(stopStart), at(stopEnd), 1)
                .drive(stop, office, at(stopEnd).plusSeconds(60), at("10:00"))
                .stay(office, at("10:01"), at("12:00"), 2).messages());
        harness.fullRebuild(user);
        assertThat(tripRows(user)).hasSize(originallySplit ? 2 : 1);
        var source = tripRows(user).getFirst();
        var edit = edits.create(user, request(TimelineEditKind.SET_MOVEMENT_TYPE, source.getTimestamp(),
                source.getTimestamp().plusSeconds(source.getTripDuration()), movementPayload(source)));
        harness.fullRebuild(user); assertThat(current(user, edit.id()).applied()).isTrue();
        setMinimumStay(user, originallySplit ? 15 : 5); harness.fullRebuild(user);
        var regenerated = tripRows(user);
        assertThat(regenerated).hasSize(originallySplit ? 1 : 2);
        boolean matches = scenario.equals("DOMINANT_SUFFIX");
        assertThat(current(user, edit.id()).applied()).isEqualTo(matches);
        if (matches) {
            var suffix = regenerated.getLast();
            assertThat(Duration.between(source.getTimestamp(), suffix.getTimestamp()).toMinutes()).isGreaterThanOrEqualTo(10);
            assertThat(suffix.getMovementType()).isEqualTo("TRAIN");
            assertThat(suffix.getId()).isEqualTo(current(user, edit.id()).target().id());
            assertThat(regenerated.getFirst().getMovementTypeSource().name()).isEqualTo("AUTO");
        } else {
            assertThat(current(user, edit.id()).target()).isNull();
            assertThat(regenerated).allSatisfy(row -> assertThat(row.getMovementTypeSource().name()).isEqualTo("AUTO"));
        }
        setMinimumStay(user, originallySplit ? 5 : 15); harness.fullRebuild(user);
        assertThat(current(user, edit.id()).applied()).isTrue();
        assertThat(tripRows(user).getFirst().getMovementType()).isEqualTo("TRAIN");
    }

    @ParameterizedTest
    @EnumSource(value = TimelineEditKind.class, names = {"SET_PLACE", "SET_VISIT_NAME"})
    void stayRemovedByPreferenceChangeKeepsItsEditAndRecoversWhenPreferenceReturns(TimelineEditKind kind) {
        UUID user = harness.createUser("preference-stay"); setMinimumStay(user, 3);
        day.ingest(harness, user);
        var bakery = stayRows(user).stream().filter(row -> row.getTimestamp().isBefore(at("08:21"))
                && row.getTimestamp().isAfter(at("08:10"))).findFirst().orElseThrow();
        var payload = kind == TimelineEditKind.SET_VISIT_NAME ? Map.<String, Object>of("visitName", "Coffee")
                : Map.<String, Object>of("locationName", "Chosen place", "latitude", day.bakery[0], "longitude", day.bakery[1]);
        var saved = edits.create(user, request(kind, bakery.getTimestamp(), bakery.getTimestamp().plusSeconds(bakery.getStayDuration()), payload));
        harness.fullRebuild(user); assertThat(current(user, saved.id()).applied()).isTrue();
        setMinimumStay(user, 15); harness.fullRebuild(user);
        var stale = current(user, saved.id());
        assertThat(stale.status()).isEqualTo(TimelineEditStatus.ACTIVE); assertThat(stale.applied()).isFalse(); assertThat(stale.target()).isNull();
        assertThat(stayRows(user)).noneSatisfy(row -> assertThat(row.getTimestamp()).isEqualTo(bakery.getTimestamp()));
        setMinimumStay(user, 3); harness.fullRebuild(user);
        assertThat(current(user, saved.id()).applied()).isTrue();
        var restored = stayRows(user).stream().filter(row -> row.getId().equals(current(user, saved.id()).target().id())).findFirst().orElseThrow();
        if (kind == TimelineEditKind.SET_VISIT_NAME) assertThat(restored.getVisitName()).isEqualTo("Coffee");
        else assertThat(restored.getLocationName()).isEqualTo("Chosen place");
    }

    @Test
    void staleStructuralSplitDoesNotBlockOtherEditsAndCanRecover() {
        UUID user = day.ingest(harness, "stale-split");
        var office = stayRows(user).getLast();
        Instant end = office.getTimestamp().plusSeconds(office.getStayDuration());
        var split = edits.create(user, request(TimelineEditKind.SPLIT_STAY, office.getTimestamp(), end, Map.of("splitAt", at("08:50").toString())));
        var label = edits.create(user, request(TimelineEditKind.SET_VISIT_NAME, office.getTimestamp(), end, Map.of("visitName", "Keep working")));
        harness.fullRebuild(user); assertThat(current(user, split.id()).applied()).isTrue();
        shiftRecording(user, 10); harness.fullRebuild(user);
        assertThat(current(user, split.id()).applied()).isFalse(); assertThat(current(user, split.id()).status()).isEqualTo(TimelineEditStatus.ACTIVE);
        assertThat(current(user, label.id()).applied()).isTrue();
        assertThat(stayRows(user).getLast().getVisitName()).isEqualTo("Keep working");
        shiftRecording(user, -10); harness.fullRebuild(user);
        assertThat(current(user, split.id()).applied()).isTrue(); assertThat(current(user, label.id()).applied()).isTrue();
    }

    @Test
    void deletedTailClearsStaleApplicationEvenOutsideTheNewGeneratedRangeAndRecovers() {
        UUID user = day.ingest(harness, "stale-tail");
        var office = stayRows(user).getLast();
        var saved = edits.create(user, request(TimelineEditKind.SET_VISIT_NAME, office.getTimestamp(),
                office.getTimestamp().plusSeconds(office.getStayDuration()), Map.of("visitName", "Remember me")));
        harness.fullRebuild(user); assertThat(current(user, saved.id()).applied()).isTrue();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("delete from gps_points where user_id = ?1 and timestamp >= ?2")
                .setParameter(1, user).setParameter(2, office.getTimestamp()).executeUpdate());
        harness.fullRebuild(user);
        var stale = current(user, saved.id());
        assertThat(stale.status()).isEqualTo(TimelineEditStatus.ACTIVE); assertThat(stale.applied()).isFalse(); assertThat(stale.target()).isNull();
        harness.ingest(user, day.track.messages().stream().filter(message -> message.getTst() >= office.getTimestamp().getEpochSecond()).toList());
        harness.fullRebuild(user);
        assertThat(current(user, saved.id()).applied()).isTrue(); assertThat(stayRows(user).getLast().getVisitName()).isEqualTo("Remember me");
    }

    @Test
    void deletingAllPointsClearsMissingTripApplicationAndLeavesEditReversible() {
        var actor = users.createAuthenticatedUser("stale-empty");
        UUID user = day.ingest(harness, actor.userId());
        var trip = tripRows(user).getFirst();
        var saved = edits.create(user, request(TimelineEditKind.SET_MOVEMENT_TYPE, trip.getTimestamp(), trip.getTimestamp().plusSeconds(trip.getTripDuration()), movementPayload(trip)));
        harness.fullRebuild(user); assertThat(current(user, saved.id()).applied()).isTrue();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("delete from gps_points where user_id = ?1").setParameter(1, user).executeUpdate());
        harness.fullRebuild(user);
        assertThat(current(user, saved.id()).applied()).isFalse(); assertThat(current(user, saved.id()).target()).isNull();
        var history = io.restassured.RestAssured.given().header("Authorization", "Bearer " + actor.accessToken()).get("/api/v1/timeline/edits");
        assertThat(history.statusCode()).isEqualTo(200);
        assertThat(history.jsonPath().getBoolean("[0].applied")).isFalse();
        assertThat(history.jsonPath().getMap("[0].target")).isNull();
        assertThat(history.jsonPath().getString("[0].status")).isEqualTo("ACTIVE");
        edits.revert(user, saved.id()).orElseThrow(); harness.fullRebuild(user);
        assertThat(current(user, saved.id()).status()).isEqualTo(TimelineEditStatus.REVERTED);
        edits.restore(user, saved.id()).orElseThrow(); harness.fullRebuild(user);
        assertThat(current(user, saved.id()).status()).isEqualTo(TimelineEditStatus.ACTIVE);
        assertThat(current(user, saved.id()).applied()).isFalse();
        harness.ingest(user, day.track.messages()); harness.fullRebuild(user);
        assertThat(current(user, saved.id()).applied()).isTrue();
        assertThat(tripRows(user).getFirst().getMovementType()).isEqualTo("TRAIN");
    }

    @ParameterizedTest
    @EnumSource(value = TimelineEditKind.class, names = {"SET_MOVEMENT_TYPE", "SET_TIMES", "MERGE", "REMOVE_EVENT"})
    void tripMovedFarAwayBecomesUnappliedWithoutRepaintingTheReplacement(TimelineEditKind kind) {
        UUID user = day.ingest(harness, "stale-endpoints");
        if (kind == TimelineEditKind.MERGE) {
            harness.ingest(user, new Track().drive(day.office, day.home, at("12:01"), at("12:45"))
                    .stay(day.home, at("12:46"), at("18:00"), 2).messages());
            harness.fullRebuild(user);
        }
        var trip = tripRows(user).getFirst();
        Map<String, Object> payload = switch (kind) {
            case SET_MOVEMENT_TYPE -> movementPayload(trip);
            case SET_TIMES -> Map.of("start", trip.getTimestamp().plusSeconds(300).toString(), "end", trip.getTimestamp().plusSeconds(trip.getTripDuration() - 300).toString());
            case MERGE -> Map.of("direction", "NEXT");
            case REMOVE_EVENT -> Map.of("resolution", "GAP");
            default -> throw new IllegalArgumentException();
        };
        var saved = edits.create(user, request(kind, trip.getTimestamp(), trip.getTimestamp().plusSeconds(trip.getTripDuration()), payload));
        harness.fullRebuild(user);
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("update gps_points set coordinates = ST_Translate(coordinates, 1, 0) where user_id = ?1").setParameter(1, user).executeUpdate());
        harness.fullRebuild(user);
        assertThat(current(user, saved.id()).applied()).isFalse();
        assertThat(tripRows(user)).allSatisfy(row -> assertThat(row.getMovementTypeSource().name()).isEqualTo("AUTO"));
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("update gps_points set coordinates = ST_Translate(coordinates, -1, 0) where user_id = ?1").setParameter(1, user).executeUpdate());
        harness.fullRebuild(user); assertThat(current(user, saved.id()).applied()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = TimelineEditKind.class, names = {"SET_TIMES", "REMOVE_EVENT"})
    void aStayCorrectionDoesNotRewriteATripReplacingTheStay(TimelineEditKind kind) {
        UUID user = day.ingest(harness, "stale-event-type"); var office = stayRows(user).getLast();
        Instant end = office.getTimestamp().plusSeconds(office.getStayDuration());
        var payload = kind == TimelineEditKind.SET_TIMES
                ? Map.<String, Object>of("start", office.getTimestamp().plusSeconds(300).toString(), "end", end.minusSeconds(300).toString())
                : Map.<String, Object>of("resolution", "GAP");
        var saved = edits.create(user, request(kind, office.getTimestamp(), end, payload));
        harness.fullRebuild(user); assertThat(current(user, saved.id()).applied()).isTrue();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                update gps_points set coordinates = ST_Translate(coordinates,
                    0.01 * extract(epoch from (timestamp - cast(?2 as timestamptz))) / 600, 0), velocity = 45
                where user_id = ?1 and timestamp >= ?2
                """).setParameter(1, user).setParameter(2, office.getTimestamp()).executeUpdate());
        harness.fullRebuild(user);
        assertThat(current(user, saved.id()).applied()).isFalse(); assertThat(current(user, saved.id()).target()).isNull();
        assertThat(tripRows(user)).anySatisfy(row -> {
            assertThat(row.getTimestamp()).isBefore(office.getTimestamp());
            assertThat(row.getTimestamp().plusSeconds(row.getTripDuration())).isAfter(end.minusSeconds(300));
        });
    }

    @ParameterizedTest
    @EnumSource(value = TimelineEditKind.class, names = {"SET_VISIT_NAME", "SET_MOVEMENT_TYPE", "REMOVE_EVENT"})
    void missingTargetCleanupIsOwnerScopedAndPreservesValidApplications(TimelineEditKind kind) {
        UUID user = day.ingest(harness, "cleanup-owner");
        var office = stayRows(user).getLast(); var trip = tripRows(user).getFirst(); var home = stayRows(user).getFirst();
        var stale = edits.create(user, request(kind, kind == TimelineEditKind.SET_MOVEMENT_TYPE ? trip.getTimestamp() : office.getTimestamp(),
                kind == TimelineEditKind.SET_MOVEMENT_TYPE ? trip.getTimestamp().plusSeconds(trip.getTripDuration()) : office.getTimestamp().plusSeconds(office.getStayDuration()),
                kind == TimelineEditKind.SET_MOVEMENT_TYPE ? movementPayload(trip)
                        : kind == TimelineEditKind.SET_VISIT_NAME ? Map.of("visitName", "Stale") : Map.of("resolution", "GAP")));
        var keep = edits.create(user, request(TimelineEditKind.SET_VISIT_NAME, home.getTimestamp(), home.getTimestamp().plusSeconds(home.getStayDuration()), Map.of("visitName", "Keep")));
        harness.fullRebuild(user);
        UUID other = new CommuteWithStops().ingest(harness, "cleanup-other"); var otherOffice = stayRows(other).getLast();
        var otherEdit = edits.create(other, request(TimelineEditKind.SET_VISIT_NAME, otherOffice.getTimestamp(), otherOffice.getTimestamp().plusSeconds(otherOffice.getStayDuration()), Map.of("visitName", "Other owner")));
        harness.fullRebuild(other);
        var target = current(user, stale.id()).target(); var otherTarget = current(other, otherEdit.id()).target();
        long keptId = current(user, keep.id()).target().id();
        QuarkusTransaction.requiringNew().run(() -> {
            Class<?> entity = switch (target.type()) {
                case "STAY" -> TimelineStayEntity.class; case "TRIP" -> TimelineTripEntity.class; case "DATA_GAP" -> TimelineDataGapEntity.class;
                default -> throw new IllegalArgumentException(target.type());
            };
            em.remove(em.find(entity, target.id()));
            em.remove(em.find(TimelineStayEntity.class, otherTarget.id()));
        });
        edits.clearMissingApplications(user);
        assertThat(current(user, stale.id()).applied()).isFalse(); assertThat(current(user, stale.id()).target()).isNull();
        assertThat(current(user, stale.id()).status()).isEqualTo(TimelineEditStatus.ACTIVE);
        assertThat(current(user, keep.id()).target().id()).isEqualTo(keptId);
        assertThat(current(other, otherEdit.id()).applied()).isTrue();
        edits.clearMissingApplications(other);
        assertThat(current(other, otherEdit.id()).applied()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TimelineEditKind.class, names = {"FORCE_STAY", "SPLIT_STAY"})
    void noGpsPassPreservesAnExistingManualStayUntilUndone(TimelineEditKind kind) {
        UUID user = day.ingest(harness, "manual-without-gps"); var office = stayRows(user).getLast();
        var payload = kind == TimelineEditKind.FORCE_STAY
                ? Map.<String, Object>of("locationName", "Manual stop", "latitude", day.bakery[0], "longitude", day.bakery[1])
                : Map.<String, Object>of("splitAt", at("10:00").toString());
        var saved = edits.create(user, request(kind, kind == TimelineEditKind.FORCE_STAY ? at("08:16") : office.getTimestamp(),
                kind == TimelineEditKind.FORCE_STAY ? at("08:20") : office.getTimestamp().plusSeconds(office.getStayDuration()), payload));
        harness.fullRebuild(user); var target = current(user, saved.id()).target();
        var manual = stayRows(user).stream().filter(row -> row.getId().equals(target.id())).findFirst().orElseThrow();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("delete from gps_points where user_id = ?1").setParameter(1, user).executeUpdate());
        harness.fullRebuild(user);
        assertThat(current(user, saved.id()).applied()).isTrue();
        assertThat(stayRows(user)).singleElement().satisfies(row -> {
            assertThat(row.getId()).isEqualTo(manual.getId()); assertThat(row.getTimestamp()).isEqualTo(manual.getTimestamp());
            assertThat(row.getStayDuration()).isEqualTo(manual.getStayDuration());
        });
        edits.revert(user, saved.id()).orElseThrow(); harness.fullRebuild(user);
        assertThat(stayRows(user)).isEmpty(); assertThat(current(user, saved.id()).applied()).isFalse();
        edits.restore(user, saved.id()).orElseThrow(); harness.fullRebuild(user);
        assertThat(current(user, saved.id()).status()).isEqualTo(TimelineEditStatus.ACTIVE);
        assertThat(current(user, saved.id()).applied()).isFalse();
        harness.ingest(user, day.track.messages()); harness.fullRebuild(user);
        assertThat(current(user, saved.id()).applied()).isTrue();
    }

    private TimelineEditDTO current(UUID user, Long id) { return edits.list(user).stream().filter(edit -> edit.id().equals(id)).findFirst().orElseThrow(); }
    private List<TimelineStayEntity> stayRows(UUID user) { return QuarkusTransaction.requiringNew().call(() -> stays.find("user.id = ?1 order by timestamp", user).list()); }
    private List<TimelineTripEntity> tripRows(UUID user) { return QuarkusTransaction.requiringNew().call(() -> trips.find("user.id = ?1 order by timestamp", user).list()); }
    private List<String> snapshot(UUID user) {
        var rows = new ArrayList<>(harness.snapshot(user).content());
        stayRows(user).forEach(row -> rows.add("LABEL " + row.getTimestamp() + " " + row.getVisitName())); return rows;
    }
    private void shiftRecording(UUID user, int minutes) {
        QuarkusTransaction.requiringNew().run(() -> {
            // Stationary points share coordinates; move this single-day fixture aside to avoid intermediate key collisions.
            em.createNativeQuery("update gps_points set timestamp = timestamp + interval '2 days' where user_id = ?1")
                    .setParameter(1, user).executeUpdate();
            em.createNativeQuery("update gps_points set timestamp = timestamp - interval '2 days' + ?2 * interval '1 minute' where user_id = ?1")
                    .setParameter(1, user).setParameter(2, minutes).executeUpdate();
        });
    }
    private void setMinimumStay(UUID user, int minutes) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                update users set timeline_preferences = coalesce(timeline_preferences, '{}'::jsonb)
                    || jsonb_build_object('staypointMinDurationMinutes', cast(?2 as integer), 'isMergeEnabled', false)
                where id = ?1
                """).setParameter(1, user).setParameter(2, minutes).executeUpdate());
    }
    private static TimelineEditRequest request(TimelineEditKind kind, Instant start, Instant end, Map<String, Object> payload) {
        var request = new TimelineEditRequest(); request.setKind(kind); request.setAnchorStart(start); request.setAnchorEnd(end);
        request.setAffectedFrom(at("06:00")); request.setAffectedTo(at("18:00")); request.setPayload(new LinkedHashMap<>(payload)); return request;
    }
    private static Map<String, Object> movementPayload(TimelineTripEntity trip) {
        return Map.of("movementType", "TRAIN", "sourceStartLatitude", trip.getStartPoint().getY(), "sourceStartLongitude", trip.getStartPoint().getX(),
                "sourceEndLatitude", trip.getEndPoint().getY(), "sourceEndLongitude", trip.getEndPoint().getX());
    }
    private static Instant at(String time) { return CommuteWithStops.at(time); }
}
