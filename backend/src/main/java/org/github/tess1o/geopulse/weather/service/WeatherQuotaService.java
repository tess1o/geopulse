package org.github.tess1o.geopulse.weather.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.weather.repository.WeatherSampleTargetRepository;

import java.time.LocalDate;
import java.time.ZoneOffset;

@ApplicationScoped
@Transactional
public class WeatherQuotaService {

    @Inject
    WeatherSampleTargetRepository targetRepository;

    public long requestsUsedToday() {
        return targetRepository.countAttemptsToday(LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC));
    }
}
