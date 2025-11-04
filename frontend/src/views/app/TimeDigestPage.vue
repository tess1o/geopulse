<template>
  <AppLayout variant="default">
    <PageContainer
      title="Rewind"
      subtitle="Explore your location story through time"
      :loading="isLoading"
    >
      <!-- Header with Period Selector -->
      <DigestHeader
        v-model:viewMode="viewMode"
        v-model:year="selectedYear"
        v-model:month="selectedMonth"
        @period-changed="handlePeriodChange"
      />

      <!-- Loading State -->
      <div v-if="isLoading" class="digest-loading">
        <ProgressSpinner size="large" />
        <p>Loading your digest...</p>
      </div>

      <!-- Error State -->
      <div v-else-if="hasError" class="digest-error">
        <BaseCard class="error-card">
          <i class="pi pi-exclamation-triangle error-icon"></i>
          <h3 class="error-title">Unable to Load Digest</h3>
          <p class="error-message">{{ errorMessage }}</p>
          <BaseButton
            label="Try Again"
            icon="pi pi-refresh"
            @click="loadDigest"
            variant="gp-primary"
          />
        </BaseCard>
      </div>

      <!-- Digest Content -->
      <div v-else-if="currentDigest" class="digest-content">
        <!-- Metrics -->
        <DigestMetrics
          :title="`${currentDigest.period?.displayName} at a Glance`"
          :metrics="currentDigest.metrics"
          :comparison="currentDigest.comparison"
          :highlights="currentDigest.highlights"
        />

        <!-- Highlights -->
        <DigestHighlights
          :highlights="currentDigest.highlights"
        />

        <!-- Two Column Layout -->
        <div class="digest-two-column">
          <!-- Places -->
          <DigestPlaces
            :places="currentDigest.topPlaces"
            :limit="10"
          />

          <!-- Milestones -->
          <DigestMilestones
            :milestones="currentDigest.milestones"
          />
        </div>

        <!-- Trends -->
        <DigestTrends
          :chartData="currentDigest.activityChart"
          :viewMode="viewMode"
        />
      </div>

      <!-- Empty State -->
      <div v-else class="digest-empty">
        <BaseCard class="empty-card">
          <i class="pi pi-calendar empty-icon"></i>
          <h3 class="empty-title">No Data Available</h3>
          <p class="empty-message">
            No location data found for {{ viewMode === 'monthly' ? monthNames[selectedMonth - 1] : '' }} {{ selectedYear }}.
          </p>
          <p class="empty-suggestion">
            Try selecting a different period or check if you have tracking data for this time.
          </p>
        </BaseCard>
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { storeToRefs } from 'pinia';
import { useToast } from 'primevue/usetoast';
import ProgressSpinner from 'primevue/progressspinner';

// Layout Components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import BaseButton from '@/components/ui/base/BaseButton.vue'

// Digest Components
import DigestHeader from '@/components/digest/DigestHeader.vue'
import DigestMetrics from '@/components/digest/DigestMetrics.vue'
import DigestHighlights from '@/components/digest/DigestHighlights.vue'
import DigestPlaces from '@/components/digest/DigestPlaces.vue'
import DigestTrends from '@/components/digest/DigestTrends.vue'
import DigestMilestones from '@/components/digest/DigestMilestones.vue'

// Store and Composables
import { useDigestStore } from '@/stores/digest'
import { useTimezone } from '@/composables/useTimezone'
import { useErrorHandler } from '@/composables/useErrorHandler'

const timezone = useTimezone()
const digestStore = useDigestStore();
const toast = useToast();
const { handleError } = useErrorHandler();
const route = useRoute();
const router = useRouter();

const { currentDigest, loading: isLoading, error: errorMessage } = storeToRefs(digestStore)

// Local state
const viewMode = ref('monthly')
const selectedYear = ref(timezone.now().year())
const selectedMonth = ref(timezone.now().month() + 1)

const monthNames = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'
]

const hasError = computed(() => digestStore.hasError)

const handlePeriodChange = async (period) => {
  viewMode.value = period.viewMode;
  selectedYear.value = period.year;
  selectedMonth.value = period.month;
  await loadDigest();
  updateURL();
};

const loadDigest = async () => {
  try {
    if (viewMode.value === 'monthly') {
      await digestStore.fetchMonthlyDigest(selectedYear.value, selectedMonth.value)
    } else {
      await digestStore.fetchYearlyDigest(selectedYear.value)
    }
  } catch (error) {
    console.error('Error loading digest:', error)
    handleError(error)
  }
}

const updateURL = () => {
  const query = { viewMode: viewMode.value, year: selectedYear.value };
  if (viewMode.value === 'monthly') {
    query.month = selectedMonth.value;
  }
  router.push({ query });
};

// Lifecycle
onMounted(async () => {
  const { viewMode: mode, year, month } = route.query;
  if (mode) {
    viewMode.value = mode;
  }
  if (year) {
    selectedYear.value = parseInt(year, 10);
    if (month) {
      selectedMonth.value = parseInt(month, 10);
    }
  } else {
    const now = timezone.now();
    selectedYear.value = now.year();
    selectedMonth.value = now.month() + 1;
  }
  await loadDigest();
  updateURL();
});
</script>

<style scoped>
.digest-content {
  width: 100%;
  max-width: 1400px;
  margin: 0 auto;
}

.digest-two-column {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--gp-spacing-xl);
  margin-bottom: var(--gp-spacing-xl);
}

/* Loading State */
.digest-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--gp-spacing-xxl);
  gap: var(--gp-spacing-lg);
}

.digest-loading p {
  color: var(--gp-text-secondary);
  font-size: 1rem;
  margin: 0;
}

/* Error State */
.digest-error {
  margin-top: var(--gp-spacing-xl);
}

.error-card {
  text-align: center;
  padding: var(--gp-spacing-xl);
}

.error-icon {
  font-size: 4rem;
  color: var(--gp-error);
  margin-bottom: var(--gp-spacing-lg);
}

.error-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  margin: 0 0 var(--gp-spacing-md);
}

.error-message {
  font-size: 1rem;
  color: var(--gp-text-secondary);
  margin: 0 0 var(--gp-spacing-lg);
  max-width: 500px;
  margin-left: auto;
  margin-right: auto;
}

/* Empty State */
.digest-empty {
  margin-top: var(--gp-spacing-xl);
}

.empty-card {
  text-align: center;
  padding: var(--gp-spacing-xl);
}

.empty-icon {
  font-size: 4rem;
  color: var(--gp-text-muted);
  opacity: 0.5;
  margin-bottom: var(--gp-spacing-lg);
}

.empty-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  margin: 0 0 var(--gp-spacing-md);
}

.empty-message {
  font-size: 1rem;
  color: var(--gp-text-secondary);
  margin: 0 0 var(--gp-spacing-sm);
  max-width: 500px;
  margin-left: auto;
  margin-right: auto;
}

.empty-suggestion {
  font-size: 0.875rem;
  color: var(--gp-text-muted);
  margin: 0;
  max-width: 500px;
  margin-left: auto;
  margin-right: auto;
  font-style: italic;
}

/* Dark Mode */
.p-dark .error-title,
.p-dark .empty-title {
  color: var(--gp-text-primary);
}

.p-dark .error-message,
.p-dark .empty-message {
  color: var(--gp-text-secondary);
}

.p-dark .empty-suggestion {
  color: var(--gp-text-muted);
}

/* Responsive Design */
@media (max-width: 1024px) {
  .digest-two-column {
    grid-template-columns: 1fr;
    gap: var(--gp-spacing-lg);
  }
}

@media (max-width: 768px) {
  .digest-content {
    padding: 0 var(--gp-spacing-sm);
  }

  .digest-two-column {
    gap: var(--gp-spacing-md);
  }

  .digest-loading,
  .error-card,
  .empty-card {
    padding: var(--gp-spacing-lg);
  }

  .error-icon,
  .empty-icon {
    font-size: 3rem;
  }

  .error-title,
  .empty-title {
    font-size: 1.25rem;
  }
}
</style>
