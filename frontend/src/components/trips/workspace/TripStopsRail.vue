<template>
  <div class="trip-rail">
    <!-- ── Header: always present, so "+ Add" is never hidden ─────────────── -->
    <div class="trip-rail-header">
      <Button
        v-if="mode === 'add'"
        icon="pi pi-arrow-left"
        text
        size="small"
        :aria-label="t('trips.stopsRail.backAria')"
        @click="mode = 'stops'"
      />
      <h3 class="trip-rail-title" :class="{ 'trip-rail-title--redundant': mode === 'stops' }">
        {{ mode === 'add' ? t('trips.stopsRail.addAPlace') : t('trips.stopsRail.stopsCount', { count: stops.length }) }}
      </h3>
      <Button
        v-if="mode === 'stops' && canEdit"
        :label="t('trips.stopsRail.add')"
        icon="pi pi-plus"
        size="small"
        @click="mode = 'add'"
      />
      <!-- Desktop only: folds the rail into a pill over the map, as the Timeline page does.
           On mobile the bottom sheet's own handle does this. -->
      <Button
        v-if="collapsible"
        icon="pi pi-chevron-right"
        text
        rounded
        size="small"
        class="trip-rail-collapse"
        :aria-label="collapseLabel"
        v-tooltip.left="collapseLabel"
        @click="$emit('collapse')"
      />
    </div>

    <!-- ── Lens switch: only once there is real data to look at ──────────── -->
    <SelectButton
      v-if="mode !== 'add' && hasActualData"
      :model-value="lens"
      :options="lensOptions"
      option-label="label"
      option-value="value"
      :allow-empty="false"
      class="trip-rail-lens"
      @update:model-value="$emit('update:lens', $event)"
    />

    <!-- ── Add state ─────────────────────────────────────────────────────── -->
    <div v-if="mode === 'add'" class="trip-rail-body">
      <TripAddStopPanel @add-stop="handleAdded" />
    </div>

    <!-- ── Timeline lens ─────────────────────────────────────────────────── -->
    <div v-else-if="lens === 'actual'" class="trip-rail-body trip-rail-actual">
      <slot name="actual" />
    </div>

    <!-- ── Plan lens: day-grouped stops ──────────────────────────────────── -->
    <div v-else ref="bodyRef" class="trip-rail-body" :class="{ 'trip-rail-body--dragging': dragging }">
      <div v-if="loading" class="trip-rail-state">
        <ProgressSpinner style="width: 32px; height: 32px" strokeWidth="4" />
      </div>

      <div v-else-if="stops.length === 0" class="trip-rail-state">
        <i class="pi pi-map-marker trip-rail-state-icon" />
        <p>{{ t('trips.stopsRail.noStopsYet') }}</p>
        <Button
          v-if="canEdit"
          :label="t('trips.stopsRail.addFirstStop')"
          icon="pi pi-plus"
          size="small"
          @click="mode = 'add'"
        />
      </div>

      <div v-else class="trip-rail-groups">
        <section
          v-for="group in localGroups"
          :key="group.key"
          class="trip-rail-group"
          :class="{ 'trip-rail-group--empty': group.items.length === 0 }"
        >
          <header class="trip-rail-group-header">
            <h4 class="trip-rail-group-title">{{ groupLabel(group) }}</h4>
            <span v-if="group.items.length" class="trip-rail-group-meta">{{ groupMeta(group) }}</span>
          </header>

          <!-- Each day is its own list sharing one Sortable group, so a stop can be dragged to
               another day as well as within its own. The whole card is the drag source: with the
               mouse a drag starts once the pointer moves (a click still focuses the stop), and on
               touch only after a long press, so swiping still scrolls the list and the sheet. The
               fallback (pointer-driven) mode is forced because native HTML5 drag cannot start on
               the card's <button> in every browser. -->
          <VueDraggable
            v-model="group.items"
            class="trip-rail-group-list"
            :group="{ name: 'trip-stops' }"
            :disabled="!canEdit"
            :animation="150"
            :delay="TOUCH_DRAG_DELAY_MS"
            :delay-on-touch-only="true"
            :touch-start-threshold="5"
            :force-fallback="true"
            filter=".trip-stop-menu"
            :prevent-on-filter="false"
            ghost-class="trip-stop--ghost"
            chosen-class="trip-stop--chosen"
            :data-empty-label="t('trips.stopsRail.dropHere')"
            @start="dragging = true"
            @end="handleDragEnd"
          >
            <article
              v-for="stop in group.items"
              :key="stop.id"
              :data-stop-id="stop.id"
              :data-visit-status="visitStatus(stop)"
              class="trip-stop"
              :class="{ 'trip-stop--selected': stop.id === selectedId, 'trip-stop--editable': canEdit }"
            >
              <!-- The travel from the previous stop belongs to the gap between two cards, so it is
                   drawn as a connector above the card rather than as a line inside it. -->
              <div v-if="legInfo(stop)" class="trip-stop-leg">
                <i :class="legInfo(stop).iconClass" aria-hidden="true" />
                <span>{{ legInfo(stop).text }}</span>
              </div>

              <div class="trip-stop-card">
                <i v-if="canEdit" class="pi pi-ellipsis-v trip-stop-grip" aria-hidden="true" />

                <button
                  type="button"
                  class="trip-stop-main"
                  @click="$emit('focus-stop', stop)"
                  @dblclick="canEdit && $emit('edit-stop', stop)"
                >
                  <!-- Priority is carried by the number badge (coloured like the stop's map marker)
                       and, for must-visit stops, a small star - not a label that competes with the
                       title. -->
                  <span class="trip-stop-title-row">
                    <span
                      class="trip-stop-sequence"
                      :class="`trip-stop-sequence--${badgeState(stop)}`"
                      :title="badgeLabel(stop)"
                      :aria-label="badgeLabel(stop)"
                      role="img"
                    >{{ sequenceMap.get(stop.id) }}</span>
                    <span class="trip-stop-title">
                      {{ stop.title }}<i
                        v-if="isMust(stop)"
                        class="pi pi-star-fill trip-stop-must"
                        aria-hidden="true"
                        v-tooltip.top="t('trips.stopsRail.mustVisit')"
                      />
                    </span>
                  </span>

                  <!-- Only states worth reading are shown; "planned, not visited yet" is the default
                       for every stop and said nothing. Visited is the green badge. -->
                  <span v-if="statusFor(stop) || stop.notes" class="trip-stop-detail">
                    <span
                      v-if="statusFor(stop)"
                      class="trip-stop-status"
                      :class="`trip-stop-status--${statusFor(stop).tone}`"
                    >{{ statusFor(stop).label }}</span>
                    <span v-if="stop.notes" class="trip-stop-notes" :title="stop.notes">{{ stop.notes }}</span>
                  </span>
                </button>

                <Button
                  v-if="canEdit"
                  icon="pi pi-ellipsis-h"
                  text
                  rounded
                  size="small"
                  class="trip-stop-menu"
                  aria-haspopup="true"
                  :aria-label="t('trips.stopsRail.stopActions')"
                  v-tooltip.top="t('trips.stopsRail.stopActions')"
                  @click="openStopMenu($event, stop)"
                />
              </div>
            </article>
          </VueDraggable>
        </section>

        <Menu ref="stopMenuRef" :model="stopMenuItems" popup />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { VueDraggable } from 'vue-draggable-plus'
import Button from 'primevue/button'
import Menu from 'primevue/menu'
import SelectButton from 'primevue/selectbutton'
import ProgressSpinner from 'primevue/progressspinner'
import TripAddStopPanel from './TripAddStopPanel.vue'
import { useTimezone } from '@/composables/useTimezone'
import { formatDistanceRounded, formatDurationCompact } from '@/utils/calculationsHelpers'
import { getTripMovementIconClass } from '@/utils/timelineIconUtils'
import {
  UNSCHEDULED_KEY,
  buildReorderPayload,
  buildSequenceMap,
  groupPlanItemsByDay,
  hasPlanCoordinates
} from '@/utils/tripPlanOrder'

const { t, locale } = useI18n()

/** Touch only: how long a press must be held before it picks a card up (a shorter touch scrolls). */
const TOUCH_DRAG_DELAY_MS = 220

const props = defineProps({
  /** Stops in plan order (see utils/tripPlanOrder). */
  stops: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  canEdit: { type: Boolean, default: false },
  /** True once the trip has real timeline data, which is when the Timeline lens is useful. */
  hasActualData: { type: Boolean, default: false },
  lens: { type: String, default: 'plan' },
  selectedId: { type: [Number, String], default: null },
  /** Set the lens without user interaction, e.g. when the trip completes. */
  initialMode: { type: String, default: 'stops' },
  /** The trip's days (ISO dates). Days without stops become drop targets while dragging. */
  tripDays: { type: Array, default: () => [] },
  /** Offer a button that hides the rail (the page collapses its split layout on `collapse`). */
  collapsible: { type: Boolean, default: false },
  /** Routed legs keyed by the id of the stop they arrive at. */
  legsByStop: { type: Map, default: () => new Map() },
  confidenceThresholds: {
    type: Object,
    default: () => ({ high: 0.9, medium: 0.75 })
  }
})

const emit = defineEmits([
  'update:lens',
  'add-stop',
  'focus-stop',
  'edit-stop',
  'delete-stop',
  'visit-override',
  'reorder',
  'collapse'
])

const timezone = useTimezone()

const mode = ref(props.initialMode)
const bodyRef = ref(null)

const lensOptions = computed(() => [
  { label: t('trips.stopsRail.lensPlan'), value: 'plan' },
  { label: t('trips.stopsRail.lensActual'), value: 'actual' }
])

const sequenceMap = computed(() => buildSequenceMap(props.stops))

// Same wording as the split layout's own toggle, so the two controls read as one.
const collapseLabel = computed(() => t('timeline.splitLayout.collapse', {
  label: t('trips.workspacePage.stopsLabel').toLowerCase()
}))

/**
 * Stops grouped by planned day, unscheduled last. The list is deliberately uncapped -
 * the previous planning panel silently truncated at 8 with no indication, so its count
 * could disagree with the table.
 *
 * The groups are a local, mutable copy because drag-and-drop edits them in place. Every trip
 * day and the unscheduled bucket are always present so Sortable has a list to drop into; empty
 * ones are hidden by CSS until a drag starts. Rebuilding is held off mid-drag, since replacing
 * the lists under Sortable would cancel the drag.
 */
const localGroups = ref([])
const dragging = ref(false)

const buildGroups = () => groupPlanItemsByDay(props.stops, {
  extraDays: props.canEdit ? props.tripDays : [],
  includeUnscheduled: props.canEdit
}).map((group) => ({ ...group, items: [...group.items] }))

watch(
  () => [props.stops, props.tripDays, props.canEdit],
  () => {
    if (!dragging.value) {
      localGroups.value = buildGroups()
    }
  },
  { immediate: true, deep: true }
)

/** "Day 2 · Fri, 2 Oct" - the day's place in the trip first, then a date that reads at a glance. */
const groupLabel = (group) => {
  if (group.key === UNSCHEDULED_KEY) return t('trips.stopsRail.unscheduled')

  // Noon UTC, formatted in UTC: a calendar date must not shift a day with the viewer's offset.
  const date = new Date(`${group.day}T12:00:00Z`)
  const dateText = Number.isNaN(date.getTime())
    ? timezone.formatDateDisplay(group.day)
    : new Intl.DateTimeFormat(locale.value, { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC' }).format(date)
  const dayIndex = props.tripDays.indexOf(group.day)
  return dayIndex >= 0 ? `${t('trips.stopsRail.dayNumber', { number: dayIndex + 1 })} · ${dateText}` : dateText
}

/** "3 stops · 4.2 km": the travel within the day, to judge whether the day is realistic. */
const groupMeta = (group) => {
  const ids = new Set(group.items.map((stop) => stop.id))
  const meters = group.items.reduce((total, stop) => {
    const leg = props.legsByStop.get(stop.id)
    return leg && ids.has(leg.fromItemId) && Number.isFinite(leg.distanceMeters) ? total + leg.distanceMeters : total
  }, 0)
  const count = t('trips.stopsRail.dayStops', { count: group.items.length }, group.items.length)
  return meters > 0 ? `${count} · ${formatDistanceRounded(meters)}` : count
}

const handleDragEnd = async () => {
  // Sortable has updated the lists' models by now, but let Vue settle before reading them.
  await nextTick()
  dragging.value = false

  const payload = buildReorderPayload(localGroups.value)
  const current = props.stops.map((stop) => ({ id: stop.id, plannedDay: stop.plannedDay || null }))
  const changed = payload.length !== current.length || payload.some((entry, index) => (
    entry.id !== current[index].id || entry.plannedDay !== current[index].plannedDay
  ))

  if (changed) {
    emit('reorder', payload)
  } else {
    localGroups.value = buildGroups()
  }
}

// A stop selected elsewhere (e.g. its map marker) is brought into view in the list.
watch(() => props.selectedId, async (id) => {
  if (id === null || id === undefined) return
  await nextTick()
  const card = bodyRef.value?.querySelector?.(`[data-stop-id="${id}"]`)
  card?.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' })
})

const stopMenuRef = ref(null)
const stopMenuStop = ref(null)

/** Everything you can do to a stop, behind one button so the card itself stays quiet. */
const stopMenuItems = computed(() => {
  const stop = stopMenuStop.value
  const override = (action) => () => emit('visit-override', { stop, action })
  return [
    { label: t('trips.stopsRail.markVisited'), icon: 'pi pi-check', command: override('CONFIRM_VISITED') },
    { label: t('trips.stopsRail.markNotVisited'), icon: 'pi pi-times', command: override('REJECT_VISIT') },
    {
      label: t('trips.stopsRail.resetToAutomatic'),
      icon: 'pi pi-refresh',
      // Only a manual decision can be reset; an automatic state has nothing to undo.
      disabled: !stop?.manualOverrideState,
      command: override('RESET_TO_AUTO')
    },
    { separator: true },
    { label: t('trips.stopsRail.editStop'), icon: 'pi pi-pencil', command: () => emit('edit-stop', stop) },
    { label: t('trips.stopsRail.deleteStop'), icon: 'pi pi-trash', class: 'trip-stop-menu-danger', command: () => emit('delete-stop', stop) }
  ]
})

const openStopMenu = (event, stop) => {
  stopMenuStop.value = stop
  stopMenuRef.value?.toggle(event)
}

const isMust = (stop) => String(stop?.priority || '').toUpperCase() === 'MUST'
// A manually rejected stop is not "visited" for display purposes, as on the map.
const isVisitedStop = (stop) => Boolean(stop?.isVisited) && stop?.manualOverrideState !== 'REJECTED'

/** Same precedence as the map marker colour: visited, then must, then optional. */
const badgeState = (stop) => {
  if (isVisitedStop(stop)) return 'visited'
  return isMust(stop) ? 'must' : 'optional'
}

const badgeLabel = (stop) => {
  const priority = isMust(stop) ? t('trips.stopsRail.mustVisit') : t('trips.stopsRail.priorityOptional')
  const parts = [t('trips.stopsRail.stopNumber', { number: sequenceMap.value.get(stop.id) }), priority]
  if (isVisitedStop(stop)) {
    // The badge is the only visible sign of a visit, so it also carries the match confidence.
    parts.push(Number.isFinite(stop.visitConfidence)
      ? `${t('trips.status.visited')} (${formatConfidence(stop.visitConfidence)})`
      : t('trips.status.visited'))
  }
  return parts.join(' · ')
}

const LEG_ICON_MOVEMENT = { WALK: 'WALK', BICYCLE: 'BICYCLE', DRIVE: 'CAR' }

const formatConfidence = (confidence) => `${Math.round(confidence * 100)}%`

/**
 * The travel into a stop from the previous one: mode, distance and time for a routed leg, and
 * distance for a straight one. Placeholder legs (drawn while routes load) carry no distance and
 * are skipped, so the connectors don't flicker.
 */
const legInfo = (stop) => {
  const leg = props.legsByStop.get(stop.id)
  if (!leg || !Number.isFinite(leg.distanceMeters)) {
    return null
  }
  const distance = formatDistanceRounded(leg.distanceMeters)
  if (leg.routed && Number.isFinite(leg.durationSeconds)) {
    return {
      iconClass: getTripMovementIconClass(LEG_ICON_MOVEMENT[leg.resolvedMode] || 'CAR'),
      text: t('trips.stopsRail.legRouted', { distance, duration: formatDurationCompact(leg.durationSeconds) })
    }
  }
  return { iconClass: 'pi pi-arrow-down', text: t('trips.stopsRail.legStraight', { distance }) }
}

/** Machine-readable visit state, for tests and styling. */
const visitStatus = (stop) => {
  if (stop?.manualOverrideState === 'REJECTED') return 'missed'
  if (isVisitedStop(stop)) return 'visited'
  if (Number.isFinite(stop?.visitConfidence) && stop.visitConfidence >= props.confidenceThresholds.medium) return 'review'
  return 'planned'
}

/** The visit state as text - only when it tells the reader something; null for the default. */
const statusFor = (stop) => {
  switch (visitStatus(stop)) {
    case 'missed':
      return { label: t('trips.status.missed'), tone: 'danger' }
    case 'review':
      return {
        label: `${t('trips.status.needsReview')} · ${formatConfidence(stop.visitConfidence)}`,
        tone: 'warn'
      }
    case 'planned':
      // Without a location the stop can never be matched automatically - worth a nudge.
      return hasPlanCoordinates(stop) ? null : { label: t('trips.stopsRail.noLocation'), tone: 'muted' }
    default:
      return null
  }
}

const handleAdded = (stop) => {
  // Returning to the list immediately is the point: you see the place land in your plan.
  mode.value = 'stops'
  emit('add-stop', stop)
}
</script>

<style scoped>
/* Absolutely positioned inside the split's side pane so the rail can never grow past it.
   With `height: 100%` the pane had no definite height to inherit, so the rail expanded
   with its content and the whole page scrolled instead of the list. */
.trip-rail {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.trip-rail-header {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  padding: var(--gp-spacing-md);
  border-bottom: 1px solid var(--gp-border);
  flex-wrap: wrap;
}

.trip-rail-title {
  margin: 0;
  flex: 1;
  font-size: 1rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--gp-text-primary);
}

.trip-rail-lens {
  margin: var(--gp-spacing-sm) var(--gp-spacing-md) 0;
}

/* Mobile: the pane is a bottom sheet whose first child is its own drag handle, so the rail
   must flow below the handle rather than cover it. Letting it flow (the pane is already a
   flex column and the handle is `flex: 0 0 auto`) gives it exactly the space the handle
   leaves, whatever the handle's computed height happens to be - a hardcoded offset would
   break as soon as the handle's label wraps. In the collapsed state the flex line has no
   space left, so the rail collapses to zero instead of painting a slice over the handle. */
@media (max-width: 768px), (max-height: 520px) and (pointer: coarse) {
  .trip-rail {
    position: static;
    flex: 1 1 auto;
    min-height: 0;
  }

  /* The sheet's drag handle already reads "Stops", so the sheet used to open with two
     stacked headings. Only the stops heading is redundant - the add-flow heading is not. */
  .trip-rail-title--redundant {
    display: none;
  }

  /* The sheet handle collapses the rail here. */
  .trip-rail-collapse {
    display: none;
  }

  /* Header collapses to just the "Add" affordance, which is never hidden. */
  .trip-rail-header {
    justify-content: flex-end;
    padding: 0 var(--gp-spacing-sm) 0;
    border-bottom: none;
  }

  .trip-rail-lens {
    margin: var(--gp-spacing-xs) var(--gp-spacing-sm) 0;
  }
}

.trip-rail-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--gp-spacing-md);
}

.trip-rail-actual {
  padding: 0;
}

.trip-rail-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--gp-spacing-sm);
  padding: var(--gp-spacing-xl) var(--gp-spacing-md);
  text-align: center;
  color: var(--gp-text-secondary);
}

.trip-rail-state-icon {
  font-size: 2rem;
}

/* A gap rather than sibling margins: hidden drop-target groups would otherwise still count. */
.trip-rail-groups {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
}

.trip-rail-group-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--gp-spacing-sm);
  margin: 0 0 var(--gp-spacing-xs);
}

.trip-rail-group-title {
  margin: 0;
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--gp-text-secondary);
}

.trip-rail-group-meta {
  flex-shrink: 0;
  font-size: 0.75rem;
  color: var(--gp-text-secondary);
  white-space: nowrap;
}

/* Empty days and the unscheduled bucket exist only as drop targets: hidden until a drag starts. */
.trip-rail-group--empty {
  display: none;
}

.trip-rail-body--dragging .trip-rail-group--empty {
  display: block;
}

.trip-rail-group-list:empty {
  min-height: 2.75rem;
  border: 1px dashed var(--gp-border);
  border-radius: var(--gp-radius-medium);
}

.trip-rail-group-list:empty::after {
  content: attr(data-empty-label);
  display: block;
  padding: var(--gp-spacing-sm);
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
  text-align: center;
}

.trip-stop + .trip-stop {
  margin-top: var(--gp-spacing-xs);
}

/* ── Connector: the travel between two cards ─────────────────────────────── */
.trip-stop-leg {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
  margin: 0 0 var(--gp-spacing-xs) 0.6875rem;
  padding: 2px 0 2px var(--gp-spacing-sm);
  border-left: 2px dotted var(--gp-border);
  font-size: 0.75rem;
  color: var(--gp-text-secondary);
}

.trip-stop-leg i {
  font-size: 0.75rem;
}

/* ── Card ─────────────────────────────────────────────────────────────────── */
.trip-stop-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
  padding: var(--gp-spacing-xs) var(--gp-spacing-xs) var(--gp-spacing-xs) var(--gp-spacing-sm);
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-card);
}

.trip-stop--selected .trip-stop-card {
  border-color: var(--gp-primary);
  box-shadow: var(--gp-shadow-card-highlighted);
}

.trip-stop--ghost .trip-stop-card {
  opacity: 0.4;
}

.trip-stop--chosen .trip-stop-card {
  box-shadow: var(--gp-shadow-dialog);
}

/* The card is the drag source; the grip only hints at it, on hover. */
.trip-stop-grip {
  position: absolute;
  left: 1px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 0.7rem;
  color: var(--gp-text-muted);
  opacity: 0;
  pointer-events: none;
}

.trip-stop-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 2px 0;
  background: none;
  border: none;
  text-align: left;
  cursor: pointer;
  color: inherit;
  font: inherit;
}

.trip-stop-title-row {
  display: flex;
  align-items: flex-start;
  gap: var(--gp-spacing-sm);
  min-width: 0;
}

.trip-stop-sequence {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.375rem;
  height: 1.375rem;
  padding: 0 4px;
  border-radius: 999px;
  font-size: 0.7rem;
  font-weight: 700;
  line-height: 1;
}

/* Optional is the default, so it stays quiet. */
.trip-stop-sequence--optional {
  border: 1px solid var(--gp-border);
  color: var(--gp-text-secondary);
}

/* Filled like the map markers; their fills don't change with the theme, so neither does the text. */
.trip-stop-sequence--must {
  background: var(--gp-danger-strong);
  color: #ffffff;
}

.trip-stop-sequence--visited {
  background: var(--gp-success-strong);
  color: #ffffff;
}

/* At most two lines, so one long name can't make a card tower over the rest. */
.trip-stop-title {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  overflow-wrap: anywhere;
  font-size: 0.95rem;
  font-weight: 600;
  line-height: 1.375rem;
}

/* Inline, so it follows the last word instead of wrapping onto a line of its own. */
.trip-stop-must {
  margin-left: 0.35em;
  font-size: 0.65rem;
  vertical-align: 0.1em;
  color: var(--gp-danger-text);
}

/* Second line, aligned under the title: an optional status, then notes cut to one line. */
.trip-stop-detail {
  display: flex;
  align-items: baseline;
  gap: var(--gp-spacing-xs);
  min-width: 0;
  padding-left: calc(1.375rem + var(--gp-spacing-sm));
  font-size: 0.8rem;
}

.trip-stop-status {
  flex-shrink: 0;
  font-weight: 600;
}

.trip-stop-status--danger {
  color: var(--gp-danger-text);
}

.trip-stop-status--warn {
  color: var(--gp-warning-text);
}

.trip-stop-status--muted {
  color: var(--gp-text-secondary);
}

.trip-stop-status + .trip-stop-notes::before {
  content: '· ';
}

.trip-stop-notes {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--gp-text-secondary);
}

.trip-stop-menu {
  flex-shrink: 0;
}

/* Pointer devices: the menu and grip appear on hover, focus or selection; touch keeps them visible. */
@media (hover: hover) and (pointer: fine) {
  .trip-stop--editable .trip-stop-card {
    cursor: grab;
  }

  .trip-stop--editable .trip-stop-card:hover .trip-stop-grip {
    opacity: 1;
  }

  .trip-stop-menu {
    opacity: 0;
    transition: opacity 0.12s ease;
  }

  .trip-stop-card:hover .trip-stop-menu,
  .trip-stop-card:focus-within .trip-stop-menu,
  .trip-stop--selected .trip-stop-menu {
    opacity: 1;
  }
}
</style>

<!-- Global, not scoped: the stop menu is teleported to <body>, out of reach of scoped styles. -->
<style>
.trip-stop-menu-danger .p-menu-item-label,
.trip-stop-menu-danger .p-menu-item-icon {
  color: var(--gp-danger-text);
}
</style>
