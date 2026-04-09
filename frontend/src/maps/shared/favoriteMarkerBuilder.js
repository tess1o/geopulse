const FAVORITE_SIZE = 32
const FAVORITE_BORDER = 3
const FAVORITE_ICON_SIZE = Math.floor(FAVORITE_SIZE * 0.7)
const FAVORITE_TOTAL_SIZE = FAVORITE_SIZE + (FAVORITE_BORDER * 2)

const toKebabCase = (value) => value.replace(/([A-Z])/g, '-$1').toLowerCase()

const toStyleString = (styles) => Object.entries(styles)
  .map(([key, styleValue]) => `${toKebabCase(key)}: ${styleValue}`)
  .join('; ')

export const createFavoritePointMarkerElement = () => {
  const element = document.createElement('div')
  element.className = 'custom-marker favorite-marker marker-pin'
  element.style.transition = 'none'
  element.style.pointerEvents = 'auto'

  const markerStyles = {
    width: `${FAVORITE_SIZE}px`,
    height: `${FAVORITE_SIZE}px`,
    border: `${FAVORITE_BORDER}px solid white`,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
    borderRadius: '50% 50% 50% 0',
    transform: 'rotate(-45deg)',
    transformOrigin: 'center center',
    background: 'linear-gradient(135deg, #E91E63, #AD1457)'
  }

  const iconStyles = {
    color: 'white',
    fontSize: `${FAVORITE_ICON_SIZE}px`,
    fontWeight: 'bold',
    transform: 'rotate(45deg)'
  }

  element.innerHTML = `
    <div style="${toStyleString(markerStyles)}; box-shadow: 2px 2px 4px rgba(0,0,0,0.3); filter: drop-shadow(2px 2px 4px rgba(0,0,0,0.2));">
      <i class="fas fa-star" style="${toStyleString(iconStyles)}"></i>
    </div>
  `.trim()

  // Raster pin anchor is ~85% of marker height, not the full bottom.
  const anchorOffsetY = Math.round((FAVORITE_TOTAL_SIZE * 0.85) - FAVORITE_TOTAL_SIZE)

  return {
    element,
    anchor: 'bottom',
    offset: [0, anchorOffsetY]
  }
}
