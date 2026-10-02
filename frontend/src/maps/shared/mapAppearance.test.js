import { describe, expect, it } from 'vitest'
import {
  COLOR_SCHEME_PRESETS,
  HEATMAP_GRADIENTS,
  MAP_COLOR_SCHEMES,
  SPEED_BAND_PALETTES,
  buildSpeedBandColorExpression,
  getHeatmapZeroStopColor,
  getOutlineColor,
  heatmapGradientToCss,
  isAppearanceCustomized,
  resolveMapAppearance,
  resolveSharedAppearanceChoice,
  sharedAppearancePreferences
} from './mapAppearance'

const HEX = /^#[0-9a-f]{6}$/i

// CIELAB lightness (L*) of an sRGB hex color.
const lightness = (hex) => {
  const [r, g, b] = [1, 3, 5].map((offset) => {
    const channel = parseInt(hex.slice(offset, offset + 2), 16) / 255
    return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4
  })
  const y = 0.2126 * r + 0.7152 * g + 0.0722 * b
  return y > 0.008856 ? 116 * Math.cbrt(y) - 16 : 903.3 * y
}

describe('resolveMapAppearance', () => {
  it('uses the original colors when nothing is set', () => {
    const appearance = resolveMapAppearance({})

    expect(appearance).toMatchObject({
      colorScheme: 'DEFAULT',
      defaultPathColor: '#007bff',
      activePathColor: '#ef4444',
      speedBandPaletteKey: 'DEFAULT',
      speedBandsEnabled: true,
      heatmapGradientKey: 'CLASSIC',
      pathWidth: 4,
      highlightedPathWidth: 6,
      outlineEnabled: false
    })
    expect(appearance.speedBandColors).toEqual(SPEED_BAND_PALETTES.DEFAULT)
    expect(Object.values(appearance.overrides)).toEqual([false, false, false, false])
  })

  it('fills unset values from the color scheme', () => {
    const appearance = resolveMapAppearance({ colorScheme: 'RED_GREEN_SAFE', defaultPathColor: '', speedBandPalette: '' })

    expect(appearance.defaultPathColor).toBe(COLOR_SCHEME_PRESETS.RED_GREEN_SAFE.defaultPathColor)
    expect(appearance.activePathColor).toBe(COLOR_SCHEME_PRESETS.RED_GREEN_SAFE.activePathColor)
    expect(appearance.speedBandColors).toEqual(SPEED_BAND_PALETTES.RED_GREEN_SAFE)
    expect(appearance.heatmapGradient).toEqual(HEATMAP_GRADIENTS.CIVIDIS)
  })

  it('lets explicit values override the color scheme', () => {
    const appearance = resolveMapAppearance({
      colorScheme: 'RED_GREEN_SAFE',
      activePathColor: '#ABCDEF',
      speedBandPalette: 'HIGH_CONTRAST',
      heatmapGradient: 'CLASSIC'
    })

    expect(appearance.activePathColor).toBe('#abcdef')
    expect(appearance.speedBandColors).toEqual(SPEED_BAND_PALETTES.HIGH_CONTRAST)
    expect(appearance.heatmapGradientKey).toBe('CLASSIC')
    expect(appearance.overrides).toEqual({
      defaultPathColor: false,
      activePathColor: true,
      speedBandPalette: true,
      heatmapGradient: true
    })
  })

  it('turns speed bands off with the OFF palette', () => {
    const appearance = resolveMapAppearance({ speedBandPalette: 'OFF' })

    expect(appearance.speedBandColors).toBeNull()
    expect(appearance.speedBandsEnabled).toBe(false)
  })

  it('ignores unknown values and clamps the width', () => {
    const appearance = resolveMapAppearance({
      colorScheme: 'NOPE',
      defaultPathColor: 'red',
      speedBandPalette: 'RAINBOW',
      pathWidth: 50,
      pathOutlineEnabled: 'yes'
    })

    expect(appearance.colorScheme).toBe('DEFAULT')
    expect(appearance.defaultPathColor).toBe('#007bff')
    expect(appearance.speedBandPaletteKey).toBe('DEFAULT')
    expect(appearance.pathWidth).toBe(10)
    expect(appearance.outlineEnabled).toBe(false)
  })
})

describe('palettes', () => {
  it('defines every preset with valid colors and known palettes', () => {
    MAP_COLOR_SCHEMES.forEach((scheme) => {
      const preset = COLOR_SCHEME_PRESETS[scheme]
      expect(preset.defaultPathColor).toMatch(HEX)
      expect(preset.activePathColor).toMatch(HEX)
      expect(SPEED_BAND_PALETTES[preset.speedBandPalette]).toBeTruthy()
      expect(HEATMAP_GRADIENTS[preset.heatmapGradient]).toBeTruthy()
    })
  })

  it('gives every speed band a valid color', () => {
    Object.values(SPEED_BAND_PALETTES).forEach((palette) => {
      ['slow', 'medium', 'fast', 'unknown'].forEach((band) => expect(palette[band]).toMatch(HEX))
    })
  })

  it('keeps color vision safe speed bands apart in lightness, so they differ without hue', () => {
    ['RED_GREEN_SAFE', 'BLUE_YELLOW_SAFE', 'HIGH_CONTRAST'].forEach((key) => {
      const { slow, medium, fast } = SPEED_BAND_PALETTES[key]
      const values = [slow, medium, fast].map(lightness).sort((a, b) => a - b)
      expect(values[1] - values[0], key).toBeGreaterThanOrEqual(10)
      expect(values[2] - values[1], key).toBeGreaterThanOrEqual(10)
    })
  })
})

describe('helpers', () => {
  it('outlines dark lines in white and light lines in near-black', () => {
    expect(getOutlineColor('#007bff')).toBe('#ffffff')
    expect(getOutlineColor('#ef4444')).toBe('#ffffff')
    expect(getOutlineColor('#4b2991')).toBe('#ffffff')
    expect(getOutlineColor('#fde725')).toBe('#111827')
    expect(getOutlineColor('#ffb000')).toBe('#111827')
  })

  it('builds a MapLibre match expression over speed bands', () => {
    const colors = SPEED_BAND_PALETTES.RED_GREEN_SAFE
    expect(buildSpeedBandColorExpression(colors)).toEqual([
      'match', ['get', 'speedBand'],
      'slow', colors.slow,
      'medium', colors.medium,
      'fast', colors.fast,
      colors.unknown
    ])
    expect(buildSpeedBandColorExpression(colors, { outline: true })[3]).toBe('#ffffff')
  })

  it('renders heatmap gradients for legends and a transparent zero stop', () => {
    expect(heatmapGradientToCss(HEATMAP_GRADIENTS.VIRIDIS)).toBe(
      'linear-gradient(to right, #440154 0%, #31688e 35%, #22a884 60%, #7ad151 80%, #fde725 100%)'
    )
    expect(getHeatmapZeroStopColor(HEATMAP_GRADIENTS.CLASSIC)).toBe('rgba(37,99,235,0)')
    expect(getHeatmapZeroStopColor(HEATMAP_GRADIENTS.VIRIDIS)).toBe('rgba(68,1,84,0)')
  })
})

describe('shared link appearance', () => {
  const ownerPrefs = { colorScheme: 'RED_GREEN_SAFE', pathOutlineEnabled: true }
  const customViewer = { colorScheme: 'HIGH_CONTRAST' }
  const plainViewer = { colorScheme: 'DEFAULT', pathWidth: 4, pathOutlineEnabled: false }

  it('detects customized preferences', () => {
    expect(isAppearanceCustomized({})).toBe(false)
    expect(isAppearanceCustomized(plainViewer)).toBe(false)
    expect(isAppearanceCustomized(ownerPrefs)).toBe(true)
    expect(isAppearanceCustomized({ activePathColor: '#ffcc00' })).toBe(true)
    expect(isAppearanceCustomized({ pathWidth: 7 })).toBe(true)
  })

  it('shows guests the owner look, or Default when the owner changed nothing', () => {
    expect(resolveSharedAppearanceChoice({ storedChoice: null, ownerPrefs, viewerPrefs: null })).toBe('OWNER')
    expect(resolveSharedAppearanceChoice({ storedChoice: null, ownerPrefs: {}, viewerPrefs: null })).toBe('DEFAULT')
  })

  it('keeps a signed-in viewer on their own customized appearance', () => {
    expect(resolveSharedAppearanceChoice({ storedChoice: null, ownerPrefs, viewerPrefs: customViewer })).toBe('MINE')
    expect(resolveSharedAppearanceChoice({ storedChoice: null, ownerPrefs, viewerPrefs: plainViewer })).toBe('OWNER')
  })

  it('honors a remembered choice while it is available', () => {
    expect(resolveSharedAppearanceChoice({ storedChoice: 'BLUE_YELLOW_SAFE', ownerPrefs, viewerPrefs: customViewer })).toBe('BLUE_YELLOW_SAFE')
    expect(resolveSharedAppearanceChoice({ storedChoice: 'OWNER', ownerPrefs, viewerPrefs: customViewer })).toBe('OWNER')
    expect(resolveSharedAppearanceChoice({ storedChoice: 'OWNER', ownerPrefs: {}, viewerPrefs: null })).toBe('DEFAULT')
    // "My settings" was remembered, but the viewer is signed out now.
    expect(resolveSharedAppearanceChoice({ storedChoice: 'MINE', ownerPrefs, viewerPrefs: null })).toBe('OWNER')
    expect(resolveSharedAppearanceChoice({ storedChoice: 'garbage', ownerPrefs, viewerPrefs: null })).toBe('OWNER')
  })

  it('maps each choice to the preferences it stands for', () => {
    const context = { ownerPrefs, viewerPrefs: customViewer }
    expect(sharedAppearancePreferences('OWNER', context)).toBe(ownerPrefs)
    expect(sharedAppearancePreferences('MINE', context)).toBe(customViewer)
    expect(sharedAppearancePreferences('DEFAULT', context)).toEqual({ colorScheme: 'DEFAULT', pathOutlineEnabled: false, pathWidth: 4 })
    expect(sharedAppearancePreferences('HIGH_CONTRAST', context)).toEqual({ colorScheme: 'HIGH_CONTRAST', pathOutlineEnabled: true, pathWidth: 6 })
  })
})
