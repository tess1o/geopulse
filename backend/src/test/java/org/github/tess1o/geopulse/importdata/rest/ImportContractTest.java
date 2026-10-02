package org.github.tess1o.geopulse.importdata.rest;

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
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.ApiProblemAssertions.assertProblemEnvelope;

/**
 * HTTP contract for the import surface: {@code /api/v1/imports}, {@code /api/v1/import-uploads} and
 * {@code /api/v1/debug-imports}.
 *
 * <p>The import scheduler is disabled in tests, so a created job stays in {@code validating} and is
 * never parsed — that keeps these tests about the HTTP contract rather than about import semantics,
 * which the existing service-level suite already covers.
 *
 * <p>Requests authenticate with a bearer header, so no CSRF token is involved; the CSRF rules for
 * cookie sessions are covered by {@code CsrfProtectionContractTest}.
 *
 * <p>Not covered: the {@code POST /debug-imports} success path. It requires a ZIP whose
 * {@code gps_data.json} matches the debug-import schema — see the notes on that test.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class ImportContractTest {

    private static final String IMPORTS = "/api/v1/imports";
    private static final String UPLOADS = "/api/v1/import-uploads";
    private static final String DEBUG_IMPORTS = "/api/v1/debug-imports";
    private static final String PASSWORD = "password123";
    private static final String FIXTURE = "/import-fixtures/sample.geojson";
    private static final String FIXTURE_NAME = "sample.geojson";

    @Inject UserService userService;
    @Inject AuthenticationService authenticationService;

    private String ownerToken;
    private String otherToken;

    @BeforeEach
    @Transactional
    void setUp() {
        ownerToken = token(register("import-owner"));
        otherToken = token(register("import-other"));
    }

    @Test
    void multipartUploadCreatesAJobThatCanBeListedAndDeleted() {
        Response created = multipart(ownerToken,
                        filePart("file", FIXTURE_NAME, fixtureBytes(), "application/geo+json"),
                        fieldPart("format", "geojson"),
                        fieldPart("options", "{}"))
                .when().post(IMPORTS);

        assertThat(created.statusCode()).isEqualTo(200);
        UUID jobId = UUID.fromString(created.jsonPath().getString("importJobId"));
        assertThat(created.jsonPath().getString("status")).isEqualTo("validating");
        assertThat(created.jsonPath().getString("uploadedFileName")).isEqualTo(FIXTURE_NAME);

        Response listed = authenticated(ownerToken).when().get(IMPORTS);
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(listed.jsonPath().getList("items.importJobId", String.class)).contains(jobId.toString());

        Response single = authenticated(ownerToken).when().get(IMPORTS + "/" + jobId);
        assertThat(single.statusCode()).isEqualTo(200);
        assertThat(single.jsonPath().getString("importJobId")).isEqualTo(jobId.toString());

        assertThat(authenticated(ownerToken).when().delete(IMPORTS + "/" + jobId).statusCode()).isEqualTo(204);
    }

    @Test
    void chunkedUploadLifecycleProducesAnImportJob() {
        byte[] payload = fixtureBytes();

        Response initialized = authenticated(ownerToken)
                .body(Map.of("fileName", FIXTURE_NAME, "fileSize", payload.length,
                        "importFormat", "geojson", "options", Map.of()))
                .when().post(UPLOADS);
        assertThat(initialized.statusCode()).isEqualTo(200);
        UUID uploadId = UUID.fromString(initialized.jsonPath().getString("uploadId"));
        assertThat(initialized.jsonPath().getInt("totalChunks")).isPositive();
        assertThat(initialized.jsonPath().getLong("chunkSizeBytes")).isPositive();

        Response chunk = multipart(ownerToken, filePart("chunk", FIXTURE_NAME, payload, "application/octet-stream"))
                .when().put(UPLOADS + "/" + uploadId + "/parts/0");
        assertThat(chunk.statusCode()).isEqualTo(200);
        assertThat(chunk.jsonPath().getInt("receivedChunks")).isEqualTo(1);

        // Re-sending a received chunk is idempotent.
        Response resent = multipart(ownerToken, filePart("chunk", FIXTURE_NAME, payload, "application/octet-stream"))
                .when().put(UPLOADS + "/" + uploadId + "/parts/0");
        assertThat(resent.statusCode()).isEqualTo(200);
        assertThat(resent.jsonPath().getInt("receivedChunks")).isEqualTo(1);

        Response status = authenticated(ownerToken).when().get(UPLOADS + "/" + uploadId);
        assertThat(status.statusCode()).isEqualTo(200);
        assertThat(status.jsonPath().getBoolean("complete")).isTrue();

        Response completed = authenticated(ownerToken).when().post(UPLOADS + "/" + uploadId + "/completion");
        assertThat(completed.statusCode()).isEqualTo(200);
        UUID jobId = UUID.fromString(completed.jsonPath().getString("importJobId"));
        assertThat(jobId).isNotNull();

        authenticated(ownerToken).when().delete(IMPORTS + "/" + jobId);
    }

    @Test
    void directUploadTreatsOptionsAsOptional() {
        Response created = multipart(ownerToken,
                        filePart("file", FIXTURE_NAME, fixtureBytes(), "application/geo+json"),
                        fieldPart("format", "geojson"))
                .when().post(IMPORTS);

        assertThat(created.statusCode()).isEqualTo(200);
        authenticated(ownerToken).when().delete(IMPORTS + "/" + created.jsonPath().getString("importJobId"));
    }

    @Test
    void directUploadAcceptsOptionsWithOrWithoutAPartContentType() {
        String options = "{\"clearDataBeforeImport\":true,"
                + "\"dateRangeFilter\":{\"startDate\":\"2026-01-01T00:00:00Z\",\"endDate\":\"2026-01-31T23:59:59Z\"}}";

        Response plainField = multipart(ownerToken,
                        filePart("file", FIXTURE_NAME, fixtureBytes(), "application/geo+json"),
                        fieldPart("format", "geojson"),
                        fieldPart("options", options))
                .when().post(IMPORTS);
        assertThat(plainField.statusCode()).isEqualTo(200);
        authenticated(ownerToken).when().delete(IMPORTS + "/" + plainField.jsonPath().getString("importJobId"));

        Response jsonPart = multipart(ownerToken,
                        filePart("file", FIXTURE_NAME, fixtureBytes(), "application/geo+json"),
                        fieldPart("format", "geojson"),
                        new Part("options", null, "application/json", options.getBytes(StandardCharsets.UTF_8)))
                .when().post(IMPORTS);
        assertThat(jsonPart.statusCode()).isEqualTo(200);
        authenticated(ownerToken).when().delete(IMPORTS + "/" + jsonPart.jsonPath().getString("importJobId"));
    }

    @Test
    void malformedOptionsAreRejected() {
        assertProblemEnvelope(multipart(ownerToken,
                        filePart("file", FIXTURE_NAME, fixtureBytes(), "application/geo+json"),
                        fieldPart("format", "geojson"),
                        fieldPart("options", "{not json"))
                        .when().post(IMPORTS),
                400, "BAD_REQUEST");

        assertProblemEnvelope(authenticated(ownerToken)
                        .body(Map.of("fileName", FIXTURE_NAME, "fileSize", 10,
                                "importFormat", "geojson", "options", "{}"))
                        .when().post(UPLOADS),
                400, "BAD_REQUEST");
    }

    @Test
    void chunkedUploadTreatsOptionsAsOptional() {
        Response initialized = authenticated(ownerToken)
                .body(Map.of("fileName", FIXTURE_NAME, "fileSize", fixtureBytes().length, "importFormat", "geojson"))
                .when().post(UPLOADS);

        assertThat(initialized.statusCode()).isEqualTo(200);
        authenticated(ownerToken).when().delete(UPLOADS + "/" + initialized.jsonPath().getString("uploadId"));
    }

    @Test
    void uploadSessionCanBeAborted() {
        Response initialized = authenticated(ownerToken)
                .body(Map.of("fileName", FIXTURE_NAME, "fileSize", fixtureBytes().length,
                        "importFormat", "geojson", "options", Map.of()))
                .when().post(UPLOADS);
        UUID uploadId = UUID.fromString(initialized.jsonPath().getString("uploadId"));

        assertThat(authenticated(ownerToken).when().delete(UPLOADS + "/" + uploadId).statusCode()).isEqualTo(204);
        assertProblemEnvelope(authenticated(ownerToken).when().get(UPLOADS + "/" + uploadId),
                404, "IMPORT_UPLOAD_NOT_FOUND");
    }

    @Test
    void exactErrorCodesForInvalidUploads() {
        // Unknown format
        assertProblemEnvelope(authenticated(ownerToken)
                        .body(Map.of("fileName", FIXTURE_NAME, "fileSize", 10,
                                "importFormat", "not-a-format", "options", Map.of()))
                        .when().post(UPLOADS),
                400, "INVALID_IMPORT_FORMAT");

        // Non-positive file size
        assertProblemEnvelope(authenticated(ownerToken)
                        .body(Map.of("fileName", FIXTURE_NAME, "fileSize", 0,
                                "importFormat", "geojson", "options", Map.of()))
                        .when().post(UPLOADS),
                400, "INVALID_IMPORT_REQUEST");

        // Unsupported extension for the declared format
        assertProblemEnvelope(authenticated(ownerToken)
                        .body(Map.of("fileName", "payload.txt", "fileSize", 10,
                                "importFormat", "geojson", "options", Map.of()))
                        .when().post(UPLOADS),
                400, "INVALID_IMPORT_FILE_TYPE");

        assertProblemEnvelope(authenticated(ownerToken).when().get(UPLOADS + "/" + UUID.randomUUID()),
                404, "IMPORT_UPLOAD_NOT_FOUND");
        assertProblemEnvelope(authenticated(ownerToken).when().post(UPLOADS + "/" + UUID.randomUUID() + "/completion"),
                404, "IMPORT_UPLOAD_NOT_FOUND");
    }

    @Test
    void onlyOneUploadSessionAtATimeAndCompletionNeedsEveryChunk() {
        Map<String, Object> init = Map.of("fileName", FIXTURE_NAME, "fileSize", fixtureBytes().length,
                "importFormat", "geojson", "options", Map.of());

        Response first = authenticated(ownerToken).body(init).when().post(UPLOADS);
        assertThat(first.statusCode()).isEqualTo(200);
        UUID uploadId = UUID.fromString(first.jsonPath().getString("uploadId"));

        try {
            // A second concurrent session is rejected.
            assertProblemEnvelope(authenticated(ownerToken).body(init).when().post(UPLOADS),
                    409, "IMPORT_ACTIVE_UPLOAD_CONFLICT");

            // Completing before the chunks arrive reports the counts.
            Response incomplete = authenticated(ownerToken)
                    .when().post(UPLOADS + "/" + uploadId + "/completion");
            assertProblemEnvelope(incomplete, 409, "IMPORT_UPLOAD_INCOMPLETE");
            assertThat(incomplete.jsonPath().getInt("parameters.receivedChunks")).isZero();
            assertThat(incomplete.jsonPath().getInt("parameters.totalChunks")).isPositive();
        } finally {
            authenticated(ownerToken).when().delete(UPLOADS + "/" + uploadId);
        }
    }

    @Test
    void importListingValidatesPagingAndMissingJobsAreNotFound() {
        assertProblemEnvelope(authenticated(ownerToken).queryParam("page", -1).when().get(IMPORTS),
                400, "INVALID_PAGE");
        assertProblemEnvelope(authenticated(ownerToken).queryParam("size", 0).when().get(IMPORTS),
                400, "INVALID_LIMIT");
        assertProblemEnvelope(authenticated(ownerToken).queryParam("size", 101).when().get(IMPORTS),
                400, "INVALID_LIMIT");

        UUID absent = UUID.randomUUID();
        assertProblemEnvelope(authenticated(ownerToken).when().get(IMPORTS + "/" + absent),
                404, "IMPORT_JOB_NOT_FOUND");
        assertProblemEnvelope(authenticated(ownerToken).when().delete(IMPORTS + "/" + absent),
                404, "IMPORT_JOB_NOT_FOUND");
    }

    @Test
    void anotherUsersImportJobIsNotVisible() {
        Response created = multipart(otherToken,
                        filePart("file", FIXTURE_NAME, fixtureBytes(), "application/geo+json"),
                        fieldPart("format", "geojson"),
                        fieldPart("options", "{}"))
                .when().post(IMPORTS);
        UUID otherJobId = UUID.fromString(created.jsonPath().getString("importJobId"));
        try {
            assertProblemEnvelope(authenticated(ownerToken).when().get(IMPORTS + "/" + otherJobId),
                    404, "IMPORT_JOB_NOT_FOUND");
            assertThat(authenticated(ownerToken).when().get(IMPORTS)
                    .jsonPath().getList("items.importJobId", String.class)).doesNotContain(otherJobId.toString());
        } finally {
            authenticated(otherToken).when().delete(IMPORTS + "/" + otherJobId);
        }
    }

    @Test
    void importEndpointsRequireAuthentication() {
        assertProblemEnvelope(multipartAnonymous(
                        filePart("file", FIXTURE_NAME, fixtureBytes(), "application/geo+json"),
                        fieldPart("format", "geojson"),
                        fieldPart("options", "{}"))
                        .when().post(IMPORTS),
                401, "AUTHENTICATION_REQUIRED");
        assertProblemEnvelope(given().contentType(ContentType.JSON)
                        .body(Map.of("fileName", FIXTURE_NAME, "fileSize", 10,
                                "importFormat", "geojson", "options", Map.of()))
                        .when().post(UPLOADS),
                401, "AUTHENTICATION_REQUIRED");
        assertProblemEnvelope(given().when().get(IMPORTS), 401, "AUTHENTICATION_REQUIRED");
    }

    /**
     * Security pin for {@code DebugImportResource}, which carries <em>no</em> security annotation at
     * all. It is refused today only because the handler body touches {@code SecurityIdentity} via
     * {@code currentUserService.getCurrentUserId()} and proactive auth is off — the guarantee is
     * incidental rather than declarative. The endpoint defaults {@code clearExistingData=true} and
     * deletes every trip, stay and GPS point for the user, so it matters.
     *
     * <p>One divergence from other problem responses is pinned here: {@code instance} keeps the
     * {@code /api/v1} prefix, which {@code GeoPulseProblemPostProcessor} normally strips — compare the
     * geocoding endpoints, which report {@code instance="/geocoding/13"}. That indicates a different
     * producer than the standard exception mapper.
     */
    @Test
    void debugImportsRefuseAnonymousAccess() {
        Response anonymous = multipartAnonymous(
                filePart("file", "dump.zip", zipWithoutGpsData(), "application/zip"),
                fieldPart("clearExistingData", "false"),
                fieldPart("updateTimelineConfig", "false"))
                .when().post(DEBUG_IMPORTS);

        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(anonymous.contentType()).startsWith("application/problem+json");
        assertThat(anonymous.jsonPath().getString("code")).isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(anonymous.jsonPath().getString("instance"))
                .as("instance keeps the /api/v1 prefix here; the post-processor strips it elsewhere")
                .isEqualTo("/api/v1/debug-imports");
    }

    @Test
    void debugImportValidatesItsArchive() {
        assertProblemEnvelope(multipart(ownerToken,
                        filePart("file", "notes.txt", "not a zip".getBytes(StandardCharsets.UTF_8), "text/plain"),
                        fieldPart("clearExistingData", "false"),
                        fieldPart("updateTimelineConfig", "false"))
                        .when().post(DEBUG_IMPORTS),
                400, "INVALID_DEBUG_IMPORT");

        // A .zip that does not contain gps_data.json.
        assertProblemEnvelope(multipart(ownerToken,
                        filePart("file", "dump.zip", zipWithoutGpsData(), "application/zip"),
                        fieldPart("clearExistingData", "false"),
                        fieldPart("updateTimelineConfig", "false"))
                        .when().post(DEBUG_IMPORTS),
                400, "INVALID_DEBUG_IMPORT");
    }

    private static byte[] fixtureBytes() {
        try (InputStream stream = ImportContractTest.class.getResourceAsStream(FIXTURE)) {
            assertThat(stream).as("fixture %s must be on the test classpath", FIXTURE).isNotNull();
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] zipWithoutGpsData() {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("readme.txt"));
            zip.write("no gps_data.json here".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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

    /** One multipart part; {@code fileName}/{@code contentType} are null for plain form fields. */
    private record Part(String name, String fileName, String contentType, byte[] content) {
    }

    private static Part filePart(String name, String fileName, byte[] content, String contentType) {
        return new Part(name, fileName, contentType, content);
    }

    private static Part fieldPart(String name, String value) {
        return new Part(name, null, null, value.getBytes(StandardCharsets.UTF_8));
    }

    private RequestSpecification multipart(String token, Part... parts) {
        return MultipartBody.spec(token, parts);
    }

    private RequestSpecification multipartAnonymous(Part... parts) {
        return MultipartBody.spec(null, parts);
    }

    /**
     * Builds the multipart body by hand, so a part can be sent with or without its own content type —
     * the way curl and browsers send plain form fields.
     */
    private static final class MultipartBody {
        private MultipartBody() {
        }

        static RequestSpecification spec(String token, Part[] parts) {
            String boundary = "----GeoPulseTest" + UUID.randomUUID().toString().replace("-", "");
            RequestSpecification spec = given()
                    .contentType("multipart/form-data; boundary=" + boundary)
                    .body(render(boundary, parts));
            return token == null ? spec : spec.header("Authorization", "Bearer " + token);
        }

        private static byte[] render(String boundary, Part[] parts) {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            for (Part part : parts) {
                StringBuilder headers = new StringBuilder();
                headers.append("--").append(boundary).append("\r\n");
                headers.append("Content-Disposition: form-data; name=\"").append(part.name()).append('"');
                if (part.fileName() != null) {
                    headers.append("; filename=\"").append(part.fileName()).append('"');
                }
                headers.append("\r\n");
                if (part.contentType() != null) {
                    headers.append("Content-Type: ").append(part.contentType()).append("\r\n");
                }
                headers.append("\r\n");
                body.writeBytes(headers.toString().getBytes(StandardCharsets.UTF_8));
                body.writeBytes(part.content());
                body.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            body.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return body.toByteArray();
        }
    }
}
