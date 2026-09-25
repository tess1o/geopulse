import { ref } from 'vue'
import { useTripsStore } from '@/stores/trips'

const SAVED_FAVORITE_SOURCE_TYPES = new Set(['favorite-point', 'favorite-area'])
const LOCAL_SOURCE_TYPES = new Set([...SAVED_FAVORITE_SOURCE_TYPES, 'geocoding'])

export const isTripPlanSavedFavoriteSource = (sourceType) => SAVED_FAVORITE_SOURCE_TYPES.has(sourceType)

export const isTripPlanLocalSearchSource = (sourceType) => LOCAL_SOURCE_TYPES.has(sourceType)

export const getTripPlanSuggestionCoordinates = (suggestion) => {
  const latitude = Number(suggestion?.latitude)
  const longitude = Number(suggestion?.longitude)

  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return null
  }

  return { latitude, longitude }
}

export const buildTripPlanProviderMetaLine = (subtitle, providerName) => {
  const safeSubtitle = subtitle?.trim()
  if (!safeSubtitle) {
    return null
  }

  const safeProviderName = providerName?.trim()
  if (!safeProviderName) {
    return safeSubtitle
  }

  const providerLower = safeProviderName.toLowerCase()
  const parts = safeSubtitle
    .split('•')
    .map((part) => part.trim())
    .filter((part) => part && part.toLowerCase() !== providerLower)

  return parts.length > 0 ? parts.join(' • ') : null
}

export const buildTripPlanLocationMeta = (suggestion, options = {}) => {
  const sourceType = suggestion?.sourceType || ''
  const savedFavoriteGroupLabel = options.savedFavoriteGroupLabel || 'Saved place'
  const geocodingGroupLabel = options.geocodingGroupLabel || savedFavoriteGroupLabel

  if (isTripPlanSavedFavoriteSource(sourceType)) {
    return {
      groupLabel: savedFavoriteGroupLabel,
      metaLine: suggestion?.subtitle?.trim() || null
    }
  }

  if (sourceType === 'geocoding') {
    return {
      groupLabel: geocodingGroupLabel,
      metaLine: suggestion?.subtitle?.trim() || null
    }
  }

  const providerName = suggestion?.providerName?.trim() || ''
  const providerPrefix = options.providerGroupPrefix ?? 'Provider: '
  return {
    groupLabel: providerName ? `${providerPrefix}${providerName}` : 'Provider',
    metaLine: buildTripPlanProviderMetaLine(suggestion?.subtitle, providerName)
  }
}

export const normalizeTripPlanSearchResult = (result, options = {}) => {
  const fallbackLabel = options.fallbackLabel || 'Planned place'
  const title = result?.title?.trim()
  const coordinates = getTripPlanSuggestionCoordinates(result)
  const displayName = title || (coordinates
    ? `${fallbackLabel} (${coordinates.latitude.toFixed(5)}, ${coordinates.longitude.toFixed(5)})`
    : fallbackLabel)
  const { groupLabel, metaLine } = buildTripPlanLocationMeta(result, options)

  return {
    ...result,
    displayName,
    groupLabel,
    metaLine
  }
}

export const useTripPlanLocationSearch = (options = {}) => {
  const tripsStore = useTripsStore()
  const query = ref('')
  const suggestions = ref([])
  const isLoading = ref(false)
  const error = ref('')
  const requestToken = ref(0)
  // Outcome of the external provider leg: 'OK' | 'DISABLED' | 'FAILED'
  const externalStatus = ref('OK')

  const reset = () => {
    query.value = ''
    suggestions.value = []
    isLoading.value = false
    error.value = ''
    externalStatus.value = 'OK'
    requestToken.value += 1
  }

  const search = async (eventOrQuery) => {
    const searchQuery = typeof eventOrQuery === 'string'
      ? eventOrQuery.trim()
      : (eventOrQuery?.query || '').trim()
    error.value = ''

    if (searchQuery.length < 2) {
      suggestions.value = []
      isLoading.value = false
      requestToken.value += 1
      return
    }

    const currentRequestToken = ++requestToken.value
    isLoading.value = true

    try {
      const bias = options.getBias?.()
      const response = await tripsStore.searchPlanLocations(searchQuery, {
        lat: bias?.lat,
        lon: bias?.lon,
        limit: options.limit || 12
      })

      if (currentRequestToken !== requestToken.value) {
        return
      }

      const results = Array.isArray(response?.results)
        ? response.results
        : (Array.isArray(response) ? response : [])

      suggestions.value = results
        .filter((result) => !(options.excludeSavedFavorites && isTripPlanSavedFavoriteSource(result?.sourceType)))
        .map((result) => normalizeTripPlanSearchResult(result, options))

      externalStatus.value = response?.externalStatus || 'OK'

      // An unavailable provider used to look identical to "nothing matched". Say which
      // it is, but only when there is nothing else to show — local matches still win.
      if (suggestions.value.length === 0 && externalStatus.value !== 'OK' && response?.externalMessage) {
        error.value = response.externalMessage
      }
    } catch (searchError) {
      if (currentRequestToken !== requestToken.value) {
        return
      }
      suggestions.value = []
      error.value = searchError.response?.data?.message || searchError.userMessage || searchError.message || 'Failed to search places.'
    } finally {
      if (currentRequestToken === requestToken.value) {
        isLoading.value = false
      }
    }
  }

  return {
    query,
    suggestions,
    isLoading,
    error,
    externalStatus,
    search,
    reset
  }
}
