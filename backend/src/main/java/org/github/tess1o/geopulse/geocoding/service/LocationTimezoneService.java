package org.github.tess1o.geopulse.geocoding.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.geocoding.dto.LocationTimezoneDTO;
import org.github.tess1o.geopulse.geocoding.dto.LocationTimezoneDTO.Status;
import org.github.tess1o.geopulse.geocoding.model.GeonamesNearestCity;
import org.github.tess1o.geopulse.geocoding.repository.GeonamesTimezoneRepository;
import org.github.tess1o.geopulse.prometheus.GeoPulseWorkloadMetrics;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.ToDoubleFunction;

/**
 * Resolves the local timezone of coordinates from the nearest GeoNames city.
 *
 * <p>Display-only: nothing here is persisted, and callers only use it when a client explicitly asks for location
 * timezones, so the default behaviour of every endpoint is unchanged.</p>
 */
@ApplicationScoped
@Slf4j
public class LocationTimezoneService {

    private static final double METERS_PER_KM = 1000.0;

    @ConfigProperty(name = "geopulse.timezone.location.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "geopulse.timezone.location.max-distance-km", defaultValue = "200")
    double maxDistanceKm;

    private final GeonamesTimezoneRepository repository;
    private final GeoPulseWorkloadMetrics workloadMetrics;
    // Only found countries are cached, so a lookup made before GeoNames finished importing is retried later.
    private final Map<String, String> countryByTimezone = new ConcurrentHashMap<>();

    @Inject
    public LocationTimezoneService(GeonamesTimezoneRepository repository, GeoPulseWorkloadMetrics workloadMetrics) {
        this.repository = repository;
        this.workloadMetrics = workloadMetrics;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getMaxDistanceKm() {
        return maxDistanceKm;
    }

    /** Enabled on this server and GeoNames cities are loaded. */
    public boolean isAvailable() {
        return enabled && repository.hasAnyCity();
    }

    /**
     * Resolves each coordinate, in input order, with a single query. Returns {@code null} elements for every input
     * when the feature is disabled on this server.
     *
     * @param source metric tag naming the caller, e.g. {@code timeline} or {@code gps_points}
     */
    public List<LocationTimezoneDTO> resolve(double[] latitudes, double[] longitudes, String source) {
        List<LocationTimezoneDTO> result = new ArrayList<>(latitudes.length);
        if (!enabled) {
            for (int i = 0; i < latitudes.length; i++) {
                result.add(null);
            }
            return result;
        }
        if (latitudes.length == 0) {
            return result;
        }

        long started = workloadMetrics.start();
        List<GeonamesNearestCity> nearest = repository.findNearestCities(latitudes, longitudes);
        workloadMetrics.recordTimer("geopulse.timezone.location.lookup.duration", started, "source", source);

        Map<Status, Integer> unresolved = new EnumMap<>(Status.class);
        for (GeonamesNearestCity city : nearest) {
            LocationTimezoneDTO dto = toDto(city);
            if (dto.getStatus() != Status.RESOLVED) {
                unresolved.merge(dto.getStatus(), 1, Integer::sum);
            }
            result.add(dto);
        }

        unresolved.forEach((status, count) -> workloadMetrics.increment(
                "geopulse.timezone.location.unresolved", count, "source", source, "reason", status.name()));
        if (log.isDebugEnabled()) {
            log.debug("Resolved location timezones for {} {} coordinates in {} ms, unresolved: {}",
                    latitudes.length, source, (System.nanoTime() - started) / 1_000_000, unresolved);
        }
        return result;
    }

    /**
     * Resolves the zone of every item with one query and hands each result to {@code setter}, in order.
     * The setter receives {@code null} when the feature is disabled on this server.
     */
    public <T> void assign(List<T> items, ToDoubleFunction<T> latitude, ToDoubleFunction<T> longitude,
                           BiConsumer<T, LocationTimezoneDTO> setter, String source) {
        if (items == null || items.isEmpty()) {
            return;
        }
        double[] latitudes = new double[items.size()];
        double[] longitudes = new double[items.size()];
        for (int i = 0; i < items.size(); i++) {
            latitudes[i] = latitude.applyAsDouble(items.get(i));
            longitudes[i] = longitude.applyAsDouble(items.get(i));
        }
        List<LocationTimezoneDTO> zones = resolve(latitudes, longitudes, source);
        for (int i = 0; i < items.size(); i++) {
            setter.accept(items.get(i), zones.get(i));
        }
    }

    /** Resolves one coordinate. {@code null} when the feature is disabled on this server. */
    public LocationTimezoneDTO resolve(double latitude, double longitude, String source) {
        return resolve(new double[]{latitude}, new double[]{longitude}, source).getFirst();
    }

    /**
     * ISO country of a profile timezone (the country most GeoNames cities in that zone belong to), used by clients
     * to tell "abroad" from "home". Empty for UTC, offsets, or when GeoNames has no city in the zone.
     */
    public Optional<String> findCountryForTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return Optional.empty();
        }
        String zone = timezone.trim();
        String cached = countryByTimezone.get(zone);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<String> country = repository.findPrimaryCountryCode(zoneAliases(zone));
        country.ifPresent(code -> countryByTimezone.put(zone, code));
        return country;
    }

    /** The zone plus its renamed counterpart, since GeoNames and user profiles may use either spelling. */
    private static List<String> zoneAliases(String zone) {
        return switch (zone) {
            case "Europe/Kyiv" -> List.of("Europe/Kyiv", "Europe/Kiev");
            case "Europe/Kiev" -> List.of("Europe/Kiev", "Europe/Kyiv");
            default -> List.of(zone);
        };
    }

    LocationTimezoneDTO toDto(GeonamesNearestCity city) {
        if (city == null) {
            return LocationTimezoneDTO.builder().status(Status.NO_GEONAMES_DATA).build();
        }

        LocationTimezoneDTO.LocationTimezoneDTOBuilder builder = LocationTimezoneDTO.builder()
                .nearestCity(city.name())
                .countryCode(city.countryCode() != null ? city.countryCode().trim() : null)
                .distanceKm(Math.round(city.distanceMeters() / 100.0) / 10.0);

        if (city.distanceMeters() > maxDistanceKm * METERS_PER_KM) {
            return builder.status(Status.BEYOND_MAX_DISTANCE).build();
        }

        String zoneId = validZoneId(city.timezone());
        if (zoneId == null) {
            return builder.status(Status.INVALID_TIMEZONE).build();
        }
        return builder.timezone(zoneId).status(Status.RESOLVED).build();
    }

    private static String validZoneId(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return null;
        }
        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (DateTimeException e) {
            return null;
        }
    }
}
