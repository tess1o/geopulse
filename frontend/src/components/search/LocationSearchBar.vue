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
            <div class="result-name">{{ option.type === 'tag' ? option.tagName : option.name }}</div>
            <div class="result-meta">
              <span class="result-type">{{ formatType(option.type) }}</span>
              <span v-if="option.type !== 'tag' && option.country" class="result-country">{{ option.country }}</span>
              <span v-if="option.type === 'tag'" class="result-visits">{{ formatTagDate(option) }}</span>
              <span v-else class="result-visits">{{ option.visitCount }} visits</span>
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
import { usePeriodTagsStore } from '@/stores/periodTags'

const router = useRouter()
const store = useLocationAnalyticsStore()
const tagsStore = usePeriodTagsStore()

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
    // Search locations
    await store.searchLocations(query)
    const locationResults = searchResults.value.map(result => ({
      ...result,
      displayName: formatDisplayName(result)
    }))

    // Search tags
    await tagsStore.fetchPeriodTags()
    const tagResults = tagsStore.periodTags
      .filter(tag => tag.tagName.toLowerCase().includes(query.toLowerCase()))
      .map(tag => ({
        ...tag,
        type: 'tag',
        displayName: tag.tagName
      }))

    // Combine results with tags first
    filteredResults.value = [...tagResults, ...locationResults]
  } catch (error) {
    console.error('Search failed:', error)
    filteredResults.value = []
  }
}

const formatDateForUrl = (dateString) => {
  const date = new Date(dateString)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const year = date.getFullYear()
  return `${month}/${day}/${year}`
}

const handleSelect = (event) => {
  const result = event.value

  if (!result) return

  // Navigate based on result type
  switch (result.type) {
    case 'tag': {
      const startDate = formatDateForUrl(result.startTime)
      const endDate = result.endTime ? formatDateForUrl(result.endTime) : formatDateForUrl(new Date())
      router.push({
        path: '/app/timeline',
        query: {
          start: startDate,
          end: endDate
        }
      })
      break
    }
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
    case 'tag':
      return 'pi pi-tag'
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
  if (type === 'tag') return 'Period Tag'
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

const formatTagDate = (tag) => {
  const formatDate = (dateString) => {
    const date = new Date(dateString)
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
  }

  if (tag.endTime) {
    return `${formatDate(tag.startTime)} - ${formatDate(tag.endTime)}`
  }
  return `Since ${formatDate(tag.startTime)}`
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
