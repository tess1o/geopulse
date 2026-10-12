import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('maplibre-gl', () => ({}))
vi.mock('@/maps/vector/utils/maplibreLayerUtils', () => ({
  ensureGeoJsonSource: vi.fn(),
  ensureLayer: vi.fn(),
  hasMapLibreLayer: vi.fn(() => false),
  isMapLibreMap: vi.fn(() => true),
  nextLayerToken: vi.fn((prefix) => prefix),
  removeLayers: vi.fn(),
  removeSources: vi.fn(),
  setLayerVisibility: vi.fn()
}))
vi.mock('@/maps/vector/layers/vectorPathLayer/hoverController', () => ({
  createVectorPathHoverController: () => ({
    syncTripHoverContext: vi.fn(),
    clearTripHoverState: vi.fn()
  })
}))
vi.mock('@/maps/vector/layers/vectorPathLayer/replayController', () => ({
  createVectorPathReplayController: () => ({
    registerReplayUserCameraHandlers: vi.fn(),
    syncReplayFocusLayerVisibility: vi.fn(),
    syncReplayMarker: vi.fn(),
    cleanupReplay: vi.fn()
  })
}))
vi.mock('@/maps/vector/layers/vectorPathLayer/dataBuilders', () => ({
  buildPathCollection: () => ({ type: 'FeatureCollection', features: [] }),
  buildHighlightedData: () => ({
    lineCollection: { type: 'FeatureCollection', features: [] },
    fullLineCoordinates: [],
    endpointMarkers: [],
    hoverPathPoints: [],
    replayPathPoints: [
      { latitude: 50.45, longitude: 30.52, timestamp: '2026-01-01T10:00:00Z' },
      { latitude: 50.46, longitude: 30.53, timestamp: '2026-01-01T10:02:00Z' }
    ]
  })
}))

import VectorPathLayer from './VectorPathLayer.vue'

const trip = { id: 7, type: 'trip', tripDuration: 120 }

const createMap = () => ({
  on: vi.fn(),
  off: vi.fn(),
  getCanvas: () => ({ style: {} })
})

const replayEmissions = (wrapper) => (wrapper.emitted('highlighted-trip-replay-data') || []).map(([payload]) => payload)

describe('VectorPathLayer replay data', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('re-sends the replay path when the same trip is selected again', async () => {
    const wrapper = mount(VectorPathLayer, {
      props: { map: createMap(), highlightedTrip: trip, showHighlightedTripPopup: false }
    })
    expect(replayEmissions(wrapper).map((payload) => payload.points.length)).toEqual([2])

    await wrapper.setProps({ highlightedTrip: null })
    await wrapper.setProps({ highlightedTrip: trip })

    expect(replayEmissions(wrapper).map((payload) => payload.points.length)).toEqual([2, 0, 2])
    wrapper.unmount()
  })
})
