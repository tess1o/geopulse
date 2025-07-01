// Journey Insights Constants and Enums

export const STREAK_STATUS = {
    INACTIVE: 'INACTIVE',         // 0 days - no recent activity
    BEGINNER: 'BEGINNER',         // 1-7 days - just getting started
    CONSISTENT: 'CONSISTENT',     // 8-30 days - building a habit
    DEDICATED: 'DEDICATED',       // 31-90 days - serious commitment
    CHAMPION: 'CHAMPION'          // 91+ days - elite tracker
}

export const ACTIVITY_LEVEL = {
    LOW: 'LOW',           // Minimal activity
    MODERATE: 'MODERATE', // Regular activity  
    HIGH: 'HIGH',         // Very active
    EXTREME: 'EXTREME'    // Exceptionally active
}

// Helper function to get streak status based on consecutive days
export const getStreakStatusFromDays = (days) => {
    if (days === 0) return STREAK_STATUS.INACTIVE
    if (days <= 7) return STREAK_STATUS.BEGINNER
    if (days <= 30) return STREAK_STATUS.CONSISTENT
    if (days <= 90) return STREAK_STATUS.DEDICATED
    return STREAK_STATUS.CHAMPION
}

// Helper function to get activity level based on metrics
export const getActivityLevelFromMetrics = (metricsPerDay) => {
    if (metricsPerDay < 5) return ACTIVITY_LEVEL.LOW
    if (metricsPerDay < 15) return ACTIVITY_LEVEL.MODERATE
    if (metricsPerDay < 30) return ACTIVITY_LEVEL.HIGH
    return ACTIVITY_LEVEL.EXTREME
}