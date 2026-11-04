package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditService;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditStatus;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayNameOverrideRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripMovementOverrideRepository;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestActor;
import org.github.tess1o.geopulse.testsupport.TestUserFactory;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionCommitResponseDto;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionRequestDto;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionSegmentDto;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionWaypointDto;
import org.github.tess1o.geopulse.trips.service.TripReconstructionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reconstructing a stretch without data adds points for the stays and trips the user describes. What the user says
 * about them must come out on the timeline as said, a rebuild included: the name given to a stay, the way a trip was
 * travelled. And it fills a stretch without data only: mixed into recorded points, the made-up ones would distort them.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineReconstructionIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    ManualEditsSupport edits;

    @Inject
    TestUserFactory testUserFactory;

    @Inject
    TripReconstructionService reconstructionService;

    @Inject
    TimelineStayNameOverrideRepository stayNameOverrideRepository;

    @Inject
    TimelineTripMovementOverrideRepository movementOverrideRepository;

    @Inject StreamingTimelineGenerationService generation;
    @Inject TimelineEditService timelineEdits;
    @Inject TimelineStayRepository stays;
    @Inject EntityManager entityManager;

    private final CommuteWithStops day = new CommuteWithStops();
    private final double[] grandma = day.track.place(49.8200, 24.0100);

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void reconstructedStayKeepsItsNameAndTripsTheirMovementType() {
        UUID userId = officeDayWithAnAfternoonWithoutData(harness.createUser("reconstruct-named"));

        TripReconstructionCommitResponseDto result = reconstructionService.commit(userId, visitToGrandma());
        edits.awaitJob(UUID.fromString(result.jobId()));

        assertReconstructedAsDescribed(userId, "after the reconstruction");
        harness.fullRebuild(userId);
        assertReconstructedAsDescribed(userId, "after a full rebuild");
    }

    @Test
    void committingTheSameReconstructionAgainUpdatesTheCorrectionsOnce() {
        UUID userId = officeDayWithAnAfternoonWithoutData(harness.createUser("reconstruct-recommit"));
        TripReconstructionCommitResponseDto first = reconstructionService.commit(userId, visitToGrandma());
        edits.awaitJob(UUID.fromString(first.jobId()));

        TripReconstructionCommitResponseDto second = reconstructionService.commit(userId,
                visitToGrandma("Grandma's place", "WALK", "CAR"));

        assertThat(second.insertedPoints()).as("all reconstruction points were already stored").isZero();
        assertThat(second.jobId()).as("the changed corrections still regenerate the timeline").isNotNull();
        edits.awaitJob(UUID.fromString(second.jobId()));

        assertThat(QuarkusTransaction.requiringNew().call(() ->
                stayNameOverrideRepository.count("user.id = ?1", userId))).isZero();
        assertThat(timelineEdits.list(userId).stream()
                .filter(edit -> edit.kind() == TimelineEditKind.FORCE_STAY && edit.status() == TimelineEditStatus.ACTIVE))
                .as("one active exact stay correction").hasSize(1);
        assertThat(QuarkusTransaction.requiringNew().call(() ->
                movementOverrideRepository.findByUserId(userId)))
                .as("one movement correction per reconstructed trip")
                .hasSize(2);

        TimelineReplayHarness.TimelineSnapshot timeline = harness.snapshot(userId);
        assertThat(timeline.stays()).as("stays after the second commit")
                .anySatisfy(stay -> assertThat(stay).contains("'Grandma's place'"));
        assertThat(timeline.trips()).as("trips after the second commit")
                .anySatisfy(trip -> assertThat(trip).contains(" WALK "))
                .anySatisfy(trip -> assertThat(trip).contains(" CAR "));
    }

    @Test
    void reconstructedStayNameAndMovementTypesAreExportedAndImported() {
        UUID original = officeDayWithAnAfternoonWithoutData(harness.createUser("reconstruct-export"));
        edits.awaitJob(UUID.fromString(reconstructionService.commit(original, visitToGrandma()).jobId()));

        byte[] export = edits.exportOf(original, at("00:00"), at("23:59"));
        var savedEdits = harness.readExportJson(export, "timeline-edits.json").path("edits");
        assertThat(savedEdits).anySatisfy(edit -> {
            assertThat(edit.path("kind").asText()).isEqualTo("FORCE_STAY");
            assertThat(edit.path("payload").path("locationName").asText()).isEqualTo("Grandma's");
        });

        UUID imported = harness.createUser("reconstruct-import");
        edits.importInto(imported, export);

        assertReconstructedAsDescribed(imported, "after export and import");
    }

    @Test
    void reconstructionOverRecordedPointsIsRefused() {
        TestActor actor = testUserFactory.createAuthenticatedUser("reconstruct-overlap");
        UUID userId = officeDayWithAnAfternoonWithoutData(actor.userId());
        // A stay at grandma's while the phone was recording at the office.
        TripReconstructionRequestDto overRecordedPoints = new TripReconstructionRequestDto(null, List.of(
                stay(at("10:00"), at("11:00"), grandma, "Grandma's")));

        for (String step : List.of("preview", "commit")) {
            Response response = given().contentType(ContentType.JSON)
                    .header("Authorization", "Bearer " + actor.accessToken())
                    .body(overRecordedPoints)
                    .when().post("/api/v1/trip-planning/reconstructions/" + step);
            assertThat(response.statusCode()).as("%s over recorded points", step).isEqualTo(409);
        }
        assertThat(harness.pointIds(userId, at("10:00"), at("11:00")))
                .as("only the recorded points")
                .hasSize(6);
    }

    @Test
    void shortUnnamedStayWorksAloneAndSurvivesRebuildAndUndoRedo() {
        UUID userId = harness.createUser("reconstruct-short");
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery(
                "update users set timeline_preferences = coalesce(timeline_preferences, '{}'::jsonb) "
                        + "|| '{\"staypointMinDurationMinutes\":7}'::jsonb where id = ?1")
                .setParameter(1, userId).executeUpdate());
        var request = new TripReconstructionRequestDto(null, List.of(stay(at("14:24"), at("14:27"), grandma, null)));
        edits.awaitJob(UUID.fromString(reconstructionService.commit(userId, request).jobId()));
        assertExactShortStay(userId);
        harness.fullRebuild(userId);
        assertExactShortStay(userId);
        var correction = timelineEdits.list(userId).getFirst();
        timelineEdits.revert(userId, correction.id());
        harness.fullRebuild(userId);
        assertThat(timelineEdits.list(userId).getFirst().status()).isEqualTo(TimelineEditStatus.REVERTED);
        timelineEdits.restore(userId, correction.id());
        harness.fullRebuild(userId);
        assertExactShortStay(userId);
    }

    @Test
    void recordedBoundaryPointsAreAllowedAndPreserved() {
        UUID userId = harness.createUser("reconstruct-boundaries");
        harness.ingest(userId, day.track.stay(grandma, at("14:24"), at("14:27"), 3).messages());
        var request = new TripReconstructionRequestDto(null, List.of(stay(at("14:24"), at("14:27"), grandma, "Visit")));
        assertThat(reconstructionService.preview(userId, request).estimatedPoints()).isPositive();
        var originalIds = harness.pointIds(userId, at("14:24"), at("14:28"));
        edits.awaitJob(UUID.fromString(reconstructionService.commit(userId, request).jobId()));
        assertThat(harness.pointIds(userId, at("14:24"), at("14:28"))).containsAll(originalIds);
        assertExactShortStay(userId);
    }

    @Test
    void samePlaceNeighborsDoNotAbsorbTheEnteredInterval() {
        UUID userId = harness.createUser("reconstruct-neighbors");
        harness.ingest(userId, day.track.stay(grandma, at("14:00"), at("14:24"), 3).messages());
        harness.ingest(userId, day.track.stay(grandma, at("14:27"), at("15:00"), 3).messages());
        harness.fullRebuild(userId);
        var request = new TripReconstructionRequestDto(null, List.of(stay(at("14:24"), at("14:27"), grandma, "Short visit")));
        edits.awaitJob(UUID.fromString(reconstructionService.commit(userId, request).jobId()));
        assertExactShortStay(userId);
        harness.ingest(userId, day.track.stay(grandma, at("15:03"), at("15:30"), 3).messages());
        generation.generateTimelineFromTimestamp(userId, at("15:03"));
        assertExactShortStay(userId);
        harness.fullRebuild(userId);
        assertExactShortStay(userId);
    }

    @Test
    void structuralConflictRollsBackGeneratedPoints() {
        UUID userId = harness.createUser("reconstruct-conflict");
        var forced = new TimelineEditRequest();
        forced.setKind(TimelineEditKind.FORCE_STAY);
        forced.setAnchorStart(at("14:24")); forced.setAnchorEnd(at("14:27"));
        forced.setAffectedFrom(at("14:24")); forced.setAffectedTo(at("14:27"));
        forced.setLatitude(grandma[0]); forced.setLongitude(grandma[1]);
        timelineEdits.create(userId, forced);
        var request = new TripReconstructionRequestDto(null, List.of(stay(at("14:24"), at("14:27"), grandma, "Visit")));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> reconstructionService.commit(userId, request))
                .isInstanceOf(org.github.tess1o.geopulse.shared.api.GeoPulseException.class);
        assertThat(harness.pointIds(userId, at("14:24"), at("14:28"))).isEmpty();
    }

    private void assertExactShortStay(UUID userId) {
        QuarkusTransaction.requiringNew().run(() -> assertThat(stays.find("user.id", userId).<org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity>list())
                .anySatisfy(stay -> {
                    assertThat(stay.getTimestamp()).isEqualTo(at("14:24"));
                    assertThat(stay.getStayDuration()).isEqualTo(180);
                }));
    }

    private void assertReconstructedAsDescribed(UUID userId, String when) {
        TimelineReplayHarness.TimelineSnapshot timeline = harness.snapshot(userId);
        assertThat(timeline.stays()).as("stays %s", when)
                .anySatisfy(stay -> assertThat(mostlyWithin(stay, at("12:50"), at("15:10"))
                        && stay.contains("'Grandma's'")).as(stay).isTrue());
        assertThat(timeline.trips()).as("trips %s", when)
                .anySatisfy(trip -> assertThat(mostlyWithin(trip, at("12:10"), at("12:50"))
                        && trip.contains(" PUBLIC_TRANSPORT ")).as(trip).isTrue())
                .anySatisfy(trip -> assertThat(mostlyWithin(trip, at("15:10"), at("15:50"))
                        && trip.contains(" MOTORCYCLE ")).as(trip).isTrue());
    }

    /** Whether a described stay or trip ("TYPE start durations ...") lies mostly within the window. */
    private static boolean mostlyWithin(String item, Instant from, Instant to) {
        String[] parts = item.split(" ");
        Instant start = Instant.parse(parts[1]);
        long seconds = Long.parseLong(parts[2].replace("s", ""));
        Instant end = start.plusSeconds(seconds);
        Instant sharedStart = start.isAfter(from) ? start : from;
        Instant sharedEnd = end.isBefore(to) ? end : to;
        return seconds > 0 && java.time.Duration.between(sharedStart, sharedEnd).getSeconds() * 2 > seconds;
    }

    /** By bus to grandma's after work, two hours there, back to the office by motorcycle. */
    private TripReconstructionRequestDto visitToGrandma() {
        return visitToGrandma("Grandma's", "PUBLIC_TRANSPORT", "MOTORCYCLE");
    }

    private TripReconstructionRequestDto visitToGrandma(String stayName, String outbound, String inbound) {
        return new TripReconstructionRequestDto(null, List.of(
                trip(at("12:10"), at("12:50"), outbound, day.office, grandma),
                stay(at("13:00"), at("15:00"), grandma, stayName),
                trip(at("15:10"), at("15:50"), inbound, grandma, day.office)));
    }

    private UUID officeDayWithAnAfternoonWithoutData(UUID userId) {
        day.ingest(harness, userId);
        harness.ingest(userId, day.track.stay(day.office, at("16:00"), at("17:00"), 10).messages());
        harness.fullRebuild(userId);
        return userId;
    }

    private static TripReconstructionSegmentDto stay(Instant from, Instant to, double[] place, String name) {
        TripReconstructionSegmentDto segment = new TripReconstructionSegmentDto();
        segment.setSegmentType("STAY");
        segment.setStartTime(from);
        segment.setEndTime(to);
        segment.setLatitude(place[0]);
        segment.setLongitude(place[1]);
        segment.setLocationName(name);
        return segment;
    }

    private static TripReconstructionSegmentDto trip(Instant from, Instant to, String movementType,
                                                     double[] start, double[] end) {
        TripReconstructionSegmentDto segment = new TripReconstructionSegmentDto();
        segment.setSegmentType("TRIP");
        segment.setStartTime(from);
        segment.setEndTime(to);
        segment.setMovementType(movementType);
        segment.setWaypoints(List.of(new TripReconstructionWaypointDto(start[0], start[1]),
                new TripReconstructionWaypointDto(end[0], end[1])));
        return segment;
    }

    private static Instant at(String time) {
        return CommuteWithStops.at(time);
    }
}
