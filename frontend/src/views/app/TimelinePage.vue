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
      <TimelineSplitLayout
        ref="timelineSplitLayoutRef"
        :show-date-navigation="isSingleDaySelected"
        :date-label="selectedDateLabel"
        @navigate-date="navigateTimelineDay"
        @layout-resize="triggerMapResize"
      >
        <template #map>
          <div v-if="mapNoData" class="loading-messages">
            No data to show on the map. Try to select different date range.
          </div>
          <div v-if="mapDataLoading" class="loading-messages">
            <ProgressSpinner />
          </div>
          <TimelineMap
              v-if="mapPreferencesLoaded"
              v-show="!mapNoData && !mapDataLoading"
              ref="mapViewRef"
              :pathData="activePathData"
              :raw-path-data="pathData"
              :matched-trip-ids="matchedTripIds"
              :map-matching-status-text="mapMatchingStatusText"
              :timelineData="timelineDataWithStayTelemetry"
              :weather-samples="weatherSamples"
              :favoritePlaces="favoritePlaces"
              :currentLocation="currentLocation"
              :showCurrentLocation="timeframeIncludesToday"
              :custom-tile-url="customMapTileUrl"
              :custom-style-url="customMapStyleUrl"
              :map-render-mode="mapRenderMode"
              :enable-trip-replay="true"
              :auto-show-trip-replay-controls="autoShowTripReplayControls"
              :read-only="demoReadOnly"
              @timeline-marker-click="handleTimelineMarkerClick"
              @highlighted-path-click="handleHighlightedPathClick"
              @edit-favorite="handleEditFavorite"
              @delete-favorite="handleFavoriteDelete"
          />
        </template>

        <template #side>
          <TimelineContainer
              ref="timelineContainerRef"
              :timelineData="timelineDataWithStayTelemetry"
              :weather-samples="weatherSamples"
              :timelineNoData="timelineNoData"
              :timelineDataLoading="timelineDataLoading"
              :dateRange="dateRange"
              :read-only="demoReadOnly"
              @timeline-item-click="handleTimelineItemClick"
              @tag-clicked="handleTagClicked"
              @rename-stay="handleRenameStay"
              @timeline-refresh-requested="handleTimelineRefreshRequested"
              @reset-data-gap-override="handleResetDataGapOverride"
              @photo-show-on-map="handleTimelinePhotoShowOnMap"
              @navigate-date="handleNavigateDate"
          />
        </template>
      </TimelineSplitLayout>

        <!-- Timeline Share Dialog -->
        <TimelineShareDialog
            v-model:visible="showShareDialog"
            :prefill-dates="shareDates"
            @created="handleShareCreated"
        />

        <TimelineLocationEditDialogs
            :favorite-visible="showFavoriteDialog"
            :selected-favorite="selectedFavorite"
            :geocoding-visible="showGeocodingEditDialog"
            :edit-geocoding-data="editGeocodingData"
            v-model:regeneration-visible="timelineRegenerationVisible"
            :regeneration-type="timelineRegenerationType"
            :current-job-id="currentJobId"
            :job-progress="jobProgress"
            @save-favorite="handleFavoriteDialogSave"
            @close-favorite="closeFavoriteEditor"
            @save-geocoding="handleSaveGeocoding"
            @close-geocoding="closeGeocodingDialog"
        />

        <TripReconstructionDialog
            :visible="showReconstructionDialog"
            mode="timeline"
            :context-start-time="timelineReconstructionStart"
            :context-end-time="timelineReconstructionEnd"
            :fallback-center="timelineReconstructionFallbackCenter"
            :read-only="demoReadOnly"
            @close="showReconstructionDialog = false"
            @committed="handleReconstructionCommitted"
        />

        <TimelineRegenerationModal
            v-model:visible="reconstructionRegenerationVisible"
            :type="reconstructionRegenerationType"
            :job-id="reconstructionJobId"
            :job-progress="reconstructionJobProgress"
        />
    </template>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, onMounted, computed, inject } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { TimelineContainer, TimelineLocationEditDialogs, TimelineSplitLayout } from '@/components/timeline'
import TimelineMap from '@/components/maps/TimelineMap.vue'
import TimelineLargeDatasetWarning from '@/components/timeline/TimelineLargeDatasetWarning.vue'
import ProgressSpinner from 'primevue/progressspinner'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { useTimezone } from '@/composables/useTimezone'
import apiService from '@/utils/apiService'
import { readCachedUserProfile } from '@/utils/userProfileCache'
import TimelineShareDialog from '@/components/sharing/TimelineShareDialog.vue'
import TimelineRegenerationModal from '@/components/dialogs/TimelineRegenerationModal.vue'
import TripReconstructionDialog from '@/components/trips/TripReconstructionDialog.vue'
import { useTimelineRegeneration } from '@/composables/useTimelineRegeneration'
import { useTimelineItemSelection } from '@/composables/useTimelineItemSelection'
import { useTimelineLocationEditing } from '@/composables/useTimelineLocationEditing'
import { useTimelineMapMatching } from '@/composables/useTimelineMapMatching'
import { getWeatherQueryRange, padWeatherBounds } from '@/utils/timelineWeatherQuery'
import { showDemoModeToast } from '@/utils/demoMode'

const timezone = useTimezone()
import { useAuthStore } from '@/stores/auth'
import { useDateRangeStore } from '@/stores/dateRange'
import { useFavoritesStore } from '@/stores/favorites'
import { useLocationStore } from '@/stores/location'
import { useTimelineStore } from '@/stores/timeline'
import { useHighlightStore } from '@/stores/highlight'
import { useTripsStore } from '@/stores/trips'

const toast = useToast()
const route = useRoute()
const router = useRouter()

const authStore = useAuthStore()
const dateRangeStore = useDateRangeStore()
const favoritesStore = useFavoritesStore()
const locationStore = useLocationStore()
const timelineStore = useTimelineStore()
const highlightStore = useHighlightStore()
const tripsStore = useTripsStore()
const confirm = useConfirm()

const timelineSplitLayoutRef = ref(null)

const {
  showFavoriteDialog,
  selectedFavorite,
  openFavoriteEditor,
  closeFavoriteEditor,
  handleFavoriteDialogSave,
  withTimelineRegeneration,
  timelineRegenerationVisible,
  timelineRegenerationType,
  currentJobId,
  jobProgress,
  showGeocodingEditDialog,
  editGeocodingData,
  closeGeocodingDialog,
  handleSaveGeocoding,
  handleRenameStay
} = useTimelineLocationEditing({
  onFavoriteSaved: (data) => {
    timelineStore.applyStayFavoriteUpdate(data)
  },
  onGeocodingSaved: (oldGeocodingId, updated) => {
    timelineStore.applyStayGeocodingUpdate(oldGeocodingId, updated)
  }
})

const { dateRange } = storeToRefs(dateRangeStore)
const { demoReadOnly } = storeToRefs(authStore)
const { favoritePlaces } = storeToRefs(favoritesStore)
const { locationPath: pathData } = storeToRefs(locationStore)
const { timelineData, weatherSamples } = storeToRefs(timelineStore)

// Template refs
const mapViewRef = ref(null)
const timelineContainerRef = ref(null)

const {
  handleTimelineItemClick,
  handleTimelineMarkerClick,
  handleHighlightedPathClick
} = useTimelineItemSelection({
  collapseForMobileSelection: () => {
    timelineSplitLayoutRef.value?.collapseForMobileSelection?.()
  }
})

const normalizeTimelineMapRenderMode = (mode) => (
  mode === 'RASTER' ? 'RASTER' : 'VECTOR'
)

const hasOwnPreference = (source, key) => (
  source && Object.prototype.hasOwnProperty.call(source, key)
)

const readTimelineDisplayFallback = () => {
  const cachedProfile = readCachedUserProfile()
  const user = authStore.user || {}
  const customMapTileUrlSource = hasOwnPreference(user, 'customMapTileUrl')
    ? user.customMapTileUrl
    : cachedProfile.customMapTileUrl
  const customMapStyleUrlSource = hasOwnPreference(user, 'customMapStyleUrl')
    ? user.customMapStyleUrl
    : cachedProfile.customMapStyleUrl
  const mapMatchingAvailable = user.mapMatchingAvailable ?? cachedProfile.mapMatchingAvailable

  return {
    showCurrentLocationTelemetry: user.showCurrentLocationTelemetry
      ?? cachedProfile.showCurrentLocationTelemetry
      ?? true,
    customMapTileUrl: customMapTileUrlSource || null,
    customMapStyleUrl: customMapStyleUrlSource || null,
    mapRenderMode: normalizeTimelineMapRenderMode(user.mapRenderMode || cachedProfile.mapRenderMode),
    autoShowTripReplayControls: user.autoShowTripReplayControls
      ?? cachedProfile.autoShowTripReplayControls
      ?? true,
    mapMatchingEnabled: mapMatchingAvailable === false
      ? false
      : (user.mapMatchingEnabled ?? cachedProfile.mapMatchingEnabled ?? false)
  }
}

const initialTimelineDisplaySettings = readTimelineDisplayFallback()

// Reactive state
const mapDataLoading = ref(false)
const mapNoData = ref(false)
const timelineNoData = ref(false)
const timelineDataLoading = ref(true)
const lastFetchedRange = ref(null)
const lastAppliedFocusKey = ref(null)
const currentLocation = ref(null)
let currentLocationRequestToken = 0
const mapPreferencesLoaded = ref(false)
const showCurrentLocationTelemetry = ref(initialTimelineDisplaySettings.showCurrentLocationTelemetry)
const customMapTileUrl = ref(initialTimelineDisplaySettings.customMapTileUrl)
const customMapStyleUrl = ref(initialTimelineDisplaySettings.customMapStyleUrl)
const mapRenderMode = ref(initialTimelineDisplaySettings.mapRenderMode)
const autoShowTripReplayControls = ref(initialTimelineDisplaySettings.autoShowTripReplayControls)
const mapMatchingEnabled = ref(initialTimelineDisplaySettings.mapMatchingEnabled)
const isFetching = ref(false) // Flag to prevent concurrent fetches
const pendingFetchKey = ref(null) // Track the currently pending fetch
const queuedFetchRange = ref(null) // Keep latest requested range while a fetch is running
const showReconstructionDialog = ref(false)
const {
  timelineRegenerationVisible: reconstructionRegenerationVisible,
  timelineRegenerationType: reconstructionRegenerationType,
  currentJobId: reconstructionJobId,
  jobProgress: reconstructionJobProgress,
  trackExistingTimelineJob
} = useTimelineRegeneration()

// Large dataset warning state
const showLargeDatasetWarning = ref(false)
const datasetCounts = ref({ totalItems: 0, stays: 0, trips: 0, dataGaps: 0, limit: 150 })
const forceLoadLargeDataset = ref(false)

// Share dialog state - injected from MainAppPage
const showShareDialog = inject('shareDialogVisible', ref(false))
const timelineReconstructionRequestToken = inject('timelineReconstructionRequestToken', ref(0))
const shareDates = computed(() => ({
  start: dateRangeStore.startDate,
  end: dateRangeStore.endDate
}))
const canReconstructTimeline = computed(() => (
  Array.isArray(dateRange.value) && dateRange.value.length === 2 && Boolean(dateRange.value[0]) && Boolean(dateRange.value[1])
))
const timelineReconstructionStart = computed(() => dateRange.value?.[0] || null)
const timelineReconstructionEnd = computed(() => dateRange.value?.[1] || null)
const timelineReconstructionFallbackCenter = computed(() => {
  const firstPathPoint = pathData.value?.points?.[0]
  if (
    firstPathPoint
    && Number.isFinite(firstPathPoint.latitude)
    && Number.isFinite(firstPathPoint.longitude)
  ) {
    return [firstPathPoint.latitude, firstPathPoint.longitude]
  }

  const firstTimelinePoint = (timelineDataWithStayTelemetry.value || []).find((item) => (
    Number.isFinite(item?.latitude) && Number.isFinite(item?.longitude)
  ))
  if (firstTimelinePoint) {
    return [firstTimelinePoint.latitude, firstTimelinePoint.longitude]
  }

  return [37.7749, -122.4194]
})

const visibleTrips = computed(() => {
  const items = Array.isArray(timelineData.value) ? timelineData.value : []
  return items.filter(item => item?.type === 'trip' && item?.id)
})

const {
  activePathData,
  matchedTripIds,
  statusText: mapMatchingStatusText,
  resolve: resolveMapMatching,
  reset: resetMapMatching
} = useTimelineMapMatching({
  enabled: mapMatchingEnabled,
  visibleTrips,
  rawPathData: pathData
})

const selectedSingleDayDate = computed(() => {
  if (!dateRange.value || dateRange.value.length !== 2) {
    return null
  }

  const dates = timezone.getDateRangeArray(dateRange.value[0], dateRange.value[1])
  return dates.length === 1 ? dates[0] : null
})

const isSingleDaySelected = computed(() => Boolean(selectedSingleDayDate.value))

const selectedDateLabel = computed(() => (
  selectedSingleDayDate.value ? timezone.formatDateLong(selectedSingleDayDate.value) : ''
))

// Methods
const triggerMapResize = () => {
  nextTick(() => {
    const invalidateMap = () => {
      mapViewRef.value?.invalidateSize?.()
    }

    invalidateMap()
    setTimeout(invalidateMap, 260)
  })
}

const showDemoReadOnlyToast = () => {
  showDemoModeToast(toast)
}

const handleEditFavorite = (favorite) => {
  if (demoReadOnly.value) {
    showDemoReadOnlyToast()
    return
  }

  // Open the edit dialog with full favorite data
  openFavoriteEditor(favorite)
}

const handleFavoriteDelete = (favorite) => {
  if (demoReadOnly.value) {
    showDemoReadOnlyToast()
    return
  }

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

const fetchWeatherData = async (startDate, endDate) => {
  if (timelineNoData.value) {
    timelineStore.clearWeatherSamples()
    return
  }

  const weatherRange = getWeatherQueryRange(startDate, endDate, timelineData.value)
  const apiBounds = padWeatherBounds(timelineStore.getGeographicBounds)

  try {
    await timelineStore.fetchWeatherSamples(weatherRange.startTime, weatherRange.endTime, apiBounds)
  } catch (error) {
    console.warn('Weather samples unavailable:', error)
    timelineStore.clearWeatherSamples()
  }
}

const refreshCurrentLocation = async () => {
  const requestToken = ++currentLocationRequestToken

  if (!timeframeIncludesToday.value) {
    currentLocation.value = null
    return
  }

  try {
    const latestPoint = await locationStore.getLastKnownPosition()

    if (requestToken !== currentLocationRequestToken || !timeframeIncludesToday.value) {
      return
    }

    const latitude = Number(latestPoint?.lat)
    const longitude = Number(latestPoint?.lon)

    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
      currentLocation.value = null
      return
    }

    currentLocation.value = {
      latitude,
      longitude,
      timestamp: latestPoint.timestamp,
      telemetryCurrentPopup: showCurrentLocationTelemetry.value
        ? (latestPoint.telemetryCurrentPopup || [])
        : []
    }
  } catch (error) {
    if (requestToken === currentLocationRequestToken) {
      currentLocation.value = null
      console.warn('Failed to load current location:', error)
    }
  }
}

const handleForceLoad = async () => {
  forceLoadLargeDataset.value = true
  showLargeDatasetWarning.value = false

  // Re-run the fetch pipeline directly for the same range.
  // The date range store deduplicates identical values, so simply setting the
  // same range again will not trigger the watcher.
  const currentRange = dateRange.value
  if (currentRange && currentRange.length === 2) {
    const [startDate, endDate] = currentRange
    const normalizedStart = new Date(startDate).toISOString()
    const normalizedEnd = new Date(endDate).toISOString()
    const rangeKey = `${normalizedStart}-${normalizedEnd}`
    await executeFetchForRange(startDate, endDate, rangeKey)
  }
}

const handleShareCreated = (share) => {
  // Dialog will stay open to show the success state with copy link
  // No need to show toast as the dialog already shows success message
}

const handleNavigateDate = (targetDate) => {
  if (!targetDate) return
  const { start, end } = timezone.createDateRangeUtc(targetDate, targetDate)
  dateRangeStore.setDateRange([start, end])
}

const navigateTimelineDay = (offset) => {
  if (!selectedSingleDayDate.value) return

  const targetDate = timezone.create(selectedSingleDayDate.value)
    .add(offset, 'day')
    .format('YYYY-MM-DD')
  handleNavigateDate(targetDate)
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

const reloadCurrentRange = async () => {
  if (!dateRange.value || dateRange.value.length !== 2) {
    return
  }

  const [startDate, endDate] = dateRange.value
  const normalizedStart = new Date(startDate).toISOString()
  const normalizedEnd = new Date(endDate).toISOString()
  const rangeKey = `${normalizedStart}-${normalizedEnd}`
  await executeFetchForRange(startDate, endDate, rangeKey)
}

const handleTimelineRefreshRequested = async () => {
  await reloadCurrentRange()
}

const handleResetDataGapOverride = (stayItem) => {
  const overrideId = stayItem?.dataGapOverrideId
  if (!overrideId) {
    return
  }

  confirm.require({
    header: 'Reset Manual Stay Override',
    message: 'Reset this manual Data Gap override back to automatic timeline detection? This will regenerate timeline segments.',
    icon: 'pi pi-exclamation-triangle',
    accept: async () => {
      try {
        await timelineStore.resetDataGapStayOverride(overrideId)
        await reloadCurrentRange()
        toast.add({
          severity: 'success',
          summary: 'Override Reset',
          detail: 'Manual Data Gap override was reset to automatic behavior.',
          life: 3000
        })
      } catch (error) {
        const errorMessage = error.response?.data?.message || error.message || 'Failed to reset manual override'
        toast.add({
          severity: 'error',
          summary: 'Reset Failed',
          detail: errorMessage,
          life: 5000
        })
      }
    }
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

const openTimelineReconstructionDialog = () => {
  if (!canReconstructTimeline.value) {
    return
  }
  showReconstructionDialog.value = true
}

const handleReconstructionCommitted = async (result) => {
  showReconstructionDialog.value = false

  const jobId = result?.jobId
  if (!jobId) {
    await reloadCurrentRange()
    return
  }

  await trackExistingTimelineJob(String(jobId), {
    modalType: 'reconstruction',
    onCompleted: async () => {
      await reloadCurrentRange()
      toast.add({
        severity: 'success',
        summary: 'Timeline Updated',
        detail: 'Missing timeline data has been added.',
        life: 3200
      })
    },
    onFailed: (progress) => {
      toast.add({
        severity: 'error',
        summary: 'Timeline Generation Failed',
        detail: progress?.errorMessage || 'Timeline generation job failed.',
        life: 5000
      })
    },
    onTrackingError: (error) => {
      toast.add({
        severity: 'error',
        summary: 'Timeline Job Tracking Failed',
        detail: error,
        life: 5000
      })
    }
  })
}

const loadTimelineDisplaySettings = async () => {
  const fallback = readTimelineDisplayFallback()

  try {
    const response = await apiService.get('/users/preferences/timeline/display')
    const data = response?.data || response
    showCurrentLocationTelemetry.value = data?.showCurrentLocationTelemetry ?? fallback.showCurrentLocationTelemetry
    customMapTileUrl.value = hasOwnPreference(data, 'customMapTileUrl')
      ? data.customMapTileUrl || null
      : fallback.customMapTileUrl
    customMapStyleUrl.value = hasOwnPreference(data, 'customMapStyleUrl')
      ? data.customMapStyleUrl || null
      : fallback.customMapStyleUrl
    mapRenderMode.value = normalizeTimelineMapRenderMode(data?.mapRenderMode || fallback.mapRenderMode)
    autoShowTripReplayControls.value = data?.autoShowTripReplayControls ?? fallback.autoShowTripReplayControls
    mapMatchingEnabled.value = data?.mapMatchingEnabled ?? fallback.mapMatchingEnabled
  } catch (error) {
    showCurrentLocationTelemetry.value = fallback.showCurrentLocationTelemetry
    customMapTileUrl.value = fallback.customMapTileUrl
    customMapStyleUrl.value = fallback.customMapStyleUrl
    mapRenderMode.value = fallback.mapRenderMode
    autoShowTripReplayControls.value = fallback.autoShowTripReplayControls
    mapMatchingEnabled.value = fallback.mapMatchingEnabled
  } finally {
    mapPreferencesLoaded.value = true
  }
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
    highlightStore.clearAllHighlights()

    const shouldProceed = await checkDatasetSize(startDate, endDate)

    if (!shouldProceed) {
      mapDataLoading.value = false
      timelineDataLoading.value = false
      return
    }

    forceLoadLargeDataset.value = false

    resetMapMatching()

    const locationPromise = fetchLocationData(startDate, endDate)
    const timelinePromise = fetchTimelineData(startDate, endDate)
    await timelinePromise
    resolveMapMatching()
    await locationPromise
    await nextTick()
    triggerMapResize()
    await fetchWeatherData(startDate, endDate)
    await refreshCurrentLocation()
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
  await loadTimelineDisplaySettings()
  await refreshCurrentLocation()
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
          if (typeof mapViewRef.value?.invalidateSize === 'function') {
            mapViewRef.value.invalidateSize()
            return
          }

          const rawMap = mapViewRef.value?.map?.value || mapViewRef.value?.map
          if (typeof rawMap?.invalidateSize === 'function') {
            rawMap.invalidateSize()
            return
          }

          if (typeof rawMap?.resize === 'function') {
            rawMap.resize()
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

const timeframeIncludesToday = computed(() => {
  if (!dateRange.value || !Array.isArray(dateRange.value) || dateRange.value.length !== 2) {
    return false
  }
  
  const [startDate, endDate] = dateRange.value
  if (!startDate || !endDate) return false
  
  const todayStart = timezone.now().startOf('day')
  const todayEnd = timezone.now().endOf('day')
  
  const start = timezone.fromUtc(startDate)
  const end = timezone.fromUtc(endDate)
  
  return start.isSameOrBefore(todayEnd) && end.isSameOrAfter(todayStart)
})

const telemetryPathPoints = computed(() => {
  const points = Array.isArray(pathData.value?.points) ? pathData.value.points : []
  return points
    .filter(point => (
      point?.timestamp &&
      Array.isArray(point?.telemetryCurrentPopup) &&
      point.telemetryCurrentPopup.length > 0
    ))
    .map(point => ({
      timestampMs: new Date(point.timestamp).getTime(),
      telemetryCurrentPopup: point.telemetryCurrentPopup
    }))
    .filter(point => Number.isFinite(point.timestampMs))
    .sort((a, b) => a.timestampMs - b.timestampMs)
})

const getStayTelemetrySnapshot = (stayItem, telemetryPoints) => {
  if (!stayItem?.timestamp || telemetryPoints.length === 0) {
    return []
  }

  const startMs = new Date(stayItem.timestamp).getTime()
  if (!Number.isFinite(startMs)) {
    return []
  }

  const durationSeconds = Number(stayItem.stayDuration) || 0
  const endMs = startMs + Math.max(0, durationSeconds * 1000)

  // Prefer latest telemetry point inside this stay window.
  for (let index = telemetryPoints.length - 1; index >= 0; index -= 1) {
    const point = telemetryPoints[index]
    if (point.timestampMs > endMs) {
      continue
    }
    if (point.timestampMs >= startMs) {
      return point.telemetryCurrentPopup || []
    }
    break
  }

  // Fall back to nearest telemetry sample to stay start if close enough.
  let nearestPoint = null
  let nearestDiffMs = Number.POSITIVE_INFINITY
  for (const point of telemetryPoints) {
    const diff = Math.abs(point.timestampMs - startMs)
    if (diff < nearestDiffMs) {
      nearestDiffMs = diff
      nearestPoint = point
    }
  }

  return nearestPoint && nearestDiffMs <= 5 * 60 * 1000
    ? (nearestPoint.telemetryCurrentPopup || [])
    : []
}

const timelineDataWithStayTelemetry = computed(() => {
  const items = Array.isArray(timelineData.value) ? timelineData.value : []

  if (!showCurrentLocationTelemetry.value) {
    return items.map(item => (
      item?.type === 'stay'
        ? { ...item, telemetryCurrentPopup: [] }
        : item
    ))
  }

  const telemetryPoints = telemetryPathPoints.value

  if (items.length === 0 || telemetryPoints.length === 0) {
    return items
  }

  return items.map(item => {
    if (item?.type !== 'stay') {
      return item
    }

    const telemetryCurrentPopup = getStayTelemetrySnapshot(item, telemetryPoints)
    return {
      ...item,
      telemetryCurrentPopup
    }
  })
})

const getFirstQueryValue = (value) => {
  if (Array.isArray(value)) {
    return value[0] || ''
  }
  return value || ''
}

const timestampsMatch = (left, right) => {
  if (!left || !right) {
    return false
  }
  if (left === right) {
    return true
  }

  const leftMs = new Date(left).getTime()
  const rightMs = new Date(right).getTime()
  return Number.isFinite(leftMs) && Number.isFinite(rightMs) && leftMs === rightMs
}

const findFocusedStay = () => {
  const focusStay = getFirstQueryValue(route.query.focusStay)
  const focusTime = getFirstQueryValue(route.query.focusTime)
  if (!focusStay && !focusTime) {
    return null
  }

  const stays = (timelineDataWithStayTelemetry.value || []).filter(item => item?.type === 'stay')
  if (focusStay) {
    const byId = stays.find(item => String(item.id) === String(focusStay))
    if (byId) {
      return byId
    }
  }

  if (focusTime) {
    return stays.find(item => timestampsMatch(item.timestamp, focusTime)) || null
  }

  return null
}

const applyTimelineFocusFromQuery = async () => {
  if (timelineDataLoading.value) {
    return
  }

  const focusedStay = findFocusedStay()
  if (!focusedStay) {
    return
  }

  const focusStay = getFirstQueryValue(route.query.focusStay)
  const focusTime = getFirstQueryValue(route.query.focusTime)
  const focusKey = `${focusStay || ''}:${focusTime || ''}:${focusedStay.id || focusedStay.timestamp || ''}`
  if (lastAppliedFocusKey.value === focusKey) {
    return
  }

  lastAppliedFocusKey.value = focusKey
  highlightStore.setHighlightedItem(focusedStay)
  await nextTick()
  timelineContainerRef.value?.scrollToTimelineItem?.(focusedStay)
}

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

watch(
  [
    timelineDataWithStayTelemetry,
    timelineDataLoading,
    () => route.query.focusStay,
    () => route.query.focusTime
  ],
  () => {
    void applyTimelineFocusFromQuery()
  },
  { immediate: true }
)

// Watch for date ranges that include today and get current location
watch(timeframeIncludesToday, () => {
  refreshCurrentLocation()
}, { immediate: true })

watch(showCurrentLocationTelemetry, () => {
  refreshCurrentLocation()
})

watch(() => timelineReconstructionRequestToken.value, () => {
  openTimelineReconstructionDialog()
})
</script>

<style scoped>
.timeline-page {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 160px); /* Account for navbar (60px) + tabs (40px) + padding (60px) */
  overflow: hidden;
  overscroll-behavior: contain;
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
@media (max-width: 768px), (max-height: 520px) and (pointer: coarse) {
  .timeline-page {
    height: calc(100dvh - 112px); /* Mobile navbar + tab bar */
    min-height: 0;
  }
}

@media (min-width: 768px) and (max-width: 1024px) {
  .timeline-page {
    height: calc(100vh - 150px);
  }
}

</style>

<style>
.gp-tab-container:has(.timeline-page),
.gp-tab-content:has(.timeline-page) {
  overscroll-behavior: contain;
}

.gp-tab-content:has(.timeline-page) {
  overflow: hidden;
}

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
