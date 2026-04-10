import { ref } from 'vue'
import maplibregl from 'maplibre-gl'

const MARKER_KEY_FACTOR = 10000

function createPhotoMarkerElement(count) {
  const wrapper = document.createElement('div')
  wrapper.className = 'gp-photo-map-marker-wrapper'

  const marker = document.createElement('div')
  marker.className = 'gp-photo-map-marker'
  marker.innerHTML = '<i class="pi pi-camera"></i>'

  if (count > 1) {
    const badge = document.createElement('span')
    badge.className = 'gp-photo-map-marker-count'
    badge.textContent = String(count)
    marker.appendChild(badge)
  }

  wrapper.appendChild(marker)
  return wrapper
}

function createFocusedMarkerElement() {
  const wrapper = document.createElement('div')
  wrapper.className = 'gp-photo-map-focused-marker-wrapper'

  const marker = document.createElement('div')
  marker.className = 'gp-photo-map-focused-marker'
  marker.innerHTML = '<i class="pi pi-camera"></i>'

  wrapper.appendChild(marker)
  return wrapper
}

function buildPhotoGroups(photos) {
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

function normalizeProvidedGroups(markerGroups = []) {
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
      count: Number.isFinite(group.count)
        ? Number(group.count)
        : (Number.isFinite(group.photoCount)
            ? Number(group.photoCount)
            : (Array.isArray(group.photos) ? group.photos.length : 1))
    }))
}

export function usePhotoMapMarkersVector({ emit } = {}) {
  const photoMarkers = ref([])
  const focusMarker = ref(null)

  const emitPhotoClick = (payload) => {
    if (typeof emit === 'function') {
      emit('photo-click', payload)
    }
  }

  const clearPhotoMarkers = () => {
    photoMarkers.value.forEach((entry) => {
      entry.marker.remove()
    })
    photoMarkers.value = []
  }

  const clearFocusMarker = () => {
    if (focusMarker.value) {
      focusMarker.value.remove()
      focusMarker.value = null
    }
  }

  const createAndAddMarker = (mapInstance, group) => {
    const element = createPhotoMarkerElement(Math.max(group.count || group.photos.length || 1, 1))
    element.addEventListener('click', () => {
      if (group.photos.length > 0) {
        emitPhotoClick({
          photos: group.photos,
          indices: group.indices,
          initialIndex: 0,
          markerGroup: {
            latitude: group.latitude,
            longitude: group.longitude,
            markerKey: group.markerKey,
            count: group.count || group.photos.length || 1
          }
        })
        return
      }

      emitPhotoClick({
        markerGroup: {
          latitude: group.latitude,
          longitude: group.longitude,
          markerKey: group.markerKey,
          count: group.count || 1
        }
      })
    })

    const marker = new maplibregl.Marker({ element, anchor: 'bottom' })
      .setLngLat([group.longitude, group.latitude])
      .addTo(mapInstance)

    photoMarkers.value.push({ marker, group })
  }

  const renderPhotoMarkers = (mapInstance, photos = []) => {
    if (!mapInstance) {
      return []
    }

    clearPhotoMarkers()
    const groups = buildPhotoGroups(Array.isArray(photos) ? photos : [])

    groups.forEach((group) => {
      createAndAddMarker(mapInstance, {
        ...group,
        markerKey: `${group.latitude},${group.longitude}`,
        count: group.photos.length
      })
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
      createAndAddMarker(mapInstance, group)
    })

    return groups
  }

  const focusOnCoordinates = (mapInstance, latitude, longitude, zoom = 16) => {
    if (!mapInstance || typeof latitude !== 'number' || typeof longitude !== 'number') {
      return
    }

    clearFocusMarker()

    focusMarker.value = new maplibregl.Marker({
      element: createFocusedMarkerElement(),
      anchor: 'bottom'
    })
      .setLngLat([longitude, latitude])
      .addTo(mapInstance)

    const currentZoom = mapInstance.getZoom?.() ?? 0
    const targetZoom = Math.max(currentZoom, zoom)
    mapInstance.easeTo({ center: [longitude, latitude], zoom: targetZoom, duration: 300 })
  }

  const focusOnPhoto = (mapInstance, photo, zoom = 16) => {
    if (!mapInstance || typeof photo?.latitude !== 'number' || typeof photo?.longitude !== 'number') {
      return
    }

    clearFocusMarker()

    const element = createFocusedMarkerElement()
    element.addEventListener('click', () => {
      emitPhotoClick({
        photos: [photo],
        indices: [0],
        initialIndex: 0
      })
    })

    focusMarker.value = new maplibregl.Marker({
      element,
      anchor: 'bottom'
    })
      .setLngLat([photo.longitude, photo.latitude])
      .addTo(mapInstance)

    const currentZoom = mapInstance.getZoom?.() ?? 0
    const targetZoom = Math.max(currentZoom, zoom)
    mapInstance.easeTo({ center: [photo.longitude, photo.latitude], zoom: targetZoom, duration: 300 })
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
