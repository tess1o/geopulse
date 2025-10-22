package org.github.tess1o.geopulse.geocoding.adapter;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.geocoding.model.photon.PhotonResponse;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.util.List;


/**
 * Simplified adapter for converting Photon geocoding responses.
 * Uses the existing GeocodingAddressFormatter to create display names.
 */
@ApplicationScoped
@Slf4j
public class PhotonResponseAdapter implements GeocodingResponseAdapter<PhotonResponse> {

    private static final String PROVIDER_NAME = "Photon";


    @Override
    public FormattableGeocodingResult adapt(PhotonResponse response, Point requestCoordinates, String providerName) {
        log.debug("Adapting Photon response: {}", response);

        if (response == null || response.getFeatures() == null || response.getFeatures().isEmpty()) {
            log.warn("Empty or null Photon response");
            return null;
        }

        PhotonResponse.Feature feature = response.getFeatures().get(0);
        PhotonResponse.Properties props = feature.getProperties();
        PhotonResponse.Geometry geom = feature.getGeometry();

        return SimpleFormattableResult.builder()
                .requestCoordinates(requestCoordinates)
                .resultCoordinates(createPoint(geom))
                .boundingBox(convertBoundingBox(props.getExtent()))
                .formattedDisplayName(formatAddress(props))
                .providerName(PROVIDER_NAME)
                .city(extractCity(props))
                .country(props.getCountry())
                .build();
    }

    private String extractCity(PhotonResponse.Properties props) {
        if (props.getCity() != null) {
            return props.getCity();
        }
        // Photon sometimes returns city-level info in 'name' when type is 'city'
        if ("city".equals(props.getType()) && props.getName() != null) {
            return props.getName();
        }
        return null;
    }

    /**
     * Formats address as user-friendly display name
     * Format: "LocationName (Street HouseNumber)" or just "Street HouseNumber" if no location name
     */
    private String formatAddress(PhotonResponse.Properties props) {
        // If no street, return name (if available) or empty string
        if (props.getStreet() == null || props.getStreet().isBlank()) {
            if (props.getName() != null && !props.getName().isBlank()) {
                return props.getName();
            }
            return "";
        }

        String locationName = "";
        if (props.getName() != null && !props.getName().isBlank()) {
            locationName = props.getName();
        }

        String addressName;
        if (props.getHousenumber() == null || props.getHousenumber().isBlank()) {
            addressName = props.getStreet();
        } else {
            addressName = String.format("%s %s", props.getStreet(), props.getHousenumber());
        }

        if (locationName.isBlank()) {
            return addressName;
        } else {
            return String.format("%s (%s)", locationName, addressName);
        }
    }

    private Point createPoint(PhotonResponse.Geometry geometry) {
        if (geometry == null || geometry.getCoordinates() == null || geometry.getCoordinates().size() < 2) {
            return null;
        }

        double lon = geometry.getLongitude();
        double lat = geometry.getLatitude();

        return GeoUtils.createPoint(lon, lat);
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    private Polygon convertBoundingBox(List<Double> boundingbox) {
        if (boundingbox == null || boundingbox.size() < 4) {
            return null;
        }
        try {
            // Photon bounding box format: [minlat, maxlat, minlon, maxlon]
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