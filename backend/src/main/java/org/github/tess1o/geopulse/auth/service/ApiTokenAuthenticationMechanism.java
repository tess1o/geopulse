package org.github.tess1o.geopulse.auth.service;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.MediaType;
import org.github.tess1o.geopulse.auth.model.ApiTokenAuthenticationResult;
import org.github.tess1o.geopulse.shared.api.UserIpAddress;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.security.Principal;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
@Priority(Priorities.AUTHENTICATION - 50)
public class ApiTokenAuthenticationMechanism implements HttpAuthenticationMechanism {
    public static final String ATTR_AUTH_TYPE = "geopulse.auth.type";
    public static final String ATTR_USER_ID = "geopulse.auth.userId";
    public static final String ATTR_USER_EMAIL = "geopulse.auth.userEmail";

    private static final String CONTEXT_AUTH_FAILURE_MESSAGE = "geopulse.apiToken.authFailureMessage";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String INVALID_SERVICE_ACCOUNT_TOKEN = "Invalid service account token";

    @Inject
    ApiTokenService apiTokenService;

    @Inject
    ApiTokenSecretService apiTokenSecretService;

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        String apiKeyToken = trimToNull(context.request().getHeader(API_KEY_HEADER));
        String bearerToken = extractBearerToken(context.request().getHeader(AUTHORIZATION_HEADER));

        boolean apiKeyIsPresent = apiKeyToken != null;
        boolean bearerIsServiceToken = isServiceToken(bearerToken);

        if (!apiKeyIsPresent && !bearerIsServiceToken) {
            return Uni.createFrom().nullItem();
        }

        if (apiKeyIsPresent && bearerIsServiceToken && !apiKeyToken.equals(bearerToken)) {
            context.put(CONTEXT_AUTH_FAILURE_MESSAGE, "Conflicting service account credentials");
            return Uni.createFrom().nullItem();
        }

        context.put(CONTEXT_AUTH_FAILURE_MESSAGE, INVALID_SERVICE_ACCOUNT_TOKEN);
        String token = apiKeyIsPresent ? apiKeyToken : bearerToken;
        String ipAddress = UserIpAddress.resolve(context.request());
        return Uni.createFrom()
                .item(() -> authenticateServiceToken(token, ipAddress))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onItem().transformToUni(identity -> identity
                        .map(securityIdentity -> Uni.createFrom().item(securityIdentity))
                        .orElseGet(() -> Uni.createFrom().nullItem()));
    }

    private Optional<SecurityIdentity> authenticateServiceToken(String token, String ipAddress) {
        Optional<ApiTokenAuthenticationResult> result = apiTokenService.authenticate(token);
        if (result.isEmpty()) {
            return Optional.empty();
        }

        ApiTokenAuthenticationResult auth = result.get();
        UserEntity user = auth.user();
        Principal principal = () -> user.getEmail();
        SecurityIdentity identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(principal)
                .addRole(user.getRole().name())
                .addAttribute(ATTR_AUTH_TYPE, "api-token")
                .addAttribute(ATTR_USER_ID, user.getId().toString())
                .addAttribute(ATTR_USER_EMAIL, user.getEmail())
                .build();

        apiTokenService.recordUsage(auth.apiToken().getId(), ipAddress);
        return Optional.of(identity);
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().item(new ChallengeData(401, "WWW-Authenticate", "Bearer"));
    }

    @Override
    public Uni<Boolean> sendChallenge(RoutingContext context) {
        String authFailureMessage = context.get(CONTEXT_AUTH_FAILURE_MESSAGE);
        if (authFailureMessage == null && hasServiceTokenCredentials(context)) {
            authFailureMessage = INVALID_SERVICE_ACCOUNT_TOKEN;
        }
        if (authFailureMessage == null) {
            return HttpAuthenticationMechanism.super.sendChallenge(context);
        }

        String responseBody = """
                {"status":"error","message":"%s","data":null}\
                """.formatted(authFailureMessage);

        return Uni.createFrom().emitter(emitter -> context.response()
                .setStatusCode(401)
                .putHeader("WWW-Authenticate", "Bearer")
                .putHeader("Content-Type", MediaType.APPLICATION_JSON)
                .end(responseBody)
                .onComplete(result -> {
                    if (result.succeeded()) {
                        emitter.complete(true);
                    } else {
                        emitter.fail(result.cause());
                    }
                }));
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of();
    }

    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        return Uni.createFrom().item(new HttpCredentialTransport(HttpCredentialTransport.Type.AUTHORIZATION, "Bearer"));
    }

    private boolean isServiceToken(String token) {
        return apiTokenSecretService.hasTokenPrefix(token);
    }

    private boolean hasServiceTokenCredentials(RoutingContext context) {
        return trimToNull(context.request().getHeader(API_KEY_HEADER)) != null
                || isServiceToken(extractBearerToken(context.request().getHeader(AUTHORIZATION_HEADER)));
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return trimToNull(authorizationHeader.substring(BEARER_PREFIX.length()));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
