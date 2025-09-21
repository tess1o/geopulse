<template>
  <PageContainer 
    title="Dashboard" 
    subtitle="Overview of your location data and analytics"
    :loading="isAnyLoading"
  >
    <!-- Loading States -->
    <template v-if="isAnyLoading">
      <DashboardGrid>
        <div class="gp-loading-placeholder" v-for="i in 9" :key="i">
          <ProgressSpinner size="small" />
        </div>
      </DashboardGrid>
    </template>

    <!-- Dashboard Content -->
    <template v-else>
      <!-- Selected Period Row (3 cards) -->
      <DashboardGrid v-if="hasSelectedRangeStats" class="dashboard-section" :columns="3">
        <ActivitySummaryCard
          title="Selected Period Summary"
          :period="formattedSelectedPeriodRange"
          :stats="statsUserRange"
          variant="default"
          :showChart="hasSelectedChartData"
        />

        <ActivitySummaryCard
            title="7 Days Overview"
            :period="formattedLastWeekRange"
            :stats="statsSevenDays"
            variant="default"
            :showChart="hasSevenDaysChartData"
        />

        <ActivitySummaryCard
            title="30 Days Overview"
            :period="formattedLastMonthRange"
            :stats="statsThirtyDays"
            variant="default"
            :showChart="hasThirtyDaysChartData"
        />
      </DashboardGrid>

      <!-- 7 Days Overview Row (3 cards) -->
      <DashboardGrid v-if="hasSevenDaysStats" class="dashboard-section" :columns="3">

        <BaseCard title="Top Places" :period="formattedSelectedPeriodRange">
          <TopPlacesContent :places="statsUserRange.places" />
        </BaseCard>
        
        <BaseCard title="Top Places" :period="formattedLastWeekRange">
          <TopPlacesContent :places="statsSevenDays.places" />
        </BaseCard>

        <BaseCard title="Top Places" :period="formattedLastMonthRange">
          <TopPlacesContent :places="statsThirtyDays.places" />
        </BaseCard>
      </DashboardGrid>

      <!-- 30 Days Overview Row (3 cards) -->
      <DashboardGrid v-if="hasThirtyDaysStats" class="dashboard-section" :columns="3">

        <BaseCard title="Route Analysis" :period="formattedSelectedPeriodRange">
          <RouteAnalysisContent :stats="statsUserRange.routes" />
        </BaseCard>


        <BaseCard title="Route Analysis" :period="formattedLastWeekRange">
          <RouteAnalysisContent :stats="statsSevenDays.routes" />
        </BaseCard>
        
        <BaseCard title="Route Analysis" :period="formattedLastMonthRange">
          <RouteAnalysisContent :stats="statsThirtyDays.routes" />
        </BaseCard>
      </DashboardGrid>

      <!-- Empty State -->
      <BaseCard v-if="!hasAnyStats" variant="subtle">
        <div class="empty-dashboard">
          <i class="pi pi-chart-line empty-icon"></i>
          <h3 class="empty-title">No Data Available</h3>
          <p class="empty-message">
            No location data found for the selected time periods. 
            Check your GPS sources or select a different date range.
          </p>
        </div>
      </BaseCard>
    </template>
  </PageContainer>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useToast } from "primevue/usetoast"
import ProgressSpinner from 'primevue/progressspinner'

// New Layout Components
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import DashboardGrid from '@/components/ui/layout/DashboardGrid.vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import MetricItem from '@/components/ui/data/MetricItem.vue'

// Dashboard Content Components
import TopPlacesContent from '@/components/dashboard/cards/TopPlacesContent.vue'
import RouteAnalysisContent from '@/components/dashboard/cards/RouteAnalysisContent.vue'
import ActivitySummaryCard from '@/components/dashboard/cards/ActivitySummaryCard.vue'

// Utils and Stores
import { useTimezone } from '@/composables/useTimezone'
import { formatDistance, formatDuration, formatSpeed } from '@/utils/calculationsHelpers'

const timezone = useTimezone()
import { useStatisticsStore } from '@/stores/statistics'
import { useDateRangeStore } from '@/stores/dateRange'

const statisticsStore = useStatisticsStore()
const dateRangeStore = useDateRangeStore()

const {
  selectedRangeStatistics: statsUserRange,
  weeklyStatistics: statsSevenDays,
  monthlyStatistics: statsThirtyDays,
  hasSelectedRangeData: hasSelectedRangeStats,
  hasWeeklyData: hasSevenDaysStats,
  hasMonthlyData: hasThirtyDaysStats
} = storeToRefs(statisticsStore)

const { dateRange, startDate, endDate, isValidRange } = storeToRefs(dateRangeStore)

// Local reactive state
const toast = useToast()
const selectedDateRangeLoading = ref(false)
const sevenDaysLoading = ref(false)
const thirtyDaysLoading = ref(false)

// Computed properties
const isAnyLoading = computed(() => {
  return selectedDateRangeLoading.value || sevenDaysLoading.value || thirtyDaysLoading.value
})

const hasAnyStats = computed(() => {
  return hasSelectedRangeStats.value || hasSevenDaysStats.value || hasThirtyDaysStats.value
})

const hasSelectedChartData = computed(() => {
  return statsUserRange.value?.distanceCarChart?.data?.length > 0 || statsUserRange.value?.distanceWalkChart?.data?.length > 0
})

const hasSevenDaysChartData = computed(() => {
  return statsSevenDays.value?.distanceCarChart?.data?.length > 0 || statsSevenDays.value?.distanceWalkChart?.data?.length > 0
})

const hasThirtyDaysChartData = computed(() => {
  return statsThirtyDays.value?.distanceCarChart?.data?.length > 0 || statsThirtyDays.value?.distanceWalkChart?.data?.length > 0
})

const formattedSelectedPeriodRange = computed(() => {
  return formatRange([dateRange.value?.[0], dateRange.value?.[1]]);
})

const formattedLastWeekRange = computed(() => {
  const range = timezone.getLastWeekRange()
  return formatRange([range.start, range.end])
})

const formattedLastMonthRange = computed(() => {
  const range = timezone.getLastMonthRange()
  return formatRange([range.start, range.end])
})

// Methods
const formatRange = (range) => {
  const [start, end] = range || []
  return start && end
      ? `${timezone.format(start, 'MM/DD')} - ${timezone.format(end, 'MM/DD')}`
      : ''
}

const getStatsUserRange = async () => {
  if (!startDate.value || !endDate.value) return

  try {
    selectedDateRangeLoading.value = true
    await statisticsStore.fetchSelectedRangeStatistics(
        startDate.value,
        endDate.value
    )
  } catch (error) {
    console.error('Error fetching selected range stats:', error)
    toast.add({
      severity: 'error',
      summary: 'Failed to fetch selected range stats',
      detail: error.message,
      life: 3000
    })
  } finally {
    selectedDateRangeLoading.value = false
  }
}

const getStatsSevenDays = async () => {
  try {
    sevenDaysLoading.value = true
    await statisticsStore.fetchWeeklyStatistics()
  } catch (error) {
    console.error('Error fetching weekly stats:', error)
    toast.add({
      severity: 'error',
      summary: 'Failed to fetch weekly stats',
      detail: error.message,
      life: 3000
    })
  } finally {
    sevenDaysLoading.value = false
  }
}

const getStatsThirtyDays = async () => {
  try {
    thirtyDaysLoading.value = true
    await statisticsStore.fetchMonthlyStatistics()
  } catch (error) {
    console.error('Error fetching monthly stats:', error)
    toast.add({
      severity: 'error',
      summary: 'Failed to fetch monthly stats',
      detail: error.message,
      life: 3000
    })
  } finally {
    thirtyDaysLoading.value = false
  }
}

const initializeComponent = async () => {
  await Promise.all([
    getStatsUserRange(),
    getStatsSevenDays(),
    getStatsThirtyDays()
  ])
}

// Watchers
watch(
    dateRange,
    async (newValue) => {
      if (newValue && isValidRange.value) {
        await initializeComponent()
      }
    },
    { immediate: true }
)

// Lifecycle
onMounted(() => {
  // Any additional initialization if needed
})
</script>

<style scoped>
/* Dashboard Sections */
.dashboard-section {
  margin-bottom: var(--gp-spacing-xl);
}

.dashboard-section:last-child {
  margin-bottom: 0;
}


/* Empty Dashboard */
.empty-dashboard {
  text-align: center;
  padding: var(--gp-spacing-xxl, 3rem) var(--gp-spacing-lg);
}

.empty-icon {
  font-size: 3rem;
  color: var(--gp-text-muted);
  margin-bottom: var(--gp-spacing-lg);
  display: block;
}

.empty-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--gp-text-secondary);
  margin: 0 0 var(--gp-spacing-md);
}

.empty-message {
  font-size: 0.875rem;
  color: var(--gp-text-muted);
  margin: 0;
  max-width: 400px;
  margin-left: auto;
  margin-right: auto;
  line-height: 1.5;
}

/* Dark Mode */
.p-dark .empty-icon {
  color: var(--gp-text-muted);
}

.p-dark .empty-title {
  color: var(--gp-text-secondary);
}

.p-dark .empty-message {
  color: var(--gp-text-muted);
}


/* Responsive adjustments */
@media (max-width: 768px) {
  .dashboard-section {
    margin-bottom: var(--gp-spacing-lg);
  }
  
  .empty-dashboard {
    padding: var(--gp-spacing-xl) var(--gp-spacing-md);
  }
  
  .empty-icon {
    font-size: 2.5rem;
  }
  
  .empty-title {
    font-size: 1.125rem;
  }
}
</style>