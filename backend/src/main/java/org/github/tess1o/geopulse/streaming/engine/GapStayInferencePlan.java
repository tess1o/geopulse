package org.github.tess1o.geopulse.streaming.engine;

import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;

import java.util.Collections;
import java.util.List;

/**
 * Internal result object describing how gap stay inference should be applied.
 * Keeps decisioning separate from event finalization/state mutation in the engine.
 */
final class GapStayInferencePlan {

    private static final GapStayInferencePlan NONE =
            new GapStayInferencePlan(false, Collections.emptyList(), null);
    private static final GapStayInferencePlan CONTINUE_EXISTING_STAY =
            new GapStayInferencePlan(true, Collections.emptyList(), null);

    private final boolean inferred;
    private final List<GPSPoint> tripPointsToFinalize;
    private final List<GPSPoint> replacementStayPoints;

    private GapStayInferencePlan(boolean inferred,
                                 List<GPSPoint> tripPointsToFinalize,
                                 List<GPSPoint> replacementStayPoints) {
        this.inferred = inferred;
        this.tripPointsToFinalize = tripPointsToFinalize;
        this.replacementStayPoints = replacementStayPoints;
    }

    static GapStayInferencePlan none() {
        return NONE;
    }

    static GapStayInferencePlan continueExistingStay() {
        return CONTINUE_EXISTING_STAY;
    }

    static GapStayInferencePlan replaceWithConfirmedStay(List<GPSPoint> stayPoints) {
        return new GapStayInferencePlan(true, List.of(), List.copyOf(stayPoints));
    }

    static GapStayInferencePlan finalizeTripAndReplaceWithConfirmedStay(List<GPSPoint> tripPoints,
                                                                        List<GPSPoint> stayPoints) {
        return new GapStayInferencePlan(true, List.copyOf(tripPoints), List.copyOf(stayPoints));
    }

    boolean isInferred() {
        return inferred;
    }

    boolean hasTripToFinalize() {
        return !tripPointsToFinalize.isEmpty();
    }

    List<GPSPoint> getTripPointsToFinalize() {
        return tripPointsToFinalize;
    }

    boolean hasReplacementStayPoints() {
        return replacementStayPoints != null && !replacementStayPoints.isEmpty();
    }

    List<GPSPoint> getReplacementStayPoints() {
        return replacementStayPoints;
    }
}
