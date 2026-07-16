import { mount } from '@vue/test-utils'

let PlaceStatsCard

const baseStatistics = {
  totalVisits: 12,
  visitsThisWeek: 1,
  visitsThisMonth: 4,
  visitsThisYear: 12,
  totalDuration: 7200,
  averageDuration: 600,
  minDuration: 120,
  maxDuration: 1800,
  firstVisit: '2026-01-02T08:00:00Z',
  lastVisit: '2026-07-15T18:00:00Z'
}

const mountCard = (statistics) => mount(PlaceStatsCard, {
  props: { statistics },
  global: {
    stubs: {
      BaseCard: {
        props: ['title'],
        template: '<section><h2>{{ title }}</h2><slot /></section>'
      }
    }
  }
})

const installLocalStorageShim = () => {
  if (typeof globalThis.localStorage?.getItem === 'function') {
    return
  }

  const storage = new Map()
  const localStorageShim = {
    getItem: vi.fn((key) => storage.get(key) || null),
    setItem: vi.fn((key, value) => storage.set(key, String(value))),
    removeItem: vi.fn((key) => storage.delete(key)),
    clear: vi.fn(() => storage.clear())
  }

  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: localStorageShim
  })
  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: localStorageShim
  })
}

describe('PlaceStatsCard', () => {
  beforeAll(async () => {
    installLocalStorageShim()
    PlaceStatsCard = (await import('./PlaceStatsCard.vue')).default
  })

  beforeEach(() => {
    localStorage.setItem('userInfo', JSON.stringify({
      timezone: 'UTC',
      dateFormat: 'MDY',
      timeFormat: '24h'
    }))
  })

  afterEach(() => {
    localStorage.clear()
  })

  it('does not render visit patterns when the backend omits them', () => {
    const wrapper = mountCard(baseStatistics)

    expect(wrapper.text()).not.toContain('Visit Patterns')
    expect(wrapper.text()).not.toContain('Typical Day')
  })

  it('renders compact visit patterns when provided', () => {
    const wrapper = mountCard({
      ...baseStatistics,
      visitPatterns: {
        mostCommonDayOfWeek: 'Friday',
        mostCommonDayVisitCount: 5,
        mostCommonArrivalPeriod: 'Evening',
        mostCommonArrivalPeriodVisitCount: 7,
        averageDaysBetweenVisits: 8.4,
        minimumVisitsRequired: 10
      }
    })

    expect(wrapper.text()).toContain('Visit Patterns')
    expect(wrapper.text()).toContain('Friday')
    expect(wrapper.text()).toContain('Evening')
    expect(wrapper.text()).toContain('Every 8 days')
  })
})
