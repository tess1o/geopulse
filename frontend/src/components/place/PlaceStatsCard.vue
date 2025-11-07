<template>
  <BaseCard title="Place Statistics">
    <div class="stats-grid">
      <!-- Visit Counts -->
      <div class="stat-section">
        <h3 class="section-title">Visit Counts</h3>
        <div class="stat-items">
          <div class="stat-item-full">
            <div class="stat-label">
              <i class="pi pi-chart-line"></i>
              <span>Total Visits</span>
            </div>
            <div class="stat-value">{{ statistics.totalVisits || 0 }}</div>
          </div>
          <div class="stat-item-full">
            <div class="stat-label">
              <i class="pi pi-calendar"></i>
              <span>This Week</span>
            </div>
            <div class="stat-value">{{ statistics.visitsThisWeek || 0 }}</div>
          </div>
          <div class="stat-item-full">
            <div class="stat-label">
              <i class="pi pi-calendar"></i>
              <span>This Month</span>
            </div>
            <div class="stat-value">{{ statistics.visitsThisMonth || 0 }}</div>
          </div>
          <div class="stat-item-full">
            <div class="stat-label">
              <i class="pi pi-calendar"></i>
              <span>This Year</span>
            </div>
            <div class="stat-value">{{ statistics.visitsThisYear || 0 }}</div>
          </div>
        </div>
      </div>

      <!-- Duration Statistics -->
      <div class="stat-section">
        <h3 class="section-title">Duration Statistics</h3>
        <div class="stat-items">
          <div class="stat-item-full">
            <div class="stat-label">
              <i class="pi pi-clock"></i>
              <span>Total Time</span>
            </div>
            <div class="stat-value">{{ formatDuration(statistics.totalDuration) }}</div>
          </div>
          <div class="stat-item-full">
            <div class="stat-label">
              <i class="pi pi-chart-bar"></i>
              <span>Average Duration</span>
            </div>
            <div class="stat-value">{{ formatDuration(statistics.averageDuration) }}</div>
          </div>
          <div class="stat-item-full">
            <div class="stat-label">
              <i class="pi pi-arrow-down"></i>
              <span>Shortest Visit</span>
            </div>
            <div class="stat-value">{{ formatDuration(statistics.minDuration) }}</div>
          </div>
          <div class="stat-item-full">
            <div class="stat-label">
              <i class="pi pi-arrow-up"></i>
              <span>Longest Visit</span>
            </div>
            <div class="stat-value">{{ formatDuration(statistics.maxDuration) }}</div>
          </div>
        </div>
      </div>

      <!-- Temporal Information -->
      <div class="stat-section">
        <h3 class="section-title">Visit History</h3>
        <div class="stat-items">
          <div class="stat-item-full">
            <div class="stat-label">
              <i class="pi pi-calendar-plus"></i>
              <span>First Visit</span>
            </div>
            <div class="stat-value">{{ formatDate(statistics.firstVisit) }}</div>
          </div>
          <div class="stat-item-full">
            <div class="stat-label">
              <i class="pi pi-calendar-times"></i>
              <span>Last Visit</span>
            </div>
            <div class="stat-value">{{ formatDate(statistics.lastVisit) }}</div>
          </div>
        </div>
      </div>
    </div>
  </BaseCard>
</template>

<script setup>
import { computed } from 'vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import { formatDurationSmart } from '@/utils/calculationsHelpers'
import { useTimezone } from '@/composables/useTimezone'

const timezone = useTimezone()

const props = defineProps({
  statistics: {
    type: Object,
    required: true
  }
})

const formatDuration = (seconds) => {
  if (seconds === null || seconds === undefined) return 'N/A'
  if (seconds === 0) return '0 seconds'
  return formatDurationSmart(seconds)
}

const formatDate = (timestamp) => {
  if (!timestamp) return 'N/A'
  return timezone.format(timestamp, 'MMMM DD, YYYY')
}
</script>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: var(--gp-spacing-xl);
}

.stat-section {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
}

.section-title {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-text-secondary);
  padding-bottom: var(--gp-spacing-sm);
  border-bottom: 2px solid var(--gp-border-light);
}

.stat-items {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
}

.stat-item-full {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--gp-spacing-md);
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-light);
}

.stat-label {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  color: var(--gp-text-secondary);
  font-weight: 500;
  font-size: 0.9rem;
}

.stat-label i {
  color: var(--gp-primary);
}

.stat-value {
  color: var(--gp-text-primary);
  font-weight: 600;
  font-size: 1rem;
}

/* Dark mode */
.p-dark .section-title {
  color: var(--gp-text-primary);
  border-bottom-color: var(--gp-border-dark);
}

.p-dark .stat-item-full {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

.p-dark .stat-label {
  color: var(--gp-text-secondary);
}

.p-dark .stat-value {
  color: var(--gp-text-primary);
}

/* Responsive design */
@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
