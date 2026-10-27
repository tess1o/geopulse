import { mount } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { buildCinematicStory, useCinematicRewind } from './useCinematicRewind'

const trip = (month, distanceMeters = 1000) => ({
  id: month,
  startTime: `2026-${String(month).padStart(2, '0')}-05T10:00:00Z`,
  endTime: `2026-${String(month).padStart(2, '0')}-05T12:00:00Z`,
  distanceMeters,
  path: [{ latitude: month, longitude: month }, { latitude: month + 1, longitude: month + 1 }]
})
const photoDuring = (id, month, extra = {}) => ({
  id,
  takenAt: `2026-${String(month).padStart(2, '0')}-05T11:00:00Z`,
  width: 1200,
  height: 800,
  ...extra
})

describe('cinematic Rewind story', () => {
  afterEach(() => vi.restoreAllMocks())

  it('films every active month in date order, whether or not its trip has photos', () => {
    // One trip per month, as the replay API returns them; only March and April have photos.
    const trips = [12, 4, 9, 1, 7, 3, 11, 2, 8, 5, 10, 6].map((month) => trip(month))
    const photos = [photoDuring('march', 3), photoDuring('april', 4)]

    const story = buildCinematicStory({ trips, photos })
    const tripScenes = story.filter((scene) => scene.type === 'trip')

    expect(tripScenes.map((scene) => scene.trip.id)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12])
    expect(tripScenes.map((scene) => scene.photos.map((photo) => photo.id)).filter((ids) => ids.length))
      .toEqual([['march'], ['april']])
    expect(story.at(-1).endMs).toBe(3000 + 3000 + (12 * 7000) + 4000)
  })

  it('gives each photo to one trip only and never pairs a photo from elsewhere in the month', () => {
    // Two trips on one day ending at the same place: a photo taken there afterwards matches both.
    const first = trip(5)
    const sameDay = { ...trip(5), id: 60, startTime: '2026-05-05T13:00:00Z', endTime: '2026-05-05T14:00:00Z' }
    const shared = { ...photoDuring('shared', 5), takenAt: '2026-05-05T15:00:00Z', latitude: 6, longitude: 6 }
    const lateInMonth = { ...photoDuring('late', 5), takenAt: '2026-05-25T11:00:00Z' }

    const tripScenes = buildCinematicStory({ trips: [first, sameDay], photos: [shared, lateInMonth] })
      .filter((scene) => scene.type === 'trip')

    expect(tripScenes.map((scene) => scene.photos.map((photo) => photo.id))).toEqual([['shared'], []])
  })

  it('gives a trip with a full reel of four photos one more second of travel, and only that trip', () => {
    const destination = { latitude: 5, longitude: 5 }
    const photos = ['13', '15', '18', '21'].map((hour) => photoDuring(`sofia-${hour}`, 4, { takenAt: `2026-04-05T${hour}:00:00Z`, ...destination }))
    const tripScenes = buildCinematicStory({ trips: [trip(4), trip(6)], photos }).filter((scene) => scene.type === 'trip')

    expect(tripScenes[0].photos).toHaveLength(4)
    expect(tripScenes[0].durationMs).toBe(8000)
    expect(tripScenes[1].durationMs).toBe(7000)
    // The camera's phases stretch with the scene: the flight in still takes 1.5 s.
    expect(tripScenes[0].phases.flightEnd * tripScenes[0].durationMs).toBeCloseTo(1500)
  })

  it('uses coordinate-bearing places only when the year has no trips', () => {
    const story = buildCinematicStory({
      topPlaces: [
        { name: 'Kyiv', coordinates: [50.45, 30.52] },
        { name: 'Invalid', coordinates: [100, 30] }
      ]
    })

    expect(story.map((scene) => scene.type)).toEqual(['intro', 'overview', 'place', 'closing'])
  })

  it('uses one animation clock for play, pause, seek, and restart', async () => {
    let frame
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
      frame = callback
      return 1
    })
    vi.spyOn(window, 'cancelAnimationFrame').mockImplementation(() => {})
    const Harness = defineComponent({
      setup() {
        return useCinematicRewind({
          story: ref(buildCinematicStory({ trips: [trip(1)] })),
          ready: ref(true)
        })
      },
      template: '<div />'
    })
    const wrapper = mount(Harness)

    wrapper.vm.play()
    frame(100)
    frame(1_100)
    expect(wrapper.vm.elapsedMs).toBe(1_000)
    wrapper.vm.pause()
    frame?.(2_100)
    expect(wrapper.vm.elapsedMs).toBe(1_000)
    wrapper.vm.seekToRatio(0.5)
    expect(wrapper.vm.progressRatio).toBe(0.5)
    wrapper.vm.restart()
    expect(wrapper.vm.elapsedMs).toBe(0)

    wrapper.unmount()
  })

  it('holds the film while the map is buffering and derives scenes from elapsed time', async () => {
    let frame
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
      frame = callback
      return 1
    })
    vi.spyOn(window, 'cancelAnimationFrame').mockImplementation(() => {})
    const Harness = defineComponent({
      setup() {
        return useCinematicRewind({
          story: ref(buildCinematicStory({ trips: [trip(1), trip(2)] })),
          ready: ref(true)
        })
      },
      template: '<div />'
    })
    const wrapper = mount(Harness)

    wrapper.vm.play()
    frame(0)
    wrapper.vm.setBuffering(true)
    frame(5_000)
    expect(wrapper.vm.elapsedMs).toBe(0)
    wrapper.vm.setBuffering(false)
    frame(5_100)
    frame(5_600)
    expect(wrapper.vm.elapsedMs).toBe(500)

    wrapper.vm.nextScene()
    wrapper.vm.nextScene()
    expect(wrapper.vm.currentScene.type).toBe('trip')
    expect(wrapper.vm.currentScene.trip.id).toBe(1)
    wrapper.vm.seekToMs(6_000 + 3_500)
    expect(wrapper.vm.sceneProgress).toBeCloseTo(0.5)
    wrapper.vm.previousScene()
    expect(wrapper.vm.currentScene.trip.id).toBe(1)
    wrapper.vm.previousScene()
    expect(wrapper.vm.currentScene.type).toBe('overview')

    wrapper.vm.seekToMs(Number.MAX_SAFE_INTEGER)
    expect(wrapper.vm.currentScene.type).toBe('closing')
    wrapper.unmount()
  })
})

