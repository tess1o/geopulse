import { describe, expect, it } from 'vitest'
import {
  MOVEMENT_TYPES,
  getMovementTypeEmoji,
  getMovementTypeIcon,
  getMovementTypeSeverity
} from './movementTypes'

describe('movement type display', () => {
  it.each(MOVEMENT_TYPES)('%s has an emoji, icon and severity', (type) => {
    expect(getMovementTypeEmoji(type)).toBeTruthy()
    expect(getMovementTypeIcon(type)).toMatch(/^(pi|fas) /)
    expect(getMovementTypeSeverity(type)).toBeTruthy()
  })

  it('accepts lower case types', () => {
    expect(getMovementTypeIcon('car')).toBe('pi pi-car')
  })

  it('falls back to UNKNOWN for missing or unrecognised types', () => {
    for (const type of [null, undefined, '', 'HOVERCRAFT']) {
      expect(getMovementTypeEmoji(type)).toBe('❓')
      expect(getMovementTypeIcon(type)).toBe('pi pi-question-circle')
      expect(getMovementTypeSeverity(type)).toBe('secondary')
    }
  })
})
