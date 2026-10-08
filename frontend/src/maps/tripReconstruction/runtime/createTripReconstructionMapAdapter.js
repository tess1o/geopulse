import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'
import { createRasterTripReconstructionMapAdapter } from '@/maps/tripReconstruction/raster/createRasterTripReconstructionMapAdapter'
import { getVectorEngine } from '@/maps/runtime/vectorEngineRegistry'

export const createTripReconstructionMapAdapter = (mapInstance, callbacks = {}) => {
  const mode = resolveMapEngineModeFromInstance(mapInstance, MAP_RENDER_MODES.RASTER)

  if (mode === MAP_RENDER_MODES.VECTOR) {
    return getVectorEngine().createVectorTripReconstructionMapAdapter(callbacks)
  }

  return createRasterTripReconstructionMapAdapter(callbacks)
}
