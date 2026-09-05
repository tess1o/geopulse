<template>
  <Dialog
    v-model:visible="internalVisible"
    header="Split Trip with Stay"
    :modal="true"
    class="gp-dialog-lg trip-stay-split-dialog"
    @hide="$emit('close')"
  >
    <div v-if="trip" class="split-content">
      <div class="trip-meta">
        <Tag value="Trip" severity="success" />
        <span>{{ formatTripRange }}</span>
      </div>

      <Message v-if="pathError" severity="error" :closable="false">{{ pathError }}</Message>
      <Message v-else-if="pathLoading" severity="info" :closable="false">Loading trip path...</Message>

      <div class="split-workspace">
        <div class="split-details">
          <Message v-if="!selectedPoint && !selectionError" severity="info" :closable="false">
            Click on the route to set your stay location.
          </Message>
          <Message v-if="selectionError" severity="warn" :closable="false">{{ selectionError }}</Message>

          <template v-if="selectedPoint">
            <div class="selection-row">
              <span>{{ formatCoordinate(selectedPoint.latitude) }}, {{ formatCoordinate(selectedPoint.longitude) }}</span>
              <span v-if="selectedDistanceMeters !== null">{{ formatDistance(selectedDistanceMeters) }}</span>
            </div>

            <div class="time-grid">
              <div class="field">
                <label for="split-stay-start">Stay start</label>
                <DatePicker
                  id="split-stay-start"
                  v-model="stayStart"
                  showTime
                  showSeconds
                  :dateFormat="timezone.getPrimeVueDatePickerFormat()"
                  :hourFormat="timezone.getTimeFormat() === '12h' ? '12' : '24'"
                  :disabled="saving"
                />
              </div>
              <div class="field">
                <label for="split-stay-end">Stay end</label>
                <DatePicker
                  id="split-stay-end"
                  v-model="stayEnd"
                  showTime
                  showSeconds
                  :dateFormat="timezone.getPrimeVueDatePickerFormat()"
                  :hourFormat="timezone.getTimeFormat() === '12h' ? '12' : '24'"
                  :disabled="saving"
                />
              </div>
            </div>

            <div class="field">
              <label for="split-location-name">Place name</label>
              <InputText
                id="split-location-name"
                v-model.trim="locationName"
                maxlength="500"
                :placeholder="resolvingPlaceName ? 'Resolving place...' : 'Resolved automatically when empty'"
                :disabled="saving"
                @input="locationNameEdited = true"
              />
            </div>

            <Message v-if="validationError" severity="error" :closable="false">{{ validationError }}</Message>
            <Message v-else-if="previewError" severity="error" :closable="false">{{ previewError }}</Message>
            <Message v-else-if="previewing" severity="info" :closable="false">Updating split result...</Message>

            <Transition name="preview">
              <section v-if="preview" class="split-preview" aria-live="polite">
                <div class="preview-original">
                  <span class="preview-icon preview-icon--original">
                    <i class="pi pi-clock" aria-hidden="true"></i>
                  </span>
                  <div class="preview-copy">
                    <span class="preview-label">Original trip</span>
                    <strong>{{ formatDuration(originalTripDurationSeconds) }}</strong>
                  </div>
                </div>

                <div class="preview-section-title">Split result</div>

                <ol class="preview-steps">
                  <li class="preview-step">
                    <span class="preview-icon preview-icon--trip">
                      <i class="pi pi-arrow-right" aria-hidden="true"></i>
                    </span>
                    <div class="preview-copy">
                      <strong>Trip for {{ formatDuration(preview.firstTrip?.tripDuration || 0) }}</strong>
                      <span>to {{ previewLocationName }}</span>
                    </div>
                  </li>
                  <li class="preview-step">
                    <span class="preview-icon preview-icon--stay">
                      <i class="pi pi-map-marker" aria-hidden="true"></i>
                    </span>
                    <div class="preview-copy">
                      <strong>Stay at {{ previewLocationName }}</strong>
                      <span>for {{ formatDuration(previewStayDurationSeconds) }}</span>
                    </div>
                  </li>
                  <li class="preview-step">
                    <span class="preview-icon preview-icon--trip">
                      <i class="pi pi-arrow-right" aria-hidden="true"></i>
                    </span>
                    <div class="preview-copy">
                      <strong>Trip for {{ formatDuration(preview.secondTrip?.tripDuration || 0) }}</strong>
                      <span>from {{ previewLocationName }}</span>
                    </div>
                  </li>
                </ol>
              </section>
            </Transition>
          </template>
        </div>

        <div class="split-map">
          <MapContainer
            ref="mapRef"
            :map-id="mapId"
            :center="mapCenter"
            :zoom="13"
            :show-controls="false"
            :enable-fullscreen="false"
            height="100%"
            width="100%"
            @map-ready="handleMapReady"
            @map-click="handleMapClick"
          >
            <template #overlays="{ map }">
              <PathLayer
                v-if="map && pathSegments.length"
                :map="map"
                :path-data="pathSegments"
                :path-options="pathOptions"
                :inspection-enabled="false"
                :show-highlighted-trip-popup="false"
              />
              <TripReconstructionMapOverlay
                v-if="map && selectedPoint"
                :map="map"
                :active-segment="activeStaySegment"
                :active-segment-index="0"
                :id-prefix="mapId"
                @stay-dragged="handleStayDragged"
              />
            </template>
          </MapContainer>
        </div>
      </div>
    </div>

    <template #footer>
      <Button label="Cancel" severity="secondary" outlined :disabled="saving" @click="internalVisible = false" />
      <Button label="Save Split" icon="pi pi-check" :disabled="!canSubmit" :loading="saving" @click="saveSplit" />
    </template>
  </Dialog>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import DatePicker from 'primevue/datepicker'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { MapContainer, PathLayer } from '@/components/maps'
import TripReconstructionMapOverlay from '@/components/trips/reconstruction/TripReconstructionMapOverlay.vue'
import { formatDuration } from '@/utils/calculationsHelpers'
import { useTimelineStore } from '@/stores/timeline'
import { useTimelinePreferencesStore } from '@/stores/timelinePreferences'
import { useTripsStore } from '@/stores/trips'
import { useTimezone } from '@/composables/useTimezone'
import { normalizeLatLngPoint } from '@/maps/shared/coordinateUtils'
import {
  normalizeTripPathPoints,
  normalizeTripPathSegments,
  resolveSplitSelectionRadiusMeters,
  resolveStayWindowFromSelection,
  validateStayWindowSelection
} from '@/utils/tripStaySplitSelection'
import '@/maps/shared/styles/tripReconstructionMarkers.css'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  trip: {
    type: Object,
    default: null
  },
  readOnly: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['close', 'split'])

const toast = useToast()
const timelineStore = useTimelineStore()
const timelinePreferencesStore = useTimelinePreferencesStore()
const tripsStore = useTripsStore()
const timezone = useTimezone()

const stayStart = ref(null)
const stayEnd = ref(null)
const locationName = ref('')
const locationNameEdited = ref(false)
const pathPoints = ref([])
const pathSegments = ref([])
const selectedPoint = ref(null)
const selectedDistanceMeters = ref(null)
const preview = ref(null)
const pathLoading = ref(false)
const previewing = ref(false)
const saving = ref(false)
const resolvingPlaceName = ref(false)
const pathError = ref('')
const selectionError = ref('')
const previewError = ref('')
const mapRef = ref(null)
let placeRequestToken = 0
let previewRequestToken = 0
let previewTimer = null

const internalVisible = computed({
  get: () => props.visible,
  set: (value) => {
    if (!value) emit('close')
  }
})

const mapId = computed(() => `trip-stay-split-map-${props.trip?.id || 'new'}`)

const tripStart = computed(() => props.trip?.timestamp || null)
const tripEnd = computed(() => {
  if (!props.trip?.timestamp || !props.trip?.tripDuration) return null
  return timezone.fromUtc(props.trip.timestamp).add(props.trip.tripDuration, 'second').utc().toISOString()
})

const splitRadiusMeters = computed(() => (
  resolveSplitSelectionRadiusMeters(timelinePreferencesStore.timelinePreferences)
))

const formatTripRange = computed(() => {
  if (!tripStart.value || !tripEnd.value) return 'Unknown time range'
  return `${formatDateTime(tripStart.value)} - ${formatDateTime(tripEnd.value)}`
})

const mapCenter = computed(() => {
  if (selectedPoint.value) return [selectedPoint.value.latitude, selectedPoint.value.longitude]
  if (pathPoints.value.length > 0) {
    const point = pathPoints.value[Math.floor(pathPoints.value.length / 2)]
    return [point.latitude, point.longitude]
  }
  return [51.505, -0.09]
})

const pathOptions = {
  color: '#f59e0b',
  weight: 5,
  opacity: 0.9,
  smoothFactor: 1
}

const activeStaySegment = computed(() => selectedPoint.value
  ? {
      id: 'split-stay',
      segmentType: 'STAY',
      latitude: selectedPoint.value.latitude,
      longitude: selectedPoint.value.longitude,
      locationName: locationName.value || 'Selected stay'
    }
  : null)

const validationError = computed(() => {
  if (!props.trip?.id) return 'Trip is missing.'
  if (props.readOnly) return 'Timeline edits are disabled.'
  if (!selectedPoint.value) return 'Select the stay location on the trip map.'
  const range = selectedRange()
  if (!range.start || !range.end) return 'Select valid start and end times.'
  if (new Date(range.end) <= new Date(range.start)) return 'Stay end must be after stay start.'
  if (new Date(range.end).getTime() - new Date(range.start).getTime() < 60_000) return 'Stay duration must be at least 60 seconds.'
  if (tripStart.value && new Date(range.start) <= new Date(tripStart.value)) return 'Stay must start after the trip starts.'
  if (tripEnd.value && new Date(range.end) >= new Date(tripEnd.value)) return 'Stay must end before the trip ends.'
  const selectionValidation = validateStayWindowSelection(pathPoints.value, selectedPoint.value, range, {
    radiusMeters: splitRadiusMeters.value,
    tripStart: tripStart.value,
    tripEnd: tripEnd.value
  })
  if (selectionValidation.error) return selectionValidation.error
  if (locationName.value && locationName.value.length > 500) return 'Place name is too long.'
  return ''
})

const canSubmit = computed(() => !validationError.value && !!preview.value && !saving.value && !previewing.value)

const originalTripDurationSeconds = computed(() => {
  const duration = Number(props.trip?.tripDuration)
  return Number.isFinite(duration) && duration >= 0 ? duration : 0
})

const previewLocationName = computed(() => (
  preview.value?.locationName || locationName.value || 'selected place'
))

const previewStayDurationSeconds = computed(() => {
  const directDuration = Number(preview.value?.stay?.stayDuration ?? preview.value?.stayDuration)
  if (Number.isFinite(directDuration) && directDuration >= 0) return directDuration

  const start = Date.parse(preview.value?.stayStartTime)
  const end = Date.parse(preview.value?.stayEndTime)
  if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) return 0
  return Math.round((end - start) / 1000)
})

watch(
  () => [props.visible, props.trip?.id],
  async ([visible]) => {
    if (!visible || !props.trip?.id) return
    resetForm()
    await loadPath()
  },
  { immediate: true }
)

watch([selectedPoint, stayStart, stayEnd, locationName], () => {
  schedulePreview()
})

const resetForm = () => {
  if (previewTimer) clearTimeout(previewTimer)
  previewTimer = null
  placeRequestToken++
  previewRequestToken++
  pathPoints.value = []
  pathSegments.value = []
  selectedPoint.value = null
  selectedDistanceMeters.value = null
  preview.value = null
  pathError.value = ''
  selectionError.value = ''
  previewError.value = ''
  previewing.value = false
  resolvingPlaceName.value = false
  locationName.value = ''
  locationNameEdited.value = false
  stayStart.value = null
  stayEnd.value = null
}

const loadPath = async () => {
  if (!tripStart.value || !tripEnd.value) return
  pathLoading.value = true
  try {
    if (!timelinePreferencesStore.hasPreferences) {
      try {
        await timelinePreferencesStore.fetchTimelinePreferences()
      } catch {
        // Fall back to the default 150m snap radius.
      }
    }

    const path = await timelineStore.fetchTripPath(tripStart.value, tripEnd.value, { simplify: false })
    pathPoints.value = normalizeTripPathPoints(path?.points || [])
    pathSegments.value = normalizeTripPathSegments(path?.segments || [], path?.points || [])
    if (pathPoints.value.length === 0) {
      pathError.value = 'No path points available for this trip.'
    }
    await nextTick()
    fitTripPath()
  } catch (error) {
    pathError.value = error.response?.data?.message || error.message || 'Failed to load trip path'
  } finally {
    pathLoading.value = false
  }
}

const handleMapReady = () => {
  nextTick(() => fitTripPath())
}

const handleMapClick = (event) => {
  applyMapSelection(event?.latlng || event?.lngLat || event)
}

const handleStayDragged = ({ latitude, longitude }) => {
  applyMapSelection({ latitude, longitude })
}

const applyMapSelection = (rawCoordinate) => {
  const coordinate = normalizeLatLngPoint(rawCoordinate)
  if (!coordinate) {
    selectionError.value = 'Select a valid stay location.'
    return
  }

  const result = resolveStayWindowFromSelection(pathPoints.value, coordinate, {
    radiusMeters: splitRadiusMeters.value,
    tripStart: tripStart.value,
    tripEnd: tripEnd.value
  })

  if (result.error) {
    if (previewTimer) clearTimeout(previewTimer)
    previewTimer = null
    placeRequestToken++
    previewRequestToken++
    selectionError.value = result.error
    selectedPoint.value = null
    selectedDistanceMeters.value = Number.isFinite(result.nearestDistanceMeters) ? result.nearestDistanceMeters : null
    stayStart.value = null
    stayEnd.value = null
    locationName.value = ''
    locationNameEdited.value = false
    preview.value = null
    previewError.value = ''
    previewing.value = false
    resolvingPlaceName.value = false
    return
  }

  selectedPoint.value = result.point
  selectedDistanceMeters.value = result.nearestDistanceMeters
  stayStart.value = toPickerDate(result.startTime)
  stayEnd.value = toPickerDate(result.endTime)
  selectionError.value = ''
  resolveLocationName(result.point)
}

const resolveLocationName = async (point) => {
  if (!point || locationNameEdited.value) return
  const token = ++placeRequestToken
  resolvingPlaceName.value = true
  try {
    const suggestion = await tripsStore.getPlanSuggestion(point.latitude, point.longitude)
    if (token !== placeRequestToken || locationNameEdited.value) return
    locationName.value = suggestion?.title?.trim() || ''
  } catch {
    // Non-blocking. The split can still be saved with coordinates.
  } finally {
    if (token === placeRequestToken) resolvingPlaceName.value = false
  }
}

const fitTripPath = () => {
  if (!mapRef.value || pathPoints.value.length === 0) return
  mapRef.value.invalidateSize?.()
  const bounds = pathPoints.value.map((point) => [point.latitude, point.longitude])
  if (bounds.length === 1) {
    mapRef.value.setView?.(bounds[0], 15)
    return
  }
  mapRef.value.fitBounds?.(bounds, { padding: [30, 30], maxZoom: 16 })
}

const schedulePreview = () => {
  if (previewTimer) clearTimeout(previewTimer)
  const token = ++previewRequestToken
  preview.value = null
  previewError.value = ''

  if (!props.visible || !selectedPoint.value || validationError.value) {
    previewing.value = false
    return
  }

  previewing.value = true
  previewTimer = setTimeout(() => loadPreview(token), 250)
}

const loadPreview = async (token) => {
  try {
    const result = await timelineStore.previewTripStaySplit(props.trip.id, buildPayload())
    if (token !== previewRequestToken) return
    preview.value = result
  } catch (error) {
    if (token !== previewRequestToken) return
    previewError.value = error.response?.data?.message || error.message || 'Could not preview trip split'
  } finally {
    if (token === previewRequestToken) previewing.value = false
  }
}

const saveSplit = async () => {
  if (!canSubmit.value) return
  saving.value = true
  try {
    const result = await timelineStore.splitTripWithStay(props.trip.id, buildPayload())
    toast.add({ severity: 'success', summary: 'Trip Split', detail: 'Stay was inserted into the trip.', life: 3000 })
    emit('split', result)
    internalVisible.value = false
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Split Failed',
      detail: error.response?.data?.message || error.message || 'Could not split trip',
      life: 5000
    })
  } finally {
    saving.value = false
  }
}

const buildPayload = () => {
  const range = selectedRange()
  const payload = {
    stayStartTime: range.start,
    stayEndTime: range.end,
    anchorTimestamp: selectedPoint.value.timestamp,
    latitude: selectedPoint.value.latitude,
    longitude: selectedPoint.value.longitude
  }
  if (locationName.value) {
    payload.locationName = locationName.value
  }
  return payload
}

const selectedRange = () => {
  const range = timezone.createDateTimeRangeFromPicker(stayStart.value, stayEnd.value)
  return { start: range.start, end: range.end }
}

const toPickerDate = (utcValue) => {
  if (!utcValue) return null
  const value = timezone.fromUtc(utcValue)
  return new Date(value.year(), value.month(), value.date(), value.hour(), value.minute(), value.second())
}

const formatDateTime = (value) => `${timezone.formatDateDisplay(value)} ${timezone.formatTime(value)}`
const formatCoordinate = (value) => Number.isFinite(value) ? value.toFixed(5) : ''
const formatDistance = (value) => value < 1000 ? `${Math.round(value)}m from click` : `${(value / 1000).toFixed(1)}km from click`
</script>

<style scoped>
.split-content {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
}

.trip-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--gp-spacing-xs);
  color: var(--gp-text-secondary);
  font-size: 0.875rem;
}

.split-workspace {
  display: grid;
  grid-template-columns: minmax(0, 4fr) minmax(0, 8fr);
  gap: var(--gp-spacing-md);
  align-items: start;
}

.split-details {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
}

.split-map {
  height: clamp(360px, 48vh, 460px);
  overflow: hidden;
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-small);
}

.split-map :deep(.trip-reconstruction-waypoint-icon--stay),
.split-map :deep(.trip-reconstruction-waypoint-marker--stay) {
  width: 42px;
  height: 42px;
  background: #2563eb;
  border: 3px solid #ffffff;
  box-shadow:
    0 0 0 4px rgba(37, 99, 235, 0.28),
    0 8px 22px rgba(15, 23, 42, 0.35);
  font-size: 1rem;
}

.selection-row {
  min-height: 1.25rem;
  display: flex;
  flex-wrap: wrap;
  gap: var(--gp-spacing-sm);
  color: var(--gp-text-secondary);
  font-size: 0.875rem;
}

.time-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--gp-spacing-md);
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-xs);
}

.field label {
  color: var(--gp-text-primary);
  font-weight: 600;
  font-size: 0.875rem;
}

.split-preview {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
  padding: var(--gp-spacing-md);
  border: 1px solid rgba(34, 197, 94, 0.35);
  border-radius: var(--gp-radius-small);
  background: rgba(34, 197, 94, 0.08);
  color: var(--gp-text-primary);
}

.preview-original,
.preview-step {
  display: flex;
  align-items: flex-start;
  gap: var(--gp-spacing-sm);
}

.preview-original {
  padding-bottom: var(--gp-spacing-sm);
  border-bottom: 1px solid var(--gp-border-light);
}

.preview-section-title {
  color: var(--gp-text-secondary);
  font-size: 0.8rem;
  font-weight: 700;
  text-transform: uppercase;
}

.preview-steps {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
  margin: 0;
  padding: 0;
  list-style: none;
}

.preview-icon {
  width: 2rem;
  height: 2rem;
  flex: 0 0 2rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  color: #ffffff;
}

.preview-icon--original {
  background: #64748b;
}

.preview-icon--trip {
  background: #2563eb;
}

.preview-icon--stay {
  background: #16a34a;
}

.preview-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.preview-copy strong,
.preview-copy span {
  overflow-wrap: anywhere;
}

.preview-copy span,
.preview-label {
  color: var(--gp-text-secondary);
}

.preview-enter-active,
.preview-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.preview-enter-from,
.preview-leave-to {
  opacity: 0;
  transform: translateY(-0.25rem);
}

:global(.trip-stay-split-dialog) {
  width: 92vw !important;
  max-width: 1200px !important;
}

@media (max-width: 767px) {
  .split-workspace {
    grid-template-columns: 1fr;
  }

  .split-map {
    order: -1;
    height: clamp(220px, 40vh, 320px);
  }

  .time-grid {
    grid-template-columns: 1fr;
  }
}
</style>
