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
  // First check if chartData exists and has the required structure
  if (!props.chartData) return false

  const carChart = props.chartData.carChart
  const walkChart = props.chartData.walkChart

  // Check if at least one chart exists with valid data
  const hasCarData = carChart?.data?.length > 0 && carChart.data.some(value => value > 0)
  const hasWalkData = walkChart?.data?.length > 0 && walkChart.data.some(value => value > 0)

  return hasCarData || hasWalkData
})

const chartLabels = computed(() => {
  // Merge labels from both car and walk charts to get all unique labels
  const carLabels = props.chartData?.carChart?.labels || []
  const walkLabels = props.chartData?.walkChart?.labels || []

  // Combine and deduplicate labels while maintaining order
  const allLabels = [...new Set([...carLabels, ...walkLabels])].sort()

  return allLabels
})

const chartDatasets = computed(() => {
  const datasets = []
  const allLabels = chartLabels.value

  // Helper function to align data with merged labels - use 0 instead of null
  const alignDataWithLabels = (sourceLabels, sourceData) => {
    const labelDataMap = new Map()
    sourceLabels.forEach((label, index) => {
      labelDataMap.set(label, sourceData[index] || 0)
    })

    // Map data to all labels, use 0 for missing values
    return allLabels.map(label => labelDataMap.get(label) || 0)
  }

  // Add car distance data if available
  if (props.chartData?.carChart?.labels?.length > 0 && props.chartData?.carChart?.data?.length > 0) {
    const alignedData = alignDataWithLabels(
      props.chartData.carChart.labels,
      props.chartData.carChart.data
    )
    datasets.push({
      label: 'Car Distance',
      data: alignedData,
      color: 'primary'
    })
  }

  // Add walk distance data if available
  if (props.chartData?.walkChart?.labels?.length > 0 && props.chartData?.walkChart?.data?.length > 0) {
    const alignedData = alignDataWithLabels(
      props.chartData.walkChart.labels,
      props.chartData.walkChart.data
    )
    datasets.push({
      label: 'Walk Distance',
      data: alignedData,
      color: 'success'
    })
  }

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
