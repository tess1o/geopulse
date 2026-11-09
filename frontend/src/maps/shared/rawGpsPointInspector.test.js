import { nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/calculationsHelpers', () => ({
  formatSpeed: (value) => `${Number(value || 0).toFixed(2)} km/h`
}))

import {
  createRawGpsPopupMount,
  createRawGpsInspectionClusters,
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

const waitForRenderCountToIncrease = async (onRender, initialRenderCount) => {
  for (let attempt = 0; attempt < 10; attempt += 1) {
    await waitForRenderNotification()
    if (onRender.mock.calls.length > initialRenderCount) {
      return
    }
  }
}

const getPopupGridValue = (element, label) => {
  const labelElement = Array.from(element.querySelectorAll('.raw-gps-popup-label'))
    .find((node) => node.textContent === label)
  return labelElement?.nextElementSibling?.textContent
}

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

    const popupMount = createRawGpsPopupMount(group, {
      timezone,
      onRender
    })
    const element = popupMount.element

    await waitForRenderNotification()
    const initialRenderCount = onRender.mock.calls.length
    expect(initialRenderCount).toBeGreaterThan(0)
    expect(onRender).toHaveBeenLastCalledWith(element)

    element.querySelectorAll('.raw-gps-stack-row')[1].click()

    await waitForRenderCountToIncrease(onRender, initialRenderCount)
    expect(onRender.mock.calls.length).toBeGreaterThan(initialRenderCount)
    popupMount.unmount()
  })

  it('shows exact selected point telemetry for stacked GPS popups', async () => {
    const group = groupRawGpsPoints([
      {
        ...pointOffsetByMeters(1, 0, 0),
        accuracy: 4,
        battery: 82,
        velocity: 10,
        altitude: 12
      },
      {
        ...pointOffsetByMeters(2, 4, 4),
        accuracy: 19,
        battery: 71,
        velocity: 12,
        altitude: 24
      }
    ])[0]

    const popupMount = createRawGpsPopupMount(group, { timezone })
    const element = popupMount.element

    expect(getPopupGridValue(element, 'Accuracy')).toBe('4m')
    expect(getPopupGridValue(element, 'Battery')).toBe('82%')
    expect(getPopupGridValue(element, 'Speed')).toBe('10.00 km/h')
    expect(getPopupGridValue(element, 'Altitude')).toBe('12m')

    element.querySelectorAll('.raw-gps-stack-row')[1].click()
    await waitForRenderNotification()

    expect(getPopupGridValue(element, 'Accuracy')).toBe('19m')
    expect(getPopupGridValue(element, 'Battery')).toBe('71%')
    expect(getPopupGridValue(element, 'Speed')).toBe('12.00 km/h')
    expect(getPopupGridValue(element, 'Altitude')).toBe('24m')
    popupMount.unmount()
  })

  it('includes compact speed and battery values in each stack row', () => {
    const group = groupRawGpsPoints([
      {
        ...pointOffsetByMeters(1, 0, 0),
        accuracy: 4,
        battery: 82,
        velocity: 10
      },
      {
        ...pointOffsetByMeters(2, 4, 4),
        accuracy: null,
        battery: -1,
        velocity: 12
      }
    ])[0]

    const popupMount = createRawGpsPopupMount(group, { timezone })
    const element = popupMount.element
    const rows = element.querySelectorAll('.raw-gps-stack-row')

    expect(rows).toHaveLength(2)
    expect(rows[0].textContent).toContain('Speed')
    expect(rows[0].textContent).toContain('10.00 km/h')
    expect(rows[0].textContent).toContain('Battery')
    expect(rows[0].textContent).toContain('82%')
    expect(rows[0].textContent).not.toContain('Accuracy')

    expect(rows[1].textContent).toContain('12.00 km/h')
    expect(rows[1].textContent).toContain('N/A')
    popupMount.unmount()
  })
})


describe('raw GPS point selection for reconstruction', () => {
  it('passes the selected point from a stationary stack to the range selector', async () => {
    const points = [pointOffsetByMeters(1, 0, 0), pointOffsetByMeters(2, 0, 0)]
    const group = groupRawGpsPoints(points)[0]
    const onSelectPoint = vi.fn()
    const popup = createRawGpsPopupMount(group, { timezone, onSelectPoint })
    await waitForRenderNotification()
    popup.element.querySelectorAll('.raw-gps-stack-row')[1].click()
    await waitForRenderNotification()
    Array.from(popup.element.querySelectorAll('button')).find(button => button.textContent.includes('Select this time')).click()
    expect(onSelectPoint).toHaveBeenCalledWith(expect.objectContaining({ id: 2, timestamp: points[1].timestamp }))
    popup.unmount()
  })
})

describe('raw GPS inspection clustering', () => {
  it('separates nearby samples as screen space allows and keeps coincident samples at their recorded coordinates', () => {
    const points = [pointOffsetByMeters(1, 0, 0), pointOffsetByMeters(2, 10, 0), pointOffsetByMeters(3, 0, 0)]
    const clusters = createRawGpsInspectionClusters()
    expect(clusters.getRenderedEntities(points, 16)).toHaveLength(1)
    const zoomed = clusters.getRenderedEntities(points, 22)
    expect(zoomed).toHaveLength(2)
    const overlapping = zoomed.find(entity => entity.isCluster)
    expect(overlapping.indices.sort()).toEqual([0, 2])
    expect(overlapping.latitude).toBe(points[0].latitude)
    expect(overlapping.longitude).toBe(points[0].longitude)
    const single = zoomed.find(entity => !entity.isCluster)
    expect(single.latitude).toBe(points[1].latitude)
    expect(single.longitude).toBe(points[1].longitude)
  })

  it('keeps hundreds of dense samples clustered instead of drawing a dot for every sample', () => {
    const points = Array.from({length: 467}, (_, index) => pointOffsetByMeters(index, index / 466 * 15, 0))
    const entities = createRawGpsInspectionClusters().getRenderedEntities(points, 18)
    expect(entities).toHaveLength(1)
    expect(entities[0].indices).toHaveLength(467)
  })
})

describe('raw GPS sample inspection', () => {
  it('synchronizes list and map inspection without selecting a boundary, including overlapping points', async () => {
    const group = groupRawGpsPoints([pointOffsetByMeters(1, 0, 0), pointOffsetByMeters(2, 0, 0)])[0]
    const onInspectPoint = vi.fn(), onSelectPoint = vi.fn()
    const popup = createRawGpsPopupMount(group, { timezone, onInspectPoint, onSelectPoint })
    popup.element.querySelectorAll('.raw-gps-stack-row')[1].click()
    await nextTick()
    expect(onInspectPoint).toHaveBeenLastCalledWith(group.points[1], 1)
    expect(popup.element.querySelectorAll('.raw-gps-stack-row')[1].getAttribute('aria-pressed')).toBe('true')
    expect(onSelectPoint).not.toHaveBeenCalled()
    popup.inspectPoint(0)
    await nextTick()
    expect(popup.element.querySelectorAll('.raw-gps-stack-row')[0].classList.contains('is-selected')).toBe(true)
    expect(onSelectPoint).not.toHaveBeenCalled()
    popup.unmount()
  })

  it('shows more timestamps and reveals and scrolls to a sample chosen beyond the first page', async () => {
    const group = groupRawGpsPoints(Array.from({ length: 170 }, (_, id) => ({
      id, latitude: 1, longitude: 2, timestamp: new Date(Date.UTC(2026, 0, 1, 0, 0, id)).toISOString()
    })))[0]
    const popup = createRawGpsPopupMount(group, { timezone })
    expect(popup.element.querySelectorAll('.raw-gps-stack-row')).toHaveLength(80)
    Array.from(popup.element.querySelectorAll('button')).find(button => button.textContent === 'Show more').click()
    await nextTick()
    expect(popup.element.querySelectorAll('.raw-gps-stack-row')).toHaveLength(160)
    const bounds = vi.spyOn(Element.prototype, 'getBoundingClientRect').mockImplementation(function () {
      return this.dataset.pointIndex === '169' ? { top: 500, bottom: 550 } : { top: 0, bottom: 300 }
    })
    try {
      popup.inspectPoint(169)
      await nextTick(); await nextTick()
      expect(popup.element.querySelectorAll('.raw-gps-stack-row')).toHaveLength(170)
      expect(popup.element.querySelector('[data-point-index="169"]').classList.contains('is-selected')).toBe(true)
      expect(popup.element.querySelector('.raw-gps-stack-list').scrollTop).toBe(250)
      expect(popup.element.textContent).not.toContain('Show more')
    } finally {
      bounds.mockRestore()
      popup.unmount()
    }
  })

  it('keeps the selected timestamp visible when the list is resized without scrolling the editor', async () => {
    let onResize
    const disconnect = vi.fn()
    vi.stubGlobal('ResizeObserver', class {
      constructor(callback) { onResize = callback }
      observe() {}
      disconnect() { disconnect() }
    })
    const popup = createRawGpsPopupMount(groupRawGpsPoints([pointOffsetByMeters(1, 0, 0), pointOffsetByMeters(2, 0, 0)])[0], { timezone })
    await nextTick()
    const list = popup.element.querySelector('.raw-gps-stack-list')
    vi.spyOn(list, 'getBoundingClientRect').mockReturnValue({ top: 0, bottom: 100 })
    vi.spyOn(list.firstElementChild, 'getBoundingClientRect').mockReturnValue({ top: 150, bottom: 180 })
    onResize()
    expect(list.scrollTop).toBe(80)
    expect(popup.element.scrollTop).toBe(0)
    popup.unmount()
    expect(disconnect).toHaveBeenCalled()
    vi.unstubAllGlobals()
  })

  it('updates boundary-selection availability without resetting inspection', async () => {
    const group = groupRawGpsPoints([pointOffsetByMeters(1, 0, 0), pointOffsetByMeters(2, 1, 1)])[0]
    const popup = createRawGpsPopupMount(group, { timezone })
    popup.inspectPoint(1)
    const select = vi.fn()
    popup.setSelectPoint(select)
    await nextTick()
    Array.from(popup.element.querySelectorAll('button')).find(button => button.textContent === 'Select this time').click()
    expect(select).toHaveBeenCalledWith(group.points[1])
    popup.setSelectPoint(null)
    await nextTick()
    expect(popup.element.textContent).not.toContain('Select this time')
    expect(popup.element.querySelector('[data-point-index="1"]').classList.contains('is-selected')).toBe(true)
    popup.unmount()
  })
})
