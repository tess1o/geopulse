import { getMovementTypePalette } from '@/utils/movementTypeColors'

// Map appearance: the single place that turns a user's color vision preset and overrides into concrete colors.
// Preference values mirror TimelineDisplayPreferences on the backend. An empty (or missing) override means
// "follow the color scheme"; the backend stores only what the user picked explicitly.

export const MAP_COLOR_SCHEMES = Object.freeze(['DEFAULT', 'RED_GREEN_SAFE', 'BLUE_YELLOW_SAFE', 'HIGH_CONTRAST'])

export const SPEED_BAND_PALETTE_OFF = 'OFF'

// slow < 10 km/h, medium 10-25 km/h, fast > 25 km/h (see highlightedTripSpeedBands.js).
export const SPEED_BAND_PALETTES = Object.freeze({
  // The original palette; unchanged for existing users.
  DEFAULT: Object.freeze({ slow: '#ef4444', medium: '#f59e0b', fast: '#22c55e', unknown: '#f59e0b' }),
  // Protanopia/deuteranopia keep the blue-yellow axis. Lightness rises slow -> fast so the bands also
  // separate in greyscale, and the brightest band (most of a car trip) stands out against vegetation.
  RED_GREEN_SAFE: Object.freeze({ slow: '#4b2991', medium: '#56b4e9', fast: '#fde725', unknown: '#9e9e9e' }),
  // Tritanopia keeps the red-cyan axis; avoids yellow/violet.
  BLUE_YELLOW_SAFE: Object.freeze({ slow: '#b2182b', medium: '#f4a582', fast: '#0fa3b1', unknown: '#9e9e9e' }),
  // Paul Tol's high-contrast scheme: distinct for all color vision types and in greyscale.
  HIGH_CONTRAST: Object.freeze({ slow: '#004488', medium: '#bb5566', fast: '#ddaa33', unknown: '#9e9e9e' })
})

export const SPEED_BAND_PALETTE_KEYS = Object.freeze([...Object.keys(SPEED_BAND_PALETTES), SPEED_BAND_PALETTE_OFF])

// Stops match the positions the heatmap layers have always used.
export const HEATMAP_GRADIENTS = Object.freeze({
  CLASSIC: Object.freeze({ 0.0: '#2563eb', 0.35: '#22c55e', 0.6: '#eab308', 0.8: '#f97316', 1.0: '#dc2626' }),
  VIRIDIS: Object.freeze({ 0.0: '#440154', 0.35: '#31688e', 0.6: '#22a884', 0.8: '#7ad151', 1.0: '#fde725' }),
  CIVIDIS: Object.freeze({ 0.0: '#00224e', 0.35: '#575c6e', 0.6: '#958f78', 0.8: '#c8b86e', 1.0: '#fee838' })
})

export const HEATMAP_GRADIENT_KEYS = Object.freeze(Object.keys(HEATMAP_GRADIENTS))

export const PATH_COLOR_MODES = Object.freeze({ SINGLE: 'SINGLE', DAY: 'DAY' })

// Distinct colors for the days of a multi-day range. Longer ranges than a palette can cover use the heatmap gradient.
// Each palette is ordered so neighboring days, which often share streets, are far apart (delta E 65 or more, also
// after its scheme's selected-trip color is skipped), and each starts with a different pair, so switching the
// color scheme visibly changes the days.
export const DAY_PALETTES = Object.freeze({
  // Vivid everyday colors (Tableau 10 without red, which is the default selected-trip color).
  CLASSIC: Object.freeze(['#1f77b4', '#2ca02c', '#9467bd', '#ff7f0e', '#e377c2', '#17becf', '#8c564b']),
  // The friend trail colors (Okabe-Ito without black and yellow), reordered.
  OKABE_ITO: Object.freeze(['#0072B2', '#D55E00', '#56B4E9', '#E69F00', '#882255', '#009E73', '#CC79A7']),
  // Paul Tol's muted scheme without its pale sand and grey.
  TOL_MUTED: Object.freeze(['#332288', '#88ccee', '#999933', '#aa4499', '#44aa99', '#cc6677', '#117733']),
  // Dark and light colors from Paul Tol's schemes, alternating so neighboring days also differ in brightness.
  CONTRAST: Object.freeze(['#882255', '#bbcc33', '#004488', '#ee99aa', '#332288', '#88ccee', '#117733'])
})

export const DAY_PALETTE_KEYS = Object.freeze(Object.keys(DAY_PALETTES))

export const COLOR_SCHEME_PRESETS = Object.freeze({
  DEFAULT: Object.freeze({
    defaultPathColor: '#007bff',
    activePathColor: '#ef4444',
    speedBandPalette: 'DEFAULT',
    heatmapGradient: 'CLASSIC',
    dayPalette: 'CLASSIC'
  }),
  RED_GREEN_SAFE: Object.freeze({
    defaultPathColor: '#0072b2',
    activePathColor: '#ffb000',
    speedBandPalette: 'RED_GREEN_SAFE',
    heatmapGradient: 'CIVIDIS',
    dayPalette: 'OKABE_ITO'
  }),
  BLUE_YELLOW_SAFE: Object.freeze({
    defaultPathColor: '#007bff',
    activePathColor: '#d81b60',
    speedBandPalette: 'BLUE_YELLOW_SAFE',
    heatmapGradient: 'VIRIDIS',
    dayPalette: 'TOL_MUTED'
  }),
  HIGH_CONTRAST: Object.freeze({
    defaultPathColor: '#1a1a1a',
    activePathColor: '#ddaa33',
    speedBandPalette: 'HIGH_CONTRAST',
    heatmapGradient: 'VIRIDIS',
    dayPalette: 'CONTRAST'
  })
})

export const PATH_WIDTH = Object.freeze({ MIN: 2, MAX: 10, DEFAULT: 4 })
export const HIGHLIGHTED_PATH_EXTRA_WIDTH = 2
export const PATH_OUTLINE_EXTRA_WIDTH = 3

// Flat user fields the auth store keeps; '' means "follow the color scheme".
export const APPEARANCE_PREFERENCE_DEFAULTS = Object.freeze({
  colorScheme: 'DEFAULT',
  defaultPathColor: '',
  activePathColor: '',
  speedBandPalette: '',
  heatmapGradient: '',
  pathColorMode: PATH_COLOR_MODES.SINGLE,
  dayPalette: '',
  pathOutlineEnabled: false,
  pathWidth: PATH_WIDTH.DEFAULT,
  showMapScale: true
})

export const APPEARANCE_PREFERENCE_KEYS = Object.freeze(Object.keys(APPEARANCE_PREFERENCE_DEFAULTS))

const HEX_COLOR_PATTERN = /^#[0-9a-f]{6}$/i

const isSet = (value) => value !== undefined && value !== null && value !== ''

const normalizeHex = (value) => (typeof value === 'string' && HEX_COLOR_PATTERN.test(value) ? value.toLowerCase() : null)

const resolveScheme = (value) => (MAP_COLOR_SCHEMES.includes(value) ? value : 'DEFAULT')

const clampPathWidth = (value) => {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    return PATH_WIDTH.DEFAULT
  }
  return Math.min(PATH_WIDTH.MAX, Math.max(PATH_WIDTH.MIN, Math.round(parsed)))
}

/**
 * Resolves the concrete map appearance: an explicit preference wins, then the color scheme preset,
 * then the DEFAULT preset.
 */
export function resolveMapAppearance(prefs = {}) {
  const colorScheme = resolveScheme(prefs?.colorScheme)
  const preset = COLOR_SCHEME_PRESETS[colorScheme]

  const explicitDefaultPathColor = normalizeHex(prefs?.defaultPathColor)
  const explicitActivePathColor = normalizeHex(prefs?.activePathColor)
  const explicitSpeedBandPalette = SPEED_BAND_PALETTE_KEYS.includes(prefs?.speedBandPalette) ? prefs.speedBandPalette : null
  const explicitHeatmapGradient = HEATMAP_GRADIENT_KEYS.includes(prefs?.heatmapGradient) ? prefs.heatmapGradient : null
  const explicitDayPalette = DAY_PALETTE_KEYS.includes(prefs?.dayPalette) ? prefs.dayPalette : null

  const speedBandPaletteKey = explicitSpeedBandPalette || preset.speedBandPalette
  const heatmapGradientKey = explicitHeatmapGradient || preset.heatmapGradient
  const dayPaletteKey = explicitDayPalette || preset.dayPalette
  const pathWidth = clampPathWidth(isSet(prefs?.pathWidth) ? prefs.pathWidth : PATH_WIDTH.DEFAULT)
  const speedBandColors = speedBandPaletteKey === SPEED_BAND_PALETTE_OFF ? null : SPEED_BAND_PALETTES[speedBandPaletteKey]

  return {
    colorScheme,
    defaultPathColor: explicitDefaultPathColor || preset.defaultPathColor,
    activePathColor: explicitActivePathColor || preset.activePathColor,
    speedBandPaletteKey,
    speedBandColors,
    speedBandsEnabled: speedBandColors !== null,
    heatmapGradientKey,
    heatmapGradient: HEATMAP_GRADIENTS[heatmapGradientKey],
    pathColorMode: prefs?.pathColorMode === PATH_COLOR_MODES.DAY ? PATH_COLOR_MODES.DAY : PATH_COLOR_MODES.SINGLE,
    dayPaletteKey,
    dayPalette: DAY_PALETTES[dayPaletteKey],
    movementTypeColors: getMovementTypePalette(colorScheme),
    pathWidth,
    highlightedPathWidth: pathWidth + HIGHLIGHTED_PATH_EXTRA_WIDTH,
    outlineEnabled: prefs?.pathOutlineEnabled === true,
    overrides: {
      defaultPathColor: explicitDefaultPathColor !== null,
      activePathColor: explicitActivePathColor !== null,
      speedBandPalette: explicitSpeedBandPalette !== null,
      heatmapGradient: explicitHeatmapGradient !== null,
      dayPalette: explicitDayPalette !== null
    }
  }
}

const hexToRgb = (hex) => {
  const normalized = normalizeHex(hex)
  if (!normalized) {
    return null
  }
  return [1, 3, 5].map((offset) => parseInt(normalized.slice(offset, offset + 2), 16))
}

// WCAG relative luminance.
export function getRelativeLuminance(hex) {
  const rgb = hexToRgb(hex)
  if (!rgb) {
    return null
  }
  const [r, g, b] = rgb.map((channel) => {
    const value = channel / 255
    return value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/** Outline (casing) color that contrasts with the line color: dark halo for light lines, white for dark ones. */
export function getOutlineColor(hex) {
  const luminance = getRelativeLuminance(hex)
  return luminance !== null && luminance > 0.35 ? '#111827' : '#ffffff'
}

const toLab = (hex) => {
  const rgb = hexToRgb(hex)
  if (!rgb) {
    return null
  }
  const [r, g, b] = rgb.map((channel) => {
    const value = channel / 255
    return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
  })
  // sRGB -> XYZ (D65), normalized by the white point.
  const xyz = [
    (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047,
    0.2126 * r + 0.7152 * g + 0.0722 * b,
    (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
  ].map((value) => (value > 0.008856 ? Math.cbrt(value) : (7.787 * value) + (16 / 116)))
  return [116 * xyz[1] - 16, 500 * (xyz[0] - xyz[1]), 200 * (xyz[1] - xyz[2])]
}

/** Perceptual difference between two colors (CIE76 delta E); null when either is not a hex color. */
export function getColorDistance(first, second) {
  const a = toLab(first)
  const b = toLab(second)
  if (!a || !b) {
    return null
  }
  return Math.hypot(a[0] - b[0], a[1] - b[1], a[2] - b[2])
}

/** MapLibre color expression for segments carrying a `speedBand` property. */
export function buildSpeedBandColorExpression(colors, { outline = false } = {}) {
  const palette = colors || SPEED_BAND_PALETTES.DEFAULT
  const pick = (color) => (outline ? getOutlineColor(color) : color)
  return [
    'match',
    ['get', 'speedBand'],
    'slow', pick(palette.slow),
    'medium', pick(palette.medium),
    'fast', pick(palette.fast),
    pick(palette.unknown)
  ]
}

const gradientStops = (gradient) => Object.entries(gradient || HEATMAP_GRADIENTS.CLASSIC)
  .map(([stop, color]) => [Number(stop), color])
  .sort((a, b) => a[0] - b[0])

/** Hex color at position t (0..1) of a gradient, interpolated between its stops. */
export function sampleGradient(gradient, t) {
  const stops = gradientStops(gradient)
  const position = Math.min(1, Math.max(0, Number(t) || 0))
  const upperIndex = stops.findIndex(([stop]) => stop >= position)
  if (upperIndex <= 0) {
    return stops[upperIndex === 0 ? 0 : stops.length - 1][1]
  }
  const [lowerStop, lowerColor] = stops[upperIndex - 1]
  const [upperStop, upperColor] = stops[upperIndex]
  const ratio = (position - lowerStop) / (upperStop - lowerStop)
  const lower = hexToRgb(lowerColor)
  const upper = hexToRgb(upperColor)
  return `#${lower.map((channel, index) => Math.round(channel + (upper[index] - channel) * ratio)
    .toString(16)
    .padStart(2, '0')).join('')}`
}

/** CSS linear-gradient for heatmap legends. */
export function heatmapGradientToCss(gradient, direction = 'to right') {
  const stops = gradientStops(gradient).map(([stop, color]) => `${color} ${Math.round(stop * 100)}%`)
  return `linear-gradient(${direction}, ${stops.join(', ')})`
}

/** Transparent version of the first gradient stop, for the zero-density stop of MapLibre heatmaps. */
export function getHeatmapZeroStopColor(gradient) {
  const [, firstColor] = gradientStops(gradient)[0]
  const rgb = hexToRgb(firstColor) || [0, 0, 0]
  return `rgba(${rgb.join(',')},0)`
}

/** True when the preferences resolve to anything other than the stock look. */
export function isAppearanceCustomized(prefs) {
  const appearance = resolveMapAppearance(prefs || {})
  return appearance.colorScheme !== 'DEFAULT'
    || Object.values(appearance.overrides).some(Boolean)
    || appearance.outlineEnabled
    || appearance.pathWidth !== PATH_WIDTH.DEFAULT
    || appearance.pathColorMode !== PATH_COLOR_MODES.SINGLE
}

// What a viewer of a shared link can pick: the owner's look, their own profile, or one of the presets.
export const SHARED_APPEARANCE_CHOICES = Object.freeze({ OWNER: 'OWNER', MINE: 'MINE' })

/** Preferences a preset stands for on a shared page; mirrors what picking it in the profile does. */
export function presetAppearancePreferences(scheme) {
  return {
    colorScheme: scheme,
    pathOutlineEnabled: scheme !== 'DEFAULT',
    pathWidth: scheme === 'HIGH_CONTRAST' ? 6 : PATH_WIDTH.DEFAULT
  }
}

/**
 * The choice in effect on a shared page. A remembered choice wins while it is still available. Otherwise a
 * signed-in viewer who customized their own appearance keeps it (it may be an accessibility need), and
 * everyone else sees the map the way the owner does.
 */
export function resolveSharedAppearanceChoice({ storedChoice, ownerPrefs, viewerPrefs }) {
  const ownerCustomized = isAppearanceCustomized(ownerPrefs)
  if (storedChoice === SHARED_APPEARANCE_CHOICES.MINE && viewerPrefs) {
    return SHARED_APPEARANCE_CHOICES.MINE
  }
  if (MAP_COLOR_SCHEMES.includes(storedChoice)) {
    return storedChoice
  }
  if (storedChoice === SHARED_APPEARANCE_CHOICES.OWNER) {
    return ownerCustomized ? SHARED_APPEARANCE_CHOICES.OWNER : 'DEFAULT'
  }
  if (viewerPrefs && isAppearanceCustomized(viewerPrefs)) {
    return SHARED_APPEARANCE_CHOICES.MINE
  }
  return ownerCustomized ? SHARED_APPEARANCE_CHOICES.OWNER : 'DEFAULT'
}

/** Preferences behind a shared-page choice, ready for resolveMapAppearance(). */
export function sharedAppearancePreferences(choice, { ownerPrefs, viewerPrefs }) {
  if (choice === SHARED_APPEARANCE_CHOICES.OWNER) {
    return ownerPrefs || {}
  }
  if (choice === SHARED_APPEARANCE_CHOICES.MINE) {
    return viewerPrefs || {}
  }
  return presetAppearancePreferences(choice)
}
