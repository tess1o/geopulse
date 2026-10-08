import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'
import { createRasterFavoritesManagementMapAdapter } from '@/maps/favoritesManagement/raster/createRasterFavoritesManagementMapAdapter'
import { getVectorEngine } from '@/maps/runtime/vectorEngineRegistry'

export const createFavoritesManagementMapAdapter = (mapInstance, callbacks = {}) => {
  const mode = resolveMapEngineModeFromInstance(mapInstance, MAP_RENDER_MODES.RASTER)

  if (mode === MAP_RENDER_MODES.VECTOR) {
    return getVectorEngine().createVectorFavoritesManagementMapAdapter(callbacks)
  }

  return createRasterFavoritesManagementMapAdapter(callbacks)
}

