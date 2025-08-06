<template>
  <BaseLayer
    ref="baseLayerRef"
    :map="map"
    :visible="visible"
    @layer-ready="handleLayerReady"
  />
</template>

<script setup>
import { ref, watch, computed, readonly, onMounted, onUnmounted } from 'vue'
import L from 'leaflet'
import BaseLayer from './BaseLayer.vue'
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
const hasPhotos = computed(() => immichStore.hasPhotos)
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
    console.warn('Immich not configured, skipping photo fetch')
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
    const firstPhoto = group.photos[0]

    // Create appropriate icon based on photo count
    const icon = photoCount === 1 ? 
      createImmichPhotoIcon() : 
      createImmichPhotoClusterIcon(photoCount)

    // Create marker without popup auto-binding
    const marker = L.marker([group.latitude, group.longitude], {
      icon,
      photoGroup: group,
      photoCount,
      groupIndex: index,
      ...props.markerOptions
    })

    // Simplified event listeners: hover = preview, click = full viewer
    
    // Click always opens PhotoViewerDialog (consistent UX)
    marker.on('click', (e) => {
      L.DomEvent.stopPropagation(e)
      if (e.originalEvent) {
        e.originalEvent.stopPropagation()
        e.originalEvent.preventDefault()
      }
      
      // Always emit photo-click for consistent PhotoViewerDialog opening
      emit('photo-click', {
        photos: group.photos,
        initialIndex: 0,
        group,
        marker,
        count: photoCount,
        event: e
      })
    })

    // Hover shows preview popup (with throttling to prevent excessive requests)
    let hoverTimeout = null
    marker.on('mouseover', (e) => {
      // Clear any pending hover timeout
      if (hoverTimeout) {
        clearTimeout(hoverTimeout)
      }
      
      // Delay popup opening to prevent excessive hovering
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
      }, 200) // 200ms delay before showing popup
    })
    
    // Hide popup when mouse leaves (with small delay to prevent flickering)
    marker.on('mouseout', (e) => {
      // Clear hover timeout if user moves mouse away quickly
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

    // Add popup content for right-click/context menu
    const popupContent = createPhotoPopupContent(group, index)
    marker.bindPopup(popupContent, {
      maxWidth: window.innerWidth < 768 ? 250 : 300,
      minWidth: window.innerWidth < 768 ? 200 : 250,
      className: 'immich-photo-popup',
      closeButton: true,
      autoClose: true,
      keepInView: false,
      autoPan: false
    })

    // Right-click shows preview (alternative access)
    marker.on('contextmenu', (e) => {
      e.originalEvent?.preventDefault()
      marker.openPopup()
    })
    
    // Popup interactions: keep open on hover, add click-to-view action
    marker.on('popupopen', () => {
      const popupElement = marker.getPopup().getElement()
      
      // Keep popup open when hovering over it
      if (popupElement) {
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

    // Add to layer and track
    baseLayerRef.value.addToLayer(marker)
    photoMarkers.value.push({
      marker,
      group,
      photoCount,
      index
    })
  })

  console.log(`Rendered ${photoMarkers.value.length} Immich photo markers`)
}

// Cache for popup content and blob URLs to avoid regenerating
const popupContentCache = ref(new Map())
const popupBlobCache = ref(new Map())

const createPhotoPopupContent = (group, groupIndex = 0) => {
  const photoCount = group.photos.length
  const cacheKey = `${group.latitude}-${group.longitude}-${photoCount}`
  
  // Return cached content if available
  if (popupContentCache.value.has(cacheKey)) {
    return popupContentCache.value.get(cacheKey)
  }
  
  const firstPhoto = group.photos[0]
  
  let content = '<div class="immich-photo-popup immich-hover-preview">'
  
  // Title
  if (photoCount === 1) {
    content += `<div class="popup-title">📷 Photo</div>`
    
    // Skip thumbnails in popups to avoid CORS issues - user can click to view full image
    content += `<div class="popup-no-thumbnail">
      <i class="pi pi-image" style="font-size: 2rem; color: #6366f1; opacity: 0.5;"></i>
      <div style="font-size: 0.8rem; color: #6366f1; margin-top: 0.25rem;">Preview unavailable</div>
    </div>`
    
    // Photo details
    if (firstPhoto.originalFileName) {
      content += `<div class="popup-filename">${firstPhoto.originalFileName}</div>`
    }
    
    if (firstPhoto.takenAt) {
      const date = new Date(firstPhoto.takenAt)
      content += `<div class="popup-time">${date.toLocaleString()}</div>`
    }
  } else {
    content += `<div class="popup-title">📷 ${photoCount} Photos</div>`
    
    // Skip thumbnail grid to avoid CORS issues - show photo count instead
    content += `<div class="popup-photo-count">
      <i class="pi pi-images" style="font-size: 3rem; color: #6366f1; opacity: 0.5;"></i>
      <div style="font-size: 1.2rem; font-weight: 600; color: #6366f1; margin-top: 0.5rem;">${photoCount} Photos</div>
      <div style="font-size: 0.8rem; color: #6366f1; opacity: 0.8;">Click to view</div>
    </div>`
    
    // Date range for clusters
    const dates = group.photos
      .map(p => p.takenAt)
      .filter(Boolean)
      .map(d => new Date(d))
      .sort((a, b) => a - b)
    
    if (dates.length > 0) {
      if (dates.length === 1) {
        content += `<div class="popup-time">${dates[0].toLocaleDateString()}</div>`
      } else {
        content += `<div class="popup-time">${dates[0].toLocaleDateString()} - ${dates[dates.length - 1].toLocaleDateString()}</div>`
      }
    }
  }
  
  // Action hint for all photos
  content += '<div class="popup-action">Click to view full size</div>'
  content += '</div>'
  
  // Cache the content
  popupContentCache.value.set(cacheKey, content)
  
  return content
}


const clearPhotoMarkers = () => {
  photoMarkers.value.forEach(({ marker }) => {
    baseLayerRef.value?.removeFromLayer(marker)
  })
  photoMarkers.value = []
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
  console.log('ImmichLayer visibility changed:', { 
    newVisible, 
    isConfigured: isConfigured.value, 
    hasPhotos: immichStore.hasPhotos,
    baseLayerReady: baseLayerRef.value?.isReady 
  })
  
  if (newVisible && isConfigured.value) {
    // If we already have photos, just render them; otherwise fetch them
    if (immichStore.hasPhotos) {
      console.log('ImmichLayer: Re-rendering existing photos')
      
      // Wait for BaseLayer to be ready before rendering
      const waitForBaseLayer = async () => {
        let attempts = 0
        while (!baseLayerRef.value?.isReady && attempts < 10) {
          console.log('ImmichLayer: Waiting for BaseLayer to be ready, attempt', attempts + 1)
          await new Promise(resolve => setTimeout(resolve, 100))
          attempts++
        }
        return baseLayerRef.value?.isReady
      }
      
      if (await waitForBaseLayer()) {
        console.log('ImmichLayer: BaseLayer ready, rendering markers')
        renderPhotoMarkers()
      } else {
        console.error('ImmichLayer: BaseLayer never became ready, cannot render markers')
      }
    } else {
      console.log('ImmichLayer: Fetching photos for first time')
      await fetchAndRenderPhotos()
    }
  } else if (!newVisible) {
    console.log('ImmichLayer: Clearing markers (hidden)')
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
/* Component has no template styles - all styling is in the popup CSS below */
</style>

<style>
/* Immich photo popup styling */

.immich-photo-popup {
  font-family: var(--font-family, system-ui);
  font-size: 0.875rem;
  line-height: 1.4;
  min-width: 200px;
  max-width: 300px;
  position: relative;
  z-index: 10001 !important;
}

.immich-photo-popup .popup-title {
  font-weight: 600;
  color: var(--gp-text-primary, #1e293b);
  margin-bottom: 0.75rem;
  font-size: 1rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.immich-photo-popup .popup-thumbnail {
  margin-bottom: 0.75rem;
  text-align: center;
}

.immich-photo-popup .popup-thumbnail img {
  max-width: 100%;
  max-height: 150px;
  border-radius: 6px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.immich-photo-popup .popup-no-thumbnail,
.immich-photo-popup .popup-photo-count {
  text-align: center;
  padding: 1rem;
  background: rgba(99, 102, 241, 0.05);
  border-radius: 8px;
  border: 1px dashed rgba(99, 102, 241, 0.2);
  margin-bottom: 0.75rem;
}

.immich-photo-popup .popup-thumbnail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 4px;
  margin-bottom: 0.75rem;
  max-width: 120px;
}

.immich-photo-popup .popup-grid-thumb {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.immich-photo-popup .popup-more {
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(99, 102, 241, 0.1);
  color: #6366f1;
  font-weight: 600;
  font-size: 0.75rem;
  border-radius: 4px;
  aspect-ratio: 1;
}

.immich-photo-popup .popup-filename {
  font-weight: 500;
  color: var(--gp-text-primary, #1e293b);
  margin-bottom: 0.5rem;
  font-size: 0.9rem;
  word-break: break-all;
}

.immich-photo-popup .popup-time {
  color: var(--gp-text-secondary, #64748b);
  font-size: 0.8rem;
  margin-bottom: 0.5rem;
}

.immich-photo-popup .popup-action {
  color: #6366f1;
  font-size: 0.8rem;
  font-weight: 500;
  text-align: center;
  padding: 0.25rem;
  background: rgba(99, 102, 241, 0.1);
  border-radius: 4px;
  margin-top: 0.5rem;
  cursor: pointer;
  transition: all 0.2s ease;
}

.immich-photo-popup .popup-action:hover {
  background: rgba(99, 102, 241, 0.2);
  color: #4f46e5;
}

/* Dark theme overrides for Immich popups */
.p-dark .leaflet-popup-content-wrapper .immich-photo-popup .popup-title,
.p-dark .leaflet-popup-content-wrapper .immich-photo-popup .popup-filename {
  color: rgba(255, 255, 255, 0.95) !important;
}

.p-dark .leaflet-popup-content-wrapper .immich-photo-popup .popup-time {
  color: rgba(255, 255, 255, 0.8) !important;
}

.p-dark .leaflet-popup-content-wrapper .immich-photo-popup .popup-action {
  color: #a5b4fc !important;
  background: rgba(99, 102, 241, 0.2) !important;
}

.p-dark .leaflet-popup-content-wrapper .immich-photo-popup .popup-action:hover {
  color: #c7d2fe !important;
  background: rgba(99, 102, 241, 0.3) !important;
}

.p-dark .leaflet-popup-content-wrapper .immich-photo-popup .popup-more {
  background: rgba(99, 102, 241, 0.2) !important;
  color: #a5b4fc !important;
}

.p-dark .leaflet-popup-content-wrapper .immich-photo-popup .popup-no-thumbnail,
.p-dark .leaflet-popup-content-wrapper .immich-photo-popup .popup-photo-count {
  background: rgba(99, 102, 241, 0.1) !important;
  border-color: rgba(99, 102, 241, 0.3) !important;
}

/* Light theme - ensure good contrast */
.immich-photo-popup .popup-title,
.immich-photo-popup .popup-filename {
  color: #1e293b !important;
}

.immich-photo-popup .popup-time {
  color: #475569 !important;
}

.immich-photo-popup .popup-action {
  color: #6366f1 !important;
  background: rgba(99, 102, 241, 0.1) !important;
}

.immich-photo-popup .popup-more {
  background: rgba(99, 102, 241, 0.1) !important;
  color: #6366f1 !important;
}

/* Hover preview styling */
.immich-hover-preview {
  cursor: pointer;
  transition: all 0.2s ease;
}

.immich-hover-preview:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2);
}

/* Mobile responsive styles */
@media (max-width: 768px) {
  .immich-photo-popup {
    font-size: 0.8rem;
    min-width: 180px;
    max-width: 250px;
  }
  
  .immich-photo-popup .popup-title {
    font-size: 0.9rem;
    margin-bottom: 0.5rem;
  }
  
  .immich-photo-popup .popup-thumbnail img {
    max-height: 120px;
  }
  
  .immich-photo-popup .popup-thumbnail-grid {
    max-width: 100px;
    gap: 3px;
  }
  
  .immich-photo-popup .popup-filename {
    font-size: 0.8rem;
    margin-bottom: 0.4rem;
  }
  
  .immich-photo-popup .popup-time {
    font-size: 0.75rem;
    margin-bottom: 0.4rem;
  }
  
  .immich-photo-popup .popup-action {
    font-size: 0.75rem;
    padding: 0.2rem;
    margin-top: 0.4rem;
  }
}

/* Small mobile devices */
@media (max-width: 480px) {
  .immich-photo-popup {
    font-size: 0.75rem;
    min-width: 160px;
    max-width: 220px;
  }
  
  .immich-photo-popup .popup-thumbnail img {
    max-height: 100px;
  }
  
  .immich-photo-popup .popup-thumbnail-grid {
    max-width: 80px;
    gap: 2px;
  }
}
</style>