package org.github.tess1o.geopulse.shared.openapi;

import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Paths;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;

import java.util.List;
import java.util.Map;

public class StandardAuthResponsesOpenApiFilter implements OASFilter {

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
            }
        }
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
