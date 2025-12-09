<template>
  <div class="location-search">
    <AutoComplete
      v-model="searchQuery"
      :suggestions="filteredResults"
      placeholder="Search places, cities, or countries..."
      :loading="isSearching"
      :min-length="2"
      :delay="300"
      option-label="displayName"
      force-selection
      @complete="handleSearch"
      @item-select="handleSelect"
    >
      <template #option="{ option }">
        <div class="search-result-item">
          <i :class="getIconClass(option)" class="result-icon"></i>
          <div class="result-content">
            <div class="result-name">{{ option.name }}</div>
            <div class="result-meta">
              <span class="result-type">{{ formatType(option.type) }}</span>
              <span v-if="option.country" class="result-country">{{ option.country }}</span>
              <span class="result-visits">{{ option.visitCount }} visits</span>
            </div>
          </div>
        </div>
      </template>
    </AutoComplete>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import AutoComplete from 'primevue/autocomplete'
import { useLocationAnalyticsStore } from '@/stores/locationAnalytics'

const router = useRouter()
const store = useLocationAnalyticsStore()

const { searchResults, searchLoading } = storeToRefs(store)

const searchQuery = ref('')
const filteredResults = ref([])

const isSearching = computed(() => searchLoading.value)

const handleSearch = async (event) => {
  const query = event.query.trim()

  if (query.length < 2) {
    filteredResults.value = []
    return
  }

  try {
    await store.searchLocations(query)
    filteredResults.value = searchResults.value.map(result => ({
      ...result,
      displayName: formatDisplayName(result)
    }))
  } catch (error) {
    console.error('Search failed:', error)
    filteredResults.value = []
  }
}

const handleSelect = (event) => {
  const result = event.value

  if (!result) return

  // Navigate based on result type
  switch (result.type) {
    case 'place':
      router.push(`/app/place-details/${result.category}/${result.id}`)
      break
    case 'city':
      router.push(`/app/location-analytics/city/${encodeURIComponent(result.name)}`)
      break
    case 'country':
      router.push(`/app/location-analytics/country/${encodeURIComponent(result.name)}`)
      break
  }

  // Clear search after navigation
  searchQuery.value = ''
  filteredResults.value = []
  store.clearSearchResults()
}

const getIconClass = (result) => {
  switch (result.type) {
    case 'place':
      return result.category === 'favorite' ? 'pi pi-heart' : 'pi pi-map-marker'
    case 'city':
      return 'pi pi-building'
    case 'country':
      return 'pi pi-globe'
    default:
      return 'pi pi-map-marker'
  }
}

const formatType = (type) => {
  if (type === 'place') return 'Place'
  if (type === 'city') return 'City'
  if (type === 'country') return 'Country'
  return type
}

const formatDisplayName = (result) => {
  if (result.type === 'city') {
    return `${result.name}, ${result.country}`
  }
  return result.name
}
</script>

<style scoped>
.location-search {
  width: 100%;
  max-width: 600px;
  margin: 0 auto;
}

.location-search :deep(.p-autocomplete) {
  width: 100%;
}

.location-search :deep(.p-autocomplete-input) {
  width: 100%;
  padding: 0.75rem 1rem;
  font-size: 1rem;
}

.search-result-item {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-md);
  padding: var(--gp-spacing-sm) 0;
}

.result-icon {
  font-size: 1.5rem;
  color: var(--gp-primary);
  min-width: 24px;
}

.result-content {
  flex: 1;
}

.result-name {
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: var(--gp-spacing-xs);
}

.result-meta {
  display: flex;
  gap: var(--gp-spacing-sm);
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
}

.result-type {
  font-weight: 500;
  color: var(--gp-primary);
}

.result-country::before {
  content: '•';
  margin-right: var(--gp-spacing-sm);
}

.result-visits::before {
  content: '•';
  margin-right: var(--gp-spacing-sm);
}

@media (max-width: 768px) {
  .location-search {
    max-width: 100%;
  }

  .result-meta {
    flex-wrap: wrap;
  }
}
</style>
