package org.github.tess1o.geopulse.coverage;

import java.util.List;
import java.util.Set;

public final class CoverageDefaults {
    public static final List<Integer> GRID_SIZES_METERS_ORDERED = List.of(20, 50, 250, 1000, 5000, 20000, 40000);
    public static final Set<Integer> GRID_SIZES_METERS = Set.copyOf(GRID_SIZES_METERS_ORDERED);
    /**
     * Grid sizes upserted together, one statement each. The finest grid holds most of the cells, so it gets a
     * statement of its own and a batch never needs more memory or temp space than that grid alone; the coarser grids
     * share one pass over the samples.
     */
    public static final List<List<Integer>> GRID_SIZE_PASSES = List.of(
            GRID_SIZES_METERS_ORDERED.subList(0, 1),
            GRID_SIZES_METERS_ORDERED.subList(1, GRID_SIZES_METERS_ORDERED.size()));
    public static final int DEFAULT_GRID_METERS = 50;
    public static final int DEFAULT_CELLS_PER_VIEW = 12_000;
    public static final int MAX_CELLS_PER_VIEW = 12_000;

    public static final int RADIUS_METERS = 20;
    public static final int SEGMENTIZE_METERS = 10;
    public static final int MAX_GAP_SECONDS = 300;
    public static final double MAX_SPEED_MPS = 60.0;
    public static final double MAX_ACCURACY_METERS = 50.0;
    public static final int PROCESSING_STALE_TIMEOUT_SECONDS = 1_800;
    public static final int DEFAULT_BATCH_SIZE = 50_000;

    private CoverageDefaults() {
    }
}
