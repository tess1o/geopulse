import { describe, expect, it, vi, beforeEach } from 'vitest'

vi.hoisted(() => {
  Object.defineProperty(globalThis, 'localStorage', {
    value: {
      getItem: () => null,
      setItem: () => {},
      removeItem: () => {},
      clear: () => {}
    },
    configurable: true
  })
})

import { createPinia, setActivePinia } from 'pinia'
import { useTripsStore } from '@/stores/trips'
import {
  getTripPlanSuggestionCoordinates,
  isTripPlanLocalSearchSource,
  isTripPlanSavedFavoriteSource,
  normalizeTripPlanSearchResult,
  useTripPlanLocationSearch
} from '@/composables/useTripPlanLocationSearch'

describe('useTripPlanLocationSearch helpers', () => {
  it('classifies saved favorite and local search sources', () => {
    expect(isTripPlanSavedFavoriteSource('favorite-point')).toBe(true)
    expect(isTripPlanSavedFavoriteSource('favorite-area')).toBe(true)
    expect(isTripPlanSavedFavoriteSource('geocoding')).toBe(false)
    expect(isTripPlanLocalSearchSource('geocoding')).toBe(true)
    expect(isTripPlanLocalSearchSource('external-search')).toBe(false)
  })

  it('normalizes provider display data without repeating provider in metadata', () => {
    const result = normalizeTripPlanSearchResult({
      sourceType: 'external-search',
      title: '',
      subtitle: 'Kyiv • Ukraine • Photon',
      latitude: 50.4501,
      longitude: 30.5234,
      providerName: 'Photon'
    }, {
      fallbackLabel: 'Place'
    })

    expect(result.displayName).toBe('Place (50.45010, 30.52340)')
    expect(result.groupLabel).toBe('Provider: Photon')
    expect(result.metaLine).toBe('Kyiv • Ukraine')
  })

  it('extracts valid coordinates only', () => {
    expect(getTripPlanSuggestionCoordinates({ latitude: 1, longitude: 2 })).toEqual({
      latitude: 1,
      longitude: 2
    })
    expect(getTripPlanSuggestionCoordinates({ latitude: 'bad', longitude: 2 })).toBeNull()
  })
})

describe('useTripPlanLocationSearch', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('filters saved favorites when requested', async () => {
    const tripsStore = useTripsStore()
    tripsStore.searchPlanLocations = vi.fn().mockResolvedValue([
      {
        sourceType: 'favorite-point',
        title: 'Home',
        latitude: 1,
        longitude: 2
      },
      {
        sourceType: 'external-search',
        title: 'Library',
        latitude: 3,
        longitude: 4,
        providerName: 'Photon'
      }
    ])

    const search = useTripPlanLocationSearch({ excludeSavedFavorites: true })
    await search.search('lib')

    expect(search.suggestions.value).toHaveLength(1)
    expect(search.suggestions.value[0].displayName).toBe('Library')
  })

  it('ignores stale async responses', async () => {
    const tripsStore = useTripsStore()
    let resolveFirst
    tripsStore.searchPlanLocations = vi.fn()
      .mockReturnValueOnce(new Promise((resolve) => {
        resolveFirst = resolve
      }))
      .mockResolvedValueOnce([
        {
          sourceType: 'external-search',
          title: 'Second',
          latitude: 3,
          longitude: 4
        }
      ])

    const search = useTripPlanLocationSearch()
    const firstSearch = search.search('first')
    await search.search('second')
    resolveFirst([
      {
        sourceType: 'external-search',
        title: 'First',
        latitude: 1,
        longitude: 2
      }
    ])
    await firstSearch

    expect(search.suggestions.value).toHaveLength(1)
    expect(search.suggestions.value[0].displayName).toBe('Second')
  })
})
