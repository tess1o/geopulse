package org.github.tess1o.geopulse.importdata.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.export.dto.*;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingStatus;
import org.github.tess1o.geopulse.mapmatching.model.TimelineTripPathMatchEntity;
import org.github.tess1o.geopulse.notes.model.NoteAnchorType;
import org.github.tess1o.geopulse.notes.model.NoteLocationSource;
import org.github.tess1o.geopulse.notes.model.TimelineNoteEntity;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapStayOverrideEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripMovementOverrideEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayNameOverrideEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripStaySplitOverrideEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineEditEntity;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditStatus;
import org.github.tess1o.geopulse.streaming.model.domain.LocationSource;
import org.github.tess1o.geopulse.streaming.model.shared.DataGapStayOverrideLocationStrategy;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.timelinelabels.model.entity.TimelineLabelEntity;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.weather.model.WeatherSampleEntity;
import org.github.tess1o.geopulse.weather.model.WeatherTargetSource;

import java.io.IOException;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.IMPORT_JSON;
import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.requireImportingUser;
import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.resolveMappedId;
import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.defaultInstant;
import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.defaultDouble;
import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.parseEnum;
import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.emptyIfNull;
import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.shouldSkipDueToDateFilter;

/**
 * GeoPulse import of data attached to the timeline: labels, user overrides, notes, weather samples and map matching
 * results. Links to stays, trips and favorites go through the ids recorded in {@link ImportReferenceMaps}.
 */
@ApplicationScoped
@Slf4j
public class GeoPulseTimelineDetailsImporter {

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    @Transactional
    public void importTimelineLabelsData(byte[] content, ImportJob job, ImportReferenceMaps referenceMaps) throws IOException {
        TimelineLabelsDataDto data = IMPORT_JSON.readValue(content, TimelineLabelsDataDto.class);
        UserEntity user = requireImportingUser(userRepository, job);

        int imported = 0;
        int updated = 0;
        List<TimelineLabelsDataDto.TimelineLabelDto> timelineLabels = emptyIfNull(data.getTimelineLabels());
        for (TimelineLabelsDataDto.TimelineLabelDto dto : timelineLabels) {
            if (dto.getName() == null || dto.getStartTime() == null || shouldSkipDueToDateFilter(dto.getStartTime(), job)) {
                continue;
            }

            TimelineLabelEntity entity = entityManager.createQuery("""
                            SELECT tag FROM TimelineLabelEntity tag
                            WHERE tag.user.id = :userId
                              AND tag.name = :name
                              AND tag.startTime = :startTime
                            """, TimelineLabelEntity.class)
                    .setParameter("userId", job.getUserId())
                    .setParameter("name", dto.getName())
                    .setParameter("startTime", dto.getStartTime())
                    .getResultStream()
                    .findFirst()
                    .orElse(null);

            boolean isNew = entity == null;
            if (isNew) {
                entity = new TimelineLabelEntity();
                entity.setUser(user);
                entity.setCreatedAt(defaultInstant(dto.getCreatedAt()));
            }

            entity.setName(dto.getName());
            entity.setStartTime(dto.getStartTime());
            entity.setEndTime(dto.getEndTime());
            entity.setSource(dto.getSource());
            entity.setIsActive(Boolean.TRUE.equals(dto.getIsActive()));
            entity.setColor(dto.getColor());
            entity.setShowAsPreset(dto.getShowAsPreset() == null ? Boolean.TRUE : dto.getShowAsPreset());
            entity.setUpdatedAt(defaultInstant(dto.getUpdatedAt()));
            entityManager.persist(entity);
            entityManager.flush();

            if (dto.getId() != null) {
                referenceMaps.timelineLabelIds.put(dto.getId(), entity.getId());
            }
            if (isNew) {
                imported++;
            } else {
                updated++;
            }
        }
        log.info("Imported {} and updated {} timeline labels for user {}", imported, updated, job.getUserId());
    }

    @Transactional
    public void importTimelineOverridesData(byte[] content, ImportJob job, ImportReferenceMaps referenceMaps) throws IOException {
        TimelineOverridesDataDto data = IMPORT_JSON.readValue(content, TimelineOverridesDataDto.class);
        UserEntity user = requireImportingUser(userRepository, job);

        int tripOverrides = 0;
        for (TimelineOverridesDataDto.TripMovementOverrideDto dto : emptyIfNull(data.getTripMovementOverrides())) {
            if (dto.getSourceTripTimestamp() == null || shouldSkipDueToDateFilter(dto.getSourceTripTimestamp(), job)) {
                continue;
            }
            if (dto.getSourceTripDurationSeconds() == null || dto.getSourceTripDurationSeconds() <= 0) {
                log.warn("Skipping trip movement override at {} for user {}: source trip duration is missing or not positive",
                        dto.getSourceTripTimestamp(), job.getUserId());
                continue;
            }
            String movementType = validTripTypeOrNull(dto.getMovementType());
            if (movementType == null) {
                log.warn("Skipping trip movement override at {} for user {}: '{}' is not a trip type",
                        dto.getSourceTripTimestamp(), job.getUserId(), dto.getMovementType());
                continue;
            }
            TimelineTripMovementOverrideEntity entity = entityManager.createQuery("""
                            SELECT override FROM TimelineTripMovementOverrideEntity override
                            WHERE override.user.id = :userId
                              AND override.sourceTripTimestamp = :timestamp
                              AND override.sourceTripDurationSeconds = :duration
                            """, TimelineTripMovementOverrideEntity.class)
                    .setParameter("userId", job.getUserId())
                    .setParameter("timestamp", dto.getSourceTripTimestamp())
                    .setParameter("duration", dto.getSourceTripDurationSeconds())
                    .getResultStream()
                    .findFirst()
                    .orElseGet(TimelineTripMovementOverrideEntity::new);

            entity.setUser(user);
            entity.setMovementType(movementType);
            Long mappedTripId = resolveMappedId(dto.getTripId(), referenceMaps.timelineTripIds);
            entity.setTrip(mappedTripId == null ? null : entityManager.getReference(TimelineTripEntity.class, mappedTripId));
            entity.setSourceTripTimestamp(dto.getSourceTripTimestamp());
            entity.setSourceTripDurationSeconds(dto.getSourceTripDurationSeconds());
            entity.setSourceDistanceMeters(dto.getSourceDistanceMeters() == null ? 0L : dto.getSourceDistanceMeters());
            entity.setSourceStartLatitude(defaultDouble(dto.getSourceStartLatitude()));
            entity.setSourceStartLongitude(defaultDouble(dto.getSourceStartLongitude()));
            entity.setSourceEndLatitude(defaultDouble(dto.getSourceEndLatitude()));
            entity.setSourceEndLongitude(defaultDouble(dto.getSourceEndLongitude()));
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(defaultInstant(dto.getCreatedAt()));
            }
            entity.setUpdatedAt(defaultInstant(dto.getUpdatedAt()));
            entityManager.persist(entity);
            tripOverrides++;
        }

        int gapOverrides = 0;
        for (TimelineOverridesDataDto.DataGapStayOverrideDto dto : emptyIfNull(data.getDataGapStayOverrides())) {
            if (dto.getSourceGapStartTime() == null || shouldSkipDueToDateFilter(dto.getSourceGapStartTime(), job)) {
                continue;
            }
            TimelineDataGapStayOverrideEntity entity = entityManager.createQuery("""
                            SELECT override FROM TimelineDataGapStayOverrideEntity override
                            WHERE override.user.id = :userId
                              AND override.sourceGapStartTime = :startTime
                              AND override.sourceGapEndTime = :endTime
                            """, TimelineDataGapStayOverrideEntity.class)
                    .setParameter("userId", job.getUserId())
                    .setParameter("startTime", dto.getSourceGapStartTime())
                    .setParameter("endTime", dto.getSourceGapEndTime())
                    .getResultStream()
                    .findFirst()
                    .orElseGet(TimelineDataGapStayOverrideEntity::new);

            entity.setUser(user);
            Long mappedDataGapId = resolveMappedId(dto.getDataGapId(), referenceMaps.timelineDataGapIds);
            Long mappedStayId = resolveMappedId(dto.getStayId(), referenceMaps.timelineStayIds);
            entity.setDataGap(mappedDataGapId == null ? null : entityManager.getReference(TimelineDataGapEntity.class, mappedDataGapId));
            entity.setStay(mappedStayId == null ? null : entityManager.getReference(TimelineStayEntity.class, mappedStayId));
            entity.setLocationStrategy(parseEnum(DataGapStayOverrideLocationStrategy.class, dto.getLocationStrategy(), DataGapStayOverrideLocationStrategy.SELECTED_LOCATION));
            entity.setSelectedFavoriteId(resolveMappedId(dto.getSelectedFavoriteId(), referenceMaps.favoriteIds));
            entity.setSelectedGeocodingId(resolveMappedId(dto.getSelectedGeocodingId(), referenceMaps.geocodingIds));
            entity.setSelectedLatitude(dto.getSelectedLatitude());
            entity.setSelectedLongitude(dto.getSelectedLongitude());
            entity.setSelectedLocationName(dto.getSelectedLocationName());
            entity.setSourceGapStartTime(dto.getSourceGapStartTime());
            entity.setSourceGapEndTime(dto.getSourceGapEndTime());
            entity.setSourceGapDurationSeconds(dto.getSourceGapDurationSeconds() == null ? 0L : dto.getSourceGapDurationSeconds());
            entity.setSourceBeforeLatitude(defaultDouble(dto.getSourceBeforeLatitude()));
            entity.setSourceBeforeLongitude(defaultDouble(dto.getSourceBeforeLongitude()));
            entity.setSourceAfterLatitude(defaultDouble(dto.getSourceAfterLatitude()));
            entity.setSourceAfterLongitude(defaultDouble(dto.getSourceAfterLongitude()));
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(defaultInstant(dto.getCreatedAt()));
            }
            entity.setUpdatedAt(defaultInstant(dto.getUpdatedAt()));
            entityManager.persist(entity);
            gapOverrides++;
        }

        int staySplitOverrides = 0;
        for (TimelineOverridesDataDto.TripStaySplitOverrideDto dto : emptyIfNull(data.getTripStaySplitOverrides())) {
            if (dto.getSourceTripTimestamp() == null || dto.getStayStartTime() == null || dto.getStayEndTime() == null
                    || shouldSkipDueToDateFilter(dto.getSourceTripTimestamp(), job)) {
                continue;
            }
            TimelineTripStaySplitOverrideEntity entity = entityManager.createQuery("""
                            SELECT override FROM TimelineTripStaySplitOverrideEntity override
                            WHERE override.user.id = :userId
                              AND override.sourceTripTimestamp = :timestamp
                              AND override.stayStartTime = :stayStart
                              AND override.stayEndTime = :stayEnd
                            """, TimelineTripStaySplitOverrideEntity.class)
                    .setParameter("userId", job.getUserId())
                    .setParameter("timestamp", dto.getSourceTripTimestamp())
                    .setParameter("stayStart", dto.getStayStartTime())
                    .setParameter("stayEnd", dto.getStayEndTime())
                    .getResultStream()
                    .findFirst()
                    .orElseGet(TimelineTripStaySplitOverrideEntity::new);

            entity.setUser(user);
            Long mappedStayId = resolveMappedId(dto.getStayId(), referenceMaps.timelineStayIds);
            Long mappedFavoriteId = resolveMappedId(dto.getFavoriteId(), referenceMaps.favoriteIds);
            Long mappedGeocodingId = resolveMappedId(dto.getGeocodingId(), referenceMaps.geocodingIds);
            entity.setStay(mappedStayId == null ? null : entityManager.getReference(TimelineStayEntity.class, mappedStayId));
            entity.setFavoriteLocation(mappedFavoriteId == null ? null : entityManager.getReference(FavoritesEntity.class, mappedFavoriteId));
            entity.setGeocodingLocation(mappedGeocodingId == null ? null : entityManager.getReference(ReverseGeocodingLocationEntity.class, mappedGeocodingId));
            entity.setStayStartTime(dto.getStayStartTime());
            entity.setStayEndTime(dto.getStayEndTime());
            entity.setAnchorTimestamp(dto.getAnchorTimestamp() != null ? dto.getAnchorTimestamp() : dto.getStayStartTime());
            entity.setStayLatitude(defaultDouble(dto.getStayLatitude()));
            entity.setStayLongitude(defaultDouble(dto.getStayLongitude()));
            entity.setStayLocationName(dto.getStayLocationName() != null ? dto.getStayLocationName() : "Unknown Location");
            // Older exports do not say; keeping the stored name is what they did.
            entity.setStayLocationNameCustom(dto.getStayLocationNameCustom() == null || dto.getStayLocationNameCustom());
            entity.setStayLocationSource(parseEnum(LocationSource.class, dto.getStayLocationSource(), LocationSource.GEOCODING));
            entity.setSourceTripTimestamp(dto.getSourceTripTimestamp());
            entity.setSourceTripDurationSeconds(dto.getSourceTripDurationSeconds() == null ? 0L : dto.getSourceTripDurationSeconds());
            entity.setSourceDistanceMeters(dto.getSourceDistanceMeters() == null ? 0L : dto.getSourceDistanceMeters());
            entity.setSourceStartLatitude(defaultDouble(dto.getSourceStartLatitude()));
            entity.setSourceStartLongitude(defaultDouble(dto.getSourceStartLongitude()));
            entity.setSourceEndLatitude(defaultDouble(dto.getSourceEndLatitude()));
            entity.setSourceEndLongitude(defaultDouble(dto.getSourceEndLongitude()));
            entity.setSourceTripMovementType(validTripTypeOrNull(dto.getSourceTripMovementType()));
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(defaultInstant(dto.getCreatedAt()));
            }
            entity.setUpdatedAt(defaultInstant(dto.getUpdatedAt()));
            entityManager.persist(entity);
            staySplitOverrides++;
        }
        int stayNames = 0;
        for (TimelineOverridesDataDto.StayNameOverrideDto dto : emptyIfNull(data.getStayNameOverrides())) {
            if (dto.getStayStartTime() == null || dto.getStayEndTime() == null || dto.getLocationName() == null
                    || dto.getLocationName().isBlank()
                    || shouldSkipDueToDateFilter(dto.getStayStartTime(), job)) {
                continue;
            }
            TimelineStayNameOverrideEntity entity = entityManager.createQuery("""
                            SELECT override FROM TimelineStayNameOverrideEntity override
                            WHERE override.user.id = :userId
                              AND override.stayStartTime = :stayStart
                              AND override.stayEndTime = :stayEnd
                            """, TimelineStayNameOverrideEntity.class)
                    .setParameter("userId", job.getUserId())
                    .setParameter("stayStart", dto.getStayStartTime())
                    .setParameter("stayEnd", dto.getStayEndTime())
                    .getResultStream()
                    .findFirst()
                    .orElseGet(TimelineStayNameOverrideEntity::new);
            entity.setUser(user);
            entity.setStayStartTime(dto.getStayStartTime());
            entity.setStayEndTime(dto.getStayEndTime());
            entity.setLocationName(dto.getLocationName());
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(defaultInstant(dto.getCreatedAt()));
            }
            entity.setUpdatedAt(defaultInstant(dto.getUpdatedAt()));
            entityManager.persist(entity);
            stayNames++;
        }
        log.info("Imported/updated {} trip overrides, {} gap overrides, {} stay-split overrides and {} stay names "
                + "for user {}", tripOverrides, gapOverrides, staySplitOverrides, stayNames, job.getUserId());
    }

    public void requireEmptyTimelineEdits(UUID userId) {
        if (entityManager.createQuery("select count(e) from TimelineEditEntity e where e.user.id = :userId", Long.class)
                .setParameter("userId", userId).getSingleResult() != 0) {
            throw new IllegalArgumentException("Timeline edits can only be imported into an account with no timeline edits");
        }
    }

    @Transactional
    public void importTimelineEditsData(byte[] content, ImportJob job, ImportReferenceMaps referenceMaps) throws IOException {
        requireEmptyTimelineEdits(job.getUserId());
        TimelineEditsDataDto data = IMPORT_JSON.readValue(content, TimelineEditsDataDto.class);
        UserEntity user = requireImportingUser(userRepository, job);
        Map<Long, TimelineEditEntity> importedByOldId = new HashMap<>();

        for (TimelineEditsDataDto.EditDto dto : emptyIfNull(data.getEdits())) {
            if (dto.getKind() == null || dto.getAnchorStart() == null || dto.getAnchorEnd() == null
                    || dto.getAffectedFrom() == null || dto.getAffectedTo() == null
                    || shouldSkipDueToDateFilter(dto.getAffectedFrom(), job)) {
                continue;
            }
            TimelineEditEntity edit = new TimelineEditEntity();
            edit.setUser(user);
            edit.setSeq(dto.getSeq());
            edit.setKind(dto.getKind());
            edit.setAnchorStart(dto.getAnchorStart());
            edit.setAnchorEnd(dto.getAnchorEnd());
            edit.setAffectedFrom(dto.getAffectedFrom());
            edit.setAffectedTo(dto.getAffectedTo());
            edit.setBeforeValues(dto.getBeforeValues() == null ? null : new LinkedHashMap<>(dto.getBeforeValues()));
            edit.setGroupId(dto.getGroupId());
            edit.setPayload(dto.getPayload() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(dto.getPayload()));
            for (String prefix : List.of("", "right", "start", "end")) {
                remapPayloadReference(edit.getPayload(), prefix.isEmpty() ? "favoriteId" : prefix + "FavoriteId", referenceMaps.favoriteIds);
                remapPayloadReference(edit.getPayload(), prefix.isEmpty() ? "geocodingId" : prefix + "GeocodingId", referenceMaps.geocodingIds);
            }
            Long favoriteId = dto.getFavoriteId() == null
                    ? payloadId(edit.getPayload(), "favoriteId") : resolveMappedId(dto.getFavoriteId(), referenceMaps.favoriteIds);
            Long geocodingId = dto.getGeocodingId() == null
                    ? payloadId(edit.getPayload(), "geocodingId") : resolveMappedId(dto.getGeocodingId(), referenceMaps.geocodingIds);
            edit.setPlaceFavorite(favoriteId == null ? null : entityManager.getReference(FavoritesEntity.class, favoriteId));
            edit.setPlaceGeocoding(geocodingId == null ? null
                    : entityManager.getReference(ReverseGeocodingLocationEntity.class, geocodingId));
            edit.setPlaceLatitude(dto.getLatitude());
            edit.setPlaceLongitude(dto.getLongitude());
            edit.setPlaceName(dto.getPlaceName());
            edit.setStatus(dto.getStatus() == null ? TimelineEditStatus.ACTIVE : dto.getStatus());
            edit.setCreatedAt(defaultInstant(dto.getCreatedAt()));
            edit.setUpdatedAt(defaultInstant(dto.getUpdatedAt()));
            entityManager.persist(edit);
            entityManager.flush();
            if (dto.getId() != null) importedByOldId.put(dto.getId(), edit);
        }

        for (TimelineEditsDataDto.EditDto dto : emptyIfNull(data.getEdits())) {
            TimelineEditEntity edit = importedByOldId.get(dto.getId());
            if (edit != null) edit.setSupersededBy(importedByOldId.get(dto.getSupersededById()));
        }
        log.info("Imported/updated {} timeline edits for user {}", importedByOldId.size(), job.getUserId());
    }

    private static Long payloadId(Map<String, Object> payload, String key) {
        return payload.get(key) instanceof Number number ? number.longValue() : null;
    }

    private static void remapPayloadReference(Map<String, Object> payload, String key, Map<Long, Long> ids) {
        Object old = payload.remove(key);
        if (old instanceof Number number) {
            Long mapped = ids.get(number.longValue());
            if (mapped != null) payload.put(key, mapped);
        }
    }

    @Transactional
    public void importNotesData(byte[] content, ImportJob job, ImportReferenceMaps referenceMaps) throws IOException {
        NotesDataDto data = IMPORT_JSON.readValue(content, NotesDataDto.class);
        UserEntity user = requireImportingUser(userRepository, job);

        List<NotesDataDto.NoteDto> notes = emptyIfNull(data.getNotes());
        for (NotesDataDto.NoteDto dto : notes) {
            if (dto.getContentMarkdown() == null || dto.getEventTime() == null || shouldSkipDueToDateFilter(dto.getEventTime(), job)) {
                continue;
            }
            TimelineNoteEntity entity = entityManager.createQuery("""
                            SELECT note FROM TimelineNoteEntity note
                            WHERE note.user.id = :userId
                              AND note.eventTime = :eventTime
                              AND note.contentMarkdown = :content
                            """, TimelineNoteEntity.class)
                    .setParameter("userId", job.getUserId())
                    .setParameter("eventTime", dto.getEventTime())
                    .setParameter("content", dto.getContentMarkdown())
                    .getResultStream()
                    .findFirst()
                    .orElseGet(TimelineNoteEntity::new);
            entity.setUser(user);
            entity.setTitle(dto.getTitle());
            entity.setContentMarkdown(dto.getContentMarkdown());
            entity.setSnippet(dto.getSnippet());
            entity.setEventTime(dto.getEventTime());
            entity.setLocation(dto.getLatitude() != null && dto.getLongitude() != null
                    ? GeoUtils.createPoint(dto.getLongitude(), dto.getLatitude())
                    : null);
            entity.setLocationSource(parseEnum(NoteLocationSource.class, dto.getLocationSource(), NoteLocationSource.NONE));
            entity.setAnchorType(parseEnum(NoteAnchorType.class, dto.getAnchorType(), NoteAnchorType.TIMESTAMP));
            Long mappedStayId = resolveMappedId(dto.getStayId(), referenceMaps.timelineStayIds);
            Long mappedTripId = resolveMappedId(dto.getTripId(), referenceMaps.timelineTripIds);
            entity.setStay(mappedStayId == null ? null : entityManager.getReference(TimelineStayEntity.class, mappedStayId));
            entity.setTrip(mappedTripId == null ? null : entityManager.getReference(TimelineTripEntity.class, mappedTripId));
            entity.setSourceItemStartTime(dto.getSourceItemStartTime());
            entity.setSourceItemDurationSeconds(dto.getSourceItemDurationSeconds());
            entity.setSourceStartLatitude(dto.getSourceStartLatitude());
            entity.setSourceStartLongitude(dto.getSourceStartLongitude());
            entity.setSourceEndLatitude(dto.getSourceEndLatitude());
            entity.setSourceEndLongitude(dto.getSourceEndLongitude());
            entity.setSourceDistanceMeters(dto.getSourceDistanceMeters());
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(defaultInstant(dto.getCreatedAt()));
            }
            entity.setUpdatedAt(defaultInstant(dto.getUpdatedAt()));
            entity.setDeletedAt(dto.getDeletedAt());
            entityManager.persist(entity);
        }
        log.info("Imported/updated {} notes for user {}", notes.size(), job.getUserId());
    }

    @Transactional
    public void importWeatherSamplesData(byte[] content, ImportJob job) throws IOException {
        WeatherSamplesDataDto data = IMPORT_JSON.readValue(content, WeatherSamplesDataDto.class);
        UserEntity user = requireImportingUser(userRepository, job);

        List<WeatherSamplesDataDto.WeatherSampleDto> samples = emptyIfNull(data.getSamples());
        for (WeatherSamplesDataDto.WeatherSampleDto dto : samples) {
            if (dto.getProvider() == null || dto.getObservedAt() == null || shouldSkipDueToDateFilter(dto.getObservedAt(), job)) {
                continue;
            }
            WeatherSampleEntity entity = entityManager.createQuery("""
                            SELECT sample FROM WeatherSampleEntity sample
                            WHERE sample.user.id = :userId
                              AND sample.provider = :provider
                              AND sample.latitudeBucket = :latBucket
                              AND sample.longitudeBucket = :lonBucket
                              AND sample.observedAt = :observedAt
                            """, WeatherSampleEntity.class)
                    .setParameter("userId", job.getUserId())
                    .setParameter("provider", dto.getProvider())
                    .setParameter("latBucket", defaultDouble(dto.getLatitudeBucket()))
                    .setParameter("lonBucket", defaultDouble(dto.getLongitudeBucket()))
                    .setParameter("observedAt", dto.getObservedAt())
                    .getResultStream()
                    .findFirst()
                    .orElseGet(WeatherSampleEntity::new);
            entity.setUser(user);
            entity.setProvider(dto.getProvider());
            entity.setSource(parseEnum(WeatherTargetSource.class, dto.getSource(), WeatherTargetSource.IMPORT_BACKFILL));
            entity.setRequestedLatitude(defaultDouble(dto.getRequestedLatitude()));
            entity.setRequestedLongitude(defaultDouble(dto.getRequestedLongitude()));
            entity.setProviderLatitude(dto.getProviderLatitude());
            entity.setProviderLongitude(dto.getProviderLongitude());
            entity.setLatitudeBucket(defaultDouble(dto.getLatitudeBucket()));
            entity.setLongitudeBucket(defaultDouble(dto.getLongitudeBucket()));
            entity.setObservedAt(dto.getObservedAt());
            entity.setFetchedAt(defaultInstant(dto.getFetchedAt()));
            entity.setTimezone(dto.getTimezone());
            entity.setWeatherCode(dto.getWeatherCode());
            entity.setTemperature(dto.getTemperature());
            entity.setApparentTemperature(dto.getApparentTemperature());
            entity.setHumidity(dto.getHumidity());
            entity.setPrecipitation(dto.getPrecipitation());
            entity.setRain(dto.getRain());
            entity.setSnowfall(dto.getSnowfall());
            entity.setCloudCover(dto.getCloudCover());
            entity.setWindSpeed(dto.getWindSpeed());
            entity.setWindGust(dto.getWindGust());
            entity.setWindDirection(dto.getWindDirection());
            entity.setPressure(dto.getPressure());
            entity.setRawData(dto.getRawData());
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(defaultInstant(dto.getCreatedAt()));
            }
            entity.setUpdatedAt(defaultInstant(dto.getUpdatedAt()));
            entityManager.persist(entity);
        }
        log.info("Imported/updated {} weather samples for user {}", samples.size(), job.getUserId());
    }

    @Transactional
    public void importMapMatchingData(byte[] content, ImportJob job, ImportReferenceMaps referenceMaps) throws IOException {
        MapMatchingDataDto data = IMPORT_JSON.readValue(content, MapMatchingDataDto.class);
        UserEntity user = requireImportingUser(userRepository, job);

        List<MapMatchingDataDto.PathMatchDto> pathMatches = emptyIfNull(data.getPathMatches());
        for (MapMatchingDataDto.PathMatchDto dto : pathMatches) {
            if (dto.getProvider() == null
                    || dto.getProfile() == null
                    || dto.getConfigHash() == null
                    || dto.getInputHash() == null
                    || dto.getMatchedSegmentsJson() == null
                    || !MapMatchingStatus.MATCHED.name().equals(dto.getStatus())
                    || shouldSkipDueToDateFilter(dto.getTripTimestamp(), job)) {
                continue;
            }

            TimelineTripPathMatchEntity entity = findExistingMapMatchingPathMatch(job.getUserId(), dto)
                    .orElseGet(TimelineTripPathMatchEntity::new);
            entity.setUser(user);
            Long mappedTripId = resolveMappedId(dto.getTripId(), referenceMaps.timelineTripIds);
            entity.setTrip(mappedTripId == null ? null : entityManager.getReference(TimelineTripEntity.class, mappedTripId));
            entity.setProvider(dto.getProvider());
            entity.setProfile(dto.getProfile());
            entity.setConfigHash(dto.getConfigHash());
            entity.setInputHash(dto.getInputHash());
            entity.setStatus(MapMatchingStatus.MATCHED);
            entity.setAttempts(dto.getAttempts() == null ? 0 : dto.getAttempts());
            entity.setNextAttemptAt(defaultInstant(dto.getNextAttemptAt()));
            entity.setLastAttemptAt(dto.getLastAttemptAt());
            entity.setLockedAt(null);
            entity.setCompletedAt(defaultInstant(dto.getCompletedAt()));
            entity.setLastError(null);
            entity.setMatchedSegmentsJson(dto.getMatchedSegmentsJson());
            entity.setSource(dto.getSource() == null ? "ON_DEMAND" : dto.getSource());
            entity.setPriority(dto.getPriority() == null ? 100 : dto.getPriority());
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(defaultInstant(dto.getCreatedAt()));
            }
            entity.setUpdatedAt(defaultInstant(dto.getUpdatedAt()));
            entityManager.persist(entity);
        }
        log.info("Imported/updated {} map matching path matches for user {}", pathMatches.size(), job.getUserId());
    }

    private Optional<TimelineTripPathMatchEntity> findExistingMapMatchingPathMatch(UUID userId,
                                                                                   MapMatchingDataDto.PathMatchDto dto) {
        return entityManager.createQuery("""
                        SELECT pathMatch FROM TimelineTripPathMatchEntity pathMatch
                        WHERE pathMatch.user.id = :userId
                          AND pathMatch.provider = :provider
                          AND pathMatch.profile = :profile
                          AND pathMatch.configHash = :configHash
                          AND pathMatch.inputHash = :inputHash
                        """, TimelineTripPathMatchEntity.class)
                .setParameter("userId", userId)
                .setParameter("provider", dto.getProvider())
                .setParameter("profile", dto.getProfile())
                .setParameter("configHash", dto.getConfigHash())
                .setParameter("inputHash", dto.getInputHash())
                .getResultStream()
                .findFirst();
    }

    /** Imported data is untrusted; an unknown movement type would otherwise fail an undo with a 500. */
    private static String validTripTypeOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TripType.valueOf(value).name();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
