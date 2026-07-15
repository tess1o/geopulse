import { describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/calculationsHelpers', () => ({
  formatSpeed: (value) => `${Number(value || 0).toFixed(2)} km/h`
}))

import {
  createRawGpsPopupElement,
  groupRawGpsPoints,
  STATIONARY_GROUP_METERS
} from './rawGpsPointInspector'

const BASE_LATITUDE = 1.327946
const BASE_LONGITUDE = 103.804579
const METERS_PER_DEGREE = 111320

const pointOffsetByMeters = (id, eastMeters, northMeters) => {
  const latitude = BASE_LATITUDE + northMeters / METERS_PER_DEGREE
  const longitude = BASE_LONGITUDE + eastMeters / (
    METERS_PER_DEGREE * Math.cos(BASE_LATITUDE * Math.PI / 180)
  )

  return {
    id,
    timestamp: `2026-07-08T10:${String(id).padStart(2, '0')}:00Z`,
    latitude,
    longitude
  }
}

const timezone = {
  formatDateDisplay: () => '03/06/2026',
  formatTime: () => '00:00:51'
}

const waitForRenderNotification = () => new Promise((resolve) => setTimeout(resolve, 20))

describe('raw GPS point inspector grouping', () => {
  it('collapses many same-location points into one stationary stack', () => {
    const points = Array.from({ length: 500 }, (_, index) => ({
      id: index + 1,
      timestamp: `2026-07-08T10:${String(index % 60).padStart(2, '0')}:00Z`,
      latitude: 1.327946,
      longitude: 103.804579,
      accuracy: 6,
      battery: 61,
      velocity: 62
    }))

    const groups = groupRawGpsPoints(points)

    expect(groups).toHaveLength(1)
    expect(groups[0].count).toBe(500)
    expect(groups[0].points).toHaveLength(500)
    expect(groups[0].representative.id).toBe(1)
  })

  it('collapses nearby stationary GPS jitter into one stack', () => {
    const groups = groupRawGpsPoints([
      pointOffsetByMeters(1, 0, 0),
      pointOffsetByMeters(2, 6, 7),
      pointOffsetByMeters(3, -8, 5),
      pointOffsetByMeters(4, 10, -9),
      pointOffsetByMeters(5, -11, -6)
    ])

    expect(STATIONARY_GROUP_METERS).toBe(25)
    expect(groups).toHaveLength(1)
    expect(groups[0].count).toBe(5)
  })

  it('does not stack points outside the stationary radius', () => {
    const groups = groupRawGpsPoints([
      pointOffsetByMeters(1, 0, 0),
      pointOffsetByMeters(2, STATIONARY_GROUP_METERS + 8, 0)
    ])

    expect(groups).toHaveLength(2)
    expect(groups.map((group) => group.count)).toEqual([1, 1])
  })

  it('keeps separate moving points as separate groups', () => {
    const groups = groupRawGpsPoints([
      { id: 1, timestamp: '2026-07-08T10:00:00Z', latitude: 1.327946, longitude: 103.804579 },
      { id: 2, timestamp: '2026-07-08T10:01:00Z', latitude: 1.328946, longitude: 103.805579 },
      { id: 3, timestamp: '2026-07-08T10:02:00Z', latitude: 1.329946, longitude: 103.806579 }
    ])

    expect(groups).toHaveLength(3)
    expect(groups.map((group) => group.count)).toEqual([1, 1, 1])
  })

  it('notifies the map when popup content renders again', async () => {
    const onRender = vi.fn()
    const group = groupRawGpsPoints([
      pointOffsetByMeters(1, 0, 0),
      pointOffsetByMeters(2, 4, 4)
    ])[0]

    const element = createRawGpsPopupElement(group, {
      timezone,
      onRender
    })

    await waitForRenderNotification()
    const initialRenderCount = onRender.mock.calls.length
    expect(initialRenderCount).toBeGreaterThan(0)
    expect(onRender).toHaveBeenLastCalledWith(element)

    element.querySelectorAll('.raw-gps-stack-row')[1].click()

    await waitForRenderNotification()
    expect(onRender.mock.calls.length).toBeGreaterThan(initialRenderCount)
  })
})
