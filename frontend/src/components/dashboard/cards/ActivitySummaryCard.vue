<template>
  <BaseCard 
    :title="title" 
    :period="period"
    :variant="variant"
  >
    <div class="activity-metrics-grid">
      <!-- Total Distance -->
      <MetricItem
        icon="pi pi-map-marker"
        iconColor="primary"
        :value="stats.totalDistanceMeters || 0"
        label="Total Distance"
        :formatter="formatDistance"
        variant="minimal"
      />
      
      <!-- Time Moving -->
      <MetricItem
        icon="pi pi-clock"
        iconColor="secondary"
        :value="stats.timeMoving || 0"
        label="Time Moving"
        :formatter="formatDuration"
        variant="minimal"
      />
      
      <!-- Daily Average -->
      <MetricItem
        icon="pi pi-chart-line"
        iconColor="info"
        :value="stats.dailyAverageDistanceMeters || 0"
        label="Daily Average"
        :formatter="formatDistance"
        variant="minimal"
      />
      
      <!-- Average Speed -->
      <MetricItem
        icon="pi pi-send"
        iconColor="warning"
        :value="stats.averageSpeed || 0"
        label="Average Speed"
        :formatter="formatSpeed"
        variant="minimal"
      />
      
      <!-- Most Active Day -->
      <div 
        v-tooltip="{value: mostActiveDayTooltip, escape: false, pt: {text: 'tooltip-content'}}"
        class="tooltip-wrapper"
      >
        <MetricItem
          icon="pi pi-star"
          iconColor="success"
          :value="stats.mostActiveDay?.day || 'N/A'"
          label="Most Active Day"
          variant="minimal"
        />
      </div>
      
      <!-- Unique Locations -->
      <MetricItem
        icon="pi pi-map"
        iconColor="muted"
        :value="stats.uniqueLocationsCount || 0"
        label="Unique Locations"
        variant="minimal"
      />
    </div>
    
    <!-- Chart Section -->
    <div v-if="showChart && hasChartData" class="chart-section">
      <h4 class="chart-title">Distance Activity</h4>
      <BarChart
        title="Distance, km"
        :labels="chartLabels"
        :datasets="chartDatasets"
        class="activity-chart"
      />
    </div>
  </BaseCard>
</template>

<script setup>
import { computed } from 'vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import MetricItem from '@/components/ui/data/MetricItem.vue'
import BarChart from '@/components/charts/BarChart.vue'
import { formatDistance, formatDuration, formatSpeed } from '@/utils/calculationsHelpers'

const props = defineProps({
  title: {
    type: String,
    required: true
  },
  period: {
    type: String,
    required: true
  },
  stats: {
    type: Object,
    required: true,
    default: () => ({
      totalDistanceMeters: 0,
      timeMoving: 0,
      dailyAverageDistanceMeters: 0,
      averageSpeed: 0,
      uniqueLocationsCount: 0,
      mostActiveDay: null,
      distanceChartsByTripType: {}
    })
  },
  variant: {
    type: String,
    default: 'default',
    validator: (value) => ['default', 'highlighted', 'subtle'].includes(value)
  },
  showChart: {
    type: Boolean,
    default: false
  }
})

// Trip type display configuration
const tripTypeConfig = {
  WALK: { label: 'Walk', color: 'success' },
  BICYCLE: { label: 'Bicycle', color: 'warning' },
  CAR: { label: 'Car', color: 'primary' },
  TRAIN: { label: 'Train', color: 'secondary' },
  FLIGHT: { label: 'Flight', color: 'danger' }
}

const hasChartData = computed(() => {
  const chartsByType = props.stats?.distanceChartsByTripType || {}
  return Object.values(chartsByType).some(chart =>
    chart?.data?.length > 0 && chart.data.some(value => value > 0)
  )
})

const chartLabels = computed(() => {
  const chartsByType = props.stats?.distanceChartsByTripType || {}

  // Collect all labels from trip types that have data (filter out empty ones)
  const allLabels = Object.values(chartsByType)
    .filter(chart => chart?.labels?.length > 0 && chart?.data?.length > 0)
    .flatMap(chart => chart.labels)

  // Merge unique labels
  const uniqueLabels = [...new Set(allLabels)]

  if (uniqueLabels.length === 0) return []

  // Define day order for sorting
  const dayOrder = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']

  // Check if labels are day abbreviations or date strings (MM/DD format)
  const isDateFormat = uniqueLabels.some(label => /^\d{2}\/\d{2}$/.test(label))

  if (isDateFormat) {
    // Sort by date (MM/DD format)
    return uniqueLabels.sort((a, b) => {
      const [aMonth, aDay] = a.split('/').map(Number)
      const [bMonth, bDay] = b.split('/').map(Number)
      return aMonth !== bMonth ? aMonth - bMonth : aDay - bDay
    })
  } else {
    // Sort by day of week
    return uniqueLabels.sort((a, b) => {
      const aIndex = dayOrder.indexOf(a.toUpperCase())
      const bIndex = dayOrder.indexOf(b.toUpperCase())
      // Handle unknown day names by putting them at the end
      if (aIndex === -1) return 1
      if (bIndex === -1) return -1
      return aIndex - bIndex
    })
  }
})

const chartDatasets = computed(() => {
  const datasets = []
  const mergedLabels = chartLabels.value
  const chartsByType = props.stats?.distanceChartsByTripType || {}

  // Process each trip type
  Object.entries(chartsByType).forEach(([tripType, chartData]) => {
    if (!chartData || !chartData.data || chartData.data.length === 0) return

    const labels = chartData.labels || []
    const data = chartData.data || []

    // Create label-to-value map for this trip type
    const dataMap = new Map(labels.map((label, i) => [label, data[i] || 0]))

    // Get configuration for this trip type
    const config = tripTypeConfig[tripType] || { label: tripType, color: 'secondary' }

    datasets.push({
      label: `${config.label}`,
      data: mergedLabels.map(label => dataMap.get(label) || 0),
      color: config.color
    })
  })

  return datasets
})

const mostActiveDayTooltip = computed(() => {
  const data = props.stats?.mostActiveDay
  if (!data) return ''
  
  return `
    <div class="most-active-day-tooltip">
      <div class="tooltip-header">
        <strong>${data.day}</strong>
        <span class="tooltip-date">${data.date}</span>
      </div>
      <div class="tooltip-content">
        <div class="tooltip-item">
          <i class="pi pi-map-marker"></i>
          <span>Distance: ${formatDistance(data.distanceTraveled)}</span>
        </div>
        <div class="tooltip-item">
          <i class="pi pi-clock"></i>
          <span>Travel Time: ${formatDuration(data.travelTime)}</span>
        </div>
        <div class="tooltip-item">
          <i class="pi pi-map"></i>
          <span>Locations: ${data.locationsVisited}</span>
        </div>
      </div>
    </div>
  `.trim()
})
</script>

<style scoped>
/* Activity Metrics Grid - 2x3 layout like original */
.activity-metrics-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--gp-spacing-lg) var(--gp-spacing-xl);
  margin-bottom: var(--gp-spacing-lg);
}

/* Chart Section */
.chart-section {
  margin-top: var(--gp-spacing-lg);
  padding-top: var(--gp-spacing-lg);
  border-top: 1px solid var(--gp-border-light);
}

.chart-title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 var(--gp-spacing-md);
}

.activity-chart {
  height: 180px;
  width: 100%;
}

/* Dark Mode */
.p-dark .chart-section {
  border-top-color: var(--gp-border-dark);
}

.p-dark .chart-title {
  color: var(--gp-text-primary);
}

/* Tooltip Wrapper */
.tooltip-wrapper {
  cursor: help;
}

/* Global Tooltip Styles */
:global(.p-tooltip .p-tooltip-text) {
  padding: 0 !important;
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
}

:global(.most-active-day-tooltip) {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  padding: var(--gp-spacing-md);
  box-shadow: var(--gp-shadow-medium);
  min-width: 200px;
  font-family: var(--gp-font-family);
}

:global(.most-active-day-tooltip .tooltip-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--gp-spacing-sm);
  padding-bottom: var(--gp-spacing-xs);
  border-bottom: 1px solid var(--gp-border-light);
}

:global(.most-active-day-tooltip .tooltip-header strong) {
  color: var(--gp-text-primary);
  font-weight: 600;
  font-size: 0.875rem;
}

:global(.most-active-day-tooltip .tooltip-date) {
  color: var(--gp-text-secondary);
  font-size: 0.75rem;
  font-weight: 500;
}

:global(.most-active-day-tooltip .tooltip-content) {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-xs);
}

:global(.most-active-day-tooltip .tooltip-item) {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
}

:global(.most-active-day-tooltip .tooltip-item i) {
  width: 12px;
  font-size: 0.75rem;
  color: var(--gp-text-muted);
}

:global(.most-active-day-tooltip .tooltip-item span) {
  font-weight: 500;
}

/* Dark Mode Tooltip Styles */
:global(.p-dark .most-active-day-tooltip) {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
  box-shadow: var(--gp-shadow-dark);
}

:global(.p-dark .most-active-day-tooltip .tooltip-header) {
  border-bottom-color: var(--gp-border-dark);
}

:global(.p-dark .most-active-day-tooltip .tooltip-header strong) {
  color: var(--gp-text-primary);
}

:global(.p-dark .most-active-day-tooltip .tooltip-date) {
  color: var(--gp-text-secondary);
}

:global(.p-dark .most-active-day-tooltip .tooltip-item) {
  color: var(--gp-text-secondary);
}

:global(.p-dark .most-active-day-tooltip .tooltip-item i) {
  color: var(--gp-text-muted);
}

/* Responsive adjustments */
@media (max-width: 768px) and (min-width: 430px) {
  /* Large phones like iPhone 16 Pro Max - keep 2 columns */
  .activity-metrics-grid {
    grid-template-columns: 1fr 1fr;
    gap: var(--gp-spacing-md) var(--gp-spacing-lg);
  }
  
  .activity-chart {
    height: 200px;
  }
}

@media (max-width: 429px) {
  /* Smaller phones - single column */
  .activity-metrics-grid {
    grid-template-columns: 1fr;
    gap: var(--gp-spacing-md);
  }
  
  .activity-chart {
    height: 200px;
  }
}
</style>