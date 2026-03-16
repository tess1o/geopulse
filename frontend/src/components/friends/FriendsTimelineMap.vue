<template>
  <div class="friends-timeline-map">
    <MapContainer
        :map-id="mapId"
        :center="mapCenter"
        :zoom="13"
        height="100%"
        width="100%"
        :show-controls="false"
        @map-ready="handleMapReady"
    >
      <template #overlays="{ map, isReady }">
        <!-- Multi-user timeline markers -->
        <div v-if="map && isReady && visibleTimelines.length > 0">
          <!-- Render markers for each user's timeline -->
          <component
              v-for="userTimeline in visibleTimelines"
              :key="userTimeline.userId"
              :is="'div'"
          >
            <!-- This would ideally use a MultiUserTimelineLayer component -->
            <!-- For now, we'll rely on the map being populated via direct Leaflet API -->
          </component>
        </div>
      </template>

      <template #controls="{ map, isReady }">
        <!-- Legend removed - user selection is handled by UserSelectionPanel -->
      </template>
    </MapContainer>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import L from 'leaflet'
import { MapContainer } from '@/components/maps'
import { createHighlightedPathStartMarker, createHighlightedPathEndMarker } from '@/utils/mapHelpers'
import {
  normalizePathPoints,
  reconstructTripPathPoints,
  resolveTripMarkerPoint,
  areSameCoordinate
} from '@/utils/tripPathReconstruction'

const props = defineProps({
  multiUserTimeline: {
    type: Object,
    default: null
  },
  selectedUserIds: {
    type: Set,
    default: () => new Set()
  },
  userColorMap: {
    type: Map,
    default: () => new Map()
  },
  selectedItem: {
    type: Object,
    default: null
  }
})

const mapId = ref(`friends-timeline-map-${Date.now()}`)
const map = ref(null)
const markerGroups = ref(new Map()) // userId -> L.LayerGroup
const markerRefs = ref(new Map()) // itemId -> L.Marker
const userPathPointsByUser = ref(new Map()) // userId -> flattened path points
const highlightedTripPath = ref(null) // currently highlighted trip polyline
const highlightedTripStartMarker = ref(null)
const highlightedTripEndMarker = ref(null)
const highlightedTripId = ref(null) // Currently highlighted trip ID

function parseCoordinate(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function hasValidCoordinates(point) {
  if (!point) return false
  return parseCoordinate(point.latitude) !== null && parseCoordinate(point.longitude) !== null
}

function toLatLng(point) {
  return [parseCoordinate(point.latitude), parseCoordinate(point.longitude)]
}

function normalizePathSegments(pathSegments) {
  if (!Array.isArray(pathSegments)) return []

  return pathSegments
    .map((segment) => {
      if (!Array.isArray(segment)) return []
      return segment
        .filter(hasValidCoordinates)
        .map(toLatLng)
    })
    .filter((segment) => segment.length >= 2)
}

function reconstructTripCoordsFromPath(trip, userPathPoints) {
  const { points } = reconstructTripPathPoints(trip, userPathPoints)
  return points.map(toLatLng)
}

function getFallbackTripCoords(trip) {
  if (!trip) return null

  const startLat = parseCoordinate(trip.latitude)
  const startLon = parseCoordinate(trip.longitude)
  const endLat = parseCoordinate(trip.endLatitude)
  const endLon = parseCoordinate(trip.endLongitude)

  if ([startLat, startLon, endLat, endLon].some(coord => coord === null)) {
    return null
  }

  return [[startLat, startLon], [endLat, endLon]]
}

function clearHighlightedTrip() {
  if (highlightedTripPath.value && map.value) {
    map.value.removeLayer(highlightedTripPath.value)
  }
  if (highlightedTripStartMarker.value && map.value) {
    map.value.removeLayer(highlightedTripStartMarker.value)
  }
  if (highlightedTripEndMarker.value && map.value) {
    map.value.removeLayer(highlightedTripEndMarker.value)
  }
  highlightedTripPath.value = null
  highlightedTripStartMarker.value = null
  highlightedTripEndMarker.value = null
  highlightedTripId.value = null
}

const visibleTimelines = computed(() => {
  if (!props.multiUserTimeline || !props.multiUserTimeline.timelines) {
    return []
  }
  return props.multiUserTimeline.timelines.filter(t =>
      props.selectedUserIds.has(t.userId)
  )
})

const mapCenter = computed(() => {
  if (visibleTimelines.value.length > 0) {
    const firstTimeline = visibleTimelines.value[0].timeline
    if (firstTimeline && firstTimeline.stays && firstTimeline.stays.length > 0) {
      const firstStay = firstTimeline.stays[0]
      if (hasValidCoordinates(firstStay)) {
        return toLatLng(firstStay)
      }
    }
  }
  return [51.505, -0.09] // Default London
})

function handleMapReady(mapInstance) {
  map.value = mapInstance
  renderAllMarkers()
}

watch([() => props.selectedUserIds, () => props.multiUserTimeline], () => {
  if (map.value) {
    renderAllMarkers()
  }
}, { deep: true })

watch(() => props.selectedItem, (newItem) => {
  if (!map.value) return

  if (newItem) {
    handleItemSelection(newItem)
  } else {
    clearHighlightedTrip()
  }
}, { deep: true })

function renderAllMarkers() {
  if (!map.value) return

  clearHighlightedTrip()

  // Clear existing markers and paths
  markerGroups.value.forEach(group => {
    map.value.removeLayer(group)
  })
  markerGroups.value.clear()
  markerRefs.value.clear()
  userPathPointsByUser.value.clear()

  const allBounds = []

  // Render markers for each visible user
  visibleTimelines.value.forEach(userTimeline => {
    const userId = userTimeline.userId
    const color = userTimeline.assignedColor
    const layerGroup = L.layerGroup()
    const pathSegments = normalizePathSegments(userTimeline.pathSegments)

    const timeline = userTimeline.timeline

    // Add baseline path segments (similar to Timeline map PathLayer)
    if (pathSegments.length > 0) {
      pathSegments.forEach((segmentCoords) => {
        const pathLine = L.polyline(segmentCoords, {
          color,
          weight: 4,
          opacity: 0.75,
          smoothFactor: 1
        })
        layerGroup.addLayer(pathLine)
        segmentCoords.forEach(coord => allBounds.push(coord))
      })
    }

    // Add stay markers
    if (timeline.stays) {
      timeline.stays.forEach(stay => {
        if (!hasValidCoordinates(stay)) {
          return
        }

        const marker = L.marker(toLatLng(stay), {
          icon: L.divIcon({
            className: 'custom-marker',
            html: `<div style="background-color: ${color}; width: 24px; height: 24px; border-radius: 50%; border: 2px solid white; box-shadow: 0 2px 4px rgba(0,0,0,0.3);"></div>`,
            iconSize: [24, 24],
            iconAnchor: [12, 12]
          })
        })

        marker.bindPopup(`
          <div style="font-family: sans-serif;">
            <div style="font-weight: 600; color: ${color}; margin-bottom: 4px;">${userTimeline.fullName}</div>
            <div style="font-weight: 500; margin-bottom: 2px;">${stay.locationName || 'Stay'}</div>
            <div style="font-size: 0.875rem; color: #666;">${formatDuration(stay.stayDuration)}</div>
          </div>
        `)

        // Store marker reference
        const stayId = `${userId}-stay-${stay.timestamp}`
        markerRefs.value.set(stayId, marker)

        layerGroup.addLayer(marker)
        allBounds.push(toLatLng(stay))
      })
    }

    const userPathPoints = normalizePathPoints(userTimeline.pathSegments)
    userPathPointsByUser.value.set(userId, userPathPoints)

    layerGroup.addTo(map.value)
    markerGroups.value.set(userId, layerGroup)
  })

  // Fit bounds to show all markers
  if (allBounds.length > 0) {
    nextTick(() => {
      map.value.fitBounds(allBounds, { padding: [50, 50] })
    })
  }
}

function handleItemSelection(item) {
  if (!map.value || !item) return

  const userId = item.userId
  const itemType = item.type

  if (itemType === 'stay') {
    // Find and open the stay marker
    const stayId = `${userId}-stay-${item.timestamp}`
    const marker = markerRefs.value.get(stayId)

    if (marker) {
      map.value.setView(toLatLng(item), 15, { animate: true })
      nextTick(() => {
        marker.openPopup()
      })
    }
  } else if (itemType === 'trip') {
    const tripId = `${userId}-trip-${item.timestamp}`

    // Toggle off if the same trip is clicked again
    if (highlightedTripId.value === tripId) {
      clearHighlightedTrip()
      return
    }

    clearHighlightedTrip()

    const userPathPoints = userPathPointsByUser.value.get(userId) || []
    let tripCoords = reconstructTripCoordsFromPath(item, userPathPoints)

    if (!tripCoords || tripCoords.length < 2) {
      tripCoords = getFallbackTripCoords(item)
    }

    if (!tripCoords || tripCoords.length < 2) {
      return
    }

    const startPoint = resolveTripMarkerPoint(item, 'start', {
      latitude: tripCoords[0][0],
      longitude: tripCoords[0][1]
    })
    const endPoint = resolveTripMarkerPoint(item, 'end', {
      latitude: tripCoords[tripCoords.length - 1][0],
      longitude: tripCoords[tripCoords.length - 1][1]
    })

    if (startPoint && endPoint && tripCoords.length >= 2) {
      // Keep highlighted polyline endpoints aligned with start/end markers.
      tripCoords[0] = [startPoint.latitude, startPoint.longitude]
      tripCoords[tripCoords.length - 1] = [endPoint.latitude, endPoint.longitude]
    }

    const tripPath = L.polyline(tripCoords, {
      color: '#ff6b6b',
      weight: 6,
      opacity: 1,
      dashArray: '10, 5'
    })

    tripPath.bindPopup(`
      <div style="font-family: sans-serif;">
        <div style="font-weight: 600; margin-bottom: 4px;">${item.userFullName || 'Trip'}</div>
        <div style="font-weight: 500; margin-bottom: 2px;">${item.movementType || 'Trip'}</div>
        <div style="font-size: 0.875rem; color: #666;">
          ${formatDuration(item.tripDuration)} • ${formatDistance(item.distanceMeters)}
        </div>
      </div>
    `)

    tripPath.addTo(map.value)
    tripPath.bringToFront()
    highlightedTripPath.value = tripPath
    highlightedTripId.value = tripId

    if (startPoint && endPoint) {
      const sameEndpoint = areSameCoordinate(startPoint, endPoint)

      const startMarker = createHighlightedPathStartMarker(
        startPoint.latitude,
        startPoint.longitude,
        true,
        sameEndpoint ? { transform: 'translateX(-14px)' } : {}
      )
      const endMarker = createHighlightedPathEndMarker(
        endPoint.latitude,
        endPoint.longitude,
        true,
        sameEndpoint ? { transform: 'translateX(14px)' } : {}
      )

      startMarker.bindPopup('<div style="font-family: sans-serif; font-weight: 600;">Trip Start</div>')
      endMarker.bindPopup('<div style="font-family: sans-serif; font-weight: 600;">Trip End</div>')

      if (sameEndpoint) {
        startMarker.setZIndexOffset(20)
        endMarker.setZIndexOffset(10)
      } else {
        startMarker.setZIndexOffset(10)
        endMarker.setZIndexOffset(10)
      }

      startMarker.addTo(map.value)
      endMarker.addTo(map.value)
      highlightedTripStartMarker.value = startMarker
      highlightedTripEndMarker.value = endMarker
    }

    const highlightBounds = L.latLngBounds(tripCoords)
    if (startPoint) {
      highlightBounds.extend([startPoint.latitude, startPoint.longitude])
    }
    if (endPoint) {
      highlightBounds.extend([endPoint.latitude, endPoint.longitude])
    }

    map.value.fitBounds(highlightBounds, { padding: [50, 50], animate: true })
    nextTick(() => {
      tripPath.openPopup()
    })
  }
}

onBeforeUnmount(() => {
  clearHighlightedTrip()
})

function formatDuration(seconds) {
  if (!seconds) return 'Unknown'
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  if (hours > 0) {
    return `${hours}h ${minutes}m`
  }
  return `${minutes}m`
}

function formatDistance(meters) {
  if (!meters) return 'Unknown'
  const km = meters / 1000
  if (km >= 1) {
    return `${km.toFixed(1)} km`
  }
  return `${meters.toFixed(0)} m`
}
</script>

<style scoped>
.friends-timeline-map {
  width: 100%;
  height: 100%;
  position: relative;
}
</style>
