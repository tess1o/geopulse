package org.github.tess1o.geopulse.ai.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.ai.model.AITripDetailsDTO;
import org.github.tess1o.geopulse.ai.model.AIWeatherDTO;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.geofencing.model.dto.GeofenceEventDto;
import org.github.tess1o.geopulse.geofencing.model.dto.GeofenceEventQueryDto;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventType;
import org.github.tess1o.geopulse.geofencing.service.GeofenceEventService;
import org.github.tess1o.geopulse.gps.model.GpsStatusDTO;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.immich.model.ImmichPhotoDto;
import org.github.tess1o.geopulse.immich.model.ImmichPhotoSearchRequest;
import org.github.tess1o.geopulse.immich.model.ImmichPhotoSearchResponse;
import org.github.tess1o.geopulse.immich.service.ImmichService;
import org.github.tess1o.geopulse.notes.model.NoteSearchResponse;
import org.github.tess1o.geopulse.notes.service.TimelineNoteService;
import org.github.tess1o.geopulse.shared.api.ApiErrorCode;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.shared.api.PageResponse;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEventExplanationDetailsDTO;
import org.github.tess1o.geopulse.streaming.service.explanation.TimelineEventExplanationService;
import org.github.tess1o.geopulse.timelinelabels.model.dto.TimelineLabelDto;
import org.github.tess1o.geopulse.timelinelabels.service.TimelineLabelService;
import org.github.tess1o.geopulse.trips.model.dto.TripDto;
import org.github.tess1o.geopulse.trips.service.TripPlanItemService;
import org.github.tess1o.geopulse.trips.service.TripService;
import org.github.tess1o.geopulse.trips.service.TripSummaryService;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.weather.dto.WeatherSampleDTO;
import org.github.tess1o.geopulse.weather.dto.WeatherSamplesResponse;
import org.github.tess1o.geopulse.weather.service.WeatherService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.github.tess1o.geopulse.ai.service.AIToolArguments.await;
import static org.github.tess1o.geopulse.ai.service.AIToolArguments.endOfDay;
import static org.github.tess1o.geopulse.ai.service.AIToolArguments.invalid;
import static org.github.tess1o.geopulse.ai.service.AIToolArguments.limit;
import static org.github.tess1o.geopulse.ai.service.AIToolArguments.optionalDate;
import static org.github.tess1o.geopulse.ai.service.AIToolArguments.optionalEnum;
import static org.github.tess1o.geopulse.ai.service.AIToolArguments.requireDate;
import static org.github.tess1o.geopulse.ai.service.AIToolArguments.requireOrdered;
import static org.github.tess1o.geopulse.ai.service.AIToolArguments.startOfDay;
import static org.github.tess1o.geopulse.ai.service.AIToolArguments.validateCircle;
import static org.github.tess1o.geopulse.ai.service.AIToolArguments.zone;

/**
 * AI tools for the context around the timeline: planned trips, timeline labels, notes, photos, weather,
 * geofence events, why the timeline looks as it does, and whether GPS data is still arriving.
 */
@Slf4j
@ApplicationScoped
public class AIContextTools {

    static final int DEFAULT_NOTES_LIMIT = 50;
    static final int DEFAULT_PHOTOS_LIMIT = 50;
    static final int MAX_NOTES_OR_PHOTOS_LIMIT = 200;
    static final int DEFAULT_WEATHER_SAMPLES = 100;
    static final int MAX_WEATHER_SAMPLES = 500;
    static final int DEFAULT_GEOFENCE_EVENTS = 25;
    static final int MAX_GEOFENCE_EVENTS = 200;
    private static final double KM_PER_DEGREE_LATITUDE = 111.32;
    private static final List<String> TRIP_STATUSES = List.of("UNPLANNED", "UPCOMING", "ACTIVE", "COMPLETED", "CANCELLED");
    private static final List<String> EVENT_KINDS = List.of("STAY", "TRIP", "DATA_GAP");
    private static final List<String> GEOFENCE_EVENT_TYPES = List.of("ENTER", "LEAVE");

    private final CurrentUserService currentUserService;
    private final TripService tripService;
    private final TripSummaryService tripSummaryService;
    private final TripPlanItemService tripPlanItemService;
    private final TimelineLabelService timelineLabelService;
    private final TimelineNoteService timelineNoteService;
    private final ImmichService immichService;
    private final WeatherService weatherService;
    private final GeofenceEventService geofenceEventService;
    private final TimelineEventExplanationService explanationService;
    private final GpsPointService gpsPointService;

    @Inject
    public AIContextTools(CurrentUserService currentUserService,
                          TripService tripService,
                          TripSummaryService tripSummaryService,
                          TripPlanItemService tripPlanItemService,
                          TimelineLabelService timelineLabelService,
                          TimelineNoteService timelineNoteService,
                          ImmichService immichService,
                          WeatherService weatherService,
                          GeofenceEventService geofenceEventService,
                          TimelineEventExplanationService explanationService,
                          GpsPointService gpsPointService) {
        this.currentUserService = currentUserService;
        this.tripService = tripService;
        this.tripSummaryService = tripSummaryService;
        this.tripPlanItemService = tripPlanItemService;
        this.timelineLabelService = timelineLabelService;
        this.timelineNoteService = timelineNoteService;
        this.immichService = immichService;
        this.weatherService = weatherService;
        this.geofenceEventService = geofenceEventService;
        this.explanationService = explanationService;
        this.gpsPointService = gpsPointService;
    }

    public List<TripDto> listTrips(String status) {
        log.info("🔧 AI TOOL EXECUTED: listTrips({})", status);
        return tripService.getTrips(currentUserService.getCurrentUserId(),
                optionalEnum(status, "status", TRIP_STATUSES));
    }

    public AITripDetailsDTO getTripDetails(Long tripId) {
        log.info("🔧 AI TOOL EXECUTED: getTripDetails({})", tripId);
        if (tripId == null) {
            throw invalid("tripId is required", "tripId");
        }
        UUID userId = currentUserService.getCurrentUserId();
        try {
            return new AITripDetailsDTO(
                    tripService.getTrip(userId, tripId),
                    tripSummaryService.getSummary(userId, tripId),
                    tripPlanItemService.getTripPlanItems(userId, tripId));
        } catch (GeoPulseException e) {
            if (e.code() != ApiErrorCode.TRIP_NOT_FOUND) {
                throw e;
            }
            throw new AIToolException("TRIP_NOT_FOUND", "Trip not found. Use listTrips to find trip ids.",
                    Map.of("tripId", tripId), e);
        }
    }

    public List<TimelineLabelDto> listTimelineLabels(String startDate, String endDate) {
        log.info("🔧 AI TOOL EXECUTED: listTimelineLabels({}, {})", startDate, endDate);
        LocalDate start = optionalDate(startDate, "startDate");
        LocalDate end = optionalDate(endDate, "endDate");
        UserEntity user = currentUserService.getCurrentUser();
        if (start == null && end == null) {
            return timelineLabelService.getTimelineLabels(user.getId());
        }
        if (start == null || end == null) {
            throw invalid("startDate and endDate must be given together", start == null ? "startDate" : "endDate");
        }
        requireOrdered(start, end);
        ZoneId zone = zone(user.getTimezone());
        return timelineLabelService.getTimelineLabelsForTimeRange(user.getId(),
                startOfDay(start, zone), endOfDay(end, zone));
    }

    public NoteSearchResponse searchNotes(String startDate, String endDate, Double latitude, Double longitude,
                                          Double radiusMeters, Integer limit) {
        log.info("🔧 AI TOOL EXECUTED: searchNotes({}, {}, {}, {}, {}, {})",
                startDate, endDate, latitude, longitude, radiusMeters, limit);
        LocalDate start = optionalDate(startDate, "startDate");
        LocalDate end = optionalDate(endDate, "endDate");
        requireOrdered(start, end);
        validateCircle(latitude, longitude, radiusMeters, "radiusMeters");
        UserEntity user = currentUserService.getCurrentUser();
        ZoneId zone = zone(user.getTimezone());
        return await(timelineNoteService.searchNotes(user.getId(),
                start == null ? Instant.EPOCH : startOfDay(start, zone),
                end == null ? Instant.now() : endOfDay(end, zone),
                true, limit(limit, DEFAULT_NOTES_LIMIT, MAX_NOTES_OR_PHOTOS_LIMIT),
                latitude, longitude, radiusMeters));
    }

    public ImmichPhotoSearchResponse searchPhotos(String startDate, String endDate, Double latitude,
                                                  Double longitude, Double radiusMeters, String city,
                                                  String country, Integer limit) {
        log.info("🔧 AI TOOL EXECUTED: searchPhotos({}, {}, {}, {}, {}, {}, {}, {})",
                startDate, endDate, latitude, longitude, radiusMeters, city, country, limit);
        LocalDate start = requireDate(startDate, "startDate");
        LocalDate end = requireDate(endDate, "endDate");
        requireOrdered(start, end);
        validateCircle(latitude, longitude, radiusMeters, "radiusMeters");
        UserEntity user = currentUserService.getCurrentUser();
        // Without Immich the service returns no photos, which the model would read as "no photos were taken".
        if (user.getImmichPreferences() == null || !Boolean.TRUE.equals(user.getImmichPreferences().getEnabled())) {
            throw new AIToolException("IMMICH_NOT_CONFIGURED",
                    "Immich is not connected for this user. It can be set up in GeoPulse's profile settings.");
        }
        ZoneId zone = zone(user.getTimezone());

        ImmichPhotoSearchRequest request = new ImmichPhotoSearchRequest();
        request.setStartDate(start.atStartOfDay(zone).toOffsetDateTime());
        request.setEndDate(end.plusDays(1).atStartOfDay(zone).minusNanos(1_000_000).toOffsetDateTime());
        request.setLatitude(latitude);
        request.setLongitude(longitude);
        request.setRadiusMeters(radiusMeters);
        request.setCity(blankToNull(city));
        request.setCountry(blankToNull(country));
        request.setLimit(limit(limit, DEFAULT_PHOTOS_LIMIT, MAX_NOTES_OR_PHOTOS_LIMIT));

        ImmichPhotoSearchResponse response = await(immichService.searchPhotos(user.getId(), request));
        // The image URLs are GeoPulse API paths that need the user's session, so they are no use to the model.
        List<ImmichPhotoDto> photos = Optional.ofNullable(response.getPhotos()).orElse(List.of()).stream()
                .map(photo -> photo.toBuilder().thumbnailUrl(null).previewUrl(null).downloadUrl(null).build())
                .toList();
        return ImmichPhotoSearchResponse.builder().photos(photos).totalCount(response.getTotalCount()).build();
    }

    public AIWeatherDTO getWeather(String startDate, String endDate, Double latitude, Double longitude,
                                   Double radiusKm, Integer limit) {
        log.info("🔧 AI TOOL EXECUTED: getWeather({}, {}, {}, {}, {}, {})",
                startDate, endDate, latitude, longitude, radiusKm, limit);
        LocalDate start = requireDate(startDate, "startDate");
        LocalDate end = requireDate(endDate, "endDate");
        requireOrdered(start, end);
        validateCircle(latitude, longitude, radiusKm, "radiusKm");
        UserEntity user = currentUserService.getCurrentUser();
        ZoneId zone = zone(user.getTimezone());

        Double minLat = null, minLon = null, maxLat = null, maxLon = null;
        if (latitude != null) {
            double latDelta = radiusKm / KM_PER_DEGREE_LATITUDE;
            double lonDelta = radiusKm / (KM_PER_DEGREE_LATITUDE * Math.max(Math.cos(Math.toRadians(latitude)), 0.01));
            minLat = Math.max(latitude - latDelta, -90);
            maxLat = Math.min(latitude + latDelta, 90);
            minLon = Math.max(longitude - lonDelta, -180);
            maxLon = Math.min(longitude + lonDelta, 180);
        }

        WeatherSamplesResponse response = weatherService.findSamples(user.getId(),
                startOfDay(start, zone), endOfDay(end, zone), minLat, minLon, maxLat, maxLon);
        if (!response.isEnabled() || !response.isConfigured()) {
            throw new AIToolException("WEATHER_NOT_CONFIGURED",
                    "The weather integration is not enabled on this GeoPulse server.");
        }
        List<WeatherSampleDTO> samples = Optional.ofNullable(response.getSamples()).orElse(List.of());
        int max = limit(limit, DEFAULT_WEATHER_SAMPLES, MAX_WEATHER_SAMPLES);
        return new AIWeatherDTO(response.getProvider(), response.getAttributionUrl(), response.getUnits(),
                thinEvenly(samples, max), samples.size(), samples.size() > max);
    }

    public PageResponse<GeofenceEventDto> listGeofenceEvents(String startDate, String endDate, String eventType,
                                                             Boolean unreadOnly, Integer limit) {
        log.info("🔧 AI TOOL EXECUTED: listGeofenceEvents({}, {}, {}, {}, {})",
                startDate, endDate, eventType, unreadOnly, limit);
        LocalDate start = optionalDate(startDate, "startDate");
        LocalDate end = optionalDate(endDate, "endDate");
        requireOrdered(start, end);
        String type = optionalEnum(eventType, "eventType", GEOFENCE_EVENT_TYPES);
        UserEntity user = currentUserService.getCurrentUser();
        ZoneId zone = zone(user.getTimezone());

        GeofenceEventQueryDto query = GeofenceEventQueryDto.builder()
                .page(0)
                .pageSize(limit(limit, DEFAULT_GEOFENCE_EVENTS, MAX_GEOFENCE_EVENTS))
                .sortBy("occurredAt")
                .sortDir("desc")
                .unreadOnly(Boolean.TRUE.equals(unreadOnly))
                .dateFrom(start == null ? null : startOfDay(start, zone))
                .dateTo(end == null ? null : endOfDay(end, zone))
                .subjectUserIds(List.of())
                .eventTypes(type == null ? List.of() : List.of(GeofenceEventType.valueOf(type)))
                .build();
        return geofenceEventService.listEventsPage(user.getId(), query);
    }

    public TimelineEventExplanationDetailsDTO explainTimelineEvent(String kind, Long id) {
        log.info("🔧 AI TOOL EXECUTED: explainTimelineEvent({}, {})", kind, id);
        String eventKind = optionalEnum(kind, "kind", EVENT_KINDS);
        if (eventKind == null) {
            throw invalid("kind is required: STAY, TRIP or DATA_GAP", "kind");
        }
        if (id == null) {
            throw invalid("id is required", "id");
        }
        UUID userId = currentUserService.getCurrentUserId();
        Optional<TimelineEventExplanationDetailsDTO> explanation = switch (eventKind) {
            case "STAY" -> explanationService.explainStay(id, userId);
            case "TRIP" -> explanationService.explainTrip(id, userId);
            default -> explanationService.explainDataGap(id, userId);
        };
        return explanation.orElseThrow(() -> new AIToolException("TIMELINE_EVENT_NOT_FOUND",
                "Timeline event not found. Use the id of a stay or trip returned by queryTimeline.",
                Map.of("kind", eventKind, "id", id)));
    }

    public GpsStatusDTO getGpsStatus() {
        log.info("🔧 AI TOOL EXECUTED: getGpsStatus()");
        return gpsPointService.getGpsStatus(currentUserService.getCurrentUserId());
    }

    /** Keeps {@code max} items spread evenly over the list, always including the first and last. */
    static <T> List<T> thinEvenly(List<T> items, int max) {
        if (items.size() <= max) {
            return items;
        }
        if (max == 1) {
            return List.of(items.get(0));
        }
        List<T> result = new ArrayList<>(max);
        double step = (items.size() - 1) / (double) (max - 1);
        for (int i = 0; i < max; i++) {
            result.add(items.get((int) Math.round(i * step)));
        }
        return result;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
