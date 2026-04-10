import L from 'leaflet'
import {
  buildBoundsPoints,
  getAreaCenterLatLng,
  getFavoritePointLatLng,
  getPendingPointLatLng,
  toLeafletBounds
} from '@/maps/favoritesManagement/shared/favoritesManagementGeometry'
import {
  createMarkerHtml,
  FAVORITES_MARKER_CLASSES,
  getLeafletIconShape
} from '@/maps/favoritesManagement/shared/favoritesManagementMarkerFactory'

const createLeafletDivIcon = (variant) => {
  const shape = getLeafletIconShape(variant)
  return L.divIcon({
    className: FAVORITES_MARKER_CLASSES[variant],
    html: createMarkerHtml(variant),
    iconSize: shape.iconSize,
    iconAnchor: shape.iconAnchor
  })
}

const formatFavoritePopupHtml = (favorite, isArea = false) => {
  const safeName = favorite?.name || 'Favorite'
  return isArea
    ? `<strong>${safeName}</strong><br>Area Favorite`
    : `<strong>${safeName}</strong>`
}

const formatPendingPopupHtml = (pendingFavorite, isArea = false) => {
  const safeName = pendingFavorite?.name || 'Pending favorite'
  return isArea
    ? `<strong>${safeName}</strong><br><span style="color: #f59e0b;">Pending Area</span>`
    : `<strong>${safeName}</strong><br><span style="color: #f59e0b;">Pending</span>`
}

export const createRasterFavoritesManagementMapAdapter = (callbacks = {}) => {
  let map = null
  let layerGroup = null
  let tempPointMarker = null
  let activePopupLayer = null

  const clearActivePopup = () => {
    if (!activePopupLayer) {
      return
    }

    if (typeof activePopupLayer.closePopup === 'function') {
      activePopupLayer.closePopup()
    } else if (typeof activePopupLayer.remove === 'function') {
      activePopupLayer.remove()
    }

    activePopupLayer = null
  }

  const withStoppedContextEvent = (eventPayload) => {
    const rawEvent = eventPayload?.originalEvent || eventPayload
    rawEvent?.preventDefault?.()
    rawEvent?.stopPropagation?.()
    rawEvent?.stopImmediatePropagation?.()
    if (rawEvent) {
      rawEvent.cancelBubble = true
    }
    L.DomEvent.stop(eventPayload)
    callbacks.onContextMenuHandled?.()
    return rawEvent
  }

  const openLayerPopup = (layer, html) => {
    if (!map || !layer) {
      return
    }

    clearActivePopup()
    layer.bindPopup(html)
    layer.openPopup()
    activePopupLayer = layer
  }

  const addFavoritePoint = (favorite) => {
    const point = getFavoritePointLatLng(favorite)
    if (!point || !layerGroup) {
      return
    }

    const marker = L.marker([point.lat, point.lng], {
      icon: createLeafletDivIcon('favoritePoint')
    })

    marker.on('click', () => {
      focusOnFavorite(favorite, { openPopup: false })
      openLayerPopup(marker, formatFavoritePopupHtml(favorite, false))
    })

    marker.on('contextmenu', (eventPayload) => {
      const rawEvent = withStoppedContextEvent(eventPayload)
      callbacks.onFavoriteContextMenu?.({
        favorite,
        originalEvent: rawEvent,
        latlng: eventPayload?.latlng || null
      })
    })

    marker.addTo(layerGroup)
  }

  const addFavoriteArea = (favorite) => {
    const bounds = toLeafletBounds(favorite)
    const center = getAreaCenterLatLng(favorite)
    if (!bounds || !center || !layerGroup) {
      return
    }

    const rectangle = L.rectangle(bounds, {
      color: '#ef4444',
      fillColor: '#ef4444',
      fillOpacity: 0.2,
      weight: 2
    })

    rectangle.on('click', () => {
      focusOnFavorite(favorite, { openPopup: false })
      openLayerPopup(rectangle, formatFavoritePopupHtml(favorite, true))
    })

    rectangle.on('contextmenu', (eventPayload) => {
      const rawEvent = withStoppedContextEvent(eventPayload)
      callbacks.onFavoriteContextMenu?.({
        favorite,
        originalEvent: rawEvent,
        latlng: eventPayload?.latlng || null
      })
    })

    rectangle.addTo(layerGroup)

    const centerMarker = L.marker([center.lat, center.lng], {
      icon: createLeafletDivIcon('favoriteArea')
    })

    centerMarker.on('click', () => {
      focusOnFavorite(favorite, { openPopup: false })
      openLayerPopup(centerMarker, formatFavoritePopupHtml(favorite, true))
    })

    centerMarker.on('contextmenu', (eventPayload) => {
      const rawEvent = withStoppedContextEvent(eventPayload)
      callbacks.onFavoriteContextMenu?.({
        favorite,
        originalEvent: rawEvent,
        latlng: eventPayload?.latlng || null
      })
    })

    centerMarker.addTo(layerGroup)
  }

  const addPendingPoint = (pendingPoint) => {
    const point = getPendingPointLatLng(pendingPoint)
    if (!point || !layerGroup) {
      return
    }

    const marker = L.marker([point.lat, point.lng], {
      icon: createLeafletDivIcon('pendingPoint')
    })

    marker.on('click', () => {
      map?.setView?.([point.lat, point.lng], 15)
      openLayerPopup(marker, formatPendingPopupHtml(pendingPoint, false))
    })

    marker.on('contextmenu', (eventPayload) => {
      const rawEvent = withStoppedContextEvent(eventPayload)
      callbacks.onPendingFavoriteContextMenu?.({
        pendingFavorite: pendingPoint,
        originalEvent: rawEvent,
        latlng: eventPayload?.latlng || null
      })
    })

    marker.addTo(layerGroup)
  }

  const addPendingArea = (pendingArea) => {
    const bounds = toLeafletBounds(pendingArea)
    const center = getAreaCenterLatLng(pendingArea)
    if (!bounds || !center || !layerGroup) {
      return
    }

    const rectangle = L.rectangle(bounds, {
      color: '#f59e0b',
      fillColor: '#f59e0b',
      fillOpacity: 0.2,
      weight: 2,
      dashArray: '5, 5'
    })

    rectangle.on('click', () => {
      map?.fitBounds?.(bounds, { padding: [50, 50], animate: true })
      openLayerPopup(rectangle, formatPendingPopupHtml(pendingArea, true))
    })

    rectangle.on('contextmenu', (eventPayload) => {
      const rawEvent = withStoppedContextEvent(eventPayload)
      callbacks.onPendingFavoriteContextMenu?.({
        pendingFavorite: pendingArea,
        originalEvent: rawEvent,
        latlng: eventPayload?.latlng || null
      })
    })

    rectangle.addTo(layerGroup)

    const centerMarker = L.marker([center.lat, center.lng], {
      icon: createLeafletDivIcon('pendingArea')
    })

    centerMarker.on('click', () => {
      map?.fitBounds?.(bounds, { padding: [50, 50], animate: true })
      openLayerPopup(centerMarker, formatPendingPopupHtml(pendingArea, true))
    })

    centerMarker.on('contextmenu', (eventPayload) => {
      const rawEvent = withStoppedContextEvent(eventPayload)
      callbacks.onPendingFavoriteContextMenu?.({
        pendingFavorite: pendingArea,
        originalEvent: rawEvent,
        latlng: eventPayload?.latlng || null
      })
    })

    centerMarker.addTo(layerGroup)
  }

  const clearFavoritesLayer = () => {
    clearActivePopup()
    layerGroup?.clearLayers?.()
  }

  const fitToAllItems = ({ favorites = [], pendingPoints = [], pendingAreas = [] }) => {
    if (!map) {
      return
    }

    const boundsPoints = buildBoundsPoints({
      favorites,
      pendingPoints,
      pendingAreas
    })

    if (boundsPoints.length === 0) {
      return
    }

    map.fitBounds(boundsPoints, { padding: [50, 50], maxZoom: 15 })
  }

  const render = ({
    favorites = [],
    pendingPoints = [],
    pendingAreas = [],
    autoFit = false
  } = {}) => {
    if (!map || !layerGroup) {
      return
    }

    clearFavoritesLayer()

    favorites.forEach((favorite) => {
      if (favorite?.type === 'POINT') {
        addFavoritePoint(favorite)
      } else if (favorite?.type === 'AREA') {
        addFavoriteArea(favorite)
      }
    })

    pendingPoints.forEach((pendingPoint) => {
      addPendingPoint(pendingPoint)
    })

    pendingAreas.forEach((pendingArea) => {
      addPendingArea(pendingArea)
    })

    if (autoFit) {
      fitToAllItems({ favorites, pendingPoints, pendingAreas })
    }
  }

  const setTempPoint = (pointLike) => {
    if (!map || !pointLike) {
      return
    }

    const point = {
      lat: Number(pointLike.lat),
      lng: Number(pointLike.lng)
    }

    if (!Number.isFinite(point.lat) || !Number.isFinite(point.lng)) {
      return
    }

    clearTempPoint()

    tempPointMarker = L.marker([point.lat, point.lng], {
      icon: createLeafletDivIcon('tempPoint')
    }).addTo(map)
  }

  const clearTempPoint = () => {
    if (tempPointMarker && map) {
      map.removeLayer(tempPointMarker)
    }
    tempPointMarker = null
  }

  const focusOnFavorite = (favorite, options = {}) => {
    if (!map || !favorite) {
      return
    }

    const shouldOpenPopup = options.openPopup !== false

    if (favorite.type === 'POINT') {
      const point = getFavoritePointLatLng(favorite)
      if (!point) {
        return
      }

      map.setView([point.lat, point.lng], 15)
      if (shouldOpenPopup) {
        const popup = L.popup()
          .setLatLng([point.lat, point.lng])
          .setContent(formatFavoritePopupHtml(favorite, false))
          .openOn(map)
        activePopupLayer = popup
      }
      return
    }

    if (favorite.type === 'AREA') {
      const bounds = toLeafletBounds(favorite)
      const center = getAreaCenterLatLng(favorite)
      if (!bounds) {
        return
      }

      map.fitBounds(bounds, { padding: [50, 50], animate: true })
      if (shouldOpenPopup && center) {
        const popup = L.popup()
          .setLatLng([center.lat, center.lng])
          .setContent(formatFavoritePopupHtml(favorite, true))
          .openOn(map)
        activePopupLayer = popup
      }
    }
  }

  const initialize = (mapInstance) => {
    map = mapInstance
    layerGroup = L.layerGroup().addTo(map)
  }

  const cleanup = () => {
    clearTempPoint()
    clearFavoritesLayer()

    if (layerGroup && map) {
      map.removeLayer(layerGroup)
    }

    layerGroup = null
    map = null
  }

  return {
    initialize,
    render,
    setTempPoint,
    clearTempPoint,
    focusOnFavorite,
    cleanup
  }
}
