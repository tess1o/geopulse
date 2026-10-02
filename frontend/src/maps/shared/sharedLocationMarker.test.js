import {flushPromises, mount} from '@vue/test-utils'
import RasterSharedLocationMarker from '@/maps/raster/markers/RasterSharedLocationMarker.vue'
import VectorSharedLocationMarker from '@/maps/vector/markers/VectorSharedLocationMarker.vue'

const mocks = vi.hoisted(() => {
  const chain = () => {
    const value = {}
    value.addTo = vi.fn(() => value)
    value.bindPopup = vi.fn(() => value)
    value.openPopup = vi.fn(() => value)
    value.isPopupOpen = vi.fn(() => false)
    value.getElement = vi.fn(() => null)
    value.setLngLat = vi.fn(() => value)
    value.setPopup = vi.fn(() => value)
    value.togglePopup = vi.fn(() => value)
    value.remove = vi.fn()
    return value
  }

  return {
    rasterMarker: chain(),
    vectorMarker: chain(),
    popup: {
      isOpen: vi.fn(() => false),
      setDOMContent: vi.fn(function () { return this })
    },
    popupMount: {element: document.createElement('div'), unmount: vi.fn()}
  }
})

vi.mock('leaflet', () => ({
  default: {
    circleMarker: vi.fn(() => mocks.rasterMarker),
    marker: vi.fn(() => mocks.rasterMarker),
    divIcon: vi.fn(() => ({}))
  }
}))
vi.mock('maplibre-gl', () => ({
  default: {
    Marker: function () { return mocks.vectorMarker },
    Popup: function () { return mocks.popup }
  }
}))
vi.mock('@/composables/useTimezone', () => ({useTimezone: () => ({})}))
vi.mock('@/maps/vector/utils/maplibreLayerUtils', () => ({isMapLibreMap: () => true}))
vi.mock('@/maps/shared/popups/mountMapPopup', () => ({mountMapPopup: () => mocks.popupMount}))
vi.mock('@/maps/shared/popups/locationPopupModels', () => ({buildSharedLocationPopupModel: () => ({})}))

const props = {
  map: {removeLayer: vi.fn()},
  latitude: 50,
  longitude: 30,
  shareData: {shareName: 'Alice'},
  openPopup: true
}

describe.each([
  ['raster', RasterSharedLocationMarker, () => mocks.rasterMarker.openPopup],
  ['vector', VectorSharedLocationMarker, () => mocks.vectorMarker.togglePopup]
])('%s shared location marker', (_, component, popupOpenMock) => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.rasterMarker.isPopupOpen.mockReturnValue(false)
    mocks.popup.isOpen.mockReturnValue(false)
  })

  it('keeps a closed popup closed after refresh', async () => {
    const wrapper = mount(component, {props})
    expect(popupOpenMock()).toHaveBeenCalledTimes(1)

    await wrapper.setProps({shareData: {shareName: 'Alice', sharedAt: 'later'}})
    await flushPromises()

    expect(popupOpenMock()).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('keeps an open popup open after refresh', async () => {
    const wrapper = mount(component, {props})
    mocks.rasterMarker.isPopupOpen.mockReturnValue(true)
    mocks.popup.isOpen.mockReturnValue(true)

    await wrapper.setProps({shareData: {shareName: 'Alice', sharedAt: 'later'}})
    await flushPromises()

    expect(popupOpenMock()).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })
})
