import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import { useJourneyInsightsStore } from '@/stores/journeyInsights'

vi.hoisted(() => {
  const storage = new Map()
  const localStorageShim = {
    getItem: (key) => storage.get(key) || null,
    setItem: (key, value) => storage.set(key, String(value)),
    removeItem: (key) => storage.delete(key),
    clear: () => storage.clear()
  }
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: localStorageShim })
  Object.defineProperty(window, 'localStorage', { configurable: true, value: localStorageShim })
})

vi.mock('@/composables/useErrorHandler', () => ({
  useErrorHandler: () => ({ handleErrorWithRetry: vi.fn() })
}))
vi.mock('@/components/ui/layout/AppLayout.vue', () => ({ default: { template: '<div><slot /></div>' } }))
vi.mock('@/components/ui/layout/PageContainer.vue', () => ({ default: { template: '<main><slot /></main>' } }))
vi.mock('@/components/ui/base/BaseCard.vue', () => ({ default: { template: '<section><slot /></section>' } }))

let JourneyInsights
let pinia

const insights = {
  geographic: {
    countries: [{ name: 'Ukraine' }],
    cities: [{ name: 'Kyiv', visits: 8 }]
  },
  distanceTraveled: {
    total: 12.4,
    byCar: 10,
    byPublicTransport: 1,
    byWalk: 2,
    byUnknown: 0.4
  },
  timePatterns: {
    mostActiveMonth: 'August',
    busiestDayOfWeek: 'Saturday',
    mostActiveTime: '3:30 PM'
  },
  achievements: {
    badges: [
      { id: 'total_distance_500_000', icon: '🛰️', title: 'Planet Circler', description: 'Travel 500,000+ km total', earned: false, progress: 92, current: 460686, target: 500000 },
      { id: 'daily_habit_10', icon: '🔥', title: 'Daily Habit Starter', description: 'Travel every day for 10 consecutive days', earned: true },
      { id: 'weather_first_sample', icon: '🌦️', title: 'Weather Witness', description: 'Record your first weather sample', earned: true }
    ]
  },
  weather: {
    samplesCount: 1,
    hottestTemperature: { temperature: 30, weatherCode: 0 },
    coldestTemperature: { temperature: -2, weatherCode: 71 },
    wettestDay: { date: '2026-05-03', precipitation: 4 },
    dominantCondition: { weatherCode: 2, label: 'Partly cloudy', samplesCount: 1 }
  }
}

const stubs = {
  ProgressSpinner: true
}

const mountPage = () => mount(JourneyInsights, { global: { plugins: [pinia], stubs } })

describe('JourneyInsights', () => {
  beforeAll(async () => {
    localStorage.setItem('userInfo', JSON.stringify({ timezone: 'UTC', dateFormat: 'YMD', timeFormat: '24h' }))
    JourneyInsights = (await import('./JourneyInsights.vue')).default
  })

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    const journeyStore = useJourneyInsightsStore()
    const authStore = useAuthStore()
    authStore.$patch({ user: { distanceUnit: 'KILOMETERS', temperatureUnit: 'CELSIUS' } })
    journeyStore.$patch({ insights, loading: false })
    vi.spyOn(journeyStore, 'fetchJourneyInsights').mockResolvedValue(insights)
  })

  it('renders the complete movement breakdown without duplicating hero details', () => {
    const wrapper = mountPage()

    expect(wrapper.text()).toContain('12 km of movement.')
    expect(wrapper.text()).toContain('Car')
    expect(wrapper.text()).toContain('Public Transportation')
    expect(wrapper.text()).toContain('Walk')
    expect(wrapper.text()).toContain('Unclassified')
    expect(wrapper.text()).toContain('Unclassified 3%')
    const movementColors = wrapper.findAll('.movement-legend i').map((element) => element.attributes('style'))
    expect(new Set(movementColors).size).toBe(movementColors.length)
    expect(wrapper.find('.journey-summary').exists()).toBe(false)
    expect(wrapper.find('.hero-stats').exists()).toBe(false)
    expect(wrapper.find('.movement-mix').exists()).toBe(false)
    expect(wrapper.find('.journey-hero .movement-bar').exists()).toBe(true)
    expect(wrapper.find('.movement-card').exists()).toBe(false)
    expect(wrapper.findAll('.insights-section').some((section) => section.text().includes('How you moved'))).toBe(false)
    expect(wrapper.text()).toContain('Ukraine')
    expect(wrapper.text()).toContain('Kyiv')
    expect(wrapper.find('.city-icon').classes()).toContain('pi-building')
    expect(wrapper.text()).toContain('Weather Along the Way')
    expect(wrapper.text()).toContain('Distance milestones')
    expect(wrapper.text()).toContain('Consistency streaks')
    expect(wrapper.text()).toContain('Weather explorer')
    expect(wrapper.text()).toContain('92%')
  })

  it('shows the empty state when insights have not loaded', () => {
    useJourneyInsightsStore().$patch({ insights: null, loading: false })
    useAuthStore().$patch({ user: { distanceUnit: 'KILOMETERS', temperatureUnit: 'CELSIUS' } })

    const wrapper = mountPage()
    expect(wrapper.text()).toContain('No Journey Data Available')
  })

  it('shows the Rewind-style loading state while fetching insights', () => {
    useJourneyInsightsStore().$patch({ loading: true })

    const wrapper = mountPage()
    expect(wrapper.text()).toContain('Building your journey insights…')
  })
})
