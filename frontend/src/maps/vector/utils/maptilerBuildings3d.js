import { isMapLibreMap } from './maplibreLayerUtils'

export const MAPTILER_BUILDINGS_LAYER_ID = 'gp-maptiler-3d-buildings'

const getStyle = (map) => {
  if (!isMapLibreMap(map) || typeof map.getStyle !== 'function') return null

  try {
    return map.getStyle()
  } catch {
    return null
  }
}

const isMapTilerUrl = (url) => {
  try {
    const hostname = new URL(url).hostname
    return hostname === 'maptiler.com' || hostname.endsWith('.maptiler.com')
  } catch {
    return false
  }
}

const isMapTilerVectorSource = (source) => (
  source?.type === 'vector'
  && [source.url, ...(Array.isArray(source.tiles) ? source.tiles : [])].some(isMapTilerUrl)
)

const getMapTilerBuildingLayer = (map) => {
  const style = getStyle(map)
  if (!style) return null

  const mapTilerSources = Object.entries(style.sources || {})
    .filter(([, source]) => isMapTilerVectorSource(source))
  const mapTilerSourceIds = new Set(mapTilerSources.map(([sourceId]) => sourceId))

  const styledBuildingLayer = style.layers?.find((layer) => (
    mapTilerSourceIds.has(layer?.source) && layer['source-layer'] === 'building'
  ))

  return styledBuildingLayer || (mapTilerSources[0] && {
    source: mapTilerSources[0][0],
    'source-layer': 'building'
  })
}

export const supportsMapTilerBuildings3d = (map) => Boolean(getMapTilerBuildingLayer(map))

const findLabelLayerId = (layers = []) => layers.find((layer) => (
  layer?.type === 'symbol' && layer.layout?.['text-field']
))?.id

export const setMapTilerBuildings3dEnabled = (map, enabled) => {
  if (!isMapLibreMap(map) || typeof map.getLayer !== 'function') return false

  const existingLayer = map.getLayer(MAPTILER_BUILDINGS_LAYER_ID)
  if (!enabled) {
    if (existingLayer && typeof map.removeLayer === 'function') {
      map.removeLayer(MAPTILER_BUILDINGS_LAYER_ID)
    }
    return true
  }

  if (!supportsMapTilerBuildings3d(map)) return false
  if (existingLayer) return true

  const buildingLayer = getMapTilerBuildingLayer(map)
  const labelLayerId = findLabelLayerId(getStyle(map)?.layers)
  map.addLayer({
    id: MAPTILER_BUILDINGS_LAYER_ID,
    source: buildingLayer.source,
    'source-layer': buildingLayer['source-layer'],
    type: 'fill-extrusion',
    minzoom: 15,
    ...(buildingLayer.filter ? { filter: buildingLayer.filter } : {}),
    paint: {
      'fill-extrusion-color': '#aaa',
      'fill-extrusion-height': ['interpolate', ['linear'], ['zoom'], 15, 0, 15.05, ['coalesce', ['get', 'render_height'], ['get', 'height'], 0]],
      'fill-extrusion-base': ['interpolate', ['linear'], ['zoom'], 15, 0, 15.05, ['coalesce', ['get', 'render_min_height'], ['get', 'min_height'], 0]],
      'fill-extrusion-opacity': 0.6
    }
  }, labelLayerId)

  return true
}
