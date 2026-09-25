<template>
  <div class="trip-rail">
    <!-- ── Header: always present, so "+ Add" is never hidden ─────────────── -->
    <div class="trip-rail-header">
      <Button
        v-if="mode === 'add'"
        icon="pi pi-arrow-left"
        text
        size="small"
        aria-label="Back to stops"
        @click="mode = 'stops'"
      />
      <h3 class="trip-rail-title" :class="{ 'trip-rail-title--redundant': mode === 'stops' }">
        {{ mode === 'add' ? 'Add a place' : `Stops (${stops.length})` }}
      </h3>
      <Button
        v-if="mode === 'stops' && canEdit"
        label="Add"
        icon="pi pi-plus"
        size="small"
        @click="mode = 'add'"
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

    <!-- ── Actual lens ───────────────────────────────────────────────────── -->
    <div v-else-if="lens === 'actual'" class="trip-rail-body trip-rail-actual">
      <slot name="actual" />
    </div>

    <!-- ── Plan lens: day-grouped stops ──────────────────────────────────── -->
    <div v-else class="trip-rail-body">
      <div v-if="loading" class="trip-rail-state">
        <ProgressSpinner style="width: 32px; height: 32px" strokeWidth="4" />
      </div>

      <div v-else-if="stops.length === 0" class="trip-rail-state">
        <i class="pi pi-map-marker trip-rail-state-icon" />
        <p>No stops yet.</p>
        <Button
          v-if="canEdit"
          label="Add your first stop"
          icon="pi pi-plus"
          size="small"
          @click="mode = 'add'"
        />
      </div>

      <template v-else>
        <section v-for="group in groups" :key="group.key" class="trip-rail-group">
          <h4 class="trip-rail-group-title">{{ group.label }}</h4>

          <article
            v-for="stop in group.items"
            :key="stop.id"
            class="trip-stop"
            :class="{ 'trip-stop--selected': stop.id === selectedId }"
          >
            <button
              type="button"
              class="trip-stop-main"
              @click="$emit('focus-stop', stop)"
            >
              <div class="trip-stop-title-row">
                <Tag
                  :value="stop.priority === 'MUST' ? 'Must' : 'Optional'"
                  :severity="stop.priority === 'MUST' ? 'danger' : 'secondary'"
                />
                <span class="trip-stop-title">{{ stop.title }}</span>
              </div>
              <small v-if="stop.notes" class="trip-stop-notes">{{ stop.notes }}</small>
              <Tag
                v-if="statusFor(stop)"
                :value="statusFor(stop).label"
                :severity="statusFor(stop).severity"
                class="trip-stop-status"
              />
              <small v-if="statusFor(stop)?.subtext" class="trip-stop-subtext">
                {{ statusFor(stop).subtext }}
              </small>
            </button>

            <div v-if="canEdit" class="trip-stop-actions">
              <Button
                v-if="hasCoordinates(stop)"
                icon="pi pi-check-circle"
                text
                size="small"
                aria-label="Mark visited"
                v-tooltip.top="'Mark visited'"
                @click="$emit('mark-visited', stop)"
              />
              <Button
                icon="pi pi-pencil"
                text
                size="small"
                aria-label="Edit stop"
                v-tooltip.top="'Edit stop'"
                @click="$emit('edit-stop', stop)"
              />
              <Button
                icon="pi pi-trash"
                text
                size="small"
                severity="danger"
                aria-label="Delete stop"
                v-tooltip.top="'Delete stop'"
                @click="$emit('delete-stop', stop)"
              />
            </div>
          </article>
        </section>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import SelectButton from 'primevue/selectbutton'
import ProgressSpinner from 'primevue/progressspinner'
import TripAddStopPanel from './TripAddStopPanel.vue'
import { useTimezone } from '@/composables/useTimezone'

const props = defineProps({
  stops: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  canEdit: { type: Boolean, default: false },
  /** True once the trip has real timeline data, which is when the Actual lens is useful. */
  hasActualData: { type: Boolean, default: false },
  lens: { type: String, default: 'plan' },
  selectedId: { type: [Number, String], default: null },
  /** Set the lens without user interaction, e.g. when the trip completes. */
  initialMode: { type: String, default: 'stops' },
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
  'mark-visited'
])

const timezone = useTimezone()

const mode = ref(props.initialMode)

const lensOptions = [
  { label: 'Plan', value: 'plan' },
  { label: 'Actual', value: 'actual' }
]

const UNPLANNED_KEY = '__unscheduled__'

const hasCoordinates = (stop) =>
  Number.isFinite(Number(stop?.latitude)) && Number.isFinite(Number(stop?.longitude))

/**
 * Stops grouped by planned day, unscheduled last. The list is deliberately uncapped -
 * the previous planning panel silently truncated at 8 with no indication, so its count
 * could disagree with the table.
 */
const groups = computed(() => {
  const buckets = new Map()

  for (const stop of props.stops) {
    const key = stop.plannedDay || UNPLANNED_KEY
    if (!buckets.has(key)) {
      buckets.set(key, [])
    }
    buckets.get(key).push(stop)
  }

  const dated = [...buckets.entries()]
    .filter(([key]) => key !== UNPLANNED_KEY)
    .sort(([a], [b]) => String(a).localeCompare(String(b)))

  const ordered = dated.map(([day, items]) => ({
    key: day,
    label: timezone.formatDateDisplay(day),
    items: sortWithinDay(items)
  }))

  if (buckets.has(UNPLANNED_KEY)) {
    ordered.push({
      key: UNPLANNED_KEY,
      label: 'Unscheduled',
      items: sortWithinDay(buckets.get(UNPLANNED_KEY))
    })
  }

  return ordered
})

const sortWithinDay = (items) =>
  [...items].sort((a, b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0))

/** Visit status, mirroring the labels the plan table used. */
const statusFor = (stop) => {
  if (stop?.manualOverrideState === 'REJECTED') {
    return { label: 'Missed', severity: 'danger', subtext: 'Manual override' }
  }

  if (stop?.isVisited) {
    const confidence = stop.visitConfidence
    return {
      label: 'Visited',
      severity: 'success',
      subtext: Number.isFinite(confidence) ? `Confidence ${Math.round(confidence * 100)}%` : null
    }
  }

  const confidence = stop?.visitConfidence
  if (Number.isFinite(confidence) && confidence >= props.confidenceThresholds.medium) {
    return {
      label: 'Needs review',
      severity: 'warn',
      subtext: `Confidence ${Math.round(confidence * 100)}%`
    }
  }

  if (!hasCoordinates(stop)) {
    return { label: 'Planned', severity: 'info', subtext: 'Add a location to auto-match' }
  }

  return { label: 'Planned', severity: 'info', subtext: 'Not visited yet' }
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
  border-bottom: 1px solid var(--gp-border-light);
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

.trip-rail-group + .trip-rail-group {
  margin-top: var(--gp-spacing-md);
}

.trip-rail-group-title {
  margin: 0 0 var(--gp-spacing-xs);
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--gp-text-secondary);
}

.trip-stop {
  display: flex;
  align-items: flex-start;
  gap: var(--gp-spacing-xs);
  padding: var(--gp-spacing-sm);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-white);
}

.trip-stop + .trip-stop {
  margin-top: var(--gp-spacing-xs);
}

.trip-stop--selected {
  border-color: var(--gp-primary);
  box-shadow: var(--gp-shadow-card-highlighted);
}

.trip-stop-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  background: none;
  border: none;
  padding: 0;
  text-align: left;
  cursor: pointer;
  color: inherit;
}

.trip-stop-title-row {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
  flex-wrap: wrap;
}

.trip-stop-title {
  font-weight: 600;
  overflow-wrap: anywhere;
}

.trip-stop-notes,
.trip-stop-subtext {
  color: var(--gp-text-secondary);
  overflow-wrap: anywhere;
}

.trip-stop-status {
  align-self: flex-start;
}

.trip-stop-actions {
  display: flex;
  flex-shrink: 0;
}

.p-dark .trip-stop {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark .trip-rail-header {
  border-bottom-color: var(--gp-border-dark);
}
</style>
