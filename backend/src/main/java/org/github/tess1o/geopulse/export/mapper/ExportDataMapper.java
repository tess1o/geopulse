package org.github.tess1o.geopulse.export.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.ai.service.AIEncryptionService;
import org.github.tess1o.geopulse.export.dto.*;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.NotificationTemplateEntity;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.friends.model.UserFriendEntity;
import org.github.tess1o.geopulse.friends.model.UserFriendPermissionEntity;
import org.github.tess1o.geopulse.mapmatching.model.TimelineTripPathMatchEntity;
import org.github.tess1o.geopulse.notes.model.TimelineNoteEntity;
import org.github.tess1o.geopulse.periods.model.entity.PeriodTagEntity;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapStayOverrideEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripMovementOverrideEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripCollaboratorEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemEntity;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.weather.model.WeatherSampleEntity;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class ExportDataMapper {

    @Inject
    AIEncryptionService encryptionService;

    public ExportMetadataDto toMetadataDto(ExportJob job) {
        return ExportMetadataDto.builder()
                .exportJobId(job.getJobId())
                .userId(job.getUserId())
                .exportDate(Instant.now())
                .dataTypes(job.getDataTypes())
                .startDate(job.getDateRange().getStartDate())
                .endDate(job.getDateRange().getEndDate())
                .format(job.getFormat())
                .version(ExportImportConstants.Versions.CURRENT)
                .build();
    }

    public RawGpsDataDto.GpsPointDto toGpsPointDto(GpsPointEntity point) {
        return RawGpsDataDto.GpsPointDto.builder()
                .id(point.getId())
                .timestamp(point.getTimestamp())
                .latitude(point.getLatitude())
                .longitude(point.getLongitude())
                .accuracy(point.getAccuracy())
                .altitude(point.getAltitude())
                .speed(point.getVelocity())
                .battery(point.getBattery())
                .deviceId(point.getDeviceId())
                .source(point.getSourceType() != null ? point.getSourceType().name() : "UNKNOWN")
                .build();
    }

    public RawGpsDataDto toRawGpsDataDto(List<GpsPointEntity> points, ExportJob job) {
        List<RawGpsDataDto.GpsPointDto> pointDtos = points.stream()
                .map(this::toGpsPointDto)
                .collect(Collectors.toList());

        return RawGpsDataDto.builder()
                .dataType("rawGps")
                .exportDate(Instant.now())
                .startDate(job.getDateRange().getStartDate())
                .endDate(job.getDateRange().getEndDate())
                .points(pointDtos)
                .build();
    }

    public TimelineDataDto.StayDto toStayDto(TimelineStayEntity stay) {
        return TimelineDataDto.StayDto.builder()
                .id(stay.getId())
                .timestamp(stay.getTimestamp())
                .endTime(stay.getTimestamp().plusSeconds(stay.getStayDuration()))
                .longitude(stay.getLocation().getX())
                .latitude(stay.getLocation().getY())
                .duration(stay.getStayDuration()) // Duration in seconds
                .address(stay.getLocationName())
                .favoriteId(stay.getFavoriteLocation() != null ? stay.getFavoriteLocation().getId() : null)
                .geocodingId(stay.getGeocodingLocation() != null ? stay.getGeocodingLocation().getId() : null)
                .build();
    }

    public TimelineDataDto.TripDto toTripDto(TimelineTripEntity trip) {
        TimelineDataDto.TripDto.TripDtoBuilder builder = TimelineDataDto.TripDto.builder()
                .id(trip.getId())
                .timestamp(trip.getTimestamp())
                .endTime(trip.getTimestamp().plusSeconds(trip.getTripDuration()))
                .startLongitude(trip.getStartPoint().getX())
                .startLatitude(trip.getStartPoint().getY())
                .endLongitude(trip.getEndPoint().getX())
                .endLatitude(trip.getEndPoint().getY())
                .distance(trip.getDistanceMeters()) // Already in meters
                .duration(trip.getTripDuration()) // Duration in seconds
                .transportMode(trip.getMovementType())
                .movementTypeSource(trip.getMovementTypeSource() != null ? trip.getMovementTypeSource().name() : null)
                .avgGpsSpeed(trip.getAvgGpsSpeed())
                .maxGpsSpeed(trip.getMaxGpsSpeed())
                .speedVariance(trip.getSpeedVariance())
                .lowAccuracyPointsCount(trip.getLowAccuracyPointsCount())
                .waterDistanceMeters(trip.getWaterDistanceMeters())
                .waterDistanceRatio(trip.getWaterDistanceRatio())
                .longestWaterSegmentMeters(trip.getLongestWaterSegmentMeters())
                .waterSampleCount(trip.getWaterSampleCount())
                .waterEvidenceAvailable(trip.getWaterEvidenceAvailable());

        return builder.build();
    }

    public TimelineDataDto.DataGapDto toDataGapDto(TimelineDataGapEntity dataGap) {
        return TimelineDataDto.DataGapDto.builder()
                .id(dataGap.getId())
                .startTime(dataGap.getStartTime())
                .endTime(dataGap.getEndTime())
                .durationSeconds(dataGap.getDurationSeconds())
                .createdAt(dataGap.getCreatedAt())
                .build();
    }

    public FavoritesDataDto.FavoritePointDto toFavoritePointDto(FavoritesEntity favorite) {
        if (favorite.getType() != FavoriteLocationType.POINT || !(favorite.getGeometry() instanceof Point point)) {
            return null;
        }

        return FavoritesDataDto.FavoritePointDto.builder()
                .id(favorite.getId())
                .name(favorite.getName())
                .city(favorite.getCity())
                .country(favorite.getCountry())
                .latitude(point.getY())
                .longitude(point.getX())
                .build();
    }

    public FavoritesDataDto.FavoriteAreaDto toFavoriteAreaDto(FavoritesEntity favorite) {
        if (favorite.getType() != FavoriteLocationType.AREA) {
            return null;
        }

        Envelope env = favorite.getGeometry().getEnvelopeInternal();
        return FavoritesDataDto.FavoriteAreaDto.builder()
                .id(favorite.getId())
                .name(favorite.getName())
                .city(favorite.getCity())
                .country(favorite.getCountry())
                .northEastLatitude(env.getMaxY())
                .northEastLongitude(env.getMaxX())
                .southWestLatitude(env.getMinY())
                .southWestLongitude(env.getMinX())
                .build();
    }

    public FavoritesDataDto toFavoritesDataDto(List<FavoritesEntity> favorites) {
        List<FavoritesDataDto.FavoritePointDto> points = favorites.stream()
                .map(this::toFavoritePointDto)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        List<FavoritesDataDto.FavoriteAreaDto> areas = favorites.stream()
                .map(this::toFavoriteAreaDto)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        return FavoritesDataDto.builder()
                .dataType("favorites")
                .exportDate(Instant.now())
                .points(points)
                .areas(areas)
                .build();
    }

    public UserInfoDataDto.UserDto toUserDto(UserEntity user) {
        return UserInfoDataDto.UserDto.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .createdAt(user.getCreatedAt())
                .preferences(user.getTimelinePreferences())
                .build();
    }

    public UserInfoDataDto toUserInfoDataDto(UserEntity user) {
        return UserInfoDataDto.builder()
                .dataType("userInfo")
                .exportDate(Instant.now())
                .user(toUserDto(user))
                .build();
    }

    public LocationSourcesDataDto.SourceDto toSourceDto(GpsSourceConfigEntity source) {
        return LocationSourcesDataDto.SourceDto.builder()
                .id(source.getId())
                .type(source.getSourceType() != null ? source.getSourceType().name() : "UNKNOWN")
                .username(source.getUsername())
                .passwordHash(source.getPasswordHash())
                .token(source.getToken())
                .deviceId(source.getDeviceId())
                .payloadEncryptionSecret(decryptPayloadEncryptionSecret(source))
                .active(source.isActive())
                .connectionType(source.getConnectionType() != null ? source.getConnectionType().name() : "HTTP")
                .filterInaccurateData(source.isFilterInaccurateData())
                .maxAllowedAccuracy(source.getMaxAllowedAccuracy())
                .maxAllowedSpeed(source.getMaxAllowedSpeed())
                .enableDuplicateDetection(source.isEnableDuplicateDetection())
                .duplicateDetectionThresholdMinutes(source.getDuplicateDetectionThresholdMinutes())
                .build();
    }

    private String decryptPayloadEncryptionSecret(GpsSourceConfigEntity source) {
        if (source.getPayloadEncryptionSecretEncrypted() == null || source.getPayloadEncryptionSecretEncrypted().isBlank()) {
            return null;
        }
        return encryptionService.decrypt(source.getPayloadEncryptionSecretEncrypted(), source.getPayloadEncryptionSecretKeyId());
    }

    public LocationSourcesDataDto toLocationSourcesDataDto(List<GpsSourceConfigEntity> sources) {
        List<LocationSourcesDataDto.SourceDto> sourceDtos = sources.stream()
                .map(this::toSourceDto)
                .collect(Collectors.toList());

        return LocationSourcesDataDto.builder()
                .dataType("locationSources")
                .exportDate(Instant.now())
                .sources(sourceDtos)
                .build();
    }

    public FriendsDataDto.FriendDto toFriendDto(UserFriendEntity friend) {
        return FriendsDataDto.FriendDto.builder()
                .id(friend.getId())
                .userId(friend.getUser().getId())
                .friendId(friend.getFriend().getId())
                .userEmail(friend.getUser().getEmail())
                .friendEmail(friend.getFriend().getEmail())
                .createdAt(friend.getCreatedAt())
                .build();
    }

    public FriendsDataDto toFriendsDataDto(List<UserFriendEntity> friends) {
        return FriendsDataDto.builder()
                .dataType("friends")
                .exportDate(Instant.now())
                .friends(friends.stream().map(this::toFriendDto).collect(Collectors.toList()))
                .build();
    }

    public FriendPermissionsDataDto.FriendPermissionDto toFriendPermissionDto(UserFriendPermissionEntity permission) {
        return FriendPermissionsDataDto.FriendPermissionDto.builder()
                .id(permission.getId())
                .userId(permission.getUser().getId())
                .friendId(permission.getFriend().getId())
                .userEmail(permission.getUser().getEmail())
                .friendEmail(permission.getFriend().getEmail())
                .shareTimeline(permission.getShareTimeline())
                .shareLiveLocation(permission.getShareLiveLocation())
                .createdAt(permission.getCreatedAt())
                .updatedAt(permission.getUpdatedAt())
                .build();
    }

    public FriendPermissionsDataDto toFriendPermissionsDataDto(List<UserFriendPermissionEntity> permissions) {
        return FriendPermissionsDataDto.builder()
                .dataType("friendPermissions")
                .exportDate(Instant.now())
                .permissions(permissions.stream().map(this::toFriendPermissionDto).collect(Collectors.toList()))
                .build();
    }

    public ReverseGeocodingDataDto.ReverseGeocodingLocationDto toReverseGeocodingLocationDto(ReverseGeocodingLocationEntity location) {
        ReverseGeocodingDataDto.ReverseGeocodingLocationDto.ReverseGeocodingLocationDtoBuilder builder =
                ReverseGeocodingDataDto.ReverseGeocodingLocationDto.builder()
                        .id(location.getId())
                        .displayName(location.getDisplayName())
                        .providerName(location.getProviderName())
                        .createdAt(location.getCreatedAt())
                        .lastAccessedAt(location.getLastAccessedAt())
                        .city(location.getCity())
                        .country(location.getCountry());

        // Handle request coordinates
        if (location.getRequestCoordinates() != null) {
            builder.requestLatitude(location.getRequestCoordinates().getY())
                    .requestLongitude(location.getRequestCoordinates().getX());
        }

        // Handle result coordinates
        if (location.getResultCoordinates() != null) {
            builder.resultLatitude(location.getResultCoordinates().getY())
                    .resultLongitude(location.getResultCoordinates().getX());
        }

        // Handle bounding box
        if (location.getBoundingBox() != null) {
            Envelope env = location.getBoundingBox().getEnvelopeInternal();
            builder.boundingBoxNorthEastLatitude(env.getMaxY())
                    .boundingBoxNorthEastLongitude(env.getMaxX())
                    .boundingBoxSouthWestLatitude(env.getMinY())
                    .boundingBoxSouthWestLongitude(env.getMinX());
        }

        return builder.build();
    }

    public ReverseGeocodingDataDto toReverseGeocodingDataDto(List<ReverseGeocodingLocationEntity> locations) {
        List<ReverseGeocodingDataDto.ReverseGeocodingLocationDto> locationDtos = locations.stream()
                .map(this::toReverseGeocodingLocationDto)
                .collect(Collectors.toList());

        return ReverseGeocodingDataDto.builder()
                .dataType("reverseGeocodingLocation")
                .exportDate(Instant.now())
                .locations(locationDtos)
                .build();
    }

    public PeriodTagsDataDto.PeriodTagDto toPeriodTagDto(PeriodTagEntity tag) {
        return PeriodTagsDataDto.PeriodTagDto.builder()
                .id(tag.getId())
                .tagName(tag.getTagName())
                .startTime(tag.getStartTime())
                .endTime(tag.getEndTime())
                .source(tag.getSource())
                .active(tag.getIsActive())
                .color(tag.getColor())
                .showAsPreset(tag.getShowAsPreset())
                .createdAt(tag.getCreatedAt())
                .updatedAt(tag.getUpdatedAt())
                .build();
    }

    public PeriodTagsDataDto toPeriodTagsDataDto(List<PeriodTagEntity> tags, ExportJob job) {
        return PeriodTagsDataDto.builder()
                .dataType(ExportImportConstants.DataTypes.PERIOD_TAGS)
                .exportDate(Instant.now())
                .startDate(job.getDateRange().getStartDate())
                .endDate(job.getDateRange().getEndDate())
                .periodTags(tags.stream().map(this::toPeriodTagDto).collect(Collectors.toList()))
                .build();
    }

    public TimelineOverridesDataDto toTimelineOverridesDataDto(
            List<TimelineTripMovementOverrideEntity> tripOverrides,
            List<TimelineDataGapStayOverrideEntity> gapOverrides) {
        return TimelineOverridesDataDto.builder()
                .dataType(ExportImportConstants.DataTypes.TIMELINE_OVERRIDES)
                .exportDate(Instant.now())
                .tripMovementOverrides(tripOverrides.stream().map(this::toTripMovementOverrideDto).collect(Collectors.toList()))
                .dataGapStayOverrides(gapOverrides.stream().map(this::toDataGapStayOverrideDto).collect(Collectors.toList()))
                .build();
    }

    private TimelineOverridesDataDto.TripMovementOverrideDto toTripMovementOverrideDto(TimelineTripMovementOverrideEntity override) {
        return TimelineOverridesDataDto.TripMovementOverrideDto.builder()
                .id(override.getId())
                .tripId(override.getTrip() != null ? override.getTrip().getId() : null)
                .movementType(override.getMovementType())
                .sourceTripTimestamp(override.getSourceTripTimestamp())
                .sourceTripDurationSeconds(override.getSourceTripDurationSeconds())
                .sourceDistanceMeters(override.getSourceDistanceMeters())
                .sourceStartLatitude(override.getSourceStartLatitude())
                .sourceStartLongitude(override.getSourceStartLongitude())
                .sourceEndLatitude(override.getSourceEndLatitude())
                .sourceEndLongitude(override.getSourceEndLongitude())
                .createdAt(override.getCreatedAt())
                .updatedAt(override.getUpdatedAt())
                .build();
    }

    private TimelineOverridesDataDto.DataGapStayOverrideDto toDataGapStayOverrideDto(TimelineDataGapStayOverrideEntity override) {
        return TimelineOverridesDataDto.DataGapStayOverrideDto.builder()
                .id(override.getId())
                .dataGapId(override.getDataGap() != null ? override.getDataGap().getId() : null)
                .stayId(override.getStay() != null ? override.getStay().getId() : null)
                .locationStrategy(override.getLocationStrategy() != null ? override.getLocationStrategy().name() : null)
                .selectedFavoriteId(override.getSelectedFavoriteId())
                .selectedGeocodingId(override.getSelectedGeocodingId())
                .selectedLatitude(override.getSelectedLatitude())
                .selectedLongitude(override.getSelectedLongitude())
                .selectedLocationName(override.getSelectedLocationName())
                .sourceGapStartTime(override.getSourceGapStartTime())
                .sourceGapEndTime(override.getSourceGapEndTime())
                .sourceGapDurationSeconds(override.getSourceGapDurationSeconds())
                .sourceBeforeLatitude(override.getSourceBeforeLatitude())
                .sourceBeforeLongitude(override.getSourceBeforeLongitude())
                .sourceAfterLatitude(override.getSourceAfterLatitude())
                .sourceAfterLongitude(override.getSourceAfterLongitude())
                .createdAt(override.getCreatedAt())
                .updatedAt(override.getUpdatedAt())
                .build();
    }

    public TripWorkspaceDataDto toTripWorkspaceDataDto(
            List<TripEntity> trips,
            Map<Long, List<TripPlanItemEntity>> planItemsByTripId,
            Map<Long, List<TripCollaboratorEntity>> collaboratorsByTripId) {
        return TripWorkspaceDataDto.builder()
                .dataType(ExportImportConstants.DataTypes.TRIP_WORKSPACE)
                .exportDate(Instant.now())
                .trips(trips.stream()
                        .map(trip -> toTripWorkspaceDto(
                                trip,
                                planItemsByTripId.getOrDefault(trip.getId(), List.of()),
                                collaboratorsByTripId.getOrDefault(trip.getId(), List.of())))
                        .collect(Collectors.toList()))
                .build();
    }

    private TripWorkspaceDataDto.TripDto toTripWorkspaceDto(
            TripEntity trip,
            List<TripPlanItemEntity> planItems,
            List<TripCollaboratorEntity> collaborators) {
        return TripWorkspaceDataDto.TripDto.builder()
                .id(trip.getId())
                .periodTagId(trip.getPeriodTag() != null ? trip.getPeriodTag().getId() : null)
                .name(trip.getName())
                .startTime(trip.getStartTime())
                .endTime(trip.getEndTime())
                .status(trip.getStatus() != null ? trip.getStatus().name() : null)
                .color(trip.getColor())
                .notes(trip.getNotes())
                .createdAt(trip.getCreatedAt())
                .updatedAt(trip.getUpdatedAt())
                .planItems(planItems.stream()
                        .map(this::toTripPlanItemDto)
                        .collect(Collectors.toList()))
                .collaborators(collaborators.stream()
                        .map(collaborator -> TripWorkspaceDataDto.TripCollaboratorDto.builder()
                                .userId(collaborator.getCollaborator().getId())
                                .email(collaborator.getCollaborator().getEmail())
                                .accessRole(collaborator.getAccessRole() != null ? collaborator.getAccessRole().name() : null)
                                .createdAt(collaborator.getCreatedAt())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    private TripWorkspaceDataDto.TripPlanItemDto toTripPlanItemDto(TripPlanItemEntity item) {
        return TripWorkspaceDataDto.TripPlanItemDto.builder()
                .id(item.getId())
                .title(item.getTitle())
                .notes(item.getNotes())
                .latitude(item.getLatitude())
                .longitude(item.getLongitude())
                .plannedDay(item.getPlannedDay())
                .priority(item.getPriority() != null ? item.getPriority().name() : null)
                .orderIndex(item.getOrderIndex())
                .visited(item.getIsVisited())
                .visitConfidence(item.getVisitConfidence())
                .visitSource(item.getVisitSource() != null ? item.getVisitSource().name() : null)
                .visitedAt(item.getVisitedAt())
                .manualOverrideState(item.getManualOverrideState() != null ? item.getManualOverrideState().name() : null)
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    public NotificationTemplatesDataDto toNotificationTemplatesDataDto(List<NotificationTemplateEntity> templates) {
        return NotificationTemplatesDataDto.builder()
                .dataType(ExportImportConstants.DataTypes.NOTIFICATION_TEMPLATES)
                .exportDate(Instant.now())
                .templates(templates.stream()
                        .map(this::toNotificationTemplateDto)
                        .collect(Collectors.toList()))
                .build();
    }

    private NotificationTemplatesDataDto.NotificationTemplateDto toNotificationTemplateDto(NotificationTemplateEntity template) {
        return NotificationTemplatesDataDto.NotificationTemplateDto.builder()
                .id(template.getId())
                .name(template.getName())
                .destination(template.getDestination())
                .externalRoutingMode(template.getExternalRoutingMode() != null ? template.getExternalRoutingMode().name() : null)
                .appriseConfigKey(template.getAppriseConfigKey())
                .appriseTag(template.getAppriseTag())
                .titleTemplate(template.getTitleTemplate())
                .bodyTemplate(template.getBodyTemplate())
                .defaultForEnter(template.getDefaultForEnter())
                .defaultForLeave(template.getDefaultForLeave())
                .enabled(template.getEnabled())
                .sendInApp(template.getSendInApp())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }

    public GeofencingDataDto toGeofencingDataDto(List<GeofenceRuleEntity> rules) {
        return GeofencingDataDto.builder()
                .dataType(ExportImportConstants.DataTypes.GEOFENCING)
                .exportDate(Instant.now())
                .rules(rules.stream().map(this::toGeofenceRuleDto).collect(Collectors.toList()))
                .build();
    }

    private GeofencingDataDto.GeofenceRuleDto toGeofenceRuleDto(GeofenceRuleEntity rule) {
        return GeofencingDataDto.GeofenceRuleDto.builder()
                .id(rule.getId())
                .name(rule.getName())
                .northEastLat(rule.getNorthEastLat())
                .northEastLon(rule.getNorthEastLon())
                .southWestLat(rule.getSouthWestLat())
                .southWestLon(rule.getSouthWestLon())
                .monitorEnter(rule.getMonitorEnter())
                .monitorLeave(rule.getMonitorLeave())
                .cooldownSeconds(rule.getCooldownSeconds())
                .enterTemplateId(rule.getEnterTemplate() != null ? rule.getEnterTemplate().getId() : null)
                .leaveTemplateId(rule.getLeaveTemplate() != null ? rule.getLeaveTemplate().getId() : null)
                .status(rule.getStatus() != null ? rule.getStatus().name() : null)
                .subjects(rule.getSubjectAssignments().stream()
                        .map(subject -> GeofencingDataDto.SubjectDto.builder()
                                .userId(subject.getSubjectUser().getId())
                                .email(subject.getSubjectUser().getEmail())
                                .createdAt(subject.getCreatedAt())
                                .build())
                        .collect(Collectors.toList()))
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }

    public NotesDataDto toNotesDataDto(List<TimelineNoteEntity> notes, ExportJob job) {
        return NotesDataDto.builder()
                .dataType(ExportImportConstants.DataTypes.NOTES)
                .exportDate(Instant.now())
                .startDate(job.getDateRange().getStartDate())
                .endDate(job.getDateRange().getEndDate())
                .notes(notes.stream().map(this::toNoteDto).collect(Collectors.toList()))
                .build();
    }

    private NotesDataDto.NoteDto toNoteDto(TimelineNoteEntity note) {
        return NotesDataDto.NoteDto.builder()
                .id(note.getId())
                .title(note.getTitle())
                .contentMarkdown(note.getContentMarkdown())
                .snippet(note.getSnippet())
                .eventTime(note.getEventTime())
                .latitude(note.getLocation() != null ? note.getLocation().getY() : null)
                .longitude(note.getLocation() != null ? note.getLocation().getX() : null)
                .locationSource(note.getLocationSource() != null ? note.getLocationSource().name() : null)
                .anchorType(note.getAnchorType() != null ? note.getAnchorType().name() : null)
                .stayId(note.getStay() != null ? note.getStay().getId() : null)
                .tripId(note.getTrip() != null ? note.getTrip().getId() : null)
                .sourceItemStartTime(note.getSourceItemStartTime())
                .sourceItemDurationSeconds(note.getSourceItemDurationSeconds())
                .sourceStartLatitude(note.getSourceStartLatitude())
                .sourceStartLongitude(note.getSourceStartLongitude())
                .sourceEndLatitude(note.getSourceEndLatitude())
                .sourceEndLongitude(note.getSourceEndLongitude())
                .sourceDistanceMeters(note.getSourceDistanceMeters())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .deletedAt(note.getDeletedAt())
                .build();
    }

    public WeatherSamplesDataDto toWeatherSamplesDataDto(List<WeatherSampleEntity> samples, ExportJob job) {
        return WeatherSamplesDataDto.builder()
                .dataType(ExportImportConstants.DataTypes.WEATHER_SAMPLES)
                .exportDate(Instant.now())
                .startDate(job.getDateRange().getStartDate())
                .endDate(job.getDateRange().getEndDate())
                .samples(samples.stream().map(this::toWeatherSampleDto).collect(Collectors.toList()))
                .build();
    }

    private WeatherSamplesDataDto.WeatherSampleDto toWeatherSampleDto(WeatherSampleEntity sample) {
        return WeatherSamplesDataDto.WeatherSampleDto.builder()
                .id(sample.getId())
                .provider(sample.getProvider())
                .source(sample.getSource() != null ? sample.getSource().name() : null)
                .requestedLatitude(sample.getRequestedLatitude())
                .requestedLongitude(sample.getRequestedLongitude())
                .providerLatitude(sample.getProviderLatitude())
                .providerLongitude(sample.getProviderLongitude())
                .latitudeBucket(sample.getLatitudeBucket())
                .longitudeBucket(sample.getLongitudeBucket())
                .observedAt(sample.getObservedAt())
                .fetchedAt(sample.getFetchedAt())
                .timezone(sample.getTimezone())
                .weatherCode(sample.getWeatherCode())
                .temperature(sample.getTemperature())
                .apparentTemperature(sample.getApparentTemperature())
                .humidity(sample.getHumidity())
                .precipitation(sample.getPrecipitation())
                .rain(sample.getRain())
                .snowfall(sample.getSnowfall())
                .cloudCover(sample.getCloudCover())
                .windSpeed(sample.getWindSpeed())
                .windGust(sample.getWindGust())
                .windDirection(sample.getWindDirection())
                .pressure(sample.getPressure())
                .rawData(sample.getRawData())
                .createdAt(sample.getCreatedAt())
                .updatedAt(sample.getUpdatedAt())
                .build();
    }

    public MapMatchingDataDto toMapMatchingDataDto(List<TimelineTripPathMatchEntity> pathMatches, ExportJob job) {
        return MapMatchingDataDto.builder()
                .dataType(ExportImportConstants.DataTypes.MAP_MATCHING)
                .exportDate(Instant.now())
                .startDate(job.getDateRange().getStartDate())
                .endDate(job.getDateRange().getEndDate())
                .pathMatches(pathMatches.stream().map(this::toPathMatchDto).collect(Collectors.toList()))
                .build();
    }

    private MapMatchingDataDto.PathMatchDto toPathMatchDto(TimelineTripPathMatchEntity pathMatch) {
        return MapMatchingDataDto.PathMatchDto.builder()
                .id(pathMatch.getId())
                .tripId(pathMatch.getTrip() == null ? null : pathMatch.getTrip().getId())
                .tripTimestamp(pathMatch.getTrip() == null ? null : pathMatch.getTrip().getTimestamp())
                .provider(pathMatch.getProvider())
                .profile(pathMatch.getProfile())
                .configHash(pathMatch.getConfigHash())
                .inputHash(pathMatch.getInputHash())
                .status(pathMatch.getStatus() == null ? null : pathMatch.getStatus().name())
                .attempts(pathMatch.getAttempts())
                .nextAttemptAt(pathMatch.getNextAttemptAt())
                .lastAttemptAt(pathMatch.getLastAttemptAt())
                .completedAt(pathMatch.getCompletedAt())
                .matchedSegmentsJson(pathMatch.getMatchedSegmentsJson())
                .source(pathMatch.getSource())
                .priority(pathMatch.getPriority())
                .createdAt(pathMatch.getCreatedAt())
                .updatedAt(pathMatch.getUpdatedAt())
                .build();
    }
}
