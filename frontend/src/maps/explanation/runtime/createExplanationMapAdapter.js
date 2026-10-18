import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'
import { createRasterExplanationMapAdapter } from '@/maps/explanation/raster/createRasterExplanationMapAdapter'
import { getVectorEngine } from '@/maps/runtime/vectorEngineRegistry'

export const createExplanationMapAdapter = (mapInstance, callbacks = {}) => {
  const mode = resolveMapEngineModeFromInstance(mapInstance, MAP_RENDER_MODES.RASTER)

  if (mode === MAP_RENDER_MODES.VECTOR) {
    return getVectorEngine().createVectorExplanationMapAdapter(callbacks)
  }

  return createRasterExplanationMapAdapter(callbacks)
}
