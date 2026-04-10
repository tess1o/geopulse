import L from 'leaflet'
import { buildRenderableRuleAreas, toRuleAreaBounds } from '@/maps/geofences/shared/geofenceRuleAreaUtils'

export const createRasterGeofenceRulesMapAdapter = (callbacks = {}) => {
  let map = null
  let editingLayer = null
  let ruleAreasLayerGroup = null

  const ensureRuleAreasLayerGroup = () => {
    if (!map) {
      return null
    }

    if (!ruleAreasLayerGroup) {
      ruleAreasLayerGroup = L.layerGroup().addTo(map)
    }

    return ruleAreasLayerGroup
  }

  const clearEditingArea = () => {
    if (!map || !editingLayer) {
      editingLayer = null
      return
    }

    map.removeLayer(editingLayer)
    editingLayer = null
  }

  const syncEditingArea = (areaLike, options = {}) => {
    if (!map) {
      return false
    }

    const bounds = toRuleAreaBounds(areaLike)
    if (!bounds) {
      clearEditingArea()
      return false
    }

    clearEditingArea()

    editingLayer = L.rectangle(bounds, {
      color: '#e91e63',
      weight: 2,
      fill: false
    }).addTo(map)

    if (options.focus) {
      map.fitBounds(bounds, { padding: [20, 20] })
    }

    return true
  }

  const syncRuleAreas = ({
    rules = [],
    editingRuleId = null,
    editingAreaExists = false,
    popupBuilder = null
  } = {}) => {
    if (!map) {
      return
    }

    const layerGroup = ensureRuleAreasLayerGroup()
    if (!layerGroup) {
      return
    }

    layerGroup.clearLayers()

    const ruleAreas = buildRenderableRuleAreas({
      rules,
      editingRuleId,
      editingAreaExists
    })

    ruleAreas.forEach(({ rule, bounds }) => {
      const rectangle = L.rectangle(bounds, {
        color: rule?.status === 'ACTIVE' ? '#3b82f6' : '#94a3b8',
        weight: 2,
        dashArray: '6 4',
        fill: true,
        fillOpacity: 0.06,
        interactive: true
      })

      if (typeof popupBuilder === 'function') {
        rectangle.bindPopup(popupBuilder(rule), {
          maxWidth: 360,
          className: 'geofence-area-popup'
        })
      }

      rectangle.addTo(layerGroup)
    })
  }

  const fitAllRuleAreas = ({
    rules = [],
    editingRuleId = null,
    editingAreaExists = false
  } = {}) => {
    if (!map) {
      return false
    }

    const ruleAreas = buildRenderableRuleAreas({
      rules,
      editingRuleId,
      editingAreaExists
    })

    if (ruleAreas.length === 0) {
      return false
    }

    const combinedBounds = L.latLngBounds(ruleAreas[0].bounds)
    for (let index = 1; index < ruleAreas.length; index += 1) {
      combinedBounds.extend(ruleAreas[index].bounds)
    }

    map.fitBounds(combinedBounds, {
      padding: [24, 24],
      maxZoom: 14
    })

    return true
  }

  const fitArea = (areaLike, options = {}) => {
    if (!map) {
      return false
    }

    const bounds = toRuleAreaBounds(areaLike)
    if (!bounds) {
      return false
    }

    map.fitBounds(bounds, {
      padding: [20, 20],
      ...options
    })

    return true
  }

  const destroy = () => {
    clearEditingArea()

    if (ruleAreasLayerGroup) {
      ruleAreasLayerGroup.clearLayers()
      if (map) {
        map.removeLayer(ruleAreasLayerGroup)
      }
      ruleAreasLayerGroup = null
    }

    map = null
  }

  return {
    initialize(mapInstance) {
      map = mapInstance
    },
    syncEditingArea,
    clearEditingArea,
    syncRuleAreas,
    fitAllRuleAreas,
    fitArea,
    destroy
  }
}
