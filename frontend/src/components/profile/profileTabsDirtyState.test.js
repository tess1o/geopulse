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

const { testMemosConfig, testImmichConnection, testAIConnection, fetchDefaultSystemMessage } = vi.hoisted(() => ({
  testMemosConfig: vi.fn(),
  testImmichConnection: vi.fn(),
  testAIConnection: vi.fn(),
  fetchDefaultSystemMessage: vi.fn().mockResolvedValue({ message: 'Default AI system message' })
}))

import { flushPromises, mount } from '@vue/test-utils'
import ProfileTab from './ProfileTab.vue'
import SecurityTab from './SecurityTab.vue'
import TimelineDisplayTab from './TimelineDisplayTab.vue'
import AIAssistantTab from './AIAssistantTab.vue'
import ImmichTab from './ImmichTab.vue'
import MemosTab from './MemosTab.vue'
import apiService from '@/utils/apiService'

vi.mock('@/utils/apiService', () => ({
  default: {
    get: vi.fn().mockResolvedValue({ message: 'Default AI system message' }),
    post: vi.fn().mockResolvedValue({})
  }
}))

vi.mock('@/stores/notes', () => ({
  useNotesStore: () => ({
    testMemosConfig
  })
}))

vi.mock('@/stores/immich', () => ({
  useImmichStore: () => ({ testConnection: testImmichConnection })
}))

vi.mock('@/stores/ai', () => ({
  useAIStore: () => ({
    testConnection: testAIConnection,
    fetchDefaultSystemMessage
  })
}))

const CardStub = {
  template: `
    <section>
      <slot name="title" />
      <slot name="subtitle" />
      <slot name="content" />
      <slot />
    </section>
  `
}

const ButtonStub = {
  props: ['label', 'disabled', 'loading'],
  emits: ['click'],
  template: `
    <button :disabled="disabled || loading" @click="$emit('click', $event)">
      {{ label }}<slot />
    </button>
  `
}

const TextInputStub = {
  props: ['modelValue', 'value'],
  emits: ['update:modelValue'],
  template: `
    <input
      :value="modelValue ?? value ?? ''"
      @input="$emit('update:modelValue', $event.target.value)"
    />
  `
}

const DropdownStub = {
  props: [
    'modelValue',
    'options',
    'optionLabel',
    'optionValue',
    'filter',
    'filterMatchMode',
    'invalid',
    'placeholder',
    'scrollHeight',
    'showClear'
  ],
  emits: ['update:modelValue'],
  template: `
    <select
      :value="modelValue ?? ''"
      @change="$emit('update:modelValue', $event.target.value)"
    >
      <option value=""></option>
      <option
        v-for="option in options"
        :key="optionValue ? option[optionValue] : option"
        :value="optionValue ? option[optionValue] : option"
      >
        {{ optionLabel ? option[optionLabel] : option }}
      </option>
    </select>
  `
}

const ToggleSwitchStub = {
  props: ['modelValue', 'disabled'],
  emits: ['update:modelValue'],
  template: `
    <input
      type="checkbox"
      :checked="modelValue"
      :disabled="disabled"
      v-bind="$attrs"
      @change="$emit('update:modelValue', $event.target.checked)"
    />
  `
}

const SliderControlStub = {
  props: ['modelValue', 'labels'],
  emits: ['update:modelValue'],
  template: `
    <input
      type="number"
      :value="modelValue"
      @input="$emit('update:modelValue', Number($event.target.value))"
    />
  `
}

const AutoCompleteStub = {
  props: ['modelValue'],
  emits: ['update:modelValue', 'change', 'blur'],
  methods: {
    parseTags(value) {
      return String(value || '')
        .split(',')
        .map((tag) => tag.trim())
        .filter(Boolean)
    },
    updateTags(event) {
      const value = this.parseTags(event.target.value)
      this.$emit('update:modelValue', value)
      this.$emit('change', { originalEvent: event, value })
    }
  },
  template: `
    <input
      :value="Array.isArray(modelValue) ? modelValue.join(', ') : ''"
      @input="updateTags"
      @blur="$emit('blur', $event)"
    />
  `
}

const SettingCardStub = {
  props: ['title', 'description', 'settingId'],
  template: '<section v-bind="$attrs" :data-setting-id="settingId"><h3>{{ title }}</h3><p>{{ description }}</p><slot name="control" /></section>'
}

const globalOptions = {
  stubs: {
    Card: CardStub,
    Button: ButtonStub,
    Avatar: true,
    InputText: TextInputStub,
    Password: TextInputStub,
    Dropdown: DropdownStub,
    Select: DropdownStub,
    AutoComplete: AutoCompleteStub,
    ToggleSwitch: ToggleSwitchStub,
    Textarea: {
      props: ['modelValue'],
      emits: ['update:modelValue'],
      template: '<textarea :value="modelValue ?? \'\'" @input="$emit(\'update:modelValue\', $event.target.value)" />'
    },
    Message: true,
    SettingCard: SettingCardStub,
    SliderControl: SliderControlStub,
    OidcManagement: true,
    ApiTokensManagement: true
  },
  directives: {
    tooltip: {}
  }
}

const lastDirtyValue = (wrapper) => {
  const events = wrapper.emitted('dirty-change') || []
  return events.at(-1)?.[0]
}

const findButtonByLabel = (wrapper, label) => {
  const button = wrapper.findAll('button').find((candidate) => candidate.text().includes(label))
  expect(button).toBeTruthy()
  return button
}

const timelineDisplayPrefs = {
  customMapTileUrl: '',
  customMapStyleUrl: '',
  mapRenderMode: 'VECTOR',
  defaultDateRangePreset: '',
  pathSimplificationEnabled: true,
  pathSimplificationTolerance: 15,
  pathMaxPoints: 0,
  pathAdaptiveSimplification: true,
  showCurrentLocationTelemetry: true,
  autoShowTripReplayControls: true,
  enable3dBuildingsByDefault: false,
  mapMatchingEnabled: false,
  mapMatchingAvailable: true
}

describe('profile tab dirty state', () => {
  it('does not expose or persist a Panoramax display preference', async () => {
    const wrapper = mount(TimelineDisplayTab, {
      props: {
        initialPreferences: { ...timelineDisplayPrefs, panoramaxEnabled: true }
      },
      global: globalOptions
    })
    await flushPromises()

    expect(wrapper.text()).not.toContain('Panoramax')
    await wrapper.find('form').trigger('submit')
    expect(wrapper.emitted('save')[0][0]).not.toHaveProperty('panoramaxEnabled')
  })

  it('persists the 3D buildings default', async () => {
    const wrapper = mount(TimelineDisplayTab, {
      props: {
        initialPreferences: { ...timelineDisplayPrefs, enable3dBuildingsByDefault: true }
      },
      global: globalOptions
    })
    await flushPromises()

    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('save')[0][0].enable3dBuildingsByDefault).toBe(true)
  })

  it('uses controls without duplicate display status values', async () => {
    const wrapper = mount(TimelineDisplayTab, {
      props: {
        initialPreferences: timelineDisplayPrefs
      },
      global: globalOptions
    })
    await flushPromises()

    expect(wrapper.findAll('.control-value')).toHaveLength(0)
  })

  it('groups timeline behavior, map display, and map processing without collapsed sections', async () => {
    const wrapper = mount(TimelineDisplayTab, {
      props: {
        initialPreferences: timelineDisplayPrefs
      },
      global: globalOptions
    })
    await flushPromises()

    const text = wrapper.text()
    expect(wrapper.findAll('details')).toHaveLength(0)
    expect(text.match(/3D buildings/g)).toHaveLength(1)
    expect(text.indexOf('Default date range')).toBeLessThan(text.indexOf('Map display & sources'))
    expect(text.indexOf('Map render mode')).toBeLessThan(text.indexOf('3D buildings'))
    expect(text.indexOf('3D buildings')).toBeLessThan(text.indexOf('Map processing'))
    expect(text.indexOf('Map processing')).toBeLessThan(text.indexOf('Save Changes'))
  })

  it('emits dirty changes from the profile tab and clears after reset', async () => {
    const wrapper = mount(ProfileTab, {
      props: {
        userName: 'Ada Lovelace',
        userEmail: 'ada@example.com',
        userAvatar: '/avatars/avatar1.png',
        userTimezone: 'UTC',
        userDistanceUnit: 'KILOMETERS',
        userTemperatureUnit: 'CELSIUS',
        userDefaultRedirectUrl: '',
        userDateFormat: 'MDY',
        userTimeFormat: '24h'
      },
      global: globalOptions
    })
    await flushPromises()

    await wrapper.find('#fullName').setValue('Ada Byron')
    expect(lastDirtyValue(wrapper)).toBe(true)

    await findButtonByLabel(wrapper, 'Reset').trigger('click')
    await flushPromises()
    expect(lastDirtyValue(wrapper)).toBe(false)
  })

  it('keeps general settings together', async () => {
    const props = {
      userName: 'Ada Lovelace', userEmail: 'ada@example.com', userAvatar: '/avatars/avatar1.png',
      userTimezone: 'UTC', userDistanceUnit: 'KILOMETERS', userTemperatureUnit: 'CELSIUS',
      userDefaultRedirectUrl: '', userDateFormat: 'MDY', userTimeFormat: '24h'
    }
    const wrapper = mount(ProfileTab, { props, global: globalOptions })
    await flushPromises()

    expect(wrapper.text()).toContain('General')
    expect(wrapper.text()).toContain('Profile')
    expect(wrapper.text()).toContain('Regional preferences')
    expect(wrapper.text()).toContain('Navigation')
    expect(wrapper.findAll('.settings-panel')).toHaveLength(3)
    expect(wrapper.find('#fullName').exists()).toBe(true)
    expect(wrapper.find('#timezone').exists()).toBe(true)
    expect(wrapper.findAll('#dateFormat')).toHaveLength(1)
    expect(wrapper.findAll('#timeFormat')).toHaveLength(1)
    expect(wrapper.findAll('#distanceUnit')).toHaveLength(1)
    expect(wrapper.findAll('#temperatureUnit')).toHaveLength(1)
    expect(wrapper.findAll('#defaultRedirectUrl')).toHaveLength(1)
    expect(wrapper.get('details[data-setting-id="profileImage"]').attributes('open')).toBeUndefined()
    await wrapper.find('select').setValue('Europe/Kyiv')
    expect(lastDirtyValue(wrapper)).toBe(true)
  })

  it('reveals a custom home page and saves profile and avatar changes', async () => {
    const props = {
      userName: 'Ada Lovelace', userEmail: 'ada@example.com', userAvatar: '/avatars/avatar1.png',
      userTimezone: 'UTC', userDistanceUnit: 'KILOMETERS', userTemperatureUnit: 'CELSIUS',
      userDefaultRedirectUrl: '', userDateFormat: 'MDY', userTimeFormat: '24h'
    }
    const wrapper = mount(ProfileTab, { props, global: globalOptions })
    await flushPromises()

    await wrapper.find('#defaultRedirectUrl').setValue('custom')
    expect(wrapper.find('#customRedirectUrl').exists()).toBe(true)
    await wrapper.find('#customRedirectUrl').setValue('/app/custom')
    await wrapper.find('button[aria-label="Choose profile image 2"]').trigger('click')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('save')[0][0]).toMatchObject({
      avatar: '/avatars/avatar2.png',
      defaultRedirectUrl: '/app/custom'
    })
  })

  it('emits dirty changes from the security tab and clears after reset', async () => {
    const wrapper = mount(SecurityTab, {
      props: {
        hasPassword: true
      },
      global: globalOptions
    })

    await wrapper.find('#newPassword').setValue('secret123')
    expect(lastDirtyValue(wrapper)).toBe(true)

    await findButtonByLabel(wrapper, 'Cancel').trigger('click')
    await flushPromises()
    expect(lastDirtyValue(wrapper)).toBe(false)
  })

  it('keeps password, connected-account, and API-token security sections together', async () => {
    const wrapper = mount(SecurityTab, {
      props: { hasPassword: true },
      global: globalOptions
    })

    expect(wrapper.text()).toContain('Security')
    expect(wrapper.text()).toContain('Change password')
    expect(wrapper.findAll('.settings-panel')).toHaveLength(1)
    expect(wrapper.find('oidc-management-stub').exists()).toBe(true)
    expect(wrapper.find('api-tokens-management-stub').exists()).toBe(true)

    await wrapper.find('#currentPassword').setValue('current-secret')
    await wrapper.find('#newPassword').setValue('new-secret')
    await wrapper.find('#confirmPassword').setValue('new-secret')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('save')[0][0]).toEqual({
      currentPassword: 'current-secret',
      newPassword: 'new-secret'
    })
  })

  it('emits dirty changes from the display tab and clears when saved preferences become canonical', async () => {
    const customMapTileUrl = 'https://tiles.example.com/{z}/{x}/{y}.png'
    const wrapper = mount(TimelineDisplayTab, {
      props: {
        initialPreferences: timelineDisplayPrefs
      },
      global: globalOptions
    })
    await flushPromises()

    await wrapper.find('#customMapTileUrl').setValue(customMapTileUrl)
    expect(lastDirtyValue(wrapper)).toBe(true)

    await wrapper.setProps({
      initialPreferences: {
        ...timelineDisplayPrefs,
        customMapTileUrl
      }
    })
    await flushPromises()
    expect(lastDirtyValue(wrapper)).toBe(false)
  })

  it('disables the map matching toggle when the feature is unavailable', async () => {
    const wrapper = mount(TimelineDisplayTab, {
      props: {
        initialPreferences: {
          ...timelineDisplayPrefs,
          mapMatchingAvailable: false
        }
      },
      global: globalOptions
    })
    await flushPromises()

    const toggle = wrapper.find('input[aria-label="Enable map matching"]')

    expect(toggle.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Unavailable')
  })

  it('allows the map matching toggle when the feature is available', async () => {
    const wrapper = mount(TimelineDisplayTab, {
      props: {
        initialPreferences: {
          ...timelineDisplayPrefs,
          mapMatchingAvailable: true
        }
      },
      global: globalOptions
    })
    await flushPromises()

    const toggle = wrapper.find('input[aria-label="Enable map matching"]')

    expect(toggle.attributes('disabled')).toBeUndefined()
    await toggle.setValue(true)
    expect(lastDirtyValue(wrapper)).toBe(true)
  })

  it('emits dirty changes from the AI tab and clears when saved settings become canonical', async () => {
    const initialSettings = {
      enabled: false,
      openaiApiKey: '',
      openaiApiUrl: 'https://api.openai.com/v1',
      openaiModel: 'gpt-4o-mini',
      openaiApiKeyConfigured: false,
      apiKeyRequired: true,
      customSystemMessage: 'Use concise answers.'
    }
    const openaiApiUrl = 'https://llm.example.com/v1'
    const wrapper = mount(AIAssistantTab, {
      props: {
        initialSettings
      },
      global: globalOptions
    })
    await flushPromises()

    await wrapper.find('#openai-api-url').setValue(openaiApiUrl)
    expect(lastDirtyValue(wrapper)).toBe(true)
    await wrapper.find('form').trigger('submit')
    expect(wrapper.emitted('save').at(-1)[0]).toMatchObject({ openaiApiUrl })

    await wrapper.setProps({
      initialSettings: {
        ...initialSettings,
        openaiApiUrl
      }
    })
    await flushPromises()
    expect(lastDirtyValue(wrapper)).toBe(false)
  })

  it('groups AI settings and keeps model refresh and read-only behavior', async () => {
    testAIConnection.mockReset().mockResolvedValue(['gpt-4o-mini'])
    const initialSettings = {
      enabled: false,
      openaiApiKey: '',
      openaiApiUrl: 'https://api.openai.com/v1',
      openaiModel: 'gpt-4o-mini',
      openaiApiKeyConfigured: false,
      apiKeyRequired: true,
      customSystemMessage: 'Use concise answers.'
    }
    const wrapper = mount(AIAssistantTab, { props: { initialSettings }, global: globalOptions })
    await flushPromises()

    expect(wrapper.findAll('.settings-panel')).toHaveLength(3)
    expect(wrapper.findAll('#openai-api-key')).toHaveLength(1)
    expect(wrapper.findAll('#openai-api-url')).toHaveLength(1)
    expect(wrapper.findAll('#openai-model')).toHaveLength(1)
    await wrapper.get('[aria-label="Refresh provider models"]').trigger('click')
    await flushPromises()
    expect(testAIConnection).toHaveBeenCalledWith({
      openaiApiUrl: 'https://api.openai.com/v1',
      openaiApiKey: '',
      isApiKeyNeeded: true
    })

    await wrapper.setProps({ readOnly: true })
    expect(wrapper.get('#ai-enabled').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[aria-label="Refresh provider models"]').attributes('disabled')).toBeDefined()
  })

  it('emits dirty changes from the Immich tab and clears after reset', async () => {
    testImmichConnection.mockReset().mockResolvedValue({ success: true, status: 'CONNECTED' })
    const wrapper = mount(ImmichTab, {
      props: {
        config: {
          serverUrl: 'https://photos.example.com',
          apiKey: 'configured-key',
          enabled: true
        },
        loading: false
      },
      global: globalOptions
    })
    await flushPromises()

    await wrapper.find('#immichServerUrl').setValue('https://new-photos.example.com')
    expect(lastDirtyValue(wrapper)).toBe(true)
    expect(wrapper.findAll('.settings-panel')).toHaveLength(2)
    expect(wrapper.findAll('#immichServerUrl')).toHaveLength(1)
    expect(wrapper.findAll('#immichApiKey')).toHaveLength(1)
    await findButtonByLabel(wrapper, 'Test Connection').trigger('click')
    await flushPromises()
    expect(testImmichConnection).toHaveBeenCalledWith({
      serverUrl: 'https://new-photos.example.com',
      apiKey: null
    })

    await findButtonByLabel(wrapper, 'Reset').trigger('click')
    await flushPromises()
    expect(lastDirtyValue(wrapper)).toBe(false)
  })

  it('keeps Immich connection controls visible but disabled while off', async () => {
    const wrapper = mount(ImmichTab, {
      props: { config: { serverUrl: 'https://photos.example.com', apiKey: 'configured-key', enabled: false }, loading: false },
      global: globalOptions
    })
    await flushPromises()

    expect(wrapper.get('#immichServerUrl').attributes('disabled')).toBeDefined()
    expect(wrapper.get('#immichApiKey').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).not.toContain('Test Connection')
  })

  it('emits dirty changes from the Memos cache toggle and clears after reset', async () => {
    testMemosConfig.mockReset().mockResolvedValue({ success: true, message: 'Connected' })
    const wrapper = mount(MemosTab, {
      props: {
        config: {
          serverUrl: 'https://memos.example.com',
          apiKey: 'configured-key',
          enabled: true,
          defaultSaveDestination: 'GEOPULSE',
          defaultVisibility: 'PRIVATE',
          searchCacheEnabled: true,
          includeTags: [],
          excludeTags: []
        },
        loading: false
      },
      global: globalOptions
    })
    await flushPromises()

    expect(wrapper.findAll('.settings-panel')).toHaveLength(4)
    expect(wrapper.findAll('#memosDefaultDestination')).toHaveLength(1)
    expect(wrapper.findAll('#memosDefaultVisibility')).toHaveLength(1)
    await findButtonByLabel(wrapper, 'Test Connection').trigger('click')
    await flushPromises()
    expect(testMemosConfig).toHaveBeenCalledWith({ serverUrl: 'https://memos.example.com', apiKey: null })

    await wrapper.find('[data-setting-id="memosSearchCacheEnabled"] input').setValue(false)
    expect(lastDirtyValue(wrapper)).toBe(true)

    await findButtonByLabel(wrapper, 'Reset').trigger('click')
    await flushPromises()
    expect(lastDirtyValue(wrapper)).toBe(false)
  })

  it('hides Memos defaults and filtering while the integration is off', async () => {
    const wrapper = mount(MemosTab, {
      props: {
        config: { serverUrl: 'https://memos.example.com', apiKey: 'configured-key', enabled: false },
        loading: false
      },
      global: globalOptions
    })
    await flushPromises()

    expect(wrapper.text()).not.toContain('Timeline defaults')
    expect(wrapper.text()).not.toContain('Filtering & performance')
    expect(wrapper.get('#memosServerUrl').attributes('disabled')).toBeDefined()
    expect(wrapper.get('#memosApiKey').attributes('disabled')).toBeDefined()
  })

  it('emits dirty changes from the Memos tag filters and clears after reset', async () => {
    const wrapper = mount(MemosTab, {
      props: {
        config: {
          serverUrl: 'https://memos.example.com',
          apiKey: 'configured-key',
          enabled: true,
          defaultSaveDestination: 'GEOPULSE',
          defaultVisibility: 'PRIVATE',
          searchCacheEnabled: true,
          includeTags: ['travel'],
          excludeTags: []
        },
        loading: false
      },
      global: globalOptions
    })
    await flushPromises()

    await wrapper.find('[data-setting-id="memosExcludeTags"] input').setValue('private')
    expect(lastDirtyValue(wrapper)).toBe(true)

    await findButtonByLabel(wrapper, 'Reset').trigger('click')
    await flushPromises()
    expect(lastDirtyValue(wrapper)).toBe(false)
  })

  it('saves normalized Memos tag arrays', async () => {
    const wrapper = mount(MemosTab, {
      props: {
        config: {
          serverUrl: 'https://memos.example.com',
          apiKey: 'configured-key',
          enabled: true,
          defaultSaveDestination: 'GEOPULSE',
          defaultVisibility: 'PRIVATE',
          searchCacheEnabled: true,
          includeTags: [],
          excludeTags: []
        },
        loading: false
      },
      global: globalOptions
    })
    await flushPromises()

    await wrapper.find('[data-setting-id="memosIncludeTags"] input').setValue(' #travel, travel, #work ')
    await wrapper.find('[data-setting-id="memosExcludeTags"] input').setValue('#private, archive')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const savePayload = wrapper.emitted('save').at(-1)[0]
    expect(savePayload.includeTags).toEqual(['travel', 'work'])
    expect(savePayload.excludeTags).toEqual(['private', 'archive'])
  })
})
