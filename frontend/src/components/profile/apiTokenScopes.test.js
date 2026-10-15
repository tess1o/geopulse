import { describe, expect, it } from 'vitest'
import { presetForScopes, scopesForPreset, toggleCustomScope } from './apiTokenScopes'

describe('apiTokenScopes', () => {
  it('maps scopes back to the preset that created them', () => {
    expect(presetForScopes(['all'])).toBe('full')
    expect(presetForScopes(['read'])).toBe('readOnly')
    expect(presetForScopes(['gps:ingest', 'read'])).toBe('homeAssistant')
    expect(presetForScopes(['read', 'export'])).toBe('backup')
    expect(presetForScopes(['read', 'write'])).toBe('custom')
    expect(presetForScopes(undefined)).toBe('custom')
  })

  it('returns preset scopes, or the checked scopes in canonical order for custom', () => {
    expect(scopesForPreset('homeAssistant')).toEqual(['read', 'gps:ingest'])
    expect(scopesForPreset('custom', ['import', 'read', 'all'])).toEqual(['read', 'import'])
  })

  it('checks read along with write', () => {
    expect(toggleCustomScope([], 'write', true)).toEqual(['read', 'write'])
    expect(toggleCustomScope(['read', 'write'], 'read', false)).toEqual(['write'])
    expect(toggleCustomScope(['read', 'export'], 'export', false)).toEqual(['read'])
  })
})
