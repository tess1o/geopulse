package org.github.tess1o.geopulse.importdata.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.export.dto.FavoritesDataDto;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.user.mapper.TimelinePreferencesMapper;
import org.github.tess1o.geopulse.user.model.TimelinePreferences;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

@ApplicationScoped
public class ImportDataMapper {

    @Inject
    TimelinePreferencesMapper timelinePreferencesMapper;

    public TimelinePreferences updateTimelinePreferences(TimelinePreferences fromImport,
                                                         TimelinePreferences existingInDb) {
        return timelinePreferencesMapper.mergePreferences(fromImport, existingInDb);
    }

    public Point createPointFromCoordinates(Double longitude, Double latitude) {
        if (longitude == null || latitude == null) {
            return null;
        }
        return GeoUtils.createPoint(longitude, latitude);
    }

    public Polygon createPolygonFromCoordinates(FavoritesDataDto.FavoriteAreaDto areaDto) {
        if (areaDto == null || areaDto.getSouthWestLatitude() == null || areaDto.getNorthEastLatitude() == null ||
                areaDto.getSouthWestLongitude() == null || areaDto.getNorthEastLongitude() == null) {
            return null;
        }
        return GeoUtils.buildBoundingBoxPolygon(
                areaDto.getSouthWestLatitude(),
                areaDto.getNorthEastLatitude(),
                areaDto.getSouthWestLongitude(),
                areaDto.getNorthEastLongitude()
        );
    }
}