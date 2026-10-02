import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ToastMessageContent from './ToastMessageContent.vue'

// Custom toast templates must render PrimeVue's default content markup, or they lose the icon, spacing and
// severity colours that Aura attaches to these class names.
describe('ToastMessageContent', () => {
  it('renders the icon, summary, detail and extra lines like a default PrimeVue toast', () => {
    const wrapper = mount(ToastMessageContent, {
      props: { message: { severity: 'success', summary: 'Saved', detail: 'All good' } },
      slots: { default: '<a class="gp-toast-action">Open</a>' }
    })

    expect(wrapper.find('svg.p-toast-message-icon').exists()).toBe(true)
    const text = wrapper.find('.p-toast-message-text')
    expect(text.find('.p-toast-summary').text()).toBe('Saved')
    expect(text.find('.p-toast-detail').text()).toBe('All good')
    expect(text.find('.gp-toast-action').text()).toBe('Open')
  })

  it('leaves the icon empty and skips the detail when PrimeVue would', () => {
    const wrapper = mount(ToastMessageContent, {
      props: { message: { severity: 'secondary', summary: 'Note' } }
    })

    expect(wrapper.find('span.p-toast-message-icon').exists()).toBe(true)
    expect(wrapper.find('svg').exists()).toBe(false)
    expect(wrapper.find('.p-toast-detail').exists()).toBe(false)
  })
})
