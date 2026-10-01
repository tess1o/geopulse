import L from 'leaflet'
import 'leaflet.markercluster'
import '@/styles/vendor/leaflet-plugins.css'

export const DEFAULT_CLUSTER_RADIUS = 48
export const DEFAULT_CLUSTER_DISABLE_ZOOM = 16

/**
 * Shared factory for Leaflet marker-cluster groups so Timeline, Notes and
 * Photos all get the same always-on, zoom-aware clustering behavior instead
 * of each layer hand-rolling (and subtly diverging on) its own options.
 */
export const createMarkerClusterGroup = ({
  maxClusterRadius = DEFAULT_CLUSTER_RADIUS,
  disableClusteringAtZoom = DEFAULT_CLUSTER_DISABLE_ZOOM,
  spiderfyOnMaxZoom = false,
  spiderfyOnEveryZoom = false,
  showCoverageOnHover = false,
  zoomToBoundsOnClick = true,
  chunkedLoading = true,
  chunkInterval = 200,
  chunkDelay = 50,
  animate = false,
  animateAddingMarkers = false,
  removeOutsideVisibleBounds = true,
  iconCreateFunction
} = {}) => {
  if (typeof L.markerClusterGroup !== 'function') {
    return null
  }

  return L.markerClusterGroup({
    maxClusterRadius,
    disableClusteringAtZoom,
    spiderfyOnMaxZoom,
    spiderfyOnEveryZoom,
    showCoverageOnHover,
    zoomToBoundsOnClick,
    chunkedLoading,
    chunkInterval,
    chunkDelay,
    animate,
    animateAddingMarkers,
    removeOutsideVisibleBounds,
    iconCreateFunction
  })
}
