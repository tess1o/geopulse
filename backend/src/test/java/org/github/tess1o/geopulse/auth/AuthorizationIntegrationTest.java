package org.github.tess1o.geopulse.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
public class AuthorizationIntegrationTest {

    @Inject
    UserService userService;

    @Inject
    UserRepository userRepository;

    @Inject
    AuthenticationService authenticationService;

    private String validJwtToken;
    private String expiredJwtToken;

    @BeforeEach
    @Transactional
    public void setup() {
        // Clean up existing users
        userRepository.findAll().stream().forEach(userRepository::delete);

        // Create test user
        userService.registerUser("test@example.com", "password123", "Test User");

        // Generate valid JWT token
        AuthResponse authResponse = authenticationService.authenticate("test@example.com", "password123");
        validJwtToken = authResponse.getAccessToken();

        // Create an expired token (manually crafted for testing - in real scenarios this would be naturally expired)
        expiredJwtToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2dlb3B1bHNlLnRlc3NpbyIsInN1YiI6IjEyMyIsImV4cCI6MTYwMDAwMDAwMCwiaWF0IjoxNjAwMDAwMDAwfQ.invalid";
    }

    /**
     * Test Case 1: Accessing protected endpoint without any authentication
     * Expected: 401 Unauthorized
     */
    @Test
    public void testAccessProtectedEndpointWithoutAuthentication() {
        given()
                .contentType(ContentType.JSON)
        .when()
                .get("/api/friends")
        .then()
                .statusCode(401);
    }

    /**
     * Test Case 2: Accessing protected endpoint with invalid JWT token
     * Expected: 401 Unauthorized
     */
    @Test
    public void testAccessProtectedEndpointWithInvalidToken() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer invalid-token-here")
                .when()
                .get("/api/friends")
                .then()
                .statusCode(401);
    }

    /**
     * Test Case 3: Accessing protected endpoint with expired JWT token
     * Expected: 401 Unauthorized
     */
    @Test
    public void testAccessProtectedEndpointWithExpiredToken() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + expiredJwtToken)
                .when()
                .get("/api/friends")
                .then()
                .statusCode(401);
    }

    /**
     * Test Case 4: Accessing protected endpoint with malformed Authorization header
     * Expected: 401 Unauthorized
     */
    @Test
    public void testAccessProtectedEndpointWithMalformedAuthHeader() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "InvalidFormat " + validJwtToken)
                .when()
                .get("/api/favorites")
                .then()
                .statusCode(401);
    }

    /**
     * Test Case 5: Accessing protected endpoint with valid JWT token
     * Expected: 200 OK (or appropriate success status)
     */
    @Test
    public void testAccessProtectedEndpointWithValidToken() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + validJwtToken)
                .when()
                .get("/api/friends")
                .then()
                .statusCode(200)
                .body("status", equalTo("success"));
    }

    /**
     * Multiple protected endpoints requiring USER role
     * Expected: All should deny access without authentication
     */
    @Test
    public void testMultipleProtectedEndpointsWithoutAuth() {
        // Test Friends endpoint
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/friends")
                .then()
                .statusCode(401);

        // Test Favorites endpoint
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/favorites")
                .then()
                .statusCode(401);

        // Test Statistics endpoint
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/statistics")
                .then()
                .statusCode(401);

        // Test GPS Source Config endpoint
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/gps/source")
                .then()
                .statusCode(401);

        // Test Timeline endpoint
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/timeline")
                .then()
                .statusCode(401);
    }

    /**
     * @PermitAll endpoints should work without authentication
     * Expected: These should be accessible without JWT token
     */
    @Test
    public void testPermitAllEndpointsWithoutAuth() {
        // Test user registration endpoint
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "email": "newuser@example.com",
                            "password": "NewPassword123!",
                            "fullName": "New User"
                        }
                        """)
                .when()
                .post("/api/users/register")
                .then()
                .statusCode(201)
                .body("status", equalTo("success"))
                .body("data.fullName", equalTo("New User"))
                .body("data.role", equalTo("USER"))
                .body("data.email", equalTo("newuser@example.com"));

        // Test login endpoint
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "email": "test@example.com",
                            "password": "password123"
                        }
                        """)
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .body("status", equalTo("success"))
                .body("data.accessToken", notNullValue());
    }

    /**
     * Test DELETE operations require authentication
     * Expected: 401 Unauthorized without token, 403/404 with valid token
     */
    @Test
    public void testDeleteOperationsRequireAuth() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .delete("/api/favorites/1")
                .then()
                .statusCode(401);

        // With valid authentication (but non-existent resource)
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + validJwtToken)
                .when()
                .delete("/api/favorites/999999")
                .then()
                .statusCode(anyOf(is(404), is(403))); // Not found or forbidden
    }
    
    @Test
    public void testPostOperationsRequireAuth() {
        // Test adding favorite without authentication
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "name": "Test Location",
                            "lat": 40.7128,
                            "lon": -74.0060
                        }
                        """)
                .when()
                .post("/api/favorites/point")
                .then()
                .statusCode(401);
    }
    
    @Test
    public void testSecurityEnforcementDemonstration() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/api/friends")
                .then()
                .statusCode(401)
                .log().body(); // Log the error response

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "email": "demo@example.com",
                            "password": "DemoPassword123!"
                        }
                        """)
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(anyOf(is(200), is(401))) // 401 if user doesn't exist, which is fine
                .log().body();

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + validJwtToken)
                .when()
                .get("/api/friends")
                .then()
                .statusCode(200)
                .log().body();
    }
}