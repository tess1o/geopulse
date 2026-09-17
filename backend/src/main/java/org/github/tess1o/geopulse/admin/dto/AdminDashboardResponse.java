package org.github.tess1o.geopulse.admin.dto;

import org.github.tess1o.geopulse.geocoding.dto.GeocodingHealthResponse;
import org.github.tess1o.geopulse.integration.dto.ExternalIntegrationHealthDto;
import org.github.tess1o.geopulse.mapmatching.dto.MapMatchingAdminStatusDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineJobStatistics;
import org.github.tess1o.geopulse.weather.dto.WeatherStatusResponse;

import java.time.Instant;
import java.util.List;

public record AdminDashboardResponse(
        long totalUsers,
        long activeUsers24h,
        long totalGpsPoints,
        long gpsActivity24h,
        WeatherStatusResponse weatherStatus,
        Health health,
        MetricsEnabled metricsEnabled
) {
    public record MetricsEnabled(boolean user, boolean gps) {}
    public record Health(BackupHealth backup, IngestionHealth ingestion,
                         GeocodingHealthResponse geocoding, MapMatchingHealth mapMatching,
                         TimelineJobStatistics timeline, SecurityHealth security) {}
    public record BackupHealth(boolean scheduled, Instant latestBackupAt, int healthMaxAgeDays, boolean stale) {}
    public record IngestionHealth(Instant latestReceivedAt, long pointsLast24h) {}
    public record MapMatchingHealth(boolean enabled, boolean configured, String provider,
                                    ExternalIntegrationHealthDto providerHealth,
                                    MapMatchingAdminStatusDTO status) {}
    public record SecurityHealth(List<SecurityWarningCode> warnings) {}
    public enum SecurityWarningCode { SCHEDULED_BACKUPS_DISABLED }
}
