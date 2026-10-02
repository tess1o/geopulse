import { describe, expect, it } from 'vitest'
import { getSpeedBandThresholds } from './speedBandThresholds'

const unitLabels = { kmh: 'km/h', mph: 'mph' }

describe('speed band thresholds', () => {
  it('uses km/h for metric users', () => {
    expect(getSpeedBandThresholds('KILOMETERS', unitLabels)).toEqual({ unit: 'km/h', slowBelow: 10, mediumUpTo: 25 })
  })

  it('converts to whole mph for imperial users', () => {
    expect(getSpeedBandThresholds('MILES', unitLabels)).toEqual({ unit: 'mph', slowBelow: 6, mediumUpTo: 16 })
  })
})
