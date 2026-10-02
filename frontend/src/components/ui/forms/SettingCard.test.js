import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SettingCard from './SettingCard.vue'

const tooltip = {
  beforeMount(element, binding) {
    element.dataset.tooltip = binding.value.value
  }
}

describe('SettingCard', () => {
  it('keeps details in an accessible info tooltip', () => {
    const wrapper = mount(SettingCard, {
      props: {
        title: 'Detection radius',
        description: 'Groups nearby GPS points.',
        details: {
          'Lower values': 'More precise',
          'Higher values': 'More forgiving'
        }
      },
      global: { directives: { tooltip } }
    })

    const help = wrapper.get('.setting-help')
    expect(wrapper.find('details').exists()).toBe(false)
    expect(help.attributes('aria-label')).toContain('Lower values: More precise')
    expect(help.attributes('data-tooltip')).toBe('Lower values: More precise\nHigher values: More forgiving')
  })
})
