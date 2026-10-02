import { shallowMount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SliderControl from './SliderControl.vue'

const global = {
  stubs: {
    Slider: {
      props: ['modelValue'],
      emits: ['update:modelValue'],
      template: '<button class="slider-stub" @click="$emit(\'update:modelValue\', 25)">{{ modelValue }}</button>'
    },
    InputNumber: {
      props: ['modelValue'],
      emits: ['update:modelValue'],
      template: '<input class="number-stub" :value="modelValue" @input="$emit(\'update:modelValue\', Number($event.target.value))" />'
    }
  }
}

describe('SliderControl', () => {
  it('emits updates from both inline controls', async () => {
    const wrapper = shallowMount(SliderControl, {
      props: { modelValue: 10, min: 0, max: 100 },
      global
    })

    expect(wrapper.get('.slider-track').exists()).toBe(true)
    await wrapper.get('.slider-stub').trigger('click')
    await wrapper.get('.number-stub').setValue('40')

    expect(wrapper.emitted('update:modelValue')).toEqual([[25], [40]])
  })
})
