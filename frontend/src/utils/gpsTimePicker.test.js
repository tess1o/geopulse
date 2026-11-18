import { describe, expect, it } from 'vitest'
import {
  buildGpsTimePickerPoints,
  formatGpsPickerDuration,
  gpsRangeForTimes,
  gpsRangeLine,
  gpsVisibleWindow,
  nearestGpsPointIndex,
  nearestSampleUnderClick,
  gpsTrackLines,
  isInGpsRange,
  selectLabelledIndices,
  setGpsBoundary,
  timeColor
} from './gpsTimePicker'

const at = (seconds, latitude = 53.959, longitude = -1.08) => ({
  latitude, longitude, timestamp: new Date(Date.UTC(2026, 10, 10, 10, 43, 0) + seconds * 1000).toISOString()
})

describe('buildGpsTimePickerPoints', () => {
  it('orders samples by time, numbers them from 1 and drops ones without time or coordinates', () => {
    const points = buildGpsTimePickerPoints([at(20), at(0), { latitude: 1, longitude: 2 }, { ...at(5), latitude: null }, at(10)])
    expect(points.map(point => [point.index, point.number, point.secondsSincePrevious])).toEqual([[0, 1, null], [1, 2, 10], [2, 3, 10]])
  })

  it('marks a pause well beyond the sampling rhythm as a gap, so arrival and departure passes are separated', () => {
    // Issue 661: samples every 5-10 s, then nothing for 6 min 17 s at the same corner.
    const seconds = [0, 5, 10, 15, 25, 30, 407, 412, 417]
    const points = buildGpsTimePickerPoints(seconds.map(value => at(value)))
    expect(points.filter(point => point.gapBefore).map(point => [point.number, point.secondsSincePrevious])).toEqual([[7, 377]])
  })

  it('never treats a pause under a minute as a gap, even with dense sampling', () => {
    const points = buildGpsTimePickerPoints([at(0), at(1), at(2), at(50)])
    expect(points.some(point => point.gapBefore)).toBe(false)
  })

  it('colours samples from early to late', () => {
    const points = buildGpsTimePickerPoints([at(0), at(10), at(20)])
    expect(points.map(point => point.color)).toEqual([timeColor(0), timeColor(0.5), timeColor(1)])
    expect(timeColor(0)).toBe('#14b8a6')
    expect(timeColor(1)).toBe('#c026d3')
  })
})

describe('gps track and range lines', () => {
  it('joins samples in time order and draws gaps separately', () => {
    const points = buildGpsTimePickerPoints([at(0, 1, 1), at(5, 2, 2), at(500, 3, 3), at(505, 4, 4)])
    expect(gpsTrackLines(points)).toEqual({ moving: [[[1, 1], [2, 2]], [[3, 3], [4, 4]]], gaps: [[[2, 2], [3, 3]]] })
  })

  it('draws the chosen range only when From is before To', () => {
    const points = buildGpsTimePickerPoints([at(0, 1, 1), at(5, 2, 2), at(10, 3, 3)])
    expect(gpsRangeLine(points, 0, 1)).toEqual([[1, 1], [2, 2]])
    expect(gpsRangeLine(points, 1, 1)).toEqual([])
    expect(gpsRangeLine(points, null, 2)).toEqual([])
    expect(isInGpsRange(1, { from: 0, to: 2 })).toBe(true)
    expect(isInGpsRange(1, { from: 0, to: null })).toBe(false)
  })
})

describe('setGpsBoundary', () => {
  it('keeps From before To by clearing the boundary it would cross', () => {
    expect(setGpsBoundary({ from: null, to: null }, 'from', 3)).toEqual({ from: 3, to: null })
    expect(setGpsBoundary({ from: 3, to: null }, 'to', 7)).toEqual({ from: 3, to: 7 })
    expect(setGpsBoundary({ from: 3, to: 7 }, 'from', 7)).toEqual({ from: 7, to: null })
    expect(setGpsBoundary({ from: 3, to: 7 }, 'to', 2)).toEqual({ from: null, to: 2 })
  })
})

describe('selectLabelledIndices', () => {
  it('skips labels that would overlap, but always keeps priority ones', () => {
    const items = [{ index: 0, x: 0, y: 0 }, { index: 1, x: 5, y: 0 }, { index: 2, x: 40, y: 0 }, { index: 3, x: 44, y: 0 }]
    expect([...selectLabelledIndices(items)]).toEqual([0, 2])
    expect([...selectLabelledIndices(items, { priority: [3] })].sort()).toEqual([0, 3])
  })
})

describe('formatGpsPickerDuration', () => {
  it('shows seconds for short pauses and drops them past an hour', () => {
    expect(formatGpsPickerDuration(45)).toBe('45 s')
    expect(formatGpsPickerDuration(377)).toBe('6 min 17 s')
    expect(formatGpsPickerDuration(7530)).toBe('2 h 5 min')
  })
})

describe('matching times to points', () => {
  const points = buildGpsTimePickerPoints([at(0), at(10), at(20), at(500)])
  const time = seconds => points[0].time + seconds * 1000

  it('finds the closest point within the allowed indices', () => {
    expect(nearestGpsPointIndex(points, time(14))).toBe(1)
    expect(nearestGpsPointIndex(points, time(16))).toBe(2)
    expect(nearestGpsPointIndex(points, time(9999))).toBe(3)
    expect(nearestGpsPointIndex(points, time(14), { min: 2 })).toBe(2)
    expect(nearestGpsPointIndex(points, time(400), { max: 2 })).toBe(2)
    expect(nearestGpsPointIndex(points, null)).toBeNull()
  })

  it('shows a chosen time on a point only when it is within two minutes of one', () => {
    expect(gpsRangeForTimes(points, time(20), time(390))).toEqual({ from: 2, to: 3 })
    expect(gpsRangeForTimes(points, time(200), null)).toEqual({ from: null, to: null })
  })
})

describe('gpsVisibleWindow', () => {
  const every = (stepSeconds, count) => buildGpsTimePickerPoints(Array.from({ length: count }, (_, index) => at(index * stepSeconds)))
  const clock = (points, index) => (points[index].time - points[0].time) / 1000

  it('keeps From to To with ten minutes of context, not the rest of the day', () => {
    const day = every(60, 24 * 60)
    const window = gpsVisibleWindow(day, day[6 * 60].time, day[7 * 60].time)
    expect([clock(day, window.from), clock(day, window.to)]).toEqual([(6 * 60 - 10) * 60, (7 * 60 + 10) * 60])
  })

  it('keeps at least five points of context when sampling is sparse and at most fifty when dense', () => {
    const sparse = every(1800, 20)
    expect(gpsVisibleWindow(sparse, sparse[10].time, sparse[12].time)).toEqual({ from: 5, to: 17 })
    const dense = every(1, 3600)
    expect(gpsVisibleWindow(dense, dense[1000].time, dense[2000].time)).toEqual({ from: 950, to: 2050 })
  })

  it('shows everything on a side that is unset or expanded', () => {
    const points = every(60, 100)
    expect(gpsVisibleWindow(points, null, null)).toEqual({ from: 0, to: 99 })
    expect(gpsVisibleWindow(points, points[50].time, null)).toEqual({ from: 40, to: 99 })
    expect(gpsVisibleWindow(points, points[50].time, points[60].time, { showAllBefore: true })).toEqual({ from: 0, to: 70 })
    expect(gpsVisibleWindow([], 1, 2)).toEqual({ from: 0, to: -1 })
  })

  it('draws a narrowed track without reaching for a hidden point before its first gap', () => {
    const points = buildGpsTimePickerPoints([at(0, 1, 1), at(500, 2, 2), at(505, 3, 3)])
    expect(gpsTrackLines(points.slice(1))).toEqual({ moving: [[[2, 2], [3, 3]]], gaps: [] })
    expect(gpsRangeLine(points.slice(1), 1, 2)).toEqual([[2, 2], [3, 3]])
  })
})

describe('nearestSampleUnderClick', () => {
  it('picks the sample whose dot is nearest the click, whatever order the dots were drawn in', () => {
    const under = [{ index: 7, x: 105, y: 0 }, { index: 6, x: 100, y: 1 }, { index: 8, x: 110, y: 0 }]
    expect(nearestSampleUnderClick(under, { x: 100, y: 0 })).toBe(6)
    expect(nearestSampleUnderClick(under, { x: 111, y: 0 })).toBe(8)
  })

  it('has no sample when nothing is under the click', () => {
    expect(nearestSampleUnderClick([], { x: 0, y: 0 })).toBeNull()
  })
})
