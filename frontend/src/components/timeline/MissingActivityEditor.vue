<template>
  <Dialog :visible="visible" modal class="missing-activity-editor" :header="t(replaceStay ? 'missingActivity.replaceStay' : chosenGap ? 'missingActivity.fillGap' : 'missingActivity.add')"
    :closable="!saving" :close-on-escape="!saving" @update:visible="value => !value && emit('close')">
    <p v-if="contextStartTime || trip?.startTime" class="editor-help">{{ t('missingActivity.dateContext', { date: timezone.formatDateDisplay(contextStartTime || trip.startTime) }) }}</p>
    <p class="editor-help">{{ t(replaceStay ? 'missingActivity.replaceStayHint' : 'missingActivity.help') }}</p>
    <p class="mobile-editor-help">{{ t('missingActivity.mobileHelp') }}</p>
    <label v-if="!replaceStay && gapOptions.length > 1" class="gap-choice">{{ t('missingActivity.chooseGap') }}
      <Select :model-value="chosenGap" :options="gapOptions" option-label="label" option-value="gap"
        :placeholder="t('missingActivity.chooseGap')" :aria-label="t('missingActivity.chooseGap')" @update:model-value="chooseGap" />
    </label>
    <div class="activity-editor-layout" :inert="saving">
      <div ref="formSection" class="activity-editor-form">
        <article v-for="(activity, index) in activities" :key="activity.id" class="activity-form"
          :class="{ selected: activity.id === activeId }" @focusin="activeId = activity.id" @click="activeId = activity.id">
          <header v-if="activities.length > 1">
            <strong>{{ t('missingActivity.activity', { number: index + 1 }) }}</strong>
            <Button v-if="activities.length > 1" icon="pi pi-trash" text size="small" :aria-label="t('missingActivity.remove')"
              @click.stop="removeActivity(index)" />
          </header>
          <div class="activity-times">
            <label>{{ t('missingActivity.from') }}
              <ActivityDateTimeField v-model="activity.start" :label="t('missingActivity.from')" :context-date="contextDate" />
            </label>
            <label>{{ t('missingActivity.to') }}
              <ActivityDateTimeField v-model="activity.end" :label="t('missingActivity.to')" :context-date="contextDate" />
            </label>
          </div>
          <Button v-if="!replaceStay" :label="t('missingActivity.useGps')" icon="pi pi-clock" text size="small"
            :aria-pressed="selectingTimes && activeId === activity.id" @click="toggleGps(activity)" />
          <div v-if="selectingTimes && activeId === activity.id" class="gps-time-choices">
            <p>{{ t('missingActivity.gpsHelp') }}</p>
            <label>{{ t('missingActivity.firstGps') }}
              <Select v-model="firstPoint" :options="pointOptions" option-label="label" option-value="point" :aria-label="t('missingActivity.firstGps')" />
            </label>
            <label>{{ t('missingActivity.secondGps') }}
              <Select v-model="secondPoint" :options="pointOptions" option-label="label" option-value="point" :aria-label="t('missingActivity.secondGps')" />
            </label>
            <p v-if="!recordedPoints.length">{{ t('missingActivity.noPoints') }}</p>
            <Button :label="t('missingActivity.applyTimes')" size="small" :disabled="!gpsRange" @click="applyGpsTimes" />
            <Button :label="t('common.cancel')" text size="small" @click="selectingTimes = false" />
          </div>
          <label v-if="!replaceStay">{{ t('missingActivity.whatHappened') }}
            <Select v-model="activity.segmentType" :options="activityTypes" option-label="label" option-value="value"
              :placeholder="t('missingActivity.chooseActivity')" :aria-label="t('missingActivity.whatHappened')" />
          </label>
          <template v-if="activity.segmentType === 'STAY'">
            <TimelinePlacePicker v-model="activity.place" :allow-map="false" external-map :point="activity.pickedPoint"
              :lookup-at="locationSuggestion || boundaryPlaces.before" @preview="previewPlace(activity, 'previewPlace', $event)"
              @request-map="requestMap('stay', $event)" @update:expanded="value => { if (!value && mapTarget === 'stay') mapTarget = null }" :map-id="`missing-stay-${activity.id}`" />
            <Button v-if="!activity.showVisitLabel && !activity.visitName" :label="t('missingActivity.addVisitLabel')" text size="small" @click="activity.showVisitLabel = true" />
            <label v-else>{{ t('missingActivity.visitName') }}<InputText v-model="activity.visitName" maxlength="500" /></label>
          </template>
          <template v-else-if="activity.segmentType === 'TRIP'">
            <label>{{ t('missingActivity.transport') }}
              <Select v-model="activity.movementType" :options="movementOptions" option-label="label" option-value="value" />
            </label>
            <h4 class="endpoint-heading"><b class="marker-start">A</b> {{ t('missingActivity.origin') }}</h4>
            <TimelinePlacePicker v-model="activity.startPlace" :label="t('missingActivity.origin')" :allow-map="false" external-map :point="activity.startPoint"
              :expanded="activeId === activity.id && activity.choosingPlace === 'start'"
              @update:expanded="expandPlace(activity, 'start', $event)" @request-map="requestMap('start', $event)"
              :lookup-at="boundaryPlaces.before" @preview="previewPlace(activity, 'previewStart', $event)" />
            <h4 class="endpoint-heading"><b class="marker-end">B</b> {{ t('missingActivity.destination') }}</h4>
            <TimelinePlacePicker v-model="activity.endPlace" :label="t('missingActivity.destination')" :allow-map="false" external-map :point="activity.endPoint"
              :expanded="activeId === activity.id && activity.choosingPlace === 'end'"
              @update:expanded="expandPlace(activity, 'end', $event)" @request-map="requestMap('end', $event)"
              :lookup-at="boundaryPlaces.after" @preview="previewPlace(activity, 'previewEnd', $event)" />
            <Button :label="t('missingActivity.addRoutePoints')" icon="pi pi-plus" outlined size="small"
              :disabled="!activity.startPlace || !activity.endPlace || selectingTimes" :aria-pressed="mapTarget === 'via' && activeId === activity.id"
              @click="requestMap('via', $event?.currentTarget)" />
            <ol v-if="activity.via.length" class="route-points">
              <li v-for="(point, pointIndex) in activity.via" :key="pointIndex">
                <Button :label="t('missingActivity.routePoint', { number: pointIndex + 1 })" icon="pi pi-pencil" text size="small"
                  :disabled="selectingTimes" :aria-pressed="mapTarget === pointIndex && activeId === activity.id" @click="requestMap(pointIndex, $event?.currentTarget)" />
                <Button icon="pi pi-arrow-up" text size="small" :disabled="pointIndex === 0 || selectingTimes"
                  :aria-label="t('missingActivity.moveEarlier', { number: pointIndex + 1 })" @click="moveRoutePoint(activity, pointIndex, -1)" />
                <Button icon="pi pi-arrow-down" text size="small" :disabled="pointIndex === activity.via.length - 1 || selectingTimes"
                  :aria-label="t('missingActivity.moveLater', { number: pointIndex + 1 })" @click="moveRoutePoint(activity, pointIndex, 1)" />
                <Button icon="pi pi-times" text size="small" :aria-label="t('missingActivity.removeRoutePoint', { number: pointIndex + 1 })" @click="activity.via.splice(pointIndex, 1); mapTarget = null" />
              </li>
            </ol>
          </template>
        </article>
        <Button v-if="!replaceStay" :label="t('missingActivity.addAnother')" icon="pi pi-plus" outlined size="small" @click="addActivity" />
      </div>
      <div ref="mapSection" class="activity-editor-map" :class="{ 'map-not-needed': !active?.segmentType && !selectingTimes }">
        <p class="map-instruction" role="status">{{ t(selectingTimes ? 'missingActivity.gpsHelp' : mapTarget === 'stay' ? 'timeline.edit.place.mapHint' : mapTarget === 'start' ? 'missingActivity.clickStart' : mapTarget === 'end' ? 'missingActivity.clickEnd' : mapTarget === 'via' ? 'missingActivity.clickRoutePoints' : Number.isInteger(mapTarget) ? 'missingActivity.clickMovePoint' : active?.segmentType === 'TRIP' ? 'missingActivity.routeHelp' : active?.segmentType === 'STAY' ? 'missingActivity.pickStay' : 'missingActivity.mapHelp') }}</p>
        <div class="map-edit-actions">
          <Button v-if="mapTarget !== null" :label="t(mapTarget === 'via' ? 'missingActivity.doneMap' : 'common.back')" :icon="mapTarget === 'via' ? 'pi pi-check' : 'pi pi-arrow-left'" size="small" @click="finishMap" />
          <Button v-if="active?.segmentType === 'TRIP'" :label="t('missingActivity.showRoute')" icon="pi pi-expand" text size="small" :disabled="!mapMarkers.length" @click="focusPlaces()" />
        </div>
        <div class="gps-map-workspace">
          <MapContainer class="activity-editor-map-canvas" :map-id="mapId" :center="initialViewport?.center || fallbackCenter" :zoom="initialViewport?.zoom ?? 13"
            :show-controls="false" :map-options="{ attributionControl: { compact: true } }" height="100%" width="100%" @map-ready="mapReady" @map-click="mapClick">
            <template #overlays="{ map, isReady }">
              <RawGpsPointsLayer v-if="visible && map && isReady" :map="map" :points="recordedPoints" :inspector-container="gpsInspector" :select-point="selectingTimes ? selectGpsPoint : null" />
              <TripReconstructionMapOverlay v-if="map && isReady" :map="map" :context-points="displayMarkers" :context-trip-lines="mapLines" :editable-context="!selectingTimes && !saving" @context-point-dragged="dragPoint" :id-prefix="mapId" />
            </template>
          </MapContainer>
          <div ref="gpsInspector" class="gps-inspector" role="region" :aria-label="t('maps.popups.rawGps.rawGpsPoint')" />
        </div>
        <div v-if="active?.segmentType === 'TRIP' && !selectingTimes" class="map-marker-legend">
          <span><b class="marker-start">A</b> {{ t('missingActivity.origin') }}</span><span><b class="marker-end">B</b> {{ t('missingActivity.destination') }}</span>
        </div>
        <small v-if="chosenGap && (!active?.startPlace || !active?.endPlace) && active?.segmentType === 'TRIP' && !selectingTimes">{{ t('missingActivity.boundaryHint') }}</small>
      </div>
    </div>
    <section v-if="review" class="activity-review" aria-live="polite">
      <h3>{{ t('missingActivity.review') }}</h3>
      <ol><li v-for="(event, index) in review.events" :key="index">
        <strong>{{ event.type === 'DATA_GAP' ? t('missingActivity.unknown') : event.locationName || event.label }}</strong>
        <span>{{ timezone.formatDateTimeDisplay(event.start, { withSeconds: true }) }} — {{ timezone.formatDateTimeDisplay(event.end, { withSeconds: true }) }}</span>
      </li></ol>
      <small>{{ t('missingActivity.reviewHint') }}</small>
    </section>
    <p v-if="error" class="activity-error" role="alert">{{ error }}</p>
    <template #footer>
      <Button :label="t('common.cancel')" text :disabled="saving" @click="emit('close')" />
      <Button v-if="!review" :label="t('missingActivity.review')" :loading="previewing" :disabled="readOnly || saving || selectingTimes" @click="preview" />
      <Button v-else :label="t('missingActivity.save')" :loading="saving" :disabled="readOnly || selectingTimes" @click="save" />
    </template>
  </Dialog>
</template>
<script setup>
import { computed, ref, shallowRef, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import ActivityDateTimeField from './ActivityDateTimeField.vue'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import MapContainer from '@/components/maps/MapContainer.vue'
import RawGpsPointsLayer from '@/components/maps/layers/RawGpsPointsLayer.vue'
import TripReconstructionMapOverlay from '@/components/trips/reconstruction/TripReconstructionMapOverlay.vue'
import TimelinePlacePicker from './TimelinePlacePicker.vue'
import { useTimelineStore } from '@/stores/timeline'
import { useTimezone } from '@/composables/useTimezone'
import { movementTypeOptions } from '@/constants/movementTypes'
import { selectedReconstructionRange, missingActivityBoundaryPlaces, pointsInViewport } from '@/utils/timelineReconstructionContext'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'
import { timelineUtcToPicker, timelinePickerToUtc } from '@/utils/timelineEditTime'
import '@/maps/shared/styles/tripReconstructionMarkers.css'
import { fitMapToBounds, setMapView } from '@/maps/tripReconstruction/shared/tripReconstructionViewport'

const props = defineProps({ visible: Boolean, tripId: { type: Number, default: null }, trip: Object,
  contextStartTime: String, contextEndTime: String, suggestedRange: Object, initialViewport: Object, replaceStay: Object,
  availableGaps: { type: Array, default: () => [] }, timelineData: { type: Array, default: () => [] },
  recordedPoints: { type: Array, default: () => [] }, locationSuggestion: Object,
  fallbackCenter: { type: Array, default: () => [37.7749, -122.4194] }, readOnly: Boolean })
const emit = defineEmits(['close', 'committed'])
const { t } = useI18n()
const timezone = useTimezone()
const store = useTimelineStore()
const chosenGap = ref(null), editorMap = shallowRef(null), mapTarget = ref(null)
const gpsInspector = ref(null)
const mapSection = ref(null), formSection = ref(null), mapReturnTarget = shallowRef(null)
const activities = ref([]), activeId = ref(null), review = ref(null), error = ref('')
const saving = ref(false), previewing = ref(false), selectingTimes = ref(false), firstPoint = ref(null), secondPoint = ref(null)
const mapId = `missing-activity-${Math.random().toString(36).slice(2)}`
let nextId = 0, version = 0
const active = computed(() => activities.value.find(activity => activity.id === activeId.value))
const activityTypes = computed(() => [{ label: t('missingActivity.stayed'), value: 'STAY' }, { label: t('missingActivity.travelled'), value: 'TRIP' }])
const movementOptions = computed(() => movementTypeOptions.map(option => ({ value: option.value, label: t(option.labelKey) })))
const pointOptions = computed(() => props.recordedPoints.map(point => ({ point, label: timezone.formatDateTimeDisplay(point.timestamp, { withSeconds: true }) })))
const gpsRange = computed(() => selectedReconstructionRange(firstPoint.value, secondPoint.value))
const contextDate = computed(() => chosenGap.value?.startTime || props.contextStartTime || props.trip?.startTime)
const gapOptions = computed(() => props.availableGaps.map(gap => ({ gap,
  label: `${timezone.formatDateTimeDisplay(gap.startTime)} — ${timezone.formatDateTimeDisplay(gap.endTime)}` })))
const boundaryPlaces = computed(() => {
  const a = active.value
  const startTime = timelinePickerToUtc(a?.start, timezone, a?.originalStart)
  const endTime = timelinePickerToUtc(a?.end, timezone, a?.originalEnd)
  const interval = startTime && endTime ? { startTime, endTime } : chosenGap.value
  return missingActivityBoundaryPlaces(interval, props.timelineData, props.initialViewport?.allPoints || props.recordedPoints)
})
const mapMarkers = computed(() => {
  const a = active.value
  if (!a?.segmentType) return []
  const marker = (point, label) => Number.isFinite(point?.latitude) && Number.isFinite(point?.longitude)
    ? { latitude: point.latitude, longitude: point.longitude, label } : null
  const stay = a?.place || a?.pickedPoint
  if (a?.segmentType === 'STAY') return [marker(stay, 'S'), ...(!stay ? [marker(props.locationSuggestion || boundaryPlaces.value.before, '•')] : [])].filter(Boolean)
  const start = a?.startPlace || a?.startPoint
  const end = a?.endPlace || a?.endPoint
  const startMarker = marker(start || boundaryPlaces.value.before, 'A')
  const endMarker = marker(end || boundaryPlaces.value.after, 'B')
  if (startMarker && !start) startMarker.draggable = false
  if (endMarker && !end) endMarker.draggable = false
  return [startMarker, ...(a?.via || []).map((point, index) => marker(point, String(index + 1))), endMarker,
    ...(!startMarker && !endMarker && props.locationSuggestion ? [marker(props.locationSuggestion, '•')] : [])].filter(Boolean)
})
const displayMarkers = computed(() => {
  if (selectingTimes.value) return []
  const a = active.value
  const preview = a?.segmentType === 'STAY' ? a.previewPlace : a?.previewStart || a?.previewEnd
  return preview ? [...mapMarkers.value, { latitude: preview.latitude, longitude: preview.longitude, label: '◇', draggable: false }] : mapMarkers.value
})
function previewPlace(activity, field, point) {
  activity[field] = point
  if (point && editorMap.value?.getBounds && !pointsInViewport([point], editorMap.value.getBounds()).length) {
    setMapView(editorMap.value, [point.latitude, point.longitude], editorMap.value.getZoom(), { animate: true })
  }
}
function expandPlace(activity, field, expanded) {
  if (expanded) { activeId.value = activity.id; activity.choosingPlace = field; mapTarget.value = null }
  else if (activity.choosingPlace === field) {
    activity.choosingPlace = null
    if (mapTarget.value === field) mapTarget.value = null
  }
}
const mapLines = computed(() => !selectingTimes.value && active.value?.segmentType === 'TRIP' && mapMarkers.value.length > 1
  ? [mapMarkers.value.map(point => [point.latitude, point.longitude])] : [])
function focusPlaces(onlyIfOutside = false) {
  const map = editorMap.value, points = mapMarkers.value
  if (!map || !points.length || selectingTimes.value) return
  if (onlyIfOutside && map.getBounds && pointsInViewport(points, map.getBounds()).length === points.length) return
  if (points.length === 1) setMapView(map, [points[0].latitude, points[0].longitude], 15, { animate: true })
  else fitMapToBounds(map, points.map(point => [point.latitude, point.longitude]), { padding: [40, 40], maxZoom: 16 })
}
watch(() => [activeId.value, active.value?.segmentType, ...mapMarkers.value.filter(point => ['A', 'B', 'S'].includes(point.label)).map(point => `${point.latitude}:${point.longitude}`)].join('|'), () => nextTick(() => { if (!mapTarget.value) focusPlaces(true) }))
function chooseGap(gap) {
  chosenGap.value = gap
  if (!gap || !active.value) return
  active.value.originalStart = gap.startTime; active.value.originalEnd = gap.endTime
  active.value.start = timelineUtcToPicker(gap.startTime, timezone); active.value.end = timelineUtcToPicker(gap.endTime, timezone)
}
function addActivity() {
  const previous = activities.value.at(-1)
  const activity = { id: ++nextId, segmentType: null, start: previous?.end || null, end: null, place: null,
    visitName: '', showVisitLabel: false, choosingPlace: 'start', startInvalid: false, endInvalid: false, originalStart: previous?.originalEnd || null, originalEnd: null, movementType: 'UNKNOWN', startPlace: null, endPlace: null, via: [], pickedPoint: null, startPoint: null, endPoint: null, previewPlace: null, previewStart: null, previewEnd: null }
  activities.value.push(activity); activeId.value = activity.id
}
function removeActivity(index) { activities.value.splice(index, 1); activeId.value = activities.value[0]?.id }
watch(() => props.visible, visible => {
  if (!visible) return
  version++; activities.value = []; addActivity(); review.value = null; error.value = ''; selectingTimes.value = false
  if (props.replaceStay) {
    chooseGap({ startTime: props.replaceStay.anchorStart, endTime: props.replaceStay.anchorEnd })
    active.value.segmentType = 'TRIP'
    return
  }
  const supplied = props.suggestedRange
  chooseGap(supplied ? { ...props.availableGaps.find(gap => gap.id != null && gap.id === supplied.id), ...supplied }
    : props.availableGaps.length === 1 ? props.availableGaps[0] : null)
}, { immediate: true })
watch(activeId, () => { mapTarget.value = null; selectingTimes.value = false; firstPoint.value = null; secondPoint.value = null }, { flush: 'sync' })
watch(() => active.value?.segmentType, () => { mapTarget.value = null })
watch(() => activities.value.map(({ segmentType, start, end, originalStart, originalEnd, place, visitName, movementType, startPlace, endPlace, via }) =>
  ({ segmentType, start, end, originalStart, originalEnd, place, visitName, movementType, startPlace, endPlace, via })),
  () => { version++; review.value = null }, { deep: true, flush: 'sync' })
function mapReady(map) {
  editorMap.value = map
  map.getContainer?.().querySelector('.maplibregl-ctrl-attrib.maplibregl-compact-show summary')?.click()
  if (props.initialViewport) map.setView(props.initialViewport.center, props.initialViewport.zoom, { animate: false })
  nextTick(() => focusPlaces(true))
}
function requestMap(target, origin = null) {
  mapTarget.value = target
  mapReturnTarget.value = origin
  if (window.matchMedia?.('(max-width: 768px)')?.matches)
    nextTick(() => mapSection.value?.scrollIntoView?.({ block: 'start', behavior: 'auto' }))
}
function returnToPlace() {
  const target = mapReturnTarget.value || formSection.value
  if (window.matchMedia?.('(max-width: 768px)')?.matches)
    nextTick(() => target?.scrollIntoView?.({ block: 'center', behavior: 'auto' }))
  mapReturnTarget.value = null
}
function finishMap() { mapTarget.value = null; returnToPlace() }
function mapClick(event) {
  if (selectingTimes.value || !active.value) return
  // Vector layer clicks also reach the map handler before the layer's click handler.
  if (event?.point && editorMap.value?.queryRenderedFeatures?.(event.point)
    .some(feature => feature.properties?.groupRaw || feature.properties?.raw_count != null)) return
  const point = event?.latlng || event?.lngLat || event
  const latitude = point?.lat, longitude = point?.lng
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return
  const placing = mapTarget.value
  if (active.value.segmentType === 'STAY') { active.value.pickedPoint = { latitude, longitude }; active.value.previewPlace = null }
  else if (active.value.segmentType === 'TRIP') {
    if (mapTarget.value === 'start' || mapTarget.value === 'end') {
      const prefix = mapTarget.value
      active.value[`${prefix}Point`] = { latitude, longitude }
      active.value[prefix === 'start' ? 'previewStart' : 'previewEnd'] = null
      mapTarget.value = null
    } else if (mapTarget.value === 'via') active.value.via.push({ latitude, longitude })
    else if (Number.isInteger(mapTarget.value)) {
      active.value.via[mapTarget.value] = { latitude, longitude }
      mapTarget.value = null
    }
  }
  if (placing !== null && placing !== 'via') returnToPlace()
}
function dragPoint({ label, latitude, longitude }) {
  if (!active.value || selectingTimes.value || saving.value) return
  const point = { latitude, longitude }
  if (label === 'S') {
    active.value.place = null
    active.value.pickedPoint = point
    active.value.previewPlace = null
  } else if (label === 'A' || label === 'B') {
    const start = label === 'A'
    active.value[start ? 'startPlace' : 'endPlace'] = null
    active.value[start ? 'startPoint' : 'endPoint'] = point
    active.value[start ? 'previewStart' : 'previewEnd'] = null
  } else if (/^\d+$/.test(label) && active.value.via[Number(label) - 1]) {
    active.value.via[Number(label) - 1] = point
  }
}
function moveRoutePoint(activity, index, direction) {
  const [point] = activity.via.splice(index, 1)
  activity.via.splice(index + direction, 0, point)
  mapTarget.value = null
}
function toggleGps(activity) { mapTarget.value = null; activeId.value = activity.id; selectingTimes.value = !selectingTimes.value; firstPoint.value = null; secondPoint.value = null }
function selectGpsPoint(point) { if (!selectingTimes.value) return; if (!firstPoint.value || secondPoint.value) { firstPoint.value = point; secondPoint.value = null } else secondPoint.value = point }
function applyGpsTimes() {
  if (!gpsRange.value || !active.value) return
  active.value.originalStart = gpsRange.value.startTime; active.value.originalEnd = gpsRange.value.endTime
  active.value.start = timelineUtcToPicker(gpsRange.value.startTime, timezone); active.value.end = timelineUtcToPicker(gpsRange.value.endTime, timezone)
  active.value.startInvalid = false; active.value.endInvalid = false; selectingTimes.value = false
}
function payload() {
  return { tripId: props.tripId, ...(props.replaceStay ? { replaceStayEditId: props.replaceStay.id } : {}), contextStart: chosenGap.value?.startTime, contextEnd: chosenGap.value?.endTime,
    activities: activities.value.map(activity => {
      const startTime = timelinePickerToUtc(activity.start, timezone, activity.originalStart)
      const endTime = timelinePickerToUtc(activity.end, timezone, activity.originalEnd)
      if (!activity.segmentType || activity.startInvalid || activity.endInvalid || !startTime || !endTime || Date.parse(endTime) <= Date.parse(startTime)) throw new Error(t('missingActivity.invalid'))
      if (activity.segmentType === 'STAY' && !activity.place) throw new Error(t('missingActivity.choosePlace'))
      if (activity.segmentType === 'TRIP' && (!activity.startPlace || !activity.endPlace)) throw new Error(t('missingActivity.chooseEndpoints'))
      return { segmentType: activity.segmentType, startTime, endTime,
        favoriteId: activity.place?.favoriteId, geocodingId: activity.place?.geocodingId,
        latitude: activity.place?.latitude, longitude: activity.place?.longitude,
        locationName: activity.place?.locationName, visitName: activity.visitName.trim() || null,
        movementType: activity.movementType, startPlace: activity.startPlace, endPlace: activity.endPlace, waypoints: activity.via }
    }) }
}
async function preview() {
  error.value = ''; const currentVersion = version; previewing.value = true
  try { const result = await store.previewActivityCorrection(payload()); if (currentVersion === version) review.value = result }
  catch (problem) { error.value = formatApiErrorDetail(problem, t('missingActivity.failed')) }
  finally { previewing.value = false }
}
async function save() {
  if (!review.value || props.readOnly || saving.value) return
  error.value = ''; saving.value = true
  try { const result = await store.saveActivityCorrection(payload()); emit('committed', result) }
  catch (problem) { error.value = formatApiErrorDetail(problem, t('missingActivity.failed')) }
  finally { saving.value = false }
}
</script>
<style>
.missing-activity-editor { --missing-activity-map-height: clamp(24rem, calc(100dvh - 19rem), 38rem); width: min(90rem, 96vw); max-height: 94dvh; }
@media (max-width: 768px) {
  .missing-activity-editor { --missing-activity-map-height: clamp(220px, 32dvh, 280px); width: 100vw; height: 100dvh; max-height: 100dvh; margin: 0; border-radius: 0; }
  .missing-activity-editor .p-dialog-header { padding: .75rem 1rem; }
  .missing-activity-editor .p-dialog-content { padding: 0 1rem .75rem; flex: 1; min-height: 0; }
  .missing-activity-editor .p-dialog-footer { padding: .75rem 1rem max(.75rem, env(safe-area-inset-bottom)); gap: .5rem; }
  .missing-activity-editor .p-dialog-footer .p-button { min-height: 44px; }
}
</style>
<style scoped>
.mobile-editor-help { display: none; }
.editor-help { color: var(--gp-text-secondary); margin: 0 0 1rem; line-height: 1.5; }
.activity-editor-layout { display: grid; grid-template-columns: minmax(19rem, .8fr) minmax(0, 1.8fr); gap: 1rem; }
.activity-editor-form { display: grid; gap: .875rem; align-content: start; }
.activity-form { border: 1px solid var(--gp-border); padding: 1rem; border-radius: var(--gp-radius-medium); display: grid; gap: .75rem; }
.activity-form.selected { border-color: var(--gp-primary); }
.activity-form header { display: flex; justify-content: space-between; align-items: center; }
.activity-times { display: grid; grid-template-columns: 1fr 1fr; gap: .75rem; }
label { display: grid; gap: .375rem; font-size: .875rem; font-weight: 600; min-width: 0; }
label :deep(input), label :deep(.p-select), label :deep(.p-datepicker) { width: 100%; min-width: 0; }
.map-instruction, small, .gps-time-choices p { font-size: .8rem; color: var(--gp-text-secondary); line-height: 1.5; }
h4 { margin: .25rem 0 0; font-size: .875rem; }
.gps-time-choices { display: grid; gap: .5rem; padding: .75rem; background: var(--gp-surface-ground); border-radius: var(--gp-radius-small); }
.gps-map-workspace { display: grid; grid-template-columns: minmax(0, 1fr); gap: .75rem; height: var(--missing-activity-map-height); min-height: 0; }
.gps-map-workspace:has(.gps-inspector:not(:empty)) { grid-template-columns: minmax(0, 1.2fr) minmax(16rem, .8fr); }
.activity-editor-map-canvas { height: 100%; min-width: 0; min-height: 0; }
.activity-editor-map-canvas :deep(.base-map) { min-height: 0; }
.gps-inspector:empty { display: none; }
.gps-inspector { min-width: 0; min-height: 0; overflow: hidden; background: var(--gp-map-popup-background); border: 1px solid var(--gp-map-popup-border); border-radius: var(--gp-radius-medium); }
.gps-inspector :deep([data-v-app]) { height: 100%; }
.gps-inspector :deep(.raw-gps-popup) { width: 100%; min-width: 0; max-width: 100%; height: 100%; max-height: none; overflow: hidden; padding: .75rem; }
.gps-inspector :deep(.gp-map-popup-header), .gps-inspector :deep(.raw-gps-point-details), .gps-inspector :deep(.p-button) { flex-shrink: 0; }
.gps-inspector :deep(.raw-gps-point-details) { margin-top: .5rem; }
.gps-inspector :deep(summary) { cursor: pointer; padding: .375rem 0; font-weight: 600; }
.gps-inspector :deep(.raw-gps-popup-grid) { grid-template-columns: minmax(0, 1fr) auto; gap: .35rem .5rem; margin-top: .5rem; }
.gps-inspector :deep(.p-button) { min-height: 2.5rem; padding: .5rem .75rem; }
.gps-inspector :deep(.raw-gps-popup-location) { display: none; }
.gps-inspector :deep(.raw-gps-stack-list) { max-height: none; overflow: auto; }
.gps-inspector :deep(.raw-gps-stack-row) { grid-template-columns: minmax(0, 1fr); gap: .25rem; }
.gps-inspector :deep(.raw-gps-stack-telemetry) { justify-content: flex-start; flex-wrap: wrap; }
@media (max-width: 1100px) {
  .gps-map-workspace:has(.gps-inspector:not(:empty)) { grid-template-columns: minmax(0, 1fr); grid-template-rows: minmax(0, 1fr) minmax(0, 1fr); height: min(40rem, calc(100dvh - 16rem)); }
  .gps-map-workspace:has(.gps-inspector details[open]) { grid-template-rows: minmax(7rem, .5fr) minmax(0, 1.5fr); }
}
.activity-editor-map { min-width: 0; align-self: start; position: sticky; top: 0; }
.activity-review { margin-top: 1rem; padding: 1rem; background: var(--gp-surface-ground); border-radius: var(--gp-radius-medium); }
.activity-review li { display: grid; gap: .25rem; margin: .75rem 0; }
.activity-review span { color: var(--gp-text-secondary); font-size: .85rem; }
.activity-error { color: var(--gp-danger); }
.route-points { padding-left: 1.5rem; }
@media (max-width: 768px) {
  .editor-help { display: none; }
  .mobile-editor-help { display: block; margin: 0 0 .75rem; font-size: .85rem; color: var(--gp-text-secondary); }
  .activity-editor-layout { grid-template-columns: 1fr; gap: .75rem; }
  .activity-editor-form { gap: .75rem; }
  .activity-form { padding: .75rem; gap: .5rem; }
  .activity-form:only-of-type { border: 0; padding: 0; }
  .activity-times { grid-template-columns: 1fr; gap: .5rem; }
  .activity-times :deep(.activity-date-time-field) { grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr); }
  .activity-form :deep(input) { font-size: 16px; }
  .activity-editor-map { position: static; scroll-margin-top: .25rem; }
  .activity-editor-map.map-not-needed { display: none; }
  .activity-editor-map :deep(.base-map) { min-height: 0; }
  .map-instruction { margin: 0 0 .5rem; }
}
</style>

<style scoped>
.gap-choice { margin-bottom: 1rem; }
.map-marker-legend { display: flex; gap: 1rem; margin-top: .5rem; color: var(--gp-text-secondary); font-size: .85rem; }
.map-marker-legend b, .endpoint-heading b { display: inline-grid; place-items: center; min-width: 1.5rem; height: 1.5rem; color: white; margin-right: .25rem; }
</style>

<style scoped>
.endpoint-heading { display: flex; align-items: center; gap: .4rem; }
.map-edit-actions { display: flex; gap: .5rem; margin-bottom: .5rem; }
.route-points li { display: flex; align-items: center; flex-wrap: wrap; }
.marker-start { background: #15803d; border-radius: 50%; }
.marker-end { background: #b91c1c; border-radius: .3rem; }
</style>
