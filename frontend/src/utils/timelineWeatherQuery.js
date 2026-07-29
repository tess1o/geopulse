const WEATHER_QUERY_TIME_PADDING_MS = 60 * 60 * 1000
const WEATHER_QUERY_BOUNDS_PADDING_DEGREES = 0.02

export function getWeatherQueryRange(startDate, endDate, timelineData = []) {
  const itemRanges = (timelineData || [])
    .map(getTimelineItemWeatherTimeRange)
    .filter(Boolean)

  if (itemRanges.length > 0) {
    const startMs = Math.min(...itemRanges.map(range => range.startMs)) - WEATHER_QUERY_TIME_PADDING_MS
    const endMs = Math.max(...itemRanges.map(range => range.endMs)) + WEATHER_QUERY_TIME_PADDING_MS
    return {
      startTime: new Date(startMs).toISOString(),
      endTime: new Date(endMs).toISOString()
    }
  }

  const fallbackStartMs = toEpochMs(startDate)
  const fallbackEndMs = toEpochMs(endDate)
  if (Number.isFinite(fallbackStartMs) && Number.isFinite(fallbackEndMs)) {
    return {
      startTime: new Date(fallbackStartMs - WEATHER_QUERY_TIME_PADDING_MS).toISOString(),
      endTime: new Date(fallbackEndMs + WEATHER_QUERY_TIME_PADDING_MS).toISOString()
    }
  }

  return { startTime: startDate, endTime: endDate }
}

export function getTimelineItemWeatherTimeRange(item) {
  if (!item || (item.type !== 'stay' && item.type !== 'trip')) {
    return null
  }

  const startMs = toEpochMs(item.timestamp || item.startTime)
  if (!Number.isFinite(startMs)) {
    return null
  }

  const durationSeconds = Number(item.stayDuration ?? item.tripDuration ?? 0)
  const endMs = durationSeconds > 0
    ? startMs + durationSeconds * 1000
    : startMs

  return { startMs, endMs: Math.max(startMs, endMs) }
}

export function padWeatherBounds(bounds) {
  if (!bounds) {
    return {}
  }

  const north = Number(bounds.north)
  const south = Number(bounds.south)
  const east = Number(bounds.east)
  const west = Number(bounds.west)
  if (![north, south, east, west].every(Number.isFinite)) {
    return {}
  }

  return {
    minLat: clampLatitude(south - WEATHER_QUERY_BOUNDS_PADDING_DEGREES),
    minLon: clampLongitude(west - WEATHER_QUERY_BOUNDS_PADDING_DEGREES),
    maxLat: clampLatitude(north + WEATHER_QUERY_BOUNDS_PADDING_DEGREES),
    maxLon: clampLongitude(east + WEATHER_QUERY_BOUNDS_PADDING_DEGREES)
  }
}

export function getTimelineGeographicBounds(timelineData = []) {
  const coordinates = (timelineData || [])
    .map(item => ({
      latitude: Number(item?.latitude),
      longitude: Number(item?.longitude)
    }))
    .filter(({ latitude, longitude }) => Number.isFinite(latitude) && Number.isFinite(longitude))

  if (coordinates.length === 0) {
    return null
  }

  const lats = coordinates.map(item => item.latitude)
  const lons = coordinates.map(item => item.longitude)

  return {
    north: Math.max(...lats),
    south: Math.min(...lats),
    east: Math.max(...lons),
    west: Math.min(...lons)
  }
}

function toEpochMs(value) {
  if (!value) {
    return NaN
  }
  const epochMs = new Date(value).getTime()
  return Number.isFinite(epochMs) ? epochMs : NaN
}

function clampLatitude(value) {
  return Math.max(-90, Math.min(90, value))
}

function clampLongitude(value) {
  return Math.max(-180, Math.min(180, value))
}
