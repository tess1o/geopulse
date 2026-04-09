import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'
import { createRasterFavoriteAreaOverlayAdapter } from '@/maps/favoritesEdit/raster/createRasterFavoriteAreaOverlayAdapter'
import { createVectorFavoriteAreaOverlayAdapter } from '@/maps/favoritesEdit/vector/createVectorFavoriteAreaOverlayAdapter'

export const createFavoriteAreaOverlayAdapter = (mapInstance) => {
  const mode = resolveMapEngineModeFromInstance(mapInstance, MAP_RENDER_MODES.RASTER)
  if (mode === MAP_RENDER_MODES.VECTOR) {
    return createVectorFavoriteAreaOverlayAdapter()
  }

  return createRasterFavoriteAreaOverlayAdapter()
}

