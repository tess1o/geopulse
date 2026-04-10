let layerSequence = 0

export function nextLayerToken(prefix = 'gp-layer') {
  layerSequence += 1
  return `${prefix}-${layerSequence}`
}

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

    // Leaflet-style corners: [[south, west], [north, east]]
    if (bounds.length === 2) {
      const southWest = toLngLatTuple(bounds[0])
      const northEast = toLngLatTuple(bounds[1])
      if (!southWest || !northEast) {
        return null
      }

      return [southWest, northEast]
    }

    // Leaflet fitBounds also accepts an array of points. Compute bounding box.
    if (bounds.length > 2) {
      let west = Infinity
      let south = Infinity
      let east = -Infinity
      let north = -Infinity

      bounds.forEach((point) => {
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

export function ensureGeoJsonSource(map, sourceId, data) {
  if (!isMapLibreMap(map)) {
    return
  }

  const source = map.getSource(sourceId)
  if (source && typeof source.setData === 'function') {
    source.setData(data)
    return
  }

  map.addSource(sourceId, {
    type: 'geojson',
    data
  })
}

export function ensureClusterSource(map, sourceId, data, options = {}) {
  if (!isMapLibreMap(map)) {
    return
  }

  const source = map.getSource(sourceId)
  if (source && typeof source.setData === 'function') {
    source.setData(data)
    return
  }

  map.addSource(sourceId, {
    type: 'geojson',
    data,
    cluster: Boolean(options.cluster),
    clusterRadius: Number.isFinite(options.clusterRadius) ? options.clusterRadius : 40,
    clusterMaxZoom: Number.isFinite(options.clusterMaxZoom) ? options.clusterMaxZoom : 14
  })
}

export function ensureLayer(map, layerConfig) {
  if (!isMapLibreMap(map) || !layerConfig?.id) {
    return
  }

  if (map.getLayer(layerConfig.id)) {
    return
  }

  map.addLayer(layerConfig)
}

export function setLayerVisibility(map, layerIds, visible) {
  if (!isMapLibreMap(map)) {
    return
  }

  const visibility = visible ? 'visible' : 'none'
  layerIds.forEach((layerId) => {
    if (!map.getLayer(layerId)) {
      return
    }

    map.setLayoutProperty(layerId, 'visibility', visibility)
  })
}

export function removeLayers(map, layerIds = []) {
  if (!isMapLibreMap(map)) {
    return
  }

  for (let index = layerIds.length - 1; index >= 0; index -= 1) {
    const layerId = layerIds[index]
    if (map.getLayer(layerId)) {
      map.removeLayer(layerId)
    }
  }
}

export function removeSources(map, sourceIds = []) {
  if (!isMapLibreMap(map)) {
    return
  }

  sourceIds.forEach((sourceId) => {
    if (map.getSource(sourceId)) {
      map.removeSource(sourceId)
    }
  })
}

export function safeFitBounds(map, bounds, options = {}) {
  if (!isMapLibreMap(map)) {
    return
  }

  const normalized = normalizeLeafletBoundsToMapLibre(bounds)
  if (!normalized) {
    return
  }

  map.fitBounds(normalized, options)
}

export function safeSetView(map, center, zoom, options = {}) {
  if (!isMapLibreMap(map)) {
    return
  }

  const lngLat = toLngLatTuple(center)
  if (!lngLat) {
    return
  }

  const currentZoom = map.getZoom()
  const targetZoom = Number.isFinite(zoom) ? zoom : currentZoom

  map.jumpTo({
    center: lngLat,
    zoom: targetZoom,
    ...options
  })
}

export function createFeatureCollection(features = []) {
  return {
    type: 'FeatureCollection',
    features: Array.isArray(features) ? features : []
  }
}
