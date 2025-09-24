<template>
  <PageContainer 
    title="Timeline Reports"
    subtitle="Explore your location data with detailed tables and export options"
    :loading="isAnyLoading"
  >
    <!-- Loading States -->
    <template v-if="isAnyLoading">
      <DashboardGrid>
        <div class="gp-loading-placeholder" v-for="i in 3" :key="i">
          <ProgressSpinner size="small" />
        </div>
      </DashboardGrid>
    </template>

    <!-- Data Tables Content -->
    <template v-else>
      <!-- Header with Date Range and Quick Stats -->
      <div class="tables-header">
        <div class="date-range-info">
          <i class="pi pi-calendar"></i>
          <span>{{ formattedDateRange }}</span>
        </div>
        <Button
          label="Export All Data"
          icon="pi pi-download"
          @click="exportAllData"
          outlined
          class="export-all-button"
        />
        <div class="quick-stats">
          <MetricItem 
            :value="totalStays" 
            label="Stays" 
            icon="pi pi-map-marker"
            class="stat-item"
          />
          <MetricItem 
            :value="totalTrips" 
            label="Trips" 
            icon="pi pi-car"
            class="stat-item"
          />
          <MetricItem 
            :value="totalDataGaps" 
            label="Data Gaps" 
            icon="pi pi-exclamation-triangle"
            class="stat-item"
          />
        </div>
      </div>

      <!-- Tab Container for Different Table Views -->
      <TabContainer
        v-if="!timelineDataLoading"
        :key="`tabs-${timelineData?.length || 0}-${activeTab}`"
        :tabs="tableTabs"
        :activeIndex="activeTabIndex"
        @tab-change="handleTabChange"
        class="data-tables-tabs"
      >
        <!-- Stays Table Tab -->
        <div v-if="activeTab === 'stays'" class="table-tab-content">
          <StaysTable
            :stays="filteredStays"
            :dateRange="dateRange"
            :loading="staysLoading"
            @export="exportStays"
            @row-select="handleRowSelect"
          />
        </div>

        <!-- Trips Table Tab -->
        <div v-if="activeTab === 'trips'" class="table-tab-content">
          <TripsTable
            :trips="filteredTrips"
            :stays="filteredStays"
            :dateRange="dateRange"
            :loading="tripsLoading"
            @export="exportTrips"
            @row-select="handleRowSelect"
          />
        </div>

        <!-- Data Gaps Table Tab -->
        <div v-if="activeTab === 'data-gaps'" class="table-tab-content">
          <DataGapsTable
            :dataGaps="filteredDataGaps"
            :dateRange="dateRange"
            :loading="dataGapsLoading"
            @export="exportDataGaps"
            @analyze="analyzeDataGap"
            @row-select="handleRowSelect"
          />
        </div>
      </TabContainer>

      <!-- No Data State -->
      <div v-if="timelineNoData && !timelineDataLoading" class="no-data-state">
        <BaseCard class="no-data-card">
          <div class="no-data-content">
            <i class="pi pi-database no-data-icon"></i>
            <h3 class="no-data-title">No Data Available</h3>
            <p class="no-data-message">
              No location data found for the selected date range.
            </p>
            <p class="no-data-suggestion">
              Try selecting a different date range or visit the Timeline page to generate data.
            </p>
          </div>
        </BaseCard>
      </div>
    </template>
  </PageContainer>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import ProgressSpinner from 'primevue/progressspinner'

// Layout Components
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import DashboardGrid from '@/components/ui/layout/DashboardGrid.vue'
import TabContainer from '@/components/ui/layout/TabContainer.vue'
import MetricItem from '@/components/ui/data/MetricItem.vue'

// Data Table Components
import StaysTable from '@/components/datatables/StaysTable.vue'
import TripsTable from '@/components/datatables/TripsTable.vue'
import DataGapsTable from '@/components/datatables/DataGapsTable.vue'

// Composables and Stores
import { useTimezone } from '@/composables/useTimezone'
import { useTimelineStore } from '@/stores/timeline'
import { useDateRangeStore } from '@/stores/dateRange'
import { DataExporter } from '@/utils/dataExporter'

const timezone = useTimezone()
const timelineStore = useTimelineStore()
const dateRangeStore = useDateRangeStore()
const toast = useToast()

// Store refs
const { timelineData } = storeToRefs(timelineStore)
const { dateRange, startDate, endDate, isValidRange } = storeToRefs(dateRangeStore)

// Local state
const activeTab = ref('stays')
const staysLoading = ref(false)
const tripsLoading = ref(false)
const dataGapsLoading = ref(false)
const timelineDataLoading = ref(false)
const timelineNoData = ref(false)

// Computed
const tableTabs = computed(() => [
  { label: 'Stays', icon: 'pi pi-map-marker', key: 'stays' },
  { label: 'Trips', icon: 'pi pi-car', key: 'trips' },
  { label: 'Data Gaps', icon: 'pi pi-exclamation-triangle', key: 'data-gaps' },
])

const activeTabIndex = computed(() => {
  return tableTabs.value.findIndex(tab => tab.key === activeTab.value)
})

const isAnyLoading = computed(() => {
  return staysLoading.value || tripsLoading.value || dataGapsLoading.value || timelineDataLoading.value
})

const formattedDateRange = computed(() => {
  if (!dateRange.value || !dateRange.value[0] || !dateRange.value[1]) return 'No date range selected'
  return `${timezone.format(dateRange.value[0], 'MMM DD')} - ${timezone.format(dateRange.value[1], 'MMM DD, YYYY')}`
})

const totalStays = computed(() => timelineStore.getStays?.length || 0)
const totalTrips = computed(() => timelineStore.getTrips?.length || 0)
const totalDataGaps = computed(() => timelineStore.getDataGaps?.length || 0)

const filteredStays = computed(() => {
  return timelineStore.getStays || []
})

const filteredTrips = computed(() => {
  return timelineStore.getTrips || []
})

const filteredDataGaps = computed(() => {
  return timelineStore.getDataGaps || []
})


// Methods
const handleTabChange = async (event) => {
  try {
    const selectedTab = tableTabs.value[event.index]
    if (selectedTab) {
      activeTab.value = selectedTab.key
      await nextTick()
    }
  } catch (error) {
    console.error('TimelineReportsPage tab change error:', error)
  }
}

// Data Loading
const fetchTimelineData = async () => {
  if (!startDate.value || !endDate.value) return
  
  timelineDataLoading.value = true
  timelineNoData.value = false

  try {
    await timelineStore.fetchMovementTimeline(startDate.value, endDate.value)

    if (timelineData.value == null || timelineData.value.length === 0) {
      timelineNoData.value = true
    }
  } catch (error) {
    console.error('Error fetching timeline data:', error)
    const errorMessage = error.response?.data?.message || error.message || error.toString()
    toast.add({
      severity: 'error',
      summary: 'Failed to fetch data',
      detail: errorMessage,
      life: 5000
    })
    timelineNoData.value = true
  } finally {
    timelineDataLoading.value = false
  }
}

const exportStays = async () => {
  try {
    await DataExporter.exportStays(filteredStays.value, dateRange.value)
    toast.add({
      severity: 'success',
      summary: 'Export Successful',
      detail: 'Stays data exported to CSV',
      life: 3000
    })
  } catch (error) {
    console.error('Export error:', error)
    toast.add({
      severity: 'error', 
      summary: 'Export Failed',
      detail: error.message || 'Failed to export stays data',
      life: 5000
    })
  }
}

const exportTrips = async () => {
  try {
    await DataExporter.exportTrips(filteredStays.value, filteredTrips.value, dateRange.value)
    toast.add({
      severity: 'success',
      summary: 'Export Successful', 
      detail: 'Trips data exported to CSV',
      life: 3000
    })
  } catch (error) {
    console.error('Export error:', error)
    toast.add({
      severity: 'error',
      summary: 'Export Failed',
      detail: error.message || 'Failed to export trips data',
      life: 5000
    })
  }
}

const exportDataGaps = async () => {
  try {
    await DataExporter.exportDataGaps(filteredDataGaps.value, dateRange.value)
    toast.add({
      severity: 'success',
      summary: 'Export Successful',
      detail: 'Data gaps exported to CSV', 
      life: 3000
    })
  } catch (error) {
    console.error('Export error:', error)
    toast.add({
      severity: 'error',
      summary: 'Export Failed',
      detail: error.message || 'Failed to export data gaps',
      life: 5000
    })
  }
}

const exportAllData = async () => {
  try {
    await Promise.all([
      DataExporter.exportStays(filteredStays.value, dateRange.value),
      DataExporter.exportTrips(filteredStays.value, filteredTrips.value, dateRange.value),
      DataExporter.exportDataGaps(filteredDataGaps.value, dateRange.value)
    ])
    toast.add({
      severity: 'success',
      summary: 'Export Successful',
      detail: 'All data exported to CSV files',
      life: 3000
    })
  } catch (error) {
    console.error('Export error:', error)
    toast.add({
      severity: 'error',
      summary: 'Export Failed',
      detail: error.message || 'Failed to export data',
      life: 5000
    })
  }
}

// Initialize when date range changes
watch(dateRange, async (newValue) => {
  if (newValue && isValidRange.value) {
    await fetchTimelineData()
  }
}, { immediate: true })

// Lifecycle
onMounted(async () => {
  // Load data if we already have a date range
  if (dateRange.value && isValidRange.value) {
    await fetchTimelineData()
  }
})
</script>

<style scoped>
/* Header Styles */
.tables-header {
  display: flex;
  align-items: center;
  margin-bottom: var(--gp-spacing-xl);
  padding: var(--gp-spacing-lg);
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-large);
  gap: var(--gp-spacing-lg);
}

.date-range-info {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  color: var(--gp-text-secondary);
  font-weight: 500;
  flex-shrink: 0;
  flex: 0 0 auto;
  min-width: 200px;
}

.date-range-info i {
  color: var(--gp-primary);
}

.export-all-button {
  flex-shrink: 0;
  font-weight: 600;
  flex: 0 0 auto;
}

.quick-stats {
  display: flex;
  gap: var(--gp-spacing-lg);
  flex: 1;
  justify-content: flex-end;
  min-width: 0;
}

.stat-item {
  flex: 1;
  min-width: 120px;
  max-width: 160px;
}

/* Ensure all quick-stats metric items have underline */
.quick-stats .stat-item {
  border-bottom: 1px solid var(--gp-border-light);
}

/* Dark mode support for quick-stats underlines */
.p-dark .quick-stats .stat-item {
  border-bottom-color: var(--gp-border-dark);
}

/* Data Tables Tabs */
.data-tables-tabs {
  margin-bottom: var(--gp-spacing-lg);
}

.table-tab-content {
  padding-top: var(--gp-spacing-md);
}

/* Loading Placeholders */
.gp-loading-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
}

/* Dark Mode */
.p-dark .tables-header {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark .gp-loading-placeholder {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

/* Responsive Design */
@media (max-width: 768px) {
  .tables-header {
    flex-direction: column;
    gap: var(--gp-spacing-md);
    align-items: stretch;
  }

  .date-range-info {
    justify-content: center;
    min-width: auto;
  }

  .export-all-button {
    align-self: center;
  }

  .quick-stats {
    justify-content: space-around;
    gap: var(--gp-spacing-md);
    flex-wrap: wrap;
  }
  
  .stat-item {
    flex: 1 1 auto;
    min-width: 100px;
    max-width: none;
  }
}

@media (max-width: 480px) {
  .tables-header {
    padding: var(--gp-spacing-md);
  }

  .quick-stats {
    flex-direction: column;
    gap: var(--gp-spacing-sm);
  }
}

/* No Data State */
.no-data-state {
  margin-top: var(--gp-spacing-xl);
}

.no-data-card {
  text-align: center;
  padding: var(--gp-spacing-xl);
}

.no-data-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--gp-spacing-md);
  max-width: 500px;
  margin: 0 auto;
}

.no-data-icon {
  font-size: 4rem;
  color: var(--gp-text-muted);
  opacity: 0.5;
}

.no-data-title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--gp-text-secondary);
}

.no-data-message {
  margin: 0;
  font-size: 1rem;
  color: var(--gp-text-secondary);
  line-height: 1.5;
}

.no-data-suggestion {
  margin: 0;
  font-size: 0.875rem;
  color: var(--gp-text-muted);
  line-height: 1.5;
  font-style: italic;
}

/* Dark Mode */
.p-dark .no-data-title {
  color: var(--gp-text-primary);
}

.p-dark .no-data-message {
  color: var(--gp-text-secondary);
}

.p-dark .no-data-suggestion {
  color: var(--gp-text-muted);
}
</style>