<template>
  <section class="gps-picker-panel" :aria-label="t('missingActivity.picker.listLabel')">
    <header class="gps-picker-header">
      <strong v-if="timesEditable" class="gps-picker-selection" aria-live="polite">
        <span class="gps-picker-boundary gps-picker-boundary--from">{{ t('missingActivity.from') }} {{ formatInstant(startTime) }}</span>
        <span aria-hidden="true">→</span>
        <span class="gps-picker-boundary gps-picker-boundary--to">{{ t('missingActivity.to') }} {{ formatInstant(endTime) }}</span>
        <span v-if="selectedSeconds !== null" class="gps-picker-selected-duration">{{ formatPickerDuration(selectedSeconds) }}</span>
      </strong>
      <span v-if="points.length">{{ t('missingActivity.picker.title', { count: points.length }, points.length) }} · {{ formatTime(points[0]) }} – {{ formatTime(points.at(-1)) }}</span>
    </header>

    <GpsTimeStrip v-if="timesEditable && points.length > 1" class="gps-picker-strip" :other-ranges="otherRanges" :points="points" :range="range" :start-time="startTime" :end-time="endTime" :visible="visible"
      :inspected-index="inspectedIndex" :hovered-index="hoveredIndex"
      @update:range="emit('update:range', $event)" @inspect="emit('inspect', $event)" @hover="emit('hover', $event)" />

    <ol ref="list" class="gps-picker-list" @mouseleave="emit('hover', null)">
      <li v-if="hiddenBefore" class="gps-picker-hidden">
        <span>{{ t('missingActivity.picker.earlierHidden', { count: hiddenBefore }, hiddenBefore) }}</span>
        <Button :label="t('missingActivity.picker.show')" text size="small" @click="emit('expand', 'before')" />
      </li>
      <template v-for="point in visiblePoints" :key="point.index">
        <li v-if="point.gapBefore" class="gps-picker-gap" role="separator">
          {{ t('missingActivity.picker.gap', { duration: formatPickerDuration(point.secondsSincePrevious) }) }}
        </li>
        <li :class="['gps-picker-row', {
          'is-inspected': point.index === inspectedIndex,
          'is-hovered': point.index === hoveredIndex,
          'is-in-range': isInGpsRange(point.index, range)
        }]" :data-point-index="point.index">
          <button type="button" class="gps-picker-row-main" :aria-pressed="point.index === inspectedIndex"
            :aria-label="t('missingActivity.picker.pointLabel', { number: point.number, time: formatTime(point) })"
            @click="emit('inspect', point.index)" @mouseenter="emit('hover', point.index)" @focus="emit('hover', point.index)">
            <span class="gps-picker-number" :style="{ '--gps-picker-color': point.color }">{{ point.number }}</span>
            <span class="gps-picker-time">{{ formatTime(point) }}</span>
            <span class="gps-picker-tags">
              <span v-if="point.index === range.from" class="gps-picker-tag gps-picker-tag--from">{{ t('missingActivity.from') }}</span>
              <span v-if="point.index === range.to" class="gps-picker-tag gps-picker-tag--to">{{ t('missingActivity.to') }}</span>
            </span>
            <span class="gps-picker-meta">
              <span v-if="point.secondsSincePrevious !== null && !point.gapBefore">{{ t('missingActivity.picker.sincePrevious', { duration: formatPickerDuration(point.secondsSincePrevious) }) }}</span>
              <span v-if="Number.isFinite(Number(point.velocity))">{{ formatSpeed(point.velocity) }}</span>
              <span v-if="Number.isFinite(Number(point.battery))">{{ t('missingActivity.picker.battery', { value: Math.round(Number(point.battery)) }) }}</span>
            </span>
          </button>
          <div v-if="point.index === inspectedIndex && (timesEditable || placeActions.length)" class="gps-picker-actions">
            <template v-if="timesEditable">
              <Button :label="t('missingActivity.picker.setFrom')" size="small" :outlined="range.from !== point.index"
                :disabled="point.index === points.length - 1" @click="setBoundary('from', point.index)" />
              <Button :label="t('missingActivity.picker.setTo')" size="small" :outlined="range.to !== point.index"
                :disabled="point.index === 0" @click="setBoundary('to', point.index)" />
            </template>
            <Button v-for="action in placeActions" :key="action.key" :label="action.label" icon="pi pi-map-marker" size="small" outlined
              @click="emit('place', action.key, point.index)" />
          </div>
        </li>
      </template>
      <li v-if="hiddenAfter" class="gps-picker-hidden">
        <span>{{ t('missingActivity.picker.laterHidden', { count: hiddenAfter }, hiddenAfter) }}</span>
        <Button :label="t('missingActivity.picker.show')" text size="small" @click="emit('expand', 'after')" />
      </li>
    </ol>
  </section>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import GpsTimeStrip from './GpsTimeStrip.vue'
import { useTimezone } from '@/composables/useTimezone'
import { formatSpeed } from '@/utils/calculationsHelpers'
import { formatGpsPickerDuration, isInGpsRange, setGpsBoundary } from '@/utils/gpsTimePicker'

const props = defineProps({
  points: { type: Array, default: () => [] },
  range: { type: Object, default: () => ({ from: null, to: null }) },
  // The activity's From and To (ms), which may sit between recorded points when typed by hand.
  startTime: { type: Number, default: null },
  endTime: { type: Number, default: null },
  // Inclusive indices of the points listed and drawn: From to To plus context, unless a side was expanded.
  visible: { type: Object, default: null },
  // False when editing a trip's route: its times are fixed, so only place actions are offered.
  timesEditable: { type: Boolean, default: true },
  // [{ key, label }]: places a selected point can fill in, e.g. the stay location or the trip start.
  placeActions: { type: Array, default: () => [] },
  otherRanges: { type: Array, default: () => [] },
  inspectedIndex: { type: Number, default: null },
  hoveredIndex: { type: Number, default: null }
})
const emit = defineEmits(['update:range', 'inspect', 'hover', 'expand', 'place'])
const { t } = useI18n()
const timezone = useTimezone()
const formatPickerDuration = formatGpsPickerDuration
const list = ref(null)

const spansDays = computed(() => props.points.length > 1
  && timezone.formatDateDisplay(props.points[0].timestamp) !== timezone.formatDateDisplay(props.points.at(-1).timestamp))
const formatTime = point => {
  if (!point) return ''
  const time = timezone.formatTime(point.timestamp, { withSeconds: true })
  return spansDays.value ? `${timezone.formatDateDisplay(point.timestamp)} ${time}` : time
}

const shown = computed(() => props.visible || { from: 0, to: props.points.length - 1 })
const visiblePoints = computed(() => props.points.slice(shown.value.from, shown.value.to + 1))
const hiddenBefore = computed(() => Math.max(shown.value.from, 0))
const hiddenAfter = computed(() => Math.max(props.points.length - 1 - shown.value.to, 0))
const formatInstant = time => (time === null ? t('missingActivity.picker.notChosen') : formatTime({ timestamp: new Date(time).toISOString() }))
const selectedSeconds = computed(() => (props.startTime !== null && props.endTime !== null && props.endTime > props.startTime
  ? (props.endTime - props.startTime) / 1000 : null))
function setBoundary(boundary, index) {
  emit('update:range', setGpsBoundary(props.range, boundary, index))
}

// Keep the row for the point under the cursor on the map, or just chosen there, in view.
const reveal = index => nextTick(() => {
  if (!Number.isInteger(index)) return
  list.value?.querySelector(`[data-point-index="${index}"]`)?.scrollIntoView?.({ block: 'nearest', behavior: 'auto' })
})
watch(() => props.inspectedIndex, reveal)
watch(() => props.hoveredIndex, reveal)
watch(() => props.range.from, reveal)
watch(() => props.range.to, reveal)
</script>

<style scoped>
.gps-picker-panel { display: flex; flex-direction: column; min-width: 0; min-height: 0; height: 100%; overflow: hidden; padding: .75rem;
  background: var(--gp-map-popup-background); border: 1px solid var(--gp-map-popup-border); border-radius: var(--gp-radius-medium); }
.gps-picker-header { display: grid; gap: .125rem; flex-shrink: 0; }
.gps-picker-header > span { color: var(--gp-text-secondary); font-size: .8rem; }
.gps-picker-selection { display: flex; flex-wrap: wrap; align-items: center; gap: .25rem .5rem; font-variant-numeric: tabular-nums; }
.gps-picker-boundary { padding-left: .4rem; border-left: 3px solid; }
.gps-picker-boundary--from { border-color: #15803d; }
.gps-picker-boundary--to { border-color: #b91c1c; }
.gps-picker-selected-duration { color: var(--gp-text-secondary); font-weight: 600; }
.gps-picker-strip { flex-shrink: 0; }
.gps-picker-list { list-style: none; margin: .75rem 0 0; padding: 0; overflow: auto; flex: 1; min-height: 0; border-top: 1px solid var(--gp-border); }
.gps-picker-row { border-bottom: 1px solid var(--gp-border); content-visibility: auto; contain-intrinsic-size: auto 3rem; }
.gps-picker-row.is-in-range { background: color-mix(in srgb, #2563eb 7%, transparent); }
.gps-picker-row.is-hovered, .gps-picker-row.is-inspected { background: color-mix(in srgb, #2563eb 16%, transparent); }
.gps-picker-row.is-inspected { box-shadow: inset 3px 0 0 #2563eb; content-visibility: visible; }
.gps-picker-row-main { display: grid; grid-template-columns: auto auto 1fr; grid-template-areas: 'number time tags' 'number meta meta';
  align-items: center; column-gap: .5rem; row-gap: .125rem; width: 100%; padding: .4rem .5rem; border: 0; background: none;
  color: inherit; font: inherit; text-align: left; cursor: pointer; }
.gps-picker-row-main:focus-visible { outline: 2px solid var(--gp-primary); outline-offset: -2px; }
.gps-picker-number { grid-area: number; display: inline-grid; place-items: center; min-width: 1.75rem; height: 1.5rem; padding: 0 .25rem;
  border-radius: 999px; background: var(--gps-picker-color); color: #ffffff; font-size: .75rem; font-weight: 700; }
.gps-picker-time { grid-area: time; font-weight: 600; font-size: .875rem; font-variant-numeric: tabular-nums; }
.gps-picker-tags { grid-area: tags; display: flex; gap: .25rem; }
.gps-picker-tag { padding: 0 .4rem; border-radius: .3rem; color: #ffffff; font-size: .7rem; font-weight: 700; line-height: 1.2rem; }
.gps-picker-tag--from { background: #15803d; }
.gps-picker-tag--to { background: #b91c1c; }
.gps-picker-meta { grid-area: meta; display: flex; flex-wrap: wrap; gap: .25rem .75rem; color: var(--gp-text-secondary); font-size: .75rem; }
.gps-picker-actions { display: flex; flex-wrap: wrap; gap: .5rem; padding: 0 .5rem .5rem 2.75rem; }
.gps-picker-hidden { display: flex; align-items: center; justify-content: space-between; gap: .5rem; padding: .25rem .5rem;
  border-bottom: 1px solid var(--gp-border); color: var(--gp-text-secondary); font-size: .75rem; background: var(--gp-surface-ground); }
.gps-picker-gap { padding: .35rem .5rem; border-bottom: 1px solid var(--gp-border); color: var(--gp-text-secondary); font-size: .75rem;
  font-style: italic; text-align: center; background: repeating-linear-gradient(-45deg, transparent 0 6px, color-mix(in srgb, var(--gp-text-secondary) 8%, transparent) 6px 12px); }
</style>
