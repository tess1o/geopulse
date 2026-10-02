import { shallowMount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import LocationDetailsHeader from './LocationDetailsHeader.vue'

const Button = {
  props: ['label'],
  emits: ['click'],
  template: '<button @click="$emit(\'click\')">{{ label }}</button>'
}

describe('LocationDetailsHeader', () => {
  it('renders identity slots and emits back', async () => {
    const wrapper = shallowMount(LocationDetailsHeader, {
      props: { title: 'Kyiv', subtitle: 'City insights', icon: 'pi pi-building' },
      slots: {
        icon: '<span class="custom-icon">K</span>',
        metadata: '<span>132 visits</span>',
        actions: '<button>Edit</button>'
      },
      global: { stubs: { Button } }
    })

    expect(wrapper.text()).toContain('Kyiv')
    expect(wrapper.text()).toContain('City insights')
    expect(wrapper.text()).toContain('132 visits')
    expect(wrapper.text()).toContain('Edit')
    expect(wrapper.get('.custom-icon').text()).toBe('K')

    await wrapper.get('.back-button').trigger('click')
    expect(wrapper.emitted('back')).toHaveLength(1)
  })
})
