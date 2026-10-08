// Engine-neutral map helpers used by both raster and vector code. They live outside src/maps/vector so that
// raster-only pages do not load vector modules (see vectorEngineRegistry).

export function isMapLibreMap(map) {
  return Boolean(map && typeof map.addSource === 'function' && typeof map.addLayer === 'function')
}

export function toFiniteNumber(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

export function toLngLatTuple(pointLike) {
  if (Array.isArray(pointLike) && pointLike.length >= 2) {
    const lat = toFiniteNumber(pointLike[0])
    const lng = toFiniteNumber(pointLike[1])
    if (lat === null || lng === null) {
      return null
    }
    return [lng, lat]
  }

  if (!pointLike || typeof pointLike !== 'object') {
    return null
  }

  const lat = toFiniteNumber(pointLike.lat ?? pointLike.latitude)
  const lng = toFiniteNumber(pointLike.lng ?? pointLike.lon ?? pointLike.longitude)

  if (lat === null || lng === null) {
    return null
  }

  return [lng, lat]
}

export function toLatLngTuple(pointLike) {
  if (Array.isArray(pointLike) && pointLike.length >= 2) {
    const lng = toFiniteNumber(pointLike[0])
    const lat = toFiniteNumber(pointLike[1])
    if (lat === null || lng === null) {
      return null
    }
    return [lat, lng]
  }

  if (!pointLike || typeof pointLike !== 'object') {
    return null
  }

  const lat = toFiniteNumber(pointLike.lat ?? pointLike.latitude)
  const lng = toFiniteNumber(pointLike.lng ?? pointLike.lon ?? pointLike.longitude)

  if (lat === null || lng === null) {
    return null
  }

  return [lat, lng]
}

export function normalizeLeafletBoundsToMapLibre(bounds) {
  if (!bounds) {
    return null
  }

  const pointsToBoundingBox = (points) => {
    let west = Infinity
    let south = Infinity
    let east = -Infinity
    let north = -Infinity

    points.forEach((point) => {
      const tuple = toLngLatTuple(point)
      if (!tuple) {
        return
      }

      const [lng, lat] = tuple
      if (lng < west) west = lng
      if (lng > east) east = lng
      if (lat < south) south = lat
      if (lat > north) north = lat
    })

    if (!Number.isFinite(west) || !Number.isFinite(south) || !Number.isFinite(east) || !Number.isFinite(north)) {
      return null
    }

    return [[west, south], [east, north]]
  }

  if (Array.isArray(bounds)) {
    if (bounds.length === 4) {
      const south = toFiniteNumber(bounds[0])
      const west = toFiniteNumber(bounds[1])
      const north = toFiniteNumber(bounds[2])
      const east = toFiniteNumber(bounds[3])

      if (south === null || west === null || north === null || east === null) {
        return null
      }

      return [[west, south], [east, north]]
    }

    // Leaflet fitBounds accepts an array of points; compute a real bounding box
    // even for two points because callers often pass unsorted coordinates.
    if (bounds.length >= 2) {
      return pointsToBoundingBox(bounds)
    }
  }

  if (typeof bounds.getSouthWest === 'function' && typeof bounds.getNorthEast === 'function') {
    const southWest = toLngLatTuple(bounds.getSouthWest())
    const northEast = toLngLatTuple(bounds.getNorthEast())
    if (!southWest || !northEast) {
      return null
    }
    return [southWest, northEast]
  }

  if (typeof bounds === 'object') {
    const south = toFiniteNumber(bounds.south)
    const west = toFiniteNumber(bounds.west)
    const north = toFiniteNumber(bounds.north)
    const east = toFiniteNumber(bounds.east)

    if (south === null || west === null || north === null || east === null) {
      return null
    }

    return [[west, south], [east, north]]
  }

  return null
}

export function createFeatureCollection(features = []) {
  return {
    type: 'FeatureCollection',
    features: Array.isArray(features) ? features : []
  }
}
