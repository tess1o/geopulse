import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'
import { createRasterFriendsTimelineMapAdapter } from '@/maps/friendsTimeline/raster/createRasterFriendsTimelineMapAdapter'
import { createVectorFriendsTimelineMapAdapter } from '@/maps/friendsTimeline/vector/createVectorFriendsTimelineMapAdapter'

export const createFriendsTimelineMapAdapter = (mapInstance, callbacks = {}) => {
  const mode = resolveMapEngineModeFromInstance(mapInstance, MAP_RENDER_MODES.RASTER)
  if (mode === MAP_RENDER_MODES.VECTOR) {
    return createVectorFriendsTimelineMapAdapter(callbacks)
  }

  return createRasterFriendsTimelineMapAdapter(callbacks)
}
