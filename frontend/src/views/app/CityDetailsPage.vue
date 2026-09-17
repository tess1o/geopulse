<template>
  <AppLayout variant="default">
    <PageContainer
      :loading="isLoading"
      max-width="xlarge"
    >
      <!-- Loading State -->
      <template v-if="isLoading && !cityDetails">
        <div class="loading-container">
          <ProgressSpinner />
          <p class="loading-text">Loading city details...</p>
        </div>
      </template>

      <!-- Error State -->
      <template v-else-if="error">
        <BaseCard>
          <div class="error-container">
            <i class="pi pi-exclamation-triangle error-icon"></i>
            <h3 class="error-title">Failed to Load City Details</h3>
            <p class="error-message">{{ error }}</p>
            <Button label="Try Again" icon="pi pi-refresh" @click="loadCityData" />
          </div>
        </BaseCard>
      </template>

      <!-- City Details Content -->
      <template v-else-if="cityDetails">
        <LocationDetailsHeader
          :title="cityDetails.cityName"
          subtitle="City insights and visit history"
          icon="pi pi-building"
          back-label="Back"
          @back="goToLocationAnalytics"
        >
          <template #metadata>
            <RouterLink
              v-if="cityDetails.country"
              :to="`/app/location-analytics/country/${encodeURIComponent(cityDetails.country)}`"
              class="detail-link"
            >
              {{ cityDetails.country }}
            </RouterLink>
            <span v-if="cityDetails.statistics">
              {{ cityDetails.statistics.totalVisits || 0 }} visits
            </span>
          </template>
        </LocationDetailsHeader>

        <!-- Statistics Card -->
        <PlaceStatsCard
          v-if="cityDetails.statistics"
          :statistics="cityDetails.statistics"
        />

        <div class="city-context-grid">
          <!-- Top Places in City -->
          <BaseCard
            v-if="cityDetails.topPlaces && cityDetails.topPlaces.length > 0"
            :title="`Top Places in ${cityDetails.cityName}`"
            class="top-places-card"
          >
            <div class="top-places-list">
              <RouterLink
                v-for="place in cityDetails.topPlaces"
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

          <!-- Map with city centroid -->
          <PlaceMap
            v-if="cityDetails.geometry && !isLoading"
            ref="placeMapRef"
            :key="`city-map-${cityName}-${cityDetails.cityName}`"
            :geometry="cityDetails.geometry"
            :location-name="cityDetails.cityName"
            :photos="cityPhotosForMap"
            :photo-marker-groups="cityMarkerGroupsForMap"
            @photo-click="handleMapPhotoClick"
          />
        </div>

        <ImmichLatestPhotosSection
          ref="cityPhotosSectionRef"
          :title="`Latest photos in ${cityDetails.cityName}`"
          :search-params="cityImmichSearchParams"
          empty-message="No Immich photos found for this city."
          @latest-photos-change="handleCityPhotosChange"
          @map-markers-change="handleCityMarkerGroupsChange"
          @show-on-map="handleCityPhotoShowOnMap"
        />

        <!-- Visits Table -->
        <PlaceVisitsTable
          :visits="cityVisits"
          :pagination="pagination"
          :loading="visitsLoading"
          :show-location-name="true"
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
import PlaceMap from '@/components/place/PlaceMap.vue'
import PlaceVisitsTable from '@/components/place/PlaceVisitsTable.vue'
import ImmichLatestPhotosSection from '@/components/location-analytics/ImmichLatestPhotosSection.vue'
import { useImmichPhotoMapBridge } from '@/composables/useImmichPhotoMapBridge'

import { useLocationAnalyticsStore } from '@/stores/locationAnalytics'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const store = useLocationAnalyticsStore()

const { cityDetails, cityVisits, cityPagination, loading } = storeToRefs(store)

const error = ref(null)
const visitsLoading = ref(false)
const currentSortBy = ref('timestamp')
const currentSortDirection = ref('desc')

const placeMapRef = ref(null)
const cityPhotosSectionRef = ref(null)
const {
  photosForMap: cityPhotosForMap,
  markerGroupsForMap: cityMarkerGroupsForMap,
  resetPhotosForMap: resetCityPhotosForMap,
  handlePhotosChange: handleCityPhotosChange,
  handleMarkerGroupsChange: handleCityMarkerGroupsChange,
  handleMapPhotoClick,
  handlePhotoShowOnMap: handleCityPhotoShowOnMap
} = useImmichPhotoMapBridge({
  mapRef: placeMapRef,
  photosSectionRef: cityPhotosSectionRef,
  focusZoom: 16
})

const cityName = computed(() => route.params.name)
const isLoading = computed(() => loading.value)
const pagination = computed(() => cityPagination.value)

const cityImmichSearchParams = computed(() => {
  const firstVisit = cityDetails.value?.statistics?.firstVisit
  const lastVisit = cityDetails.value?.statistics?.lastVisit
  const city = cityDetails.value?.cityName
  const geometry = cityDetails.value?.geometry

  if (!firstVisit || !lastVisit || !city) {
    return null
  }

  const params = {
    startDate: firstVisit,
    endDate: lastVisit,
    city,
    country: cityDetails.value?.country
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

const loadCityData = async () => {
  error.value = null
  resetCityPhotosForMap()

  try {
    await store.fetchCityDetails(cityName.value)
    await loadVisits(0, 50)
  } catch (err) {
    console.error('Error loading city data:', err)
    error.value = formatApiErrorDetail(err, 'Failed to load city details')
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.value,
      life: 5000
    })
  }
}

const loadVisits = async (page, pageSize, sortBy = currentSortBy.value, sortDirection = currentSortDirection.value) => {
  visitsLoading.value = true

  try {
    await store.fetchCityVisits(cityName.value, page, pageSize, sortBy, sortDirection)
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
    await store.exportVisits('city', cityName.value, currentSortBy.value, currentSortDirection.value)

    toast.add({
      severity: 'success',
      summary: 'Export Successful',
      detail: `Exported visits to ${cityName.value}`,
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

onMounted(async () => {
  store.clearCityData()
  await loadCityData()
})

watch(
  () => route.params.name,
  async (newName, oldName) => {
    if (newName !== oldName) {
      store.clearCityData()
      resetCityPhotosForMap()
      await loadCityData()
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

.detail-link {
  color: var(--gp-primary);
  font-weight: 600;
  text-underline-offset: .18em;
}

.detail-link:hover {
  color: var(--gp-primary-hover);
}

.city-context-grid {
  display: grid;
  grid-template-columns: minmax(18rem, 1fr) minmax(0, 2fr);
  gap: var(--gp-spacing-lg);
  margin-bottom: var(--gp-spacing-xl);
  align-items: stretch;
}

.city-context-grid > :only-child {
  grid-column: 1 / -1;
}

.top-places-list {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

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

.top-place-item:hover {
  border-color: var(--gp-primary);
  background: var(--gp-primary-50);
}

.place-info {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  flex: 1;
  min-width: 0;
}

.place-icon {
  font-size: 1.125rem;
  color: var(--gp-primary);
}

.place-details {
  min-width: 0;
}

.place-name {
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: var(--gp-spacing-xs);
  line-height: 1.25;
  overflow-wrap: anywhere;
}

.place-stats {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
}

.top-place-item:focus-visible {
  outline: 2px solid var(--gp-primary);
  outline-offset: 2px;
}

.top-place-item > .pi-chevron-right {
  flex-shrink: 0;
  margin-left: var(--gp-spacing-sm);
}

.p-dark .detail-link {
  color: var(--gp-primary-light);
}

.p-dark .detail-link:hover {
  color: var(--gp-primary);
}

@media (max-width: 1024px) {
  .city-context-grid {
    grid-template-columns: 1fr;
  }
}
</style>
