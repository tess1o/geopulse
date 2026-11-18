import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import Strip from './GpsTimeStrip.vue'
import { buildGpsTimePickerPoints } from '@/utils/gpsTimePicker'

vi.mock('@/composables/useTimezone', () => ({
  useTimezone: () => ({ formatTime: value => value.slice(11, 19) })
}))

// 10 points every 5 s, then 6 min 17 s with nothing, then 2 more: a bar 400 px wide covering 0-427 s.
const seconds = [0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 422, 427]
const at = value => ({ latitude: 1, longitude: 2, timestamp: new Date(Date.UTC(2026, 10, 10, 10, 43, 0) + value * 1000).toISOString() })
const points = buildGpsTimePickerPoints(seconds.map(at))
const xFor = value => (value / 427) * 400
const mountStrip = props => {
  const wrapper = mount(Strip, { props: { points, range: { from: null, to: null }, ...props }, attachTo: document.body })
  wrapper.find('.gps-strip').element.getBoundingClientRect = () => ({ left: 0, width: 400 })
  return wrapper
}
const percent = element => Number.parseFloat(element.style.left)
// jsdom has no PointerEvent, and test-utils cannot set clientX on a plain Event.
const pointer = async (wrapper, type, clientX) => {
  wrapper.element.dispatchEvent(new MouseEvent(type, { clientX, bubbles: true }))
  await nextTick()
}

describe('GpsTimeStrip', () => {
  it('places points by real time, so a pause is an empty stretch with its length', () => {
    const wrapper = mountStrip()
    const ticks = wrapper.findAll('.gps-strip-tick').map(tick => percent(tick.element))
    expect(ticks[9]).toBeCloseTo(45 / 427 * 100)
    expect(ticks[10]).toBeCloseTo(422 / 427 * 100)
    const gap = wrapper.find('.gps-strip-gap')
    expect(gap.text()).toBe('6 min 17 s')
    expect(percent(gap.element)).toBeCloseTo(45 / 427 * 100)
  })

  it('shades From to To and shows unset handles at the ends', async () => {
    const wrapper = mountStrip()
    expect(wrapper.find('.gps-strip-selection').exists()).toBe(false)
    expect(wrapper.findAll('.gps-strip-handle.is-unset').map(handle => percent(handle.element))).toEqual([0, 100])
    expect(wrapper.find('.gps-strip-handle--from').attributes('aria-valuetext')).toBe('Not chosen')
    await wrapper.setProps({ range: { from: 9, to: 10 }, startTime: points[9].time, endTime: points[10].time })
    const selection = wrapper.find('.gps-strip-selection').element
    expect(percent(selection)).toBeCloseTo(45 / 427 * 100)
    expect(Number.parseFloat(selection.style.width)).toBeCloseTo(377 / 427 * 100)
    expect(wrapper.find('.gps-strip-handle--to').attributes('aria-valuetext')).toBe('10:50:02')
  })

  it('drags a handle snapping to points and never past the other handle', async () => {
    const wrapper = mountStrip({ range: { from: 2, to: 9 }, startTime: points[2].time, endTime: points[9].time })
    const strip = wrapper.find('.gps-strip')
    await pointer(wrapper.find('.gps-strip-handle--to'), 'pointerdown', 0)
    await pointer(strip, 'pointermove', xFor(420))
    // Dragging To left of From stops at the point just after From.
    await pointer(strip, 'pointermove', xFor(1))
    window.dispatchEvent(new Event('pointerup'))
    expect(wrapper.emitted('update:range')).toEqual([[{ from: 2, to: 10 }], [{ from: 2, to: 3 }]])
    expect(wrapper.emitted('inspect')).toBeUndefined()
    wrapper.unmount()
  })

  it('selects and hovers the nearest point when the bar is clicked or pointed at', async () => {
    const wrapper = mountStrip()
    const strip = wrapper.find('.gps-strip')
    await pointer(strip, 'pointermove', xFor(300))
    await pointer(strip, 'pointerdown', xFor(16))
    await pointer(strip, 'pointerleave', 0)
    expect(wrapper.emitted('hover')).toEqual([[10], [null]])
    expect(wrapper.emitted('inspect')).toEqual([[3]])
    wrapper.unmount()
  })

  it('moves a handle point by point with the keyboard', async () => {
    const wrapper = mountStrip({ range: { from: 2, to: 4 }, startTime: points[2].time, endTime: points[4].time })
    const from = wrapper.find('.gps-strip-handle--from')
    await from.trigger('keydown', { key: 'ArrowLeft' })
    await from.trigger('keydown', { key: 'End' })
    await wrapper.find('.gps-strip-handle--to').trigger('keydown', { key: 'Home' })
    expect(wrapper.emitted('update:range')).toEqual([[{ from: 1, to: 4 }], [{ from: 3, to: 4 }], [{ from: 2, to: 3 }]])
    wrapper.unmount()
  })
  it('dims hidden points and only selects points that are shown', async () => {
    const wrapper = mountStrip({ visible: { from: 9, to: 11 } })
    expect(wrapper.findAll('.gps-strip-tick.is-hidden')).toHaveLength(9)
    await pointer(wrapper.find('.gps-strip'), 'pointerdown', xFor(5))
    expect(wrapper.emitted('inspect')).toEqual([[9]])
    wrapper.unmount()
  })
  it('marks the times of other activities as faint bands', () => {
    const wrapper = mountStrip({ otherRanges: [{ start: points[0].time, end: points[9].time }, { start: points[11].time + 60000, end: points[11].time + 120000 }] })
    const bands = wrapper.findAll('.gps-strip-other')
    expect(bands).toHaveLength(1)
    expect(Number.parseFloat(bands[0].element.style.width)).toBeCloseTo(45 / 427 * 100)
  })
})
