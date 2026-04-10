import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'
import { createRasterDetailsMapAdapter } from '@/maps/details/raster/createRasterDetailsMapAdapter'
import { createVectorDetailsMapAdapter } from '@/maps/details/vector/createVectorDetailsMapAdapter'

export const createDetailsMapAdapter = (mapInstance, callbacks = {}) => {
  const mode = resolveMapEngineModeFromInstance(mapInstance, MAP_RENDER_MODES.RASTER)

  if (mode === MAP_RENDER_MODES.VECTOR) {
    return createVectorDetailsMapAdapter(callbacks)
  }

  return createRasterDetailsMapAdapter(callbacks)
}
