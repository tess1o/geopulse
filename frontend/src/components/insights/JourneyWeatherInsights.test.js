import { mount } from '@vue/test-utils'

let JourneyWeatherInsights

const weather = {
  samplesCount: 4,
  hottestTemperature: {
    observedAt: '2026-07-20T14:00:00Z',
    latitude: 49.5512,
    longitude: 25.6023,
    weatherCode: 0,
    temperature: 30,
    windSpeed: 12
  },
  coldestTemperature: {
    observedAt: '2026-01-15T06:00:00Z',
    latitude: 50.4501,
    longitude: 30.5234,
    weatherCode: 71,
    temperature: -3
  },
  averageTemperature: 11.5,
  wettestDay: {
    date: '2026-05-03',
    precipitation: 12.5
  },
  rainySamplesCount: 2,
  windiestSample: {
    windSpeed: 16
  },
  dominantCondition: {
    weatherCode: 2,
    label: 'Partly cloudy',
    samplesCount: 3
  }
}

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

describe('JourneyWeatherInsights', () => {
  beforeAll(async () => {
    installLocalStorageShim()
    JourneyWeatherInsights = (await import('./JourneyWeatherInsights.vue')).default
  })

  beforeEach(() => {
    localStorage.setItem('userInfo', JSON.stringify({
      timezone: 'UTC',
      dateFormat: 'YMD',
      timeFormat: '24h'
    }))
  })

  afterEach(() => {
    localStorage.clear()
  })

  it('does not render without weather samples', () => {
    const wrapper = mount(JourneyWeatherInsights, {
      props: {
        weather: null
      }
    })

    expect(wrapper.text()).not.toContain('Weather Along the Way')
  })

  it('renders metric weather summaries', () => {
    const wrapper = mount(JourneyWeatherInsights, {
      props: {
        weather,
        measureUnit: 'METRIC'
      }
    })

    expect(wrapper.text()).toContain('Weather Along the Way')
    expect(wrapper.text()).toContain('30°C')
    expect(wrapper.text()).toContain('-3°C')
    expect(wrapper.text()).toContain('13 mm')
    expect(wrapper.text()).toContain('Max wind 16 km/h')
    expect(wrapper.text()).toContain('Partly cloudy')
  })

  it('renders imperial temperature, precipitation, and wind units', () => {
    const wrapper = mount(JourneyWeatherInsights, {
      props: {
        weather,
        measureUnit: 'IMPERIAL'
      }
    })

    expect(wrapper.text()).toContain('86°F')
    expect(wrapper.text()).toContain('27°F')
    expect(wrapper.text()).toContain('0.5 in')
    expect(wrapper.text()).toContain('Max wind 10 mph')
  })
})
