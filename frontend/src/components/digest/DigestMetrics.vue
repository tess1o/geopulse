<template>
  <div class="digest-metrics">
    <h3 class="metrics-title">
      <i class="pi pi-chart-bar"></i>
      {{ title }}
    </h3>

    <div class="metrics-grid">
      <!-- Total Distance -->
      <div class="metric-card">
        <div class="metric-icon">🚗</div>
        <div class="metric-value">{{ formatDistance(metrics.totalDistance) }}</div>
        <div class="metric-label">Total Distance</div>
        <div class="metric-change" v-if="comparison" :class="comparisonClass">
          {{ comparisonText }}
        </div>
      </div>

      <!-- Active Days -->
      <div class="metric-card">
        <div class="metric-icon">📅</div>
        <div class="metric-value">{{ metrics.activeDays }}</div>
        <div class="metric-label">Active Days</div>
      </div>

      <!-- Cities Visited -->
      <div class="metric-card">
        <div class="metric-icon">🏙️</div>
        <div class="metric-value">{{ metrics.citiesVisited }}</div>
        <div class="metric-label">Cities Visited</div>
      </div>

      <!-- Trip Count -->
      <div class="metric-card">
        <div class="metric-icon">🛣️</div>
        <div class="metric-value">{{ metrics.tripCount }}</div>
        <div class="metric-label">Trips Completed</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { formatDistance } from '@/utils/calculationsHelpers'

const props = defineProps({
  title: {
    type: String,
    default: 'At a Glance'
  },
  metrics: {
    type: Object,
    required: true
  },
  comparison: {
    type: Object,
    default: null
  }
})

const comparisonClass = computed(() => {
  if (!props.comparison) return ''
  return {
    'increase': props.comparison.direction === 'increase',
    'decrease': props.comparison.direction === 'decrease',
    'same': props.comparison.direction === 'same'
  }
})

const comparisonText = computed(() => {
  if (!props.comparison) return ''

  const percent = Math.abs(props.comparison.percentChange)
  if (props.comparison.direction === 'increase') {
    return `↑ ${percent}% more than last period`
  } else if (props.comparison.direction === 'decrease') {
    return `↓ ${percent}% less than last period`
  } else {
    return '→ Same as last period'
  }
})
</script>

<style scoped>
.digest-metrics {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-large);
  padding: var(--gp-spacing-xl);
  margin-bottom: var(--gp-spacing-xl);
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
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--gp-spacing-lg);
}

.metric-card {
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  padding: var(--gp-spacing-lg);
  text-align: center;
  transition: all 0.3s ease;
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
