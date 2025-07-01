<template>
  <!-- No Data State -->
  <div v-if="noDataAvailable" class="no-data-container">
    <div class="no-data-content">
      <i class="pi pi-directions no-data-icon"></i>
      <h3 class="no-data-title">No Route Data</h3>
      <p class="no-data-message">
        There are no routes analyzed for this period.
      </p>
    </div>
  </div>

  <!-- Route Metrics -->
  <div v-else class="route-metrics-container">
    <!-- Unique Routes -->
    <MetricItem
      icon="pi pi-directions"
      iconColor="primary"
      :value="stats.uniqueRoutesCount"
      label="Unique Routes"
      variant="minimal"
    />

    <!-- Most Common Route -->
    <div class="route-detail">
      <div class="route-detail-icon">
        <i class="pi pi-refresh"></i>
      </div>
      <div class="route-detail-content">
        <div class="route-name">
          {{ stats.mostCommonRoute?.name || 'N/A' }}
        </div>
        <div class="route-label">Most Common Route</div>
        <div v-if="stats.mostCommonRoute?.count" class="trips-count">
          {{ stats.mostCommonRoute.count }} trips
        </div>
      </div>
    </div>

    <!-- Average Trip Duration -->
    <MetricItem
      icon="pi pi-clock"
      iconColor="secondary"
      :value="stats.avgTripDuration"
      label="Avg Trip Duration"
      :formatter="formatDuration"
      variant="minimal"
    />

    <!-- Longest Trip Duration -->
    <MetricItem
      icon="pi pi-stopwatch"
      iconColor="warning"
      :value="stats.longestTripDuration"
      label="Longest Trip (duration)"
      :formatter="formatDuration"
      variant="minimal"
    />

    <!-- Longest Trip Distance -->
    <MetricItem
      icon="pi pi-map-marker"
      iconColor="info"
      :value="stats.longestTripDistance"
      label="Longest Trip (distance)"
      :formatter="formatDistance"
      variant="minimal"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { formatDistance, formatDuration } from '@/utils/calculationsHelpers'
import MetricItem from '@/components/ui/data/MetricItem.vue'

const props = defineProps({
  stats: {
    type: Object,
    required: true,
    default: () => ({
      uniqueRoutesCount: 0,
      mostCommonRoute: {
        name: '',
        count: 0
      },
      avgTripDuration: 0,
      longestTripDuration: 0,
      longestTripDistance: 0
    })
  }
})

// Computed properties
const noDataAvailable = computed(() => {
  return !props.stats ||
      (props.stats.uniqueRoutesCount === 0 &&
          (props.stats.mostCommonRoute?.count === 0 || !props.stats.mostCommonRoute?.count))
})
</script>

<style scoped>
/* Route Metrics */
.route-metrics-container {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
}

/* Special Route Detail */
.route-detail {
  display: flex;
  align-items: flex-start;
  gap: var(--gp-spacing-md);
  padding: var(--gp-spacing-md) 0;
  border-bottom: 1px solid var(--gp-border-light);
}

.route-detail:last-child {
  border-bottom: none;
}

.route-detail-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--gp-radius-medium);
  background: var(--gp-timeline-green);
  color: var(--gp-secondary);
  border: 1px solid var(--gp-secondary-light);
  flex-shrink: 0;
  font-size: 1rem;
}

.route-detail-content {
  flex: 1;
  min-width: 0;
}

.route-name {
  font-size: 1.125rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  margin-bottom: 0.25rem;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.route-label {
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
  font-weight: 500;
  line-height: 1.3;
  margin-bottom: 0.125rem;
}

.trips-count {
  font-size: 0.75rem;
  color: var(--gp-text-muted);
  font-weight: 500;
}

/* No Data State */
.no-data-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  padding: var(--gp-spacing-lg);
}

.no-data-content {
  text-align: center;
}

.no-data-icon {
  font-size: 2rem;
  color: var(--gp-text-muted);
  margin-bottom: var(--gp-spacing-md);
  display: block;
}

.no-data-title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-text-secondary);
  margin: 0 0 var(--gp-spacing-sm);
}

.no-data-message {
  font-size: 0.875rem;
  color: var(--gp-text-muted);
  margin: 0;
  max-width: 250px;
  line-height: 1.4;
}

/* Dark Mode */
.p-dark .route-detail {
  border-bottom-color: var(--gp-border-dark);
}

.p-dark .route-name {
  color: var(--gp-text-primary);
}

.p-dark .route-label {
  color: var(--gp-text-secondary);
}

.p-dark .trips-count {
  color: var(--gp-text-muted);
}

.p-dark .no-data-icon {
  color: var(--gp-text-muted);
}

.p-dark .no-data-title {
  color: var(--gp-text-secondary);
}

.p-dark .no-data-message {
  color: var(--gp-text-muted);
}

/* Responsive adjustments */
@media (max-width: 640px) {
  .route-detail {
    gap: var(--gp-spacing-sm);
    padding: var(--gp-spacing-sm) 0;
  }

  .route-detail-icon {
    width: 28px;
    height: 28px;
    font-size: 0.875rem;
  }

  .route-name {
    font-size: 1rem;
  }

  .route-label {
    font-size: 0.75rem;
  }

  .trips-count {
    font-size: 0.7rem;
  }
}
</style>