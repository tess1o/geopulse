import { describe, expect, it } from 'vitest'
import { getScaleBarUnit } from './mapEngineUtils'

describe('scale bar unit', () => {
  it('uses metric for kilometre users', () => {
    expect(getScaleBarUnit('KILOMETERS')).toBe('metric')
  })

  it('uses imperial for mile users', () => {
    expect(getScaleBarUnit('MILES')).toBe('imperial')
  })

  it('falls back to metric when the unit is missing', () => {
    expect(getScaleBarUnit(undefined)).toBe('metric')
    expect(getScaleBarUnit(null)).toBe('metric')
  })
})
