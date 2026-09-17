<template>
  <AppLayout variant="default">
    <PageContainer
      :loading="isLoading"
      max-width="xlarge"
    >
      <!-- Loading State -->
      <template v-if="isLoading && !countryDetails">
        <div class="loading-container">
          <ProgressSpinner />
          <p class="loading-text">Loading country details...</p>
        </div>
      </template>

      <!-- Error State -->
      <template v-else-if="error">
        <BaseCard>
          <div class="error-container">
            <i class="pi pi-exclamation-triangle error-icon"></i>
            <h3 class="error-title">Failed to Load Country Details</h3>
            <p class="error-message">{{ error }}</p>
            <Button label="Try Again" icon="pi pi-refresh" @click="loadCountryData" />
          </div>
        </BaseCard>
      </template>

      <!-- Country Details Content -->
      <template v-else-if="countryDetails">
        <LocationDetailsHeader
          :title="countryDetails.countryName"
          subtitle="Country insights and visit history"
          icon="pi pi-globe"
          back-label="Back"
          @back="goToLocationAnalytics"
        >
          <template #metadata>
            <span>{{ countryDetails.cities.length }} cities visited</span>
            <span v-if="countryDetails.statistics">
              {{ countryDetails.statistics.totalVisits || 0 }} visits
            </span>
          </template>
        </LocationDetailsHeader>

        <!-- Statistics Card -->
        <PlaceStatsCard
          v-if="countryDetails.statistics"
          :statistics="countryDetails.statistics"
        />

        <div
          v-if="countryDetails.cities?.length || countryDetails.topPlaces?.length"
          class="country-lists-grid"
        >
          <!-- Cities Breakdown -->
          <BaseCard
            v-if="countryDetails.cities?.length"
            :title="`Cities in ${countryDetails.countryName}`"
            class="cities-card"
          >
            <div id="country-cities-list" class="cities-list">
              <RouterLink
                v-for="city in visibleCities"
                :key="city.cityName"
                :to="`/app/location-analytics/city/${encodeURIComponent(city.cityName)}`"
                class="city-item"
              >
                <div class="city-info">
                  <i class="pi pi-building city-icon"></i>
                  <div class="city-details">
                    <div class="city-name">{{ city.cityName }}</div>
                    <div class="city-stats">
                      {{ city.visitCount }} visits • {{ formatDuration(city.totalDuration) }} • {{ city.uniquePlaces }} places
                    </div>
                  </div>
                </div>
                <i class="pi pi-chevron-right"></i>
              </RouterLink>
            </div>
            <template v-if="countryDetails.cities.length > 5" #footer>
              <Button
                :label="showAllCities ? 'Show top 5' : `Show all ${countryDetails.cities.length} cities`"
                :icon="showAllCities ? 'pi pi-chevron-up' : 'pi pi-chevron-down'"
                :aria-expanded="showAllCities"
                aria-controls="country-cities-list"
                size="small"
                text
                @click="showAllCities = !showAllCities"
              />
            </template>
          </BaseCard>

          <!-- Top Places in Country -->
          <BaseCard
            v-if="countryDetails.topPlaces?.length"
            :title="`Top Places in ${countryDetails.countryName}`"
            class="top-places-card"
          >
            <div class="top-places-list">
              <RouterLink
                v-for="place in countryDetails.topPlaces"
                :key="`${place.type}-${place.id}`"
                :to="`/app/place-details/${place.type}/${place.id}`"
                class="top-place-item"
              >
                <div class="place-info">
                  <i :class="place.type === 'favorite' ? 'pi pi-heart' : 'pi pi-map-marker'" class="place-icon"></i>
                  <div class="place-details">
                    <div class="place-name">{{ place.name }}</div>
                    <div class="place-stats">
                      {{ place.visitCount }} visits • {{ formatDuration(place.totalDuration) }}
                    </div>
                  </div>
                </div>
                <i class="pi pi-chevron-right"></i>
              </RouterLink>
            </div>
          </BaseCard>
        </div>

        <ImmichPhotosMapCard
          v-if="hasPhotoMap"
          ref="countryPhotosMapRef"
          :key="`country-photos-map-${countryName}`"
          :title="`Photo locations in ${countryDetails.countryName}`"
          :photos="countryPhotosForMap"
          :photo-marker-groups="countryMarkerGroupsForMap"
          @photo-click="handleCountryMapPhotoClick"
        />

        <CountryCitiesMap
          v-else-if="showCityFallbackMap"
          :cities="countryDetails.cities"
          :country-name="countryDetails.countryName"
          @city-select="handleCitySelect"
        />

        <ImmichLatestPhotosSection
          ref="countryPhotosSectionRef"
          :title="`Latest photos in ${countryDetails.countryName}`"
          :search-params="countryImmichSearchParams"
          empty-message="No Immich photos found for this country."
          :show-on-map-enabled="true"
          @latest-photos-change="handleCountryPhotosChange"
          @map-markers-change="handleCountryMarkerGroupsChange"
          @show-on-map="handleCountryPhotoShowOnMap"
        />

        <!-- Visits Table -->
        <PlaceVisitsTable
          :visits="countryVisits"
          :pagination="pagination"
          :loading="visitsLoading"
          :show-city="true"
          :show-location-name="true"
          :enable-city-navigation="true"
          :show-end-time="false"
          :enable-timeline-navigation="true"
          @page-change="handlePageChange"
          @sort-change="handleSortChange"
          @export="handleExportVisits"
        />
      </template>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import ProgressSpinner from 'primevue/progressspinner'

import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import LocationDetailsHeader from '@/components/location-analytics/LocationDetailsHeader.vue'
import PlaceStatsCard from '@/components/place/PlaceStatsCard.vue'
import PlaceVisitsTable from '@/components/place/PlaceVisitsTable.vue'
import ImmichLatestPhotosSection from '@/components/location-analytics/ImmichLatestPhotosSection.vue'
import ImmichPhotosMapCard from '@/components/location-analytics/ImmichPhotosMapCard.vue'
import CountryCitiesMap from '@/components/location-analytics/CountryCitiesMap.vue'
import { useImmichPhotoMapBridge } from '@/composables/useImmichPhotoMapBridge'

import { useLocationAnalyticsStore } from '@/stores/locationAnalytics'
import { useImmichStore } from '@/stores/immich'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const store = useLocationAnalyticsStore()
const immichStore = useImmichStore()

const { countryDetails, countryVisits, countryPagination, loading } = storeToRefs(store)

const error = ref(null)
const visitsLoading = ref(false)
const showAllCities = ref(false)
const immichAvailabilityResolved = ref(false)
const currentSortBy = ref('timestamp')
const currentSortDirection = ref('desc')
const countryPhotosSectionRef = ref(null)
const countryPhotosMapRef = ref(null)
const {
  photosForMap: countryPhotosForMap,
  markerGroupsForMap: countryMarkerGroupsForMap,
  resetPhotosForMap: resetCountryPhotosForMap,
  handlePhotosChange: handleCountryPhotosChange,
  handleMarkerGroupsChange: handleCountryMarkerGroupsChange,
  handleMapPhotoClick: handleCountryMapPhotoClick,
  handlePhotoShowOnMap: handleCountryPhotoShowOnMap
} = useImmichPhotoMapBridge({
  mapRef: countryPhotosMapRef,
  photosSectionRef: countryPhotosSectionRef,
  focusZoom: 12
})

const countryName = computed(() => route.params.name)
const isLoading = computed(() => loading.value)
const pagination = computed(() => countryPagination.value)
const visibleCities = computed(() => {
  const cities = countryDetails.value?.cities || []
  return showAllCities.value ? cities : cities.slice(0, 5)
})
const hasPhotoMap = computed(() => countryPhotosForMap.value.length > 0 || countryMarkerGroupsForMap.value.length > 0)
const showCityFallbackMap = computed(() =>
  immichAvailabilityResolved.value &&
  !immichStore.isConfigured &&
  (countryDetails.value?.cities || []).some((city) => Number.isFinite(city?.latitude) && Number.isFinite(city?.longitude))
)
const countryImmichSearchParams = computed(() => {
  const firstVisit = countryDetails.value?.statistics?.firstVisit
  const lastVisit = countryDetails.value?.statistics?.lastVisit
  const country = countryDetails.value?.countryName
  const geometry = countryDetails.value?.geometry

  if (!firstVisit || !lastVisit || !country) {
    return null
  }

  const params = {
    startDate: firstVisit,
    endDate: lastVisit,
    country
  }

  if (
    geometry?.type === 'point' &&
    typeof geometry?.latitude === 'number' &&
    Number.isFinite(geometry.latitude) &&
    typeof geometry?.longitude === 'number' &&
    Number.isFinite(geometry.longitude)
  ) {
    params.latitude = geometry.latitude
    params.longitude = geometry.longitude
  }

  return params
})

const formatDuration = (seconds) => {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  if (hours > 0) {
    return `${hours}h ${minutes}m`
  }
  return `${minutes}m`
}

const loadCountryData = async () => {
  error.value = null
  showAllCities.value = false
  resetCountryPhotosForMap()

  try {
    await store.fetchCountryDetails(countryName.value)
    await loadVisits(0, 50)
  } catch (err) {
    console.error('Error loading country data:', err)
    error.value = formatApiErrorDetail(err, 'Failed to load country details')
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.value,
      life: 5000
    })
  }
}

const resolveImmichAvailability = async () => {
  try {
    await immichStore.fetchConfig()
  } catch {
    // An unavailable configuration uses the city map fallback.
  } finally {
    immichAvailabilityResolved.value = true
  }
}

const loadVisits = async (page, pageSize, sortBy = currentSortBy.value, sortDirection = currentSortDirection.value) => {
  visitsLoading.value = true

  try {
    await store.fetchCountryVisits(countryName.value, page, pageSize, sortBy, sortDirection)
  } catch (err) {
    console.error('Error loading visits:', err)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load visit history',
      life: 3000
    })
  } finally {
    visitsLoading.value = false
  }
}

const handlePageChange = async ({ page, pageSize }) => {
  await loadVisits(page, pageSize)
}

const handleSortChange = async ({ sortBy, sortDirection }) => {
  currentSortBy.value = sortBy
  currentSortDirection.value = sortDirection
  await loadVisits(pagination.value.currentPage, pagination.value.pageSize, sortBy, sortDirection)
}

const handleExportVisits = async () => {
  try {
    await store.exportVisits('country', countryName.value, currentSortBy.value, currentSortDirection.value)

    toast.add({
      severity: 'success',
      summary: 'Export Successful',
      detail: `Exported visits to ${countryName.value}`,
      life: 5000
    })
  } catch (err) {
    console.error('Error exporting visits:', err)
    toast.add({
      severity: 'error',
      summary: 'Export Failed',
      detail: formatApiErrorDetail(err, 'Failed to export visits'),
      life: 5000
    })
  }
}

const goToLocationAnalytics = () => {
  router.push('/app/location-analytics')
}

const handleCitySelect = (city) => {
  if (city?.cityName) {
    router.push(`/app/location-analytics/city/${encodeURIComponent(city.cityName)}`)
  }
}

onMounted(async () => {
  store.clearCountryData()
  await Promise.all([loadCountryData(), resolveImmichAvailability()])
})

watch(
  () => route.params.name,
  async (newName, oldName) => {
    if (newName !== oldName) {
      store.clearCountryData()
      resetCountryPhotosForMap()
      await loadCountryData()
    }
  }
)
</script>

<style scoped>
.loading-container,
.error-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--gp-spacing-xxl);
  gap: var(--gp-spacing-md);
  text-align: center;
}

.error-icon {
  font-size: 4rem;
  color: var(--gp-error);
  opacity: 0.7;
}

.country-lists-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
  gap: var(--gp-spacing-lg);
  margin-bottom: var(--gp-spacing-xl);
}

.country-lists-grid > :only-child {
  grid-column: 1 / -1;
}

.cities-card,
.top-places-card {
  width: 100%;
}

.cities-list,
.top-places-list {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.city-item,
.top-place-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--gp-spacing-sm) var(--gp-spacing-md);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  color: inherit;
  text-decoration: none;
  transition: all 0.2s ease;
}

.city-item:hover,
.top-place-item:hover {
  border-color: var(--gp-primary);
  background: var(--gp-primary-50);
}

.city-item:focus-visible,
.top-place-item:focus-visible {
  outline: 2px solid var(--gp-primary);
  outline-offset: 2px;
}

.city-info,
.place-info {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  flex: 1;
  min-width: 0;
}

.city-icon,
.place-icon {
  font-size: 1.125rem;
  color: var(--gp-primary);
}

.city-details,
.place-details {
  min-width: 0;
}

.city-name,
.place-name {
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: var(--gp-spacing-xs);
  line-height: 1.25;
  overflow-wrap: anywhere;
}

.city-stats,
.place-stats {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
}

.city-item > .pi-chevron-right,
.top-place-item > .pi-chevron-right {
  flex-shrink: 0;
  margin-left: var(--gp-spacing-sm);
}

@media (max-width: 1024px) {
  .country-lists-grid {
    grid-template-columns: 1fr;
  }
}

</style>
