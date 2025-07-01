/**
 * Composable for managing map interactions and event handling
 * Centralizes click handlers, context menus, and user interactions
 */
import { ref, computed, readonly } from 'vue'

export function useMapInteractions(options = {}) {
  const {
    onTimelineMarkerClick,
    onPathClick,
    onFriendClick,
    onFavoriteClick,
    onMapClick,
    onMapContextMenu
  } = options

  // Interaction state
  const selectedMarker = ref(null)
  const hoveredMarker = ref(null)
  const isDrawingMode = ref(false)
  const drawingType = ref(null) // 'point', 'area'

  // Context menu state
  const contextMenu = ref({
    visible: false,
    x: 0,
    y: 0,
    type: null, // 'map', 'marker', 'favorite'
    data: null
  })

  // Timeline marker interactions
  const handleTimelineMarkerClick = (event) => {
    const { timelineItem, marker, event: leafletEvent } = event
    
    selectedMarker.value = {
      type: 'timeline',
      data: timelineItem,
      marker
    }

    onTimelineMarkerClick?.(event)
  }

  const handleTimelineMarkerHover = (event) => {
    const { timelineItem, marker } = event
    
    hoveredMarker.value = {
      type: 'timeline',
      data: timelineItem,
      marker
    }
  }

  // Path interactions
  const handlePathClick = (event) => {
    const { pathData, pathIndex, event: leafletEvent } = event
    
    selectedMarker.value = {
      type: 'path',
      data: pathData,
      index: pathIndex
    }

    onPathClick?.(event)
  }

  const handlePathHover = (event) => {
    const { pathData, pathIndex } = event
    
    hoveredMarker.value = {
      type: 'path',
      data: pathData,
      index: pathIndex
    }
  }

  // Friend interactions
  const handleFriendClick = (event) => {
    const { friend, marker, event: leafletEvent } = event
    
    selectedMarker.value = {
      type: 'friend',
      data: friend,
      marker
    }

    onFriendClick?.(event)
  }

  const handleFriendHover = (event) => {
    const { friend, marker } = event
    
    hoveredMarker.value = {
      type: 'friend',
      data: friend,
      marker
    }
  }

  // Favorite interactions
  const handleFavoriteClick = (event) => {
    const { favorite, marker, event: leafletEvent } = event
    
    selectedMarker.value = {
      type: 'favorite',
      data: favorite,
      marker
    }

    onFavoriteClick?.(event)
  }

  const handleFavoriteHover = (event) => {
    const { favorite, marker } = event
    
    hoveredMarker.value = {
      type: 'favorite',
      data: favorite,
      marker
    }
  }

  const handleFavoriteContextMenu = (event) => {
    const { favorite, event: leafletEvent } = event
    
    showContextMenu({
      x: leafletEvent.originalEvent.clientX,
      y: leafletEvent.originalEvent.clientY,
      type: 'favorite',
      data: favorite
    })
  }

  // Map interactions
  const handleMapClick = (event) => {
    // Clear selection unless clicking on a marker
    if (!event.target._icon) {
      clearSelection()
    }

    hideContextMenu()
    onMapClick?.(event)
  }

  const handleMapContextMenu = (event) => {
    showContextMenu({
      x: event.originalEvent.clientX,
      y: event.originalEvent.clientY,
      type: 'map',
      data: {
        latlng: event.latlng,
        containerPoint: event.containerPoint,
        layerPoint: event.layerPoint
      }
    })

    onMapContextMenu?.(event)
  }

  // Context menu management
  const showContextMenu = (menuData) => {
    contextMenu.value = {
      visible: true,
      ...menuData
    }
  }

  const hideContextMenu = () => {
    contextMenu.value.visible = false
  }

  // Selection management
  const clearSelection = () => {
    selectedMarker.value = null
  }

  const clearHover = () => {
    hoveredMarker.value = null
  }

  const selectMarker = (type, data, marker = null) => {
    selectedMarker.value = {
      type,
      data,
      marker
    }
  }

  // Drawing mode management
  const startDrawing = (type = 'point') => {
    isDrawingMode.value = true
    drawingType.value = type
  }

  const stopDrawing = () => {
    isDrawingMode.value = false
    drawingType.value = null
  }

  const toggleDrawing = (type = 'point') => {
    if (isDrawingMode.value && drawingType.value === type) {
      stopDrawing()
    } else {
      startDrawing(type)
    }
  }

  // Computed
  const hasSelection = computed(() => selectedMarker.value !== null)
  const hasHover = computed(() => hoveredMarker.value !== null)
  const isContextMenuVisible = computed(() => contextMenu.value.visible)

  const selectionInfo = computed(() => {
    if (!selectedMarker.value) return null
    
    return {
      type: selectedMarker.value.type,
      id: selectedMarker.value.data?.id,
      name: selectedMarker.value.data?.name || selectedMarker.value.data?.address,
      data: selectedMarker.value.data
    }
  })

  // Interaction configuration for different marker types
  const interactionConfig = computed(() => ({
    timeline: {
      onClick: handleTimelineMarkerClick,
      onHover: handleTimelineMarkerHover,
      selectable: true,
      hoverable: true
    },
    path: {
      onClick: handlePathClick,
      onHover: handlePathHover,
      selectable: true,
      hoverable: true
    },
    friend: {
      onClick: handleFriendClick,
      onHover: handleFriendHover,
      selectable: true,
      hoverable: true
    },
    favorite: {
      onClick: handleFavoriteClick,
      onHover: handleFavoriteHover,
      onContextMenu: handleFavoriteContextMenu,
      selectable: true,
      hoverable: true,
      contextMenu: true
    }
  }))

  return {
    // State
    selectedMarker: readonly(selectedMarker),
    hoveredMarker: readonly(hoveredMarker),
    contextMenu: readonly(contextMenu),
    isDrawingMode: readonly(isDrawingMode),
    drawingType: readonly(drawingType),

    // Event handlers
    handleTimelineMarkerClick,
    handleTimelineMarkerHover,
    handlePathClick,
    handlePathHover,
    handleFriendClick,
    handleFriendHover,
    handleFavoriteClick,
    handleFavoriteHover,
    handleFavoriteContextMenu,
    handleMapClick,
    handleMapContextMenu,

    // Context menu
    showContextMenu,
    hideContextMenu,

    // Selection management
    clearSelection,
    clearHover,
    selectMarker,

    // Drawing mode
    startDrawing,
    stopDrawing,
    toggleDrawing,

    // Computed
    hasSelection,
    hasHover,
    isContextMenuVisible,
    selectionInfo,
    interactionConfig
  }
}