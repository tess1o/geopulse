<template>
  <div class="timeline-page">
    <Message v-if="matchingTripWorkspace" severity="info" :closable="false" class="trip-workspace-banner">
      <div class="trip-workspace-banner-content">
        <span>
          Current date range matches trip plan:
          <strong>{{ matchingTripWorkspace.name }}</strong>
        </span>
        <Button
          label="Open Trip Planner"
          icon="pi pi-briefcase"
          size="small"
          outlined
          @click="openMatchingTripWorkspace"
        />
      </div>
    </Message>

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
            @delete-favorite="handleFavoriteDelete"
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
            @rename-stay="handleRenameStay"
            @photo-show-on-map="handleTimelinePhotoShowOnMap"
        />
          </div>
        </div>

        <!-- Timeline Share Dialog -->
        <TimelineShareDialog
            v-model:visible="showShareDialog"
            :prefill-dates="shareDates"
            @created="handleShareCreated"
        />

        <!-- Edit Favorite Dialog -->
        <EditFavoriteDialog
            v-if="selectedFavorite"
            :visible="showFavoriteDialog"
            :header="'Edit Favorite Location'"
            :favorite-location="selectedFavorite"
            @edit-favorite="handleFavoriteDialogSave"
            @close="closeFavoriteEditor"
        />

        <GeocodingEditDialog
            :visible="showGeocodingEditDialog"
            :geocoding-result="editGeocodingData"
            @save="handleSaveGeocoding"
            @close="closeGeocodingDialog"
        />

        <!-- Timeline Regeneration Modal -->
        <TimelineRegenerationModal
            v-model:visible="timelineRegenerationVisible"
            :type="timelineRegenerationType"
            :job-id="currentJobId"
            :job-progress="jobProgress"
        />
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, onMounted, computed, inject } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { TimelineContainer } from '@/components/timeline'
import TimelineMap from '@/components/maps/TimelineMap.vue'
import TimelineLargeDatasetWarning from '@/components/timeline/TimelineLargeDatasetWarning.vue'
import ProgressSpinner from 'primevue/progressspinner'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { useTimezone } from '@/composables/useTimezone'
import apiService from '@/utils/apiService'
import TimelineShareDialog from '@/components/sharing/TimelineShareDialog.vue'
import EditFavoriteDialog from '@/components/dialogs/EditFavoriteDialog.vue'
import GeocodingEditDialog from '@/components/dialogs/GeocodingEditDialog.vue'
import TimelineRegenerationModal from '@/components/dialogs/TimelineRegenerationModal.vue'
import { useFavoriteEditor } from '@/composables/useFavoriteEditor'

const timezone = useTimezone()
import { useDateRangeStore } from '@/stores/dateRange'
import { useFavoritesStore } from '@/stores/favorites'
import { useGeocodingStore } from '@/stores/geocoding'
import { useLocationStore } from '@/stores/location'
import { useTimelineStore } from '@/stores/timeline'
import { useHighlightStore } from '@/stores/highlight'
import { useTripsStore } from '@/stores/trips'

const toast = useToast()
const router = useRouter()

const dateRangeStore = useDateRangeStore()
const favoritesStore = useFavoritesStore()
const geocodingStore = useGeocodingStore()
const locationStore = useLocationStore()
const timelineStore = useTimelineStore()
const highlightStore = useHighlightStore()
const tripsStore = useTripsStore()
const confirm = useConfirm()

// Favorite editor composable
const {
  showDialog: showFavoriteDialog,
  selectedFavorite,
  openEditor: openFavoriteEditor,
  closeEditor: closeFavoriteEditor,
  handleSave: handleFavoriteSave,
  withTimelineRegeneration,
  timelineRegenerationVisible,
  timelineRegenerationType,
  currentJobId,
  jobProgress
} = useFavoriteEditor()

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
const isFetching = ref(false) // Flag to prevent concurrent fetches
const pendingFetchKey = ref(null) // Track the currently pending fetch
const queuedFetchRange = ref(null) // Keep latest requested range while a fetch is running
const showGeocodingEditDialog = ref(false)
const editGeocodingData = ref(null)

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

const handleTimelineMarkerClick = (itemOrEvent) => {
  const item = itemOrEvent?.timelineItem || itemOrEvent
  if (!item) return
  handleTimelineItemClick(item)
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

const handleEditFavorite = (favorite) => {
  // Open the edit dialog with full favorite data
  openFavoriteEditor(favorite)
}

const getFavoriteById = (favoriteId) => {
  const points = favoritePlaces.value?.points || []
  const areas = favoritePlaces.value?.areas || []
  return [...points, ...areas].find((favorite) => favorite.id === favoriteId) || null
}

const handleFavoriteDialogSave = async (data) => {
  await handleFavoriteSave(data, {
    onSuccess: () => {
      favoritesStore.fetchFavoritePlaces()
      timelineStore.applyStayFavoriteUpdate(data)
    }
  })
}

const closeGeocodingDialog = () => {
  showGeocodingEditDialog.value = false
  editGeocodingData.value = null
}

const handleSaveGeocoding = async (updatedData) => {
  if (!editGeocodingData.value?.id) return

  const oldGeocodingId = editGeocodingData.value.id
  try {
    const updated = await geocodingStore.updateGeocodingResult(oldGeocodingId, updatedData)
    timelineStore.applyStayGeocodingUpdate(oldGeocodingId, updated)

    toast.add({
      severity: 'success',
      summary: 'Updated',
      detail: 'Stay location name updated successfully.',
      life: 3000
    })

    closeGeocodingDialog()
  } catch (error) {
    console.error('Failed to update geocoding result from timeline:', error)
    const errorMessage = error.response?.data?.message || error.message || 'Failed to update stay location'
    toast.add({
      severity: 'error',
      summary: 'Update Failed',
      detail: errorMessage,
      life: 5000
    })
  }
}

const openFavoriteRenameDialog = async (stayItem) => {
  let favorite = getFavoriteById(stayItem.favoriteId)

  if (!favorite) {
    await favoritesStore.fetchFavoritePlaces()
    favorite = getFavoriteById(stayItem.favoriteId)
  }

  if (!favorite) {
    toast.add({
      severity: 'error',
      summary: 'Favorite Not Found',
      detail: 'Could not load favorite details for this stay.',
      life: 4000
    })
    return
  }

  openFavoriteEditor({ ...favorite })
}

const openGeocodingRenameDialog = async (stayItem) => {
  try {
    const geocoding = await geocodingStore.getGeocodingResult(stayItem.geocodingId)
    editGeocodingData.value = {
      id: geocoding?.id ?? stayItem.geocodingId,
      displayName: geocoding?.displayName ?? stayItem.locationName ?? '',
      city: geocoding?.city ?? stayItem.city ?? '',
      country: geocoding?.country ?? stayItem.country ?? '',
      latitude: geocoding?.latitude ?? stayItem.latitude,
      longitude: geocoding?.longitude ?? stayItem.longitude,
      providerName: geocoding?.providerName || 'Unknown'
    }
    showGeocodingEditDialog.value = true
  } catch (error) {
    console.error('Failed to load geocoding details for stay rename:', error)
    const errorMessage = error.response?.data?.message || error.message || 'Could not load geocoding details'
    toast.add({
      severity: 'error',
      summary: 'Unable to Rename',
      detail: errorMessage,
      life: 5000
    })
  }
}

const handleRenameStay = (stayItem) => {
  if (!stayItem?.favoriteId && !stayItem?.geocodingId) {
    return
  }

  const locationName = stayItem.locationName || 'this location'

  confirm.require({
    header: 'Rename Stay Location',
    message: `Renaming "${locationName}" will update all stays with this name. Continue?`,
    icon: 'pi pi-exclamation-triangle',
    accept: () => {
      if (stayItem.favoriteId) {
        openFavoriteRenameDialog(stayItem)
        return
      }

      openGeocodingRenameDialog(stayItem)
    }
  })
}

const handleFavoriteDelete = (favorite) => {
  confirm.require({
    message: 'Are you sure you want to delete this favorite location? This will also regenerate your timeline data.',
    header: 'Delete Favorite',
    icon: 'pi pi-exclamation-triangle',
    accept: () => {
      const action = () => favoritesStore.deleteFavorite(favorite.id)

      withTimelineRegeneration(action, {
        modalType: 'favorite-delete',
        successMessage: `Favorite "${favorite.name}" deleted successfully. Timeline is regenerating.`,
        errorMessage: 'Failed to delete favorite location.',
        onSuccess: () => {
          favoritesStore.fetchFavoritePlaces()
        }
      })
    }
  })
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

const openMatchingTripWorkspace = () => {
  if (!matchingTripWorkspace.value?.id || !dateRange.value || dateRange.value.length !== 2) {
    return
  }

  const [startDate, endDate] = dateRange.value
  router.push({
    path: `/app/trips/${matchingTripWorkspace.value.id}`,
    query: {
      start: timezone.formatUrlDate(startDate),
      end: timezone.formatUrlDate(endDate)
    }
  })
}

const handleTimelinePhotoShowOnMap = (photo) => {
  mapViewRef.value?.focusOnPhoto?.(photo)
}

const queueLatestFetchRange = (startDate, endDate, rangeKey) => {
  queuedFetchRange.value = { startDate, endDate, rangeKey }
  console.info('Fetch in progress, queued latest range:', rangeKey)
}

const executeFetchForRange = async (startDate, endDate, rangeKey) => {
  if (isFetching.value) {
    queueLatestFetchRange(startDate, endDate, rangeKey)
    return
  }

  // Mark as fetching and set the range before starting async operation
  isFetching.value = true
  pendingFetchKey.value = rangeKey
  lastFetchedRange.value = rangeKey

  try {
    // Clear stale map/timeline highlights immediately on date-range change.
    // Otherwise the previously selected trip path can remain visible while the
    // new range loads, which is confusing UX.
    lastHighlightedPath.value = null
    highlightStore.clearAllHighlights()

    const shouldProceed = await checkDatasetSize(startDate, endDate)

    if (!shouldProceed) {
      mapDataLoading.value = false
      timelineDataLoading.value = false
      return
    }

    forceLoadLargeDataset.value = false

    await Promise.all([
      fetchLocationData(startDate, endDate),
      fetchTimelineData(startDate, endDate),
    ])
  } finally {
    isFetching.value = false
    pendingFetchKey.value = null

    // Always run the latest queued range (if any) after the current fetch
    // completes so route/date changes are never lost.
    const queued = queuedFetchRange.value
    queuedFetchRange.value = null
    if (queued && queued.rangeKey !== rangeKey) {
      await executeFetchForRange(queued.startDate, queued.endDate, queued.rangeKey)
    }
  }
}

// Lifecycle
onMounted(async () => {
  await favoritesStore.fetchFavoritePlaces()
  tripsStore.fetchTrips().catch(() => {
    // Best-effort fetch for trip plan quick navigation banner
  })
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
const matchingTripWorkspace = computed(() => {
  if (!dateRange.value || !Array.isArray(dateRange.value) || dateRange.value.length !== 2) {
    return null
  }

  const [rangeStart, rangeEnd] = dateRange.value
  if (!rangeStart || !rangeEnd) {
    return null
  }

  const selectedStart = timezone.formatUrlDate(rangeStart)
  const selectedEnd = timezone.formatUrlDate(rangeEnd)
  const trips = Array.isArray(tripsStore.trips) ? tripsStore.trips : []

  return trips.find((trip) => {
    if (!trip?.startTime || !trip?.endTime) return false
    return timezone.formatUrlDate(trip.startTime) === selectedStart &&
      timezone.formatUrlDate(trip.endTime) === selectedEnd
  }) || null
})

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
  if (!newValue || !timezone.isValidDataRange(newValue)) return

  const [startDate, endDate] = newValue

  // Normalize dates to ensure consistent comparison
  const normalizedStart = new Date(startDate).toISOString()
  const normalizedEnd = new Date(endDate).toISOString()
  const rangeKey = `${normalizedStart}-${normalizedEnd}`

  // Skip if we've already fetched this range
  if (lastFetchedRange.value === rangeKey && !forceLoadLargeDataset.value) {
    console.log('Skipping fetch - range already fetched:', rangeKey)
    return
  }

  // Skip if the same fetch is already in progress
  if (isFetching.value && pendingFetchKey.value === rangeKey) {
    console.warn('Fetch already in progress for range:', rangeKey)
    return
  }

  await executeFetchForRange(startDate, endDate, rangeKey)
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

.trip-workspace-banner {
  margin: 0.5rem 0.5rem 0 0.5rem;
}

.trip-workspace-banner-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gp-spacing-sm);
  flex-wrap: wrap;
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
    min-height: 0;
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
