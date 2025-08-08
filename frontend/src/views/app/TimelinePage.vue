<template>
  <div class="timeline-page">
    <div class="left-pane">
      <div v-if="mapNoData" class="loading-messages">
        No data to show on the map. Try to select different date range.
      </div>
      <div v-if="mapDataLoading" class="loading-messages">
        <ProgressSpinner />
      </div>
      <TimelineMap
          v-show="!mapNoData && !mapDataLoading"
          ref="mapViewRef"
          :pathData="pathData"
          :timelineData="timelineData"
          :favoritePlaces="favoritePlaces"
          :friends="friends"
          :currentLocation="currentLocation"
          :showCurrentLocation="isToday"
          @timeline-marker-click="handleTimelineMarkerClick"
          @highlighted-path-click="handleHighlightedPathClick"
          @add-point="handleAddPointSubmit"
          @add-area="handleAddAreaSubmit"
          @edit-favorite="handleEditFavorite"
          @delete-favorite="handleDeleteFavorite"
      />
    </div>

    <div class="right-pane">
      <TimelineContainer
          ref="timelineRef"
          :timelineData="timelineData"
          :timelineNoData="timelineNoData"
          :timelineDataLoading="timelineDataLoading"
          :dateRange="dateRange"
          @timeline-item-click="handleTimelineItemClick"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, onMounted, computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import { TimelineContainer } from '@/components/timeline'
import TimelineMap from '@/components/maps/TimelineMap.vue'
import { isValidDataRange } from '@/utils/dateHelpers'
import { useDateRangeStore } from '@/stores/dateRange'
import { useFavoritesStore } from '@/stores/favorites'
import { useLocationStore } from '@/stores/location'
import { useTimelineStore } from '@/stores/timeline'
import { useHighlightStore } from '@/stores/highlight'
import {useFriendsStore} from "@/stores/friends";

const toast = useToast()

const dateRangeStore = useDateRangeStore()
const favoritesStore = useFavoritesStore()
const locationStore = useLocationStore()
const timelineStore = useTimelineStore()
const highlightStore = useHighlightStore()
const friendsStore = useFriendsStore()

const { dateRange } = storeToRefs(dateRangeStore)
const { favoritePlaces } = storeToRefs(favoritesStore)
const { locationPath: pathData } = storeToRefs(locationStore)
const { timelineData } = storeToRefs(timelineStore)
const { friends } = storeToRefs(friendsStore)

// Template refs
const mapViewRef = ref(null)
const timelineRef = ref(null)

// Reactive state
const mapDataLoading = ref(false)
const mapNoData = ref(false)
const timelineNoData = ref(false)
const timelineDataLoading = ref(true)
const lastHighlightedPath = ref(null)
const lastFetchedRange = ref(null)
const currentLocation = ref(null)
const geolocationError = ref(null)

// Methods
const triggerMapResize = () => {
  nextTick(() => {
    setTimeout(() => {
      mapViewRef.value?.invalidateMapSize?.()
    }, 250)
  })
}

const handleTimelineMarkerClick = (item) => {
  const index = timelineStore.findTimelineItemIndex(
      item.timestamp,
      item.latitude,
      item.longitude
  )

  if (index !== -1) {
    handleTimelineItemClick(item)
  }
}

const handleHighlightedPathClick = (data) => {
  handleTimelineItemClick(data)
}

const handleTimelineItemClick = (item) => {
  // Check if this item is already highlighted
  if (highlightStore.isItemHighlighted(item)) {
    lastHighlightedPath.value = null
    highlightStore.clearAllHighlights()
    return
  }

  lastHighlightedPath.value = item
  highlightStore.setHighlightedItem(item)
}

const handleAddPointSubmit = async (point) => {
  try {
    console.log(point);
    await favoritesStore.addPointToFavorites(
        point.name,
        point.lat,
        point.lon
    )

    toast.add({
      severity: 'success',
      summary: 'Point added',
      detail: 'The point was added to your favorites',
      life: 3000
    })
  } catch (error) {
    console.error('Error adding point to favorites:', error)
    toast.add({
      severity: 'error',
      summary: 'Failed to add point',
      detail: 'Could not add the point to your favorites',
      life: 3000
    })
  }
}

const handleAddAreaSubmit = async (area) => {
  try {
    await favoritesStore.addAreaToFavorites(
        area.name,
        area.northEastLat,
        area.northEastLon,
        area.southWestLat,
        area.southWestLon
    )

    toast.add({
      severity: 'success',
      summary: 'Area added',
      detail: 'The area was added to your favorites',
      life: 3000
    })
  } catch (error) {
    console.error('Error adding area to favorites:', error)
    toast.add({
      severity: 'error',
      summary: 'Failed to add area',
      detail: 'Could not add the area to your favorites',
      life: 3000
    })
  }
}

const handleEditFavorite = async (favorite) => {
  try {
    await favoritesStore.editFavorite(favorite.id, favorite.name)

    toast.add({
      severity: 'success',
      summary: 'Favorite renamed',
      detail: 'The favorite location was renamed',
      life: 3000
    })
  } catch (error) {
    console.error('Error editing favorite:', error)
    toast.add({
      severity: 'error',
      summary: 'Failed to rename favorite',
      detail: 'Could not rename the favorite location',
      life: 3000
    })
  }
}

const handleDeleteFavorite = async (favorite) => {
  try {
    await favoritesStore.deleteFavorite(favorite.id)

    toast.add({
      severity: 'success',
      summary: 'Favorite deleted',
      detail: 'The favorite location was deleted',
      life: 3000
    })
  } catch (error) {
    console.error('Error deleting favorite:', error)
    toast.add({
      severity: 'error',
      summary: 'Failed to delete favorite',
      detail: 'Could not delete the favorite location',
      life: 3000
    })
  }
}

const fetchLocationData = async (startDate, endDate) => {
  mapDataLoading.value = true
  mapNoData.value = false

  try {
    await locationStore.fetchLocationPath(startDate, endDate)


    if (!pathData.value || !pathData.value.points || pathData.value.points.length === 0) {
      toast.add({
        severity: 'info',
        detail: 'No location data for given date range',
        life: 3000
      })
      mapNoData.value = true
    }
  } catch (error) {
    console.error('Error fetching location data:', error)
    mapNoData.value = true
    const errorMessage = error.response?.data?.message || error.message || error.toString()
    toast.add({
      severity: 'error',
      summary: 'Failed to fetch location data',
      detail: errorMessage,
      life: 3000
    })
  } finally {
    mapDataLoading.value = false
  }
}

const fetchTimelineData = async (startDate, endDate) => {
  timelineDataLoading.value = true
  timelineNoData.value = false

  try {
    await timelineStore.fetchMovementTimeline(startDate, endDate)

    if (timelineData.value == null || timelineData.value.length === 0) {
      console.log('No timeline data')
      toast.add({
        severity: 'info',
        detail: 'No timeline data for given date range',
        life: 3000
      })
      timelineNoData.value = true
    }
  } catch (error) {
    console.error('Error fetching timeline data:', error)
    const errorMessage = error.response?.data?.message || error.message || error.toString()
    toast.add({
      severity: 'error',
      summary: 'Failed to fetch timeline',
      detail: errorMessage,
      life: 3000
    })
    timelineNoData.value = true
  } finally {
    timelineDataLoading.value = false
  }
}

const getCurrentLocation = () => {
  if (!pathData.value || !pathData.value.points || pathData.value.points.length === 0) {
    currentLocation.value = null
    geolocationError.value = 'No location data available'
    return
  }

  // Get the latest location from pathData (last point in the array)
  const latestPoint = pathData.value.points[pathData.value.points.length - 1]
  
  if (latestPoint && latestPoint.latitude && latestPoint.longitude) {
    currentLocation.value = {
      latitude: latestPoint.latitude,
      longitude: latestPoint.longitude,
      timestamp: latestPoint.timestamp
    }
    geolocationError.value = null
  } else {
    currentLocation.value = null
    geolocationError.value = 'Invalid location data'
  }
}

// Lifecycle
onMounted(async () => {
  await Promise.all([
    favoritesStore.fetchFavoritePlaces(),
    friendsStore.fetchFriends()
  ])
})

// Watchers
watch([mapDataLoading, mapNoData], ([newLoading, newNoData], [oldLoading, oldNoData]) => {
  if (!newLoading && !newNoData) {
    // Check if map was previously hidden and is now being shown
    const wasHidden = oldNoData || oldLoading
    const isNowVisible = !newNoData && !newLoading

    if (wasHidden && isNowVisible) {
      // Map is now visible after being hidden - force reinitialization
      nextTick(() => {
        setTimeout(() => {
          // Force map container to reinitialize by invalidating size
          if (mapViewRef.value && mapViewRef.value.map) {
            mapViewRef.value.map.invalidateSize()
          }
        }, 300)
      })
    } else {
      triggerMapResize()
    }
  }
})

// Computed properties
const isToday = computed(() => {
  if (!dateRange.value || !Array.isArray(dateRange.value) || dateRange.value.length !== 2) {
    return false
  }
  
  const [startDate, endDate] = dateRange.value
  if (!startDate || !endDate) return false
  
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  
  const startDateOnly = new Date(startDate)
  startDateOnly.setHours(0, 0, 0, 0)
  
  const endDateOnly = new Date(endDate)
  endDateOnly.setHours(0, 0, 0, 0)
  
  return startDateOnly.getTime() === today.getTime() && 
         endDateOnly.getTime() === today.getTime()
})

watch(dateRange, async (newValue) => {
  if (newValue && isValidDataRange(newValue)) {
    const [startDate, endDate] = newValue

    // Create a range key to detect if we've already fetched this exact range
    const rangeKey = `${startDate.getTime()}-${endDate.getTime()}`

    // Skip if we've already fetched this exact range
    if (lastFetchedRange.value === rangeKey) {
      return
    }

    lastFetchedRange.value = rangeKey

    await Promise.all([
      fetchLocationData(startDate, endDate),
      fetchTimelineData(startDate, endDate),
    ])
  }
}, { immediate: true })

// Watch for today's date and get current location
watch(isToday, (newValue) => {
  if (newValue) {
    getCurrentLocation()
  }
}, { immediate: true })

// Watch for pathData changes to update current location
watch(pathData, () => {
  if (isToday.value) {
    getCurrentLocation()
  }
}, { deep: true })
</script>

<style scoped>
.timeline-page {
  flex: 1;
  display: flex;
  height: calc(100vh - 160px); /* Account for navbar (60px) + tabs (40px) + padding (60px) */
  overflow: hidden;
}

.left-pane {
  flex: 5;
  display: flex;
  margin-top: 1rem;
  margin-left: 0.5rem;
  margin-right: 1rem;
  height: 100%;
  max-height: 70vh; /* Reduced map height to prevent page scrolling */
  min-height: 350px;
  flex-direction: column;
}

.right-pane {
  flex: 1;
  overflow-y: auto !important;
  height: 100%;
  min-height: 350px;
  border-radius: var(--gp-radius-medium);
}

.loading-messages {
  color: var(--gp-text-secondary);
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  font-size: 0.875rem;
  font-weight: 500;
  padding: var(--gp-spacing-lg);
  margin-top: 1rem;
  display: flex;
  justify-content: center;
  align-items: center;
  text-align: center;
}

/* Dark mode for loading messages */
.p-dark .loading-messages {
  color: var(--gp-text-primary);
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

/* Responsive design */
@media (max-width: 768px) {
  .timeline-page {
    flex-direction: column;
    height: calc(100vh - 140px); /* Adjust for mobile navbar height */
    gap: 0.5rem;
  }

  .left-pane {
    flex: 1;
    width: 100%;
    margin-right: 0;
    margin-top: 0.5rem;
    margin-bottom: 0;
  }

  .right-pane {
    flex: 1;
    width: 100%;
    margin-top: 0;
    overflow-y: auto !important;
  }
}

@media (min-width: 768px) and (max-width: 1024px) {
  .timeline-page {
    height: calc(100vh - 150px);
  }

  .left-pane {
    flex: 6;
    max-height: 65vh;
  }

  .right-pane {
    flex: 3;
  }
}

@media (min-width: 1024px) and (max-width: 1280px) {
  .left-pane {
    max-height: 68vh;
  }
}

@media (min-width: 1280px) and (max-width: 1599px) {
  .left-pane {
    flex: 5;
    max-height: 70vh;
  }

  .right-pane {
    flex: 2;
  }
}

@media (min-width: 1600px) {
  .left-pane {
    flex: 4;
    max-height: 75vh;
  }

  .right-pane {
    flex: 1;
  }
}
</style>

<style>
/* Override padding on the timeline container */
.p-timeline-left .p-timeline-event-opposite {
  display: none !important; /* optional: remove opposite content space */
}

.p-timeline-left .p-timeline-event {
  margin-left: 0 !important; /* remove extra margin */
}

/* Adjust the content container */
.p-timeline-left .p-timeline-event-content {
  padding-left: 0.5rem !important; /* or 0 if you want no space */
}
</style>