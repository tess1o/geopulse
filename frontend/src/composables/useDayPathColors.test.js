import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { useTimezone } from '@/composables/useTimezone'
import { useDayPathColors } from '@/composables/useDayPathColors'
import { DAY_PALETTES, resolveMapAppearance } from '@/maps/shared/mapAppearance'

const point = (timestamp) => ({ timestamp, latitude: 50.45, longitude: 30.52 })

describe('useDayPathColors', () => {
  const tz = useTimezone()

  beforeEach(() => {
    tz.setTimezone('Europe/Kyiv')
  })

  afterEach(() => {
    tz.setTimezone('UTC')
  })

  // Kyiv leaves summer time on 2026-10-25 (UTC+3 -> UTC+2), so that day is 25 hours long.
  const dayRange = ref(['2026-10-23T21:00:00Z', '2026-10-26T21:59:59Z'])
  const appearance = ref(resolveMapAppearance({}))

  it('colors the days of the selected range in the profile timezone, across a DST change', () => {
    const lateOn25th = point('2026-10-25T21:30:00Z') // 23:30 in Kyiv after the clocks went back
    const earlyOn26th = point('2026-10-25T22:30:00Z') // 00:30 in Kyiv
    const pathData = ref([[point('2026-10-25T20:00:00Z'), lateOn25th, earlyOn26th]])

    const { isMultiDay, dayColorMap, pathPieces } = useDayPathColors({
      pathData,
      dayRange,
      enabled: ref(true),
      appearance
    })

    expect(isMultiDay.value).toBe(true)
    expect(dayColorMap.value).toEqual({
      '2026-10-24': DAY_PALETTES.CLASSIC[0],
      '2026-10-25': DAY_PALETTES.CLASSIC[1],
      '2026-10-26': DAY_PALETTES.CLASSIC[2]
    })
    expect(pathPieces.value).toHaveLength(1)
    expect(pathPieces.value[0].map((piece) => piece.dayKey)).toEqual(['2026-10-25', '2026-10-26'])
    expect(pathPieces.value[0][1].points).toEqual([lateOn25th, earlyOn26th])
  })

  it('draws a single color when turned off or for a single day', () => {
    const pathData = ref([[point('2026-10-24T08:00:00Z'), point('2026-10-24T09:00:00Z')]])
    const enabled = ref(false)

    const { dayColors, pathPieces } = useDayPathColors({ pathData, dayRange, enabled, appearance })
    expect(dayColors.value).toBeNull()
    expect(pathPieces.value).toBeNull()

    const singleDay = useDayPathColors({
      pathData,
      dayRange: ref(['2026-10-23T21:00:00Z', '2026-10-24T20:59:59Z']),
      enabled: ref(true),
      appearance
    })
    expect(singleDay.isMultiDay.value).toBe(false)
    expect(singleDay.dayColors.value).toBeNull()
  })

  it('takes the days from the path when no range is given', () => {
    const pathData = ref([
      [point('2026-10-24T08:00:00Z'), point('2026-10-24T09:00:00Z')],
      [point('2026-10-25T08:00:00Z'), point('2026-10-25T09:00:00Z')]
    ])

    const { dayColorMap } = useDayPathColors({ pathData, dayRange: ref(null), enabled: ref(true), appearance })

    expect(Object.keys(dayColorMap.value)).toEqual(['2026-10-24', '2026-10-25'])
  })

  it('mutes the other days around a focused day, with or without day colors', () => {
    const pathData = ref([[
      point('2026-10-24T08:00:00Z'),
      point('2026-10-24T09:00:00Z'),
      point('2026-10-25T08:00:00Z'),
      point('2026-10-25T09:00:00Z')
    ]])
    const enabled = ref(false)
    const focusedDay = ref('2026-10-25')

    const { pathPieces, activeFocusedDay } = useDayPathColors({ pathData, dayRange, enabled, appearance, focusedDay })

    expect(activeFocusedDay.value).toBe('2026-10-25')
    expect(pathPieces.value[0].map(({ dayKey, color, muted }) => ({ dayKey, color, muted }))).toEqual([
      { dayKey: '2026-10-24', color: null, muted: true },
      { dayKey: '2026-10-25', color: null, muted: false }
    ])

    enabled.value = true
    expect(pathPieces.value[0][1]).toMatchObject({ color: DAY_PALETTES.CLASSIC[1], muted: false })

    focusedDay.value = null
    expect(pathPieces.value[0].every((piece) => piece.muted === undefined)).toBe(true)
  })

  it('ignores a focused day outside the range', () => {
    const pathData = ref([[point('2026-10-24T08:00:00Z'), point('2026-10-24T09:00:00Z')]])

    const { pathPieces, activeFocusedDay } = useDayPathColors({
      pathData,
      dayRange,
      enabled: ref(false),
      appearance,
      focusedDay: ref('2026-11-02')
    })

    expect(activeFocusedDay.value).toBeNull()
    expect(pathPieces.value).toBeNull()
  })
})
