const toFiniteNumber = (value) => {
  if (value === null || value === undefined) {
    return null
  }

  if (typeof value === 'string' && value.trim() === '') {
    return null
  }

  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

const toLatLng = (latitude, longitude) => {
  const lat = toFiniteNumber(latitude)
  const lng = toFiniteNumber(longitude)
  if (lat === null || lng === null) {
    return null
  }

  return { lat, lng }
}

export const getFavoritePointLatLng = (favorite) => {
  return toLatLng(favorite?.latitude, favorite?.longitude)
}

export const getPendingPointLatLng = (pendingPoint) => {
  return toLatLng(pendingPoint?.lat, pendingPoint?.lon)
}

export const getAreaBounds = (areaLike) => {
  const ring = areaLike?.geometry?.coordinates?.[0]
  if (ring?.length) {
    return {
      southWestLat: Math.min(...ring.map(c => c[1])), southWestLon: Math.min(...ring.map(c => c[0])),
      northEastLat: Math.max(...ring.map(c => c[1])), northEastLon: Math.max(...ring.map(c => c[0]))
    }
  }
  const southWestLat = toFiniteNumber(areaLike?.southWestLat)
  const southWestLon = toFiniteNumber(areaLike?.southWestLon)
  const northEastLat = toFiniteNumber(areaLike?.northEastLat)
  const northEastLon = toFiniteNumber(areaLike?.northEastLon)

  if (
    southWestLat === null
    || southWestLon === null
    || northEastLat === null
    || northEastLon === null
  ) {
    return null
  }

  return {
    southWestLat,
    southWestLon,
    northEastLat,
    northEastLon
  }
}

export const toLeafletBounds = (areaLike) => {
  const bounds = getAreaBounds(areaLike)
  if (!bounds) {
    return null
  }

  return [
    [bounds.southWestLat, bounds.southWestLon],
    [bounds.northEastLat, bounds.northEastLon]
  ]
}

export const getAreaCenterLatLng = (areaLike) => {
  const representative = toLatLng(areaLike?.latitude, areaLike?.longitude)
  if (representative) return representative
  const bounds = getAreaBounds(areaLike)
  if (!bounds) {
    return null
  }

  const ring = areaLike?.geometry?.coordinates?.[0]
  if (ring?.length) {
    const middleLat = (bounds.southWestLat + bounds.northEastLat) / 2
    let below = bounds.southWestLat
    let above = bounds.northEastLat
    // Avoid vertex latitudes so intersections form unambiguous interior intervals.
    for (const [, lat] of ring) {
      if (lat <= middleLat) below = Math.max(below, lat)
      else above = Math.min(above, lat)
    }
    const lat = (below + above) / 2
    const intersections = []
    for (let i = 1; i < ring.length; i++) {
      const [ax, ay] = ring[i - 1]
      const [bx, by] = ring[i]
      if ((ay > lat) !== (by > lat)) {
        intersections.push(ax + (lat - ay) * (bx - ax) / (by - ay))
      }
    }
    intersections.sort((a, b) => a - b)
    let widest = 0
    let center = null
    for (let i = 0; i + 1 < intersections.length; i += 2) {
      const width = intersections[i + 1] - intersections[i]
      if (width > widest) {
        widest = width
        center = {lat, lng: (intersections[i] + intersections[i + 1]) / 2}
      }
    }
    return center
  }

  return {
    lat: (bounds.southWestLat + bounds.northEastLat) / 2,
    lng: (bounds.southWestLon + bounds.northEastLon) / 2
  }
}

export const getAreaPolygonLngLat = (areaLike) => {
  if (areaLike?.geometry?.type === 'Polygon') return areaLike.geometry.coordinates[0]
  const bounds = getAreaBounds(areaLike)
  if (!bounds) {
    return null
  }

  return [
    [bounds.southWestLon, bounds.southWestLat],
    [bounds.northEastLon, bounds.southWestLat],
    [bounds.northEastLon, bounds.northEastLat],
    [bounds.southWestLon, bounds.northEastLat],
    [bounds.southWestLon, bounds.southWestLat]
  ]
}

export const extractLatLng = (eventPayload) => {
  const latlng = eventPayload?.latlng
  if (!latlng || typeof latlng !== 'object') {
    return null
  }

  return toLatLng(latlng.lat, latlng.lng)
}

export const buildBoundsPoints = ({ favorites = [], pendingPoints = [], pendingAreas = [] }) => {
  const points = []

  favorites.forEach((favorite) => {
    if (favorite?.type === 'POINT') {
      const point = getFavoritePointLatLng(favorite)
      if (point) {
        points.push([point.lat, point.lng])
      }
      return
    }

    if (favorite?.type === 'AREA') {
      const bounds = toLeafletBounds(favorite)
      if (bounds) {
        points.push(bounds[0], bounds[1])
      }
    }
  })

  pendingPoints.forEach((pendingPoint) => {
    const point = getPendingPointLatLng(pendingPoint)
    if (point) {
      points.push([point.lat, point.lng])
    }
  })

  pendingAreas.forEach((pendingArea) => {
    const bounds = toLeafletBounds(pendingArea)
    if (bounds) {
      points.push(bounds[0], bounds[1])
    }
  })

  return points
}

export const areaPayload = (area) => area?.geometry
  ? { geometry: area.geometry }
  : getAreaBounds(area)

export const toLeafletPolygon = (area) => getAreaPolygonLngLat(area)?.map(([lng, lat]) => [lat, lng])

// Compare independently of ring direction and starting vertex.
export const areaGeometryKey = (area) => {
  const ring = getAreaPolygonLngLat(area)
  if (!ring) return ''
  const vertices = ring.slice(0, -1).map(c => JSON.stringify(c))
  const rotations = vertices.flatMap((_, i) => {
    const rotated = [...vertices.slice(i), ...vertices.slice(0, i)]
    return [rotated.join(';'), rotated.reverse().join(';')]
  })
  return rotations.sort()[0]
}

export const polygonError = (ring) => {
  if (ring.length < 4 || ring.length > 501) return 'vertices'
  if (ring.some(c => c.length !== 2 || !c.every(Number.isFinite) || Math.abs(c[0]) > 180 || Math.abs(c[1]) > 90)) return 'coordinates'
  if (ring[0][0] !== ring.at(-1)[0] || ring[0][1] !== ring.at(-1)[1]) return 'closed'
  const cross = (a, b, c) => (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
  const onSegment = (a, b, c) => cross(a, b, c) === 0 && c[0] >= Math.min(a[0], b[0]) && c[0] <= Math.max(a[0], b[0]) && c[1] >= Math.min(a[1], b[1]) && c[1] <= Math.max(a[1], b[1])
  let area = 0
  for (let i = 0; i < ring.length - 1; i++) {
    const a = ring[i], b = ring[i + 1]
    if (Math.abs(a[0] - b[0]) > 180) return 'antimeridian'
    if (a[0] === b[0] && a[1] === b[1]) return 'crossing'
    area += a[0] * b[1] - b[0] * a[1]
    for (let j = i + 2; j < ring.length - 1; j++) {
      if (i === 0 && j === ring.length - 2) continue
      const c = ring[j], d = ring[j + 1]
      if ((cross(a, b, c) * cross(a, b, d) < 0 && cross(c, d, a) * cross(c, d, b) < 0)
        || onSegment(a, b, c) || onSegment(a, b, d) || onSegment(c, d, a) || onSegment(c, d, b)) return 'crossing'
    }
  }
  return area === 0 ? 'crossing' : null
}
