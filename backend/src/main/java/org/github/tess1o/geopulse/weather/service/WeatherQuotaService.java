package org.github.tess1o.geopulse.weather.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.weather.model.WeatherTargetSource;
import org.github.tess1o.geopulse.weather.repository.WeatherDailyRequestUsage;
import org.github.tess1o.geopulse.weather.repository.WeatherDailyRequestUsageRepository;

import java.time.LocalDate;
import java.time.ZoneOffset;

@ApplicationScoped
@Transactional
public class WeatherQuotaService {

    @Inject
    WeatherDailyRequestUsageRepository usageRepository;

    public long requestsUsedToday() {
        return usage().requestCount();
    }

    public WeatherDailyRequestUsage usage() {
        return usageRepository.today();
    }

    public boolean tryReserve(WeatherTargetSource source, int dailyLimit, int ongoingReserve) {
        return usageRepository.tryReserve(source, dailyLimit, ongoingReserve);
    }

    public boolean tryReserveConnectionTest(int calls, int dailyLimit) {
        return usageRepository.tryReserve(WeatherTargetSource.ONGOING, calls, dailyLimit, 0);
    }

    public long cleanupOldUsage(int retentionDays) {
        LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(Math.max(1, retentionDays));
        return usageRepository.deleteBefore(cutoff);
    }
}
