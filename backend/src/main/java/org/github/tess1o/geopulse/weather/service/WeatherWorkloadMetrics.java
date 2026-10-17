package org.github.tess1o.geopulse.weather.service;

import org.github.tess1o.geopulse.prometheus.GeoPulseWorkloadMetrics;
import org.github.tess1o.geopulse.weather.model.WeatherTargetSource;

/**
 * Weather pipeline timers and counters. Every call is a no-op when no metrics registry is available.
 */
final class WeatherWorkloadMetrics {

    private final GeoPulseWorkloadMetrics workloadMetrics;

    WeatherWorkloadMetrics(GeoPulseWorkloadMetrics workloadMetrics) {
        this.workloadMetrics = workloadMetrics;
    }

    long start() {
        return workloadMetrics == null ? System.nanoTime() : workloadMetrics.start();
    }

    void recordTimer(String name, long startedAtNanos, String source, String result) {
        if (workloadMetrics == null) {
            return;
        }
        workloadMetrics.recordTimer(name, startedAtNanos,
                "component", "weather",
                "source", normalizeWeatherSource(source),
                "result", result);
    }

    void recordStage(long startedAtNanos, String stage, String source, String result) {
        if (workloadMetrics == null) {
            return;
        }
        workloadMetrics.recordTimer("geopulse.weather.stage.duration", startedAtNanos,
                "component", "weather",
                "stage", stage,
                "source", normalizeWeatherSource(source),
                "result", result);
    }

    void countTargets(WeatherTargetSource source, String result, long count) {
        if (workloadMetrics == null || count <= 0) {
            return;
        }
        workloadMetrics.increment("geopulse.weather.targets", count,
                "component", "weather",
                "source", source == null ? "ALL" : source.name(),
                "result", result);
    }

    void countChunks(String trigger, String result, long count) {
        if (workloadMetrics == null || count <= 0) {
            return;
        }
        workloadMetrics.increment("geopulse.weather.reconciliation.chunks", count,
                "component", "weather",
                "trigger", normalizeWeatherSource(trigger),
                "result", result);
    }

    void recordProviderRequest(String providerKey, WeatherTargetSource source, String result) {
        if (workloadMetrics == null) {
            return;
        }
        workloadMetrics.increment("geopulse.weather.provider.requests",
                "component", "weather",
                "provider", providerKey,
                "source", source.name(),
                "result", result);
    }

    void countQuotaBlock(String type, String providerKey) {
        if (workloadMetrics != null) {
            workloadMetrics.increment("geopulse.weather.quota.blocks",
                    "component", "weather",
                    "type", type,
                    "provider", providerKey);
        }
    }

    private String normalizeWeatherSource(String source) {
        return source == null || source.isBlank() ? "unknown" : source;
    }
}
