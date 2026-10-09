import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { useTimezone } from '@/composables/useTimezone'

const resolved = (timezone, extra = {}) => ({
  timezone,
  status: 'RESOLVED',
  nearestCity: 'City',
  countryCode: 'XX',
  distanceKm: 3.2,
  ...extra
})

describe('useTimezone location-local time', () => {
  const tz = useTimezone()

  beforeEach(() => {
    tz.setTimezone('America/Chicago')
    tz.setDateFormat('YMD')
    tz.setTimeFormat('24h')
    tz.setTimeDisplayMode('profile')
  })

  afterEach(() => {
    tz.setTimezone('UTC')
    tz.setDateFormat('MDY')
    tz.setTimeDisplayMode('profile')
    tz.setLocationTimeHomeCountry(null)
  })

  describe('profile mode (default)', () => {
    it('formats exactly like the profile-timezone formatters, whatever zone the item carries', () => {
      const ts = '2025-07-10T13:00:00Z'
      const london = resolved('Europe/London')

      expect(tz.formatTimeAt(ts, london)).toBe(tz.formatTime(ts))
      expect(tz.formatTimeAt(ts, london, { withSeconds: true })).toBe(tz.formatTime(ts, { withSeconds: true }))
      expect(tz.formatDateDisplayAt(ts, london)).toBe(tz.formatDateDisplay(ts))
      expect(tz.formatDateTimeDisplayAt(ts, london)).toBe(tz.formatDateTimeDisplay(ts))
      expect(tz.formatDateTimeDisplayAt(ts, london)).toBe('2025-07-10 08:00')
    })

    it('shows no provenance hint', () => {
      expect(tz.getLocationTimezoneHint(resolved('Europe/London'))).toBe('')
      expect(tz.getLocationTimezoneHint(null)).toBe('')
    })

    it('keeps overnight start text unchanged', () => {
      const stay = { timestamp: '2025-07-10T13:00:00Z', stayDuration: 86400, locationTimezone: resolved('Europe/London') }
      const withoutZone = { ...stay, locationTimezone: undefined }

      expect(tz.getOvernightTimestampText(stay, '2025-07-10')).toBe(tz.getOvernightTimestampText(withoutZone, '2025-07-10'))
      expect(tz.getOvernightTimestampText(stay, '2025-07-11')).toBe(tz.getOvernightTimestampText(withoutZone, '2025-07-11'))
    })
  })

  describe('location mode', () => {
    beforeEach(() => {
      tz.setTimeDisplayMode('location')
    })

    it('shows the time in the item zone with a short zone label', () => {
      const text = tz.formatDateTimeDisplayAt('2025-07-10T13:00:00Z', resolved('Europe/London'))

      expect(text).toMatch(/^2025-07-10 14:00 \S+/)
      expect(tz.formatTimeAt('2025-07-10T13:00:00Z', resolved('Europe/London'))).toMatch(/^14:00 \S+/)
    })

    it('hides the label when the zone has the same offset as the profile timezone', () => {
      tz.setTimezone('Europe/Berlin')

      expect(tz.formatTimeAt('2025-07-10T13:00:00Z', resolved('Europe/Paris'))).toBe('15:00')
    })

    it('uses the local date when it differs from the profile day', () => {
      // 18:30 on Jul 10 in Chicago is 02:30 on Jul 11 in Kyiv.
      const ts = '2025-07-10T23:30:00Z'

      expect(tz.formatDateDisplayAt(ts, resolved('Europe/Kyiv'))).toBe('2025-07-11')
      expect(tz.formatDateTimeDisplayAt(ts, resolved('Europe/Kyiv'))).toMatch(/^2025-07-11 02:30 \S+/)
    })

    it('falls back to the profile timezone when the item has no resolved zone', () => {
      const ts = '2025-07-10T13:00:00Z'
      const unresolved = { status: 'BEYOND_MAX_DISTANCE', nearestCity: 'Far', countryCode: 'XX', distanceKm: 512 }

      expect(tz.formatDateTimeDisplayAt(ts, null)).toBe('2025-07-10 08:00')
      expect(tz.formatDateTimeDisplayAt(ts, undefined)).toBe('2025-07-10 08:00')
      expect(tz.formatDateTimeDisplayAt(ts, unresolved)).toBe('2025-07-10 08:00')
      expect(tz.formatDateTimeDisplayAt(ts, resolved('America/Chicago'))).toBe('2025-07-10 08:00')
    })

    it('shows the overnight start in the item zone while matching days in the profile timezone', () => {
      const stay = { timestamp: '2025-07-10T13:00:00Z', stayDuration: 86400, locationTimezone: resolved('Europe/London') }

      expect(tz.getOvernightTimestampText(stay, '2025-07-10')).toMatch(/^2025-07-10, 14:00 \S+/)
      expect(tz.getOvernightTimestampText(stay, '2025-07-11')).toContain('Jul 10, 14:00')
    })

    it('explains where the zone came from, or why it is missing', () => {
      const hint = tz.getLocationTimezoneHint(resolved('Europe/Warsaw', { nearestCity: 'Przemyśl', countryCode: 'PL' }))
      expect(hint).toContain('Europe/Warsaw')
      expect(hint).toContain('Przemyśl')

      expect(tz.getLocationTimezoneHint({ status: 'BEYOND_MAX_DISTANCE', nearestCity: 'Far', countryCode: 'XX', distanceKm: 512 }))
        .toContain('512')
      expect(tz.getLocationTimezoneHint({ status: 'NO_GEONAMES_DATA' })).toContain('GeoNames')
      expect(tz.getLocationTimezoneHint({ status: 'INVALID_TIMEZONE', nearestCity: 'Broken' })).toContain('Broken')
      expect(tz.getLocationTimezoneHint(null)).not.toBe('')
    })
  })

  describe('abroad labels and whole-day labels', () => {
    const sofia = resolved('Europe/Sofia', { countryCode: 'BG' })
    const kyivZone = resolved('Europe/Kyiv', { countryCode: 'UA' })
    const warsaw = resolved('Europe/Warsaw', { countryCode: 'PL' })
    // 07:55 in Sofia and Kyiv (both GMT+3 in summer), 06:55 in Warsaw.
    const ts = '2025-07-10T04:55:00Z'

    beforeEach(() => {
      tz.setTimezone('Europe/Kyiv')
      tz.setTimeDisplayMode('location')
    })

    it('labels a same-offset place in another country once the home country is known', () => {
      expect(tz.formatTimeAt(ts, sofia)).toBe('07:55')

      tz.setLocationTimeHomeCountry('ua')

      expect(tz.formatTimeAt(ts, sofia)).toMatch(/^07:55 \S+/)
      expect(tz.formatTimeAt(ts, warsaw)).toMatch(/^06:55 \S+/)
      expect(tz.formatTimeAt(ts, kyivZone)).toBe('07:55')
    })

    it('labels a home item when the caller forces it (mixed day on the timeline)', () => {
      tz.setLocationTimeHomeCountry('UA')

      expect(tz.formatDateTimeDisplayAt(ts, kyivZone, { forceLabel: true })).toMatch(/^2025-07-10 07:55 \S+/)
      expect(tz.getOvernightTimestampText(
        { timestamp: ts, stayDuration: 86400, locationTimezone: kyivZone }, '2025-07-10', { forceLabel: true }
      )).toMatch(/^2025-07-10, 07:55 \S+/)
    })

    it('never labels anything in profile mode, even when forced', () => {
      tz.setTimeDisplayMode('profile')
      tz.setLocationTimeHomeCountry('UA')

      expect(tz.formatTimeAt(ts, sofia, { forceLabel: true })).toBe(tz.formatTime(ts))
      expect(tz.isItemForeign({ timestamp: ts, stayDuration: 60, locationTimezone: warsaw })).toBe(false)
    })

    it('marks a stay, trip or gap as foreign when any of its zones is', () => {
      tz.setLocationTimeHomeCountry('UA')

      expect(tz.isItemForeign({ timestamp: ts, stayDuration: 600, locationTimezone: kyivZone })).toBe(false)
      expect(tz.isItemForeign({ timestamp: ts, stayDuration: 600, locationTimezone: sofia })).toBe(true)
      expect(tz.isItemForeign({
        timestamp: ts, tripDuration: 3600, startLocationTimezone: kyivZone, endLocationTimezone: warsaw
      })).toBe(true)
      expect(tz.isItemForeign({
        startTime: ts, endTime: '2025-07-10T08:00:00Z', startLocationTimezone: kyivZone, endLocationTimezone: kyivZone
      })).toBe(false)
    })
  })

  describe('calendar dates and request params', () => {
    it('formats a calendar date without shifting it west of UTC', () => {
      tz.setTimezone('America/New_York')

      expect(tz.formatCalendarDateDisplay('2026-10-07')).toBe('2026-10-07')
      expect(tz.formatCalendarDateDisplay('2026-10-07T00:00:00Z')).toBe('2026-10-07')
      expect(tz.formatDateDisplay('2026-10-07')).toBe('2026-10-06') // the bug this helper avoids
    })

    it('adds includeLocationTimezones only in location mode', () => {
      const params = { page: 0 }

      expect(tz.withLocationTimezoneParams(params)).toBe(params)
      expect(tz.withLocationTimezoneParams(undefined)).toBeUndefined()

      tz.setTimeDisplayMode('location')
      expect(tz.withLocationTimezoneParams(params)).toEqual({ page: 0, includeLocationTimezones: true })
      expect(tz.withLocationTimezoneParams(undefined)).toEqual({ includeLocationTimezones: true })
    })
  })
})
