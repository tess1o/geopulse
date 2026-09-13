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
      Select: { props: ['options'], template: '<div class="select-options">{{ options.map((option) => option.label).join(", ") }}</div>' },
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
    expect(wrapper.get('[data-setting-id="preferredMotorizedType"] .select-options').text()).toBe('Car, Public Transportation')
  })
})
