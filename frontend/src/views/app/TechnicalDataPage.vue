<template>
  <AppLayout variant="default">
    <PageContainer 
      title="GPS Data" 
      subtitle="Technical information about your GPS tracking data"
      :loading="isLoading"
      variant="fullwidth"
    >
    <template #actions>
      <Button 
        label="Export CSV" 
        icon="pi pi-download" 
        @click="handleExportCSV"
        :loading="exportLoading"
        :disabled="!hasData"
      />
    </template>

    <!-- Summary Statistics -->
    <div class="stats-grid">
      <BaseCard variant="subtle" class="stat-card">
        <MetricItem
          icon="pi pi-database"
          icon-color="primary"
          :value="summaryStats.totalPoints"
          label="Total GPS Points"
          :formatter="formatNumber"
        />
      </BaseCard>
      
      <BaseCard variant="subtle" class="stat-card">
        <MetricItem
          icon="pi pi-calendar-plus"
          icon-color="secondary"
          :value="summaryStats.pointsToday"
          label="Points Today"
          :formatter="formatNumber"
        />
      </BaseCard>
      
      <BaseCard variant="subtle" class="stat-card">
        <MetricItem
          icon="pi pi-play"
          icon-color="success"
          :value="summaryStats.firstPointDate"
          label="First GPS Point"
          :formatter="formatDate"
        />
      </BaseCard>
      
      <BaseCard variant="subtle" class="stat-card">
        <MetricItem
          icon="pi pi-stop"
          icon-color="info"
          :value="summaryStats.lastPointDate"
          label="Latest GPS Point"
          :formatter="formatDate"
        />
      </BaseCard>
    </div>

    <!-- Date Filter -->
    <BaseCard class="filter-section">
      <div class="filter-controls">
        <div class="filter-group">
          <label class="filter-label">Date Range:</label>
          <DatePicker
            v-model="dateRange"
            selection-mode="range"
            :number-of-months="1"
            date-format="mm/dd/yy"
            placeholder="Select date range"
            class="date-picker"
            @date-select="handleDateChange"
          />
        </div>
        <Button 
          label="Clear Filter" 
          severity="secondary" 
          size="small"
          @click="clearDateFilter"
          :disabled="!dateRange || dateRange.length === 0"
        />
      </div>
    </BaseCard>

    <!-- GPS Points Table -->
    <BaseCard class="table-section">
      <DataTable 
        :value="gpsPoints"
        :loading="tableLoading"
        paginator
        :rows="pageSize"
        :total-records="totalRecords"
        lazy
        @page="onPageChange"
        data-key="id"
        responsive-layout="scroll"
        class="gps-data-table"
        :pt="{
          root: 'bg-surface-0 dark:bg-surface-950',
          header: 'bg-surface-50 dark:bg-surface-900 border-surface-200 dark:border-surface-700',
          tbody: 'bg-surface-0 dark:bg-surface-950',
          row: 'bg-surface-0 dark:bg-surface-950 hover:bg-surface-50 dark:hover:bg-surface-800',
          cell: 'text-surface-900 dark:text-surface-100 border-surface-200 dark:border-surface-700',
          paginator: 'bg-surface-50 dark:bg-surface-900 border-surface-200 dark:border-surface-700'
        }"
      >
        <template #header>
          <div class="table-header">
            <span class="table-title">GPS Points</span>
            <span class="table-subtitle" v-if="hasDateFilter">
              {{ formatDateRange(dateRange) }}
            </span>
          </div>
        </template>

        <template #empty>
          <div class="empty-state">
            <i class="pi pi-map-marker empty-icon"></i>
            <h3>No GPS Points Found</h3>
            <p>No GPS tracking data available for the selected criteria.</p>
          </div>
        </template>

        <Column field="timestamp" header="Date" sortable class="timestamp-col">
          <template #body="slotProps">
            <div class="timestamp-cell">
              <div class="timestamp-date">{{ formatTimestamp(slotProps.data.timestamp).date }}</div>
              <div class="timestamp-time">{{ formatTimestamp(slotProps.data.timestamp).time }}</div>
            </div>
          </template>
        </Column>

        <Column header="Location" class="coordinates-col">
          <template #body="slotProps">
            <div class="coordinates-cell">
              <div class="coordinate-line">{{ slotProps.data.coordinates.lat.toFixed(6) }}</div>
              <div class="coordinate-line">{{ slotProps.data.coordinates.lng.toFixed(6) }}</div>
            </div>
          </template>
        </Column>

        <Column field="velocity" header="Speed" class="numeric-col">
          <template #body="slotProps">
            <span v-if="slotProps.data.velocity !== null && slotProps.data.velocity > 0">{{ (slotProps.data.velocity).toFixed(1) }} km/h</span>
            <span v-else class="null-value">-</span>
          </template>
        </Column>

        <Column field="accuracy" header="Accuracy" class="numeric-col">
          <template #body="slotProps">
            <span v-if="slotProps.data.accuracy">{{ slotProps.data.accuracy.toFixed(1) }}m</span>
            <span v-else class="null-value">-</span>
          </template>
        </Column>

        <Column field="altitude" header="Altitude" sortable class="numeric-col" v-if="!isMobile">
          <template #body="slotProps">
            <span v-if="slotProps.data.altitude">{{ Math.round(slotProps.data.altitude) }}m</span>
            <span v-else class="null-value">-</span>
          </template>
        </Column>

        <Column field="battery" header="Battery" sortable class="numeric-col" v-if="!isMobile">
          <template #body="slotProps">
            <span v-if="slotProps.data.battery !== null && slotProps.data.battery >= 0">{{ Math.round(slotProps.data.battery) }}%</span>
            <span v-else class="null-value">-</span>
          </template>
        </Column>

        <Column field="sourceType" header="Source" class="source-col" v-if="!isMobile">
          <template #body="slotProps">
            <Tag 
              :value="slotProps.data.sourceType || 'Unknown'" 
              :severity="getSourceSeverity(slotProps.data.sourceType)"
              class="source-tag"
            />
          </template>
        </Column>
      </DataTable>
    </BaseCard>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useTechnicalDataStore } from '@/stores/technicalData'
import {formatDate, formatDateMMDDYYYY} from "@/utils/dateHelpers";

// Components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import MetricItem from '@/components/ui/data/MetricItem.vue'

// PrimeVue
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import DatePicker from 'primevue/datepicker'
import Tag from 'primevue/tag'

// Store and utils
const technicalDataStore = useTechnicalDataStore()
const toast = useToast()

// Reactive state
const isMobile = ref(false)
const dateRange = ref(null)
const pageSize = ref(50)
const currentPage = ref(0)

// Loading states
const isLoading = ref(false)
const tableLoading = ref(false)
const exportLoading = ref(false)

// Computed properties
const summaryStats = computed(() => technicalDataStore.summaryStats)
const gpsPoints = computed(() => technicalDataStore.gpsPoints)
const totalRecords = computed(() => technicalDataStore.totalRecords)

const hasData = computed(() => summaryStats.value.totalPoints > 0)
const hasDateFilter = computed(() => 
  dateRange.value && 
  dateRange.value.length === 2 && 
  dateRange.value[0] && 
  dateRange.value[1]
)

// Methods
const formatNumber = (value) => {
  if (!value && value !== 0) return '0'
  return new Intl.NumberFormat().format(value)
}

const formatTimestamp = (timestamp) => {
  if (!timestamp) return { date: '-', time: '-' }
  const date = new Date(timestamp)
  return {
    date: date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
    time: date.toLocaleTimeString('en-US', { 
      hour: '2-digit', 
      minute: '2-digit',
      hour12: false 
    })
  }
}

const formatDateRange = (range) => {
  if (!range || range.length < 2) return ''
  const start = range[0].toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
  const end = range[1].toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
  return `${start} - ${end}`
}

const getSourceSeverity = (sourceType) => {
  const severityMap = {
    'OWNTRACKS': 'success',
    'OVERLAND': 'info',
    'MANUAL': 'warning',
    'IMPORT': 'secondary',
    'GOOGLE_TIMELINE': 'danger',
    'DAWARICH' : 'danger',
    'GPX': 'contrast'
  }
  return severityMap[sourceType] || 'contrast'
}

const handleResize = () => {
  isMobile.value = window.innerWidth < 768
  pageSize.value = isMobile.value ? 25 : 50
}

const handleDateChange = async () => {
  if (hasDateFilter.value) {
    currentPage.value = 0
    await loadGPSPoints()
  }
}

const clearDateFilter = async () => {
  dateRange.value = null
  currentPage.value = 0
  await loadGPSPoints()
}

const onPageChange = async (event) => {
  currentPage.value = event.page
  await loadGPSPoints()
}

const loadSummaryStats = async () => {
  try {
    isLoading.value = true
    await technicalDataStore.fetchSummaryStats()
  } catch (error) {
    console.error('Error loading summary stats:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load GPS data summary',
      life: 3000
    })
  } finally {
    isLoading.value = false
  }
}

const loadGPSPoints = async () => {
  try {
    tableLoading.value = true
    const params = {
      page: currentPage.value + 1,
      limit: pageSize.value
    }
    
    if (hasDateFilter.value && dateRange.value[0] && dateRange.value[1]) {
      params.startDate = dateRange.value[0].toISOString().split('T')[0]
      params.endDate = dateRange.value[1].toISOString().split('T')[0]
    }
    
    await technicalDataStore.fetchGPSPoints(params)
  } catch (error) {
    console.error('Error loading GPS points:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load GPS points',
      life: 3000
    })
  } finally {
    tableLoading.value = false
  }
}

const handleExportCSV = async () => {
  try {
    exportLoading.value = true
    const params = {}
    
    if (hasDateFilter.value && dateRange.value[0] && dateRange.value[1]) {
      params.startDate = dateRange.value[0].toISOString().split('T')[0]
      params.endDate = dateRange.value[1].toISOString().split('T')[0]
    }
    
    await technicalDataStore.exportGPSPoints(params)
    
    toast.add({
      severity: 'success',
      summary: 'Export Started',
      detail: 'Your GPS data export has started downloading',
      life: 3000
    })
  } catch (error) {
    console.error('Error exporting GPS points:', error)
    toast.add({
      severity: 'error',
      summary: 'Export Failed',
      detail: 'Failed to export GPS data',
      life: 3000
    })
  } finally {
    exportLoading.value = false
  }
}

// Lifecycle
onMounted(async () => {
  handleResize()
  window.addEventListener('resize', handleResize)
  
  await Promise.all([
    loadSummaryStats(),
    loadGPSPoints()
  ])
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})

// Watchers
watch(pageSize, async () => {
  currentPage.value = 0
  await loadGPSPoints()
})
</script>

<style scoped>
/* Stats Grid */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: var(--gp-spacing-md);
  margin-bottom: var(--gp-spacing-xl);
  width: 100%;
  box-sizing: border-box;
}

/* Large screen optimizations - ensure 4 cards in one row on larger monitors */
@media (min-width: 1024px) {
  .stats-grid {
    grid-template-columns: repeat(4, 1fr);
    max-width: none;
    gap: var(--gp-spacing-md);
  }
}

/* Extra large screens */
@media (min-width: 1440px) {
  .stats-grid {
    grid-template-columns: repeat(4, 1fr);
    max-width: none;
    gap: var(--gp-spacing-lg);
  }
}

/* Extra large screens - even wider spacing */
@media (min-width: 1920px) {
  .stats-grid {
    gap: var(--gp-spacing-lg);
  }
}

/* Tablet screens - maintain 2x2 grid with better spacing */
@media (min-width: 481px) and (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr 1fr;
    gap: var(--gp-spacing-md);
  }

  .stat-card {
    padding: var(--gp-spacing-md);
  }

  .stat-card :deep(.gp-metric-value) {
    font-size: 1.2rem;
    font-weight: 600;
  }

  .stat-card :deep(.gp-metric-label) {
    font-size: 0.8rem;
  }

  .stat-card :deep(.gp-metric-icon) {
    width: 32px;
    height: 32px;
    font-size: 1rem;
  }
}

.stat-card {
  padding: var(--gp-spacing-md);
}

/* Filter Section */
.filter-section {
  margin-bottom: var(--gp-spacing-lg);
}

.filter-controls {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-lg);
  flex-wrap: wrap;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  flex: 1;
  min-width: 250px;
}

.filter-label {
  font-weight: 500;
  color: var(--gp-text-secondary);
  white-space: nowrap;
}

.date-picker {
  flex: 1;
  max-width: 300px;
}

/* Large screen filter optimizations */
@media (min-width: 1440px) {
  .filter-controls {
    justify-content: flex-start;
    flex-wrap: nowrap;
  }
  
  .filter-group {
    flex: 0 0 auto;
    min-width: 300px;
  }
  
  .date-picker {
    max-width: 400px;
  }
}

/* Table Section */
.table-section {
  overflow: hidden;
}

/* Large screen table optimizations */
@media (min-width: 1440px) {
  .table-section {
    margin: 0 -var(--gp-spacing-md); /* Extend table slightly beyond container */
  }
}

/* Mobile table constraints */
@media (max-width: 768px) {
  .table-section {
    margin: 0;
    max-width: 100%;
    overflow-x: auto;
  }

  .gps-data-table {
    max-width: 100%;
    width: 100%;
  }

  /* Force table to fit in viewport */
  .gps-data-table :deep(.p-datatable) {
    max-width: 100% !important;
    width: 100% !important;
  }

  .gps-data-table :deep(.p-datatable-wrapper) {
    overflow-x: auto;
    max-width: 100%;
  }

  /* Mobile paginator optimizations */
  .gps-data-table :deep(.p-paginator) {
    flex-wrap: nowrap !important;
    justify-content: center !important;
    gap: 4px !important;
    padding: var(--gp-spacing-sm) !important;
  }

  .gps-data-table :deep(.p-paginator .p-paginator-page),
  .gps-data-table :deep(.p-paginator .p-paginator-next),
  .gps-data-table :deep(.p-paginator .p-paginator-prev),  
  .gps-data-table :deep(.p-paginator .p-paginator-first),
  .gps-data-table :deep(.p-paginator .p-paginator-last) {
    min-width: 32px !important;
    width: 32px !important;
    height: 32px !important;
    padding: 0 !important;
    margin: 0 1px !important;
    font-size: 0.8rem !important;
    display: flex !important;
    align-items: center !important;
    justify-content: center !important;
  }

  /* Hide some pagination elements on very small screens */
  .gps-data-table :deep(.p-paginator .p-paginator-first),
  .gps-data-table :deep(.p-paginator .p-paginator-last) {
    display: none !important;
  }

  /* Show fewer page numbers on mobile */
  .gps-data-table :deep(.p-paginator .p-paginator-page:nth-child(n+8)) {
    display: none !important;
  }
}

.table-header {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.table-title {
  font-weight: 600;
  font-size: 1.1rem;
  color: var(--gp-text-primary);
}

.table-subtitle {
  font-size: 0.875rem;
  color: var(--gp-text-muted);
}

/* Table Columns */
.gps-data-table :deep(.timestamp-col) {
  min-width: 120px;
  width: 120px;
}

.gps-data-table :deep(.coordinates-col) {
  min-width: 140px;
  width: 140px;
}

.gps-data-table :deep(.numeric-col) {
  min-width: 80px;
  width: 80px;
  text-align: right;
}

.gps-data-table :deep(.source-col) {
  min-width: 100px;
  width: 100px;
}

/* Large screen column optimizations */
@media (min-width: 1440px) {
  .gps-data-table :deep(.timestamp-col) {
    min-width: 140px;
    width: 140px;
  }

  .gps-data-table :deep(.coordinates-col) {
    min-width: 160px;
    width: 160px;
  }

  .gps-data-table :deep(.numeric-col) {
    min-width: 100px;
    width: 100px;
  }

  .gps-data-table :deep(.source-col) {
    min-width: 120px;
    width: 120px;
  }
}

/* Cell Content */
.timestamp-cell {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.timestamp-date {
  font-weight: 500;
  font-size: 0.875rem;
  color: var(--gp-text-primary);
}

.timestamp-time {
  font-size: 0.75rem;
  color: var(--gp-text-muted);
  font-family: monospace;
}

.coordinates-cell {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.coordinate-line {
  font-family: monospace;
  font-size: 0.8rem;
  color: var(--gp-text-primary);
}

.null-value {
  color: var(--gp-text-muted);
  font-style: italic;
}

.source-tag {
  font-size: 0.75rem;
}

/* Empty State */
.empty-state {
  text-align: center;
  padding: var(--gp-spacing-xxl) var(--gp-spacing-lg);
}

.empty-icon {
  font-size: 3rem;
  color: var(--gp-text-muted);
  margin-bottom: var(--gp-spacing-lg);
  display: block;
}

.empty-state h3 {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--gp-text-secondary);
  margin: 0 0 var(--gp-spacing-md);
}

.empty-state p {
  color: var(--gp-text-muted);
  margin: 0;
}

/* Responsive Design */
@media (max-width: 768px) {
  /* Override PageContainer padding for mobile */
  :deep(.gp-page-container--fullwidth) {
    padding-left: var(--gp-spacing-xs) !important;
    padding-right: var(--gp-spacing-xs) !important;
    max-width: 100vw !important;
    overflow-x: hidden;
  }

  /* Override page content container */
  :deep(.gp-page-content) {
    max-width: 100% !important;
    overflow-x: hidden;
  }

  /* Force all content to respect viewport width */
  .stats-grid,
  .filter-section,
  .table-section {
    max-width: calc(100vw - 2 * var(--gp-spacing-xs));
    box-sizing: border-box;
    margin-left: 0;
    margin-right: 0;
  }

  /* Ensure BaseCards don't exceed viewport */
  .stat-card,
  .filter-section,
  .table-section {
    margin-left: 0 !important;
    margin-right: 0 !important;
  }

  .stats-grid {
    grid-template-columns: 1fr 1fr;
    gap: var(--gp-spacing-sm);
    margin-left: 0;
    margin-right: 0;
    width: 100%;
    box-sizing: border-box;
  }

  .stat-card {
    min-width: 0; /* Allow cards to shrink */
    width: 100%;
    padding: var(--gp-spacing-sm);
    box-sizing: border-box;
    overflow: hidden; /* Prevent content overflow */
  }

  /* Ensure MetricItem content respects card boundaries */
  .stat-card :deep(.gp-metric-item) {
    gap: var(--gp-spacing-sm);
    padding: var(--gp-spacing-sm) 0;
  }

  .stat-card :deep(.gp-metric-content) {
    min-width: 0;
    overflow: hidden;
  }

  .stat-card :deep(.gp-metric-value) {
    font-size: 1.1rem;
    font-weight: 600;
    word-break: break-word;
    line-height: 1.2;
  }

  .stat-card :deep(.gp-metric-label) {
    font-size: 0.75rem;
    word-break: break-word;
    line-height: 1.3;
    margin-top: 0.125rem;
  }

  .stat-card :deep(.gp-metric-icon) {
    width: 28px;
    height: 28px;
    font-size: 0.9rem;
  }

  .filter-controls {
    flex-direction: column;
    align-items: stretch;
    gap: var(--gp-spacing-md);
    width: 100%;
    max-width: 100%;
    box-sizing: border-box;
  }

  .filter-group {
    flex-direction: column;
    align-items: stretch;
    min-width: unset;
    width: 100%;
    max-width: 100%;
  }

  .date-picker {
    max-width: 100%;
    width: 100%;
  }

  /* Ensure datepicker component fits */
  .filter-section :deep(.p-datepicker) {
    width: 100% !important;
    max-width: 100% !important;
  }

  .filter-section :deep(.p-inputtext) {
    width: 100% !important;
    max-width: 100% !important;
    box-sizing: border-box;
  }

  .gps-data-table :deep(.timestamp-col) {
    min-width: 100px;
    width: 100px;
  }

  .gps-data-table :deep(.coordinates-col) {
    min-width: 120px;
    width: 120px;
  }

  .coordinate-line {
    font-size: 0.75rem;
  }

  .empty-state {
    padding: var(--gp-spacing-xl) var(--gp-spacing-md);
  }
}

/* Very small screens - keep 2x2 grid but adjust spacing */
@media (max-width: 480px) {
  .stats-grid {
    grid-template-columns: 1fr 1fr;
    gap: var(--gp-spacing-xs);
  }

  .stat-card {
    width: 100%;
    padding: var(--gp-spacing-xs) var(--gp-spacing-sm);
    box-sizing: border-box;
    overflow: hidden;
  }

  /* Adjust text sizing for very small screens */
  .stat-card :deep(.gp-metric-value) {
    font-size: 1rem;
    line-height: 1.1;
  }

  .stat-card :deep(.gp-metric-label) {
    font-size: 0.7rem;
    line-height: 1.2;
  }

  .stat-card :deep(.gp-metric-icon) {
    width: 24px;
    height: 24px;
    font-size: 0.8rem;
  }
  
  .timestamp-date,
  .timestamp-time {
    font-size: 0.8rem;
  }

  .coordinate-line {
    font-size: 0.7rem;
  }

  /* Extra mobile paginator optimizations for very small screens */
  .gps-data-table :deep(.p-paginator) {
    gap: 2px !important;
    padding: var(--gp-spacing-xs) !important;
  }

  .gps-data-table :deep(.p-paginator .p-paginator-page),
  .gps-data-table :deep(.p-paginator .p-paginator-next),
  .gps-data-table :deep(.p-paginator .p-paginator-prev) {
    min-width: 28px !important;
    width: 28px !important;
    height: 28px !important;
    font-size: 0.75rem !important;
  }

  /* Show even fewer elements on very small screens */
  .gps-data-table :deep(.p-paginator .p-paginator-page:nth-child(n+6)) {
    display: none !important;
  }
}

/* Dark Mode */
.p-dark .table-title {
  color: var(--gp-text-primary);
}

.p-dark .table-subtitle {
  color: var(--gp-text-muted);
}

.p-dark .timestamp-date {
  color: var(--gp-text-primary);
}

.p-dark .timestamp-time {
  color: var(--gp-text-muted);
}

.p-dark .coordinate-line {
  color: var(--gp-text-primary);
}

.p-dark .null-value {
  color: var(--gp-text-muted);
}

.p-dark .empty-icon {
  color: var(--gp-text-muted);
}

.p-dark .empty-state h3 {
  color: var(--gp-text-secondary);
}

.p-dark .empty-state p {
  color: var(--gp-text-muted);
}

/* PrimeVue DataTable Dark Mode Overrides */
.p-dark .gps-data-table :deep(.p-datatable) {
  background: var(--gp-surface-dark) !important;
  color: var(--gp-text-primary) !important;
}

.p-dark .gps-data-table :deep(.p-datatable-header) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .gps-data-table :deep(.p-datatable-tbody > tr) {
  background: var(--gp-surface-dark) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .gps-data-table :deep(.p-datatable-tbody > tr:hover) {
  background: var(--gp-surface-light) !important;
}

.p-dark .gps-data-table :deep(.p-datatable-tbody > tr > td) {
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .gps-data-table :deep(.p-datatable-thead > tr > th) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .gps-data-table :deep(.p-datatable-paginator-bottom),
.p-dark .gps-data-table :deep(.p-paginator.p-component) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border: 1px solid var(--gp-border-dark) !important;
  border-top: 1px solid var(--gp-border-dark) !important;
  border-radius: 0 0 0 0 !important;
  overflow: hidden !important;
}

/* Fix white corners by ensuring all child elements have proper background */
.p-dark .gps-data-table :deep(.p-datatable-paginator-bottom *),
.p-dark .gps-data-table :deep(.p-paginator.p-component *) {
  background-color: inherit !important;
}

.p-dark .gps-data-table :deep(.p-datatable-paginator-bottom::before),
.p-dark .gps-data-table :deep(.p-datatable-paginator-bottom::after),
.p-dark .gps-data-table :deep(.p-paginator.p-component::before),
.p-dark .gps-data-table :deep(.p-paginator.p-component::after) {
  background: var(--gp-surface-darker) !important;
}

.p-dark .gp-data-table :deep(.p-paginator) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border: none !important;
}

.p-dark .gps-data-table :deep(.p-paginator .p-paginator-page),
.p-dark .gps-data-table :deep(.p-paginator .p-paginator-next),
.p-dark .gps-data-table :deep(.p-paginator .p-paginator-prev),
.p-dark .gps-data-table :deep(.p-paginator .p-paginator-first),
.p-dark .gps-data-table :deep(.p-paginator .p-paginator-last) {
  color: var(--gp-text-primary) !important;
  background: transparent !important;
  border: 1px solid var(--gp-border-dark) !important;
  margin: 0 2px !important;
}

.p-dark .gps-data-table :deep(.p-paginator .p-paginator-page:hover),
.p-dark .gps-data-table :deep(.p-paginator .p-paginator-next:hover),
.p-dark .gps-data-table :deep(.p-paginator .p-paginator-prev:hover),
.p-dark .gps-data-table :deep(.p-paginator .p-paginator-first:hover),
.p-dark .gps-data-table :deep(.p-paginator .p-paginator-last:hover) {
  background: var(--gp-surface-light) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-medium) !important;
}

.p-dark .gps-data-table :deep(.p-paginator .p-paginator-page.p-highlight) {
  background: var(--gp-primary) !important;
  color: white !important;
  border-color: var(--gp-primary) !important;
}

.p-dark .gps-data-table :deep(.p-paginator .p-paginator-current) {
  color: var(--gp-text-secondary) !important;
}

/* Fix the table wrapper to ensure proper corner styling */
.p-dark .gps-data-table :deep(.p-datatable-wrapper) {
  border-radius: var(--gp-radius-medium) !important;
  overflow: hidden !important;
  background: var(--gp-surface-dark) !important;
}

.p-dark .gps-data-table :deep(.p-datatable) {
  border-radius: var(--gp-radius-medium) !important;
  overflow: hidden !important;
}

/* Ensure the table container has proper rounded corners */
.p-dark .table-section :deep(.p-card-body) {
  padding: 0 !important;
  border-radius: var(--gp-radius-medium) !important;
  overflow: hidden !important;
}

/* Light mode paginator fixes */
.gps-data-table :deep(.p-datatable-paginator-bottom),
.gps-data-table :deep(.p-paginator.p-component) {
  background: var(--gp-surface-light) !important;
  border: 1px solid var(--gp-border-light) !important;
  border-top: 1px solid var(--gp-border-light) !important;
  border-radius: 0 0 0 0 !important;
  overflow: hidden !important;
}

/* Remove unwanted focus/active borders on page header */
:deep(.gp-page-header) {
  outline: none !important;
  border: none !important;
  box-shadow: none !important;
}

:deep(.gp-page-header):focus,
:deep(.gp-page-header):active,
:deep(.gp-page-header):focus-within {
  outline: none !important;
  border: none !important;
  box-shadow: none !important;
}

/* Remove focus borders from page container */
:deep(.gp-page-container) {
  outline: none !important;
}

:deep(.gp-page-container):focus,
:deep(.gp-page-container):active {
  outline: none !important;
  border: none !important;
  box-shadow: none !important;
}
</style>