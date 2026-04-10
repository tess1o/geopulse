import maplibregl from 'maplibre-gl'
import '@/maps/shared/styles/mapPopupContent.css'
import {
  buildBoundsPoints,
  getAreaCenterLatLng,
  getAreaPolygonLngLat,
  getFavoritePointLatLng,
  getPendingPointLatLng,
  toLeafletBounds
} from '@/maps/favoritesManagement/shared/favoritesManagementGeometry'
import {
  createVectorMarkerElement,
  getVectorMarkerAnchor
} from '@/maps/favoritesManagement/shared/favoritesManagementMarkerFactory'
import {
  createFeatureCollection,
  ensureGeoJsonSource,
  ensureLayer,
  isMapLibreMap,
  normalizeLeafletBoundsToMapLibre,
  removeLayers,
  removeSources
} from '@/maps/vector/utils/maplibreLayerUtils'

const TOKEN = `favorites-management-${Math.random().toString(36).slice(2, 10)}`

const layerIds = {
  savedAreasSourceId: `${TOKEN}-saved-areas-source`,
  savedAreasFillId: `${TOKEN}-saved-areas-fill`,
  savedAreasLineId: `${TOKEN}-saved-areas-line`,
  pendingAreasSourceId: `${TOKEN}-pending-areas-source`,
  pendingAreasFillId: `${TOKEN}-pending-areas-fill`,
  pendingAreasLineId: `${TOKEN}-pending-areas-line`
}

const buildFavoritePopupHtml = (favorite, isArea = false) => {
  const safeName = favorite?.name || 'Favorite'
  return isArea
    ? `<strong>${safeName}</strong><br>Area Favorite`
    : `<strong>${safeName}</strong>`
}

const buildPendingPopupHtml = (pendingFavorite, isArea = false) => {
  const safeName = pendingFavorite?.name || 'Pending favorite'
  return isArea
    ? `<strong>${safeName}</strong><br><span style="color: #f59e0b;">Pending Area</span>`
    : `<strong>${safeName}</strong><br><span style="color: #f59e0b;">Pending</span>`
}

export const createVectorFavoritesManagementMapAdapter = (callbacks = {}) => {
  let map = null
  let styleLoadHandler = null
  let lastRenderPayload = null
  let activePopup = null
  let tempPointMarker = null
  const markers = []

  const mapEventHandlers = {
    click: null,
    contextmenu: null,
    mousemove: null,
    mouseleave: null
  }

  const clearActivePopup = () => {
    if (activePopup) {
      activePopup.remove()
      activePopup = null
    }
  }

  const openPopup = (lng, lat, html) => {
    if (!isMapLibreMap(map)) {
      return
    }

    clearActivePopup()
    activePopup = new maplibregl.Popup({
      closeButton: true,
      closeOnClick: true,
      closeOnMove: false,
      maxWidth: '300px',
      offset: 12,
      className: 'gp-favorites-popup-container'
    })
      .setLngLat([lng, lat])
      .setHTML(html)
      .addTo(map)
  }

  const stopContextMenuEvent = (rawEvent) => {
    rawEvent?.preventDefault?.()
    rawEvent?.stopPropagation?.()
    rawEvent?.stopImmediatePropagation?.()
    if (rawEvent) {
      rawEvent.cancelBubble = true
    }
    callbacks.onContextMenuHandled?.()
  }

  const registerMarker = (marker, cleanup) => {
    markers.push({
      marker,
      cleanup
    })
  }

  const clearMarkers = () => {
    markers.forEach((entry) => {
      entry.cleanup?.()
      entry.marker?.remove?.()
    })
    markers.length = 0
  }

  const getInteractiveLayerIds = () => {
    if (!isMapLibreMap(map)) {
      return []
    }

    return [
      layerIds.savedAreasFillId,
      layerIds.savedAreasLineId,
      layerIds.pendingAreasFillId,
      layerIds.pendingAreasLineId
    ].filter((layerId) => map.getLayer(layerId))
  }

  const getFeatureAtEventPoint = (eventPayload) => {
    if (!isMapLibreMap(map) || !eventPayload?.point) {
      return null
    }

    const interactiveLayerIds = getInteractiveLayerIds()
    if (interactiveLayerIds.length === 0) {
      return null
    }

    const features = map.queryRenderedFeatures(eventPayload.point, {
      layers: interactiveLayerIds
    })

    if (!Array.isArray(features) || features.length === 0) {
      return null
    }

    return features[0]
  }

  const parseFeaturePayload = (feature) => {
    const rawPayload = feature?.properties?.payload
    if (!rawPayload) {
      return null
    }

    try {
      return JSON.parse(rawPayload)
    } catch {
      return null
    }
  }

  const addPointMarker = ({ item, variant, onClick, onContextMenu, zIndex }) => {
    const point = variant === 'favoritePoint' ? getFavoritePointLatLng(item) : getPendingPointLatLng(item)
    if (!point || !isMapLibreMap(map)) {
      return
    }

    const element = createVectorMarkerElement(variant)
    element.style.zIndex = String(zIndex)
    element.style.cursor = 'pointer'

    const marker = new maplibregl.Marker({
      element,
      anchor: getVectorMarkerAnchor(variant)
    })
      .setLngLat([point.lng, point.lat])
      .addTo(map)

    const handleClick = (eventPayload) => {
      eventPayload.preventDefault()
      eventPayload.stopPropagation()
      onClick?.(point)
    }

    const handleContextMenu = (eventPayload) => {
      stopContextMenuEvent(eventPayload)
      onContextMenu?.(point, eventPayload)
    }

    element.addEventListener('click', handleClick)
    element.addEventListener('contextmenu', handleContextMenu)

    registerMarker(marker, () => {
      element.removeEventListener('click', handleClick)
      element.removeEventListener('contextmenu', handleContextMenu)
    })
  }

  const addAreaCenterMarker = ({ item, variant, onClick, onContextMenu, zIndex }) => {
    const center = getAreaCenterLatLng(item)
    if (!center || !isMapLibreMap(map)) {
      return
    }

    const element = createVectorMarkerElement(variant)
    element.style.zIndex = String(zIndex)
    element.style.cursor = 'pointer'

    const marker = new maplibregl.Marker({
      element,
      anchor: getVectorMarkerAnchor(variant)
    })
      .setLngLat([center.lng, center.lat])
      .addTo(map)

    const handleClick = (eventPayload) => {
      eventPayload.preventDefault()
      eventPayload.stopPropagation()
      onClick?.(center)
    }

    const handleContextMenu = (eventPayload) => {
      stopContextMenuEvent(eventPayload)
      onContextMenu?.(center, eventPayload)
    }

    element.addEventListener('click', handleClick)
    element.addEventListener('contextmenu', handleContextMenu)

    registerMarker(marker, () => {
      element.removeEventListener('click', handleClick)
      element.removeEventListener('contextmenu', handleContextMenu)
    })
  }

  const clearAreaLayers = () => {
    if (!isMapLibreMap(map)) {
      return
    }

    removeLayers(map, [
      layerIds.savedAreasLineId,
      layerIds.savedAreasFillId,
      layerIds.pendingAreasLineId,
      layerIds.pendingAreasFillId
    ])

    removeSources(map, [
      layerIds.savedAreasSourceId,
      layerIds.pendingAreasSourceId
    ])
  }

  const syncAreaLayers = (payload) => {
    if (!isMapLibreMap(map)) {
      return
    }

    const favoriteAreaFeatures = []
    payload?.favorites?.forEach((favorite) => {
      if (favorite?.type !== 'AREA') {
        return
      }

      const polygon = getAreaPolygonLngLat(favorite)
      if (!polygon) {
        return
      }

      favoriteAreaFeatures.push({
        type: 'Feature',
        geometry: {
          type: 'Polygon',
          coordinates: [polygon]
        },
        properties: {
          itemType: 'favorite',
          payload: JSON.stringify(favorite)
        }
      })
    })

    const pendingAreaFeatures = []
    payload?.pendingAreas?.forEach((pendingArea) => {
      const polygon = getAreaPolygonLngLat(pendingArea)
      if (!polygon) {
        return
      }

      pendingAreaFeatures.push({
        type: 'Feature',
        geometry: {
          type: 'Polygon',
          coordinates: [polygon]
        },
        properties: {
          itemType: 'pending',
          payload: JSON.stringify(pendingArea)
        }
      })
    })

    ensureGeoJsonSource(map, layerIds.savedAreasSourceId, createFeatureCollection(favoriteAreaFeatures))
    ensureGeoJsonSource(map, layerIds.pendingAreasSourceId, createFeatureCollection(pendingAreaFeatures))

    ensureLayer(map, {
      id: layerIds.savedAreasFillId,
      type: 'fill',
      source: layerIds.savedAreasSourceId,
      paint: {
        'fill-color': '#ef4444',
        'fill-opacity': 0.2
      }
    })

    ensureLayer(map, {
      id: layerIds.savedAreasLineId,
      type: 'line',
      source: layerIds.savedAreasSourceId,
      paint: {
        'line-color': '#ef4444',
        'line-width': 2
      }
    })

    ensureLayer(map, {
      id: layerIds.pendingAreasFillId,
      type: 'fill',
      source: layerIds.pendingAreasSourceId,
      paint: {
        'fill-color': '#f59e0b',
        'fill-opacity': 0.2
      }
    })

    ensureLayer(map, {
      id: layerIds.pendingAreasLineId,
      type: 'line',
      source: layerIds.pendingAreasSourceId,
      paint: {
        'line-color': '#f59e0b',
        'line-width': 2,
        'line-dasharray': [2, 2]
      }
    })
  }

  const fitToAllItems = ({ favorites = [], pendingPoints = [], pendingAreas = [] }) => {
    if (!isMapLibreMap(map)) {
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

    const normalizedBounds = normalizeLeafletBoundsToMapLibre(boundsPoints)
    if (!normalizedBounds) {
      return
    }

    map.fitBounds(normalizedBounds, {
      padding: 50,
      maxZoom: 15,
      duration: 0
    })
  }

  const focusOnFavorite = (favorite, options = {}) => {
    if (!isMapLibreMap(map) || !favorite) {
      return
    }

    const shouldOpenPopup = options.openPopup !== false
    if (favorite.type === 'POINT') {
      const point = getFavoritePointLatLng(favorite)
      if (!point) {
        return
      }

      map.jumpTo({
        center: [point.lng, point.lat],
        zoom: 15
      })

      if (shouldOpenPopup) {
        openPopup(point.lng, point.lat, buildFavoritePopupHtml(favorite, false))
      }
      return
    }

    if (favorite.type === 'AREA') {
      const bounds = toLeafletBounds(favorite)
      const center = getAreaCenterLatLng(favorite)
      if (!bounds) {
        return
      }

      const normalizedBounds = normalizeLeafletBoundsToMapLibre(bounds)
      if (!normalizedBounds) {
        return
      }

      map.fitBounds(normalizedBounds, {
        padding: 50,
        maxZoom: 15,
        duration: 0
      })

      if (shouldOpenPopup && center) {
        openPopup(center.lng, center.lat, buildFavoritePopupHtml(favorite, true))
      }
    }
  }

  const focusOnPendingArea = (pendingArea) => {
    if (!isMapLibreMap(map)) {
      return
    }

    const bounds = toLeafletBounds(pendingArea)
    const center = getAreaCenterLatLng(pendingArea)
    if (!bounds) {
      return
    }

    const normalizedBounds = normalizeLeafletBoundsToMapLibre(bounds)
    if (!normalizedBounds) {
      return
    }

    map.fitBounds(normalizedBounds, {
      padding: 50,
      maxZoom: 15,
      duration: 0
    })

    if (center) {
      openPopup(center.lng, center.lat, buildPendingPopupHtml(pendingArea, true))
    }
  }

  const attachMapEvents = () => {
    if (!isMapLibreMap(map) || mapEventHandlers.click) {
      return
    }

    mapEventHandlers.click = (eventPayload) => {
      const feature = getFeatureAtEventPoint(eventPayload)
      const payload = parseFeaturePayload(feature)
      const itemType = feature?.properties?.itemType
      if (!payload || !itemType) {
        return
      }

      if (itemType === 'favorite') {
        focusOnFavorite(payload, { openPopup: true })
      } else if (itemType === 'pending') {
        focusOnPendingArea(payload)
      }
    }

    mapEventHandlers.contextmenu = (eventPayload) => {
      const feature = getFeatureAtEventPoint(eventPayload)
      const payload = parseFeaturePayload(feature)
      const itemType = feature?.properties?.itemType
      if (!payload || !itemType) {
        return
      }

      const rawEvent = eventPayload?.originalEvent || eventPayload
      stopContextMenuEvent(rawEvent)

      if (itemType === 'favorite') {
        callbacks.onFavoriteContextMenu?.({
          favorite: payload,
          originalEvent: rawEvent,
          latlng: eventPayload?.lngLat
            ? { lat: eventPayload.lngLat.lat, lng: eventPayload.lngLat.lng }
            : null
        })
      } else if (itemType === 'pending') {
        callbacks.onPendingFavoriteContextMenu?.({
          pendingFavorite: payload,
          originalEvent: rawEvent,
          latlng: eventPayload?.lngLat
            ? { lat: eventPayload.lngLat.lat, lng: eventPayload.lngLat.lng }
            : null
        })
      }
    }

    mapEventHandlers.mousemove = (eventPayload) => {
      const feature = getFeatureAtEventPoint(eventPayload)
      map.getCanvas().style.cursor = feature ? 'pointer' : ''
    }

    mapEventHandlers.mouseleave = () => {
      map.getCanvas().style.cursor = ''
    }

    map.on('click', mapEventHandlers.click)
    map.on('contextmenu', mapEventHandlers.contextmenu)
    map.on('mousemove', mapEventHandlers.mousemove)
    map.on('mouseleave', mapEventHandlers.mouseleave)
  }

  const detachMapEvents = () => {
    if (!isMapLibreMap(map)) {
      return
    }

    if (mapEventHandlers.click) {
      map.off('click', mapEventHandlers.click)
      mapEventHandlers.click = null
    }
    if (mapEventHandlers.contextmenu) {
      map.off('contextmenu', mapEventHandlers.contextmenu)
      mapEventHandlers.contextmenu = null
    }
    if (mapEventHandlers.mousemove) {
      map.off('mousemove', mapEventHandlers.mousemove)
      mapEventHandlers.mousemove = null
    }
    if (mapEventHandlers.mouseleave) {
      map.off('mouseleave', mapEventHandlers.mouseleave)
      mapEventHandlers.mouseleave = null
    }
  }

  const render = ({
    favorites = [],
    pendingPoints = [],
    pendingAreas = [],
    autoFit = false
  } = {}) => {
    if (!isMapLibreMap(map)) {
      return
    }

    lastRenderPayload = {
      favorites,
      pendingPoints,
      pendingAreas,
      autoFit
    }

    clearMarkers()
    clearActivePopup()
    syncAreaLayers(lastRenderPayload)

    favorites.forEach((favorite) => {
      if (favorite?.type === 'POINT') {
        addPointMarker({
          item: favorite,
          variant: 'favoritePoint',
          zIndex: 180,
          onClick: (point) => {
            map.jumpTo({
              center: [point.lng, point.lat],
              zoom: 15
            })
            openPopup(point.lng, point.lat, buildFavoritePopupHtml(favorite, false))
          },
          onContextMenu: (point, rawEvent) => {
            callbacks.onFavoriteContextMenu?.({
              favorite,
              originalEvent: rawEvent,
              latlng: point
            })
          }
        })
      } else if (favorite?.type === 'AREA') {
        addAreaCenterMarker({
          item: favorite,
          variant: 'favoriteArea',
          zIndex: 180,
          onClick: (center) => {
            focusOnFavorite(favorite, { openPopup: false })
            openPopup(center.lng, center.lat, buildFavoritePopupHtml(favorite, true))
          },
          onContextMenu: (center, rawEvent) => {
            callbacks.onFavoriteContextMenu?.({
              favorite,
              originalEvent: rawEvent,
              latlng: center
            })
          }
        })
      }
    })

    pendingPoints.forEach((pendingPoint) => {
      addPointMarker({
        item: pendingPoint,
        variant: 'pendingPoint',
        zIndex: 185,
        onClick: (point) => {
          map.jumpTo({
            center: [point.lng, point.lat],
            zoom: 15
          })
          openPopup(point.lng, point.lat, buildPendingPopupHtml(pendingPoint, false))
        },
        onContextMenu: (point, rawEvent) => {
          callbacks.onPendingFavoriteContextMenu?.({
            pendingFavorite: pendingPoint,
            originalEvent: rawEvent,
            latlng: point
          })
        }
      })
    })

    pendingAreas.forEach((pendingArea) => {
      addAreaCenterMarker({
        item: pendingArea,
        variant: 'pendingArea',
        zIndex: 185,
        onClick: () => {
          focusOnPendingArea(pendingArea)
        },
        onContextMenu: (center, rawEvent) => {
          callbacks.onPendingFavoriteContextMenu?.({
            pendingFavorite: pendingArea,
            originalEvent: rawEvent,
            latlng: center
          })
        }
      })
    })

    if (autoFit) {
      fitToAllItems(lastRenderPayload)
    }
  }

  const setTempPoint = (pointLike) => {
    if (!isMapLibreMap(map) || !pointLike) {
      return
    }

    const lat = Number(pointLike.lat)
    const lng = Number(pointLike.lng)
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
      return
    }

    clearTempPoint()

    const element = createVectorMarkerElement('tempPoint')
    element.style.zIndex = '190'
    element.style.cursor = 'pointer'

    tempPointMarker = new maplibregl.Marker({
      element,
      anchor: getVectorMarkerAnchor('tempPoint')
    })
      .setLngLat([lng, lat])
      .addTo(map)
  }

  const clearTempPoint = () => {
    if (tempPointMarker) {
      tempPointMarker.remove()
      tempPointMarker = null
    }
  }

  const initialize = (mapInstance) => {
    map = mapInstance
    attachMapEvents()

    styleLoadHandler = () => {
      if (!lastRenderPayload) {
        return
      }

      syncAreaLayers(lastRenderPayload)
    }

    map.on('style.load', styleLoadHandler)
  }

  const cleanup = () => {
    clearTempPoint()
    clearMarkers()
    clearActivePopup()
    detachMapEvents()
    clearAreaLayers()

    if (isMapLibreMap(map) && styleLoadHandler) {
      map.off('style.load', styleLoadHandler)
    }

    styleLoadHandler = null
    lastRenderPayload = null
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
