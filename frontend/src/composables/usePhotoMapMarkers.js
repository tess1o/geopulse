import { ref } from 'vue'
import L from 'leaflet'
import 'leaflet.markercluster'
import 'leaflet.markercluster/dist/MarkerCluster.css'
import 'leaflet.markercluster/dist/MarkerCluster.Default.css'
import {
  buildPhotoGroupsFromPhotos,
  buildPhotoMarkerClickPayload,
  getPhotoMarkerCount,
  getSinglePhotoForThumbnail,
  normalizePhotoMarkerGroups
} from '@/maps/shared/photoMarkerGroups'
import { getPhotoThumbnailBlobUrl } from '@/utils/immichPhotoThumbnails'

const PHOTO_CLUSTER_MAX_RADIUS = 48
const PHOTO_CLUSTER_DISABLE_ZOOM = 16

const createPhotoMarkerIcon = (count, { thumbnailBlobUrl = null, alt = 'Immich photo thumbnail' } = {}) => {
  const badgeHtml = count > 1
    ? `<span class="gp-photo-map-marker-count">${count}</span>`
    : ''
  const safeAlt = String(alt).replace(/"/g, '&quot;')
  const contentHtml = thumbnailBlobUrl
    ? `<img class="gp-photo-map-marker-thumb" src="${thumbnailBlobUrl}" alt="${safeAlt}" />`
    : '<i class="pi pi-camera"></i>'

  return L.divIcon({
    className: 'gp-photo-map-marker-wrapper',
    html: `
      <div class="gp-photo-map-marker">
        ${contentHtml}
        ${badgeHtml}
      </div>
    `,
    iconSize: [34, 34],
    iconAnchor: [17, 34]
  })
}

const createPhotoClusterIcon = (count) => {
  const badgeHtml = count > 1
    ? `<span class="gp-photo-map-marker-count">${count}</span>`
    : ''

  return L.divIcon({
    className: 'gp-photo-map-marker-wrapper',
    html: `
      <div class="gp-photo-map-marker">
        <i class="pi pi-camera"></i>
        ${badgeHtml}
      </div>
    `,
    iconSize: [34, 34],
    iconAnchor: [17, 17]
  })
}

const createFocusedPhotoMarkerIcon = () => {
  return L.divIcon({
    className: 'gp-photo-map-focused-marker-wrapper',
    html: `
      <div class="gp-photo-map-focused-marker">
        <i class="pi pi-camera"></i>
      </div>
    `,
    iconSize: [28, 28],
    iconAnchor: [14, 34]
  })
}

export const usePhotoMapMarkers = ({ emit, markerZIndexOffset = 300, focusMarkerZIndexOffset = 500 } = {}) => {
  const photoMarkers = ref([])
  const markerClusterGroup = ref(null)
  const focusMarker = ref(null)
  let renderCycle = 0

  const emitPhotoClick = (payload) => {
    if (typeof emit === 'function') {
      emit('photo-click', payload)
    }
  }

  const clearPhotoMarkers = () => {
    renderCycle += 1

    if (markerClusterGroup.value) {
      markerClusterGroup.value.clearLayers()
      markerClusterGroup.value.remove()
      markerClusterGroup.value = null
    }

    photoMarkers.value.forEach((photoMarker) => {
      photoMarker.remove()
    })
    photoMarkers.value = []
  }

  const clearFocusMarker = () => {
    if (focusMarker.value) {
      focusMarker.value.remove()
      focusMarker.value = null
    }
  }

  const createClusterGroup = () => {
    if (typeof L.markerClusterGroup !== 'function') {
      return null
    }

    return L.markerClusterGroup({
      maxClusterRadius: PHOTO_CLUSTER_MAX_RADIUS,
      disableClusteringAtZoom: PHOTO_CLUSTER_DISABLE_ZOOM,
      spiderfyOnMaxZoom: true,
      showCoverageOnHover: false,
      zoomToBoundsOnClick: true,
      chunkedLoading: true,
      chunkInterval: 200,
      chunkDelay: 50,
      animate: false,
      removeOutsideVisibleBounds: true,
      iconCreateFunction: (cluster) => {
        const totalPhotos = cluster.getAllChildMarkers()
          .reduce((sum, marker) => sum + Number(marker.options?.photoCount || 1), 0)

        return createPhotoClusterIcon(totalPhotos)
      }
    })
  }

  const applyThumbnailToMarker = async (marker, group, currentRenderCycle) => {
    const photo = getSinglePhotoForThumbnail(group)
    if (!photo) {
      return
    }

    try {
      const thumbnailBlobUrl = await getPhotoThumbnailBlobUrl(photo)
      if (!thumbnailBlobUrl || currentRenderCycle !== renderCycle || !photoMarkers.value.includes(marker)) {
        return
      }

      marker.setIcon(createPhotoMarkerIcon(1, {
        thumbnailBlobUrl,
        alt: photo.originalFileName || 'Immich photo thumbnail'
      }))
    } catch {
      // Keep the camera marker when thumbnails cannot be loaded.
    }
  }

  const addPhotoMarker = (mapInstance, group, currentRenderCycle) => {
    const count = getPhotoMarkerCount(group)
    const marker = L.marker([group.latitude, group.longitude], {
      icon: createPhotoMarkerIcon(count),
      zIndexOffset: markerZIndexOffset,
      photoCount: count
    })

    marker.on('click', () => {
      emitPhotoClick(buildPhotoMarkerClickPayload(group))
    })

    if (markerClusterGroup.value) {
      markerClusterGroup.value.addLayer(marker)
    } else {
      marker.addTo(mapInstance)
    }

    photoMarkers.value.push(marker)
    applyThumbnailToMarker(marker, group, currentRenderCycle)
  }

  const renderGroups = (mapInstance, groups = []) => {
    if (!mapInstance) {
      return []
    }

    clearPhotoMarkers()
    const currentRenderCycle = renderCycle
    markerClusterGroup.value = createClusterGroup()
    if (markerClusterGroup.value) {
      mapInstance.addLayer(markerClusterGroup.value)
    }

    groups.forEach((group) => {
      addPhotoMarker(mapInstance, group, currentRenderCycle)
    })

    return groups
  }

  const renderPhotoMarkers = (mapInstance, photos = []) => {
    return renderGroups(mapInstance, buildPhotoGroupsFromPhotos(photos))
  }

  const renderPhotoMarkerGroups = (mapInstance, markerGroups = []) => {
    return renderGroups(mapInstance, normalizePhotoMarkerGroups(markerGroups))
  }

  const focusOnCoordinates = (mapInstance, latitude, longitude, zoom = 16) => {
    if (!mapInstance || typeof latitude !== 'number' || typeof longitude !== 'number') {
      return
    }

    clearFocusMarker()

    focusMarker.value = L.circleMarker([latitude, longitude], {
      radius: 7,
      color: '#1d4ed8',
      weight: 3,
      fillColor: '#60a5fa',
      fillOpacity: 0.9
    }).addTo(mapInstance)

    const targetZoom = Math.max(mapInstance.getZoom?.() || 0, zoom)
    mapInstance.setView([latitude, longitude], targetZoom, { animate: true })
  }

  const focusOnPhoto = (mapInstance, photo, zoom = 16) => {
    if (!mapInstance || !photo || typeof photo.latitude !== 'number' || typeof photo.longitude !== 'number') {
      return
    }

    clearFocusMarker()
    focusMarker.value = L.marker([photo.latitude, photo.longitude], {
      icon: createFocusedPhotoMarkerIcon(),
      zIndexOffset: focusMarkerZIndexOffset
    }).addTo(mapInstance)

    if (focusMarker.value.bringToFront) {
      focusMarker.value.bringToFront()
    }

    focusMarker.value.on('click', () => {
      emitPhotoClick({
        photos: [photo],
        indices: [0],
        initialIndex: 0
      })
    })

    const targetZoom = Math.max(mapInstance.getZoom?.() || 0, zoom)
    mapInstance.setView([photo.latitude, photo.longitude], targetZoom, { animate: true })
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
