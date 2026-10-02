vi.hoisted(() => {
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: {
      getItem: vi.fn(),
      setItem: vi.fn(),
      removeItem: vi.fn(),
      clear: vi.fn()
    }
  })
})

import { shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import TripMapMatchingDetailsDialog from './TripMapMatchingDetailsDialog.vue'

const routerPush = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush })
}))

const mountDialog = (props = {}) => shallowMount(TripMapMatchingDetailsDialog, {
  props: {
    visible: true,
    trip: {
      id: 42,
      type: 'trip',
      timestamp: '2026-09-16T04:45:00Z',
      tripDuration: 900,
      movementType: 'CAR'
    },
    ...props
  },
  global: {
    stubs: {
      Dialog: {
        name: 'Dialog',
        props: ['visible', 'header'],
        template: '<div v-if="visible"><h3>{{ header }}</h3><slot /><slot name="footer" /></div>'
      },
      Button: {
        props: ['label'],
        emits: ['click'],
        template: '<button @click="$emit(\'click\')">{{ label }}</button>'
      },
      Tag: { props: ['value'], template: '<span class="tag">{{ value }}</span>' }
    }
  }
})

const givenAdmin = (canViewAdmin) => {
  const auth = useAuthStore()
  auth.user = { role: canViewAdmin ? 'ADMIN' : 'USER' }
}

describe('TripMapMatchingDetailsDialog', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    routerPush.mockReset()
  })

  it('confirms a refined route and when it was refined', () => {
    const wrapper = mountDialog({
      info: { status: 'COMPLETED', targetId: 11, completedAt: '2026-09-16T20:51:32Z' }
    })

    expect(wrapper.text()).toContain('Map Matching Details')
    expect(wrapper.find('.tag').text()).toBe('MATCHED')
    expect(wrapper.text()).toContain('This route was refined with map matching.')
    expect(wrapper.text()).toContain('Refined on')
    expect(wrapper.text()).not.toContain('Open map matching settings')
    expect(wrapper.text()).not.toContain('An administrator can re-run')
  })

  it('reports a trip that is still queued', () => {
    const wrapper = mountDialog({ info: { status: 'QUEUED', targetId: 12 } })

    expect(wrapper.find('.tag').text()).toBe('QUEUED')
    expect(wrapper.text()).toContain('This trip is queued for map matching.')
  })

  it('explains a failed match and offers the underlying server message', () => {
    const wrapper = mountDialog({
      info: {
        status: 'FAILED',
        targetId: 11,
        error: 'Valhalla trace_route failed with HTTP 400: {"error_code":443}'
      }
    })

    expect(wrapper.find('.tag').text()).toBe('FAILED')
    expect(wrapper.text()).toContain('The routing engine could not find a road or path for this trip.')
    expect(wrapper.find('code').text()).toContain('error_code')
    expect(wrapper.text()).not.toContain('before it reached the map-matching queue')
  })

  it('flags a trip that was skipped before it was ever queued', () => {
    const wrapper = mountDialog({
      info: {
        status: 'SKIPPED',
        targetId: null,
        error: 'Trip has fewer than two eligible GPS points'
      }
    })

    expect(wrapper.find('.tag').text()).toBe('SKIPPED')
    expect(wrapper.text()).toContain('Trip has fewer than two eligible GPS points')
    expect(wrapper.text()).toContain('before it reached the map-matching queue')
  })

  it('offers administrators a shortcut to the settings that re-run matching', async () => {
    givenAdmin(true)
    const wrapper = mountDialog({
      info: { status: 'FAILED', targetId: 11, completedAt: '2026-09-11T13:45:19Z', error: 'boom' }
    })

    const settingsButton = wrapper.findAll('button')
      .find(button => button.text() === 'Open map matching settings')
    expect(settingsButton).toBeTruthy()
    expect(wrapper.text()).toContain('Last attempted on')

    await settingsButton.trigger('click')

    expect(wrapper.emitted('close')).toBeTruthy()
    expect(routerPush).toHaveBeenCalledWith({ path: '/app/admin/settings', query: { tab: 'map-matching' } })
  })

  it('keeps the plain sentence for users who cannot open the admin panel', () => {
    givenAdmin(false)
    const wrapper = mountDialog({ info: { status: 'FAILED', targetId: 11, error: 'boom' } })

    expect(wrapper.findAll('button').find(button => button.text() === 'Open map matching settings')).toBeUndefined()
    expect(wrapper.text()).toContain('An administrator can re-run map matching')
  })

  it('closes when the dialog is dismissed', async () => {
    const wrapper = mountDialog({ info: { status: 'FAILED', targetId: 11, error: 'boom' } })

    await wrapper.findComponent({ name: 'Dialog' }).vm.$emit('update:visible', false)
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('close')).toBeTruthy()
  })
})
