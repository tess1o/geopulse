import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { NOTE_MARKER_COLOR, PHOTO_MARKER_COLOR } from './mapColors'

// CSS-drawn markers (raster note and photo markers, stack rows) read the tokens, JS-drawn ones (MapLibre layers,
// canvas icons) read mapColors.js. A colour changed on one side only shows the same item in two colours.
// Resolved from this file's path: Vite rewrites a literal `new URL('….css', import.meta.url)` into an asset URL.
const tokensCss = readFileSync(resolve(dirname(fileURLToPath(import.meta.url)), '../../styles/tokens.css'), 'utf8')

const rootTokenValue = (name) => {
  const rootBlock = tokensCss.slice(tokensCss.indexOf(':root {'), tokensCss.indexOf('\n}', tokensCss.indexOf(':root {')))
  const match = rootBlock.match(new RegExp(`${name}:\\s*([^;]+);`))
  return match?.[1].trim().toLowerCase()
}

describe('mapColors', () => {
  it.each([
    ['--gp-map-marker-note', NOTE_MARKER_COLOR],
    ['--gp-map-marker-photo', PHOTO_MARKER_COLOR]
  ])('%s in tokens.css matches mapColors.js', (token, jsColor) => {
    expect(rootTokenValue(token)).toBe(jsColor.toLowerCase())
  })

  it('defines no dark-mode value for marker tokens, since markers sit on light tiles in both themes', () => {
    const darkBlock = tokensCss.slice(tokensCss.indexOf('.p-dark {'))
    expect(darkBlock).not.toMatch(/--gp-map-marker-/)
  })
})
