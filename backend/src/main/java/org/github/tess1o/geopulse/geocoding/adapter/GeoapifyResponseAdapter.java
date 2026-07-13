package org.github.tess1o.geopulse.geocoding.adapter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.geocoding.exception.GeocodingException;
import org.github.tess1o.geopulse.geocoding.mapper.CountryMapper;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.geocoding.model.geoapify.GeoapifyResponse;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

@ApplicationScoped
@Slf4j
public class GeoapifyResponseAdapter implements GeocodingResponseAdapter<GeoapifyResponse> {

    @Inject
    CountryMapper countryMapper;

    @Override
    public FormattableGeocodingResult adapt(GeoapifyResponse response, Point requestCoordinates, String providerName) {
        java.util.List<GeoapifyResponse.Result> results = response == null ? java.util.List.of() : response.getEffectiveResults();
        if (results.isEmpty()) {
            log.warn("Empty or null Geoapify response for coordinates: lon={}, lat={}",
                    requestCoordinates.getX(), requestCoordinates.getY());
            throw new GeocodingException("Geoapify returned empty or null response");
        }

        GeoapifyResponse.Result result = results.getFirst();
        Point resultPoint = createPoint(result, requestCoordinates);

        return SimpleFormattableResult.builder()
                .requestCoordinates(requestCoordinates)
                .resultCoordinates(resultPoint)
                .boundingBox(convertBoundingBox(result.getBbox()))
                .formattedDisplayName(formatAddress(result))
                .providerName(providerName)
                .city(extractCity(result))
                .country(countryMapper.normalize(result.getCountry()))
                .build();
    }

    @Override
    public String getProviderName() {
        return "Geoapify";
    }

    private String extractCity(GeoapifyResponse.Result result) {
        if (hasText(result.getCity())) {
            return result.getCity();
        }
        if (hasText(result.getTown())) {
            return result.getTown();
        }
        if (hasText(result.getVillage())) {
            return result.getVillage();
        }
        if (hasText(result.getMunicipality())) {
            return result.getMunicipality();
        }
        return null;
    }

    private String formatAddress(GeoapifyResponse.Result result) {
        if (hasText(result.getFormatted())) {
            return result.getFormatted();
        }

        String streetAddress = joinNonBlank(" ", result.getStreet(), result.getHousenumber());
        if (hasText(result.getName()) && hasText(streetAddress)) {
            return String.format("%s (%s)", result.getName(), streetAddress);
        }
        if (hasText(result.getName())) {
            return result.getName();
        }
        if (hasText(streetAddress)) {
            return streetAddress;
        }
        return joinNonBlank(", ", extractCity(result), result.getState(), result.getCountry());
    }

    private Point createPoint(GeoapifyResponse.Result result, Point fallbackPoint) {
        if (result.getLon() == null || result.getLat() == null) {
            return fallbackPoint;
        }
        return GeoUtils.createPoint(result.getLon(), result.getLat());
    }

    private Polygon convertBoundingBox(GeoapifyResponse.Bbox bbox) {
        if (bbox == null || bbox.getLat1() == null || bbox.getLat2() == null
                || bbox.getLon1() == null || bbox.getLon2() == null) {
            return null;
        }

        double south = Math.min(bbox.getLat1(), bbox.getLat2());
        double north = Math.max(bbox.getLat1(), bbox.getLat2());
        double west = Math.min(bbox.getLon1(), bbox.getLon2());
        double east = Math.max(bbox.getLon1(), bbox.getLon2());

        try {
            return GeoUtils.buildBoundingBoxPolygon(south, north, west, east);
        } catch (Exception e) {
            log.warn("Failed to convert Geoapify bounding box: {}", bbox, e);
            return null;
        }
    }

    private String joinNonBlank(String delimiter, String... values) {
        return java.util.Arrays.stream(values)
                .filter(this::hasText)
                .collect(java.util.stream.Collectors.joining(delimiter));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
