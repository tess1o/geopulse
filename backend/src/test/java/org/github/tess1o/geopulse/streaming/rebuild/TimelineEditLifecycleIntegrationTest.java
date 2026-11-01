package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.edits.*;
import org.github.tess1o.geopulse.streaming.model.dto.*;
import org.github.tess1o.geopulse.streaming.model.entity.*;
import org.github.tess1o.geopulse.streaming.repository.*;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import jakarta.persistence.EntityManager;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Real GPS -> edit service -> generation -> committed database rows. No generation mocks. */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineEditLifecycleIntegrationTest {
    @Inject TimelineReplayHarness harness;
    @Inject TimelineEditService edits;
    @Inject ManualEditsSupport transfers;
    @Inject org.github.tess1o.geopulse.favorites.service.FavoriteLocationService favorites;
    @Inject TimelineStayRepository stays;
    @Inject EntityManager em;
    @Inject TimelineTripRepository trips;
    @Inject TimelineDataGapRepository gaps;
    @Inject StreamingTimelineGenerationService generation;
    private final CommuteWithStops day = new CommuteWithStops();

    @BeforeEach
    void mocks() { GeocodingTestMocks.install(); }

    @ParameterizedTest(name = "{0}: persisted output, undo, redo and incremental replay")
    @ValueSource(strings = {"SET_MOVEMENT_TYPE", "SET_PLACE", "SET_VISIT_NAME", "FORCE_STAY", "FORCE_TRIP",
            "SET_TIMES", "SPLIT_STAY", "FORCE_STAY_SUBMINUTE", "MERGE_PREVIOUS", "MERGE_NEXT", "REMOVE_GAP",
            "REMOVE_EXTEND_PREVIOUS", "REMOVE_EXTEND_NEXT", "REMOVE_MERGE"})
    void editLifecycle(String scenario) {
        UUID user = day.ingest(harness, "edit-lifecycle");
        // A return trip makes both merge directions and every removal resolution possible.
        Track afternoon = new Track();
        harness.ingest(user, afternoon.drive(day.office, day.home, at("12:01"), at("12:45"))
                .stay(day.home, at("12:46"), at("18:00"), 2).messages());
        if (scenario.equals("FORCE_TRIP")) {
            disableGapInference(user);
            harness.ingest(user, new Track().stay(day.office, at("21:00"), at("22:00"), 2).messages());
        }
        harness.fullRebuild(user);
        var baseline = snapshot(user);
        List<TimelineTripEntity> originalTrips = tripRows(user);
        assertThat(originalTrips).hasSize(2);
        TimelineTripEntity outbound = originalTrips.getFirst();
        TimelineTripEntity inbound = originalTrips.getLast();
        TimelineStayEntity office = stayRows(user).stream()
                .filter(row -> row.getTimestamp().equals(at("08:46"))).findFirst().orElseThrow();
        Instant officeEnd = office.getTimestamp().plusSeconds(office.getStayDuration());
        TimelineEditRequest request = request(scenario, outbound, inbound, office, officeEnd, user);
        var preview = edits.preview(user, request);
        assertThat(preview.events()).isNotEmpty();
        var edit = edits.create(user, request);
        harness.fullRebuild(user);
        assertOutput(user, scenario, outbound, inbound, office, officeEnd);
        assertPreviewMatchesStored(user, preview);
        assertApplied(user, edit.id());
        var changed = snapshot(user);
        assertThat(changed).isNotEqualTo(baseline);
        harness.fullRebuild(user);
        assertThat(snapshot(user)).containsExactlyElementsOf(changed);
        if (request.getKind().structural()) {
            generation.generateTimelineFromTimestamp(user, request.getAnchorStart().plusSeconds(60));
            assertThat(snapshot(user)).containsExactlyElementsOf(changed);
            assertApplied(user, edit.id());
        }
        edits.revert(user, edit.id()).orElseThrow();
        harness.fullRebuild(user);
        assertThat(snapshot(user)).containsExactlyElementsOf(baseline);
        assertThat(edits.list(user)).filteredOn(row -> row.id().equals(edit.id())).singleElement()
                .satisfies(row -> { assertThat(row.status()).isEqualTo(TimelineEditStatus.REVERTED); assertThat(row.applied()).isFalse(); });
        edits.restore(user, edit.id()).orElseThrow();
        harness.fullRebuild(user);
        assertOutput(user, scenario, outbound, inbound, office, officeEnd);
        assertThat(snapshot(user)).containsExactlyElementsOf(changed);
        assertApplied(user, edit.id());
        UUID imported = harness.createUser("edit-lifecycle-import");
        if (scenario.equals("FORCE_TRIP")) disableGapInference(imported);
        transfers.importInto(imported, transfers.exportOf(user, at("00:00"), at("23:59")));
        transfers.awaitTimelineJobs(imported);
        harness.fullRebuild(imported);
        assertOutput(imported, scenario, outbound, inbound, office, officeEnd);
        assertThat(harness.snapshot(imported).contentAcrossUsers())
                .containsExactlyElementsOf(harness.snapshot(user).contentAcrossUsers());
        var importedEdit = edits.list(imported).getFirst();
        assertThat(importedEdit.kind()).isEqualTo(request.getKind());
        assertApplied(imported, importedEdit.id());
    }

    @org.junit.jupiter.api.Test
    void movementApplicationRemainsAfterLaterPasses() {
        everyEditSurvivesLiveIngestion("SET_MOVEMENT_TYPE", 15);
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> liveCases() {
        return java.util.stream.Stream.of("SET_MOVEMENT_TYPE", "SET_PLACE", "SET_VISIT_NAME", "FORCE_STAY", "FORCE_TRIP",
                "SET_TIMES", "SPLIT_STAY", "FORCE_STAY_SUBMINUTE", "MERGE_PREVIOUS", "MERGE_NEXT", "REMOVE_GAP", "REMOVE_EXTEND_PREVIOUS",
                "REMOVE_EXTEND_NEXT", "REMOVE_MERGE").flatMap(scenario -> java.util.stream.Stream.of(1, 5, 15)
                .map(batch -> org.junit.jupiter.params.provider.Arguments.of(scenario, batch)));
    }

    @ParameterizedTest(name = "{0}, live batches of {1}")
    @org.junit.jupiter.params.provider.MethodSource("liveCases")
    void everyEditSurvivesLiveIngestion(String scenario, int batchSize) {
        UUID source = day.ingest(harness, "live-edit-source");
        var messages = new Track().drive(day.office, day.home, at("12:01"), at("12:45"))
                .stay(day.home, at("12:46"), at("18:00"), 2).messages();
        harness.ingest(source, messages);
        if (scenario.equals("FORCE_TRIP")) {
            disableGapInference(source);
            harness.ingest(source, new Track().stay(day.office, at("21:00"), at("22:00"), 2).messages());
        }
        harness.fullRebuild(source);
        var originals = tripRows(source);
        var office = stayRows(source).stream().filter(row -> row.getTimestamp().equals(at("08:46"))).findFirst().orElseThrow();
        var officeEnd = office.getTimestamp().plusSeconds(office.getStayDuration());
        var request = request(scenario, originals.getFirst(), originals.getLast(), office, officeEnd, source);
        edits.create(source, request);
        harness.fullRebuild(source);
        if (scenario.equals("FORCE_STAY")) {
            var label = new TimelineEditRequest(); label.setKind(TimelineEditKind.SET_VISIT_NAME);
            label.setAnchorStart(at("08:16")); label.setAnchorEnd(at("08:20"));
            label.setPayload(new LinkedHashMap<>(Map.of("visitName", "Nested breakfast")));
            edits.create(source, label); harness.fullRebuild(source);
        }
        UUID live = harness.createUser("live-edit-target");
        if (scenario.equals("FORCE_TRIP")) disableGapInference(live);
        for (var edit : edits.list(source)) QuarkusTransaction.requiringNew().run(() -> em.persist(TimelineEditEntity.builder()
                .user(em.getReference(org.github.tess1o.geopulse.user.model.UserEntity.class, live))
                .seq(edit.seq()).kind(edit.kind()).anchorStart(edit.anchorStart()).anchorEnd(edit.anchorEnd())
                .affectedFrom(edit.affectedFrom()).affectedTo(edit.affectedTo()).payload(new LinkedHashMap<>(edit.payload()))
                .beforeValues(edit.beforeValues() == null ? null : new LinkedHashMap<>(edit.beforeValues())).status(edit.status()).build()));
        var all = new ArrayList<>(day.track.messages()); all.addAll(messages);
        if (scenario.equals("FORCE_TRIP")) all.addAll(new Track().stay(day.office, at("21:00"), at("22:00"), 2).messages());
        var batches = new ArrayList<List<org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage>>();
        for (int index = 0; index < all.size(); index += batchSize) batches.add(all.subList(index, Math.min(all.size(), index + batchSize)));
        harness.replayLive(live, batches);
        assertOutput(live, scenario, originals.getFirst(), originals.getLast(), office, officeEnd);
        assertThat(harness.snapshot(live).contentAcrossUsers()).containsExactlyElementsOf(harness.snapshot(source).contentAcrossUsers());
        for (var edit : edits.list(live)) assertApplied(live, edit.id());
        if (scenario.equals("FORCE_STAY")) assertThat(stayRows(live)).anySatisfy(row -> assertThat(row.getVisitName()).isEqualTo("Nested breakfast"));
        harness.assertMatchesFullRebuild(live, "live " + scenario + " batch " + batchSize);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void referencedPlacesAndVisitLabelsSurviveTransferAndRemainIndependent(boolean split, boolean geocoded) {
        UUID user = day.ingest(harness, "edit-reference-transfer");
        Long sourceId = QuarkusTransaction.requiringNew().call(() -> {
            if (geocoded) {
                var location = new org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity();
                location.setUser(em.getReference(org.github.tess1o.geopulse.user.model.UserEntity.class, user));
                location.setRequestCoordinates(org.github.tess1o.geopulse.shared.geo.GeoUtils.createPoint(day.bakery[1], day.bakery[0]));
                location.setDisplayName("Chosen place"); location.setProviderName("test");
                em.persist(location); em.flush(); return location.getId();
            }
            var favorite = org.github.tess1o.geopulse.favorites.model.FavoritesEntity.builder()
                    .user(em.getReference(org.github.tess1o.geopulse.user.model.UserEntity.class, user))
                    .name("Chosen place").type(org.github.tess1o.geopulse.favorites.model.FavoriteLocationType.POINT)
                    .geometry(org.github.tess1o.geopulse.shared.geo.GeoUtils.createPoint(day.bakery[1], day.bakery[0])).build();
            em.persist(favorite); em.flush(); return favorite.getId();
        });
        var label = new TimelineEditRequest(); label.setKind(TimelineEditKind.SET_VISIT_NAME);
        label.setAnchorStart(at("08:46")); label.setAnchorEnd(at("12:00"));
        label.setPayload(new LinkedHashMap<>(Map.of("visitName", "Independent label")));
        edits.create(user, label); harness.fullRebuild(user);
        var place = new TimelineEditRequest(); place.setKind(split ? TimelineEditKind.SPLIT_STAY : TimelineEditKind.SET_PLACE);
        place.setAnchorStart(at("08:46")); place.setAnchorEnd(at("12:00"));
        String key = split ? (geocoded ? "rightGeocodingId" : "rightFavoriteId") : (geocoded ? "geocodingId" : "favoriteId");
        var payload = new LinkedHashMap<String, Object>(); payload.put(key, sourceId);
        if (split) payload.put("splitAt", at("10:00").toString());
        place.setPayload(payload); var placeEdit = edits.create(user, place); harness.fullRebuild(user);
        UUID imported = harness.createUser("edit-reference-import");
        transfers.importInto(imported, transfers.exportOf(user, at("00:00"), at("23:59")));
        transfers.awaitTimelineJobs(imported); harness.fullRebuild(imported);
        QuarkusTransaction.requiringNew().run(() -> {
            var row = stays.find("user.id = ?1 and timestamp = ?2", imported, split ? at("10:00") : at("08:46")).firstResult();
            assertThat(row.getLocationName()).isEqualTo("Chosen place");
            assertThat(row.getVisitName()).isEqualTo("Independent label");
            assertThat(row.getLocation().getY()).isEqualTo(day.bakery[0]);
            Long destinationId = geocoded ? row.getGeocodingLocation().getId() : row.getFavoriteLocation().getId();
            if (!geocoded) assertThat(destinationId).isNotEqualTo(sourceId);
            var importedPlaceEdit = edits.list(imported).stream().filter(e -> e.kind() == place.getKind()).findFirst().orElseThrow();
            assertThat(((Number) importedPlaceEdit.payload().get(key)).longValue()).isEqualTo(destinationId);
            if (split) {
                var left = stays.find("user.id = ?1 and timestamp = ?2", imported, at("08:46")).firstResult();
                assertThat(left.getLocationName()).isNotEqualTo("Chosen place");
            }
        });
        if (geocoded) QuarkusTransaction.requiringNew().run(() -> {
            // Cache eviction unlinks persisted stays first; their FK deliberately prevents a dangling reference.
            em.createNativeQuery("update timeline_stays set geocoding_id=null where geocoding_id=:id")
                    .setParameter("id", sourceId).executeUpdate();
            em.createNativeQuery("delete from reverse_geocoding_location where id=:id").setParameter("id", sourceId).executeUpdate();
        });
        else favorites.deleteFavorite(user, sourceId);
        harness.fullRebuild(user);
        var deletedSnapshot = snapshot(user);
        var chosen = stayRows(user).stream().filter(row -> row.getTimestamp().equals(split ? at("10:00") : at("08:46"))).findFirst().orElseThrow();
        assertThat(chosen.getLocationName()).isEqualTo("Chosen place");
        assertThat(chosen.getVisitName()).isEqualTo("Independent label");
        assertThat(chosen.getLocation().getY()).isEqualTo(day.bakery[0]);
        assertThat(chosen.getLocation().getX()).isEqualTo(day.bakery[1]);
        assertThat(chosen.getFavoriteLocation()).isNull(); assertThat(chosen.getGeocodingLocation()).isNull();
        assertThat(chosen.getLocationSource()).isEqualTo(org.github.tess1o.geopulse.streaming.model.domain.LocationSource.HISTORICAL);
        assertApplied(user, placeEdit.id());
        edits.revert(user, placeEdit.id()).orElseThrow(); harness.fullRebuild(user);
        assertThat(stayRows(user)).anySatisfy(row -> {
            assertThat(row.getTimestamp()).isEqualTo(at("08:46"));
            assertThat(row.getVisitName()).isEqualTo("Independent label");
            assertThat(row.getLocationName()).isNotEqualTo("Chosen place");
        });
        edits.restore(user, placeEdit.id()).orElseThrow(); harness.fullRebuild(user);
        assertThat(snapshot(user)).containsExactlyElementsOf(deletedSnapshot);
    }

    private TimelineEditRequest request(String scenario, TimelineTripEntity outbound, TimelineTripEntity inbound,
                                        TimelineStayEntity office, Instant officeEnd, UUID user) {
        TimelineEditRequest request = new TimelineEditRequest();
        boolean trip = scenario.equals("SET_MOVEMENT_TYPE") || scenario.startsWith("MERGE_");
        TimelineTripEntity target = scenario.equals("MERGE_PREVIOUS") ? inbound : outbound;
        request.setAnchorStart(trip ? target.getTimestamp() : office.getTimestamp());
        request.setAnchorEnd(trip ? target.getTimestamp().plusSeconds(target.getTripDuration()) : officeEnd);
        request.setAffectedFrom(at("06:00"));
        request.setAffectedTo(at("22:00"));
        Map<String, Object> payload = new LinkedHashMap<>();
        String kind = scenario.equals("FORCE_STAY_SUBMINUTE") ? "FORCE_STAY" : scenario;
        if (scenario.startsWith("MERGE_")) { kind = "MERGE"; payload.put("direction", scenario.substring(6)); }
        else if (scenario.startsWith("REMOVE_")) { kind = "REMOVE_EVENT"; payload.put("resolution", scenario.substring(7)); }
        else switch (scenario) {
            case "SET_MOVEMENT_TYPE" -> payload.put("movementType", "TRAIN");
            case "SET_VISIT_NAME" -> payload.put("visitName", "Planning day");
            case "SET_PLACE" -> payload.putAll(Map.of("locationName", "Cabin", "latitude", day.bakery[0], "longitude", day.bakery[1]));
            case "FORCE_STAY", "FORCE_STAY_SUBMINUTE" -> {
                request.setAnchorStart(scenario.equals("FORCE_STAY") ? at("08:16") : at("08:08").plusSeconds(30));
                request.setAnchorEnd(scenario.equals("FORCE_STAY") ? at("08:20") : at("08:09").plusSeconds(30));
                payload.putAll(Map.of("locationName", "Bakery", "latitude", day.bakery[0], "longitude", day.bakery[1]));
            }
            case "FORCE_TRIP" -> {
                var gap = QuarkusTransaction.requiringNew().call(() -> gaps.findByUserId(user).stream()
                        .filter(row -> row.getEndTime().equals(at("21:00"))).findFirst().orElseThrow());
                request.setAnchorStart(gap.getStartTime()); request.setAnchorEnd(gap.getEndTime());
                payload.putAll(Map.of("movementType", "WALK", "startLatitude", day.home[0], "startLongitude", day.home[1],
                        "endLatitude", day.office[0], "endLongitude", day.office[1]));
            }
            case "SET_TIMES" -> payload.putAll(Map.of("start", at("08:50").toString(), "end", officeEnd.toString()));
            case "SPLIT_STAY" -> payload.putAll(Map.of("splitAt", at("10:00").toString(), "rightLocationName", "Gym",
                    "rightLatitude", day.bakery[0], "rightLongitude", day.bakery[1]));
            default -> throw new IllegalArgumentException(scenario);
        }
        request.setKind(TimelineEditKind.valueOf(kind)); request.setPayload(new LinkedHashMap<>(payload));
        return request;
    }

    private void assertOutput(UUID user, String scenario, TimelineTripEntity outbound, TimelineTripEntity inbound,
                              TimelineStayEntity office, Instant officeEnd) {
        var currentStays = stayRows(user);
        var currentTrips = tripRows(user);
        switch (scenario) {
            case "SET_MOVEMENT_TYPE" -> {
                var row = currentTrips.getFirst();
                assertThat(row.getMovementType()).isEqualTo("TRAIN");
                assertThat(row.getMovementTypeSource().name()).isEqualTo("MANUAL");
                assertThat(row.getDistanceMeters()).isEqualTo(outbound.getDistanceMeters());
                assertThat(row.getStartPoint()).isEqualTo(outbound.getStartPoint());
                assertThat(row.getEndPoint()).isEqualTo(outbound.getEndPoint());
                assertThat(row.getTripDuration()).isEqualTo(outbound.getTripDuration());
            }
            case "SET_PLACE", "SET_VISIT_NAME" -> {
                var row = currentStays.stream().filter(stay -> stay.getTimestamp().equals(office.getTimestamp())).findFirst().orElseThrow();
                assertThat(row.getStayDuration()).isEqualTo(office.getStayDuration());
                if (scenario.equals("SET_PLACE")) {
                    assertThat(row.getLocationName()).isEqualTo("Cabin");
                    assertThat(row.getLocation().getY()).isEqualTo(day.bakery[0]);
                    assertThat(row.getLocation().getX()).isEqualTo(day.bakery[1]);
                    assertThat(row.getFavoriteLocation()).isNull(); assertThat(row.getGeocodingLocation()).isNull();
                    assertThat(row.getVisitName()).isEqualTo(office.getVisitName());
                } else {
                    assertThat(row.getVisitName()).isEqualTo("Planning day");
                    assertThat(row.getLocation()).isEqualTo(office.getLocation());
                    assertThat(row.getLocationName()).isEqualTo(office.getLocationName());
                }
            }
            case "FORCE_STAY" -> {
                assertThat(currentStays).anySatisfy(row -> {
                    assertThat(row.getTimestamp()).isEqualTo(at("08:16")); assertThat(row.getStayDuration()).isEqualTo(240);
                    assertThat(row.getLocationName()).isEqualTo("Bakery");
                    assertThat(row.getLocation().getY()).isEqualTo(day.bakery[0]);
                });
                assertThat(currentTrips).hasSize(3);
                var left = currentTrips.get(0); var right = currentTrips.get(1);
                assertThat(left.getTimestamp()).isEqualTo(outbound.getTimestamp());
                assertThat(left.getTimestamp().plusSeconds(left.getTripDuration())).isEqualTo(at("08:16"));
                assertThat(right.getTimestamp()).isEqualTo(at("08:20"));
                assertThat(right.getTimestamp().plusSeconds(right.getTripDuration())).isEqualTo(office.getTimestamp());
                assertThat(left.getDistanceMeters()).isLessThan(outbound.getDistanceMeters());
                assertThat(right.getDistanceMeters()).isLessThan(outbound.getDistanceMeters());
                assertThat(left.getDistanceMeters() + right.getDistanceMeters()).isCloseTo(outbound.getDistanceMeters(), within(10L));
                assertThat(left.getEndPoint().getY()).isCloseTo(day.bakery[0], within(0.00001));
                assertThat(right.getStartPoint().getX()).isCloseTo(day.bakery[1], within(0.00001));
                assertThat(left.getMaxGpsSpeed()).isNotNull(); assertThat(right.getMaxGpsSpeed()).isNotNull();
            }
            case "FORCE_STAY_SUBMINUTE" -> {
                assertThat(currentTrips).hasSize(3);
                var left = currentTrips.get(0); var right = currentTrips.get(1);
                assertThat(left.getTimestamp().plusSeconds(left.getTripDuration())).isEqualTo(at("08:08").plusSeconds(30));
                assertThat(right.getTimestamp()).isEqualTo(at("08:09").plusSeconds(30));
                double endRatio = 450.0 / 840.0;
                double startRatio = 510.0 / 840.0;
                assertThat(left.getEndPoint().getY()).isCloseTo(day.home[0] + endRatio * (day.bakery[0] - day.home[0]), within(0.000001));
                assertThat(right.getStartPoint().getX()).isCloseTo(day.home[1] + startRatio * (day.bakery[1] - day.home[1]), within(0.000001));
                assertThat(left.getDistanceMeters()).isLessThan(outbound.getDistanceMeters());
                assertThat(right.getDistanceMeters()).isLessThan(outbound.getDistanceMeters());
                assertThat(left.getDistanceMeters() + right.getDistanceMeters()).isLessThan(outbound.getDistanceMeters());
            }
            case "FORCE_TRIP" -> {
                assertThat(currentTrips).hasSize(3).anySatisfy(row -> {
                    assertThat(row.getTimestamp()).isEqualTo(at("18:00"));
                    assertThat(row.getTripDuration()).isEqualTo(10800);
                    assertThat(row.getMovementType()).isEqualTo("WALK"); assertThat(row.getMovementTypeSource().name()).isEqualTo("MANUAL");
                    assertThat(row.getStartPoint().getY()).isEqualTo(day.home[0]); assertThat(row.getEndPoint().getX()).isEqualTo(day.office[1]);
                });
            }
            case "SET_TIMES" -> {
                assertThat(currentStays).anySatisfy(row -> { assertThat(row.getTimestamp()).isEqualTo(at("08:50"));
                    assertThat(row.getStayDuration()).isEqualTo(Duration.between(at("08:50"), officeEnd).toSeconds()); });
                var row = currentTrips.getFirst();
                assertThat(row.getTimestamp().plusSeconds(row.getTripDuration())).isEqualTo(at("08:50"));
                assertThat(row.getEndPoint().getY()).isCloseTo(day.office[0], within(0.00001));
            }
            case "SPLIT_STAY" -> {
                assertThat(currentStays).hasSize(4).anySatisfy(row -> {
                    assertThat(row.getTimestamp()).isEqualTo(office.getTimestamp());
                    assertThat(row.getStayDuration()).isEqualTo(Duration.between(office.getTimestamp(), at("10:00")).toSeconds());
                }).anySatisfy(row -> { assertThat(row.getTimestamp()).isEqualTo(at("10:00"));
                    assertThat(row.getStayDuration()).isEqualTo(Duration.between(at("10:00"), officeEnd).toSeconds());
                    assertThat(row.getLocationName()).isEqualTo("Gym"); assertThat(row.getFavoriteLocation()).isNull(); });
            }
            case "MERGE_PREVIOUS", "MERGE_NEXT", "REMOVE_MERGE" -> {
                assertThat(currentTrips).hasSize(1); assertThat(currentStays).hasSize(2);
                var row = currentTrips.getFirst();
                assertThat(row.getTimestamp()).isEqualTo(outbound.getTimestamp());
                assertThat(row.getTimestamp().plusSeconds(row.getTripDuration())).isEqualTo(inbound.getTimestamp().plusSeconds(inbound.getTripDuration()));
                assertThat(row.getStartPoint()).isEqualTo(outbound.getStartPoint());
                assertThat(row.getEndPoint()).isEqualTo(inbound.getEndPoint());
                // The merged recording includes travel between the old intervals; PostGIS checks its full distance below.
                assertThat(row.getDistanceMeters()).isGreaterThanOrEqualTo(outbound.getDistanceMeters() + inbound.getDistanceMeters());
            }
            case "REMOVE_GAP" -> {
                assertThat(currentStays).hasSize(2);
                assertThat(QuarkusTransaction.requiringNew().call(() -> gaps.findByUserId(user))).anySatisfy(row -> {
                    assertThat(row.getStartTime()).isEqualTo(office.getTimestamp()); assertThat(row.getEndTime()).isEqualTo(officeEnd); });
            }
            case "REMOVE_EXTEND_PREVIOUS" -> {
                assertThat(currentStays).hasSize(2);
                assertThat(currentTrips.getFirst().getTimestamp().plusSeconds(currentTrips.getFirst().getTripDuration())).isEqualTo(officeEnd);
            }
            case "REMOVE_EXTEND_NEXT" -> {
                assertThat(currentStays).hasSize(2); assertThat(currentTrips.getLast().getTimestamp()).isEqualTo(office.getTimestamp());
            }
            default -> throw new IllegalArgumentException(scenario);
        }
        assertThat(currentStays).allSatisfy(row -> assertThat(row.getStayDuration()).isPositive());
        assertThat(currentTrips).allSatisfy(row -> assertThat(row.getTripDuration()).isPositive());
        for (var row : currentTrips) {
            if (scenario.equals("FORCE_TRIP") && row.getTimestamp().equals(at("18:00"))) continue;
            if (scenario.equals("FORCE_STAY_SUBMINUTE") && row.getTimestamp().isBefore(office.getTimestamp())) continue;
            double recordedDistance = QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery("""
                    select coalesce(sum(ST_DistanceSphere(previous, coordinates)), 0)
                    from (select coordinates, lag(coordinates) over (order by timestamp, id) as previous
                          from gps_points where user_id = ?1 and timestamp between ?2 and ?3) points
                    """).setParameter(1, user).setParameter(2, row.getTimestamp())
                    .setParameter(3, row.getTimestamp().plusSeconds(row.getTripDuration())).getSingleResult()).doubleValue());
            assertThat((double) row.getDistanceMeters()).as("recorded distance for %s", row.getTimestamp())
                    .isCloseTo(recordedDistance, within(10.0));
            assertThat(row.getMaxGpsSpeed()).isCloseTo(12.5, within(0.001));
            assertThat(row.getLowAccuracyPointsCount()).isZero();
            assertThat(row.getWaterEvidenceAvailable()).isFalse();
        }
    }

    private void disableGapInference(UUID user) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                update users set timeline_preferences = coalesce(timeline_preferences, '{}'::jsonb)
                    || '{"gapStayInferenceEnabled":false,"gapTripInferenceEnabled":false,"dataGapThresholdSeconds":1800}'::jsonb
                where id = ?1
                """).setParameter(1, user).executeUpdate());
    }

    private List<String> snapshot(UUID user) {
        List<String> result = new ArrayList<>(harness.snapshot(user).content());
        stayRows(user).forEach(row -> result.add("LABEL " + row.getTimestamp() + " " + row.getVisitName()));
        return result;
    }

    private void assertPreviewMatchesStored(UUID user, TimelineEditPreviewDTO preview) {
        List<String> expected = preview.events().stream().filter(event -> !event.end().isAfter(at("22:00")))
                .map(event -> event.type() + "|" + event.start() + "|" + event.end() + "|" + event.label()).sorted().toList();
        List<String> actual = QuarkusTransaction.requiringNew().call(() -> {
            List<String> rows = new ArrayList<>();
            stays.find("user.id", user).list().forEach(row -> rows.add("STAY|" + row.getTimestamp() + "|"
                    + row.getTimestamp().plusSeconds(row.getStayDuration()) + "|"
                    + (row.getVisitName() == null ? row.getLocationName() : row.getVisitName())));
            trips.find("user.id", user).list().forEach(row -> rows.add("TRIP|" + row.getTimestamp() + "|"
                    + row.getTimestamp().plusSeconds(row.getTripDuration()) + "|" + row.getMovementType()));
            gaps.findByUserId(user).stream().filter(row -> !row.getEndTime().isAfter(at("22:00")))
                    .forEach(row -> rows.add("DATA_GAP|" + row.getStartTime() + "|" + row.getEndTime() + "|Data gap"));
            return rows.stream().sorted().toList();
        });
        assertThat(actual).as("preview must describe the committed timeline").containsExactlyElementsOf(expected);
    }

    private void assertApplied(UUID user, Long id) {
        assertThat(edits.list(user)).filteredOn(row -> row.id().equals(id)).singleElement().satisfies(row -> {
            assertThat(row.status()).isEqualTo(TimelineEditStatus.ACTIVE); assertThat(row.applied()).isTrue();
            assertThat(row.target()).isNotNull();
            boolean exists = QuarkusTransaction.requiringNew().call(() -> switch (row.target().type()) {
                case "STAY" -> stays.find("id = ?1 and user.id = ?2", row.target().id(), user).count() == 1;
                case "TRIP" -> trips.find("id = ?1 and user.id = ?2", row.target().id(), user).count() == 1;
                case "DATA_GAP" -> gaps.find("id = ?1 and user.id = ?2", row.target().id(), user).count() == 1;
                default -> false;
            });
            assertThat(exists).isTrue();
        });
    }
    private List<TimelineTripEntity> tripRows(UUID user) { return QuarkusTransaction.requiringNew().call(() -> trips.find("user.id = ?1 order by timestamp", user).list()); }
    private List<TimelineStayEntity> stayRows(UUID user) { return QuarkusTransaction.requiringNew().call(() -> stays.find("user.id = ?1 order by timestamp", user).list()); }
    private static Instant at(String time) { return CommuteWithStops.at(time); }
}
