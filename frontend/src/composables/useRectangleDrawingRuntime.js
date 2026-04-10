import { ref } from 'vue'
import L from 'leaflet'
import {
  isRasterMapInstance,
  isVectorMapInstance,
  MAP_RENDER_MODES
} from '@/maps/contracts/mapContracts'

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

export function useRectangleDrawingRuntime(options = {}) {
  const {
    onRectangleCreated,
    onDrawingStart,
    onDrawingEnd
  } = options

  const drawingState = ref({
    isDrawing: false,
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
    map.scrollWheelZoom?.disable?.()
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
    map.scrollZoom?.disable?.()
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
    const source = map.getSource(sourceId)
    const data = createPolygonFeatureCollection(coordinates)

    if (source && typeof source.setData === 'function') {
      source.setData(data)
      return
    }

    map.addSource(sourceId, {
      type: 'geojson',
      data
    })
  }

  const ensureVectorLineLayer = (layerId, sourceId, options = {}) => {
    if (map.getLayer(layerId)) {
      return
    }

    map.addLayer({
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
    if (map?.getLayer?.(layerId)) {
      map.removeLayer(layerId)
    }
  }

  const removeVectorSource = (sourceId) => {
    if (map?.getSource?.(sourceId)) {
      map.removeSource(sourceId)
    }
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

  const startDrawing = () => {
    if (!map || drawingState.value.isDrawing) {
      return
    }

    drawingState.value.isDrawing = true
    drawingState.value.startPoint = null

    const container = map.getContainer()
    container.style.cursor = 'crosshair'
    container.addEventListener('mousedown', drawingHandlers.onStart, { passive: false })
    container.addEventListener('touchstart', drawingHandlers.onStart, { passive: false })
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

    drawingState.value.isDrawing = false
    drawingState.value.startPoint = null

    const container = map.getContainer()
    container.removeEventListener('mousedown', drawingHandlers.onStart)
    container.removeEventListener('touchstart', drawingHandlers.onStart)
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

    onDrawingEnd?.()
  }

  const handleDrawStart = (event) => {
    if (!drawingState.value.isDrawing) {
      return
    }

    event.preventDefault()
    event.stopPropagation()

    const latLng = getLatLngFromEvent(event)
    if (!latLng) {
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

      onRectangleCreated?.({
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
    if (drawingState.value.isDrawing) {
      stopDrawing()
    }

    map = mapInstance
    mode.value = isVectorMapInstance(mapInstance)
      ? MAP_RENDER_MODES.VECTOR
      : MAP_RENDER_MODES.RASTER

    if (!isRasterMapInstance(mapInstance) && !isVectorMapInstance(mapInstance)) {
      mode.value = MAP_RENDER_MODES.RASTER
    }

    drawingHandlers.onStart = (event) => handleDrawStart(event)
    drawingHandlers.onMove = (event) => handleDrawMove(event)
    drawingHandlers.onEnd = (event) => handleDrawEnd(event)
  }

  const isDrawing = () => drawingState.value.isDrawing

  return {
    drawingState,
    initialize,
    startDrawing,
    stopDrawing,
    cleanupTempLayer,
    isDrawing
  }
}
