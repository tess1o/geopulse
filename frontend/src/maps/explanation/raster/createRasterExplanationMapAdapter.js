import L from 'leaflet'
import {
  buildPointPopupElement,
  EXPLANATION_MAP_COLORS,
  EXPLANATION_POPUP_CLASS_NAME,
  pointPaint,
  pointsInDrawOrder,
  pointState
} from '@/maps/explanation/shared/explanationMapModel'

const FIT_OPTIONS = { padding: [28, 28], maxZoom: 18 }

const toLeafletStyle = (paint) => ({
  radius: paint.radius,
  fillColor: paint.fillColor,
  fillOpacity: paint.fillOpacity,
  color: paint.strokeColor,
  weight: paint.strokeWidth,
  opacity: paint.strokeOpacity
})

const lineStyle = (type) => ({
  color: type === 'event' ? EXPLANATION_MAP_COLORS.path
    : type === 'gap' ? EXPLANATION_MAP_COLORS.gap : EXPLANATION_MAP_COLORS.contextPath,
  weight: type === 'event' ? 3 : 2,
  opacity: type === 'event' ? 0.6 : 0.7,
  dashArray: type === 'gap' ? '6 8' : null,
  interactive: false
})

/**
 * Leaflet drawing of the explanation map model.
 * @param callbacks.describePoint (point) => { title, lines } for the popup of a clicked point
 */
export const createRasterExplanationMapAdapter = (callbacks = {}) => {
  let map = null
  let group = null
  let markers = []
  let selected = null

  const clear = () => {
    if (group) {
      group.clearLayers()
    }
    markers = []
  }

  const restyle = () => {
    markers.forEach(({ marker, point }) => marker.setStyle(toLeafletStyle(pointPaint(point, pointState(point, selected)))))
  }

  const render = (model) => {
    if (!map || !model) return false
    if (!group) {
      group = L.layerGroup().addTo(map)
    }
    clear()
    selected = null

    if (model.circle) {
      L.circle([model.circle.latitude, model.circle.longitude], {
        radius: model.circle.radiusMeters,
        color: EXPLANATION_MAP_COLORS.radius,
        weight: 2,
        dashArray: '6 6',
        fillColor: EXPLANATION_MAP_COLORS.radius,
        fillOpacity: 0.06,
        interactive: false
      }).addTo(group)
    }

    model.lines.forEach((line) => {
      L.polyline(line.coordinates, lineStyle(line.type)).addTo(group)
    })

    if (model.anchor) {
      L.circleMarker([model.anchor.latitude, model.anchor.longitude], {
        radius: 6,
        color: EXPLANATION_MAP_COLORS.deciding,
        weight: 2,
        fillColor: EXPLANATION_MAP_COLORS.outline,
        fillOpacity: 1,
        interactive: false
      }).addTo(group)
    }

    pointsInDrawOrder(model.points).forEach((point) => {
      const marker = L.circleMarker([point.latitude, point.longitude], toLeafletStyle(pointPaint(point)))
      if (typeof callbacks.describePoint === 'function') {
        marker.on('click', () => {
          marker.bindPopup(buildPointPopupElement(callbacks.describePoint(point)), { maxWidth: 280, className: EXPLANATION_POPUP_CLASS_NAME }).openPopup()
        })
      }
      marker.addTo(group)
      markers.push({ marker, point })
    })

    fitTo(model.bounds)
    return true
  }

  const fitTo = (bounds) => {
    if (!map || !bounds) return
    map.fitBounds(bounds, FIT_OPTIONS)
  }

  /** Emphasizes the points with these indexes, or shows all as normal for null. */
  const highlight = (indexes) => {
    selected = indexes ? new Set(indexes) : null
    restyle()
  }

  const destroy = () => {
    clear()
    if (map && group) {
      map.removeLayer(group)
    }
    group = null
    map = null
  }

  return {
    initialize(mapInstance) {
      map = mapInstance
    },
    render,
    highlight,
    fitTo,
    destroy
  }
}
