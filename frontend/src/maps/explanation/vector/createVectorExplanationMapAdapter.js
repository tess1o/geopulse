import * as maplibregl from 'maplibre-gl'
import {
  ensureGeoJsonSource,
  ensureLayer,
  isMapLibreMap,
  removeLayers,
  removeSources,
  safeFitBounds
} from '@/maps/vector/utils/maplibreLayerUtils'
import {
  buildPointPopupElement,
  circleRing,
  EXPLANATION_MAP_COLORS,
  EXPLANATION_POPUP_CLASS_NAME,
  pointPaint,
  pointsInDrawOrder,
  pointState
} from '@/maps/explanation/shared/explanationMapModel'

const FIT_OPTIONS = { padding: 28, maxZoom: 18, duration: 0 }

const lineColor = (type) => (type === 'event' ? EXPLANATION_MAP_COLORS.path
  : type === 'gap' ? EXPLANATION_MAP_COLORS.gap : EXPLANATION_MAP_COLORS.contextPath)

/**
 * MapLibre drawing of the explanation map model.
 * @param callbacks.describePoint (point) => { title, lines } for the popup of a clicked point
 */
export const createVectorExplanationMapAdapter = (callbacks = {}) => {
  const token = `explanation-map-${Math.random().toString(36).slice(2, 10)}`
  const ids = {
    circleSource: `${token}-circle`,
    circleFill: `${token}-circle-fill`,
    circleLine: `${token}-circle-line`,
    linesSource: `${token}-lines`,
    solidLines: `${token}-lines-solid`,
    dashedLines: `${token}-lines-dashed`,
    pointsSource: `${token}-points`,
    points: `${token}-points-layer`,
    anchorSource: `${token}-anchor`,
    anchor: `${token}-anchor-layer`
  }

  let map = null
  let model = null
  let selected = null
  let popup = null
  let clickHandler = null
  let enterHandler = null
  let leaveHandler = null

  const pointFeatures = () => pointsInDrawOrder(model?.points || []).map((point) => {
    const paint = pointPaint(point, pointState(point, selected))
    return {
      type: 'Feature',
      geometry: { type: 'Point', coordinates: [point.longitude, point.latitude] },
      properties: { index: point.index, ...paint }
    }
  })

  const detachHandlers = () => {
    if (!map) return
    if (clickHandler) map.off('click', ids.points, clickHandler)
    if (enterHandler) map.off('mouseenter', ids.points, enterHandler)
    if (leaveHandler) map.off('mouseleave', ids.points, leaveHandler)
    clickHandler = enterHandler = leaveHandler = null
  }

  const clear = () => {
    popup?.remove()
    popup = null
    if (!isMapLibreMap(map)) return
    detachHandlers()
    removeLayers(map, [ids.points, ids.anchor, ids.dashedLines, ids.solidLines, ids.circleLine, ids.circleFill])
    removeSources(map, [ids.pointsSource, ids.anchorSource, ids.linesSource, ids.circleSource])
  }

  const render = (nextModel) => {
    if (!isMapLibreMap(map) || !nextModel) return false
    clear()
    model = nextModel
    selected = null

    if (model.circle) {
      const ring = circleRing(model.circle.latitude, model.circle.longitude, model.circle.radiusMeters)
      ensureGeoJsonSource(map, ids.circleSource, {
        type: 'FeatureCollection',
        features: [{ type: 'Feature', geometry: { type: 'Polygon', coordinates: [ring] }, properties: {} }]
      })
      ensureLayer(map, {
        id: ids.circleFill,
        type: 'fill',
        source: ids.circleSource,
        paint: { 'fill-color': EXPLANATION_MAP_COLORS.radius, 'fill-opacity': 0.06 }
      })
      ensureLayer(map, {
        id: ids.circleLine,
        type: 'line',
        source: ids.circleSource,
        paint: { 'line-color': EXPLANATION_MAP_COLORS.radius, 'line-width': 2, 'line-dasharray': [3, 3] }
      })
    }

    ensureGeoJsonSource(map, ids.linesSource, {
      type: 'FeatureCollection',
      features: model.lines.map((line) => ({
        type: 'Feature',
        geometry: { type: 'LineString', coordinates: line.coordinates.map(([lat, lng]) => [lng, lat]) },
        properties: { type: line.type, color: lineColor(line.type), width: line.type === 'event' ? 3 : 2 }
      }))
    })
    ensureLayer(map, {
      id: ids.solidLines,
      type: 'line',
      source: ids.linesSource,
      filter: ['!=', ['get', 'type'], 'gap'],
      layout: { 'line-join': 'round', 'line-cap': 'round' },
      paint: { 'line-color': ['get', 'color'], 'line-width': ['get', 'width'], 'line-opacity': 0.65 }
    })
    ensureLayer(map, {
      id: ids.dashedLines,
      type: 'line',
      source: ids.linesSource,
      filter: ['==', ['get', 'type'], 'gap'],
      paint: { 'line-color': ['get', 'color'], 'line-width': 2, 'line-dasharray': [3, 4] }
    })

    if (model.anchor) {
      ensureGeoJsonSource(map, ids.anchorSource, {
        type: 'FeatureCollection',
        features: [{
          type: 'Feature',
          geometry: { type: 'Point', coordinates: [model.anchor.longitude, model.anchor.latitude] },
          properties: {}
        }]
      })
      ensureLayer(map, {
        id: ids.anchor,
        type: 'circle',
        source: ids.anchorSource,
        paint: {
          'circle-radius': 6,
          'circle-color': EXPLANATION_MAP_COLORS.outline,
          'circle-stroke-color': EXPLANATION_MAP_COLORS.deciding,
          'circle-stroke-width': 2
        }
      })
    }

    ensureGeoJsonSource(map, ids.pointsSource, { type: 'FeatureCollection', features: pointFeatures() })
    ensureLayer(map, {
      id: ids.points,
      type: 'circle',
      source: ids.pointsSource,
      paint: {
        'circle-radius': ['get', 'radius'],
        'circle-color': ['get', 'fillColor'],
        'circle-opacity': ['get', 'fillOpacity'],
        'circle-stroke-color': ['get', 'strokeColor'],
        'circle-stroke-width': ['get', 'strokeWidth'],
        'circle-stroke-opacity': ['get', 'strokeOpacity']
      }
    })

    if (typeof callbacks.describePoint === 'function') {
      clickHandler = (event) => {
        const index = event.features?.[0]?.properties?.index
        const point = model?.points.find((candidate) => candidate.index === Number(index))
        if (!point) return
        popup?.remove()
        popup = new maplibregl.Popup({
          closeButton: true,
          closeOnClick: true,
          maxWidth: '280px',
          offset: 10,
          className: EXPLANATION_POPUP_CLASS_NAME
        })
          .setLngLat([point.longitude, point.latitude])
          .setDOMContent(buildPointPopupElement(callbacks.describePoint(point)))
          .addTo(map)
      }
      enterHandler = () => { map.getCanvas().style.cursor = 'pointer' }
      leaveHandler = () => { map.getCanvas().style.cursor = '' }
      map.on('click', ids.points, clickHandler)
      map.on('mouseenter', ids.points, enterHandler)
      map.on('mouseleave', ids.points, leaveHandler)
    }

    fitTo(model.bounds)
    return true
  }

  const fitTo = (bounds) => {
    if (!isMapLibreMap(map) || !bounds) return
    safeFitBounds(map, bounds, FIT_OPTIONS)
  }

  /** Emphasizes the points with these indexes, or shows all as normal for null. */
  const highlight = (indexes) => {
    selected = indexes ? new Set(indexes) : null
    if (!isMapLibreMap(map) || !model) return
    ensureGeoJsonSource(map, ids.pointsSource, { type: 'FeatureCollection', features: pointFeatures() })
  }

  const destroy = () => {
    clear()
    model = null
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
