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
import org.github.tess1o.geopulse.gps.integrations.gpx.model.*;
import org.github.tess1o.geopulse.gps.integrations.homeassistant.model.HomeAssistantBattery;
import org.github.tess1o.geopulse.gps.integrations.homeassistant.model.HomeAssistantGpsData;
import org.github.tess1o.geopulse.gps.integrations.homeassistant.model.HomeAssistantLocation;
import org.github.tess1o.geopulse.gps.integrations.overland.model.OverlandLocationMessage;
import org.github.tess1o.geopulse.gps.integrations.overland.model.OverlandLocations;
import org.github.tess1o.geopulse.gps.integrations.overland.model.Properties;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.ConfigurationMessage;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.StatusMessage;
import org.github.tess1o.geopulse.gps.model.*;

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
        HomeAssistantBattery.class,
        HomeAssistantGpsData.class,
        HomeAssistantLocation.class,
})
public class GpsNativeConfig {
}
