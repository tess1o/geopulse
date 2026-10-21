import { describe, expect, it } from 'vitest'
import { DAY_PALETTES, HEATMAP_GRADIENTS, getColorDistance } from './mapAppearance'
import {
  DAY_COLOR_MODES,
  MIN_DISTANCE_FROM_ACTIVE_COLOR,
  buildDayColors,
  splitPathGroupsByDay
} from './pathDayColoring'

const point = (timestamp, latitude = 50, longitude = 30) => ({ timestamp, latitude, longitude })

const DAYS = [
  { dayKey: '2026-10-12', startMs: Date.parse('2026-10-12T00:00:00Z'), color: '#111111' },
  { dayKey: '2026-10-13', startMs: Date.parse('2026-10-13T00:00:00Z'), color: '#222222' },
  { dayKey: '2026-10-14', startMs: Date.parse('2026-10-14T00:00:00Z'), color: '#333333' }
]

const dayKeys = (count) => Array.from({ length: count }, (_, index) => `2026-10-${String(index + 1).padStart(2, '0')}`)

describe('buildDayColors', () => {
  it('gives each day of a short range its own palette color, in range order', () => {
    const result = buildDayColors(dayKeys(3), {
      dayPalette: DAY_PALETTES.OKABE_ITO,
      heatmapGradient: HEATMAP_GRADIENTS.CLASSIC,
      activePathColor: '#ef4444'
    })

    expect(result.mode).toBe(DAY_COLOR_MODES.DISTINCT)
    expect(result.days).toEqual([
      { dayKey: '2026-10-01', color: DAY_PALETTES.OKABE_ITO[0] },
      { dayKey: '2026-10-02', color: DAY_PALETTES.OKABE_ITO[1] },
      { dayKey: '2026-10-03', color: DAY_PALETTES.OKABE_ITO[2] }
    ])
  })

  it('returns null for a single day', () => {
    expect(buildDayColors(['2026-10-01'], { dayPalette: DAY_PALETTES.OKABE_ITO })).toBeNull()
    expect(buildDayColors([], { dayPalette: DAY_PALETTES.OKABE_ITO })).toBeNull()
  })

  it('skips palette colors that could pass for a custom selected-trip color', () => {
    const result = buildDayColors(dayKeys(6), {
      dayPalette: DAY_PALETTES.OKABE_ITO,
      heatmapGradient: HEATMAP_GRADIENTS.CLASSIC,
      activePathColor: '#d55e00'
    })

    expect(result.mode).toBe(DAY_COLOR_MODES.DISTINCT)
    expect(result.days.map((day) => day.color)).not.toContain('#D55E00')
    result.days.forEach(({ color }) => {
      expect(getColorDistance(color, '#d55e00')).toBeGreaterThanOrEqual(MIN_DISTANCE_FROM_ACTIVE_COLOR)
    })
  })

  it('drops the Okabe-Ito orange next to the red-green safe amber selection color', () => {
    const result = buildDayColors(dayKeys(6), {
      dayPalette: DAY_PALETTES.OKABE_ITO,
      heatmapGradient: HEATMAP_GRADIENTS.CIVIDIS,
      activePathColor: '#ffb000'
    })

    expect(result.days.map((day) => day.color)).not.toContain('#E69F00')
  })

  it('runs longer ranges along the gradient, oldest to newest, clear of the selected-trip color', () => {
    const result = buildDayColors(dayKeys(30), {
      dayPalette: DAY_PALETTES.OKABE_ITO,
      heatmapGradient: HEATMAP_GRADIENTS.CLASSIC,
      activePathColor: '#ef4444'
    })

    expect(result.mode).toBe(DAY_COLOR_MODES.GRADIENT)
    expect(result.days).toHaveLength(30)
    expect(result.days[0].color).toBe(HEATMAP_GRADIENTS.CLASSIC[0.0])
    // The red end of the classic gradient is too close to the default selection red, so it is cut off.
    result.days.forEach(({ color }) => {
      expect(getColorDistance(color, '#ef4444')).toBeGreaterThanOrEqual(MIN_DISTANCE_FROM_ACTIVE_COLOR)
    })
  })

  it('switches to the gradient once the remaining palette is too short', () => {
    // Okabe-Ito loses its orange next to the red-green safe amber, leaving six colors.
    const options = { dayPalette: DAY_PALETTES.OKABE_ITO, heatmapGradient: HEATMAP_GRADIENTS.CIVIDIS, activePathColor: '#ffb000' }
    expect(buildDayColors(dayKeys(6), options).mode).toBe(DAY_COLOR_MODES.DISTINCT)
    expect(buildDayColors(dayKeys(7), options).mode).toBe(DAY_COLOR_MODES.GRADIENT)
  })

  it('keeps colors tied to range position, not to which days have data', () => {
    const options = { dayPalette: DAY_PALETTES.OKABE_ITO, activePathColor: '#ef4444' }
    const week = buildDayColors(dayKeys(4), options)
    expect(week.days[3]).toEqual({ dayKey: '2026-10-04', color: DAY_PALETTES.OKABE_ITO[3] })
  })
})

describe('splitPathGroupsByDay', () => {
  it('cuts a group at midnight and starts the new day at the previous point, so there is no gap', () => {
    const before = point('2026-10-12T23:50:00Z')
    const lastOfDay = point('2026-10-12T23:58:00Z')
    const firstOfNext = point('2026-10-13T00:03:00Z')
    const after = point('2026-10-13T00:10:00Z')

    const [pieces] = splitPathGroupsByDay([[before, lastOfDay, firstOfNext, after]], DAYS)

    expect(pieces).toEqual([
      { dayKey: '2026-10-12', color: '#111111', points: [before, lastOfDay] },
      { dayKey: '2026-10-13', color: '#222222', points: [lastOfDay, firstOfNext, after] }
    ])
  })

  it('keeps one piece per group within a day and one result entry per group', () => {
    const groups = [
      [point('2026-10-12T08:00:00Z'), point('2026-10-12T09:00:00Z')],
      [point('2026-10-14T08:00:00Z'), point('2026-10-14T09:00:00Z')]
    ]

    const result = splitPathGroupsByDay(groups, DAYS)

    expect(result).toHaveLength(2)
    expect(result[0].map((piece) => piece.dayKey)).toEqual(['2026-10-12'])
    expect(result[1].map((piece) => piece.dayKey)).toEqual(['2026-10-14'])
  })

  it('puts points outside the range on the first or last day', () => {
    const [pieces] = splitPathGroupsByDay([[
      point('2026-10-11T22:00:00Z'),
      point('2026-10-11T23:00:00Z'),
      point('2026-10-15T01:00:00Z')
    ]], DAYS)

    expect(pieces.map((piece) => piece.dayKey)).toEqual(['2026-10-12', '2026-10-14'])
  })

  it('drops single-point pieces and empty groups', () => {
    const lone = point('2026-10-12T23:59:00Z')
    const next = point('2026-10-13T00:01:00Z')

    const result = splitPathGroupsByDay([[lone, next], [], [point('2026-10-12T10:00:00Z')]], DAYS)

    expect(result[0]).toEqual([{ dayKey: '2026-10-13', color: '#222222', points: [lone, next] }])
    expect(result[1]).toEqual([])
    expect(result[2]).toEqual([])
  })

  it('accepts epoch-millisecond timestamps and keeps untimed points with the piece they follow', () => {
    const first = point(Date.parse('2026-10-12T10:00:00Z'))
    const untimed = point(null)
    const last = point(Date.parse('2026-10-12T11:00:00Z'))

    const [pieces] = splitPathGroupsByDay([[first, untimed, last]], DAYS)

    expect(pieces).toEqual([{ dayKey: '2026-10-12', color: '#111111', points: [first, untimed, last] }])
  })

  it('returns no pieces without days', () => {
    expect(splitPathGroupsByDay([[point('2026-10-12T10:00:00Z')]], [])).toEqual([])
  })
})
