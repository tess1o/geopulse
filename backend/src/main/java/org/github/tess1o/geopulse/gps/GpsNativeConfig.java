package org.github.tess1o.geopulse.gps;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.gps.integrations.dawarich.model.point.DawarichGeometry;
import org.github.tess1o.geopulse.gps.integrations.dawarich.model.point.DawarichLocation;
import org.github.tess1o.geopulse.gps.integrations.dawarich.model.point.DawarichPayload;
import org.github.tess1o.geopulse.gps.integrations.dawarich.model.point.DawarichProperties;
import org.github.tess1o.geopulse.gps.integrations.dawarich.model.stats.DawarichMonthlyDistanceKm;
import org.github.tess1o.geopulse.gps.integrations.dawarich.model.stats.DawarichStatsResponse;
import org.github.tess1o.geopulse.gps.integrations.dawarich.model.stats.DawarichYearlyStats;
import org.github.tess1o.geopulse.gps.integrations.googletimeline.model.*;
import org.github.tess1o.geopulse.gps.integrations.googletimeline.model.semanticsegments.*;
import org.github.tess1o.geopulse.gps.integrations.gpx.model.*;
import org.github.tess1o.geopulse.gps.integrations.homeassistant.model.HomeAssistantBattery;
import org.github.tess1o.geopulse.gps.integrations.homeassistant.model.HomeAssistantGpsData;
import org.github.tess1o.geopulse.gps.integrations.homeassistant.model.HomeAssistantLocation;
import org.github.tess1o.geopulse.gps.integrations.overland.model.OverlandLocationMessage;
import org.github.tess1o.geopulse.gps.integrations.overland.model.OverlandLocations;
import org.github.tess1o.geopulse.gps.integrations.overland.model.OverlandResultResponse;
import org.github.tess1o.geopulse.gps.integrations.overland.model.Properties;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.ConfigurationMessage;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.StatusMessage;
import org.github.tess1o.geopulse.gps.model.*;
import org.github.tess1o.geopulse.gps.service.simplification.TimelineSegmentBoundary;

@RegisterForReflection(targets = {
        GpsPointEntity.class,
        GoogleTimelineRecordType.class,
        GpsPointPathPointDTO.class,
        GpsPointSummaryDTO.class,
        GpsPointPathDTO.class,
        BulkDeleteGpsPointsDto.class,
        EditGpsPointDto.class,
        GpsPointDTO.class,
        GpsPointPageDTO.class,
        GpsPointPaginationDTO.class,
        GpxFile.class,
        GpxMetadata.class,
        GpxTrack.class,
        GpxTrackPoint.class,
        GpxTrackSegment.class,
        GpxWaypoint.class,
        OwnTracksLocationMessage.class,
        StatusMessage.class,
        ConfigurationMessage.class,
        OverlandLocationMessage.class,
        org.github.tess1o.geopulse.gps.integrations.overland.model.Geometry.class,
        Properties.class,
        OverlandLocations.class,
        OverlandResultResponse.class,
        DawarichGeometry.class,
        DawarichLocation.class,
        DawarichPayload.class,
        DawarichProperties.class,
        DawarichMonthlyDistanceKm.class,
        DawarichStatsResponse.class,
        DawarichYearlyStats.class,
        GoogleTimelineActivity.class,
        GoogleTimelineActivityCandidate.class,
        GoogleTimelineGpsPoint.class,
        GoogleTimelinePath.class,
        GoogleTimelineRecord.class,
        GoogleTimelineVisit.class,
        GoogleTimelineVisitCandidate.class,
        GoogleTimelineSemanticSegmentsRoot.class,
        GoogleTimelineSemanticSegment.class,
        GoogleTimelineSemanticVisit.class,
        GoogleTimelineSemanticVisitCandidate.class,
        GoogleTimelineSemanticLatitudeLongitude.class,
        GoogleTimelineSemanticActivity.class,
        GoogleTimelineSemanticActivityCandidate.class,
        GoogleTimelineSemanticTimelinePath.class,
        GoogleTimelineRawSignal.class,
        GoogleTimelinePosition.class,
        GoogleTimelineUserLocationProfile.class,
        GoogleTimelineFrequentPlace.class,
        HomeAssistantBattery.class,
        HomeAssistantGpsData.class,
        HomeAssistantLocation.class,
        GpsPointFilterDTO.class,
        org.github.tess1o.geopulse.gps.integrations.geojson.model.GeoJsonFeatureCollection.class,
        org.github.tess1o.geopulse.gps.integrations.geojson.model.GeoJsonFeature.class,
        org.github.tess1o.geopulse.gps.integrations.geojson.model.GeoJsonGeometry.class,
        org.github.tess1o.geopulse.gps.integrations.geojson.model.GeoJsonPoint.class,
        org.github.tess1o.geopulse.gps.integrations.geojson.model.GeoJsonLineString.class,
        org.github.tess1o.geopulse.gps.integrations.geojson.model.GeoJsonProperties.class,
        TimelineSegmentBoundary.class,
        TimelineSegmentBoundary.SegmentType.class
})
public class GpsNativeConfig {
}
