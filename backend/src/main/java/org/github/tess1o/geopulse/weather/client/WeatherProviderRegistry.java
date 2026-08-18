package org.github.tess1o.geopulse.weather.client;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.weather.service.WeatherConfigurationService;

import java.util.Optional;

@ApplicationScoped
public class WeatherProviderRegistry {

    @Inject
    OpenMeteoWeatherClient openMeteoWeatherClient;

    @Inject
    PirateWeatherClient pirateWeatherClient;

    public Optional<WeatherProviderClient> client(String providerKey) {
        return switch (providerKey) {
            case WeatherConfigurationService.PROVIDER_OPEN_METEO -> Optional.of(openMeteoWeatherClient);
            case WeatherConfigurationService.PROVIDER_PIRATE_WEATHER -> Optional.of(pirateWeatherClient);
            default -> Optional.empty();
        };
    }
}
