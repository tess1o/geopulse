<template>
  <div class="digest-trends">
    <h3 class="trends-title">
      <i class="pi pi-chart-line"></i>
      Activity Trends
    </h3>

    <div v-if="hasChartData" class="chart-container">
      <BarChart
        :title="viewMode === 'monthly' ? 'Weekly Distance, km' : 'Monthly Distance, km'"
        :labels="chartLabels"
        :datasets="chartDatasets"
      />
    </div>

    <div class="no-trends-placeholder" v-else>
      <i class="pi pi-chart-line"></i>
      <p>No activity trends for this period.</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import BarChart from '@/components/charts/BarChart.vue'

// Trip type display configuration
const tripTypeConfig = {
  WALK: { label: 'Walk', color: 'success' },
  BICYCLE: { label: 'Bicycle', color: 'warning' },
  CAR: { label: 'Car', color: 'primary' },
  TRAIN: { label: 'Train', color: 'secondary' },
  FLIGHT: { label: 'Flight', color: 'danger' }
}

const props = defineProps({
  chartData: {
    type: Object,
    default: null
  },
  viewMode: {
    type: String,
    default: 'monthly'
  }
})

const hasChartData = computed(() => {
  if (!props.chartData) return false

  const chartsByType = props.chartData.chartsByTripType || {}

  // Check if at least one chart exists with valid data
  return Object.values(chartsByType).some(chart =>
    chart?.data?.length > 0 && chart.data.some(value => value > 0)
  )
})

const chartLabels = computed(() => {
  const chartsByType = props.chartData?.chartsByTripType || {}

  // Collect all labels from trip types that have data (filter out empty ones)
  const allLabels = Object.values(chartsByType)
    .filter(chart => chart?.labels?.length > 0 && chart?.data?.length > 0)
    .flatMap(chart => chart.labels)

  // Combine and deduplicate labels
  const uniqueLabels = [...new Set(allLabels)]

  // Sort chronologically based on view mode
  if (props.viewMode === 'yearly') {
    // For yearly view, sort months chronologically
    // Support both full and abbreviated month names
    const monthOrder = {
      'January': 0, 'Jan': 0,
      'February': 1, 'Feb': 1,
      'March': 2, 'Mar': 2,
      'April': 3, 'Apr': 3,
      'May': 4,
      'June': 5, 'Jun': 5,
      'July': 6, 'Jul': 6,
      'August': 7, 'Aug': 7,
      'September': 8, 'Sep': 8, 'Sept': 8,
      'October': 9, 'Oct': 9,
      'November': 10, 'Nov': 10,
      'December': 11, 'Dec': 11
    }
    uniqueLabels.sort((a, b) => {
      const orderA = monthOrder[a] ?? 999
      const orderB = monthOrder[b] ?? 999
      return orderA - orderB
    })
  } else {
    // For monthly view (weeks), sort naturally
    uniqueLabels.sort()
  }

  return uniqueLabels
})

const chartDatasets = computed(() => {
  const datasets = []
  const allLabels = chartLabels.value
  const chartsByType = props.chartData?.chartsByTripType || {}

  // Helper function to align data with merged labels - use 0 instead of null
  const alignDataWithLabels = (sourceLabels, sourceData) => {
    const labelDataMap = new Map()
    sourceLabels.forEach((label, index) => {
      labelDataMap.set(label, sourceData[index] || 0)
    })

    // Map data to all labels, use 0 for missing values
    return allLabels.map(label => labelDataMap.get(label) || 0)
  }

  // Process each trip type
  Object.entries(chartsByType).forEach(([tripType, chartData]) => {
    if (!chartData || !chartData.labels?.length || !chartData.data?.length) return

    const alignedData = alignDataWithLabels(chartData.labels, chartData.data)

    // Get configuration for this trip type
    const config = tripTypeConfig[tripType] || { label: tripType, color: 'secondary' }

    datasets.push({
      label: `${config.label}`,
      data: alignedData,
      color: config.color
    })
  })

  return datasets
})
</script>

<style scoped>
.digest-trends {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-large);
  padding: var(--gp-spacing-xl);
  margin-bottom: var(--gp-spacing-xl);
}

.trends-title {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  margin: 0 0 var(--gp-spacing-lg);
}

.trends-title i {
  color: var(--gp-secondary);
}

.chart-container {
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  padding: var(--gp-spacing-lg);
  height: 420px;
}

.no-trends-placeholder {
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

.no-trends-placeholder i {
  font-size: 3rem;
  opacity: 0.4;
  margin-bottom: var(--gp-spacing-md);
  color: var(--gp-text-muted);
}

.no-trends-placeholder p {
  margin: 0;
  font-size: 0.9375rem;
  font-style: italic;
  opacity: 0.8;
}

/* Dark Mode */
.p-dark .digest-trends {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark .chart-container,
.p-dark .no-trends-placeholder {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

.p-dark .trends-title {
  color: var(--gp-text-primary);
}

/* Responsive */
@media (max-width: 768px) {
  .chart-container {
    padding: var(--gp-spacing-md);
    height: 370px;
  }

  .no-trends-placeholder {
    min-height: 150px;
    padding: var(--gp-spacing-xl) var(--gp-spacing-md);
  }
}
</style>
