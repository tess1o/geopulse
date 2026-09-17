package org.github.tess1o.geopulse.shared.openapi;

import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Paths;
import org.eclipse.microprofile.openapi.models.media.Content;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;
import org.github.tess1o.geopulse.shared.api.ApiErrorCode;

import java.util.List;
import java.util.Map;

public class StandardAuthResponsesOpenApiFilter implements OASFilter {

    private static final String API_ERROR_RESPONSE_SCHEMA = "GeoPulseHttpProblem";
    private static final String API_ERROR_RESPONSE_REF = "#/components/schemas/" + API_ERROR_RESPONSE_SCHEMA;
    private static final String API_ERROR_CODES_EXTENSION = "x-geopulse-api-errors";
    private static final String APPLICATION_PROBLEM_JSON = "application/problem+json";
    private static final String UNAUTHORIZED_DESCRIPTION =
            "Authentication is required or the provided credentials are invalid.";
    private static final String FORBIDDEN_DESCRIPTION =
            "The authenticated user does not have permission to access this resource.";
    private static final List<PublicApiGroup> PUBLIC_API_GROUPS = List.of(
            new PublicApiGroup("Public: Auth: Invitations",
                    "/api/auth/invitation"),
            new PublicApiGroup("Public: Auth: Mobile",
                    "/api/auth/mobile", "/api/mobile/session/exchange"),
            new PublicApiGroup("Public: Auth: OIDC",
                    "/api/auth/oidc"),
            new PublicApiGroup("Public: Auth: Sessions",
                    "/api/auth"),
            new PublicApiGroup("Public: Auth: Registration",
                    "/api/users/sign-up/status", "/api/users/register"),
            new PublicApiGroup("Public: System: Metrics",
                    "/api/prometheus/metrics"),
            new PublicApiGroup("Public: System: Health",
                    "/api/health"),
            new PublicApiGroup("Public: System: Version",
                    "/api/version"),
            new PublicApiGroup("Public: Home",
                    "/api/home/content"),
            new PublicApiGroup("Public: GPS Integrations: OwnTracks",
                    "/api/owntracks"),
            new PublicApiGroup("Public: GPS Integrations: Overland",
                    "/api/overland"),
            new PublicApiGroup("Public: GPS Integrations: Traccar",
                    "/api/traccar"),
            new PublicApiGroup("Public: GPS Integrations: GPS Logger",
                    "/api/gpslogger"),
            new PublicApiGroup("Public: GPS Integrations: Dawarich",
                    "/api/dawarich"),
            new PublicApiGroup("Public: GPS Integrations: Home Assistant",
                    "/api/homeassistant"),
            new PublicApiGroup("Public: GPS Integrations: Colota",
                    "/api/colota"),
            new PublicApiGroup("Public: Sharing: Shared Links",
                    "/api/shared")
    );

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        Paths paths = openAPI.getPaths();
        if (paths == null || paths.getPathItems() == null) {
            return;
        }

        for (Map.Entry<String, PathItem> pathEntry : paths.getPathItems().entrySet()) {
            PathItem pathItem = pathEntry.getValue();
            if (pathItem == null || pathItem.getOperations() == null) {
                continue;
            }

            for (Operation operation : pathItem.getOperations().values()) {
                PublicApiGroup publicApiGroup = publicApiGroupFor(pathEntry.getKey());
                if (publicApiGroup != null) {
                    markAsPublic(operation, publicApiGroup);
                } else {
                    addStandardAuthResponses(operation);
                }
                addApiErrorResponseContent(operation);
            }
        }

        openAPI.getComponents().addSchema(API_ERROR_RESPONSE_SCHEMA, apiErrorResponseSchema());
    }

    private static PublicApiGroup publicApiGroupFor(String path) {
        return PUBLIC_API_GROUPS.stream()
                .filter(group -> group.matches(path))
                .findFirst()
                .orElse(null);
    }

    private static void markAsPublic(Operation operation, PublicApiGroup publicApiGroup) {
        if (operation == null) {
            return;
        }

        operation.setSecurity(null);
        operation.setTags(List.of(publicApiGroup.tagName()));

        APIResponses responses = operation.getResponses();
        if (responses != null) {
            responses.removeAPIResponse("401");
            responses.removeAPIResponse("403");
        }
    }

    private static void addStandardAuthResponses(Operation operation) {
        if (operation == null || operation.getSecurity() == null || operation.getSecurity().isEmpty()) {
            return;
        }

        APIResponses responses = operation.getResponses();
        if (responses == null) {
            responses = OASFactory.createAPIResponses();
            operation.setResponses(responses);
        }

        setResponseDescription(responses, "401", UNAUTHORIZED_DESCRIPTION);
        setResponseDescription(responses, "403", FORBIDDEN_DESCRIPTION);
    }

    private static void setResponseDescription(APIResponses responses, String responseCode, String description) {
        APIResponse response = responses.getAPIResponse(responseCode);
        if (response == null) {
            response = OASFactory.createAPIResponse();
            responses.addAPIResponse(responseCode, response);
        }
        response.setDescription(description);
    }

    private static void addApiErrorResponseContent(Operation operation) {
        Object errorCodes = operation.getExtension(API_ERROR_CODES_EXTENSION);
        if (!(errorCodes instanceof String codes)) {
            return;
        }
        operation.removeExtension(API_ERROR_CODES_EXTENSION);

        APIResponses responses = operation.getResponses();
        if (responses == null || !hasSuccessResponse(responses)) {
            return;
        }
        for (String code : codes.split(",")) {
            addApiErrorResponse(responses, code.trim());
        }

        responses.getAPIResponses().forEach((responseCode, response) -> {
            if (isDocumentableError(responseCode) && response.getContent() == null) {
                response.setContent(apiErrorContent());
            }
        });
    }

    private static void addApiErrorResponse(APIResponses responses, String responseCode) {
        if (!isDocumentableError(responseCode) || responses.hasAPIResponse(responseCode)) {
            return;
        }

        Response.Status status = Response.Status.fromStatusCode(Integer.parseInt(responseCode));
        responses.addAPIResponse(responseCode, OASFactory.createAPIResponse()
                .description(status == null ? "Error" : status.getReasonPhrase())
                .content(apiErrorContent()));
    }

    private static Content apiErrorContent() {
        return OASFactory.createContent()
                .addMediaType(APPLICATION_PROBLEM_JSON, OASFactory.createMediaType()
                        .schema(OASFactory.createSchema().ref(API_ERROR_RESPONSE_REF)));
    }

    private static boolean hasSuccessResponse(APIResponses responses) {
        return responses.getAPIResponses().keySet().stream().anyMatch(code -> code.startsWith("2"));
    }

    private static boolean isDocumentableError(String responseCode) {
        return (responseCode.startsWith("4") || responseCode.startsWith("5"))
                && !responseCode.equals("401")
                && !responseCode.equals("403");
    }

    private static Schema apiErrorResponseSchema() {
        return OASFactory.createSchema()
                .addType(Schema.SchemaType.OBJECT)
                .addProperty("type", OASFactory.createSchema().addType(Schema.SchemaType.STRING))
                .addProperty("title", OASFactory.createSchema().addType(Schema.SchemaType.STRING))
                .addProperty("status", OASFactory.createSchema().addType(Schema.SchemaType.INTEGER))
                .addProperty("detail", OASFactory.createSchema().addType(Schema.SchemaType.STRING))
                .addProperty("code", OASFactory.createSchema()
                        .addType(Schema.SchemaType.STRING)
                        .enumeration(java.util.Arrays.stream(ApiErrorCode.values())
                                .map(ApiErrorCode::name)
                                .map(value -> (Object) value)
                                .toList()))
                .addProperty("parameters", OASFactory.createSchema().addType(Schema.SchemaType.OBJECT))
                .addProperty("violations", OASFactory.createSchema().addType(Schema.SchemaType.ARRAY));
    }

    private record PublicApiGroup(String tagName, List<String> pathPrefixes) {
        PublicApiGroup(String tagName, String... pathPrefixes) {
            this(tagName, List.of(pathPrefixes));
        }

        boolean matches(String path) {
            return pathPrefixes.stream()
                    .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
        }
    }
}
