<template>
  <div class="digest-metrics">
    <h3 class="metrics-title">
      <i class="pi pi-chart-bar"></i>
      {{ title }}
    </h3>

    <div v-if="hasMetrics" class="metrics-grid">
      <!-- ROW 1: Distance Overview -->

      <!-- Total Distance -->
      <div class="metric-card">
        <div class="metric-icon">🚗</div>
        <div class="metric-value">{{ formatDistanceRounded(metrics.totalDistance) }}</div>
        <div class="metric-label">Total Distance</div>
        <div class="metric-change" v-if="comparison" :class="comparisonClass">
          {{ comparisonText }}
        </div>
      </div>

      <!-- Car Distance -->
      <div class="metric-card" v-if="metrics.carDistance > 0">
        <div class="metric-icon">🚗</div>
        <div class="metric-value">{{ formatDistanceRounded(metrics.carDistance) }}</div>
        <div class="metric-label">Distance by Car</div>
        <div class="metric-percentage" v-if="metrics.totalDistance > 0">
          {{ getPercentage(metrics.carDistance, metrics.totalDistance) }}% of total
        </div>
      </div>

      <!-- Walk Distance -->
      <div class="metric-card" v-if="metrics.walkDistance > 0">
        <div class="metric-icon">🚶</div>
        <div class="metric-value">{{ formatDistanceRounded(metrics.walkDistance) }}</div>
        <div class="metric-label">Distance Walking</div>
        <div class="metric-percentage" v-if="metrics.totalDistance > 0">
          {{ getPercentage(metrics.walkDistance, metrics.totalDistance) }}% of total
        </div>
      </div>

      <!-- Bicycle Distance -->
      <div class="metric-card" v-if="metrics.bicycleDistance > 0">
        <div class="metric-icon">🚴</div>
        <div class="metric-value">{{ formatDistanceRounded(metrics.bicycleDistance) }}</div>
        <div class="metric-label">Distance Cycling</div>
        <div class="metric-percentage" v-if="metrics.totalDistance > 0">
          {{ getPercentage(metrics.bicycleDistance, metrics.totalDistance) }}% of total
        </div>
      </div>

      <!-- Running Distance -->
      <div class="metric-card" v-if="metrics.runningDistance > 0">
        <div class="metric-icon">🏃</div>
        <div class="metric-value">{{ formatDistanceRounded(metrics.runningDistance) }}</div>
        <div class="metric-label">Distance Running</div>
        <div class="metric-percentage" v-if="metrics.totalDistance > 0">
          {{ getPercentage(metrics.runningDistance, metrics.totalDistance) }}% of total
        </div>
      </div>

      <!-- Train Distance -->
      <div class="metric-card" v-if="metrics.trainDistance > 0">
        <div class="metric-icon">🚆</div>
        <div class="metric-value">{{ formatDistanceRounded(metrics.trainDistance) }}</div>
        <div class="metric-label">Distance by Train</div>
        <div class="metric-percentage" v-if="metrics.totalDistance > 0">
          {{ getPercentage(metrics.trainDistance, metrics.totalDistance) }}% of total
        </div>
      </div>

      <!-- Flight Distance -->
      <div class="metric-card" v-if="metrics.flightDistance > 0">
        <div class="metric-icon">✈️</div>
        <div class="metric-value">{{ formatDistanceRounded(metrics.flightDistance) }}</div>
        <div class="metric-label">Distance by Flight</div>
        <div class="metric-percentage" v-if="metrics.totalDistance > 0">
          {{ getPercentage(metrics.flightDistance, metrics.totalDistance) }}% of total
        </div>
      </div>

      <!-- Unknown Distance -->
      <div class="metric-card" v-if="metrics.unknownDistance > 0">
        <div class="metric-icon">❓</div>
        <div class="metric-value">{{ formatDistanceRounded(metrics.unknownDistance) }}</div>
        <div class="metric-label">Distance Unknown</div>
        <div class="metric-percentage" v-if="metrics.totalDistance > 0">
          {{ getPercentage(metrics.unknownDistance, metrics.totalDistance) }}% of total
        </div>
      </div>

      <!-- ROW 2: Time & Activity -->

      <!-- Active Days -->
      <div class="metric-card">
        <div class="metric-icon">📅</div>
        <div class="metric-value">{{ metrics.activeDays }}</div>
        <div class="metric-label">Active Days</div>
      </div>

      <!-- Time Moving -->
      <div class="metric-card" v-if="metrics.timeMoving">
        <div class="metric-icon">⏱️</div>
        <div class="metric-value">{{ formatDuration(metrics.timeMoving) }}</div>
        <div class="metric-label">Time Moving</div>
      </div>

      <!-- Peak Hours -->
      <div class="metric-card" v-if="hasPeakHours">
        <div class="metric-icon">🕐</div>
        <div class="metric-value peak-hours-value">
          <div v-for="(hour, index) in highlights.peakHours" :key="index" class="peak-hour-range">
            {{ hour }}
          </div>
        </div>
        <div class="metric-label">Most Active Times</div>
      </div>

      <!-- ROW 3: Activity Details -->

      <!-- Trip Count -->
      <div class="metric-card">
        <div class="metric-icon">🛣️</div>
        <div class="metric-value">{{ metrics.tripCount }}</div>
        <div class="metric-label">Trips Completed</div>
      </div>

      <!-- Stay Count -->
      <div class="metric-card">
        <div class="metric-icon">⏸️</div>
        <div class="metric-value">{{ metrics.stayCount || 0 }}</div>
        <div class="metric-label">Stays Recorded</div>
      </div>

      <!-- Cities Visited -->
      <div class="metric-card">
        <div class="metric-icon">🏙️</div>
        <div class="metric-value">{{ metrics.citiesVisited }}</div>
        <div class="metric-label">Cities Visited</div>
      </div>
    </div>
    <div v-else class="no-metrics-placeholder">
      <i class="pi pi-chart-bar"></i>
      <p>No metrics available for this period.</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { formatDistanceRounded, formatDuration } from '@/utils/calculationsHelpers'

const props = defineProps({
  title: {
    type: String,
    default: 'At a Glance'
  },
  metrics: {
    type: Object,
    default: () => ({})
  },
  comparison: {
    type: Object,
    default: null
  },
  highlights: {
    type: Object,
    default: () => ({})
  }
});

const hasMetrics = computed(() => {
  return props.metrics && props.metrics.tripCount > 0;
});

const comparisonClass = computed(() => {
  if (!props.comparison) return ''
  return {
    'increase': props.comparison.direction === 'increase',
    'decrease': props.comparison.direction === 'decrease',
    'same': props.comparison.direction === 'same'
  }
});

const comparisonText = computed(() => {
  if (!props.comparison) return ''

  const percent = Math.abs(props.comparison.percentChange)
  if (props.comparison.direction === 'increase') {
    return `↑ ${percent}% more than previous period`
  } else if (props.comparison.direction === 'decrease') {
    return `↓ ${percent}% less than previous period`
  } else {
    return '→ Same as previous period'
  }
})

const getPercentage = (value, total) => {
  if (!total || total === 0) return 0
  return Math.round((value / total) * 100)
}

const hasPeakHours = computed(() => {
  return props.highlights?.peakHours && props.highlights.peakHours.length > 0
})
</script>

<style scoped>
.digest-metrics {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-large);
  padding: var(--gp-spacing-xl);
  margin-bottom: var(--gp-spacing-xl);
  min-height: 300px;
}

.metrics-title {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  margin: 0 0 var(--gp-spacing-lg);
}

.metrics-title i {
  color: var(--gp-primary);
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--gp-spacing-lg);
}

.metric-card {
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  padding: var(--gp-spacing-lg);
  text-align: center;
  transition: all 0.3s ease;
  min-width: 230px;
}

.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--gp-shadow-card-hover);
  border-color: var(--gp-primary);
}

.metric-icon {
  font-size: 2.5rem;
  margin-bottom: var(--gp-spacing-sm);
  line-height: 1;
}

.metric-value {
  font-size: 2rem;
  font-weight: 800;
  color: var(--gp-primary);
  line-height: 1;
  margin-bottom: var(--gp-spacing-xs);
}

.metric-label {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--gp-text-secondary);
  margin-bottom: var(--gp-spacing-xs);
}

.metric-change {
  font-size: 0.75rem;
  font-weight: 500;
  margin-top: var(--gp-spacing-sm);
}

.metric-change.increase {
  color: var(--gp-success);
}

.metric-change.decrease {
  color: var(--gp-error);
}

.metric-change.same {
  color: var(--gp-text-muted);
}

.metric-percentage {
  font-size: 0.7rem;
  font-weight: 500;
  color: var(--gp-text-muted);
  margin-top: var(--gp-spacing-xs);
}

.peak-hours-value {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.peak-hour-range {
  line-height: 1.2;
}

.no-metrics-placeholder {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: var(--gp-spacing-xl);
  color: var(--gp-text-muted);
  font-style: italic;
}

.no-metrics-placeholder i {
  font-size: 2rem;
  opacity: 0.5;
  margin-bottom: var(--gp-spacing-md);
}

/* Dark Mode */
.p-dark .digest-metrics {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark .metric-card {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

.p-dark .metrics-title {
  color: var(--gp-text-primary);
}

/* Responsive */
@media (max-width: 768px) {
  .metrics-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: var(--gp-spacing-md);
  }

  .metric-value {
    font-size: 1.5rem;
  }

  .metric-icon {
    font-size: 2rem;
  }
}

@media (max-width: 480px) {
  .metrics-grid {
    grid-template-columns: 1fr;
  }
}
</style>
