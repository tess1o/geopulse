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
        @map-ready="handleMapReady"
        @map-click="handleMapClick"
        @stay-dragged="handleStayDragged"
        @waypoint-dragged="handleWaypointDragged"
        @waypoint-removed="handleWaypointRemoved"
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
      <div class="footer-actions">
        <Button
          label="Cancel"
          icon="pi pi-times"
          outlined
          @click="handleClose"
        />
        <Button
          label="Validate"
          icon="pi pi-check-circle"
          outlined
          :loading="isPreviewLoading"
          @click="previewReconstruction"
        />
        <Button
          label="Commit & Regenerate"
          icon="pi pi-check"
          :loading="isCommitLoading"
          @click="commitReconstruction"
        />
      </div>
    </template>
  </Dialog>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useToast } from 'primevue/usetoast'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import { useTimezone } from '@/composables/useTimezone'
import { useTripsStore } from '@/stores/trips'
import { useFavoritesStore } from '@/stores/favorites'
import { useGeocodingStore } from '@/stores/geocoding'
import { useTripReconstructionSegments } from '@/composables/useTripReconstructionSegments'
import { useTripReconstructionLocationSync } from '@/composables/useTripReconstructionLocationSync'
import { fitMapToActiveSegment } from '@/maps/tripReconstruction/shared/tripReconstructionViewport'
import TripReconstructionSegmentsPanel from '@/components/trips/reconstruction/TripReconstructionSegmentsPanel.vue'
import TripReconstructionMapPanel from '@/components/trips/reconstruction/TripReconstructionMapPanel.vue'
import TripReconstructionPreviewSummary from '@/components/trips/reconstruction/TripReconstructionPreviewSummary.vue'

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
  }
})

const emit = defineEmits(['close', 'committed'])

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

const dialogHeader = computed(() => (props.mode === 'trip' ? 'Add Missing Trip Data' : 'Add Missing Timeline Data'))
const reconstructionHelpMessage = computed(() => (
  props.mode === 'trip'
    ? 'Add stays and trips to generate missing GPS points for this trip. Existing GPS points are preserved; this does not replace existing timeline data. For a trip segment, add multiple waypoints on the map: first point is start, last point is end.'
    : 'Add stays and trips to generate missing GPS points for any dates and times you choose. Existing GPS points are preserved; this does not replace existing timeline data. For a trip segment, add multiple waypoints on the map: first point is start, last point is end.'
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
  return locationSync?.locationSourceLabel?.(segment) || 'Unknown'
}

const contextPoints = computed(() => getContextPoints())
const contextTripLines = computed(() => getContextTripLines())

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

const previewReconstruction = async () => {
  const { payload, error } = buildValidatedPayload(props.tripId)
  if (error) {
    toast.add({
      severity: 'warn',
      summary: 'Validation Error',
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
      derivedWarnings.push(`Detected ${previewSummary.value.gapCount} uncovered interval(s) totaling ${formatDurationMinutes(previewSummary.value.gapMinutes)}.`)
    }

    previewWarnings.value = [...backendWarnings, ...derivedWarnings]

    toast.add({
      severity: 'success',
      summary: 'Validation Ready',
      detail: `Generated points: ${previewResult.value?.estimatedPoints || 0}. Uncovered intervals: ${previewSummary.value.gapCount}.`,
      life: 2800
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Validation Failed',
      detail: error.response?.data?.message || error.message || 'Request failed.',
      life: 5000
    })
  } finally {
    isPreviewLoading.value = false
  }
}

const commitReconstruction = async () => {
  const { payload, error } = buildValidatedPayload(props.tripId)
  if (error) {
    toast.add({
      severity: 'warn',
      summary: 'Validation Error',
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
        summary: 'Points Saved',
        detail: result.regenerationWarning,
        life: 6000
      })
    } else {
      toast.add({
        severity: 'success',
        summary: 'Missing Data Saved',
        detail: `Inserted ${result?.insertedPoints || 0} GPS points.`,
        life: 3200
      })
    }

    emit('committed', result)
    internalVisible.value = false
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Commit Failed',
      detail: error.response?.data?.message || error.message || 'Request failed.',
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
    return `${safeMinutes} min`
  }

  const hours = Math.floor(safeMinutes / 60)
  const remainder = safeMinutes % 60
  if (remainder === 0) {
    return `${hours} h`
  }

  return `${hours} h ${remainder} min`
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

  nextTick(() => {
    renderActiveViewport()
  })
})

watch(internalVisible, (nextVisible) => {
  if (!nextVisible) {
    previewWarnings.value = []
    emit('close')
  }
})

watch(activeSegmentId, () => {
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

.footer-actions {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--gp-spacing-xs);
  flex-wrap: wrap;
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
