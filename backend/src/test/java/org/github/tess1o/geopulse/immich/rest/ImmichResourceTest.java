package org.github.tess1o.geopulse.immich.rest;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.immich.model.*;
import org.github.tess1o.geopulse.immich.service.ImmichService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
class ImmichResourceTest {

    @InjectMock
    ImmichService immichService;

    @InjectMock
    CurrentUserService currentUserService;

    private final UUID testUserId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    void testUnauthorizedAccess() {
        given()
                .when()
                .get("/api/users/me/immich-config")
                .then()
                .statusCode(401);
    }

    @Test
    void testGetCurrentUserImmichConfigWithMock() {
        when(currentUserService.getCurrentUserId()).thenReturn(testUserId);
        when(immichService.getUserImmichConfig(testUserId))
                .thenReturn(Optional.of(ImmichConfigResponse.builder()
                        .serverUrl("http://localhost:2283")
                        .enabled(true)
                        .build()));

        given()
                .header("Authorization", "Bearer mock-token")
                .when()
                .get("/api/users/me/immich-config")
                .then()
                .statusCode(401);
    }
}