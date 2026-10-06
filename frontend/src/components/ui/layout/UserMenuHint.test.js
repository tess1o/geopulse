import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'

const { storage } = vi.hoisted(() => {
  const storage = new Map()
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: {
      getItem: (key) => (storage.has(key) ? storage.get(key) : null),
      setItem: (key, value) => storage.set(key, String(value)),
      removeItem: (key) => storage.delete(key),
      clear: () => storage.clear()
    }
  })
  Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: () => 'blob:test' })
  return { storage }
})

vi.mock('@/router', () => ({ default: { push: vi.fn() } }))

const STORAGE_KEY = 'geopulse-hint-user-menu-2.0'
const anchor = { getBoundingClientRect: () => ({ bottom: 50, right: 1000, width: 36 }) }

describe('UserMenuHint', () => {
  let UserMenuHint
  let pinia

  beforeEach(async () => {
    storage.clear()
    pinia = createPinia()
    setActivePinia(pinia)
    // Created long before the 2.0 menu change: this person knows the old side menu.
    useAuthStore().$patch({ user: { fullName: 'Test User', createdAt: '2024-01-01T00:00:00Z' } })
    UserMenuHint = (await import('./UserMenuHint.vue')).default
  })

  // The hint decides to show in onMounted, so it renders one tick after mounting.
  const mountHint = async () => {
    const wrapper = mount(UserMenuHint, {
      props: { anchor },
      global: { plugins: [pinia], stubs: { teleport: true } }
    })
    await flushPromises()
    return wrapper
  }

  it('points existing users at the avatar menu', async () => {
    const wrapper = await mountHint()

    const hint = wrapper.find('.gp-user-menu-hint')
    expect(hint.exists()).toBe(true)
    expect(hint.text()).toContain('Profile, timeline preferences, theme, language and logout have moved here.')
    expect(hint.attributes('style')).toContain('top: 62px')
  })

  it('stays hidden once dismissed', async () => {
    storage.set(STORAGE_KEY, '1')

    expect((await mountHint()).find('.gp-user-menu-hint').exists()).toBe(false)
  })

  it('skips brand-new accounts, which never saw the old menu', async () => {
    useAuthStore().$patch({ user: { fullName: 'New User', createdAt: new Date().toISOString() } })

    expect((await mountHint()).find('.gp-user-menu-hint').exists()).toBe(false)
  })

  it('hides and remembers the dismissal on "Got it"', async () => {
    const wrapper = await mountHint()

    await wrapper.find('.gp-user-menu-hint button').trigger('click')

    expect(wrapper.find('.gp-user-menu-hint').exists()).toBe(false)
    expect(storage.get(STORAGE_KEY)).toBe('1')
  })

  it('remembers the dismissal when the parent dismisses it by opening the menu', async () => {
    const wrapper = await mountHint()

    wrapper.vm.dismiss()
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.gp-user-menu-hint').exists()).toBe(false)
    expect(storage.get(STORAGE_KEY)).toBe('1')
  })
})
