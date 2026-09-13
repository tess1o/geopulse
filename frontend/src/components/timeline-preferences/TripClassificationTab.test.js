import { shallowMount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TripClassificationTab from './TripClassificationTab.vue'

const mountTab = (modelValue) => shallowMount(TripClassificationTab, {
  props: {
    modelValue,
    getWarningMessagesForType: () => ({ value: [] })
  },
  global: {
    stubs: {
      PreferencesTabLayout: { template: '<main><slot /></main>' },
      TransportTypeCard: { template: '<section><slot name="parameters" /></section>' },
      SettingCard: { template: '<section><slot name="control" /></section>' },
      Card: { template: '<section><slot name="content" /></section>' },
      Select: {
        props: ['modelValue', 'options', 'optionLabel', 'optionValue'],
        emits: ['update:modelValue'],
        template: `
          <select class="select-options" :value="modelValue" @change="$emit('update:modelValue', $event.target.value)">
            <option v-for="option in options" :key="option[optionValue]" :value="option[optionValue]">
              {{ option[optionLabel] }}
            </option>
          </select>
        `
      },
      SliderControl: true,
      ToggleSwitch: true,
      Message: true,
      Button: true
    }
  }
})

describe('TripClassificationTab', () => {
  it('offers Public Transportation as an enabled motor-vehicle default label', () => {
    const wrapper = mountTab({
      carEnabled: true,
      motorcycleEnabled: false,
      publicTransportationEnabled: true,
      preferredMotorizedType: 'PUBLIC_TRANSPORT'
    })

    expect(wrapper.get('[data-setting-id="publicTransportationEnabled"]').text()).toContain('Public Transportation Label')
    expect(wrapper.get('[data-setting-id="preferredMotorizedType"]').text()).toContain('Public Transportation')
    expect(wrapper.get('[data-setting-id="preferredMotorizedType"] .select-options').findAll('option').map((option) => option.text())).toEqual([
      'Car',
      'Public Transportation'
    ])
    expect(wrapper.findAll('.control-value')).toHaveLength(0)
  })

  it('renders one preferred-label select and emits its selected value', async () => {
    const wrapper = mountTab({
      carEnabled: true,
      motorcycleEnabled: false,
      publicTransportationEnabled: true,
      preferredMotorizedType: 'PUBLIC_TRANSPORT'
    })

    const select = wrapper.get('[data-setting-id="preferredMotorizedType"] .select-options')
    expect(wrapper.findAll('[data-setting-id="preferredMotorizedType"] .select-options')).toHaveLength(1)

    await select.setValue('CAR')
    expect(wrapper.emitted('update:modelValue').at(-1)[0]).toMatchObject({
      preferredMotorizedType: 'CAR'
    })
  })
})
