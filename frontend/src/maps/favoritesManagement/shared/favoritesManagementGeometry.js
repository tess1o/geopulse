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
  const bounds = getAreaBounds(areaLike)
  if (!bounds) {
    return null
  }

  return {
    lat: (bounds.southWestLat + bounds.northEastLat) / 2,
    lng: (bounds.southWestLon + bounds.northEastLon) / 2
  }
}

export const getAreaPolygonLngLat = (areaLike) => {
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
