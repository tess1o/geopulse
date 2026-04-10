export const toFiniteCoordinate = (value) => {
  if (value === null || value === undefined || value === '') {
    return null
  }

  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

export const isValidLatitude = (value) => Number.isFinite(value) && value >= -90 && value <= 90

export const isValidLongitude = (value) => Number.isFinite(value) && value >= -180 && value <= 180

export function normalizeLatLngPoint(point) {
  if (!point) {
    return null
  }

  if (Array.isArray(point) && point.length >= 2) {
    const first = toFiniteCoordinate(point[0])
    const second = toFiniteCoordinate(point[1])
    if (first === null || second === null) {
      return null
    }

    // Interpret [lng, lat] when [lat, lng] would be invalid.
    if (!isValidLatitude(first) && isValidLatitude(second) && isValidLongitude(first)) {
      return { lat: second, lng: first }
    }

    // Interpret [lng, lat] when second cannot be longitude.
    if (!isValidLongitude(second) && isValidLongitude(first) && isValidLatitude(second)) {
      return { lat: second, lng: first }
    }

    return { lat: first, lng: second }
  }

  const rawLat = toFiniteCoordinate(point.latitude ?? point.lat)
  const rawLng = toFiniteCoordinate(point.longitude ?? point.lng ?? point.lon)
  if (rawLat === null || rawLng === null) {
    return null
  }

  // Auto-correct obvious inversion from object inputs.
  if (!isValidLatitude(rawLat) && isValidLatitude(rawLng) && isValidLongitude(rawLat)) {
    return { lat: rawLng, lng: rawLat }
  }

  return { lat: rawLat, lng: rawLng }
}

export const hasValidLatLngPoint = (point) => Boolean(normalizeLatLngPoint(point))

export const toLeafletLatLngTuple = (point) => {
  const normalized = normalizeLatLngPoint(point)
  if (!normalized) {
    return null
  }

  return [normalized.lat, normalized.lng]
}

export const toMapLibreLngLatTuple = (point) => {
  const normalized = normalizeLatLngPoint(point)
  if (!normalized) {
    return null
  }

  return [normalized.lng, normalized.lat]
}

export const normalizePathSegmentsToLeafletTuples = (pathSegments) => {
  if (!Array.isArray(pathSegments)) {
    return []
  }

  return pathSegments
    .map((segment) => {
      if (!Array.isArray(segment)) {
        return []
      }

      return segment
        .map((point) => toLeafletLatLngTuple(point))
        .filter((tuple) => Array.isArray(tuple) && tuple.length === 2)
    })
    .filter((segment) => segment.length >= 2)
}

export const normalizeBoundsPointsToLeafletTuples = (points) => {
  if (!Array.isArray(points)) {
    return []
  }

  return points
    .map((point) => toLeafletLatLngTuple(point))
    .filter((tuple) => Array.isArray(tuple) && tuple.length === 2)
}

const normalizeCorners = (points = []) => {
  const normalizedPoints = normalizeBoundsPointsToLeafletTuples(points)
  if (normalizedPoints.length === 0) {
    return null
  }

  let south = Infinity
  let west = Infinity
  let north = -Infinity
  let east = -Infinity

  normalizedPoints.forEach(([lat, lng]) => {
    south = Math.min(south, lat)
    west = Math.min(west, lng)
    north = Math.max(north, lat)
    east = Math.max(east, lng)
  })

  if (![south, west, north, east].every(Number.isFinite)) {
    return null
  }

  return [
    [south, west],
    [north, east]
  ]
}

const toAreaCorners = (areaLike) => {
  if (!areaLike || typeof areaLike !== 'object') {
    return null
  }

  const southWestLat = toFiniteCoordinate(areaLike.southWestLat ?? areaLike.south)
  const southWestLon = toFiniteCoordinate(areaLike.southWestLon ?? areaLike.west)
  const northEastLat = toFiniteCoordinate(areaLike.northEastLat ?? areaLike.north)
  const northEastLon = toFiniteCoordinate(areaLike.northEastLon ?? areaLike.east)

  if ([southWestLat, southWestLon, northEastLat, northEastLon].some((value) => value === null)) {
    return null
  }

  return normalizeCorners([
    [southWestLat, southWestLon],
    [northEastLat, northEastLon]
  ])
}

export const normalizeBoundsToLeafletCorners = (boundsLike) => {
  if (!boundsLike) {
    return null
  }

  if (Array.isArray(boundsLike)) {
    if (boundsLike.length === 4) {
      const south = toFiniteCoordinate(boundsLike[0])
      const west = toFiniteCoordinate(boundsLike[1])
      const north = toFiniteCoordinate(boundsLike[2])
      const east = toFiniteCoordinate(boundsLike[3])
      if ([south, west, north, east].some((value) => value === null)) {
        return null
      }
      return normalizeCorners([
        [south, west],
        [north, east]
      ])
    }

    if (boundsLike.length === 2) {
      const first = toFiniteCoordinate(boundsLike[0])
      const second = toFiniteCoordinate(boundsLike[1])
      if (first !== null && second !== null) {
        return normalizeCorners([[first, second]])
      }

      return normalizeCorners(boundsLike)
    }

    if (boundsLike.length > 0) {
      return normalizeCorners(boundsLike)
    }
  }

  if (
    typeof boundsLike.getSouthWest === 'function'
    && typeof boundsLike.getNorthEast === 'function'
  ) {
    return normalizeCorners([
      boundsLike.getSouthWest(),
      boundsLike.getNorthEast()
    ])
  }

  const areaCorners = toAreaCorners(boundsLike)
  if (areaCorners) {
    return areaCorners
  }

  return null
}
