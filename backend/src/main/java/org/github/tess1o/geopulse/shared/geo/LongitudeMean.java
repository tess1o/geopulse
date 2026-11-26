package org.github.tess1o.geopulse.shared.geo;

/**
 * The mean of longitudes, for the centre of points close together. Points on both sides of the 180th meridian (Fiji,
 * Chukotka) average across it: the arithmetic mean of 179.99 and -179.99 is 0, on the other side of the world. All
 * other points keep the plain arithmetic mean, summed in the order they were added.
 */
public final class LongitudeMean {

    private double sum;
    private double min = Double.POSITIVE_INFINITY;
    private double max = Double.NEGATIVE_INFINITY;
    /** The same longitudes in [0, 360), which runs across the 180th meridian without a break. */
    private double eastSum;
    private double eastMin = Double.POSITIVE_INFINITY;
    private double eastMax = Double.NEGATIVE_INFINITY;
    private int count;

    public void add(double longitude) {
        double east = longitude < 0 ? longitude + 360 : longitude;
        sum += longitude;
        min = Math.min(min, longitude);
        max = Math.max(max, longitude);
        eastSum += east;
        eastMin = Math.min(eastMin, east);
        eastMax = Math.max(eastMax, east);
        count++;
    }

    public void clear() {
        sum = 0;
        min = Double.POSITIVE_INFINITY;
        max = Double.NEGATIVE_INFINITY;
        eastSum = 0;
        eastMin = Double.POSITIVE_INFINITY;
        eastMax = Double.NEGATIVE_INFINITY;
        count = 0;
    }

    /** The mean of the longitudes added, or NaN when there are none. */
    public double mean() {
        if (count == 0) {
            return Double.NaN;
        }
        // Across the meridian: more than half the globe apart as they are, within half of it in [0, 360)
        boolean acrossMeridian = max - min > 180 && eastMax - eastMin <= 180;
        return acrossMeridian ? Math.IEEEremainder(eastSum / count, 360) : sum / count;
    }
}
