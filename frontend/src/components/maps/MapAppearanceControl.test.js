import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.hoisted(() => {
  const storage = new Map()
  const shim = {
    getItem: (key) => storage.get(key) || null,
    setItem: (key, value) => storage.set(key, String(value)),
    removeItem: (key) => storage.delete(key),
    clear: () => storage.clear()
  }
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: shim })
  Object.defineProperty(window, 'localStorage', { configurable: true, value: shim })
})

vi.mock('@/utils/apiService', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }))

import MapAppearanceControl from './MapAppearanceControl.vue'
import { useMapAppearance, useSharedMapAppearance } from '@/composables/useMapAppearance'
import { useAuthStore } from '@/stores/auth'

const optionValues = (wrapper) => wrapper.findAll('[role="radio"]')
  .map((option) => option.attributes('data-testid').replace('map-appearance-option-', ''))

const openMenu = async (wrapper) => wrapper.find('[data-testid="map-appearance-button"]').trigger('click')

describe('MapAppearanceControl', () => {
  let shared

  beforeEach(() => {
    setActivePinia(createPinia())
    window.localStorage.clear()
    shared = useSharedMapAppearance()
    shared.setChoice('')
    window.localStorage.clear()
  })

  it('offers a guest the owner look and the presets, with the owner look selected', async () => {
    shared.setSharedOwner({ colorScheme: 'RED_GREEN_SAFE' }, 'Anna')
    const wrapper = mount(MapAppearanceControl)
    await openMenu(wrapper)

    expect(optionValues(wrapper)).toEqual(['OWNER', 'DEFAULT', 'RED_GREEN_SAFE', 'BLUE_YELLOW_SAFE', 'HIGH_CONTRAST'])
    expect(wrapper.text()).toContain('As shared by Anna')
    expect(wrapper.find('[data-testid="map-appearance-option-OWNER"]').attributes('aria-checked')).toBe('true')
    expect(useMapAppearance().value.colorScheme).toBe('RED_GREEN_SAFE')
  })

  it('hides the owner row when the owner uses the default look', async () => {
    shared.setSharedOwner({}, 'Anna')
    const wrapper = mount(MapAppearanceControl)
    await openMenu(wrapper)

    expect(optionValues(wrapper)).toEqual(['DEFAULT', 'RED_GREEN_SAFE', 'BLUE_YELLOW_SAFE', 'HIGH_CONTRAST'])
    expect(wrapper.find('[data-testid="map-appearance-option-DEFAULT"]').attributes('aria-checked')).toBe('true')
  })

  it('applies and remembers a picked preset', async () => {
    shared.setSharedOwner({ colorScheme: 'RED_GREEN_SAFE' }, 'Anna')
    const wrapper = mount(MapAppearanceControl)
    await openMenu(wrapper)

    await wrapper.find('[data-testid="map-appearance-option-HIGH_CONTRAST"]').trigger('click')

    expect(useMapAppearance().value.colorScheme).toBe('HIGH_CONTRAST')
    expect(window.localStorage.getItem('gp-shared-map-colors')).toBe('HIGH_CONTRAST')
    expect(wrapper.find('[data-testid="map-appearance-panel"]').exists()).toBe(false)
  })

  it('offers "My settings" to a signed-in viewer and defaults to it when they customized their profile', async () => {
    useAuthStore().setUser({ id: 'viewer-1', colorScheme: 'BLUE_YELLOW_SAFE' })
    shared.setSharedOwner({ colorScheme: 'RED_GREEN_SAFE' }, 'Anna')
    const wrapper = mount(MapAppearanceControl)
    await openMenu(wrapper)

    expect(optionValues(wrapper).slice(0, 2)).toEqual(['OWNER', 'MINE'])
    expect(wrapper.find('[data-testid="map-appearance-option-MINE"]').attributes('aria-checked')).toBe('true')
    expect(useMapAppearance().value.colorScheme).toBe('BLUE_YELLOW_SAFE')
  })

  it('recognizes a signed-in viewer from the cached profile on public shared pages', async () => {
    window.localStorage.setItem('userInfo', JSON.stringify({ id: 'viewer-2', colorScheme: 'HIGH_CONTRAST' }))
    shared.setSharedOwner({ colorScheme: 'RED_GREEN_SAFE' }, 'Anna')
    const wrapper = mount(MapAppearanceControl)
    await openMenu(wrapper)

    expect(optionValues(wrapper)).toContain('MINE')
    expect(useMapAppearance().value.colorScheme).toBe('HIGH_CONTRAST')
  })

  it('leaves regular pages on the signed-in user\'s own appearance', () => {
    useAuthStore().setUser({ id: 'viewer-1', colorScheme: 'BLUE_YELLOW_SAFE' })
    shared.setSharedOwner({ colorScheme: 'RED_GREEN_SAFE' }, 'Anna')
    shared.setChoice('HIGH_CONTRAST')
    shared.clearSharedOwner()

    expect(useMapAppearance().value.colorScheme).toBe('BLUE_YELLOW_SAFE')
  })
})
