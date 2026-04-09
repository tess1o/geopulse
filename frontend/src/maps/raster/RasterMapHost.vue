<template>
  <div :id="mapId" class="base-map" data-testid="map-host-raster"></div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { FullScreen } from 'leaflet.fullscreen'
import 'leaflet.fullscreen/dist/Control.FullScreen.css'
import { useAuthStore } from '@/stores/auth'
import { MAP_RENDER_MODES, markMapEngineMode } from '@/maps/contracts/mapContracts'
import { fixLeafletMarkerAnimation, fixLeafletMarkerImages, fixLeafLetTooltip } from '@/utils/mapHelpers'
import { resolveRasterTileSource } from '@/maps/runtime/mapSourceResolver'

const props = defineProps({
  mapId: {
    type: String,
    default: 'leaflet-map'
  },
  center: {
    type: Array,
    default: () => null
  },
  zoom: {
    type: Number,
    default: 13
  },
  options: {
    type: Object,
    default: () => ({})
  },
  height: {
    type: String,
    default: '100%'
  },
  width: {
    type: String,
    default: '100%'
  },
  customTileUrl: {
    type: String,
    default: null
  },
  customStyleUrl: {
    type: String,
    default: null
  },
  mapRenderMode: {
    type: String,
    default: null
  },
  isSharedView: {
    type: Boolean,
    default: false
  },
  enableFullscreen: {
    type: Boolean,
    default: true
  },
  fullscreenOptions: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['map-ready', 'map-click', 'map-contextmenu', 'map-warning'])
const authStore = useAuthStore()

const map = ref(null)
const isReady = ref(false)
const isInitializing = ref(false)
const rasterTileLayer = ref(null)

const currentTileSource = computed(() => {
  return resolveRasterTileSource({
    overrideTileUrl: props.customTileUrl,
    isSharedView: props.isSharedView
  })
})

const getBaseTileOptions = (attribution) => ({
  attribution,
  maxZoom: 19,
  minZoom: 0,
  tileSize: 256,
  keepBuffer: 4,
  updateWhenIdle: false,
  updateWhenZooming: true,
  updateInterval: 50,
  maxNativeZoom: 19
})

const createFallbackTileLayer = () => {
  return L.tileLayer('/osm/tiles/{s}/{z}/{x}/{y}.png', {
    ...getBaseTileOptions('© OpenStreetMap contributors'),
    subdomains: ['a', 'b', 'c'],
    crossOrigin: true
  })
}

const createRasterTileLayer = () => {
  const source = currentTileSource.value
  const tileLayerOptions = {
    ...getBaseTileOptions(source.attribution),
    ...(source.subdomains.length > 0 ? { subdomains: source.subdomains } : {})
  }

  const tileLayer = L.tileLayer(source.tileUrl, tileLayerOptions)

  tileLayer.on('tileerror', (error) => {
    if (!error?.tile || error.tile.src?.startsWith('data:')) {
      return
    }

    const tile = error.tile
    const originalSrc = tile.src
    const retryCount = tile.dataset.retryCount ? Number.parseInt(tile.dataset.retryCount, 10) : 0

    if (retryCount < 2 && !originalSrc.startsWith('data:')) {
      setTimeout(() => {
        if (tile.src === originalSrc || tile.src === '') {
          tile.dataset.retryCount = String(retryCount + 1)
          tile.src = ''
          tile.src = originalSrc
        }
      }, 1000 * (retryCount + 1))
    }
  })

  return tileLayer
}

const replaceRasterTileLayer = () => {
  if (!map.value) {
    return
  }

  if (rasterTileLayer.value && map.value.hasLayer(rasterTileLayer.value)) {
    map.value.removeLayer(rasterTileLayer.value)
  }

  try {
    rasterTileLayer.value = createRasterTileLayer()
    rasterTileLayer.value.addTo(map.value)
  } catch (error) {
    emit('map-warning', {
      code: 'raster_tile_load_failed',
      message: 'Custom raster tiles failed to load. Falling back to default OpenStreetMap tiles.',
      error
    })

    rasterTileLayer.value = createFallbackTileLayer()
    rasterTileLayer.value.addTo(map.value)
  }
}

const initializeMap = async () => {
  if (isInitializing.value || map.value) {
    return
  }

  isInitializing.value = true

  fixLeafletMarkerImages()
  fixLeafLetTooltip()
  fixLeafletMarkerAnimation()

  await nextTick()

  let attempts = 0
  const maxAttempts = 20

  const tryInit = () => {
    const container = document.getElementById(props.mapId)

    const isContainerReady = container
      && container.offsetWidth > 0
      && container.offsetHeight > 0
      && container.clientWidth > 0
      && container.clientHeight > 0
      && getComputedStyle(container).display !== 'none'

    if (!isContainerReady) {
      attempts += 1
      if (attempts < maxAttempts) {
        setTimeout(tryInit, 150)
      } else {
        isInitializing.value = false
      }
      return
    }

    try {
      if (container._leaflet_id) {
        if (container._leaflet) {
          container._leaflet.remove()
        }
        delete container._leaflet_id
        delete container._leaflet
        container.innerHTML = ''
      }

      map.value = L.map(props.mapId, {
        attributionControl: true,
        zoomControl: true,
        touchZoom: true,
        dragging: true,
        tap: true,
        touchExtend: false,
        ...props.options
      })

      if (props.center) {
        map.value.setView(props.center, props.zoom)
      }

      markMapEngineMode(map.value, MAP_RENDER_MODES.RASTER)

      if (props.enableFullscreen) {
        map.value.addControl(new FullScreen(props.fullscreenOptions))
      }

      replaceRasterTileLayer()

      map.value.on('click', (event) => {
        emit('map-click', event)
      })

      map.value.on('contextmenu', (event) => {
        event.originalEvent.preventDefault()
        emit('map-contextmenu', event)
      })

      setTimeout(() => {
        map.value?.invalidateSize()
      }, 100)

      isReady.value = true
      isInitializing.value = false

      setupResizeObserver()
      setupVisibilityObserver()

      emit('map-ready', map.value)
    } catch (error) {
      isInitializing.value = false
      attempts += 1
      if (attempts < maxAttempts) {
        setTimeout(tryInit, 200)
      }
    }
  }

  setTimeout(tryInit, 50)
}

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
  if (!map.value || !bounds) {
    return
  }

  const toFiniteCoordinate = (value) => {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }

  const isValidLatitude = (value) => Number.isFinite(value) && value >= -90 && value <= 90
  const isValidLongitude = (value) => Number.isFinite(value) && value >= -180 && value <= 180

  const normalizePoint = (point) => {
    if (Array.isArray(point) && point.length >= 2) {
      const latitude = toFiniteCoordinate(point[0])
      const longitude = toFiniteCoordinate(point[1])
      if (
        latitude === null
        || longitude === null
        || !isValidLatitude(latitude)
        || !isValidLongitude(longitude)
      ) {
        return null
      }
      return [latitude, longitude]
    }

    if (point && typeof point === 'object') {
      const latitude = toFiniteCoordinate(point.lat ?? point.latitude)
      const longitude = toFiniteCoordinate(point.lng ?? point.lon ?? point.longitude)
      if (
        latitude === null
        || longitude === null
        || !isValidLatitude(latitude)
        || !isValidLongitude(longitude)
      ) {
        return null
      }
      return [latitude, longitude]
    }

    return null
  }

  let normalizedBounds = bounds
  if (Array.isArray(bounds)) {
    normalizedBounds = bounds
      .map(normalizePoint)
      .filter(Boolean)
  }

  if (Array.isArray(normalizedBounds) && normalizedBounds.length === 0) {
    console.warn('[RasterMapHost] fitBounds skipped: all bounds points are invalid', { bounds, options })
    return
  }

  try {
    if (Array.isArray(normalizedBounds) && normalizedBounds.length === 1) {
      map.value.setView(normalizedBounds[0], map.value.getZoom())
      return
    }

    map.value.fitBounds(normalizedBounds, { padding: [20, 20], ...options })
  } catch (error) {
    console.error('[RasterMapHost] fitBounds failed', { error, bounds, normalizedBounds, options })
  }
}

watch(() => props.center, (newCenter, oldCenter) => {
  if (map.value && newCenter) {
    const zoom = oldCenter ? map.value.getZoom() : props.zoom
    map.value.setView(newCenter, zoom)
  }
})

watch(() => props.zoom, (newZoom) => {
  if (map.value && typeof newZoom === 'number') {
    map.value.setZoom(newZoom)
  }
})

watch(
  () => [props.customTileUrl, props.isSharedView, authStore.customMapTileUrl],
  () => {
    replaceRasterTileLayer()
  }
)

defineExpose({
  map,
  isReady,
  invalidateSize,
  setView,
  fitBounds,
  L
})

let resizeObserver = null
let visibilityObserver = null

const setupResizeObserver = () => {
  const container = document.getElementById(props.mapId)
  if (container && window.ResizeObserver && !resizeObserver) {
    resizeObserver = new ResizeObserver((entries) => {
      for (const entry of entries) {
        if (map.value && entry.contentRect.width > 0 && entry.contentRect.height > 0) {
          setTimeout(() => {
            map.value?.invalidateSize()
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
          const rect = entry.boundingClientRect
          if (rect.width > 0 && rect.height > 0) {
            if (!map.value && !isInitializing.value) {
              setTimeout(() => {
                if (!map.value && !isInitializing.value) {
                  initializeMap()
                }
              }, 200)
            } else {
              setTimeout(() => {
                map.value?.invalidateSize()
              }, 300)
            }
          }
        }
      }
    }, {
      threshold: [0, 0.1]
    })

    visibilityObserver.observe(container)
  }
}

onMounted(() => {
  initializeMap()

  setTimeout(() => {
    setupVisibilityObserver()
  }, 50)
})

onUnmounted(() => {
  isInitializing.value = false
  isReady.value = false

  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }

  if (visibilityObserver) {
    visibilityObserver.disconnect()
    visibilityObserver = null
  }

  if (map.value) {
    try {
      map.value.remove()
    } catch {
      // no-op
    }
    map.value = null
  }

  rasterTileLayer.value = null

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
