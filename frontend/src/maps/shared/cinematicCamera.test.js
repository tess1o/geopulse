import { describe, expect, it } from 'vitest'
import {
  TRIP_PHASES,
  buildCinematicCameraPlan,
  buildTripPhases,
  fitCinematicBounds,
  globeZoomForViewport,
  resolveCinematicCamera,
  tripRouteProgress
} from './cinematicCamera'

const viewport = { width: 1000, height: 800 }

// On-screen globe diameter in MapLibre: 512·2^zoom / (π·cos(latitude)).
const globeDiameter = ({ zoom, center }) => (512 * (2 ** zoom)) / (Math.PI * Math.cos((center[1] * Math.PI) / 180))

const trip = (id, path) => ({
  id,
  startTime: '2026-01-05T10:00:00Z',
  path: path.map(([longitude, latitude]) => ({ longitude, latitude }))
})

const scenes = (trips) => [
  { type: 'intro' },
  { type: 'overview' },
  ...trips.map((value) => ({ type: 'trip', trip: value })),
  { type: 'closing' }
]

const expectSameView = (actual, expected) => {
  expect(actual.center[0]).toBeCloseTo(expected.center[0], 6)
  expect(actual.center[1]).toBeCloseTo(expected.center[1], 6)
  expect(actual.zoom).toBeCloseTo(expected.zoom, 6)
  expect(actual.pitch).toBeCloseTo(expected.pitch, 6)
  expect(actual.bearing).toBeCloseTo(expected.bearing, 6)
}

describe('cinematic camera', () => {
  it('sizes the hero globe to the viewport, compensating for MapLibre latitude scaling', () => {
    // 90% of the 800 px side: 720 px diameter = 512·2^z/π.
    expect(globeZoomForViewport(viewport, 0)).toBeCloseTo(Math.log2((720 * Math.PI) / 512), 6)
    expect(globeZoomForViewport(viewport, 60)).toBeCloseTo(globeZoomForViewport(viewport, 0) - 1, 6)
  })

  it('frames bounds like MapLibre\'s Mercator fitBounds', () => {
    const fitted = fitCinematicBounds([[-79, 43], [-73, 45]], { width: 612, height: 612 }, 50)

    expect(fitted.center[0]).toBe(-76)
    expect(fitted.center[1]).toBeCloseTo(44.007, 2) // the Mercator midpoint, a little north of 44°
    expect(fitted.zoom).toBeCloseTo(Math.log2((512 * 360) / 6 / 512), 6) // the 6° of longitude fill 512 px
  })

  it('never pulls the overview back past a screen-filling globe', () => {
    const plan = buildCinematicCameraPlan({
      scenes: scenes([trip(1, [[-120, 40], [-70, 42]]), trip(2, [[100, 30], [140, 35]])]),
      viewport
    })

    const overview = plan[1].to
    expect(overview.zoom).toBeCloseTo(globeZoomForViewport(viewport, overview.center[1]), 6)
    // The opening and closing globes stay big: well over half the 800 px side (the old view was ~280 px).
    expect(globeDiameter(plan[0].from)).toBeGreaterThan(0.6 * 800)
    expect(globeDiameter(plan.at(-1).to)).toBeGreaterThan(0.6 * 800)
  })

  it('films only where the user has been: Eastern Europe trips never send the camera south over Africa', () => {
    const plan = buildCinematicCameraPlan({
      scenes: scenes([
        trip(1, [[30.52, 50.45], [24.03, 49.84]]), // Kyiv → Lviv
        trip(2, [[27.91, 43.21], [27.47, 42.5]]), // Varna → Burgas
        trip(3, [[21.01, 52.23], [19.94, 50.06]]) // Warsaw → Kraków
      ]),
      viewport
    })

    plan.forEach((entry) => {
      for (let progress = 0; progress <= 1; progress += 0.05) {
        const cursor = entry.followStart
          ? { longitude: entry.followStart.center[0], latitude: entry.followStart.center[1] }
          : null
        const [longitude, latitude] = resolveCinematicCamera(entry, progress, { cursor }).center
        expect(latitude, `${entry.type} at ${progress.toFixed(2)}`).toBeGreaterThan(40)
        expect(longitude, `${entry.type} at ${progress.toFixed(2)}`).toBeGreaterThan(-30)
        expect(longitude, `${entry.type} at ${progress.toFixed(2)}`).toBeLessThan(60)
      }
    })
  })

  it('keeps every cut continuous: each scene starts where the previous one ended', () => {
    const plan = buildCinematicCameraPlan({
      scenes: scenes([trip(1, [[30.5, 50.4], [24, 49.8]]), trip(2, [[2.35, 48.85], [4.35, 50.85]])]),
      viewport
    })
    const cursorAtStart = (entry) => ({ longitude: entry.followStart?.center[0], latitude: entry.followStart?.center[1] })

    for (let index = 1; index < plan.length; index += 1) {
      const previous = resolveCinematicCamera(plan[index - 1], 1, { cursor: cursorAtStart(plan[index - 1]) })
      const next = resolveCinematicCamera(plan[index], 0, { cursor: cursorAtStart(plan[index]) })
      expectSameView(next, previous)
    }
  })

  it('chases the trip marker with a pitched camera, then reveals the whole route', () => {
    const plan = buildCinematicCameraPlan({ scenes: scenes([trip(1, [[30.5, 50.4], [24, 49.8]])]), viewport })
    const entry = plan[2]

    const following = resolveCinematicCamera(entry, 0.5, { cursor: { longitude: 27, latitude: 50.1 } })
    expect(following.center).toEqual([27, 50.1])
    expect(following.pitch).toBe(52)
    expect(following.zoom).toBeGreaterThan(entry.to.zoom)
    // Heading west (≈263°): the camera leans left, but no further than 60° (plus a few degrees of drift).
    expect(following.bearing).toBeGreaterThanOrEqual(-60)
    expect(following.bearing).toBeLessThan(-50)

    expectSameView(resolveCinematicCamera(entry, 1, { cursor: { longitude: 24, latitude: 49.8 } }), entry.to)
    expect(entry.to.zoom).toBeGreaterThan(3)
  })

  it('arcs up between distant places, like flyTo', () => {
    const plan = buildCinematicCameraPlan({
      scenes: scenes([trip(1, [[30.5, 50.4], [24, 49.8]]), trip(2, [[-74, 40.7], [-71, 42.3]])]),
      viewport
    })
    const entry = plan[3]
    const midFlight = resolveCinematicCamera(entry, TRIP_PHASES.flightEnd / 2)

    expect(midFlight.zoom).toBeLessThan(Math.min(entry.from.zoom, entry.followStart.zoom) - 1)
  })

  it('flies across the antimeridian by the short route', () => {
    const plan = buildCinematicCameraPlan({
      scenes: scenes([trip(1, [[178, -17], [179.5, -16.5]]), trip(2, [[-179.5, -16], [-178, -15]])]),
      viewport
    })
    const midFlight = resolveCinematicCamera(plan[3], TRIP_PHASES.flightEnd / 2)

    expect(Math.abs(midFlight.center[0])).toBeGreaterThan(178)
  })

  it('holds one flat view per scene with reduced motion', () => {
    const plan = buildCinematicCameraPlan({ scenes: scenes([trip(1, [[30.5, 50.4], [24, 49.8]])]), viewport })

    const start = resolveCinematicCamera(plan[2], 0, { reducedMotion: true })
    const end = resolveCinematicCamera(plan[2], 0.9, { reducedMotion: true, cursor: { longitude: 25, latitude: 50 } })
    expect(start).toEqual(end)
    expect(start.pitch).toBe(0)
  })

  it('draws the route only while the camera follows it, starting and finishing gently', () => {
    const followSpan = TRIP_PHASES.followEnd - TRIP_PHASES.flightEnd
    expect(tripRouteProgress(0)).toBe(0)
    expect(tripRouteProgress(TRIP_PHASES.flightEnd)).toBe(0)
    expect(tripRouteProgress(TRIP_PHASES.flightEnd + (followSpan / 2))).toBeCloseTo(0.5)
    expect(tripRouteProgress(TRIP_PHASES.flightEnd + (followSpan / 10))).toBeLessThan(0.1)
    expect(tripRouteProgress(TRIP_PHASES.followEnd)).toBeCloseTo(1, 10)
    expect(tripRouteProgress(1)).toBe(1)
  })

  it('gives a 7 s trip 1.5 s of flight, 3.5 s of chase and 2 s on the finished route', () => {
    expect(TRIP_PHASES.flightEnd * 7).toBeCloseTo(1.5)
    expect((TRIP_PHASES.followEnd - TRIP_PHASES.flightEnd) * 7).toBeCloseTo(3.5)
    expect((1 - TRIP_PHASES.followEnd) * 7).toBeCloseTo(2)
  })

  it('keeps the camera within 60° of north whichever way the trip goes', () => {
    const southbound = trip(1, [[24, 52], [24.5, 45]])
    const eastbound = trip(2, [[10, 48], [20, 48]])
    const plan = buildCinematicCameraPlan({ scenes: scenes([southbound, eastbound]), viewport })

    plan.filter((entry) => entry.type === 'trip').forEach((entry) => {
      for (let progress = 0; progress <= 1; progress += 0.05) {
        const { bearing } = resolveCinematicCamera(entry, progress, { cursor: { longitude: 24, latitude: 50 } })
        expect(Math.abs(bearing)).toBeLessThanOrEqual(60 + 6 + 3)
      }
    })
  })

  it('rests on the revealed route, pushing in slightly, before the next trip', () => {
    const plan = buildCinematicCameraPlan({ scenes: scenes([trip(1, [[30.5, 50.4], [24, 49.8]])]), viewport })
    const entry = plan[2]

    expectSameView(resolveCinematicCamera(entry, TRIP_PHASES.revealEnd), entry.reveal)
    expect(entry.to.zoom).toBeGreaterThan(entry.reveal.zoom)
    expect(entry.to.zoom - entry.reveal.zoom).toBeLessThan(0.5)
  })

  it('stretches the chase and the rest for longer trip scenes, keeping the flight and reveal fixed', () => {
    const phases = buildTripPhases({ followMs: 4500, holdMs: 2200 })

    expect(phases.durationMs).toBe(9000)
    expect(phases.flightEnd * 9000).toBeCloseTo(1500)
    expect((phases.followEnd - phases.flightEnd) * 9000).toBeCloseTo(4500)
    expect((phases.revealEnd - phases.followEnd) * 9000).toBeCloseTo(800)
  })
})

