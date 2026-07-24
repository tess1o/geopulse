package org.github.tess1o.geopulse.weather;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.weather.client.OpenMeteoRestClient;
import org.github.tess1o.geopulse.weather.dto.*;
import org.github.tess1o.geopulse.weather.model.WeatherSampleEntity;
import org.github.tess1o.geopulse.weather.model.WeatherSampleTargetEntity;
import org.github.tess1o.geopulse.weather.model.WeatherTargetSource;
import org.github.tess1o.geopulse.weather.model.WeatherTargetStatus;

@RegisterForReflection(targets = {
        WeatherSampleEntity.class,
        WeatherSampleTargetEntity.class,
        WeatherTargetSource.class,
        WeatherTargetStatus.class,
        WeatherSampleDTO.class,
        WeatherSamplesResponse.class,
        WeatherStatusResponse.class,
        WeatherTargetQueueResponse.class,
        WeatherBackfillRequest.class,
        WeatherTestResponse.class,
        WeatherProviderSample.class,
        OpenMeteoResponse.class,
        OpenMeteoResponse.OpenMeteoCurrent.class,
        OpenMeteoResponse.OpenMeteoHourly.class,
        OpenMeteoRestClient.class
})
public class WeatherNativeConfig {
}
