<template>
  <div class="large-dataset-warning">
    <BaseCard class="warning-card">
      <div class="warning-content">
        <div class="warning-header">
          <i class="pi pi-exclamation-triangle warning-icon"></i>
          <h2 class="warning-title">{{ t('timeline.largeDataset.title') }}</h2>
        </div>

        <div class="warning-body">
          <p class="dataset-info">
            <i18n-t keypath="timeline.largeDataset.itemsCount" tag="span">
              <template #count><strong>{{ totalItems }}</strong></template>
            </i18n-t>
            <span class="breakdown">{{ dataGaps > 0 ? t('timeline.largeDataset.breakdownWithGaps', { stays, trips, dataGaps }) : t('timeline.largeDataset.breakdown', { stays, trips }) }}</span>
          </p>

          <p class="recommendation">
            {{ t('timeline.largeDataset.recommendation') }}
          </p>

          <div class="primary-action">
            <Button
              :label="t('timeline.largeDataset.openReports')"
              icon="pi pi-chart-bar"
              @click="navigateToReports"
              severity="primary"
              size="large"
              class="reports-button"
            />
          </div>

          <div class="secondary-actions">
            <p class="or-text">{{ t('timeline.largeDataset.orViewRecent') }}</p>
            <div class="quick-ranges">
              <Button
                :label="t('timeline.largeDataset.last7Days')"
                icon="pi pi-calendar"
                @click="selectLast7Days"
                outlined
                size="small"
              />
              <Button
                :label="t('timeline.largeDataset.last30Days')"
                icon="pi pi-calendar"
                @click="selectLast30Days"
                outlined
                size="small"
              />
            </div>
          </div>

          <div class="force-load-option">
            <Checkbox
              v-model="forceLoad"
              inputId="force-load-checkbox"
              binary
            />
            <label for="force-load-checkbox" class="force-load-label">
              {{ t('timeline.largeDataset.loadAnyway') }}
            </label>
          </div>

          <Button
            v-if="forceLoad"
            :label="t('timeline.largeDataset.continueWithRange')"
            @click="$emit('force-load')"
            severity="secondary"
            class="continue-button"
          />
        </div>
      </div>
    </BaseCard>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import { useDateRangeStore } from '@/stores/dateRange'
import { useTimezone } from '@/composables/useTimezone'

const { t } = useI18n()
const router = useRouter()
const dateRangeStore = useDateRangeStore()
const timezone = useTimezone()

const props = defineProps({
  totalItems: {
    type: Number,
    required: true
  },
  stays: {
    type: Number,
    required: true
  },
  trips: {
    type: Number,
    required: true
  },
  dataGaps: {
    type: Number,
    default: 0
  }
})

const emit = defineEmits(['force-load'])

const forceLoad = ref(false)

const navigateToReports = () => {
  // Navigate to Timeline Reports with current date range
  const { dateRange } = dateRangeStore
  if (dateRange && dateRange.length === 2) {
    const start = timezone.formatUrlDate(dateRange[0])
    const end = timezone.formatUrlDate(dateRange[1])
    router.push(`/app/timeline-reports?start=${start}&end=${end}`)
  } else {
    router.push('/app/timeline-reports')
  }
}

const selectLast7Days = () => {
  const end = timezone.now().endOf('day')
  const start = timezone.now().subtract(6, 'days').startOf('day')
  dateRangeStore.setDateRange([start.toISOString(), end.toISOString()])
}

const selectLast30Days = () => {
  const end = timezone.now().endOf('day')
  const start = timezone.now().subtract(29, 'days').startOf('day')
  dateRangeStore.setDateRange([start.toISOString(), end.toISOString()])
}
</script>

<style scoped>
.large-dataset-warning {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
  padding: var(--gp-spacing-xl);
}

.warning-card {
  max-width: 600px;
  width: 100%;
}

.warning-content {
  padding: var(--gp-spacing-xl);
}

.warning-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--gp-spacing-md);
  margin-bottom: var(--gp-spacing-lg);
}

.warning-icon {
  font-size: 2.5rem;
  color: var(--gp-warning);
}

.warning-title {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.warning-body {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-lg);
}

.dataset-info {
  font-size: 1rem;
  color: var(--gp-text-primary);
  margin: 0;
  text-align: center;
}

.dataset-info strong {
  color: var(--gp-warning);
  font-size: 1.25rem;
}

.breakdown {
  display: block;
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  margin-top: var(--gp-spacing-xs);
}

.recommendation {
  font-size: 0.9375rem;
  color: var(--gp-text-secondary);
  margin: 0;
  text-align: center;
  line-height: 1.5;
}

.primary-action {
  display: flex;
  justify-content: center;
  margin-top: var(--gp-spacing-md);
}

.reports-button {
  min-width: 250px;
  font-weight: 600;
}

.secondary-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--gp-spacing-sm);
  padding-top: var(--gp-spacing-md);
  border-top: 1px solid var(--gp-border-light);
}

.or-text {
  margin: 0;
  font-size: 0.875rem;
  color: var(--gp-text-muted);
}

.quick-ranges {
  display: flex;
  gap: var(--gp-spacing-sm);
}

.force-load-option {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--gp-spacing-sm);
  padding-top: var(--gp-spacing-md);
  border-top: 1px solid var(--gp-border-light);
}

.force-load-label {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  cursor: pointer;
  user-select: none;
}

.continue-button {
  width: 100%;
  margin-top: var(--gp-spacing-sm);
}

/* Dark Mode */
.p-dark .warning-title {
  color: var(--gp-text-primary);
}

.p-dark .secondary-actions,
.p-dark .force-load-option {
  border-top-color: var(--gp-border-dark);
}

/* Responsive */
@media (max-width: 768px) {
  .large-dataset-warning {
    padding: var(--gp-spacing-md);
  }

  .warning-content {
    padding: var(--gp-spacing-lg);
  }

  .warning-header {
    flex-direction: column;
    text-align: center;
  }

  .warning-title {
    font-size: 1.25rem;
  }

  .reports-button {
    min-width: auto;
    width: 100%;
  }

  .quick-ranges {
    flex-direction: column;
    width: 100%;
  }

  .quick-ranges button {
    width: 100%;
  }
}
</style>
