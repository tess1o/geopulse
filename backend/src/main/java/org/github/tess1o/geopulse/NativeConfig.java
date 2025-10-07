package org.github.tess1o.geopulse;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.geolatte.geom.codec.PostgisWkbDecoder;
import org.geolatte.geom.codec.PostgisWkbEncoder;
import org.geolatte.geom.codec.PostgisWkbV2Encoder;
import org.github.tess1o.geopulse.ai.model.*;
import org.github.tess1o.geopulse.ai.rest.AIResource;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.model.LoginRequest;
import org.github.tess1o.geopulse.auth.model.TokenRefreshRequest;
import org.github.tess1o.geopulse.auth.oidc.model.OidcSessionStateEntity;
import org.github.tess1o.geopulse.auth.oidc.model.UserOidcConnectionEntity;
import org.github.tess1o.geopulse.digest.model.*;
import org.github.tess1o.geopulse.export.dto.*;
import org.github.tess1o.geopulse.export.model.CreateExportRequest;
import org.github.tess1o.geopulse.export.model.ExportDateRange;
import org.github.tess1o.geopulse.export.model.ExportJobResponse;
import org.github.tess1o.geopulse.export.model.ExportStatus;
import org.github.tess1o.geopulse.favorites.model.*;
import org.github.tess1o.geopulse.friends.invitation.model.FriendInvitationDTO;
import org.github.tess1o.geopulse.friends.invitation.model.FriendInvitationEntity;
import org.github.tess1o.geopulse.friends.invitation.model.InvitationStatus;
import org.github.tess1o.geopulse.friends.model.FriendInfoDTO;
import org.github.tess1o.geopulse.friends.model.UserFriendEntity;
import org.github.tess1o.geopulse.geocoding.client.GoogleMapsRestClient;
import org.github.tess1o.geopulse.geocoding.client.MapboxRestClient;
import org.github.tess1o.geopulse.geocoding.client.NominatimRestClient;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfig;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.geocoding.model.googlemaps.*;
import org.github.tess1o.geopulse.geocoding.model.mapbox.*;
import org.github.tess1o.geopulse.geocoding.model.nominatim.NominatimAddress;
import org.github.tess1o.geopulse.geocoding.model.nominatim.NominatimAddressFormatter;
import org.github.tess1o.geopulse.geocoding.model.nominatim.NominatimResponse;
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
import org.github.tess1o.geopulse.gpssource.model.*;
import org.github.tess1o.geopulse.immich.model.*;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportJobResponse;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.model.ImportStatus;
import org.github.tess1o.geopulse.insight.model.*;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.sharing.model.*;
import org.github.tess1o.geopulse.statistics.model.*;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.LocationSource;
import org.github.tess1o.geopulse.streaming.model.domain.ProcessorMode;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEventType;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineDataGapDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineTripDTO;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.user.model.*;
import org.locationtech.jts.geom.*;


@RegisterForReflection(
        targets = {
                // Your entities
                UserEntity.class,
                GpsPointEntity.class,
                FriendInvitationEntity.class,
                UserFriendEntity.class,
                ReverseGeocodingLocationEntity.class,
                UserFriendEntity.class,
                TimelineStayEntity.class,
                TimelineTripEntity.class,
                TimelineDataGapEntity.class,
                SharedLinkEntity.class,
                UserBadgeEntity.class,
                UserOidcConnectionEntity.class,
                OidcSessionStateEntity.class,
                GpsSourceConfigEntity.class,

                // JSON types
                TimelinePreferences.class,
                ImmichPreferences.class,

                // Enums
                ChartGroupMode.class,
                ExportStatus.class,
                FavoriteLocationType.class,
                GoogleTimelineRecordType.class,
                GpsSourceType.class,
                ImportStatus.class,
                InvitationStatus.class,
                LocationSource.class,
                ProcessorMode.class,
                StayGroupBy.class,
                TimelineEventType.class,
                TimelineStatus.class,
                TripGroupBy.class,
                TripType.class,


                // Hibernate Spatial (JTS classes you use)
                Coordinate.class,
                Envelope.class,
                Geometry.class,
                GeometryFactory.class,
                LineString.class,
                Point.class,
                Polygon.class,
                PostgisWkbDecoder.class,
                PostgisWkbEncoder.class,
                PostgisWkbV2Encoder.class,

                // DTOs
                AuthResponse.class,
                ApiResponse.class,
                UpdateShareLinkDto.class,
                UpdateUserPasswordRequest.class,
                UpdateProfileRequest.class,
                UpdateTimelinePreferencesRequest.class,
                UpdateGpsSourceConfigStatusDto.class,
                UserRegistrationRequest.class,
                LoginRequest.class,
                TokenRefreshRequest.class,
                UserAISettings.class,
                AIMovementTimelineDTO.class,
                AIStayStatsDTO.class,
                AITimelineStayDTO.class,
                AITimelineTripDTO.class,
                AITripStatsDTO.class,
                AIResource.OpenAIConnectionTestRequest.class,
                AIResource.ChatRequest.class,
                AIResource.ChatResponse.class,
                TimeDigest.class,
                CreateExportRequest.class,
                RawGpsDataDto.class,
                DataGapsDataDto.class,
                FavoritesDataDto.class,
                LocationSourcesDataDto.class,
                ReverseGeocodingDataDto.class,
                TimelineDataGapDTO.class,
                UserInfoDataDto.class,
                TimelineDataDto.StayDto.class,
                TimelineDataDto.TripDto.class,
                TimelineDataDto.DataGapDto.class,
                ExportJobResponse.class,
                ExportMetadataDto.class,
                FavoriteLocationsDto.class,
                FriendInfoDTO.class,
                FavoritesDataDto.class,
                FavoritePointDto.class,
                FavoriteAreaDto.class,
                AddAreaToFavoritesDto.class,
                AddPointToFavoritesDto.class,
                EditFavoriteDto.class,
                FavoriteLocationsDto.class,
                FriendInvitationDTO.class,
                GpsPointPathPointDTO.class,
                GpsPointSummaryDTO.class,
                GpsPointPathDTO.class,
                BulkDeleteGpsPointsDto.class,
                EditGpsPointDto.class,
                GpsPointDTO.class,
                GpsPointPageDTO.class,
                GpsPointPaginationDTO.class,

                MovementTimelineDTO.class,
                TimelineStayLocationDTO.class,
                TimelineTripDTO.class,
                TimelineDataGapDTO.class,
                TimelineConfig.class,
                UserResponse.class,
                UserStatistics.class,
                MostActiveDayDto.class,
                TopPlace.class,
                RoutesStatistics.class,
                BarChartData.class,
                MostCommonRoute.class,
                Achievements.class,
                Badge.class,
                City.class,
                Country.class,
                DistanceTraveled.class,
                GeographicInsights.class,
                JourneyInsights.class,
                TimePatterns.class,
                ImportJob.class,
                ExportDateRange.class,
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
                ImportJobResponse.class,
                ImportOptions.class,
                ImmichSearchResponse.class,
                ImmichSearchResponse.ImmichSearchAssets.class,
                ImmichAsset.class,
                ImmichPhotoDto.class,
                ImmichPhotoSearchRequest.class,
                ImmichPhotoSearchResponse.class,
                UpdateImmichConfigRequest.class,
                CreateGpsSourceConfigDto.class,
                GpsSourceConfigDTO.class,
                UpdateGpsSourceConfigDto.class,

                AccessTokenResponse.class,
                CreateShareLinkRequest.class,
                CreateShareLinkResponse.class,
                LocationHistoryResponse.class,
                SharedLinkDto.class,
                SharedLinksDto.class,
                SharedLocationInfo.class,
                ShareLinkResponse.class,
                UpdateShareLinkDto.class,
                VerifyPasswordRequest.class,

                ActivityChartData.class,
                DigestHighlight.class,
                DigestMetrics.class,
                Milestone.class,
                PeriodComparison.class,
                PeriodInfo.class,
                TimeDigest.class,

                FormattableGeocodingResult.class,
                SimpleFormattableResult.class,
                GoogleMapsAddressComponent.class,
                GoogleMapsGeometry.class,
                GoogleMapsLocation.class,
                GoogleMapsPlusCode.class,
                GoogleMapsResponse.class,
                GoogleMapsResult.class,
                GoogleMapsViewport.class,

                MapboxContext.class,
                MapboxFeature.class,
                MapboxGeometry.class,
                MapboxProperties.class,
                MapboxResponse.class,

                NominatimAddress.class,
                NominatimAddressFormatter.class,
                NominatimResponse.class,

                GeocodingConfig.class,
                MapboxRestClient.class,
                GoogleMapsRestClient.class,
                NominatimRestClient.class

        }
)
public class NativeConfig {
}
