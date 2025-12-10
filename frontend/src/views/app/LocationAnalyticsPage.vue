<template>
  <AppLayout variant="default">
    <PageContainer
      title="Location Analytics"
      subtitle="Explore your visits by city and country"
      variant="fullwidth"
    >
      <!-- Search and Tabs Row -->
      <div class="header-controls">
        <div class="search-container">
          <LocationSearchBar />
        </div>

        <div class="analytics-tabs">
          <Button
            :label="`Cities (${cities.length})`"
            icon="pi pi-building"
            :class="{ 'active-tab': activeTab === 'cities' }"
            @click="activeTab = 'cities'"
          />
          <Button
            :label="`Countries (${countries.length})`"
            icon="pi pi-globe"
            :class="{ 'active-tab': activeTab === 'countries' }"
            @click="activeTab = 'countries'"
          />
        </div>
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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import ProgressSpinner from 'primevue/progressspinner'

import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import LocationSearchBar from '@/components/search/LocationSearchBar.vue'

import { useLocationAnalyticsStore } from '@/stores/locationAnalytics'

const router = useRouter()
const toast = useToast()
const store = useLocationAnalyticsStore()

const { cities, countries, citiesLoading, countriesLoading } = storeToRefs(store)

const activeTab = ref('cities')

const navigateToCity = (cityName) => {
  router.push(`/app/location-analytics/city/${encodeURIComponent(cityName)}`)
}

const navigateToCountry = (countryName) => {
  router.push(`/app/location-analytics/country/${encodeURIComponent(countryName)}`)
}

onMounted(async () => {
  try {
    await Promise.all([
      store.fetchAllCities(),
      store.fetchAllCountries()
    ])
  } catch (error) {
    console.error('Failed to load location analytics:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load location data',
      life: 5000
    })
  }
})
</script>

<style scoped>
.header-controls {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-lg);
  margin-bottom: var(--gp-spacing-xl);
  padding: 0 var(--gp-spacing-lg);
}

.search-container {
  flex: 1;
  max-width: 400px;
}

.analytics-tabs {
  display: flex;
  gap: var(--gp-spacing-md);
  flex-shrink: 0;
}

.analytics-tabs .p-button {
  min-width: 140px;
}

.analytics-tabs .active-tab {
  background: var(--gp-primary);
  color: white;
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
  .header-controls {
    flex-direction: column;
    align-items: stretch;
  }

  .search-container {
    max-width: 100%;
  }

  .analytics-tabs {
    width: 100%;
  }

  .analytics-tabs .p-button {
    flex: 1;
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
