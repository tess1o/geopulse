<template>
  <div class="timeline-page">
    <!-- Large Dataset Warning -->
    <TimelineLargeDatasetWarning
      v-if="showLargeDatasetWarning"
      :totalItems="datasetCounts.totalItems"
      :stays="datasetCounts.stays"
      :trips="datasetCounts.trips"
      :dataGaps="datasetCounts.dataGaps"
      @force-load="handleForceLoad"
    />

    <!-- Normal Timeline View -->
    <template v-else>
      <div class="timeline-content-wrapper">
        <div class="timeline-main">
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
            :currentLocation="currentLocation"
            :showCurrentLocation="isToday"
            @timeline-marker-click="handleTimelineMarkerClick"
            @highlighted-path-click="handleHighlightedPathClick"
            @edit-favorite="handleEditFavorite"
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
            @tag-clicked="handleTagClicked"
        />
          </div>
        </div>

        <!-- Timeline Share Dialog -->
        <TimelineShareDialog
            v-model:visible="showShareDialog"
            :prefill-dates="shareDates"
            @created="handleShareCreated"
        />
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, onMounted, computed, inject } from 'vue'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import { TimelineContainer } from '@/components/timeline'
import TimelineMap from '@/components/maps/TimelineMap.vue'
import TimelineLargeDatasetWarning from '@/components/timeline/TimelineLargeDatasetWarning.vue'
import ProgressSpinner from 'primevue/progressspinner'
import { useTimezone } from '@/composables/useTimezone'
import apiService from '@/utils/apiService'
import TimelineShareDialog from '@/components/sharing/TimelineShareDialog.vue'

const timezone = useTimezone()
import { useDateRangeStore } from '@/stores/dateRange'
import { useFavoritesStore } from '@/stores/favorites'
import { useLocationStore } from '@/stores/location'
import { useTimelineStore } from '@/stores/timeline'
import { useHighlightStore } from '@/stores/highlight'

const toast = useToast()

const dateRangeStore = useDateRangeStore()
const favoritesStore = useFavoritesStore()
const locationStore = useLocationStore()
const timelineStore = useTimelineStore()
const highlightStore = useHighlightStore()

const { dateRange } = storeToRefs(dateRangeStore)
const { favoritePlaces } = storeToRefs(favoritesStore)
const { locationPath: pathData } = storeToRefs(locationStore)
const { timelineData } = storeToRefs(timelineStore)

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

// Large dataset warning state
const showLargeDatasetWarning = ref(false)
const datasetCounts = ref({ totalItems: 0, stays: 0, trips: 0, dataGaps: 0, limit: 150 })
const forceLoadLargeDataset = ref(false)

// Share dialog state - injected from MainAppPage
const showShareDialog = inject('shareDialogVisible', ref(false))
const shareDates = computed(() => ({
  start: dateRangeStore.startDate,
  end: dateRangeStore.endDate
}))

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

const checkDatasetSize = async (startDate, endDate) => {
  try {
    const response = await apiService.get('/streaming-timeline/count', {
      startTime: startDate,
      endTime: endDate
    })

    const counts = response.data
    datasetCounts.value = {
      totalItems: counts.totalItems || 0,
      stays: counts.stays || 0,
      trips: counts.trips || 0,
      dataGaps: counts.dataGaps || 0,
      limit: counts.limit || 150
    }

    // Check if dataset exceeds limit
    if (datasetCounts.value.totalItems > datasetCounts.value.limit && !forceLoadLargeDataset.value) {
      showLargeDatasetWarning.value = true
      return false // Don't proceed with loading
    }

    showLargeDatasetWarning.value = false
    return true // Proceed with loading
  } catch (error) {
    console.error('Error checking dataset size:', error)
    // On error, proceed with loading (fail open)
    return true
  }
}

const fetchTimelineData = async (startDate, endDate) => {
  timelineDataLoading.value = true
  timelineNoData.value = false

  try {
    await timelineStore.fetchMovementTimeline(startDate, endDate)

    if (timelineData.value == null || timelineData.value.length === 0) {
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

const handleForceLoad = () => {
  forceLoadLargeDataset.value = true
  showLargeDatasetWarning.value = false
  // Trigger the watcher to run again with force load flag set
  const currentRange = dateRange.value
  if (currentRange && currentRange.length === 2) {
    lastFetchedRange.value = null // Reset to allow refetch
    dateRangeStore.setDateRange([...currentRange]) // Trigger watcher
  }
}

const handleShareCreated = (share) => {
  // Dialog will stay open to show the success state with copy link
  // No need to show toast as the dialog already shows success message
}

const handleTagClicked = (tag) => {
  // Update date range to show full tag period
  const startDate = new Date(tag.startTime)
  const endDate = tag.endTime ? new Date(tag.endTime) : new Date()

  dateRangeStore.setDateRange([startDate, endDate])

  toast.add({
    severity: 'info',
    summary: `Viewing ${tag.tagName}`,
    detail: `Timeline updated to show ${tag.tagName} period`,
    life: 3000
  })
}

// Lifecycle
onMounted(async () => {
  await Promise.all([
    favoritesStore.fetchFavoritePlaces(),
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
  
  const today = timezone.now().startOf('day');
  
  const start = timezone.fromUtc(startDate).startOf('day');
  const end = timezone.fromUtc(endDate).startOf('day');
  
  return start.isSame(today) && end.isSame(today);
})

watch(dateRange, async (newValue) => {
  if (newValue && timezone.isValidDataRange(newValue)) {
    const [startDate, endDate] = newValue

    // Create a range key to detect if we've already fetched this exact range
    const rangeKey = `${startDate}-${endDate}`

    // Skip if we've already fetched this exact range
    if (lastFetchedRange.value === rangeKey && !forceLoadLargeDataset.value) {
      return
    }

    // Check dataset size before fetching
    const shouldProceed = await checkDatasetSize(startDate, endDate)

    if (!shouldProceed) {
      // Dataset is too large, warning is shown
      // Reset loading states since we're not proceeding
      mapDataLoading.value = false
      timelineDataLoading.value = false
      return
    }

    lastFetchedRange.value = rangeKey
    forceLoadLargeDataset.value = false // Reset force load flag

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
  flex-direction: column;
  height: calc(100vh - 160px); /* Account for navbar (60px) + tabs (40px) + padding (60px) */
  overflow: hidden;
}

/* Center the warning when shown */
.timeline-page:has(.large-dataset-warning) {
  justify-content: center;
  align-items: center;
}

.timeline-content-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.timeline-main {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.left-pane {
  flex: 5;
  display: flex;
  margin-top: 0.5rem;
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
  .timeline-main {
    flex-direction: column;
    gap: 0.5rem;
  }

  .timeline-page {
    height: calc(100vh - 140px); /* Adjust for mobile navbar height */
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
    flex: 3;
    max-height: 65vh;
  }

  .right-pane {
    flex: 2;
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