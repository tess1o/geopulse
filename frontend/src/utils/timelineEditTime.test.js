import { useTimezone } from '@/composables/useTimezone'
import { beforeEach, afterEach, describe, expect, it } from 'vitest'
import { parseTimelinePickerInput, timelinePickerToUtc } from './timelineEditTime'
const timezone = { getPrimeVueDatePickerFormat: () => 'mm/dd/yy', getTimeFormatPattern: seconds => seconds ? 'HH:mm:ss' : 'HH:mm' }
describe('typed timeline times', () => {
  it('accepts a 24-hour time without AM/PM', () => {
    const parsed = parseTimelinePickerInput({ target: { value: '10/01/2026 11:59' } }, timezone)
    expect(parsed.getHours()).toBe(11)
    expect(parsed.getMinutes()).toBe(59)
  })
  it('preserves seconds for adding a stop', () => {
    expect(parseTimelinePickerInput({ target: { value: '10/01/2026 11:59:12' } }, timezone, true).getSeconds()).toBe(12)
  })
  it('rejects incomplete or impossible dates', () => {
    expect(parseTimelinePickerInput({ target: { value: '10/01/2026 11:' } }, timezone)).toBeNull()
    expect(parseTimelinePickerInput({ target: { value: '02/30/2026 11:59' } }, timezone)).toBeNull()
  })
  it.each(['1:05 PM', '01:05 PM'])('uses the selected date order and 12-hour clock: %s', value => {
    const settings = { getPrimeVueDatePickerFormat: () => 'dd/mm/yy', getTimeFormatPattern: () => 'h:mm A' }
    expect(parseTimelinePickerInput({ target: { value: `31/10/2026 ${value}` } }, settings).getHours()).toBe(13)
  })
})

describe('timeline picker clock changes', () => {
  const conversion = useTimezone()
  beforeEach(() => conversion.setTimezone('America/New_York'))
  afterEach(() => conversion.setTimezone('UTC'))
  it.each([
    ['2026-03-08T01:59:12', '2026-03-08T06:59:12.000Z'],
    ['2026-03-08T03:00:00', '2026-03-08T07:00:00.000Z'],
    ['2026-11-01T00:59:00', '2026-11-01T04:59:00.000Z'],
    ['2026-11-01T02:00:00', '2026-11-01T07:00:00.000Z'],
    ['2026-09-16T00:00:12', '2026-09-16T04:00:12.000Z']
  ])('converts user wall time %s to an explicit instant', (wall, expected) => {
    expect(timelinePickerToUtc(new Date(wall), conversion)).toBe(expected)
  })
  it.each(['2026-03-08T02:30:00', '2026-11-01T01:30:00'])('rejects a newly entered uncertain time %s', wall => {
    const picker = parseTimelinePickerInput({ target: { value: wall.replace('T', ' ') } }, {
      getPrimeVueDatePickerFormat: () => 'yy-mm-dd', getTimeFormatPattern: () => 'HH:mm:ss'
    }, true)
    expect(timelinePickerToUtc(picker, conversion)).toBeNull()
  })
  it.each(['2026-11-01T05:30:00.000Z', '2026-11-01T06:30:00.000Z', '2026-11-01T06:30:00.123Z'])('keeps either existing repeated-hour boundary %s', original => {
    expect(timelinePickerToUtc(new Date(conversion.fromUtc(original).format('YYYY-MM-DDTHH:mm:ss.SSS')), conversion, original)).toBe(original)
  })
  it('rejects invalid or cleared input', () => {
    expect(timelinePickerToUtc(null, conversion)).toBeNull()
    expect(timelinePickerToUtc(new Date(NaN), conversion)).toBeNull()
  })
})
