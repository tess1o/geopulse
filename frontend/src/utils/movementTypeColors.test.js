import { describe, expect, it } from 'vitest'
import { MOVEMENT_TYPES } from '@/constants/movementTypes'
import { MOVEMENT_TYPE_PALETTES, getMovementTypePalette } from './movementTypeColors'

describe('movement type palettes', () => {
  it.each(Object.keys(MOVEMENT_TYPE_PALETTES))('%s gives every movement type its own color', (key) => {
    const palette = MOVEMENT_TYPE_PALETTES[key]

    expect(Object.keys(palette).sort()).toEqual([...MOVEMENT_TYPES].sort())
    expect(new Set(Object.values(palette)).size).toBe(MOVEMENT_TYPES.length)
  })

  it('uses the color blind safe palette for every color vision scheme', () => {
    expect(getMovementTypePalette(undefined)).toBe(MOVEMENT_TYPE_PALETTES.DEFAULT)
    expect(getMovementTypePalette('DEFAULT')).toBe(MOVEMENT_TYPE_PALETTES.DEFAULT)
    for (const scheme of ['RED_GREEN_SAFE', 'BLUE_YELLOW_SAFE', 'HIGH_CONTRAST']) {
      expect(getMovementTypePalette(scheme)).toBe(MOVEMENT_TYPE_PALETTES.COLOR_BLIND_SAFE)
    }
  })
})
