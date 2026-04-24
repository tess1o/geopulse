<template>
  <div class="map-panel">
    <div class="map-toolbar">
      <AutoComplete
        v-model="localSearchQuery"
        :suggestions="searchSuggestions"
        optionLabel="displayName"
        :placeholder="searchPlaceholder"
        :minLength="2"
        :delay="300"
        :loading="false"
        :showEmptyMessage="!searchLoading"
        class="map-search"
        @complete="(event) => emit('search-complete', event)"
        @item-select="(event) => emit('search-select', event?.value || null)"
      >
        <template #option="{ option }">
          <div class="map-search-option">
            <div class="map-search-option-title">{{ option.displayName }}</div>
            <div class="map-search-option-meta">
              <span
                v-if="option.groupLabel"
                class="map-search-source-chip"
                :class="option.groupLabel === 'Saved place' ? 'map-search-source-chip--saved' : 'map-search-source-chip--provider'"
              >
                {{ option.groupLabel }}
              </span>
              <span v-if="option.metaLine" class="map-search-option-subtitle">{{ option.metaLine }}</span>
            </div>
          </div>
        </template>
      </AutoComplete>

      <div v-if="searchLoading" class="map-search-loading" aria-live="polite">
        <i class="pi pi-spin pi-spinner map-search-loading-icon" />
        <span>Searching places...</span>
      </div>
    </div>

    <small v-if="searchError" class="map-search-error">{{ searchError }}</small>

    <MapContainer
      :map-id="mapId"
      :center="center"
      :zoom="zoom"
      :show-controls="false"
      height="600px"
      width="100%"
      @map-ready="(map) => emit('map-ready', map)"
      @map-click="(event) => emit('map-click', event)"
    />

    <TripReconstructionMapOverlay
      :map="map"
      :active-segment="activeSegment"
      :active-segment-index="activeSegmentIndex"
      :context-points="contextPoints"
      :context-trip-lines="contextTripLines"
      :id-prefix="idPrefix"
      @stay-dragged="(payload) => emit('stay-dragged', payload)"
      @waypoint-dragged="(payload) => emit('waypoint-dragged', payload)"
      @waypoint-removed="(payload) => emit('waypoint-removed', payload)"
    />

    <div class="map-hint">
      <span v-if="activeSegment?.segmentType === 'TRIP'">
        Search or click map to append waypoints for this trip segment. Existing stays/trips are shown as context.
      </span>
      <span v-else-if="activeSegment?.segmentType === 'STAY'">
        Search or click map to place the stay location.
      </span>
      <span v-else>
        Select a segment to edit it on the map.
      </span>
    </div>

    <slot />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import AutoComplete from 'primevue/autocomplete'
import { MapContainer } from '@/components/maps'
import TripReconstructionMapOverlay from '@/components/trips/reconstruction/TripReconstructionMapOverlay.vue'
import '@/maps/shared/styles/tripReconstructionMarkers.css'

const props = defineProps({
  mapId: {
    type: String,
    required: true
  },
  center: {
    type: Array,
    required: true
  },
  zoom: {
    type: Number,
    required: true
  },
  map: {
    type: Object,
    default: null
  },
  activeSegment: {
    type: Object,
    default: null
  },
  activeSegmentIndex: {
    type: Number,
    default: -1
  },
  contextPoints: {
    type: Array,
    default: () => []
  },
  contextTripLines: {
    type: Array,
    default: () => []
  },
  idPrefix: {
    type: String,
    default: null
  },
  searchQuery: {
    type: String,
    default: ''
  },
  searchSuggestions: {
    type: Array,
    default: () => []
  },
  searchLoading: {
    type: Boolean,
    default: false
  },
  searchError: {
    type: String,
    default: ''
  }
})

const emit = defineEmits([
  'map-ready',
  'map-click',
  'stay-dragged',
  'waypoint-dragged',
  'waypoint-removed',
  'search-complete',
  'search-select',
  'update:search-query'
])

const localSearchQuery = computed({
  get: () => props.searchQuery,
  set: (value) => {
    emit('update:search-query', value || '')
  }
})

const searchPlaceholder = computed(() => {
  if (props.activeSegment?.segmentType === 'TRIP') {
    return 'Search location to add waypoint...'
  }
  if (props.activeSegment?.segmentType === 'STAY') {
    return 'Search location to place stay...'
  }
  return 'Search location...'
})
</script>

<style scoped>
.map-panel {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.map-toolbar {
  display: inline-flex;
  gap: var(--gp-spacing-xs);
  align-items: center;
  justify-content: flex-start;
  width: fit-content;
  max-width: 100%;
}

.map-search {
  flex: 0 1 auto;
  width: min(360px, 100%);
  min-width: 280px;
}

.map-search :deep(.p-autocomplete-loader) {
  display: none !important;
}

.map-search-loading {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  flex: 0 0 auto;
  white-space: nowrap;
  font-size: 0.78rem;
  color: var(--gp-text-secondary);
}

.map-search-loading-icon {
  font-size: 0.95rem;
}

.map-search-option {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.map-search-option-title {
  font-size: 0.88rem;
  color: var(--gp-text-primary);
}

.map-search-option-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.2rem;
}

.map-search-source-chip {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  border: 1px solid transparent;
  padding: 0.08rem 0.45rem;
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.map-search-source-chip--saved {
  color: #166534;
  background: #dcfce7;
  border-color: #86efac;
}

.map-search-source-chip--provider {
  color: #1e3a8a;
  background: #dbeafe;
  border-color: #93c5fd;
}

.map-search-option-subtitle {
  font-size: 0.75rem;
  color: var(--gp-text-secondary);
}

.map-search-error {
  color: var(--gp-danger);
  font-size: 0.78rem;
}

.map-hint {
  font-size: 0.84rem;
  color: var(--gp-text-secondary);
}

@media (max-width: 768px) {
  .map-toolbar {
    display: flex;
    width: 100%;
    flex-wrap: wrap;
  }

  .map-search {
    flex: 1 1 100%;
    width: 100%;
    min-width: 0;
  }
}
</style>
