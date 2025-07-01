package org.github.tess1o.geopulse.geocoding.adapter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.geocoding.model.nominatim.NominatimAddressFormatter;
import org.github.tess1o.geopulse.geocoding.model.nominatim.NominatimResponse;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;

import java.util.List;

/**
 * Simplified adapter for converting Nominatim geocoding responses.
 * Uses the existing GeocodingAddressFormatter to create display names.
 */
@ApplicationScoped
@Slf4j
public class NominatimResponseAdapter implements GeocodingResponseAdapter<NominatimResponse> {

    private static final String PROVIDER_NAME = "Nominatim";
    private final NominatimAddressFormatter addressFormatter;

    @Inject
    public NominatimResponseAdapter(NominatimAddressFormatter addressFormatter) {
        this.addressFormatter = addressFormatter;
    }

    @Override
    public FormattableGeocodingResult adapt(NominatimResponse nominatimResponse, Point requestCoordinates, String providerName) {
        log.debug("Adapting Nominatim response: {}", nominatimResponse.getDisplayName());

        SimpleFormattableResult.SimpleFormattableResultBuilder builder = SimpleFormattableResult.builder()
                .requestCoordinates(requestCoordinates)
                .providerName(providerName);

        // Convert result coordinates if available
        if (nominatimResponse.getLat() != null && nominatimResponse.getLon() != null) {
            try {
                double lat = Double.parseDouble(nominatimResponse.getLat());
                double lon = Double.parseDouble(nominatimResponse.getLon());
                Point resultCoordinates = GeoUtils.createPoint(lon, lat);
                builder.resultCoordinates(resultCoordinates);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse coordinates from Nominatim response: lat={}, lon={}",
                        nominatimResponse.getLat(), nominatimResponse.getLon());
                builder.resultCoordinates(requestCoordinates); // Fallback to request coordinates
            }
        } else {
            builder.resultCoordinates(requestCoordinates);
        }

        // Convert bounding box if available
        if (nominatimResponse.getBoundingbox() != null && nominatimResponse.getBoundingbox().size() >= 4) {
            builder.boundingBox(convertBoundingBox(nominatimResponse.getBoundingbox()));
        }

        // Format display name using existing formatter
        double longitude = requestCoordinates.getX();
        double latitude = requestCoordinates.getY();
        String formattedDisplayName = addressFormatter.formatAddress(nominatimResponse, longitude, latitude);
        builder.formattedDisplayName(formattedDisplayName);

        // Extract city and country from address
        if (nominatimResponse.getAddress() != null) {
            String city = nominatimResponse.getAddress().getCity();
            if (city == null || city.isBlank()) {
                city = nominatimResponse.getAddress().getTown();
            }
            builder.city(city);
            builder.country(nominatimResponse.getAddress().getCountry());
        }

        return builder.build();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    private Polygon convertBoundingBox(List<Double> boundingbox) {
        try {
            // Nominatim bounding box format: [minlat, maxlat, minlon, maxlon]
            double minLat = boundingbox.get(0);
            double maxLat = boundingbox.get(1);
            double minLon = boundingbox.get(2);
            double maxLon = boundingbox.get(3);

            return GeoUtils.buildBoundingBoxPolygon(minLat, maxLat, minLon, maxLon);
        } catch (Exception e) {
            log.warn("Failed to convert bounding box: {}", boundingbox, e);
            return null;
        }
    }
}