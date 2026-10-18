import { describe, expect, it } from 'vitest'
import {
  comparisonState,
  dayInZone,
  differingFields,
  formatReportDuration,
  sortedWarnings,
  timelineQuery
} from './debugImportReport'

describe('debug import report helpers', () => {
  it('summarises the comparison', () => {
    expect(comparisonState(null)).toBe('unavailable')
    expect(comparisonState({ available: false })).toBe('unavailable')
    expect(comparisonState({ available: true, matches: true })).toBe('match')
    expect(comparisonState({ available: true, matches: false })).toBe('differs')
  })

  it('formats durations compactly', () => {
    expect(formatReportDuration(45)).toBe('45s')
    expect(formatReportDuration(1063)).toBe('17m 43s')
    expect(formatReportDuration(103069)).toBe('28h 37m')
    expect(formatReportDuration(null)).toBe('')
  })

  it('finds the day in the exporting user timezone', () => {
    // 23:30 UTC is already the next day in Kyiv
    expect(dayInZone('2026-10-07T23:30:00Z', 'Europe/Kyiv')).toBe('2026-10-08')
    expect(dayInZone('2026-10-07T23:30:00Z', 'UTC')).toBe('2026-10-07')
  })

  it('opens the day of the problem, else the exported range', () => {
    expect(timelineQuery({ timezone: 'UTC', issue: { time: '2026-10-08T12:30:00Z' } }))
      .toEqual({ start: '2026-10-08', end: '2026-10-08' })
    expect(timelineQuery({
      timezone: 'UTC',
      issue: { time: null },
      requestedRange: { start: '2026-10-06T00:00:00Z', end: '2026-10-08T23:59:59Z' }
    })).toEqual({ start: '2026-10-06', end: '2026-10-08' })
    expect(timelineQuery({ timezone: 'UTC', issue: {} })).toBeNull()
  })

  it('puts the warnings that explain a difference first', () => {
    const codes = sortedWarnings([
      { code: 'EXISTING_DATA_KEPT' }, { code: 'SOMETHING_NEW' }, { code: 'TIMELINE_STALE' }, { code: 'TIMELINE_DIFFERS' }
    ]).map(warning => warning.code)
    expect(codes).toEqual(['TIMELINE_DIFFERS', 'TIMELINE_STALE', 'EXISTING_DATA_KEPT', 'SOMETHING_NEW'])
  })

  it('names the fields that differ', () => {
    const row = {
      status: 'DIFFERENT',
      expected: { end: 'a', durationSeconds: 10, movementType: 'CAR' },
      actual: { end: 'b', durationSeconds: 20, movementType: 'CAR' }
    }
    expect(differingFields(row)).toEqual(['end', 'durationSeconds'])
    expect(differingFields({ ...row, status: 'MATCH' })).toEqual([])
  })
})
