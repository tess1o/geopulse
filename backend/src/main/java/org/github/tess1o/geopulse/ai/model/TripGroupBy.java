package org.github.tess1o.geopulse.ai.model;

/**
 * Enumeration of available grouping options for trip statistics.
 * Used to aggregate trips by different criteria for statistical analysis.
 */
public enum TripGroupBy {
    
    /**
     * Group trips by movement type (e.g., "WALKING", "DRIVING", "CYCLING")
     */
    MOVEMENT_TYPE("movementType"),
    
    /**
     * Group trips by origin location name (e.g., "Home", "Office")
     */
    ORIGIN_LOCATION_NAME("originLocationName"),
    
    /**
     * Group trips by destination location name (e.g., "Office", "Gym")
     */
    DESTINATION_LOCATION_NAME("destinationLocationName"),
    
    /**
     * Group trips by day (e.g., "2024-09-15", "2024-09-16")
     */
    DAY("day"),
    
    /**
     * Group trips by week (e.g., "2024-W37", "2024-W38")
     */
    WEEK("week"),
    
    /**
     * Group trips by month (e.g., "2024-09", "2024-10")
     */
    MONTH("month");
    
    private final String value;
    
    TripGroupBy(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Convert string value to enum, case-insensitive.
     * Used by AI tools to parse user input.
     * 
     * @param value String value to convert
     * @return Corresponding enum value
     * @throws IllegalArgumentException if value is not recognized
     */
    public static TripGroupBy fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("GroupBy value cannot be null");
        }
        
        String normalizedValue = value.toLowerCase().trim();
        for (TripGroupBy groupBy : TripGroupBy.values()) {
            if (groupBy.getValue().toLowerCase().equals(normalizedValue)) {
                return groupBy;
            }
        }
        
        throw new IllegalArgumentException("Unknown trip groupBy value: " + value + 
            ". Valid options: movementType, originLocationName, destinationLocationName, day, week, month");
    }
}