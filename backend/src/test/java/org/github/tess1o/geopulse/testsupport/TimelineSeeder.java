package org.github.tess1o.geopulse.testsupport;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceDeliveryStatus;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventType;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleStatus;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.notes.model.TimelineNoteEntity;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.streaming.model.domain.LocationSource;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.weather.model.WeatherSampleEntity;
import org.github.tess1o.geopulse.weather.model.WeatherTargetSource;

import java.time.Instant;

/**
 * Persists timeline entities with exact values, so read services can be checked against numbers worked out by
 * hand. Each method joins the caller's transaction or starts its own.
 */
@ApplicationScoped
public class TimelineSeeder {

    @Inject
    EntityManager entityManager;

    @Transactional
    public UserEntity user(String prefix, String timezone) {
        UserEntity user = UserEntity.builder()
                .email(TestIds.uniqueEmail(prefix))
                .fullName("Seeded " + prefix)
                .role(Role.USER)
                .isActive(true)
                .emailVerified(false)
                .passwordHash("test")
                .timezone(timezone)
                .build();
        entityManager.persist(user);
        return user;
    }

    @Transactional
    public FavoritesEntity favorite(UserEntity user, String name, String city, String country, double lat, double lon) {
        FavoritesEntity favorite = FavoritesEntity.builder()
                .user(user)
                .name(name)
                .city(city)
                .country(country)
                .type(FavoriteLocationType.POINT)
                .geometry(GeoUtils.createPoint(lon, lat))
                .mergeImpact(Boolean.FALSE)
                .build();
        entityManager.persist(favorite);
        return favorite;
    }

    /** A reverse-geocoding result owned by {@code user}, so other users' searches never see it. */
    @Transactional
    public ReverseGeocodingLocationEntity geocoding(UserEntity user, String displayName, String city, String country,
                                                    double lat, double lon) {
        ReverseGeocodingLocationEntity location = new ReverseGeocodingLocationEntity();
        location.setUser(user);
        location.setDisplayName(displayName);
        location.setCity(city);
        location.setCountry(country);
        location.setProviderName("test");
        location.setRequestCoordinates(GeoUtils.createPoint(lon, lat));
        location.setResultCoordinates(GeoUtils.createPoint(lon, lat));
        entityManager.persist(location);
        return location;
    }

    @Transactional
    public TimelineStayEntity stay(UserEntity user, FavoritesEntity favorite, Instant start, long durationSeconds) {
        double lon = favorite.getGeometry().getCoordinate().x;
        double lat = favorite.getGeometry().getCoordinate().y;
        TimelineStayEntity stay = TimelineStayEntity.builder()
                .user(user)
                .timestamp(start)
                .stayDuration(durationSeconds)
                .location(GeoUtils.createPoint(lon, lat))
                .locationName(favorite.getName())
                .favoriteLocation(favorite)
                .locationSource(LocationSource.FAVORITE)
                .build();
        entityManager.persist(stay);
        return stay;
    }

    @Transactional
    public TimelineStayEntity stay(UserEntity user, ReverseGeocodingLocationEntity location, Instant start,
                                   long durationSeconds) {
        TimelineStayEntity stay = TimelineStayEntity.builder()
                .user(user)
                .timestamp(start)
                .stayDuration(durationSeconds)
                .location(location.getRequestCoordinates())
                .locationName(location.getDisplayName())
                .geocodingLocation(location)
                .locationSource(LocationSource.GEOCODING)
                .build();
        entityManager.persist(stay);
        return stay;
    }

    @Transactional
    public TimelineTripEntity trip(UserEntity user, Instant start, long distanceMeters, long durationSeconds,
                                   String movementType) {
        TimelineTripEntity trip = TimelineTripEntity.builder()
                .user(user)
                .timestamp(start)
                .distanceMeters(distanceMeters)
                .tripDuration(durationSeconds)
                .movementType(movementType)
                .startPoint(GeoUtils.createPoint(30.5234, 50.4501))
                .endPoint(GeoUtils.createPoint(30.5300, 50.4550))
                .build();
        entityManager.persist(trip);
        return trip;
    }

    @Transactional
    public GpsPointEntity gpsPoint(UserEntity user, Instant timestamp, double lat, double lon, Double accuracy) {
        GpsPointEntity point = new GpsPointEntity();
        point.setUser(user);
        point.setTimestamp(timestamp);
        point.setCoordinates(GeoUtils.createPoint(lon, lat));
        point.setAccuracy(accuracy);
        point.setVelocity(0.0);
        point.setSourceType(GpsSourceType.OWNTRACKS);
        point.setDeviceId("seeded-device");
        point.setCreatedAt(timestamp);
        entityManager.persist(point);
        return point;
    }

    @Transactional
    public TimelineNoteEntity note(UserEntity user, String title, Instant eventTime, Double lat, Double lon) {
        TimelineNoteEntity note = TimelineNoteEntity.builder()
                .user(user)
                .title(title)
                .contentMarkdown("Content of " + title)
                .snippet(title)
                .eventTime(eventTime)
                .location(lat == null ? null : GeoUtils.createPoint(lon, lat))
                .build();
        entityManager.persist(note);
        return note;
    }

    @Transactional
    public WeatherSampleEntity weatherSample(UserEntity user, Instant observedAt, double lat, double lon,
                                             double temperature) {
        WeatherSampleEntity sample = WeatherSampleEntity.builder()
                .user(user)
                .provider("open-meteo")
                .source(WeatherTargetSource.ONGOING)
                .requestedLatitude(lat)
                .requestedLongitude(lon)
                .latitudeBucket(lat)
                .longitudeBucket(lon)
                .observedAt(observedAt)
                .temperature(temperature)
                .build();
        entityManager.persist(sample);
        return sample;
    }

    @Transactional
    public GeofenceRuleEntity geofenceRule(UserEntity owner, String name) {
        GeofenceRuleEntity rule = GeofenceRuleEntity.builder()
                .ownerUser(owner)
                .name(name)
                .northEastLat(50.5)
                .northEastLon(30.6)
                .southWestLat(50.4)
                .southWestLon(30.4)
                .monitorEnter(true)
                .monitorLeave(true)
                .cooldownSeconds(120)
                .status(GeofenceRuleStatus.ACTIVE)
                .build();
        entityManager.persist(rule);
        return rule;
    }

    @Transactional
    public GeofenceEventEntity geofenceEvent(UserEntity owner, GeofenceRuleEntity rule, GeofenceEventType type,
                                             String title, Instant occurredAt) {
        GeofenceEventEntity event = GeofenceEventEntity.builder()
                .ownerUser(owner)
                .subjectUser(owner)
                .subjectDisplayName(owner.getFullName())
                .rule(rule)
                .eventType(type)
                .occurredAt(occurredAt)
                .title(title)
                .message("Message for " + title)
                .deliveryStatus(GeofenceDeliveryStatus.PENDING)
                .deliveryAttempts(0)
                .createdAt(occurredAt)
                .build();
        entityManager.persist(event);
        return event;
    }
}
