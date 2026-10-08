import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { defineComponent, nextTick, ref } from 'vue'
import PrimeVue from 'primevue/config'
import AutoComplete from 'primevue/autocomplete'
import Dialog from 'primevue/dialog'
import { autocompleteEscapePassThrough } from './autocompleteEscape'

const flush = async () => {
  await nextTick()
  await new Promise(resolve => setTimeout(resolve, 0))
  await nextTick()
}

const Host = defineComponent({
  components: { AutoComplete, Dialog },
  setup() {
    // Opened after mount, like real dialogs: Dialog binds its Escape listener in the enter transition.
    const visible = ref(false)
    const value = ref('')
    const suggestions = ref([])
    const complete = () => {
      suggestions.value = ['alpha@example.com', 'beta@example.com']
    }
    return { visible, value, suggestions, complete }
  },
  template: `
    <Dialog v-model:visible="visible" modal header="Invite">
      <AutoComplete v-model="value" :suggestions="suggestions" :delay="0" input-id="friend-email" @complete="complete" />
    </Dialog>
  `
})

const mountHost = () => mount(Host, {
  attachTo: document.body,
  global: {
    plugins: [[PrimeVue, { pt: { autocomplete: autocompleteEscapePassThrough } }]],
    // Test utils stub <transition> by default; Dialog binds its Escape listener in the enter hook.
    stubs: { transition: false }
  }
})

const pressEscape = (element) => {
  element.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true }))
}

describe('AutoComplete Escape inside a Dialog', () => {
  let wrapper

  afterEach(() => {
    wrapper?.unmount()
    document.body.innerHTML = ''
  })

  it('closes the dialog on Escape when no suggestion list is open', async () => {
    wrapper = mountHost()
    wrapper.vm.visible = true
    await flush()

    pressEscape(document.getElementById('friend-email'))
    await flush()
    expect(wrapper.vm.visible).toBe(false)
  })

  it('closes the suggestion list first and the dialog only on the next Escape', async () => {
    wrapper = mountHost()
    wrapper.vm.visible = true
    await flush()

    const input = document.getElementById('friend-email')
    input.value = 'a'
    input.dispatchEvent(new Event('input', { bubbles: true }))
    await flush()
    expect(input.getAttribute('aria-expanded')).toBe('true')

    pressEscape(input)
    await flush()
    expect(input.getAttribute('aria-expanded')).toBe('false')
    expect(wrapper.vm.visible).toBe(true)

    pressEscape(input)
    await flush()
    expect(wrapper.vm.visible).toBe(false)
  })
})
