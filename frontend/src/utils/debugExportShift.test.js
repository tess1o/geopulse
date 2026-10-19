import { describe, expect, it } from 'vitest'
import { generateLongitudeShift } from './debugExportShift'

describe('generateLongitudeShift', () => {
  it('moves far enough east or west to hide the location', () => {
    expect(generateLongitudeShift(() => 0)).toBe(-30)
    expect(generateLongitudeShift(sequence(0, 0.9))).toBe(30)
    expect(generateLongitudeShift(sequence(0.999999, 0.9))).toBeCloseTo(180, 3)
  })

  it('stays within a full turn and keeps six decimals', () => {
    for (let i = 0; i < 1000; i++) {
      const shift = generateLongitudeShift()
      expect(Math.abs(shift)).toBeGreaterThanOrEqual(30)
      expect(Math.abs(shift)).toBeLessThanOrEqual(180)
      expect(Number(shift.toFixed(6))).toBe(shift)
    }
  })
})

const sequence = (...values) => {
  let index = 0
  return () => values[index++]
}
