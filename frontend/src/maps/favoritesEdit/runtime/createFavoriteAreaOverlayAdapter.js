import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'
import { createRasterFavoriteAreaOverlayAdapter } from '@/maps/favoritesEdit/raster/createRasterFavoriteAreaOverlayAdapter'
import { getVectorEngine } from '@/maps/runtime/vectorEngineRegistry'

export const createFavoriteAreaOverlayAdapter = (mapInstance) => {
  const mode = resolveMapEngineModeFromInstance(mapInstance, MAP_RENDER_MODES.RASTER)
  if (mode === MAP_RENDER_MODES.VECTOR) {
    return getVectorEngine().createVectorFavoriteAreaOverlayAdapter()
  }

  return createRasterFavoriteAreaOverlayAdapter()
}

