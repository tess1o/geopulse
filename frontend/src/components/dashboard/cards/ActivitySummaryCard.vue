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
        :label="t('ui.dashboard.metrics.totalDistance')"
        :formatter="formatDistance"
        variant="minimal"
      />

      <!-- Time Moving -->
      <MetricItem
        icon="pi pi-clock"
        iconColor="secondary"
        :value="stats.timeMoving || 0"
        :label="t('ui.dashboard.metrics.timeMoving')"
        :formatter="formatDuration"
        variant="minimal"
      />

      <!-- Daily Average -->
      <MetricItem
        icon="pi pi-chart-line"
        iconColor="info"
        :value="stats.dailyAverageDistanceMeters || 0"
        :label="t('ui.dashboard.metrics.dailyAverage')"
        :formatter="formatDistance"
        variant="minimal"
      />

      <!-- Average Speed -->
      <MetricItem
        icon="pi pi-send"
        iconColor="warning"
        :value="stats.averageSpeed || 0"
        :label="t('ui.dashboard.metrics.averageSpeed')"
        :formatter="formatSpeed"
        variant="minimal"
      />

      <!-- Most Active Day -->
      <div
        v-tooltip="{value: mostActiveDayTooltip, escape: false, pt: {text: 'most-active-day-tooltip-text'}}"
        class="tooltip-wrapper"
      >
        <MetricItem
          icon="pi pi-star"
          iconColor="success"
          :value="stats.mostActiveDay?.day || t('ui.dashboard.metrics.notAvailable')"
          :label="t('ui.dashboard.metrics.mostActiveDay')"
          variant="minimal"
        />
      </div>

      <!-- Unique Locations -->
      <MetricItem
        icon="pi pi-map"
        iconColor="muted"
        :value="stats.uniqueLocationsCount || 0"
        :label="t('ui.dashboard.metrics.uniqueLocations')"
        variant="minimal"
      />
    </div>

    <!-- Chart Section -->
    <div v-if="showChart && hasChartData" class="chart-section">
      <h4 class="chart-title">{{ t('ui.dashboard.distanceActivityChart') }}</h4>
      <BarChart
        :labels="chartLabels"
        :datasets="chartDatasets"
        :yAxisTitle="yAxisTitle"
        :valueFormatter="formatDistanceValue"
        class="activity-chart"
      />
    </div>
  </BaseCard>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import MetricItem from '@/components/ui/data/MetricItem.vue'
import BarChart from '@/components/charts/BarChart.vue'
import {
  formatDistance,
  formatDuration,
  formatSpeed,
  convertKilometersToDisplayUnit,
  formatDistanceValue,
  getDistanceUnitLabel
} from '@/utils/calculationsHelpers'
import { buildMergedChartAxis, getChartPointKey } from '@/utils/chartAxisHelpers'

const { t, te } = useI18n()

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

// Trip type colors; labels come from the shared movementTypes.* catalog.
const tripTypeColors = {
  WALK: 'success',
  BICYCLE: 'warning',
  RUNNING: 'contrast',
  CAR: 'primary',
  MOTORCYCLE: 'info',
  PUBLIC_TRANSPORT: 'secondary',
  TRAIN: 'secondary',
  FLIGHT: 'danger',
  BOAT: 'info'
}

// Y-axis title based on unit system
const yAxisTitle = computed(() => t('ui.dashboard.distanceAxisTitle', { unit: getDistanceUnitLabel() }))

const hasChartData = computed(() => {
  const chartsByType = props.stats?.distanceChartsByTripType || {}
  return Object.values(chartsByType).some(chart =>
    chart?.data?.length > 0 && chart.data.some(value => value > 0)
  )
})

const mergedChartAxis = computed(() => {
  const chartsByType = props.stats?.distanceChartsByTripType || {}
  return buildMergedChartAxis(chartsByType)
})

const chartLabels = computed(() => mergedChartAxis.value.labels)

const chartDatasets = computed(() => {
  const datasets = []
  const mergedKeys = mergedChartAxis.value.keys
  const chartsByType = props.stats?.distanceChartsByTripType || {}

  // Process each trip type
  Object.entries(chartsByType).forEach(([tripType, chartData]) => {
    if (!chartData || !chartData.data || chartData.data.length === 0) return

    const data = chartData.data || []

    // Create key-to-value map for this trip type, converting km to the active unit.
    const dataMap = new Map((chartData.labels || []).map((_, i) => [
      getChartPointKey(chartData, i),
      convertKilometersToDisplayUnit(data[i] || 0)
    ]))

    // Get configuration for this trip type
    const label = te(`movementTypes.${tripType}`) ? t(`movementTypes.${tripType}`) : tripType
    const color = tripTypeColors[tripType] || 'secondary'

    datasets.push({
      label,
      data: mergedKeys.map(key => dataMap.get(key) || 0),
      color
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
          <span>${t('ui.dashboard.tooltip.distance', { value: formatDistance(data.distanceTraveled) })}</span>
        </div>
        <div class="tooltip-item">
          <i class="pi pi-clock"></i>
          <span>${t('ui.dashboard.tooltip.travelTime', { value: formatDuration(data.travelTime) })}</span>
        </div>
        <div class="tooltip-item">
          <i class="pi pi-map"></i>
          <span>${t('ui.dashboard.tooltip.locations', { value: data.locationsVisited })}</span>
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
  border-top: 1px solid var(--gp-border);
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

/* Tooltip Wrapper */
.tooltip-wrapper {
  cursor: help;
}

/* The tooltip body is the card below; strip the default tooltip chrome from this tooltip only. */
:global(.p-tooltip-text.most-active-day-tooltip-text) {
  padding: 0;
  background: transparent;
  border: none;
  box-shadow: none;
}

:global(.most-active-day-tooltip) {
  background: var(--gp-surface-card);
  border: 1px solid var(--gp-border);
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
  border-bottom: 1px solid var(--gp-border);
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
