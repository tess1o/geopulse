/**
 * API token scopes as the backend names them (`ApiTokenScope`), and the presets offered when creating a
 * token. Scopes are fixed once a token exists.
 */
export const API_TOKEN_SCOPES = Object.freeze(['read', 'write', 'gps:ingest', 'export', 'import'])

export const FULL_ACCESS_SCOPE = 'all'

export const API_TOKEN_PRESETS = Object.freeze([
  { id: 'full', scopes: [FULL_ACCESS_SCOPE] },
  { id: 'readOnly', scopes: ['read'] },
  { id: 'homeAssistant', scopes: ['read', 'gps:ingest'] },
  { id: 'backup', scopes: ['read', 'export'] },
  { id: 'custom', scopes: null }
])

const sameScopes = (a, b) => a.length === b.length && a.every(scope => b.includes(scope))

/** Preset id matching exactly these scopes, or `custom`. */
export function presetForScopes(scopes) {
  const list = Array.isArray(scopes) ? scopes : []
  const match = API_TOKEN_PRESETS.find(preset => preset.scopes && sameScopes(preset.scopes, list))
  return match ? match.id : 'custom'
}

/** Scopes to send for a preset; `custom` uses the checked scopes. */
export function scopesForPreset(presetId, customScopes = []) {
  const preset = API_TOKEN_PRESETS.find(item => item.id === presetId)
  if (preset?.scopes) return [...preset.scopes]
  return API_TOKEN_SCOPES.filter(scope => customScopes.includes(scope))
}

/**
 * Applies a checkbox change in the custom picker. Writing without reading is rarely useful, so checking
 * `write` also checks `read`; unchecking `read` leaves `write` as the user chose.
 */
export function toggleCustomScope(customScopes, scope, checked) {
  const next = new Set(customScopes)
  if (checked) {
    next.add(scope)
    if (scope === 'write') next.add('read')
  } else {
    next.delete(scope)
  }
  return API_TOKEN_SCOPES.filter(item => next.has(item))
}
