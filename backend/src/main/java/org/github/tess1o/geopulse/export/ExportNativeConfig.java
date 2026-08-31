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
        LocationSourcesDataDto.SourceDto.class,
        PeriodTagsDataDto.class,
        PeriodTagsDataDto.PeriodTagDto.class,
        TimelineOverridesDataDto.class,
        TimelineOverridesDataDto.TripMovementOverrideDto.class,
        TimelineOverridesDataDto.DataGapStayOverrideDto.class,
        TripWorkspaceDataDto.class,
        TripWorkspaceDataDto.TripDto.class,
        TripWorkspaceDataDto.TripPlanItemDto.class,
        TripWorkspaceDataDto.TripCollaboratorDto.class,
        NotificationTemplatesDataDto.class,
        NotificationTemplatesDataDto.NotificationTemplateDto.class,
        GeofencingDataDto.class,
        GeofencingDataDto.GeofenceRuleDto.class,
        GeofencingDataDto.SubjectDto.class,
        NotesDataDto.class,
        NotesDataDto.NoteDto.class,
        WeatherSamplesDataDto.class,
        WeatherSamplesDataDto.WeatherSampleDto.class,
        MapMatchingDataDto.class,
        MapMatchingDataDto.PathMatchDto.class,
        FriendsDataDto.class,
        FriendsDataDto.FriendDto.class,
        FriendPermissionsDataDto.class,
        FriendPermissionsDataDto.FriendPermissionDto.class,
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
