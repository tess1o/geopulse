<template>
  <AppLayout variant="default">
    <PageContainer
      :title="pageTitle"
      :subtitle="`Statistics and visit history for ${cityName}`"
      :loading="isLoading"
      variant="fullwidth"
    >
      <!-- Breadcrumb -->
      <div class="breadcrumb-nav">
        <Button
          label="Back to Location Analytics"
          icon="pi pi-arrow-left"
          class="p-button-text"
          @click="goToLocationAnalytics"
        />
      </div>

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
        <!-- City Header -->
        <BaseCard class="city-header-card">
          <div class="city-header">
            <div class="city-header-icon">
              <i class="pi pi-building"></i>
            </div>
            <div class="city-header-content">
              <h1 class="city-title">{{ cityDetails.cityName }}</h1>
              <p class="city-country">
                <span
                  class="country-link"
                  @click="navigateToCountry(cityDetails.country)"
                >
                  {{ cityDetails.country }}
                </span>
              </p>
            </div>
          </div>
        </BaseCard>

        <!-- Statistics Card -->
        <PlaceStatsCard
          v-if="cityDetails.statistics"
          :statistics="cityDetails.statistics"
        />

        <!-- Top Places in City -->
        <BaseCard v-if="cityDetails.topPlaces && cityDetails.topPlaces.length > 0" class="top-places-card">
          <h3 class="section-title">Top Places in {{ cityDetails.cityName }}</h3>
          <div class="top-places-list">
            <div
              v-for="place in cityDetails.topPlaces"
              :key="`${place.type}-${place.id}`"
              class="top-place-item"
              @click="navigateToPlace(place)"
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
            </div>
          </div>
        </BaseCard>

        <!-- Map with city centroid -->
        <PlaceMap
          v-if="cityDetails && cityDetails.geometry && !isLoading"
          :key="`city-map-${cityName}-${cityDetails.cityName}`"
          :geometry="cityDetails.geometry"
          :location-name="cityDetails.cityName"
        />

        <!-- Visits Table -->
        <PlaceVisitsTable
          :visits="cityVisits"
          :pagination="pagination"
          :loading="visitsLoading"
          :show-location-name="true"
          :show-end-time="false"
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
import PlaceStatsCard from '@/components/place/PlaceStatsCard.vue'
import PlaceMap from '@/components/place/PlaceMap.vue'
import PlaceVisitsTable from '@/components/place/PlaceVisitsTable.vue'

import { useLocationAnalyticsStore } from '@/stores/locationAnalytics'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const store = useLocationAnalyticsStore()

const { cityDetails, cityVisits, cityPagination, loading } = storeToRefs(store)

const error = ref(null)
const visitsLoading = ref(false)
const currentSortBy = ref('timestamp')
const currentSortDirection = ref('desc')

const cityName = computed(() => route.params.name)
const pageTitle = computed(() => {
  return cityDetails.value
    ? `${cityDetails.value.cityName}, ${cityDetails.value.country}`
    : 'City Details'
})
const isLoading = computed(() => loading.value)
const pagination = computed(() => cityPagination.value)

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

  try {
    await store.fetchCityDetails(cityName.value)
    await loadVisits(0, 50)
  } catch (err) {
    console.error('Error loading city data:', err)
    error.value = err.response?.data?.message || err.message || 'Failed to load city details'
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
    const url = `/api/location-analytics/city/${encodeURIComponent(cityName.value)}/visits/export?sortBy=${currentSortBy.value}&sortDirection=${currentSortDirection.value}`

    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('authToken')}` || ''
      }
    })

    if (!response.ok) {
      throw new Error(`Export failed with status ${response.status}`)
    }

    const blob = await response.blob()
    const downloadUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = `city_${cityName.value}_visits_${new Date().toISOString().split('T')[0]}.csv`
    link.click()
    URL.revokeObjectURL(downloadUrl)

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
      detail: err.message || 'Failed to export visits',
      life: 5000
    })
  }
}

const navigateToPlace = (place) => {
  router.push(`/app/place-details/${place.type}/${place.id}`)
}

const navigateToCountry = (countryName) => {
  if (countryName) {
    router.push(`/app/location-analytics/country/${encodeURIComponent(countryName)}`)
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
      await loadCityData()
    }
  }
)
</script>

<style scoped>
.breadcrumb-nav {
  margin-bottom: var(--gp-spacing-lg);
  padding: 0 var(--gp-spacing-lg);
  padding-top: env(safe-area-inset-top);
}

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

.city-header-card {
  margin-bottom: var(--gp-spacing-xl);
}

.city-header {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-lg);
}

.city-header-icon {
  font-size: 3rem;
  color: var(--gp-primary);
}

.city-title {
  margin: 0;
  font-size: 2rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.city-country {
  margin: var(--gp-spacing-xs) 0 0;
  font-size: 1.125rem;
  color: var(--gp-text-secondary);
}

.country-link {
  color: var(--gp-primary);
  cursor: pointer;
  text-decoration: underline;
  font-weight: 500;
  transition: color 0.2s ease;
}

.country-link:hover {
  color: var(--gp-primary-hover);
}

.top-places-card {
  margin-bottom: var(--gp-spacing-xl);
}

.section-title {
  margin: 0 0 var(--gp-spacing-lg);
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--gp-text-primary);
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
  padding: var(--gp-spacing-md);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  cursor: pointer;
  transition: all 0.2s ease;
}

.top-place-item:hover {
  border-color: var(--gp-primary);
  background: var(--gp-primary-50);
}

.place-info {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-md);
  flex: 1;
}

.place-icon {
  font-size: 1.5rem;
  color: var(--gp-primary);
}

.place-name {
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: var(--gp-spacing-xs);
}

.place-stats {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
}

.p-dark .country-link {
  color: var(--gp-primary-light);
}

.p-dark .country-link:hover {
  color: var(--gp-primary);
}

@media (max-width: 768px) {
  .city-title {
    font-size: 1.5rem;
  }

  .city-header-icon {
    font-size: 2rem;
  }
}
</style>
