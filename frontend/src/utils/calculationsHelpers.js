import { useUnits } from '@/composables/useUnits';
import { formatDistanceForUnit, formatSpeedForUnit } from '@/utils/measurementFormatters'
import { t } from '@/locales'
export { formatDuration } from '@/utils/durationFormatter'

/**
 * Convert kilometers to the display unit (km or miles) as a numeric value
 * Used for chart data that's already in kilometers from the backend
 * @param {number} kilometers - Distance in kilometers
 * @returns {number} - Distance in km (metric) or miles (imperial)
 */
export function convertKilometersToDisplayUnit(kilometers) {
    const unit = useUnits().getDistanceUnit();
    if (unit === 'MILES') {
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
    const unit = useUnits().getDistanceUnit();
    if (unit === 'MILES') {
        return `${value.toFixed(2)} ${t('common.units.mi')}`;
    }
    return `${value.toFixed(2)} ${t('common.units.km')}`;
}

/**
 * Get the distance unit label
 * @returns {string} - 'km' or 'mi'
 */
export function getDistanceUnitLabel() {
    const unit = useUnits().getDistanceUnit();
    return unit === 'MILES' ? t('common.units.mi') : t('common.units.km');
}

export function formatDistance(meters) {
    const unit = useUnits().getDistanceUnit();
    return formatDistanceForUnit(meters, { unit });
}

export function formatDistanceRounded(meters) {
    const unit = useUnits().getDistanceUnit();
    return formatDistanceForUnit(meters, { unit, rounded: true });
}

export function formatSpeed(speedKmH) {
    const unit = useUnits().getDistanceUnit();
    return formatSpeedForUnit(speedKmH, { unit, fallback: t('common.notAvailable') });
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

    if (days > 0) parts.push(t('common.duration.compactDays', { count: days }))
    if (hours > 0) parts.push(t('common.duration.compactHours', { count: hours }))
    if (minutes > 0) parts.push(t('common.duration.compactMinutes', { count: minutes }))

    return parts.join(' ') || t('common.duration.compactZero')
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
        return t('common.duration.minutes', { count: minutes }, minutes)
    } else if (days === 0) {
        // Less than 1 day - show hours and optionally minutes
        const remainingMinutes = minutes - (hours * 60)
        if (remainingMinutes === 0) {
            return t('common.duration.hours', { count: hours }, hours)
        } else {
            return `${t('common.duration.hours', { count: hours }, hours)} ${t('common.duration.minutes', { count: remainingMinutes }, remainingMinutes)}`
        }
    } else if (days <= 7) {
        const remainingHours = hours - (days * 24)
        if (remainingHours === 0) {
            return t('common.duration.days', { count: days }, days)
        } else {
            return `${t('common.duration.days', { count: days }, days)} ${t('common.duration.hours', { count: remainingHours }, remainingHours)}`
        }
    } else if (weeks <= 4) {
        const remainingDays = days - (weeks * 7)
        if (remainingDays === 0) {
            return t('common.duration.weeks', { count: weeks }, weeks)
        } else {
            return `${t('common.duration.weeks', { count: weeks }, weeks)} ${t('common.duration.days', { count: remainingDays }, remainingDays)}`
        }
    } else {
        const months = Math.floor(weeks / 4.35) // Approximate months
        const remainingWeeks = Math.floor(weeks - (months * 4.35))
        if (remainingWeeks === 0) {
            return t('common.duration.months', { count: months }, months)
        } else {
            return `${t('common.duration.months', { count: months }, months)} ${t('common.duration.weeks', { count: remainingWeeks }, remainingWeeks)}`
        }
    }
}
