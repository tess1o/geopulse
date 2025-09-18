package org.github.tess1o.geopulse.insight.model;

/**
 * Value object representing the result of badge processing operations.
 * Replaces int[] arrays with a self-documenting, type-safe alternative.
 */
public record BadgeProcessingResult(
        int totalUpdated,
        int newlyEarned,
        int missingCreated
) {
    
    /**
     * Empty result with all counts as zero
     */
    public static final BadgeProcessingResult EMPTY = new BadgeProcessingResult(0, 0, 0);
    
    /**
     * Create result with only updated count
     */
    public static BadgeProcessingResult updated(int count) {
        return new BadgeProcessingResult(count, 0, 0);
    }
    
    /**
     * Create result with only newly earned count
     */
    public static BadgeProcessingResult earned(int count) {
        return new BadgeProcessingResult(0, count, 0);
    }
    
    /**
     * Create result with only missing created count
     */
    public static BadgeProcessingResult created(int count) {
        return new BadgeProcessingResult(0, 0, count);
    }
    
    /**
     * Add another result to this one
     */
    public BadgeProcessingResult add(BadgeProcessingResult other) {
        return new BadgeProcessingResult(
                totalUpdated + other.totalUpdated,
                newlyEarned + other.newlyEarned,
                missingCreated + other.missingCreated
        );
    }

    @Override
    public String toString() {
        return String.format("BadgeProcessingResult[updated=%d, earned=%d, created=%d]", 
                           totalUpdated, newlyEarned, missingCreated);
    }
}