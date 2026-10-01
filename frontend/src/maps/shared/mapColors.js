/**
 * Marker colours for markers drawn from JS: inline marker styles (both engines), MapLibre paint properties and canvas
 * icons. They are the same in both themes: the base tiles don't switch theme, so markers are drawn for a light map.
 * Colours that CSS also uses are mirrored as --gp-map-marker-* tokens in src/styles/tokens.css; mapColors.test.js
 * keeps the two in sync.
 */

// Timeline items (stay / trip / data gap): marker fill and the ring drawn around a highlighted marker.
export const TIMELINE_MARKER_COLORS = Object.freeze({
  stay: Object.freeze({ color: '#1A56DB', highlightRingColor: 'rgba(96, 165, 250, 0.55)' }),
  trip: Object.freeze({ color: '#10B981', highlightRingColor: 'rgba(52, 211, 153, 0.55)' }),
  dataGap: Object.freeze({ color: '#F59E0B', highlightRingColor: 'rgba(251, 191, 36, 0.55)' })
})

// Start and end markers of a highlighted trip (both engines, own and friends' timelines).
export const TRIP_ENDPOINT_GRADIENTS = Object.freeze({
  start: Object.freeze({ from: '#2ECC71', to: '#27AE60' }),
  end: Object.freeze({ from: '#D84315', to: '#C0392B' })
})

// Violet keeps notes distinct from the teal timeline stack markers. = --gp-map-marker-note
export const NOTE_MARKER_COLOR = '#7c3aed'

// = --gp-map-marker-photo
export const PHOTO_MARKER_COLOR = '#2563eb'
