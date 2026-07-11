const toFiniteNumber = (value) => {
  if (value === null || value === undefined || value === '') {
    return null
  }

  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

export const resolveAverageTripSpeedKmh = (trip) => {
  const distanceMeters = toFiniteNumber(trip?.distanceMeters ?? trip?.totalDistanceMeters)
  const durationSeconds = toFiniteNumber(trip?.tripDuration ?? trip?.durationSeconds)

  if (distanceMeters === null || durationSeconds === null || distanceMeters < 0 || durationSeconds <= 0) {
    return null
  }

  return (distanceMeters / durationSeconds) * 3.6
}

export const resolveHoverSpeedKmh = (hoverTiming) => {
  const explicitSpeed = toFiniteNumber(hoverTiming?.speedKmh)
  if (explicitSpeed !== null && explicitSpeed >= 0) {
    return explicitSpeed
  }

  const segmentDistanceMeters = toFiniteNumber(hoverTiming?.segmentDistanceMeters)
  const segmentDurationSeconds = toFiniteNumber(hoverTiming?.segmentDurationSeconds)
  if (
    segmentDistanceMeters === null
    || segmentDurationSeconds === null
    || segmentDistanceMeters < 0
    || segmentDurationSeconds <= 0
  ) {
    return null
  }

  return (segmentDistanceMeters / segmentDurationSeconds) * 3.6
}
