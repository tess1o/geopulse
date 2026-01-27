<template>
  <AppLayout variant="default">
    <PageContainer 
      title="GPS Data" 
      subtitle="Technical information about your GPS tracking data"
      :loading="isLoading"
      variant="fullwidth"
    >
    <template #actions>
      <div class="header-actions">
        <Badge
          v-if="selectedRows.length > 0"
          :value="selectedRows.length"
          severity="info"
          class="selection-badge"
        >
          <span class="selection-text">{{ selectedRows.length }} selected</span>
        </Badge>

        <Button
          v-if="selectedRows.length > 0"
          label="Delete Selected"
          icon="pi pi-trash"
          severity="danger"
          size="small"
          @click="bulkDeleteGpsPoints"
          :disabled="selectedRows.length === 0"
          class="bulk-delete-button"
        />
        <Button
          :label="exportButtonLabel"
          icon="pi pi-download"
          @click="handleExportCSV"
          :loading="exportLoading"
          :disabled="!hasData || totalRecords === 0"
        />
      </div>
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
          :formatter="timezone.timeAgo"
        />
      </BaseCard>
    </div>

    <!-- Date Filter -->
    <BaseCard class="filter-section">
      <div class="filter-header">
        <h3 class="filter-title">Filters</h3>
        <div class="filter-header-actions">
          <Button
            v-if="activeFilterCount > 0"
            :label="`${activeFilterCount} active`"
            severity="info"
            size="small"
            outlined
            :badge="activeFilterCount.toString()"
          />
          <Button
            v-if="hasActiveFilters"
            label="Clear All"
            severity="secondary"
            size="small"
            text
            @click="clearAllFilters"
          />
          <Button
            :icon="showAdvancedFilters ? 'pi pi-chevron-up' : 'pi pi-chevron-down'"
            :label="showAdvancedFilters ? 'Hide Advanced' : 'Show Advanced'"
            text
            size="small"
            @click="showAdvancedFilters = !showAdvancedFilters"
          />
        </div>
      </div>

      <!-- Quick Date Presets -->
      <div class="quick-presets">
        <Button label="Today" size="small" outlined @click="setToday" />
        <Button label="Yesterday" size="small" outlined @click="setYesterday" />
        <Button label="Last 7 Days" size="small" outlined @click="setLast7Days" />
        <Button label="Last 30 Days" size="small" outlined @click="setLast30Days" />
      </div>

      <div class="filter-controls">
        <div class="filter-group filter-group-wide">
          <label class="filter-label">Date & Time Range:</label>
          <DatePicker
            v-model="dateRange"
            selection-mode="range"
            show-time
            hour-format="24"
            :number-of-months="1"
            date-format="mm/dd/yy"
            placeholder="Select range"
            class="date-picker date-picker-wide"
            @date-select="handleDateChange"
          />
        </div>
        <Button
          v-if="hasDateFilter"
          label="Clear"
          severity="secondary"
          size="small"
          text
          icon="pi pi-times"
          @click="clearDateFilter"
        />
      </div>

      <!-- Advanced Filters -->
      <div v-if="showAdvancedFilters" class="advanced-filters">
        <div class="filter-row">
          <div class="filter-field">
            <label class="filter-label">Source Types:</label>
            <MultiSelect
              v-model="filters.sourceTypes"
              :options="sourceTypeOptions"
              option-label="label"
              option-value="value"
              placeholder="All Sources"
              display="chip"
              class="filter-input"
              :showToggleAll="false"
            />
          </div>
        </div>

        <div class="filter-row">
          <div class="filter-field">
            <label class="filter-label">Accuracy (meters):</label>
            <div class="range-inputs">
              <InputNumber
                v-model="filters.accuracyMin"
                placeholder="Min"
                :min="0"
                :max="10000"
                class="range-input"
              />
              <span class="range-separator">to</span>
              <InputNumber
                v-model="filters.accuracyMax"
                placeholder="Max"
                :min="0"
                :max="10000"
                class="range-input"
              />
            </div>
          </div>

          <div class="filter-field">
            <label class="filter-label">Speed (km/h):</label>
            <div class="range-inputs">
              <InputNumber
                v-model="filters.speedMin"
                placeholder="Min"
                :min="0"
                :max="500"
                class="range-input"
              />
              <span class="range-separator">to</span>
              <InputNumber
                v-model="filters.speedMax"
                placeholder="Max"
                :min="0"
                :max="500"
                class="range-input"
              />
            </div>
          </div>
        </div>
      </div>

      <!-- Active Filter Chips -->
      <div v-if="hasActiveFilters" class="active-filter-chips">
        <Chip
          v-if="hasDateFilter"
          :label="`Date: ${formatDateRange(dateRange)}`"
          removable
          @remove="clearDateFilter"
        />
        <Chip
          v-if="filters.accuracyMin !== null || filters.accuracyMax !== null"
          :label="`Accuracy: ${filters.accuracyMin || 0} - ${filters.accuracyMax || '∞'} m`"
          removable
          @remove="filters.accuracyMin = null; filters.accuracyMax = null"
        />
        <Chip
          v-if="filters.speedMin !== null || filters.speedMax !== null"
          :label="`Speed: ${filters.speedMin || 0} - ${filters.speedMax || '∞'} km/h`"
          removable
          @remove="filters.speedMin = null; filters.speedMax = null"
        />
        <Chip
          v-if="filters.sourceTypes && filters.sourceTypes.length > 0"
          :label="`Sources: ${filters.sourceTypes.length} selected`"
          removable
          @remove="filters.sourceTypes = []"
        />
      </div>
    </BaseCard>

    <!-- Filtered Results Banner -->
    <BaseCard v-if="hasActiveFilters && filteredRecordsText" class="filtered-banner">
      <div class="filtered-banner-content">
        <i class="pi pi-filter"></i>
        <span class="filtered-text">{{ filteredRecordsText }}</span>
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
        :first="currentPage * pageSize"
        lazy
        @page="onPageChange"
        @sort="onSort"
        @row-click="handleRowClick"
        v-model:sort-field="sortField"
        v-model:sort-order="sortOrder"
        data-key="id"
        responsive-layout="scroll"
        class="gps-data-table"
        v-model:selection="selectedRows"
        selection-mode="multiple"
        :pt="{
          root: 'bg-surface-0 dark:bg-surface-950',
          header: 'bg-surface-50 dark:bg-surface-900 border-surface-200 dark:border-surface-700',
          tbody: 'bg-surface-0 dark:bg-surface-950',
          row: 'bg-surface-0 dark:bg-surface-950 hover:bg-surface-50 dark:hover:bg-surface-800',
          cell: 'text-surface-900 dark:text-surface-100 border-surface-200 dark:border-surface-700',
          paginator: 'bg-surface-50 dark:bg-surface-900 border-surface-200 dark:border-surface-700'
        }"
      >
        <template #paginatorstart>
          <span class="paginator-info">Page {{ currentPage + 1 }} of {{ totalPages.toLocaleString() }}</span>
        </template>
        <template #paginatorend>
          <span class="paginator-info">{{ totalRecords.toLocaleString() }} total</span>
        </template>
        <template #header>
          <div class="table-header">
            <div class="table-header-left">
              <span class="table-title">GPS Points</span>
              <span class="table-subtitle">
                <template v-if="totalRecords > 0">
                  Showing {{ currentPage * pageSize + 1 }}-{{ Math.min((currentPage + 1) * pageSize, totalRecords) }} of {{ totalRecords.toLocaleString() }} points
                  <span v-if="hasActiveFilters && summaryStats.filteredPoints" class="filtered-info">
                    (filtered from {{ summaryStats.totalPoints.toLocaleString() }})
                  </span>
                </template>
                <template v-else>
                  No points found
                </template>
              </span>
            </div>
            <div class="table-header-right">
              <label class="page-size-label">Rows per page:</label>
              <Dropdown
                v-model="pageSize"
                :options="pageSizeOptions"
                class="page-size-dropdown"
              />
            </div>
          </div>
        </template>

        <template #empty>
          <div class="empty-state">
            <i class="pi pi-map-marker empty-icon"></i>
            <h3>No GPS Points Found</h3>
            <p>No GPS tracking data available for the selected criteria.</p>
          </div>
        </template>

        <!-- Selection Column -->
        <Column selectionMode="multiple" headerStyle="width: 3rem" class="selection-col"></Column>

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

        <Column field="velocity" sortable header="Speed" class="numeric-col">
          <template #body="slotProps">
            <span v-if="slotProps.data.velocity !== null && slotProps.data.velocity >= 0">{{ formatSpeed(slotProps.data.velocity.toFixed(1)) }}</span>
            <span v-else class="null-value">-</span>
          </template>
        </Column>

        <Column field="accuracy" sortable header="Accuracy" class="numeric-col" v-if="!isMobile">
          <template #body="slotProps">
            <span v-if="slotProps.data.accuracy">{{ formatDistance(slotProps.data.accuracy.toFixed(1)) }}</span>
            <span v-else class="null-value">-</span>
          </template>
        </Column>

        <Column field="altitude" header="Altitude" class="numeric-col" v-if="!isMobile && !isTablet">
          <template #body="slotProps">
            <span v-if="slotProps.data.altitude">{{ formatDistance(Math.round(slotProps.data.altitude)) }}</span>
            <span v-else class="null-value">-</span>
          </template>
        </Column>

        <Column field="battery" header="Battery" sortable class="numeric-col" v-if="!isMobile && !isTablet">
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

        <Column header="Actions" class="actions-col">
          <template #body="slotProps">
            <div class="actions-buttons">
              <Button 
                icon="pi pi-pencil" 
                severity="secondary" 
                size="small"
                text
                @click="editGpsPoint(slotProps.data)"
                v-tooltip.top="'Edit GPS Point'"
                class="action-button edit-button"
              />
              <Button 
                icon="pi pi-trash" 
                severity="danger" 
                size="small"
                text
                @click="deleteGpsPoint(slotProps.data)"
                v-tooltip.top="'Delete GPS Point'"
                class="action-button delete-button"
              />
            </div>
          </template>
        </Column>
      </DataTable>
    </BaseCard>
    
    <!-- Delete Confirmation Dialog -->
    <Dialog
      v-model:visible="showDeleteDialog"
      :header="'Delete GPS Point'"
      :modal="true"
      :style="{ width: '25rem' }"
    >
      <div class="confirm-dialog-content">
        <i class="pi pi-exclamation-triangle confirm-icon"></i>
        <span>Are you sure you want to delete this GPS point? This action cannot be undone.</span>
      </div>
      <template #footer>
        <Button 
          label="Cancel" 
          severity="secondary" 
          @click="showDeleteDialog = false" 
          autofocus 
          :disabled="deleteLoading"
        />
        <Button 
          label="Delete" 
          severity="danger" 
          @click="confirmDeleteGpsPoint" 
          :loading="deleteLoading"
        />
      </template>
    </Dialog>

    <!-- Bulk Delete Confirmation Dialog -->
    <Dialog
      v-model:visible="showBulkDeleteDialog"
      :header="'Delete Multiple GPS Points'"
      :modal="true"
      :style="{ width: '30rem' }"
    >
      <div class="confirm-dialog-content">
        <i class="pi pi-exclamation-triangle confirm-icon"></i>
        <span>
          Are you sure you want to delete {{ selectedRows.length }} GPS point{{ selectedRows.length > 1 ? 's' : '' }}? 
          This action cannot be undone.
        </span>
      </div>
      <template #footer>
        <Button 
          label="Cancel" 
          severity="secondary" 
          @click="showBulkDeleteDialog = false" 
          autofocus 
          :disabled="bulkDeleteLoading"
        />
        <Button 
          label="Delete All" 
          severity="danger" 
          @click="confirmBulkDelete" 
          :loading="bulkDeleteLoading"
        />
      </template>
    </Dialog>

    <!-- Edit GPS Point Dialog -->
    <GpsPointEditDialog 
      :visible="showEditDialog"
      :gps-point="selectedGpsPoint"
      @close="showEditDialog = false"
      @save="handleEditSave"
    />
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useTechnicalDataStore } from '@/stores/technicalData'
import { useTimezone } from '@/composables/useTimezone'

const timezone = useTimezone()

// Components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import MetricItem from '@/components/ui/data/MetricItem.vue'
import GpsPointEditDialog from '@/components/dialogs/GpsPointEditDialog.vue'

// PrimeVue
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import DatePicker from 'primevue/datepicker'
import Tag from 'primevue/tag'
import Dialog from 'primevue/dialog'
import MultiSelect from 'primevue/multiselect'
import InputNumber from 'primevue/inputnumber'
import Chip from 'primevue/chip'
import Badge from 'primevue/badge'
import Dropdown from 'primevue/dropdown'
import {formatDistance, formatSpeed} from "../../utils/calculationsHelpers";

// Store and utils
const technicalDataStore = useTechnicalDataStore()
const toast = useToast()

// Reactive state
const isMobile = ref(false)
const isTablet = ref(false)
const dateRange = ref(null)
const pageSize = ref(50)
const currentPage = ref(0)
const sortField = ref('timestamp')
const sortOrder = ref(-1) // -1 for descending, 1 for ascending

// Advanced filters
const showAdvancedFilters = ref(false)
const filters = ref({
  sourceTypes: [],
  accuracyMin: null,
  accuracyMax: null,
  speedMin: null,
  speedMax: null
})

// Source type options for multi-select
const sourceTypeOptions = ref([
  { label: 'OwnTracks', value: 'OWNTRACKS' },
  { label: 'Overland', value: 'OVERLAND' },
  { label: 'Google Timeline', value: 'GOOGLE_TIMELINE' },
  { label: 'GPX', value: 'GPX' },
  { label: 'Dawarich', value: 'DAWARICH' },
  { label: 'Home Assistant', value: 'HOME_ASSISTANT' },
  { label: 'GeoJSON', value: 'GEOJSON' },
  { label: 'CSV', value: 'CSV' }
])

// Page size options
const pageSizeOptions = ref([25, 50, 100, 200, 500])

// Loading states
const isLoading = ref(false)
const tableLoading = ref(false)
const exportLoading = ref(false)

// Edit/Delete states
const showEditDialog = ref(false)
const showDeleteDialog = ref(false)
const selectedGpsPoint = ref(null)

// Bulk delete states
const selectedRows = ref([])
const showBulkDeleteDialog = ref(false)
const bulkDeleteLoading = ref(false)
const deleteLoading = ref(false)

// Shift-click selection tracking
const lastSelectedIndex = ref(null)

// Computed properties
const summaryStats = computed(() => technicalDataStore.summaryStats)
const gpsPoints = computed(() => technicalDataStore.gpsPoints)
const totalRecords = computed(() => technicalDataStore.totalRecords)

const hasData = computed(() => summaryStats.value.totalPoints > 0)
const hasDateFilter = computed(() =>
  dateRange.value &&
  ((Array.isArray(dateRange.value) && dateRange.value.length === 2 && dateRange.value[0] && dateRange.value[1]) ||
   (dateRange.value[0] && dateRange.value[1]))
)

const hasActiveFilters = computed(() => {
  return hasDateFilter.value ||
         (filters.value.accuracyMin !== null && filters.value.accuracyMin !== undefined) ||
         (filters.value.accuracyMax !== null && filters.value.accuracyMax !== undefined) ||
         (filters.value.speedMin !== null && filters.value.speedMin !== undefined) ||
         (filters.value.speedMax !== null && filters.value.speedMax !== undefined) ||
         (filters.value.sourceTypes && filters.value.sourceTypes.length > 0)
})

const activeFilterCount = computed(() => {
  let count = 0
  if (hasDateFilter.value) count++
  if (filters.value.accuracyMin !== null || filters.value.accuracyMax !== null) count++
  if (filters.value.speedMin !== null || filters.value.speedMax !== null) count++
  if (filters.value.sourceTypes && filters.value.sourceTypes.length > 0) count++
  return count
})

const filteredRecordsText = computed(() => {
  if (!hasActiveFilters.value) return ''

  const filtered = totalRecords.value
  const unfilteredTotal = summaryStats.value.totalPoints

  if (filtered === 0) {
    return 'No points available for selected period'
  }

  // Show comparison: filtered count vs unfiltered total
  if (filtered < unfilteredTotal) {
    return `Showing ${filtered.toLocaleString()} of ${unfilteredTotal.toLocaleString()} points`
  }

  return `Showing ${filtered.toLocaleString()} points`
})

const exportButtonLabel = computed(() => {
  // Priority 1: Manual selection (regardless of filters)
  if (selectedRows.value.length > 0) {
    return `Export CSV (${selectedRows.value.length} selected)`
  }

  // Priority 2: Filters applied, no manual selection
  if (hasActiveFilters.value) {
    if (totalRecords.value === 0) {
      return 'Export CSV (0 pts)'
    }
    return `Export CSV (${totalRecords.value.toLocaleString()} pts)`
  }

  // Priority 3: No filters and no manual selection
  return 'Export CSV'
})

const totalPages = computed(() => {
  if (!totalRecords.value || !pageSize.value) return 0
  return Math.ceil(totalRecords.value / pageSize.value)
})

// Removed - using timezone composable directly

// Methods
const formatNumber = (value) => {
  if (!value && value !== 0) return '0'
  return new Intl.NumberFormat().format(value)
}

const formatDate = (value) => {
  if (!value) return '-';
  return timezone.format(value, 'YYYY-MM-DD');
}

const formatTimestamp = (timestamp) => {
  if (!timestamp) return { date: '-', time: '-' }
  return {
    date: timezone.format(timestamp, 'YYYY-MM-DD'),
    time: timezone.format(timestamp, 'HH:mm')
  }
}

const formatDateRange = (range) => {
  if (!range || range.length < 2) return ''
  const start = timezone.format(range[0], 'MMM D');
  const end = timezone.format(range[1], 'MMM D');
  return `${start} - ${end}`
}

const formatDateForAPI = (date, isEndDate = false) => {
  // Fix: Use createDateRangeFromPicker to properly handle browser timezone dates
  // The date parameter comes from browser's DatePicker component and needs to be 
  // converted to user's timezone before getting UTC boundaries
  const { start, end } = timezone.createDateRangeFromPicker(date, date);
  return isEndDate ? end : start;
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
  isTablet.value = window.innerWidth >= 768 && window.innerWidth < 1024
  pageSize.value = isMobile.value ? 25 : 50
}

const handleDateChange = async () => {
  if (hasDateFilter.value) {
    currentPage.value = 0
    selectedRows.value = [] // Clear selection when date filter changes
    lastSelectedIndex.value = null // Reset shift-click tracking
    await loadGPSPoints()
    await loadSummaryStats()
  }
}

const clearDateFilter = async () => {
  dateRange.value = null
  currentPage.value = 0
  selectedRows.value = [] // Clear selection when clearing filters
  lastSelectedIndex.value = null // Reset shift-click tracking
  await loadGPSPoints()
  await loadSummaryStats()
}

// Quick date preset methods
const setToday = () => {
  const now = new Date()
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0)
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59)
  dateRange.value = [start, end]
  handleDateChange()
}

const setYesterday = () => {
  const yesterday = new Date()
  yesterday.setDate(yesterday.getDate() - 1)
  const start = new Date(yesterday.getFullYear(), yesterday.getMonth(), yesterday.getDate(), 0, 0, 0)
  const end = new Date(yesterday.getFullYear(), yesterday.getMonth(), yesterday.getDate(), 23, 59, 59)
  dateRange.value = [start, end]
  handleDateChange()
}

const setLast7Days = () => {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 7)
  start.setHours(0, 0, 0, 0)
  end.setHours(23, 59, 59, 999)
  dateRange.value = [start, end]
  handleDateChange()
}

const setLast30Days = () => {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 30)
  start.setHours(0, 0, 0, 0)
  end.setHours(23, 59, 59, 999)
  dateRange.value = [start, end]
  handleDateChange()
}

const clearAllFilters = async () => {
  dateRange.value = null
  filters.value = {
    sourceTypes: [],
    accuracyMin: null,
    accuracyMax: null,
    speedMin: null,
    speedMax: null
  }
  currentPage.value = 0
  selectedRows.value = [] // Clear selection when clearing all filters
  lastSelectedIndex.value = null // Reset shift-click tracking
  await loadGPSPoints()
  await loadSummaryStats()
}

const onPageChange = async (event) => {
  currentPage.value = event.page
  lastSelectedIndex.value = null // Reset shift-click tracking on page change
  await loadGPSPoints()
}

const onSort = async (event) => {
  sortField.value = event.sortField
  sortOrder.value = event.sortOrder
  currentPage.value = 0 // Reset to first page on sort
  await loadGPSPoints()
}

const loadSummaryStats = async () => {
  try {
    isLoading.value = true

    // Build filter params for summary
    const params = buildFilterParams()

    await technicalDataStore.fetchSummaryStats(params)
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

// Helper to build filter params
const buildFilterParams = () => {
  const params = {}

  // Date/time range
  if (hasDateFilter.value) {
    const dates = Array.isArray(dateRange.value) ? dateRange.value : [dateRange.value[0], dateRange.value[1]]
    if (dates[0]) params.startTime = formatDateForAPI(dates[0], false)
    if (dates[1]) params.endTime = formatDateForAPI(dates[1], true)
  }

  // Advanced filters
  if (filters.value.accuracyMin !== null && filters.value.accuracyMin !== undefined) {
    params.accuracyMin = filters.value.accuracyMin
  }
  if (filters.value.accuracyMax !== null && filters.value.accuracyMax !== undefined) {
    params.accuracyMax = filters.value.accuracyMax
  }
  if (filters.value.speedMin !== null && filters.value.speedMin !== undefined) {
    params.speedMin = filters.value.speedMin
  }
  if (filters.value.speedMax !== null && filters.value.speedMax !== undefined) {
    params.speedMax = filters.value.speedMax
  }
  if (filters.value.sourceTypes && filters.value.sourceTypes.length > 0) {
    params.sourceTypes = filters.value.sourceTypes.join(',')
  }

  return params
}

const loadGPSPoints = async () => {
  try {
    tableLoading.value = true

    // Start with pagination and sorting
    const params = {
      page: currentPage.value + 1,
      limit: pageSize.value,
      sortBy: sortField.value,
      sortOrder: sortOrder.value === 1 ? 'asc' : 'desc'
    }

    // Merge with filter params
    Object.assign(params, buildFilterParams())

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

    // Priority 1: If manual selection is active, export only selected rows
    if (selectedRows.value.length > 0) {
      const selectedIds = selectedRows.value.map(row => row.id)
      await technicalDataStore.exportGPSPoints({}, selectedIds)

      toast.add({
        severity: 'success',
        summary: 'Export Started',
        detail: `Exporting ${selectedRows.value.length} selected GPS points. Download will start shortly.`,
        life: 4000
      })
      return
    }

    // Priority 2 & 3: Export with filters or all data
    const params = buildFilterParams()
    await technicalDataStore.exportGPSPoints(params)

    const recordCount = totalRecords.value || summaryStats.value.totalPoints
    toast.add({
      severity: 'success',
      summary: 'Export Started',
      detail: `Exporting ${recordCount.toLocaleString()} GPS points. Download will start shortly.`,
      life: 4000
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

const editGpsPoint = (gpsPoint) => {
  selectedGpsPoint.value = gpsPoint
  showEditDialog.value = true
}

const deleteGpsPoint = (gpsPoint) => {
  selectedGpsPoint.value = gpsPoint
  showDeleteDialog.value = true
}

const confirmDeleteGpsPoint = async () => {
  if (!selectedGpsPoint.value || deleteLoading.value) return
  
  deleteLoading.value = true
  
  try {
    await technicalDataStore.deleteGpsPoint(selectedGpsPoint.value.id)
    
    toast.add({
      severity: 'success',
      summary: 'GPS Point Deleted',
      detail: 'GPS point has been successfully deleted',
      life: 3000
    })
    
    // Refresh the data
    await loadGPSPoints()
    await loadSummaryStats()
    
  } catch (error) {
    console.error('Error deleting GPS point:', error)
    const errorMessage = error.response?.data?.message || error.message || 'Failed to delete GPS point'
    toast.add({
      severity: 'error',
      summary: 'Delete Failed',
      detail: errorMessage,
      life: 5000
    })
  } finally {
    deleteLoading.value = false
    showDeleteDialog.value = false
    selectedGpsPoint.value = null
  }
}

const handleEditSave = async (updatedData) => {
  if (!selectedGpsPoint.value) return
  
  try {
    await technicalDataStore.updateGpsPoint(selectedGpsPoint.value.id, updatedData)
    
    toast.add({
      severity: 'success',
      summary: 'GPS Point Updated',
      detail: 'GPS point has been successfully updated',
      life: 3000
    })
    
    // Refresh the data
    await loadGPSPoints()
    
  } catch (error) {
    console.error('Error updating GPS point:', error)
    const errorMessage = error.response?.data?.message || error.message || 'Failed to update GPS point'
    toast.add({
      severity: 'error',
      summary: 'Update Failed',
      detail: errorMessage,
      life: 5000
    })
  } finally {
    showEditDialog.value = false
    selectedGpsPoint.value = null
  }
}

const bulkDeleteGpsPoints = () => {
  if (selectedRows.value.length === 0) return
  showBulkDeleteDialog.value = true
}

const confirmBulkDelete = async () => {
  if (selectedRows.value.length === 0 || bulkDeleteLoading.value) return

  bulkDeleteLoading.value = true

  try {
    const pointIds = selectedRows.value.map(row => row.id)
    const result = await technicalDataStore.deleteGpsPoints(pointIds)

    // Extract the correct count from the response
    const deletedCount = result?.data?.deletedCount || result?.deletedCount || pointIds.length

    toast.add({
      severity: 'success',
      summary: 'GPS Points Deleted',
      detail: `Successfully deleted ${deletedCount} GPS point${deletedCount !== 1 ? 's' : ''}`,
      life: 3000
    })

    // Clear selection and refresh data
    selectedRows.value = []
    lastSelectedIndex.value = null // Reset shift-click tracking
    await loadGPSPoints()
    await loadSummaryStats()

  } catch (error) {
    console.error('Error deleting GPS points:', error)
    const errorMessage = error.response?.data?.message || error.message || 'Failed to delete selected GPS points'
    toast.add({
      severity: 'error',
      summary: 'Bulk Delete Failed',
      detail: errorMessage,
      life: 5000
    })
  } finally {
    bulkDeleteLoading.value = false
    showBulkDeleteDialog.value = false
  }
}

// Handle row click for shift-select functionality
const handleRowClick = (event) => {
  const clickedRow = event.data
  const clickedIndex = gpsPoints.value.findIndex(row => row.id === clickedRow.id)

  // If shift key is not pressed, just update the last selected index
  if (!event.originalEvent.shiftKey) {
    lastSelectedIndex.value = clickedIndex
    return
  }

  // Shift key is pressed - select range
  if (lastSelectedIndex.value !== null && clickedIndex !== -1) {
    const start = Math.min(lastSelectedIndex.value, clickedIndex)
    const end = Math.max(lastSelectedIndex.value, clickedIndex)

    // Get all rows in the range
    const rangeRows = gpsPoints.value.slice(start, end + 1)

    // Create a set of currently selected IDs for faster lookup
    const selectedIds = new Set(selectedRows.value.map(row => row.id))

    // Add all rows in range to selection (avoid duplicates)
    rangeRows.forEach(row => {
      if (!selectedIds.has(row.id)) {
        selectedRows.value.push(row)
      }
    })
  }

  // Update last selected index
  lastSelectedIndex.value = clickedIndex
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

// Watch filters for changes
watch(filters, async () => {
  currentPage.value = 0
  selectedRows.value = [] // Clear selection when filters change
  lastSelectedIndex.value = null // Reset shift-click tracking
  await loadGPSPoints()
  await loadSummaryStats()
}, { deep: true })
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

.date-picker-wide {
  max-width: 500px !important;
  min-width: 400px;
}

.filter-group-wide {
  flex: 1;
  max-width: 600px;
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

.gps-data-table :deep(.selection-col) {
  width: 3rem;
  min-width: 3rem;
  text-align: center;
}

.gps-data-table :deep(.actions-col) {
  min-width: 80px;
  width: 80px;
  text-align: center;
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
    min-width: 90px;
    width: 90px;
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
  /* Override PageContainer padding for mobile with balanced margins */
  :deep(.gp-page-container--fullwidth) {
    padding-left: var(--gp-spacing-md) !important;
    padding-right: var(--gp-spacing-md) !important;
    max-width: 100vw !important;
    overflow-x: hidden;
    box-sizing: border-box;
  }

  /* Override page content container */
  :deep(.gp-page-content) {
    max-width: 100% !important;
    overflow-x: hidden;
    box-sizing: border-box;
  }

  /* Force all content to respect viewport width */
  .stats-grid,
  .filter-section,
  .table-section {
    max-width: 100%;
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
    box-sizing: border-box;
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

  .date-picker,
  .date-picker-wide {
    max-width: 100%;
    width: 100%;
    min-width: unset;
  }

  .filter-group-wide {
    max-width: 100%;
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
    min-width: 90px;
    width: 90px;
  }

  .gps-data-table :deep(.coordinates-col) {
    min-width: 100px;
    width: 100px;
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

.p-dark .gps-data-table :deep(.p-paginator .p-paginator-page.p-highlight),
.p-dark .gps-data-table :deep(.p-paginator .p-paginator-page-selected) {
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

/* Light mode selected page styling */
.gps-data-table :deep(.p-paginator .p-paginator-page.p-highlight),
.gps-data-table :deep(.p-paginator .p-paginator-page-selected) {
  background: var(--gp-primary) !important;
  color: white !important;
  border-color: var(--gp-primary) !important;
}

/* Paginator styling */
.gps-data-table :deep(.p-paginator .p-paginator-pages) {
  display: flex;
  align-items: center;
  gap: 4px;
}

.gps-data-table :deep(.p-paginator .p-paginator-page) {
  min-width: 2.5rem;
  height: 2.5rem;
}

.paginator-info {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  padding: 0 0.5rem;
}

.p-dark .paginator-info {
  color: var(--gp-text-secondary);
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

/* Actions Column */
.actions-buttons {
  display: flex;
  gap: var(--gp-spacing-xs);
  justify-content: center;
  align-items: center;
}

.action-button {
  min-width: 32px !important;
  width: 32px !important;
  height: 32px !important;
  padding: 0 !important;
  border-radius: var(--gp-radius-small);
  transition: all 0.2s ease;
}

.edit-button:hover {
  background-color: var(--gp-primary-light) !important;
  color: var(--gp-primary) !important;
}

.delete-button:hover {
  background-color: var(--p-red-50) !important;
  color: var(--p-red-600) !important;
}

/* Confirmation Dialog */
.confirm-dialog-content {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-md);
  padding: var(--gp-spacing-md) 0;
}

.confirm-icon {
  color: var(--p-orange-500);
  font-size: 1.5rem;
  flex-shrink: 0;
}

/* Mobile Actions */
@media (max-width: 768px) {
  .gps-data-table :deep(.selection-col) {
    width: 2.5rem;
    min-width: 2.5rem;
  }

  .gps-data-table :deep(.actions-col) {
    min-width: 70px;
    width: 70px;
  }
  
  .actions-buttons {
    gap: var(--gp-spacing-xs);
  }
  
  .action-button {
    min-width: 28px !important;
    width: 28px !important;
    height: 28px !important;
    font-size: 0.75rem !important;
  }
}

/* Header Actions */
.header-actions {
  display: flex;
  gap: var(--gp-spacing-md);
  align-items: center;
}

.bulk-delete-button {
  animation: fadeInScale 0.2s ease;
}

@keyframes fadeInScale {
  from {
    opacity: 0;
    transform: scale(0.9);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

/* Mobile header actions */
@media (max-width: 768px) {
  .header-actions {
    flex-direction: row;
    flex-wrap: wrap;
    gap: var(--gp-spacing-sm);
    width: 100%;
    justify-content: flex-start;
  }

  .bulk-delete-button {
    flex: 0 0 auto;
    width: auto;
    min-width: 120px;
    max-width: 140px;
    order: 1;
  }

  .header-actions > button:not(.bulk-delete-button) {
    flex: 0 0 auto;
    width: auto;
    min-width: 100px;
    max-width: 120px;
    order: 2;
  }
}

/* Filter Enhancements */
.filter-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--gp-spacing-md);
  padding-bottom: var(--gp-spacing-sm);
  border-bottom: 1px solid var(--gp-border-light);
}

.filter-title {
  font-size: 1.1rem;
  font-weight: 600;
  margin: 0;
  color: var(--gp-text-primary);
}

.filter-header-actions {
  display: flex;
  gap: var(--gp-spacing-sm);
  align-items: center;
}

.quick-presets {
  display: flex;
  gap: var(--gp-spacing-sm);
  margin-bottom: var(--gp-spacing-md);
  flex-wrap: wrap;
}

.advanced-filters {
  margin-top: var(--gp-spacing-md);
  padding-top: var(--gp-spacing-md);
  border-top: 1px solid var(--gp-border-light);
}

.filter-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: var(--gp-spacing-md);
  margin-bottom: var(--gp-spacing-md);
}

.filter-field {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-xs);
}

.range-inputs {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
}

.range-input {
  flex: 1;
}

.range-separator {
  color: var(--gp-text-muted);
  font-size: 0.9rem;
}

.filter-input {
  width: 100%;
}

.active-filter-chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gp-spacing-sm);
  margin-top: var(--gp-spacing-md);
  padding-top: var(--gp-spacing-md);
  border-top: 1px solid var(--gp-border-light);
}

/* Filtered Banner */
.filtered-banner {
  margin-bottom: var(--gp-spacing-lg);
  background: var(--p-primary-50);
  border-left: 4px solid var(--gp-primary);
}

.filtered-banner-content {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  padding: var(--gp-spacing-sm);
}

.filtered-banner-content i {
  color: var(--gp-primary);
  font-size: 1.2rem;
}

.filtered-text {
  font-weight: 500;
  color: var(--gp-text-primary);
}

/* Table Header Enhancements */
.table-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--gp-spacing-md);
  width: 100%;
}

.table-header-left {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  flex: 1;
  min-width: 0;
}

.table-header-right {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  flex-shrink: 0;
}

.page-size-label {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  white-space: nowrap;
}

.page-size-dropdown {
  min-width: 80px;
}

.filtered-info {
  color: var(--gp-text-muted);
  font-size: 0.85rem;
}

/* Selection Badge */
.selection-badge {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
  padding: var(--gp-spacing-xs) var(--gp-spacing-sm);
  background: var(--p-primary-50);
  border-radius: var(--gp-radius-small);
}

.selection-text {
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--gp-primary);
}

/* Mobile Responsive Filters */
@media (max-width: 768px) {
  .filter-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .filter-header-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .quick-presets {
    display: grid;
    grid-template-columns: 1fr 1fr;
    width: 100%;
  }

  .filter-row {
    grid-template-columns: 1fr;
  }

  .table-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .table-header-right {
    width: 100%;
    justify-content: space-between;
  }
}

/* Dark Mode Filters */
.p-dark .filter-header {
  border-color: var(--gp-border-dark);
}

.p-dark .advanced-filters {
  border-color: var(--gp-border-dark);
}

.p-dark .active-filter-chips {
  border-color: var(--gp-border-dark);
}

.p-dark .filtered-banner {
  background: var(--p-primary-900);
  border-color: var(--gp-primary);
}

.p-dark .filter-input :deep(.p-chip) {
  background: var(--p-surface-800) !important;
  color: var(--p-text-color) !important;
}

.p-dark .filter-input :deep(.p-chip .p-chip-remove-icon) {
  color: var(--p-text-color) !important;
}
</style>