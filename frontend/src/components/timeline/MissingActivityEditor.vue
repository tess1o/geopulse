<template>
  <Dialog :visible="visible" modal class="missing-activity-editor" :header="t(routeTarget ? 'missingActivity.editRoute' : rebuildRange ? 'missingActivity.rebuildInterval' : chosenGap ? 'missingActivity.fillGap' : 'missingActivity.add')"
    :closable="!saving" :close-on-escape="!saving" @update:visible="value => !value && emit('close')">
    <p v-if="contextStartTime || trip?.startTime" class="editor-help">{{ t('missingActivity.dateContext', { date: timezone.formatDateDisplay(contextStartTime || trip.startTime) }) }}</p>
    <p class="editor-help">{{ t(routeTarget ? 'missingActivity.editRouteHint' : rebuildRange ? 'missingActivity.rebuildIntervalHint' : 'missingActivity.help') }}</p>
    <p class="mobile-editor-help">{{ t('missingActivity.mobileHelp') }}</p>
    <label v-if="!routeTarget && !rebuildRange && gapOptions.length > 1" class="gap-choice">{{ t('missingActivity.chooseGap') }}
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
          <div v-if="!routeTarget" class="activity-times">
            <label>{{ t('missingActivity.from') }}
              <ActivityDateTimeField v-model="activity.start" :label="t('missingActivity.from')" :context-date="contextDate" />
            </label>
            <label>{{ t('missingActivity.to') }}
              <ActivityDateTimeField v-model="activity.end" :label="t('missingActivity.to')" :context-date="contextDate" />
            </label>
          </div>
          <p v-if="!routeTarget && activeId === activity.id && !hasPicker" class="gps-no-points">{{ t('missingActivity.noPoints') }}</p>
          <label v-if="!routeTarget">{{ t('missingActivity.whatHappened') }}
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
            <label v-if="!routeTarget">{{ t('missingActivity.transport') }}
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
              :disabled="!activity.startPlace || !activity.endPlace" :aria-pressed="mapTarget === 'via' && activeId === activity.id"
              @click="requestMap('via', $event?.currentTarget)" />
            <ol v-if="activity.via.length" class="route-points">
              <li v-for="(point, pointIndex) in activity.via" :key="pointIndex">
                <Button :label="t('missingActivity.routePoint', { number: pointIndex + 1 })" icon="pi pi-pencil" text size="small"
                  :aria-pressed="mapTarget === pointIndex && activeId === activity.id" @click="requestMap(pointIndex, $event?.currentTarget)" />
                <Button icon="pi pi-arrow-up" text size="small" :disabled="pointIndex === 0"
                  :aria-label="t('missingActivity.moveEarlier', { number: pointIndex + 1 })" @click="moveRoutePoint(activity, pointIndex, -1)" />
                <Button icon="pi pi-arrow-down" text size="small" :disabled="pointIndex === activity.via.length - 1"
                  :aria-label="t('missingActivity.moveLater', { number: pointIndex + 1 })" @click="moveRoutePoint(activity, pointIndex, 1)" />
                <Button icon="pi pi-times" text size="small" :aria-label="t('missingActivity.removeRoutePoint', { number: pointIndex + 1 })" @click="activity.via.splice(pointIndex, 1); mapTarget = null" />
              </li>
            </ol>
          </template>
        </article>
        <Button v-if="!routeTarget" :label="t('missingActivity.addAnother')" icon="pi pi-plus" outlined size="small" @click="addActivity" />
      </div>
      <div ref="mapSection" class="activity-editor-map" :class="{ 'map-not-needed': !active?.segmentType && !hasPicker }">
        <!-- The map places something only after Pick on map; this banner says what the next click sets. -->
        <div v-if="mapTarget !== null" class="map-target-banner" role="status">
          <span>{{ t(mapTargetHint) }}</span>
          <Button :label="t(mapTarget === 'via' ? 'missingActivity.doneMap' : 'common.cancel')" :icon="mapTarget === 'via' ? 'pi pi-check' : 'pi pi-times'"
            size="small" :text="mapTarget !== 'via'" @click="finishMap" />
        </div>
        <p v-else class="map-instruction" role="status">{{ t(active?.segmentType === 'TRIP' ? 'missingActivity.routeHelp' : active?.segmentType === 'STAY' ? 'missingActivity.pickStay' : 'missingActivity.mapHelp') }}</p>
        <div v-if="active?.segmentType === 'TRIP'" class="map-edit-actions">
          <Button :label="t('missingActivity.showRoute')" icon="pi pi-expand" text size="small" :disabled="!mapMarkers.length" @click="focusPlaces()" />
        </div>
        <div class="gps-map-workspace" :class="{ 'is-picking': hasPicker }">
          <MapContainer class="activity-editor-map-canvas" :map-id="mapId" :center="initialViewport?.center || fallbackCenter" :zoom="initialViewport?.zoom ?? 13"
            :show-controls="false" :map-options="{ attributionControl: { compact: true } }" height="100%" width="100%" @map-ready="mapReady" @map-click="mapClick">
            <template #overlays="{ map, isReady }">
              <GpsTimePickerLayer v-if="visible && map && isReady && hasPicker" :map="map" :points="pickerVisiblePoints" :range="pickerRange"
                :inspected-index="inspectedIndex" :hovered-index="hoveredIndex" :pin-labels="{ from: t('missingActivity.from'), to: t('missingActivity.to') }"
                @inspect="inspectPickerPoint($event, false)" @hover="hoveredIndex = $event" />
              <TripReconstructionMapOverlay v-if="map && isReady" :map="map" :context-points="displayMarkers" :context-trip-lines="mapLines" :editable-context="!saving" @context-point-dragged="dragPoint" :id-prefix="mapId" />
            </template>
          </MapContainer>
          <GpsTimePickerPanel v-if="hasPicker" :range="pickerRange" :points="pickerPoints" :start-time="activityTimes.start" :end-time="activityTimes.end"
            :visible="pickerWindow" :times-editable="!routeTarget" :place-actions="placeActions" :other-ranges="otherRanges"
            @expand="side => pickerExpanded[side === 'before' ? 'showAllBefore' : 'showAllAfter'] = true"
            @update:range="setPickerRange" @place="placeFromPoint" :inspected-index="inspectedIndex"
            :hovered-index="hoveredIndex" @inspect="inspectPickerPoint($event, true)" @hover="hoveredIndex = $event" />
        </div>
        <div v-if="active?.segmentType === 'TRIP'" class="map-marker-legend">
          <span><b class="marker-start">A</b> {{ t('missingActivity.origin') }}</span><span><b class="marker-end">B</b> {{ t('missingActivity.destination') }}</span>
        </div>
        <small v-if="chosenGap && (!active?.startPlace || !active?.endPlace) && active?.segmentType === 'TRIP'">{{ t('missingActivity.boundaryHint') }}</small>
      </div>
    </div>
    <section v-if="review" class="activity-review" aria-live="polite">
      <h3>{{ t('missingActivity.review') }}</h3>
      <ol><li v-for="(event, index) in review.events" :key="index">
        <strong>{{ event.type === 'DATA_GAP' ? t('missingActivity.unknown') : event.locationName || event.label }}</strong>
        <span>{{ timezone.formatDateTimeDisplay(event.start, { withSeconds: true }) }} — {{ timezone.formatDateTimeDisplay(event.end, { withSeconds: true }) }}</span>
      </li></ol>
      <small>{{ t(routeTarget ? 'missingActivity.editRouteReviewHint' : 'missingActivity.reviewHint') }}</small>
    </section>
    <section v-if="warnings.length" class="activity-warnings" aria-live="polite">
      <p v-for="(warning, index) in warnings" :key="`${warning.code}-${index}`"
        :class="['activity-warning', `activity-warning--${(warning.severity || 'info').toLowerCase()}`]">
        {{ t(`missingActivity.warnings.${warning.code}`, warning.message) }}
        <Button v-if="warning.groupId && (warning.code === 'REPLACES_CORRECTION' || warning.code === 'PARTIAL_OVERLAP')"
          :label="t('missingActivity.replaceCorrection')" text size="small"
          @click="replaceCorrection(warning)" />
      </p>
    </section>
    <p v-if="error" class="activity-error" role="alert">{{ error }}</p>
    <template #footer>
      <Button :label="t('common.cancel')" text :disabled="saving" @click="emit('close')" />
      <Button v-if="!review" :label="t('missingActivity.review')" :loading="previewing" :disabled="readOnly || saving" @click="preview" />
      <Button v-else :label="t('missingActivity.save')" :loading="saving" :disabled="readOnly || hasInvalidWarning" @click="save" />
    </template>
  </Dialog>
</template>
<script setup>
import { computed, onBeforeUnmount, ref, shallowRef, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import ActivityDateTimeField from './ActivityDateTimeField.vue'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import MapContainer from '@/components/maps/MapContainer.vue'
import GpsTimePickerLayer from '@/components/maps/layers/GpsTimePickerLayer.vue'
import GpsTimePickerPanel from './GpsTimePickerPanel.vue'
import TripReconstructionMapOverlay from '@/components/trips/reconstruction/TripReconstructionMapOverlay.vue'
import TimelinePlacePicker from './TimelinePlacePicker.vue'
import { useTimelineStore } from '@/stores/timeline'
import { useTimezone } from '@/composables/useTimezone'
import { movementTypeOptions } from '@/constants/movementTypes'
import { missingActivityBoundaryPlaces, pointsInViewport } from '@/utils/timelineReconstructionContext'
import { buildGpsTimePickerPoints, gpsRangeForTimes, gpsVisibleWindow } from '@/utils/gpsTimePicker'
import { normalizeTripPathPoints } from '@/utils/tripStaySplitSelection'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'
import { timelineUtcToPicker, timelinePickerToUtc } from '@/utils/timelineEditTime'
import '@/maps/shared/styles/tripReconstructionMarkers.css'
import { fitMapToBounds, setMapView } from '@/maps/tripReconstruction/shared/tripReconstructionViewport'

const props = defineProps({ visible: Boolean, tripId: { type: Number, default: null }, trip: Object,
  contextStartTime: String, contextEndTime: String, suggestedRange: Object, initialViewport: Object, rebuildRange: Object, routeTarget: Object,
  availableGaps: { type: Array, default: () => [] }, timelineData: { type: Array, default: () => [] },
  recordedPoints: { type: Array, default: () => [] }, locationSuggestion: Object,
  fallbackCenter: { type: Array, default: () => [37.7749, -122.4194] }, readOnly: Boolean })
const emit = defineEmits(['close', 'committed'])
const { t } = useI18n()
const timezone = useTimezone()
const store = useTimelineStore()
const chosenGap = ref(null), editorMap = shallowRef(null), mapTarget = ref(null)
const mapSection = ref(null), formSection = ref(null), mapReturnTarget = shallowRef(null)
const activities = ref([]), activeId = ref(null), review = ref(null), error = ref('')
const warnings = ref([]), previewToken = ref(null), replacesGroupId = ref(null)
const saving = ref(false), previewing = ref(false)
const inspectedIndex = ref(null), hoveredIndex = ref(null)
const mapId = `missing-activity-${Math.random().toString(36).slice(2)}`
let nextId = 0, version = 0
const active = computed(() => activities.value.find(activity => activity.id === activeId.value))
const activityTypes = computed(() => [{ label: t('missingActivity.stayed'), value: 'STAY' }, { label: t('missingActivity.travelled'), value: 'TRIP' }])
const movementOptions = computed(() => movementTypeOptions.map(option => ({ value: option.value, label: t(option.labelKey) })))
const pickerPoints = computed(() => buildGpsTimePickerPoints(props.recordedPoints))
// Recorded points are always the evidence on this map: numbered in time order beside a list and time strip.
const hasPicker = computed(() => pickerPoints.value.length > 0)
// The activity's own From/To is the only selection: GPS picks write into it and the map, list and strip show it.
const toTime = (value, original) => { const utc = timelinePickerToUtc(value, timezone, original); return utc ? Date.parse(utc) : null }
const activityTimes = computed(() => ({ start: toTime(active.value?.start, active.value?.originalStart), end: toTime(active.value?.end, active.value?.originalEnd) }))
const pickerRange = computed(() => gpsRangeForTimes(pickerPoints.value, activityTimes.value.start, activityTimes.value.end))
// The list and map show only From to To plus context; a collapsed side can be expanded until the times change.
const pickerExpanded = ref({ showAllBefore: false, showAllAfter: false })
const pickerWindow = computed(() => gpsVisibleWindow(pickerPoints.value, activityTimes.value.start, activityTimes.value.end, pickerExpanded.value))
const pickerVisiblePoints = computed(() => pickerPoints.value.slice(pickerWindow.value.from, pickerWindow.value.to + 1))
// Times already taken by the other activities, shown faintly on the strip.
const otherRanges = computed(() => activities.value.filter(activity => activity.id !== activeId.value).map(activity => ({
  start: toTime(activity.start, activity.originalStart), end: toTime(activity.end, activity.originalEnd)
})).filter(range => range.start !== null && range.end !== null && range.end > range.start))
const MAP_TARGET_HINTS = { stay: 'missingActivity.clickStay', start: 'missingActivity.clickStart', end: 'missingActivity.clickEnd', via: 'missingActivity.clickRoutePoints' }
const mapTargetHint = computed(() => Number.isInteger(mapTarget.value) ? 'missingActivity.clickMovePoint' : MAP_TARGET_HINTS[mapTarget.value])
// What a selected GPS point can fill in besides From/To: the place being picked, or the places this activity needs.
const placeActions = computed(() => {
  const activity = active.value, target = mapTarget.value
  if (!activity?.segmentType) return []
  if (target === 'via') return [{ key: 'via', label: t('missingActivity.picker.useAsRoutePoint') }]
  if (Number.isInteger(target)) return [{ key: target, label: t('missingActivity.picker.moveRoutePointHere', { number: target + 1 }) }]
  if (activity.segmentType === 'STAY') return [{ key: 'stay', label: t('missingActivity.picker.useAsLocation') }]
  const start = { key: 'start', label: t('missingActivity.picker.useAsStart') }, end = { key: 'end', label: t('missingActivity.picker.useAsDestination') }
  return target === 'start' ? [start] : target === 'end' ? [end] : [start, end]
})
// An invalid draft or an unconfirmed replacement cannot be saved: the user picks Replace or adjusts the range.
const hasInvalidWarning = computed(() => warnings.value.some(warning => warning.severity === 'INVALID'
  || warning.code === 'REPLACES_CORRECTION'))
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
const mapLines = computed(() => active.value?.segmentType === 'TRIP' && mapMarkers.value.length > 1
  ? [mapMarkers.value.map(point => [point.latitude, point.longitude])] : [])
function focusPlaces(onlyIfOutside = false) {
  const map = editorMap.value, points = mapMarkers.value
  if (!map || !points.length) return
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
  version++; activities.value = []; addActivity(); review.value = null; error.value = ''; resetPicker()
  warnings.value = []; previewToken.value = null; replacesGroupId.value = null
  if (props.routeTarget) {
    chooseGap({ startTime: props.routeTarget.startTime, endTime: props.routeTarget.endTime })
    const activity = active.value
    activity.segmentType = 'TRIP'
    activity.movementType = props.routeTarget.movementType || 'UNKNOWN'
    activity.startPlace = routePlace(props.routeTarget, 'start')
    activity.endPlace = routePlace(props.routeTarget, 'end')
    activity.via = (props.routeTarget.manualRoute || []).slice(1, -1).map(point => ({ ...point }))
    if (!props.routeTarget.manualRoute) {
      const targetId = props.routeTarget.id
      store.fetchTripPath(props.routeTarget.startTime, props.routeTarget.endTime)
        .then(path => {
          const points = path?.points || []
          if (!props.visible || props.routeTarget?.id !== targetId || points.length <= 2) return
          const normalized = normalizeTripPathPoints(points)
          const inner = (normalized.length ? normalized : points).slice(1, -1)
          const step = Math.max(1, Math.ceil(inner.length / 200))
          activity.via = inner.filter((_, index) => index % step === 0)
            .map(point => ({ latitude: point.latitude, longitude: point.longitude }))
        })
        .catch(() => {})
    }
    return
  }
  if (props.rebuildRange) {
    chooseGap({ startTime: props.rebuildRange.startTime, endTime: props.rebuildRange.endTime })
    active.value.segmentType = props.rebuildRange.type === 'stay' ? 'STAY' : 'TRIP'
    if (props.rebuildRange.type === 'stay') {
      active.value.place = { latitude: props.rebuildRange.latitude, longitude: props.rebuildRange.longitude,
        locationName: props.rebuildRange.label || '', source: 'custom' }
    } else if (props.rebuildRange.movementType) {
      active.value.movementType = props.rebuildRange.movementType
    }
    return
  }
  const supplied = props.suggestedRange
  chooseGap(supplied ? { ...props.availableGaps.find(gap => gap.id != null && gap.id === supplied.id), ...supplied }
    : props.availableGaps.length === 1 ? props.availableGaps[0] : null)
}, { immediate: true })
function resetPicker() { inspectedIndex.value = null; hoveredIndex.value = null; pickerExpanded.value = { showAllBefore: false, showAllAfter: false } }
watch(activeId, () => { mapTarget.value = null; resetPicker() }, { flush: 'sync' })
watch(pickerPoints, resetPicker)
watch(() => [activityTimes.value.start, activityTimes.value.end], () => { pickerExpanded.value = { showAllBefore: false, showAllAfter: false } })
// A point that drops out of view is no longer selected or hovered.
watch(pickerWindow, ({ from, to }) => {
  const outside = index => Number.isInteger(index) && (index < from || index > to)
  if (outside(inspectedIndex.value)) inspectedIndex.value = null
  if (outside(hoveredIndex.value)) hoveredIndex.value = null
})
// Bring the narrowed points into view once dragging settles, and only when none of them are on screen.
let pickerFitTimer = null
watch(() => `${pickerWindow.value.from}:${pickerWindow.value.to}`, () => {
  clearTimeout(pickerFitTimer)
  pickerFitTimer = setTimeout(() => { if (hasPicker.value) showPickerPoints() }, 400)
})
onBeforeUnmount(() => clearTimeout(pickerFitTimer))
watch(() => active.value?.segmentType, () => { mapTarget.value = null })
watch(() => activities.value.map(({ segmentType, start, end, originalStart, originalEnd, place, visitName, movementType, startPlace, endPlace, via }) =>
  ({ segmentType, start, end, originalStart, originalEnd, place, visitName, movementType, startPlace, endPlace, via })),
  () => { version++; review.value = null; previewToken.value = null; warnings.value = []; replacesGroupId.value = null }, { deep: true, flush: 'sync' })
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
// The map places something only once Pick on map chose what; otherwise a click just clears the selected point.
function mapClick(event) {
  if (!active.value || mapTarget.value === null) return
  // A vector click on a GPS point also reaches the map; the point's own handler places at its recorded spot.
  if (event?.point && editorMap.value?.queryRenderedFeatures?.(event.point).some(feature => feature.properties?.gpsPickerPoint)) return
  const point = event?.latlng || event?.lngLat || event
  placeAt(mapTarget.value, { latitude: point?.lat, longitude: point?.lng })
}
function placeAt(target, { latitude, longitude }) {
  const activity = active.value
  if (!activity || !Number.isFinite(latitude) || !Number.isFinite(longitude)) return
  const point = { latitude, longitude }
  if (target === 'stay') { activity.pickedPoint = point; activity.previewPlace = null }
  else if (target === 'start' || target === 'end') { activity[`${target}Point`] = point; activity[target === 'start' ? 'previewStart' : 'previewEnd'] = null }
  else if (target === 'via') { activity.via.push(point); return }
  else if (Number.isInteger(target) && activity.via[target]) activity.via[target] = point
  else return
  // One placement finishes picking, except route points, which are added until Done.
  if (mapTarget.value === target) { mapTarget.value = null; returnToPlace() }
}
function placeFromPoint(target, index) {
  const point = pickerPoints.value[index]
  if (point) placeAt(target, point)
}
function dragPoint({ label, latitude, longitude }) {
  if (!active.value || saving.value) return
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
// Bring the shown points into view when the map was moved away from them, e.g. while placing a stay.
function showPickerPoints() {
  const map = editorMap.value, points = pickerVisiblePoints.value
  if (!map?.getBounds || !points.length || pointsInViewport(points, map.getBounds()).length) return
  if (points.length === 1) setMapView(map, [points[0].latitude, points[0].longitude], 17, { animate: true })
  else fitMapToBounds(map, points.map(point => [point.latitude, point.longitude]), { padding: [40, 40], maxZoom: 18 })
}
function inspectPickerPoint(index, fromList) {
  inspectedIndex.value = index
  const point = pickerPoints.value[index], map = editorMap.value
  // While picking on the map, clicking a GPS point places at exactly where it was recorded.
  if (!fromList && point && mapTarget.value !== null) placeAt(mapTarget.value, point)
  if (fromList && point && map?.getBounds && !pointsInViewport([point], map.getBounds()).length)
    setMapView(map, [point.latitude, point.longitude], map.getZoom(), { animate: true })
}
function setBoundaryTime(activity, boundary, point) {
  const [field, original] = boundary === 'from' ? ['start', 'originalStart'] : ['end', 'originalEnd']
  activity[original] = point ? new Date(point.time).toISOString() : null
  activity[field] = point ? timelineUtcToPicker(activity[original], timezone) : null
  activity[`${field}Invalid`] = false
}
// Applies a From/To change made on the map, list or strip to the activity's times, keeping seconds.
function setPickerRange(next) {
  const activity = active.value
  if (!activity) return
  const changed = ['from', 'to'].filter(boundary => next[boundary] !== pickerRange.value[boundary])
  changed.forEach(boundary => setBoundaryTime(activity, boundary, pickerPoints.value[next[boundary]] || null))
  // A typed time on the other side is not matched to a point, so clear it here if the new one crosses it.
  const { start, end } = activityTimes.value
  if (changed.length === 1 && start !== null && end !== null && end <= start) setBoundaryTime(activity, changed[0] === 'from' ? 'to' : 'from', null)
}
function payload() {
  return { tripId: props.tripId,
    previewToken: previewToken.value, replacesGroupId: replacesGroupId.value,
    contextStart: chosenGap.value?.startTime, contextEnd: chosenGap.value?.endTime,
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
function routePlace(item, prefix) {
  const latitude = prefix === 'start' ? (item.startLatitude ?? item.latitude) : item.endLatitude
  const longitude = prefix === 'start' ? (item.startLongitude ?? item.longitude) : item.endLongitude
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null
  const locationName = prefix === 'start'
    ? (item.startLocationName ?? item.locationName ?? '')
    : (item.endLocationName ?? '')
  return { latitude, longitude, locationName, source: 'custom' }
}
function routePayload() {
  const activity = active.value
  if (!activity?.startPlace || !activity?.endPlace) throw new Error(t('missingActivity.chooseEndpoints'))
  const point = place => ({ latitude: place.latitude, longitude: place.longitude })
  return { op: 'SET_ROUTE',
    anchorStart: new Date(props.routeTarget.startTime).toISOString(),
    anchorEnd: new Date(props.routeTarget.endTime).toISOString(),
    affectedFrom: new Date(props.routeTarget.startTime).toISOString(),
    affectedTo: new Date(props.routeTarget.endTime).toISOString(),
    payload: { waypoints: [point(activity.startPlace), ...activity.via.map(point), point(activity.endPlace)] },
    previewToken: previewToken.value }
}
async function preview() {
  error.value = ''; const currentVersion = version; previewing.value = true
  try {
    if (props.routeTarget) {
      const result = await store.previewCorrections(routePayload())
      if (currentVersion === version) {
        review.value = { events: result.events || [], token: result.token }
        previewToken.value = result.token || null
        warnings.value = []
      }
      return
    }
    const result = await store.previewCorrections(payload())
    if (currentVersion === version) {
      review.value = result
      previewToken.value = result.token || null
      warnings.value = result.warnings || []
    }
  }
  catch (problem) { error.value = formatApiErrorDetail(problem, t('missingActivity.failed')) }
  finally { previewing.value = false }
}
function replaceCorrection(warning) {
  replacesGroupId.value = warning.groupId
  review.value = null
  preview()
}
async function save() {
  if (!review.value || props.readOnly || saving.value || hasInvalidWarning.value) return
  error.value = ''; saving.value = true
  try {
    const result = await store.saveCorrections(props.routeTarget ? routePayload() : payload())
    emit('committed', result)
  }
  catch (problem) {
    if (problem?.code === 'TIMELINE_PREVIEW_STALE') {
      review.value = null; previewToken.value = null
      await preview()
      error.value = t('timeline.edit.previewChanged')
    } else error.value = formatApiErrorDetail(problem, t('missingActivity.failed'))
  }
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
.map-instruction, small, .gps-no-points { font-size: .8rem; color: var(--gp-text-secondary); line-height: 1.5; }
h4 { margin: .25rem 0 0; font-size: .875rem; }
.gps-no-points { margin: 0; }
.gps-map-workspace { display: grid; grid-template-columns: minmax(0, 1fr); gap: .75rem; height: var(--missing-activity-map-height); min-height: 0; }
.gps-map-workspace.is-picking { grid-template-columns: minmax(0, 1.2fr) minmax(16rem, .8fr); }
.activity-editor-map-canvas { height: 100%; min-width: 0; min-height: 0; }
.activity-editor-map-canvas :deep(.base-map) { min-height: 0; }
@media (max-width: 1100px) {
  .gps-map-workspace.is-picking { grid-template-columns: minmax(0, 1fr); grid-template-rows: minmax(0, 1fr) minmax(0, 1fr); height: min(40rem, calc(100dvh - 16rem)); }
}
.activity-editor-map { min-width: 0; align-self: start; position: sticky; top: 0; }
.activity-review { margin-top: 1rem; padding: 1rem; background: var(--gp-surface-ground); border-radius: var(--gp-radius-medium); }
.activity-review li { display: grid; gap: .25rem; margin: .75rem 0; }
.activity-review span { color: var(--gp-text-secondary); font-size: .85rem; }
.activity-warnings { margin-top: .75rem; display: grid; gap: .5rem; }
.activity-warning { display: flex; align-items: center; justify-content: space-between; gap: .5rem; margin: 0; padding: .6rem .75rem; border-radius: var(--gp-radius-small); font-size: .85rem; line-height: 1.4; }
.activity-warning--invalid { background: var(--gp-danger-soft); color: var(--gp-danger-text); }
.activity-warning--confirm { background: var(--gp-warning-soft); color: var(--gp-warning-text); }
.activity-warning--info { background: var(--gp-surface-ground); color: var(--gp-text-secondary); }
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
.map-target-banner { display: flex; align-items: center; justify-content: space-between; gap: .75rem; margin-bottom: .5rem; padding: .4rem .5rem .4rem .75rem;
  border-radius: var(--gp-radius-small); background: color-mix(in srgb, var(--gp-primary) 14%, transparent); border: 1px solid color-mix(in srgb, var(--gp-primary) 45%, transparent);
  font-size: .85rem; font-weight: 600; }
.route-points li { display: flex; align-items: center; flex-wrap: wrap; }
.marker-start { background: #15803d; border-radius: 50%; }
.marker-end { background: #b91c1c; border-radius: .3rem; }
</style>
