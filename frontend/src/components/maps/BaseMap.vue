<template>
  <div :id="mapId" class="base-map"></div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import {fixLeafletMarkerImages, fixLeafLetTooltip, fixLeafletMarkerAnimation} from "@/utils/mapHelpers";

const props = defineProps({
  mapId: {
    type: String,
    default: 'leaflet-map'
  },
  center: {
    type: Array,
    default: () => [51.505, -0.09]
  },
  zoom: {
    type: Number,
    default: 13
  },
  height: {
    type: String,
    default: '100%'
  },
  width: {
    type: String,
    default: '100%'
  }
})

const emit = defineEmits(['map-ready', 'map-click', 'map-contextmenu'])

// Reactive state
const map = ref(null)
const isReady = ref(false)
const isInitializing = ref(false)

// Map initialization with improved container detection
const initializeMap = async () => {
  // Prevent multiple initialization attempts
  if (isInitializing.value || map.value) {
    return
  }
  
  isInitializing.value = true
  
  fixLeafletMarkerImages();
  fixLeafLetTooltip();
  fixLeafletMarkerAnimation();
  
  // Wait for next tick to ensure DOM is ready
  await nextTick()
  
  // Wait for container to be ready with retries
  let attempts = 0
  const maxAttempts = 20 // Increased max attempts
  
  function tryInit() {
    const container = document.getElementById(props.mapId)
    
    // More comprehensive container readiness check
    const isContainerReady = container && 
                            container.offsetWidth > 0 && 
                            container.offsetHeight > 0 &&
                            container.clientWidth > 0 &&
                            container.clientHeight > 0 &&
                            getComputedStyle(container).display !== 'none'

    if (isContainerReady) {
      try {
        // Check if container already has a Leaflet map and clean it up
        if (container._leaflet_id) {
          // Remove any existing Leaflet instance
          if (container._leaflet) {
            container._leaflet.remove()
          }
          // Clear Leaflet's internal references
          delete container._leaflet_id
          delete container._leaflet
          // Clear the container content
          container.innerHTML = ''
        }
        
        // Create map with error handling
        try {
          map.value = L.map(props.mapId, {
            attributionControl: true,
            zoomControl: true,
            touchZoom: true,
            dragging: true,
            tap: true,
            touchExtend: false
          }).setView(props.center, props.zoom)

          // Add tile layer
          L.tileLayer('/osm/tiles/{s}/{z}/{x}/{y}.png', {
            attribution: '© OpenStreetMap contributors',
            subdomains: ['a', 'b', 'c'],
            maxZoom: 19
          }).addTo(map.value)
        } catch (mapError) {
          console.error('Error creating Leaflet map:', mapError)
          throw mapError
        }

        // Add event listeners
        map.value.on('click', (e) => {
          emit('map-click', e)
        })

        map.value.on('contextmenu', (e) => {
          e.originalEvent.preventDefault()
          emit('map-contextmenu', e)
        })

        // Ensure map container size is correct
        setTimeout(() => {
          if (map.value) {
            map.value.invalidateSize()
          }
        }, 100)

        isReady.value = true
        isInitializing.value = false
        
        // Set up ResizeObserver after map is created
        setupResizeObserver()
        
        // Set up visibility observer to handle show/hide scenarios
        setupVisibilityObserver()
        
        emit('map-ready', map.value)
        
      } catch (error) {
        console.error('BaseMap initialization error:', error)
        isInitializing.value = false
        attempts++
        if (attempts < maxAttempts) {
          setTimeout(tryInit, 200)
        } else {
          console.error(`BaseMap initialization failed after ${maxAttempts} attempts:`, props.mapId)
        }
      }
    } else {
      attempts++
      if (attempts < maxAttempts) {
        setTimeout(tryInit, 150)
      } else {
        isInitializing.value = false
      }
    }
  }
  
  // Start initialization with a small delay
  setTimeout(tryInit, 50)
}

// Public methods
const invalidateSize = () => {
  if (map.value) {
    map.value.invalidateSize()
  }
}

const setView = (center, zoom) => {
  if (map.value) {
    map.value.setView(center, zoom)
  }
}

const fitBounds = (bounds, options = {}) => {
  if (map.value && bounds) {
    map.value.fitBounds(bounds, { padding: [20, 20], ...options })
  }
}

// Watch for prop changes
watch(() => props.center, (newCenter) => {
  if (map.value && newCenter) {
    map.value.setView(newCenter, map.value.getZoom())
  }
})

watch(() => props.zoom, (newZoom) => {
  if (map.value && typeof newZoom === 'number') {
    map.value.setZoom(newZoom)
  }
})

// Expose methods
defineExpose({
  map,
  isReady,
  invalidateSize,
  setView,
  fitBounds
})

// ResizeObserver to handle container size changes
let resizeObserver = null
let visibilityObserver = null

const setupResizeObserver = () => {
  const container = document.getElementById(props.mapId)
  if (container && window.ResizeObserver && !resizeObserver) {
    resizeObserver = new ResizeObserver((entries) => {
      for (const entry of entries) {
        if (map.value && entry.contentRect.width > 0 && entry.contentRect.height > 0) {
          // Delay invalidateSize to avoid conflicts during rapid resizing
          setTimeout(() => {
            if (map.value) {
              map.value.invalidateSize()
            }
          }, 100)
        }
      }
    })
    
    resizeObserver.observe(container)
  }
}

const setupVisibilityObserver = () => {
  const container = document.getElementById(props.mapId)
  if (container && window.IntersectionObserver && !visibilityObserver) {
    visibilityObserver = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting && entry.intersectionRatio > 0) {
          // Container became visible
          const containerRect = entry.boundingClientRect
          if (containerRect.width > 0 && containerRect.height > 0) {
            // Only initialize if map doesn't exist yet and not currently initializing
            if (!map.value && !isInitializing.value) {
              setTimeout(() => {
                if (!map.value && !isInitializing.value) {
                  initializeMap()
                }
              }, 200) // Increased delay for tab switching
            } else if (map.value) {
              // Just invalidate size if map already exists
              setTimeout(() => {
                if (map.value) {
                  map.value.invalidateSize()
                }
              }, 300) // Increased delay for tab switching
            }
          }
        }
      }
    }, {
      threshold: [0, 0.1] // Trigger when element becomes visible
    })
    
    visibilityObserver.observe(container)
  }
}

// Lifecycle
onMounted(() => {
  initializeMap()
  
  // Set up visibility observer early in case container starts hidden
  setTimeout(() => {
    setupVisibilityObserver()
  }, 50)
})

onUnmounted(() => {
  // Set flag to prevent any pending initialization
  isInitializing.value = false
  isReady.value = false
  
  // Disconnect observers first
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  
  if (visibilityObserver) {
    visibilityObserver.disconnect()
    visibilityObserver = null
  }
  
  // Remove map instance
  if (map.value) {
    try {
      map.value.remove()
    } catch (error) {
      console.warn('Error removing map:', error)
    }
    map.value = null
  }
  
  // Clean up container with small delay to ensure DOM operations complete
  setTimeout(() => {
    const container = document.getElementById(props.mapId)
    if (container) {
      if (container._leaflet_id) {
        delete container._leaflet_id
        delete container._leaflet
      }
      container.innerHTML = ''
    }
  }, 10)
})
</script>

<style scoped>
.base-map {
  width: v-bind(width);
  height: v-bind(height);
  min-width: 300px;
  min-height: 300px;
  background-color: #f0f0f0;
}
</style>