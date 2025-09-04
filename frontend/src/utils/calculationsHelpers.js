export function formatDistance(meters) {
    if (meters < 1000) {
        const d = Math.round(meters * 100) / 100
        return `${d} m`;
    } else {
        const d = Math.round((meters / 1000) * 100) / 100
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
    const speed = Number(speedKmH);
    if (isNaN(speed)) {
        return 'N/A';
    }
    return speed.toFixed(2) + ' km/h';
}