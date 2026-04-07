import { usePhotoMapMarkers } from '@/composables/usePhotoMapMarkers'
import { usePhotoMapMarkersVector } from '@/maps/vector/composables/usePhotoMapMarkersVector'
import { isMapLibreMap } from '@/maps/vector/utils/maplibreLayerUtils'

export function usePhotoMapMarkersRuntime(options = {}) {
  const rasterMarkers = usePhotoMapMarkers(options)
  const vectorMarkers = usePhotoMapMarkersVector(options)

  const pickImplementation = (mapInstance) => {
    return isMapLibreMap(mapInstance) ? vectorMarkers : rasterMarkers
  }

  const clearPhotoMarkers = () => {
    rasterMarkers.clearPhotoMarkers?.()
    vectorMarkers.clearPhotoMarkers?.()
  }

  const clearFocusMarker = () => {
    rasterMarkers.clearFocusMarker?.()
    vectorMarkers.clearFocusMarker?.()
  }

  const renderPhotoMarkers = (mapInstance, photos = []) => {
    return pickImplementation(mapInstance).renderPhotoMarkers(mapInstance, photos)
  }

  const renderPhotoMarkerGroups = (mapInstance, markerGroups = []) => {
    return pickImplementation(mapInstance).renderPhotoMarkerGroups(mapInstance, markerGroups)
  }

  const focusOnCoordinates = (mapInstance, latitude, longitude, zoom = 16) => {
    return pickImplementation(mapInstance).focusOnCoordinates(mapInstance, latitude, longitude, zoom)
  }

  const focusOnPhoto = (mapInstance, photo, zoom = 16) => {
    return pickImplementation(mapInstance).focusOnPhoto(mapInstance, photo, zoom)
  }

  return {
    clearPhotoMarkers,
    clearFocusMarker,
    renderPhotoMarkers,
    renderPhotoMarkerGroups,
    focusOnCoordinates,
    focusOnPhoto
  }
}
