package org.github.tess1o.geopulse.geocoding.service.external;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.geocoding.adapter.PhotonResponseAdapter;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfigurationService;

import java.util.Optional;

@ApplicationScoped
@Typed(ChibiGeoGeocodingService.class)
public class ChibiGeoGeocodingService extends PhotonGeocodingService {

    private final GeocodingConfigurationService configService;

    protected ChibiGeoGeocodingService() {
        super();
        this.configService = null;
    }

    @Inject
    public ChibiGeoGeocodingService(PhotonResponseAdapter adapter,
                                    GeocodingConfigurationService configService,
                                    @ConfigProperty(name = "quarkus.rest-client.chibigeo-api.url") String defaultUrl) {
        super(adapter, configService, defaultUrl);
        this.configService = configService;
    }

    @Override
    public boolean isEnabled() {
        String apiKey = configService.getChibiGeoApiKey();
        return configService.isChibiGeoEnabled() && apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String getProviderName() {
        return "ChibiGeo";
    }

    @Override
    protected Optional<String> getConfiguredUrl() {
        return configService.getChibiGeoUrl();
    }

    @Override
    protected Optional<String> getConfiguredLanguage() {
        return configService.getChibiGeoLanguage();
    }

    @Override
    protected String getApiKeyHeader() {
        return configService.getChibiGeoApiKey();
    }
}
