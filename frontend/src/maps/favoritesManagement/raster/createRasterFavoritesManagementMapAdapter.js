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
import MapInfoPopup from '@/maps/shared/popups/MapInfoPopup.vue'
import { mountMapPopup } from '@/maps/shared/popups/mountMapPopup'
import { buildFavoriteManagementPopupModel } from '@/maps/shared/popups/favoritePopupModel'
import {
  getMapPopupVariantClassName,
  MAP_POPUP_COMPACT_MAX_WIDTH_PX
} from '@/maps/shared/popups/mapPopupOptions'

const createLeafletDivIcon = (variant) => {
  const shape = getLeafletIconShape(variant)
  return L.divIcon({
    className: FAVORITES_MARKER_CLASSES[variant],
    html: createMarkerHtml(variant),
    iconSize: shape.iconSize,
    iconAnchor: shape.iconAnchor
  })
}

export const createRasterFavoritesManagementMapAdapter = (callbacks = {}) => {
  let map = null
  let layerGroup = null
  let tempPointMarker = null
  let activePopupLayer = null
  let activePopupMount = null

  const clearActivePopup = () => {
    if (!activePopupLayer) {
      activePopupMount?.unmount?.()
      activePopupMount = null
      return
    }

    if (typeof activePopupLayer.closePopup === 'function') {
      activePopupLayer.closePopup()
    } else if (typeof activePopupLayer.remove === 'function') {
      activePopupLayer.remove()
    }

    activePopupMount?.unmount?.()
    activePopupLayer = null
    activePopupMount = null
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

  const openLayerPopup = (layer, popupModel) => {
    if (!map || !layer) {
      return
    }

    clearActivePopup()
    activePopupMount = mountMapPopup(MapInfoPopup, popupModel)
    layer.bindPopup(activePopupMount.element, {
      maxWidth: MAP_POPUP_COMPACT_MAX_WIDTH_PX,
      className: getMapPopupVariantClassName('compact', 'gp-favorites-popup-container')
    })
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
      openLayerPopup(marker, buildFavoriteManagementPopupModel(favorite, { isArea: false }))
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
      openLayerPopup(rectangle, buildFavoriteManagementPopupModel(favorite, { isArea: true }))
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
      openLayerPopup(centerMarker, buildFavoriteManagementPopupModel(favorite, { isArea: true }))
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
      openLayerPopup(marker, buildFavoriteManagementPopupModel(pendingPoint, {
        pending: true,
        isArea: false
      }))
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
      openLayerPopup(rectangle, buildFavoriteManagementPopupModel(pendingArea, {
        pending: true,
        isArea: true
      }))
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
      openLayerPopup(centerMarker, buildFavoriteManagementPopupModel(pendingArea, {
        pending: true,
        isArea: true
      }))
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

  const focusOnPoint = (pointLike, options = {}) => {
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

    map.setView([point.lat, point.lng], options.zoom || 15)
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
        clearActivePopup()
        activePopupMount = mountMapPopup(
          MapInfoPopup,
          buildFavoriteManagementPopupModel(favorite, { isArea: false })
        )
        const popup = L.popup({
          maxWidth: MAP_POPUP_COMPACT_MAX_WIDTH_PX,
          className: getMapPopupVariantClassName('compact', 'gp-favorites-popup-container')
        })
          .setLatLng([point.lat, point.lng])
          .setContent(activePopupMount.element)
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
        clearActivePopup()
        activePopupMount = mountMapPopup(
          MapInfoPopup,
          buildFavoriteManagementPopupModel(favorite, { isArea: true })
        )
        const popup = L.popup({
          maxWidth: MAP_POPUP_COMPACT_MAX_WIDTH_PX,
          className: getMapPopupVariantClassName('compact', 'gp-favorites-popup-container')
        })
          .setLatLng([center.lat, center.lng])
          .setContent(activePopupMount.element)
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
    focusOnPoint,
    focusOnFavorite,
    cleanup
  }
}
