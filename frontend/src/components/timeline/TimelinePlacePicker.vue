<template>
  <div class="place-picker">
    <div v-if="current" class="place-picker-current">
      <span class="place-picker-caption">{{ t('timeline.edit.place.current') }}</span>
      <span class="place-picker-name"><i class="pi pi-map-marker" aria-hidden="true" />{{ current.locationName }}</span>
    </div>

    <div v-if="modelValue" class="place-picker-selected" aria-live="polite">
      <span class="place-picker-caption">{{ t('timeline.edit.place.selected') }}</span>
      <span class="place-picker-name"><i class="pi pi-check-circle" aria-hidden="true" />{{ modelValue.locationName || t('timeline.edit.place.unnamed') }}</span>
      <span class="place-picker-source">{{ sourceLabel(modelValue) }}</span>
    </div>

    <section v-if="options.length || lookupLoading || lookupError" class="place-picker-section">
      <h4>{{ t('timeline.edit.place.suggestions') }}</h4>
      <small v-if="lookupLoading">{{ t('timeline.edit.place.resolving') }}</small>
      <small v-else-if="lookupError" class="place-picker-error">{{ lookupError }}</small>
      <div class="place-picker-options">
        <button v-for="option in options" :key="placeKey(option)" type="button" class="place-picker-option"
          :class="{ 'is-selected': isSelected(option) }" :aria-pressed="isSelected(option)" @click="choose(option)">
          <i :class="option.favoriteId ? 'pi pi-star' : 'pi pi-map-marker'" aria-hidden="true" />
          <span>{{ option.locationName }}</span>
          <small>{{ option.hint || sourceLabel(option) }}</small>
        </button>
      </div>
    </section>

    <section v-if="allowSearch" class="place-picker-section">
      <label class="place-picker-search">{{ t('timeline.edit.place.search') }}
        <AutoComplete v-model="searchValue" :suggestions="searchResults" option-label="displayName"
          :placeholder="t('timeline.edit.place.searchPlaceholder')" :min-length="2" :delay="250"
          :loading="searchLoading" force-selection @complete="search" @option-select="chooseSearchResult" />
      </label>
      <small v-if="searchError" class="place-picker-error">{{ searchError }}</small>
    </section>

    <section v-if="allowMap" class="place-picker-section">
      <Button type="button" :label="mapOpen ? t('timeline.edit.place.hideMap') : t('timeline.edit.place.pickOnMap')"
        icon="pi pi-map" text @click="mapOpen = !mapOpen" />
      <template v-if="mapOpen">
        <small>{{ t('timeline.edit.place.mapHint') }}</small>
        <div class="place-picker-map">
          <MapContainer :map-id="mapId" :center="mapCenter" :zoom="15" :show-controls="false"
            :enable-fullscreen="false" height="100%" width="100%" @map-click="handleMapClick">
            <template #overlays="{ map }">
              <TripReconstructionMapOverlay v-if="map && (pickedPoint || referencePoints.length)" :map="map"
                :active-segment="pickedSegment" :active-segment-index="0" :context-points="referencePoints"
                :id-prefix="mapId" @stay-dragged="pickPoint" />
            </template>
          </MapContainer>
        </div>
        <span v-if="referencePoints.length" class="place-picker-legend">
          <span class="trip-reconstruction-context-marker" aria-hidden="true">{{ referenceMarker }}</span>
          {{ referenceCaption || t('timeline.edit.place.referenceCaption') }}: {{ reference.locationName }}
        </span>
      </template>
    </section>

    <section v-if="pickedPoint" class="place-picker-section">
      <h4>{{ t('timeline.edit.place.newPlace') }}</h4>
      <div class="place-picker-new" :class="{ 'is-selected': customSelected }">
        <label>{{ t('timeline.edit.place.newPlaceName') }}
          <InputText v-model.trim="customName" maxlength="500"
            :placeholder="customNameLoading ? t('timeline.edit.place.resolving') : t('timeline.edit.place.newPlacePlaceholder')"
            @input="customNameEdited = true" />
        </label>
        <span v-if="customSelected" class="place-picker-new-state" aria-live="polite">
          <i class="pi pi-check-circle" aria-hidden="true" />{{ t('timeline.edit.place.usingNewPlace') }}
        </span>
        <Button v-else type="button" :label="t('timeline.edit.place.useNewPlace')" outlined size="small"
          :disabled="!customName" @click="chooseCustom" />
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AutoComplete from 'primevue/autocomplete'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import MapContainer from '@/components/maps/MapContainer.vue'
import TripReconstructionMapOverlay from '@/components/trips/reconstruction/TripReconstructionMapOverlay.vue'
import { normalizeLatLngPoint } from '@/maps/shared/coordinateUtils'
import { useTimelineStore } from '@/stores/timeline'
import { useLocationAnalyticsStore } from '@/stores/locationAnalytics'
import { useTripsStore } from '@/stores/trips'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'
import { placeKey } from '@/utils/timelineEditPlace'
import '@/maps/shared/styles/tripReconstructionMarkers.css'

// Chooses a place (see timelineEditPlace.js) from suggestions, places known around a point, a search of the user's
// places, or a point picked on the map. A picked point becomes the favorite there, if any, or a new named place.
const props = defineProps({
  modelValue: { type: Object, default: null },
  current: { type: Object, default: null },
  suggestions: { type: Array, default: () => [] },
  // Point whose favorites and known places are offered, without choosing one.
  lookupAt: { type: Object, default: null },
  // Point picked outside the picker, such as on a trip's route; handled like a point picked on its own map.
  point: { type: Object, default: null },
  // A place shown on the map for orientation only, such as the visit being split.
  reference: { type: Object, default: null },
  referenceCaption: { type: String, default: '' },
  allowMap: { type: Boolean, default: true },
  allowSearch: { type: Boolean, default: true },
  mapId: { type: String, default: 'timeline-place-picker-map' }
})
const emit = defineEmits(['update:modelValue'])
const { t } = useI18n()
const timelineStore = useTimelineStore()
const locationAnalyticsStore = useLocationAnalyticsStore()
const tripsStore = useTripsStore()

const lookupOptions = ref([])
const lookupLoading = ref(false)
const lookupError = ref('')
const searchValue = ref(null)
const searchResults = ref([])
const searchLoading = ref(false)
const searchError = ref('')
const mapOpen = ref(false)
const pickedPoint = ref(null)
const customName = ref('')
const customNameEdited = ref(false)
const customNameLoading = ref(false)
let lookupToken = 0
let pickToken = 0
let searchToken = 0

const options = computed(() => {
  const seen = new Set(props.current ? [placeKey(props.current)] : [])
  return [...props.suggestions, ...lookupOptions.value].filter(option => {
    const key = placeKey(option)
    if (!option?.locationName || seen.has(key)) return false
    seen.add(key)
    return true
  })
})
// The marker's text is inserted as HTML by the raster map, so it is a fixed glyph, never a place name.
const referenceMarker = '•'
const referencePoints = computed(() => props.reference?.latitude != null && props.reference?.longitude != null
  ? [{ id: 'reference-place', latitude: props.reference.latitude, longitude: props.reference.longitude, label: referenceMarker }]
  : [])
const mapCenter = computed(() => {
  const point = pickedPoint.value || props.reference || props.lookupAt || props.current
  return point?.latitude != null ? [point.latitude, point.longitude] : [51.505, -0.09]
})
const customSelected = computed(() => props.modelValue?.source === 'custom')
const pickedSegment = computed(() => pickedPoint.value && {
  id: 'picked-place', segmentType: 'STAY', latitude: pickedPoint.value.latitude,
  longitude: pickedPoint.value.longitude, locationName: customName.value || t('timeline.edit.place.newPlace')
})

// Typing a name means the new place is the one meant, and keeps the chosen place's name current.
watch(customName, () => {
  if (customNameEdited.value) chooseCustom()
})
watch(() => [props.lookupAt?.latitude, props.lookupAt?.longitude], () => {
  if (props.lookupAt?.latitude != null) lookup(props.lookupAt)
}, { immediate: true })
watch(() => [props.point?.latitude, props.point?.longitude], () => {
  if (props.point?.latitude != null) pickPoint(props.point)
}, { immediate: true })

async function lookup(point) {
  const token = ++lookupToken
  lookupLoading.value = true
  lookupError.value = ''
  try {
    const result = await timelineStore.lookupLocation(point.latitude, point.longitude)
    if (token !== lookupToken) return null
    lookupOptions.value = [
      ...(result?.favoriteMatches || []).map(favorite => ({
        favoriteId: favorite.id, locationName: favorite.name, source: 'favorite'
      })),
      ...(result?.visitMatches || []).map(match => ({
        favoriteId: match.favoriteId ?? null, geocodingId: match.geocodingId ?? null,
        locationName: match.name, source: match.favoriteId ? 'favorite' : 'known'
      }))
    ]
    return lookupOptions.value
  } catch (problem) {
    if (token === lookupToken) lookupError.value = formatApiErrorDetail(problem, t('timeline.edit.place.lookupFailed'))
    return null
  } finally {
    if (token === lookupToken) lookupLoading.value = false
  }
}

async function search(event) {
  const token = ++searchToken
  const query = event?.query?.trim() || ''
  searchError.value = ''
  searchResults.value = []
  if (query.length < 2) { searchLoading.value = false; return }
  searchLoading.value = true
  try {
    const results = await locationAnalyticsStore.searchLocations(query, 'place')
    if (token !== searchToken) return
    searchResults.value = (results || [])
      .filter(result => result?.category === 'favorite' || result?.category === 'geocoding')
      .map(result => ({ ...result, displayName: result.displayName || result.name }))
  } catch (problem) {
    if (token === searchToken) searchError.value = formatApiErrorDetail(problem, t('timeline.edit.place.searchFailed'))
  } finally {
    if (token === searchToken) searchLoading.value = false
  }
}

function chooseSearchResult(event) {
  const result = event?.value
  if (!result) return
  choose({
    favoriteId: result.category === 'favorite' ? result.id : null,
    geocodingId: result.category === 'geocoding' ? result.id : null,
    locationName: result.displayName, source: result.category === 'favorite' ? 'favorite' : 'known'
  })
}

function handleMapClick(event) {
  pickPoint(event?.latlng || event?.lngLat || event)
}

async function pickPoint(raw) {
  const normalized = normalizeLatLngPoint(raw)
  if (!normalized) return
  const point = { latitude: normalized.lat, longitude: normalized.lng }
  const token = ++pickToken
  pickedPoint.value = point
  const found = lookup(point)
  if (!customNameEdited.value) {
    customNameLoading.value = true
    try {
      const suggestion = await tripsStore.getPlanSuggestion(point.latitude, point.longitude)
      if (!customNameEdited.value && token === pickToken) customName.value = suggestion?.title?.trim() || ''
    } catch {
      // The user can still name the place.
    } finally {
      if (token === pickToken) customNameLoading.value = false
    }
  }
  const favorite = (await found)?.find(option => option.source === 'favorite')
  if (token !== pickToken) return
  if (favorite) choose(favorite)
  else chooseCustom()
}

function chooseCustom() {
  if (!pickedPoint.value || !customName.value) return
  choose({
    latitude: pickedPoint.value.latitude, longitude: pickedPoint.value.longitude,
    locationName: customName.value, source: 'custom'
  })
}

function choose(option) {
  pickToken++
  emit('update:modelValue', {
    favoriteId: option.favoriteId ?? null, geocodingId: option.geocodingId ?? null,
    latitude: option.latitude ?? null, longitude: option.longitude ?? null,
    locationName: option.locationName, source: option.source || (option.favoriteId ? 'favorite' : 'known')
  })
}

function isSelected(option) { return props.modelValue != null && placeKey(props.modelValue) === placeKey(option) }

function sourceLabel(place) {
  if (place?.source === 'custom') return t('timeline.edit.place.source.custom')
  return place?.favoriteId ? t('timeline.edit.place.source.favorite') : t('timeline.edit.place.source.known')
}

</script>

<style>
.place-picker {
  display: grid;
  gap: 1rem;
}

.place-picker-current,
.place-picker-selected {
  display: grid;
  gap: .2rem;
  padding: .75rem .85rem;
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-medium);
}

.place-picker-selected {
  border-color: var(--gp-primary-soft-border);
  background: color-mix(in srgb, var(--gp-primary) 7%, var(--gp-surface-card));
}

.place-picker-caption,
.place-picker-section h4 {
  margin: 0;
  color: var(--gp-text-secondary);
  font-size: .72rem;
  font-weight: 700;
  letter-spacing: .06em;
  text-transform: uppercase;
}

.place-picker-name {
  display: flex;
  align-items: center;
  gap: .5rem;
  color: var(--gp-text-primary);
  font-weight: 650;
  overflow-wrap: anywhere;
}

.place-picker-source {
  color: var(--gp-text-muted);
  font-size: .76rem;
}

.place-picker-section {
  display: grid;
  gap: .5rem;
}

.place-picker-options {
  display: grid;
  gap: .4rem;
}

.place-picker-option {
  display: grid;
  grid-template-columns: 1.25rem minmax(0, 1fr);
  column-gap: .55rem;
  align-items: center;
  padding: .55rem .7rem;
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-card);
  color: var(--gp-text-primary);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.place-picker-option small {
  grid-column: 2;
  color: var(--gp-text-muted);
  font-size: .74rem;
}

.place-picker-option:hover,
.place-picker-option.is-selected {
  border-color: var(--gp-primary);
  background: var(--gp-surface-hover);
}

.place-picker-option:focus-visible {
  outline: 2px solid var(--gp-primary);
  outline-offset: 2px;
}

.place-picker-search .p-autocomplete,
.place-picker-search .p-autocomplete-input {
  width: 100%;
}

.place-picker-map {
  height: 15rem;
  overflow: hidden;
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-medium);
}

.place-picker-new {
  display: grid;
  gap: .6rem;
  padding: .75rem .85rem;
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-medium);
}

.place-picker-new.is-selected {
  border-color: var(--gp-primary);
  background: color-mix(in srgb, var(--gp-primary) 7%, var(--gp-surface-card));
}

.place-picker-new label {
  display: grid;
  gap: .4rem;
}

.place-picker-new-state {
  display: flex;
  align-items: center;
  gap: .45rem;
  color: var(--gp-primary-text);
  font-size: .82rem;
  font-weight: 650;
}

.place-picker-legend {
  display: flex;
  align-items: center;
  gap: .5rem;
  color: var(--gp-text-secondary);
  font-size: .78rem;
  overflow-wrap: anywhere;
}

.place-picker-legend .trip-reconstruction-context-marker {
  flex: 0 0 auto;
}

.place-picker-error {
  color: var(--gp-danger-text);
}

.place-picker .p-button {
  justify-self: start;
}
</style>
