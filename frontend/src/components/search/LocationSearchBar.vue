<template>
  <div class="location-search">
    <AutoComplete
      v-model="searchQuery"
      :suggestions="filteredResults"
      placeholder="Search locations, pages, settings..."
      :loading="isSearching"
      :min-length="2"
      :delay="250"
      option-label="displayName"
      force-selection
      @complete="handleSearch"
      @item-select="handleSelect"
    >
      <template #option="{ option }">
        <div class="search-result-item">
          <i :class="getIconClass(option)" class="result-icon"></i>
          <div class="result-content">
            <div class="result-name">{{ option.displayName }}</div>
            <div class="result-meta">
              <span class="result-type">{{ option.groupLabel }}</span>
              <span v-if="option.metaLine" class="result-secondary">{{ option.metaLine }}</span>
            </div>
          </div>
        </div>
      </template>
    </AutoComplete>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import AutoComplete from 'primevue/autocomplete'
import { useLocationAnalyticsStore } from '@/stores/locationAnalytics'
import { usePeriodTagsStore } from '@/stores/periodTags'
import { useAuthStore } from '@/stores/auth'
import { useTimezone } from '@/composables/useTimezone'
import { buildPageIndex, buildSettingsIndex } from '@/constants/globalSearchRegistry'
import { searchAndRankItems } from '@/utils/globalSearchScoring'

const router = useRouter()
const store = useLocationAnalyticsStore()
const tagsStore = usePeriodTagsStore()
const authStore = useAuthStore()
const timezone = useTimezone()

const { searchResults, searchLoading } = storeToRefs(store)

const searchQuery = ref('')
const filteredResults = ref([])
const requestToken = ref(0)
const tagsLoaded = ref(false)

const isSearching = computed(() => searchLoading.value)

const pageItems = computed(() => {
  return buildPageIndex(router.getRoutes(), authStore.isAdmin).map((item) => ({
    id: `page:${item.id}`,
    resultType: 'page',
    displayName: item.title,
    metaLine: item.subtitle,
    groupLabel: 'Pages',
    icon: item.icon || 'pi pi-compass',
    to: item.to,
    tab: item.tab,
    setting: item.setting,
    keywords: item.keywords || []
  }))
})

const settingItems = computed(() => {
  return buildSettingsIndex(authStore.isAdmin).map((item) => ({
    id: `setting:${item.id}`,
    resultType: 'setting',
    displayName: item.title,
    metaLine: item.subtitle,
    groupLabel: 'Settings',
    icon: item.icon || 'pi pi-sliders-h',
    to: item.to,
    tab: item.tab,
    setting: item.setting,
    keywords: item.keywords || []
  }))
})

const ensureTagsLoaded = async () => {
  if (tagsLoaded.value) return
  await tagsStore.fetchPeriodTags()
  tagsLoaded.value = true
}

const formatDateForUrl = (dateString) => timezone.formatUrlDate(dateString)

const formatTagDate = (tag) => {
  const formatDate = (dateString) => timezone.formatDateDisplay(dateString)

  if (tag.endTime) {
    return `${formatDate(tag.startTime)} - ${formatDate(tag.endTime)}`
  }

  return `Since ${formatDate(tag.startTime)}`
}

const toLocationSuggestion = (result) => {
  let displayName = result.name || ''
  let metaLine = ''

  switch (result.type) {
    case 'tag':
      displayName = result.tagName
      metaLine = formatTagDate(result)
      break
    case 'place':
      metaLine = `${result.visitCount || 0} visits`
      if (result.country) metaLine = `${result.country} • ${metaLine}`
      break
    case 'city':
      metaLine = result.country ? `${result.country} • ${result.visitCount || 0} visits` : `${result.visitCount || 0} visits`
      break
    case 'country':
      metaLine = `${result.visitCount || 0} visits`
      break
    default:
      metaLine = ''
  }

  return {
    id: `location:${result.type}:${result.id || result.name || result.tagName}`,
    resultType: result.type,
    displayName,
    metaLine,
    groupLabel: 'Locations',
    icon: result.type === 'tag'
      ? 'pi pi-tag'
      : result.type === 'city'
        ? 'pi pi-building'
        : result.type === 'country'
          ? 'pi pi-globe'
          : result.category === 'favorite'
            ? 'pi pi-heart'
            : 'pi pi-map-marker',
    name: result.name,
    country: result.country,
    category: result.category,
    idValue: result.id,
    visitCount: Number(result.visitCount || 0),
    startTime: result.startTime,
    endTime: result.endTime,
    keywords: [result.country, result.category, result.type, result.tagName].filter(Boolean)
  }
}

const dedupeResults = (items) => {
  const seen = new Set()
  const unique = []

  for (const item of items) {
    const key = `${item.resultType}|${item.id}|${item.displayName}|${item.to || ''}|${item.setting || ''}`
    if (seen.has(key)) continue
    seen.add(key)
    unique.push(item)
  }

  return unique
}

const handleSearch = async (event) => {
  const query = event.query.trim()
  const token = ++requestToken.value

  if (query.length < 2) {
    filteredResults.value = []
    return
  }

  // Static results
  const pages = searchAndRankItems(query, pageItems.value, { minScore: 120 })
    .map((entry) => entry.item)
    .slice(0, 8)

  const settings = searchAndRankItems(query, settingItems.value, { minScore: 120 })
    .map((entry) => entry.item)
    .slice(0, 12)

  // Locations results
  let locations = []
  try {
    await store.searchLocations(query)
    await ensureTagsLoaded()

    if (token !== requestToken.value) return

    const apiLocationItems = (searchResults.value || []).map((result) => toLocationSuggestion(result))
    const tagItems = (tagsStore.periodTags || [])
      .filter((tag) => tag.tagName?.toLowerCase().includes(query.toLowerCase()))
      .map((tag) => toLocationSuggestion({ ...tag, type: 'tag' }))

    locations = searchAndRankItems(query, [...tagItems, ...apiLocationItems], { minScore: 90 })
      .sort((a, b) => {
        // Primary sort for locations: most visited first
        const visitsDiff = (b.item.visitCount || 0) - (a.item.visitCount || 0)
        if (visitsDiff !== 0) return visitsDiff

        // Tie-break with relevance score
        const scoreDiff = b.score - a.score
        if (scoreDiff !== 0) return scoreDiff

        return (a.item.displayName || '').localeCompare(b.item.displayName || '')
      })
      .map((entry) => entry.item)
      .slice(0, 12)
  } catch (error) {
    console.error('Unified search failed:', error)
  }

  if (token !== requestToken.value) return

  // Explicit order: Locations, Pages, Settings
  filteredResults.value = dedupeResults([
    ...locations,
    ...pages,
    ...settings
  ]).slice(0, 28)
}

const handleSelect = (event) => {
  const result = event.value
  if (!result) return

  switch (result.resultType) {
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
      router.push(`/app/place-details/${result.category}/${result.idValue}`)
      break
    case 'city':
      router.push(`/app/location-analytics/city/${encodeURIComponent(result.name)}`)
      break
    case 'country':
      router.push(`/app/location-analytics/country/${encodeURIComponent(result.name)}`)
      break
    case 'page':
      router.push(result.to)
      break
    case 'setting': {
      const query = {}
      if (result.tab) query.tab = result.tab
      if (result.setting) query.setting = result.setting
      router.push({ path: result.to, query })
      break
    }
    default:
      break
  }

  searchQuery.value = ''
  filteredResults.value = []
  store.clearSearchResults()
}

const getIconClass = (result) => {
  return result.icon || 'pi pi-search'
}
</script>

<style scoped>
.location-search {
  width: 100%;
  max-width: 680px;
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
  font-size: 1.2rem;
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
  font-weight: 600;
  color: var(--gp-primary);
}

.result-secondary::before {
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
