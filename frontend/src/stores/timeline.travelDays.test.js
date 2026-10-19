import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import apiService from '../utils/apiService'
import { useTimezone } from '@/composables/useTimezone'
import { useTimelineStore } from './timeline'

vi.mock('../utils/apiService', () => ({
  default: {
    get: vi.fn()
  }
}))

// Travel days in "Local time at each place" mode, from the API response to the text on each timeline card. Mirrors
// tests/e2e/location-time-display.spec.js ("Local day boundaries"): the store fetches and filters, the grouping is
// TimelineContainer's, and the card text comes from the same helpers the cards call.
// September 2025: Paris CEST (UTC+2), Los Angeles PDT (UTC-7), New York EDT (UTC-4), Tokyo UTC+9, Honolulu HST (UTC-10).

const zone = (timezone, countryCode) => ({ timezone, status: 'RESOLVED', nearestCity: 'City', countryCode, distanceKm: 1 })
const PARIS = zone('Europe/Paris', 'FR')
const LOS_ANGELES = zone('America/Los_Angeles', 'US')
const NEW_YORK = zone('America/New_York', 'US')
const TOKYO = zone('Asia/Tokyo', 'JP')
const HONOLULU = zone('Pacific/Honolulu', 'US')

const HOUR = 3600
const stay = (locationName, timestamp, stayDuration, locationTimezone) => ({ locationName, timestamp, stayDuration, locationTimezone })
const trip = (timestamp, tripDuration, startLocationTimezone, endLocationTimezone) => ({
  timestamp, tripDuration, startLocationTimezone, endLocationTimezone, distanceMeters: 9100000, movementType: 'FLIGHT'
})
const gap = (startTime, endTime, startLocationTimezone, endLocationTimezone) => ({
  startTime, endTime, startLocationTimezone, endLocationTimezone
})

const EUROPE_TO_US = {
  stays: [
    stay('Hotel Louvre', '2025-09-21T06:00:00Z', 5 * HOUR, PARIS),
    stay('Downtown LA Hotel', '2025-09-21T22:30:00Z', 16.5 * HOUR, LOS_ANGELES),
    stay('Santa Monica Pier', '2025-09-22T23:00:00Z', 2 * HOUR, LOS_ANGELES)
  ],
  trips: [trip('2025-09-21T11:00:00Z', 11.5 * HOUR, PARIS, LOS_ANGELES)],
  dataGaps: []
}

const US_TO_EUROPE = {
  stays: [
    stay('Downtown LA Hotel', '2025-09-21T16:00:00Z', 7 * HOUR, LOS_ANGELES),
    stay('Hotel Louvre', '2025-09-22T10:30:00Z', 10 * HOUR, PARIS),
    stay('Left Bank Cafe', '2025-09-22T22:30:00Z', 1.5 * HOUR, PARIS)
  ],
  trips: [trip('2025-09-22T00:00:00Z', 10.5 * HOUR, LOS_ANGELES, PARIS)],
  dataGaps: []
}

describe('travel days in location time mode', () => {
  const tz = useTimezone()
  let store

  /** What TimelineContainer renders for a range of dates: the date groups with each card's key text. */
  const renderDays = (startDate, endDate) => {
    const { start, end } = tz.createDateRangeUtc(startDate, endDate)
    const items = store.timelineData || []
    return tz.getDateRangeArray(start, end)
      .map((date) => {
        const dayItems = items.filter((item) => tz.shouldItemAppearOnDate(item, date))
        const forceLabel = dayItems.some((item) => tz.isItemForeign(item))
        const cards = dayItems.map((item) => {
          const name = item.locationName || item.type
          if (tz.getTotalDaysSpanned(item) > 1) {
            return {
              name,
              overnight: true,
              timestamp: tz.getOvernightTimestampText(item, date, { forceLabel }),
              onThisDay: tz.getOvernightOnThisDayText(item, date)
            }
          }
          const card = {
            name,
            timestamp: tz.formatDateTimeDisplayAt(item.timestamp, tz.getItemStartLocationTimezone(item), { forceLabel })
          }
          if (item.type === 'dataGap') {
            card.end = tz.formatDateTimeDisplayAt(item.endTime, item.endLocationTimezone, { forceLabel })
          }
          return card
        })
        return { date, cards }
      })
      .filter((group) => group.cards.length > 0)
  }

  /** Loads the timeline the way TimelinePage does for the selected dates; returns the requested range. */
  const load = async (response, startDate, endDate = startDate) => {
    apiService.get.mockResolvedValue(response)
    const { start, end } = tz.createDateRangeUtc(startDate, endDate)
    await store.fetchMovementTimeline(start, end)
    const params = apiService.get.mock.calls.at(-1)[1]
    return { from: params.from, to: params.to }
  }

  beforeEach(() => {
    setActivePinia(createPinia())
    store = useTimelineStore()
    tz.setDateFormat('MDY')
    tz.setTimeFormat('24h')
    tz.setTimeDisplayMode('location')
    apiService.get.mockReset()
  })

  afterEach(() => {
    tz.setTimezone('UTC')
    tz.setTimeDisplayMode('profile')
  })

  describe('Europe to the US (westbound), profile Europe/Paris', () => {
    beforeEach(() => tz.setTimezone('Europe/Paris'))

    it('keeps the California evening on the travel day, a 33-hour day', async () => {
      const requested = await load(EUROPE_TO_US, '2025-09-21')

      expect(requested).toEqual({ from: '2025-09-19T20:00:00.000Z', to: '2025-09-22T23:59:59.999Z' })
      expect(store.localDayWindow).toEqual({ start: '2025-09-20T22:00:00.000Z', end: '2025-09-22T06:59:59.999Z' })
      expect(renderDays('2025-09-21', '2025-09-21')).toEqual([{
        date: '2025-09-21',
        cards: [
          { name: 'Hotel Louvre', timestamp: '09/21/2025 08:00 GMT+2' },
          { name: 'trip', timestamp: '09/21/2025 13:00 GMT+2' },
          { name: 'Downtown LA Hotel', overnight: true, timestamp: '09/21/2025, 15:30 PDT', onThisDay: '15:30 - 23:59 (8h 29m)' }
        ]
      }])
    })

    it('starts the next day at midnight in California', async () => {
      await load(EUROPE_TO_US, '2025-09-22')

      expect(store.localDayWindow).toEqual({ start: '2025-09-22T07:00:00.000Z', end: '2025-09-23T06:59:59.999Z' })
      expect(renderDays('2025-09-22', '2025-09-22')).toEqual([{
        date: '2025-09-22',
        cards: [
          { name: 'Downtown LA Hotel', overnight: true, timestamp: 'Continued from Sep 21, 15:30 PDT', onThisDay: '00:00 - 08:00 (8h)' },
          { name: 'Santa Monica Pier', timestamp: '09/22/2025 16:00 PDT' }
        ]
      }])
    })

    it('keeps a data gap during the flight on the travel day', async () => {
      await load({
        stays: [
          stay('Hotel Louvre', '2025-09-21T06:00:00Z', 5 * HOUR, PARIS),
          stay('Midtown Office', '2025-09-21T19:00:00Z', 8 * HOUR, NEW_YORK)
        ],
        trips: [],
        dataGaps: [gap('2025-09-21T11:00:00Z', '2025-09-21T19:00:00Z', PARIS, NEW_YORK)]
      }, '2025-09-21', '2025-09-22')

      expect(renderDays('2025-09-21', '2025-09-22')).toEqual([{
        date: '2025-09-21',
        cards: [
          { name: 'Hotel Louvre', timestamp: '09/21/2025 08:00 GMT+2' },
          { name: 'dataGap', timestamp: '09/21/2025 13:00 GMT+2', end: '09/21/2025 15:00 EDT' },
          { name: 'Midtown Office', timestamp: '09/21/2025 15:00 EDT' }
        ]
      }])
    })

    it('profile mode keeps Paris days and requests exactly the selected day', async () => {
      tz.setTimeDisplayMode('profile')
      const requested = await load(EUROPE_TO_US, '2025-09-21')

      expect(requested).toEqual({ from: '2025-09-20T22:00:00.000Z', to: '2025-09-21T21:59:59.999Z' })
      expect(store.localDayWindow).toBeNull()
      // The mock returns every item; the California stay starts on Sep 22 in Paris and is not shown on Sep 21.
      // The flight lands at 00:30 Paris time, so it is an overnight trip here.
      expect(renderDays('2025-09-21', '2025-09-21')).toEqual([{
        date: '2025-09-21',
        cards: [
          { name: 'Hotel Louvre', timestamp: '09/21/2025 08:00' },
          { name: 'trip', overnight: true, timestamp: '09/21/2025, 13:00', onThisDay: '13:00 - 23:59 (10h 59m)' }
        ]
      }])
    })
  })

  describe('the US to Europe (eastbound), profile America/Los_Angeles', () => {
    beforeEach(() => tz.setTimezone('America/Los_Angeles'))

    it('splits an overnight flight at midnight on the departure clock', async () => {
      await load(US_TO_EUROPE, '2025-09-21', '2025-09-22')

      expect(renderDays('2025-09-21', '2025-09-22')).toEqual([
        {
          date: '2025-09-21',
          cards: [
            { name: 'Downtown LA Hotel', timestamp: '09/21/2025 09:00 PDT' },
            { name: 'trip', overnight: true, timestamp: '09/21/2025, 17:00 PDT', onThisDay: '17:00 - 23:59 (6h 59m)' }
          ]
        },
        {
          date: '2025-09-22',
          cards: [
            { name: 'trip', overnight: true, timestamp: 'Continued from Sep 21, 17:00 PDT', onThisDay: '00:00 - 12:30 (3h 30m)' },
            { name: 'Hotel Louvre', timestamp: '09/22/2025 12:30 GMT+2' }
          ]
        }
      ])
    })

    it('starts the arrival day mid-flight and ends it at midnight in Paris, a 15-hour day', async () => {
      const requested = await load(US_TO_EUROPE, '2025-09-22')

      expect(requested).toEqual({ from: '2025-09-21T05:00:00.000Z', to: '2025-09-24T08:59:59.999Z' })
      expect(store.localDayWindow).toEqual({ start: '2025-09-22T07:00:00.000Z', end: '2025-09-22T21:59:59.999Z' })
      expect(store.timelineData.map((item) => item.locationName || item.type)).toEqual(['trip', 'Hotel Louvre'])

      await load(US_TO_EUROPE, '2025-09-23')
      expect(renderDays('2025-09-23', '2025-09-23')).toEqual([{
        date: '2025-09-23',
        cards: [{ name: 'Left Bank Cafe', timestamp: '09/23/2025 00:30 GMT+2' }]
      }])
    })

    it('profile mode keeps Los Angeles days', async () => {
      tz.setTimeDisplayMode('profile')
      await load(US_TO_EUROPE, '2025-09-22')

      expect(renderDays('2025-09-22', '2025-09-22')).toEqual([{
        date: '2025-09-22',
        cards: [
          { name: 'trip', overnight: true, timestamp: 'Continued from Sep 21, 17:00', onThisDay: '00:00 - 03:30 (3h 30m)' },
          { name: 'Hotel Louvre', timestamp: '09/22/2025 03:30' },
          { name: 'Left Bank Cafe', timestamp: '09/22/2025 15:30' }
        ]
      }])
    })

    it('splits a data gap during the flight at midnight in New York', async () => {
      tz.setTimezone('America/New_York')
      await load({
        stays: [
          stay('Midtown Office', '2025-09-21T14:00:00Z', 8 * HOUR, NEW_YORK),
          stay('Hotel Louvre', '2025-09-22T08:00:00Z', 4 * HOUR, PARIS)
        ],
        trips: [],
        dataGaps: [gap('2025-09-21T22:00:00Z', '2025-09-22T08:00:00Z', NEW_YORK, PARIS)]
      }, '2025-09-21', '2025-09-22')

      expect(renderDays('2025-09-21', '2025-09-22')).toEqual([
        {
          date: '2025-09-21',
          cards: [
            { name: 'Midtown Office', timestamp: '09/21/2025 10:00 EDT' },
            { name: 'dataGap', overnight: true, timestamp: '09/21/2025, 18:00 EDT', onThisDay: '18:00 - 23:59 (5h 59m)' }
          ]
        },
        {
          date: '2025-09-22',
          cards: [
            { name: 'dataGap', overnight: true, timestamp: 'Continued from Sep 21, 18:00 EDT', onThisDay: '00:00 - 10:00 (4h)' },
            { name: 'Hotel Louvre', timestamp: '09/22/2025 10:00 GMT+2' }
          ]
        }
      ])
    })
  })

  describe('across the date line', () => {
    it('Tokyo to Honolulu: landing on an earlier date keeps everything on the departure day, a 43-hour day', async () => {
      tz.setTimezone('Asia/Tokyo')
      const response = {
        stays: [
          stay('Shinjuku Hotel', '2025-09-21T00:00:00Z', HOUR, TOKYO),
          stay('Waikiki Hotel', '2025-09-21T08:00:00Z', 10 * HOUR, HONOLULU),
          stay('Diamond Head Lookout', '2025-09-21T19:00:00Z', 3 * HOUR, HONOLULU)
        ],
        trips: [trip('2025-09-21T01:00:00Z', 7 * HOUR, TOKYO, HONOLULU)],
        dataGaps: []
      }

      await load(response, '2025-09-20', '2025-09-22')
      expect(renderDays('2025-09-20', '2025-09-22')).toEqual([{
        date: '2025-09-21',
        cards: [
          { name: 'Shinjuku Hotel', timestamp: '09/21/2025 09:00 GMT+9' },
          { name: 'trip', timestamp: '09/21/2025 10:00 GMT+9' },
          { name: 'Waikiki Hotel', timestamp: '09/20/2025 22:00 HST' },
          { name: 'Diamond Head Lookout', timestamp: '09/21/2025 09:00 HST' }
        ]
      }])

      await load(response, '2025-09-21')
      expect(store.localDayWindow).toEqual({ start: '2025-09-20T15:00:00.000Z', end: '2025-09-22T09:59:59.999Z' })
    })

    it('Honolulu to Tokyo: a flight that skips a date without passing midnight stays on its departure day', async () => {
      tz.setTimezone('Pacific/Honolulu')
      const response = {
        stays: [
          stay('Waikiki Hotel', '2025-09-20T20:00:00Z', 4 * HOUR, HONOLULU),
          stay('Shinjuku Hotel', '2025-09-21T08:00:00Z', 3 * HOUR, TOKYO)
        ],
        // Take-off at 14:00 HST is already 09:00 on Sep 21 in Tokyo; landing at 17:00 JST is 22:00 HST on Sep 20
        trips: [trip('2025-09-21T00:00:00Z', 8 * HOUR, HONOLULU, TOKYO)],
        dataGaps: []
      }

      await load(response, '2025-09-20', '2025-09-21')
      expect(renderDays('2025-09-20', '2025-09-21')).toEqual([
        {
          date: '2025-09-20',
          cards: [
            { name: 'Waikiki Hotel', timestamp: '09/20/2025 10:00 HST' },
            { name: 'trip', timestamp: '09/20/2025 14:00 HST' }
          ]
        },
        {
          date: '2025-09-21',
          cards: [{ name: 'Shinjuku Hotel', timestamp: '09/21/2025 17:00 GMT+9' }]
        }
      ])

      // Sep 20 runs until landing; Sep 21 starts there instead of at the earlier midnight in Tokyo
      await load(response, '2025-09-20')
      expect(store.localDayWindow).toEqual({ start: '2025-09-20T10:00:00.000Z', end: '2025-09-21T07:59:59.999Z' })
      await load(response, '2025-09-21')
      expect(store.localDayWindow).toEqual({ start: '2025-09-21T08:00:00.000Z', end: '2025-09-21T14:59:59.999Z' })
    })
  })
})
