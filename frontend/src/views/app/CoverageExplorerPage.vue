<template>
  <AppLayout variant="default">
    <ConfirmDialog />
    <PageContainer
        :title="t('analytics.coverageExplorer.title')"
        :subtitle="t('analytics.coverageExplorer.subtitle')"
        maxWidth="none"
        padding="large"
    >
      <div class="coverage-toolbar" :aria-label="t('analytics.coverageExplorer.controlsAriaLabel')">
        <div class="grid-control">
          <label for="coverage-grid" class="control-label">{{ t('analytics.coverageExplorer.gridLabel') }}</label>
          <Dropdown
              input-id="coverage-grid"
              v-model="selectedGrid"
              :options="gridOptions"
              optionLabel="label"
              optionValue="value"
              class="grid-dropdown"
              :aria-label="t('analytics.coverageExplorer.gridAriaLabel')"
          />
        </div>
        <div class="coverage-actions">
          <div class="coverage-toggle">
            <label for="coverage-toggle" class="control-label">{{ t('analytics.coverageExplorer.coverageLabel') }}</label>
            <div class="toggle-row">
              <InputSwitch
                inputId="coverage-toggle"
                v-model="userCoverageEnabled"
                :disabled="!canToggleCoverage"
                v-tooltip.bottom="demoReadOnly ? t('analytics.coverageExplorer.toggleTooltipDemoDisabled') : t('analytics.coverageExplorer.toggleTooltip')"
                @change="handleCoverageToggle"
                :aria-label="t('analytics.coverageExplorer.toggleAriaLabel')"
              />
              <span class="toggle-text">{{ coverageToggleLabel }}</span>
            </div>
          </div>
          <Button
            v-if="userEnabled"
            :label="t('analytics.coverageExplorer.recalculate')"
            icon="pi pi-refresh"
            severity="secondary"
            outlined
            class="coverage-recalculate"
            :disabled="!canRecalculateCoverage"
            :loading="settingsUpdating && !statusLoading"
            v-tooltip.bottom="demoReadOnly ? t('analytics.coverageExplorer.recalculateTooltipDemoDisabled') : t('analytics.coverageExplorer.recalculateTooltip')"
            @click="confirmCoverageRecalculation"
          />
        </div>
        <div class="seen-area-summary" aria-live="polite">
          <i class="pi pi-map" aria-hidden="true"></i>
          <div>
            <span class="control-label">{{ t('analytics.coverageExplorer.seenArea') }}</span>
            <strong>{{ summaryLoading ? t('analytics.coverageExplorer.loadingEllipsis') : formattedArea }}</strong>
          </div>
        </div>
      </div>

      <Message v-if="demoReadOnly" severity="error" :closable="false" class="demo-read-only-message">
        {{ t('analytics.coverageExplorer.demoReadOnlyMessage') }}
      </Message>

      <Message v-if="coverageActionError" severity="error" :closable="false" class="coverage-action-error">
        {{ coverageActionError }}
      </Message>

      <div class="coverage-page">
        <div class="coverage-map-card">
        <div class="map-header">
          <div class="map-title">{{ t('analytics.coverageExplorer.coverageMap') }}</div>
          <div class="map-meta">
            <span v-if="statusLoading">{{ t('analytics.coverageExplorer.checkingStatus') }}</span>
            <span v-else-if="statusErrorMessage">{{ statusErrorMessage }}</span>
            <span v-else-if="!userEnabled">{{ t('analytics.coverageExplorer.coverageNotEnabled') }}</span>
            <span v-else-if="processing">{{ t('analytics.coverageExplorer.calculating') }}</span>
            <span v-else-if="cellsErrorMessage">{{ cellsErrorMessage }}</span>
            <span v-else-if="showCoverageUpdatingMeta">{{ t('analytics.coverageExplorer.updatingMeta') }}</span>
            <span v-else-if="showNoCoverageDataYet">{{ t('analytics.coverageExplorer.noCoverageDataYet') }}</span>
            <span v-else-if="showNoCoverageInView">{{ t('analytics.coverageExplorer.noCoverageInView') }}</span>
            <span v-else>{{ t('analytics.coverageExplorer.cellsInView', { count: coverageCells.length.toLocaleString() }) }}</span>
          </div>
        </div>
        <div class="map-container">
          <MapContainer
              ref="mapContainerRef"
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
              <span>{{ t('analytics.coverageExplorer.checkingCoverageStatus') }}</span>
            </div>
          </div>

          <div v-else-if="!userEnabled" class="map-overlay map-empty">
            <div class="map-overlay-content">
              <i class="pi pi-power-off empty-icon"></i>
              <div>
                <strong>{{ t('analytics.coverageExplorer.coverageIsOff') }}</strong>
                <p>{{ t('analytics.coverageExplorer.enableCoverageHint') }}</p>
              </div>
              <Button
                :label="t('analytics.coverageExplorer.enableCoverage')"
                icon="pi pi-power-off"
                class="overlay-enable-button"
                :disabled="!canToggleCoverage || settingsUpdating"
                v-tooltip.bottom="demoReadOnly ? t('analytics.coverageExplorer.enableTooltipDemoDisabled') : t('analytics.coverageExplorer.enableTooltip')"
                @click="enableCoverageFromOverlay"
              />
            </div>
          </div>

          <div v-else-if="processing" class="map-overlay">
            <div class="map-overlay-content">
              <ProgressSpinner style="width: 40px; height: 40px" strokeWidth="4"/>
              <span>{{ t('analytics.coverageExplorer.calculatingCoverage') }}</span>
            </div>
          </div>

          <div v-else-if="showCoverageLoadingOverlay" class="map-overlay">
            <div class="map-overlay-content">
              <ProgressSpinner style="width: 40px; height: 40px" strokeWidth="4"/>
              <span>{{ t('analytics.coverageExplorer.loadingCoverage') }}</span>
            </div>
          </div>

          <div v-else-if="showNoCoverageDataYet" class="map-overlay map-empty">
            <div class="map-overlay-content">
              <i class="pi pi-map empty-icon"></i>
              <div>
                <strong>{{ t('analytics.coverageExplorer.noCoverageDataYet') }}</strong>
                <p>{{ t('analytics.coverageExplorer.noCoverageDataYetHint') }}</p>
              </div>
            </div>
          </div>

          <div v-else-if="showNoCoverageInView" class="map-overlay map-empty">
            <div class="map-overlay-content">
              <i class="pi pi-map empty-icon"></i>
              <div>
                <strong>{{ t('analytics.coverageExplorer.noCoverageInView') }}</strong>
                <p>{{ t('analytics.coverageExplorer.noCoverageInViewHint') }}</p>
              </div>
            </div>
          </div>
        </div>
        <div class="map-legend">
          <span class="legend-chip"></span>
          <span>{{ t('analytics.coverageExplorer.legendText') }}</span>
        </div>
      </div>
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {storeToRefs} from 'pinia'
import Button from 'primevue/button'
import ConfirmDialog from 'primevue/confirmdialog'
import Dropdown from 'primevue/dropdown'
import InputSwitch from 'primevue/inputswitch'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import {useConfirm} from 'primevue/useconfirm'
import {useToast} from 'primevue/usetoast'
import {MapContainer, CoverageLayer} from '@/components/maps'
import {useAuthStore} from '@/stores/auth'
import {useCoverageStore} from '@/stores/coverage'
import {useLocationStore} from '@/stores/location'
import {showDemoModeToast} from '@/utils/demoMode'
import {formatApiErrorDetail} from '@/utils/apiErrorDetail'

import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'

const {t} = useI18n()
const coverageStore = useCoverageStore()
const authStore = useAuthStore()
const locationStore = useLocationStore()
const confirm = useConfirm()
const toast = useToast()

const mapContainerRef = ref(null)
const mapInstance = ref(null)
const mapCenter = ref([51.505, -0.09])
const mapZoom = ref(10)

const gridOptions = computed(() => [
  {label: t('analytics.coverageExplorer.gridAuto'), value: 'auto'},
  {label: t('analytics.coverageExplorer.grid20m'), value: 20},
  {label: t('analytics.coverageExplorer.grid50m'), value: 50},
  {label: t('analytics.coverageExplorer.grid250m'), value: 250},
  {label: t('analytics.coverageExplorer.grid1km'), value: 1000},
  {label: t('analytics.coverageExplorer.grid5km'), value: 5000},
  {label: t('analytics.coverageExplorer.grid20km'), value: 20000},
  {label: t('analytics.coverageExplorer.grid40km'), value: 40000}
])
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
const statusErrorMessage = computed(() => statusError.value ? t('analytics.coverageExplorer.statusUnavailable') : '')
const cellsErrorMessage = computed(() => cellsError.value ? t('analytics.coverageExplorer.refreshFailed') : '')
const demoReadOnly = computed(() => authStore.demoReadOnly)

const userCoverageEnabled = ref(false)
const coverageActionError = ref('')

const summary = ref(null)
const summaryLoading = ref(false)
const summaryGrid = 20

const coverageToggleLabel = computed(() => {
  if (statusLoading.value) return t('analytics.coverageExplorer.toggleLoading')
  if (settingsUpdating.value) return t('analytics.coverageExplorer.toggleUpdating')
  if (demoReadOnly.value) return userEnabled.value ? t('analytics.coverageExplorer.toggleEnabledReadOnly') : t('analytics.coverageExplorer.toggleDisabledReadOnly')
  return userEnabled.value ? t('analytics.coverageExplorer.toggleEnabled') : t('analytics.coverageExplorer.toggleDisabled')
})
const canToggleCoverage = computed(() =>
  statusReady.value && !settingsUpdating.value && !processing.value && !demoReadOnly.value
)
const canRecalculateCoverage = computed(() =>
  statusReady.value
  && userEnabled.value
  && !settingsUpdating.value
  && !processing.value
  && !statusLoading.value
  && !demoReadOnly.value
)

const showDemoCoverageReadOnlyToast = () => {
  showDemoModeToast(toast, t('analytics.coverageExplorer.demoReadOnlyToast'))
}

const getGridForZoom = (zoom) => {
  if (zoom <= 4) return 40000
  if (zoom <= 6) return 20000
  if (zoom <= 8) return 5000
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
  if (!coverageAllowed.value) return t('analytics.coverageExplorer.areaUnavailable')
  if (!summary.value) return t('analytics.coverageExplorer.areaZero')
  return t('analytics.coverageExplorer.areaValue', { value: formatNumber(summary.value.areaSquareKm) })
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
  if (demoReadOnly.value) {
    userCoverageEnabled.value = userEnabled.value
    showDemoCoverageReadOnlyToast()
    return
  }

  if (!statusReady.value || settingsUpdating.value) {
    userCoverageEnabled.value = userEnabled.value
    return
  }
  const desired = userCoverageEnabled.value
  coverageActionError.value = ''
  try {
    const updated = await coverageStore.updateCoverageSettings(desired)
    if (!updated) {
      userCoverageEnabled.value = userEnabled.value
      return
    }

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
    userCoverageEnabled.value = userEnabled.value
    const detail = formatApiErrorDetail(error, t('analytics.coverageExplorer.updateSettingsFailed'))
    coverageActionError.value = detail
    toast.add({
      severity: 'error',
      summary: t('analytics.coverageExplorer.settingsErrorSummary'),
      detail,
      life: 5000
    })
    console.error('Failed to update coverage settings:', error)
  }
}

const enableCoverageFromOverlay = async () => {
  if (demoReadOnly.value) {
    showDemoCoverageReadOnlyToast()
    return
  }

  if (!canToggleCoverage.value || userCoverageEnabled.value) {
    return
  }
  userCoverageEnabled.value = true
  await handleCoverageToggle()
}

const handleCoverageRecalculation = async () => {
  if (demoReadOnly.value) {
    showDemoCoverageReadOnlyToast()
    return
  }

  if (!canRecalculateCoverage.value) {
    return
  }
  coverageActionError.value = ''
  try {
    const updated = await coverageStore.recalculateCoverage()
    if (updated?.processing) {
      startStatusPolling()
      return
    }
    coverageStore.invalidateSummary(summaryGrid)
    await loadSummary()
    lastRequestKey = ''
    scheduleFetch()
  } catch (error) {
    const detail = formatApiErrorDetail(error, t('analytics.coverageExplorer.recalculateFailed'))
    coverageActionError.value = detail
    toast.add({
      severity: 'error',
      summary: t('analytics.coverageExplorer.recalculateErrorSummary'),
      detail,
      life: 5000
    })
    console.error('Failed to recalculate coverage:', error)
  }
}

const confirmCoverageRecalculation = () => {
  if (demoReadOnly.value) {
    showDemoCoverageReadOnlyToast()
    return
  }

  if (!canRecalculateCoverage.value) return

  confirm.require({
    message: t('analytics.coverageExplorer.recalculateConfirmMessage'),
    header: t('analytics.coverageExplorer.recalculateConfirmHeader'),
    icon: 'pi pi-exclamation-triangle',
    rejectProps: {
      label: t('analytics.coverageExplorer.cancel'),
      severity: 'secondary',
      outlined: true
    },
    acceptProps: {
      label: t('analytics.coverageExplorer.recalculateConfirmAccept'),
      severity: 'primary'
    },
    accept: handleCoverageRecalculation
  })
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
      mapContainerRef.value?.setView?.([lastPosition.lat, lastPosition.lon], 12, {animate: false})
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

.demo-read-only-message,
.coverage-action-error {
  margin-bottom: var(--gp-spacing-md);
}

.coverage-toolbar {
  display: flex;
  gap: 1rem;
  align-items: end;
  flex-wrap: wrap;
  margin-bottom: var(--gp-spacing-lg);
  padding: var(--gp-spacing-md);
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-large);
  background: var(--gp-surface-card);
  box-shadow: var(--gp-shadow-subtle);
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

.coverage-actions {
  display: flex;
  align-items: flex-end;
  gap: var(--gp-spacing-md);
}

.seen-area-summary {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  min-height: 2.5rem;
  padding-left: var(--gp-spacing-md);
  border-left: 1px solid var(--gp-border);
  color: var(--gp-primary);
}

.seen-area-summary > i {
  font-size: 1.1rem;
}

.seen-area-summary .control-label {
  display: block;
  margin-bottom: .15rem;
}

.seen-area-summary strong {
  display: block;
  color: var(--gp-text-primary);
  font-size: 1.05rem;
  line-height: 1.1;
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

.coverage-map-card {
  background: var(--gp-surface-card);
  border: 1px solid var(--gp-border);
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
  border-bottom: 1px solid var(--gp-border);
  background: var(--gp-surface-muted);
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
  background: color-mix(in srgb, var(--gp-surface-card) 78%, transparent);
  color: var(--gp-text-primary);
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
  background: color-mix(in srgb, var(--gp-surface-card) 92%, transparent);
  border: 1px solid var(--gp-border);
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
  color: var(--gp-text-secondary);
}

.map-overlay p {
  margin: 0.35rem 0 0;
  color: var(--gp-text-secondary);
}

.map-overlay strong {
  color: var(--gp-text-primary);
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
  border-top: 1px solid var(--gp-border);
  background: var(--gp-surface-muted);
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
  .coverage-toolbar {
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
  .coverage-actions,
  .coverage-toggle {
    width: 100%;
    min-width: 0;
  }

  .seen-area-summary {
    grid-column: 1 / -1;
    padding-left: 0;
    border-left: 0;
  }
}

@media (max-width: 600px) {
  .coverage-page {
    gap: 1rem;
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
  .coverage-toolbar {
    grid-template-columns: 1fr;
    gap: var(--gp-spacing-sm);
  }

  .coverage-recalculate {
    grid-column: auto;
    width: 100%;
  }

  .coverage-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .toggle-text {
    font-size: 0.8rem;
  }
}
</style>
