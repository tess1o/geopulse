export const ROUTE_DISPLAY_MODES = Object.freeze({
  MATCHED: 'matched',
  RAW: 'raw',
  COMPARISON: 'comparison'
})

export const ROUTE_DISPLAY_MODE_ORDER = Object.freeze([
  ROUTE_DISPLAY_MODES.MATCHED,
  ROUTE_DISPLAY_MODES.RAW,
  ROUTE_DISPLAY_MODES.COMPARISON
])

export const normalizeRouteDisplayMode = (mode) => (
  ROUTE_DISPLAY_MODE_ORDER.includes(mode)
    ? mode
    : ROUTE_DISPLAY_MODES.MATCHED
)

export const getNextRouteDisplayMode = (mode) => {
  const currentMode = normalizeRouteDisplayMode(mode)
  const currentIndex = ROUTE_DISPLAY_MODE_ORDER.indexOf(currentMode)
  return ROUTE_DISPLAY_MODE_ORDER[(currentIndex + 1) % ROUTE_DISPLAY_MODE_ORDER.length]
}
