<template>
  <div class="digest-trends">
    <h3 class="trends-title">
      <i class="pi pi-chart-line"></i>
      {{ t('analytics.digest.trends.title') }}
    </h3>

    <div v-if="hasChartData" class="chart-container">
      <BarChart
        :labels="chartLabels"
        :datasets="chartDatasets"
        :yAxisTitle="yAxisTitle"
        :valueFormatter="formatDistanceValue"
      />
    </div>

    <div class="no-trends-placeholder" v-else>
      <i class="pi pi-chart-line"></i>
      <p>{{ t('analytics.digest.trends.empty') }}</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BarChart from '@/components/charts/BarChart.vue'
import {
  convertKilometersToDisplayUnit,
  formatDistanceValue,
  getDistanceUnitLabel
} from '@/utils/calculationsHelpers'
import { buildMergedChartAxis, getChartPointKey } from '@/utils/chartAxisHelpers'

const { t } = useI18n()

// Trip type display configuration with distinct colors -- labels resolve through the shared
// `movementTypes.*` catalog (same enum values used by trip reconstruction/profile) rather than
// duplicating English text here.
const tripTypeConfig = computed(() => ({
  WALK: { label: t('movementTypes.WALK'), color: 'success' },
  RUNNING: { label: t('movementTypes.RUNNING'), color: 'contrast' },
  BICYCLE: { label: t('movementTypes.BICYCLE'), color: 'warning' },
  CAR: { label: t('movementTypes.CAR'), color: 'primary' },
  MOTORCYCLE: { label: t('movementTypes.MOTORCYCLE'), color: 'info' },
  PUBLIC_TRANSPORT: { label: t('movementTypes.PUBLIC_TRANSPORT'), color: 'secondary' },
  TRAIN: { label: t('movementTypes.TRAIN'), color: 'secondary' },
  FLIGHT: { label: t('movementTypes.FLIGHT'), color: 'danger' },
  BOAT: { label: t('movementTypes.BOAT'), color: 'info' }
}))

// Y-axis title based on unit system and view mode
const yAxisTitle = computed(() => {
  const unitLabel = getDistanceUnitLabel()
  return props.viewMode === 'monthly'
    ? t('analytics.digest.trends.weeklyDistance', { unit: unitLabel })
    : t('analytics.digest.trends.monthlyDistance', { unit: unitLabel })
})

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

const mergedChartAxis = computed(() => {
  const chartsByType = props.chartData?.chartsByTripType || {}
  return buildMergedChartAxis(chartsByType, { viewMode: props.viewMode })
})

const chartLabels = computed(() => mergedChartAxis.value.labels)

const chartDatasets = computed(() => {
  const datasets = []
  const mergedKeys = mergedChartAxis.value.keys
  const chartsByType = props.chartData?.chartsByTripType || {}

  // Align series data using stable point keys so ordering is independent from display labels.
  const alignDataWithKeys = (chart, sourceData) => {
    const labelDataMap = new Map()
    const sourceLabels = chart?.labels || []

    sourceLabels.forEach((_, index) => {
      labelDataMap.set(getChartPointKey(chart, index), convertKilometersToDisplayUnit(sourceData[index] || 0))
    })

    // Map data to all merged keys, use 0 for missing values
    return mergedKeys.map(key => labelDataMap.get(key) || 0)
  }

  // Process each trip type
  Object.entries(chartsByType).forEach(([tripType, chartData]) => {
    if (!chartData || !chartData.labels?.length || !chartData.data?.length) return

    const alignedData = alignDataWithKeys(chartData, chartData.data)

    // Get configuration for this trip type
    const config = tripTypeConfig.value[tripType] || { label: tripType, color: 'secondary' }

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
.digest-trends { background: var(--gp-surface-card); border: 1px solid var(--gp-border); border-radius: 18px; padding: var(--gp-spacing-xl); margin-bottom: var(--gp-spacing-xl); }

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

.chart-container { background: var(--gp-surface-muted); border: 0; border-radius: 12px; padding: var(--gp-spacing-lg); height: 380px; }

.no-trends-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--gp-spacing-xxl) var(--gp-spacing-xl);
  text-align: center;
  color: var(--gp-text-muted);
  background: var(--gp-surface-muted);
  border: 1px solid var(--gp-border);
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
