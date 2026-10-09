import { describe, expect, it, vi } from 'vitest'
import { buildTimelineStackItems } from '../timelineStackContent'
import {
  buildHighlightedTripPopupModel,
  buildTimelineItemPopupModel,
  buildTripEndpointPopupModel
} from './timelinePopupModels'
import { buildTripPlanItemPopupModel } from './tripPlanPopupModel'

const warsaw = { timezone: 'Europe/Warsaw', status: 'RESOLVED' }
const kyiv = { timezone: 'Europe/Kyiv', status: 'RESOLVED' }

// Same shape the map layers pass: (value, locationTimezone) => text.
const layerFormatter = vi.fn((value, locationTimezone) => `${value}@${locationTimezone?.timezone || 'profile'}`)

describe('map popups pass each item zone to the formatter', () => {
  it('stay, trip and data gap popups use their start zone', () => {
    expect(buildTimelineItemPopupModel(
      { type: 'stay', locationName: 'Office', timestamp: 'T1', locationTimezone: warsaw },
      { formatDateTimeDisplay: layerFormatter }
    ).subtitle).toBe('T1@Europe/Warsaw')

    expect(buildTimelineItemPopupModel(
      { type: 'trip', timestamp: 'T2', startLocationTimezone: kyiv, endLocationTimezone: warsaw },
      { formatDateTimeDisplay: layerFormatter }
    ).subtitle).toBe('T2@Europe/Kyiv')

    expect(buildTimelineItemPopupModel(
      { type: 'dataGap', startTime: 'T3', startLocationTimezone: warsaw },
      { formatDateTimeDisplay: layerFormatter }
    ).subtitle).toBe('T3@Europe/Warsaw')
  })

  it('highlighted trip and trip endpoints show the end in the destination zone', () => {
    const trip = {
      movementType: 'CAR',
      timestamp: '2026-07-24T10:00:00.000Z',
      tripDuration: 3600,
      distanceMeters: 1000,
      startLocationTimezone: kyiv,
      endLocationTimezone: warsaw
    }

    const rows = buildHighlightedTripPopupModel(trip, { formatDateTimeDisplay: layerFormatter, unit: 'KILOMETERS' }).rows
    expect(rows[0].value).toBe('2026-07-24T10:00:00.000Z@Europe/Kyiv')
    expect(rows[1].value).toBe('2026-07-24T11:00:00.000Z@Europe/Warsaw')

    const end = buildTripEndpointPopupModel(trip, 'end', { formatDateTimeDisplay: layerFormatter })
    expect(end.rows[0].value).toBe('2026-07-24T11:00:00.000Z@Europe/Warsaw')
  })

  it('stack rows format each item in its own zone when given formatItemDateTime', () => {
    const rows = buildTimelineStackItems([
      { type: 'stay', timestamp: 'S1', locationTimezone: warsaw, locationName: 'A' },
      { type: 'trip', timestamp: 'S2', startLocationTimezone: kyiv }
    ], {
      formatItemDateTime: (value, item) => layerFormatter(value, item.locationTimezone || item.startLocationTimezone)
    })

    expect(rows.map((row) => row.dateStr)).toEqual(['S1@Europe/Warsaw', 'S2@Europe/Kyiv'])
  })

  it('stack rows keep the date and time parts without formatItemDateTime', () => {
    const rows = buildTimelineStackItems([{ type: 'stay', timestamp: 'S1', locationName: 'A' }], {
      formatDateDisplay: () => '07/24/2026',
      formatTime: () => '14:30:00'
    })

    expect(rows[0].dateStr).toBe('07/24/2026 14:30:00')
  })
})

describe('trip plan popup', () => {
  it('formats the planned day as a calendar date, without timezone conversion', () => {
    const timezone = {
      formatCalendarDateDisplay: vi.fn(() => '10/07/2026'),
      formatDateDisplay: vi.fn(() => 'shifted')
    }

    const model = buildTripPlanItemPopupModel({ title: 'Museum', plannedDay: '2026-10-07' }, { timezone })

    expect(timezone.formatCalendarDateDisplay).toHaveBeenCalledWith('2026-10-07')
    expect(timezone.formatDateDisplay).not.toHaveBeenCalled()
    expect(JSON.stringify(model)).toContain('10/07/2026')
  })
})
