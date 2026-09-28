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
        distanceUnit: 'KILOMETERS',
        temperatureUnit: 'CELSIUS'
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
        distanceUnit: 'MILES',
        temperatureUnit: 'FAHRENHEIT'
      }
    })

    expect(wrapper.text()).toContain('86°F')
    expect(wrapper.text()).toContain('27°F')
    expect(wrapper.text()).toContain('0.5 in')
    expect(wrapper.text()).toContain('Max wind 10 mph')
  })

  describe('Ukrainian locale', () => {
    beforeEach(async () => {
      const { setLocale } = await import('@/composables/useLocale')
      await setLocale('uk', { persist: false })
    })

    const mountWeather = (overrides = {}) => mount(JourneyWeatherInsights, {
      props: {
        weather: { ...weather, ...overrides },
        distanceUnit: 'KILOMETERS',
        temperatureUnit: 'CELSIUS'
      }
    })

    it('translates the section, card labels and units', () => {
      const text = mountWeather().text()

      expect(text).toContain('Погода в дорозі')
      expect(text).toContain('Найспекотніший момент')
      expect(text).toContain('Найхолодніший момент')
      expect(text).toContain('Найдощовіший день')
      expect(text).toContain('Найпоширеніша погода')
      expect(text).toContain('Макс. вітер 16 km/h')
      // Numbers and units are data, not copy.
      expect(text).toContain('30°C')
      expect(text).toContain('13 mm')
    })

    it('translates the condition from the backend weather code, not its English label', () => {
      // The payload's label is English; the code is the locale-neutral key, so uk must win.
      expect(mountWeather().text()).toContain('Мінлива хмарність')
      expect(mountWeather().text()).not.toContain('Partly cloudy')
    })

    it('falls back to the server label when the payload carries no code', () => {
      const text = mountWeather({ dominantCondition: { label: 'Some Server Text', samplesCount: 3 } }).text()

      expect(text).toContain('Some Server Text')
    })

    it('uses the unknown-condition key when there is no code or label', () => {
      expect(mountWeather({ dominantCondition: { samplesCount: 3 } }).text()).toContain('Погода')
    })

    it('selects the right Ukrainian plural form for rainy samples and total samples', () => {
      // rainySamplesCount 2 -> few; dominantCondition.samplesCount 3 -> few.
      expect(mountWeather().text()).toContain('2 зразки з дощем')
      expect(mountWeather().text()).toContain('3 зразки · Сер.')

      // ...and the many form for a count like 5.
      const many = mountWeather({ rainySamplesCount: 5, dominantCondition: { weatherCode: 2, samplesCount: 5 } }).text()
      expect(many).toContain('5 зразків з дощем')
      expect(many).toContain('5 зразків · Сер.')
    })

    it('translates the missing-date placeholder', () => {
      const text = mountWeather({ hottestTemperature: { temperature: 30, weatherCode: 0 } }).text()

      expect(text).toContain('Дата недоступна')
      expect(text).not.toContain('Date unavailable')
    })
  })
})
