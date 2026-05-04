package org.github.tess1o.geopulse.geocoding.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTWriter;

/**
 * Applies safety guardrails for provider-supplied geocoding bounding boxes.
 * Oversized bounding boxes are dropped (stored as null) to prevent broad cache poisoning.
 */
@ApplicationScoped
@Slf4j
public class GeocodingBoundingBoxGuardService {

    private static final double SQUARE_METERS_PER_SQUARE_KILOMETER = 1_000_000d;

    @ConfigProperty(name = "geocoding.cache.max-bbox-area-km2", defaultValue = "5000")
    double maxBboxAreaKm2;

    private final EntityManager entityManager;
    private final WKTWriter wktWriter = new WKTWriter();

    @Inject
    public GeocodingBoundingBoxGuardService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Returns the input bbox when it is within the configured max area, otherwise returns null.
     */
    public Polygon sanitizeForPersistence(Polygon boundingBox, String providerName, Long entityId) {
        if (boundingBox == null) {
            return null;
        }

        Double areaSquareMeters = calculateAreaSquareMeters(boundingBox);
        if (areaSquareMeters == null) {
            // If area can't be calculated reliably, keep existing behavior and persist bbox.
            return boundingBox;
        }

        double maxAreaSquareMeters = getMaxBboxAreaSquareMeters();
        if (areaSquareMeters <= maxAreaSquareMeters) {
            return boundingBox;
        }

        log.info("Dropping oversized geocoding bounding box: provider={}, entityId={}, areaKm2={}, maxAllowedKm2={}",
                providerName,
                entityId,
                areaSquareMeters / SQUARE_METERS_PER_SQUARE_KILOMETER,
                maxBboxAreaKm2);

        return null;
    }

    public double getMaxBboxAreaSquareMeters() {
        return Math.max(0d, maxBboxAreaKm2) * SQUARE_METERS_PER_SQUARE_KILOMETER;
    }

    private Double calculateAreaSquareMeters(Polygon boundingBox) {
        try {
            String wkt = wktWriter.write(boundingBox);
            Object value = entityManager.createNativeQuery(
                            "SELECT ST_Area(ST_GeomFromText(:wkt, 4326)::geography)")
                    .setParameter("wkt", wkt)
                    .getSingleResult();
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            return null;
        } catch (Exception e) {
            log.debug("Failed to calculate geodesic bbox area, keeping bbox as-is: {}", e.getMessage());
            return null;
        }
    }
}
