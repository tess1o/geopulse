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
      <article v-for="edit in active" :key="edit.id" :data-edit-id="edit.id" tabindex="-1">
        <ChangeEntry :edit="edit"/>
      </article>
    </template>
    <details v-if="past.length">
      <summary>{{ t('timeline.edit.previousChanges') }} ({{ past.length }})</summary>
      <article v-for="edit in past" :key="edit.id" :data-edit-id="edit.id" tabindex="-1">
        <ChangeEntry :edit="edit"/>
      </article>
    </details>
  </section>
</template>

<script setup>
import {computed, defineComponent, h, nextTick, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Button from 'primevue/button'
import {useTimelineStore} from '@/stores/timeline'
import {useTimezone} from '@/composables/useTimezone'
import {formatApiErrorDetail} from '@/utils/apiErrorDetail'

const props = defineProps({visible: Boolean, item: {type: Object, default: null}, edits: {type: Array, default: null}, reload: Function})
const emit = defineEmits(['changed', 'loaded', 'replace-stay'])
const {t, te} = useI18n()
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
const direct = edit => props.item?.id != null && edit.target?.type === (props.item.type === 'dataGap' ? 'DATA_GAP' : props.item.type.toUpperCase()) && Number(edit.target.id) === Number(props.item.id)
const visibleEdits = computed(() => {
  if (props.edits !== null) return props.edits
  if (!props.item) return edits.value
  const start = Date.parse(props.item.timestamp || props.item.startTime)
  const end = props.item.endTime ? Date.parse(props.item.endTime) : start + (props.item.stayDuration ?? props.item.tripDuration ?? props.item.durationSeconds ?? 0) * 1000
  return edits.value.filter(edit => {
    if (direct(edit)) return true
    const attributeType = { SET_VISIT_NAME: 'stay', SET_PLACE: 'stay', SET_MOVEMENT_TYPE: 'trip' }[edit.kind]
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
      applied: members.every(value => value.applied) })
  }
  return [...groups.values()]
})
const active = computed(() => groupedEntries.value.filter(edit => edit.status === 'ACTIVE').sort((a, b) => b.seq - a.seq))
const past = computed(() => groupedEntries.value.filter(edit => edit.status !== 'ACTIVE').sort((a, b) => b.seq - a.seq))
watch(() => [props.visible, props.item], () => {
  focusedEditId.value = null
  if (props.visible && props.edits === null) load()
}, {immediate: true})

async function load() {
  const version = ++loadVersion
  loading.value = true;
  error.value = ''
  try {
    const result = props.edits !== null ? await props.reload?.() : await store.fetchTimelineEdits()
    if (version !== loadVersion) return
    if (props.edits === null) edits.value = result
    emit('loaded', visibleEdits.value)
  } catch (problem) {
    if (version === loadVersion) error.value = formatApiErrorDetail(problem, t('timeline.edit.historyFailed'))
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

function value(edit, before) {
  const data = before ? edit.beforeValues : edit.payload
  if (!data) return t('timeline.edit.previousUnavailable')
  if (edit.kind === 'SET_TIMES') return data.start && data.end ? period(data.start, data.end) : t('timeline.edit.previousUnavailable')
  if (edit.kind === 'SET_VISIT_NAME') return data.visitName || t('timeline.edit.noLabel')
  if (['SET_PLACE', 'FORCE_STAY'].includes(edit.kind)) return data.locationName || t(before ? 'timeline.edit.previousUnavailable' : 'timeline.edit.place.unnamed')
  if (['SET_MOVEMENT_TYPE', 'FORCE_TRIP'].includes(edit.kind) && !data.movementType) return t('timeline.edit.previousUnavailable')
  if (['SET_MOVEMENT_TYPE', 'FORCE_TRIP'].includes(edit.kind)) return te(`movementTypes.${data.movementType}`) ? t(`movementTypes.${data.movementType}`) : data.movementType
  if (edit.kind === 'SPLIT_STAY') return before ? period(data.start, data.end) : `${timezone.formatDateTimeDisplay(data.splitAt)} · ${data.rightLocationName || t('timeline.edit.place.unnamed')}`
  if (edit.kind === 'REMOVE_EVENT') return before ? period(data.start, data.end) : t(`timeline.edit.resolution.${{
    GAP: 'gap',
    MERGE: 'merge',
    EXTEND_PREVIOUS: 'previous',
    EXTEND_NEXT: 'next',
    DISMISS: 'dismiss'
  }[data.resolution]}`)
  if (edit.kind === 'MERGE') return before ? period(data.start, data.end) : t(`timeline.edit.${data.direction === 'PREVIOUS' ? 'previousEvent' : 'nextEvent'}`)
  return period(edit.anchorStart, edit.anchorEnd)
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

async function change(edit, restore) {
  if (busy.value) return
  busy.value = edit.id;
  error.value = '';
  conflictId.value = null
  try {
    const result = edit.groupId
      ? await (restore ? store.restoreActivityCorrection(edit.groupId) : store.revertActivityCorrection(edit.groupId))
      : await (restore ? store.restoreTimelineEdit(edit.id) : store.revertTimelineEdit(edit.id))
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
  props: {edit: Object},
  setup: entry => () => {
    const edit = entry.edit
    return h('div', [
      h('strong', edit.groupId ? t('missingActivity.group', { count: edit.members?.length || 1 }) : t(`timeline.edit.kind.${edit.kind}`)),
      h('span', {class: 'change-status'}, t(`timeline.edit.status.${edit.status}`)),
      props.item && !direct(edit) ? h('small', t('timeline.edit.relatedChange')) : null,
      h('p', {class: 'change-value'}, [h('small', t('timeline.edit.before')), h('span', value(edit, true))]),
      h('p', {class: 'change-value'}, [h('small', t('timeline.edit.savedValue')), h('span', value(edit, false))]),
      h('small', period(edit.anchorStart, edit.anchorEnd)),
      edit.groupId ? h('ul', edit.members.map(member => h('li', `${t(`timeline.edit.kind.${member.kind}`)} · ${period(member.anchorStart, member.anchorEnd)}`))) : null,
      edit.payload?.reconstruction ? h('p', t('trips.reconstructionContext.undoHint')) : null,
      edit.status === 'ACTIVE' && !edit.applied ? h('p', {role: 'status'}, t('timeline.edit.unapplied')) : null,
      edit.status === 'ACTIVE' && edit.kind === 'FORCE_STAY' && !edit.groupId && edit.target?.type === 'STAY'
        && !edit.payload?.reconstruction ? h(Button, {
          label: t('missingActivity.replaceStay'),
          text: true,
          disabled: busy.value !== null,
          onClick: () => emit('replace-stay', edit)
        }) : null,
      ['ACTIVE', 'REVERTED'].includes(edit.status) ? h(Button, {
        label: t(`timeline.edit.${edit.status === 'ACTIVE' ? 'revert' : 'restore'}`),
        icon: edit.status === 'ACTIVE' ? 'pi pi-undo' : 'pi pi-replay',
        text: true,
        disabled: busy.value !== null,
        loading: busy.value === edit.id,
        onClick: () => change(edit, edit.status === 'REVERTED')
      }) : null
    ])
  }
})
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

.timeline-changes summary {
  cursor: pointer;
  padding: .65rem 0;
}
</style>
