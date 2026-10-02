import { TRIP_ENDPOINT_GRADIENTS } from './mapColors'

const ENDPOINT_SIZE = 40
export const TRIP_ENDPOINT_MARKER_SIZE = ENDPOINT_SIZE
const ENDPOINT_BORDER = 3
const ENDPOINT_ICON_SIZE = Math.floor(ENDPOINT_SIZE * 0.7)

const ENDPOINT_TOKENS = {
  start: {
    className: 'highlight-start-marker',
    shape: 'circle',
    iconClass: 'fas fa-play',
    gradient: TRIP_ENDPOINT_GRADIENTS.start
  },
  end: {
    className: 'highlight-end-marker',
    shape: 'square',
    iconClass: 'fas fa-stop',
    gradient: TRIP_ENDPOINT_GRADIENTS.end
  }
}

const toKebabCase = (value) => value.replace(/([A-Z])/g, '-$1').toLowerCase()

const toStyleString = (styles) => Object.entries(styles)
  .map(([key, styleValue]) => `${toKebabCase(key)}: ${styleValue}`)
  .join('; ')

const resolveToken = (markerType) => (markerType === 'end' ? ENDPOINT_TOKENS.end : ENDPOINT_TOKENS.start)

const resolveBorderRadius = (shape) => (shape === 'square' ? '15%' : '50%')

export const createTripEndpointMarkerElement = ({ markerType, instant = true, styleOverrides = {} } = {}) => {
  const token = resolveToken(markerType)

  const element = document.createElement('div')
  element.className = `custom-marker ${token.className}${instant ? ' instant' : ''} marker-${token.shape}`
  element.style.transition = 'none'
  element.style.pointerEvents = 'auto'

  const markerStyles = {
    backgroundColor: token.gradient.from,
    width: `${ENDPOINT_SIZE}px`,
    height: `${ENDPOINT_SIZE}px`,
    border: `${ENDPOINT_BORDER}px solid white`,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
    borderRadius: resolveBorderRadius(token.shape),
    background: `linear-gradient(135deg, ${token.gradient.from}, ${token.gradient.to})`,
    ...styleOverrides
  }

  const iconStyles = {
    color: 'white',
    fontSize: `${ENDPOINT_ICON_SIZE}px`,
    fontWeight: 'bold'
  }

  element.innerHTML = `
    <div style="${toStyleString(markerStyles)}; box-shadow: 0 2px 4px rgba(0,0,0,0.3);">
      <i class="${token.iconClass}" style="${toStyleString(iconStyles)}"></i>
    </div>
  `.trim()

  return element
}
