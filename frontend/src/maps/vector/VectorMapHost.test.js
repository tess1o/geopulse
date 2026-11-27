import { mount } from '@vue/test-utils'
import { markRaw } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import VectorMapHost from './VectorMapHost.vue'

vi.mock('maplibre-gl', () => ({ setWorkerUrl: vi.fn() }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({}) }))
vi.mock('@/maps/runtime/mapSourceResolver', () => ({ resolveVectorStyleSource: () => ({ styleUrl: '' }) }))

describe('vector zoom synchronization', () => {
  let wrapper

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllTimers()
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('skips the zoom echoed by moveend and still applies an external zoom change', async () => {
    vi.useFakeTimers()
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null)
    wrapper = mount(VectorMapHost, { props: { zoom: 10 } })
    const map = markRaw({ getZoom: () => 12.25, setZoom: vi.fn(), remove: vi.fn() })
    wrapper.vm.$.setupState.map = map

    await wrapper.setProps({ zoom: map.getZoom() })
    expect(map.setZoom).not.toHaveBeenCalled()
    await wrapper.setProps({ zoom: 14 })
    expect(map.setZoom).toHaveBeenCalledExactlyOnceWith(14)
  })
})
