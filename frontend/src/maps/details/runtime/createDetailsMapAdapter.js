import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'
import { createRasterDetailsMapAdapter } from '@/maps/details/raster/createRasterDetailsMapAdapter'
import { getVectorEngine } from '@/maps/runtime/vectorEngineRegistry'

export const createDetailsMapAdapter = (mapInstance, callbacks = {}) => {
  const mode = resolveMapEngineModeFromInstance(mapInstance, MAP_RENDER_MODES.RASTER)

  if (mode === MAP_RENDER_MODES.VECTOR) {
    return getVectorEngine().createVectorDetailsMapAdapter(callbacks)
  }

  return createRasterDetailsMapAdapter(callbacks)
}
