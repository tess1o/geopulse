import { ref } from 'vue'
import L from 'leaflet'

const MARKER_KEY_FACTOR = 10000

const createPhotoMarkerIcon = (count) => {
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
    iconAnchor: [17, 34]
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

const buildPhotoGroups = (photos) => {
  const groups = new Map()

  photos.forEach((photo, index) => {
    if (typeof photo?.latitude !== 'number' || typeof photo?.longitude !== 'number') {
      return
    }

    const roundedLat = Math.round(photo.latitude * MARKER_KEY_FACTOR) / MARKER_KEY_FACTOR
    const roundedLng = Math.round(photo.longitude * MARKER_KEY_FACTOR) / MARKER_KEY_FACTOR
    const key = `${roundedLat},${roundedLng}`

    if (!groups.has(key)) {
      groups.set(key, {
        latitude: roundedLat,
        longitude: roundedLng,
        photos: [],
        indices: []
      })
    }

    const group = groups.get(key)
    group.photos.push(photo)
    group.indices.push(index)
  })

  return Array.from(groups.values())
}

const normalizeProvidedGroups = (markerGroups = []) => {
  if (!Array.isArray(markerGroups)) {
    return []
  }

  return markerGroups
    .filter((group) => typeof group?.latitude === 'number' && typeof group?.longitude === 'number')
    .map((group) => ({
      latitude: group.latitude,
      longitude: group.longitude,
      photos: Array.isArray(group.photos) ? group.photos : [],
      indices: Array.isArray(group.indices) ? group.indices : [],
      markerKey: group.markerKey || `${group.latitude},${group.longitude}`,
      count: Number.isFinite(group.count) ? Number(group.count) : (
        Number.isFinite(group.photoCount) ? Number(group.photoCount) : (Array.isArray(group.photos) ? group.photos.length : 1)
      )
    }))
}

export const usePhotoMapMarkers = ({ emit, markerZIndexOffset = 300, focusMarkerZIndexOffset = 500 } = {}) => {
  const photoMarkers = ref([])
  const focusMarker = ref(null)

  const emitPhotoClick = (payload) => {
    if (typeof emit === 'function') {
      emit('photo-click', payload)
    }
  }

  const clearPhotoMarkers = () => {
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

  const renderPhotoMarkers = (mapInstance, photos = []) => {
    if (!mapInstance) {
      return []
    }

    clearPhotoMarkers()
    const groups = buildPhotoGroups(Array.isArray(photos) ? photos : [])

    groups.forEach((group) => {
      const marker = L.marker([group.latitude, group.longitude], {
        icon: createPhotoMarkerIcon(group.photos.length),
        zIndexOffset: markerZIndexOffset
      }).addTo(mapInstance)

      marker.on('click', () => {
        emitPhotoClick({
          photos: group.photos,
          indices: group.indices,
          initialIndex: 0
        })
      })

      photoMarkers.value.push(marker)
    })

    return groups
  }

  const renderPhotoMarkerGroups = (mapInstance, markerGroups = []) => {
    if (!mapInstance) {
      return []
    }

    clearPhotoMarkers()
    const groups = normalizeProvidedGroups(markerGroups)

    groups.forEach((group) => {
      const marker = L.marker([group.latitude, group.longitude], {
        icon: createPhotoMarkerIcon(Math.max(group.count || 1, 1)),
        zIndexOffset: markerZIndexOffset
      }).addTo(mapInstance)

      marker.on('click', () => {
        if (group.photos.length > 0) {
          emitPhotoClick({
            photos: group.photos,
            indices: group.indices,
            initialIndex: 0,
            markerGroup: {
              latitude: group.latitude,
              longitude: group.longitude,
              markerKey: group.markerKey,
              count: group.count
            }
          })
          return
        }

        emitPhotoClick({
          markerGroup: {
            latitude: group.latitude,
            longitude: group.longitude,
            markerKey: group.markerKey,
            count: group.count
          }
        })
      })

      photoMarkers.value.push(marker)
    })

    return groups
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
