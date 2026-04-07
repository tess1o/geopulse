export const MAP_RENDER_MODES = Object.freeze({
  RASTER: 'RASTER',
  VECTOR: 'VECTOR'
})

export const MAP_EVENTS = Object.freeze({
  MAP_READY: 'map-ready',
  MAP_CLICK: 'map-click',
  MAP_CONTEXTMENU: 'map-contextmenu',
  MAP_WARNING: 'map-warning',
  ENGINE_FATAL: 'engine-fatal'
})

export const MAP_ENGINE_HINT_KEY = '__gpEngineMode'

/**
 * @typedef {Object} GeoPoint
 * @property {number} lat
 * @property {number} lng
 */

/**
 * @typedef {Object} GeoBounds
 * @property {number} south
 * @property {number} west
 * @property {number} north
 * @property {number} east
 */

/**
 * @typedef {Object} LineString
 * @property {GeoPoint[]} points
 */

/**
 * @typedef {Object} MapHostContract
 * @property {(center:number[], zoom:number, options?:Object) => void} setView
 * @property {(bounds:any, options?:Object) => void} fitBounds
 * @property {() => void} invalidateSize
 */

/**
 * @typedef {Object} LayerAdapterContract
 * @property {(map:any) => void} mount
 * @property {() => void} unmount
 * @property {(data:any) => void} updateData
 * @property {(visible:boolean) => void} setVisible
 * @property {(zIndex:number) => void} setZIndex
 */

export function normalizeMapRenderMode(mode) {
  return mode === MAP_RENDER_MODES.RASTER ? MAP_RENDER_MODES.RASTER : MAP_RENDER_MODES.VECTOR
}

export function markMapEngineMode(mapInstance, mode) {
  if (!mapInstance || typeof mapInstance !== 'object') {
    return
  }

  try {
    mapInstance[MAP_ENGINE_HINT_KEY] = normalizeMapRenderMode(mode)
  } catch {
    // Non-extensible map objects are ignored.
  }
}

export function resolveMapEngineModeFromInstance(mapInstance, fallback = MAP_RENDER_MODES.VECTOR) {
  if (!mapInstance) {
    return normalizeMapRenderMode(fallback)
  }

  const hintedMode = mapInstance[MAP_ENGINE_HINT_KEY]
  if (hintedMode === MAP_RENDER_MODES.RASTER || hintedMode === MAP_RENDER_MODES.VECTOR) {
    return hintedMode
  }

  // Leaflet heuristics
  if (typeof mapInstance.addLayer === 'function' && typeof mapInstance.setView === 'function') {
    return MAP_RENDER_MODES.RASTER
  }

  // MapLibre heuristics
  if (typeof mapInstance.addSource === 'function' && typeof mapInstance.setStyle === 'function') {
    return MAP_RENDER_MODES.VECTOR
  }

  return normalizeMapRenderMode(fallback)
}

export function isRasterMapInstance(mapInstance) {
  return resolveMapEngineModeFromInstance(mapInstance) === MAP_RENDER_MODES.RASTER
}

export function isVectorMapInstance(mapInstance) {
  return resolveMapEngineModeFromInstance(mapInstance) === MAP_RENDER_MODES.VECTOR
}
