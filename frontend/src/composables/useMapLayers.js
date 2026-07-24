/**
 * Composable for managing map layers visibility and state
 * Centralizes layer control logic previously scattered in MapView.vue
 */
import { ref, computed, readonly } from 'vue'

export function useMapLayers() {
  // Layer visibility state
  const showFriends = ref(false)
  const showFavorites = ref(false)
  const showTimeline = ref(true)
  const showPath = ref(true)
  const showRawGpsPoints = ref(false)
  const showImmich = ref(false)
  const showNotes = ref(false)
  const showWeather = ref(false)

  // Layer control methods
  const toggleFriends = (value) => {
    showFriends.value = value !== undefined ? value : !showFriends.value
  }

  const toggleFavorites = (value) => {
    const newValue = value !== undefined ? value : !showFavorites.value
    console.log('toggleFavorites called:', { 
      currentValue: showFavorites.value, 
      providedValue: value, 
      newValue 
    })
    showFavorites.value = newValue
  }

  const toggleTimeline = (value) => {
    showTimeline.value = value !== undefined ? value : !showTimeline.value
  }

  const togglePath = (value) => {
    showPath.value = value !== undefined ? value : !showPath.value
  }

  const toggleRawGpsPoints = (value) => {
    showRawGpsPoints.value = value !== undefined ? value : !showRawGpsPoints.value
  }

  const toggleImmich = (value) => {
    console.log('useMapLayers: toggleImmich called', {
      currentValue: showImmich.value,
      receivedValue: value,
      willSetTo: value !== undefined ? value : !showImmich.value
    })
    showImmich.value = value !== undefined ? value : !showImmich.value
    console.log('useMapLayers: showImmich is now', showImmich.value)
  }

  const toggleNotes = (value) => {
    showNotes.value = value !== undefined ? value : !showNotes.value
  }

  const toggleWeather = (value) => {
    showWeather.value = value !== undefined ? value : !showWeather.value
  }

  // Convenience methods
  const showAllLayers = () => {
    showFriends.value = true
    showFavorites.value = true
    showTimeline.value = true
    showPath.value = true
    showRawGpsPoints.value = true
    showImmich.value = true
    showNotes.value = true
    showWeather.value = true
  }

  const hideAllLayers = () => {
    showFriends.value = false
    showFavorites.value = false
    showTimeline.value = false
    showPath.value = false
    showRawGpsPoints.value = false
    showImmich.value = false
    showNotes.value = false
    showWeather.value = false
  }

  const resetToDefaults = () => {
    showFriends.value = false
    showFavorites.value = false
    showTimeline.value = true
    showPath.value = true
    showRawGpsPoints.value = false
    showImmich.value = false
    showNotes.value = false
    showWeather.value = false
  }

  // Computed
  const visibleLayerCount = computed(() => {
    return [showFriends.value, showFavorites.value, showTimeline.value, showPath.value, showRawGpsPoints.value, showImmich.value, showNotes.value, showWeather.value]
      .filter(Boolean).length
  })

  const hasVisibleLayers = computed(() => visibleLayerCount.value > 0)

  // Layer configuration for easy iteration
  const layerConfig = computed(() => [
    {
      id: 'friends',
      label: 'Friends',
      visible: showFriends.value,
      toggle: toggleFriends,
      icon: 'pi pi-users'
    },
    {
      id: 'favorites',
      label: 'Favorites',
      visible: showFavorites.value,
      toggle: toggleFavorites,
      icon: 'pi pi-bookmark'
    },
    {
      id: 'timeline',
      label: 'Timeline',
      visible: showTimeline.value,
      toggle: toggleTimeline,
      icon: 'pi pi-clock'
    },
    {
      id: 'path',
      label: 'Path',
      visible: showPath.value,
      toggle: togglePath,
      icon: 'pi pi-map'
    },
    {
      id: 'raw-gps-points',
      label: 'Raw GPS Points',
      visible: showRawGpsPoints.value,
      toggle: toggleRawGpsPoints,
      icon: 'pi pi-circle'
    },
    {
      id: 'immich',
      label: 'Photos',
      visible: showImmich.value,
      toggle: toggleImmich,
      icon: 'pi pi-camera'
    },
    {
      id: 'notes',
      label: 'Notes',
      visible: showNotes.value,
      toggle: toggleNotes,
      icon: 'pi pi-file-edit'
    },
    {
      id: 'weather',
      label: 'Weather',
      visible: showWeather.value,
      toggle: toggleWeather,
      icon: 'pi pi-cloud'
    }
  ])

  return {
    // State
    showFriends: readonly(showFriends),
    showFavorites: readonly(showFavorites),
    showTimeline: readonly(showTimeline),
    showPath: readonly(showPath),
    showRawGpsPoints: readonly(showRawGpsPoints),
    showImmich: readonly(showImmich),
    showNotes: readonly(showNotes),
    showWeather: readonly(showWeather),

    // Methods
    toggleFriends,
    toggleFavorites,
    toggleTimeline,
    togglePath,
    toggleRawGpsPoints,
    toggleImmich,
    toggleNotes,
    toggleWeather,
    showAllLayers,
    hideAllLayers,
    resetToDefaults,

    // Computed
    visibleLayerCount,
    hasVisibleLayers,
    layerConfig
  }
}
