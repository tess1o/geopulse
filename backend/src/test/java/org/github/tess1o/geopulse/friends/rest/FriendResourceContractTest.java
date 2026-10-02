package org.github.tess1o.geopulse.friends.rest;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.ApiProblemAssertions.assertProblemEnvelope;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class FriendResourceContractTest {

    private static final String FRIENDS = "/api/v1/friends";
    private static final String INVITATIONS = "/api/v1/friend-invitations";
    private static final String PASSWORD = "password123";

    @Inject UserService userService;
    @Inject AuthenticationService authenticationService;

    private TestUser owner;
    private TestUser other;
    private TestUser third;

    @BeforeEach
    @Transactional
    void setUp() {
        GeocodingTestMocks.install();
        owner = register("friend-owner", "Friend Owner");
        other = register("friend-other", "Friend Other");
        third = register("friend-third", "Friend Third");
    }

    @Test
    void invitationRoutesCreateListRejectCancelAndAccept() {
        long rejectedId = sendInvitation(owner, other);
        assertThat(authenticated(owner).when().get(INVITATIONS + "/sent").statusCode()).isEqualTo(200);
        assertThat(authenticated(owner).when().get(INVITATIONS + "/sent").jsonPath().getList("id", Long.class))
                .contains(rejectedId);
        assertThat(authenticated(other).when().get(INVITATIONS + "/received").jsonPath().getList("id", Long.class))
                .contains(rejectedId);

        Response rejected = authenticated(other).when().post(INVITATIONS + "/" + rejectedId + "/reject");
        assertThat(rejected.statusCode()).isEqualTo(200);
        assertThat(rejected.jsonPath().getString("invitationStatus")).isEqualTo("REJECTED");

        long cancelledId = sendInvitation(owner, third);
        Response cancelled = authenticated(owner).when().delete(INVITATIONS + "/" + cancelledId);
        assertThat(cancelled.statusCode()).isEqualTo(200);
        assertThat(cancelled.jsonPath().getString("invitationStatus")).isEqualTo("CANCELLED");

        long acceptedId = sendInvitation(third, owner);
        Response accepted = authenticated(owner).when().post(INVITATIONS + "/" + acceptedId + "/accept");
        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(accepted.jsonPath().getString("invitationStatus")).isEqualTo("ACCEPTED");
        assertThat(authenticated(owner).when().get(FRIENDS).jsonPath().getList("friendId", String.class))
                .contains(third.id().toString());
    }

    @Test
    void friendRoutesListPermissionsLocationTrailsCandidatesAndDelete() {
        makeFriends(owner, other);

        Response candidates = authenticated(owner).queryParam("q", third.email()).when().get(FRIENDS + "/candidates");
        assertThat(candidates.statusCode()).isEqualTo(200);
        assertThat(candidates.jsonPath().getList("email", String.class)).contains(third.email());

        Response timelinePermission = authenticated(owner)
                .body(Map.of("shareTimeline", true))
                .when().put(FRIENDS + "/" + other.id() + "/permissions");
        assertThat(timelinePermission.statusCode()).isEqualTo(200);
        assertThat(timelinePermission.jsonPath().getBoolean("shareTimeline")).isTrue();

        Response livePermission = authenticated(owner)
                .body(Map.of("shareLiveLocation", true))
                .when().put(FRIENDS + "/" + other.id() + "/permissions/live");
        assertThat(livePermission.statusCode()).isEqualTo(200);
        assertThat(livePermission.jsonPath().getBoolean("shareLiveLocation")).isTrue();

        Response permission = authenticated(owner).when().get(FRIENDS + "/" + other.id() + "/permissions");
        assertThat(permission.statusCode()).isEqualTo(200);
        assertThat(permission.jsonPath().getString("friendId")).isEqualTo(other.id().toString());
        assertThat(permission.jsonPath().getBoolean("shareTimeline")).isTrue();
        assertThat(permission.jsonPath().getBoolean("shareLiveLocation")).isTrue();

        Response permissions = authenticated(owner).when().get(FRIENDS + "/permissions");
        assertThat(permissions.statusCode()).isEqualTo(200);
        assertThat(permissions.jsonPath().getList("friendId", String.class)).contains(other.id().toString());

        authenticated(other)
                .body(Map.of("shareLiveLocation", true))
                .when().put(FRIENDS + "/" + owner.id() + "/permissions/live")
                .then().statusCode(200);
        ingestPoint(other, Instant.now().minusSeconds(30));

        Response location = authenticated(owner).when().get(FRIENDS + "/" + other.id() + "/location");
        assertThat(location.statusCode()).isEqualTo(200);
        assertThat(location.jsonPath().getString("userId")).isEqualTo(other.id().toString());
        assertThat(location.jsonPath().getDouble("latitude")).isEqualTo(50.45);

        Response trails = authenticated(owner).queryParam("minutes", 60).when().get(FRIENDS + "/trails");
        assertThat(trails.statusCode()).isEqualTo(200);
        assertThat(trails.jsonPath().getList("friendId", String.class)).contains(other.id().toString());
        assertThat(trails.jsonPath().getList("find { it.friendId == '" + other.id() + "' }.points"))
                .isNotEmpty();

        Response friends = authenticated(owner).when().get(FRIENDS);
        assertThat(friends.statusCode()).isEqualTo(200);
        assertThat(friends.jsonPath().getList("friendId", String.class)).contains(other.id().toString());

        Response deleted = authenticated(owner).when().delete(FRIENDS + "/" + other.id());
        assertThat(deleted.statusCode()).isEqualTo(204);
        assertThat(authenticated(owner).when().get(FRIENDS).jsonPath().getList("friendId", String.class))
                .doesNotContain(other.id().toString());
    }

    @Test
    void friendFailuresUseExactCodes() {
        assertProblemEnvelope(authenticated(owner).when().get(FRIENDS + "/not-a-uuid/permissions"),
                400, "INVALID_FRIEND_ID");
        assertProblemEnvelope(authenticated(owner).when().get(FRIENDS + "/" + other.id() + "/permissions"),
                404, "FRIEND_RELATIONSHIP_NOT_FOUND");
        assertProblemEnvelope(authenticated(owner).when().get(FRIENDS + "/" + other.id() + "/location"),
                403, "FRIEND_LOCATION_ACCESS_DENIED");
        assertProblemEnvelope(authenticated(owner).queryParam("minutes", 0).when().get(FRIENDS + "/trails"),
                400, "INVALID_FRIEND_TRAIL_RANGE");

        makeFriends(owner, other);
        authenticated(other)
                .body(Map.of("shareLiveLocation", true))
                .when().put(FRIENDS + "/" + owner.id() + "/permissions/live")
                .then().statusCode(200);
        assertProblemEnvelope(authenticated(owner).when().get(FRIENDS + "/" + other.id() + "/location"),
                404, "FRIEND_LOCATION_NOT_FOUND");
    }

    @Test
    void invitationFailuresUseExactCodes() {
        assertProblemEnvelope(authenticated(owner)
                        .body(Map.of("receiverEmail", TestIds.uniqueEmail("missing-friend")))
                        .when().post(INVITATIONS),
                404, "FRIEND_INVITATION_RECIPIENT_NOT_FOUND");
        assertProblemEnvelope(authenticated(owner)
                        .body(Map.of("receiverEmail", owner.email()))
                        .when().post(INVITATIONS),
                400, "INVALID_FRIEND_INVITATION");
        assertProblemEnvelope(authenticated(owner).when().post(INVITATIONS + "/999999999/accept"),
                404, "FRIEND_INVITATION_NOT_FOUND");

        long invitationId = sendInvitation(owner, other);
        assertProblemEnvelope(authenticated(third).when().post(INVITATIONS + "/" + invitationId + "/accept"),
                403, "FRIEND_INVITATION_ACCESS_DENIED");
        authenticated(other).when().post(INVITATIONS + "/" + invitationId + "/accept").then().statusCode(200);
        assertProblemEnvelope(authenticated(other).when().post(INVITATIONS + "/" + invitationId + "/accept"),
                409, "FRIEND_INVITATION_WRONG_STATUS");
    }

    private TestUser register(String prefix, String name) {
        String email = TestIds.uniqueEmail(prefix);
        UserEntity user = userService.registerUser(email, PASSWORD, name, "UTC");
        String token = authenticationService.authenticate(email, PASSWORD).getAccessToken();
        return new TestUser(user.getId(), email, token);
    }

    private long sendInvitation(TestUser sender, TestUser receiver) {
        Response response = authenticated(sender)
                .body(Map.of("receiverEmail", receiver.email()))
                .when().post(INVITATIONS);
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.jsonPath().getString("senderId")).isEqualTo(sender.id().toString());
        assertThat(response.jsonPath().getString("receiverId")).isEqualTo(receiver.id().toString());
        assertThat(response.jsonPath().getString("invitationStatus")).isEqualTo("PENDING");
        return response.jsonPath().getLong("id");
    }

    private void makeFriends(TestUser sender, TestUser receiver) {
        long invitationId = sendInvitation(sender, receiver);
        authenticated(receiver).when().post(INVITATIONS + "/" + invitationId + "/accept").then().statusCode(200);
    }

    private void ingestPoint(TestUser user, Instant timestamp) {
        authenticated(user)
                .header("X-Device-Id", "friend-contract")
                .body(Map.of("points", List.of(Map.of(
                        "timestamp", timestamp.toString(),
                        "coordinates", Map.of("lat", 50.45, "lng", 30.52),
                        "accuracy", 5.0,
                        "battery", 90.0,
                        "velocity", 1.0,
                        "altitude", 100.0))))
                .when().post("/api/v1/gps/points")
                .then().statusCode(200);
    }

    private static RequestSpecification authenticated(TestUser user) {
        return given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + user.token());
    }

    private record TestUser(UUID id, String email, String token) {
    }
}
