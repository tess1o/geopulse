import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'
import { createRasterGeofenceRulesMapAdapter } from '@/maps/geofences/raster/createRasterGeofenceRulesMapAdapter'
import { getVectorEngine } from '@/maps/runtime/vectorEngineRegistry'

export const createGeofenceRulesMapAdapter = (mapInstance, callbacks = {}) => {
  const mode = resolveMapEngineModeFromInstance(mapInstance, MAP_RENDER_MODES.RASTER)

  if (mode === MAP_RENDER_MODES.VECTOR) {
    return getVectorEngine().createVectorGeofenceRulesMapAdapter(callbacks)
  }

  return createRasterGeofenceRulesMapAdapter(callbacks)
}
