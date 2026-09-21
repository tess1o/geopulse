package org.github.tess1o.geopulse.timelinelabels.rest;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.ApiProblemAssertions.assertProblemEnvelope;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineLabelResourceContractTest {

    private static final String LABELS = "/api/v1/timeline-labels";
    private static final String PASSWORD = "password123";

    @Inject
    UserService userService;

    @Inject
    AuthenticationService authenticationService;

    private String token;

    @BeforeEach
    @Transactional
    void setUp() {
        String email = TestIds.uniqueEmail("timeline-label-owner");
        userService.registerUser(email, PASSWORD, "Timeline Label Owner", "UTC");
        token = authenticationService.authenticate(email, PASSWORD).getAccessToken();
    }

    @Test
    void createsListsUpdatesAndDeletesALabel() {
        String name = TestIds.uniqueValue("vacation");
        long id = createLabel(name, pastStart(), pastEnd());

        Response list = authenticated().when().get(LABELS);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.jsonPath().getList("name")).contains(name);

        String updatedName = TestIds.uniqueValue("vacation-updated");
        Response update = authenticated()
                .body(Map.of(
                        "name", updatedName,
                        "startTime", pastStart().toString(),
                        "endTime", pastEnd().toString()))
                .when().put(LABELS + "/" + id);
        assertThat(update.statusCode()).isEqualTo(200);
        assertThat(update.jsonPath().getString("name")).isEqualTo(updatedName);

        assertThat(authenticated().when().delete(LABELS + "/" + id).statusCode()).isEqualTo(204);
    }

    @Test
    void serializesTheNameFieldUnderItsRenamedKey() {
        String name = TestIds.uniqueValue("renamed-field");
        Response created = authenticated()
                .body(Map.of(
                        "name", name,
                        "startTime", pastStart().toString(),
                        "endTime", pastEnd().toString()))
                .when().post(LABELS);

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.jsonPath().getString("name")).isEqualTo(name);
        assertThat(created.jsonPath().getMap("$")).doesNotContainKey("tagName");
    }

    @Test
    void acceptsARangeEntirelyInTheFuture() {
        Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(30, ChronoUnit.DAYS);
        Instant end = start.plus(14, ChronoUnit.DAYS);

        Response created = authenticated()
                .body(Map.of(
                        "name", TestIds.uniqueValue("planned-vacation"),
                        "startTime", start.toString(),
                        "endTime", end.toString()))
                .when().post(LABELS);

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(Instant.parse(created.jsonPath().getString("startTime"))).isEqualTo(start);
        assertThat(Instant.parse(created.jsonPath().getString("endTime"))).isEqualTo(end);
    }

    @Test
    void rejectsAnEndBeforeTheStart() {
        Response response = authenticated()
                .body(Map.of(
                        "name", TestIds.uniqueValue("bad-range"),
                        "startTime", pastEnd().toString(),
                        "endTime", pastStart().toString()))
                .when().post(LABELS);

        assertProblemEnvelope(response, 400, "INVALID_TIMELINE_LABEL");
    }

    @Test
    void timeRangeFilterRequiresFromAndToTogether() {
        assertProblemEnvelope(
                authenticated().queryParam("from", pastStart().toString()).when().get(LABELS),
                400, "INVALID_TIMELINE_LABEL_RANGE");

        assertProblemEnvelope(
                authenticated().queryParam("to", pastEnd().toString()).when().get(LABELS),
                400, "INVALID_TIMELINE_LABEL_RANGE");
    }

    @Test
    void timeRangeFilterRejectsFromAfterTo() {
        Response response = authenticated()
                .queryParam("from", pastEnd().toString())
                .queryParam("to", pastStart().toString())
                .when().get(LABELS);

        assertProblemEnvelope(response, 400, "INVALID_TIMELINE_LABEL_RANGE");
    }

    @Test
    void timeRangeFilterReturnsLabelsOverlappingTheWindow() {
        String name = TestIds.uniqueValue("ranged");
        createLabel(name, pastStart(), pastEnd());

        Response response = authenticated()
                .queryParam("from", pastStart().minus(1, ChronoUnit.DAYS).toString())
                .queryParam("to", pastEnd().plus(1, ChronoUnit.DAYS).toString())
                .when().get(LABELS);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getList("name")).contains(name);
    }

    @Test
    void checkOverlapsTakesTheSameRangeNamesAsTheCreatePayload() {
        String name = TestIds.uniqueValue("overlapping");
        createLabel(name, pastStart(), pastEnd());

        Map<String, Object> params = new HashMap<>();
        params.put("startTime", pastStart().toString());
        params.put("endTime", pastEnd().toString());

        Response overlaps = authenticated().queryParams(params).when().get(LABELS + "/check-overlaps");
        assertThat(overlaps.statusCode()).isEqualTo(200);
        assertThat(overlaps.jsonPath().getList("name")).contains(name);
    }

    @Test
    void checkOverlapsRejectsTheOldFilterNames() {
        Response response = authenticated()
                .queryParam("from", pastStart().toString())
                .queryParam("to", pastEnd().toString())
                .when().get(LABELS + "/check-overlaps");

        assertProblemEnvelope(response, 400, "INVALID_TIMELINE_LABEL_RANGE");
    }

    @Test
    void activeLabelReturnsNoContentWhenNoneIsOpen() {
        assertThat(authenticated().when().get(LABELS + "/active").statusCode()).isEqualTo(204);
    }

    @Test
    void rejectsAnUnknownDeleteMode() {
        long id = createLabel(TestIds.uniqueValue("delete-mode"), pastStart(), pastEnd());

        Response response = authenticated()
                .queryParam("mode", "delete_everything")
                .when().delete(LABELS + "/" + id);

        assertProblemEnvelope(response, 400, "INVALID_TIMELINE_LABEL_DELETE_MODE");
    }

    private long createLabel(String name, Instant start, Instant end) {
        Response created = authenticated()
                .body(Map.of("name", name, "startTime", start.toString(), "endTime", end.toString()))
                .when().post(LABELS);

        assertThat(created.statusCode()).isEqualTo(201);
        return created.jsonPath().getLong("id");
    }

    private RequestSpecification authenticated() {
        return given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token);
    }

    private static Instant pastStart() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(10, ChronoUnit.DAYS);
    }

    private static Instant pastEnd() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(3, ChronoUnit.DAYS);
    }
}
