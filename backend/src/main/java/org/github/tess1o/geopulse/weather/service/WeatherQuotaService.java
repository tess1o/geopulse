package org.github.tess1o.geopulse.weather.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.weather.repository.WeatherSampleTargetRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@ApplicationScoped
@Transactional
public class WeatherQuotaService {

    @Inject
    WeatherConfigurationService configurationService;

    @Inject
    WeatherSampleTargetRepository targetRepository;

    public long requestsUsedToday() {
        return targetRepository.countAttemptsToday(LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    public long requestsRemainingToday() {
        return Math.max(0, configurationService.dailyRequestLimit() - requestsUsedToday());
    }

    public long backfillRequestsRemainingToday() {
        long used = requestsUsedToday();
        long limitAfterReserve = Math.max(0, configurationService.dailyRequestLimit() - configurationService.ongoingReserve());
        return Math.max(0, limitAfterReserve - used);
    }

    public boolean canProcess(WeatherSampleCandidate candidate) {
        if (candidate == null) {
            return false;
        }
        if (candidate.source() == org.github.tess1o.geopulse.weather.model.WeatherTargetSource.ONGOING) {
            return requestsRemainingToday() > 0;
        }
        return backfillRequestsRemainingToday() > 0;
    }

    public Instant utcDayStart() {
        return LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
