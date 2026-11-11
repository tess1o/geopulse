<template>
  <div ref="picker" class="place-picker" :role="label ? 'group' : undefined" :aria-label="label || undefined" @keydown.esc="escape">
    <div v-if="current" class="place-picker-current">
      <span class="place-picker-caption">{{ t('timeline.edit.place.current') }}</span>
      <span class="place-picker-name">{{ current.locationName }}</span>
    </div>
    <div v-if="modelValue" ref="selectedSummary" class="place-picker-selected" aria-live="polite">
      <span class="place-picker-name"><i class="pi pi-map-marker" aria-hidden="true" />{{ modelValue.locationName || t('missingActivity.selectedLocation') }}</span>
      <Button v-if="!isChoosing" :label="t('missingActivity.changePlace')" text size="small" @click="open" />
    </div>
    <Button v-else-if="!isChoosing" :label="t('missingActivity.choosePlaceAction')" icon="pi pi-map-marker" outlined @click="open" />
    <Button v-if="mapPlace && modelValue?.source === 'custom' && !isChoosing"
      :label="t('timeline.edit.place.useLinkedPlace', { name: mapPlace.locationName })" text size="small" @click="choose(mapPlace)" />
    <section v-if="isChoosing" class="place-picker-section">
      <label v-if="allowSearch" class="place-picker-search">{{ t('missingActivity.findPlace') }}
        <InputText ref="searchInput" v-model="query" :placeholder="t('missingActivity.findPlace')"
          @keydown.down.prevent="focusOption(0)" autocomplete="off" />
      </label>
      <Button v-if="allowMap || externalMap" :label="t('timeline.edit.place.pickOnMap')" icon="pi pi-map" text @click="requestMap" />
      <small v-if="searchLoading || lookupLoading" role="status">{{ t('timeline.edit.place.resolving') }}</small>
      <small v-if="searchError || lookupError" role="alert" class="place-picker-error">{{ searchError || lookupError }}</small>
      <span v-if="!query.trim() && options.length" class="place-picker-caption">{{ t('missingActivity.nearbyPlaces') }}</span>
      <div ref="optionList" class="place-picker-options">
        <button v-for="(option, index) in visibleOptions" :key="placeKey(option)" type="button" class="place-picker-option"
          @mouseenter="preview(option)" @mouseleave="clearPreview" @focus="preview(option)" @blur="clearPreview"
          @keydown.down.prevent="focusOption(index + 1)" @keydown.up.prevent="focusOption(index - 1)" @click="choose(option)">
          <i :class="option.favoriteId ? 'pi pi-star' : 'pi pi-map-marker'" aria-hidden="true" />
          <span>{{ option.locationName }}</span>
        </button>
      </div>
      <small v-if="query.trim().length >= 2 && !searchLoading && !searchError && !visibleOptions.length">{{ t('missingActivity.noMatchingPlaces') }}</small>
      <Button v-if="modelValue" :label="t('common.cancel')" text size="small" @click="cancel" />
    </section>
    <template v-if="allowMap && mapOpen">
      <small>{{ t('timeline.edit.place.mapHint') }}</small>
      <div class="place-picker-map">
        <MapContainer :map-id="mapId" :center="mapCenter" :zoom="15" :show-controls="false"
          :enable-fullscreen="false" height="100%" width="100%" @map-click="handleMapClick">
          <template #overlays="{ map }">
            <TripReconstructionMapOverlay v-if="map" :map="map" :active-segment="pickedSegment"
              :active-segment-index="0" :context-points="referencePoints" :id-prefix="mapId" @stay-dragged="pickPoint" />
          </template>
        </MapContainer>
      </div>
      <span v-if="reference" class="place-picker-legend">{{ referenceCaption || t('timeline.edit.place.referenceCaption') }}: {{ reference.locationName }}</span>
    </template>
  </div>
</template>

<script setup>
import { computed, ref, watch, onBeforeUnmount, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
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

const props = defineProps({
  modelValue: { type: Object, default: null }, current: { type: Object, default: null },
  suggestions: { type: Array, default: () => [] }, lookupAt: { type: Object, default: null },
  point: { type: Object, default: null }, reference: { type: Object, default: null }, referenceCaption: { type: String, default: '' },
  allowMap: { type: Boolean, default: true }, allowSearch: { type: Boolean, default: true },
  label: { type: String, default: '' }, externalMap: Boolean, expanded: { type: Boolean, default: null }, mapId: { type: String, default: 'timeline-place-picker-map' }
})
const emit = defineEmits(['update:modelValue', 'update:expanded', 'preview', 'request-map'])
const { t } = useI18n()
const timelineStore = useTimelineStore(), locationAnalyticsStore = useLocationAnalyticsStore(), tripsStore = useTripsStore()
const choosing = ref(!props.modelValue), query = ref(''), mapOpen = ref(false), previewPoint = ref(null)
const picker = ref(null)
const searchInput = ref(null), optionList = ref(null), selectedSummary = ref(null)
const mapPlace = ref(null)
const lookupOptions = ref([]), searchResults = ref([])
const lookupLoading = ref(false), searchLoading = ref(false), lookupError = ref(''), searchError = ref('')
let lookupToken = 0, pickToken = 0, searchToken = 0, searchTimer
const isChoosing = computed(() => props.expanded ?? choosing.value)
const options = computed(() => {
  const seen = new Set(props.current ? [placeKey(props.current)] : [])
  return [...props.suggestions, ...lookupOptions.value].filter(option => {
    const key = placeKey(option)
    if (!option?.locationName || seen.has(key)) return false
    seen.add(key); return true
  }).slice(0, 3)
})
const visibleOptions = computed(() => query.value.trim() ? searchResults.value : options.value)
const referencePoints = computed(() => [
  ...(props.reference?.latitude != null ? [{ ...props.reference, label: '•' }] : []),
  ...(previewPoint.value ? [{ ...previewPoint.value, label: '◇' }] : [])
])
const mapCenter = computed(() => {
  const point = props.modelValue || props.reference || props.lookupAt || props.current
  return point?.latitude != null ? [point.latitude, point.longitude] : [51.505, -0.09]
})
const pickedSegment = computed(() => props.modelValue?.latitude != null && {
  ...props.modelValue, id: 'picked-place', segmentType: 'STAY'
} || null)
watch(() => [props.lookupAt?.latitude, props.lookupAt?.longitude], () => {
  if (props.lookupAt?.latitude != null) lookup(props.lookupAt)
}, { immediate: true })
watch(() => props.point, point => { if (point) pickPoint(point) }, { immediate: true })
watch(isChoosing, value => { if (!value) clearPreview() })
watch(query, value => {
  searchToken++; clearTimeout(searchTimer); searchResults.value = []; searchError.value = ''
  searchLoading.value = value.trim().length >= 2
  if (searchLoading.value) searchTimer = setTimeout(() => search({ query: value }), 250)
})
onBeforeUnmount(() => { pickToken++; lookupToken++; searchToken++; clearTimeout(searchTimer) })
function setChoosing(value) { choosing.value = value; emit('update:expanded', value) }
function open() { query.value = ''; setChoosing(true); nextTick(() => searchInput.value?.$el?.focus()) }
function escape(event) {
  if (isChoosing.value && props.modelValue) { event.stopPropagation(); event.preventDefault(); cancel() }
}
function cancel() {
  if (!props.modelValue) { clearPreview(); return }
  pickToken++; searchToken++; clearTimeout(searchTimer); clearPreview(); mapOpen.value = false; setChoosing(false); nextTick(() => selectedSummary.value?.querySelector('button')?.focus())
}
function clearPreview() { previewPoint.value = null; emit('preview', null) }
function preview(option) {
  if (Number.isFinite(option?.latitude) && Number.isFinite(option?.longitude)) {
    previewPoint.value = option; emit('preview', option)
  }
}
function focusOption(index) {
  const buttons = optionList.value?.querySelectorAll('button') || []
  if (index < 0) searchInput.value?.$el?.focus()
  else buttons[Math.min(index, buttons.length - 1)]?.focus()
}
function requestMap() {
  clearPreview()
  if (props.externalMap) emit('request-map', picker.value)
  else mapOpen.value = true
}
function choose(option, focusSummary = true) {
  pickToken++; searchToken++; clearTimeout(searchTimer); clearPreview()
  mapPlace.value = null
  emit('update:modelValue', {
    favoriteId: option.favoriteId ?? null, geocodingId: option.geocodingId ?? null,
    latitude: option.latitude ?? null, longitude: option.longitude ?? null,
    locationName: option.locationName, source: option.source || (option.favoriteId ? 'favorite' : 'known')
  })
  setChoosing(false)
  if (focusSummary) nextTick(() => selectedSummary.value?.querySelector('button')?.focus())
}
async function lookup(point) {
  const token = ++lookupToken
  lookupLoading.value = true; lookupError.value = ''
  try {
    const result = await timelineStore.lookupLocation(point.latitude, point.longitude, { redirectOnServerError: false })
    if (token !== lookupToken) return
    lookupOptions.value = [
      ...(result?.favoriteMatches || []).map(favorite => ({ favoriteId: favorite.id, locationName: favorite.name,
        latitude: favorite.latitude ?? point.latitude, longitude: favorite.longitude ?? point.longitude })),
      ...(result?.visitMatches || []).map(match => ({ favoriteId: match.favoriteId, geocodingId: match.geocodingId,
        locationName: match.name, latitude: match.visits?.[0]?.latitude ?? point.latitude, longitude: match.visits?.[0]?.longitude ?? point.longitude }))
    ]
  } catch (problem) {
    if (token === lookupToken) lookupError.value = formatApiErrorDetail(problem, t('timeline.edit.place.lookupFailed'))
  } finally { if (token === lookupToken) lookupLoading.value = false }
}
async function search(event) {
  const token = ++searchToken, text = event?.query?.trim() || ''
  searchError.value = ''; searchResults.value = []
  if (text.length < 2) { searchLoading.value = false; return }
  searchLoading.value = true
  try {
    const results = await locationAnalyticsStore.searchLocations(text, 'place', { redirectOnServerError: false })
    if (token !== searchToken) return
    searchResults.value = (results || []).filter(result => ['favorite', 'geocoding'].includes(result?.category))
      .map(result => ({ ...result, favoriteId: result.category === 'favorite' ? result.id : null,
        geocodingId: result.category === 'geocoding' ? result.id : null, locationName: result.displayName || result.name }))
  } catch (problem) {
    if (token === searchToken) searchError.value = formatApiErrorDetail(problem, t('timeline.edit.place.searchFailed'))
  } finally { if (token === searchToken) searchLoading.value = false }
}
function handleMapClick(event) { pickPoint(event?.latlng || event?.lngLat || event) }
async function pickPoint(raw) {
  const normalized = normalizeLatLngPoint(raw)
  if (!normalized) return
  choose({ latitude: normalized.lat, longitude: normalized.lng, locationName: t('missingActivity.selectedLocation'), source: 'custom' }, false)
  const token = pickToken, selected = { latitude: normalized.lat, longitude: normalized.lng }
  lookup(selected)
  try {
    const suggestion = await tripsStore.getPlanSuggestion(selected.latitude, selected.longitude, { redirectOnServerError: false })
    if (token === pickToken && suggestion?.title?.trim()) {
      const locationName = suggestion.title.trim()
      emit('update:modelValue', { ...selected, favoriteId: null, geocodingId: null, locationName, source: 'custom' })
      if (suggestion.favoriteId || suggestion.geocodingId) {
        mapPlace.value = { ...selected, favoriteId: suggestion.favoriteId, geocodingId: suggestion.geocodingId, locationName }
      }
    }
  } catch { /* Coordinates remain usable without an address. */ }
}
</script>

<style scoped>
.place-picker, .place-picker-section, .place-picker-options { display: grid; gap: .5rem; min-width: 0; }
.place-picker-current { display: grid; gap: .25rem; color: var(--gp-text-secondary); }
.place-picker-selected { display: flex; align-items: center; justify-content: space-between; gap: .5rem; padding: .75rem; border: 1px solid var(--gp-primary-soft-border); border-radius: var(--gp-radius-medium); background: color-mix(in srgb, var(--gp-primary) 7%, var(--gp-surface-card)); }
.place-picker-name { display: flex; gap: .5rem; align-items: center; overflow-wrap: anywhere; min-width: 0; }
.place-picker-caption { font-size: .75rem; color: var(--gp-text-secondary); }
.place-picker-search { display: grid; gap: .4rem; font-size: .85rem; }
.place-picker-search :deep(input) { width: 100%; }
.place-picker-option { display: flex; align-items: center; gap: .5rem; padding: .65rem .75rem; border: 1px solid var(--gp-border); border-radius: var(--gp-radius-medium); background: var(--gp-surface-card); color: var(--gp-text-primary); font: inherit; text-align: left; cursor: pointer; }
.place-picker-option:hover, .place-picker-option:focus-visible { border-color: var(--gp-primary); background: var(--gp-surface-hover); }
.place-picker-option:focus-visible { outline: 2px solid var(--gp-primary); outline-offset: 2px; }
.place-picker-map { height: 15rem; overflow: hidden; border-radius: var(--gp-radius-medium); }
.place-picker-legend, small { color: var(--gp-text-secondary); font-size: .8rem; }
.place-picker-error { color: var(--gp-danger-text); }
.place-picker .p-button { justify-self: start; }
</style>
