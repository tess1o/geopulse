package org.github.tess1o.geopulse.shared.openapi;

import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Paths;
import org.eclipse.microprofile.openapi.models.media.Content;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StandardAuthResponsesOpenApiFilter implements OASFilter {

    private static final String API_ERROR_RESPONSE_SCHEMA = "ApiProblemResponse";
    private static final String API_ERROR_RESPONSE_REF = "#/components/schemas/" + API_ERROR_RESPONSE_SCHEMA;
    private static final String API_ERROR_CODES_EXTENSION = "x-geopulse-api-errors";
    private static final String APPLICATION_PROBLEM_JSON = "application/problem+json";
    private static final String UNAUTHORIZED_DESCRIPTION =
            "Authentication is required or the provided credentials are invalid.";
    private static final String FORBIDDEN_DESCRIPTION =
            "The authenticated user does not have permission to access this resource.";
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
                removeInfrastructureHeaders(operation);
                addStandardAuthResponses(operation);
                addApiErrorResponseContent(operation);
            }
        }

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

}
