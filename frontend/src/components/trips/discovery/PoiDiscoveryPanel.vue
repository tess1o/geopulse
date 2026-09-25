<template>
  <div class="poi-discovery">
    <div v-if="showHeader" class="poi-discovery-header">
      <div>
        <h3 class="poi-discovery-title">Places to visit</h3>
        <p class="gp-text-secondary poi-discovery-subtitle">
          Popular places near an area, with photos. Pick an area to explore.
        </p>
      </div>
    </div>

    <!-- Area picker -->
    <div class="poi-discovery-controls">
      <TripPlanLocationSearchInput
        input-id="poiAreaSearch"
        v-model="areaQuery"
        :suggestions="suggestions"
        placeholder="Search a city or area to explore..."
        :loading="isSearching"
        :error="searchError"
        class="poi-area-search"
        @complete="handleAreaSearchComplete"
        @select="handleAreaSelect"
      />

      <Select
        v-model="radius"
        :options="radiusOptions"
        optionLabel="label"
        optionValue="value"
        class="poi-radius-select"
        aria-label="Search radius"
        @change="refresh"
      />

      <Button
        v-if="plannedStopsCentroid"
        label="Use my planned stops"
        icon="pi pi-map-marker"
        severity="secondary"
        outlined
        @click="usePlannedStops"
      />
    </div>

    <div v-if="store.area" class="poi-area-summary">
      <i class="pi pi-map-marker" />
      <span>{{ areaLabel }}</span>
      <Button label="Change" link size="small" @click="resetArea" />
    </div>

    <!-- No area chosen yet -->
    <div v-if="!store.area" class="poi-empty">
      <i class="pi pi-compass poi-empty-icon" />
      <p>Choose a place above to see what is worth visiting there.</p>
    </div>

    <!-- Loading: skeleton grid, so the layout does not jump -->
    <div v-else-if="store.loading" class="poi-grid">
      <div v-for="n in 6" :key="n" class="poi-card">
        <Skeleton height="9rem" border-radius="8px" />
        <Skeleton width="70%" height="1rem" class="mt-2" />
        <Skeleton width="90%" height="0.8rem" />
      </div>
    </div>

    <!-- Provider problem: say so rather than showing an empty list -->
    <Message v-else-if="store.error" severity="error" :closable="false">
      {{ store.error }}
      <Button label="Retry" link size="small" @click="refresh" />
    </Message>

    <div v-else-if="!store.hasResults" class="poi-empty">
      <i class="pi pi-search poi-empty-icon" />
      <p>No places found in this area. Try a larger radius.</p>
    </div>

    <template v-else>
      <div class="poi-grid">
        <article v-for="poi in store.results" :key="poi.id" class="poi-card">
          <PoiImage :endpoint="poi.imageUrl" :alt="poi.name" />

          <div class="poi-card-body">
            <h4 class="poi-card-title">{{ poi.name }}</h4>
            <p v-if="poi.description" class="poi-card-description">{{ poi.description }}</p>

            <!-- Per-image licence. Commons licences are per file, so this is not optional. -->
            <p v-if="poi.imageLicense" class="poi-card-credit">
              Photo:
              <a
                v-if="poi.imageFilePageUrl"
                :href="poi.imageFilePageUrl"
                target="_blank"
                rel="noopener noreferrer"
              >{{ poi.imageAuthor || 'Unknown' }}</a>
              <span v-else>{{ poi.imageAuthor || 'Unknown' }}</span>
              · {{ poi.imageLicense }}
            </p>

            <Button
              :label="isAdded(poi) ? 'Added' : 'Add to plan'"
              :icon="isAdded(poi) ? 'pi pi-check' : 'pi pi-plus'"
              :disabled="isAdded(poi)"
              size="small"
              class="poi-card-action"
              @click="$emit('add-to-plan', poi)"
            />
          </div>
        </article>
      </div>

      <p class="poi-attribution">{{ store.attribution }}</p>
    </template>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import Select from 'primevue/select'
import Button from 'primevue/button'
import Skeleton from 'primevue/skeleton'
import Message from 'primevue/message'
import TripPlanLocationSearchInput from '@/components/trips/TripPlanLocationSearchInput.vue'
import PoiImage from './PoiImage.vue'
import { usePoiDiscoveryStore } from '@/stores/poiDiscovery'
import {
  getTripPlanSuggestionCoordinates,
  useTripPlanLocationSearch
} from '@/composables/useTripPlanLocationSearch'

const props = defineProps({
  planItems: { type: Array, default: () => [] },
  /** Hidden when hosted in a container that already provides the heading. */
  showHeader: { type: Boolean, default: true }
})

defineEmits(['add-to-plan'])

const store = usePoiDiscoveryStore()
const radius = ref(store.radiusMeters)

const radiusOptions = [
  { label: '2 km', value: 2000 },
  { label: '5 km', value: 5000 },
  { label: '8 km', value: 8000 },
  { label: '15 km', value: 15000 },
  { label: '30 km', value: 30000 }
]

const areaLabel = ref('')

const {
  query: areaQuery,
  suggestions,
  isLoading: isSearching,
  error: searchError,
  search: runAreaSearch
} = useTripPlanLocationSearch({
  fallbackLabel: 'Area',
  limit: 8
})

const plannedStopsCentroid = computed(() => {
  const located = props.planItems.filter(
    (item) => Number.isFinite(Number(item.latitude)) && Number.isFinite(Number(item.longitude))
  )
  if (located.length === 0) return null

  const sum = located.reduce(
    (acc, item) => ({
      latitude: acc.latitude + Number(item.latitude),
      longitude: acc.longitude + Number(item.longitude)
    }),
    { latitude: 0, longitude: 0 }
  )
  return { latitude: sum.latitude / located.length, longitude: sum.longitude / located.length }
})

const handleAreaSearchComplete = (event) => runAreaSearch(event)

const handleAreaSelect = async (suggestion) => {
  const coordinates = getTripPlanSuggestionCoordinates(suggestion)
  if (!coordinates) return

  areaLabel.value = suggestion?.title || suggestion?.displayName || 'Selected area'
  const moved = store.setArea(coordinates)
  if (moved) {
    await refresh()
  }
}

const usePlannedStops = async () => {
  if (!plannedStopsCentroid.value) return
  areaLabel.value = 'Your planned stops'
  store.setArea(plannedStopsCentroid.value)
  await refresh()
}

const refresh = async () => {
  store.setRadius(radius.value)
  await store.search()
}

const resetArea = () => {
  areaLabel.value = ''
  store.clear()
}

/** A place is "added" when a plan item already sits on top of it. */
const isAdded = (poi) =>
  props.planItems.some(
    (item) =>
      Number.isFinite(Number(item.latitude))
      && Math.abs(Number(item.latitude) - poi.latitude) < 1e-5
      && Math.abs(Number(item.longitude) - poi.longitude) < 1e-5
  )
</script>

<style scoped>
.poi-discovery { padding: var(--gp-spacing-md, 1rem) 0; }
.poi-discovery-title { margin: 0; font-size: 1.05rem; }
.poi-discovery-subtitle { margin: 0.25rem 0 0; font-size: 0.85rem; }
.poi-discovery-controls { display: flex; gap: 0.5rem; flex-wrap: wrap; align-items: center; margin: 1rem 0 0.5rem; }
.poi-area-search { flex: 1 1 18rem; min-width: 14rem; }
.poi-radius-select { width: 8rem; }
.poi-area-summary { display: flex; align-items: center; gap: 0.5rem; font-size: 0.9rem; margin-bottom: 0.75rem; }
.poi-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(15rem, 1fr)); gap: 1rem; }
.poi-card { display: flex; flex-direction: column; gap: 0.5rem; }
.poi-card-body { display: flex; flex-direction: column; gap: 0.35rem; }
.poi-card-title { margin: 0; font-size: 0.95rem; }
.poi-card-description { margin: 0; font-size: 0.82rem; color: var(--gp-text-secondary, #6b7280); }
.poi-card-credit { margin: 0; font-size: 0.7rem; color: var(--gp-text-secondary, #6b7280); }
.poi-card-action { align-self: flex-start; margin-top: 0.25rem; }
.poi-attribution { margin-top: 1rem; font-size: 0.72rem; color: var(--gp-text-secondary, #6b7280); }
.poi-empty { text-align: center; padding: 2rem 1rem; color: var(--gp-text-secondary, #6b7280); }
.poi-empty-icon { font-size: 2rem; display: block; margin-bottom: 0.5rem; }
.mt-2 { margin-top: 0.5rem; }
</style>
