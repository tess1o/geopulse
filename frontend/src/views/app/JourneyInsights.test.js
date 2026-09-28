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
vi.mock('@/components/ui/layout/PageContainer.vue', () => ({
  default: {
    name: 'PageContainer',
    props: ['title', 'subtitle'],
    template: '<main><h1>{{ title }}</h1><p>{{ subtitle }}</p><slot /></main>'
  }
}))
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
    mostActiveYearMonth: '2026-08',
    monthlyComparison: { key: 'insights.patterns.monthlyComparison.faster', parameters: { percent: 12 }, fallback: '12% faster pace than your best month!' },
    busiestDayOfWeek: 6, // ISO-8601: Saturday
    dayInsight: { key: 'insights.patterns.dayInsight.weekend', parameters: {}, fallback: 'Perfect for weekend adventures!' },
    mostActiveHour: 15,
    timeInsight: { key: 'insights.patterns.timeInsight.evening', parameters: {}, fallback: 'Evening adventurer' }
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

  it('formats time patterns from locale-neutral backend data and resolves their MessageDescriptor insight text', () => {
    const wrapper = mountPage()

    // mostActiveYearMonth ('2026-08') and busiestDayOfWeek (6, ISO-8601 Saturday) are locale-neutral
    // so the frontend formats them via dayjs against the active locale, same as the rest of the app.
    expect(wrapper.text()).toContain('August 2026')
    expect(wrapper.text()).toContain('Saturday')
    // mostActiveHour (15) plus the fixed :30 bucket, formatted per the test profile's 24h preference.
    expect(wrapper.text()).toContain('15:30')
    // monthlyComparison/dayInsight/timeInsight are MessageDescriptors; { percent: 12 } interpolates
    // into the catalog's `{percent}` placeholder rather than the backend's pre-formatted fallback.
    expect(wrapper.text()).toContain('12% faster pace than your best month!')
    expect(wrapper.text()).toContain('Perfect for weekend adventures!')
    expect(wrapper.text()).toContain('Evening adventurer')
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

  describe('Ukrainian locale', () => {
    beforeEach(async () => {
      const { setLocale } = await import('@/composables/useLocale')
      await setLocale('uk', { persist: false })
    })

    it('translates the page chrome and movement breakdown', () => {
      const wrapper = mountPage()

      // PageContainer is stubbed in this suite, so its title/subtitle are asserted as props rather
      // than as rendered text.
      expect(wrapper.findComponent({ name: 'PageContainer' }).props('title')).toBe('Статистика подорожей')
      expect(wrapper.findComponent({ name: 'PageContainer' }).props('subtitle')).toBe('Ваша історія переміщень в одному місці.')
      expect(wrapper.text()).toContain('Де ви були')
      expect(wrapper.text()).toContain('Часові закономірності')
      expect(wrapper.text()).toContain('12 км переміщень.')
      // Movement labels come from the same key table the tabs use.
      expect(wrapper.text()).toContain('Автомобіль')
      expect(wrapper.text()).toContain('Громадський транспорт')
      expect(wrapper.text()).toContain('Нерозпізнано 3%')
      // Achievement-group titles are page chrome, so they translate...
      expect(wrapper.text()).toContain('Віхи відстані')
      expect(wrapper.text()).toContain('Серії активності')
    })

    it('formats and translates time patterns', () => {
      const wrapper = mountPage()

      // Month/weekday names come from dayjs's own uk locale (switched globally by setLocale), not
      // from any catalog entry of ours.
      expect(wrapper.text()).toContain('серпень 2026')
      expect(wrapper.text()).toContain('субота')
      // The MessageDescriptor keys resolve through locales/uk/insights.js's `patterns.*` catalog.
      expect(wrapper.text()).toContain('На 12% швидший темп, ніж у вашому найкращому місяці!')
      expect(wrapper.text()).toContain('Ідеально для вихідних пригод!')
      expect(wrapper.text()).toContain('Вечірній мандрівник')
    })

    it('translates badge titles and descriptions from the badges catalog', () => {
      const wrapper = mountPage()

      // Badges are served and persisted by the backend in English, but the frontend keeps its own
      // `badges.<id>.*` catalog (locales/uk/badges.js) keyed by the backend's locale-neutral badge id,
      // so a badge with a catalog entry renders translated rather than leaking the API's English text.
      expect(wrapper.text()).toContain('Володар планети')
      expect(wrapper.text()).toContain('Подолайте 500 000+ км сумарно')
      expect(wrapper.text()).toContain('Початок звички')
      expect(wrapper.text()).not.toContain('Planet Circler')
      expect(wrapper.text()).not.toContain('badges.')
      // The surrounding status chip is chrome and does translate.
      expect(wrapper.text()).toContain('Отримано')
    })

    it('selects the right Ukrainian plural form for each visit count', () => {
      // Ukrainian has three plural forms where English has two; a count of 21 must not read as "many"
      // and 2 must not read as "one".
      useJourneyInsightsStore().$patch({
        insights: {
          ...insights,
          geographic: {
            ...insights.geographic,
            cities: [
              { name: 'Kyiv', visits: 1 },
              { name: 'Lviv', visits: 3 },
              { name: 'Odesa', visits: 8 }
            ]
          }
        }
      })

      const text = mountPage().text()

      expect(text).toContain('1 візит')
      expect(text).toContain('3 візити')
      expect(text).toContain('8 візитів')
    })
  })
})
