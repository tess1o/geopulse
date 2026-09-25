<template>
  <div class="trip-add">
    <SelectButton
      :model-value="mode"
      :options="modes"
      option-label="label"
      option-value="value"
      :allow-empty="false"
      class="trip-add-modes"
      @update:model-value="mode = $event"
    />

    <!-- Search a place you can already name -->
    <div v-if="mode === 'search'" class="trip-add-search">
      <TripPlanLocationSearchInput
        input-id="tripAddStopSearch"
        v-model="query"
        :suggestions="suggestions"
        placeholder="Search saved places or providers..."
        :loading="isSearching"
        :error="searchError"
        @complete="handleSearch"
        @select="handleSearchSelect"
      />
      <p class="trip-add-hint">
        Pick a result to add it to your stops. You can set the day and priority afterwards.
      </p>
    </div>

    <!-- Discover places nearby, with photos -->
    <div v-else class="trip-add-explore">
      <PoiDiscoveryPanel :plan-items="planItems" :show-header="false" @add-to-plan="handlePoiSelect" />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import SelectButton from 'primevue/selectbutton'
import TripPlanLocationSearchInput from '@/components/trips/TripPlanLocationSearchInput.vue'
import PoiDiscoveryPanel from '@/components/trips/discovery/PoiDiscoveryPanel.vue'
import {
  getTripPlanSuggestionCoordinates,
  useTripPlanLocationSearch
} from '@/composables/useTripPlanLocationSearch'

const props = defineProps({
  planItems: { type: Array, default: () => [] }
})

const emit = defineEmits(['add-stop'])

const modes = [
  { label: 'Search by name', value: 'search' },
  { label: 'Explore nearby', value: 'explore' }
]

const mode = ref('search')

const {
  query,
  suggestions,
  isLoading: isSearching,
  error: searchError,
  search: runSearch
} = useTripPlanLocationSearch({ fallbackLabel: 'Stop', limit: 10 })

const handleSearch = (event) => runSearch(event)

const handleSearchSelect = (suggestion) => {
  if (!suggestion) return
  const coordinates = getTripPlanSuggestionCoordinates(suggestion)
  emit('add-stop', {
    title: suggestion.title || suggestion.displayName,
    description: null,
    latitude: coordinates?.latitude ?? null,
    longitude: coordinates?.longitude ?? null
  })
}

/** A discovered POI already carries a name, a description and coordinates. */
const handlePoiSelect = (poi) => {
  emit('add-stop', {
    title: poi.name,
    description: poi.description || null,
    latitude: poi.latitude,
    longitude: poi.longitude
  })
}
</script>

<style scoped>
.trip-add {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
}

/* Styled explicitly rather than relying on SelectButton defaults: on the dark theme the
   default active segment rendered as a light chip that neither matched the app nor
   separated cleanly from its neighbour. */
.trip-add-modes {
  align-self: stretch;
}

.trip-add-modes :deep(.p-selectbutton) {
  display: flex;
  width: 100%;
  gap: 2px;
  padding: 2px;
  background: var(--gp-surface-gray);
  border: 1px solid var(--gp-border-medium);
  border-radius: var(--gp-radius-medium);
}

.trip-add-modes :deep(.p-togglebutton) {
  flex: 1;
  background: transparent;
  border: none;
  border-radius: var(--gp-radius-small);
  color: var(--gp-text-secondary);
  font-weight: 600;
  padding: var(--gp-spacing-sm) var(--gp-spacing-md);
}

.trip-add-modes :deep(.p-togglebutton:not(.p-togglebutton-checked):hover) {
  background: var(--gp-surface-light);
  color: var(--gp-text-primary);
}

/* The label sits inside .p-togglebutton-content > .p-togglebutton-label, each of which
   carries its own colour, so setting it on the button alone leaves grey text on the
   primary fill. */
.trip-add-modes :deep(.p-togglebutton.p-togglebutton-checked),
.trip-add-modes :deep(.p-togglebutton.p-togglebutton-checked .p-togglebutton-content),
.trip-add-modes :deep(.p-togglebutton.p-togglebutton-checked .p-togglebutton-label) {
  background: var(--gp-primary);
  color: #ffffff;
}

.trip-add-modes :deep(.p-togglebutton.p-togglebutton-checked:hover),
.trip-add-modes :deep(.p-togglebutton.p-togglebutton-checked:hover .p-togglebutton-content),
.trip-add-modes :deep(.p-togglebutton.p-togglebutton-checked:hover .p-togglebutton-label) {
  background: var(--gp-primary-hover);
  color: #ffffff;
}

.trip-add-modes :deep(.p-togglebutton .p-togglebutton-content) {
  background: transparent;
}

.p-dark .trip-add-modes :deep(.p-selectbutton) {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

.p-dark .trip-add-modes :deep(.p-togglebutton) {
  color: var(--gp-text-muted);
}

.p-dark .trip-add-modes :deep(.p-togglebutton:not(.p-togglebutton-checked):hover) {
  background: var(--gp-surface-dark);
  color: var(--gp-text-primary);
}

.trip-add-hint {
  margin: var(--gp-spacing-sm) 0 0;
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
}

.trip-add-explore :deep(.poi-discovery-controls) {
  margin-top: 0;
}

.trip-add-explore :deep(.poi-grid) {
  grid-template-columns: repeat(auto-fill, minmax(13rem, 1fr));
}
</style>
