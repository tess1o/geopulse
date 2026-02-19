<template>
  <div class="digest-heatmap">
    <div class="heatmap-header">
      <h3 class="heatmap-title">
        <i class="pi pi-map"></i>
        Location Heatmap
      </h3>
      <div class="heatmap-controls" v-if="!isLoading && !hasError">
        <div class="layer-toggle">
          <button
            :class="['toggle-btn', { active: layerMode === 'stays' }]"
            @click="layerMode = 'stays'"
          >
            <i class="pi pi-home"></i>
            Stays
          </button>
          <button
            :class="['toggle-btn', { active: layerMode === 'trips' }]"
            @click="layerMode = 'trips'"
          >
            <i class="pi pi-directions"></i>
            Trips
          </button>
        </div>
        <div class="intensity-toggle" v-if="layerMode !== 'trips'">
          <button
            :class="['toggle-btn', { active: intensityMode === 'duration' }]"
            @click="intensityMode = 'duration'"
          >
            <i class="pi pi-clock"></i>
            By Duration
          </button>
          <button
            :class="['toggle-btn', { active: intensityMode === 'visits' }]"
            @click="intensityMode = 'visits'"
          >
            <i class="pi pi-refresh"></i>
            By Visits
          </button>
        </div>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="isLoading" class="heatmap-state">
      <i class="pi pi-spin pi-spinner heatmap-state-icon"></i>
      <p>Loading heatmap data…</p>
    </div>

    <!-- Error -->
    <div v-else-if="hasError" class="heatmap-state heatmap-state--error">
      <i class="pi pi-exclamation-triangle heatmap-state-icon"></i>
      <p>Could not load heatmap data.</p>
    </div>

    <!-- Empty -->
    <div v-else-if="!hasData" class="heatmap-state">
      <i class="pi pi-map heatmap-state-icon"></i>
      <p>No location data available for this period.</p>
    </div>

    <!-- Map -->
    <div v-else class="map-wrapper">
      <BaseMap
        ref="baseMapRef"
        :mapId="mapId"
        height="420px"
        width="100%"
        @map-ready="onMapReady"
      />
      <!-- Legend -->
      <div class="heatmap-legend">
        <span class="legend-label">Low</span>
        <div class="legend-gradient"></div>
        <span class="legend-label">High</span>
        <span class="legend-hint">{{ legendHint }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, computed, onUnmounted, nextTick } from 'vue'
import { storeToRefs } from 'pinia'
import L from 'leaflet'
import 'leaflet.heat'
import BaseMap from '@/components/maps/BaseMap.vue'
import { useDigestStore } from '@/stores/digest'

const props = defineProps({
  viewMode: { type: String, default: 'monthly' },
  year:     { type: Number, required: true },
  month:    { type: Number, default: null },
})

// Unique map id to avoid clashes when multiple maps exist in the DOM
const mapId = `digest-heatmap-${Math.random().toString(36).slice(2)}`

const digestStore = useDigestStore()
const { heatmapLoading: isLoading, heatmapError } = storeToRefs(digestStore)

const baseMapRef  = ref(null)
const mapInstance = ref(null)
const intensityMode = ref('duration') // 'duration' | 'visits'
const layerMode = ref('stays') // 'stays' | 'trips'
const lastNonTripIntensity = ref('duration')
const heatPoints    = ref([])
let   heatLayer     = null
let   zoomHandler   = null

const hasError = computed(() => !!heatmapError.value)
const hasData  = computed(() => heatPoints.value.length > 0)
const legendHint = computed(() => {
  if (intensityMode.value === 'duration') {
    return layerMode.value === 'trips' ? 'Time moving' : 'Time spent'
  }
  return 'Visit count'
})

// ─── Data loading ────────────────────────────────────────────────────────────

const loadHeatmap = async () => {
  const activeLayer = layerMode.value
  const dataPromise = digestStore.fetchHeatmapData(
    props.viewMode,
    props.year,
    props.month,
    activeLayer
  )

  // Prefetch the other layer for faster switching (do not await)
  const prefetchLayer = activeLayer === 'stays' ? 'trips' : 'stays'
  digestStore.fetchHeatmapData(
    props.viewMode,
    props.year,
    props.month,
    prefetchLayer
  ).catch(() => {})

  const data = await dataPromise
  heatPoints.value = Array.isArray(data) ? data : []
  if (baseMapRef.value) {
    await nextTick()
    fitMap()
    renderHeatLayer()
  }
}

// ─── Heatmap rendering ───────────────────────────────────────────────────────

/**
 * Safely detach the heat layer from the Leaflet map.
 * Must be called BEFORE BaseMap destroys the Leaflet instance; otherwise
 * leaflet.heat's internal _reset listener fires on a null map reference,
 * causing: "Cannot read properties of null (reading 'containerPointToLayerPoint')"
 */
const removeHeatLayer = () => {
  if (!heatLayer) return
  try {
    if (mapInstance.value) {
      if (zoomHandler) {
        mapInstance.value.off('zoomend', zoomHandler)
        zoomHandler = null
      }
      mapInstance.value.removeLayer(heatLayer)
    }
  } catch (e) {
    // ignore – map may already be gone
  }
  heatLayer = null
}

const buildLeafletPoints = () => {
  if (!heatPoints.value.length) return []

  // Compute max value for normalisation
  const getValue = (p) => intensityMode.value === 'duration' ? p.durationSeconds : p.visits
  const values = heatPoints.value
    .map(getValue)
    .filter((v) => Number.isFinite(v) && v > 0)

  const scaleConfig = {
    combined: { minWeight: 0.04, gamma: 0.6 },
    stays: { minWeight: 0.05, gamma: 0.6 },
    trips: { minWeight: 0.02, gamma: 1.0 }
  }[layerMode.value] || { minWeight: 0.04, gamma: 0.6 }

  const maxVal = Math.max(...values, 1)
  const minWeight = scaleConfig.minWeight
  const gamma = scaleConfig.gamma

  const points = heatPoints.value.map(p => {
    const raw = getValue(p)
    const linear = Math.min(1, raw / maxVal)
    const weight = Math.max(minWeight, Math.pow(linear, gamma))
    return [Number(p.lat), Number(p.lng), weight]
  })

  return points
}

const renderHeatLayer = () => {
  const map = mapInstance.value
  if (!map) return

  try {
    // Detach existing layer safely before adding a new one
    removeHeatLayer()

    const points = buildLeafletPoints()
    if (!points.length) return

    if (!L?.heatLayer) {
      console.warn('DigestHeatmap: leaflet.heat not available')
      return
    }

    if (!map._loaded) {
      const first = heatPoints.value[0]
      if (first) {
        map.setView([Number(first.lat), Number(first.lng)], 12)
      }
    }

    const zoom = map.getZoom()
    const heatStyle = {
      combined: { radius: 32, blur: 24, minOpacity: 0.25, max: 1.0 },
      stays: { radius: 40, blur: 28, minOpacity: 0.3, max: 1.0 },
      trips: { radius: 18, blur: 12, minOpacity: 0.2, max: 1.0 }
    }[layerMode.value] || { radius: 32, blur: 24, minOpacity: 0.25, max: 1.0 }

    heatLayer = L.heatLayer(points, {
      radius:   heatStyle.radius,
      blur:     heatStyle.blur,
      // lock intensity scaling to current zoom (prevents color shifting)
      maxZoom:  zoom,
      max:      heatStyle.max,
      minOpacity: heatStyle.minOpacity,
      gradient: {
        0.0: '#2563eb',   // blue  – low
        0.35: '#22c55e',  // green
        0.6: '#eab308',   // yellow
        0.8: '#f97316',   // orange
        1.0: '#dc2626',   // red   – high
      }
    }).addTo(map)

    // Keep intensity stable across zoom changes
    zoomHandler = () => {
      if (!heatLayer) return
      heatLayer.setOptions({ maxZoom: map.getZoom() })
      heatLayer.redraw()
    }
    map.on('zoomend', zoomHandler)
  } catch (e) {
    console.warn('DigestHeatmap: could not render heat layer', e)
  }
}

const fitMap = () => {
  if (!heatPoints.value.length) return
  const map = mapInstance.value
  if (!map) return

  const bounds = L.latLngBounds(heatPoints.value.map(p => [p.lat, p.lng]))
  map.fitBounds(bounds, { padding: [40, 40], maxZoom: 14 })
}

// ─── Events ──────────────────────────────────────────────────────────────────

const onMapReady = async (map) => {
  mapInstance.value = map
  await nextTick()
  if (heatPoints.value.length) {
    fitMap()
    renderHeatLayer()
  }
}

// Re-render when intensity mode switches
watch(intensityMode, () => {
  if (mapInstance.value) renderHeatLayer()
})

watch(layerMode, (nextLayer, prevLayer) => {
  if (prevLayer !== 'trips') {
    lastNonTripIntensity.value = intensityMode.value
  }

  if (nextLayer === 'trips') {
    intensityMode.value = 'duration'
  } else if (prevLayer === 'trips' && lastNonTripIntensity.value) {
    intensityMode.value = lastNonTripIntensity.value
  }
})

// Reload when the parent changes period
watch(
  () => [props.viewMode, props.year, props.month, layerMode.value],
  () => loadHeatmap(),
  { immediate: true }
)

// Cleanup
onUnmounted(() => {
  removeHeatLayer()
})
</script>

<style scoped>
.digest-heatmap {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-large);
  padding: var(--gp-spacing-xl);
  margin-bottom: var(--gp-spacing-xl);
}

/* ── Header ── */
.heatmap-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--gp-spacing-md);
  margin-bottom: var(--gp-spacing-lg);
}

.heatmap-title {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  margin: 0;
}

.heatmap-title i {
  color: var(--gp-primary);
}

/* ── Toggle ── */
.heatmap-controls {
  display: flex;
  gap: var(--gp-spacing-sm);
  align-items: center;
  flex-wrap: wrap;
}

.layer-toggle,
.intensity-toggle {
  display: flex;
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  overflow: hidden;
}

.toggle-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  font-size: 0.8125rem;
  font-weight: 500;
  color: var(--gp-text-secondary);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.toggle-btn.active {
  background: var(--gp-primary);
  color: #fff;
}

.toggle-btn:not(.active):hover {
  background: var(--gp-surface-hover, rgba(0,0,0,.05));
}

/* ── States ── */
.heatmap-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--gp-spacing-xxl) var(--gp-spacing-xl);
  text-align: center;
  color: var(--gp-text-muted);
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  min-height: 200px;
}

.heatmap-state--error {
  color: var(--gp-error);
}

.heatmap-state-icon {
  font-size: 2.5rem;
  opacity: 0.5;
  margin-bottom: var(--gp-spacing-md);
}

.heatmap-state p {
  margin: 0;
  font-size: 0.9375rem;
  font-style: italic;
}

/* ── Map wrapper & legend ── */
.map-wrapper {
  position: relative;
  border-radius: var(--gp-radius-medium);
  overflow: hidden;
}

.heatmap-legend {
  position: absolute;
  bottom: 12px;
  right: 12px;
  z-index: 1000;
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(6px);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  padding: 6px 12px;
  font-size: 0.75rem;
  color: var(--gp-text-secondary);
  flex-wrap: wrap;
}

.legend-gradient {
  width: 80px;
  height: 10px;
  border-radius: 5px;
  background: linear-gradient(to right, #3b82f6, #22c55e, #f59e0b, #ef4444);
}

.legend-hint {
  font-style: italic;
  opacity: 0.7;
  margin-left: 4px;
}

/* ── Dark mode ── */
.p-dark .digest-heatmap {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark .intensity-toggle {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

.p-dark .heatmap-state {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

.p-dark .heatmap-legend {
  background: rgba(30, 30, 40, 0.92);
  border-color: var(--gp-border-dark);
  color: var(--gp-text-secondary);
}

.p-dark .heatmap-title {
  color: var(--gp-text-primary);
}

/* ── Responsive ── */
@media (max-width: 768px) {
  .heatmap-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
