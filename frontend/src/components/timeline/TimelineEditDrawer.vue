<template>
  <Drawer
    :visible="visible"
    position="right"
    class="timeline-edit-drawer"
    :header="header"
    @show="rememberFocus" @after-hide="restoreFocus"
    @update:visible="value => !value && close()"
    :dismissable="!saving" :close-on-escape="!saving"
  >
    <div v-if="item" class="timeline-edit-content">
      <div class="timeline-edit-summary">
        <span class="timeline-edit-summary-icon" aria-hidden="true"><i :class="summaryIcon" /></span>
        <div>
          <strong>{{ summary }}</strong>
          <p v-if="zeroDurationTrip" role="note">{{ t('timeline.trip.unrecordedHint') }}</p>
        </div>
      </div>

      <div v-show="!action" ref="changesSection" tabindex="-1"><TimelineChangesList ref="changesList" :visible="visible" :item="item" @changed="emit('submitted', $event)" @loaded="savedEdits = $event" /></div>
      <template v-if="!action">
        <section class="edit-action-section">
          <h3>{{ t('timeline.edit.details') }}</h3>
          <button v-for="entry in detailActions" :key="entry.id" class="edit-action-row" @click="selectAction(entry)">
            <span class="edit-action-icon"><i :class="entry.icon" /></span>
            <span class="edit-action-text">
              <span>{{ entry.label }}</span>
              <small v-if="currentValue(entry.id)" class="current-value">{{ currentValue(entry.id) }}</small>
              <small v-if="entry.hint">{{ entry.hint }}</small>
            </span>
            <i class="pi pi-chevron-right edit-action-chevron" />
          </button>
        </section>
        <section v-if="structuralActions.length || item.type === 'stay'" class="edit-action-section">
          <h3>{{ t('timeline.edit.moreChanges') }}</h3>
          <p v-if="item.type === 'stay' && !renameAction" role="note">{{ t('timeline.edit.renameUnlinked') }}</p>
          <button v-for="entry in structuralActions" :key="entry.id" class="edit-action-row"
            :class="{ 'is-danger': entry.id === 'REMOVE_EVENT' }" @click="selectAction(entry)">
            <span class="edit-action-icon"><i :class="entry.icon" /></span>
            <span class="edit-action-text">
              <span>{{ entry.label }}</span>
              <small v-if="currentValue(entry.id)" class="current-value">{{ currentValue(entry.id) }}</small>
              <small v-if="entry.hint">{{ entry.hint }}</small>
            </span>
            <i class="pi pi-chevron-right edit-action-chevron" />
          </button>
        </section>
      </template>

      <form v-else :id="formId" class="timeline-edit-form" @submit.prevent="apply">
        <button type="button" class="drawer-back" @click="back"><i class="pi pi-arrow-left" /> {{ t('common.back') }}</button>
        <div class="edit-form-heading">
          <span class="edit-action-icon"><i :class="action.icon" /></span>
          <h3>{{ action.label }}</h3>
        </div>

        <p v-if="action.structural" class="edit-step" role="status">{{ t(preview ? 'timeline.edit.reviewReady' : 'timeline.edit.reviewFirst') }}</p>
        <template v-if="action.id === 'SET_VISIT_NAME'">
          <Button v-if="activeLabelEdit" type="button" :label="t('timeline.edit.removeLabel')" icon="pi pi-undo" text @click="removeLabel" />
          <label>{{ t('timeline.edit.visitLabel') }}
            <InputText v-model="form.visitName" maxlength="500" required :placeholder="t('timeline.edit.visitLabelPlaceholder')" />
          </label>
          <small>{{ t('timeline.edit.visitLabelHint', { location: item.locationName }) }}</small>
        </template>
        <template v-else-if="action.id === 'SET_PLACE'">
          <small>{{ t('timeline.edit.locationHint') }}</small>
          <TimelinePlacePicker v-model="form.place" :current="currentPlace" :lookup-at="currentPlace"
            :reference="currentPlace" map-id="timeline-edit-place-map" />
        </template>

        <template v-else-if="action.id === 'SET_MOVEMENT_TYPE'">
          <label>{{ t('timeline.edit.movementType') }}
            <Select :aria-label="t('timeline.edit.movementType')" v-model="form.movementType" :options="movementTypes" option-label="label" option-value="value" />
          </label>
        </template>
        <template v-else-if="action.id === 'SET_TIMES'">
          <label>{{ t('timeline.edit.start') }}
            <DatePicker v-model="form.start" @input="onTimeInput('start', $event)" @update:model-value="form.startInvalid = false" show-time fluid :date-format="timezone.getPrimeVueDatePickerFormat()"
              :hour-format="timezone.getTimeFormat() === '12h' ? '12' : '24'" />
          </label>
          <label>{{ t('timeline.edit.end') }}
            <DatePicker v-model="form.end" @input="onTimeInput('end', $event)" @update:model-value="form.endInvalid = false" show-time fluid :date-format="timezone.getPrimeVueDatePickerFormat()"
              :hour-format="timezone.getTimeFormat() === '12h' ? '12' : '24'" />
          </label>
          <small>{{ t('timeline.edit.sharedBoundaryHint') }}</small>
        </template>
        <template v-else-if="action.id === 'SPLIT_STAY'">
          <label>{{ t('timeline.edit.splitAt') }}
            <DatePicker v-model="form.splitAt" @input="onTimeInput('splitAt', $event)" @update:model-value="form.splitAtInvalid = false" show-time fluid :date-format="timezone.getPrimeVueDatePickerFormat()"
              :hour-format="timezone.getTimeFormat() === '12h' ? '12' : '24'" :invalid="splitAtOutside" />
          </label>
          <small :class="{ 'edit-field-error': splitAtOutside }" :role="splitAtOutside ? 'alert' : undefined">
            {{ t('timeline.edit.splitAtRange', splitAtBounds) }}
          </small>
          <small>{{ t('timeline.edit.splitStayHint', { location: item.locationName }) }}</small>
          <h4 class="edit-question">{{ t('timeline.edit.splitStayQuestion') }}</h4>
          <TimelinePlacePicker v-model="form.place" :lookup-at="currentPlace" :reference="currentPlace"
            :reference-caption="t('timeline.edit.place.splitReferenceCaption')" map-id="timeline-edit-split-map" />
        </template>
        <template v-else-if="action.id === 'MERGE'">
          <small>{{ t(`timeline.edit.merge.${item.type}.explanation`) }}</small>
          <div class="merge-choices" role="radiogroup" :aria-label="t('timeline.edit.mergeDirection')">
            <button v-for="choice in directions" :key="choice.value" type="button" role="radio"
              class="merge-choice" :class="{ 'is-selected': form.direction === choice.value }"
              :aria-checked="form.direction === choice.value" @click="form.direction = choice.value">
              <i :class="form.direction === choice.value ? 'pi pi-check-circle' : 'pi pi-circle'" aria-hidden="true" />
              <span class="merge-choice-text">
                <strong>{{ choice.label }}</strong>
                <span>{{ choice.target }}</span>
                <small>{{ choice.removes }}</small>
              </span>
            </button>
          </div>
        </template>
        <template v-else-if="action.id === 'REMOVE_EVENT'">
          <p v-if="zeroDurationTrip">{{ t('timeline.edit.dismissTransitionHint') }}</p>
          <label v-else>{{ t('timeline.edit.removeResolution') }}
            <Select v-model="form.resolution" :options="resolutions" option-label="label" option-value="value" />
          </label>
        </template>


        <p v-if="validationMessage" class="edit-field-error" role="status">{{ validationMessage }}</p>
        <p v-if="!action.structural" class="edit-comparison">{{ currentValue(action.id) || t('timeline.edit.noLabel') }} → {{ proposedValue }}</p>
        <div v-if="preview" ref="previewSection" tabindex="-1" class="edit-preview" aria-live="polite">
          <h4>{{ t('timeline.edit.before') }}</h4>
          <div v-for="event in beforeEvents" :key="`${event.type}-${eventStart(event)}`" class="edit-preview-event">
            <strong>{{ event.locationName || eventName(event) }}</strong>
            <small v-if="event.visitName"><i class="pi pi-tag" aria-hidden="true" /> {{ event.visitName }}</small>
            <span>{{ formatPeriod(eventStart(event), eventEnd(event)) }} · {{ formatDuration(eventSeconds(event)) }}</span>
          </div>
          <h4>{{ t('timeline.edit.after') }}</h4>
          <div v-for="event in preview.events" :key="`${event.type}-${event.start}`" class="edit-preview-event">
            <strong>{{ previewEventType(event) }} · {{ previewEventLabel(event) }}</strong>
            <small v-if="event.locationName && event.label && event.label !== event.locationName"><i class="pi pi-tag" aria-hidden="true" /> {{ event.label }}</small>
            <span>{{ formatPeriod(event.start, event.end) }} · {{ formatDuration((Date.parse(event.end) - Date.parse(event.start)) / 1000) }}</span>
          </div>
        </div>
        <p v-if="error" class="edit-error" role="alert"><i class="pi pi-exclamation-circle" />{{ error }}</p>

        <Button v-if="conflictId" type="button" :label="t('timeline.edit.viewChange')" text @click="viewConflict" />

      </form>
    </div>
    <template v-if="action" #footer>
      <div class="drawer-actions">
        <Button type="button" :label="t('common.cancel')" text @click="close" />
        <Button v-if="action.structural && !preview" type="button" :label="t('timeline.edit.reviewChanges')"
          :loading="previewing" :disabled="!validInput || saving" @click="loadPreview" />
        <Button v-else type="submit" :form="formId" :label="t('timeline.edit.saveChanges')" :loading="saving" :disabled="!canApply" />
      </div>
    </template>
  </Drawer>
</template>

<script setup>
import { computed, nextTick, reactive, ref, useId, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Drawer from 'primevue/drawer'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import DatePicker from 'primevue/datepicker'
import Select from 'primevue/select'
import TimelineChangesList from './TimelineChangesList.vue'
import TimelinePlacePicker from './TimelinePlacePicker.vue'
import { useTimelineStore } from '@/stores/timeline'
import { useTimezone } from '@/composables/useTimezone'
import { formatDuration } from '@/utils/calculationsHelpers'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'
import { parseTimelinePickerInput, timelinePickerToUtc, timelineUtcToPicker } from '@/utils/timelineEditTime'
import { canMergeTimelineEvents, placePayload, samePlace, stayPlace } from '@/utils/timelineEditPlace'

const props = defineProps({
  focusChanges: { type: Boolean, default: false },
  visible: { type: Boolean, default: false },
  item: { type: Object, default: null },
  previousItem: { type: Object, default: null },
  nextItem: { type: Object, default: null },
  mergePreviousItem: { type: Object, default: null },
  mergeNextItem: { type: Object, default: null }
})
const emit = defineEmits(['close', 'submitted', 'rename-place', 'split-trip-with-stay', 'rebuild-interval'])
const { t, te } = useI18n()
const store = useTimelineStore()
const timezone = useTimezone()
const action = ref(null)
const preview = ref(null)
const previewing = ref(false)
const previewSection = ref(null)
const saving = ref(false)
const error = ref('')
const form = reactive({})
const formId = useId()
const savedEdits = ref([])
const changesList = ref(null)
const changesSection = ref(null)
const conflictId = ref(null)
const activeLabelEdit = computed(() => savedEdits.value.find(edit => edit.kind === 'SET_VISIT_NAME' && edit.status === 'ACTIVE' && edit.target?.type === 'STAY' && Number(edit.target.id) === Number(props.item?.id)))
let previewVersion = 0
let initialForm = ''
function onTimeInput(field, event) {
  const parsed = parseTimelinePickerInput(event, timezone)
  form[`${field}Invalid`] = !parsed
  if (parsed) form[field] = parsed
}
const dirty = computed(() => action.value && JSON.stringify(form) !== initialForm)
const proposedValue = computed(() => action.value?.id === 'SET_VISIT_NAME' ? form.visitName : movementLabel(form.movementType))
function movementLabel(value) { return te(`movementTypes.${value}`) ? t(`movementTypes.${value}`) : value }
function currentValue(id) {
  if (id === 'SET_VISIT_NAME') return props.item?.visitName || t('timeline.edit.noLabel')
  if (id === 'SET_PLACE') return props.item?.locationName
  if (id === 'SET_TIMES') return formatPeriod(interval.value.start, interval.value.end)
  if (id === 'SET_MOVEMENT_TYPE') return movementLabel(props.item?.movementType)
  return ''
}
const beforeEvents = computed(() => [...new Set([props.previousItem, props.item, props.nextItem, ...(action.value?.id === 'MERGE' ? [form.direction === 'PREVIOUS' ? props.mergePreviousItem : props.mergeNextItem] : [])].filter(Boolean))].sort((a, b) => Date.parse(eventStart(a)) - Date.parse(eventStart(b))))
function confirmDiscard() { return !dirty.value || window.confirm(t('timeline.edit.discardChanges')) }
function back() { if (!saving.value && confirmDiscard()) { reset(); action.value = null } }
function removeLabel() { if (activeLabelEdit.value) changesList.value?.change(activeLabelEdit.value, false) }
async function viewConflict() {
  if (!confirmDiscard()) return
  action.value = null
  await nextTick()
  changesSection.value?.focus()
  changesList.value?.focusChange(conflictId.value)
}

const interval = computed(() => {
  if (!props.item) return { start: null, end: null }
  const start = props.item.timestamp || props.item.startTime
  const seconds = props.item.stayDuration ?? props.item.tripDuration ?? props.item.durationSeconds
  return { start, end: props.item.endTime || new Date(new Date(start).getTime() + seconds * 1000).toISOString() }
})
const zeroDurationTrip = computed(() => props.item?.type === 'trip' && Date.parse(interval.value.start) === Date.parse(interval.value.end))

const header = computed(() => t(`timeline.edit.header.${props.item?.type || 'stay'}`))
const summaryIcon = computed(() => props.item?.type === 'trip'
  ? 'pi pi-directions'
  : props.item?.type === 'dataGap' ? 'pi pi-exclamation-triangle' : 'pi pi-map-marker')
const summary = computed(() => [
  zeroDurationTrip.value ? t('timeline.trip.unrecordedTransition') : props.item?.locationName || movementLabel(props.item?.movementType),
  props.item?.visitName,
  formatPeriod(interval.value.start, interval.value.end)
].filter(Boolean).join(' · '))
const entry = (id, key, icon, structural = false, hintKey = key) => ({
  id, label: t(`timeline.edit.action.${key}`), icon, structural,
  hint: te(`timeline.edit.actionHint.${hintKey}`) ? t(`timeline.edit.actionHint.${hintKey}`) : ''
})
const detailActions = computed(() => {
  if (props.item?.type === 'stay') return [
    entry('SET_PLACE', 'location', 'pi pi-map-marker'),
    entry('SET_VISIT_NAME', 'visitLabel', 'pi pi-tag'),
    entry('SET_TIMES', 'times', 'pi pi-clock', true)
  ]
  if (props.item?.type === 'trip') return [
    entry('SET_MOVEMENT_TYPE', 'movement', 'pi pi-car'),
    entry('SET_TIMES', 'times', 'pi pi-clock', true)
  ]
  return []
})
const renameAction = computed(() => props.item?.type === 'stay' && (props.item.favoriteId || props.item.geocodingId)
  ? entry('RENAME_PLACE', 'renameLocation', 'pi pi-pencil') : null)
const structuralActions = computed(() => {
  if (zeroDurationTrip.value) return [entry('REMOVE_EVENT', 'remove', 'pi pi-trash', true, 'dismissTransition')]
  const merge = directions.value.length
    ? [entry('MERGE', 'merge', 'pi pi-link', true, props.item?.type === 'trip' ? 'mergeTrip' : 'mergeStay')]
    : []
  if (props.item?.type === 'stay') return [...(renameAction.value ? [renameAction.value] : []), entry('SPLIT_STAY', 'splitStay', 'pi pi-arrows-h', true), ...merge, entry('REBUILD_INTERVAL', 'rebuildInterval', 'pi pi-sync', true), entry('REMOVE_EVENT', 'remove', 'pi pi-trash', true)]
  if (props.item?.type === 'trip') {
    // A manual route is copied as-is when an event is carved, so rebuilding a reconstructed trip would leave the
    // remainder with the whole route. Such trips can be removed or merged, not rebuilt.
    const manualRoute = props.item.manualRoute
    return [...(manualRoute ? [] : [entry('SPLIT_TRIP', 'splitTrip', 'pi pi-map-marker', true)]), ...merge,
      ...(manualRoute ? [] : [entry('REBUILD_INTERVAL', 'rebuildInterval', 'pi pi-sync', true)]),
      entry('REMOVE_EVENT', 'remove', 'pi pi-trash', true)]
  }
  return []
})
const movementTypes = computed(() => ['WALK', 'BICYCLE', 'RUNNING', 'CAR', 'MOTORCYCLE', 'PUBLIC_TRANSPORT', 'TRAIN', 'FLIGHT', 'BOAT', 'UNKNOWN']
  .map(value => ({ value, label: te(`movementTypes.${value}`) ? t(`movementTypes.${value}`) : value })))
// Merging joins this event with the nearest one of its type in a direction, swallowing at most one event between.
const directions = computed(() => [
  props.mergePreviousItem && mergeChoice('PREVIOUS', props.mergePreviousItem, props.previousItem),
  props.mergeNextItem && mergeChoice('NEXT', props.mergeNextItem, props.nextItem)
].filter(Boolean))
const resolutions = computed(() => [
  canMergeTimelineEvents(props.previousItem, props.nextItem)
    && { value: 'MERGE', label: t('timeline.edit.resolution.merge') },
  props.previousItem && { value: 'EXTEND_PREVIOUS', label: `${t('timeline.edit.resolution.previous')} · ${eventName(props.previousItem)}` },
  props.nextItem && { value: 'EXTEND_NEXT', label: `${t('timeline.edit.resolution.next')} · ${eventName(props.nextItem)}` },
  { value: 'GAP', label: t('timeline.edit.resolution.gap') }
].filter(Boolean))
const currentPlace = computed(() => props.item?.type === 'stay' ? stayPlace(props.item) : null)
// A gap most often continues the visit before it or leads into the one after it.
const placeActions = ['SET_PLACE', 'SPLIT_STAY']
// The split time is checked rather than limited in the picker: a limited picker refuses each intermediate step, so
// changing 17:22 to 16:59 is impossible when the hour goes first and 16:22 falls before the visit.
const splitAtOutside = computed(() => {
  const splitAt = Date.parse(fromPickerDate(form.splitAt))
  return Number.isFinite(splitAt)
    && (splitAt <= Date.parse(interval.value.start) || splitAt >= Date.parse(interval.value.end))
})
const splitAtBounds = computed(() => {
  const { start, end } = interval.value
  if (!start || !end) return { start: '', end: '' }
  const sameDay = timezone.fromUtc(start).isSame(timezone.fromUtc(end), 'day')
  const format = value => sameDay ? timezone.formatTime(value) : timezone.formatDateTimeDisplay(value)
  return { start: format(start), end: format(end) }
})
const validInput = computed(() => {
  if (!action.value || saving.value) return false
  if (action.value.id === 'SET_TIMES' && (form.startInvalid || form.endInvalid)) return false
  if (action.value.id === 'SPLIT_STAY' && form.splitAtInvalid) return false
  if (action.value.id === 'SET_VISIT_NAME' && (!form.visitName?.trim() || form.visitName.trim() === (props.item?.visitName || ''))) return false
  if (action.value.id === 'SET_MOVEMENT_TYPE' && form.movementType === props.item?.movementType) return false
  if (action.value.id === 'REMOVE_EVENT' && !form.resolution) return false
  if (placeActions.includes(action.value.id) && !form.place) return false
  if (action.value.id === 'SET_PLACE' && form.place?.source === 'custom' && !form.place.locationName?.trim()) return false
  if (action.value.id === 'SET_PLACE' && samePlace(form.place, currentPlace.value)) return false
  if (action.value.id === 'SPLIT_STAY' && (!fromPickerDate(form.splitAt) || splitAtOutside.value)) return false
  if (action.value.id === 'SET_TIMES' && (form.startInvalid || form.endInvalid || !fromPickerDate(form.start) || !fromPickerDate(form.end) || Date.parse(fromPickerDate(form.start)) >= Date.parse(fromPickerDate(form.end)) || (Date.parse(fromPickerDate(form.start)) === Date.parse(interval.value.start) && Date.parse(fromPickerDate(form.end)) === Date.parse(interval.value.end)))) return false
  return true
})

const canApply = computed(() => validInput.value && !previewing.value && (!action.value?.structural || preview.value !== null))
const validationMessage = computed(() => {
  if (!action.value || saving.value) return ''
  if (action.value.id === 'SET_TIMES' && !form.startInvalid && !form.endInvalid && form.start && form.end
    && (!fromPickerDate(form.start) || !fromPickerDate(form.end))) return t('timeline.edit.uncertainTime')
  if (action.value.id === 'SPLIT_STAY' && !form.splitAtInvalid && form.splitAt && !fromPickerDate(form.splitAt)) return t('timeline.edit.uncertainTime')
  if ((action.value.id === 'SET_TIMES' && (form.startInvalid || form.endInvalid || !fromPickerDate(form.start) || !fromPickerDate(form.end))) || (action.value.id === 'SPLIT_STAY' && (form.splitAtInvalid || !fromPickerDate(form.splitAt)))) return t('timeline.edit.validTimesRequired')
  if (placeActions.includes(action.value.id) && !form.place) return t('timeline.edit.placeRequired')
  if (action.value.id === 'SET_PLACE' && form.place?.source === 'custom' && !form.place.locationName?.trim()) return t('timeline.edit.placeNameRequired')
  if (action.value.id === 'SET_VISIT_NAME' && !form.visitName?.trim()) return t('timeline.edit.labelRequired')
  if (action.value.id === 'SET_TIMES' && (form.startInvalid || form.endInvalid || !fromPickerDate(form.start) || !fromPickerDate(form.end) || Date.parse(fromPickerDate(form.start)) >= Date.parse(fromPickerDate(form.end)))) return t('timeline.edit.invalidTimes')
  if (action.value.id === 'REMOVE_EVENT' && !form.resolution) return t('timeline.edit.chooseResolution')
  if (!validInput.value) return t('timeline.edit.unchanged')
  return ''
})
watch(() => [props.visible, props.item], async () => {
  reset()
  if (props.visible && props.focusChanges) { await nextTick(); changesSection.value?.focus(); changesSection.value?.scrollIntoView?.({ block: 'start' }) }
}, { immediate: true })
watch(form, () => { previewVersion++; preview.value = null; previewing.value = false }, { deep: true, flush: 'sync' })
watch(() => action.value?.id, () => { previewVersion++; preview.value = null; previewing.value = false })

function reset() {
  savedEdits.value = []
  action.value = null
  preview.value = null
  error.value = ''
  conflictId.value = null
  Object.assign(form, {
    visitName: props.item?.visitName || '', place: null, movementType: props.item?.movementType || 'UNKNOWN',
    start: toPickerDate(interval.value.start), end: toPickerDate(interval.value.end),
    splitAt: midpointPickerDate(interval.value.start, interval.value.end), direction: 'NEXT', resolution: null, startPlace: null, endPlace: null, startInvalid: false, endInvalid: false, splitAtInvalid: false
  })
  initialForm = JSON.stringify(form)
}

function selectAction(selected) {
  if (selected.id === 'RENAME_PLACE') {
    emit('rename-place', props.item)
    return
  }
  if (selected.id === 'SPLIT_TRIP') {
    // Choosing where on the route the stop was needs the route map, which the trip split dialog provides.
    emit('split-trip-with-stay', props.item)
    return
  }
  if (selected.id === 'REBUILD_INTERVAL') {
    // The activity editor rebuilds the whole event interval, so it owns the map and the time range itself.
    emit('rebuild-interval', props.item)
    return
  }
  // The place picker starts from the current place so a custom point's name can be edited in place.
  form.place = selected.id === 'SET_PLACE' ? currentPlace.value : null
  action.value = selected
  if (selected.id === 'MERGE') form.direction = directions.value[0]?.value
  if (selected.id === 'REMOVE_EVENT') form.resolution = zeroDurationTrip.value ? 'DISMISS' : null
  initialForm = JSON.stringify(form)
  preview.value = null
  error.value = ''
}

function request() {
  const anchorStart = interval.value.start
  const anchorEnd = interval.value.end
  const payload = {}
  if (action.value.id === 'SET_VISIT_NAME') payload.visitName = form.visitName.trim()
  if (action.value.id === 'SET_PLACE') Object.assign(payload, placePayload(form.place))
  if (action.value.id === 'SET_MOVEMENT_TYPE') payload.movementType = form.movementType
  if (action.value.id === 'SET_MOVEMENT_TYPE' || zeroDurationTrip.value) {
    payload.sourceType = 'TRIP'
    payload.sourceStartLatitude = props.item.startLatitude ?? props.item.latitude
    payload.sourceStartLongitude = props.item.startLongitude ?? props.item.longitude
    payload.sourceEndLatitude = props.item.endLatitude
    payload.sourceEndLongitude = props.item.endLongitude
  }
  if (action.value.id === 'SET_TIMES') { payload.start = fromPickerDate(form.start); payload.end = fromPickerDate(form.end) }
  if (action.value.id === 'SPLIT_STAY') {
    payload.splitAt = fromPickerDate(form.splitAt)
    Object.assign(payload, placePayload(form.place, 'right'))
  }
  if (action.value.id === 'MERGE') payload.direction = form.direction
  if (action.value.id === 'REMOVE_EVENT') payload.resolution = form.resolution
  const mergePrevious = action.value.id === 'MERGE' && form.direction === 'PREVIOUS' ? props.mergePreviousItem : null
  const mergeNext = action.value.id === 'MERGE' && form.direction === 'NEXT' ? props.mergeNextItem : null
  const affectedStarts = [anchorStart, props.previousItem && (props.previousItem.timestamp || props.previousItem.startTime),
    mergePrevious && (mergePrevious.timestamp || mergePrevious.startTime)].filter(Boolean)
  const affectedEnds = [anchorEnd, props.nextItem && eventEnd(props.nextItem), mergeNext && eventEnd(mergeNext)].filter(Boolean)
  return {
    kind: action.value.id, anchorStart, anchorEnd,
    affectedFrom: affectedStarts.sort()[0], affectedTo: affectedEnds.sort().at(-1), payload
  }
}

async function loadPreview() {
  if (!validInput.value) return
  const version = ++previewVersion
  const input = request()
  previewing.value = true; error.value = ''; conflictId.value = null
  try {
    const result = await store.previewTimelineEdit(input)
    if (version === previewVersion) {
      preview.value = result
      await nextTick()
      if (version === previewVersion) { previewSection.value?.focus(); previewSection.value?.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' }) }
    }
  } catch (problem) { if (version === previewVersion) showError(problem, 'previewFailed') }
  finally { if (version === previewVersion) previewing.value = false }
}
function showError(problem, fallback) {
  error.value = formatApiErrorDetail(problem, t(`timeline.edit.${fallback}`))
  conflictId.value = problem?.parameters?.conflictingEditId || problem?.response?.data?.parameters?.conflictingEditId || null
}
async function apply() {
  if (!canApply.value) return
  const input = request()
  saving.value = true; error.value = ''
  try {
    const result = await store.createTimelineEdit(input)
    initialForm = JSON.stringify(form)
    emit('submitted', { ...result, operation: 'create' })
  } catch (problem) { showError(problem, 'saveFailed') }
  finally { saving.value = false }
}
let opener = null
function rememberFocus() { opener = document.activeElement }
function restoreFocus() {
  if (document.activeElement === document.body && opener?.isConnected) opener.focus()
}
function close() { if (!saving.value && confirmDiscard()) emit('close') }

function mergeChoice(direction, target, neighbour) {
  const type = props.item.type
  const between = neighbour && neighbour !== target ? neighbour : null
  return {
    value: direction,
    label: t(`timeline.edit.merge.${type}.${direction === 'NEXT' ? 'next' : 'previous'}`),
    target: [eventName(target), formatTimeRange(eventStart(target), eventEnd(target))].join(' · '),
    removes: between
      ? t(`timeline.edit.merge.${type}.removes`, { duration: formatDuration(eventSeconds(between)), name: eventName(between) })
      : t('timeline.edit.merge.adjacent')
  }
}
function eventName(event) {
  if (event.type === 'stay') return [event.locationName, event.visitName].filter(Boolean).join(' · ')
  if (event.type === 'trip') return te(`movementTypes.${event.movementType}`) ? t(`movementTypes.${event.movementType}`) : event.movementType
  return t('timeline.edit.eventType.DATA_GAP')
}
function eventStart(event) { return event.timestamp || event.startTime }
function eventSeconds(event) { return Math.max(0, (Date.parse(eventEnd(event)) - Date.parse(eventStart(event))) / 1000) }
function formatTimeRange(start, end) { return `${timezone.formatTime(start)}–${timezone.formatTime(end)}` }
function previewEventType(event) {
  return te(`timeline.edit.eventType.${event.type}`) ? t(`timeline.edit.eventType.${event.type}`) : event.type
}
function previewEventLabel(event) {
  if (event.type === 'TRIP' && te(`movementTypes.${event.label}`)) return t(`movementTypes.${event.label}`)
  if (event.type === 'DATA_GAP') return t('timeline.edit.eventType.DATA_GAP')
  return event.locationName || event.label
}
function eventEnd(item) {
  if (item.endTime) return item.endTime
  const seconds = item.stayDuration ?? item.tripDuration ?? item.durationSeconds ?? 0
  return new Date(new Date(item.timestamp || item.startTime).getTime() + seconds * 1000).toISOString()
}
// The date picker works with browser-local Date objects, so times are shown and read in the user's timezone through
// the same conversions as the other pickers, not in the browser's.
function toPickerDate(value) { return timelineUtcToPicker(value, timezone) }
function fromPickerDate(value) {
  const original = value === form.start ? interval.value.start : value === form.end ? interval.value.end : null
  return timelinePickerToUtc(value, timezone, original)
}
function midpointPickerDate(start, end) {
  return start && end ? toPickerDate(new Date((Date.parse(start) + Date.parse(end)) / 2).toISOString()) : null
}
function formatPeriod(start, end) {
  return start && end ? `${timezone.formatDateTimeDisplay(start)} – ${timezone.formatDateTimeDisplay(end)}` : ''
}
function startCoordinate(item) {
  if (!item) return null
  const latitude = item.startLatitude ?? item.latitude
  const longitude = item.startLongitude ?? item.longitude
  return latitude == null || longitude == null ? null : { latitude, longitude }
}
function endCoordinate(item) {
  if (!item) return null
  const latitude = item.endLatitude ?? item.latitude
  const longitude = item.endLongitude ?? item.longitude
  return latitude == null || longitude == null ? null : { latitude, longitude }
}
</script>

<style>
.timeline-edit-drawer {
  width: min(29rem, 100vw) !important;
  background: var(--gp-surface-card);
  box-shadow: var(--gp-shadow-dialog);
}

.timeline-edit-drawer .p-drawer-header {
  padding: 1.15rem 1.25rem .85rem;
  border-bottom: 1px solid var(--gp-border);
}

.timeline-edit-drawer .p-drawer-title {
  color: var(--gp-text-primary);
  font-size: 1.08rem;
  font-weight: 700;
}

.timeline-edit-drawer .p-drawer-content {
  padding: 0;
}

.timeline-edit-content {
  padding: 1.25rem;
  color: var(--gp-text-primary);
}

.timeline-edit-summary {
  display: grid;
  grid-template-columns: 2.75rem minmax(0, 1fr);
  align-items: center;
  gap: .85rem;
  margin: 0 0 1rem;
  padding: .75rem;
  border: 1px solid var(--gp-primary-soft-border);
  border-radius: var(--gp-radius-large);
  background: color-mix(in srgb, var(--gp-primary) 7%, var(--gp-surface-card));
}

.timeline-edit-summary-icon,
.edit-action-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  color: var(--gp-primary-text);
  background: color-mix(in srgb, var(--gp-primary) 12%, var(--gp-surface-card));
  border-radius: var(--gp-radius-medium);
}

.timeline-edit-summary-icon {
  width: 2.75rem;
  height: 2.75rem;
  font-size: 1.1rem;
}

.timeline-edit-summary > div {
  min-width: 0;
  display: grid;
  gap: .18rem;
}

.timeline-edit-summary span:not(.timeline-edit-summary-icon) {
  color: var(--gp-text-secondary);
  font-size: .72rem;
  font-weight: 700;
  letter-spacing: .06em;
  text-transform: uppercase;
}

.timeline-edit-summary strong {
  overflow: hidden;
  font-size: .94rem;
  line-height: 1.35;
  overflow-wrap: anywhere;
  white-space: normal;
}

.edit-action-section + .edit-action-section {
  margin-top: 1.6rem;
}

.edit-action-section h3 {
  margin: 0 0 .55rem;
  color: var(--gp-text-secondary);
  font-size: .76rem;
  font-weight: 700;
  letter-spacing: .06em;
  text-transform: uppercase;
}

.edit-action-row {
  width: 100%;
  min-height: 3.25rem;
  display: grid;
  grid-template-columns: 2.15rem minmax(0, 1fr) auto;
  gap: .7rem;
  align-items: center;
  margin: 0 0 .4rem;
  padding: .55rem .7rem;
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-card);
  color: var(--gp-text-primary);
  font: inherit;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  transition: background-color .15s ease, border-color .15s ease, box-shadow .15s ease, transform .15s ease;
}

.edit-action-row:hover {
  border-color: color-mix(in srgb, var(--gp-primary) 45%, var(--gp-border));
  background: var(--gp-surface-hover);
  box-shadow: var(--gp-shadow-subtle);
  transform: translateY(-1px);
}

.edit-action-row:focus-visible,
.drawer-back:focus-visible {
  outline: 2px solid var(--gp-primary);
  outline-offset: 2px;
}

.edit-action-icon {
  width: 2.15rem;
  height: 2.15rem;
}

.edit-action-chevron {
  color: var(--gp-text-muted);
  font-size: .72rem;
}

.drawer-actions {
  display: flex;
  gap: .55rem;
  flex-wrap: wrap;
}

.edit-action-row.is-danger .edit-action-icon,
.edit-action-row.is-danger .edit-action-text > span {
  color: var(--gp-danger-text);
}

.edit-action-row.is-danger .edit-action-icon {
  background: var(--gp-danger-soft);
}

.timeline-edit-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.edit-form-heading {
  display: flex;
  align-items: center;
  gap: .7rem;
  padding-bottom: .85rem;
  border-bottom: 1px solid var(--gp-border);
}

.edit-form-heading h3 {
  margin: 0;
  font-size: 1.05rem;
}

.timeline-edit-form label {
  display: grid;
  gap: .4rem;
  color: var(--gp-text-secondary);
  font-size: .82rem;
  font-weight: 650;
}

.timeline-edit-form input,
.timeline-edit-form .p-inputtext,
.timeline-edit-form .p-select,
.timeline-edit-form .p-datepicker {
  width: 100%;
}

.timeline-edit-form input:focus-visible {
  outline: none;
  border-color: var(--gp-primary);
  box-shadow: var(--gp-focus-ring);
}

.timeline-edit-form small {
  color: var(--gp-text-secondary);
  line-height: 1.45;
}

.timeline-edit-form small.edit-field-error {
  color: var(--gp-danger-text);
  font-weight: 600;
}

.edit-action-text {
  min-width: 0;
  display: grid;
  gap: .15rem;
}

.edit-action-text small {
  color: var(--gp-text-secondary);
  font-size: .76rem;
  font-weight: 500;
  line-height: 1.35;
}

.merge-choices {
  display: grid;
  gap: .55rem;
}

.merge-choice {
  display: grid;
  grid-template-columns: 1.25rem minmax(0, 1fr);
  gap: .65rem;
  align-items: start;
  padding: .75rem .85rem;
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-card);
  color: var(--gp-text-primary);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.merge-choice > i {
  margin-top: .15rem;
  color: var(--gp-text-muted);
}

.merge-choice:hover,
.merge-choice.is-selected {
  border-color: var(--gp-primary);
  background: color-mix(in srgb, var(--gp-primary) 7%, var(--gp-surface-card));
}

.merge-choice.is-selected > i {
  color: var(--gp-primary-text);
}

.merge-choice:focus-visible {
  outline: 2px solid var(--gp-primary);
  outline-offset: 2px;
}

.merge-choice-text {
  min-width: 0;
  display: grid;
  gap: .2rem;
}

.merge-choice-text span {
  color: var(--gp-text-secondary);
  font-size: .85rem;
}

.merge-choice-text small {
  color: var(--gp-text-muted);
  font-size: .78rem;
  line-height: 1.4;
}

.edit-question {
  margin: .25rem 0 0;
  color: var(--gp-text-primary);
  font-size: .95rem;
}

.drawer-back {
  min-height: 2.25rem;
  display: inline-flex;
  align-items: center;
  gap: .45rem;
  align-self: flex-start;
  padding: .35rem .55rem;
  border: 0;
  border-radius: var(--gp-radius-medium);
  background: transparent;
  color: var(--gp-primary-text);
  font: inherit;
  font-weight: 650;
  cursor: pointer;
}

.drawer-back:hover {
  background: var(--gp-surface-hover);
}

.edit-step { margin: 0; padding: .65rem .75rem; border-radius: var(--gp-radius-small); background: var(--gp-primary-soft); color: var(--gp-primary-text); font-size: .85rem; }

.edit-preview {
  display: grid;
  gap: .55rem;
  padding: .9rem;
  border: 1px solid var(--gp-info-border);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-info-soft);
}

.edit-preview h4 {
  margin: 0;
  color: var(--gp-info-text);
  font-size: .82rem;
}

.edit-preview-event {
  display: grid;
  gap: .15rem;
  padding: .55rem .65rem;
  border-radius: var(--gp-radius-small);
  background: var(--gp-surface-card);
  color: var(--gp-text-secondary);
  font-size: .78rem;
  line-height: 1.45;
}

.edit-error {
  display: flex;
  align-items: flex-start;
  gap: .5rem;
  margin: 0;
  padding: .75rem;
  border: 1px solid var(--gp-danger-border);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-danger-soft);
  color: var(--gp-danger-text);
  font-size: .82rem;
  line-height: 1.4;
}

 .timeline-edit-drawer .p-drawer-footer {
  padding: .85rem 1.25rem;
  border-top: 1px solid var(--gp-border);
  background: var(--gp-surface-card);
}
.drawer-actions { justify-content: flex-end; }


@media (max-width: 640px) {
  .timeline-edit-drawer {
    width: 100vw !important;
    max-width: none !important;
    height: 100dvh !important;
    border-radius: 0 !important;
  }

  .timeline-edit-drawer .p-drawer-header {
    padding: .9rem 1rem .75rem;
  }

  .timeline-edit-content {
    padding: 1rem;
  }

  .timeline-edit-summary {
    margin-bottom: 1.2rem;
  }

  .edit-action-row {
    min-height: 3.5rem;
  }

  .drawer-actions {
    bottom: -1rem;
    margin: auto -1rem -1rem;
    padding: .8rem 1rem calc(.8rem + env(safe-area-inset-bottom));
  }

  .drawer-actions .p-button {
    flex: 1 1 auto;
    min-height: 2.75rem;
  }
}
</style>
