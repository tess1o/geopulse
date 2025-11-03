package org.github.tess1o.geopulse.geocoding.adapter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geocoding.exception.GeocodingException;
import org.github.tess1o.geopulse.geocoding.mapper.CountryMapper;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.geocoding.model.mapbox.*;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;

import java.util.List;

/**
 * Simplified adapter for converting Mapbox geocoding responses.
 * Formats display names specifically for Mapbox results.
 */
@ApplicationScoped
@Slf4j
public class MapboxResponseAdapter implements GeocodingResponseAdapter<MapboxResponse> {
    
    private static final String PROVIDER_NAME = "Mapbox";

    @Inject
    CountryMapper countryMapper;
    
    @Override
    public FormattableGeocodingResult adapt(MapboxResponse mapboxResponse, Point requestCoordinates, String providerName) {
        log.debug("Adapting Mapbox response for coordinates: lon={}, lat={}",
                 requestCoordinates.getX(), requestCoordinates.getY());

        if (mapboxResponse == null || mapboxResponse.getFeatures() == null || mapboxResponse.getFeatures().isEmpty()) {
            log.warn("Empty or null Mapbox response for coordinates: lon={}, lat={}",
                    requestCoordinates.getX(), requestCoordinates.getY());
            throw new GeocodingException("Mapbox returned empty or null response");
        }
        
        // Use the first feature (most relevant)
        MapboxFeature firstFeature = mapboxResponse.getFeatures().get(0);
        
        SimpleFormattableResult.SimpleFormattableResultBuilder builder = SimpleFormattableResult.builder()
            .requestCoordinates(requestCoordinates)
            .providerName(providerName);
        
        // Extract result coordinates
        if (firstFeature.getGeometry() != null && firstFeature.getGeometry().getCoordinates() != null) {
            List<Double> coords = firstFeature.getGeometry().getCoordinates();
            if (coords.size() >= 2) {
                Point resultCoordinates = GeoUtils.createPoint(coords.get(0), coords.get(1)); // [lon, lat]
                builder.resultCoordinates(resultCoordinates);
            }
        } else {
            builder.resultCoordinates(requestCoordinates);
        }
        
        // Extract bounding box
        if (firstFeature.getBbox() != null && firstFeature.getBbox().size() >= 4) {
            Polygon boundingBox = extractBoundingBox(firstFeature.getBbox());
            builder.boundingBox(boundingBox);
        }
        
        // Format display name using Mapbox specific logic
        String formattedDisplayName = formatMapboxDisplayName(firstFeature);
        builder.formattedDisplayName(formattedDisplayName);
        
        // Extract city and country from context
        builder.city(extractCity(firstFeature));
        String country = extractCountry(firstFeature);
        String normalizedCountry = countryMapper.normalize(country);
        builder.country(normalizedCountry);
        
        return builder.build();
    }

    
    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }
    
    /**
     * Format display name for Mapbox results.
     * Uses the text field as name and extracts street address if available.
     */
    private String formatMapboxDisplayName(MapboxFeature feature) {
        String name = feature.getText();
        String streetAddress = extractStreetAddress(feature);
        
        if (name != null && !name.isBlank()) {
            if (streetAddress != null && !streetAddress.isBlank() && !name.equals(streetAddress)) {
                return String.format("%s (%s)", name, streetAddress);
            } else {
                return name;
            }
        } else {
            // Fallback to place_name
            return feature.getPlaceName() != null ? 
                   feature.getPlaceName() : 
                   "Unknown location";
        }
    }
    
    /**
     * Extract street address from Mapbox feature.
     */
    private String extractStreetAddress(MapboxFeature feature) {
        // Try to get from properties first
        if (feature.getProperties() != null && feature.getProperties().getAddress() != null) {
            return feature.getProperties().getAddress();
        }
        
        // Extract from context if it's an address type
        if (feature.getContext() != null) {
            return feature.getContext().stream()
                .filter(c -> c.getId() != null && c.getId().startsWith("address"))
                .map(MapboxContext::getText)
                .findFirst()
                .orElse(null);
        }
        
        return null;
    }
    
    /**
     * Extract city name from Mapbox context.
     */
    private String extractCity(MapboxFeature feature) {
        if (feature.getContext() == null) {
            return null;
        }
        
        return feature.getContext().stream()
            .filter(c -> c.getId() != null && 
                    (c.getId().startsWith("place.") || 
                     c.getId().startsWith("locality.") ||
                     c.getId().startsWith("district.")))
            .map(MapboxContext::getText)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Extract country name from Mapbox context.
     */
    private String extractCountry(MapboxFeature feature) {
        if (feature.getContext() == null) {
            return null;
        }
        
        return feature.getContext().stream()
            .filter(c -> c.getId() != null && c.getId().startsWith("country."))
            .map(MapboxContext::getText)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Extract bounding box from Mapbox bbox array.
     */
    private Polygon extractBoundingBox(List<Double> bbox) {
        if (bbox.size() < 4) {
            return null;
        }
        
        try {
            // Mapbox bbox format: [minLng, minLat, maxLng, maxLat]
            double minLng = bbox.get(0);
            double minLat = bbox.get(1);
            double maxLng = bbox.get(2);
            double maxLat = bbox.get(3);
            
            return GeoUtils.buildBoundingBoxPolygon(minLat, maxLat, minLng, maxLng);
        } catch (Exception e) {
            log.warn("Failed to create bounding box from Mapbox bbox: {}", bbox, e);
            return null;
        }
    }
    
}