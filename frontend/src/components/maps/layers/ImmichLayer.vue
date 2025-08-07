<template>
  <BaseLayer
    ref="baseLayerRef"
    :map="map"
    :visible="visible"
    @layer-ready="handleLayerReady"
  />
</template>

<script setup>
import { ref, watch, computed, readonly, onMounted, onUnmounted, createApp } from 'vue'
import L from 'leaflet'
import BaseLayer from './BaseLayer.vue'
import ImmichPhotoPopup from '../popups/ImmichPhotoPopup.vue'
import { createImmichPhotoIcon, createImmichPhotoClusterIcon } from '@/utils/mapHelpers'
import { useImmichStore } from '@/stores/immich'
import { useDateRangeStore } from '@/stores/dateRange'

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  visible: {
    type: Boolean,
    default: true
  },
  markerOptions: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['photo-click', 'cluster-click', 'photo-hover', 'error'])

// Store instances
const immichStore = useImmichStore()
const dateRangeStore = useDateRangeStore()

// State
const baseLayerRef = ref(null)
const photoMarkers = ref([])
const loading = ref(false)

// Computed
const isConfigured = computed(() => immichStore.isConfigured)
const photoGroups = computed(() => immichStore.photoGroups)

// Layer management
const handleLayerReady = async (layerGroup) => {
  // Only fetch and render if configured and visible
  if (isConfigured.value && props.visible) {
    await fetchAndRenderPhotos()
  }
}

const fetchAndRenderPhotos = async () => {
  if (!isConfigured.value) {
    emit('error', {
      type: 'config',
      message: 'Immich is not configured. Please set up your Immich server first.',
      error: new Error('Immich not configured')
    })
    return
  }

  try {
    loading.value = true
    
    // Fetch photos from the store (it handles date range automatically)
    await immichStore.fetchPhotos()
    
    // Check if there was an error during fetch
    if (immichStore.photosError) {
      emit('error', {
        type: 'fetch',
        message: immichStore.photosError,
        error: new Error(immichStore.photosError)
      })
      return
    }
    
    // Render markers after photos are fetched
    if (baseLayerRef.value?.isReady) {
      renderPhotoMarkers()
    }
  } catch (error) {
    console.error('Failed to fetch Immich photos:', error)
    emit('error', {
      type: 'fetch',
      message: error.userMessage || 'Failed to load photos from Immich',
      error
    })
  } finally {
    loading.value = false
  }
}

const renderPhotoMarkers = () => {
  if (!baseLayerRef.value || !isConfigured.value) return

  // Clear existing markers
  clearPhotoMarkers()

  // Render grouped photos (clustered by location)
  photoGroups.value.forEach((group, index) => {
    if (!group.latitude || !group.longitude || group.photos.length === 0) return

    const photoCount = group.photos.length

    // Create appropriate icon based on photo count
    const icon = photoCount === 1 ? 
      createImmichPhotoIcon() : 
      createImmichPhotoClusterIcon(photoCount)

    // Create marker
    const marker = L.marker([group.latitude, group.longitude], {
      icon,
      photoGroup: group,
      photoCount,
      ...props.markerOptions
    })
    
    // Click opens PhotoViewerDialog
    marker.on('click', (e) => {
      L.DomEvent.stopPropagation(e)
      if (e.originalEvent) {
        e.originalEvent.stopPropagation()
        e.originalEvent.preventDefault()
      }
      
      // Emit photo-click for PhotoViewerDialog
      emit('photo-click', {
        photos: group.photos,
        initialIndex: 0,
        group,
        marker,
        count: photoCount,
        event: e
      })
    })

    // Hover shows preview popup with throttling
    let hoverTimeout = null
    marker.on('mouseover', (e) => {
      if (hoverTimeout) clearTimeout(hoverTimeout)
      
      hoverTimeout = setTimeout(() => {
        if (!marker.isPopupOpen()) {
          marker.openPopup()
          emit('photo-hover', {
            photos: group.photos,
            group,
            marker,
            count: photoCount,
            event: e
          })
        }
      }, 200)
    })
    
    // Hide popup when mouse leaves with delay to prevent flickering
    marker.on('mouseout', (e) => {
      if (hoverTimeout) {
        clearTimeout(hoverTimeout)
        hoverTimeout = null
      }
      
      setTimeout(() => {
        if (!marker.getPopup()?._isHovered && marker.isPopupOpen()) {
          marker.closePopup()
        }
      }, 150)
    })

    // Bind popup content
    const cacheKey = `${group.latitude}-${group.longitude}-${photoCount}`
    const popupContent = createPhotoPopupContent(group)
    const popupData = popupContentCache.value.get(cacheKey)
    
    marker.bindPopup(popupContent, {
      maxWidth: window.innerWidth < 768 ? 250 : 300,
      minWidth: window.innerWidth < 768 ? 200 : 250,
      className: 'immich-photo-popup',
      closeButton: true,
      autoClose: true,
      keepInView: false,
      autoPan: false,
      offset: [0, -15]
    })

    // Right-click shows preview
    marker.on('contextmenu', (e) => {
      e.originalEvent?.preventDefault()
      marker.openPopup()
    })
    
    // Setup popup interactions
    marker.on('popupopen', () => {
      const popupElement = marker.getPopup().getElement()
      
      if (popupElement) {
        // Adjust popup position based on screen location
        adjustPopupPositionOnScreen(marker, popupElement)
        
        // Mount Vue component if this popup has structured data
        if (popupData && popupData.containerId) {
          const container = popupElement.querySelector(`#${popupData.containerId}`)
          if (container && !popupVueApps.value.has(popupData.containerId)) {
            const app = createApp(ImmichPhotoPopup, {
              group: popupData.group
            })
            app.mount(container)
            popupVueApps.value.set(popupData.containerId, app)
          }
        }
        
        // Keep popup open when hovering over it
        popupElement.addEventListener('mouseenter', () => {
          marker.getPopup()._isHovered = true
        })
        
        popupElement.addEventListener('mouseleave', () => {
          marker.getPopup()._isHovered = false
          setTimeout(() => {
            if (!marker.getPopup()._isHovered) {
              marker.closePopup()
            }
          }, 100)
        })
        
        // Add click listener to popup to open full viewer
        popupElement.addEventListener('click', () => {
          marker.closePopup()
          emit('photo-click', {
            photos: group.photos,
            initialIndex: 0,
            group,
            marker,
            count: photoCount
          })
        })
      }
    })
    
    // Cleanup Vue app when popup closes
    marker.on('popupclose', () => {
      if (popupData.containerId && popupVueApps.value.has(popupData.containerId)) {
        const app = popupVueApps.value.get(popupData.containerId)
        app.unmount()
        popupVueApps.value.delete(popupData.containerId)
      }
    })

    // Add to layer and track
    baseLayerRef.value.addToLayer(marker)
    photoMarkers.value.push({ marker, group, photoCount })
  })

}

// Cache for popup content to avoid regenerating
const popupContentCache = ref(new Map())

// Store Vue app instances for popups to keep them reactive
const popupVueApps = ref(new Map())

const adjustPopupPositionOnScreen = (marker, popupElement) => {
  if (!props.map || !popupElement) return
  
  const map = props.map
  const markerLatLng = marker.getLatLng()
  const markerPoint = map.latLngToContainerPoint(markerLatLng)
  const mapSize = map.getSize()
  
  // Get popup dimensions with small delay for rendering
  setTimeout(() => {
    const popupRect = popupElement.getBoundingClientRect()
    const popupHeight = popupRect.height || 200
    const distanceFromTop = markerPoint.y
    const popupWithMargin = popupHeight + 60
    
    // Override Leaflet's positioning
    popupElement.style.position = 'fixed'
    popupElement.style.left = `${markerPoint.x}px`
    popupElement.style.bottom = 'auto'
    popupElement.style.marginLeft = '0'
    
    // Position popup based on available space
    if (distanceFromTop < popupWithMargin) {
      // Position below marker when near top
      popupElement.style.top = `${markerPoint.y + 15}px`
      popupElement.style.transform = 'translateX(-50%)'
      
      // Flip the tip
      const tip = popupElement.querySelector('.leaflet-popup-tip-container')
      if (tip) {
        tip.style.transform = 'rotate(180deg) translateY(-5px)'
      }
    } else {
      // Default position above marker
      popupElement.style.top = `${markerPoint.y - popupHeight - 10}px`
      popupElement.style.transform = 'translateX(-50%)'
    }
  }, 10)
}

const createPhotoPopupContent = (group) => {
  const photoCount = group.photos.length
  const cacheKey = `${group.latitude}-${group.longitude}-${photoCount}`
  
  // Return cached content if available
  if (popupContentCache.value.has(cacheKey)) {
    return popupContentCache.value.get(cacheKey).content
  }
  
  // Create a container div with a unique ID for the Vue component
  const containerId = `popup-${cacheKey.replace(/[^a-zA-Z0-9]/g, '')}-${Date.now()}`
  const content = `<div id="${containerId}"></div>`
  
  // Cache the content
  popupContentCache.value.set(cacheKey, { content, containerId, group })
  
  return content
}



const clearPhotoMarkers = () => {
  photoMarkers.value.forEach(({ marker }) => {
    baseLayerRef.value?.removeFromLayer(marker)
  })
  photoMarkers.value = []
  
  // Clean up all Vue popup apps
  popupVueApps.value.forEach((app, containerId) => {
    try {
      app.unmount()
    } catch (error) {
      console.warn('Error unmounting popup app:', error)
    }
  })
  popupVueApps.value.clear()
}

const refreshPhotos = async () => {
  if (!isConfigured.value || !props.visible) return
  
  try {
    await immichStore.fetchPhotos(null, null, true) // Force refresh
    
    // Check if there was an error during refresh
    if (immichStore.photosError) {
      emit('error', {
        type: 'refresh',
        message: immichStore.photosError,
        error: new Error(immichStore.photosError)
      })
      return
    }
    
    if (baseLayerRef.value?.isReady) {
      renderPhotoMarkers()
    }
  } catch (error) {
    console.error('Failed to refresh Immich photos:', error)
    emit('error', {
      type: 'refresh',
      message: error.userMessage || 'Failed to refresh photos from Immich',
      error
    })
  }
}

// Watch for data changes
watch(() => immichStore.photoGroups, () => {
  if (baseLayerRef.value?.isReady && props.visible) {
    renderPhotoMarkers()
  }
}, { deep: true })

// Watch for date range changes
watch(() => dateRangeStore.getCurrentDateRange, async (newRange) => {
  if (newRange && props.visible && isConfigured.value) {
    await fetchAndRenderPhotos()
  }
}, { deep: true })

// Watch for visibility changes
watch(() => props.visible, async (newVisible) => {
  
  if (newVisible && isConfigured.value) {
    // Re-render existing photos or fetch new ones
    if (immichStore.hasPhotos) {
      // Wait for BaseLayer to be ready before rendering
      const waitForBaseLayer = async () => {
        let attempts = 0
        while (!baseLayerRef.value?.isReady && attempts < 10) {
          await new Promise(resolve => setTimeout(resolve, 100))
          attempts++
        }
        return baseLayerRef.value?.isReady
      }
      
      if (await waitForBaseLayer()) {
        renderPhotoMarkers()
      }
    } else {
      await fetchAndRenderPhotos()
    }
  } else if (!newVisible) {
    clearPhotoMarkers()
  }
})

// Watch for configuration changes
watch(() => immichStore.isConfigured, async (newConfigured) => {
  if (newConfigured && props.visible) {
    await fetchAndRenderPhotos()
  } else if (!newConfigured) {
    clearPhotoMarkers()
  }
})

// Initialize configuration check on mount
onMounted(async () => {
  try {
    // Check/fetch Immich configuration
    await immichStore.fetchConfig()
  } catch (error) {
    console.error('Failed to fetch Immich config on mount:', error)
  }
})

// Cleanup on unmount
onUnmounted(() => {
  clearPhotoMarkers()
})

// Expose methods
defineExpose({
  baseLayerRef,
  photoMarkers: readonly(photoMarkers),
  refreshPhotos,
  clearPhotoMarkers,
  isLoading: readonly(loading)
})
</script>

<style scoped>
/* Component has no template styles - popup styling is now in ImmichPhotoPopup.vue */
</style>