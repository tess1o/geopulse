package org.github.tess1o.geopulse.trips.rest;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.friends.model.UserFriendEntity;
import org.github.tess1o.geopulse.friends.repository.FriendshipRepository;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.trips.model.entity.TripCollaboratorAccessRole;
import org.github.tess1o.geopulse.trips.model.entity.TripCollaboratorEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripStatus;
import org.github.tess1o.geopulse.trips.repository.TripCollaboratorRepository;
import org.github.tess1o.geopulse.trips.repository.TripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class, restrictToAnnotatedClass = true)
@SerializedDatabaseTest
class TripCollaborationResourceIntegrationTest {

    @Inject
    AuthenticationService authenticationService;
    @Inject
    UserService userService;
    @Inject
    UserRepository userRepository;
    @Inject
    TripRepository tripRepository;
    @Inject
    TripCollaboratorRepository tripCollaboratorRepository;
    @Inject
    FriendshipRepository friendshipRepository;

    private String ownerToken;
    private String friendToken;
    private String outsiderToken;
    private UUID friendId;
    private Long tripId;

    @BeforeEach
    @Transactional
    void setUp() {
        String ownerEmail = "trip-collab-owner-" + UUID.randomUUID() + "@example.com";
        String friendEmail = "trip-collab-friend-" + UUID.randomUUID() + "@example.com";
        String outsiderEmail = "trip-collab-outsider-" + UUID.randomUUID() + "@example.com";

        userService.registerUser(ownerEmail, "password123", "Trip Owner", "UTC");
        userService.registerUser(friendEmail, "password123", "Trip Friend", "UTC");
        userService.registerUser(outsiderEmail, "password123", "Trip Outsider", "UTC");

        AuthResponse ownerAuth = authenticationService.authenticate(ownerEmail, "password123");
        ownerToken = ownerAuth.getAccessToken();

        AuthResponse friendAuth = authenticationService.authenticate(friendEmail, "password123");
        friendToken = friendAuth.getAccessToken();

        AuthResponse outsiderAuth = authenticationService.authenticate(outsiderEmail, "password123");
        outsiderToken = outsiderAuth.getAccessToken();

        UserEntity owner = userRepository.findByEmail(ownerEmail).orElseThrow();
        UserEntity friend = userRepository.findByEmail(friendEmail).orElseThrow();
        friendId = friend.getId();

        persistFriendship(owner, friend);

        TripEntity trip = TripEntity.builder()
                .user(owner)
                .name("Collab Trip")
                .startTime(Instant.parse("2026-05-01T00:00:00Z"))
                .endTime(Instant.parse("2026-05-05T00:00:00Z"))
                .status(TripStatus.UPCOMING)
                .build();
        tripRepository.persist(trip);
        tripId = trip.getId();

        tripCollaboratorRepository.persist(TripCollaboratorEntity.builder()
                .trip(trip)
                .collaborator(friend)
                .accessRole(TripCollaboratorAccessRole.VIEW)
                .build());
    }

    @Test
    void viewerCanReadTripButCannotEditPlanItems() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + friendToken)
                .when()
                .get("/api/trips/{tripId}", tripId)
                .then()
                .statusCode(200)
                .body("status", equalTo("success"))
                .body("data.isOwner", equalTo(false))
                .body("data.accessRole", equalTo("VIEW"));

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + friendToken)
                .body("""
                        {
                          "title": "Cannot edit"
                        }
                        """)
                .when()
                .post("/api/trips/{tripId}/plan-items", tripId)
                .then()
                .statusCode(404)
                .body("status", equalTo("error"));
    }

    @Test
    void ownerCanPromoteToEditorAndEditorCanCreatePlanItem() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + ownerToken)
                .body("""
                        {
                          "accessRole": "EDIT"
                        }
                        """)
                .when()
                .put("/api/trips/{tripId}/collaborators/{friendId}", tripId, friendId)
                .then()
                .statusCode(200)
                .body("status", equalTo("success"))
                .body("data.accessRole", equalTo("EDIT"));

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + friendToken)
                .body("""
                        {
                          "title": "Now editable"
                        }
                        """)
                .when()
                .post("/api/trips/{tripId}/plan-items", tripId)
                .then()
                .statusCode(201)
                .body("status", equalTo("success"))
                .body("data.title", equalTo("Now editable"));
    }

    @Test
    void outsiderCannotAccessTripEvenIfTripIdIsKnown() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + outsiderToken)
                .when()
                .get("/api/trips/{tripId}", tripId)
                .then()
                .statusCode(404)
                .body("status", equalTo("error"));
    }

    private void persistFriendship(UserEntity owner, UserEntity friend) {
        UserFriendEntity ownerToFriend = new UserFriendEntity();
        ownerToFriend.setUser(owner);
        ownerToFriend.setFriend(friend);
        friendshipRepository.persist(ownerToFriend);

        UserFriendEntity friendToOwner = new UserFriendEntity();
        friendToOwner.setUser(friend);
        friendToOwner.setFriend(owner);
        friendshipRepository.persist(friendToOwner);
    }
}
