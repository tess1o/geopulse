/**
 * Composable for managing map highlights (timeline, paths, stay points)
 * Integrates with highlight store and provides local highlight management
 */
import { ref, computed, watch, readonly } from 'vue'
import { useHighlightStore } from '@/stores/highlight'
import { storeToRefs } from 'pinia'

export function useMapHighlights() {
  // Get highlight store
  const highlightStore = useHighlightStore()
  const { highlightedTrip, highlightedStayPoint } = storeToRefs(highlightStore)

  // Temporary highlights (for hover effects only)
  const tempHighlights = ref({
    timelineItem: null,
    pathIndex: null,
    friend: null,
    favorite: null
  })

  // Highlight methods for timeline items
  const highlightTimelineItem = (item, temporary = false) => {
    if (temporary) {
      tempHighlights.value.timelineItem = item
    } else {
      // Use store for persistent highlights
      highlightStore.setHighlightedItem(item)
    }
  }

  const clearTimelineHighlight = (temporary = false) => {
    if (temporary) {
      tempHighlights.value.timelineItem = null
    } else {
      // Clear store highlights
      highlightStore.clearAllHighlights()
    }
  }

  // Highlight methods for paths (temporary only - paths don't use store)
  const highlightPath = (pathIndex, temporary = true) => {
    tempHighlights.value.pathIndex = pathIndex
  }

  const clearPathHighlight = (temporary = true) => {
    tempHighlights.value.pathIndex = null
  }

  // Highlight methods for friends (temporary only - friends don't use store)
  const highlightFriend = (friend, temporary = true) => {
    tempHighlights.value.friend = friend
  }

  const clearFriendHighlight = (temporary = true) => {
    tempHighlights.value.friend = null
  }

  // Highlight methods for favorites (temporary only - favorites don't use store)
  const highlightFavorite = (favorite, temporary = true) => {
    tempHighlights.value.favorite = favorite
  }

  const clearFavoriteHighlight = (temporary = true) => {
    tempHighlights.value.favorite = null
  }

  // Clear all highlights
  const clearAllTempHighlights = () => {
    tempHighlights.value = {
      timelineItem: null,
      pathIndex: null,
      friend: null,
      favorite: null
    }
  }

  const clearAllMapHighlights = () => {
    // Clear temporary highlights
    clearAllTempHighlights()
    // Clear store highlights  
    highlightStore.clearAllHighlights()
  }

  // Computed properties for combined highlights
  const activeTimelineHighlight = computed(() => {
    // Temporary highlights take precedence, then store
    return tempHighlights.value.timelineItem || 
           highlightedStayPoint.value ||
           highlightedTrip.value
  })

  const activePathHighlight = computed(() => {
    return tempHighlights.value.pathIndex
  })

  const activeFriendHighlight = computed(() => {
    return tempHighlights.value.friend
  })

  const activeFavoriteHighlight = computed(() => {
    return tempHighlights.value.favorite
  })

  // Check if any highlights are active
  const hasActiveHighlights = computed(() => {
    return Boolean(
      activeTimelineHighlight.value ||
      activePathHighlight.value !== null ||
      activeFriendHighlight.value ||
      activeFavoriteHighlight.value
    )
  })

  const hasTempHighlights = computed(() => {
    return Boolean(
      tempHighlights.value.timelineItem ||
      tempHighlights.value.pathIndex !== null ||
      tempHighlights.value.friend ||
      tempHighlights.value.favorite
    )
  })

  // Highlight configuration for different types
  const highlightConfig = computed(() => ({
    timeline: {
      active: activeTimelineHighlight.value,
      highlight: highlightTimelineItem,
      clear: clearTimelineHighlight,
      color: '#27AE60',
      pulse: true
    },
    path: {
      active: activePathHighlight.value,
      highlight: highlightPath,
      clear: clearPathHighlight,
      color: '#ff6b6b',
      weight: 6
    },
    friend: {
      active: activeFriendHighlight.value,
      highlight: highlightFriend,
      clear: clearFriendHighlight,
      color: '#F39C12',
      pulse: true
    },
    favorite: {
      active: activeFavoriteHighlight.value,
      highlight: highlightFavorite,
      clear: clearFavoriteHighlight,
      color: '#e91e63',
      glow: true
    }
  }))

  // Auto-clear temporary highlights after a delay
  const autoCleanupTimeouts = ref(new Map())

  const setTemporaryHighlight = (type, value, duration = 3000) => {
    // Clear existing timeout for this type
    if (autoCleanupTimeouts.value.has(type)) {
      clearTimeout(autoCleanupTimeouts.value.get(type))
    }

    // Set highlight
    switch (type) {
      case 'timeline':
        highlightTimelineItem(value, true)
        break
      case 'path':
        highlightPath(value, true)
        break
      case 'friend':
        highlightFriend(value, true)
        break
      case 'favorite':
        highlightFavorite(value, true)
        break
    }

    // Set cleanup timeout
    if (duration > 0) {
      const timeout = setTimeout(() => {
        switch (type) {
          case 'timeline':
            clearTimelineHighlight(true)
            break
          case 'path':
            clearPathHighlight(true)
            break
          case 'friend':
            clearFriendHighlight(true)
            break
          case 'favorite':
            clearFavoriteHighlight(true)
            break
        }
        autoCleanupTimeouts.value.delete(type)
      }, duration)

      autoCleanupTimeouts.value.set(type, timeout)
    }
  }

  // Watch for store changes to sync local state if needed
  watch(highlightedTrip, (newTrip) => {
    if (newTrip) {
      // Could sync with local highlights if needed
      console.debug('Store highlighted trip changed:', newTrip)
    }
  })

  watch(highlightedStayPoint, (newStayPoint) => {
    if (newStayPoint) {
      // Could sync with local highlights if needed
      console.debug('Store highlighted stay point changed:', newStayPoint)
    }
  })

  return {
    // Store highlights (readonly)
    highlightedTrip: readonly(highlightedTrip),
    highlightedStayPoint: readonly(highlightedStayPoint),

    // Temp highlight state
    tempHighlights: readonly(tempHighlights),

    // Highlight methods
    highlightTimelineItem,
    clearTimelineHighlight,
    highlightPath,
    clearPathHighlight,
    highlightFriend,
    clearFriendHighlight,
    highlightFavorite,
    clearFavoriteHighlight,
    clearAllTempHighlights,
    clearAllMapHighlights,

    // Temporary highlights
    setTemporaryHighlight,

    // Active highlights (computed)
    activeTimelineHighlight,
    activePathHighlight,
    activeFriendHighlight,
    activeFavoriteHighlight,

    // Status checks
    hasActiveHighlights,
    hasTempHighlights,

    // Configuration
    highlightConfig
  }
}