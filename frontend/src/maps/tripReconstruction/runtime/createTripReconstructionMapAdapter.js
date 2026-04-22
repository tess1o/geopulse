import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'
import { createRasterTripReconstructionMapAdapter } from '@/maps/tripReconstruction/raster/createRasterTripReconstructionMapAdapter'
import { createVectorTripReconstructionMapAdapter } from '@/maps/tripReconstruction/vector/createVectorTripReconstructionMapAdapter'

export const createTripReconstructionMapAdapter = (mapInstance, callbacks = {}) => {
  const mode = resolveMapEngineModeFromInstance(mapInstance, MAP_RENDER_MODES.RASTER)

  if (mode === MAP_RENDER_MODES.VECTOR) {
    return createVectorTripReconstructionMapAdapter(callbacks)
  }

  return createRasterTripReconstructionMapAdapter(callbacks)
}
