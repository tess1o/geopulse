import { mount } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { nextTick } from 'vue'
import TimelineSplitLayout from './TimelineSplitLayout.vue'

const setMatchMedia = (matches) => {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: vi.fn().mockImplementation((query) => ({
      matches,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn()
    }))
  })
}

const createPointerEvent = (type, { clientY, pointerType = 'touch', button = 0 } = {}) => {
  const event = new Event(type, { bubbles: true, cancelable: true })
  Object.defineProperty(event, 'clientY', {
    configurable: true,
    value: clientY
  })
  Object.defineProperty(event, 'pointerType', {
    configurable: true,
    value: pointerType
  })
  Object.defineProperty(event, 'button', {
    configurable: true,
    value: button
  })
  return event
}

describe('TimelineSplitLayout', () => {
  beforeEach(() => {
    Object.defineProperty(window, 'innerHeight', {
      configurable: true,
      value: 800
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('cycles between expanded and collapsed labels on desktop', async () => {
    setMatchMedia(false)
    const wrapper = mount(TimelineSplitLayout, {
      slots: {
        map: '<div>Map</div>',
        side: '<div>Timeline content</div>'
      }
    })

    expect(wrapper.text()).toContain('Movement Timeline')

    await wrapper.find('.timeline-sheet-toggle-button').trigger('click')
    await nextTick()

    expect(wrapper.text()).toContain('Timeline')
    expect(wrapper.find('.timeline-split-main').classes()).toContain('timeline-main--sheet-collapsed')
    expect(wrapper.emitted('layout-resize')).toBeTruthy()
  })

  it('renders side slot content directly inside the side pane', () => {
    setMatchMedia(false)
    const wrapper = mount(TimelineSplitLayout, {
      slots: {
        map: '<div>Map</div>',
        side: '<section class="timeline-container">Timeline content</section>'
      }
    })

    const pane = wrapper.find('.timeline-split-side-pane').element
    const directTimelineChild = Array.from(pane.children)
      .some((child) => child.classList.contains('timeline-container'))

    expect(directTimelineChild).toBe(true)
  })

  it('settles mobile drag to the nearest sheet state', async () => {
    setMatchMedia(true)
    const wrapper = mount(TimelineSplitLayout, {
      slots: {
        map: '<div>Map</div>',
        side: '<div>Timeline content</div>'
      },
      attachTo: document.body
    })

    vi.spyOn(wrapper.find('.timeline-split-side-pane').element, 'getBoundingClientRect')
      .mockReturnValue({
        width: 360,
        height: 400,
        top: 400,
        right: 360,
        bottom: 800,
        left: 0,
        x: 0,
        y: 400,
        toJSON: () => {}
      })

    wrapper.find('.timeline-sheet-handle').element.dispatchEvent(createPointerEvent('pointerdown', {
      clientY: 200
    }))
    window.dispatchEvent(createPointerEvent('pointermove', { clientY: 760 }))
    window.dispatchEvent(createPointerEvent('pointerup', { clientY: 760 }))
    await nextTick()

    expect(wrapper.find('.timeline-split-side-pane').classes()).toContain('timeline-sheet--compact')
    expect(wrapper.text()).toContain('Timeline')

    wrapper.unmount()
  })

  it('expands collapsed mobile sheet when tapping the handle', async () => {
    setMatchMedia(true)
    const wrapper = mount(TimelineSplitLayout, {
      props: {
        initialState: 'collapsed'
      },
      slots: {
        map: '<div>Map</div>',
        side: '<div>Timeline content</div>'
      },
      attachTo: document.body
    })

    const sidePane = wrapper.find('.timeline-split-side-pane')
    expect(sidePane.classes()).toContain('timeline-sheet--compact')

    wrapper.find('.timeline-sheet-handle').element.dispatchEvent(createPointerEvent('pointerdown', {
      clientY: 760
    }))
    window.dispatchEvent(createPointerEvent('pointerup', { clientY: 760 }))
    await nextTick()

    expect(sidePane.classes()).toContain('timeline-sheet--half')
    expect(sidePane.classes()).not.toContain('timeline-sheet--compact')
    expect(wrapper.emitted('layout-resize')).toBeTruthy()

    wrapper.unmount()
  })
})
