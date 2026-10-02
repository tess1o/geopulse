package org.github.tess1o.geopulse.streaming.rest;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestCoordinates;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.ApiProblemAssertions.assertProblemEnvelope;

/**
 * HTTP contract for {@code /api/v1/places}.
 *
 * <p>{@code {type}} is {@code favorite} or {@code geocoding}; anything else is treated as not-found
 * rather than rejected, which is asserted below.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class PlaceDetailsContractTest {

    private static final String PLACES = "/api/v1/places";
    private static final String PASSWORD = "password123";

    @Inject UserService userService;
    @Inject AuthenticationService authenticationService;
    @Inject ReverseGeocodingLocationRepository geocodingRepository;

    private TestCoordinates.Scope coords;
    private UserEntity owner;
    private String ownerToken;
    private String otherToken;

    @BeforeEach
    @Transactional
    void setUp() {
        GeocodingTestMocks.install();
        coords = TestCoordinates.newScope();
        owner = register("places-owner");
        UserEntity other = register("places-other");
        ownerToken = token(owner);
        otherToken = token(other);
    }

    @Test
    void favoritePlacesSupportDetailsVisitsPhotosAndRename() {
        long favoriteId = createFavorite(ownerToken, TestIds.uniqueValue("place"));

        Response details = authenticated(ownerToken).when().get(PLACES + "/favorite/" + favoriteId);
        assertThat(details.statusCode()).isEqualTo(200);

        Response visits = authenticated(ownerToken).when().get(PLACES + "/favorite/" + favoriteId + "/visits");
        assertThat(visits.statusCode()).isEqualTo(200);
        assertThat(visits.jsonPath().getInt("page")).isZero();

        Response window = authenticated(ownerToken)
                .when().get(PLACES + "/favorite/" + favoriteId + "/photo-search-window");
        assertThat(window.statusCode()).isEqualTo(200);

        String renamed = TestIds.uniqueValue("renamed-place");
        Response rename = authenticated(ownerToken)
                .body(Map.of("name", renamed))
                .when().put(PLACES + "/favorite/" + favoriteId);
        assertThat(rename.statusCode()).isEqualTo(204);

        Response afterRename = authenticated(ownerToken).when().get(PLACES + "/favorite/" + favoriteId);
        assertThat(afterRename.statusCode()).isEqualTo(200);
        assertThat(afterRename.jsonPath().getString("locationName")).isEqualTo(renamed);
    }

    @Test
    void geocodingPlacesAreReadable() {
        long geocodingId = seedGeocodingRow("Geocoding place row");

        Response details = authenticated(ownerToken).when().get(PLACES + "/geocoding/" + geocodingId);
        assertThat(details.statusCode()).isEqualTo(200);
        assertThat(details.jsonPath().getString("locationName")).isEqualTo("Geocoding place row");
        assertThat(details.jsonPath().getBoolean("canEdit")).isFalse();
    }

    @Test
    void unknownTypeAndMissingIdsAreNotFound() {
        assertProblemEnvelope(authenticated(ownerToken).when().get(PLACES + "/favorite/999999999"),
                404, "PLACE_NOT_FOUND");
        assertProblemEnvelope(authenticated(ownerToken).when().get(PLACES + "/geocoding/999999999"),
                404, "PLACE_NOT_FOUND");
        // An unrecognised type is not an error, just an absent place.
        assertProblemEnvelope(authenticated(ownerToken).when().get(PLACES + "/restaurant/1"),
                404, "PLACE_NOT_FOUND");
    }

    @Test
    void crossTenantFavoritesAreNotFound() {
        long otherFavoriteId = createFavorite(otherToken, TestIds.uniqueValue("other-place"));

        assertProblemEnvelope(authenticated(ownerToken).when().get(PLACES + "/favorite/" + otherFavoriteId),
                404, "PLACE_NOT_FOUND");
        // Reads hide the row as 404, but a rename on an unreachable favorite reports
        // PLACE_RENAME_NOT_ALLOWED (409) — the same code a non-renamable type gets. Either way the
        // response carries no information about whether the id exists.
        assertProblemEnvelope(authenticated(ownerToken)
                        .body(Map.of("name", "Hijacked"))
                        .when().put(PLACES + "/favorite/" + otherFavoriteId),
                409, "PLACE_RENAME_NOT_ALLOWED");
    }

    @Test
    void exactErrorCodesForInvalidRequests() {
        long favoriteId = createFavorite(ownerToken, TestIds.uniqueValue("error-place"));

        // radiusMeters must be within (0, 5000]
        assertProblemEnvelope(authenticated(ownerToken)
                        .queryParam("radiusMeters", 0)
                        .when().get(PLACES + "/favorite/" + favoriteId + "/photo-search-window"),
                400, "INVALID_PLACE_REQUEST");
        assertProblemEnvelope(authenticated(ownerToken)
                        .queryParam("radiusMeters", 5001)
                        .when().get(PLACES + "/favorite/" + favoriteId + "/photo-search-window"),
                400, "INVALID_PLACE_REQUEST");

        assertProblemEnvelope(authenticated(ownerToken).queryParam("page", -1)
                        .when().get(PLACES + "/favorite/" + favoriteId + "/visits"),
                400, "INVALID_PAGE");

        assertProblemEnvelope(authenticated(ownerToken)
                        .body(Map.of("name", "   "))
                        .when().put(PLACES + "/favorite/" + favoriteId),
                400, "VALIDATION_FAILED");

        // Only favorites can be renamed.
        long geocodingId = seedGeocodingRow("Not A Favorite");
        assertProblemEnvelope(authenticated(ownerToken)
                        .body(Map.of("name", "New name"))
                        .when().put(PLACES + "/geocoding/" + geocodingId),
                409, "PLACE_RENAME_NOT_ALLOWED");
    }

    private long createFavorite(String token, String name) {
        Response created = authenticated(token)
                .body(Map.of("name", name, "lat", 50.45, "lon", 30.52))
                .when().post("/api/v1/favorites/points");
        assertThat(created.statusCode()).isEqualTo(201);

        Response favorites = authenticated(token).when().get("/api/v1/favorites");
        List<Number> ids = favorites.jsonPath().getList("points.findAll { it.name == '" + name + "' }.id", Number.class);
        assertThat(ids).hasSize(1);
        return ids.getFirst().longValue();
    }

    private long seedGeocodingRow(String displayName) {
        // Committed so the HTTP request on another thread can see it.
        return QuarkusTransaction.requiringNew().call(() -> {
            ReverseGeocodingLocationEntity entity = new ReverseGeocodingLocationEntity();
            entity.setUser(owner);
            entity.setRequestCoordinates(coords.point(30.52, 50.45));
            entity.setResultCoordinates(coords.point(30.52, 50.45));
            entity.setDisplayName(displayName);
            entity.setProviderName("test-provider");
            entity.setCreatedAt(Instant.now());
            geocodingRepository.persist(entity);
            geocodingRepository.flush();
            return entity.getId();
        });
    }

    private UserEntity register(String prefix) {
        String email = TestIds.uniqueEmail(prefix);
        return userService.registerUser(email, PASSWORD, prefix + " User", "UTC");
    }

    private String token(UserEntity user) {
        return authenticationService.authenticate(user.getEmail(), PASSWORD).getAccessToken();
    }

    private static RequestSpecification authenticated(String token) {
        return given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token);
    }
}
