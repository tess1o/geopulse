<template>
  <section ref="section" class="timeline-changes" :aria-label="t('timeline.edit.yourChanges')">
    <h3>{{ t('timeline.edit.yourChanges') }}</h3>
    <p v-if="loading" role="status">{{ t('timeline.edit.loadingHistory') }}</p>
    <p v-else-if="error" role="alert">{{ error }}
      <Button :label="t('timeline.edit.retry')" text @click="load"/>
    </p>
    <Button v-if="conflictId" :label="t('timeline.edit.viewChange')" text @click="focusChange(conflictId)"/>
    <p v-if="!loading && !error && !active.length">{{ t('timeline.edit.noHistory') }}</p>
    <template v-if="!loading && active.length">
      <p class="changes-hint">{{ t('timeline.edit.undoHint') }}</p>
      <article v-for="entry in active" :key="entry.id" :data-edit-id="entry.id" tabindex="-1">
        <ChangeEntry :entry="entry"/>
      </article>
    </template>
    <details v-if="past.length">
      <summary>{{ t('timeline.edit.previousChanges') }} ({{ past.length }})</summary>
      <article v-for="entry in past" :key="entry.id" :data-edit-id="entry.id" tabindex="-1">
        <ChangeEntry :entry="entry"/>
      </article>
    </details>
  </section>
</template>

<script setup>
import {computed, defineComponent, h, nextTick, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useConfirm} from 'primevue/useconfirm'
import Button from 'primevue/button'
import {useTimelineStore} from '@/stores/timeline'
import {useTimezone} from '@/composables/useTimezone'
import {formatApiErrorDetail} from '@/utils/apiErrorDetail'

const props = defineProps({visible: Boolean, item: {type: Object, default: null}, edits: {type: Array, default: null}, reload: Function})
const emit = defineEmits(['changed', 'replace'])
const {t, te} = useI18n()
const confirm = useConfirm()
const timezone = useTimezone()
const store = useTimelineStore()
const edits = ref([])
const loading = ref(false)
const error = ref('')
const busy = ref(null)
const conflictId = ref(null)
const focusedEditId = ref(null)
const section = ref(null)
let loadVersion = 0
const period = (start, end) => `${timezone.formatDateTimeDisplay(start)} – ${timezone.formatDateTimeDisplay(end)}`
const direct = edit => props.item?.id != null && (edit.effects?.length ? edit.effects : [edit.target]).some(target => target?.type === (props.item.type === 'dataGap' ? 'DATA_GAP' : props.item.type.toUpperCase()) && Number(target.id) === Number(props.item.id))
const STATE = {inEffect: 'IN_EFFECT', undone: 'UNDONE', replaced: 'REPLACED'}
const visibleEdits = computed(() => {
  if (props.edits !== null) return props.edits
  if (!props.item) return edits.value
  const start = Date.parse(props.item.timestamp || props.item.startTime)
  const end = props.item.endTime ? Date.parse(props.item.endTime) : start + (props.item.stayDuration ?? props.item.tripDuration ?? props.item.durationSeconds ?? 0) * 1000
  return edits.value.filter(edit => {
    if (direct(edit)) return true
    const attributeType = { SET_LABEL: 'stay', SET_PLACE: 'stay', SET_TRIP_TYPE: 'trip' }[edit.op]
    if (attributeType) {
      if (edit.target || props.item.type !== attributeType) return false
      if (start === end) return Date.parse(edit.anchorStart) === start && Date.parse(edit.anchorEnd) === end
      return Date.parse(edit.anchorStart) < end && Date.parse(edit.anchorEnd) > start
    }
    return Date.parse(edit.affectedFrom || edit.anchorStart) < end && Date.parse(edit.affectedTo || edit.anchorEnd) > start
  })
})
const groupedEntries = computed(() => {
  const groups = new Map()
  const allEdits = props.edits || edits.value
  const focused = allEdits.find(edit => Number(edit.id) === Number(focusedEditId.value))
  for (const edit of focused ? [...visibleEdits.value, focused] : visibleEdits.value) {
    const key = edit.groupId || edit.id
    if (groups.has(key)) continue
    const members = edit.groupId ? allEdits.filter(value => value.groupId === edit.groupId) : [edit]
    groups.set(key, { ...edit, members,
      anchorStart: members.map(value => value.anchorStart).sort()[0],
      anchorEnd: members.map(value => value.anchorEnd).sort().at(-1),
      applied: members.some(value => value.applied) })
  }
  return [...groups.values()]
})
const active = computed(() => groupedEntries.value.filter(entry => entry.status === STATE.inEffect).sort((a, b) => b.seq - a.seq))
const past = computed(() => groupedEntries.value.filter(entry => entry.status !== STATE.inEffect).sort((a, b) => b.seq - a.seq))
watch(() => [props.visible, props.item], () => {
  focusedEditId.value = null
  if (props.visible && props.edits === null) load()
}, {immediate: true})

async function load() {
  const version = ++loadVersion
  loading.value = true;
  error.value = ''
  try {
    const result = props.edits !== null ? await props.reload?.() : await store.fetchCorrections()
    if (version !== loadVersion) return
    if (props.edits === null) edits.value = result
  } catch (problem) {
    if (version === loadVersion) error.value = formatApiErrorDetail(problem, t('timeline.edit.historyFailed'))
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

function movementLabel(value) {
  return value && te(`movementTypes.${value}`) ? t(`movementTypes.${value}`) : value
}
function resolutionLabel(resolution) {
  const key = {GAP: 'gap', MERGE: 'merge', EXTEND_PREVIOUS: 'previous', EXTEND_NEXT: 'next', DISMISS: 'dismiss'}[resolution]
  return key ? t(`timeline.edit.resolution.${key}`) : ''
}
function eventName(edit) {
  const type = edit.beforeValues?.type || edit.target?.type
  if (type === 'TRIP') return t('timeline.edit.eventName.trip')
  if (type === 'DATA_GAP') return t('timeline.edit.eventName.gap')
  return t('timeline.edit.eventName.stay')
}
/** One plain-language sentence for a correction, from its operation and stored values. */
function summary(edit) {
  const data = edit.payload || {}
  const when = period(edit.anchorStart, edit.anchorEnd)
  switch (edit.op) {
    case 'PLACE_STAY':
      // Migrated split rows carry the second half's place under the right* keys.
      return t('timeline.edit.summary.placeStay', {period: when, place: data.locationName || data.rightLocationName || t('timeline.edit.place.unnamed')})
    case 'PLACE_TRIP': {
      const from = data.startLocationName || data.locationName
      const to = data.endLocationName
      const type = movementLabel(data.movementType)
      return from || to
        ? t('timeline.edit.summary.placeTripPlaces', {period: when, from: from || t('timeline.edit.place.unnamed'), to: to || t('timeline.edit.place.unnamed'), type})
        : t('timeline.edit.summary.placeTrip', {period: when, type})
    }
    case 'CLEAR':
      return t('timeline.edit.summary.clear', {event: eventName(edit), period: when, resolution: resolutionLabel(data.resolution)})
    case 'RESIZE':
      return data.start && data.end
        ? t('timeline.edit.summary.resize', {period: period(data.start, data.end)})
        : t('timeline.edit.summary.changed', {period: when})
    case 'SET_PLACE':
      return t('timeline.edit.summary.setPlace', {place: data.locationName || t('timeline.edit.place.unnamed')})
    case 'SET_TRIP_TYPE':
      return t('timeline.edit.summary.setTripType', {type: movementLabel(data.movementType)})
    case 'SET_LABEL':
      return data.visitName
        ? t('timeline.edit.summary.setLabel', {label: data.visitName})
        : t('timeline.edit.summary.clearLabel')
    case 'SET_ROUTE':
      return t('timeline.edit.summary.setRoute', {period: when})
    case 'MERGE':
      return t('timeline.edit.summary.merge', {period: when})
    default:
      return t('timeline.edit.summary.changed', {period: when})
  }
}
function valueRows(edit) {
  return ['SET_LABEL', 'SET_PLACE', 'SET_TRIP_TYPE', 'RESIZE'].includes(edit.op)
}
function previousValue(edit) {
  const before = edit.beforeValues
  if (!before) return ''
  if (edit.op === 'SET_LABEL') return before.visitName || t('timeline.edit.noLabel')
  if (edit.op === 'SET_PLACE') return before.locationName || t('timeline.edit.previousUnavailable')
  if (edit.op === 'SET_TRIP_TYPE') return movementLabel(before.movementType)
  if (edit.op === 'RESIZE' && before.start && before.end) return period(before.start, before.end)
  return ''
}
function currentValue(edit) {
  const data = edit.payload || {}
  if (edit.op === 'SET_LABEL') return data.visitName || t('timeline.edit.noLabel')
  if (edit.op === 'SET_PLACE') return data.locationName || t('timeline.edit.place.unnamed')
  if (edit.op === 'SET_TRIP_TYPE') return movementLabel(data.movementType)
  if (edit.op === 'RESIZE' && data.start && data.end) return period(data.start, data.end)
  return ''
}

async function focusChange(id) {
  focusedEditId.value = id
  await nextTick()
  const groupEntry = groupedEntries.value.find(edit => edit.members.some(member => Number(member.id) === Number(id)))
  const entry = section.value?.querySelector(`[data-edit-id="${groupEntry?.id}"]`)
  const details = entry?.closest('details')
  if (details) details.open = true
  entry?.focus()
  entry?.scrollIntoView?.({block: 'nearest'})
}

const allEdits = () => props.edits || edits.value
// A later correction that lists an earlier one as a prerequisite cannot be replayed without it. Undo them together
// rather than making the user find and undo each one first; a group moves as one unit.
function dependentsOf(entry) {
  const all = allEdits()
  const members = entry.members?.length ? entry.members : [entry]
  const memberIds = new Set(members.map(member => Number(member.id)))
  const sequences = new Set(members.map(member => Number(member.seq)))
  const dependents = new Map()
  let changed = true
  while (changed) {
    changed = false
    for (const edit of all) {
      if (edit.status !== STATE.inEffect || memberIds.has(Number(edit.id)) || dependents.has(Number(edit.id))) continue
      const prerequisites = (edit.beforeValues?.prerequisiteSequences || []).map(Number)
      if (!prerequisites.some(sequence => sequences.has(sequence))) continue
      const group = edit.groupId
        ? all.filter(member => member.groupId === edit.groupId && member.status === STATE.inEffect)
        : [edit]
      let added = false
      for (const member of group) {
        if (memberIds.has(Number(member.id))) continue
        if (!dependents.has(Number(member.id))) {
          dependents.set(Number(member.id), member)
          added = true
        }
        sequences.add(Number(member.seq))
      }
      if (added) changed = true
    }
  }
  return [...dependents.values()]
}

function confirmCascade(entry, dependents) {
  return new Promise(resolve => confirm.require({
    header: t('timeline.edit.undoDependents.header'),
    message: t('timeline.edit.undoDependents.message', {count: dependents.length}),
    icon: 'pi pi-exclamation-triangle',
    accept: () => resolve(true),
    reject: () => resolve(false)
  }))
}

async function change(edit, restore) {
  if (busy.value || !edit.groupId) return
  let cascade = false
  if (!restore) {
    const dependents = dependentsOf(edit)
    if (dependents.length) {
      if (!await confirmCascade(edit, dependents)) return
      cascade = true
    }
  }
  busy.value = edit.id;
  error.value = '';
  conflictId.value = null
  try {
    const result = await (restore ? store.restoreCorrection(edit.groupId) : store.undoCorrection(edit.groupId, cascade))
    const finish = async () => {
      busy.value = null;
      await load()
    }
    emit('changed', {...result, operation: restore ? 'redo' : 'undo', onUpdated: finish, onUpdateFailed: finish})
    if (!result.timelineJobId || result.timelineJobQueued) await finish()
  } catch (problem) {
    error.value = formatApiErrorDetail(problem, t('timeline.edit.saveFailed'));
    conflictId.value = problem?.parameters?.conflictingEditId || null;
    busy.value = null
  }
}

const ChangeEntry = defineComponent({
  props: {entry: Object},
  setup: entry => () => {
    const edit = entry.entry
    const undone = edit.status === STATE.undone
    const replaced = edit.status === STATE.replaced
    const before = previousValue(edit)
    const current = currentValue(edit)
    return h('div', [
      h('strong', summary(edit)),
      h('span', {class: 'change-status'}, t(`timeline.edit.state.${statusKey(edit.status)}`)),
      props.item && !direct(edit) ? h('small', t('timeline.edit.relatedChange')) : null,
      valueRows(edit) && (before || current)
        ? h('p', {class: 'change-value'}, [
            h('small', t('timeline.edit.before')), h('span', before || t('timeline.edit.previousUnavailable')),
            h('small', t('timeline.edit.now')), h('span', current || t('timeline.edit.previousUnavailable'))
          ])
        : null,
      h('small', period(edit.anchorStart, edit.anchorEnd)),
      edit.groupId && edit.members?.length > 1 ? h('small', t('missingActivity.group', {count: edit.members.length})) : null,
      edit.groupId && edit.members?.length > 1
        ? h('ul', edit.members.map(member => h('li', summary(member))))
        : null,
      edit.payload?.reconstruction ? h('p', t('trips.reconstructionContext.undoHint')) : null,
      replaced ? h('p', {role: 'status'}, t('timeline.edit.replacedBy')) : null,
      edit.status === STATE.inEffect && !edit.applied ? h('p', {role: 'status'}, t('timeline.edit.dormant')) : null,
      h('div', {class: 'change-actions'}, [
        repeatable(edit) ? h(Button, {
          label: t('timeline.edit.replace'),
          icon: 'pi pi-refresh',
          text: true,
          disabled: busy.value !== null,
          onClick: () => emit('replace', edit)
        }) : null,
        edit.status === STATE.inEffect || undone ? h(Button, {
          label: t(`timeline.edit.${undone ? 'restore' : 'undo'}`),
          icon: undone ? 'pi pi-replay' : 'pi pi-undo',
          text: true,
          disabled: busy.value !== null || !edit.groupId,
          loading: busy.value === edit.id,
          onClick: () => change(edit, undone)
        }) : null
      ])
    ])
  }
})
function repeatable(edit) {
  return edit.status === STATE.inEffect && ['PLACE_STAY', 'PLACE_TRIP'].includes(edit.op)
}
function statusKey(status) {
  return status === STATE.undone ? 'undone' : status === STATE.replaced ? 'replaced' : 'inEffect'
}
defineExpose({load, change, focusChange})
</script>

<style>
.timeline-changes h3 {
  font-size: .9rem;
}

.timeline-changes article {
  margin: .65rem 0;
  padding: .8rem;
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-medium);
  overflow-wrap: anywhere;
}

.timeline-changes article strong {
  color: var(--gp-text-primary);
}

.timeline-changes small, .timeline-changes .changes-hint {
  display: block;
  color: var(--gp-text-secondary);
  font-size: .8rem;
}

.timeline-changes p {
  margin: .5rem 0;
}

.timeline-changes .change-status {
  float: right;
  font-size: .75rem;
  color: var(--gp-text-secondary);
}

.timeline-changes .change-value {
  display: grid;
  grid-template-columns: 4.5rem minmax(0, 1fr);
  gap: .5rem;
  font-size: .85rem;
}

.timeline-changes .change-value small {
  padding-top: .1rem;
}

.timeline-changes .change-actions {
  display: flex;
  justify-content: flex-end;
  gap: .25rem;
}

.timeline-changes summary {
  cursor: pointer;
  padding: .65rem 0;
}
</style>
