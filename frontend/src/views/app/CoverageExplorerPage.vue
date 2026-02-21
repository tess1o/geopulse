<template>
  <AppLayout variant="default">
    <PageContainer
        title="Coverage Explorer"
        subtitle="Lifetime map coverage from your GPS history. Zoom in to see which streets and blocks you have already explored."
        maxWidth="none"
        padding="large"
    >
      <template #actions>
        <div class="action-controls">
          <div class="grid-control">
            <label for="coverage-grid" class="control-label">Grid</label>
            <Dropdown
                input-id="coverage-grid"
                v-model="selectedGrid"
                :options="gridOptions"
                optionLabel="label"
                optionValue="value"
                class="grid-dropdown"
                aria-label="Coverage grid resolution"
            />
          </div>
          <div class="coverage-toggle">
            <label for="coverage-toggle" class="control-label">Coverage</label>
            <div class="toggle-row">
              <InputSwitch
                inputId="coverage-toggle"
                v-model="userCoverageEnabled"
                :disabled="!canToggleCoverage"
                @change="handleCoverageToggle"
                aria-label="Enable or disable coverage processing"
              />
              <span class="toggle-text">{{ coverageToggleLabel }}</span>
            </div>
          </div>
        </div>
      </template>

      <div class="coverage-page">

      <div class="coverage-stats">
        <BaseCard title="Seen Area" class="coverage-stat-card">
          <div class="stat-value">
            <span v-if="summaryLoading">Loading...</span>
            <span v-else>{{ formattedArea }}</span>
          </div>
          <div class="stat-subtext">
            <span v-if="summaryLoading">Calculating from covered cells</span>
            <span v-else-if="!coverageAllowed">Enable coverage to calculate</span>
            <span v-else>{{ formattedCells }} ({{ summaryGrid }} m grid)</span>
          </div>
        </BaseCard>
        <BaseCard title="Resolution" class="coverage-stat-card">
          <div class="stat-value">{{ effectiveGrid }} m</div>
          <div class="stat-subtext">{{ gridModeLabel }}</div>
        </BaseCard>
        <BaseCard title="Coverage Style" class="coverage-stat-card">
          <div class="stat-value">{{ radiusLabel }}</div>
          <div class="stat-subtext">Road corridor radius</div>
        </BaseCard>
      </div>

        <div class="coverage-map-card">
        <div class="map-header">
          <div class="map-title">Coverage Map</div>
          <div class="map-meta">
            <span v-if="statusLoading">Checking status...</span>
            <span v-else-if="statusErrorMessage">{{ statusErrorMessage }}</span>
            <span v-else-if="!userEnabled">Coverage not enabled</span>
            <span v-else-if="processing">Calculating...</span>
            <span v-else-if="cellsErrorMessage">{{ cellsErrorMessage }}</span>
            <span v-else-if="showCoverageUpdatingMeta">Updating...</span>
            <span v-else-if="showNoCoverageDataYet">No coverage data yet</span>
            <span v-else-if="showNoCoverageInView">No coverage in current view</span>
            <span v-else>{{ coverageCells.length.toLocaleString() }} cells in current view</span>
          </div>
        </div>
        <div class="map-container">
          <MapContainer
              map-id="coverage-map"
              :center="mapCenter"
              :zoom="mapZoom"
              :show-controls="false"
              @map-ready="handleMapReady"
          >
            <template #overlays="{ map, isReady }">
              <CoverageLayer
                  v-if="map && isReady"
                  :map="map"
                  :cells="coverageCells"
                  :grid-meters="effectiveGrid"
                  :visible="true"
              />
            </template>
          </MapContainer>

          <div v-if="statusLoading" class="map-overlay">
            <div class="map-overlay-content">
              <ProgressSpinner style="width: 40px; height: 40px" strokeWidth="4"/>
              <span>Checking coverage status</span>
            </div>
          </div>

          <div v-else-if="!userEnabled" class="map-overlay map-empty">
            <div class="map-overlay-content">
              <i class="pi pi-power-off empty-icon"></i>
              <div>
                <strong>Coverage is off</strong>
                <p>Enable coverage to start building your exploration map.</p>
              </div>
              <Button
                label="Enable Coverage"
                icon="pi pi-power-off"
                class="overlay-enable-button"
                :disabled="!canToggleCoverage || settingsUpdating"
                @click="enableCoverageFromOverlay"
              />
            </div>
          </div>

          <div v-else-if="processing" class="map-overlay">
            <div class="map-overlay-content">
              <ProgressSpinner style="width: 40px; height: 40px" strokeWidth="4"/>
              <span>Calculating coverage</span>
            </div>
          </div>

          <div v-else-if="showCoverageLoadingOverlay" class="map-overlay">
            <div class="map-overlay-content">
              <ProgressSpinner style="width: 40px; height: 40px" strokeWidth="4"/>
              <span>Loading coverage</span>
            </div>
          </div>

          <div v-else-if="showNoCoverageDataYet" class="map-overlay map-empty">
            <div class="map-overlay-content">
              <i class="pi pi-map empty-icon"></i>
              <div>
                <strong>No coverage data yet</strong>
                <p>Coverage will appear once the background job processes your GPS points.</p>
              </div>
            </div>
          </div>

          <div v-else-if="showNoCoverageInView" class="map-overlay map-empty">
            <div class="map-overlay-content">
              <i class="pi pi-map empty-icon"></i>
              <div>
                <strong>No coverage in current view</strong>
                <p>Try panning or zooming to an area you have already explored.</p>
              </div>
            </div>
          </div>
        </div>
        <div class="map-legend">
          <span class="legend-chip"></span>
          <span>Explored area (opacity shows repeat visits)</span>
        </div>
      </div>
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {storeToRefs} from 'pinia'
import Button from 'primevue/button'
import Dropdown from 'primevue/dropdown'
import InputSwitch from 'primevue/inputswitch'
import ProgressSpinner from 'primevue/progressspinner'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import {MapContainer, CoverageLayer} from '@/components/maps'
import {useCoverageStore} from '@/stores/coverage'
import {useLocationStore} from '@/stores/location'

import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'

const coverageStore = useCoverageStore()
const locationStore = useLocationStore()

const mapInstance = ref(null)
const mapCenter = ref([51.505, -0.09])
const mapZoom = ref(10)

const gridOptions = [
  {label: 'Auto (based on zoom)', value: 'auto'},
  {label: '20 m grid (street)', value: 20},
  {label: '50 m grid (local)', value: 50},
  {label: '250 m grid (city)', value: 250},
  {label: '1 km grid (regional)', value: 1000},
  {label: '5 km grid (country)', value: 5000},
  {label: '20 km grid (continent)', value: 20000},
  {label: '40 km grid (global)', value: 40000}
]
const selectedGrid = ref('auto')

const {
  cells: storeCells,
  loadingCells,
  cellsError,
  status,
  statusLoading,
  statusError,
  settingsUpdating
} = storeToRefs(coverageStore)
const coverageCells = computed(() => storeCells.value || [])
const coverageLoading = computed(() => loadingCells.value)
const coverageStatus = computed(() => status.value)
const userEnabled = computed(() => coverageStatus.value?.userEnabled ?? false)
const processing = computed(() => coverageStatus.value?.processing ?? false)
const hasCoverageHistory = computed(() => coverageStatus.value?.hasCells ?? false)
const statusReady = computed(() => status.value !== null)
const coverageAllowed = computed(() => userEnabled.value)
const statusErrorMessage = computed(() => statusError.value ? 'Coverage status unavailable' : '')
const cellsErrorMessage = computed(() => cellsError.value ? 'Failed to refresh current view' : '')

const userCoverageEnabled = ref(false)

const summary = ref(null)
const summaryLoading = ref(false)
const summaryGrid = 20

const radiusLabel = computed(() => '20 m')
const gridModeLabel = computed(() => selectedGrid.value === 'auto'
    ? `Auto (zoom ${mapZoom.value})`
    : 'Manual selection')
const coverageToggleLabel = computed(() => {
  if (statusLoading.value) return 'Loading...'
  if (settingsUpdating.value) return 'Updating...'
  return userEnabled.value ? 'Enabled' : 'Disabled'
})
const canToggleCoverage = computed(() =>
  statusReady.value && !settingsUpdating.value && !processing.value
)

const getGridForZoom = (zoom) => {
  if (zoom <= 3) return 40000
  if (zoom <= 5) return 20000
  if (zoom <= 7) return 5000
  if (zoom <= 10) return 1000
  if (zoom <= 12) return 250
  if (zoom <= 14) return 50
  return 20
}

const effectiveGrid = computed(() => {
  if (selectedGrid.value === 'auto') {
    return getGridForZoom(mapZoom.value)
  }
  return selectedGrid.value
})

let fetchTimer = null
let mapMoveHandler = null
let lastRequestKey = ''
let fetchRequestId = 0
let coverageOverlayTimer = null
let coverageMetaTimer = null
let summaryRefreshTimer = null
const showCoverageLoadingOverlay = ref(false)
const showCoverageUpdatingMeta = ref(false)

const showNoCoverageDataYet = computed(() =>
  coverageAllowed.value
  && !processing.value
  && !coverageLoading.value
  && coverageCells.value.length === 0
  && !hasCoverageHistory.value
)

const showNoCoverageInView = computed(() =>
  coverageAllowed.value
  && !processing.value
  && !coverageLoading.value
  && coverageCells.value.length === 0
  && hasCoverageHistory.value
)

const formatNumber = (value, digits = 1) => {
  if (!Number.isFinite(value)) return '0'
  return value.toLocaleString(undefined, {maximumFractionDigits: digits})
}

const formattedArea = computed(() => {
  if (!coverageAllowed.value) return '—'
  if (!summary.value) return '0 km^2'
  return `${formatNumber(summary.value.areaSquareKm)} km^2`
})

const formattedCells = computed(() => {
  if (!coverageAllowed.value) return '—'
  if (!summary.value) return '0 cells'
  return `${summary.value.totalCells.toLocaleString()} cells`
})

const getBboxFromMap = () => {
  if (!mapInstance.value) return null
  const bounds = mapInstance.value.getBounds()
  const round = (value) => Math.round(value * 10000) / 10000
  const clamp = (value, min, max) => Math.min(max, Math.max(min, value))

  let west = bounds.getWest()
  let east = bounds.getEast()
  let south = bounds.getSouth()
  let north = bounds.getNorth()

  if (east - west >= 360) {
    west = -180
    east = 180
  } else {
    west = clamp(west, -180, 180)
    east = clamp(east, -180, 180)
  }

  south = clamp(south, -90, 90)
  north = clamp(north, -90, 90)

  return [
    round(west),
    round(south),
    round(east),
    round(north)
  ].join(',')
}

const fetchCoverage = async () => {
  if (!coverageAllowed.value || processing.value) return
  const bbox = getBboxFromMap()
  if (!bbox) return

  const requestKey = `${effectiveGrid.value}:${bbox}`
  if (requestKey === lastRequestKey) return
  lastRequestKey = requestKey

  const requestId = ++fetchRequestId
  const cells = await coverageStore.fetchCoverageCells(bbox, effectiveGrid.value)
  if (requestId !== fetchRequestId) return
  if (cells === null) {
    lastRequestKey = ''
    return
  }
}

const scheduleFetch = () => {
  if (fetchTimer) {
    clearTimeout(fetchTimer)
  }
  fetchTimer = setTimeout(fetchCoverage, 350)
}

let statusPollTimer = null

const loadStatus = async (options = {}) => {
  const data = await coverageStore.fetchCoverageStatus(options)
  if (data?.processing) {
    startStatusPolling()
  }
  return data
}

const startStatusPolling = () => {
  if (statusPollTimer) return
  statusPollTimer = setInterval(async () => {
    const data = await coverageStore.fetchCoverageStatus({silent: true})
    if (!data) {
      return
    }
    if (!data.processing) {
      stopStatusPolling()
      if (coverageAllowed.value) {
        coverageStore.invalidateSummary(summaryGrid)
        await loadSummary()
        lastRequestKey = ''
        scheduleFetch()
      }
    }
  }, 5000)
}

const stopStatusPolling = () => {
  if (!statusPollTimer) return
  clearInterval(statusPollTimer)
  statusPollTimer = null
}

const startSummaryRefresh = () => {
  if (summaryRefreshTimer) return
  summaryRefreshTimer = setInterval(() => {
    if (!coverageAllowed.value || processing.value || summaryLoading.value) {
      return
    }
    coverageStore.invalidateSummary(summaryGrid)
    loadSummary()
  }, 60000)
}

const stopSummaryRefresh = () => {
  if (!summaryRefreshTimer) return
  clearInterval(summaryRefreshTimer)
  summaryRefreshTimer = null
}

const handleCoverageToggle = async () => {
  if (!statusReady.value || settingsUpdating.value) {
    return
  }
  const desired = userCoverageEnabled.value
  try {
    const updated = await coverageStore.updateCoverageSettings(desired)
    if (!updated) return

    if (!desired) {
      stopStatusPolling()
      stopSummaryRefresh()
      coverageStore.cancelCoverageCellsRequests()
      coverageStore.clearCells()
      coverageStore.invalidateSummary(summaryGrid)
      summary.value = null
      lastRequestKey = ''
      fetchRequestId += 1
      return
    }

    if (updated.processing) {
      startStatusPolling()
      return
    }

    coverageStore.invalidateSummary(summaryGrid)
    await loadSummary()
    startSummaryRefresh()
    lastRequestKey = ''
    scheduleFetch()
  } catch (error) {
    userCoverageEnabled.value = !desired
    console.error('Failed to update coverage settings:', error)
  }
}

const enableCoverageFromOverlay = async () => {
  if (!canToggleCoverage.value || userCoverageEnabled.value) {
    return
  }
  userCoverageEnabled.value = true
  await handleCoverageToggle()
}

const loadSummary = async () => {
  if (!coverageAllowed.value) {
    summary.value = null
    summaryLoading.value = false
    return
  }
  summaryLoading.value = true
  summary.value = await coverageStore.fetchCoverageSummary(summaryGrid)
  summaryLoading.value = false
}

const handleMapReady = async (map) => {
  mapInstance.value = map

  mapMoveHandler = () => {
    mapZoom.value = map.getZoom()
    scheduleFetch()
  }
  map.on('moveend', mapMoveHandler)
  map.on('zoomend', mapMoveHandler)

  try {
    const lastPosition = await locationStore.getLastKnownPosition()
    if (lastPosition?.lat && lastPosition?.lon) {
      map.setView([lastPosition.lat, lastPosition.lon], 12, {animate: false})
    }
  } catch (error) {
    console.warn('Failed to load last known position:', error)
  }

  mapMoveHandler()
}

watch(effectiveGrid, () => {
  scheduleFetch()
})

watch(status, (value) => {
  if (!value) return
  userCoverageEnabled.value = value.userEnabled
})

watch(coverageAllowed, (allowed) => {
  if (!allowed) {
    stopSummaryRefresh()
    coverageStore.cancelCoverageCellsRequests()
    coverageStore.clearCells()
    coverageStore.invalidateSummary(summaryGrid)
    summary.value = null
    summaryLoading.value = false
    lastRequestKey = ''
    fetchRequestId += 1
    return
  }
  loadSummary()
  startSummaryRefresh()
  scheduleFetch()
})

watch(processing, (isProcessing) => {
  if (isProcessing) {
    stopSummaryRefresh()
    startStatusPolling()
  } else {
    stopStatusPolling()
    if (coverageAllowed.value) {
      startSummaryRefresh()
    }
  }
})

watch(() => [coverageLoading.value, coverageCells.value.length], ([isLoading, cellCount]) => {
  if (!isLoading) {
    if (coverageOverlayTimer) {
      clearTimeout(coverageOverlayTimer)
      coverageOverlayTimer = null
    }
    if (coverageMetaTimer) {
      clearTimeout(coverageMetaTimer)
      coverageMetaTimer = null
    }
    showCoverageLoadingOverlay.value = false
    showCoverageUpdatingMeta.value = false
    return
  }

  // Keep existing cells visible while refreshing to avoid map flicker.
  if (cellCount > 0) {
    if (coverageOverlayTimer) {
      clearTimeout(coverageOverlayTimer)
      coverageOverlayTimer = null
    }
    showCoverageLoadingOverlay.value = false
    if (coverageMetaTimer) {
      clearTimeout(coverageMetaTimer)
    }
    coverageMetaTimer = setTimeout(() => {
      if (coverageLoading.value && coverageCells.value.length > 0) {
        showCoverageUpdatingMeta.value = true
      }
    }, 250)
    return
  }

  if (coverageOverlayTimer) {
    clearTimeout(coverageOverlayTimer)
  }
  coverageOverlayTimer = setTimeout(() => {
    if (coverageLoading.value && coverageCells.value.length === 0) {
      showCoverageLoadingOverlay.value = true
    }
  }, 300)
  showCoverageUpdatingMeta.value = false
})

onMounted(() => {
  loadStatus().then(() => {
    if (coverageAllowed.value) {
      loadSummary()
      startSummaryRefresh()
    }
  })
})

onBeforeUnmount(() => {
  if (fetchTimer) {
    clearTimeout(fetchTimer)
    fetchTimer = null
  }
  if (coverageOverlayTimer) {
    clearTimeout(coverageOverlayTimer)
    coverageOverlayTimer = null
  }
  if (coverageMetaTimer) {
    clearTimeout(coverageMetaTimer)
    coverageMetaTimer = null
  }
  showCoverageLoadingOverlay.value = false
  showCoverageUpdatingMeta.value = false
  stopSummaryRefresh()
  coverageStore.cancelCoverageCellsRequests()
  fetchRequestId += 1
  stopStatusPolling()
  if (mapInstance.value && mapMoveHandler) {
    mapInstance.value.off('moveend', mapMoveHandler)
    mapInstance.value.off('zoomend', mapMoveHandler)
  }
})
</script>

<style scoped>
.coverage-page {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.action-controls {
  display: flex;
  gap: 1rem;
  align-items: flex-start;
  flex-wrap: wrap;
}

.grid-control {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.coverage-toggle {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  min-width: 160px;
}

.toggle-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-height: 2rem;
}

.toggle-text {
  font-size: 0.85rem;
  color: var(--gp-text-secondary);
}

.control-label {
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--gp-text-muted);
}

.grid-dropdown {
  min-width: 220px;
}

.coverage-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1rem;
}

.stat-value {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--gp-text-primary);
}

.stat-subtext {
  margin-top: 0.35rem;
  color: var(--gp-text-secondary);
  font-size: 0.85rem;
}

.coverage-map-card {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-large);
  box-shadow: var(--gp-shadow-card);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.map-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 1.5rem;
  border-bottom: 1px solid var(--gp-border-light);
  background: var(--gp-surface-light);
}

.map-title {
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-size: 0.85rem;
  color: var(--gp-text-primary);
}

.map-meta {
  color: var(--gp-text-muted);
  font-size: 0.85rem;
}

.map-container {
  position: relative;
  height: 70vh;
  min-height: 420px;
}

.map-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.78);
  color: #0f172a;
  font-weight: 500;
  z-index: 500;
  pointer-events: none;
}

.map-overlay-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 1rem 1.25rem;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(15, 23, 42, 0.16);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.18);
  max-width: min(92%, 520px);
  pointer-events: auto;
}

.map-overlay.map-empty {
  text-align: center;
  padding: 2rem;
}

.map-overlay .empty-icon {
  font-size: 2rem;
  color: #334155;
}

.map-overlay p {
  margin: 0.35rem 0 0;
  color: #334155;
}

.map-overlay strong {
  color: #0f172a;
}

:global(.p-dark) .map-overlay {
  background: rgba(2, 6, 23, 0.74);
  color: #f8fafc;
}

:global(.p-dark) .map-overlay-content {
  background: rgba(15, 23, 42, 0.9);
  border-color: rgba(148, 163, 184, 0.35);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.45);
}

:global(.p-dark) .map-overlay .empty-icon {
  color: #cbd5e1;
}

:global(.p-dark) .map-overlay p {
  color: #cbd5e1;
}

:global(.p-dark) .map-overlay strong,
:global(.p-dark) .map-overlay span {
  color: #f8fafc;
}

.overlay-enable-button {
  margin-top: 0.5rem;
  min-width: 190px;
}

.map-legend {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1.5rem;
  font-size: 0.85rem;
  color: var(--gp-text-secondary);
  border-top: 1px solid var(--gp-border-light);
  background: var(--gp-surface-light);
}

.legend-chip {
  width: 16px;
  height: 16px;
  border-radius: 4px;
  background: #3b82f6;
  opacity: 0.6;
  border: 1px solid rgba(59, 130, 246, 0.4);
}

@media (max-width: 900px) {
  .action-controls {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0.75rem;
    align-items: end;
    width: 100%;
  }

  .grid-dropdown {
    min-width: 100%;
  }

  .grid-control,
  .coverage-toggle {
    width: 100%;
    min-width: 0;
  }
}

@media (max-width: 600px) {
  .coverage-page {
    gap: 1rem;
  }

  .coverage-stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 0.45rem;
    margin: 0;
  }

  .coverage-stat-card {
    min-width: 0;
  }

  .coverage-stat-card :deep(.gp-card-header) {
    padding: 0.4rem 0.55rem;
  }

  .coverage-stat-card :deep(.gp-card-content) {
    padding: 0.55rem;
  }

  .coverage-stat-card :deep(.gp-card-title) {
    font-size: 0.62rem;
    letter-spacing: 0.04em;
  }

  .stat-value {
    font-size: clamp(0.95rem, 4.2vw, 1.1rem);
    line-height: 1.1;
  }

  .stat-subtext {
    margin-top: 0.2rem;
    font-size: 0.68rem;
    line-height: 1.25;
  }

  .map-header {
    padding: 0.75rem 1rem;
    flex-direction: column;
    align-items: flex-start;
    gap: 0.25rem;
  }

  .map-meta {
    font-size: 0.8rem;
  }

  .map-container {
    height: 52vh;
    min-height: 300px;
  }

  .map-legend {
    padding: 0.65rem 1rem;
    font-size: 0.8rem;
  }
}

@media (max-width: 420px) {
  .action-controls {
    gap: 0.6rem;
  }

  .toggle-text {
    font-size: 0.8rem;
  }
}
</style>
