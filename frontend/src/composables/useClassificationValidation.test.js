import { ref } from 'vue'
import { describe, expect, it } from 'vitest'
import { useClassificationValidation } from './useClassificationValidation'

const flightMessages = (prefs) =>
  useClassificationValidation(ref(prefs)).getWarningMessagesForType('flight').value

describe('useClassificationValidation flight warnings', () => {
  it('does not warn for default flight thresholds', () => {
    expect(flightMessages({
      flightEnabled: true,
      flightMinAvgSpeed: 400,
      flightMinMaxSpeed: 500,
      flightMinDistanceKm: 0,
      trainEnabled: true,
      trainMaxAvgSpeed: 150,
      trainMaxMaxSpeed: 180
    })).toEqual([])
  })

  it('does not warn when flight detection is disabled', () => {
    expect(flightMessages({
      flightEnabled: false,
      flightMinMaxSpeed: 150,
      flightMinDistanceKm: 0,
      trainEnabled: true,
      trainMaxMaxSpeed: 180
    })).toEqual([])
  })

  it('warns when flight min peak speed overlaps train max peak speed', () => {
    const messages = flightMessages({
      flightEnabled: true,
      flightMinAvgSpeed: 150,
      flightMinMaxSpeed: 150,
      flightMinDistanceKm: 10,
      trainEnabled: true,
      trainMaxAvgSpeed: 120,
      trainMaxMaxSpeed: 180
    })

    expect(messages).toHaveLength(1)
    expect(messages[0]).toContain('train max peak speed (180 km/h)')
  })

  it('warns about a low peak threshold only when no minimum distance is set', () => {
    const prefs = {
      flightEnabled: true,
      flightMinAvgSpeed: 150,
      flightMinMaxSpeed: 200,
      trainEnabled: false
    }

    const withoutDistance = flightMessages({ ...prefs, flightMinDistanceKm: 0 })
    expect(withoutDistance).toHaveLength(1)
    expect(withoutDistance[0]).toContain('no minimum flight distance is set')

    expect(flightMessages({ ...prefs, flightMinDistanceKm: 5 })).toEqual([])
  })

  it('treats a missing minimum distance as no minimum', () => {
    const messages = flightMessages({
      flightEnabled: true,
      flightMinAvgSpeed: 100,
      flightMinMaxSpeed: 150
    })

    expect(messages).toHaveLength(1)
    expect(messages[0]).toContain('no minimum flight distance is set')
  })

  it('warns about a low altitude minimum height only when altitude detection is enabled', () => {
    const prefs = {
      flightEnabled: true,
      flightMinAvgSpeed: 400,
      flightMinMaxSpeed: 500,
      flightMinDistanceKm: 0,
      trainEnabled: false,
      flightAltitudeMinHeightMeters: 50
    }

    const enabled = flightMessages({ ...prefs, flightAltitudeDetectionEnabled: true })
    expect(enabled).toHaveLength(1)
    expect(enabled[0]).toContain('minimum height (50 m) is low')

    expect(flightMessages({ ...prefs, flightAltitudeDetectionEnabled: false })).toEqual([])
    expect(flightMessages({ ...prefs, flightEnabled: false, flightAltitudeDetectionEnabled: true })).toEqual([])
  })

  it('does not warn for the default altitude minimum height', () => {
    expect(flightMessages({
      flightEnabled: true,
      flightMinAvgSpeed: 400,
      flightMinMaxSpeed: 500,
      flightMinDistanceKm: 0,
      trainEnabled: false,
      flightAltitudeDetectionEnabled: true,
      flightAltitudeMinHeightMeters: 300
    })).toEqual([])
  })
})
