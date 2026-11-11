package org.github.tess1o.geopulse.streaming.rest;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.model.domain.LocationSource;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineRebuildRequestRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.testsupport.AsyncTimelineTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestActor;
import org.github.tess1o.geopulse.testsupport.TestUserFactory;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.ApiProblemAssertions.assertProblemEnvelope;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineEditResourceIntegrationTest {

    private static final String EDITS = "/api/v1/timeline/edits";
    private static final Instant START = Instant.parse("2026-10-20T10:00:00Z");
    private static final Instant END = Instant.parse("2026-10-20T12:00:00Z");

    @Inject TestUserFactory users;
    @Inject TimelineStayRepository stays;
    @Inject TimelineRebuildRequestRepository rebuildRequests;
    @Inject EntityManager entityManager;
    @Inject org.github.tess1o.geopulse.streaming.service.TimelineUserLock userLock;

    @Inject org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository gaps;

    private TestActor owner;
    private TestActor other;

    @BeforeEach
    void setUp() {
        AsyncTimelineTestMocks.install();
        owner = users.createAuthenticatedUser("timeline-edit-owner");
        other = users.createAuthenticatedUser("timeline-edit-other");
        QuarkusTransaction.requiringNew().run(() -> stays.persist(TimelineStayEntity.builder()
                .user(UserEntity.findById(owner.userId()))
                .timestamp(START)
                .stayDuration(7200)
                .location(GeoUtils.createPoint(30.5234, 50.4501))
                .locationName("Home")
                .locationSource(LocationSource.FAVORITE)
                .build()));
    }

    @Test
    void invalidRequestShapesNeverWrite() {
        var valid = new java.util.LinkedHashMap<String, Object>(Map.of(
                "kind", "SET_VISIT_NAME", "anchorStart", START.toString(), "anchorEnd", END.toString(),
                "payload", Map.of("visitName", "Valid")));
        var invalid = new java.util.ArrayList<java.util.Map<String, Object>>();
        for (String field : new String[]{"kind", "anchorStart", "anchorEnd"}) {
            var missing = new java.util.LinkedHashMap<>(valid);
            missing.remove(field);
            invalid.add(missing);
            var explicitNull = new java.util.LinkedHashMap<>(valid);
            explicitNull.put(field, null);
            invalid.add(explicitNull);
        }
        for (Instant end : new Instant[]{START, START.minusSeconds(1)}) {
            var anchor = new java.util.LinkedHashMap<>(valid);
            anchor.put("anchorEnd", end.toString());
            invalid.add(anchor);
            var affected = new java.util.LinkedHashMap<>(valid);
            affected.put("affectedFrom", START.toString());
            affected.put("affectedTo", end.toString());
            invalid.add(affected);
        }
        for (var request : invalid) assertInvalidWithoutWrites(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "right", "start", "end"})
    void conflictingPlaceReferencesNeverWrite(String prefix) {
        String kind = prefix.equals("right") ? "SPLIT_STAY" : prefix.isEmpty() ? "SET_PLACE" : "FORCE_TRIP";
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put(prefix.isEmpty() ? "favoriteId" : prefix + "FavoriteId", 1L);
        payload.put(prefix.isEmpty() ? "geocodingId" : prefix + "GeocodingId", 2L);
        payload.put("splitAt", START.plusSeconds(3600).toString());
        payload.put("movementType", "WALK");
        assertInvalidWithoutWrites(Map.of("kind", kind, "anchorStart", START.toString(),
                "anchorEnd", END.toString(), "payload", payload), "cannot reference both");
    }

    @ParameterizedTest
    @ValueSource(strings = {"FORCE_STAY", "FORCE_TRIP"})
    void ongoingGapConversionsAreRejectedButCompletedGapsRemainConvertible(String kind) {
        QuarkusTransaction.requiringNew().run(() -> {
            UserEntity user = UserEntity.findById(owner.userId());
            gaps.persist(org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity.builder()
                    .user(user).startTime(END).endTime(END.plusSeconds(3600)).build());
            var point = new org.github.tess1o.geopulse.gps.model.GpsPointEntity();
            point.setUser(user);
            point.setTimestamp(END);
            point.setCoordinates(GeoUtils.createPoint(30.0, 50.0));
            point.setSourceType(org.github.tess1o.geopulse.shared.gps.GpsSourceType.HOME_ASSISTANT);
            point.setCreatedAt(END);
            entityManager.persist(point);
        });
        var payload = Map.of("locationName", "Cafe", "latitude", 50.0, "longitude", 30.0,
                "movementType", "WALK", "startLatitude", 50.0, "startLongitude", 30.0,
                "endLatitude", 50.1, "endLongitude", 30.1);
        // Even an interior slice must be rejected; the caller supplies no ongoing flag.
        assertInvalidWithoutWrites(Map.of("kind", kind, "anchorStart", END.plusSeconds(60).toString(),
                "anchorEnd", END.plusSeconds(1800).toString(), "payload", payload), "Cannot convert an ongoing data gap");
        QuarkusTransaction.requiringNew().run(() -> {
            var point = new org.github.tess1o.geopulse.gps.model.GpsPointEntity();
            point.setUser(UserEntity.findById(owner.userId()));
            point.setTimestamp(END.plusSeconds(3600));
            point.setCoordinates(GeoUtils.createPoint(30.1, 50.1));
            point.setSourceType(org.github.tess1o.geopulse.shared.gps.GpsSourceType.HOME_ASSISTANT);
            point.setCreatedAt(END.plusSeconds(3600));
            entityManager.persist(point);
        });
        var request = Map.of("kind", kind, "anchorStart", END.toString(),
                "anchorEnd", END.plusSeconds(3600).toString(), "payload", payload);
        assertThat(authenticated(owner).body(request).post(EDITS + "/preview").statusCode()).isEqualTo(200);
        assertThat(authenticated(owner).body(request).post(EDITS).statusCode()).isEqualTo(200);
    }

    private void assertInvalidWithoutWrites(Map<String, Object> request, String... expectedMessages) {
        for (String endpoint : new String[]{"/preview", ""}) {
            Response response = authenticated(owner).body(request).post(EDITS + endpoint);
            assertProblemEnvelope(response, 400, "INVALID_TIMELINE_REQUEST");
            for (String message : expectedMessages) assertThat(response.asString()).contains(message);
            assertThat(authenticated(owner).get(EDITS).jsonPath().getList("$")).isEmpty();
            assertThat(rebuildRequests.find(owner.userId())).isEmpty();
        }
    }

    @Test
    void previewsCreatesRevertsAndRestoresOneEditWithDurableRebuildRequests() {
        Map<String, Object> request = Map.of(
                "kind", "SET_VISIT_NAME",
                "anchorStart", START.toString(),
                "anchorEnd", END.toString(),
                "payload", Map.of("visitName", "Quiet morning"));

        Response preview = authenticated(owner).body(request).post(EDITS + "/preview");
        assertThat(preview.statusCode()).isEqualTo(200);
        assertThat(preview.jsonPath().getString("events[0].label")).isEqualTo("Quiet morning");
        assertThat(preview.jsonPath().getString("events[0].locationName")).isEqualTo("Home");

        Response created = authenticated(owner).body(request).post(EDITS);
        assertThat(created.statusCode()).isEqualTo(200);
        assertThat(created.jsonPath().getString("edit.beforeValues.locationName")).isEqualTo("Home");
        assertThat(created.jsonPath().getString("edit.beforeValues.start")).isEqualTo(START.toString());
        assertThat(created.jsonPath().getMap("edit.beforeValues")).containsKey("visitName");
        long id = created.jsonPath().getLong("edit.id");
        assertThat(created.jsonPath().getString("timelineJobId")).isNotBlank();
        assertThat(rebuildRequests.find(owner.userId())).isPresent();

        Response listed = authenticated(owner).get(EDITS);
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(listed.jsonPath().getLong("[0].id")).isEqualTo(id);
        assertThat(listed.jsonPath().getString("[0].status")).isEqualTo("ACTIVE");

        Response reverted = authenticated(owner).post(EDITS + "/" + id + "/revert");
        assertThat(reverted.statusCode()).isEqualTo(200);
        assertThat(reverted.jsonPath().getString("edit.status")).isEqualTo("REVERTED");

        Response restored = authenticated(owner).post(EDITS + "/" + id + "/restore");
        assertThat(restored.statusCode()).isEqualTo(200);
        assertThat(restored.jsonPath().getString("edit.status")).isEqualTo("ACTIVE");
    }

    @Test
    void editsAreOwnerOnlyAtEveryEndpoint() {
        Map<String, Object> request = Map.of(
                "kind", "SET_VISIT_NAME",
                "anchorStart", START.toString(),
                "anchorEnd", END.toString(),
                "payload", Map.of("visitName", "Private"));
        long id = authenticated(owner).body(request).post(EDITS).jsonPath().getLong("edit.id");

        assertThat(authenticated(other).get(EDITS).jsonPath().getList("$")).isEmpty();
        assertProblemEnvelope(authenticated(other).post(EDITS + "/" + id + "/revert"),
                404, "TIMELINE_EDIT_NOT_FOUND");
        assertProblemEnvelope(given().contentType(ContentType.JSON).body(request).post(EDITS + "/preview"),
                401, "AUTHENTICATION_REQUIRED");
    }

    @Test
    void sameFieldEditSupersedesItsPredecessor() {
        long unrelated = authenticated(owner).body(Map.of("kind", "SET_PLACE", "anchorStart", START.toString(),
                "anchorEnd", END.toString(), "payload", Map.of("latitude", 51.0, "longitude", 31.0, "locationName", "Independent")))
                .post(EDITS).jsonPath().getLong("edit.id");
        long firstId = createVisitName("First").jsonPath().getLong("edit.id");
        long secondId = createVisitName("Second").jsonPath().getLong("edit.id");

        Response listed = authenticated(owner).get(EDITS);
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(listed.jsonPath().getString("find { it.id == " + unrelated + " }.status")).isEqualTo("ACTIVE");
        assertThat(listed.jsonPath().getString("find { it.id == " + firstId + " }.status"))
                .isEqualTo("SUPERSEDED");
        assertThat(listed.jsonPath().getString("find { it.id == " + secondId + " }.status"))
                .isEqualTo("ACTIVE");
    }

    @Test
    void sameFieldEditsOnNeighbouringEventsRemainIndependent() {
        Instant secondEnd = END.plusSeconds(7200);
        QuarkusTransaction.requiringNew().run(() -> stays.persist(TimelineStayEntity.builder()
                .user(UserEntity.findById(owner.userId()))
                .timestamp(END)
                .stayDuration(7200)
                .location(GeoUtils.createPoint(30.5234, 50.4501))
                .locationName("Office")
                .locationSource(LocationSource.FAVORITE)
                .build()));
        createVisitName("First");

        Response second = authenticated(owner).body(Map.of(
                "kind", "SET_VISIT_NAME",
                "anchorStart", END.toString(),
                "anchorEnd", secondEnd.toString(),
                "affectedFrom", START.toString(),
                "affectedTo", secondEnd.toString(),
                "payload", Map.of("visitName", "Second"))).post(EDITS);

        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(authenticated(owner).get(EDITS).jsonPath().getList("status", String.class))
                .containsExactly("ACTIVE", "ACTIVE");
    }

    @Test
    void structuralConflictsRequireExplicitSuperseding() {
        Map<String, Object> split = Map.of(
                "kind", "SPLIT_STAY",
                "anchorStart", START.toString(),
                "anchorEnd", END.toString(),
                "payload", Map.of("splitAt", START.plusSeconds(3600).toString()));
        long firstId = authenticated(owner).body(split).post(EDITS).jsonPath().getLong("edit.id");

        Map<String, Object> remove = Map.of(
                "kind", "REMOVE_EVENT",
                "anchorStart", START.toString(),
                "anchorEnd", END.toString(),
                "payload", Map.of("resolution", "GAP"));
        String beforeConflict = storedState(owner);
        var previewConflict = authenticated(owner).body(remove).post(EDITS + "/preview");
        assertProblemEnvelope(previewConflict, 409, "TIMELINE_EDIT_CONFLICT");
        assertThat(previewConflict.jsonPath().getLong("parameters.conflictingEditId")).isEqualTo(firstId);
        assertProblemEnvelope(authenticated(owner).body(remove).post(EDITS),
                409, "TIMELINE_EDIT_CONFLICT");
        assertThat(storedState(owner)).isEqualTo(beforeConflict);

        Map<String, Object> supersedingRemove = new java.util.LinkedHashMap<>(remove);
        supersedingRemove.put("supersede", true);
        Response replacement = authenticated(owner).body(supersedingRemove).post(EDITS);
        assertThat(replacement.statusCode()).isEqualTo(200);
        assertThat(authenticated(owner).get(EDITS).jsonPath()
                .getString("find { it.id == " + firstId + " }.status")).isEqualTo("SUPERSEDED");
    }

    @Test
    void restoringAnOlderSameFieldEditRejectsANewerActiveEdit() {
        long firstId = createVisitName("First").jsonPath().getLong("edit.id");
        assertThat(authenticated(owner).post(EDITS + "/" + firstId + "/revert").statusCode()).isEqualTo(200);
        createVisitName("Second");

        assertProblemEnvelope(authenticated(owner).post(EDITS + "/" + firstId + "/restore"),
                409, "TIMELINE_EDIT_CONFLICT");
    }

    @Test
    void invalidPayloadIsAClientError() {
        Map<String, Object> request = Map.of(
                "kind", "SET_VISIT_NAME",
                "anchorStart", START.toString(),
                "anchorEnd", END.toString(),
                "payload", Map.of());

        assertProblemEnvelope(authenticated(owner).body(request).post(EDITS + "/preview"),
                400, "INVALID_TIMELINE_REQUEST");
    }

    @Test
    void placeReferenceIsResolvedServerSideAndMustBelongToTheOwner() {
        long favoriteId = QuarkusTransaction.requiringNew().call(() -> {
            FavoritesEntity favorite = FavoritesEntity.builder()
                    .user(entityManager.getReference(UserEntity.class, owner.userId()))
                    .geometry(GeoUtils.createPoint(31.2, 51.2))
                    .name("Cabin")
                    .type(FavoriteLocationType.POINT)
                    .build();
            entityManager.persist(favorite);
            entityManager.flush();
            return favorite.getId();
        });
        Map<String, Object> request = Map.of(
                "kind", "SET_PLACE",
                "anchorStart", START.toString(),
                "anchorEnd", END.toString(),
                "favoriteId", favoriteId);

        Response preview = authenticated(owner).body(request).post(EDITS + "/preview");
        assertThat(preview.statusCode()).isEqualTo(200);
        assertThat(preview.jsonPath().getString("events[0].label")).isEqualTo("Cabin");
        Response created = authenticated(owner).body(request).post(EDITS);
        assertThat(created.jsonPath().getDouble("edit.payload.latitude")).isEqualTo(51.2);
        assertThat(created.jsonPath().getDouble("edit.payload.longitude")).isEqualTo(31.2);

        assertProblemEnvelope(authenticated(other).body(request).post(EDITS + "/preview"),
                400, "INVALID_TIMELINE_REQUEST");
    }

    @Test
    void tripEndpointsAreRequiredAndOwnerPlaceReferencesAreResolved() {
        Map<String, Object> missing = Map.of("kind", "FORCE_TRIP", "anchorStart", START.toString(),
                "anchorEnd", END.toString(), "payload", Map.of("movementType", "WALK"));
        assertProblemEnvelope(authenticated(owner).body(missing).post(EDITS + "/preview"),
                400, "INVALID_TIMELINE_REQUEST");
        long favoriteId = QuarkusTransaction.requiringNew().call(() -> {
            FavoritesEntity favorite = FavoritesEntity.builder()
                    .user(entityManager.getReference(UserEntity.class, owner.userId()))
                    .geometry(GeoUtils.createPoint(31.2, 51.2)).name("Cabin")
                    .type(FavoriteLocationType.POINT).build();
            entityManager.persist(favorite);
            entityManager.flush();
            return favorite.getId();
        });
        Map<String, Object> request = Map.of("kind", "FORCE_TRIP", "anchorStart", START.toString(),
                "anchorEnd", END.toString(), "payload", Map.of("movementType", "WALK",
                        "startFavoriteId", favoriteId, "endLatitude", 51.3, "endLongitude", 31.3));
        Response created = authenticated(owner).body(request).post(EDITS);
        assertThat(created.statusCode()).isEqualTo(200);
        assertThat(created.jsonPath().getDouble("edit.payload.startLatitude")).isEqualTo(51.2);
        assertThat(created.jsonPath().getDouble("edit.payload.startLongitude")).isEqualTo(31.2);
        assertProblemEnvelope(authenticated(other).body(request).post(EDITS + "/preview"),
                400, "INVALID_TIMELINE_REQUEST");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void splitStaySecondPlaceReferenceIsResolvedServerSide(boolean withVisitLabel) {
        if (withVisitLabel) QuarkusTransaction.requiringNew().run(() -> entityManager.createQuery(
                "update TimelineStayEntity set visitName = :label where user.id = :userId")
                .setParameter("label", "Dinner with Alex").setParameter("userId", owner.userId()).executeUpdate());
        long favoriteId = QuarkusTransaction.requiringNew().call(() -> {
            FavoritesEntity favorite = FavoritesEntity.builder()
                    .user(entityManager.getReference(UserEntity.class, owner.userId()))
                    .geometry(GeoUtils.createPoint(31.2, 51.2))
                    .name("Gym")
                    .type(FavoriteLocationType.POINT)
                    .build();
            entityManager.persist(favorite);
            entityManager.flush();
            return favorite.getId();
        });
        Map<String, Object> request = Map.of(
                "kind", "SPLIT_STAY",
                "anchorStart", START.toString(),
                "anchorEnd", END.toString(),
                "payload", Map.of("splitAt", "2026-10-20T11:00:00Z", "rightFavoriteId", favoriteId));

        Response preview = authenticated(owner).body(request).post(EDITS + "/preview");
        assertThat(preview.statusCode()).isEqualTo(200);
        assertThat(preview.jsonPath().getString("events[0].label")).isEqualTo(withVisitLabel ? "Dinner with Alex" : "Home");
        assertThat(preview.jsonPath().getString("events[1].label")).isEqualTo(withVisitLabel ? "Dinner with Alex" : "Gym");
        assertThat(preview.jsonPath().getString("events[0].locationName")).isEqualTo("Home");
        assertThat(preview.jsonPath().getString("events[1].locationName")).isEqualTo("Gym");
        Response created = authenticated(owner).body(request).post(EDITS);
        assertThat(created.jsonPath().getString("edit.payload.rightLocationName")).isEqualTo("Gym");
        assertThat(created.jsonPath().getDouble("edit.payload.rightLatitude")).isEqualTo(51.2);
        assertThat(created.jsonPath().getDouble("edit.payload.rightLongitude")).isEqualTo(31.2);

        assertProblemEnvelope(authenticated(other).body(request).post(EDITS + "/preview"),
                400, "INVALID_TIMELINE_REQUEST");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/preview", ""})
    void staleTargetIsAClientErrorAndDoesNotWrite(String endpoint) {
        var request = Map.of("kind", "SET_VISIT_NAME", "anchorStart", END.toString(),
                "anchorEnd", END.plusSeconds(3600).toString(), "payload", Map.of("visitName", "Stale"));
        assertProblemEnvelope(authenticated(owner).body(request).post(EDITS + endpoint),
                400, "INVALID_TIMELINE_REQUEST");
        assertThat(authenticated(owner).get(EDITS).jsonPath().getList("$")).isEmpty();
        assertThat(rebuildRequests.find(owner.userId())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"favoriteId", "rightFavoriteId", "startFavoriteId", "endFavoriteId",
            "geocodingId", "rightGeocodingId", "startGeocodingId", "endGeocodingId"})
    void validPlaceReferencesRejectOnlyTheForeignOwnerWithoutWrites(String key) {
        long ownId = createPlace(owner, key);
        long foreignId = createPlace(other, key);
        var payload = new java.util.LinkedHashMap<String, Object>();
        String kind = key.startsWith("right") ? "SPLIT_STAY"
                : key.startsWith("start") || key.startsWith("end") ? "FORCE_TRIP" : "SET_PLACE";
        if (kind.equals("SPLIT_STAY")) payload.put("splitAt", START.plusSeconds(3600).toString());
        if (kind.equals("FORCE_TRIP")) {
            payload.putAll(Map.of("movementType", "WALK", "startLatitude", 50.0, "startLongitude", 30.0,
                    "endLatitude", 51.0, "endLongitude", 31.0));
        }
        payload.put(key, ownId);
        var request = Map.of("kind", kind, "anchorStart", START.toString(), "anchorEnd", END.toString(), "payload", payload);
        String before = storedState(owner);
        assertThat(authenticated(owner).body(request).post(EDITS + "/preview").statusCode()).isEqualTo(200);
        assertThat(storedState(owner)).isEqualTo(before);
        payload.put(key, foreignId);
        for (String endpoint : new String[]{"/preview", ""}) {
            Response rejected = authenticated(owner).body(request).post(EDITS + endpoint);
            assertProblemEnvelope(rejected, 400, "INVALID_TIMELINE_REQUEST");
            assertThat(rejected.asString()).contains("not found or access denied");
            assertThat(storedState(owner)).isEqualTo(before);
        }
        payload.put(key, ownId);
        assertThat(authenticated(owner).body(request).post(EDITS).statusCode()).isEqualTo(200);
    }

    private long createPlace(TestActor actor, String key) {
        return QuarkusTransaction.requiringNew().call(() -> {
            if (key.contains("Geocoding") || key.equals("geocodingId")) {
                var place = new org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity();
                place.setUser(UserEntity.findById(actor.userId())); place.setDisplayName("Chosen");
                place.setProviderName("test"); place.setRequestCoordinates(GeoUtils.createPoint(31.0, 51.0));
                entityManager.persist(place); entityManager.flush(); return place.getId();
            }
            var place = FavoritesEntity.builder().user(UserEntity.findById(actor.userId()))
                    .geometry(GeoUtils.createPoint(31.0, 51.0)).name("Chosen").type(FavoriteLocationType.POINT).build();
            entityManager.persist(place); entityManager.flush(); return place.getId();
        });
    }

    private String storedState(TestActor actor) {
        return QuarkusTransaction.requiringNew().call(() -> entityManager.createNativeQuery("""
                select jsonb_build_object(
                  'edits', (select jsonb_agg(to_jsonb(e) order by id) from timeline_edits e where user_id=:owner),
                  'applications', (select jsonb_agg(to_jsonb(a) order by a.edit_id) from timeline_edit_applications a
                      join timeline_edits e on e.id=a.edit_id where e.user_id=:owner),
                  'stays', (select jsonb_agg(to_jsonb(s) order by id) from timeline_stays s where user_id=:owner),
                  'trips', (select jsonb_agg(to_jsonb(t) order by id) from timeline_trips t where user_id=:owner),
                  'gaps', (select jsonb_agg(to_jsonb(g) order by id) from timeline_data_gaps g where user_id=:owner))::text
                """).setParameter("owner", actor.userId()).getSingleResult().toString())
                + rebuildRequests.find(actor.userId()).toString();
    }

    @Test
    void anotherUserCannotRestoreOrEditTheOwnersTimeline() {
        long id = createVisitName("Private").jsonPath().getLong("edit.id");
        assertProblemEnvelope(authenticated(other).post(EDITS + "/" + id + "/restore"), 404, "TIMELINE_EDIT_NOT_FOUND");
        var request = Map.of("kind", "SET_VISIT_NAME", "anchorStart", START.toString(), "anchorEnd", END.toString(), "payload", Map.of("visitName", "Intruder"));
        for (String endpoint : new String[]{"/preview", ""}) assertProblemEnvelope(
                authenticated(other).body(request).post(EDITS + endpoint), 400, "INVALID_TIMELINE_REQUEST");
        assertThat(authenticated(owner).get(EDITS).jsonPath().getString("[0].payload.visitName")).isEqualTo("Private");
        assertThat(rebuildRequests.find(other.userId())).isEmpty();
    }

    @Test
    void invalidStructuralPayloadsNeverWrite() {
        var cases = java.util.List.of(
                Map.of("kind", "SPLIT_STAY", "payload", Map.of("splitAt", START.toString())),
                Map.of("kind", "SPLIT_STAY", "payload", Map.of("splitAt", END.toString())),
                Map.of("kind", "SET_TIMES", "payload", Map.of("start", END.toString(), "end", START.toString())),
                Map.of("kind", "REMOVE_EVENT", "payload", Map.of("resolution", "EXTEND_PREVIOUS")),
                Map.of("kind", "REMOVE_EVENT", "payload", Map.of("resolution", "EXTEND_NEXT")),
                Map.of("kind", "SET_MOVEMENT_TYPE", "payload", Map.of("movementType", "INVALID")),
                Map.of("kind", "SET_PLACE", "payload", Map.of()),
                Map.of("kind", "FORCE_STAY", "payload", Map.of("latitude", 91, "longitude", 30)),
                Map.of("kind", "SET_PLACE", "payload", Map.of("latitude", 50)),
                Map.of("kind", "SPLIT_STAY", "payload", Map.of("splitAt", START.plusSeconds(3600).toString(), "rightLatitude", 50, "rightLongitude", 181)));
        for (var invalid : cases) {
            var request = new java.util.LinkedHashMap<String, Object>(invalid);
            request.put("anchorStart", START.toString()); request.put("anchorEnd", END.toString());
            for (String endpoint : new String[]{"/preview", ""}) assertProblemEnvelope(
                    authenticated(owner).body(request).post(EDITS + endpoint), 400, "INVALID_TIMELINE_REQUEST");
        }
        assertThat(authenticated(owner).get(EDITS).jsonPath().getList("$")).isEmpty();
        assertThat(rebuildRequests.find(owner.userId())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "revert", "restore"})
    void unifiedMutationsRespectTheOwnerLockWithoutPartialWrites(String operation) throws Exception {
        long id = createVisitName("Original").jsonPath().getLong("edit.id");
        if (operation.equals("restore")) {
            assertThat(authenticated(owner).post(EDITS + "/" + id + "/revert").statusCode()).isEqualTo(200);
        }
        String before = storedState(owner);
        QuarkusTransaction.requiringNew().run(() -> stays.persist(TimelineStayEntity.builder()
                .user(UserEntity.findById(other.userId())).timestamp(START).stayDuration(7200)
                .location(GeoUtils.createPoint(30.5, 50.4)).locationName("Other").locationSource(LocationSource.FAVORITE).build()));
        var locked = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var holder = java.util.concurrent.CompletableFuture.runAsync(() -> QuarkusTransaction.requiringNew().run(() -> {
            assertThat(userLock.tryLock(owner.userId())).isTrue();
            locked.countDown();
            try {
                assertThat(release.await(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt(); throw new AssertionError(exception);
            }
        }));
        try {
            assertThat(locked.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            Response rejected = operation.equals("create") ? createVisitName("Rejected")
                    : authenticated(owner).post(EDITS + "/" + id + "/" + operation);
            assertProblemEnvelope(rejected, 409, "TIMELINE_REGENERATION_IN_PROGRESS");
            assertThat(storedState(owner)).isEqualTo(before);
            assertThat(authenticated(other).body(Map.of("kind", "SET_VISIT_NAME", "anchorStart", START.toString(),
                    "anchorEnd", END.toString(), "payload", Map.of("visitName", "Independent"))).post(EDITS).statusCode()).isEqualTo(200);
        } finally {
            release.countDown(); holder.join();
        }
        assertThat(storedState(owner)).isEqualTo(before);
    }

    private Response createVisitName(String name) {
        return authenticated(owner).body(Map.of(
                "kind", "SET_VISIT_NAME",
                "anchorStart", START.toString(),
                "anchorEnd", END.toString(),
                "payload", Map.of("visitName", name))).post(EDITS);
    }

    private static io.restassured.specification.RequestSpecification authenticated(TestActor actor) {
        return given().contentType(ContentType.JSON).header("Authorization", "Bearer " + actor.accessToken());
    }
}
