import { shallowMount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MapControls from './MapControls.vue'

const mountControls = (props = {}) => shallowMount(MapControls, {
  props: { map: {}, ...props },
  global: {
    stubs: {
      Menu: {
        name: 'Menu',
        props: ['model'],
        template: '<div />'
      }
    }
  }
})

const moreItems = (wrapper) => wrapper.findComponent({ name: 'Menu' }).props('model')

describe('MapControls mobile More menu', () => {
  it('toggles Panoramax when MapLibre support is available', async () => {
    const wrapper = mountControls({ showPanoramaxControl: true, panoramaxSupported: true })

    await wrapper.find('[title="Show Panoramax coverage"]').trigger('click')

    expect(wrapper.emitted('toggle-panoramax')).toEqual([[true]])
  })

  it('keeps Panoramax disabled in raster mode', () => {
    const wrapper = mountControls({ showPanoramaxControl: true, panoramaxSupported: false })

    expect(wrapper.find('[title="Panoramax coverage requires MapLibre vector maps"]').attributes('disabled')).toBeDefined()
  })

  it('toggles 3D buildings from the button and More menu', async () => {
    const wrapper = mountControls({ show3dBuildingsControl: true })

    await wrapper.find('[title="Show 3D buildings"]').trigger('click')
    moreItems(wrapper).find((item) => item.label === 'Show 3D buildings').command()

    expect(wrapper.emitted('toggle-3d-buildings')).toEqual([[true], [true]])
  })

  it('highlights More when a hidden control is active', () => {
    const wrapper = mountControls({ show3dBuildingsControl: true, buildings3dEnabled: true })

    expect(wrapper.find('.more-controls-trigger').classes()).toContain('active')
  })

  it('runs hidden control actions from More', () => {
    const wrapper = mountControls({
      showRouteDisplayModeControl: true,
      showPath: true,
      showHeatmap: true,
      showNotesButton: true,
      showWeatherButton: true
    })
    const items = moreItems(wrapper)

    items.find((item) => item.label.startsWith('Showing matched route')).command()
    items.find((item) => item.label === 'Heatmap: Trips').command()
    items.find((item) => item.label === 'Show Notes').command()
    items.find((item) => item.label === 'Show Weather').command()

    expect(wrapper.emitted('cycle-route-display-mode')).toEqual([[]])
    expect(wrapper.emitted('heatmap-layer-change')).toEqual([['trips']])
    expect(wrapper.emitted('toggle-heatmap')).toEqual([[true]])
    expect(wrapper.emitted('toggle-notes')).toEqual([[true]])
    expect(wrapper.emitted('toggle-weather')).toEqual([[true]])
  })
})
