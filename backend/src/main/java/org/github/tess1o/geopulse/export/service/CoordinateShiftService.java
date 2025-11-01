package org.github.tess1o.geopulse.export.service;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;

/**
 * Service for privacy-preserving coordinate shifting.
 * Applies consistent shifts to GPS coordinates to protect user privacy
 * while preserving relative distances and shapes.
 */
@ApplicationScoped
@Slf4j
public class CoordinateShiftService {
    private static final SecureRandom RANDOM = new SecureRandom();

    // Reasonable shift ranges to keep coordinates on valid Earth surface
    // Latitude range: -90 to 90 degrees
    // Longitude range: -180 to 180 degrees
    // We use smaller ranges to avoid edge cases and keep data realistic
    private static final double MIN_LAT_SHIFT = -30.0;
    private static final double MAX_LAT_SHIFT = 30.0;
    private static final double MIN_LON_SHIFT = -60.0;
    private static final double MAX_LON_SHIFT = 60.0;

    /**
     * Container for coordinate shift values
     */
    @Getter
    public static class CoordinateShift {
        private final double latitudeShift;
        private final double longitudeShift;

        public CoordinateShift(double latitudeShift, double longitudeShift) {
            this.latitudeShift = latitudeShift;
            this.longitudeShift = longitudeShift;
        }
    }

    /**
     * Generate random coordinate shifts within safe ranges.
     * The shifts are large enough to protect privacy but keep coordinates
     * within valid Earth coordinate system bounds.
     *
     * @return CoordinateShift with random latitude and longitude offsets
     */
    public CoordinateShift generateRandomShift() {
        double latShift = MIN_LAT_SHIFT + (MAX_LAT_SHIFT - MIN_LAT_SHIFT) * RANDOM.nextDouble();
        double lonShift = MIN_LON_SHIFT + (MAX_LON_SHIFT - MIN_LON_SHIFT) * RANDOM.nextDouble();

        log.debug("Generated random coordinate shift: lat={}, lon={}", latShift, lonShift);
        return new CoordinateShift(latShift, lonShift);
    }

    /**
     * Create a coordinate shift from provided values, or generate random if null.
     *
     * @param latitudeShift Optional latitude shift
     * @param longitudeShift Optional longitude shift
     * @return CoordinateShift with provided or random values
     */
    public CoordinateShift getOrGenerateShift(Double latitudeShift, Double longitudeShift) {
        if (latitudeShift != null && longitudeShift != null) {
            log.debug("Using provided coordinate shift: lat={}, lon={}", latitudeShift, longitudeShift);
            return new CoordinateShift(latitudeShift, longitudeShift);
        }
        return generateRandomShift();
    }

    /**
     * Apply coordinate shift to a latitude value.
     * Note: Does NOT clamp values to allow detection of out-of-bounds coordinates.
     * Caller should validate shift values before applying to avoid invalid coordinates.
     *
     * @param originalLatitude Original latitude
     * @param shift Coordinate shift to apply
     * @return Shifted latitude (may be out of valid range [-90, 90])
     */
    public double shiftLatitude(double originalLatitude, CoordinateShift shift) {
        return originalLatitude + shift.getLatitudeShift();
    }

    /**
     * Apply coordinate shift to a longitude value.
     * Wraps result to valid longitude range [-180, 180].
     *
     * @param originalLongitude Original longitude
     * @param shift Coordinate shift to apply
     * @return Shifted longitude, wrapped to valid range
     */
    public double shiftLongitude(double originalLongitude, CoordinateShift shift) {
        double shifted = originalLongitude + shift.getLongitudeShift();
        // Wrap longitude to [-180, 180] range
        while (shifted > 180.0) {
            shifted -= 360.0;
        }
        while (shifted < -180.0) {
            shifted += 360.0;
        }
        return shifted;
    }

    /**
     * Apply coordinate shift to both latitude and longitude.
     *
     * @param originalLatitude Original latitude
     * @param originalLongitude Original longitude
     * @param shift Coordinate shift to apply
     * @return Array of [shiftedLatitude, shiftedLongitude]
     */
    public double[] shiftCoordinates(double originalLatitude, double originalLongitude, CoordinateShift shift) {
        return new double[]{
                shiftLatitude(originalLatitude, shift),
                shiftLongitude(originalLongitude, shift)
        };
    }

    /**
     * Validate that a coordinate shift will keep most coordinates in valid ranges.
     * This is a helper method for UI validation.
     *
     * @param latitudeShift Proposed latitude shift
     * @param longitudeShift Proposed longitude shift
     * @param minLat Minimum latitude in dataset
     * @param maxLat Maximum latitude in dataset
     * @param minLon Minimum longitude in dataset
     * @param maxLon Maximum longitude in dataset
     * @return true if shift is reasonable, false if it would cause issues
     */
    public boolean isValidShift(double latitudeShift, double longitudeShift,
                                double minLat, double maxLat,
                                double minLon, double maxLon) {
        // Check if shifted coordinates would still be in reasonable ranges
        double shiftedMinLat = minLat + latitudeShift;
        double shiftedMaxLat = maxLat + latitudeShift;

        // For latitude, we need to ensure we don't go too far outside [-90, 90]
        // We allow some overflow since we clamp, but warn if too extreme
        if (shiftedMinLat < -120.0 || shiftedMaxLat > 120.0) {
            log.warn("Latitude shift {} would push coordinates far outside valid range", latitudeShift);
            return false;
        }

        // Longitude wraps around, so we're more lenient
        // Just check it's not unreasonably large
        if (Math.abs(longitudeShift) > 180.0) {
            log.warn("Longitude shift {} is unreasonably large", longitudeShift);
            return false;
        }

        return true;
    }
}
