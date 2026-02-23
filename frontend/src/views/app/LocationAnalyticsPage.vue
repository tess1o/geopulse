<template>
  <AppLayout variant="default">
    <PageContainer
      title="Location Analytics"
      subtitle="Explore your visits by map, city, and country"
      variant="fullwidth"
    >
      <template #actions>
        <div class="header-actions">
          <div class="search-container">
            <LocationSearchBar />
          </div>

          <div class="analytics-tabs">
            <Button
              label="Map"
              icon="pi pi-map-marker"
              :class="{ 'active-tab': activeTab === 'map' }"
              @click="handleTabClick('map')"
            />
            <Button
              :label="citiesTabLabel"
              icon="pi pi-building"
              :class="{ 'active-tab': activeTab === 'cities' }"
              @click="handleTabClick('cities')"
            />
            <Button
              :label="countriesTabLabel"
              icon="pi pi-globe"
              :class="{ 'active-tab': activeTab === 'countries' }"
              @click="handleTabClick('countries')"
            />
          </div>
        </div>
      </template>

      <!-- Map Panel -->
      <div v-if="activeTab === 'map'" class="map-tab-content">
        <LocationAnalyticsMap
          :places="sortedMapPlaces"
          :loading="mapPlacesLoading"
          :selected-place-key="selectedMapPlaceKey"
          :hovered-place-key="hoveredMapPlaceKey"
          :selection-focus-mode="selectedMapPlaceFocusMode"
          @viewport-change="handleMapViewportChange"
          @place-click="handleMapPlaceClick"
          @open-place-details="openMapPlaceDetails"
        />

        <BaseCard class="map-places-card">
          <template #header>
            <div class="map-places-title">
              <div class="map-places-heading">
                <span class="map-places-label">Recent places in view</span>
                <span class="map-places-count">{{ sortedMapPlaces.length }}</span>
              </div>
              <div class="map-places-controls">
                <Button
                  icon="pi pi-angle-left"
                  text
                  rounded
                  :disabled="!canScrollRailLeft"
                  aria-label="Scroll places left"
                  @click="scrollPlacesRail(-1)"
                />
                <Button
                  icon="pi pi-angle-right"
                  text
                  rounded
                  :disabled="!canScrollRailRight"
                  aria-label="Scroll places right"
                  @click="scrollPlacesRail(1)"
                />
              </div>
            </div>
          </template>
          <div v-if="mapPlacesLoading && sortedMapPlaces.length === 0" class="loading-container">
            <ProgressSpinner />
          </div>
          <div v-else-if="sortedMapPlaces.length === 0" class="empty-state compact">
            <i class="pi pi-map-marker empty-icon"></i>
            <p>No places found for this area.</p>
          </div>
          <div v-else class="map-places-rail-wrapper">
            <div
              ref="mapPlacesRailRef"
              class="map-places-rail"
              @scroll="updateRailScrollState"
            >
              <article
              v-for="place in mapPlacesPreview"
              :key="getPlaceKey(place)"
              class="map-place-item"
              :class="{
                active: selectedMapPlaceKey === getPlaceKey(place),
                hovered: hoveredMapPlaceKey === getPlaceKey(place)
              }"
              :ref="(el) => setMapPlaceRef(place, el)"
              @click="selectMapPlace(place)"
              @mouseenter="setHoveredMapPlace(place)"
              @mouseleave="clearHoveredMapPlace(place)"
            >
              <div class="map-place-thumb">
                <i class="pi pi-map-marker"></i>
              </div>
              <div class="map-place-main">
                <div class="map-place-name">{{ place.locationName || 'Unknown location' }}</div>
                <div class="map-place-meta">
                  {{ [place.city, place.country].filter(Boolean).join(', ') || 'Unknown area' }}
                </div>
                <div class="map-place-timeline">
                  Last visit: {{ formatLastVisitFull(place.lastVisit) }}
                </div>
              </div>
              <div class="map-place-side">
                <div class="map-place-visits" :title="`${place.visitCount} visits`">
                  <span class="value">{{ place.visitCount }}</span>
                  <span class="label">visits</span>
                </div>
                <Button
                  icon="pi pi-external-link"
                  class="map-place-open-btn"
                  text
                  rounded
                  aria-label="Open place details"
                  @click.stop="openMapPlaceDetails(place)"
                />
              </div>
              </article>
            </div>
          </div>
        </BaseCard>
      </div>

      <!-- Cities Panel -->
      <div v-if="activeTab === 'cities'">
        <div v-if="citiesLoading" class="loading-container">
          <ProgressSpinner />
        </div>

        <div v-else-if="cities.length === 0" class="empty-state">
          <i class="pi pi-building empty-icon"></i>
          <p>No cities found in your travel history</p>
        </div>

        <div v-else class="location-grid">
          <BaseCard
            v-for="city in cities"
            :key="`${city.cityName}-${city.country}`"
            class="location-card"
            @click="navigateToCity(city.cityName)"
          >
            <div class="location-icon">
              <i class="pi pi-building"></i>
            </div>
            <h3 class="location-name">{{ city.cityName }}</h3>
            <p class="location-country">{{ city.country }}</p>
            <div class="location-stats">
              <div class="stat-item">
                <span class="stat-value">{{ city.visitCount }}</span>
                <span class="stat-label">visits</span>
              </div>
              <div class="stat-item">
                <span class="stat-value">{{ city.uniquePlaces }}</span>
                <span class="stat-label">places</span>
              </div>
            </div>
          </BaseCard>
        </div>
      </div>

      <!-- Countries Panel -->
      <div v-if="activeTab === 'countries'">
        <div v-if="countriesLoading" class="loading-container">
          <ProgressSpinner />
        </div>

        <div v-else-if="countries.length === 0" class="empty-state">
          <i class="pi pi-globe empty-icon"></i>
          <p>No countries found in your travel history</p>
        </div>

        <div v-else class="location-grid">
          <BaseCard
            v-for="country in countries"
            :key="country.countryName"
            class="location-card"
            @click="navigateToCountry(country.countryName)"
          >
            <div class="location-icon">
              <i class="pi pi-globe"></i>
            </div>
            <h3 class="location-name">{{ country.countryName }}</h3>
            <div class="location-stats">
              <div class="stat-item">
                <span class="stat-value">{{ country.visitCount }}</span>
                <span class="stat-label">visits</span>
              </div>
              <div class="stat-item">
                <span class="stat-value">{{ country.cityCount }}</span>
                <span class="stat-label">cities</span>
              </div>
              <div class="stat-item">
                <span class="stat-value">{{ country.uniquePlaces }}</span>
                <span class="stat-label">places</span>
              </div>
            </div>
          </BaseCard>
        </div>
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import ProgressSpinner from 'primevue/progressspinner'

import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import LocationSearchBar from '@/components/search/LocationSearchBar.vue'
import LocationAnalyticsMap from '@/components/location-analytics/LocationAnalyticsMap.vue'

import { useLocationAnalyticsStore } from '@/stores/locationAnalytics'

const router = useRouter()
const route = useRoute()
const toast = useToast()
const store = useLocationAnalyticsStore()

const {
  mapPlaces,
  mapPlacesLoading,
  cities,
  countries,
  citiesLoading,
  countriesLoading
} = storeToRefs(store)

const TAB_MAP = 'map'
const TAB_CITIES = 'cities'
const TAB_COUNTRIES = 'countries'
const VALID_TABS = new Set([TAB_MAP, TAB_CITIES, TAB_COUNTRIES])

const normalizeTab = (value) => {
  if (typeof value !== 'string') return TAB_MAP
  return VALID_TABS.has(value) ? value : TAB_MAP
}

const activeTab = ref(normalizeTab(route.query.tab))
const citiesLoaded = ref(false)
const countriesLoaded = ref(false)
const selectedMapPlace = ref(null)
const lastMapRequestKey = ref('')
const mapPlacesRailRef = ref(null)
const canScrollRailLeft = ref(false)
const canScrollRailRight = ref(false)
const mapPlaceRefs = new Map()
const hoveredMapPlaceKey = ref(null)
const selectedMapPlaceFocusMode = ref('pan')
let mapFetchTimer = null

const getPlaceKey = (place) => `${place.type}-${place.id}`

const selectedMapPlaceKey = computed(() => {
  if (!selectedMapPlace.value) return null
  return getPlaceKey(selectedMapPlace.value)
})

const sortedMapPlaces = computed(() => {
  if (!mapPlaces.value || mapPlaces.value.length === 0) return []
  return [...mapPlaces.value].sort((a, b) => {
    const aTime = a.lastVisit ? new Date(a.lastVisit).getTime() : 0
    const bTime = b.lastVisit ? new Date(b.lastVisit).getTime() : 0
    return bTime - aTime
  })
})

const mapPlacesPreview = computed(() => sortedMapPlaces.value.slice(0, 60))
const citiesTabLabel = computed(() => (citiesLoaded.value ? `Cities (${cities.value.length})` : 'Cities'))
const countriesTabLabel = computed(() => (countriesLoaded.value ? `Countries (${countries.value.length})` : 'Countries'))

const formatLastVisitFull = (timestamp) => {
  if (!timestamp) return 'Unknown'
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return 'Unknown'
  return date.toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  })
}

const setMapPlaceRef = (place, element) => {
  const key = getPlaceKey(place)
  if (element) {
    mapPlaceRefs.set(key, element)
  } else {
    mapPlaceRefs.delete(key)
  }
}

const setHoveredMapPlace = (place) => {
  hoveredMapPlaceKey.value = getPlaceKey(place)
}

const clearHoveredMapPlace = (place) => {
  const key = getPlaceKey(place)
  if (hoveredMapPlaceKey.value === key) {
    hoveredMapPlaceKey.value = null
  }
}

const updateRailScrollState = () => {
  const rail = mapPlacesRailRef.value
  if (!rail) {
    canScrollRailLeft.value = false
    canScrollRailRight.value = false
    return
  }

  const maxScrollLeft = rail.scrollWidth - rail.clientWidth
  canScrollRailLeft.value = rail.scrollLeft > 4
  canScrollRailRight.value = rail.scrollLeft < (maxScrollLeft - 4)
}

const scrollPlacesRail = (direction) => {
  const rail = mapPlacesRailRef.value
  if (!rail) return

  const step = Math.max(260, rail.clientWidth * 0.72)
  rail.scrollBy({
    left: step * direction,
    behavior: 'smooth'
  })
}

const buildViewportKey = (viewport) => {
  if (!viewport) return 'all'
  const round = (value) => (typeof value === 'number' ? value.toFixed(4) : 'x')
  return `${round(viewport.minLat)}:${round(viewport.maxLat)}:${round(viewport.minLon)}:${round(viewport.maxLon)}:${round(viewport.zoom)}`
}

const loadCities = async () => {
  if (citiesLoaded.value || citiesLoading.value) return
  await store.fetchAllCities()
  citiesLoaded.value = true
}

const loadCountries = async () => {
  if (countriesLoaded.value || countriesLoading.value) return
  await store.fetchAllCountries()
  countriesLoaded.value = true
}

const syncTabQuery = async (tab) => {
  const current = normalizeTab(route.query.tab)
  if (current === tab && route.query.tab === tab) return

  await router.replace({
    query: {
      ...route.query,
      tab
    }
  })
}

const handleTabClick = (tab) => {
  activeTab.value = normalizeTab(tab)
}

const prefetchTabCounts = async () => {
  const [citiesResult, countriesResult] = await Promise.allSettled([
    loadCities(),
    loadCountries()
  ])

  if (citiesResult.status === 'rejected') {
    console.error('Failed to prefetch cities for tab count:', citiesResult.reason)
  }
  if (countriesResult.status === 'rejected') {
    console.error('Failed to prefetch countries for tab count:', countriesResult.reason)
  }
}

const fetchMapPlaces = async (viewport, force = false) => {
  const requestKey = buildViewportKey(viewport)
  if (!force && requestKey === lastMapRequestKey.value) {
    return
  }
  lastMapRequestKey.value = requestKey

  try {
    const params = {
      minVisits: 1,
      limit: 5000
    }

    if (viewport) {
      params.minLat = viewport.minLat
      params.maxLat = viewport.maxLat
      params.minLon = viewport.minLon
      params.maxLon = viewport.maxLon
    }

    await store.fetchMapPlaces(params)

    if (selectedMapPlace.value) {
      const selectedKey = getPlaceKey(selectedMapPlace.value)
      const exists = (mapPlaces.value || []).some((place) => getPlaceKey(place) === selectedKey)
      if (!exists) {
        selectedMapPlace.value = null
      }
    }
  } catch (error) {
    console.error('Failed to fetch map places:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load map places',
      life: 5000
    })
  }
}

const handleMapViewportChange = (viewport) => {
  if (mapFetchTimer) {
    clearTimeout(mapFetchTimer)
  }

  mapFetchTimer = setTimeout(() => {
    fetchMapPlaces(viewport)
  }, 240)
}

const handleMapPlaceClick = (place) => {
  selectedMapPlaceFocusMode.value = 'pan'
  selectedMapPlace.value = place
}

const selectMapPlace = (place) => {
  selectedMapPlaceFocusMode.value = 'soft-zoom'
  selectedMapPlace.value = place
}

const openMapPlaceDetails = (place) => {
  const resolvedRoute = router.resolve(`/app/place-details/${place.type}/${place.id}`)
  const newWindow = window.open(resolvedRoute.href, '_blank')
  if (!newWindow) {
    router.push(resolvedRoute.fullPath)
    return
  }
  // Prevent opener access without relying on noopener return semantics,
  // which can report null even when the tab opened.
  newWindow.opener = null
}

const navigateToCity = (cityName) => {
  router.push(`/app/location-analytics/city/${encodeURIComponent(cityName)}`)
}

const navigateToCountry = (countryName) => {
  router.push(`/app/location-analytics/country/${encodeURIComponent(countryName)}`)
}

watch(activeTab, async (newTab) => {
  try {
    await syncTabQuery(newTab)

    if (newTab === 'cities') {
      await loadCities()
    } else if (newTab === 'countries') {
      await loadCountries()
    }
  } catch (error) {
    console.error('Failed to load location analytics tab data:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load location data',
      life: 5000
    })
  }
})

watch(() => route.query.tab, (queryTab) => {
  const nextTab = normalizeTab(queryTab)
  if (nextTab !== activeTab.value) {
    activeTab.value = nextTab
  }
})

watch(mapPlacesPreview, async () => {
  await nextTick()
  updateRailScrollState()
})

watch(selectedMapPlaceKey, async (selectedKey) => {
  if (!selectedKey) return
  await nextTick()
  const selectedElement = mapPlaceRefs.get(selectedKey)
  if (selectedElement && selectedElement.scrollIntoView) {
    selectedElement.scrollIntoView({
      behavior: 'smooth',
      block: 'nearest',
      inline: 'center'
    })
  }
})

onMounted(() => {
  void syncTabQuery(activeTab.value)
  window.addEventListener('resize', updateRailScrollState, { passive: true })
  void prefetchTabCounts()
})

onBeforeUnmount(() => {
  if (mapFetchTimer) {
    clearTimeout(mapFetchTimer)
    mapFetchTimer = null
  }
  window.removeEventListener('resize', updateRailScrollState)
  mapPlaceRefs.clear()
})
</script>

<style scoped>
:deep(.gp-page-container) {
  height: auto;
}

:deep(.gp-page-header-content) {
  align-items: center;
}

:deep(.gp-page-actions) {
  width: min(920px, 62vw);
}

.header-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--gp-spacing-md);
  width: 100%;
  flex-wrap: nowrap;
}

.search-container {
  width: min(460px, 42vw);
  min-width: 280px;
}

.analytics-tabs {
  display: flex;
  gap: var(--gp-spacing-sm);
  flex-shrink: 0;
}

.analytics-tabs .p-button {
  min-width: 120px;
  background: transparent;
  border: 1px solid var(--gp-border-light);
  color: var(--gp-text-secondary);
  box-shadow: none;
}

.analytics-tabs .active-tab {
  background: var(--gp-primary);
  border-color: var(--gp-primary);
  color: white;
}

.analytics-tabs .p-button:not(.active-tab):hover {
  background: var(--gp-surface-light);
  border-color: color-mix(in srgb, var(--gp-primary) 45%, var(--gp-border-light));
  color: var(--gp-text-primary);
}

.map-tab-content {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-lg);
  padding: 0 var(--gp-spacing-lg);
}

.map-places-card {
  overflow: hidden;
}

.map-places-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  width: 100%;
}

.map-places-heading {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.map-places-label {
  font-weight: 600;
  color: var(--gp-text-primary);
}

.map-places-count {
  font-size: 0.76rem;
  color: var(--gp-text-secondary);
  background: color-mix(in srgb, var(--gp-primary) 10%, var(--gp-surface-white));
  border: 1px solid color-mix(in srgb, var(--gp-primary) 25%, var(--gp-border-light));
  border-radius: 999px;
  padding: 0.14rem 0.5rem;
  line-height: 1.2;
}

.map-places-controls {
  display: flex;
  gap: 0.15rem;
}

.map-places-rail-wrapper {
  overflow: hidden;
}

.map-places-rail {
  display: flex;
  gap: 0.75rem;
  overflow-x: auto;
  overflow-y: hidden;
  scroll-snap-type: x mandatory;
  padding-bottom: 0.2rem;
  scroll-behavior: smooth;
  -webkit-overflow-scrolling: touch;
}

.map-places-rail::-webkit-scrollbar {
  height: 8px;
}

.map-places-rail::-webkit-scrollbar-thumb {
  background: var(--gp-border-light);
  border-radius: 8px;
}

.map-places-rail::-webkit-scrollbar-track {
  background: transparent;
}

.map-place-item {
  display: flex;
  flex: 0 0 310px;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  padding: 0.68rem;
  cursor: pointer;
  transition: border-color 0.18s ease, background-color 0.18s ease;
  scroll-snap-align: center;
}

.map-place-item:hover {
  border-color: var(--gp-primary);
  background: var(--gp-surface-light);
}

.map-place-item.hovered {
  border-color: var(--gp-primary);
  background: var(--gp-surface-light);
}

.map-place-item.active {
  border-color: var(--gp-primary);
  background: color-mix(in srgb, var(--gp-primary) 8%, var(--gp-surface-white));
}

.map-place-thumb {
  width: 2.25rem;
  height: 2.25rem;
  min-width: 2.25rem;
  border-radius: 999px;
  background: color-mix(in srgb, var(--gp-primary) 15%, var(--gp-surface-white));
  color: var(--gp-primary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.map-place-main {
  flex: 1;
  min-width: 0;
}

.map-place-name {
  font-weight: 600;
  color: var(--gp-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.map-place-meta {
  font-size: 0.82rem;
  color: var(--gp-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.map-place-timeline {
  font-size: 0.76rem;
  color: var(--gp-text-secondary);
}

.map-place-side {
  display: flex;
  align-items: center;
  gap: 0.28rem;
}

.map-place-visits {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  line-height: 1.05;
  min-width: 52px;
}

.map-place-visits .value {
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--gp-text-primary);
}

.map-place-visits .label {
  font-size: 0.64rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--gp-text-secondary);
}

.map-place-open-btn {
  opacity: 0.72;
}

.map-place-item:hover .map-place-open-btn,
.map-place-item.active .map-place-open-btn {
  opacity: 1;
}

.loading-container {
  display: flex;
  justify-content: center;
  padding: var(--gp-spacing-xxl);
}

.empty-state {
  text-align: center;
  padding: var(--gp-spacing-xxl);
  color: var(--gp-text-secondary);
}

.empty-state.compact {
  padding: var(--gp-spacing-lg);
}

.empty-icon {
  font-size: 4rem;
  opacity: 0.5;
  margin-bottom: var(--gp-spacing-md);
}

.location-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: var(--gp-spacing-lg);
  padding: 0 var(--gp-spacing-lg);
}

.location-card {
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: center;
  padding: var(--gp-spacing-xl);
}

.location-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--gp-shadow-medium);
  border-color: var(--gp-primary);
}

.location-icon {
  font-size: 3rem;
  color: var(--gp-primary);
  margin-bottom: var(--gp-spacing-md);
}

.location-name {
  margin: 0 0 var(--gp-spacing-xs);
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.location-country {
  margin: 0 0 var(--gp-spacing-lg);
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
}

.location-stats {
  display: flex;
  justify-content: center;
  gap: var(--gp-spacing-lg);
  padding-top: var(--gp-spacing-md);
  border-top: 1px solid var(--gp-border-light);
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--gp-spacing-xs);
}

.stat-value {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--gp-primary);
}

.stat-label {
  font-size: 0.75rem;
  text-transform: uppercase;
  color: var(--gp-text-secondary);
  letter-spacing: 0.5px;
}

@media (max-width: 768px) {
  :deep(.gp-page-header-content) {
    flex-direction: column;
    align-items: stretch;
  }

  :deep(.gp-page-actions) {
    width: 100%;
  }

  .header-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .search-container {
    width: 100%;
    min-width: 0;
    max-width: 100%;
  }

  .analytics-tabs {
    width: 100%;
    gap: var(--gp-spacing-sm);
  }

  .analytics-tabs .p-button {
    flex: 1;
    min-width: 0;
  }

  .map-tab-content {
    padding: 0 var(--gp-spacing-sm);
  }

  .map-places-controls {
    display: none;
  }

  .map-place-item {
    flex: 0 0 min(84vw, 320px);
  }

  .location-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: var(--gp-spacing-sm);
  }

  .location-card {
    padding: var(--gp-spacing-sm);
  }

  .location-icon {
    font-size: 1.75rem;
    margin-bottom: var(--gp-spacing-xs);
  }

  .location-name {
    font-size: 0.95rem;
    margin: 0 0 var(--gp-spacing-xxs);
  }

  .location-country {
    font-size: 0.75rem;
    margin: 0 0 var(--gp-spacing-xs);
  }

  .location-stats {
    gap: var(--gp-spacing-sm);
    padding-top: var(--gp-spacing-xs);
  }

  .stat-value {
    font-size: 1rem;
  }

  .stat-label {
    font-size: 0.65rem;
  }
}
</style>
