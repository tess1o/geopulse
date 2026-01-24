package org.github.tess1o.geopulse.export;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.export.dto.*;
import org.github.tess1o.geopulse.export.model.*;
import org.github.tess1o.geopulse.export.rest.ExportResource;

@RegisterForReflection(targets = {
        ExportStatus.class,
        CreateExportRequest.class,
        RawGpsDataDto.class,
        DataGapsDataDto.class,
        FavoritesDataDto.class,
        LocationSourcesDataDto.class,
        ReverseGeocodingDataDto.class,
        UserInfoDataDto.class,
        TimelineDataDto.StayDto.class,
        TimelineDataDto.TripDto.class,
        TimelineDataDto.DataGapDto.class,
        ExportJobResponse.class,
        ExportMetadataDto.class,
        FavoritesDataDto.class,
        ExportDateRange.class,
        ExportResource.ListExportJobsResponse.class
})
public class ExportNativeConfig {
}
