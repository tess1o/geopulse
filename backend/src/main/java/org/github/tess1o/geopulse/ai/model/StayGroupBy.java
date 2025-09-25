package org.github.tess1o.geopulse.ai.model;

/**
 * Enumeration of available grouping options for stay statistics.
 * Used to aggregate stays by different criteria for statistical analysis.
 */
public enum StayGroupBy {
    
    /**
     * Group stays by location name (e.g., "Home", "Office", "Coffee Shop")
     */
    LOCATION_NAME("locationName"),
    
    /**
     * Group stays by city (e.g., "New York", "London", "Tokyo")
     */
    CITY("city"),
    
    /**
     * Group stays by country (e.g., "United States", "United Kingdom", "Japan")
     */
    COUNTRY("country"),
    
    /**
     * Group stays by day (e.g., "2024-09-15", "2024-09-16")
     */
    DAY("day"),
    
    /**
     * Group stays by week (e.g., "2024-W37", "2024-W38")
     */
    WEEK("week"),
    
    /**
     * Group stays by month (e.g., "2024-09", "2024-10")
     */
    MONTH("month");
    
    private final String value;
    
    StayGroupBy(String value) {
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
    public static StayGroupBy fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("GroupBy value cannot be null");
        }
        
        String normalizedValue = value.toLowerCase().trim();
        for (StayGroupBy groupBy : StayGroupBy.values()) {
            if (groupBy.getValue().toLowerCase().equals(normalizedValue)) {
                return groupBy;
            }
        }
        
        throw new IllegalArgumentException("Unknown stay groupBy value: " + value + 
            ". Valid options: locationName, city, country, day, week, month");
    }
}