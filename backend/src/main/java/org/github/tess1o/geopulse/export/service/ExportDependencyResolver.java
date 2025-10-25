package org.github.tess1o.geopulse.export.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service responsible for resolving export dependencies.
 * Determines what additional data needs to be included when exporting timeline data.
 */
@ApplicationScoped
@Slf4j
public class ExportDependencyResolver {

    @Inject
    ExportDataCollectorService dataCollectorService;

    /**
     * Collects timeline dependencies and adds them to the actualDataTypes set.
     * Timeline exports may require favorites and reverse geocoding data.
     *
     * @param job             the export job
     * @param actualDataTypes the set of data types to be exported (will be modified)
     */
    public void collectTimelineDependencies(ExportJob job, Set<String> actualDataTypes) {
        log.debug("Collecting timeline dependencies for user {}", job.getUserId());

        // Get all stays to collect dependency IDs
        var stays = dataCollectorService.collectTimelineStays(job);

        // Collect unique favorite IDs
        Set<Long> favoriteIds = extractFavoriteIds(stays);

        // Collect unique reverse geocoding IDs
        Set<Long> geocodingIds = extractGeocodingIds(stays);

        if (!favoriteIds.isEmpty()) {
            actualDataTypes.add(ExportImportConstants.DataTypes.FAVORITES);
            log.debug("Auto-including {} favorite locations for timeline export", favoriteIds.size());
        }

        if (!geocodingIds.isEmpty()) {
            actualDataTypes.add(ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION);
            log.debug("Auto-including {} reverse geocoding locations for timeline export", geocodingIds.size());
        }
    }

    /**
     * Extracts favorite location IDs from timeline stays.
     *
     * @param stays list of timeline stays
     * @return set of favorite location IDs
     */
    public Set<Long> extractFavoriteIds(List<TimelineStayEntity> stays) {
        return stays.stream()
                .filter(stay -> stay.getFavoriteLocation() != null)
                .map(stay -> stay.getFavoriteLocation().getId())
                .collect(Collectors.toSet());
    }

    /**
     * Extracts reverse geocoding location IDs from timeline stays.
     *
     * @param stays list of timeline stays
     * @return set of reverse geocoding location IDs
     */
    public Set<Long> extractGeocodingIds(List<TimelineStayEntity> stays) {
        return stays.stream()
                .filter(stay -> stay.getGeocodingLocation() != null)
                .map(stay -> stay.getGeocodingLocation().getId())
                .collect(Collectors.toSet());
    }
}
