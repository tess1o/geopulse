<template>
  <Dialog
    v-model:visible="internalVisible"
    header="Trip Classification Details"
    :modal="true"
    class="gp-dialog-lg"
    @hide="$emit('close')"
  >
    <div v-if="loading" class="loading-state">
      <ProgressSpinner />
      <p>Loading classification details...</p>
    </div>

    <div v-else-if="error" class="error-state">
      <Message severity="error" :closable="false">
        {{ error }}
      </Message>
    </div>

    <div v-else-if="details" class="classification-content">
      <!-- Section 1: Trip Overview -->
      <div class="section">
        <h3 class="section-title">Trip Overview</h3>
        <div class="details-grid">
          <DetailItem label="Start Time" :value="formatDateTime(details.timestamp)" />
          <DetailItem label="Duration" :value="formatDuration(details.tripDurationSeconds)" />
          <DetailItem label="Distance" :value="formatDistance(details.distanceMeters)" />
          <DetailItem label="Classification">
            <template #value>
              <Tag
                :value="details.currentClassification"
                :severity="getTransportSeverity(details.currentClassification)"
              />
            </template>
          </DetailItem>
        </div>
      </div>

      <!-- Section 2: GPS Statistics -->
      <div class="section">
        <h3 class="section-title">GPS Statistics</h3>
        <div class="stats-grid">
          <StatCard
            icon="pi pi-chart-line"
            label="Average GPS Speed"
            :value="formatSpeed(details.statistics.avgGpsSpeedKmh)"
          />
          <StatCard
            icon="pi pi-bolt"
            label="Max GPS Speed"
            :value="formatSpeed(details.statistics.maxGpsSpeedKmh)"
          />
          <StatCard
            icon="pi pi-calculator"
            label="Calculated Avg Speed"
            :value="formatSpeed(details.statistics.calculatedAvgSpeedKmh)"
            hint="From distance/duration"
          />
          <StatCard
            icon="pi pi-wave-pulse"
            label="Speed Variance"
            :value="formatVariance(details.statistics.speedVarianceKmh)"
          />
          <StatCard
            icon="pi pi-exclamation-triangle"
            label="Low Accuracy Points"
            :value="details.statistics.lowAccuracyPointsCount || 0"
          />
          <StatCard
            icon="pi pi-verified"
            label="GPS Reliability"
            :value="details.statistics.gpsReliable ? 'Reliable' : 'Unreliable'"
            :severity="details.statistics.gpsReliable ? 'success' : 'warn'"
            :hint="details.statistics.gpsReliable
              ? 'GPS speeds are within expected range and used for classification'
              : 'GPS speeds unreliable - using calculated speed from distance/duration instead'"
          />
        </div>
      </div>

      <!-- Section 3: Classification Priority Order -->
      <div class="section">
        <div class="priority-banner">
          <div class="priority-banner-icon">
            <i class="pi pi-sort-amount-down"></i>
          </div>
          <div class="priority-banner-content">
            <h3 class="priority-banner-title">Classification Priority Order</h3>
            <div class="priority-flow">
              <span class="priority-step">✈️ FLIGHT</span>
              <i class="pi pi-arrow-right"></i>
              <span class="priority-step">🚊 TRAIN</span>
              <i class="pi pi-arrow-right"></i>
              <span class="priority-step">🚴 BICYCLE</span>
              <i class="pi pi-arrow-right"></i>
              <span class="priority-step">🏃 RUNNING</span>
              <i class="pi pi-arrow-right"></i>
              <span class="priority-step">🚗 CAR</span>
              <i class="pi pi-arrow-right"></i>
              <span class="priority-step">🚶 WALK</span>
              <i class="pi pi-arrow-right"></i>
              <span class="priority-step priority-unknown">❓ UNKNOWN</span>
            </div>
            <p class="priority-banner-description">
              Trips are classified in priority order from left to right. Once a match is found, classification stops.
              <a
                href="https://tess1o.github.io/geopulse/docs/user-guide/timeline/travel_classification"
                target="_blank"
                rel="noopener noreferrer"
                class="doc-link"
              >
                <i class="pi pi-external-link"></i> Learn more
              </a>
            </p>
          </div>
        </div>
      </div>

      <!-- Section 4: Classification Steps -->
      <div class="section">
        <h3 class="section-title">Classification Analysis</h3>
        <p class="section-description">
          Detailed threshold checks for each transport type.
        </p>

        <div class="steps-list">
          <ClassificationStepCard
            v-for="(step, index) in details.steps"
            :key="index"
            :step="step"
            :index="index + 1"
            :is-selected="step.tripType === details.currentClassification"
          />
        </div>
      </div>

      <!-- Section 5: Final Reason -->
      <div class="section">
        <Message severity="info" :closable="false">
          <strong>Final Decision:</strong> {{ details.finalReason }}
        </Message>
      </div>
    </div>

    <template #footer>
      <Button label="Close" outlined @click="internalVisible = false" />
    </template>
  </Dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import { useToast } from 'primevue/usetoast'
import apiService from '@/utils/apiService'
import { useTimezone } from '@/composables/useTimezone'
import { formatDurationSmart, formatDistance } from '@/utils/calculationsHelpers'
import DetailItem from './classification/DetailItem.vue'
import StatCard from './classification/StatCard.vue'
import ClassificationStepCard from './classification/ClassificationStepCard.vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  trip: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['close'])

const toast = useToast()
const timezone = useTimezone()

const loading = ref(false)
const error = ref(null)
const details = ref(null)

const internalVisible = computed({
  get: () => props.visible,
  set: (value) => {
    if (!value) emit('close')
  }
})

// Watch for trip changes and fetch details
watch(() => props.trip, async (newTrip) => {
  if (newTrip?.id && props.visible) {
    await fetchClassificationDetails(newTrip.id)
  }
}, { immediate: true })

watch(() => props.visible, async (visible) => {
  if (visible && props.trip?.id && !details.value) {
    await fetchClassificationDetails(props.trip.id)
  } else if (!visible) {
    // Reset when dialog closes
    details.value = null
    error.value = null
  }
})

const fetchClassificationDetails = async (tripId) => {
  loading.value = true
  error.value = null

  try {
    const response = await apiService.get(
      `/streaming-timeline/trips/${tripId}/classification`
    )

    if (response.status === 'success') {
      details.value = response.data
    } else {
      error.value = response.message || 'Failed to load classification details'
    }
  } catch (err) {
    console.error('Error fetching classification details:', err)
    error.value = err.message || 'Failed to load classification details'
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Could not load trip classification details',
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

// Formatting helpers
const formatDateTime = (timestamp) => {
  if (!timestamp) return 'N/A'
  return timezone.format(timestamp, 'YYYY-MM-DD HH:mm:ss')
}

const formatDuration = (seconds) => {
  if (!seconds) return 'N/A'
  return formatDurationSmart(seconds)
}

const formatSpeed = (speedKmh) => {
  if (speedKmh === null || speedKmh === undefined) return 'N/A'
  return `${speedKmh.toFixed(1)} km/h`
}

const formatVariance = (variance) => {
  if (variance === null || variance === undefined) return 'N/A'
  return variance.toFixed(1)
}

const getTransportSeverity = (transportMode) => {
  const severityMap = {
    'CAR': 'info',
    'WALK': 'success',
    'BICYCLE': 'info',
    'RUNNING': 'success',
    'TRAIN': 'info',
    'FLIGHT': 'danger',
    'UNKNOWN': 'secondary'
  }
  return severityMap[transportMode?.toUpperCase()] || 'secondary'
}
</script>

<style scoped>
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--gp-spacing-xxl);
  gap: var(--gp-spacing-md);
}

.loading-state p {
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
  margin: 0;
}

.error-state {
  padding: var(--gp-spacing-md);
}

.classification-content {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-xl);
  max-height: 70vh;
  overflow-y: auto;
  padding: var(--gp-spacing-xs);
}

/* Custom scrollbar */
.classification-content::-webkit-scrollbar {
  width: 8px;
}

.classification-content::-webkit-scrollbar-track {
  background: var(--gp-surface-light);
  border-radius: 4px;
}

.classification-content::-webkit-scrollbar-thumb {
  background: var(--gp-border-medium);
  border-radius: 4px;
}

.classification-content::-webkit-scrollbar-thumb:hover {
  background: var(--gp-primary);
}

.section {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
}

.section-title {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--gp-primary);
  margin: 0;
  padding-bottom: var(--gp-spacing-sm);
  border-bottom: 2px solid var(--gp-primary);
}

.section-description {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0;
  font-style: italic;
}

.details-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--gp-spacing-md);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--gp-spacing-md);
}

.steps-list {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
}

.priority-banner {
  display: flex;
  gap: var(--gp-spacing-md);
  padding: var(--gp-spacing-lg);
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
  border-left: 4px solid var(--gp-primary);
}

.priority-banner-icon {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  font-size: 2rem;
  color: var(--gp-primary);
  flex-shrink: 0;
  padding-top: 4px;
}

.priority-banner-content {
  flex: 1;
}

.priority-banner-title {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  margin: 0 0 var(--gp-spacing-md) 0;
}

.priority-flow {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--gp-spacing-sm);
  margin-bottom: var(--gp-spacing-md);
}

.priority-step {
  padding: var(--gp-spacing-xs) var(--gp-spacing-sm);
  background: var(--gp-primary);
  color: white;
  border-radius: var(--gp-radius-small);
  font-weight: 600;
  font-size: 0.875rem;
  white-space: nowrap;
}

.priority-step.priority-unknown {
  background: var(--gp-text-muted);
}

.priority-flow i {
  color: var(--gp-primary);
  font-size: 0.875rem;
}

.priority-banner-description {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.5;
}

.doc-link {
  color: var(--gp-primary);
  text-decoration: none;
  font-weight: 600;
  font-size: 0.875rem;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 4px;
  transition: all 0.2s ease;
}

.doc-link:hover {
  text-decoration: underline;
  color: var(--gp-primary-dark);
}

.doc-link i {
  font-size: 0.75rem;
}

/* Dark Mode */
.p-dark .priority-banner {
  background: var(--gp-surface-darker);
  border-left-color: var(--gp-primary);
}

.p-dark .priority-banner-icon {
  color: var(--gp-primary);
}

.p-dark .priority-banner-title {
  color: var(--gp-text-primary);
}

.p-dark .priority-step {
  background: var(--gp-primary);
  color: white;
}

.p-dark .priority-step.priority-unknown {
  background: var(--gp-text-muted);
}

.p-dark .priority-flow i {
  color: var(--gp-primary);
}

.p-dark .priority-banner-description {
  color: var(--gp-text-secondary);
}

.p-dark .doc-link:hover {
  color: var(--gp-primary-light);
}

/* Dark Mode (continued) */
.p-dark .classification-content::-webkit-scrollbar-track {
  background: var(--gp-surface-darker);
}

.p-dark .classification-content::-webkit-scrollbar-thumb {
  background: var(--gp-border-dark);
}

.p-dark .classification-content::-webkit-scrollbar-thumb:hover {
  background: var(--gp-primary);
}

.p-dark .section-title {
  color: var(--gp-primary);
  border-color: var(--gp-primary);
}

.p-dark .section-description {
  color: var(--gp-text-secondary);
}

.p-dark .loading-state p {
  color: var(--gp-text-secondary);
}

/* Mobile Responsive */
@media (max-width: 768px) {
  .classification-content {
    max-height: 60vh;
  }

  .section {
    gap: var(--gp-spacing-sm);
  }

  .section-title {
    font-size: 1rem;
  }

  .section-description {
    font-size: 0.85rem;
  }

  .details-grid {
    grid-template-columns: 1fr;
    gap: var(--gp-spacing-sm);
  }

  .stats-grid {
    grid-template-columns: 1fr;
    gap: var(--gp-spacing-sm);
  }

  .steps-list {
    gap: var(--gp-spacing-sm);
  }

  .priority-banner {
    flex-direction: column;
    padding: var(--gp-spacing-md);
  }

  .priority-banner-icon {
    font-size: 1.5rem;
  }

  .priority-banner-title {
    font-size: 1rem;
  }

  .priority-flow {
    gap: 6px;
  }

  .priority-step {
    font-size: 0.75rem;
    padding: 4px 8px;
  }

  .priority-flow i {
    font-size: 0.75rem;
  }

  .priority-banner-description {
    font-size: 0.8rem;
  }

  .doc-link {
    font-size: 0.8rem;
  }
}

@media (max-width: 480px) {
  .classification-content {
    max-height: 50vh;
  }

  .section-title {
    font-size: 0.95rem;
  }
}

/* PrimeVue Dialog overrides */
:deep(.p-dialog) {
  border-radius: var(--gp-radius-large);
  box-shadow: var(--gp-shadow-large);
}

:deep(.p-dialog-header) {
  background: var(--gp-surface-white);
  border-bottom: 1px solid var(--gp-border-light);
  color: var(--gp-text-primary);
}

:deep(.p-dialog-content) {
  background: var(--gp-surface-white);
  color: var(--gp-text-primary);
}

:deep(.p-dialog-footer) {
  background: var(--gp-surface-white);
  border-top: 1px solid var(--gp-border-light);
  display: flex;
  justify-content: flex-end;
  gap: var(--gp-spacing-sm);
}

.p-dark :deep(.p-dialog-header),
.p-dark :deep(.p-dialog-content),
.p-dark :deep(.p-dialog-footer) {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-medium);
  color: var(--gp-text-primary);
}
</style>
