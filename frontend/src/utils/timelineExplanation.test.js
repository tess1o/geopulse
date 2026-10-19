import { describe, expect, it } from 'vitest'
import { i18n, loadLocale } from '@/locales'
import { explanationLines } from './timelineExplanation'

const formatters = {
  t: (key, params) => i18n.global.t(key, params),
  te: key => i18n.global.te(key),
  formatDuration: seconds => `${seconds}s`,
  formatDistance: meters => `${meters} m`,
  formatTime: value => `at ${value}`
}

describe('explanationLines', () => {
  it('explains a stay from start to end', () => {
    const lines = explanationLines({
      start: { code: 'ARRIVAL', params: { stopSeconds: 90, points: 4, stopSpeedKmh: 7.2 } },
      notes: [{ code: 'CONFIRMED', params: { radiusMeters: 50, minMinutes: 7 } }],
      end: { code: 'MOVED_AWAY', params: { distanceMeters: 230, radiusMeters: 50 } }
    }, formatters)

    expect(lines).toHaveLength(3)
    expect(lines[0]).toContain('90s')
    expect(lines[1]).toContain('50 m')
    expect(lines[1]).toContain('7')
    expect(lines[2]).toContain('230 m')
  })

  it('picks the sentence for the direction of a water transition', () => {
    const [toWater] = explanationLines({ start: { code: 'WATER_TRANSITION', params: { toWater: true } } }, formatters)
    const [toLand] = explanationLines({ start: { code: 'WATER_TRANSITION', params: { toWater: false } } }, formatters)
    expect(toWater).not.toEqual(toLand)
  })

  it('leaves out codes it has no sentence for', () => {
    expect(explanationLines({ start: { code: 'SOMETHING_NEW' } }, formatters)).toEqual([])
    expect(explanationLines(null, formatters)).toEqual([])
  })

  it('has a sentence for every reason code the engine records', async () => {
    await loadLocale('uk')
    const codes = {
      start: ['FIRST_POINT', 'AFTER_DATA_GAP', 'ARRIVAL', 'SPARSE_STOP', 'LEFT_STAY', 'SHORT_STOP', 'WATER_TRANSITION_TO_WATER',
        'WATER_TRANSITION_TO_LAND', 'INFERRED_DURING_GAP', 'INFERRED_TRIP_DURING_GAP', 'CONTINUITY', 'NO_POINTS', 'NO_POINTS_SINCE_LAST',
        'STOPPED_BEFORE_GAP', 'MANUAL_SPLIT', 'MANUAL_GAP_STAY'],
      note: ['CONFIRMED', 'FAVORITE_AREA', 'CONTINUED_OVER_GAP', 'MERGED', 'JOINED_SEGMENTS', 'SHORT_STOP', 'MANUAL_SPLIT', 'MANUAL_GAP_STAY'],
      end: ['MOVED_AWAY', 'ARRIVAL', 'SPARSE_STOP', 'DATA_GAP', 'LAST_POINT', 'WATER_TRANSITION_TO_WATER', 'WATER_TRANSITION_TO_LAND',
        'STOPPED_BEFORE_GAP']
    }
    for (const locale of ['en', 'uk']) {
      for (const [section, list] of Object.entries(codes)) {
        for (const code of list) {
          expect(i18n.global.te(`timeline.explanation.${section}.${code}`, locale), `${locale} ${section}.${code}`).toBe(true)
        }
      }
    }
  })
})
