const EARTH_RADIUS_METERS = 6371000;

const toFiniteNumber = (value) => {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
};

const toRadians = (value) => (value * Math.PI) / 180;

export const haversineDistanceMetersFromCoordinates = (latitude1, longitude1, latitude2, longitude2) => {
    const lat1 = toFiniteNumber(latitude1);
    const lon1 = toFiniteNumber(longitude1);
    const lat2 = toFiniteNumber(latitude2);
    const lon2 = toFiniteNumber(longitude2);

    if (lat1 === null || lon1 === null || lat2 === null || lon2 === null) {
        return Number.NaN;
    }

    const dLat = toRadians(lat2 - lat1);
    const dLon = toRadians(lon2 - lon1);

    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos(toRadians(lat1)) * Math.cos(toRadians(lat2))
        * Math.sin(dLon / 2) * Math.sin(dLon / 2);

    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return EARTH_RADIUS_METERS * c;
};

export const haversineDistanceMeters = (left, right) => {
    const leftLat = left?.latitude ?? left?.lat;
    const leftLon = left?.longitude ?? left?.lng ?? left?.lon;
    const rightLat = right?.latitude ?? right?.lat;
    const rightLon = right?.longitude ?? right?.lng ?? right?.lon;

    return haversineDistanceMetersFromCoordinates(leftLat, leftLon, rightLat, rightLon);
};
