import { useMeasureUnit } from '@/composables/useMeasureUnit';

/**
 * Convert kilometers to the display unit (km or miles) as a numeric value
 * Used for chart data that's already in kilometers from the backend
 * @param {number} kilometers - Distance in kilometers
 * @returns {number} - Distance in km (metric) or miles (imperial)
 */
export function convertKilometersToDisplayUnit(kilometers) {
    const unit = useMeasureUnit().getMeasureUnit();
    if (unit === 'IMPERIAL') {
        // Convert kilometers to miles
        return kilometers * 0.621371;
    }
    // Already in kilometers, return as-is
    return kilometers;
}

/**
 * Format a distance value that's already in the display unit (km or miles)
 * @param {number} value - Distance value in km or miles
 * @returns {string} - Formatted string with unit suffix
 */
export function formatDistanceValue(value) {
    const unit = useMeasureUnit().getMeasureUnit();
    if (unit === 'IMPERIAL') {
        return `${value.toFixed(2)} mi`;
    }
    return `${value.toFixed(2)} km`;
}

/**
 * Get the distance unit label
 * @returns {string} - 'km' or 'mi'
 */
export function getDistanceUnitLabel() {
    const unit = useMeasureUnit().getMeasureUnit();
    return unit === 'IMPERIAL' ? 'mi' : 'km';
}

export function formatDistance(meters) {
    const unit = useMeasureUnit().getMeasureUnit();

    if (unit === 'IMPERIAL') {
        const feet = meters * 3.28084;
        if (feet < 5280) {
            return `${Math.round(feet)} ft`;
        } else {
            const miles = feet / 5280;
            return `${miles.toFixed(2)} mi`;
        }
    }

    if (meters < 1000) {
        const d = Math.round(meters * 100) / 100
        return `${d} m`;
    } else {
        const d = Math.round((meters / 1000) * 100) / 100
        return `${d} km`;
    }
}

export function formatDistanceRounded(meters) {
    const unit = useMeasureUnit().getMeasureUnit();

    if (unit === 'IMPERIAL') {
        const feet = meters * 3.28084;
        if (feet < 5280) {
            return `${Math.round(feet)} ft`;
        } else {
            const miles = feet / 5280;
            return `${Math.round(miles)} mi`;
        }
    }

    if (meters < 1000) {
        const d = Math.round(meters); // no decimals for meters either
        return `${d} m`;
    } else {
        const d = Math.round(meters / 1000); // whole km, no "."
        return `${d} km`;
    }
}

export function formatDuration(seconds) {
    if (seconds < 60) {
        return "less than a minute";
    }
    
    const minutes = seconds / 60;
    const days = Math.floor(minutes / (60 * 24));
    const hrs = Math.floor((minutes % (60 * 24)) / 60);
    const mins = Math.floor(minutes % 60);

    const parts = [];

    if (days > 0) {
        parts.push(`${days} day${days > 1 ? 's' : ''}`);
    }

    if (hrs > 0) {
        parts.push(`${hrs} hour${hrs > 1 ? 's' : ''}`);
    }

    // Only include minutes if there are no days
    if (mins > 0 && days === 0) {
        parts.push(`${mins} minute${mins > 1 ? 's' : ''}`);
    }

    return parts.join(' ');
}

export function formatSpeed(speedKmH) {
    const unit = useMeasureUnit().getMeasureUnit();
    const speed = Number(speedKmH);

    if (isNaN(speed)) {
        return 'N/A';
    }

    if (unit === 'IMPERIAL') {
        const speedMph = speed * 0.621371;
        return `${speedMph.toFixed(2)} mph`;
    }

    return speed.toFixed(2) + ' km/h';
}

/**
 * Format duration in compact format (e.g., "2d 3h 15m")
 * Uses pure JavaScript calculations to avoid dependencies
 * @param {number} durationSeconds - Duration in seconds
 * @returns {string} - Formatted duration string
 */
export function formatDurationCompact(durationSeconds) {
    const totalMinutes = Math.floor(durationSeconds / 60)
    const totalHours = Math.floor(durationSeconds / 3600)
    const days = Math.floor(totalHours / 24)
    const hours = totalHours % 24
    const minutes = totalMinutes % 60
    
    const parts = []
    
    if (days > 0) parts.push(`${days}d`)
    if (hours > 0) parts.push(`${hours}h`)
    if (minutes > 0) parts.push(`${minutes}m`)
    
    return parts.join(' ') || '0m'
}

/**
 * Format duration with smart scaling (minutes/hours/days/weeks/months)
 * Handles edge cases and provides user-friendly output
 * @param {number} durationSeconds - Duration in seconds  
 * @returns {string} - Formatted duration string
 */
export function formatDurationSmart(durationSeconds) {
    const minutes = Math.floor(durationSeconds / 60)
    const hours = Math.floor(durationSeconds / 3600)
    const days = Math.floor(hours / 24)
    const weeks = Math.floor(days / 7)
    
    if (hours === 0) {
        // Less than 1 hour - show minutes
        return `${minutes} minute${minutes === 1 ? '' : 's'}`
    } else if (days === 0) {
        // Less than 1 day - show hours and optionally minutes
        const remainingMinutes = minutes - (hours * 60)
        if (remainingMinutes === 0) {
            return `${hours} hour${hours === 1 ? '' : 's'}`
        } else {
            return `${hours} hour${hours === 1 ? '' : 's'} ${remainingMinutes} minute${remainingMinutes === 1 ? '' : 's'}`
        }
    } else if (days <= 7) {
        const remainingHours = hours - (days * 24)
        if (remainingHours === 0) {
            return `${days} day${days === 1 ? '' : 's'}`
        } else {
            return `${days} day${days === 1 ? '' : 's'} ${remainingHours} hour${remainingHours === 1 ? '' : 's'}`
        }
    } else if (weeks <= 4) {
        const remainingDays = days - (weeks * 7)
        if (remainingDays === 0) {
            return `${weeks} week${weeks === 1 ? '' : 's'}`
        } else {
            return `${weeks} week${weeks === 1 ? '' : 's'} ${remainingDays} day${remainingDays === 1 ? '' : 's'}`
        }
    } else {
        const months = Math.floor(weeks / 4.35) // Approximate months
        const remainingWeeks = Math.floor(weeks - (months * 4.35))
        if (remainingWeeks === 0) {
            return `${months} month${months === 1 ? '' : 's'}`
        } else {
            return `${months} month${months === 1 ? '' : 's'} ${remainingWeeks} week${remainingWeeks === 1 ? '' : 's'}`
        }
    }
}