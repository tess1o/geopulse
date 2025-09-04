package org.github.tess1o.geopulse.export.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.export.dto.*;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class ExportDataMapper {

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
                .latitude(stay.getLatitude())
                .longitude(stay.getLongitude())
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
                .startLatitude(trip.getStartLatitude())
                .startLongitude(trip.getStartLongitude())
                .endLatitude(trip.getEndLatitude())
                .endLongitude(trip.getEndLongitude())
                .distance(trip.getDistanceMeters()) // Already in meters
                .duration(trip.getTripDuration()) // Duration in seconds
                .transportMode(trip.getMovementType());

        // Convert LineString path to coordinate array
        if (trip.getPath() != null) {
            List<List<Double>> pathCoordinates = new ArrayList<>();
            Coordinate[] coordinates = trip.getPath().getCoordinates();
            for (Coordinate coordinate : coordinates) {
                pathCoordinates.add(Arrays.asList(coordinate.x, coordinate.y)); // [longitude, latitude]
            }
            builder.path(pathCoordinates);
        }

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

    public TimelineDataDto toTimelineDataDto(List<TimelineStayEntity> stays, List<TimelineTripEntity> trips, ExportJob job) {
        return toTimelineDataDto(stays, trips, new ArrayList<>(), job);
    }

    public TimelineDataDto toTimelineDataDto(List<TimelineStayEntity> stays, List<TimelineTripEntity> trips, List<TimelineDataGapEntity> dataGaps, ExportJob job) {
        List<TimelineDataDto.StayDto> stayDtos = stays.stream()
                .map(this::toStayDto)
                .collect(Collectors.toList());

        List<TimelineDataDto.TripDto> tripDtos = trips.stream()
                .map(this::toTripDto)
                .collect(Collectors.toList());

        List<TimelineDataDto.DataGapDto> dataGapDtos = dataGaps.stream()
                .map(this::toDataGapDto)
                .collect(Collectors.toList());

        return TimelineDataDto.builder()
                .dataType("timeline")
                .exportDate(Instant.now())
                .startDate(job.getDateRange().getStartDate())
                .endDate(job.getDateRange().getEndDate())
                .stays(stayDtos)
                .trips(tripDtos)
                .dataGaps(dataGapDtos)
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
                .active(source.isActive())
                .connectionType(source.getConnectionType() != null ? source.getConnectionType().name() : "HTTP")
                .build();
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
}