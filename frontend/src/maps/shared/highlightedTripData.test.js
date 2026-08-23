import { describe, expect, it } from 'vitest'
import { buildHighlightedTripData } from './highlightedTripData'

describe('highlighted trip path data', () => {
  it('preserves disconnected path fragments without creating a highlighted bridge', () => {
    const highlighted = buildHighlightedTripData({
      highlightedTrip: {
        id: 1,
        type: 'trip',
        timestamp: '2026-08-16T14:16:00Z',
        tripDuration: 60,
        latitude: 50,
        longitude: 30,
        endLatitude: 51.1,
        endLongitude: 31.1
      },
      pathData: [
        [
          { latitude: 50, longitude: 30, timestamp: '2026-08-16T14:16:00Z' },
          { latitude: 50.1, longitude: 30.1, timestamp: '2026-08-16T14:16:20Z' }
        ],
        [
          { latitude: 51, longitude: 31, timestamp: '2026-08-16T14:16:40Z' },
          { latitude: 51.1, longitude: 31.1, timestamp: '2026-08-16T14:17:00Z' }
        ]
      ]
    })

    expect(highlighted.renderedTripSegments).toHaveLength(2)
    expect(highlighted.highlightedSegments.segments).toHaveLength(2)
    expect(highlighted.highlightedSegments.segments.map(segment => segment.coordinates)).toEqual([
      [[30, 50], [30.1, 50.1]],
      [[31, 51], [31.1, 51.1]]
    ])
    expect(highlighted.replayPathPoints.map(point => Date.parse(point.timestamp))).toEqual([
      Date.parse('2026-08-16T14:16:00Z'),
      Date.parse('2026-08-16T14:16:20Z'),
      Date.parse('2026-08-16T14:16:40Z'),
      Date.parse('2026-08-16T14:17:00Z')
    ])
  })
})
