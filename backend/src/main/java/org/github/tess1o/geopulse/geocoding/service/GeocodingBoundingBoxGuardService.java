package org.github.tess1o.geopulse.geocoding.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTWriter;

/**
 * Applies safety guardrails for provider-supplied geocoding bounding boxes.
 * Oversized bounding boxes are dropped (stored as null) to prevent broad cache poisoning.
 * <p>
 * Area is measured by {@code geopulse_geocoding_bbox_area_km2}, the same planar-consistent
 * measure used by cache containment queries and the V36.86.0 cleanup migration. Using
 * {@code ST_Area(bounding_box::geography)} instead would report 0 for boxes spanning the
 * full longitude range and let them poison containment matching.
 */
@ApplicationScoped
@Slf4j
public class GeocodingBoundingBoxGuardService {

    @ConfigProperty(name = "geocoding.cache.max-bbox-area-km2", defaultValue = "5000")
    double maxBboxAreaKm2;

    @Inject
    SystemSettingsService settingsService;

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

        Double areaKm2 = calculateAreaKm2(boundingBox);
        if (areaKm2 == null) {
            // Fail closed: an unverifiable bbox must not participate in containment matching.
            return null;
        }

        double maxAreaKm2 = getMaxBboxAreaKm2();
        if (areaKm2 <= maxAreaKm2) {
            return boundingBox;
        }

        log.info("Dropping oversized geocoding bounding box: provider={}, entityId={}, areaKm2={}, maxAllowedKm2={}",
                providerName,
                entityId,
                areaKm2,
                maxBboxAreaKm2());

        return null;
    }

    public double getMaxBboxAreaKm2() {
        return Math.max(0d, maxBboxAreaKm2());
    }

    private double maxBboxAreaKm2() {
        return settingsService == null ? maxBboxAreaKm2 : settingsService.getInteger("geocoding.cache.max-bbox-area-km2");
    }

    private Double calculateAreaKm2(Polygon boundingBox) {
        try {
            String wkt = wktWriter.write(boundingBox);
            Object value = entityManager.createNativeQuery(
                            "SELECT geopulse_geocoding_bbox_area_km2(ST_GeomFromText(:wkt, 4326))")
                    .setParameter("wkt", wkt)
                    .getSingleResult();
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to calculate bbox area, dropping bbox: {}", e.getMessage());
            return null;
        }
    }
}
