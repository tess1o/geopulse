package org.github.tess1o.geopulse.streaming;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.streaming.edits.payload.*;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineReason;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineExplanation;
import org.github.tess1o.geopulse.streaming.edits.ActivityCorrectionService;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.edits.TimelineCorrectionOp;
import org.github.tess1o.geopulse.streaming.edits.TimelineCorrectionStatus;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditStatus;
import org.github.tess1o.geopulse.streaming.model.TimelineJobProgress;
import org.github.tess1o.geopulse.streaming.model.domain.LocationSource;
import org.github.tess1o.geopulse.streaming.model.domain.ProcessorMode;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEventType;
import org.github.tess1o.geopulse.streaming.model.dto.*;
import org.github.tess1o.geopulse.streaming.model.entity.*;
import org.github.tess1o.geopulse.streaming.model.shared.MovementTypeSource;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.service.TimelineSafetyNetService;

@RegisterForReflection(targets = {
       CorrectionPayload.class,
       BeforeValues.class,
       PlaceStayPayload.class,
       PlaceTripPayload.class,
       ClearPayload.class,
       ClearPayload.RemovalResolution.class,
       ResizePayload.class,
       SetPlacePayload.class,
       SetTripTypePayload.class,
       SetLabelPayload.class,
       SetRoutePayload.class,
       MergePayload.class,
       MergePayload.MergeDirection.class,
       SplitStayPayload.class,
        ActivityCorrectionService.Activity.class,
        ActivityCorrectionService.Place.class,
        ActivityCorrectionService.Preview.class,
        ActivityCorrectionService.Request.class,
        ActivityCorrectionService.Warning.class,
        org.github.tess1o.geopulse.streaming.rest.TimelineCorrectionResource.Response.class,
        TimelineExplanation.class,
        TimelineReason.class,
        TimelineEditKind.class,
        TimelineEditStatus.class,
        TimelineCorrectionOp.class,
        TimelineCorrectionStatus.class,
        TimelineCorrectionRequest.class,
        TimelineEditEntity.class,
        TimelineCorrectionEffectEntity.class,
        TimelineEditRequest.class,
        TimelineEditDTO.class,
        TimelineEditTargetDTO.class,
        TimelineEditPreviewDTO.class,
        TimelineEditPreviewDTO.Event.class,
        TimelineStayEntity.class,
        TimelineTripEntity.class,
        TimelineRegenerationCampaignEntity.class,
        TimelineRegenerationCampaignUserEntity.class,
        TimelineRegenerationCampaignSource.class,
        TimelineRegenerationCampaignStatus.class,
        TimelineRegenerationCampaignUserStatus.class,
        TimelineDataGapEntity.class,
        LocationSource.class,
        ProcessorMode.class,
        TimelineEventType.class,
        TripType.class,
        MovementTypeSource.class,
        TimelineDataGapDTO.class,
        TimelineExplanationDTO.class,
        TimelineExplanationDTO.Reason.class,
        TimelineEventExplanationDetailsDTO.class,
        TimelineEventExplanationDetailsDTO.Coordinates.class,
        TimelineEventExplanationDetailsDTO.Settings.class,
        TimelineEventExplanationDetailsDTO.SettingChange.class,
        TimelineEventExplanationDetailsDTO.Point.class,
        TimelineEventExplanationDetailsDTO.Filtered.class,
        TimelineEventExplanationDetailsDTO.IngestionFilter.class,
        MovementTimelineDTO.class,
        TimelineCountResponse.class,
        TimelineStayLocationDTO.class,
        TimelineTripDTO.class,
        TimelineDataGapDTO.class,
        CreateTimelineRegenerationCampaignRequest.class,
        TimelineRegenerationCampaignPreviewRequest.class,
        TimelineRegenerationCampaignPreviewDTO.class,
        TimelineRegenerationCampaignSummaryDTO.class,
        TimelineRegenerationCampaignDetailDTO.class,
        TimelineRegenerationCampaignUserDTO.class,
        TimelineConfig.class,
        TimelineJobProgress.class,
        TimelineJobProgress.JobStatus.class,
        org.github.tess1o.geopulse.streaming.model.TimelineJobStep.class,
        org.github.tess1o.geopulse.streaming.model.TimelineJobMemory.class,
        org.github.tess1o.geopulse.streaming.model.TimelineJobResources.class,
        org.github.tess1o.geopulse.streaming.model.TimelineJobType.class,
        TimelineJobStatistics.class,
        // Key of the EnumMap the nightly safety net reports its results in.
        TimelineSafetyNetService.Outcome.class,
        PlaceDetailsDTO.class,
        PlaceGeometryDTO.class,
        PlaceStatisticsDTO.class,
        PlaceVisitPatternsDTO.class,
        PlaceVisitDTO.class,
        UpdatePlaceNameRequest.class,
        FavoriteRelationDTO.class,
        TripClassificationDetailsDTO.class,
        TripClassificationDetailsDTO.TripStatistics.class,
        TripClassificationDetailsDTO.ClassificationThresholds.class,
        TripClassificationDetailsDTO.ClassificationStep.class,
        TripClassificationDetailsDTO.ThresholdCheck.class,
        BoatSetupStatusDTO.class,
        BoatSetupStatusDTO.Status.class,
        BoatSetupStatusDTO.DatasetStatus.class,
        BoatSetupStatusDTO.EnvironmentStatus.class,
        BoatSetupStatusDTO.Phase.class,
        BoatSetupStatusDTO.ErrorCode.class,
        BoatSetupStartResponseDTO.class,
        // Location Analytics DTOs
        LocationStatisticsDTO.class,
        LocationAnalyticsMapPlaceDTO.class,
        TopPlaceInLocationDTO.class,
        CitySummaryDTO.class,
        CityDetailsDTO.class,
        CityInCountryDTO.class,
        CountrySummaryDTO.class,
        CountryDetailsDTO.class,
        LocationSearchResultDTO.class,
        MultiUserTimelineDTO.class,
        MultiUserTimelineDTO.UserTimelineDTO.class,
        MultiUserTimelineDTO.MultiUserTimelineDTOBuilder.class,
        MultiUserTimelineDTO.TimelineStats.class,
        PlacePhotoSearchWindowDTO.class,
        LocationLookupFavoriteDTO.class,
        LocationLookupVisitDTO.class,
        LocationLookupMatchDTO.class,
        LocationLookupResponseDTO.class,
        CurrentActivityState.class,
        CurrentStatusDTO.class,
        CurrentStatusDTO.StayStatus.class,
        CurrentStatusDTO.TripStatus.class,
        CurrentStatusDTO.DataGapStatus.class,
        CurrentStatusDTO.LastPointStatus.class,
        CurrentStatusDTO.TodaySummary.class
})
public class StreamingNativeConfig {
}
