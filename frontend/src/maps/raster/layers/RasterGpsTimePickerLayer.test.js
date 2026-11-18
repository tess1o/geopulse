import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { buildGpsTimePickerPoints } from '@/utils/gpsTimePicker'

const mocks = vi.hoisted(() => ({ circles: [], lines: [], markers: [] }))
vi.mock('leaflet', () => {
  const layer = (store, extra) => {
    const item = { handlers: {}, removed: false, ...extra,
      on(event, callback) { this.handlers[event] = callback; return this },
      addTo() { return this }, remove() { this.removed = true }, bringToFront: vi.fn(),
      setStyle(style) { Object.assign(this.options, style); return this } }
    store.push(item)
    return item
  }
  return { default: {
    canvas: options => ({ options, remove: vi.fn() }),
    DomEvent: { stopPropagation: vi.fn() },
    divIcon: options => options,
    circleMarker: (coordinates, options) => layer(mocks.circles, { coordinates, options: { ...options } }),
    polyline: (coordinates, options) => layer(mocks.lines, { coordinates, options }),
    marker: (coordinates, options) => layer(mocks.markers, { coordinates, options })
  } }
})

const { default: Layer } = await import('./RasterGpsTimePickerLayer.vue')

const at = (seconds, latitude) => ({ latitude, longitude: 1, timestamp: new Date(Date.UTC(2026, 10, 10, 10, 0, seconds)).toISOString() })
const points = buildGpsTimePickerPoints([at(0, 1), at(5, 2), at(10, 3), at(400, 4)])
const createMap = () => {
  const panes = {}
  return {
    getPane: name => panes[name], createPane: name => { panes[name] = { style: {} }; return panes[name] },
    on: vi.fn(), off: vi.fn(),
    getBounds: () => ({ pad() { return this }, contains: () => true }),
    latLngToContainerPoint: ([latitude]) => ({ x: latitude * 100, y: 0 })
  }
}
const live = list => list.filter(item => !item.removed)
const pins = () => live(mocks.markers).filter(marker => marker.options.icon.className === 'gps-picker-pin-icon')
const labels = () => live(mocks.markers).filter(marker => marker.options.icon.className === 'gps-picker-label-icon')

beforeEach(() => { mocks.circles.length = 0; mocks.lines.length = 0; mocks.markers.length = 0 })

describe('RasterGpsTimePickerLayer', () => {
  it('draws every sample on its own, numbered, joined by the track with the gap dashed', () => {
    mount(Layer, { props: { map: createMap(), points } })
    expect(live(mocks.circles).map(circle => circle.coordinates)).toEqual([[1, 1], [2, 1], [3, 1], [4, 1]])
    expect(live(mocks.circles).map(circle => circle.options.fillColor)).toEqual(points.map(point => point.color))
    const tracks = live(mocks.lines)
    expect(tracks.filter(line => !line.options.dashArray).map(line => line.coordinates)).toEqual([[[1, 1], [2, 1], [3, 1]]])
    expect(tracks.filter(line => line.options.dashArray).map(line => line.coordinates)).toEqual([[[3, 1], [4, 1]]])
    expect(labels().map(marker => marker.options.icon.html)).toEqual(points.map(point => expect.stringContaining(`>${point.number}</span>`)))
  })

  it('reports clicks and hover on a sample by its index', () => {
    const wrapper = mount(Layer, { props: { map: createMap(), points } })
    const map = wrapper.props('map')
    const dot = live(mocks.circles)[2]
    dot.handlers.click({}); dot.handlers.mouseover(); dot.handlers.mouseout()
    expect(wrapper.emitted('inspect')).toEqual([[2]])
    expect(wrapper.emitted('hover')).toEqual([[2], [null]])
    // A click on an empty part of the map deselects the point.
    map.on.mock.calls.find(([event]) => event === 'click')[1]()
    expect(wrapper.emitted('inspect')).toEqual([[2], [null]])
    wrapper.unmount()
    expect(map.off).toHaveBeenCalledWith('click', expect.any(Function))
  })

  it('a click where dots overlap picks the sample recorded nearest to the click, not the one drawn last', () => {
    // Two samples a few pixels apart, as a drive leaving a place records them at a low zoom (#675, E2E-13).
    const close = buildGpsTimePickerPoints([at(0, 1), at(60, 1.05), at(120, 3)])
    const map = { ...createMap(), mouseEventToContainerPoint: event => event.at }
    const wrapper = mount(Layer, { props: { map, points: close } })
    const [first, second] = live(mocks.circles)
    // Leaflet hands the click to the dot drawn last and, for a small circle marker, reports the dot's own centre as
    // the event's containerPoint; only the original mouse event says where the click landed.
    second.handlers.click({ containerPoint: { x: 105, y: 0 }, originalEvent: { at: { x: 100, y: 0 } } })
    first.handlers.click({ containerPoint: { x: 100, y: 0 }, originalEvent: { at: { x: 105, y: 0 } } })
    expect(wrapper.emitted('inspect')).toEqual([[0], [1]])
  })

  it('pins From and To, highlights the range and dims samples outside it', async () => {
    const wrapper = mount(Layer, { props: { map: createMap(), points, pinLabels: { from: 'From', to: 'To' } } })
    await wrapper.setProps({ range: { from: 0, to: 2 }, hoveredIndex: 3 })
    await nextTick()
    expect(pins().map(marker => [marker.coordinates, marker.options.icon.html.match(/>([^<]+)<\/span>/)[1]])).toEqual([[[1, 1], 'From · 1'], [[3, 1], 'To · 3']])
    expect(live(mocks.lines).find(line => line.options.color === '#2563eb').coordinates).toEqual([[1, 1], [2, 1], [3, 1]])
    const circles = live(mocks.circles)
    expect(circles.slice(0, 3).map(circle => circle.options.fillOpacity)).toEqual([0.95, 0.95, 0.95])
    // The hovered sample stays fully visible and enlarged even outside the range.
    expect([circles[3].options.fillOpacity, circles[3].options.radius]).toEqual([0.95, 8])
    pins()[1].handlers.click()
    expect(wrapper.emitted('inspect')).toEqual([[2]])
    await wrapper.setProps({ hoveredIndex: null })
    expect(live(mocks.circles)[3].options.fillOpacity).toBe(0.3)
  })
})
