vi.hoisted(() => {
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: {
      getItem: vi.fn(),
      setItem: vi.fn(),
      removeItem: vi.fn(),
      clear: vi.fn()
    }
  })
})

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import AuthenticationSettingsTab from './AuthenticationSettingsTab.vue'
import GeocodingSettingsTab from './GeocodingSettingsTab.vue'
import ImportSettingsTab from './ImportSettingsTab.vue'
import SystemSettingsTab from './SystemSettingsTab.vue'
import PoiSettingsTab from './PoiSettingsTab.vue'
import { buildAdminSettingsIndex } from '@/constants/globalSearchRegistry'
import { useAuthStore } from '@/stores/auth'
import { searchAndRankItems } from '@/utils/globalSearchScoring'

const mocks = vi.hoisted(() => ({
  loadSettings: vi.fn(),
  updateSetting: vi.fn(),
  resetSetting: vi.fn(),
  getCustomGeocodingProviders: vi.fn(),
  toastAdd: vi.fn()
}))

vi.mock('@/composables/useAdminSettings', () => ({
  useAdminSettings: () => ({
    loadSettings: mocks.loadSettings,
    updateSetting: mocks.updateSetting,
    resetSetting: mocks.resetSetting
  })
}))

vi.mock('@/utils/adminService', () => ({
  default: {
    getCustomGeocodingProviders: mocks.getCustomGeocodingProviders
  }
}))

vi.mock('@/utils/apiService', () => ({
  default: {
    post: vi.fn(),
    // The real auth store is used here, and it reaches for these while the tabs mount.
    get: vi.fn().mockResolvedValue([]),
    isTokenExpired: vi.fn(() => false),
    clearAuthData: vi.fn(),
    handleError: vi.fn()
  }
}))

vi.mock('primevue/usetoast', () => ({
  useToast: () => ({ add: mocks.toastAdd })
}))

const setting = (key, valueType = 'INTEGER', currentValue = 1) => ({
  key,
  label: key,
  description: `${key} description`,
  valueType,
  currentValue,
  defaultValue: String(currentValue),
  isDefault: true,
  readOnly: false
})

const settingsByCategory = {
  auth: [
    setting('auth.registration.enabled', 'BOOLEAN', true),
    setting('auth.oidc.callback-base-url', 'STRING', 'https://app.example'),
    setting('auth.oidc.jwks-cache.ttl-hours'),
    setting('auth.oidc.cleanup.session-states.enabled', 'BOOLEAN', true)
  ],
  geocoding: [
    setting('geocoding.primary-provider', 'STRING', 'nominatim'),
    setting('geocoding.fallback-provider', 'STRING', ''),
    setting('geocoding.delay-ms'),
    setting('geocoding.nominatim.enabled', 'BOOLEAN', true),
    setting('geocoding.nominatim.url', 'STRING', ''),
    setting('geocoding.nominatim.language', 'STRING', ''),
    setting('geocoding.nominatim.public-host-forward-search-enabled', 'BOOLEAN', false),
    setting('geocoding.cache.max-bbox-area-km2'),
    setting('geocoding.reconcile.item.max-attempts'),
    setting('geocoding.reconcile.circuit-open-wait-ms'),
    setting('geocoding.reconcile.inter-item-delay-ms')
  ],
  import: [
    setting('import.chunk-size-mb'),
    setting('import.bulk-insert-batch-size'),
    setting('import.large-file-threshold-mb'),
    setting('import.drop-folder.enabled', 'BOOLEAN', false),
    setting('import.geojson-streaming-batch-size'),
    setting('import.transaction-timeout-minutes'),
    setting('import.upload-cleanup-minutes'),
    setting('import.geonames.cities.enabled', 'BOOLEAN', true),
    setting('import.geonames.cities.url', 'STRING', 'https://download.example/cities.zip'),
    setting('import.geonames.cities.batch-size'),
    setting('import.geonames.countries.enabled', 'BOOLEAN', true),
    setting('import.geonames.countries.url', 'STRING', 'https://download.example/countryInfo.txt'),
    setting('import.geonames.countries.batch-size')
  ],
  poi: [
    setting('poi.enabled', 'BOOLEAN', true),
    setting('poi.user-agent', 'STRING', 'GeoPulse/test'),
    setting('poi.language', 'STRING', 'en'),
    setting('poi.attribution.enabled', 'BOOLEAN', true),
    setting('poi.wikidata.endpoint', 'STRING', 'https://query.wikidata.org'),
    setting('poi.commons.endpoint', 'STRING', 'https://commons.wikimedia.org'),
    setting('poi.max-results'),
    setting('poi.commons.thumb-width'),
    setting('poi.cache.ttl-days'),
    setting('poi.cache.image-ttl-days')
  ],
  system: [
    setting('system.user.default-distance-unit', 'STRING', 'KILOMETERS'),
    setting('system.version-check.github-api-url', 'STRING', 'https://api.example/releases/latest'),
    setting('system.version-check.release-url', 'STRING', 'https://example/releases'),
    setting('system.version-check.cache-ttl-minutes'),
    setting('system.water-dataset.url', 'STRING', 'https://example/water.copy.gz'),
    setting('system.water-dataset.sha256', 'STRING', ''),
    setting('system.water-dataset.auto-import', 'BOOLEAN', true),
    setting('system.water-dataset.download-timeout-hours')
  ]
}

const PrimeStub = {
  props: ['modelValue', 'disabled'],
  emits: ['update:modelValue', 'change', 'input'],
  template: '<input :disabled="disabled" :value="modelValue" @input="$emit(\'input\', $event)" @change="$emit(\'change\', $event)" />'
}

const mountTab = async (component) => {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().user = { role: 'ADMIN', canViewAdmin: true, adminReadOnly: false }

  const wrapper = mount(component, {
    global: {
      plugins: [pinia],
      directives: {
        tooltip: {}
      },
      stubs: {
        Button: { props: ['label', 'disabled'], template: '<button :disabled="disabled">{{ label }}</button>' },
        InputNumber: PrimeStub,
        InputSwitch: PrimeStub,
        InputText: PrimeStub,
        Password: PrimeStub,
        Select: PrimeStub,
        Textarea: PrimeStub,
        Message: { template: '<div><slot /></div>' },
        SettingItem: {
          props: ['setting'],
          template: '<div class="setting-item" :data-setting-id="setting.key"><span>{{ setting.key }}</span><slot name="control" :setting="setting" /></div>'
        },
        SettingSection: { props: ['title'], template: '<section><h2>{{ title }}</h2><slot /></section>' },
        Tag: { props: ['value'], template: '<span>{{ value }}</span>' }
      }
    }
  })
  await flushPromises()
  return wrapper
}

describe('Admin advanced settings structure', () => {
  beforeEach(() => {
    mocks.loadSettings.mockReset().mockImplementation(category => Promise.resolve(settingsByCategory[category] || []))
    mocks.updateSetting.mockReset()
    mocks.resetSetting.mockReset()
    mocks.getCustomGeocodingProviders.mockReset().mockResolvedValue([])
    mocks.toastAdd.mockReset()
  })

  it('renders promoted advanced sections inside existing tabs', async () => {
    const auth = await mountTab(AuthenticationSettingsTab)
    expect(auth.text()).toContain('OIDC Advanced')
    expect(auth.text()).toContain('auth.oidc.callback-base-url')

    const geocoding = await mountTab(GeocodingSettingsTab)
    expect(geocoding.text()).toContain('Advanced Operations')
    expect(geocoding.text()).toContain('geocoding.cache.max-bbox-area-km2')

    const imports = await mountTab(ImportSettingsTab)
    expect(imports.text()).toContain('GeoNames')
    expect(imports.text()).toContain('import.geonames.cities.url')
    expect(imports.text()).toContain('import.transaction-timeout-minutes')

    const system = await mountTab(SystemSettingsTab)
    expect(system.text()).toContain('Update Check')
    expect(system.text()).toContain('Water Dataset')
    expect(system.text()).toContain('system.water-dataset.url')

    const poi = await mountTab(PoiSettingsTab)
    expect(poi.text()).toContain('Advanced settings')
    // The user-agent must stay in the basic section: burying it is how a self-hoster ends
    // up rate-limited without ever seeing the setting that controls it.
    expect(poi.text()).toContain('poi.user-agent')
    expect(poi.text()).toContain('poi.wikidata.endpoint')
  })

  it('adds promoted settings to admin settings search metadata', () => {
    const index = buildAdminSettingsIndex(true)
    const indexedSettings = new Set(index.map(item => item.setting))

    expect(indexedSettings.has('auth.oidc.callback-base-url')).toBe(true)
    expect(indexedSettings.has('geocoding.reconcile.item.max-attempts')).toBe(true)
    expect(indexedSettings.has('weather.open-meteo.connect-timeout-seconds')).toBe(true)
    expect(indexedSettings.has('import.geonames.countries.url')).toBe(true)
    expect(indexedSettings.has('system.version-check.github-api-url')).toBe(true)
    expect(indexedSettings.has('backup.local.path')).toBe(true)
    expect(indexedSettings.has('poi.wikidata.endpoint')).toBe(true)

    const backupResults = searchAndRankItems('backup', index, { minScore: 120 }).map(entry => entry.item)
    expect(backupResults.length).toBeGreaterThan(0)
    expect(backupResults.every(item => item.tab === 'backup')).toBe(true)
    expect(backupResults.map(item => item.setting)).toContain('backup.local.path')

    // Guards ADMIN_TAB_BY_PREFIX: without a poi. entry these would deep-link to the
    // wrong tab rather than fail.
    // Asserted from the index rather than through a search: searching "poi" also matches
    // "endpoint" (en-poi-nt), so unrelated settings legitimately rank in.
    const poiItems = index.filter(item => item.setting.startsWith('poi.'))
    expect(poiItems.length).toBeGreaterThan(0)
    expect(poiItems.every(item => item.tab === 'poi')).toBe(true)
    expect(poiItems.map(item => item.setting)).toContain('poi.wikidata.endpoint')
  })
})
