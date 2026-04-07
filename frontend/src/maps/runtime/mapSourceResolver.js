import { useAuthStore } from '@/stores/auth'
import { MAP_RENDER_MODES, normalizeMapRenderMode } from '@/maps/contracts/mapContracts'

export const DEFAULT_RASTER_TILE_URL = '/osm/tiles/{s}/{z}/{x}/{y}.png'
export const DEFAULT_VECTOR_STYLE_URL = 'https://tiles.openfreemap.org/styles/liberty'

const GENERIC_VECTOR_ATTRIBUTION = '© <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap contributors</a>'

function readAuthSnapshot() {
  try {
    const authStore = useAuthStore()
    return {
      customMapTileUrl: authStore.customMapTileUrl || null,
      customMapStyleUrl: authStore.customMapStyleUrl || null,
      mapRenderMode: authStore.mapRenderMode || MAP_RENDER_MODES.VECTOR
    }
  } catch (error) {
    return {
      customMapTileUrl: null,
      customMapStyleUrl: null,
      mapRenderMode: MAP_RENDER_MODES.VECTOR
    }
  }
}

function normalizeOptionalUrl(url) {
  if (typeof url !== 'string') {
    return null
  }

  const normalized = url.trim()
  return normalized.length > 0 ? normalized : null
}

function appendVersionHash(url) {
  if (!url) {
    return DEFAULT_RASTER_TILE_URL
  }

  try {
    const urlHash = btoa(url).substring(0, 8)
    const separator = url.includes('?') ? '&' : '?'
    return `${url}${separator}v=${urlHash}`
  } catch {
    return url
  }
}

function resolveRasterAttribution(tileUrl) {
  const normalizedUrl = (tileUrl || '').toLowerCase()

  if (normalizedUrl.includes('maptiler.com')) {
    return '© <a href="https://www.maptiler.com/copyright/" target="_blank">MapTiler</a> © <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap contributors</a>'
  }
  if (normalizedUrl.includes('mapbox.com')) {
    return '© <a href="https://www.mapbox.com/about/maps/" target="_blank">Mapbox</a> © <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap contributors</a>'
  }
  if (normalizedUrl.includes('thunderforest.com')) {
    return '© <a href="https://www.thunderforest.com/" target="_blank">Thunderforest</a> © <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap contributors</a>'
  }
  if (normalizedUrl.includes('stadiamaps.com')) {
    return '© <a href="https://stadiamaps.com/" target="_blank">Stadia Maps</a> © <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap contributors</a>'
  }
  if (tileUrl && tileUrl !== DEFAULT_RASTER_TILE_URL) {
    return '© Custom Tile Provider © <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap contributors</a>'
  }

  return '© <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap</a> contributors'
}

function resolveSubdomains(tileUrl) {
  if (tileUrl?.includes('{s}')) {
    return ['a', 'b', 'c']
  }

  if (!tileUrl || tileUrl === DEFAULT_RASTER_TILE_URL) {
    return ['a', 'b', 'c']
  }

  return []
}

export function resolveEffectiveMapMode(options = {}) {
  const {
    isSharedView = false,
    overrideRenderMode = null,
    overrideTileUrl = null,
    overrideStyleUrl = null
  } = options

  const authSnapshot = readAuthSnapshot()
  const modeOverride = overrideRenderMode || null

  if (modeOverride) {
    return normalizeMapRenderMode(modeOverride)
  }

  if (!isSharedView) {
    return normalizeMapRenderMode(authSnapshot.mapRenderMode)
  }

  const hasLegacyRasterOnlySource = Boolean(normalizeOptionalUrl(overrideTileUrl)) && !normalizeOptionalUrl(overrideStyleUrl)
  if (hasLegacyRasterOnlySource) {
    return MAP_RENDER_MODES.RASTER
  }

  return MAP_RENDER_MODES.VECTOR
}

export function resolveRasterTileSource(options = {}) {
  const {
    isSharedView = false,
    overrideTileUrl = null
  } = options

  const authSnapshot = readAuthSnapshot()
  const preferredTileUrl = normalizeOptionalUrl(overrideTileUrl)
    || (!isSharedView ? normalizeOptionalUrl(authSnapshot.customMapTileUrl) : null)
    || DEFAULT_RASTER_TILE_URL

  return {
    tileUrl: appendVersionHash(preferredTileUrl),
    attribution: resolveRasterAttribution(preferredTileUrl),
    subdomains: resolveSubdomains(preferredTileUrl),
    isCustom: preferredTileUrl !== DEFAULT_RASTER_TILE_URL
  }
}

export function resolveVectorStyleSource(options = {}) {
  const {
    isSharedView = false,
    overrideStyleUrl = null
  } = options

  const authSnapshot = readAuthSnapshot()
  const preferredStyleUrl = normalizeOptionalUrl(overrideStyleUrl)
    || (!isSharedView ? normalizeOptionalUrl(authSnapshot.customMapStyleUrl) : null)
    || DEFAULT_VECTOR_STYLE_URL

  return {
    styleUrl: preferredStyleUrl,
    attribution: GENERIC_VECTOR_ATTRIBUTION,
    isCustom: preferredStyleUrl !== DEFAULT_VECTOR_STYLE_URL
  }
}
