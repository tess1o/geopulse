import { haversineDistanceMetersFromCoordinates } from '@/utils/geoDistance'

// Bounds whose diagonal is at most this many meters are shown as a single location instead of being fitted.
export const TIMELINE_COLLAPSED_BOUNDS_THRESHOLD_METERS = 250

const toFiniteMapCoordinate = (value) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

const isValidMapLatitude = (value) => Number.isFinite(value) && value >= -90 && value <= 90
const isValidMapLongitude = (value) => Number.isFinite(value) && value >= -180 && value <= 180

/** [lat, lon] for a [lat, lon] pair or an object with latitude/lat and longitude/lng/lon; null when invalid. */
export const normalizeTimelineBoundsPoint = (point) => {
  if (Array.isArray(point) && point.length >= 2) {
    const latitude = toFiniteMapCoordinate(point[0])
    const longitude = toFiniteMapCoordinate(point[1])
    if (isValidMapLatitude(latitude) && isValidMapLongitude(longitude)) {
      return [latitude, longitude]
    }
  }

  if (point && typeof point === 'object') {
    const latitude = toFiniteMapCoordinate(point.latitude ?? point.lat)
    const longitude = toFiniteMapCoordinate(point.longitude ?? point.lng ?? point.lon)
    if (isValidMapLatitude(latitude) && isValidMapLongitude(longitude)) {
      return [latitude, longitude]
    }
  }

  return null
}

const addTimelineBoundsPoint = (bounds, point) => {
  const normalizedPoint = normalizeTimelineBoundsPoint(point)
  if (normalizedPoint) {
    bounds.push(normalizedPoint)
  }
}

/**
 * Every point the timeline map shows: path points, timeline items, favorites (points and area corners) and
 * planned items. Returns null when there is nothing to show.
 */
export const collectTimelineDataBounds = ({ pathData = [], timelineData = [], favoritesData = [], plannedItemsData = [] }) => {
  const bounds = []

  pathData.forEach(pathGroup => {
    if (Array.isArray(pathGroup)) {
      pathGroup.forEach(point => {
        addTimelineBoundsPoint(bounds, point)
      })
    }
  })

  timelineData.forEach(item => {
    addTimelineBoundsPoint(bounds, item)
  })

  favoritesData.forEach(favorite => {
    if (favorite.type === 'point') {
      addTimelineBoundsPoint(bounds, favorite)
    } else if (favorite.type === 'area' && favorite.coordinates) {
      favorite.coordinates.forEach(coord => {
        addTimelineBoundsPoint(bounds, coord)
      })
    }
  })

  plannedItemsData.forEach(item => {
    addTimelineBoundsPoint(bounds, item)
  })

  return bounds.length > 0 ? bounds : null
}

/**
 * Viewport for a list of points: the valid points, their center, and whether they are close enough together
 * to show as one location. Null when no point is valid.
 */
export const getTimelineBoundsViewport = (bounds) => {
  const points = Array.isArray(bounds)
    ? bounds.map(normalizeTimelineBoundsPoint).filter(Boolean)
    : []

  if (points.length === 0) {
    return null
  }

  let south = Infinity
  let west = Infinity
  let north = -Infinity
  let east = -Infinity

  points.forEach(([latitude, longitude]) => {
    south = Math.min(south, latitude)
    west = Math.min(west, longitude)
    north = Math.max(north, latitude)
    east = Math.max(east, longitude)
  })

  if (![south, west, north, east].every(Number.isFinite)) {
    return null
  }

  const center = [
    (south + north) / 2,
    (west + east) / 2
  ]
  const diagonalMeters = haversineDistanceMetersFromCoordinates(south, west, north, east)
  const isCollapsedBounds = points.length === 1
    || (Number.isFinite(diagonalMeters) && diagonalMeters <= TIMELINE_COLLAPSED_BOUNDS_THRESHOLD_METERS)

  return {
    points,
    center,
    isCollapsedBounds
  }
}
