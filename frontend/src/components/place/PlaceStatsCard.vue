<template>
  <BaseCard title="Visit overview" class="place-statistics">
    <div class="summary-grid">
      <MetricItem icon="pi pi-chart-line" label="Total visits" :value="statistics.totalVisits || 0" variant="card" />
      <MetricItem icon="pi pi-clock" label="Total time" :value="formatDuration(statistics.totalDuration)" variant="card" />
      <MetricItem icon="pi pi-chart-bar" label="Average visit" :value="formatDuration(statistics.averageDuration)" variant="card" />
      <MetricItem v-if="hasUniquePlaces" icon="pi pi-map-marker" label="Places visited" :value="statistics.uniquePlaces" variant="card" />
    </div>

    <div class="details-grid">
      <section class="stats-group">
        <h3>Activity</h3>
        <MetricItem icon="pi pi-calendar" label="This week" :value="statistics.visitsThisWeek || 0" size="small" variant="minimal" />
        <MetricItem icon="pi pi-calendar" label="This month" :value="statistics.visitsThisMonth || 0" size="small" variant="minimal" />
        <MetricItem icon="pi pi-calendar" label="This year" :value="statistics.visitsThisYear || 0" size="small" variant="minimal" />
      </section>

      <section class="stats-group">
        <h3>Visit span</h3>
        <MetricItem icon="pi pi-calendar-plus" label="First visit" :value="formatDate(statistics.firstVisit)" size="small" variant="minimal" />
        <MetricItem icon="pi pi-calendar-times" label="Last visit" :value="formatDate(statistics.lastVisit)" size="small" variant="minimal" />
      </section>

      <section class="stats-group">
        <h3>Duration range</h3>
        <MetricItem icon="pi pi-arrow-down" label="Shortest visit" :value="formatDuration(statistics.minDuration)" size="small" variant="minimal" />
        <MetricItem icon="pi pi-arrow-up" label="Longest visit" :value="formatDuration(statistics.maxDuration)" size="small" variant="minimal" />
      </section>

      <section v-if="hasVisitPatterns" class="stats-group">
        <h3>Visit patterns</h3>
        <MetricItem icon="pi pi-calendar" label="Typical day" :value="visitPatterns.mostCommonDayOfWeek" size="small" variant="minimal" />
        <MetricItem icon="pi pi-clock" label="Arrival period" :value="visitPatterns.mostCommonArrivalPeriod" size="small" variant="minimal" />
        <MetricItem icon="pi pi-refresh" label="Visit cadence" :value="formatCadence(visitPatterns.averageDaysBetweenVisits)" size="small" variant="minimal" />
      </section>
    </div>
  </BaseCard>
</template>

<script setup>
import { computed } from 'vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import MetricItem from '@/components/ui/data/MetricItem.vue'
import { formatDurationSmart } from '@/utils/calculationsHelpers'
import { useTimezone } from '@/composables/useTimezone'

const timezone = useTimezone()
const props = defineProps({
  statistics: { type: Object, required: true }
})

const visitPatterns = computed(() => props.statistics?.visitPatterns || null)
const hasVisitPatterns = computed(() => Boolean(visitPatterns.value))
const hasUniquePlaces = computed(() => props.statistics?.uniquePlaces !== null && props.statistics?.uniquePlaces !== undefined)

const formatDuration = (seconds) => {
  if (seconds === null || seconds === undefined) return 'N/A'
  if (seconds === 0) return '0 seconds'
  return formatDurationSmart(seconds)
}

const formatDate = (timestamp) => timestamp ? timezone.format(timestamp, 'MMMM DD, YYYY') : 'N/A'

const formatCadence = (days) => {
  if (days === null || days === undefined || Number.isNaN(Number(days))) return 'N/A'
  const roundedDays = Math.round(Number(days))
  if (roundedDays < 1) return 'Less than daily'
  return roundedDays === 1 ? 'Every day' : `Every ${roundedDays} days`
}
</script>

<style scoped>
.place-statistics {
  margin-bottom: var(--gp-spacing-xl);
}

.summary-grid,
.details-grid {
  display: grid;
  gap: var(--gp-spacing-md);
}

.summary-grid {
  grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr));
  margin-bottom: var(--gp-spacing-lg);
}

.summary-grid :deep(.gp-metric-item) {
  min-height: 4.75rem;
  margin: 0;
}

.details-grid {
  grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr));
}

.stats-group {
  padding: var(--gp-spacing-md);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
}

.stats-group h3 {
  margin: 0 0 var(--gp-spacing-xs);
  color: var(--gp-text-primary);
  font-size: .9rem;
}

.stats-group :deep(.gp-metric-item) {
  margin: 0;
}

.stats-group :deep(.gp-metric-content) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gp-spacing-sm);
}

.stats-group :deep(.gp-metric-value) {
  order: 2;
  margin: 0;
  color: var(--gp-text-primary);
  font-size: .9rem;
  text-align: right;
}

.stats-group :deep(.gp-metric-label) {
  order: 1;
  margin: 0;
  font-size: .8rem;
  white-space: nowrap;
}

.p-dark .stats-group {
  border-color: var(--gp-border-dark);
}

@media (max-width: 1100px) {
  .summary-grid,
  .details-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .summary-grid,
  .details-grid {
    grid-template-columns: 1fr;
  }
}
</style>
