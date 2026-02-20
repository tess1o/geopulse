package org.github.tess1o.geopulse.coverage;

public final class CoverageDefaults {
    public static final int[] GRID_SIZES_METERS = {50, 250, 1000, 5000, 20000, 40000};
    public static final int DEFAULT_GRID_METERS = 50;

    public static final int RADIUS_METERS = 20;
    public static final int SEGMENTIZE_METERS = 10;
    public static final int MAX_GAP_SECONDS = 300;
    public static final double MAX_SPEED_MPS = 60.0;
    public static final double MAX_ACCURACY_METERS = 50.0;

    public static final String SCHEDULE_EVERY = "2h";
    public static final String SCHEDULE_DELAYED = "0m";

    private CoverageDefaults() {
    }
}
