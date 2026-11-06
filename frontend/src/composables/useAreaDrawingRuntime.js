import { ref, onBeforeUnmount } from 'vue'
import {t} from '@/locales'
import {polygonError} from '@/maps/favoritesManagement/shared/favoritesManagementGeometry'
import L from 'leaflet'
import {
  isRasterMapInstance,
  isVectorMapInstance,
  MAP_RENDER_MODES
} from '@/maps/contracts/mapContracts'
import { getVectorEngine } from '@/maps/runtime/vectorEngineRegistry'

// MapLibre layer helpers live in the lazily loaded vector engine; only the vector code paths below use them.
const vectorLayerUtils = () => getVectorEngine().maplibreLayerUtils

const MIN_RECTANGLE_SIZE = 0.00001

const createBoundsApi = (southWest, northEast) => ({
  getSouthWest: () => ({ lat: southWest.lat, lng: southWest.lng }),
  getNorthEast: () => ({ lat: northEast.lat, lng: northEast.lng })
})

const toRectangleCoordinates = (southWest, northEast) => ([
  [southWest.lng, southWest.lat],
  [northEast.lng, southWest.lat],
  [northEast.lng, northEast.lat],
  [southWest.lng, northEast.lat],
  [southWest.lng, southWest.lat]
])

const createPolygonFeatureCollection = (coordinates) => ({
  type: 'FeatureCollection',
  features: [
    {
      type: 'Feature',
      geometry: {
        type: 'Polygon',
        coordinates: [coordinates]
      },
      properties: {}
    }
  ]
})

export function useAreaDrawingRuntime(options = {}) {
  const {
    onRectangleCreated,
    onDrawingStart,
    onDrawingEnd
  } = options

  const drawingState = ref({
    isDrawing: false,
    isAdjusting: false,
    shape: 'rectangle',
    vertices: [],
    error: null,
    geometry: null,
    startPoint: null,
    previewRectangle: null,
    tempAreaLayer: null
  })

  const mode = ref(MAP_RENDER_MODES.RASTER)
  const vectorIds = {
    previewSourceId: `gp-rect-preview-${Math.random().toString(36).slice(2, 10)}`,
    previewLayerId: `gp-rect-preview-line-${Math.random().toString(36).slice(2, 10)}`,
    tempSourceId: `gp-rect-temp-${Math.random().toString(36).slice(2, 10)}`,
    tempLayerId: `gp-rect-temp-line-${Math.random().toString(36).slice(2, 10)}`
  }

  let map = null
  let interactionStates = []
  let vertexMarkers = []
  let edgeDrag = null
  let draggingVertex = false
  let suppressVertexClickUntil = 0
  let styleLoadHandler = null
  const interactionNames = ['dragging', 'touchZoom', 'doubleClickZoom', 'scrollWheelZoom', 'boxZoom', 'keyboard', 'tap', 'dragPan', 'touchZoomRotate', 'scrollZoom']
  const rememberInteractions = () => {
    interactionStates = interactionNames.filter(name => map[name]?.isEnabled || map[name]?.enabled)
      .map(name => [name, map[name].isEnabled?.() ?? map[name].enabled()])
  }
  const restoreInteractions = () => interactionStates.forEach(([name, enabled]) => {
    map[name]?.[enabled ? 'enable' : 'disable']?.()
  })
  const handleKey = (event) => {
    if (event.key === 'Escape') { stopDrawing(); cleanupTempLayer() }
  }
  let drawingHandlers = {
    onStart: null,
    onMove: null,
    onEnd: null
  }

  const preventContextMenu = (event) => {
    if (drawingState.value.isDrawing) {
      event.preventDefault()
      event.stopPropagation()
    }
  }

  const disableRasterInteractions = () => {
    map.dragging?.disable?.()
    map.touchZoom?.disable?.()
    map.doubleClickZoom?.disable?.()
    map.boxZoom?.disable?.()
    map.keyboard?.disable?.()
    map.tap?.disable?.()
  }

  const enableRasterInteractions = () => {
    map.dragging?.enable?.()
    map.touchZoom?.enable?.()
    map.doubleClickZoom?.enable?.()
    map.scrollWheelZoom?.enable?.()
    map.boxZoom?.enable?.()
    map.keyboard?.enable?.()
    map.tap?.enable?.()
  }

  const disableVectorInteractions = () => {
    map.dragPan?.disable?.()
    map.touchZoomRotate?.disable?.()
    map.doubleClickZoom?.disable?.()
    map.boxZoom?.disable?.()
    map.keyboard?.disable?.()
  }

  const enableVectorInteractions = () => {
    map.dragPan?.enable?.()
    map.touchZoomRotate?.enable?.()
    map.doubleClickZoom?.enable?.()
    map.scrollZoom?.enable?.()
    map.boxZoom?.enable?.()
    map.keyboard?.enable?.()
  }

  const getContainerPointFromEvent = (event) => {
    if (!map?.getContainer) {
      return null
    }

    let clientX
    let clientY

    if (event.type.startsWith('touch')) {
      const touches = event.type === 'touchend' ? event.changedTouches : event.touches
      if (!touches || touches.length === 0) {
        return null
      }
      clientX = touches[0].clientX
      clientY = touches[0].clientY
    } else {
      clientX = event.clientX
      clientY = event.clientY
    }

    const containerRect = map.getContainer().getBoundingClientRect()
    return {
      x: clientX - containerRect.left,
      y: clientY - containerRect.top
    }
  }

  const getLatLngFromEvent = (event) => {
    const containerPoint = getContainerPointFromEvent(event)
    if (!containerPoint) {
      return null
    }

    if (mode.value === MAP_RENDER_MODES.RASTER) {
      const latLngValue = map.containerPointToLatLng(L.point(containerPoint.x, containerPoint.y))
      return {
        lat: latLngValue.lat,
        lng: latLngValue.lng
      }
    }

    const lngLatValue = map.unproject([containerPoint.x, containerPoint.y])
    return {
      lat: lngLatValue.lat,
      lng: lngLatValue.lng
    }
  }

  const ensureVectorSource = (sourceId, coordinates) => {
    const source = vectorLayerUtils().getMapLibreSource(map, sourceId)
    const data = createPolygonFeatureCollection(coordinates)

    if (source && typeof source.setData === 'function') {
      source.setData(data)
      return
    }

    vectorLayerUtils().ensureGeoJsonSource(map, sourceId, data)
  }

  const ensureVectorLineLayer = (layerId, sourceId, options = {}) => {
    if (vectorLayerUtils().hasMapLibreLayer(map, layerId)) {
      return
    }

    vectorLayerUtils().ensureLayer(map, {
      id: layerId,
      type: 'line',
      source: sourceId,
      layout: {
        'line-join': 'round',
        'line-cap': 'round'
      },
      paint: {
        'line-color': options.color || '#e91e63',
        'line-width': options.width || 2,
        'line-opacity': options.opacity ?? 1,
        ...(options.dashArray ? { 'line-dasharray': options.dashArray } : {})
      }
    })
  }

  const removeVectorLayer = (layerId) => {
    vectorLayerUtils().removeLayers(map, [layerId])
  }

  const removeVectorSource = (sourceId) => {
    vectorLayerUtils().removeSources(map, [sourceId])
  }

  const removeVectorPreview = () => {
    removeVectorLayer(vectorIds.previewLayerId)
    removeVectorSource(vectorIds.previewSourceId)
    drawingState.value.previewRectangle = null
  }

  const removeVectorTempArea = () => {
    removeVectorLayer(vectorIds.tempLayerId)
    removeVectorSource(vectorIds.tempSourceId)
    drawingState.value.tempAreaLayer = null
  }

  const startDrawing = (shape = 'rectangle') => {
    if (drawingState.value.isDrawing) stopDrawing()
    if (!map) {
      return
    }

    cleanupTempLayer()
    drawingState.value.isAdjusting = false
    drawingState.value.shape = shape === 'polygon' ? 'polygon' : 'rectangle'
    drawingState.value.vertices = []
    drawingState.value.error = null
    drawingState.value.geometry = null
    rememberInteractions()
    document.addEventListener('keydown', handleKey)
    drawingState.value.isDrawing = true
    drawingState.value.startPoint = null

    const container = map.getContainer()
    container.style.cursor = 'crosshair'
    container.addEventListener('pointerdown', handleEdgeDown, true)
    container.addEventListener('pointermove', handleEdgeHover)
    container.addEventListener('mousedown', drawingHandlers.onStart, { passive: false })
    container.addEventListener('touchstart', drawingHandlers.onStart, { passive: false })
    container.addEventListener('mouseleave', clearPointerPreview)
    container.addEventListener('mousemove', drawingHandlers.onMove, { passive: false })
    container.addEventListener('touchmove', drawingHandlers.onMove, { passive: false })
    container.addEventListener('mouseup', drawingHandlers.onEnd, { passive: false })
    container.addEventListener('touchend', drawingHandlers.onEnd, { passive: false })
    container.addEventListener('contextmenu', preventContextMenu)

    if (mode.value === MAP_RENDER_MODES.RASTER) {
      disableRasterInteractions()
    } else {
      disableVectorInteractions()
    }

    onDrawingStart?.()
  }

  const stopDrawing = () => {
    if (!map) {
      return
    }

    document.removeEventListener('keydown', handleKey)
    vertexMarkers.forEach(marker => marker.remove())
    vertexMarkers = []
    endEdgeDrag()
    draggingVertex = false
    drawingState.value.isDrawing = false
    drawingState.value.startPoint = null

    const container = map.getContainer()
    container.removeEventListener('pointerdown', handleEdgeDown, true)
    container.removeEventListener('pointermove', handleEdgeHover)
    container.removeEventListener('mousedown', drawingHandlers.onStart)
    container.removeEventListener('touchstart', drawingHandlers.onStart)
    container.removeEventListener('mouseleave', clearPointerPreview)
    container.removeEventListener('mousemove', drawingHandlers.onMove)
    container.removeEventListener('touchmove', drawingHandlers.onMove)
    container.removeEventListener('mouseup', drawingHandlers.onEnd)
    container.removeEventListener('touchend', drawingHandlers.onEnd)
    container.removeEventListener('contextmenu', preventContextMenu)
    container.style.cursor = ''

    if (mode.value === MAP_RENDER_MODES.RASTER) {
      if (drawingState.value.previewRectangle) {
        map.removeLayer(drawingState.value.previewRectangle)
        drawingState.value.previewRectangle = null
      }
      enableRasterInteractions()
    } else {
      removeVectorPreview()
      enableVectorInteractions()
    }

    restoreInteractions()
    onDrawingEnd?.()
  }

  const project = (coordinate) => mode.value === MAP_RENDER_MODES.RASTER
    ? map.latLngToContainerPoint([coordinate[1], coordinate[0]]) : map.project(coordinate)

  const syncVertexMarkers = () => {
    const vertices = drawingState.value.vertices
    if (vertexMarkers.length !== vertices.length) {
      vertexMarkers.forEach(marker => marker.remove())
      vertexMarkers = vertices.map((coordinate, index) => {
        const element = document.createElement('button')
        element.type = 'button'
        element.className = 'gp-area-vertex'
        element.style.cssText = `width:18px;height:18px;border:3px solid #e91e63;border-radius:50%;background:${index === 0 ? '#e91e63' : '#fff'};padding:0;cursor:move;touch-action:none`
        element.setAttribute('aria-label', t('favoritesGeocodingDialogs.areaDrawing.corner', {number: index + 1}))
        let marker
        if (mode.value === MAP_RENDER_MODES.RASTER) {
          marker = L.marker([coordinate[1], coordinate[0]], {
            draggable: true, keyboard: false, icon: L.divIcon({html: element, className: 'gp-area-vertex-wrapper', iconSize: [18,18], iconAnchor: [9,9]})
          }).addTo(map)
        } else {
          marker = new (getVectorEngine().maplibregl.Marker)({element, draggable: true})
            .setLngLat(coordinate).addTo(map)
        }
        // Marker dragging owns these events; they must not add another vertex on the map.
        for (const eventName of ['dblclick', 'contextmenu']) {
          element.addEventListener(eventName, event => event.stopPropagation())
        }
        let pressedOnHandle = false
        element.addEventListener('pointerdown', () => { pressedOnHandle = true })
        element.addEventListener('click', event => {
          event.stopPropagation()
          // A new corner can appear under the cursor before mouseup; adding it must not finish the shape.
          const selectedExistingCorner = pressedOnHandle || event.detail === 0
          pressedOnHandle = false
          if (!drawingState.value.isAdjusting && selectedExistingCorner && (index === 0 || index === vertices.length - 1) && !draggingVertex && Date.now() >= suppressVertexClickUntil && vertices.length >= 3) finishPolygon()
        })
        element.addEventListener('keydown', event => {
          if (drawingState.value.isAdjusting && (event.key === '+' || event.key === 'Insert')) {
            event.preventDefault(); event.stopPropagation()
            if (vertices.length >= 500) { drawingState.value.error = 'vertices'; return }
            const a = project(vertices[index]), b = project(vertices[(index + 1) % vertices.length])
            vertices.splice(index + 1, 0, unproject({x: (a.x + b.x) / 2, y: (a.y + b.y) / 2}))
            drawingState.value.error = null
            renderPolygonPreview()
            const handle = vertexMarkers[index + 1]?.getElement?.()
            if (handle?.matches('.gp-area-vertex')) handle.focus()
            else handle?.querySelector('.gp-area-vertex')?.focus()
            return
          }
          if (!drawingState.value.isAdjusting && event.key === 'Enter' && (index === 0 || index === vertices.length - 1)) { event.preventDefault(); finishPolygon(); return }
          const offset = {ArrowLeft: [-1,0], ArrowRight: [1,0], ArrowUp: [0,-1], ArrowDown: [0,1]}[event.key]
          if (!offset) return
          event.preventDefault(); event.stopPropagation()
          const point = project(vertices[index]), step = event.shiftKey ? 10 : 1
          const moved = mode.value === MAP_RENDER_MODES.RASTER
            ? map.containerPointToLatLng(L.point(point.x + offset[0] * step, point.y + offset[1] * step))
            : map.unproject([point.x + offset[0] * step, point.y + offset[1] * step])
          vertices[index] = [moved.lng, moved.lat]
          drawingState.value.error = null
          renderPolygonPreview()
        })
        marker.on('dragstart', () => { draggingVertex = true })
        marker.on('drag', () => {
          const moved = mode.value === MAP_RENDER_MODES.RASTER ? marker.getLatLng() : marker.getLngLat()
          vertices[index] = [moved.lng, moved.lat]
          drawingState.value.error = null
          renderPolygonPreview()
        })
        marker.on('dragend', () => {
          // Suppress the click generated when a dragged first corner is released.
          suppressVertexClickUntil = Date.now() + 250
          draggingVertex = false
        })
        return marker
      })
    }
    if (!draggingVertex) vertexMarkers.forEach((marker, index) => {
      if (mode.value === MAP_RENDER_MODES.RASTER) marker.setLatLng([vertices[index][1], vertices[index][0]])
      else marker.setLngLat(vertices[index])
    })
  }

  const unproject = (point) => {
    const location = mode.value === MAP_RENDER_MODES.RASTER
      ? map.containerPointToLatLng(L.point(point.x, point.y)) : map.unproject([point.x, point.y])
    return [location.lng, location.lat]
  }

  const nearestEdge = (event) => {
    if (!drawingState.value.isDrawing || !drawingState.value.isAdjusting || draggingVertex
      || event.target?.closest?.('button, .gp-area-vertex-wrapper, .leaflet-control, .maplibregl-ctrl')) return null
    const position = getContainerPointFromEvent(event)
    if (!position) return null
    const vertices = drawingState.value.vertices
    const pixels = vertices.map(project)
    // Corners keep their own drag behavior; only the space between them reshapes an edge.
    if (pixels.some(p => Math.hypot(p.x - position.x, p.y - position.y) <= 14)) return null
    let nearest = null
    let distance = event.pointerType === 'touch' ? 16 : 8
    pixels.forEach((a, index) => {
      const b = pixels[(index + 1) % pixels.length]
      const dx = b.x - a.x, dy = b.y - a.y
      const lengthSquared = dx * dx + dy * dy
      if (!lengthSquared) return
      const t = Math.max(0, Math.min(1, ((position.x - a.x) * dx + (position.y - a.y) * dy) / lengthSquared))
      const point = {x: a.x + t * dx, y: a.y + t * dy}
      const gap = Math.hypot(point.x - position.x, point.y - position.y)
      if (gap <= distance) { distance = gap; nearest = {index, point} }
    })
    return nearest
  }

  const handleEdgeHover = (event) => {
    if (edgeDrag || !drawingState.value.isAdjusting || !drawingState.value.isDrawing) return
    map.getContainer().style.cursor = nearestEdge(event)
      ? (drawingState.value.vertices.length >= 500 ? 'not-allowed' : 'move') : ''
  }

  const endEdgeDrag = (cancelled = false) => {
    document.removeEventListener('pointermove', handleEdgeMove, true)
    document.removeEventListener('pointerup', handleEdgeUp, true)
    document.removeEventListener('pointercancel', handleEdgeCancel, true)
    if (!edgeDrag) return
    const drag = edgeDrag
    edgeDrag = null
    if (cancelled && drag.inserted) {
      drawingState.value.vertices.splice(drag.index + 1, 1)
      renderPolygonPreview()
    }
    drag.pan?.[drag.panEnabled ? 'enable' : 'disable']?.()
    map.getContainer().style.cursor = ''
  }

  const handleEdgeMove = (event) => {
    if (!edgeDrag || event.pointerId !== edgeDrag.pointerId) return
    event.preventDefault()
    event.stopPropagation()
    const dx = event.clientX - edgeDrag.clientX, dy = event.clientY - edgeDrag.clientY
    if (!edgeDrag.inserted && Math.hypot(dx, dy) < 4) return
    const coordinate = unproject({x: edgeDrag.point.x + dx, y: edgeDrag.point.y + dy})
    const vertices = drawingState.value.vertices
    if (!edgeDrag.inserted) {
      vertices.splice(edgeDrag.index + 1, 0, coordinate)
      edgeDrag.inserted = true
    } else vertices[edgeDrag.index + 1] = coordinate
    drawingState.value.error = null
    renderPolygonPreview()
  }

  const handleEdgeUp = (event) => {
    if (!edgeDrag || event.pointerId !== edgeDrag.pointerId) return
    handleEdgeMove(event)
    endEdgeDrag()
  }

  const handleEdgeCancel = (event) => {
    if (edgeDrag && event.pointerId === edgeDrag.pointerId) endEdgeDrag(true)
  }

  const handleEdgeDown = (event) => {
    if (edgeDrag || event.button !== 0 || event.isPrimary === false) return
    const edge = nearestEdge(event)
    if (!edge) return
    event.preventDefault()
    event.stopPropagation()
    if (drawingState.value.vertices.length >= 500) { drawingState.value.error = 'vertices'; return }
    const pan = mode.value === MAP_RENDER_MODES.RASTER ? map.dragging : map.dragPan
    edgeDrag = {...edge, pointerId: event.pointerId, clientX: event.clientX, clientY: event.clientY,
      inserted: false, pan, panEnabled: pan?.isEnabled?.() ?? pan?.enabled?.() ?? false}
    pan?.disable?.()
    map.getContainer().style.cursor = 'move'
    document.addEventListener('pointermove', handleEdgeMove, {capture: true, passive: false})
    document.addEventListener('pointerup', handleEdgeUp, true)
    document.addEventListener('pointercancel', handleEdgeCancel, true)
  }

  const startEditing = (geometry) => {
    startDrawing('polygon')
    drawingState.value.isAdjusting = true
    drawingState.value.vertices = geometry.coordinates[0].slice(0, -1).map(c => [...c])
    restoreInteractions()
    map.getContainer().style.cursor = ''
    renderPolygonPreview()
  }

  const renderPolygonPreview = (pointer = null) => {
    const vertices = drawingState.value.vertices
    const line = [...vertices]
    if (drawingState.value.isAdjusting && vertices.length >= 3) line.push(vertices[0])
    // Cursor movement is only a guide; it never changes the committed polygon boundary.
    const guide = !drawingState.value.isAdjusting && pointer && vertices.length ? [vertices.at(-1), [pointer.lng, pointer.lat]] : null
    if (line.length === 0) {
      vertexMarkers.forEach(marker => marker.remove())
      vertexMarkers = []
      if (mode.value === MAP_RENDER_MODES.RASTER) {
        if (drawingState.value.previewRectangle) map.removeLayer(drawingState.value.previewRectangle)
        drawingState.value.previewRectangle = null
      } else removeVectorPreview()
      return
    }
    syncVertexMarkers()
    if (mode.value === MAP_RENDER_MODES.RASTER) {
      if (drawingState.value.previewRectangle) map.removeLayer(drawingState.value.previewRectangle)
      drawingState.value.previewRectangle = L.polyline([line, ...(guide ? [guide] : [])].map(segment => segment.map(([lon, lat]) => [lat, lon])), {color: '#e91e63', dashArray: '5,5', interactive: false}).addTo(map)
    } else {
      const data = {type: 'FeatureCollection', features: [line, ...(guide ? [guide] : [])].map(segment => ({type: 'Feature', properties: {}, geometry: {type: 'LineString', coordinates: segment.length === 1 ? [segment[0], segment[0]] : segment}}))}
      const source = vectorLayerUtils().getMapLibreSource(map, vectorIds.previewSourceId)
      if (source) source.setData(data)
      else vectorLayerUtils().ensureGeoJsonSource(map, vectorIds.previewSourceId, data)
      ensureVectorLineLayer(vectorIds.previewLayerId, vectorIds.previewSourceId)
    }
  }

  const clearPointerPreview = () => {
    if (drawingState.value.isAdjusting && !edgeDrag) map.getContainer().style.cursor = ''
    if (drawingState.value.isDrawing && drawingState.value.shape === 'polygon') renderPolygonPreview()
  }

  const undoPoint = () => {
    if (drawingState.value.isAdjusting) return
    drawingState.value.vertices.pop()
    drawingState.value.error = null
    renderPolygonPreview()
  }

  const finishPolygon = () => {
    const vertices = drawingState.value.vertices
    const ring = vertices.length ? [...vertices.map(c => [...c]), [...vertices[0]]] : []
    drawingState.value.error = polygonError(ring)
    if (drawingState.value.error) return
    const southWest = {lat: Math.min(...vertices.map(c => c[1])), lng: Math.min(...vertices.map(c => c[0]))}
    const northEast = {lat: Math.max(...vertices.map(c => c[1])), lng: Math.max(...vertices.map(c => c[0]))}
    const bounds = createBoundsApi(southWest, northEast)
    drawingState.value.geometry = {type: 'Polygon', coordinates: [ring]}
    if (mode.value === MAP_RENDER_MODES.RASTER) {
      drawingState.value.tempAreaLayer = L.polygon(ring.map(([lon, lat]) => [lat, lon]), {color: '#e91e63', fillOpacity: 0.15}).addTo(map)
    } else {
      ensureVectorSource(vectorIds.tempSourceId, ring)
      ensureVectorLineLayer(vectorIds.tempLayerId, vectorIds.tempSourceId)
      drawingState.value.tempAreaLayer = {getBounds: () => bounds}
    }
    onRectangleCreated?.({bounds, layer: drawingState.value.tempAreaLayer, geometry: drawingState.value.geometry})
    stopDrawing()
  }

  const handleDrawStart = (event) => {
    if (!drawingState.value.isDrawing || drawingState.value.isAdjusting) {
      return
    }

    if (event.target?.closest?.('.gp-area-vertex, .gp-area-vertex-wrapper')) return
    event.preventDefault()
    event.stopPropagation()

    const latLng = getLatLngFromEvent(event)
    if (!latLng) {
      return
    }

    if (drawingState.value.shape === 'polygon') {
      const vertices = drawingState.value.vertices
      if (vertices.length >= 3) {
        const first = vertices[0]
        const a = mode.value === MAP_RENDER_MODES.RASTER
          ? map.latLngToContainerPoint([first[1], first[0]]) : map.project(first)
        const b = getContainerPointFromEvent(event)
        if (Math.hypot(a.x - b.x, a.y - b.y) <= 12) { finishPolygon(); return }
      }
      if (vertices.length >= 500) { drawingState.value.error = 'vertices'; return }
      vertices.push([latLng.lng, latLng.lat])
      drawingState.value.error = null
      renderPolygonPreview()
      return
    }

    drawingState.value.startPoint = latLng

    if (mode.value === MAP_RENDER_MODES.RASTER) {
      const bounds = L.latLngBounds(
        L.latLng(latLng.lat, latLng.lng),
        L.latLng(latLng.lat, latLng.lng)
      )
      drawingState.value.previewRectangle = L.rectangle(bounds, {
        color: '#e91e63',
        weight: 2,
        fill: false,
        dashArray: '5, 5'
      }).addTo(map)
      return
    }

    const coordinates = toRectangleCoordinates(latLng, latLng)
    ensureVectorSource(vectorIds.previewSourceId, coordinates)
    ensureVectorLineLayer(vectorIds.previewLayerId, vectorIds.previewSourceId, {
      color: '#e91e63',
      width: 2,
      dashArray: [2, 2]
    })
  }

  const handleDrawMove = (event) => {
    if (drawingState.value.isAdjusting) return
    if (drawingState.value.isDrawing && drawingState.value.shape === 'polygon') {
      if (event.type === 'mousemove' && !draggingVertex) {
        const overControl = event.target?.closest?.('button, .area-drawing-controls, .gp-area-vertex-wrapper')
        renderPolygonPreview(overControl ? null : getLatLngFromEvent(event))
      }
      return
    }
    if (!drawingState.value.isDrawing || !drawingState.value.startPoint) {
      return
    }

    event.preventDefault()
    event.stopPropagation()

    const latLng = getLatLngFromEvent(event)
    if (!latLng) {
      return
    }

    if (mode.value === MAP_RENDER_MODES.RASTER) {
      if (!drawingState.value.previewRectangle) {
        return
      }

      const bounds = L.latLngBounds(
        L.latLng(drawingState.value.startPoint.lat, drawingState.value.startPoint.lng),
        L.latLng(latLng.lat, latLng.lng)
      )
      drawingState.value.previewRectangle.setBounds(bounds)
      return
    }

    const southWest = {
      lat: Math.min(drawingState.value.startPoint.lat, latLng.lat),
      lng: Math.min(drawingState.value.startPoint.lng, latLng.lng)
    }
    const northEast = {
      lat: Math.max(drawingState.value.startPoint.lat, latLng.lat),
      lng: Math.max(drawingState.value.startPoint.lng, latLng.lng)
    }
    const coordinates = toRectangleCoordinates(southWest, northEast)
    ensureVectorSource(vectorIds.previewSourceId, coordinates)
    ensureVectorLineLayer(vectorIds.previewLayerId, vectorIds.previewSourceId, {
      color: '#e91e63',
      width: 2,
      dashArray: [2, 2]
    })
  }

  const handleDrawEnd = (event) => {
    if (!drawingState.value.isDrawing || !drawingState.value.startPoint) {
      return
    }

    event.preventDefault()
    event.stopPropagation()

    const latLng = getLatLngFromEvent(event)
    if (!latLng) {
      stopDrawing()
      return
    }

    const southWest = {
      lat: Math.min(drawingState.value.startPoint.lat, latLng.lat),
      lng: Math.min(drawingState.value.startPoint.lng, latLng.lng)
    }
    const northEast = {
      lat: Math.max(drawingState.value.startPoint.lat, latLng.lat),
      lng: Math.max(drawingState.value.startPoint.lng, latLng.lng)
    }
    const sizeDiff = Math.abs(northEast.lat - southWest.lat) + Math.abs(northEast.lng - southWest.lng)
    const bounds = createBoundsApi(southWest, northEast)

    if (sizeDiff > MIN_RECTANGLE_SIZE) {
      if (mode.value === MAP_RENDER_MODES.RASTER) {
        drawingState.value.tempAreaLayer = L.rectangle(
          [
            [southWest.lat, southWest.lng],
            [northEast.lat, northEast.lng]
          ],
          {
            color: '#e91e63',
            weight: 2,
            fill: false
          }
        )
      } else {
        removeVectorTempArea()

        const coordinates = toRectangleCoordinates(southWest, northEast)
        ensureVectorSource(vectorIds.tempSourceId, coordinates)
        ensureVectorLineLayer(vectorIds.tempLayerId, vectorIds.tempSourceId, {
          color: '#e91e63',
          width: 2
        })

        drawingState.value.tempAreaLayer = {
          getBounds: () => bounds
        }
      }

      drawingState.value.geometry = {type: 'Polygon', coordinates: [toRectangleCoordinates(southWest, northEast)]}
      onRectangleCreated?.({
        geometry: drawingState.value.geometry,
        bounds,
        layer: drawingState.value.tempAreaLayer
      })
    }

    stopDrawing()
  }

  const cleanupTempLayer = () => {
    if (!map) {
      drawingState.value.tempAreaLayer = null
      return
    }

    if (mode.value === MAP_RENDER_MODES.RASTER) {
      if (drawingState.value.tempAreaLayer) {
        map.removeLayer(drawingState.value.tempAreaLayer)
      }
      drawingState.value.tempAreaLayer = null
      return
    }

    removeVectorTempArea()
  }

  const initialize = (mapInstance) => {
    if (map) {
      if (styleLoadHandler) map.off?.('style.load', styleLoadHandler)
      if (drawingState.value.isDrawing) stopDrawing()
      cleanupTempLayer()
    }
    map = mapInstance
    mode.value = isVectorMapInstance(mapInstance)
      ? MAP_RENDER_MODES.VECTOR
      : MAP_RENDER_MODES.RASTER

    if (!isRasterMapInstance(mapInstance) && !isVectorMapInstance(mapInstance)) {
      mode.value = MAP_RENDER_MODES.RASTER
    }

    styleLoadHandler = () => {
      if (drawingState.value.isDrawing && drawingState.value.shape === 'polygon') renderPolygonPreview()
      if (drawingState.value.geometry && drawingState.value.tempAreaLayer) {
        ensureVectorSource(vectorIds.tempSourceId, drawingState.value.geometry.coordinates[0])
        ensureVectorLineLayer(vectorIds.tempLayerId, vectorIds.tempSourceId)
      }
    }
    if (mode.value === MAP_RENDER_MODES.VECTOR) map.on('style.load', styleLoadHandler)
    drawingHandlers.onStart = (event) => handleDrawStart(event)
    drawingHandlers.onMove = (event) => handleDrawMove(event)
    drawingHandlers.onEnd = (event) => handleDrawEnd(event)
  }

  const isDrawing = () => drawingState.value.isDrawing

  onBeforeUnmount(() => {
    if (styleLoadHandler) map?.off?.('style.load', styleLoadHandler)
    if (drawingState.value.isDrawing) stopDrawing()
    cleanupTempLayer()
  })

  return {
    clearPointerPreview,
    startEditing,
    undoPoint,
    finishPolygon,
    drawingState,
    initialize,
    startDrawing,
    stopDrawing,
    cleanupTempLayer,
    isDrawing
  }
}
