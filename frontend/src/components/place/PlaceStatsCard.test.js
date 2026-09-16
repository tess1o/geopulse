import { shallowMount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import PlaceStatsCard from './PlaceStatsCard.vue'

vi.hoisted(() => {
  const storage = new Map()
  const localStorageShim = {
    getItem: (key) => storage.get(key) || null,
    setItem: (key, value) => storage.set(key, String(value)),
    removeItem: (key) => storage.delete(key),
    clear: () => storage.clear()
  }
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: localStorageShim })
})

vi.mock('@/composables/useTimezone', () => ({
  useTimezone: () => ({ format: (value) => `date:${value}` })
}))

const MetricItem = {
  props: ['label', 'value'],
  template: '<div class="metric-stub">{{ label }}: {{ value }}</div>'
}

const statistics = {
  totalVisits: 12,
  totalDuration: 7200,
  averageDuration: 600,
  minDuration: 60,
  maxDuration: 1800,
  firstVisit: 'first',
  lastVisit: 'last',
  visitsThisWeek: 2,
  visitsThisMonth: 5,
  visitsThisYear: 10
}

describe('PlaceStatsCard', () => {
  it('renders the shared visit metrics and only shows available patterns', () => {
    const withoutPatterns = shallowMount(PlaceStatsCard, {
      props: { statistics },
      global: { stubs: { BaseCard: { template: '<section><slot /></section>' }, MetricItem } }
    })
    expect(withoutPatterns.text()).toContain('Total visits: 12')
    expect(withoutPatterns.text()).toContain('Activity')
    expect(withoutPatterns.text()).toContain('First visit: date:first')
    expect(withoutPatterns.text()).not.toContain('Visit patterns')

    const withPatterns = shallowMount(PlaceStatsCard, {
      props: {
        statistics: {
          ...statistics,
          visitPatterns: {
            mostCommonDayOfWeek: 'Saturday',
            mostCommonArrivalPeriod: 'Evening',
            averageDaysBetweenVisits: 3
          }
        }
      },
      global: { stubs: { BaseCard: { template: '<section><slot /></section>' }, MetricItem } }
    })
    expect(withPatterns.text()).toContain('Visit patterns')
    expect(withPatterns.text()).toContain('Typical day: Saturday')
    expect(withPatterns.text()).toContain('Visit cadence: Every 3 days')
  })
})
