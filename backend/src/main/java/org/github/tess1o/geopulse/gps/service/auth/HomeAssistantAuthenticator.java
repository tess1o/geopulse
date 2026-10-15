package org.github.tess1o.geopulse.gps.service.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.exceptions.InvalidPasswordException;
import org.github.tess1o.geopulse.auth.model.ApiTokenScope;
import org.github.tess1o.geopulse.auth.service.ApiTokenService;
import org.github.tess1o.geopulse.gpssource.service.GpsSourceService;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.util.Map;
import java.util.Optional;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.GPS_SOURCE_DISABLED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.GPS_SOURCE_NOT_CONNECTED;

@ApplicationScoped
@Slf4j
public class HomeAssistantAuthenticator extends AbstractGpsIntegrationAuthenticator {
    @Override
    public GpsSourceType getSupportedSourceType() {
        return GpsSourceType.HOME_ASSISTANT;
    }

    @Inject
    ApiTokenService apiTokenService;

    @Inject
    GpsSourceService gpsSourceService;

    /**
     * Accepts the source's own token, or a personal API token with the {@code gps:ingest} scope; points sent
     * with an API token go to the Home Assistant source connected to that token in Location Sources.
     */
    @Override
    protected Optional<GpsSourceConfigEntity> findConfig(String authHeader) {
        String token = extractBearerToken(authHeader);
        if (apiTokenService.isApiToken(token)) {
            return apiTokenService.authenticate(token)
                    .filter(auth -> auth.apiToken().getScopeSet().contains(ApiTokenScope.ALL)
                            || auth.apiToken().getScopeSet().contains(ApiTokenScope.GPS_INGEST))
                    .map(auth -> gpsSourceService.findApiTokenSource(auth.apiToken(), GpsSourceType.HOME_ASSISTANT)
                            .orElseThrow(() -> new GeoPulseException(GPS_SOURCE_NOT_CONNECTED,
                                    "No Home Assistant location source is connected to this API token. Add one under "
                                            + "Location Sources, choosing \"GeoPulse integration\".",
                                    Map.of("apiTokenId", auth.apiToken().getId().toString()))));
        }
        return configProvider.findByTokenAndSourceType(token, GpsSourceType.HOME_ASSISTANT);
    }

    /**
     * A disabled source connected to an API token is reported as such (403), not as bad credentials: the token
     * itself is fine, and clients should not ask the user to re-authenticate.
     */
    @Override
    protected boolean isConfigActive(GpsSourceConfigEntity config) {
        if (!config.isActive() && config.getApiToken() != null) {
            throw new GeoPulseException(GPS_SOURCE_DISABLED, "The Home Assistant location source is disabled in GeoPulse",
                    Map.of("sourceId", config.getId().toString()));
        }
        return super.isConfigActive(config);
    }

    @Override
    protected void validateCredentials(String authHeader, GpsSourceConfigEntity config) throws InvalidPasswordException {
        if (config.getApiToken() != null) {
            return; // already verified by ApiTokenService.authenticate in findConfig
        }
        String token = extractBearerToken(authHeader);
        if (!token.equals(config.getToken())) {
            throw new InvalidPasswordException("Invalid token");
        }
    }

    @Override
    protected void logConfigNotFound() {
        log.error("No GPS source config found for Home Assisant token");
    }

    @Override
    protected void logAuthenticationFailed(Exception e) {
        log.error("Home Assisant authentication failed", e);
    }
}
