package org.github.tess1o.geopulse.digest.service;

import org.github.tess1o.geopulse.digest.model.HeatmapDataPoint;
import org.github.tess1o.geopulse.digest.model.HeatmapLayer;
import org.github.tess1o.geopulse.digest.model.TimeDigest;

import java.util.List;
import java.util.UUID;

public interface DigestService {
    TimeDigest getMonthlyDigest(UUID userId, int year, int month, String timezone);

    TimeDigest getYearlyDigest(UUID userId, int year, String timezone);

    List<HeatmapDataPoint> getMonthlyHeatmap(UUID userId, int year, int month, String timezone);

    List<HeatmapDataPoint> getYearlyHeatmap(UUID userId, int year, String timezone);

    List<HeatmapDataPoint> getMonthlyHeatmap(UUID userId, int year, int month, String timezone, HeatmapLayer layer);

    List<HeatmapDataPoint> getYearlyHeatmap(UUID userId, int year, String timezone, HeatmapLayer layer);
}
