package org.github.tess1o.geopulse.export.service;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for privacy-preserving coordinate shifting.
 * Applies consistent shifts to GPS coordinates to protect user privacy
 * while preserving relative distances and shapes.
 */
@ApplicationScoped
@Slf4j
public class CoordinateShiftService {

    /**
     * Apply coordinate shift to a latitude value.
     * Note: Does NOT clamp values to allow detection of out-of-bounds coordinates.
     * Caller should validate shift values before applying to avoid invalid coordinates.
     *
     * @param originalLatitude Original latitude
     * @param shift            Coordinate shift to apply
     * @return Shifted latitude (may be out of valid range [-90, 90])
     */
    public double shiftLatitude(double originalLatitude, double shift) {
        return originalLatitude + shift;
    }

    /**
     * Apply coordinate shift to a longitude value.
     * Wraps result to valid longitude range [-180, 180].
     *
     * @param originalLongitude Original longitude
     * @param shift             Coordinate shift to apply
     * @return Shifted longitude, wrapped to valid range
     */
    public double shiftLongitude(double originalLongitude, double shift) {
        double shifted = originalLongitude + shift;
        // Wrap longitude to [-180, 180] range
        while (shifted > 180.0) {
            shifted -= 360.0;
        }
        while (shifted < -180.0) {
            shifted += 360.0;
        }
        return shifted;
    }
}
