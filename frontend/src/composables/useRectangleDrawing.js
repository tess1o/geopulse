/**
 * Composable for managing rectangle drawing on Leaflet maps
 * Provides touch-optimized rectangle drawing functionality
 */
import { ref } from 'vue'
import L from 'leaflet'

export function useRectangleDrawing(options = {}) {
  const {
    onRectangleCreated,
    onDrawingStart,
    onDrawingEnd
  } = options

  // Drawing state
  const drawingState = ref({
    isDrawing: false,
    startPoint: null,
    previewRectangle: null,
    tempAreaLayer: null
  })

  let map = null
  let drawingHandlers = {
    onStart: null,
    onMove: null,
    onEnd: null
  }

  /**
   * Initialize the rectangle drawing on a map
   */
  const initialize = (mapInstance) => {
    map = mapInstance
    
    // Create drawing event handlers with proper context
    drawingHandlers.onStart = (e) => handleDrawStart(e)
    drawingHandlers.onMove = (e) => handleDrawMove(e)
    drawingHandlers.onEnd = (e) => handleDrawEnd(e)
  }

  /**
   * Start rectangle drawing mode
   */
  const startDrawing = () => {
    if (!map) return

    drawingState.value.isDrawing = true
    
    // Change cursor to crosshair (desktop only)
    map.getContainer().style.cursor = 'crosshair'
    
    // Disable all map interactions during drawing
    map.dragging.disable()
    map.touchZoom.disable()
    map.doubleClickZoom.disable()
    map.scrollWheelZoom.disable()
    map.boxZoom.disable()
    map.keyboard.disable()
    if (map.tap) map.tap.disable()
    
    // Add drawing event listeners to map container for better mobile support
    const container = map.getContainer()
    
    container.addEventListener('mousedown', drawingHandlers.onStart, { passive: false })
    container.addEventListener('touchstart', drawingHandlers.onStart, { passive: false })
    container.addEventListener('mousemove', drawingHandlers.onMove, { passive: false })
    container.addEventListener('touchmove', drawingHandlers.onMove, { passive: false })
    container.addEventListener('mouseup', drawingHandlers.onEnd, { passive: false })
    container.addEventListener('touchend', drawingHandlers.onEnd, { passive: false })
    
    // Prevent context menu during drawing
    container.addEventListener('contextmenu', preventContextMenu)
    
    onDrawingStart?.()
  }

  /**
   * Stop rectangle drawing mode
   */
  const stopDrawing = () => {
    if (!map) return
    
    drawingState.value.isDrawing = false
    drawingState.value.startPoint = null
    
    // Remove preview rectangle
    if (drawingState.value.previewRectangle) {
      map.removeLayer(drawingState.value.previewRectangle)
      drawingState.value.previewRectangle = null
    }
    
    // Remove event listeners
    const container = map.getContainer()
    container.removeEventListener('mousedown', drawingHandlers.onStart)
    container.removeEventListener('touchstart', drawingHandlers.onStart)
    container.removeEventListener('mousemove', drawingHandlers.onMove)
    container.removeEventListener('touchmove', drawingHandlers.onMove)
    container.removeEventListener('mouseup', drawingHandlers.onEnd)
    container.removeEventListener('touchend', drawingHandlers.onEnd)
    container.removeEventListener('contextmenu', preventContextMenu)
    
    // Reset cursor
    container.style.cursor = ''
    
    // Re-enable all map interactions
    map.dragging.enable()
    map.touchZoom.enable()
    map.doubleClickZoom.enable()
    map.scrollWheelZoom.enable()
    map.boxZoom.enable()
    map.keyboard.enable()
    if (map.tap) map.tap.enable()
    
    onDrawingEnd?.()
  }

  /**
   * Handle drawing start (mouse down / touch start)
   */
  const handleDrawStart = (e) => {
    if (!drawingState.value.isDrawing) return
    
    e.preventDefault()
    e.stopPropagation()
    
    const latlng = getLatLngFromEvent(e)
    drawingState.value.startPoint = latlng
    
    // Create preview rectangle
    const bounds = L.latLngBounds(latlng, latlng)
    drawingState.value.previewRectangle = L.rectangle(bounds, {
      color: '#e91e63',
      weight: 2,
      fill: false,
      dashArray: '5, 5'
    }).addTo(map)
  }

  /**
   * Handle drawing move (mouse move / touch move)
   */
  const handleDrawMove = (e) => {
    if (!drawingState.value.isDrawing || !drawingState.value.startPoint || !drawingState.value.previewRectangle) return
    
    e.preventDefault()
    e.stopPropagation()
    
    const latlng = getLatLngFromEvent(e)
    
    // Update preview rectangle bounds
    const bounds = L.latLngBounds(drawingState.value.startPoint, latlng)
    drawingState.value.previewRectangle.setBounds(bounds)
  }

  /**
   * Handle drawing end (mouse up / touch end)
   */
  const handleDrawEnd = (e) => {
    if (!drawingState.value.isDrawing || !drawingState.value.startPoint) return
    
    e.preventDefault()
    e.stopPropagation()
    
    const latlng = getLatLngFromEvent(e)
    const bounds = L.latLngBounds(drawingState.value.startPoint, latlng)
    
    // Only create rectangle if it has some size
    const sizeDiff = Math.abs(bounds.getNorthEast().lat - bounds.getSouthWest().lat) + 
                     Math.abs(bounds.getNorthEast().lng - bounds.getSouthWest().lng)
    
    if (sizeDiff > 0.001) { // Minimum size threshold
      drawingState.value.tempAreaLayer = L.rectangle(bounds, {
        color: '#e91e63',
        weight: 2,
        fill: false
      })
      
      onRectangleCreated?.({
        bounds,
        layer: drawingState.value.tempAreaLayer
      })
    }
    
    stopDrawing()
  }

  /**
   * Get lat/lng coordinates from mouse or touch event
   */
  const getLatLngFromEvent = (e) => {
    let clientX, clientY
    
    if (e.type.startsWith('touch')) {
      const touches = e.type === 'touchend' ? e.changedTouches : e.touches
      if (touches.length === 0) return null
      clientX = touches[0].clientX
      clientY = touches[0].clientY
    } else {
      clientX = e.clientX
      clientY = e.clientY
    }
    
    // Convert screen coordinates to lat/lng
    const containerRect = map.getContainer().getBoundingClientRect()
    const relativePoint = L.point(
      clientX - containerRect.left,
      clientY - containerRect.top
    )
    
    return map.containerPointToLatLng(relativePoint)
  }

  /**
   * Prevent context menu during drawing
   */
  const preventContextMenu = (e) => {
    if (drawingState.value.isDrawing) {
      e.preventDefault()
    }
  }

  /**
   * Clean up temporary area layer
   */
  const cleanupTempLayer = () => {
    if (drawingState.value.tempAreaLayer && map) {
      map.removeLayer(drawingState.value.tempAreaLayer)
      drawingState.value.tempAreaLayer = null
    }
  }

  /**
   * Check if currently in drawing mode
   */
  const isDrawing = () => drawingState.value.isDrawing

  return {
    // State
    drawingState,
    
    // Methods
    initialize,
    startDrawing,
    stopDrawing,
    cleanupTempLayer,
    isDrawing
  }
}