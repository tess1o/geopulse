import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MAP_RENDER_MODES, markMapEngineMode } from '@/maps/contracts/mapContracts'
import CinematicRewindDialog from './CinematicRewindDialog.vue'

const { fetchYearlyReplay, fetchMonthlyReplay, layerUtils, imageService } = vi.hoisted(() => ({
  fetchYearlyReplay: vi.fn(),
  fetchMonthlyReplay: vi.fn(),
  layerUtils: {
    ensureGeoJsonSource: vi.fn(),
    ensureLayer: vi.fn(),
    removeLayers: vi.fn(),
    removeSources: vi.fn(),
    getMapLibreSource: vi.fn(() => ({ setData: vi.fn() }))
  },
  imageService: {
    loadAuthenticatedImage: vi.fn(async (url) => {
      if (url.includes('unreachable')) throw new Error('Immich unavailable')
      return `blob:${url}`
    }),
    revokeBlobUrl: vi.fn()
  }
}))

vi.mock('@/stores/digest', () => ({ useDigestStore: () => ({ fetchYearlyReplay, fetchMonthlyReplay }) }))
vi.mock('@/utils/imageService', () => ({ imageService }))
vi.mock('@/utils/calculationsHelpers', () => ({ formatDistanceRounded: (meters) => `${meters} m` }))
const { appearance, profileStyle } = vi.hoisted(() => ({
  appearance: {
    defaultPathColor: '#1f2937',
    activePathColor: '#dc2626',
    pathWidth: 5,
    highlightedPathWidth: 7,
    outlineEnabled: false
  },
  profileStyle: { version: 8, sources: {}, layers: [] }
}))
vi.mock('@/composables/useMapAppearance', async () => {
  const { ref } = await import('vue')
  return { useMapAppearance: () => ref(appearance) }
})
vi.mock('@/maps/runtime/mapSourceResolver', () => ({ resolveProfileMapLibreStyle: () => profileStyle }))
vi.mock('@/composables/useTimezone', () => ({
  useTimezone: () => ({
    format: (value, pattern) => {
      if (pattern === 'YYYY-MM') return String(value).slice(0, 7)
      if (pattern === 'YYYY-MM-DD') return String(value).slice(0, 10)
      const longMonth = new Date(value).toLocaleString('en-US', { month: 'long', timeZone: 'UTC' })
      if (pattern === 'D MMMM') return `${new Date(value).getUTCDate()} ${longMonth}`
      if (pattern === 'MMMM YYYY') return `${longMonth} ${String(value).slice(0, 4)}`
      const month = { MMMM: 'long', MMM: 'short' }[pattern]
      return month ? new Date(value).toLocaleString('en-US', { month, timeZone: 'UTC' }) : String(value)
    },
    formatDateDisplay: (value) => String(value)
  })
}))
vi.mock('@/maps/vector/utils/maplibreLayerUtils', async (importOriginal) => ({
  ...(await importOriginal()),
  ...layerUtils
}))
vi.mock('@/components/maps/BaseMap.vue', () => ({
  default: {
    name: 'BaseMap',
    props: ['mapId', 'center', 'zoom', 'height', 'width', 'mapRenderMode', 'styleOverride', 'enableFullscreen', 'showScale', 'options'],
    emits: ['map-ready', 'map-warning'],
    template: '<div class="base-map-stub" />'
  }
}))
vi.mock('@/components/dialogs/PhotoViewerDialog.vue', () => ({
  default: {
    name: 'PhotoViewerDialog',
    props: ['visible', 'photos', 'initialPhotoIndex', 'allowShowOnMap', 'preloadedBlobUrlResolver'],
    template: '<div v-if="visible" class="photo-viewer-stub" />'
  }
}))
vi.mock('primevue/dialog', () => ({
  default: {
    name: 'Dialog',
    props: ['visible'],
    emits: ['update:visible'],
    template: '<div v-if="visible" class="dialog-stub"><slot /></div>'
  }
}))
vi.mock('primevue/button', () => ({
  default: { props: ['label'], emits: ['click'], template: '<button @click="$emit(\'click\')">{{ label }}</button>' }
}))
vi.mock('primevue/progressspinner', () => ({ default: { template: '<div class="spinner" />' } }))

const trip = (month, extra = {}) => ({
  id: month,
  startTime: `2026-${String(month).padStart(2, '0')}-05T10:00:00Z`,
  endTime: `2026-${String(month).padStart(2, '0')}-05T11:00:00Z`,
  tripDuration: 3600,
  distanceMeters: 1000 * month,
  movementType: 'CAR',
  path: [
    { latitude: 50, longitude: 30, timestamp: `2026-${String(month).padStart(2, '0')}-05T10:00:00Z` },
    { latitude: 49, longitude: 24, timestamp: `2026-${String(month).padStart(2, '0')}-05T11:00:00Z` }
  ],
  ...extra
})

// Taken during the trip of that month (10:00–11:00).
const photo = (id, month) => ({
  id,
  takenAt: `2026-${String(month).padStart(2, '0')}-05T10:30:00Z`,
  thumbnailUrl: `/integrations/immich/photos/${id}/thumbnail`,
  previewUrl: `/integrations/immich/photos/${id}/preview`,
  width: 1200,
  height: 800
})

const createMap = ({ mode = MAP_RENDER_MODES.VECTOR, globe = true } = {}) => {
  const handlers = {}
  const map = {
    handlers,
    tilesLoaded: true,
    jumpTo: vi.fn(),
    getContainer: () => ({ clientWidth: 1200, clientHeight: 800 }),
    areTilesLoaded: vi.fn(() => map.tilesLoaded),
    setSky: vi.fn(),
    on: vi.fn((event, handler) => { (handlers[event] ||= []).push(handler) }),
    once: vi.fn((event, handler) => { (handlers[event] ||= []).push(handler) }),
    off: vi.fn((event, handler) => { handlers[event] = (handlers[event] || []).filter((item) => item !== handler) }),
    fire: (event) => [...(handlers[event] || [])].forEach((handler) => handler()),
    setProjection: vi.fn(() => { if (!globe) throw new Error('Globe projection is unavailable') }),
    getProjection: vi.fn(() => ({ type: globe ? 'globe' : 'mercator' })),
    easeTo: vi.fn(),
    fitBounds: vi.fn()
  }
  markMapEngineMode(map, mode)
  return map
}

let frames
const runFrame = (timestamp) => {
  const pending = frames
  frames = []
  pending.forEach((callback) => callback(timestamp))
}

const digest = {
  metrics: { totalDistance: 25_000, tripCount: 4, citiesVisited: 2, activeDays: 5 },
  topPlaces: []
}

const openPlayer = async ({
  trips = [trip(1)], photos = [], map = createMap(), reducedMotion = false, month = null, topPlaces = []
} = {}) => {
  fetchYearlyReplay.mockResolvedValue({ year: 2026, trips })
  fetchMonthlyReplay.mockResolvedValue({ year: 2026, month, trips })
  window.matchMedia = vi.fn(() => ({ matches: reducedMotion }))
  const wrapper = mount(CinematicRewindDialog, {
    props: { visible: true, year: 2026, month, digest: { ...digest, topPlaces }, photosPayload: { photos, settled: true } },
    attachTo: document.body
  })
  await flushPromises()
  wrapper.findComponent({ name: 'BaseMap' }).vm.$emit('map-ready', map)
  await flushPromises()
  map.fire('idle')
  await flushPromises()
  return { wrapper, map }
}

const playButton = (wrapper) => wrapper.findAll('.cinematic-controls button')[2]
const seekTo = async (wrapper, value) => {
  await wrapper.find('.cinematic-controls input[type="range"]').setValue(String(value))
  await flushPromises()
}

describe('CinematicRewindDialog', () => {
  beforeEach(() => {
    frames = []
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
      frames.push(callback)
      return frames.length
    })
    vi.spyOn(window, 'cancelAnimationFrame').mockImplementation(() => {})
    vi.stubGlobal('Image', class {
      decode() {
        return this.src.includes('undecodable') ? Promise.reject(new Error('corrupt')) : Promise.resolve()
      }
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
    document.body.innerHTML = ''
  })

  it('plays map-only without Immich on a globe, then leaves nothing running after close', async () => {
    const { wrapper, map } = await openPlayer()

    expect(map.setProjection).toHaveBeenCalledWith({ type: 'globe' })
    expect(wrapper.find('.cinematic-notice').exists()).toBe(false)
    expect(playButton(wrapper).attributes('aria-label')).toBe('Pause')

    runFrame(0)
    runFrame(1_200)
    await flushPromises()
    expect(wrapper.find('.cinematic-controls span').text()).toBe('00:01')
    expect(wrapper.find('.cinematic-photo').exists()).toBe(false)

    await wrapper.find('.cinematic-close').trigger('click')

    expect(wrapper.emitted('update:visible')).toContainEqual([false])
    expect(window.cancelAnimationFrame).toHaveBeenCalled()
    expect(map.handlers['style.load']).toEqual([])
    expect(map.handlers.resize).toEqual([])
    expect(map.handlers.idle).toEqual([])
    expect(layerUtils.removeLayers).toHaveBeenCalledWith(map, expect.arrayContaining([expect.stringContaining('gp-cinematic')]))
    expect(layerUtils.removeSources).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('plays the trip\'s photos one after another while travelling, collecting them in a strip', async () => {
    // Photos taken at the destination (49, 24) in the hours after the 11:00 arrival.
    const atDestination = (id, hour) => ({ ...photo(id, 1), takenAt: `2026-01-05T${hour}:00:00Z`, latitude: 49, longitude: 24 })
    const { wrapper } = await openPlayer({
      photos: [atDestination('a', 12), atDestination('b', 13), atDestination('c', 14), atDestination('d', 15)]
    })
    const current = () => wrapper.find('.cinematic-reel-current img').attributes('src')
    const strip = () => wrapper.findAll('.cinematic-reel-strip li')

    // Four photos earn one more second of travel: the trip runs 6000–14000 ms of an 18000 ms film, flying in
    // until 7500 ms and travelling until 12000 ms, so each photo has 1125 ms.
    expect(wrapper.findAll('.cinematic-controls span')[1].text()).toBe('00:18')
    await seekTo(wrapper, Math.round((7_000 / 18_000) * 1000))
    expect(wrapper.find('.cinematic-reel').exists()).toBe(false)

    await seekTo(wrapper, Math.round((8_000 / 18_000) * 1000))
    expect(current()).toBe('blob:/integrations/immich/photos/a/preview')
    expect(strip()).toHaveLength(1)

    await seekTo(wrapper, Math.round((10_100 / 18_000) * 1000))
    expect(current()).toBe('blob:/integrations/immich/photos/c/preview')
    expect(strip()).toHaveLength(3)
    expect(strip()[2].classes()).toContain('current')
    expect(wrapper.find('.cinematic-reel-strip').attributes('aria-label')).toBe('Photo 3 of 4')

    // On the finished route the last photo stays, with all four in the strip.
    await seekTo(wrapper, Math.round((13_000 / 18_000) * 1000))
    expect(current()).toBe('blob:/integrations/immich/photos/d/preview')
    expect(strip()).toHaveLength(4)

    await seekTo(wrapper, Math.round((15_000 / 18_000) * 1000))
    expect(wrapper.find('.cinematic-reel').exists()).toBe(false)
    wrapper.unmount()
  })

  it('shows only a trip\'s own photos and skips photos that fail to load or decode', async () => {
    const { wrapper } = await openPlayer({
      trips: [trip(1), trip(2), trip(3)],
      photos: [photo('january', 1), photo('undecodable', 2), photo('unreachable', 3), photo('no-trip', 7)]
    })

    // Three 7000 ms trips from 6000 ms in a 31000 ms film.
    await seekTo(wrapper, Math.round((9_000 / 31_000) * 1000))
    expect(wrapper.find('.cinematic-reel-current img').attributes('src')).toBe('blob:/integrations/immich/photos/january/preview')
    expect(wrapper.find('.cinematic-reel-strip').exists()).toBe(false) // a single photo needs no strip

    await seekTo(wrapper, Math.round((16_000 / 31_000) * 1000))
    expect(wrapper.find('.cinematic-reel').exists()).toBe(false)
    await seekTo(wrapper, Math.round((23_000 / 31_000) * 1000))
    expect(wrapper.find('.cinematic-reel').exists()).toBe(false)

    expect(imageService.loadAuthenticatedImage).not.toHaveBeenCalledWith(expect.stringContaining('no-trip'))
    expect(imageService.revokeBlobUrl).toHaveBeenCalledWith('blob:/integrations/immich/photos/undecodable/preview')

    await wrapper.find('.cinematic-close').trigger('click')
    expect(imageService.revokeBlobUrl).toHaveBeenCalledWith('blob:/integrations/immich/photos/january/preview')
    wrapper.unmount()
  })

  it('starts at once and holds a scene only until Immich serves its photos or the preload limit passes', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    try {
      imageService.loadAuthenticatedImage.mockImplementationOnce(() => new Promise(() => {}))
      const { wrapper } = await openPlayer({ photos: [photo('stalled', 1)] })
      const clock = () => wrapper.find('.cinematic-controls span').text()
      expect(playButton(wrapper).attributes('aria-label')).toBe('Pause')

      // Into the trip, 7004 ms into a 17000 ms film: its photo is still loading, so the clock holds.
      await seekTo(wrapper, Math.round((7_000 / 17_000) * 1000))
      runFrame(0)
      runFrame(1_000)
      await flushPromises()
      expect(clock()).toBe('00:07')
      expect(wrapper.find('.cinematic-buffering').text()).toBe('Loading photos…')

      vi.advanceTimersByTime(8_000)
      await flushPromises()
      runFrame(2_000)
      await flushPromises()

      expect(clock()).toBe('00:08')
      expect(wrapper.find('.cinematic-buffering').exists()).toBe(false)
      expect(wrapper.find('.cinematic-reel').exists()).toBe(false)
      wrapper.unmount()
    } finally {
      vi.useRealTimers()
    }
  })

  it('draws on the user\'s own timeline tiles with their Appearance path colours and width', async () => {
    layerUtils.ensureLayer.mockClear()
    const { wrapper } = await openPlayer()
    const paintOf = (suffix) => layerUtils.ensureLayer.mock.calls.map(([, layer]) => layer).findLast((layer) => layer.id.endsWith(suffix))?.paint

    expect(wrapper.findComponent({ name: 'BaseMap' }).props('styleOverride')).toBe(profileStyle)
    expect(paintOf('-all-layer')).toMatchObject({ 'line-color': '#1f2937', 'line-width': 5 })
    expect(paintOf('-progress-layer')).toMatchObject({ 'line-color': '#dc2626', 'line-width': 7 })
    expect(paintOf('-marker-layer')).toMatchObject({ 'circle-stroke-color': '#dc2626' })
    expect(layerUtils.ensureLayer.mock.calls.some(([, layer]) => layer.id.endsWith('-all-outline-layer'))).toBe(false)
    wrapper.unmount()
  })

  it('adds the contrasting outline when the Appearance outline setting is on', async () => {
    appearance.outlineEnabled = true
    try {
      const { wrapper } = await openPlayer()
      const paintOf = (suffix) => layerUtils.ensureLayer.mock.calls.map(([, layer]) => layer).findLast((layer) => layer.id.endsWith(suffix))?.paint

      // Dark lines get a white casing, three pixels wider, as on the timeline map.
      expect(paintOf('-all-outline-layer')).toMatchObject({ 'line-color': '#ffffff', 'line-width': 8 })
      expect(paintOf('-progress-glow-layer')).toMatchObject({ 'line-color': '#ffffff', 'line-width': 10 })
      wrapper.unmount()
    } finally {
      appearance.outlineEnabled = false
    }
  })

  it('keeps playing in Mercator when the globe projection is unavailable, including after a style reload', async () => {
    const { wrapper, map } = await openPlayer({ map: createMap({ globe: false }) })

    expect(wrapper.find('.cinematic-notice').text()).toContain('using the flat map')
    expect(wrapper.find('.cinematic-controls').exists()).toBe(true)
    expect(playButton(wrapper).attributes('aria-label')).toBe('Pause')

    map.fire('style.load')
    expect(map.setProjection).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('shows the unsupported state when the vector map falls back to raster or fails', async () => {
    const raster = await openPlayer({ map: createMap({ mode: MAP_RENDER_MODES.RASTER }) })
    expect(raster.wrapper.text()).toContain('3D Rewind is not supported on this device.')
    expect(raster.wrapper.find('.cinematic-controls').exists()).toBe(false)
    expect(raster.map.setProjection).not.toHaveBeenCalled()
    raster.wrapper.unmount()

    fetchYearlyReplay.mockResolvedValue({ year: 2026, trips: [trip(1)] })
    const failed = mount(CinematicRewindDialog, {
      props: { visible: true, year: 2026, digest, photosPayload: { photos: [], settled: true } }
    })
    await flushPromises()
    failed.findComponent({ name: 'BaseMap' }).vm.$emit('map-warning', { code: 'vector_engine_init_failed' })
    await flushPromises()
    expect(failed.text()).toContain('3D Rewind is not supported on this device.')
    failed.unmount()
  })

  it('drives the camera from the film clock: a screen-filling globe, then a pitched chase of the trip', async () => {
    const { wrapper, map } = await openPlayer()
    const lastCamera = () => map.jumpTo.mock.lastCall[0]

    expect(map.setSky).toHaveBeenCalled()
    const [startLatitude, startLongitude] = wrapper.findComponent({ name: 'BaseMap' }).props('center')
    expect(startLatitude).toBeCloseTo(49.5, 0) // opens over the trip, not at a default spot
    expect(startLongitude).toBeCloseTo(27, 6)
    // Not the old zoom-0.8 "moon" view: the globe spans well over half of the 800 px side.
    const { zoom, center } = lastCamera()
    expect((512 * (2 ** zoom)) / (Math.PI * Math.cos((center[1] * Math.PI) / 180))).toBeGreaterThan(0.6 * 800)

    // The trip scene runs 6000–13000 ms of a 17000 ms film; halfway through it the camera chases the marker.
    await seekTo(wrapper, Math.round((9_500 / 17_000) * 1000))
    const chase = lastCamera()
    expect(chase.pitch).toBe(52)
    expect(chase.center[0]).toBeLessThan(30)
    expect(chase.center[0]).toBeGreaterThan(24)

    await seekTo(wrapper, Math.round((9_500 / 17_000) * 1000))
    expect(lastCamera()).toEqual(chase)
    expect(map.easeTo).not.toHaveBeenCalled()
    expect(map.fitBounds).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('holds the film while tiles are missing and resumes once the map is idle', async () => {
    let now = 1_000
    vi.spyOn(Date, 'now').mockImplementation(() => now)
    const { wrapper, map } = await openPlayer()
    map.tilesLoaded = false

    for (const timestamp of [0, 500, 1_000, 1_500]) {
      runFrame(timestamp)
      await flushPromises()
      now += 500
    }
    expect(wrapper.find('.cinematic-buffering').exists()).toBe(true)

    const held = wrapper.find('.cinematic-controls span').text()
    runFrame(4_000)
    await flushPromises()
    expect(wrapper.find('.cinematic-controls span').text()).toBe(held)

    map.tilesLoaded = true
    map.fire('idle')
    await flushPromises()
    expect(wrapper.find('.cinematic-buffering').exists()).toBe(false)
    wrapper.unmount()
  })

  it('clears tile buffering when closed and advances after reopening', async () => {
    let now = 1_000
    vi.spyOn(Date, 'now').mockImplementation(() => now)
    const { wrapper, map } = await openPlayer()
    map.tilesLoaded = false
    for (const timestamp of [0, 500, 1_000, 1_500]) {
      runFrame(timestamp)
      await flushPromises()
      now += 500
    }
    expect(wrapper.find('.cinematic-buffering').exists()).toBe(true)

    await wrapper.find('.cinematic-close').trigger('click')
    await wrapper.setProps({ visible: false })
    frames = []
    await wrapper.setProps({ visible: true })
    await flushPromises()
    const reopenedMap = createMap()
    wrapper.findComponent({ name: 'BaseMap' }).vm.$emit('map-ready', reopenedMap)
    await flushPromises()
    reopenedMap.fire('idle')
    await flushPromises()
    runFrame(5_000)
    runFrame(6_000)
    await flushPromises()

    expect(wrapper.find('.cinematic-buffering').exists()).toBe(false)
    expect(wrapper.find('.cinematic-controls span').text()).toBe('00:01')
    wrapper.unmount()
  })

  it('opens paused and holds one flat view per scene when reduced motion is preferred', async () => {
    const { wrapper, map } = await openPlayer({ reducedMotion: true, photos: [photo('january', 1)] })

    expect(playButton(wrapper).attributes('aria-label')).toBe('Play')
    expect(frames).toEqual([])
    expect(map.jumpTo.mock.lastCall[0].pitch).toBe(0)

    await seekTo(wrapper, Math.round((6_500 / 17_000) * 1000))
    const tripView = map.jumpTo.mock.lastCall[0]
    await seekTo(wrapper, Math.round((12_000 / 17_000) * 1000))
    expect(map.jumpTo.mock.lastCall[0]).toEqual(tripView)
    expect(tripView.pitch).toBe(0)
    expect(wrapper.find('.cinematic-reel-current img').exists()).toBe(true) // photos still show, without motion
    wrapper.unmount()
  })

  it('puts the month first: a chapter card during the flight, then the month as the caption headline', async () => {
    const { wrapper } = await openPlayer({ trips: [trip(3), trip(9)] })

    // intro 0–3000, overview 3000–6000, March 6000–13000, September 13000–20000, closing 20000–24000.
    await seekTo(wrapper, Math.round((6_500 / 24_000) * 1000))
    expect(wrapper.find('.cinematic-card--chapter').text()).toContain('March')
    expect(wrapper.find('.cinematic-card--chapter').text()).toBe('March') // the month alone, no "1 of 2"

    await seekTo(wrapper, Math.round((10_000 / 24_000) * 1000))
    const caption = wrapper.find('.cinematic-card--caption')
    expect(caption.find('h2').text()).toBe('March')
    expect(caption.find('span').text()).toBe('🚗 Car · 3000 m · 1 hour')
    expect(caption.text()).not.toContain('of 2')
    wrapper.unmount()
  })

  it('shows the year as a month strip that marks the playing month and jumps to a month\'s trip', async () => {
    const { wrapper } = await openPlayer({ trips: [trip(3), trip(9)] })
    const months = () => wrapper.findAll('.cinematic-months button')

    expect(months().map((button) => button.text())).toEqual(['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'])
    expect(months().filter((button) => button.attributes('disabled') === undefined).map((button) => button.text())).toEqual(['Mar', 'Sep'])
    expect(wrapper.find('.cinematic-months button.active').exists()).toBe(false)

    await months()[8].trigger('click')
    await flushPromises()
    expect(wrapper.find('.cinematic-months button.active').text()).toBe('Sep')
    expect(wrapper.find('.cinematic-months button.active').attributes('aria-current')).toBe('step')
    expect(wrapper.find('.cinematic-controls span').text()).toBe('00:13')
    expect(months()[8].attributes('aria-label')).toBe('Jump to September')
    wrapper.unmount()
  })

  it('plays a month as chapters of seven days, each headed by its trip\'s date', async () => {
    const twentieth = trip(3, {
      id: 'twentieth',
      startTime: '2026-03-20T10:00:00Z',
      endTime: '2026-03-20T11:00:00Z'
    })
    const { wrapper } = await openPlayer({ month: 3, trips: [trip(3), twentieth] })
    const chapters = () => wrapper.findAll('.cinematic-months button')

    expect(fetchMonthlyReplay).toHaveBeenCalledWith(2026, 3)
    expect(fetchYearlyReplay).not.toHaveBeenCalled()
    expect(wrapper.find('.cinematic-card--hero p').text()).toBe('Your month in motion')
    expect(wrapper.find('.cinematic-card--hero h1').text()).toBe('March 2026')
    expect(chapters().map((button) => button.text())).toEqual(['1–7', '8–14', '15–21', '22–28', '29–31'])
    expect(chapters().filter((button) => button.attributes('disabled') === undefined).map((button) => button.text()))
      .toEqual(['1–7', '15–21'])
    expect(wrapper.find('.cinematic-months').attributes('aria-label')).toBe('Weeks of the month')
    expect(chapters()[2].attributes('aria-label')).toBe('Jump to 15–21 March')

    // intro 0–3000, overview 3000–6000, March 5 6000–13000, March 20 13000–20000, closing 20000–24000.
    await seekTo(wrapper, Math.round((6_500 / 24_000) * 1000))
    expect(wrapper.find('.cinematic-card--chapter h1').text()).toBe('5 March')
    await chapters()[2].trigger('click')
    await flushPromises()
    expect(wrapper.find('.cinematic-card--chapter h1').text()).toBe('20 March')
    expect(wrapper.find('.cinematic-months button.active').text()).toBe('15–21')
    wrapper.unmount()
  })

  it('names where a trip went, and a named trip by its name and its number of trips', async () => {
    const { wrapper } = await openPlayer({
      trips: [
        trip(3, { originName: 'Home', destinationName: 'Office', legCount: 1 }),
        trip(6, { originName: 'Home', destinationName: null, legCount: 1 }),
        trip(9, { name: 'Lisbon', legCount: 3, movementType: 'FLIGHT' })
      ]
    })
    const caption = () => wrapper.find('.cinematic-card--caption')

    // intro 0–3000, overview 3000–6000, March 6000–13000, June 13000–20000, September 20000–27000, closing to 31000.
    await seekTo(wrapper, Math.round((6_500 / 31_000) * 1000))
    expect(wrapper.find('.cinematic-card--chapter p').text()).toBe('Home → Office')
    await seekTo(wrapper, Math.round((10_000 / 31_000) * 1000))
    expect(caption().find('h2').text()).toBe('March')
    expect(caption().find('.cinematic-route').text()).toBe('Home → Office')
    expect(caption().find('span').text()).toBe('🚗 Car · 3000 m · 1 hour')

    await seekTo(wrapper, Math.round((17_000 / 31_000) * 1000))
    expect(caption().find('.cinematic-route').text()).toBe('From Home')

    await seekTo(wrapper, Math.round((24_000 / 31_000) * 1000))
    expect(caption().find('.cinematic-route').text()).toBe('Lisbon')
    expect(caption().find('span').text()).toBe('3 trips · 9000 m · 1 hour')
    wrapper.unmount()
  })

  it('plays the photos taken at a top place when the period has no trips', async () => {
    const atCafe = { ...photo('cafe', 4), latitude: 50.4502, longitude: 30.5236 }
    const elsewhere = { ...photo('elsewhere', 4), latitude: 48.0, longitude: 24.0 }
    const { wrapper } = await openPlayer({
      trips: [],
      topPlaces: [{ name: 'Café', coordinates: [50.4501, 30.5234], visits: 3 }],
      photos: [atCafe, elsewhere]
    })

    // intro 0–3000, overview 3000–6000, the place with its photo 6000–12000, closing 12000–16000.
    expect(wrapper.findAll('.cinematic-controls span')[1].text()).toBe('00:16')
    await seekTo(wrapper, Math.round((7_000 / 16_000) * 1000))
    expect(wrapper.find('.cinematic-reel').exists()).toBe(false) // still flying in

    await seekTo(wrapper, Math.round((9_500 / 16_000) * 1000))
    const image = wrapper.find('.cinematic-reel-current img')
    expect(image.attributes('src')).toBe('blob:/integrations/immich/photos/cafe/preview')
    expect(image.attributes('alt')).toBe('Memory from Café')
    expect(imageService.loadAuthenticatedImage).not.toHaveBeenCalledWith(expect.stringContaining('elsewhere'))
    wrapper.unmount()
  })

  it('pauses and opens the playing photo in the photo viewer', async () => {
    const { wrapper } = await openPlayer({ photos: [photo('january', 1)] })

    await seekTo(wrapper, Math.round((9_000 / 17_000) * 1000))
    await wrapper.find('.cinematic-photo-frame').trigger('click')

    expect(playButton(wrapper).attributes('aria-label')).toBe('Play')
    const viewer = wrapper.findComponent({ name: 'PhotoViewerDialog' })
    expect(viewer.props('visible')).toBe(true)
    expect(viewer.props('photos').map((entry) => entry.id)).toEqual(['january'])
    expect(viewer.props('initialPhotoIndex')).toBe(0)
    expect(viewer.props('preloadedBlobUrlResolver')('january')).toBe('blob:/integrations/immich/photos/january/preview')
    wrapper.unmount()
  })

  it('supports Space, arrow keys and Escape without hijacking focused controls', async () => {
    const { wrapper } = await openPlayer()
    const player = wrapper.find('.cinematic-player')

    await player.trigger('keydown', { key: ' ' })
    expect(playButton(wrapper).attributes('aria-label')).toBe('Play')

    await playButton(wrapper).trigger('keydown', { key: ' ' })
    expect(playButton(wrapper).attributes('aria-label')).toBe('Play')

    await player.trigger('keydown', { key: 'ArrowRight' })
    expect(wrapper.find('.cinematic-card--caption').text()).toContain('The year at a glance')
    await wrapper.find('.cinematic-controls input').trigger('keydown', { key: 'ArrowLeft' })
    expect(wrapper.find('.cinematic-card--caption').text()).toContain('The year at a glance')
    await player.trigger('keydown', { key: 'ArrowLeft' })
    expect(wrapper.find('.cinematic-card--hero').exists()).toBe(true)

    await player.trigger('keydown', { key: 'Escape' })
    expect(wrapper.emitted('update:visible')).toContainEqual([false])
    wrapper.unmount()
  })

  it('pauses when the tab is hidden', async () => {
    const { wrapper } = await openPlayer()
    Object.defineProperty(document, 'hidden', { configurable: true, value: true })

    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()

    expect(playButton(wrapper).attributes('aria-label')).toBe('Play')
    delete document.hidden
    wrapper.unmount()
  })

  it('stops the clock when the page hides it and starts over when reopened', async () => {
    const { wrapper } = await openPlayer()
    runFrame(0)
    runFrame(2_500)
    await flushPromises()
    expect(wrapper.find('.cinematic-controls span').text()).toBe('00:02')

    await wrapper.setProps({ visible: false })
    expect(window.cancelAnimationFrame).toHaveBeenCalled()
    frames = []

    await wrapper.setProps({ visible: true })
    await flushPromises()
    expect(fetchYearlyReplay).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.cinematic-controls span').text()).toBe('00:00')
    expect(playButton(wrapper).attributes('aria-label')).toBe('Play') // waiting for the new map

    const map = createMap()
    wrapper.findComponent({ name: 'BaseMap' }).vm.$emit('map-ready', map)
    await flushPromises()
    map.fire('idle')
    await flushPromises()
    expect(playButton(wrapper).attributes('aria-label')).toBe('Pause')
    wrapper.unmount()
  })

  it('waits for Immich to settle and ignores gallery updates once the film has started', async () => {
    fetchYearlyReplay.mockResolvedValue({ year: 2026, trips: [trip(1)] })
    window.matchMedia = vi.fn(() => ({ matches: false }))
    const wrapper = mount(CinematicRewindDialog, {
      props: { visible: true, year: 2026, digest, photosPayload: { photos: [], settled: false } }
    })
    await flushPromises()
    expect(wrapper.findComponent({ name: 'BaseMap' }).exists()).toBe(false)

    await wrapper.setProps({ photosPayload: { photos: [photo('january', 1)], settled: true } })
    await flushPromises()
    const map = createMap()
    wrapper.findComponent({ name: 'BaseMap' }).vm.$emit('map-ready', map)
    await flushPromises()
    map.fire('idle')
    await flushPromises()
    expect(playButton(wrapper).attributes('aria-label')).toBe('Pause')

    await wrapper.setProps({ photosPayload: { photos: [], settled: false } })
    await flushPromises()
    expect(wrapper.findComponent({ name: 'BaseMap' }).exists()).toBe(true)
    expect(playButton(wrapper).attributes('aria-label')).toBe('Pause')
    wrapper.unmount()
  })
})
