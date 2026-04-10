import maplibregl from 'maplibre-gl'
import {
  isMapLibreMap,
  normalizeLeafletBoundsToMapLibre
} from '@/maps/vector/utils/maplibreLayerUtils'
import { buildRenderableRuleAreas, toRuleAreaBounds } from '@/maps/geofences/shared/geofenceRuleAreaUtils'

const toPolygonCoordinates = (bounds) => {
  if (!Array.isArray(bounds) || bounds.length !== 2) {
    return null
  }

  const [southWest, northEast] = bounds
  if (!Array.isArray(southWest) || !Array.isArray(northEast)) {
    return null
  }

  const south = Number(southWest[0])
  const west = Number(southWest[1])
  const north = Number(northEast[0])
  const east = Number(northEast[1])

  if (![south, west, north, east].every(Number.isFinite)) {
    return null
  }

  return [
    [west, south],
    [east, south],
    [east, north],
    [west, north],
    [west, south]
  ]
}

const createFeatureCollection = (features = []) => ({
  type: 'FeatureCollection',
  features
})

export const createVectorGeofenceRulesMapAdapter = () => {
  const token = `geofence-rules-${Math.random().toString(36).slice(2, 10)}`

  const ids = {
    editingSourceId: `${token}-editing-source`,
    editingLayerId: `${token}-editing-layer`,
    rulesSourceId: `${token}-rules-source`,
    rulesFillLayerId: `${token}-rules-fill-layer`,
    rulesLineLayerId: `${token}-rules-line-layer`
  }

  let map = null
  let styleLoadHandler = null
  let popup = null
  const ruleLookup = new Map()

  let lastEditingArea = null
  let lastRuleSyncState = {
    rules: [],
    editingRuleId: null,
    editingAreaExists: false,
    popupBuilder: null
  }

  const eventHandlers = {
    click: null,
    mousemove: null,
    mouseleave: null
  }

  const clearPopup = () => {
    if (popup) {
      popup.remove()
      popup = null
    }
  }

  const removeEditingLayers = () => {
    if (!isMapLibreMap(map)) {
      return
    }

    if (map.getLayer(ids.editingLayerId)) {
      map.removeLayer(ids.editingLayerId)
    }
    if (map.getSource(ids.editingSourceId)) {
      map.removeSource(ids.editingSourceId)
    }
  }

  const removeRuleLayers = () => {
    if (!isMapLibreMap(map)) {
      return
    }

    if (map.getLayer(ids.rulesLineLayerId)) {
      map.removeLayer(ids.rulesLineLayerId)
    }
    if (map.getLayer(ids.rulesFillLayerId)) {
      map.removeLayer(ids.rulesFillLayerId)
    }
    if (map.getSource(ids.rulesSourceId)) {
      map.removeSource(ids.rulesSourceId)
    }
  }

  const ensureEditingArea = (areaLike) => {
    const bounds = toRuleAreaBounds(areaLike)
    if (!bounds || !isMapLibreMap(map)) {
      removeEditingLayers()
      return false
    }

    const coordinates = toPolygonCoordinates(bounds)
    if (!coordinates) {
      removeEditingLayers()
      return false
    }

    const data = createFeatureCollection([
      {
        type: 'Feature',
        geometry: {
          type: 'Polygon',
          coordinates: [coordinates]
        },
        properties: {}
      }
    ])

    const source = map.getSource(ids.editingSourceId)
    if (source && typeof source.setData === 'function') {
      source.setData(data)
    } else {
      map.addSource(ids.editingSourceId, {
        type: 'geojson',
        data
      })
    }

    if (!map.getLayer(ids.editingLayerId)) {
      map.addLayer({
        id: ids.editingLayerId,
        type: 'line',
        source: ids.editingSourceId,
        paint: {
          'line-color': '#e91e63',
          'line-width': 2,
          'line-opacity': 1
        }
      })
    }

    return true
  }

  const ensureRuleAreas = ({
    rules = [],
    editingRuleId = null,
    editingAreaExists = false
  } = {}) => {
    if (!isMapLibreMap(map)) {
      return
    }

    const ruleAreas = buildRenderableRuleAreas({
      rules,
      editingRuleId,
      editingAreaExists
    })

    ruleLookup.clear()

    const features = ruleAreas
      .map(({ rule, bounds }) => {
        const coordinates = toPolygonCoordinates(bounds)
        if (!coordinates) {
          return null
        }

        const normalizedRuleId = String(rule?.id ?? '')
        ruleLookup.set(normalizedRuleId, rule)

        return {
          type: 'Feature',
          geometry: {
            type: 'Polygon',
            coordinates: [coordinates]
          },
          properties: {
            ruleId: normalizedRuleId,
            status: String(rule?.status || '')
          }
        }
      })
      .filter(Boolean)

    const data = createFeatureCollection(features)

    const source = map.getSource(ids.rulesSourceId)
    if (source && typeof source.setData === 'function') {
      source.setData(data)
    } else {
      map.addSource(ids.rulesSourceId, {
        type: 'geojson',
        data
      })
    }

    if (!map.getLayer(ids.rulesFillLayerId)) {
      map.addLayer({
        id: ids.rulesFillLayerId,
        type: 'fill',
        source: ids.rulesSourceId,
        paint: {
          'fill-color': [
            'case',
            ['==', ['get', 'status'], 'ACTIVE'],
            '#3b82f6',
            '#94a3b8'
          ],
          'fill-opacity': 0.06
        }
      })
    }

    if (!map.getLayer(ids.rulesLineLayerId)) {
      map.addLayer({
        id: ids.rulesLineLayerId,
        type: 'line',
        source: ids.rulesSourceId,
        paint: {
          'line-color': [
            'case',
            ['==', ['get', 'status'], 'ACTIVE'],
            '#3b82f6',
            '#94a3b8'
          ],
          'line-width': 2,
          'line-dasharray': [2, 2],
          'line-opacity': 0.95
        }
      })
    }
  }

  const fitBoundsFromPoints = (points, options = {}) => {
    if (!isMapLibreMap(map) || !Array.isArray(points) || points.length === 0) {
      return false
    }

    const normalizedBounds = normalizeLeafletBoundsToMapLibre(points)
    if (!normalizedBounds) {
      return false
    }

    map.fitBounds(normalizedBounds, {
      padding: 24,
      maxZoom: 14,
      ...options
    })

    return true
  }

  const syncEditingArea = (areaLike, options = {}) => {
    if (!isMapLibreMap(map)) {
      return false
    }

    lastEditingArea = areaLike || null

    const hasEditingArea = ensureEditingArea(areaLike)
    if (hasEditingArea && options.focus) {
      fitArea(areaLike)
    }

    return hasEditingArea
  }

  const clearEditingArea = () => {
    lastEditingArea = null
    removeEditingLayers()
  }

  const syncRuleAreas = ({
    rules = [],
    editingRuleId = null,
    editingAreaExists = false,
    popupBuilder = null
  } = {}) => {
    lastRuleSyncState = {
      rules,
      editingRuleId,
      editingAreaExists,
      popupBuilder
    }

    ensureRuleAreas(lastRuleSyncState)
  }

  const fitAllRuleAreas = ({
    rules = [],
    editingRuleId = null,
    editingAreaExists = false
  } = {}) => {
    const ruleAreas = buildRenderableRuleAreas({
      rules,
      editingRuleId,
      editingAreaExists
    })

    const points = []
    ruleAreas.forEach(({ bounds }) => {
      points.push(bounds[0], bounds[1])
    })

    return fitBoundsFromPoints(points)
  }

  const fitArea = (areaLike, options = {}) => {
    const bounds = toRuleAreaBounds(areaLike)
    if (!bounds) {
      return false
    }

    return fitBoundsFromPoints(bounds, {
      padding: 20,
      ...options
    })
  }

  const attachEvents = () => {
    if (!isMapLibreMap(map) || eventHandlers.click) {
      return
    }

    const getFeatureAtPoint = (eventPayload) => {
      const layers = [ids.rulesFillLayerId, ids.rulesLineLayerId].filter((layerId) => map.getLayer(layerId))
      if (layers.length === 0) {
        return null
      }

      const features = map.queryRenderedFeatures(eventPayload.point, { layers })
      if (!Array.isArray(features) || features.length === 0) {
        return null
      }

      return features[0]
    }

    eventHandlers.click = (eventPayload) => {
      const feature = getFeatureAtPoint(eventPayload)
      const ruleId = feature?.properties?.ruleId
      if (!ruleId) {
        return
      }

      const rule = ruleLookup.get(String(ruleId))
      if (!rule || typeof lastRuleSyncState.popupBuilder !== 'function') {
        return
      }

      clearPopup()
      popup = new maplibregl.Popup({
        closeButton: true,
        closeOnClick: true,
        closeOnMove: false,
        className: 'geofence-area-popup',
        maxWidth: '360px',
        offset: 12
      })
        .setLngLat([eventPayload.lngLat.lng, eventPayload.lngLat.lat])
        .setHTML(lastRuleSyncState.popupBuilder(rule))
        .addTo(map)
    }

    eventHandlers.mousemove = (eventPayload) => {
      const feature = getFeatureAtPoint(eventPayload)
      map.getCanvas().style.cursor = feature ? 'pointer' : ''
    }

    eventHandlers.mouseleave = () => {
      map.getCanvas().style.cursor = ''
    }

    map.on('click', eventHandlers.click)
    map.on('mousemove', eventHandlers.mousemove)
    map.on('mouseleave', eventHandlers.mouseleave)
  }

  const detachEvents = () => {
    if (!isMapLibreMap(map)) {
      return
    }

    if (eventHandlers.click) {
      map.off('click', eventHandlers.click)
      eventHandlers.click = null
    }

    if (eventHandlers.mousemove) {
      map.off('mousemove', eventHandlers.mousemove)
      eventHandlers.mousemove = null
    }

    if (eventHandlers.mouseleave) {
      map.off('mouseleave', eventHandlers.mouseleave)
      eventHandlers.mouseleave = null
    }
  }

  const applyCachedState = () => {
    if (!isMapLibreMap(map)) {
      return
    }

    ensureRuleAreas(lastRuleSyncState)
    ensureEditingArea(lastEditingArea)
  }

  const destroy = () => {
    clearPopup()
    detachEvents()
    removeEditingLayers()
    removeRuleLayers()

    if (isMapLibreMap(map) && styleLoadHandler) {
      map.off('style.load', styleLoadHandler)
    }

    styleLoadHandler = null
    map = null
    lastEditingArea = null
    lastRuleSyncState = {
      rules: [],
      editingRuleId: null,
      editingAreaExists: false,
      popupBuilder: null
    }
    ruleLookup.clear()
  }

  return {
    initialize(mapInstance) {
      map = mapInstance
      if (!isMapLibreMap(map)) {
        return
      }

      attachEvents()

      styleLoadHandler = () => {
        applyCachedState()
      }

      map.on('style.load', styleLoadHandler)
    },
    syncEditingArea,
    clearEditingArea,
    syncRuleAreas,
    fitAllRuleAreas,
    fitArea,
    destroy
  }
}
