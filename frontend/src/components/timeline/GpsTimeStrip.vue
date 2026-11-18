<template>
  <div ref="track" class="gps-strip" :aria-label="t('missingActivity.picker.timeRange')" role="group"
    @pointerdown="pickAt" @pointermove="hoverAt" @pointerleave="!dragging && emit('hover', null)">
    <div class="gps-strip-rail" />
    <div v-for="(other, index) in otherBands" :key="`other-${index}`" class="gps-strip-other" :style="{ left: `${other.left}%`, width: `${other.width}%` }" />
    <div v-if="selection" class="gps-strip-selection" :style="{ left: `${selection.left}%`, width: `${selection.width}%` }" />
    <span v-for="gap in gapLabels" :key="`gap-${gap.index}`" class="gps-strip-gap" :style="{ left: `${gap.left}%`, width: `${gap.width}%` }">
      {{ gap.label }}
    </span>
    <span v-for="point in points" :key="point.index" class="gps-strip-tick"
      :class="{ 'is-emphasised': point.index === hoveredIndex || point.index === inspectedIndex, 'is-hidden': !isShown(point.index) }"
      :style="{ left: `${position(point.time)}%`, '--gps-picker-color': point.color }" />
    <button v-for="boundary in ['from', 'to']" :key="boundary" type="button" role="slider"
      :class="['gps-strip-handle', `gps-strip-handle--${boundary}`, { 'is-unset': handleTime(boundary) === null }]"
      :style="{ left: `${handlePosition(boundary)}%` }"
      :aria-label="t(`missingActivity.${boundary}`)" :aria-valuemin="0" :aria-valuemax="points.length - 1"
      :aria-valuenow="range[boundary] ?? undefined" :aria-valuetext="handleText(boundary)"
      @pointerdown.stop="startDrag(boundary, $event)" @keydown="moveWithKey(boundary, $event)">
      <span class="gps-strip-handle-label">{{ t(`missingActivity.${boundary}`) }}</span>
    </button>
  </div>
</template>

<script setup>
// The recorded points along a bar proportional to real time: pauses show up as empty, labelled stretches.
// Two handles choose From and To and snap to recorded points; clicking the bar selects the nearest point.
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useTimezone } from '@/composables/useTimezone'
import { formatGpsPickerDuration, nearestGpsPointIndex, setGpsBoundary } from '@/utils/gpsTimePicker'

const props = defineProps({
  points: { type: Array, default: () => [] },
  range: { type: Object, default: () => ({ from: null, to: null }) },
  startTime: { type: Number, default: null },
  endTime: { type: Number, default: null },
  inspectedIndex: { type: Number, default: null },
  hoveredIndex: { type: Number, default: null },
  // Points outside this inclusive index window are hidden from the list and map, so they are dimmed here.
  visible: { type: Object, default: null },
  // Times of the dialog's other activities ([{ start, end }] in ms), shown faintly so overlaps are visible.
  otherRanges: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:range', 'inspect', 'hover'])
const { t } = useI18n()
const timezone = useTimezone()
const track = ref(null)
const dragging = ref(null)

// Gap labels need room; narrower pauses still show as an empty stretch of the bar.
const MIN_LABEL_WIDTH_PERCENT = 14

const domain = computed(() => {
  const times = [props.points[0]?.time, props.points.at(-1)?.time, props.startTime, props.endTime].filter(Number.isFinite)
  const start = Math.min(...times), end = Math.max(...times)
  return { start, span: Math.max(end - start, 1) }
})
const shown = computed(() => props.visible || { from: 0, to: props.points.length - 1 })
const isShown = index => index >= shown.value.from && index <= shown.value.to
const position = time => ((time - domain.value.start) / domain.value.span) * 100
const handleTime = boundary => (boundary === 'from' ? props.startTime : props.endTime)
const handlePosition = boundary => {
  const time = handleTime(boundary)
  return time === null ? (boundary === 'from' ? 0 : 100) : position(time)
}
const handleText = boundary => {
  const time = handleTime(boundary)
  return time === null ? t('missingActivity.picker.notChosen') : timezone.formatTime(new Date(time).toISOString(), { withSeconds: true })
}
const selection = computed(() => {
  if (props.startTime === null || props.endTime === null || props.endTime <= props.startTime) return null
  const left = position(props.startTime)
  return { left, width: position(props.endTime) - left }
})
const otherBands = computed(() => props.otherRanges.map(({ start, end }) => {
  const left = Math.max(position(start), 0)
  return { left, width: Math.max(Math.min(position(end), 100) - left, 0) }
}).filter(band => band.width > 0))
const gapLabels = computed(() => props.points.filter(point => point.gapBefore).map(point => {
  const left = position(props.points[point.index - 1].time)
  return { index: point.index, left, width: position(point.time) - left, label: formatGpsPickerDuration(point.secondsSincePrevious) }
}).filter(gap => gap.width >= MIN_LABEL_WIDTH_PERCENT))

const timeAt = clientX => {
  const rect = track.value?.getBoundingClientRect?.()
  if (!rect?.width) return null
  const fraction = Math.min(Math.max((clientX - rect.left) / rect.width, 0), 1)
  return domain.value.start + fraction * domain.value.span
}
// From stays before To: each handle only snaps to points on its own side of the other one.
const limits = boundary => (boundary === 'from'
  ? { min: 0, max: props.range.to === null ? props.points.length - 2 : props.range.to - 1 }
  : { min: props.range.from === null ? 1 : props.range.from + 1, max: props.points.length - 1 })
const setBoundary = (boundary, index) => {
  if (index === null || index === props.range[boundary]) return
  emit('update:range', setGpsBoundary(props.range, boundary, index))
}

// Clicking and pointing only reach points that are shown, so nothing is selected out of sight.
const shownLimits = () => ({ min: shown.value.from, max: shown.value.to })
function pickAt(event) {
  const index = nearestGpsPointIndex(props.points, timeAt(event.clientX), shownLimits())
  if (index !== null) emit('inspect', index)
}
function hoverAt(event) {
  if (dragging.value) {
    const index = nearestGpsPointIndex(props.points, timeAt(event.clientX), limits(dragging.value))
    setBoundary(dragging.value, index)
    if (index !== null) emit('hover', index)
    return
  }
  const index = nearestGpsPointIndex(props.points, timeAt(event.clientX), shownLimits())
  if (index !== null && index !== props.hoveredIndex) emit('hover', index)
}
function startDrag(boundary, event) {
  dragging.value = boundary
  track.value?.setPointerCapture?.(event.pointerId)
  const stop = () => {
    dragging.value = null
    track.value?.releasePointerCapture?.(event.pointerId)
    window.removeEventListener('pointerup', stop)
    window.removeEventListener('pointercancel', stop)
  }
  window.addEventListener('pointerup', stop)
  window.addEventListener('pointercancel', stop)
}
function moveWithKey(boundary, event) {
  const { min, max } = limits(boundary)
  const current = props.range[boundary] ?? (boundary === 'from' ? min - 1 : max + 1)
  const next = { ArrowLeft: current - 1, ArrowDown: current - 1, ArrowRight: current + 1, ArrowUp: current + 1, Home: min, End: max }[event.key]
  if (next === undefined) return
  event.preventDefault()
  setBoundary(boundary, Math.min(Math.max(next, min), max))
}
</script>

<style scoped>
.gps-strip { position: relative; height: 3.25rem; margin: 1.5rem .75rem .25rem; touch-action: none; cursor: pointer; user-select: none; }
.gps-strip-rail { position: absolute; left: 0; right: 0; top: 1.5rem; height: 2px; background: var(--gp-border); }
.gps-strip-selection { position: absolute; top: .75rem; height: 1.6rem; border-radius: .3rem; background: color-mix(in srgb, #2563eb 18%, transparent);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, #2563eb 45%, transparent); }
.gps-strip-other { position: absolute; top: .75rem; height: 1.6rem; border-radius: .3rem; background: repeating-linear-gradient(-45deg,
  color-mix(in srgb, var(--gp-text-secondary) 14%, transparent) 0 4px, transparent 4px 8px); pointer-events: none; }
.gps-strip-tick { position: absolute; top: .95rem; width: 2px; height: 1.2rem; margin-left: -1px; border-radius: 1px; background: var(--gps-picker-color); pointer-events: none; }
.gps-strip-tick.is-hidden { opacity: .25; }
.gps-strip-tick.is-emphasised { top: .6rem; height: 1.9rem; width: 4px; margin-left: -2px; background: #0f172a; z-index: 1; }
.gps-strip-gap { position: absolute; top: 2.15rem; overflow: hidden; color: var(--gp-text-secondary); font-size: .68rem; font-style: italic;
  text-align: center; white-space: nowrap; text-overflow: ellipsis; pointer-events: none; }
.gps-strip-handle { position: absolute; top: .5rem; width: 14px; height: 2.1rem; margin-left: -7px; padding: 0; border: 2px solid #ffffff;
  border-radius: 4px; cursor: ew-resize; z-index: 2; box-shadow: 0 1px 4px rgba(15, 23, 42, .35); }
.gps-strip-handle--from { background: #15803d; }
.gps-strip-handle--to { background: #b91c1c; }
.gps-strip-handle.is-unset { opacity: .45; }
.gps-strip-handle:focus-visible { outline: 2px solid var(--gp-primary); outline-offset: 2px; }
.gps-strip-handle-label { position: absolute; bottom: calc(100% + 2px); color: var(--gp-text-secondary); font-size: .65rem; font-weight: 700; white-space: nowrap; }
/* Labels point away from each other so they stay readable when the handles are close together. */
.gps-strip-handle--from .gps-strip-handle-label { right: 0; }
.gps-strip-handle--to .gps-strip-handle-label { left: 0; }
</style>
