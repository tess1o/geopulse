package org.github.tess1o.geopulse.notes.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.notes.client.MemosClient;
import org.github.tess1o.geopulse.notes.model.*;
import org.github.tess1o.geopulse.notes.repository.TimelineNoteRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class TimelineNoteService {

    private static final int DEFAULT_SEARCH_LIMIT = 1000;
    private static final int MAX_SEARCH_LIMIT = 5000;

    private final ConcurrentHashMap<NoteSearchCacheKey, CachedMemosSearchResult> memosSearchCache = new ConcurrentHashMap<>();

    @Inject
    TimelineNoteRepository noteRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    MemosClient memosClient;

    @ConfigProperty(name = "memos.notes.search-cache-ttl-seconds", defaultValue = "300")
    long memosSearchCacheTtlSeconds;

    @ConfigProperty(name = "memos.notes.search-cache-max-entries", defaultValue = "200")
    int memosSearchCacheMaxEntries;

    @ConfigProperty(name = "geopulse.notes.trip-marker.max-gps-gap-seconds", defaultValue = "900")
    long maxTripMarkerGpsGapSeconds;

    @ConfigProperty(name = "geopulse.notes.anchor.matching.max_timestamp_delta_seconds", defaultValue = "21600")
    long maxAnchorTimestampDeltaSeconds;

    @ConfigProperty(name = "geopulse.notes.anchor.matching.max_point_distance_meters", defaultValue = "350.0")
    double maxAnchorPointDistanceMeters;

    public CompletableFuture<NoteSearchResponse> searchNotes(
            UUID userId,
            Instant startTime,
            Instant endTime,
            boolean includeExternal,
            Integer limit,
            Double latitude,
            Double longitude,
            Double radiusMeters
    ) {
        int resolvedLimit = resolveLimit(limit);
        List<NoteDto> localNotes = noteRepository.findByUserIdAndTimeRange(userId, startTime, endTime).stream()
                .map(this::mapLocalNote)
                .collect(Collectors.toCollection(ArrayList::new));

        List<NoteDto> externalNotes = includeExternal
                ? loadMemosNotes(userId, startTime, endTime, resolvedLimit)
                : List.of();

        List<NoteDto> combined = new ArrayList<>(localNotes);
        combined.addAll(externalNotes);
        resolveTimelineLocations(userId, startTime, endTime, combined);
        List<NoteDto> filtered = filterByRadius(combined, latitude, longitude, radiusMeters).stream()
                .sorted(noteComparator())
                .limit(resolvedLimit)
                .toList();

        return CompletableFuture.completedFuture(NoteSearchResponse.builder()
                .notes(filtered)
                .totalCount(filtered.size())
                .geopulseCount((int) filtered.stream().filter(note -> note.getSource() == NoteSource.GEOPULSE).count())
                .memosCount((int) filtered.stream().filter(note -> note.getSource() == NoteSource.MEMOS).count())
                .build());
    }

    public CompletableFuture<NoteMapMarkersResponse> getMapMarkers(
            UUID userId,
            Instant startTime,
            Instant endTime,
            boolean includeExternal,
            Integer coordinatePrecision
    ) {
        int safePrecision = sanitizeCoordinatePrecision(coordinatePrecision);
        return searchNotes(userId, startTime, endTime, includeExternal, MAX_SEARCH_LIMIT, null, null, null)
                .thenApply(response -> {
                    Map<String, MapMarkerAccumulator> groups = new LinkedHashMap<>();
                    int locatedCount = 0;

                    for (NoteDto note : response.getNotes()) {
                        if (note.getLatitude() == null || note.getLongitude() == null) {
                            continue;
                        }

                        locatedCount++;
                        double roundedLat = roundCoordinate(note.getLatitude(), safePrecision);
                        double roundedLon = roundCoordinate(note.getLongitude(), safePrecision);
                        String key = roundedLat + "," + roundedLon;
                        groups.compute(key, (ignored, existing) -> {
                            if (existing == null) {
                                return new MapMarkerAccumulator(roundedLat, roundedLon, note.getEventTime(), note.getLocationSource(), 1, note);
                            }
                            return existing.add(note);
                        });
                    }

                    List<NoteMapMarkerDto> markers = groups.values().stream()
                            .map(group -> NoteMapMarkerDto.builder()
                                    .latitude(group.latitude())
                                    .longitude(group.longitude())
                                    .count(group.count())
                                    .latestEventTime(group.latestEventTime())
                                    .locationSource(group.locationSource())
                                    .singleNote(group.count() == 1 ? group.singleNote() : null)
                                    .build())
                            .sorted(Comparator.comparing(
                                    NoteMapMarkerDto::getLatestEventTime,
                                    Comparator.nullsLast(Comparator.reverseOrder())
                            ))
                            .toList();

                    return NoteMapMarkersResponse.builder()
                            .markers(markers)
                            .totalNotes(response.getNotes().size())
                            .locatedNotes(locatedCount)
                            .build();
                });
    }

    @Transactional
    public CompletableFuture<NoteDto> createNote(UUID userId, CreateNoteRequest request) {
        NoteDestination destination = resolveDestination(userId, request.getDestination());
        if (destination == NoteDestination.MEMOS) {
            return createMemosNote(userId, request);
        }
        return CompletableFuture.completedFuture(createLocalNote(userId, request));
    }

    @Transactional
    public NoteDto updateLocalNote(UUID userId, Long noteId, UpdateNoteRequest request) {
        TimelineNoteEntity note = noteRepository.findActiveByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new NoSuchElementException("Note not found"));

        note.setTitle(normalizeTitle(request.getTitle()));
        note.setContentMarkdown(request.getContentMarkdown().trim());
        note.setSnippet(buildSnippet(request.getContentMarkdown()));
        if (request.getEventTime() != null) {
            note.setEventTime(request.getEventTime());
        }
        if (request.getLatitude() != null && request.getLongitude() != null) {
            note.setLocation(GeoUtils.createPoint(request.getLongitude(), request.getLatitude()));
            note.setLocationSource(NoteLocationSource.EXPLICIT);
        }
        return mapLocalNote(note);
    }

    @Transactional
    public void deleteLocalNote(UUID userId, Long noteId) {
        TimelineNoteEntity note = noteRepository.findActiveByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new NoSuchElementException("Note not found"));
        note.setDeletedAt(Instant.now());
    }

    public Optional<MemosConfigResponse> getMemosConfig(UUID userId) {
        UserEntity user = userRepository.findById(userId);
        if (user == null || user.getMemosPreferences() == null) {
            return Optional.empty();
        }
        return Optional.of(toConfigResponse(applyMemosDefaults(user.getMemosPreferences())));
    }

    @Transactional
    public void updateMemosConfig(UUID userId, UpdateMemosConfigRequest request) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        MemosPreferences existing = applyMemosDefaults(user.getMemosPreferences());
        String apiKey = request.getApiKey();
        if ((apiKey == null || apiKey.isBlank()) && existing.getApiKey() != null && !existing.getApiKey().isBlank()) {
            apiKey = existing.getApiKey();
        }

        String serverUrl = normalizeServerUrl(request.getServerUrl());
        boolean enabled = Boolean.TRUE.equals(request.getEnabled());
        if (enabled && (serverUrl == null || serverUrl.isBlank())) {
            throw new IllegalArgumentException("Server URL is required when Memos integration is enabled");
        }
        if (enabled && (apiKey == null || apiKey.isBlank())) {
            throw new IllegalArgumentException("API key is required when Memos integration is enabled");
        }

        MemosPreferences preferences = MemosPreferences.builder()
                .serverUrl(serverUrl)
                .apiKey(apiKey)
                .enabled(enabled)
                .defaultSaveDestination(request.getDefaultSaveDestination() != null
                        ? request.getDefaultSaveDestination()
                        : existing.getDefaultSaveDestination())
                .defaultVisibility(request.getDefaultVisibility() != null
                        ? request.getDefaultVisibility()
                        : existing.getDefaultVisibility())
                .maxNotesPerRequest(resolvePositiveOrDefault(request.getMaxNotesPerRequest(), existing.getMaxNotesPerRequest(), DEFAULT_SEARCH_LIMIT))
                .maxContentBytes(resolvePositiveOrDefault(request.getMaxContentBytes(), existing.getMaxContentBytes(), 64_000))
                .build();

        user.setMemosPreferences(preferences);
        userRepository.persist(user);
        invalidateMemosCacheForUser(userId);
    }

    public CompletableFuture<TestMemosConnectionResponse> testMemosConnection(UUID userId, TestMemosConnectionRequest request) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            return CompletableFuture.completedFuture(TestMemosConnectionResponse.builder()
                    .success(false)
                    .message("User not found")
                    .build());
        }

        String serverUrl = normalizeServerUrl(request.getServerUrl());
        String apiKey = request.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            MemosPreferences preferences = user.getMemosPreferences();
            if (preferences != null && preferences.getApiKey() != null && !preferences.getApiKey().isBlank()) {
                apiKey = preferences.getApiKey();
            } else {
                return CompletableFuture.completedFuture(TestMemosConnectionResponse.builder()
                        .success(false)
                        .message("API key is required")
                        .details("No API key provided and no saved API key found")
                        .build());
            }
        }

        try {
            MemosListResponse response = memosClient.fetchMemosPage(serverUrl, apiKey, 1, null, null);
            return CompletableFuture.completedFuture(TestMemosConnectionResponse.builder()
                    .success(true)
                    .message("Successfully connected to Memos server")
                    .details("Server returned " + (response.getMemos() == null ? 0 : response.getMemos().size()) + " memo(s)")
                    .build());
        } catch (Exception e) {
            return CompletableFuture.completedFuture(TestMemosConnectionResponse.builder()
                    .success(false)
                    .message(resolveMemosErrorMessage(e))
                    .details(e.getMessage())
                    .build());
        }
    }

    @Transactional
    public int reattachAnchoredNotes(UUID userId) {
        int reattached = reattachStayNotes(userId) + reattachTripNotes(userId);
        if (reattached > 0) {
            log.info("Reattached {} GeoPulse notes after timeline regeneration for user {}", reattached, userId);
        }
        return reattached;
    }

    private List<NoteDto> loadMemosNotes(UUID userId, Instant startTime, Instant endTime, int limit) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        MemosPreferences preferences = applyMemosDefaults(user.getMemosPreferences());
        if (!Boolean.TRUE.equals(preferences.getEnabled())) {
            return List.of();
        }
        if (preferences.getServerUrl() == null || preferences.getServerUrl().isBlank()
                || preferences.getApiKey() == null || preferences.getApiKey().isBlank()) {
            return List.of();
        }

        int providerLimit = Math.min(limit, Math.max(1, preferences.getMaxNotesPerRequest()));
        NoteSearchCacheKey cacheKey = new NoteSearchCacheKey(userId, startTime, endTime, providerLimit);
        List<NoteDto> cached = getCachedMemosResult(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            List<NoteDto> notes = memosClient.listMemosAllPages(preferences.getServerUrl(), preferences.getApiKey(), startTime, endTime, providerLimit)
                    .stream()
                    .map(memo -> mapMemosMemo(memo, preferences))
                    .filter(Objects::nonNull)
                    .sorted(noteComparator())
                    .toList();
            cacheMemosResult(cacheKey, notes);
            return notes;
        } catch (Exception e) {
            log.error("Failed to load Memos notes for user {}: {}", userId, e.getMessage(), e);
            throw e instanceof RuntimeException runtimeException
                    ? runtimeException
                    : new RuntimeException(e);
        }
    }

    protected NoteDto createLocalNote(UUID userId, CreateNoteRequest request) {
        UserEntity userRef = entityManager.getReference(UserEntity.class, userId);
        TimelineNoteEntity note = new TimelineNoteEntity();
        note.setUser(userRef);
        note.setTitle(normalizeTitle(request.getTitle()));
        note.setContentMarkdown(request.getContentMarkdown().trim());
        note.setSnippet(buildSnippet(request.getContentMarkdown()));
        note.setAnchorType(request.getAnchorType() != null ? request.getAnchorType() : NoteAnchorType.TIMESTAMP);
        note.setEventTime(resolveEventTime(userId, request));

        if (request.getLatitude() != null && request.getLongitude() != null) {
            note.setLocation(GeoUtils.createPoint(request.getLongitude(), request.getLatitude()));
            note.setLocationSource(NoteLocationSource.EXPLICIT);
        } else {
            note.setLocationSource(NoteLocationSource.NONE);
        }

        attachAnchor(userId, note, request.getAnchorId());
        noteRepository.persist(note);
        return mapLocalNote(note);
    }

    private CompletableFuture<NoteDto> createMemosNote(UUID userId, CreateNoteRequest request) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        MemosPreferences preferences = applyMemosDefaults(user.getMemosPreferences());
        if (!Boolean.TRUE.equals(preferences.getEnabled())
                || preferences.getServerUrl() == null || preferences.getServerUrl().isBlank()
                || preferences.getApiKey() == null || preferences.getApiKey().isBlank()) {
            throw new IllegalStateException("Memos is not configured");
        }

        Instant eventTime = resolveEventTime(userId, request);
        ResolvedLocation location = resolveCreateRequestLocation(userId, request, eventTime);
        MemosCreateMemoRequest createRequest = MemosCreateMemoRequest.builder()
                .content(request.getContentMarkdown().trim())
                .visibility(request.getVisibility() != null ? request.getVisibility() : preferences.getDefaultVisibility())
                .createTime(eventTime)
                .location(location.latitude() != null && location.longitude() != null
                        ? MemosLocation.builder()
                        .latitude(location.latitude())
                        .longitude(location.longitude())
                        .placeholder(location.placeholder())
                        .build()
                        : null)
                .build();

        MemosMemo memo = memosClient.createMemo(preferences.getServerUrl(), preferences.getApiKey(), createRequest);
        invalidateMemosCacheForUser(userId);
        NoteDto dto = mapMemosMemo(memo, preferences);
        if (dto.getLatitude() == null && location.latitude() != null) {
            dto.setLatitude(location.latitude());
            dto.setLongitude(location.longitude());
            dto.setLocationSource(location.source());
        }
        return CompletableFuture.completedFuture(dto);
    }

    private void attachAnchor(UUID userId, TimelineNoteEntity note, Long anchorId) {
        if (note.getAnchorType() == NoteAnchorType.STAY && anchorId != null) {
            TimelineStayEntity stay = stayRepository.findByIdOptional(anchorId)
                    .filter(entity -> entity.getUser() != null && userId.equals(entity.getUser().getId()))
                    .orElseThrow(() -> new NoSuchElementException("Stay not found"));
            note.setStay(stay);
            syncStayAnchor(note, stay);
            if (note.getLocation() == null && stay.getLocation() != null) {
                note.setLocation(stay.getLocation());
                note.setLocationSource(NoteLocationSource.DERIVED_STAY);
            }
        } else if (note.getAnchorType() == NoteAnchorType.TRIP && anchorId != null) {
            TimelineTripEntity trip = tripRepository.findByIdOptional(anchorId)
                    .filter(entity -> entity.getUser() != null && userId.equals(entity.getUser().getId()))
                    .orElseThrow(() -> new NoSuchElementException("Trip not found"));
            note.setTrip(trip);
            syncTripAnchor(note, trip);
            if (note.getLocation() == null) {
                ResolvedLocation location = resolveTripNoteLocation(userId, trip, note.getEventTime());
                if (location.latitude() != null && location.longitude() != null) {
                    note.setLocation(GeoUtils.createPoint(location.longitude(), location.latitude()));
                    note.setLocationSource(location.source());
                }
            }
        }
    }

    private void resolveTimelineLocations(UUID userId, Instant startTime, Instant endTime, List<NoteDto> notes) {
        if (notes.isEmpty()) {
            return;
        }

        List<TimelineStayEntity> stays = stayRepository.findByUserIdAndTimeRangeWithExpansion(userId, startTime, endTime);
        List<TimelineTripEntity> trips = tripRepository.findByUserIdAndTimeRangeWithExpansion(userId, startTime, endTime);

        for (NoteDto note : notes) {
            if (note.getLatitude() != null && note.getLongitude() != null) {
                continue;
            }
            if (note.getEventTime() == null) {
                continue;
            }

            TimelineStayEntity stay = findContainingStay(stays, note.getEventTime());
            if (stay != null && stay.getLocation() != null) {
                note.setLatitude(stay.getLocation().getY());
                note.setLongitude(stay.getLocation().getX());
                note.setLocationSource(NoteLocationSource.DERIVED_STAY);
                note.setAnchorType(NoteAnchorType.STAY);
                note.setAnchorId(stay.getId());
                continue;
            }

            TimelineTripEntity trip = findContainingTrip(trips, note.getEventTime());
            if (trip != null) {
                ResolvedLocation location = resolveTripNoteLocation(userId, trip, note.getEventTime());
                if (location.latitude() != null && location.longitude() != null) {
                    note.setLatitude(location.latitude());
                    note.setLongitude(location.longitude());
                    note.setLocationSource(location.source());
                    note.setAnchorType(NoteAnchorType.TRIP);
                    note.setAnchorId(trip.getId());
                }
            }
        }
    }

    private ResolvedLocation resolveCreateRequestLocation(UUID userId, CreateNoteRequest request, Instant eventTime) {
        if (request.getLatitude() != null && request.getLongitude() != null) {
            return new ResolvedLocation(request.getLatitude(), request.getLongitude(), null, NoteLocationSource.EXPLICIT);
        }
        if (request.getAnchorType() == NoteAnchorType.STAY && request.getAnchorId() != null) {
            return stayRepository.findByIdOptional(request.getAnchorId())
                    .filter(stay -> stay.getUser() != null && userId.equals(stay.getUser().getId()))
                    .filter(stay -> stay.getLocation() != null)
                    .map(stay -> new ResolvedLocation(
                            stay.getLocation().getY(),
                            stay.getLocation().getX(),
                            stay.getLocationName(),
                            NoteLocationSource.DERIVED_STAY
                    ))
                    .orElse(new ResolvedLocation(null, null, null, NoteLocationSource.NONE));
        }
        if (request.getAnchorType() == NoteAnchorType.TRIP && request.getAnchorId() != null) {
            return tripRepository.findByIdOptional(request.getAnchorId())
                    .filter(trip -> trip.getUser() != null && userId.equals(trip.getUser().getId()))
                    .map(trip -> resolveTripNoteLocation(userId, trip, eventTime))
                    .orElse(new ResolvedLocation(null, null, null, NoteLocationSource.NONE));
        }
        return new ResolvedLocation(null, null, null, NoteLocationSource.NONE);
    }

    private ResolvedLocation resolveTripNoteLocation(UUID userId, TimelineTripEntity trip, Instant eventTime) {
        if (trip.getStartPoint() == null || trip.getEndPoint() == null || trip.getTimestamp() == null || eventTime == null) {
            return new ResolvedLocation(null, null, null, NoteLocationSource.NONE);
        }

        Instant tripStart = trip.getTimestamp();
        Instant tripEnd = tripStart.plusSeconds(Math.max(0L, trip.getTripDuration()));
        if (eventTime.isBefore(tripStart) || eventTime.isAfter(tripEnd)) {
            eventTime = tripStart;
        }

        Optional<GpsPointEntity> before = gpsPointRepository.findLatestByUserIdAtOrBeforeTimestamp(userId, eventTime)
                .filter(point -> !point.getTimestamp().isBefore(tripStart) && !point.getTimestamp().isAfter(tripEnd));
        Optional<GpsPointEntity> after = gpsPointRepository.findEarliestByUserIdAtOrAfterTimestamp(userId, eventTime)
                .filter(point -> !point.getTimestamp().isBefore(tripStart) && !point.getTimestamp().isAfter(tripEnd));
        Optional<GpsPointEntity> nearest = chooseNearestPoint(before, after, eventTime);
        if (nearest.isPresent() && nearest.get().getCoordinates() != null) {
            long deltaSeconds = Math.abs(Duration.between(nearest.get().getTimestamp(), eventTime).getSeconds());
            if (deltaSeconds <= maxTripMarkerGpsGapSeconds) {
                return new ResolvedLocation(
                        nearest.get().getCoordinates().getY(),
                        nearest.get().getCoordinates().getX(),
                        null,
                        NoteLocationSource.DERIVED_TRIP_GPS
                );
            }
        }

        double fraction = calculateTripFraction(tripStart, tripEnd, eventTime);
        double lat = trip.getStartPoint().getY() + (trip.getEndPoint().getY() - trip.getStartPoint().getY()) * fraction;
        double lon = trip.getStartPoint().getX() + (trip.getEndPoint().getX() - trip.getStartPoint().getX()) * fraction;
        return new ResolvedLocation(lat, lon, null, NoteLocationSource.DERIVED_TRIP_INTERPOLATED);
    }

    private Optional<GpsPointEntity> chooseNearestPoint(Optional<GpsPointEntity> before, Optional<GpsPointEntity> after, Instant eventTime) {
        if (before.isEmpty()) {
            return after;
        }
        if (after.isEmpty()) {
            return before;
        }
        long beforeDelta = Math.abs(Duration.between(before.get().getTimestamp(), eventTime).getSeconds());
        long afterDelta = Math.abs(Duration.between(after.get().getTimestamp(), eventTime).getSeconds());
        return beforeDelta <= afterDelta ? before : after;
    }

    private double calculateTripFraction(Instant tripStart, Instant tripEnd, Instant eventTime) {
        long duration = Math.max(1L, Duration.between(tripStart, tripEnd).toMillis());
        long elapsed = Math.max(0L, Math.min(duration, Duration.between(tripStart, eventTime).toMillis()));
        return (double) elapsed / (double) duration;
    }

    private TimelineStayEntity findContainingStay(List<TimelineStayEntity> stays, Instant eventTime) {
        for (TimelineStayEntity stay : stays) {
            Instant start = stay.getTimestamp();
            if (containsEventTime(start, stay.getStayDuration(), eventTime)) {
                return stay;
            }
        }
        return null;
    }

    private TimelineTripEntity findContainingTrip(List<TimelineTripEntity> trips, Instant eventTime) {
        for (TimelineTripEntity trip : trips) {
            Instant start = trip.getTimestamp();
            if (containsEventTime(start, trip.getTripDuration(), eventTime)) {
                return trip;
            }
        }
        return null;
    }

    private boolean containsEventTime(Instant start, long durationSeconds, Instant eventTime) {
        if (start == null || eventTime == null) {
            return false;
        }
        if (durationSeconds <= 0) {
            return eventTime.equals(start);
        }

        Instant end = start.plusSeconds(durationSeconds);
        return !eventTime.isBefore(start) && eventTime.isBefore(end);
    }

    private List<NoteDto> filterByRadius(List<NoteDto> notes, Double latitude, Double longitude, Double radiusMeters) {
        if (latitude == null || longitude == null || radiusMeters == null || radiusMeters <= 0) {
            return notes;
        }
        return notes.stream()
                .filter(note -> note.getLatitude() != null && note.getLongitude() != null)
                .filter(note -> GeoUtils.haversine(latitude, longitude, note.getLatitude(), note.getLongitude()) <= radiusMeters)
                .toList();
    }

    private NoteDto mapLocalNote(TimelineNoteEntity note) {
        Long anchorId = null;
        if (note.getAnchorType() == NoteAnchorType.STAY && note.getStay() != null) {
            anchorId = note.getStay().getId();
        } else if (note.getAnchorType() == NoteAnchorType.TRIP && note.getTrip() != null) {
            anchorId = note.getTrip().getId();
        }

        Point location = note.getLocation();
        return NoteDto.builder()
                .id(note.getId())
                .source(NoteSource.GEOPULSE)
                .title(note.getTitle())
                .contentMarkdown(note.getContentMarkdown())
                .snippet(note.getSnippet())
                .eventTime(note.getEventTime())
                .latitude(location != null ? location.getY() : null)
                .longitude(location != null ? location.getX() : null)
                .locationSource(note.getLocationSource())
                .anchorType(note.getAnchorType())
                .anchorId(anchorId)
                .editable(true)
                .truncated(false)
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }

    private NoteDto mapMemosMemo(MemosMemo memo, MemosPreferences preferences) {
        if (memo == null || memo.getCreateTime() == null) {
            return null;
        }

        String content = memo.getContent() != null ? memo.getContent() : "";
        boolean truncated = isTooLarge(content, preferences.getMaxContentBytes());
        String renderedContent = truncated ? truncateByChars(content, preferences.getMaxContentBytes()) : content;
        MemosLocation location = memo.getLocation();

        return NoteDto.builder()
                .source(NoteSource.MEMOS)
                .externalId(resolveMemosExternalId(memo))
                .externalUrl(buildMemosExternalUrl(preferences.getServerUrl(), memo))
                .title(null)
                .contentMarkdown(renderedContent)
                .snippet(resolveSnippet(memo.getSnippet(), content))
                .eventTime(memo.getCreateTime())
                .latitude(location != null ? location.getLatitude() : null)
                .longitude(location != null ? location.getLongitude() : null)
                .locationSource(location != null && location.getLatitude() != null && location.getLongitude() != null
                        ? NoteLocationSource.EXPLICIT
                        : NoteLocationSource.NONE)
                .anchorType(NoteAnchorType.TIMESTAMP)
                .editable(false)
                .truncated(truncated)
                .createdAt(memo.getCreateTime())
                .updatedAt(memo.getUpdateTime())
                .build();
    }

    private int reattachStayNotes(UUID userId) {
        List<TimelineNoteEntity> notes = noteRepository.findUnmatchedAnchoredNotes(userId, NoteAnchorType.STAY);
        if (notes.isEmpty()) {
            return 0;
        }
        List<TimelineStayEntity> candidates = stayRepository.findByUserIdAndTimeRangeWithExpansion(
                userId,
                notes.stream().map(TimelineNoteEntity::getSourceItemStartTime).filter(Objects::nonNull).min(Instant::compareTo).orElse(Instant.EPOCH).minusSeconds(maxAnchorTimestampDeltaSeconds),
                Instant.now().plusSeconds(maxAnchorTimestampDeltaSeconds)
        );

        int count = 0;
        Set<Long> matchedStayIds = new HashSet<>();
        for (TimelineNoteEntity note : notes) {
            TimelineStayEntity best = findBestStayMatch(note, candidates, matchedStayIds);
            if (best == null) {
                continue;
            }
            note.setStay(best);
            syncStayAnchor(note, best);
            matchedStayIds.add(best.getId());
            count++;
        }
        return count;
    }

    private int reattachTripNotes(UUID userId) {
        List<TimelineNoteEntity> notes = noteRepository.findUnmatchedAnchoredNotes(userId, NoteAnchorType.TRIP);
        if (notes.isEmpty()) {
            return 0;
        }
        List<TimelineTripEntity> candidates = tripRepository.findByUser(userId);
        int count = 0;
        Set<Long> matchedTripIds = new HashSet<>();
        for (TimelineNoteEntity note : notes) {
            TimelineTripEntity best = findBestTripMatch(note, candidates, matchedTripIds);
            if (best == null) {
                continue;
            }
            note.setTrip(best);
            syncTripAnchor(note, best);
            matchedTripIds.add(best.getId());
            count++;
        }
        return count;
    }

    private TimelineStayEntity findBestStayMatch(TimelineNoteEntity note, List<TimelineStayEntity> candidates, Set<Long> matchedStayIds) {
        TimelineStayEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (TimelineStayEntity stay : candidates) {
            if (stay.getId() == null || matchedStayIds.contains(stay.getId()) || stay.getLocation() == null
                    || note.getSourceItemStartTime() == null
                    || note.getSourceStartLatitude() == null || note.getSourceStartLongitude() == null) {
                continue;
            }
            long timestampDeltaSeconds = Math.abs(Duration.between(note.getSourceItemStartTime(), stay.getTimestamp()).getSeconds());
            if (timestampDeltaSeconds > maxAnchorTimestampDeltaSeconds) {
                continue;
            }
            double distance = GeoUtils.haversine(
                    note.getSourceStartLatitude(),
                    note.getSourceStartLongitude(),
                    stay.getLocation().getY(),
                    stay.getLocation().getX()
            );
            if (distance > maxAnchorPointDistanceMeters) {
                continue;
            }
            double score = timestampDeltaSeconds + distance + Math.abs(nullToZero(note.getSourceItemDurationSeconds()) - stay.getStayDuration()) * 0.2;
            if (score < bestScore) {
                bestScore = score;
                best = stay;
            }
        }
        return best;
    }

    private TimelineTripEntity findBestTripMatch(TimelineNoteEntity note, List<TimelineTripEntity> candidates, Set<Long> matchedTripIds) {
        TimelineTripEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (TimelineTripEntity trip : candidates) {
            if (trip.getId() == null || matchedTripIds.contains(trip.getId()) || trip.getStartPoint() == null
                    || trip.getEndPoint() == null || note.getSourceItemStartTime() == null
                    || note.getSourceStartLatitude() == null || note.getSourceStartLongitude() == null
                    || note.getSourceEndLatitude() == null || note.getSourceEndLongitude() == null) {
                continue;
            }
            long timestampDeltaSeconds = Math.abs(Duration.between(note.getSourceItemStartTime(), trip.getTimestamp()).getSeconds());
            if (timestampDeltaSeconds > maxAnchorTimestampDeltaSeconds) {
                continue;
            }
            double startDistance = GeoUtils.haversine(note.getSourceStartLatitude(), note.getSourceStartLongitude(), trip.getStartPoint().getY(), trip.getStartPoint().getX());
            double endDistance = GeoUtils.haversine(note.getSourceEndLatitude(), note.getSourceEndLongitude(), trip.getEndPoint().getY(), trip.getEndPoint().getX());
            if (startDistance > maxAnchorPointDistanceMeters || endDistance > maxAnchorPointDistanceMeters) {
                continue;
            }
            double score = timestampDeltaSeconds
                    + startDistance
                    + endDistance
                    + Math.abs(nullToZero(note.getSourceItemDurationSeconds()) - trip.getTripDuration()) * 0.2
                    + Math.abs(nullToZero(note.getSourceDistanceMeters()) - trip.getDistanceMeters()) * 0.02;
            if (score < bestScore) {
                bestScore = score;
                best = trip;
            }
        }
        return best;
    }

    private void syncStayAnchor(TimelineNoteEntity note, TimelineStayEntity stay) {
        note.setSourceItemStartTime(stay.getTimestamp());
        note.setSourceItemDurationSeconds(stay.getStayDuration());
        if (stay.getLocation() != null) {
            note.setSourceStartLatitude(stay.getLocation().getY());
            note.setSourceStartLongitude(stay.getLocation().getX());
        }
    }

    private void syncTripAnchor(TimelineNoteEntity note, TimelineTripEntity trip) {
        note.setSourceItemStartTime(trip.getTimestamp());
        note.setSourceItemDurationSeconds(trip.getTripDuration());
        note.setSourceDistanceMeters(trip.getDistanceMeters());
        if (trip.getStartPoint() != null) {
            note.setSourceStartLatitude(trip.getStartPoint().getY());
            note.setSourceStartLongitude(trip.getStartPoint().getX());
        }
        if (trip.getEndPoint() != null) {
            note.setSourceEndLatitude(trip.getEndPoint().getY());
            note.setSourceEndLongitude(trip.getEndPoint().getX());
        }
    }

    private Instant resolveEventTime(UUID userId, CreateNoteRequest request) {
        if (request.getEventTime() != null) {
            return request.getEventTime();
        }
        if (request.getAnchorType() == NoteAnchorType.STAY && request.getAnchorId() != null) {
            return stayRepository.findByIdOptional(request.getAnchorId())
                    .filter(stay -> stay.getUser() != null && userId.equals(stay.getUser().getId()))
                    .map(TimelineStayEntity::getTimestamp)
                    .orElse(Instant.now());
        }
        if (request.getAnchorType() == NoteAnchorType.TRIP && request.getAnchorId() != null) {
            return tripRepository.findByIdOptional(request.getAnchorId())
                    .filter(trip -> trip.getUser() != null && userId.equals(trip.getUser().getId()))
                    .map(TimelineTripEntity::getTimestamp)
                    .orElse(Instant.now());
        }
        return Instant.now();
    }

    private NoteDestination resolveDestination(UUID userId, NoteDestination requestedDestination) {
        if (requestedDestination != null) {
            return requestedDestination;
        }
        UserEntity user = userRepository.findById(userId);
        MemosPreferences preferences = user != null ? applyMemosDefaults(user.getMemosPreferences()) : new MemosPreferences();
        if (Boolean.TRUE.equals(preferences.getEnabled()) && preferences.getDefaultSaveDestination() != null) {
            return preferences.getDefaultSaveDestination();
        }
        return NoteDestination.GEOPULSE;
    }

    private MemosConfigResponse toConfigResponse(MemosPreferences preferences) {
        return MemosConfigResponse.builder()
                .serverUrl(preferences.getServerUrl())
                .apiKey(preferences.getApiKey())
                .enabled(Boolean.TRUE.equals(preferences.getEnabled()))
                .defaultSaveDestination(preferences.getDefaultSaveDestination())
                .defaultVisibility(preferences.getDefaultVisibility())
                .maxNotesPerRequest(preferences.getMaxNotesPerRequest())
                .maxContentBytes(preferences.getMaxContentBytes())
                .build();
    }

    private MemosPreferences applyMemosDefaults(MemosPreferences preferences) {
        MemosPreferences safe = preferences != null ? preferences : new MemosPreferences();
        if (safe.getDefaultSaveDestination() == null) {
            safe.setDefaultSaveDestination(NoteDestination.GEOPULSE);
        }
        if (safe.getDefaultVisibility() == null) {
            safe.setDefaultVisibility(MemosVisibility.PRIVATE);
        }
        if (safe.getMaxNotesPerRequest() == null || safe.getMaxNotesPerRequest() <= 0) {
            safe.setMaxNotesPerRequest(DEFAULT_SEARCH_LIMIT);
        }
        if (safe.getMaxContentBytes() == null || safe.getMaxContentBytes() <= 0) {
            safe.setMaxContentBytes(64_000);
        }
        return safe;
    }

    private Comparator<NoteDto> noteComparator() {
        return Comparator.comparing(NoteDto::getEventTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(note -> note.getSource() != null ? note.getSource().name() : "")
                .thenComparing(note -> note.getId() != null ? String.valueOf(note.getId()) : String.valueOf(note.getExternalId()));
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        String trimmed = title.trim();
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }

    private String buildSnippet(String content) {
        return truncate(resolveSnippet(null, content), 500);
    }

    private String resolveSnippet(String providedSnippet, String content) {
        if (providedSnippet != null && !providedSnippet.isBlank()) {
            return truncate(providedSnippet.trim(), 500);
        }
        String normalized = content == null ? "" : content
                .replaceAll("(?s)```.*?```", " ")
                .replaceAll("[#*_`>\\[\\]()]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return truncate(normalized, 500);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)).trim() + "…";
    }

    private boolean isTooLarge(String content, int maxBytes) {
        return content != null && content.getBytes(StandardCharsets.UTF_8).length > maxBytes;
    }

    private String truncateByChars(String content, int maxBytes) {
        if (content == null) {
            return "";
        }
        int maxChars = Math.max(1000, Math.min(content.length(), maxBytes));
        return truncate(content, maxChars);
    }

    private String resolveMemosExternalId(MemosMemo memo) {
        if (memo.getName() != null && !memo.getName().isBlank()) {
            return memo.getName();
        }
        return memo.getUid();
    }

    private String buildMemosExternalUrl(String serverUrl, MemosMemo memo) {
        if (serverUrl == null || serverUrl.isBlank()) {
            return null;
        }
        String normalized = normalizeServerUrl(serverUrl);
        if (memo.getUid() != null && !memo.getUid().isBlank()) {
            return normalized + "/m/" + memo.getUid();
        }
        String externalId = resolveMemosExternalId(memo);
        return externalId == null ? normalized : normalized + "/" + externalId;
    }

    private String normalizeServerUrl(String serverUrl) {
        if (serverUrl == null || serverUrl.isBlank()) {
            return serverUrl;
        }
        String normalized = serverUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private int resolveLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return DEFAULT_SEARCH_LIMIT;
        }
        return Math.min(requestedLimit, MAX_SEARCH_LIMIT);
    }

    private int resolvePositiveOrDefault(Integer requested, Integer existing, int fallback) {
        if (requested != null && requested > 0) {
            return requested;
        }
        if (existing != null && existing > 0) {
            return existing;
        }
        return fallback;
    }

    private String resolveMemosErrorMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message != null) {
            if (message.contains("401") || message.contains("Unauthorized")) {
                return "Authentication failed";
            }
            if (message.contains("404") || message.contains("Not Found")) {
                return "Server not found";
            }
            if (message.toLowerCase(Locale.ROOT).contains("timeout") || message.contains("Connection refused")) {
                return "Connection timeout";
            }
        }
        return "Failed to connect to Memos server";
    }

    private int sanitizeCoordinatePrecision(Integer precision) {
        if (precision == null) {
            return 4;
        }
        return Math.max(3, Math.min(6, precision));
    }

    private double roundCoordinate(double value, int precision) {
        double factor = Math.pow(10, precision);
        return Math.round(value * factor) / factor;
    }

    private long nullToZero(Long value) {
        return value != null ? value : 0L;
    }

    private List<NoteDto> getCachedMemosResult(NoteSearchCacheKey cacheKey) {
        evictExpiredMemosCacheEntries();
        long nowEpochMillis = Instant.now().toEpochMilli();
        CachedMemosSearchResult cached = memosSearchCache.computeIfPresent(cacheKey, (ignored, value) -> {
            if (value.expiresAtEpochMillis() <= nowEpochMillis) {
                return null;
            }
            return value.touch(nowEpochMillis);
        });
        return cached != null ? cached.notes() : null;
    }

    private void cacheMemosResult(NoteSearchCacheKey cacheKey, List<NoteDto> notes) {
        long nowEpochMillis = Instant.now().toEpochMilli();
        long expiresAtEpochMillis = nowEpochMillis + Math.max(1L, memosSearchCacheTtlSeconds) * 1000L;
        memosSearchCache.put(cacheKey, new CachedMemosSearchResult(List.copyOf(notes), expiresAtEpochMillis, nowEpochMillis));
        evictExpiredMemosCacheEntries();
        evictMemosCacheEntriesForSizeLimit();
    }

    private void invalidateMemosCacheForUser(UUID userId) {
        memosSearchCache.entrySet().removeIf(entry -> entry.getKey().userId().equals(userId));
    }

    private void evictExpiredMemosCacheEntries() {
        long nowEpochMillis = Instant.now().toEpochMilli();
        memosSearchCache.entrySet().removeIf(entry -> entry.getValue().expiresAtEpochMillis() <= nowEpochMillis);
    }

    private void evictMemosCacheEntriesForSizeLimit() {
        int maxEntries = Math.max(1, memosSearchCacheMaxEntries);
        int overflow = memosSearchCache.size() - maxEntries;
        if (overflow <= 0) {
            return;
        }
        memosSearchCache.entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getValue().lastAccessEpochMillis()))
                .limit(overflow)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(memosSearchCache::remove);
    }

    private record ResolvedLocation(Double latitude, Double longitude, String placeholder, NoteLocationSource source) {
    }

    private record NoteSearchCacheKey(UUID userId, Instant startTime, Instant endTime, int limit) {
    }

    private record CachedMemosSearchResult(List<NoteDto> notes, long expiresAtEpochMillis, long lastAccessEpochMillis) {
        private CachedMemosSearchResult touch(long touchedAtEpochMillis) {
            return new CachedMemosSearchResult(notes, expiresAtEpochMillis, touchedAtEpochMillis);
        }
    }

    private record MapMarkerAccumulator(
            double latitude,
            double longitude,
            Instant latestEventTime,
            NoteLocationSource locationSource,
            int count,
            NoteDto singleNote
    ) {
        private MapMarkerAccumulator add(NoteDto note) {
            Instant latest = latestEventTime;
            if (latest == null || (note.getEventTime() != null && note.getEventTime().isAfter(latest))) {
                latest = note.getEventTime();
            }
            NoteLocationSource source = locationSource == note.getLocationSource()
                    ? locationSource
                    : NoteLocationSource.NONE;
            return new MapMarkerAccumulator(latitude, longitude, latest, source, count + 1, null);
        }
    }
}
