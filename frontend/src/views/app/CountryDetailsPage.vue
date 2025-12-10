<template>
  <AppLayout variant="default">
    <PageContainer
      :title="countryName"
      :subtitle="`Statistics and visit history for ${countryName}`"
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
        <!-- Country Header -->
        <BaseCard class="country-header-card">
          <div class="country-header">
            <div class="country-header-icon">
              <i class="pi pi-globe"></i>
            </div>
            <div class="country-header-content">
              <h1 class="country-title">{{ countryDetails.countryName }}</h1>
              <p class="country-meta">{{ countryDetails.cities.length }} cities visited</p>
            </div>
          </div>
        </BaseCard>

        <!-- Statistics Card -->
        <PlaceStatsCard
          v-if="countryDetails.statistics"
          :statistics="countryDetails.statistics"
        />

        <!-- Cities Breakdown -->
        <BaseCard v-if="countryDetails.cities && countryDetails.cities.length > 0" class="cities-card">
          <h3 class="section-title">Cities in {{ countryDetails.countryName }}</h3>
          <div class="cities-list">
            <div
              v-for="city in countryDetails.cities"
              :key="city.cityName"
              class="city-item"
              @click="navigateToCity(city.cityName)"
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
            </div>
          </div>
        </BaseCard>

        <!-- Top Places in Country -->
        <BaseCard v-if="countryDetails.topPlaces && countryDetails.topPlaces.length > 0" class="top-places-card">
          <h3 class="section-title">Top Places in {{ countryDetails.countryName }}</h3>
          <div class="top-places-list">
            <div
              v-for="place in countryDetails.topPlaces"
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

        <!-- Visits Table -->
        <PlaceVisitsTable
          :visits="countryVisits"
          :pagination="pagination"
          :loading="visitsLoading"
          :show-city="true"
          :show-location-name="true"
          :enable-city-navigation="true"
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
import PlaceVisitsTable from '@/components/place/PlaceVisitsTable.vue'

import { useLocationAnalyticsStore } from '@/stores/locationAnalytics'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const store = useLocationAnalyticsStore()

const { countryDetails, countryVisits, countryPagination, loading } = storeToRefs(store)

const error = ref(null)
const visitsLoading = ref(false)
const currentSortBy = ref('timestamp')
const currentSortDirection = ref('desc')

const countryName = computed(() => route.params.name)
const isLoading = computed(() => loading.value)
const pagination = computed(() => countryPagination.value)

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

  try {
    await store.fetchCountryDetails(countryName.value)
    await loadVisits(0, 50)
  } catch (err) {
    console.error('Error loading country data:', err)
    error.value = err.response?.data?.message || err.message || 'Failed to load country details'
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
    const url = `/api/location-analytics/country/${encodeURIComponent(countryName.value)}/visits/export?sortBy=${currentSortBy.value}&sortDirection=${currentSortDirection.value}`

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
    link.download = `country_${countryName.value}_visits_${new Date().toISOString().split('T')[0]}.csv`
    link.click()
    URL.revokeObjectURL(downloadUrl)

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
      detail: err.message || 'Failed to export visits',
      life: 5000
    })
  }
}

const navigateToPlace = (place) => {
  router.push(`/app/place-details/${place.type}/${place.id}`)
}

const navigateToCity = (cityName) => {
  router.push(`/app/location-analytics/city/${encodeURIComponent(cityName)}`)
}

const goToLocationAnalytics = () => {
  router.push('/app/location-analytics')
}

onMounted(async () => {
  store.clearCountryData()
  await loadCountryData()
})

watch(
  () => route.params.name,
  async (newName, oldName) => {
    if (newName !== oldName) {
      store.clearCountryData()
      await loadCountryData()
    }
  }
)
</script>

<style scoped>
.breadcrumb-nav {
  margin-bottom: var(--gp-spacing-lg);
  padding: 0 var(--gp-spacing-lg);
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

.country-header-card {
  margin-bottom: var(--gp-spacing-xl);
}

.country-header {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-lg);
}

.country-header-icon {
  font-size: 3rem;
  color: var(--gp-primary);
}

.country-title {
  margin: 0;
  font-size: 2rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.country-meta {
  margin: var(--gp-spacing-xs) 0 0;
  font-size: 1.125rem;
  color: var(--gp-text-secondary);
}

.cities-card,
.top-places-card {
  margin-bottom: var(--gp-spacing-xl);
}

.section-title {
  margin: 0 0 var(--gp-spacing-lg);
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--gp-text-primary);
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
  padding: var(--gp-spacing-md);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  cursor: pointer;
  transition: all 0.2s ease;
}

.city-item:hover,
.top-place-item:hover {
  border-color: var(--gp-primary);
  background: var(--gp-primary-50);
}

.city-info,
.place-info {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-md);
  flex: 1;
}

.city-icon,
.place-icon {
  font-size: 1.5rem;
  color: var(--gp-primary);
}

.city-name,
.place-name {
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: var(--gp-spacing-xs);
}

.city-stats,
.place-stats {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
}

@media (max-width: 768px) {
  .country-title {
    font-size: 1.5rem;
  }

  .country-header-icon {
    font-size: 2rem;
  }
}
</style>
