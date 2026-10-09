package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.geocoding.dto.LocationTimezoneDTO;
import org.github.tess1o.geopulse.geocoding.service.LocationTimezoneService;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineDataGapDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineTripDTO;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Adds the local timezone of each stay, trip endpoint and data gap to an assembled timeline.
 *
 * <p>Stays and trip endpoints are resolved together in one query. Data gaps have no coordinates, so each gap
 * takes the zone of the item that ends right before it and the item that starts right after it.</p>
 */
@ApplicationScoped
public class TimelineLocationTimezoneEnricher {

    private final LocationTimezoneService locationTimezoneService;

    @Inject
    public TimelineLocationTimezoneEnricher(LocationTimezoneService locationTimezoneService) {
        this.locationTimezoneService = locationTimezoneService;
    }

    public void enrich(MovementTimelineDTO timeline) {
        if (!locationTimezoneService.isEnabled()) {
            return;
        }

        List<TimelineStayLocationDTO> stays = timeline.getStays();
        List<TimelineTripDTO> trips = timeline.getTrips();
        int size = stays.size() + trips.size() * 2;
        double[] latitudes = new double[size];
        double[] longitudes = new double[size];

        int index = 0;
        for (TimelineStayLocationDTO stay : stays) {
            latitudes[index] = stay.getLatitude();
            longitudes[index++] = stay.getLongitude();
        }
        for (TimelineTripDTO trip : trips) {
            latitudes[index] = trip.getLatitude();
            longitudes[index++] = trip.getLongitude();
            latitudes[index] = trip.getEndLatitude();
            longitudes[index++] = trip.getEndLongitude();
        }

        List<LocationTimezoneDTO> zones = locationTimezoneService.resolve(latitudes, longitudes, "timeline");

        index = 0;
        for (TimelineStayLocationDTO stay : stays) {
            stay.setLocationTimezone(zones.get(index++));
        }
        for (TimelineTripDTO trip : trips) {
            trip.setStartLocationTimezone(zones.get(index++));
            trip.setEndLocationTimezone(zones.get(index++));
        }

        assignDataGapTimezones(timeline);
    }

    static void assignDataGapTimezones(MovementTimelineDTO timeline) {
        if (timeline.getDataGaps().isEmpty()) {
            return;
        }

        // Zone in effect when an item ends / starts, keyed by that instant.
        TreeMap<Instant, LocationTimezoneDTO> zoneAtItemEnd = new TreeMap<>();
        TreeMap<Instant, LocationTimezoneDTO> zoneAtItemStart = new TreeMap<>();
        for (TimelineStayLocationDTO stay : timeline.getStays()) {
            if (stay.getTimestamp() == null || stay.getLocationTimezone() == null) {
                continue;
            }
            zoneAtItemStart.put(stay.getTimestamp(), stay.getLocationTimezone());
            zoneAtItemEnd.put(stay.getTimestamp().plusSeconds(stay.getStayDuration()), stay.getLocationTimezone());
        }
        for (TimelineTripDTO trip : timeline.getTrips()) {
            if (trip.getTimestamp() == null) {
                continue;
            }
            if (trip.getStartLocationTimezone() != null) {
                zoneAtItemStart.put(trip.getTimestamp(), trip.getStartLocationTimezone());
            }
            if (trip.getEndLocationTimezone() != null) {
                zoneAtItemEnd.put(trip.getTimestamp().plusSeconds(trip.getTripDuration()),
                        trip.getEndLocationTimezone());
            }
        }

        for (TimelineDataGapDTO gap : timeline.getDataGaps()) {
            if (gap.getStartTime() != null) {
                Map.Entry<Instant, LocationTimezoneDTO> before = zoneAtItemEnd.floorEntry(gap.getStartTime());
                gap.setStartLocationTimezone(before != null ? before.getValue() : null);
            }
            if (gap.getEndTime() != null) {
                Map.Entry<Instant, LocationTimezoneDTO> after = zoneAtItemStart.ceilingEntry(gap.getEndTime());
                gap.setEndLocationTimezone(after != null ? after.getValue() : null);
            }
        }
    }
}
