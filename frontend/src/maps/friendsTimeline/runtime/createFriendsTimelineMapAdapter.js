import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'
import { createRasterFriendsTimelineMapAdapter } from '@/maps/friendsTimeline/raster/createRasterFriendsTimelineMapAdapter'
import { getVectorEngine } from '@/maps/runtime/vectorEngineRegistry'

export const createFriendsTimelineMapAdapter = (mapInstance, callbacks = {}) => {
  const mode = resolveMapEngineModeFromInstance(mapInstance, MAP_RENDER_MODES.RASTER)
  if (mode === MAP_RENDER_MODES.VECTOR) {
    return getVectorEngine().createVectorFriendsTimelineMapAdapter(callbacks)
  }

  return createRasterFriendsTimelineMapAdapter(callbacks)
}
