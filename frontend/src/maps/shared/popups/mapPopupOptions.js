const MAP_POPUP_CLASS_NAME = 'gp-map-popup-container'
const MAP_POPUP_COMPACT_CLASS_NAME = 'gp-map-popup-container--compact'
const MAP_POPUP_WIDE_CLASS_NAME = 'gp-map-popup-container--wide'

export const MAP_POPUP_MAX_WIDTH = 'min(430px, calc(100vw - 32px))'
export const MAP_POPUP_MAX_WIDTH_PX = 430
export const MAP_POPUP_COMPACT_MAX_WIDTH = 'min(320px, calc(100vw - 32px))'
export const MAP_POPUP_COMPACT_MAX_WIDTH_PX = 320
export const MAP_POPUP_OFFSET = 14

const getMapPopupClassName = (...classNames) => (
  [MAP_POPUP_CLASS_NAME, ...classNames]
    .filter(Boolean)
    .join(' ')
)

export const getMapPopupVariantClassName = (variant = 'compact', ...classNames) => (
  getMapPopupClassName(
    variant === 'wide' ? MAP_POPUP_WIDE_CLASS_NAME : MAP_POPUP_COMPACT_CLASS_NAME,
    ...classNames
  )
)
