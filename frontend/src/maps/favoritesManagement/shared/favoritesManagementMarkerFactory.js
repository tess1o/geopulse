const FAVORITE_POINT_HTML = '<div class="favorite-marker-icon"><i class="pi pi-map-marker"></i></div>'
const FAVORITE_AREA_HTML = '<div class="favorite-area-icon"><i class="pi pi-th-large"></i></div>'
const PENDING_POINT_HTML = '<div class="pending-marker-icon"><i class="pi pi-map-marker"></i></div>'
const PENDING_AREA_HTML = '<div class="pending-area-icon"><i class="pi pi-th-large"></i></div>'
const TEMP_POINT_HTML = '<div class="temp-marker-icon"><i class="pi pi-map-marker"></i></div>'

export const FAVORITES_MARKER_CLASSES = Object.freeze({
  favoritePoint: 'favorite-point-marker',
  favoriteArea: 'favorite-area-marker',
  pendingPoint: 'pending-point-marker',
  pendingArea: 'pending-area-marker',
  tempPoint: 'temp-favorite-marker'
})

export const createMarkerHtml = (variant) => {
  switch (variant) {
    case 'favoritePoint':
      return FAVORITE_POINT_HTML
    case 'favoriteArea':
      return FAVORITE_AREA_HTML
    case 'pendingPoint':
      return PENDING_POINT_HTML
    case 'pendingArea':
      return PENDING_AREA_HTML
    case 'tempPoint':
      return TEMP_POINT_HTML
    default:
      return FAVORITE_POINT_HTML
  }
}

export const getLeafletIconShape = (variant) => {
  if (variant === 'favoriteArea' || variant === 'pendingArea') {
    return {
      iconSize: [40, 40],
      iconAnchor: [20, 20]
    }
  }

  return {
    iconSize: [40, 40],
    iconAnchor: [20, 40]
  }
}

export const getVectorMarkerAnchor = (variant) => {
  if (variant === 'favoriteArea' || variant === 'pendingArea') {
    return 'center'
  }

  return 'bottom'
}

export const createVectorMarkerElement = (variant) => {
  const root = document.createElement('div')
  root.className = `${FAVORITES_MARKER_CLASSES[variant]} favorites-management-marker`
  root.innerHTML = createMarkerHtml(variant)
  return root
}

