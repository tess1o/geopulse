import { flushPromises, shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { markRaw, nextTick, watch } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import apiService from '@/utils/apiService'
import { useCoverageStore } from '@/stores/coverage'
import CoverageExplorerPage from './CoverageExplorerPage.vue'

const mocks = vi.hoisted(() => ({ map: null }))
vi.mock('@/components/maps', () => ({
  MapContainer: {
    name: 'MapContainer',
    props: ['center', 'zoom'],
    setup: () => ({ map: mocks.map }),
    template: '<div><slot name="overlays" :map="map" :isReady="true" /></div>'
  },
  CoverageLayer: { name: 'CoverageLayer', props: ['map', 'cells', 'gridMeters', 'visible'], template: '<div />' }
}))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ demoReadOnly: false }) }))
vi.mock('@/stores/location', () => ({ useLocationStore: () => ({ getLastKnownPosition: async () => null }) }))
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: vi.fn() }) }))
vi.mock('@/utils/apiService', () => ({ default: { get: vi.fn() } }))
vi.mock('@/components/ui/layout/AppLayout.vue', () => ({ default: { template: '<div><slot /></div>' } }))

const createMap = (zoom = 15, engine = 'RASTER') => {
  const handlers = {}
  return markRaw({
    __gpEngineMode: engine,
    zoom,
    west: 1,
    getZoom() { return this.zoom },
    getBounds() {
      return { getWest: () => this.west, getEast: () => 3, getSouth: () => 2, getNorth: () => 4 }
    },
    on: vi.fn((event, handler) => { handlers[event] = handler }),
    off: vi.fn(),
    move() { handlers.moveend() }
  })
}

describe('coverage view requests', () => {
  let wrapper
  let currentStatus
  let respondWithCells
  const cellsRequests = () => apiService.get.mock.calls.filter(([url]) => url === '/coverage/cells')
  const summaryRequests = () => apiService.get.mock.calls.filter(([url]) => url === '/coverage/summary')
  const statusRequests = () => apiService.get.mock.calls.filter(([url]) => url === '/coverage/status')

  const openPage = async (zoom = 15, engine = 'RASTER') => {
    mocks.map = createMap(zoom, engine)
    wrapper = shallowMount(CoverageExplorerPage, {
      global: {
        stubs: {
          AppLayout: { template: '<div><slot /></div>' },
          PageContainer: { template: '<div><slot /></div>' },
          MapContainer: false
        },
        directives: { tooltip: () => {} }
      }
    })
    await flushPromises()
    wrapper.findComponent({ name: 'MapContainer' }).vm.$emit('map-ready', mocks.map)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(350)
    await flushPromises()
  }

  beforeEach(() => {
    vi.useFakeTimers()
    vi.resetAllMocks()
    setActivePinia(createPinia())
    currentStatus = { userEnabled: true, processing: false, hasCells: true, lastProcessed: '2026-11-01T00:00:00Z' }
    respondWithCells = grid => ({ gridMeters: grid, truncated: false, cells: [[1, 2, 3]] })
    apiService.get.mockImplementation(async (url, params) => {
      if (url === '/coverage/status') return { ...currentStatus }
      if (url === '/coverage/summary') return { areaSquareKm: 1 }
      if (url === '/coverage/cells') return respondWithCells(params.grid)
      throw new Error(`Unexpected API request: ${url}`)
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.useRealTimers()
  })

  it('steps through coarser auto grids for the same view and starts fresh after zooming in', async () => {
    respondWithCells = grid => ({ gridMeters: grid, truncated: grid < 250, cells: [[1, 2, 3]] })
    await openPage()
    expect(cellsRequests().map(([, params]) => params)).toEqual([
      { bbox: '1,2,3,4', grid: 20 },
      { bbox: '1,2,3,4', grid: 50 },
      { bbox: '1,2,3,4', grid: 250 }
    ])
    expect(wrapper.findComponent({ name: 'CoverageLayer' }).props('gridMeters')).toBe(250)

    mocks.map.zoom = 16
    mocks.map.west = 1.5
    mocks.map.move()
    await vi.advanceTimersByTimeAsync(350)
    expect(cellsRequests().slice(3).map(([, params]) => params.grid)).toEqual([20, 50, 250])
  })

  it('stops auto coarsening at 40000 metres even if the response remains truncated', async () => {
    respondWithCells = grid => ({ gridMeters: grid, truncated: true, cells: [[1, 2, 3]] })
    await openPage()
    expect(cellsRequests().map(([, params]) => params.grid)).toEqual([20, 50, 250, 1000, 5000, 20000, 40000])
    expect(useCoverageStore().cellsTruncated).toBe(true)
  })

  it('commits only the final auto-grid response while preserving drawn cells during retries', async () => {
    const store = useCoverageStore()
    const previousCells = Object.freeze([[9, 9, 9]])
    store.cells = previousCells
    store.cellsGrid = 1000
    const committed = vi.fn()
    const stop = watch(() => store.cells, committed, { flush: 'sync' })
    let finishRequest
    respondWithCells = grid => grid < 250
      ? { gridMeters: grid, truncated: true, cells: [[grid, 2, 3]] }
      : new Promise(resolve => { finishRequest = resolve })
    await openPage()

    expect(cellsRequests().map(([, params]) => params.grid)).toEqual([20, 50, 250])
    expect(committed).not.toHaveBeenCalled()
    expect(store.cells).toBe(previousCells)
    expect(wrapper.findComponent({ name: 'CoverageLayer' }).props('gridMeters')).toBe(1000)
    const finalCells = [[250, 2, 3]]
    finishRequest({ gridMeters: 250, truncated: false, cells: finalCells })
    await flushPromises()
    expect(committed).toHaveBeenCalledTimes(1)
    expect(store.cells).toBe(finalCells)
    expect(wrapper.findComponent({ name: 'CoverageLayer' }).props('gridMeters')).toBe(250)
    stop()
  })

  it.each([['RASTER', 15], ['VECTOR', 14]])('remembers a stepped-up grid for pans at the same %s zoom', async (engine, zoom) => {
    respondWithCells = grid => ({ gridMeters: grid, truncated: grid < 250, cells: [[1, 2, 3]] })
    await openPage(zoom, engine)
    mocks.map.move()
    await vi.advanceTimersByTimeAsync(350)
    expect(cellsRequests()).toHaveLength(3)

    respondWithCells = grid => ({ gridMeters: grid, truncated: false, cells: [[1, 2, 3]] })
    mocks.map.west = 1.5
    mocks.map.move()
    await vi.advanceTimersByTimeAsync(350)
    expect(cellsRequests().map(([, params]) => params.grid)).toEqual([20, 50, 250, 250])

    // A zoom change within the same grid threshold must still reset the remembered step-up.
    mocks.map.zoom += 1
    mocks.map.move()
    await vi.advanceTimersByTimeAsync(350)
    expect(cellsRequests().at(-1)[1].grid).toBe(20)
  })

  it('resets the remembered auto grid when the grid selection changes', async () => {
    respondWithCells = grid => ({ gridMeters: grid, truncated: grid < 250, cells: [[1, 2, 3]] })
    await openPage()
    const dropdown = wrapper.findComponent({ name: 'Dropdown' })
    dropdown.vm.$emit('update:modelValue', 50)
    await nextTick()
    await vi.advanceTimersByTimeAsync(350)
    respondWithCells = grid => ({ gridMeters: grid, truncated: false, cells: [[1, 2, 3]] })
    dropdown.vm.$emit('update:modelValue', 'auto')
    await nextTick()
    await vi.advanceTimersByTimeAsync(350)
    expect(cellsRequests().map(([, params]) => params.grid)).toEqual([20, 50, 250, 50, 20])
  })

  it('shows a hint for a truncated manual grid without coarsening it', async () => {
    await openPage()
    respondWithCells = grid => ({ gridMeters: grid, truncated: true, cells: [[1, 2, 3]] })
    wrapper.findComponent({ name: 'Dropdown' }).vm.$emit('update:modelValue', 50)
    await nextTick()
    await vi.advanceTimersByTimeAsync(350)
    expect(cellsRequests().map(([, params]) => params.grid)).toEqual([20, 50])
    expect(wrapper.find('.truncation-hint').text()).toBe('Zoom in to see all cells')
  })

  it.each([['RASTER', 15], ['VECTOR', 14]])('uses the same auto grid for equivalent %s views', async (engine, zoom) => {
    await openPage(zoom, engine)
    expect(cellsRequests().map(([, params]) => params.grid)).toEqual([20])
  })

  it('keeps the fetched grid on old cells while a zoom request is pending', async () => {
    await openPage()
    let finishRequest
    respondWithCells = grid => new Promise(resolve => { finishRequest = () => resolve({ gridMeters: grid, truncated: false, cells: [[4, 5, 6]] }) })
    mocks.map.zoom = 10
    mocks.map.move()
    await nextTick()
    expect(wrapper.findComponent({ name: 'CoverageLayer' }).props('gridMeters')).toBe(20)
    await vi.advanceTimersByTimeAsync(350)
    expect(wrapper.findComponent({ name: 'CoverageLayer' }).props('gridMeters')).toBe(20)
    finishRequest()
    await flushPromises()
    expect(wrapper.findComponent({ name: 'CoverageLayer' }).props('gridMeters')).toBe(1000)
  })

  it('abandons truncation retries when the viewport changes during the request', async () => {
    await openPage()
    let finishRequest
    respondWithCells = grid => new Promise(resolve => { finishRequest = () => resolve({ gridMeters: grid, truncated: true, cells: [[4, 5, 6]] }) })
    mocks.map.west = 1.1
    mocks.map.move()
    await vi.advanceTimersByTimeAsync(350)
    mocks.map.west = 1.2
    mocks.map.move()
    finishRequest()
    await flushPromises()
    expect(cellsRequests().map(([, params]) => params.grid)).toEqual([20, 20])
    expect(useCoverageStore().cells).toEqual([[1, 2, 3]])
  })

  it('discards a final response when its viewport is no longer current', async () => {
    await openPage()
    let finishRequest
    respondWithCells = grid => new Promise(resolve => { finishRequest = () => resolve({ gridMeters: grid, truncated: false, cells: [[4, 5, 6]] }) })
    mocks.map.west = 1.1
    mocks.map.move()
    await vi.advanceTimersByTimeAsync(350)
    mocks.map.west = 1.2
    mocks.map.move()
    finishRequest()
    await flushPromises()
    expect(useCoverageStore().cells).toEqual([[1, 2, 3]])
  })

  it('polls every minute while idle and every five seconds during processing', async () => {
    await openPage()
    expect(statusRequests()).toHaveLength(1)
    await vi.advanceTimersByTimeAsync(59000)
    expect(statusRequests()).toHaveLength(1)
    await vi.advanceTimersByTimeAsync(1000)
    expect(statusRequests()).toHaveLength(2)

    currentStatus.processing = true
    useCoverageStore().status = { ...currentStatus }
    await nextTick()
    await vi.advanceTimersByTimeAsync(4999)
    expect(statusRequests()).toHaveLength(2)
    await vi.advanceTimersByTimeAsync(1)
    expect(statusRequests()).toHaveLength(3)

    currentStatus.processing = false
    useCoverageStore().status = { ...currentStatus }
    await nextTick()
    await vi.advanceTimersByTimeAsync(59999)
    expect(statusRequests()).toHaveLength(3)
    await vi.advanceTimersByTimeAsync(1)
    expect(statusRequests()).toHaveLength(4)
  })

  it('refreshes the summary only when lastProcessed changes, including after processing completes', async () => {
    await openPage()
    expect(summaryRequests()).toHaveLength(1)
    await vi.advanceTimersByTimeAsync(60000)
    expect(summaryRequests()).toHaveLength(1)

    currentStatus.lastProcessed = '2026-11-02T00:00:00Z'
    await vi.advanceTimersByTimeAsync(60000)
    expect(summaryRequests()).toHaveLength(2)
    currentStatus.processing = true
    await vi.advanceTimersByTimeAsync(60000)
    expect(summaryRequests()).toHaveLength(2)
    currentStatus.processing = false
    currentStatus.lastProcessed = '2026-11-03T00:00:00Z'
    await vi.advanceTimersByTimeAsync(5000)
    expect(summaryRequests()).toHaveLength(3)
    await vi.advanceTimersByTimeAsync(60000)
    expect(summaryRequests()).toHaveLength(3)
  })

  it('loads the summary when returning to the page with an already-enabled store', async () => {
    useCoverageStore().status = { ...currentStatus }
    await openPage()
    expect(summaryRequests()).toHaveLength(1)
    expect(wrapper.text()).toContain('1 km')
  })
})
