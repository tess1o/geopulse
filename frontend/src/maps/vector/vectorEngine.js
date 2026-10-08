// Everything that needs MapLibre at runtime, loaded as one lazy chunk by vectorEngineRegistry.loadVectorEngine().
// Code outside src/maps/vector must reach these through getVectorEngine(), never through a static import.

export * as maplibregl from 'maplibre-gl'
export * as maplibreLayerUtils from './utils/maplibreLayerUtils'

export { default as VectorCoverageLayer } from './layers/VectorCoverageLayer.vue'
export { default as VectorCrossTypeCollisionLayer } from './layers/VectorCrossTypeCollisionLayer.vue'
export { default as VectorCurrentLocationLayer } from './layers/VectorCurrentLocationLayer.vue'
export { default as VectorFavoritesLayer } from './layers/VectorFavoritesLayer.vue'
export { default as VectorFriendsLayer } from './layers/VectorFriendsLayer.vue'
export { default as VectorHeatmapLayer } from './layers/VectorHeatmapLayer.vue'
export { default as VectorImmichLayer } from './layers/VectorImmichLayer.vue'
export { default as VectorLocationAnalyticsDotsLayer } from './layers/VectorLocationAnalyticsDotsLayer.vue'
export { default as VectorNotesLayer } from './layers/VectorNotesLayer.vue'
export { default as VectorPanoramaxLayer } from './layers/VectorPanoramaxLayer.vue'
export { default as VectorPathLayer } from './layers/VectorPathLayer.vue'
export { default as VectorRawGpsPointsLayer } from './layers/VectorRawGpsPointsLayer.vue'
export { default as VectorTimelineLayer } from './layers/VectorTimelineLayer.vue'
export { default as VectorTripPlanLayer } from './layers/VectorTripPlanLayer.vue'
export { default as VectorTripPlanRouteLayer } from './layers/VectorTripPlanRouteLayer.vue'
export { default as VectorWeatherLayer } from './layers/VectorWeatherLayer.vue'
export { default as VectorSharedLocationMarker } from './markers/VectorSharedLocationMarker.vue'
export { default as VectorViewerLocationMarker } from './markers/VectorViewerLocationMarker.vue'

export { usePhotoMapMarkersVector } from './composables/usePhotoMapMarkersVector'

export { createVectorDetailsMapAdapter } from '@/maps/details/vector/createVectorDetailsMapAdapter'
export { createVectorFavoriteAreaOverlayAdapter } from '@/maps/favoritesEdit/vector/createVectorFavoriteAreaOverlayAdapter'
export { createVectorFavoritesManagementMapAdapter } from '@/maps/favoritesManagement/vector/createVectorFavoritesManagementMapAdapter'
export { createVectorFriendsTimelineMapAdapter } from '@/maps/friendsTimeline/vector/createVectorFriendsTimelineMapAdapter'
export { createVectorGeofenceRulesMapAdapter } from '@/maps/geofences/vector/createVectorGeofenceRulesMapAdapter'
export { createVectorTripReconstructionMapAdapter } from '@/maps/tripReconstruction/vector/createVectorTripReconstructionMapAdapter'
