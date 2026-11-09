import { describe, expect, it } from 'vitest'
import {
  resolveStayWindowFromSelection,
  validateStayWindowSelection
} from './tripStaySplitSelection'

const points = [
  { timestamp: '2026-05-08T05:42:00Z', latitude: 49.54943, longitude: 25.59829 },
  { timestamp: '2026-05-08T05:47:00Z', latitude: 49.55954, longitude: 25.62185 },
  { timestamp: '2026-05-08T05:52:00Z', latitude: 49.55532, longitude: 25.63964 },
  { timestamp: '2026-05-08T05:57:00Z', latitude: 49.55076, longitude: 25.67009 },
  { timestamp: '2026-05-08T06:02:00Z', latitude: 49.55111, longitude: 25.67066 },
  { timestamp: '2026-05-08T06:07:00Z', latitude: 49.55025, longitude: 25.65824 },
  { timestamp: '2026-05-08T06:12:00Z', latitude: 49.55471, longitude: 25.64198 },
  { timestamp: '2026-05-08T06:32:00Z', latitude: 49.54943, longitude: 25.59829 }
]

describe('trip stay split selection', () => {
  const shortTripPoints = (nearOrigin = false) => [
    ['19:49:38', nearOrigin ? 50 : 50.0056],
    ['19:50:01', 50.0002],
    ['19:53:49', 50.0003],
    ['19:55:42', nearOrigin ? 50.0056 : 50]
  ].map(([time, latitude]) => ({ timestamp: `2026-11-05T${time}Z`, latitude, longitude: 30 }))

  it.each([false, true])('allows an interior stop whose cluster touches an endpoint (origin: %s)', (nearOrigin) => {
    const path = shortTripPoints(nearOrigin)
    const options = { tripStart: path[0].timestamp, tripEnd: path[3].timestamp }
    const selection = resolveStayWindowFromSelection(path, path[1], options)

    expect(selection.error).toBeUndefined()
    expect(selection.startIndex).toBe(nearOrigin ? 0 : 1)
    expect(selection.endIndex).toBe(nearOrigin ? 2 : 3)
    expect(Date.parse(selection.startTime)).toBeGreaterThan(Date.parse(options.tripStart))
    expect(Date.parse(selection.endTime)).toBeLessThan(Date.parse(options.tripEnd))
    expect(validateStayWindowSelection(path, selection.point, selection, options)).toEqual({ valid: true })

    const range = { start: path[1].timestamp, end: path[2].timestamp }
    expect(validateStayWindowSelection(path, selection.point, range, options)).toEqual({ valid: true })
    expect((Date.parse(range.start) - Date.parse(options.tripStart)) / 1000).toBe(23)
    expect((Date.parse(range.end) - Date.parse(range.start)) / 1000).toBe(228)
    expect((Date.parse(options.tripEnd) - Date.parse(range.end)) / 1000).toBe(113)
  })

  it.each([0, 3])('rejects an actual trip endpoint in both checks (index: %s)', (index) => {
    const path = shortTripPoints()
    const options = { tripStart: path[0].timestamp, tripEnd: path[3].timestamp }
    expect(resolveStayWindowFromSelection(path, path[index], options).error).toContain('intermediate stop')
    expect(validateStayWindowSelection(path, path[index], {
      start: options.tripStart, end: options.tripEnd
    }, options).error).toContain('intermediate stop')
  })

  it('uses the nearby contiguous cluster as the stay window', () => {
    const result = resolveStayWindowFromSelection(points, { latitude: 49.551, longitude: 25.6704 }, {
      radiusMeters: 150,
      tripStart: '2026-05-08T05:42:00Z',
      tripEnd: '2026-05-08T06:32:00Z'
    })

    expect(result.error).toBeUndefined()
    expect(result.startTime).toBe('2026-05-08T05:57:00.000Z')
    expect(result.endTime).toBe('2026-05-08T06:02:00.000Z')
  })

  it('rejects selections too far from the path', () => {
    const result = resolveStayWindowFromSelection(points, { latitude: 49.7, longitude: 25.9 }, {
      radiusMeters: 150
    })

    expect(result.error).toContain('closer to the trip path')
  })

  it('pads a sparse single point into a bounded interval', () => {
    const sparsePoints = [points[0], points[3], points[7]]
    const result = resolveStayWindowFromSelection(sparsePoints, points[3], {
      radiusMeters: 150,
      tripStart: '2026-05-08T05:42:00Z',
      tripEnd: '2026-05-08T06:32:00Z'
    })

    expect(result.startTime).toBe('2026-05-08T05:52:00.000Z')
    expect(result.endTime).toBe('2026-05-08T06:02:00.000Z')
  })

  it('rejects the trip origin or destination as an inserted stay', () => {
    const result = resolveStayWindowFromSelection(points, points[0], {
      radiusMeters: 150,
      tripStart: '2026-05-08T05:42:00Z',
      tripEnd: '2026-05-08T06:32:00Z'
    })

    expect(result.error).toContain('intermediate stop')
  })

  it('rejects edited ranges that do not contain the selected occurrence timestamp', () => {
    const selected = resolveStayWindowFromSelection(points, points[3], {
      radiusMeters: 150,
      tripStart: '2026-05-08T05:42:00Z',
      tripEnd: '2026-05-08T06:32:00Z'
    })
    const result = validateStayWindowSelection(points, selected.point, {
      startTime: '2026-05-08T06:08:00Z',
      endTime: '2026-05-08T06:18:00Z'
    }, {
      radiusMeters: 150,
      tripStart: '2026-05-08T05:42:00Z',
      tripEnd: '2026-05-08T06:32:00Z'
    })

    expect(result.error).toBe('Selected place is not near GPS points in this time range.')
  })
})
