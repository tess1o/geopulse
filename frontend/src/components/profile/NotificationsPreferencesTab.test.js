import { flushPromises, mount } from '@vue/test-utils'
import NotificationChannelSettings from './NotificationChannelSettings.vue'
import NotificationsPreferencesTab from './NotificationsPreferencesTab.vue'

const api = vi.hoisted(() => ({
  get: vi.fn(),
  put: vi.fn()
}))

vi.mock('@/utils/apiService', () => ({ default: api }))

const CardStub = { template: '<section><slot name="content" /></section>' }
const ButtonStub = {
  props: ['label', 'disabled', 'loading'],
  template: '<button :disabled="disabled || loading">{{ label }}</button>'
}
const SettingCardStub = {
  props: ['title', 'description', 'settingId'],
  template: '<section :data-setting-id="settingId"><h4>{{ title }}</h4><p>{{ description }}</p><slot name="control" /></section>'
}
const InputSwitchStub = {
  props: ['modelValue', 'disabled'],
  emits: ['update:modelValue'],
  template: '<input type="checkbox" :checked="modelValue" :disabled="disabled" @change="$emit(\'update:modelValue\', $event.target.checked)" />'
}
const InputNumberStub = {
  props: ['modelValue', 'disabled'],
  emits: ['update:modelValue'],
  template: '<input type="number" :value="modelValue" :disabled="disabled" @input="$emit(\'update:modelValue\', Number($event.target.value))" />'
}
const TextInputStub = {
  props: ['modelValue', 'disabled'],
  emits: ['update:modelValue'],
  template: '<input :value="modelValue" :disabled="disabled" @input="$emit(\'update:modelValue\', $event.target.value)" />'
}
const DropdownStub = {
  props: ['modelValue', 'options', 'optionLabel', 'optionValue', 'disabled'],
  emits: ['update:modelValue'],
  template: `
    <select :value="modelValue" :disabled="disabled" @change="$emit('update:modelValue', $event.target.value)">
      <option v-for="option in options" :key="option[optionValue]" :value="option[optionValue]">{{ option[optionLabel] }}</option>
    </select>
  `
}
const ChannelSettingsStub = {
  props: ['modelValue', 'label', 'readOnly'],
  template: '<div class="channel-settings-stub" role="group" :aria-label="label" />'
}

const preferences = {
  gpsHealthEnabled: false,
  gpsSilenceMinutes: 45,
  gpsHealth: { inAppEnabled: true, appriseEnabled: false, routingMode: 'URLS', destination: '', appriseConfigKey: '', appriseTag: '' },
  rewindEnabled: false,
  rewind: { inAppEnabled: true, appriseEnabled: false, routingMode: 'URLS', destination: '', appriseConfigKey: '', appriseTag: '' },
  whatsNewEnabled: true
}

const mountPreferences = (props = {}) => mount(NotificationsPreferencesTab, {
  props,
  global: {
    stubs: {
      Card: CardStub,
      Button: ButtonStub,
      SettingCard: SettingCardStub,
      InputSwitch: InputSwitchStub,
      InputNumber: InputNumberStub,
      ChannelSettings: ChannelSettingsStub
    }
  }
})

describe('NotificationsPreferencesTab', () => {
  beforeEach(() => {
    api.get.mockReset().mockResolvedValue({ data: structuredClone(preferences) })
    api.put.mockReset().mockImplementation(async (_url, value) => ({ data: JSON.parse(JSON.stringify(value)) }))
  })

  it('loads grouped settings, reveals dependent rows, and saves the existing payload', async () => {
    const wrapper = mountPreferences()
    await flushPromises()

    expect(api.get).toHaveBeenCalledWith('/notifications/preferences')
    expect(wrapper.text()).toContain('GPS health')
    expect(wrapper.text()).toContain('Monthly Rewind')
    expect(wrapper.text()).toContain('Product updates')
    expect(wrapper.findAll('.settings-panel')).toHaveLength(3)
    expect(wrapper.find('[data-setting-id="gpsSilenceMinutes"]').exists()).toBe(false)

    await wrapper.find('#gps-health').setValue(true)
    await wrapper.find('#rewind').setValue(true)

    expect(wrapper.find('[data-setting-id="gpsSilenceMinutes"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="GPS health delivery"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="Rewind delivery"]').exists()).toBe(true)

    await wrapper.find('form').trigger('submit')
    expect(api.put).toHaveBeenCalledWith('/notifications/preferences', expect.objectContaining({
      gpsHealthEnabled: true,
      rewindEnabled: true,
      whatsNewEnabled: true
    }))
    expect(wrapper.emitted('saved')).toHaveLength(1)
  })

  it('keeps notification controls read-only', async () => {
    const wrapper = mountPreferences({ readOnly: true })
    await flushPromises()

    expect(wrapper.findAll('input[type="checkbox"]').every((input) => input.attributes('disabled') !== undefined)).toBe(true)
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
    await wrapper.find('form').trigger('submit')
    expect(api.put).not.toHaveBeenCalled()
  })
})

describe('NotificationChannelSettings', () => {
  const mountChannels = (modelValue) => mount(NotificationChannelSettings, {
    props: { modelValue, label: 'Delivery', readOnly: false },
    global: {
      stubs: {
        SettingCard: SettingCardStub,
        InputSwitch: InputSwitchStub,
        Dropdown: DropdownStub,
        Textarea: TextInputStub,
        InputText: TextInputStub
      }
    }
  })

  it('preserves URL and config-key routing modes', async () => {
    const model = structuredClone(preferences.gpsHealth)
    const wrapper = mountChannels(model)

    expect(wrapper.get('[role="group"]').attributes('aria-label')).toBe('Delivery')
    expect(wrapper.text()).not.toContain('Delivery')
    expect(wrapper.text()).not.toContain('Apprise routing')
    await wrapper.findAll('input[type="checkbox"]')[1].setValue(true)
    expect(wrapper.emitted('update:modelValue').at(-1)[0]).toEqual({ ...model, appriseEnabled: true })

    await wrapper.setProps({ modelValue: { ...model, appriseEnabled: true } })
    expect(wrapper.text()).toContain('Destination URLs')
    await wrapper.find('select').setValue('KEY_TAG')
    expect(wrapper.emitted('update:modelValue').at(-1)[0]).toMatchObject({ appriseEnabled: true, routingMode: 'KEY_TAG' })

    await wrapper.setProps({ modelValue: { ...model, appriseEnabled: true, routingMode: 'KEY_TAG' } })
    expect(wrapper.text()).toContain('Configuration key')
    expect(wrapper.text()).toContain('Configuration tag')
    expect(wrapper.text()).not.toContain('Destination URLs')
  })
})
