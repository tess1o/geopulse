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
import ProfileTab from './ProfileTab.vue'
import SecurityTab from './SecurityTab.vue'
import TimelineDisplayTab from './TimelineDisplayTab.vue'
import AIAssistantTab from './AIAssistantTab.vue'
import ImmichTab from './ImmichTab.vue'
import MemosTab from './MemosTab.vue'

vi.mock('@/utils/apiService', () => ({
  default: {
    get: vi.fn().mockResolvedValue({ message: 'Default AI system message' }),
    post: vi.fn().mockResolvedValue({})
  }
}))

vi.mock('@/stores/notes', () => ({
  useNotesStore: () => ({
    testMemosConfig: vi.fn()
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
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: `
    <input
      type="checkbox"
      :checked="modelValue"
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
  template: '<section><slot name="control" /></section>'
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
  autoShowTripReplayControls: true
}

describe('profile tab dirty state', () => {
  it('emits dirty changes from the profile tab and clears after reset', async () => {
    const wrapper = mount(ProfileTab, {
      props: {
        userName: 'Ada Lovelace',
        userEmail: 'ada@example.com',
        userAvatar: '/avatars/avatar1.png',
        userTimezone: 'UTC',
        userMeasureUnit: 'METRIC',
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

    await wrapper.setProps({
      initialSettings: {
        ...initialSettings,
        openaiApiUrl
      }
    })
    await flushPromises()
    expect(lastDirtyValue(wrapper)).toBe(false)
  })

  it('emits dirty changes from the Immich tab and clears after reset', async () => {
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

    await findButtonByLabel(wrapper, 'Reset').trigger('click')
    await flushPromises()
    expect(lastDirtyValue(wrapper)).toBe(false)
  })

  it('emits dirty changes from the Memos cache toggle and clears after reset', async () => {
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

    await wrapper.find('[data-setting-id="memosSearchCacheEnabled"] input').setValue(false)
    expect(lastDirtyValue(wrapper)).toBe(true)

    await findButtonByLabel(wrapper, 'Reset').trigger('click')
    await flushPromises()
    expect(lastDirtyValue(wrapper)).toBe(false)
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
