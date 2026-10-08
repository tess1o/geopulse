import { usePhotoMapMarkers } from '@/composables/usePhotoMapMarkers'
import { getVectorEngine } from '@/maps/runtime/vectorEngineRegistry'
import { isMapLibreMap } from '@/maps/shared/mapEngineUtils'

export function usePhotoMapMarkersRuntime(options = {}) {
  const rasterMarkers = usePhotoMapMarkers(options)
  // Created on first use with a MapLibre map: the vector engine (and maplibre-gl) is only loaded for vector maps.
  let vectorMarkers = null

  const getVectorMarkers = () => {
    vectorMarkers ||= getVectorEngine().usePhotoMapMarkersVector(options)
    return vectorMarkers
  }

  const pickImplementation = (mapInstance) => {
    return isMapLibreMap(mapInstance) ? getVectorMarkers() : rasterMarkers
  }

  const clearPhotoMarkers = () => {
    rasterMarkers.clearPhotoMarkers?.()
    vectorMarkers?.clearPhotoMarkers?.()
  }

  const clearFocusMarker = () => {
    rasterMarkers.clearFocusMarker?.()
    vectorMarkers?.clearFocusMarker?.()
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
