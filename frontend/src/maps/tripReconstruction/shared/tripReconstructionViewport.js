import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'
import { resolveActiveSegmentViewport } from '@/maps/tripReconstruction/shared/tripReconstructionMapData'

export const normalizeMapLibrePadding = (paddingValue) => {
  if (Array.isArray(paddingValue) && paddingValue.length >= 2) {
    const horizontal = Number(paddingValue[0])
    const vertical = Number(paddingValue[1])

    if (Number.isFinite(horizontal) && Number.isFinite(vertical)) {
      return {
        left: horizontal,
        right: horizontal,
        top: vertical,
        bottom: vertical
      }
    }
  }

  return paddingValue
}

export const setMapView = (mapInstance, center, zoom, options = {}) => {
  if (!mapInstance) return
  if (!Array.isArray(center) || center.length < 2) return

  mapInstance.setView(center, zoom, options)
}

export const fitMapToBounds = (mapInstance, bounds, options = {}) => {
  if (!mapInstance || !Array.isArray(bounds) || bounds.length < 2) {
    return
  }

  const mode = resolveMapEngineModeFromInstance(mapInstance, MAP_RENDER_MODES.RASTER)
  if (mode === MAP_RENDER_MODES.VECTOR) {
    mapInstance.fitBounds(
      bounds.map(([latitude, longitude]) => [longitude, latitude]),
      {
        ...options,
        padding: normalizeMapLibrePadding(options.padding)
      }
    )
    return
  }

  mapInstance.fitBounds(bounds, options)
}

export const fitMapToActiveSegment = (mapInstance, segments, activeSegmentIndex) => {
  const viewport = resolveActiveSegmentViewport(segments, activeSegmentIndex)
  if (!viewport) {
    return
  }

  if (viewport.type === 'fit-bounds') {
    fitMapToBounds(mapInstance, viewport.bounds, viewport.options || {})
    return
  }

  setMapView(mapInstance, viewport.center, viewport.zoom, { animate: true })
}
