<template>
  <div class="digest-trends">
    <h3 class="trends-title">
      <i class="pi pi-chart-line"></i>
      Activity Trends
    </h3>

    <div class="chart-container" v-if="hasChartData">
      <BarChart
        :labels="chartData.labels"
        :data="chartData.data"
        :title="viewMode === 'monthly' ? 'Weekly Distance' : 'Monthly Distance'"
        color="primary"
      />
    </div>

    <div class="no-data" v-else>
      <i class="pi pi-chart-line"></i>
      <p>No activity data available for this period</p>
      <p class="no-data-hint">Track your location to see activity trends</p>
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
  return props.chartData &&
         props.chartData.data &&
         Array.isArray(props.chartData.data) &&
         props.chartData.data.length > 0 &&
         props.chartData.data.some(val => val > 0)
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
  min-height: 300px;
}

.no-data {
  text-align: center;
  padding: var(--gp-spacing-xl);
  color: var(--gp-text-muted);
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
}

.no-data i {
  font-size: 3rem;
  opacity: 0.5;
  margin-bottom: var(--gp-spacing-md);
}

.no-data p {
  margin: 0;
  font-style: italic;
}

.no-data-hint {
  font-size: 0.75rem;
  margin-top: var(--gp-spacing-sm);
  opacity: 0.7;
}

/* Dark Mode */
.p-dark .digest-trends {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark .chart-container,
.p-dark .no-data {
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
  }
}
</style>
