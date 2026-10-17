/**
 * Movement-type colours for the distance-share bars (Journey insights hero, period digest hero) and the
 * per-type distance charts. Keyed by the backend movement type; UNKNOWN is the "other" share. Fixed colours,
 * readable on both themes. The palette follows the user's map color scheme (resolveMapAppearance).
 */
export const MOVEMENT_TYPE_PALETTES = Object.freeze({
  // The original palette; unchanged for existing users.
  DEFAULT: Object.freeze({
    CAR: '#3b82f6',
    MOTORCYCLE: '#06b6d4',
    PUBLIC_TRANSPORT: '#ec4899',
    WALK: '#10b981',
    BICYCLE: '#f59e0b',
    RUNNING: '#8b5cf6',
    TRAIN: '#6366f1',
    FLIGHT: '#ef4444',
    BOAT: '#14b8a6',
    UNKNOWN: '#94a3b8'
  }),
  // Paul Tol's "muted" qualitative scheme: nine colours that stay distinct for every color vision type, plus
  // pale grey for the "other" share.
  COLOR_BLIND_SAFE: Object.freeze({
    CAR: '#88ccee',
    MOTORCYCLE: '#44aa99',
    PUBLIC_TRANSPORT: '#cc6677',
    WALK: '#117733',
    BICYCLE: '#ddcc77',
    RUNNING: '#aa4499',
    TRAIN: '#332288',
    FLIGHT: '#882255',
    BOAT: '#999933',
    UNKNOWN: '#dddddd'
  })
})

export const MOVEMENT_TYPE_COLORS = MOVEMENT_TYPE_PALETTES.DEFAULT

/** Palette for a map color scheme: every scheme other than DEFAULT is a color vision preset. */
export const getMovementTypePalette = (colorScheme) => (
  !colorScheme || colorScheme === 'DEFAULT' ? MOVEMENT_TYPE_PALETTES.DEFAULT : MOVEMENT_TYPE_PALETTES.COLOR_BLIND_SAFE
)
