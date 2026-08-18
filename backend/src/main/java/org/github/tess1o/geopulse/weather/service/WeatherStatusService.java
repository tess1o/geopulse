package org.github.tess1o.geopulse.weather.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.weather.dto.WeatherStatusResponse;

@ApplicationScoped
public class WeatherStatusService {

    @Inject
    WeatherService weatherService;

    @Inject
    WeatherPipelineWorker worker;

    public WeatherStatusResponse status() {
        WeatherStatusResponse response = weatherService.status();
        response.setProcessing(worker.snapshot());
        response.setFetchBlockedReason(worker.lastBlockReason());
        response.setLastCompletedAt(worker.lastCompletedAt());
        return response;
    }
}

