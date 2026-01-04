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
import { ref, computed, watch, nextTick } from 'vue'
import L from 'leaflet'
import { MapContainer } from '@/components/maps'
import { createUserTimelineIcon } from '@/utils/mapHelpers'

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
const tripPaths = ref(new Map()) // tripId -> L.Polyline
const highlightedTripId = ref(null) // Currently highlighted trip ID

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
      return [firstStay.latitude, firstStay.longitude]
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
    // Clear all highlights when item is deselected
    if (highlightedTripId.value) {
      const tripPath = tripPaths.value.get(highlightedTripId.value)
      if (tripPath) {
        tripPath.setStyle({
          color: tripPath.options.originalColor,
          weight: tripPath.options.originalWeight,
          opacity: tripPath.options.originalOpacity
        })
      }
      highlightedTripId.value = null
    }
  }
}, { deep: true })

function renderAllMarkers() {
  if (!map.value) return

  // Clear any existing highlight
  highlightedTripId.value = null

  // Clear existing markers and paths
  markerGroups.value.forEach(group => {
    map.value.removeLayer(group)
  })
  markerGroups.value.clear()
  markerRefs.value.clear()

  // Clear trip paths
  tripPaths.value.forEach(path => {
    map.value.removeLayer(path)
  })
  tripPaths.value.clear()

  const allBounds = []

  // Render markers for each visible user
  visibleTimelines.value.forEach(userTimeline => {
    const userId = userTimeline.userId
    const color = userTimeline.assignedColor
    const layerGroup = L.layerGroup()

    const timeline = userTimeline.timeline

    // Add stay markers
    if (timeline.stays) {
      timeline.stays.forEach(stay => {
        const marker = L.marker([stay.latitude, stay.longitude], {
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
        allBounds.push([stay.latitude, stay.longitude])
      })
    }

    // Add trip paths
    if (timeline.trips) {
      timeline.trips.forEach(trip => {
        // Draw trip path if we have coordinates
        if (trip.latitude && trip.longitude && trip.endLatitude && trip.endLongitude) {
          const tripPath = L.polyline(
            [[trip.latitude, trip.longitude], [trip.endLatitude, trip.endLongitude]],
            {
              color: color,
              weight: 3,
              opacity: 0.6,
              smoothFactor: 1
            }
          )

          tripPath.bindPopup(`
            <div style="font-family: sans-serif;">
              <div style="font-weight: 600; color: ${color}; margin-bottom: 4px;">${userTimeline.fullName}</div>
              <div style="font-weight: 500; margin-bottom: 2px;">${trip.movementType || 'Trip'}</div>
              <div style="font-size: 0.875rem; color: #666;">
                ${formatDuration(trip.tripDuration)} • ${formatDistance(trip.distanceMeters)}
              </div>
            </div>
          `)

          const tripId = `${userId}-trip-${trip.timestamp}`
          tripPaths.value.set(tripId, tripPath)

          layerGroup.addLayer(tripPath)
          allBounds.push([trip.latitude, trip.longitude])
          allBounds.push([trip.endLatitude, trip.endLongitude])
        }
      })
    }

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
      map.value.setView([item.latitude, item.longitude], 15, { animate: true })
      nextTick(() => {
        marker.openPopup()
      })
    }
  } else if (itemType === 'trip') {
    // Highlight and zoom to trip path
    const tripId = `${userId}-trip-${item.timestamp}`
    const tripPath = tripPaths.value.get(tripId)

    if (tripPath) {
      // Check if clicking the already highlighted trip (toggle off)
      if (highlightedTripId.value === tripId) {
        // Un-highlight: reset to original style
        tripPath.setStyle({
          color: tripPath.options.originalColor,
          weight: tripPath.options.originalWeight,
          opacity: tripPath.options.originalOpacity
        })
        highlightedTripId.value = null
        return
      }

      // Reset previously highlighted trip (if different)
      if (highlightedTripId.value) {
        const prevTripPath = tripPaths.value.get(highlightedTripId.value)
        if (prevTripPath) {
          prevTripPath.setStyle({
            color: prevTripPath.options.originalColor,
            weight: prevTripPath.options.originalWeight,
            opacity: prevTripPath.options.originalOpacity
          })
        }
      }

      // Store original styles if not already stored
      if (!tripPath.options.originalColor) {
        tripPath.options.originalColor = tripPath.options.color
        tripPath.options.originalWeight = tripPath.options.weight
        tripPath.options.originalOpacity = tripPath.options.opacity
      }

      // Highlight the path with red color
      tripPath.setStyle({
        color: '#ef4444',  // Red highlight color
        weight: 6,
        opacity: 1
      })

      highlightedTripId.value = tripId

      // Zoom to trip bounds
      map.value.fitBounds(tripPath.getBounds(), { padding: [50, 50], animate: true })

      // Open popup
      nextTick(() => {
        tripPath.openPopup()
      })
    }
  }
}

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
