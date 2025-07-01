export function formatDistance(km) {
    if (km < 1) {
        const d = Math.round(km * 1000 * 100) / 100
        return `${d} m`;
    } else {
        const d = Math.round(km * 100) / 100
        return `${d} km`;
    }
}

export function formatDuration(minutes) {
    if (minutes < 1) {
        return "less than a minute";
    }

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