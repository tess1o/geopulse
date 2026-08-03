package org.github.tess1o.geopulse.weather.job;

import org.github.tess1o.geopulse.weather.service.WeatherConfigurationService;
import org.github.tess1o.geopulse.weather.service.WeatherService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
class WeatherJobDisabledGuardTest {

    private final WeatherService weatherService = mock(WeatherService.class);
    private final WeatherConfigurationService configurationService = mock(WeatherConfigurationService.class);

    @Test
    void ongoingDiscoverySkipsWhenWeatherIsDisabled() {
        when(configurationService.isEnabled()).thenReturn(false);
        WeatherOngoingDiscoveryJob job = new WeatherOngoingDiscoveryJob();
        job.weatherService = weatherService;
        job.configurationService = configurationService;

        job.discoverOngoingWeatherTargets();

        verifyNoInteractions(weatherService);
    }

    @Test
    void ongoingDiscoverySkipsWhenOngoingWeatherIsDisabled() {
        when(configurationService.isEnabled()).thenReturn(true);
        when(configurationService.ongoingEnabled()).thenReturn(false);
        WeatherOngoingDiscoveryJob job = new WeatherOngoingDiscoveryJob();
        job.weatherService = weatherService;
        job.configurationService = configurationService;

        job.discoverOngoingWeatherTargets();

        verifyNoInteractions(weatherService);
    }

    @Test
    void sampleFetchSkipsWhenWeatherIsDisabled() {
        when(configurationService.isEnabled()).thenReturn(false);
        WeatherSampleFetchJob job = new WeatherSampleFetchJob();
        job.weatherService = weatherService;
        job.configurationService = configurationService;

        job.fetchWeatherSamples();

        verifyNoInteractions(weatherService);
    }

    @Test
    void providerHealthProbeSkipsWhenWeatherIsDisabled() {
        when(configurationService.isEnabled()).thenReturn(false);
        WeatherProviderHealthProbeJob job = new WeatherProviderHealthProbeJob();
        job.weatherService = weatherService;
        job.configurationService = configurationService;

        job.probeWeatherProviderHealth();

        verifyNoInteractions(weatherService);
    }

    @Test
    void targetCleanupSkipsWhenWeatherIsDisabled() {
        when(configurationService.isEnabled()).thenReturn(false);
        WeatherTargetCleanupJob job = new WeatherTargetCleanupJob();
        job.weatherService = weatherService;
        job.configurationService = configurationService;

        job.cleanupWeatherTargets();

        verifyNoInteractions(weatherService);
    }
}
