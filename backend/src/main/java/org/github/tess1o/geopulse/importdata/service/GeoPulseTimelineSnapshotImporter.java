package org.github.tess1o.geopulse.importdata.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.dto.*;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.streaming.model.domain.LocationSource;
import org.github.tess1o.geopulse.streaming.model.shared.MovementTypeSource;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.IMPORT_JSON;
import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.requireImportingUser;
import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.resolveMappedId;
import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.durationSeconds;
import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.defaultInstant;
import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.parseEnum;
import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.emptyIfNull;
import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.shouldSkipDueToDateFilter;

/**
 * GeoPulse full-backup restore of stored timeline rows (stays, trips, data gaps) with their original ids, and the
 * cleanup that runs before such a restore.
 */
@ApplicationScoped
@Slf4j
public class GeoPulseTimelineSnapshotImporter {

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    @Transactional
    public void importTimelineDataSnapshot(byte[] content, ImportJob job, ImportReferenceMaps referenceMaps) throws IOException {
        TimelineDataDto timelineData = IMPORT_JSON.readValue(content, TimelineDataDto.class);
        UserEntity user = requireImportingUser(userRepository, job);

        int stays = importTimelineStaysSnapshot(timelineData, user, job, referenceMaps);
        int trips = importTimelineTripsSnapshot(timelineData, user, job, referenceMaps);
        int dataGaps = importTimelineDataGapsSnapshot(timelineData, user, job, referenceMaps);
        entityManager.flush();

        log.info("Imported timeline snapshot for user {}: {} stays, {} trips, {} data gaps",
                job.getUserId(), stays, trips, dataGaps);
    }

    private int importTimelineStaysSnapshot(TimelineDataDto timelineData,
                                            UserEntity user,
                                            ImportJob job,
                                            ImportReferenceMaps referenceMaps) {
        int imported = 0;
        for (TimelineDataDto.StayDto dto : emptyIfNull(timelineData.getStays())) {
            if (dto.getId() <= 0
                    || dto.getTimestamp() == null
                    || dto.getLatitude() == null
                    || dto.getLongitude() == null
                    || shouldSkipDueToDateFilter(dto.getTimestamp(), job)) {
                continue;
            }

            Long favoriteId = resolveMappedId(dto.getFavoriteId(), referenceMaps.favoriteIds);
            Long geocodingId = resolveMappedId(dto.getGeocodingId(), referenceMaps.geocodingIds);
            String locationSource = favoriteId == null ? LocationSource.GEOCODING.name() : LocationSource.FAVORITE.name();

            entityManager.createNativeQuery("""
                            INSERT INTO timeline_stays
                            (id, user_id, timestamp, stay_duration, location, location_name,
                             favorite_id, geocoding_id, created_at, last_updated, location_source)
                            VALUES (:id, :userId, :timestamp, :duration,
                                    ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326),
                                    :locationName, :favoriteId, :geocodingId, :createdAt, :lastUpdated, :locationSource)
                            ON CONFLICT (id) DO UPDATE SET
                                user_id = EXCLUDED.user_id,
                                timestamp = EXCLUDED.timestamp,
                                stay_duration = EXCLUDED.stay_duration,
                                location = EXCLUDED.location,
                                location_name = EXCLUDED.location_name,
                                favorite_id = EXCLUDED.favorite_id,
                                geocoding_id = EXCLUDED.geocoding_id,
                                created_at = EXCLUDED.created_at,
                                last_updated = EXCLUDED.last_updated,
                                location_source = EXCLUDED.location_source
                            """)
                    .setParameter("id", dto.getId())
                    .setParameter("userId", user.getId())
                    .setParameter("timestamp", dto.getTimestamp())
                    .setParameter("duration", durationSeconds(dto.getTimestamp(), dto.getEndTime(), dto.getDuration()))
                    .setParameter("longitude", dto.getLongitude())
                    .setParameter("latitude", dto.getLatitude())
                    .setParameter("locationName", dto.getAddress() == null ? "" : dto.getAddress())
                    .setParameter("favoriteId", favoriteId)
                    .setParameter("geocodingId", geocodingId)
                    .setParameter("createdAt", Instant.now())
                    .setParameter("lastUpdated", Instant.now())
                    .setParameter("locationSource", locationSource)
                    .executeUpdate();

            referenceMaps.timelineStayIds.put(dto.getId(), dto.getId());
            imported++;
        }
        return imported;
    }

    private int importTimelineTripsSnapshot(TimelineDataDto timelineData,
                                            UserEntity user,
                                            ImportJob job,
                                            ImportReferenceMaps referenceMaps) {
        int imported = 0;
        for (TimelineDataDto.TripDto dto : emptyIfNull(timelineData.getTrips())) {
            if (dto.getId() <= 0
                    || dto.getTimestamp() == null
                    || dto.getStartLatitude() == null
                    || dto.getStartLongitude() == null
                    || dto.getEndLatitude() == null
                    || dto.getEndLongitude() == null
                    || shouldSkipDueToDateFilter(dto.getTimestamp(), job)) {
                continue;
            }

            MovementTypeSource source = parseEnum(MovementTypeSource.class, dto.getMovementTypeSource(), MovementTypeSource.AUTO);
            entityManager.createNativeQuery("""
                            INSERT INTO timeline_trips
                            (id, user_id, timestamp, trip_duration, start_point, end_point,
                             distance_meters, movement_type, movement_type_source, avg_gps_speed, max_gps_speed,
                             speed_variance, low_accuracy_points_count, water_distance_meters, water_distance_ratio,
                             longest_water_segment_meters, water_sample_count, water_evidence_available,
                             created_at, last_updated)
                            VALUES (:id, :userId, :timestamp, :duration,
                                    ST_SetSRID(ST_MakePoint(:startLongitude, :startLatitude), 4326),
                                    ST_SetSRID(ST_MakePoint(:endLongitude, :endLatitude), 4326),
                                    :distance, :movementType, :movementTypeSource, :avgGpsSpeed, :maxGpsSpeed,
                                    :speedVariance, :lowAccuracyPointsCount, :waterDistanceMeters, :waterDistanceRatio,
                                    :longestWaterSegmentMeters, :waterSampleCount, :waterEvidenceAvailable,
                                    :createdAt, :lastUpdated)
                            ON CONFLICT (id) DO UPDATE SET
                                user_id = EXCLUDED.user_id,
                                timestamp = EXCLUDED.timestamp,
                                trip_duration = EXCLUDED.trip_duration,
                                start_point = EXCLUDED.start_point,
                                end_point = EXCLUDED.end_point,
                                distance_meters = EXCLUDED.distance_meters,
                                movement_type = EXCLUDED.movement_type,
                                movement_type_source = EXCLUDED.movement_type_source,
                                avg_gps_speed = EXCLUDED.avg_gps_speed,
                                max_gps_speed = EXCLUDED.max_gps_speed,
                                speed_variance = EXCLUDED.speed_variance,
                                low_accuracy_points_count = EXCLUDED.low_accuracy_points_count,
                                water_distance_meters = EXCLUDED.water_distance_meters,
                                water_distance_ratio = EXCLUDED.water_distance_ratio,
                                longest_water_segment_meters = EXCLUDED.longest_water_segment_meters,
                                water_sample_count = EXCLUDED.water_sample_count,
                                water_evidence_available = EXCLUDED.water_evidence_available,
                                created_at = EXCLUDED.created_at,
                                last_updated = EXCLUDED.last_updated
                            """)
                    .setParameter("id", dto.getId())
                    .setParameter("userId", user.getId())
                    .setParameter("timestamp", dto.getTimestamp())
                    .setParameter("duration", durationSeconds(dto.getTimestamp(), dto.getEndTime(), dto.getDuration()))
                    .setParameter("startLongitude", dto.getStartLongitude())
                    .setParameter("startLatitude", dto.getStartLatitude())
                    .setParameter("endLongitude", dto.getEndLongitude())
                    .setParameter("endLatitude", dto.getEndLatitude())
                    .setParameter("distance", dto.getDistance() == null ? 0L : dto.getDistance())
                    .setParameter("movementType", dto.getTransportMode())
                    .setParameter("movementTypeSource", source.name())
                    .setParameter("avgGpsSpeed", dto.getAvgGpsSpeed())
                    .setParameter("maxGpsSpeed", dto.getMaxGpsSpeed())
                    .setParameter("speedVariance", dto.getSpeedVariance())
                    .setParameter("lowAccuracyPointsCount", dto.getLowAccuracyPointsCount())
                    .setParameter("waterDistanceMeters", dto.getWaterDistanceMeters())
                    .setParameter("waterDistanceRatio", dto.getWaterDistanceRatio())
                    .setParameter("longestWaterSegmentMeters", dto.getLongestWaterSegmentMeters())
                    .setParameter("waterSampleCount", dto.getWaterSampleCount())
                    .setParameter("waterEvidenceAvailable", dto.getWaterEvidenceAvailable())
                    .setParameter("createdAt", Instant.now())
                    .setParameter("lastUpdated", Instant.now())
                    .executeUpdate();

            referenceMaps.timelineTripIds.put(dto.getId(), dto.getId());
            imported++;
        }
        return imported;
    }

    private int importTimelineDataGapsSnapshot(TimelineDataDto timelineData,
                                               UserEntity user,
                                               ImportJob job,
                                               ImportReferenceMaps referenceMaps) {
        int imported = 0;
        for (TimelineDataDto.DataGapDto dto : emptyIfNull(timelineData.getDataGaps())) {
            if (dto.getId() <= 0
                    || dto.getStartTime() == null
                    || dto.getEndTime() == null
                    || !dto.getEndTime().isAfter(dto.getStartTime())
                    || shouldSkipDueToDateFilter(dto.getStartTime(), job)) {
                continue;
            }

            entityManager.createNativeQuery("""
                            INSERT INTO timeline_data_gaps
                            (id, user_id, start_time, end_time, duration_seconds, created_at)
                            VALUES (:id, :userId, :startTime, :endTime, :durationSeconds, :createdAt)
                            ON CONFLICT (id) DO UPDATE SET
                                user_id = EXCLUDED.user_id,
                                start_time = EXCLUDED.start_time,
                                end_time = EXCLUDED.end_time,
                                duration_seconds = EXCLUDED.duration_seconds,
                                created_at = EXCLUDED.created_at
                            """)
                    .setParameter("id", dto.getId())
                    .setParameter("userId", user.getId())
                    .setParameter("startTime", dto.getStartTime())
                    .setParameter("endTime", dto.getEndTime())
                    .setParameter("durationSeconds", Math.max(1L, dto.getDurationSeconds()))
                    .setParameter("createdAt", defaultInstant(dto.getCreatedAt()))
                    .executeUpdate();

            referenceMaps.timelineDataGapIds.put(dto.getId(), dto.getId());
            imported++;
        }
        return imported;
    }

    public void clearTimelineSnapshotData(UUID userId) {
        log.info("Clearing existing timeline snapshot data for user {}", userId);
        executeUserDelete("DELETE FROM timeline_notes WHERE user_id = :userId", userId);
        executeUserDelete("DELETE FROM timeline_trip_path_matches WHERE user_id = :userId", userId);
        executeUserDelete("DELETE FROM map_matching_reconciliations WHERE user_id = :userId", userId);
        executeUserDelete("DELETE FROM timeline_trip_movement_overrides WHERE user_id = :userId", userId);
        executeUserDelete("DELETE FROM timeline_data_gap_stay_overrides WHERE user_id = :userId", userId);
        executeUserDelete("DELETE FROM timeline_stays WHERE user_id = :userId", userId);
        executeUserDelete("DELETE FROM timeline_trips WHERE user_id = :userId", userId);
        executeUserDelete("DELETE FROM timeline_data_gaps WHERE user_id = :userId", userId);
        entityManager.flush();
        entityManager.clear();
    }

    private void executeUserDelete(String sql, UUID userId) {
        int deleted = entityManager.createNativeQuery(sql)
                .setParameter("userId", userId)
                .executeUpdate();
        log.debug("Deleted {} rows with snapshot cleanup SQL: {}", deleted, sql);
    }
}
