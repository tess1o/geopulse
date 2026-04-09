<template>
  <div :id="mapId" class="base-map" data-testid="map-host-vector"></div>
</template>

<script setup>
import { computed, markRaw, nextTick, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import '@/maps/shared/styles/maplibreMarkerFixes.css'
import { useAuthStore } from '@/stores/auth'
import { MAP_RENDER_MODES, markMapEngineMode } from '@/maps/contracts/mapContracts'
import { resolveVectorStyleSource } from '@/maps/runtime/mapSourceResolver'
import { normalizeLeafletBoundsToMapLibre, toLngLatTuple } from '@/maps/vector/utils/maplibreLayerUtils'

const props = defineProps({
  mapId: {
    type: String,
    default: 'maplibre-map'
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

const emit = defineEmits(['map-ready', 'map-click', 'map-contextmenu', 'map-warning', 'engine-fatal'])
const authStore = useAuthStore()

const map = shallowRef(null)
const isReady = ref(false)
const isInitializing = ref(false)
const appliedStyleUrl = ref('')

const currentStyleSource = computed(() => {
  return resolveVectorStyleSource({
    overrideStyleUrl: props.customStyleUrl,
    isSharedView: props.isSharedView
  })
})

const hasBrowserWebGlContext = () => {
  if (typeof document === 'undefined') {
    return false
  }

  try {
    const canvas = document.createElement('canvas')
    return Boolean(
      canvas.getContext('webgl2')
      || canvas.getContext('webgl')
      || canvas.getContext('experimental-webgl')
    )
  } catch {
    return false
  }
}

const getWebGlDiagnostics = () => {
  const browserContextAvailable = hasBrowserWebGlContext()

  let mapLibreReportedSupported = false
  try {
    mapLibreReportedSupported = typeof maplibregl.supported === 'function'
      ? maplibregl.supported()
      : false
  } catch {
    mapLibreReportedSupported = false
  }

  return {
    browserContextAvailable,
    mapLibreReportedSupported,
    isSupported: browserContextAvailable || mapLibreReportedSupported
  }
}

const buildMapClickPayload = (event) => {
  const lat = event?.lngLat?.lat
  const lng = event?.lngLat?.lng

  return {
    ...event,
    latlng: Number.isFinite(lat) && Number.isFinite(lng)
      ? { lat, lng }
      : null,
    originalEvent: event?.originalEvent || null,
    target: map.value
  }
}

const applyCenterAndZoom = (mapInstance) => {
  const center = toLngLatTuple(props.center)
  if (center) {
    mapInstance.jumpTo({
      center,
      zoom: Number.isFinite(props.zoom) ? props.zoom : mapInstance.getZoom()
    })
  } else if (Number.isFinite(props.zoom)) {
    mapInstance.setZoom(props.zoom)
  }
}

const createMapInstance = () => {
  const source = currentStyleSource.value
  const initialCenter = toLngLatTuple(props.center) || [0, 0]

  const options = {
    container: props.mapId,
    style: source.styleUrl,
    center: initialCenter,
    zoom: Number.isFinite(props.zoom) ? props.zoom : 13,
    attributionControl: true,
    ...props.options
  }

  const vectorMap = new maplibregl.Map(options)
  const originalJumpTo = vectorMap.jumpTo.bind(vectorMap)
  appliedStyleUrl.value = source.styleUrl

  // Leaflet-style compatibility surface for existing read-only consumers.
  vectorMap.setView = (center, zoom, setViewOptions = {}) => {
    const targetCenter = toLngLatTuple(center)
    if (!targetCenter) {
      return vectorMap
    }

    const targetZoom = Number.isFinite(zoom) ? zoom : vectorMap.getZoom()
    if (setViewOptions?.animate) {
      vectorMap.easeTo({ center: targetCenter, zoom: targetZoom, ...setViewOptions })
      return vectorMap
    }

    originalJumpTo({ center: targetCenter, zoom: targetZoom })
    return vectorMap
  }

  vectorMap.invalidateSize = () => {
    vectorMap.resize()
    return vectorMap
  }

  vectorMap.addControl(new maplibregl.NavigationControl({ showCompass: true, visualizePitch: true }), 'top-left')

  if (props.enableFullscreen) {
    vectorMap.addControl(new maplibregl.FullscreenControl(props.fullscreenOptions), 'top-left')
  }

  vectorMap.on('load', () => {
    if (!map.value) {
      return
    }

    markMapEngineMode(vectorMap, MAP_RENDER_MODES.VECTOR)
    isReady.value = true
    isInitializing.value = false

    emit('map-ready', vectorMap)

    setupResizeObserver()
    setupVisibilityObserver()

    setTimeout(() => {
      vectorMap.resize()
    }, 50)
  })

  vectorMap.on('click', (event) => {
    emit('map-click', buildMapClickPayload(event))
  })

  vectorMap.on('contextmenu', (event) => {
    event?.originalEvent?.preventDefault?.()
    emit('map-contextmenu', buildMapClickPayload(event))
  })

  vectorMap.on('error', (event) => {
    emit('map-warning', {
      code: 'vector_runtime_error',
      message: 'Vector map rendering reported a runtime error.',
      error: event?.error || event
    })
  })

  return vectorMap
}

const initializeMap = async () => {
  if (isInitializing.value || map.value) {
    return
  }

  const webGlDiagnostics = getWebGlDiagnostics()
  if (!webGlDiagnostics.isSupported) {
    emit('engine-fatal', {
      code: 'webgl_not_supported',
      message: 'WebGL appears unavailable for this session. Falling back to raster tiles.',
      diagnostics: webGlDiagnostics
    })
    return
  }

  if (!webGlDiagnostics.mapLibreReportedSupported && webGlDiagnostics.browserContextAvailable) {
    emit('map-warning', {
      code: 'webgl_partial_support',
      message: 'Browser WebGL is available but MapLibre support check was inconclusive. Trying vector initialization anyway.',
      diagnostics: webGlDiagnostics
    })
  }

  isInitializing.value = true
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
      map.value = markRaw(createMapInstance())
      applyCenterAndZoom(map.value)
    } catch (error) {
      isInitializing.value = false
      map.value = null
      emit('engine-fatal', {
        code: 'vector_engine_init_failed',
        message: 'Vector engine initialization failed. Falling back to raster tiles.',
        error
      })
    }
  }

  setTimeout(tryInit, 50)
}

const invalidateSize = () => {
  if (map.value) {
    map.value.resize()
  }
}

const setView = (center, zoom, options = {}) => {
  if (!map.value) {
    return
  }

  const targetCenter = toLngLatTuple(center)
  if (!targetCenter) {
    return
  }

  const targetZoom = Number.isFinite(zoom) ? zoom : map.value.getZoom()

  if (options?.animate) {
    map.value.easeTo({ center: targetCenter, zoom: targetZoom, ...options })
    return
  }

  map.value.jumpTo({ center: targetCenter, zoom: targetZoom })
}

const fitBounds = (bounds, options = {}) => {
  if (!map.value || !bounds) {
    return
  }

  const normalizedBounds = normalizeLeafletBoundsToMapLibre(bounds)
  if (!normalizedBounds) {
    return
  }

  map.value.fitBounds(normalizedBounds, {
    padding: 20,
    ...options
  })
}

watch(() => props.center, (newCenter, oldCenter) => {
  if (!map.value || !newCenter) {
    return
  }

  const targetZoom = oldCenter ? map.value.getZoom() : props.zoom
  setView(newCenter, targetZoom)
})

watch(() => props.zoom, (newZoom) => {
  if (!map.value || !Number.isFinite(newZoom)) {
    return
  }

  map.value.setZoom(newZoom)
})

watch(
  () => [props.customStyleUrl, props.isSharedView, authStore.customMapStyleUrl],
  ([customStyleUrl, isSharedView, authCustomStyle], [prevCustomStyleUrl, prevIsSharedView, prevAuthCustomStyle]) => {
    if (!map.value) {
      return
    }

    const hasAnyDifference = customStyleUrl !== prevCustomStyleUrl
      || isSharedView !== prevIsSharedView
      || authCustomStyle !== prevAuthCustomStyle

    if (!hasAnyDifference) {
      return
    }

    const source = currentStyleSource.value
    if (source.styleUrl === appliedStyleUrl.value) {
      return
    }

    const center = map.value.getCenter?.()
    const zoom = map.value.getZoom?.()
    const bearing = map.value.getBearing?.()
    const pitch = map.value.getPitch?.()

    const hasCameraState = center
      && Number.isFinite(center.lng)
      && Number.isFinite(center.lat)
      && Number.isFinite(zoom)

    try {
      map.value.setStyle(source.styleUrl, { diff: true })
      appliedStyleUrl.value = source.styleUrl

      if (hasCameraState) {
        map.value.once('style.load', () => {
          if (!map.value) {
            return
          }

          map.value.jumpTo({
            center: [center.lng, center.lat],
            zoom,
            bearing: Number.isFinite(bearing) ? bearing : map.value.getBearing(),
            pitch: Number.isFinite(pitch) ? pitch : map.value.getPitch()
          })
        })
      }
    } catch (error) {
      emit('map-warning', {
        code: 'vector_style_update_failed',
        message: 'Failed to apply updated vector style URL.',
        error
      })
    }
  }
)

defineExpose({
  map,
  isReady,
  invalidateSize,
  setView,
  fitBounds,
  maplibregl
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
            map.value?.resize()
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
                map.value?.resize()
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
