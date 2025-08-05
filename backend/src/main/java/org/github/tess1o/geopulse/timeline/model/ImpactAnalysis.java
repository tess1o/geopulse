package org.github.tess1o.geopulse.timeline.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Analysis result for favorite location changes.
 * Determines whether the change can be handled with simple updates or requires regeneration.
 */
@Getter
@RequiredArgsConstructor
public class ImpactAnalysis {
    
    private final ImpactType type;
    private final Long favoriteId;
    private final List<LocalDate> affectedDates;
    private final String reason;

    public enum ImpactType {
        /**
         * Simple name change that can be handled with direct SQL updates.
         * No structural changes to timeline required.
         */
        NAME_ONLY,
        
        /**
         * Complex change that requires timeline regeneration.
         * May affect merging, splitting, or other structural aspects.
         */
        STRUCTURAL
    }

    /**
     * Create analysis for a name-only change (fast path).
     */
    public static ImpactAnalysis nameOnly(Long favoriteId, String reason) {
        return new ImpactAnalysis(ImpactType.NAME_ONLY, favoriteId, List.of(), reason);
    }

    /**
     * Create analysis for a structural change (slow path).
     */
    public static ImpactAnalysis structural(Long favoriteId, List<LocalDate> affectedDates, String reason) {
        return new ImpactAnalysis(ImpactType.STRUCTURAL, favoriteId, affectedDates, reason);
    }

    /**
     * Check if this change requires regeneration.
     */
    public boolean requiresRegeneration() {
        return type == ImpactType.STRUCTURAL;
    }

    /**
     * Check if this change can be handled with simple updates.
     */
    public boolean isSimpleUpdate() {
        return type == ImpactType.NAME_ONLY;
    }
}