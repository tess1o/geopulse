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

    it('shows the overnight start in the item zone', () => {
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

  describe('local day boundaries', () => {
    // Profile Europe/Paris (CEST, UTC+2); California is PDT (UTC-7) in October 2026.
    const paris = resolved('Europe/Paris', { countryCode: 'FR' })
    const la = resolved('America/Los_Angeles', { countryCode: 'US' })
    const tokyo = resolved('Asia/Tokyo', { countryCode: 'JP' })
    const honolulu = resolved('Pacific/Honolulu', { countryCode: 'US' })

    const stay = (timestamp, stayDuration, locationTimezone) => ({ type: 'stay', timestamp, stayDuration, locationTimezone })
    const trip = (timestamp, tripDuration, startLocationTimezone, endLocationTimezone) => ({
      type: 'trip', timestamp, tripDuration, startLocationTimezone, endLocationTimezone
    })

    // Westbound: Paris until 13:00 CEST, fly to SFO landing 15:30 PDT the same day, sleep in California.
    const parisMorning = () => stay('2026-10-17T20:00:00Z', 15 * 3600, paris)
    const westbound = () => trip('2026-10-18T11:00:00Z', 11.5 * 3600, paris, la)
    const californiaNight = () => stay('2026-10-18T22:30:00Z', 17.5 * 3600, la)
    // Eastbound: leave California 17:00 PDT on the 18th, land in Paris 12:30 CEST on the 19th.
    const eastbound = () => trip('2026-10-19T00:00:00Z', 10.5 * 3600, la, paris)

    beforeEach(() => {
      tz.setTimezone('Europe/Paris')
    })

    describe('location mode', () => {
      beforeEach(() => {
        tz.setTimeDisplayMode('location')
      })

      it('keeps a westbound travel day on one date until midnight in California', () => {
        const items = tz.annotateLocalDaySpans([parisMorning(), westbound(), californiaNight()])

        expect(tz.getItemLocalDaySpan(items[1])).toEqual({ startDate: '2026-10-18', endDate: '2026-10-18' })
        expect(tz.shouldItemAppearOnDate(items[2], '2026-10-18')).toBe(true)
        expect(tz.getItemDisplayType(items[2], '2026-10-18')).toBe('start')
        expect(tz.formatOnThisDayDuration(items[2], '2026-10-18', 'stay')).toMatch(/^15:30 - 23:59 /)
        expect(tz.getLocalDayWindowUtc(items, '2026-10-18', '2026-10-18')).toEqual({
          start: '2026-10-17T22:00:00.000Z', // 00:00 in Paris
          end: '2026-10-19T06:59:59.999Z' // 23:59:59.999 in California
        })
      })

      it('splits an eastbound overnight flight at midnight on the departure clock', () => {
        const flight = eastbound()

        expect(tz.getItemLocalDaySpan(flight)).toEqual({ startDate: '2026-10-18', endDate: '2026-10-19' })
        expect(tz.shouldShowAsOvernight(flight)).toBe(true)
        expect(tz.getItemDisplayType(flight, '2026-10-18')).toBe('start')
        expect(tz.getItemDisplayType(flight, '2026-10-19')).toBe('end')
        expect(tz.getTotalDaysSpanned(flight)).toBe(2)
        expect(tz.getItemDayBounds(flight, '2026-10-19').start.toISOString()).toBe('2026-10-19T07:00:00.000Z')
        expect(tz.formatOnThisDayDuration(flight, '2026-10-18', 'trip')).toMatch(/^17:00 - 23:59 /)
        expect(tz.formatOnThisDayDuration(flight, '2026-10-19', 'trip')).toMatch(/^00:00 - 12:30 /)
      })

      it('dates stays by midnight in their own zone, not the profile timezone', () => {
        const lateEvening = stay('2026-10-18T05:00:00Z', 4 * 3600, la) // 22:00 -> 02:00 PDT
        const afternoon = stay('2026-10-18T20:00:00Z', 3 * 3600, la) // 13:00 -> 16:00 PDT, crosses Paris midnight

        expect(tz.shouldShowAsOvernight(lateEvening)).toBe(true)
        expect(tz.getItemLocalDaySpan(lateEvening)).toEqual({ startDate: '2026-10-17', endDate: '2026-10-18' })
        expect(tz.shouldShowAsOvernight(afternoon)).toBe(false)
        expect(tz.shouldItemAppearOnDate(afternoon, '2026-10-19')).toBe(false)
      })

      it('never moves dates backwards across the date line', () => {
        const items = tz.annotateLocalDaySpans([
          trip('2026-10-19T01:00:00Z', 7 * 3600, tokyo, honolulu), // 10:00 JST 19th -> 22:00 HST 18th
          stay('2026-10-19T08:00:00Z', 10 * 3600, honolulu) // 22:00 HST 18th -> 08:00 HST 19th
        ])

        expect(tz.getItemLocalDaySpan(items[0])).toEqual({ startDate: '2026-10-19', endDate: '2026-10-19' })
        expect(tz.getItemLocalDaySpan(items[1])).toEqual({ startDate: '2026-10-19', endDate: '2026-10-19' })
        expect(tz.getItemDayBounds(items[1], '2026-10-19').start.toISOString()).toBe('2026-10-19T08:00:00.000Z')
      })

      it('keeps a flight on its departure day when it passes midnight on neither clock', () => {
        // Honolulu 14:00 Sep 20 (already 09:00 Sep 21 in Tokyo) -> Tokyo 17:00 Sep 21 (22:00 Sep 20 in Honolulu)
        const [, flight, shinjuku] = tz.annotateLocalDaySpans([
          stay('2025-09-20T20:00:00Z', 4 * 3600, honolulu),
          trip('2025-09-21T00:00:00Z', 8 * 3600, honolulu, tokyo),
          stay('2025-09-21T08:00:00Z', 3 * 3600, tokyo)
        ])

        expect(tz.getItemLocalDaySpan(flight)).toEqual({ startDate: '2025-09-20', endDate: '2025-09-20' })
        expect(tz.shouldShowAsOvernight(flight)).toBe(false)
        expect(tz.getItemLocalDaySpan(shinjuku)).toEqual({ startDate: '2025-09-21', endDate: '2025-09-21' })
      })

      it('starts a day no earlier than the previous date\'s last item ends', () => {
        const items = tz.annotateLocalDaySpans([
          trip('2025-09-21T00:00:00Z', 8 * 3600, honolulu, tokyo),
          stay('2025-09-21T08:00:00Z', 3 * 3600, tokyo)
        ])

        // Midnight on Sep 21 in Tokyo (Sep 20 15:00 UTC) came before take-off; the day starts at landing
        expect(tz.getLocalDayWindowUtc(items, '2025-09-21', '2025-09-21')).toEqual({
          start: '2025-09-21T08:00:00.000Z',
          end: '2025-09-21T14:59:59.999Z'
        })
      })

      it('pads the fetch range by the largest UTC offset gap', () => {
        expect(tz.getLocalDayFetchRange('2026-10-17T22:00:00.000Z', '2026-10-18T21:59:59.999Z')).toEqual({
          start: '2026-10-16T20:00:00.000Z',
          end: '2026-10-19T23:59:59.999Z'
        })
      })
    })

    describe('profile mode', () => {
      it('keeps profile-timezone days whatever zones the items carry', () => {
        const items = [parisMorning(), westbound(), californiaNight(), eastbound()]
        const withoutZones = items.map(({ locationTimezone, startLocationTimezone, endLocationTimezone, ...rest }) => rest)

        items.forEach((item, index) => {
          const plain = withoutZones[index]
          for (const date of ['2026-10-17', '2026-10-18', '2026-10-19']) {
            expect(tz.shouldItemAppearOnDate(item, date)).toBe(tz.shouldItemAppearOnDate(plain, date))
            expect(tz.getItemDisplayType(item, date)).toBe(tz.getItemDisplayType(plain, date))
            expect(tz.formatOnThisDayDuration(item, date, item.type)).toBe(tz.formatOnThisDayDuration(plain, date, plain.type))
          }
          expect(tz.shouldShowAsOvernight(item)).toBe(tz.shouldShowAsOvernight(plain))
        })

        // 00:30 CEST on the 19th: the California night starts on the 19th in profile mode
        expect(tz.shouldItemAppearOnDate(items[2], '2026-10-18')).toBe(false)
        expect(tz.getLocalDayFetchRange('a', 'b')).toEqual({ start: 'a', end: 'b' })
      })
    })
  })
})
