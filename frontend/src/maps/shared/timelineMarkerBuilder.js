import { getTripMovementIconClass } from '@/utils/timelineIconUtils'

const MARKER_SIZES = {
  STANDARD: {
    SIZE: 24,
    BORDER: 2
  },
  HIGHLIGHT: {
    SIZE: 40,
    BORDER: 3
  }
}

const DESKTOP_TIMELINE_BREAKPOINT = 1024
const DESKTOP_TIMELINE_MARKER_SIZE = {
  ...MARKER_SIZES.STANDARD,
  SIZE: 28
}
const DESKTOP_TIMELINE_HIGHLIGHT_MARKER_SIZE = {
  ...MARKER_SIZES.HIGHLIGHT,
  SIZE: 44
}

const toKebabCase = (value) => value.replace(/([A-Z])/g, '-$1').toLowerCase()

const toStyleString = (styles) => Object.entries(styles)
  .map(([key, styleValue]) => `${toKebabCase(key)}: ${styleValue}`)
  .join('; ')

const isCssIconClass = (icon) => (
  typeof icon === 'string'
  && (
    icon.startsWith('pi ')
    || icon.startsWith('fas ')
    || icon.startsWith('far ')
    || icon.startsWith('fal ')
    || icon.startsWith('fab ')
    || icon.startsWith('fa-')
  )
)

const getResponsiveTimelineMarkerSize = (highlighted = false) => {
  const isDesktop = typeof window !== 'undefined' && window.innerWidth >= DESKTOP_TIMELINE_BREAKPOINT
  if (!isDesktop) {
    return highlighted ? MARKER_SIZES.HIGHLIGHT : MARKER_SIZES.STANDARD
  }

  return highlighted ? DESKTOP_TIMELINE_HIGHLIGHT_MARKER_SIZE : DESKTOP_TIMELINE_MARKER_SIZE
}

const resolveTimelineMarkerVisual = (item) => {
  const itemType = item?.type
  if (itemType === 'trip') {
    return {
      color: '#10B981',
      icon: getTripMovementIconClass(item?.movementType),
      highlightRingColor: 'rgba(52, 211, 153, 0.55)'
    }
  }

  if (itemType === 'dataGap') {
    return {
      color: '#F59E0B',
      icon: 'pi pi-question',
      highlightRingColor: 'rgba(251, 191, 36, 0.55)'
    }
  }

  return {
    color: '#1A56DB',
    icon: 'pi pi-map-marker',
    highlightRingColor: 'rgba(96, 165, 250, 0.55)'
  }
}

const createCustomMarkerElement = ({
  color,
  icon,
  size,
  className,
  customStyle = {},
  shape = 'circle'
}) => {
  const root = document.createElement('div')
  root.className = `${className} marker-${shape}`
  root.style.transition = 'none'
  root.style.pointerEvents = 'auto'

  const baseStyles = {
    backgroundColor: color,
    width: `${size.SIZE}px`,
    height: `${size.SIZE}px`,
    border: `${size.BORDER}px solid white`,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
    borderRadius: '50%',
    boxSizing: 'border-box',
    ...customStyle
  }

  let iconContent = ''
  if (icon) {
    const iconSize = Math.floor(size.SIZE * 0.7)
    if (isCssIconClass(icon)) {
      iconContent = `<i class="${icon}" style="color: white; font-size: ${iconSize}px; font-weight: bold;"></i>`
    } else {
      const emojiSize = Math.floor(size.SIZE * 0.8)
      iconContent = `<span style="font-size: ${emojiSize}px; line-height: 1;">${icon}</span>`
    }
  }

  root.innerHTML = `<div style="${toStyleString(baseStyles)}; box-shadow: 0 2px 4px rgba(0,0,0,0.3);">${iconContent}</div>`

  return {
    element: root,
    // Raster anchor math effectively shifts center by border width.
    offset: [-size.BORDER, -size.BORDER]
  }
}

export const createTimelineMarkerElement = ({ item, highlighted = false } = {}) => {
  const markerVisual = resolveTimelineMarkerVisual(item)
  const markerSize = getResponsiveTimelineMarkerSize(highlighted)

  return createCustomMarkerElement({
    color: markerVisual.color,
    icon: markerVisual.icon,
    size: markerSize,
    className: highlighted
      ? 'custom-marker timeline-marker highlighted'
      : 'custom-marker timeline-marker',
    customStyle: highlighted
      ? { boxShadow: `0 0 0 4px ${markerVisual.highlightRingColor}, 0 4px 10px rgba(15, 23, 42, 0.25)` }
      : {},
    shape: 'circle'
  })
}

export const createTimelineStackMarkerElement = ({ count, highlighted = false } = {}) => {
  const root = document.createElement('div')
  root.className = highlighted
    ? 'timeline-stack-marker timeline-stack-marker-highlighted'
    : 'timeline-stack-marker'
  root.style.transition = 'none'
  root.style.pointerEvents = 'auto'

  const label = document.createElement('span')
  label.textContent = String(count)
  root.appendChild(label)

  const size = highlighted ? 34 : 30
  return {
    element: root,
    offset: [0, 0],
    size
  }
}
