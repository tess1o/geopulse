<template>
  <div class="digest-highlights">
    <h3 class="highlights-title">
      <i class="pi pi-star-fill"></i>
      Highlights
    </h3>

    <div v-if="hasHighlights" class="highlights-grid">
      <!-- Longest Trip -->
      <div class="highlight-card" v-if="highlights.longestTrip">
        <div class="highlight-icon">🚗</div>
        <div class="highlight-content">
          <div class="highlight-title">Longest Trip</div>
          <div class="highlight-value">
            {{ formatDistance(highlights.longestTrip.distance) }}
          </div>
          <div class="highlight-date">{{ formatDate(highlights.longestTrip.date) }}</div>
        </div>
      </div>

      <!-- Most Visited -->
      <div class="highlight-card" v-if="highlights.mostVisited">
        <div class="highlight-icon">📍</div>
        <div class="highlight-content">
          <div class="highlight-title">Most Visited Place</div>
          <div class="highlight-value">{{ highlights.mostVisited.name }}</div>
          <div class="highlight-date">{{ highlights.mostVisited.visits }} visits</div>
        </div>
      </div>

      <!-- Busiest Day -->
      <div class="highlight-card" v-if="highlights.busiestDay">
        <div class="highlight-icon">⚡</div>
        <div class="highlight-content">
          <div class="highlight-title">Busiest Day</div>
          <div class="highlight-value">{{ highlights.busiestDay.trips }} trips</div>
          <div class="highlight-date">
            {{ formatDate(highlights.busiestDay.date) }} - {{ formatDistance(highlights.busiestDay.distance) }}
          </div>
        </div>
      </div>
    </div>
    <div v-else class="no-highlights-placeholder">
      <i class="pi pi-star"></i>
      <p>No highlights for this period.</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { formatDistance } from '@/utils/calculationsHelpers';
import { useTimezone } from '@/composables/useTimezone';

const timezone = useTimezone();

const props = defineProps({
  highlights: {
    type: Object,
    default: () => ({})
  }
});

const hasHighlights = computed(() => {
  return props.highlights && (
    props.highlights.longestTrip ||
    props.highlights.mostVisited ||
    props.highlights.busiestDay
  );
});

const formatDate = (date) => {
  if (!date) return '';
  return timezone.fromUtc(date).format('MMM DD, YYYY');
};
</script>

<style scoped>
.digest-highlights {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-large);
  padding: var(--gp-spacing-xl);
  margin-bottom: var(--gp-spacing-xl);
  min-height: 00px;
}

.highlights-title {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  margin: 0 0 var(--gp-spacing-lg);
}

.highlights-title i {
  color: var(--gp-warning);
}

.highlights-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--gp-spacing-md);
}

.highlight-card {
  display: flex;
  gap: var(--gp-spacing-md);
  background: var(--gp-timeline-blue);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  padding: var(--gp-spacing-lg);
  transition: all 0.3s ease;
}

.highlight-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--gp-shadow-card-hover);
  border-color: var(--gp-primary);
}

.highlight-icon {
  font-size: 2.5rem;
  flex-shrink: 0;
  line-height: 1;
}

.highlight-content {
  flex: 1;
}

.highlight-title {
  font-size: 0.75rem;
  text-transform: uppercase;
  color: var(--gp-text-muted);
  font-weight: 600;
  letter-spacing: 0.025em;
  margin-bottom: var(--gp-spacing-xs);
}

.highlight-value {
  font-size: 1rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  margin-bottom: var(--gp-spacing-xs);
  line-height: 1.3;
  word-break: break-all;
}

.highlight-date {
  font-size: 0.75rem;
  color: var(--gp-text-secondary);
  font-weight: 500;
}

.no-highlights-placeholder {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: var(--gp-spacing-xl);
  color: var(--gp-text-muted);
  font-style: italic;
}

.no-highlights-placeholder i {
  font-size: 2rem;
  opacity: 0.5;
  margin-bottom: var(--gp-spacing-md);
}

.highlight-cities {
  font-size: 0.75rem;
  color: var(--gp-text-secondary);
  font-weight: 500;
  margin-top: var(--gp-spacing-xs);
  font-style: italic;
}

/* Dark Mode */
.p-dark .digest-highlights {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark .highlight-card {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

.p-dark .highlights-title {
  color: var(--gp-text-primary);
}

/* Responsive */
@media (max-width: 768px) {
  .highlights-grid {
    grid-template-columns: 1fr;
    gap: var(--gp-spacing-sm);
  }

  .highlight-card {
    padding: var(--gp-spacing-md);
  }

  .highlight-icon {
    font-size: 2rem;
  }
}
</style>
