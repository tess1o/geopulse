<template>
  <DataTable
    :value="items"
    :paginator="true"
    :rows="10"
    :rowsPerPageOptions="[10, 25, 50]"
    stripedRows
  >
    <Column field="title" header="Title" sortable>
      <template #body="{ data }">
        <div class="plan-item-title">
          <button
            type="button"
            class="plan-item-link"
            :disabled="typeof data.latitude !== 'number' || typeof data.longitude !== 'number'"
            @click="emitFocusItem(data)"
          >
            {{ data.title }}
          </button>
          <small v-if="data.notes">{{ data.notes }}</small>
          <small>{{ formatPlannedDay(data.plannedDay) }}</small>
        </div>
      </template>
    </Column>

    <Column field="priority" header="Priority" sortable style="width: 8rem">
      <template #body="{ data }">
        <Tag
          :value="data.priority || 'OPTIONAL'"
          :severity="getPrioritySeverity(data.priority)"
        />
      </template>
    </Column>

    <Column v-if="!isPlanningMode" header="Matched Stay">
      <template #body="{ data }">
        <div class="actual-visit-cell">
          <template v-if="hasMatchedStay(data)">
            <strong>{{ getMatchedStayTitle(data) }}</strong>
            <small>{{ getMatchedStayTimeLabel(data) }}</small>
            <Tag
              v-if="getMatchedConfidenceBadge(data)"
              class="matched-confidence-badge"
              :value="getMatchedConfidenceBadge(data).label"
              :severity="getMatchedConfidenceBadge(data).severity"
            />
          </template>
          <span v-else class="matched-stay-empty">—</span>
        </div>
      </template>
    </Column>

    <Column field="isVisited" header="Status" sortable style="width: 11rem">
      <template #body="{ data }">
        <Tag
          :severity="getVisitStatusMeta(data).severity"
          :value="getVisitStatusMeta(data).label"
        />
        <small class="confidence-text" v-if="getVisitStatusMeta(data).subtext">
          {{ getVisitStatusMeta(data).subtext }}
        </small>
      </template>
    </Column>

    <Column header="Actions" style="width: 16rem">
      <template #body="{ data }">
        <Button
          icon="pi pi-check"
          class="p-button-text p-button-sm"
          v-tooltip.top="'Mark visited'"
          @click="emitOverride(data, 'CONFIRM_VISITED')"
        />
        <Button
          icon="pi pi-times"
          class="p-button-text p-button-sm"
          v-tooltip.top="'Mark not visited'"
          @click="emitOverride(data, 'REJECT_VISIT')"
        />
        <Button
          icon="pi pi-undo"
          class="p-button-text p-button-sm"
          v-tooltip.top="'Reset visit state'"
          @click="emitOverride(data, 'RESET_TO_AUTO')"
        />
        <Button
          icon="pi pi-pencil"
          class="p-button-text p-button-sm"
          v-tooltip.top="'Edit item'"
          @click="emitEditItem(data)"
        />
        <Button
          icon="pi pi-map-marker"
          class="p-button-text p-button-sm"
          v-tooltip.top="'Open in Google Maps'"
          :disabled="!hasCoordinates(data)"
          @click="openGoogleMaps(data)"
        />
        <Button
          icon="pi pi-trash"
          class="p-button-text p-button-sm"
          severity="danger"
          v-tooltip.top="'Delete item'"
          @click="emitDeleteItem(data)"
        />
      </template>
    </Column>

    <template #empty>
      <div class="empty-state">
        <i class="pi pi-list-check empty-state-icon"></i>
        <p>No plan items yet.</p>
        <small>Add places from map context menu to compare planned vs actual visits.</small>
      </div>
    </template>
  </DataTable>
</template>

<script setup>
import { computed } from 'vue'
import { useTimezone } from '@/composables/useTimezone'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'

const props = defineProps({
  items: {
    type: Array,
    default: () => []
  },
  isPlanningMode: {
    type: Boolean,
    default: false
  },
  isActiveTrip: {
    type: Boolean,
    default: false
  },
  visitSuggestions: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['focus-item', 'override', 'edit-item', 'delete-item'])
const timezone = useTimezone()

const visitSuggestionsByPlanItem = computed(() => {
  const map = new Map()
  for (const suggestion of props.visitSuggestions || []) {
    if (suggestion?.planItemId !== null && suggestion?.planItemId !== undefined) {
      map.set(Number(suggestion.planItemId), suggestion)
    }
  }
  return map
})

const formatDateTime = (value) => {
  if (!value) return '—'
  return `${timezone.formatDateDisplay(value)} ${timezone.format(value, 'HH:mm')}`
}

const formatPlannedDay = (plannedDay) => {
  if (!plannedDay) return '—'
  const parsed = timezone.parseUrlDate(plannedDay, false)
  return parsed ? timezone.formatDateDisplay(parsed) : plannedDay
}

const formatVisitConfidence = (confidence) => {
  if (confidence === null || confidence === undefined) return '—'
  return `${Math.round(confidence * 100)}%`
}

const getPrioritySeverity = (priority) => {
  return String(priority || '').toUpperCase() === 'MUST' ? 'danger' : 'warn'
}

const getSuggestionForItem = (item) => {
  if (!item?.id) return null
  return visitSuggestionsByPlanItem.value.get(Number(item.id)) || null
}

const getActualConfidenceLabel = (item) => {
  const suggestion = getSuggestionForItem(item)
  if (suggestion?.confidence !== null && suggestion?.confidence !== undefined) {
    return `Confidence ${formatVisitConfidence(suggestion.confidence)}`
  }
  if (item?.visitConfidence !== null && item?.visitConfidence !== undefined) {
    return `Confidence ${formatVisitConfidence(item.visitConfidence)}`
  }
  return null
}

const hasMatchedStay = (item) => {
  const suggestion = getSuggestionForItem(item)
  if (!suggestion) return false
  return Boolean(
    (suggestion.matchedStayId !== null && suggestion.matchedStayId !== undefined)
    || suggestion.matchedLocationName
    || suggestion.matchedStayStart
  )
}

const getMatchedStayTitle = (item) => {
  const suggestion = getSuggestionForItem(item)
  return suggestion?.matchedLocationName || 'Unknown place'
}

const getMatchedStayTimeLabel = (item) => {
  const suggestion = getSuggestionForItem(item)
  if (suggestion?.matchedStayStart) {
    return formatDateTime(suggestion.matchedStayStart)
  }
  return '—'
}

const getMatchedConfidenceBadge = (item) => {
  const suggestion = getSuggestionForItem(item)
  const confidence = suggestion?.confidence
  if (confidence === null || confidence === undefined) {
    return null
  }
  if (confidence >= 0.9) {
    return { label: 'High', severity: 'success' }
  }
  if (confidence >= 0.75) {
    return { label: 'Medium', severity: 'warn' }
  }
  return { label: 'Low', severity: 'danger' }
}

const getVisitStatusMeta = (item) => {
  const suggestion = getSuggestionForItem(item)
  const decision = String(suggestion?.decision || '').toUpperCase()
  const manualState = String(item?.manualOverrideState || '').toUpperCase()
  const isVisited = item?.isVisited || decision === 'AUTO_MATCHED' || decision === 'MANUAL_OVERRIDE'

  if (props.isPlanningMode && !isVisited) {
    return { label: 'Planned', severity: 'info', subtext: 'No actual data yet' }
  }
  if (manualState === 'REJECTED') {
    return { label: 'Missed', severity: 'danger', subtext: 'Manual override' }
  }
  if (isVisited) {
    return { label: 'Visited', severity: 'success', subtext: getActualConfidenceLabel(item) }
  }
  if (decision === 'NO_COORDINATES') {
    return {
      label: 'Planned',
      severity: 'info',
      subtext: props.isActiveTrip ? 'Not visited yet' : 'Add map point for auto-matching'
    }
  }
  if (decision === 'SUGGESTED') {
    return { label: 'Needs review', severity: 'warn', subtext: getActualConfidenceLabel(item) }
  }
  if (props.isActiveTrip) {
    return { label: 'Planned', severity: 'info', subtext: 'Not visited yet' }
  }
  return { label: 'Missed', severity: 'secondary', subtext: suggestion?.reason || null }
}

const emitFocusItem = (item) => emit('focus-item', item)
const emitEditItem = (item) => emit('edit-item', item)
const emitDeleteItem = (item) => emit('delete-item', item)
const emitOverride = (item, action) => emit('override', { item, action })

const hasCoordinates = (item) => {
  return typeof item?.latitude === 'number' &&
    Number.isFinite(item.latitude) &&
    typeof item?.longitude === 'number' &&
    Number.isFinite(item.longitude)
}

const openGoogleMaps = (item) => {
  if (!hasCoordinates(item)) return

  const url = `https://www.google.com/maps?q=${item.latitude},${item.longitude}`
  window.open(url, '_blank', 'noopener,noreferrer')
}
</script>

<style scoped>
.plan-item-title {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.plan-item-link {
  border: 0;
  background: transparent;
  color: var(--gp-text-primary);
  font-weight: 700;
  padding: 0;
  text-align: left;
  cursor: pointer;
}

.plan-item-link:hover:not(:disabled) {
  color: var(--gp-primary, #1a56db);
  text-decoration: underline;
}

.plan-item-link:disabled {
  color: var(--gp-text-secondary);
  cursor: default;
}

.plan-item-title small {
  color: var(--gp-text-secondary);
}

.actual-visit-cell {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.actual-visit-cell small {
  color: var(--gp-text-secondary);
}

.matched-stay-empty {
  color: var(--gp-text-secondary);
}

.matched-confidence-badge {
  align-self: flex-start;
}

.confidence-text {
  display: block;
  margin-top: 0.25rem;
  color: var(--gp-text-secondary);
}

.empty-state {
  text-align: center;
  padding: var(--gp-spacing-xl);
  color: var(--gp-text-secondary);
}

.empty-state-icon {
  font-size: 2.5rem;
  margin-bottom: var(--gp-spacing-sm);
  color: var(--gp-text-muted);
}
</style>
