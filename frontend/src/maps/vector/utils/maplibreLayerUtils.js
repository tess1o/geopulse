import {
  isMapLibreMap,
  toFiniteNumber,
  toLngLatTuple,
  toLatLngTuple,
  normalizeLeafletBoundsToMapLibre,
  createFeatureCollection
} from '@/maps/shared/mapEngineUtils'

// Re-exported so vector modules keep importing everything layer-related from one place.
export {
  isMapLibreMap,
  toFiniteNumber,
  toLngLatTuple,
  toLatLngTuple,
  normalizeLeafletBoundsToMapLibre,
  createFeatureCollection
}

let layerSequence = 0

export function nextLayerToken(prefix = 'gp-layer') {
  layerSequence += 1
  return `${prefix}-${layerSequence}`
}

export function hasMapLibreStyle(map) {
  return Boolean(isMapLibreMap(map) && map.style)
}

export function hasMapLibreLayer(map, layerId) {
  if (!hasMapLibreStyle(map) || !layerId || typeof map.getLayer !== 'function') {
    return false
  }

  try {
    return Boolean(map.getLayer(layerId))
  } catch {
    return false
  }
}

export function hasMapLibreSource(map, sourceId) {
  if (!hasMapLibreStyle(map) || !sourceId || typeof map.getSource !== 'function') {
    return false
  }

  try {
    return Boolean(map.getSource(sourceId))
  } catch {
    return false
  }
}

export function getMapLibreSource(map, sourceId) {
  if (!hasMapLibreStyle(map) || !sourceId || typeof map.getSource !== 'function') {
    return null
  }

  try {
    return map.getSource(sourceId) || null
  } catch {
    return null
  }
}

export function ensureGeoJsonSource(map, sourceId, data) {
  if (!hasMapLibreStyle(map)) {
    return
  }

  const source = getMapLibreSource(map, sourceId)
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
  if (!hasMapLibreStyle(map)) {
    return
  }

  const source = getMapLibreSource(map, sourceId)
  if (source && typeof source.setData === 'function') {
    source.setData(data)
    return
  }

  map.addSource(sourceId, {
    type: 'geojson',
    data,
    cluster: Boolean(options.cluster),
    clusterRadius: Number.isFinite(options.clusterRadius) ? options.clusterRadius : 40,
    clusterMaxZoom: Number.isFinite(options.clusterMaxZoom) ? options.clusterMaxZoom : 14,
    ...(options.clusterProperties ? { clusterProperties: options.clusterProperties } : {})
  })
}

// `beforeId` places the new layer under an existing one (e.g. a casing under its line); ignored when absent.
export function ensureLayer(map, layerConfig, beforeId = null) {
  if (!hasMapLibreStyle(map) || !layerConfig?.id) {
    return
  }

  if (hasMapLibreLayer(map, layerConfig.id)) {
    return
  }

  if (beforeId && hasMapLibreLayer(map, beforeId)) {
    map.addLayer(layerConfig, beforeId)
    return
  }

  map.addLayer(layerConfig)
}

export function setLayerVisibility(map, layerIds, visible) {
  if (!hasMapLibreStyle(map)) {
    return
  }

  const visibility = visible ? 'visible' : 'none'
  layerIds.forEach((layerId) => {
    if (!hasMapLibreLayer(map, layerId)) {
      return
    }

    map.setLayoutProperty(layerId, 'visibility', visibility)
  })
}

export function removeLayers(map, layerIds = []) {
  if (!hasMapLibreStyle(map)) {
    return
  }

  for (let index = layerIds.length - 1; index >= 0; index -= 1) {
    const layerId = layerIds[index]
    if (hasMapLibreLayer(map, layerId)) {
      try {
        map.removeLayer(layerId)
      } catch {
        // MapLibre may drop its style while Vue is unmounting overlay components.
      }
    }
  }
}

export function removeSources(map, sourceIds = []) {
  if (!hasMapLibreStyle(map)) {
    return
  }

  sourceIds.forEach((sourceId) => {
    if (hasMapLibreSource(map, sourceId)) {
      try {
        map.removeSource(sourceId)
      } catch {
        // MapLibre may drop its style while Vue is unmounting overlay components.
      }
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

