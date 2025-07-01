/**
 * Date-related utility functions
 */

/**
 * Format a date string to a locale string
 * @param {string} dateString - Date string to format
 * @returns {string} - Formatted date string
 */
export function formatDate(dateString) {
    if (!dateString) return 'Unknown';

    const date = new Date(dateString);
    return date.toLocaleString(undefined, {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false, // optional: use false for 24-hour format
    });
}

export function formatDateMMDDYYYY(date) {
    const mm = String(date.getMonth() + 1).padStart(2, '0');
    const dd = String(date.getDate()).padStart(2, '0');
    const yyyy = date.getFullYear();
    return `${mm}/${dd}/${yyyy}`;
}

export function isValidDate(date) {
    return date instanceof Date && !isNaN(date);
}

export function isValidDataRange(dateRange) {
    return dateRange != null && dateRange.length === 2 && dateRange[0] != null && dateRange[1] != null;
}

export function timeAgo(date) {
    if (!date) return 'Never'

    // Convert string to Date if needed
    const dateObj = typeof date === 'string' ? new Date(date) : date

    // Check if date is valid
    if (isNaN(dateObj.getTime()) || (dateObj === 'Invalid Date')) {
        return date;
    }

    const now = new Date()
    const diffMs = now - dateObj
    const diffMinutes = Math.floor(diffMs / (1000 * 60))
    const diffHours = Math.floor(diffMs / (1000 * 60 * 60))
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))

    if (diffMinutes < 1) return 'Just now'
    if (diffMinutes < 60) return `${diffMinutes} min ago`
    if (diffHours < 24) return `${diffHours} hours ago`
    if (diffDays < 30) return `${diffDays} days ago`

    // For older dates, show actual date
    return dateObj.toLocaleDateString();
}

export function setToEndOfDay(date) {
    const endOfDay = new Date(date);
    endOfDay.setHours(23, 59, 59, 999);
    return endOfDay;
}

/**
 * Get today's date range (start of day to now) in UTC
 * @returns {Object} - Object with start and end dates
 */
export function getTodayRange() {
    const now = new Date();
    const offsetMinutes = -now.getTimezoneOffset(); // e.g., 180 for GMT+3

    const localNow = new Date(now.getTime() + offsetMinutes * 60 * 1000);
    const year = localNow.getUTCFullYear();
    const month = localNow.getUTCMonth();
    const day = localNow.getUTCDate();

    const localStart = new Date(Date.UTC(year, month, day, 0, 0, 0));
    const start = new Date(localStart.getTime() - offsetMinutes * 60 * 1000);

    const localEnd = new Date(Date.UTC(year, month, day, 23, 59, 59));
    const end = new Date(localEnd.getTime() - offsetMinutes * 60 * 1000);

    return {start, end: end};
}

export function getLastWeekRange() {
    const now = new Date();
    // Create start date at 7 days ago, preserving the current time
    const start = new Date(now);
    start.setUTCDate(start.getUTCDate() - 7);
    return {start, end: now};
}

/**
 * Get last month's date range (30 days ago to now) in UTC
 * @returns {Object} - Object with start and end dates
 */
export function getLastMonthRange() {
    const now = new Date();
    // Create start date at 30 days ago, preserving the current time
    const start = new Date(now);
    start.setUTCDate(start.getUTCDate() - 30);
    return {start, end: now};
}

export function isToday(date) {
    const now = new Date();
    const year = now.getUTCFullYear();
    const month = now.getUTCMonth();
    const day = now.getUTCDate();
    const d = new Date(date);
    return d.getUTCFullYear() === year && d.getUTCMonth() === month && d.getUTCDate() === day;
}
