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
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.model.ApiTokenAuthenticationResult;
import org.github.tess1o.geopulse.shared.api.UserIpAddress;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.security.Principal;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
@Priority(Priorities.AUTHENTICATION - 50)
@Slf4j
public class ApiTokenAuthenticationMechanism implements HttpAuthenticationMechanism {
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
            context.put(AuthenticationChallenge.FAILURE_DETAIL, "Conflicting service account credentials");
            return Uni.createFrom().nullItem();
        }

        context.put(AuthenticationChallenge.FAILURE_DETAIL, INVALID_SERVICE_ACCOUNT_TOKEN);
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
                .addAttribute(IdentityAttributes.AUTH_TYPE, IdentityAttributes.AUTH_TYPE_API_TOKEN)
                .addAttribute(IdentityAttributes.USER_ID, user.getId().toString())
                .addAttribute(IdentityAttributes.USER_EMAIL, user.getEmail())
                .addAttribute(IdentityAttributes.API_TOKEN_ID, auth.apiToken().getId().toString())
                .addAttribute(IdentityAttributes.API_TOKEN_SCOPES, auth.apiToken().getScopeSet())
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
        if (context.get(AuthenticationChallenge.FAILURE_DETAIL) == null && hasServiceTokenCredentials(context)) {
            context.put(AuthenticationChallenge.FAILURE_DETAIL, INVALID_SERVICE_ACCOUNT_TOKEN);
        }
        return AuthenticationChallenge.send(context);
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
