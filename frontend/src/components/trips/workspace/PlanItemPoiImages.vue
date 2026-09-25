<template>
  <div v-if="pois.length || loading" class="poi-suggest">
    <label class="field-label">What's here</label>

    <div v-if="loading" class="poi-suggest-loading">
      <ProgressSpinner style="width: 24px; height: 24px" strokeWidth="4" />
    </div>

    <div v-else class="poi-suggest-grid">
      <button
        v-for="poi in pois"
        :key="poi.id"
        type="button"
        class="poi-suggest-card"
        :title="`Use &quot;${poi.name}&quot;`"
        @click="handleSelect(poi)"
      >
        <PoiImage :endpoint="poi.imageUrl" :alt="poi.name" />
        <span class="poi-suggest-name">{{ poi.name }}</span>
        <!-- Per-image credit: Commons licences are per file, so it travels with the photo. -->
        <span v-if="poi.imageLicense" class="poi-suggest-credit">
          {{ poi.imageAuthor || 'Unknown' }} · {{ poi.imageLicense }}
        </span>
      </button>
    </div>

    <p v-if="pois.length" class="poi-suggest-hint">Click a photo to name this stop after it.</p>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import ProgressSpinner from 'primevue/progressspinner'
import apiService from '@/utils/apiService'
import PoiImage from '@/components/trips/discovery/PoiImage.vue'

/**
 * Shows what is actually at the pinned coordinates - name and photo - so a stop added by
 * right-clicking the map is not just a pair of numbers the user has to identify themselves.
 *
 * Reuses the POI search endpoint, so this needs no new data model: it is a live lookup.
 */
const props = defineProps({
  latitude: { type: Number, default: null },
  longitude: { type: Number, default: null },
  /** Tight by default, so the suggestion is what is at the pin rather than the district. */
  radiusMeters: { type: Number, default: 220 },
  limit: { type: Number, default: 3 }
})

const emit = defineEmits(['select'])

const pois = ref([])
const loading = ref(false)
let requestToken = 0

/**
 * Selecting a suggestion snaps the pin to that place, which changes the coordinates this
 * component watches - so it would immediately re-query and swap the list out from under
 * the click. The pin move is wanted; the reload is not, so it is skipped exactly once.
 */
let suppressNextLoad = false

const handleSelect = (poi) => {
  suppressNextLoad = true
  emit('select', poi)
}

const hasCoordinates = () =>
  Number.isFinite(props.latitude) && Number.isFinite(props.longitude)

const load = async () => {
  if (!hasCoordinates()) {
    pois.value = []
    return
  }

  const token = ++requestToken
  loading.value = true
  try {
    const response = await apiService.get('/poi/search', {
      latitude: props.latitude,
      longitude: props.longitude,
      radiusMeters: props.radiusMeters,
      limit: props.limit
    })
    if (token !== requestToken) return
    pois.value = Array.isArray(response?.results) ? response.results : []
  } catch (error) {
    // Discovery is a convenience here, never a blocker: no photos simply means no
    // suggestions, and the user names the stop themselves.
    if (token === requestToken) {
      pois.value = []
    }
  } finally {
    if (token === requestToken) {
      loading.value = false
    }
  }
}

watch(() => [props.latitude, props.longitude], () => {
  if (suppressNextLoad) {
    suppressNextLoad = false
    return
  }
  load()
}, { immediate: true })
</script>

<style scoped>
.poi-suggest {
  margin-top: var(--gp-spacing-md);
}

.poi-suggest-loading {
  display: flex;
  justify-content: center;
  padding: var(--gp-spacing-md);
}

/* Three across, as asked. `minmax(0, 1fr)` rather than `1fr` is the important part: an
   `auto` minimum lets a photo's intrinsic width force its column wider, which is what was
   collapsing this to two columns with dead space beside them. */
.poi-suggest-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--gp-spacing-sm);
}

@media (max-width: 640px) {
  .poi-suggest-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.poi-suggest-card {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0;
  background: none;
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  overflow: hidden;
  cursor: pointer;
  text-align: left;
}

.poi-suggest-card:hover {
  border-color: var(--gp-primary);
}

.poi-suggest-name {
  padding: 0 var(--gp-spacing-xs);
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.poi-suggest-credit {
  padding: 0 var(--gp-spacing-xs) var(--gp-spacing-xs);
  font-size: 0.62rem;
  color: var(--gp-text-secondary);
}

.poi-suggest-hint {
  margin: var(--gp-spacing-xs) 0 0;
  font-size: 0.72rem;
  color: var(--gp-text-secondary);
}

.p-dark .poi-suggest-card {
  border-color: var(--gp-border-dark);
}
</style>
