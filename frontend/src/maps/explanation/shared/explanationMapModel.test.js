import { describe, expect, it } from 'vitest'
import {
  boundsForIndexes,
  buildExplanationMapModel,
  circleRing,
  EXPLANATION_POPUP_CLASS_NAME,
  haversineMeters,
  pointPaint,
  pointsInDrawOrder
} from './explanationMapModel'

const point = (latitude, extra = {}) => ({
  timestamp: '2026-10-08T17:00:00Z',
  latitude,
  longitude: 24.03,
  part: 'EVENT',
  used: true,
  reasons: [],
  ...extra
})

describe('buildExplanationMapModel', () => {
  it('draws a stay: its points by kind, the radius around the centre, and the point that ended it', () => {
    const model = buildExplanationMapModel({
      kind: 'STAY',
      centre: { latitude: 49.84, longitude: 24.03 },
      location: { latitude: 49.8401, longitude: 24.0301 },
      radiusMeters: 50,
      points: [
        point(49.838, { part: 'BEFORE' }),
        point(49.84, { reasons: ['ARRIVAL'] }),
        point(49.8402, { used: false, accuracyMeters: 200 }),
        point(49.8401),
        point(49.842, { part: 'AFTER', reasons: ['MOVED_AWAY'] })
      ]
    })

    expect(model.points.map(p => p.style)).toEqual(['context', 'used', 'filtered', 'used', 'context'])
    expect(model.points.map(p => p.deciding)).toEqual([false, true, false, false, true])
    expect(model.circle).toEqual({ latitude: 49.84, longitude: 24.03, radiusMeters: 50 })
    expect(model.anchor).toEqual({ latitude: 49.8401, longitude: 24.0301 })
    expect(model.reasonIndexes).toEqual({ ARRIVAL: [1], MOVED_AWAY: [4] })
    // The left-out point is not on the path
    expect(model.lines.find(line => line.type === 'event').coordinates).toEqual([[49.84, 24.03], [49.8401, 24.03]])
    // The view takes in the event, the radius and the point that ended it, but not every context point
    expect(model.bounds[1][0]).toBeCloseTo(49.842)
    expect(model.bounds[0][0]).toBeGreaterThan(49.838)
  })

  it('draws a data gap as a dashed line between its edges, without a radius', () => {
    const model = buildExplanationMapModel({
      kind: 'DATA_GAP',
      points: [
        point(49.84, { part: 'BEFORE', reasons: ['NO_POINTS'] }),
        point(49.85, { used: false }),
        point(49.86, { part: 'AFTER', reasons: ['NO_POINTS'] })
      ]
    })

    expect(model.circle).toBeNull()
    expect(model.lines).toEqual([{ type: 'gap', coordinates: [[49.84, 24.03], [49.86, 24.03]] }])
  })

  it('draws a trip without points of its own between its ends', () => {
    const model = buildExplanationMapModel({
      kind: 'TRIP',
      startPoint: { latitude: 49.84, longitude: 24.03 },
      endPoint: { latitude: 49.85, longitude: 24.04 },
      points: []
    })

    expect(model.lines).toEqual([{ type: 'gap', coordinates: [[49.84, 24.03], [49.85, 24.04]] }])
    expect(model.bounds).toEqual([[49.84, 24.03], [49.85, 24.04]])
  })

  it('zooms to the points of a step', () => {
    const model = buildExplanationMapModel({ kind: 'TRIP', points: [point(49.84), point(49.85), point(49.86)] })
    expect(boundsForIndexes(model, [1, 2])).toEqual([[49.85, 24.03], [49.86, 24.03]])
  })
})

describe('point drawing', () => {
  it('draws deciding points larger and on top, and dims the others while a step is selected', () => {
    const used = { index: 0, style: 'used', deciding: false }
    const deciding = { index: 1, style: 'used', deciding: true }
    const filtered = { index: 2, style: 'filtered', deciding: false }

    expect(pointPaint(deciding).radius).toBeGreaterThan(pointPaint(used).radius)
    expect(pointPaint(used, 'dimmed').fillOpacity).toBeLessThan(pointPaint(used).fillOpacity)
    expect(pointPaint(used, 'selected').radius).toBeGreaterThan(pointPaint(used).radius)
    expect(pointsInDrawOrder([deciding, filtered, used]).map(p => p.index)).toEqual([0, 2, 1])
  })

  it('makes a closed circle of the stay radius', () => {
    const ring = circleRing(49.84, 24.03, 50)
    expect(ring[0]).toEqual(ring[ring.length - 1])
    expect(haversineMeters(49.84, 24.03, ring[16][1], ring[16][0])).toBeCloseTo(50, 0)
  })
})

describe('point popup', () => {
  it('uses the shared map popup card, whose colours follow the light and dark theme', () => {
    expect(EXPLANATION_POPUP_CLASS_NAME.split(' ')).toContain('gp-map-popup-container')
  })
})
