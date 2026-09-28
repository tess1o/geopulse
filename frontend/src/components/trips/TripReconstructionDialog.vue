<template>
  <Dialog
    v-model:visible="internalVisible"
    modal
    :header="dialogHeader"
    class="gp-dialog-xl trip-reconstruction-dialog"
    @hide="handleClose"
  >
    <div class="reconstruction-layout">
      <TripReconstructionSegmentsPanel
        :reconstruction-help-message="reconstructionHelpMessage"
        :segments="segments"
        :active-segment-id="activeSegmentId"
        :segment-type-options="segmentTypeOptions"
        :movement-type-options="movementTypeOptions"
        :timezone="timezone"
        :location-source-label="locationSourceLabel"
        :waypoint-label="waypointLabel"
        :waypoint-tag-severity="waypointTagSeverity"
        @set-active-segment="setActiveSegment"
        @add-segment="addSegment"
        @move-segment="moveSegment"
        @remove-segment="removeSegment"
        @update-segment-type="updateSegmentType"
        @update-segment-field="updateSegmentField"
        @move-waypoint="moveWaypoint"
        @remove-waypoint="removeWaypoint"
      />

      <TripReconstructionMapPanel
        :map-id="`trip-reconstruction-map-${mapId}`"
        :center="mapCenter"
        :zoom="mapZoom"
        :map="mapInstance"
        :active-segment="activeSegment"
        :active-segment-index="activeSegmentIndex"
        :context-points="contextPoints"
        :context-trip-lines="contextTripLines"
        :id-prefix="`trip-reconstruction-${mapId}`"
        :search-query="searchQuery"
        :search-suggestions="searchSuggestions"
        :search-loading="isSearchLoading"
        :search-error="searchError"
        @map-ready="handleMapReady"
        @map-click="handleMapClick"
        @stay-dragged="handleStayDragged"
        @waypoint-dragged="handleWaypointDragged"
        @waypoint-removed="handleWaypointRemoved"
        @search-complete="handleSearchComplete"
        @search-select="handleSearchSelect"
        @update:search-query="searchQuery = $event"
      >
        <TripReconstructionPreviewSummary
          :preview-result="previewResult"
          :preview-summary="previewSummary"
          :preview-warnings="previewWarnings"
          :format-date-time="formatDateTime"
          :format-duration-minutes="formatDurationMinutes"
        />
      </TripReconstructionMapPanel>
    </div>

    <template #footer>
      <div class="footer-stack">
        <p v-if="readOnly" class="demo-disabled-text">
          {{ t('trips.reconstructionDialog.demoDisabledText') }}
        </p>
        <div class="footer-actions">
          <Button
            :label="t('trips.reconstructionDialog.cancel')"
            icon="pi pi-times"
            outlined
            @click="handleClose"
          />
          <Button
            :label="t('trips.reconstructionDialog.validate')"
            icon="pi pi-check-circle"
            outlined
            :loading="isPreviewLoading"
            @click="previewReconstruction"
          />
          <Button
            :label="t('trips.reconstructionDialog.commitAndRegenerate')"
            icon="pi pi-check"
            :loading="isCommitLoading"
            :disabled="readOnly || isCommitLoading"
            v-tooltip.bottom="readOnly ? t('trips.reconstructionDialog.commitTooltipDemoDisabled') : t('trips.reconstructionDialog.commitTooltip')"
            @click="commitReconstruction"
          />
        </div>
      </div>
    </template>
  </Dialog>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import { useTimezone } from '@/composables/useTimezone'
import { useTripsStore } from '@/stores/trips'
import { useFavoritesStore } from '@/stores/favorites'
import { useGeocodingStore } from '@/stores/geocoding'
import { useTripReconstructionSegments } from '@/composables/useTripReconstructionSegments'
import { useTripReconstructionLocationSync } from '@/composables/useTripReconstructionLocationSync'
import {
  getTripPlanSuggestionCoordinates,
  isTripPlanLocalSearchSource,
  useTripPlanLocationSearch
} from '@/composables/useTripPlanLocationSearch'
import { fitMapToActiveSegment } from '@/maps/tripReconstruction/shared/tripReconstructionViewport'
import TripReconstructionSegmentsPanel from '@/components/trips/reconstruction/TripReconstructionSegmentsPanel.vue'
import TripReconstructionMapPanel from '@/components/trips/reconstruction/TripReconstructionMapPanel.vue'
import TripReconstructionPreviewSummary from '@/components/trips/reconstruction/TripReconstructionPreviewSummary.vue'
import { showDemoModeToast } from '@/utils/demoMode'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  mode: {
    type: String,
    default: 'trip'
  },
  tripId: {
    type: Number,
    default: null
  },
  trip: {
    type: Object,
    default: null
  },
  contextStartTime: {
    type: String,
    default: null
  },
  contextEndTime: {
    type: String,
    default: null
  },
  fallbackCenter: {
    type: Array,
    default: () => [37.7749, -122.4194]
  },
  readOnly: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['close', 'committed'])

const { t } = useI18n()
const timezone = useTimezone()
const toast = useToast()
const tripsStore = useTripsStore()
const favoritesStore = useFavoritesStore()
const geocodingStore = useGeocodingStore()

const internalVisible = ref(props.visible)
const mapId = ref(Math.random().toString(36).slice(2, 10))
const mapInstance = ref(null)
const mapCenter = ref([...props.fallbackCenter])
const mapZoom = ref(13)
const isPreviewLoading = ref(false)
const isCommitLoading = ref(false)
const previewResult = ref(null)
const previewWarnings = ref([])

const {
  query: searchQuery,
  suggestions: searchSuggestions,
  isLoading: isSearchLoading,
  error: searchError,
  search: handleSearchComplete,
  reset: resetSearchState
} = useTripPlanLocationSearch({
  getBias: () => getSearchBias(),
  fallbackLabel: t('trips.search.plannedPlaceFallback')
})

const dialogHeader = computed(() => (props.mode === 'trip' ? t('trips.reconstructionDialog.headerTrip') : t('trips.reconstructionDialog.headerTimeline')))
const reconstructionHelpMessage = computed(() => (
  props.mode === 'trip'
    ? t('trips.reconstructionDialog.helpTrip')
    : t('trips.reconstructionDialog.helpTimeline')
))

const getContextRangeDates = () => {
  if (props.mode === 'trip') {
    if (!props.trip?.startTime || !props.trip?.endTime) {
      return { start: null, end: null }
    }

    return {
      start: timezone.fromUtc(props.trip.startTime).toDate(),
      end: timezone.fromUtc(props.trip.endTime).toDate()
    }
  }

  if (!props.contextStartTime || !props.contextEndTime) {
    return { start: null, end: null }
  }

  return {
    start: timezone.fromUtc(props.contextStartTime).toDate(),
    end: timezone.fromUtc(props.contextEndTime).toDate()
  }
}

const sharedSegments = ref([])

const locationSync = useTripReconstructionLocationSync({
  segments: sharedSegments,
  tripsStore,
  favoritesStore,
  geocodingStore
})

const renderActiveViewport = () => {
  fitMapToActiveSegment(mapInstance.value, segments.value, activeSegmentIndex.value)
}

const {
  segments,
  activeSegmentId,
  activeSegmentIndex,
  activeSegment,
  segmentTypeOptions,
  movementTypeOptions,
  previewSummary,
  waypointLabel,
  waypointTagSeverity,
  addSegment,
  removeSegment,
  moveSegment,
  setActiveSegment,
  updateSegmentField,
  updateSegmentType,
  addWaypoint,
  updateWaypoint,
  removeWaypoint,
  moveWaypoint,
  updateStayLocation,
  resetDialogState,
  buildValidatedPayload,
  consumeActiveSegmentReframeFlag,
  getContextPoints,
  getContextTripLines
} = useTripReconstructionSegments({
  timezone,
  segmentsRef: sharedSegments,
  getContextRangeDates,
  clearStayLocationResolution: locationSync.clearStayLocationResolution,
  resolveStayLocationName: locationSync.resolveStayLocationName,
  onRenderRequest: ({ reframe }) => {
    if (!reframe) {
      return
    }

    nextTick(() => {
      renderActiveViewport()
    })
  }
})

const locationSourceLabel = (segment) => {
  return locationSync?.locationSourceLabel?.(segment) || t('trips.locationSource.unknown')
}

const contextPoints = computed(() => getContextPoints())
const contextTripLines = computed(() => getContextTripLines())

const normalizeSuggestionId = (value) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? Math.trunc(parsed) : null
}

const getSearchBias = () => {
  const active = activeSegment.value
  if (active?.segmentType === 'STAY' && Number.isFinite(active.latitude) && Number.isFinite(active.longitude)) {
    return { lat: active.latitude, lon: active.longitude }
  }

  if (active?.segmentType === 'TRIP' && Array.isArray(active.waypoints) && active.waypoints.length > 0) {
    const waypoint = active.waypoints[active.waypoints.length - 1]
    if (Number.isFinite(waypoint?.latitude) && Number.isFinite(waypoint?.longitude)) {
      return { lat: waypoint.latitude, lon: waypoint.longitude }
    }
  }

  const center = mapInstance.value?.getCenter?.()
  if (Number.isFinite(center?.lat) && Number.isFinite(center?.lng)) {
    return { lat: center.lat, lon: center.lng }
  }

  if (Array.isArray(mapCenter.value) && mapCenter.value.length >= 2) {
    const [lat, lon] = mapCenter.value
    if (Number.isFinite(lat) && Number.isFinite(lon)) {
      return { lat, lon }
    }
  }

  return null
}

const handleMapReady = (map) => {
  mapInstance.value = map
  renderActiveViewport()
}

const handleMapClick = (event) => {
  if (!activeSegment.value || !event?.latlng) {
    return
  }

  const { lat, lng } = event.latlng
  const segmentIndex = activeSegmentIndex.value

  if (activeSegment.value.segmentType === 'STAY') {
    updateStayLocation(segmentIndex, lat, lng, { reframe: false, resolveName: true })
    return
  }

  addWaypoint(segmentIndex, lat, lng)
}

const handleStayDragged = ({ segmentIndex, latitude, longitude }) => {
  updateStayLocation(segmentIndex, latitude, longitude, { reframe: false, resolveName: true })
}

const handleWaypointDragged = ({ segmentIndex, waypointIndex, latitude, longitude }) => {
  updateWaypoint(segmentIndex, waypointIndex, latitude, longitude)
}

const handleWaypointRemoved = ({ segmentIndex, waypointIndex }) => {
  removeWaypoint(segmentIndex, waypointIndex)
}

const applySelectedSuggestionToStay = (segmentIndex, suggestion) => {
  const coordinates = getTripPlanSuggestionCoordinates(suggestion)
  if (!coordinates) {
    return
  }

  const sourceType = suggestion?.sourceType || 'coordinates'
  const shouldResolveName = !isTripPlanLocalSearchSource(sourceType)

  updateStayLocation(segmentIndex, coordinates.latitude, coordinates.longitude, {
    reframe: true,
    resolveName: shouldResolveName
  })

  const segment = segments.value[segmentIndex]
  if (!segment || segment.segmentType !== 'STAY') {
    return
  }

  if (shouldResolveName) {
    return
  }

  segment.locationName = suggestion?.title?.trim() || segment.locationName
  segment.locationSourceType = sourceType
  segment.locationFavoriteId = normalizeSuggestionId(suggestion?.favoriteId)
  segment.locationFavoriteType = suggestion?.favoriteType || null
  segment.locationGeocodingId = normalizeSuggestionId(suggestion?.geocodingId)
  segment.locationResolvedName = suggestion?.title?.trim() || null
  segment.locationNameEdited = false
}

const handleSearchSelect = (suggestion) => {
  if (!activeSegment.value) {
    return
  }

  const coordinates = getTripPlanSuggestionCoordinates(suggestion)
  if (!coordinates) {
    return
  }

  const segmentIndex = activeSegmentIndex.value

  if (activeSegment.value.segmentType === 'STAY') {
    applySelectedSuggestionToStay(segmentIndex, suggestion)
  } else if (activeSegment.value.segmentType === 'TRIP') {
    addWaypoint(segmentIndex, coordinates.latitude, coordinates.longitude)
    nextTick(() => {
      renderActiveViewport()
    })
  }

  resetSearchState()
}

const previewReconstruction = async () => {
  const { payload, error } = buildValidatedPayload(props.tripId)
  if (error) {
    toast.add({
      severity: 'warn',
      summary: t('trips.reconstructionDialog.validationErrorSummary'),
      detail: error,
      life: 3500
    })
    return
  }

  isPreviewLoading.value = true
  try {
    previewResult.value = await tripsStore.previewReconstruction(payload)

    const backendWarnings = Array.isArray(previewResult.value?.warnings)
      ? previewResult.value.warnings
      : []

    const derivedWarnings = []
    if (previewSummary.value.gapCount > 0) {
      derivedWarnings.push(t('trips.reconstructionDialog.detectedGapWarning', {
        count: previewSummary.value.gapCount,
        duration: formatDurationMinutes(previewSummary.value.gapMinutes)
      }))
    }

    previewWarnings.value = [...backendWarnings, ...derivedWarnings]

    toast.add({
      severity: 'success',
      summary: t('trips.reconstructionDialog.validationReadySummary'),
      detail: t('trips.reconstructionDialog.validationReadyDetail', {
        points: previewResult.value?.estimatedPoints || 0,
        gaps: previewSummary.value.gapCount
      }),
      life: 2800
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('trips.reconstructionDialog.validationFailedSummary'),
      detail: formatApiErrorDetail(error, t('trips.reconstructionDialog.requestFailed')),
      life: 5000
    })
  } finally {
    isPreviewLoading.value = false
  }
}

const commitReconstruction = async () => {
  if (props.readOnly) {
    showDemoModeToast(toast, t('trips.reconstructionDialog.commitDemoToast'))
    return
  }

  const { payload, error } = buildValidatedPayload(props.tripId)
  if (error) {
    toast.add({
      severity: 'warn',
      summary: t('trips.reconstructionDialog.validationErrorSummary'),
      detail: error,
      life: 3500
    })
    return
  }

  isCommitLoading.value = true
  try {
    await locationSync.syncStayLocationNamesToSources()
    const result = await tripsStore.commitReconstruction(payload)

    if (result?.regenerationWarning) {
      toast.add({
        severity: 'warn',
        summary: t('trips.reconstructionDialog.pointsSavedSummary'),
        detail: result.regenerationWarning,
        life: 6000
      })
    } else {
      toast.add({
        severity: 'success',
        summary: t('trips.reconstructionDialog.dataSavedSummary'),
        detail: t('trips.reconstructionDialog.dataSavedDetail', { count: result?.insertedPoints || 0 }),
        life: 3200
      })
    }

    emit('committed', result)
    internalVisible.value = false
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('trips.reconstructionDialog.commitFailedSummary'),
      detail: formatApiErrorDetail(error, t('trips.reconstructionDialog.requestFailed')),
      life: 5000
    })
  } finally {
    isCommitLoading.value = false
  }
}

const formatDateTime = (value) => {
  if (!value) return '—'
  return timezone.formatDateTimeDisplay(value)
}

const formatDurationMinutes = (minutes) => {
  const safeMinutes = Number.isFinite(minutes) ? Math.max(0, minutes) : 0
  if (safeMinutes < 60) {
    return t('trips.reconstructionDialog.minutesShort', { minutes: safeMinutes })
  }

  const hours = Math.floor(safeMinutes / 60)
  const remainder = safeMinutes % 60
  if (remainder === 0) {
    return t('trips.reconstructionDialog.hoursShort', { hours })
  }

  return t('trips.reconstructionDialog.hoursMinutesShort', { hours, minutes: remainder })
}

const handleClose = () => {
  internalVisible.value = false
}

watch(() => props.visible, (nextVisible) => {
  internalVisible.value = nextVisible

  if (!nextVisible) {
    return
  }

  mapCenter.value = Array.isArray(props.fallbackCenter) ? [...props.fallbackCenter] : [37.7749, -122.4194]
  mapZoom.value = 13

  resetDialogState()
  locationSync?.resetLocationResolutionState?.()
  previewResult.value = null
  previewWarnings.value = []
  resetSearchState()

  nextTick(() => {
    renderActiveViewport()
  })
})

watch(internalVisible, (nextVisible) => {
  if (!nextVisible) {
    previewWarnings.value = []
    resetSearchState()
    emit('close')
  }
})

watch(activeSegmentId, () => {
  searchSuggestions.value = []
  searchError.value = ''

  const shouldReframe = consumeActiveSegmentReframeFlag()
  if (!shouldReframe) {
    return
  }

  nextTick(() => {
    renderActiveViewport()
  })
})
</script>

<style scoped>
.reconstruction-layout {
  display: grid;
  grid-template-columns: minmax(340px, 0.85fr) minmax(0, 1.75fr);
  gap: var(--gp-spacing-md);
  min-height: 0;
}

.footer-stack {
  width: 100%;
  display: grid;
  gap: var(--gp-spacing-sm);
}

.footer-actions {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--gp-spacing-xs);
  flex-wrap: wrap;
}

.demo-disabled-text {
  margin: 0;
  text-align: right;
}

@media (max-width: 1080px) {
  .reconstruction-layout {
    grid-template-columns: 1fr;
  }
}
</style>

<style>
.trip-reconstruction-dialog {
  width: 96vw !important;
  max-width: 1600px !important;
}
</style>
