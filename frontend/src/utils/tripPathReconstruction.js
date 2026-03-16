const toTimestampMs = (value) => {
  if (!value) return null
  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? null : parsed
}

const toFiniteCoordinate = (value) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

const hasValidPathPoint = (point) => {
  if (!point) return false
  return toFiniteCoordinate(point.latitude) !== null && toFiniteCoordinate(point.longitude) !== null
}

export const areSameCoordinate = (first, second, epsilon = 1e-7) => {
  if (!first || !second) return false
  return Math.abs(first.latitude - second.latitude) <= epsilon
    && Math.abs(first.longitude - second.longitude) <= epsilon
}

export const resolveTripMarkerPoint = (trip, type, fallbackPoint = null) => {
  if (!trip) return fallbackPoint

  if (type === 'start') {
    const lat = toFiniteCoordinate(trip.latitude)
    const lon = toFiniteCoordinate(trip.longitude)
    if (lat !== null && lon !== null) {
      return { latitude: lat, longitude: lon }
    }
  }

  if (type === 'end') {
    const lat = toFiniteCoordinate(trip.endLatitude)
    const lon = toFiniteCoordinate(trip.endLongitude)
    if (lat !== null && lon !== null) {
      return { latitude: lat, longitude: lon }
    }
  }

  return fallbackPoint
}

export const normalizePathPoints = (pathData) => {
  if (!Array.isArray(pathData)) return []

  return pathData
    .flat()
    .filter(hasValidPathPoint)
    .map((point) => ({
      ...point,
      _timestampMs: toTimestampMs(point.timestamp)
    }))
    .filter((point) => point._timestampMs !== null)
    .sort((a, b) => a._timestampMs - b._timestampMs)
}

export const reconstructTripPathPoints = (
  trip,
  normalizedPathPoints
) => {
  if (!trip || !Array.isArray(normalizedPathPoints) || normalizedPathPoints.length === 0) {
    return { points: [], strategy: 'none' }
  }

  const startMs = toTimestampMs(trip.timestamp)
  if (startMs === null) {
    return { points: [], strategy: 'invalid_trip_time' }
  }

  const durationSeconds = Number.isFinite(Number(trip.tripDuration)) ? Number(trip.tripDuration) : 0
  const endMs = startMs + Math.max(0, durationSeconds) * 1000

  const strictPoints = normalizedPathPoints.filter((point) =>
    point._timestampMs >= startMs && point._timestampMs <= endMs
  )
  if (strictPoints.length >= 2) {
    return { points: strictPoints, strategy: 'strict' }
  }

  return { points: [], strategy: 'no_match' }
}
