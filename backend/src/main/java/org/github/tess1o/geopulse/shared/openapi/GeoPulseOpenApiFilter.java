package org.github.tess1o.geopulse.shared.openapi;

import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.Components;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Paths;
import org.eclipse.microprofile.openapi.models.media.Content;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;
import org.eclipse.microprofile.openapi.models.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.models.security.SecurityScheme;
import org.eclipse.microprofile.openapi.models.tags.Tag;
import org.github.tess1o.geopulse.auth.security.SecurityRoles;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Post-processes the generated OpenAPI document: applies the tag catalog and documentation groups, documents the
 * authentication schemes and the access level of every operation, and adds the standard error responses.
 */
public class GeoPulseOpenApiFilter implements OASFilter {

    private static final Logger LOG = Logger.getLogger(GeoPulseOpenApiFilter.class);
    private static final String UNCATEGORIZED_TAG = "Other";

    private static final String API_ERROR_RESPONSE_SCHEMA = "ApiProblemResponse";
    private static final String API_ERROR_RESPONSE_REF = "#/components/schemas/" + API_ERROR_RESPONSE_SCHEMA;
    private static final String API_ERROR_CODES_EXTENSION = "x-geopulse-api-errors";
    private static final String APPLICATION_PROBLEM_JSON = "application/problem+json";
    private static final String UNAUTHORIZED_DESCRIPTION =
            "Authentication is required or the provided credentials are invalid.";
    private static final String FORBIDDEN_DESCRIPTION =
            "The authenticated user does not have permission to access this resource.";
    private static final String INTERNAL_LINE = "**Internal:** Used by the GeoPulse web app. Not part of the "
            + "documented API and can change without notice.";

    private enum Access {
        PUBLIC("**Access:** Public. No authentication required."),
        USER("**Access:** Any signed-in user, with a session or an API token."),
        ADMIN("**Access:** Administrators only."),
        ADMIN_READ("**Access:** Administrators only. In demo mode, the demo account can also call this "
                + "read-only endpoint."),
        GPS_SOURCE("**Access:** Credentials of a GPS source configured in GeoPulse, not a session or API token."),
        SHARE_LINK("**Access:** Anyone with the share link, using the link's access token. "
                + "No GeoPulse account needed.");

        private final String line;

        Access(String line) {
            this.line = line;
        }
    }

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        Paths paths = openAPI.getPaths();
        if (paths == null || paths.getPathItems() == null) {
            return;
        }

        Set<String> usedTags = new TreeSet<>();
        for (Map.Entry<String, PathItem> pathEntry : paths.getPathItems().entrySet()) {
            PathItem pathItem = pathEntry.getValue();
            if (pathItem == null || pathItem.getOperations() == null) {
                continue;
            }

            for (Map.Entry<PathItem.HttpMethod, Operation> operationEntry : pathItem.getOperations().entrySet()) {
                Operation operation = operationEntry.getValue();
                String operationName = operationEntry.getKey() + " " + pathEntry.getKey();
                usedTags.addAll(collectTags(operation, operationName));
                warnIfUndocumented(operation, operationName);

                Access access = resolveAccess(operation);
                applySecurity(operation, access);
                appendDescriptionLines(operation, access);
                removeInfrastructureHeaders(operation);
                addStandardAuthResponses(operation, access);
                addApiErrorResponseContent(operation);
            }
        }

        applyTagCatalog(openAPI, usedTags);
        applySecuritySchemes(openAPI);
    }

    /**
     * Problems are logged instead of thrown: the build swallows filter exceptions and would silently publish the
     * unfiltered document. Operations with a missing or unknown tag end up in the "Other" group.
     */
    private static Set<String> collectTags(Operation operation, String operationName) {
        List<String> tags = operation.getTags();
        if (tags == null || tags.isEmpty()) {
            LOG.errorf("OpenAPI operation %s has no tag. Add @Tag(name = ApiTags.X) to the resource.", operationName);
            operation.setTags(List.of(UNCATEGORIZED_TAG));
            return Set.of(UNCATEGORIZED_TAG);
        }
        for (String tag : tags) {
            if (ApiTagCatalog.find(tag).isEmpty()) {
                LOG.errorf("OpenAPI operation %s uses tag '%s' that is not registered in ApiTagCatalog.",
                        operationName, tag);
            }
        }
        return new LinkedHashSet<>(tags);
    }

    private static void warnIfUndocumented(Operation operation, String operationName) {
        if (operation.getDescription() == null || operation.getDescription().isBlank()) {
            LOG.warnf("OpenAPI operation %s has no description", operationName);
        }
    }

    private static void applyTagCatalog(OpenAPI openAPI, Set<String> usedTags) {
        List<Tag> tags = new ArrayList<>();
        List<Map<String, Object>> tagGroups = new ArrayList<>();
        for (ApiTagCatalog.TagGroup group : ApiTagCatalog.GROUPS) {
            List<String> groupTags = new ArrayList<>();
            for (ApiTagCatalog.TagDoc tagDoc : group.tags()) {
                if (!usedTags.contains(tagDoc.name())) {
                    LOG.errorf("ApiTagCatalog tag '%s' is not used by any OpenAPI operation.", tagDoc.name());
                    continue;
                }
                Tag tag = OASFactory.createTag()
                        .name(tagDoc.name())
                        .description(tagDoc.description());
                if (tagDoc.displayName() != null) {
                    tag.addExtension("x-displayName", tagDoc.displayName());
                }
                tags.add(tag);
                groupTags.add(tagDoc.name());
            }
            tagGroups.add(tagGroup(group.name(), groupTags));
        }

        List<String> uncategorized = usedTags.stream()
                .filter(tag -> ApiTagCatalog.find(tag).isEmpty())
                .toList();
        if (!uncategorized.isEmpty()) {
            uncategorized.forEach(tag -> tags.add(OASFactory.createTag().name(tag)));
            tagGroups.add(tagGroup(UNCATEGORIZED_TAG, uncategorized));
        }

        openAPI.setTags(tags);
        openAPI.addExtension("x-tagGroups", tagGroups);
    }

    private static Map<String, Object> tagGroup(String name, List<String> tags) {
        Map<String, Object> tagGroup = new LinkedHashMap<>();
        tagGroup.put("name", name);
        tagGroup.put("tags", tags);
        return tagGroup;
    }

    private static void applySecuritySchemes(OpenAPI openAPI) {
        Components components = openAPI.getComponents();
        if (components == null) {
            components = OASFactory.createComponents();
            openAPI.setComponents(components);
        }
        Map<String, SecurityScheme> schemes = new LinkedHashMap<>();
        schemes.put(ApiSecuritySchemes.BEARER, OASFactory.createSecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .description("A user API token or a session access token, sent as `Authorization: Bearer <token>`."));
        schemes.put(ApiSecuritySchemes.API_KEY, OASFactory.createSecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("X-API-Key")
                .description("A user API token, sent as `X-API-Key: <token>`. "
                        + "Create one under Profile → Security → API tokens."));
        schemes.put(ApiSecuritySchemes.GPS_SOURCE_BASIC, OASFactory.createSecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("basic")
                .description("Username and password of a GPS source (OwnTracks, GPSLogger, Colota)."));
        schemes.put(ApiSecuritySchemes.GPS_SOURCE_TOKEN, OASFactory.createSecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .description("Token of a GPS source (Overland, Traccar, Home Assistant, Dawarich), "
                        + "sent as `Authorization: Bearer <token>`."));
        schemes.put(ApiSecuritySchemes.GPS_SOURCE_QUERY_KEY, OASFactory.createSecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.QUERY)
                .name("api_key")
                .description("Token of a Dawarich GPS source, sent as the `api_key` query parameter."));
        schemes.put(ApiSecuritySchemes.SHARE_LINK_TOKEN, OASFactory.createSecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .description("Short-lived access token of a share link, from "
                        + "`POST /api/v1/public/share-links/{linkId}/access-tokens`."));
        components.setSecuritySchemes(schemes);
    }

    private static Access resolveAccess(Operation operation) {
        List<SecurityRequirement> security = operation.getSecurity();
        if (security == null || security.isEmpty()) {
            return Access.PUBLIC;
        }
        Set<String> schemes = security.stream()
                .filter(requirement -> requirement.getSchemes() != null)
                .flatMap(requirement -> requirement.getSchemes().keySet().stream())
                .collect(Collectors.toSet());
        if (schemes.contains(ApiSecuritySchemes.GPS_SOURCE_BASIC)
                || schemes.contains(ApiSecuritySchemes.GPS_SOURCE_TOKEN)
                || schemes.contains(ApiSecuritySchemes.GPS_SOURCE_QUERY_KEY)) {
            return Access.GPS_SOURCE;
        }
        if (schemes.contains(ApiSecuritySchemes.SHARE_LINK_TOKEN)) {
            return Access.SHARE_LINK;
        }
        Set<String> roles = security.stream()
                .filter(requirement -> requirement.getSchemes() != null)
                .flatMap(requirement -> requirement.getSchemes().values().stream())
                .filter(scopes -> scopes != null)
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        if (roles.contains(SecurityRoles.USER)) {
            return Access.USER;
        }
        if (roles.contains(SecurityRoles.DEMO_ADMIN_READ)) {
            return Access.ADMIN_READ;
        }
        if (roles.contains(SecurityRoles.ADMIN)) {
            return Access.ADMIN;
        }
        return Access.USER;
    }

    private static void applySecurity(Operation operation, Access access) {
        switch (access) {
            case USER, ADMIN, ADMIN_READ -> operation.setSecurity(List.of(
                    OASFactory.createSecurityRequirement().addScheme(ApiSecuritySchemes.BEARER),
                    OASFactory.createSecurityRequirement().addScheme(ApiSecuritySchemes.API_KEY)));
            case PUBLIC, GPS_SOURCE, SHARE_LINK -> {
                // Public operations have no requirement; the others keep the requirement declared on the resource.
            }
        }
    }

    private static void appendDescriptionLines(Operation operation, Access access) {
        String lines = isInternal(operation) ? INTERNAL_LINE + "\n\n" + access.line : access.line;
        String description = operation.getDescription();
        operation.setDescription(description == null || description.isBlank()
                ? lines
                : description.stripTrailing() + "\n\n" + lines);
    }

    private static boolean isInternal(Operation operation) {
        return "true".equals(String.valueOf(operation.getExtension(ApiExtensions.INTERNAL)));
    }

    private static void removeInfrastructureHeaders(Operation operation) {
        if (operation.getParameters() == null || operation.getParameters().isEmpty()) {
            return;
        }

        // 1. Stream and filter out the headers you don't want
        List<Parameter> filteredParameters = operation.getParameters().stream()
                .filter(parameter -> !(parameter.getIn() == Parameter.In.HEADER
                        && ("X-Forwarded-For".equalsIgnoreCase(parameter.getName())
                        || "X-Real-IP".equalsIgnoreCase(parameter.getName()))))
                .collect(Collectors.toList());

        // 2. Replace the unmodifiable list with your new filtered list
        operation.setParameters(filteredParameters);
    }

    private static void addStandardAuthResponses(Operation operation, Access access) {
        if (access == Access.PUBLIC) {
            return;
        }

        APIResponses responses = operation.getResponses();
        if (responses == null) {
            responses = OASFactory.createAPIResponses();
            operation.setResponses(responses);
        }

        setResponseDescription(responses, "401", UNAUTHORIZED_DESCRIPTION);
        if (access != Access.GPS_SOURCE && access != Access.SHARE_LINK) {
            setResponseDescription(responses, "403", FORBIDDEN_DESCRIPTION);
        }
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

}
