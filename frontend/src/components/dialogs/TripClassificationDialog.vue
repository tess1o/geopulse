<template>
  <Dialog
    v-model:visible="internalVisible"
    :header="t('classification.dialog.header')"
    :modal="true"
    class="gp-dialog-lg"
    @hide="$emit('close')"
  >
    <div v-if="loading" class="loading-state">
      <ProgressSpinner />
      <p>{{ t('classification.dialog.loading') }}</p>
    </div>

    <div v-else-if="error" class="error-state">
      <Message severity="error" :closable="false">
        {{ error }}
      </Message>
    </div>

    <div v-else-if="details" class="classification-content">
      <!-- Section 1: Trip Overview -->
      <div class="section">
        <h3 class="section-title">{{ t('classification.overview.title') }}</h3>
        <div class="details-grid">
          <DetailItem :label="t('classification.overview.startTime')" :value="formatDateTime(details.timestamp, props.trip?.startLocationTimezone)" />
          <DetailItem :label="t('classification.overview.duration')" :value="formatDuration(details.tripDurationSeconds)" />
          <DetailItem :label="t('classification.overview.distance')" :value="formatDistance(details.distanceMeters)" />
          <DetailItem :label="t('classification.overview.effectiveClassification')">
            <template #value>
              <Tag
                :value="movementTypeLabel(details.currentClassification)"
                :severity="getTransportSeverity(details.currentClassification)"
              />
            </template>
          </DetailItem>
          <DetailItem :label="t('classification.overview.automaticClassification')">
            <template #value>
              <Tag
                :value="movementTypeLabel(details.algorithmClassification)"
                :severity="getTransportSeverity(details.algorithmClassification)"
              />
            </template>
          </DetailItem>
          <DetailItem :label="t('classification.overview.classificationSource')">
            <template #value>
              <Tag
                :value="details.movementTypeSource"
                :severity="details.movementTypeSource === 'MANUAL' ? 'warn' : 'success'"
              />
            </template>
          </DetailItem>
        </div>
        <Message v-if="details.movementTypeSource === 'MANUAL'" severity="warn" :closable="false">
          {{ t('classification.overview.manualOverrideActive') }}
        </Message>
      </div>

      <!-- Section 2: Manual Override -->
      <div class="section">
        <h3 class="section-title">{{ t('classification.editSection.title') }}</h3>
        <Message v-if="details.currentClassification === 'UNKNOWN'" severity="warn" :closable="false">
          {{ t('classification.editSection.unrecognizedWarning') }}
        </Message>
        <Message v-else severity="info" :closable="false">
          {{ t('classification.editSection.overrideInfo') }}
        </Message>
        <Message v-if="readOnly" severity="error" :closable="false">
          {{ t('classification.editSection.readOnlyError') }}
        </Message>
        <div class="override-controls">
          <Select
            v-model="selectedMovementType"
            :options="movementTypeOptions"
            optionLabel="label"
            optionValue="value"
            :placeholder="t('classification.editSection.selectPlaceholder')"
            class="movement-select"
            :disabled="savingMovementType || readOnly"
          />
          <div class="override-actions">
            <Button
              :label="t('classification.editSection.saveButton')"
              icon="pi pi-save"
              :loading="savingMovementType"
              :disabled="readOnly || !selectedMovementType || savingMovementType"
              @click="saveManualMovementType"
            />
            <Button
              :label="t('classification.editSection.resetButton')"
              icon="pi pi-refresh"
              severity="secondary"
              outlined
              :loading="savingMovementType"
              :disabled="readOnly || !canResetMovementType || savingMovementType"
              @click="resetToAutomaticMovementType"
            />
          </div>
        </div>
      </div>

      <!-- Section 3: GPS Statistics -->
      <div class="section">
        <h3 class="section-title">{{ t('classification.stats.title') }}</h3>
        <div class="stats-grid">
          <StatCard
            icon="pi pi-chart-line"
            :label="t('classification.stats.avgSpeed')"
            :value="formatSpeed(details.statistics.avgGpsSpeedKmh)"
          />
          <StatCard
            icon="pi pi-bolt"
            :label="t('classification.stats.maxSpeed')"
            :value="formatSpeed(details.statistics.maxGpsSpeedKmh)"
          />
          <StatCard
            icon="pi pi-calculator"
            :label="t('classification.stats.calculatedAvgSpeed')"
            :value="formatSpeed(details.statistics.calculatedAvgSpeedKmh)"
            :hint="t('classification.stats.calculatedAvgSpeedHint')"
          />
          <StatCard
            icon="pi pi-wave-pulse"
            :label="t('classification.stats.speedVariance')"
            :value="formatVariance(details.statistics.speedVarianceKmh)"
          />
          <StatCard
            icon="pi pi-exclamation-triangle"
            :label="t('classification.stats.lowAccuracyPoints')"
            :value="details.statistics.lowAccuracyPointsCount || 0"
          />
          <StatCard
            icon="pi pi-verified"
            :label="t('classification.stats.gpsReliability')"
            :value="details.statistics.gpsReliable ? t('classification.stats.reliable') : t('classification.stats.unreliable')"
            :severity="details.statistics.gpsReliable ? 'success' : 'warn'"
            :hint="details.statistics.gpsReliable
              ? t('classification.stats.reliableHint')
              : t('classification.stats.unreliableHint')"
          />
        </div>
      </div>

      <!-- Section 4: Classification Priority Order -->
      <div class="section">
        <div class="priority-banner">
          <div class="priority-banner-icon">
            <i class="pi pi-sort-amount-down"></i>
          </div>
          <div class="priority-banner-content">
            <h3 class="priority-banner-title">{{ t('classification.priority.title') }}</h3>
            <div class="priority-flow">
              <template v-for="(step, stepIndex) in priorityOrderSteps" :key="step.type">
                <span class="priority-step" :class="{ 'priority-unknown': step.type === 'UNKNOWN' }">
                  {{ step.icon }} {{ step.label }}
                </span>
                <i v-if="stepIndex < priorityOrderSteps.length - 1" class="pi pi-arrow-right"></i>
              </template>
            </div>
            <p class="priority-banner-description">
              {{ t('classification.priority.description') }}
              <a
                href="https://geopulse.cc/docs/user-guide/timeline/travel_classification"
                target="_blank"
                rel="noopener noreferrer"
                class="doc-link"
              >
                <i class="pi pi-external-link"></i> {{ t('classification.priority.learnMore') }}
              </a>
            </p>
          </div>
        </div>
      </div>

      <!-- Section 5: Classification Steps -->
      <div class="section">
        <h3 class="section-title">{{ t('classification.steps.title') }}</h3>
        <p class="section-description">
          {{ t('classification.steps.description') }}
        </p>

        <div class="steps-list">
          <ClassificationStepCard
            v-for="(step, index) in details.steps"
            :key="index"
            :step="step"
            :index="index + 1"
            :is-selected="step.tripType === details.algorithmClassification"
          />
        </div>
      </div>

      <!-- Section 6: Final Reason -->
      <div class="section">
        <Message severity="info" :closable="false">
          <strong>{{ t('classification.finalDecisionLabel') }}</strong> {{ details.finalReason }}
        </Message>
      </div>
    </div>

    <template #footer>
      <Button :label="t('classification.dialog.close')" outlined @click="internalVisible = false" />
    </template>
  </Dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import Select from 'primevue/select'
import { useToast } from 'primevue/usetoast'
import { useTimezone } from '@/composables/useTimezone'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'
import { formatDurationSmart, formatDistance } from '@/utils/calculationsHelpers'
import { useTimelineStore } from '@/stores/timeline'
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
  },
  readOnly: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['close', 'movement-updated'])

const { t, te } = useI18n()
const toast = useToast()
const timezone = useTimezone()
const timelineStore = useTimelineStore()

const loading = ref(false)
const error = ref(null)
const details = ref(null)
const savingMovementType = ref(false)
const selectedMovementType = ref(null)

const MOVEMENT_TYPE_VALUES = ['WALK', 'CAR', 'MOTORCYCLE', 'PUBLIC_TRANSPORT', 'BICYCLE', 'RUNNING', 'TRAIN', 'FLIGHT', 'BOAT', 'UNKNOWN']

// Translated at call time so a locale switch doesn't leave stale labels behind.
const movementTypeLabel = (type) => {
  if (!type) return type
  const key = `movementTypes.${type}`
  return te(key) ? t(key) : type
}

const movementTypeOptions = computed(() => MOVEMENT_TYPE_VALUES.map((value) => ({
  label: movementTypeLabel(value),
  value
})))

const PRIORITY_ORDER_ICONS = {
  FLIGHT: '✈️',
  BOAT: '⛵',
  TRAIN: '🚊',
  BICYCLE: '🚴',
  RUNNING: '🏃',
  CAR: '🚗',
  WALK: '🚶',
  UNKNOWN: '❓'
}
const PRIORITY_ORDER_VALUES = ['FLIGHT', 'BOAT', 'TRAIN', 'BICYCLE', 'RUNNING', 'CAR', 'WALK', 'UNKNOWN']

const priorityOrderSteps = computed(() => PRIORITY_ORDER_VALUES.map((type) => ({
  type,
  icon: PRIORITY_ORDER_ICONS[type],
  label: movementTypeLabel(type).toUpperCase()
})))

const canResetMovementType = computed(() => details.value?.movementTypeSource === 'MANUAL')

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
    selectedMovementType.value = null
  }
})

async function fetchClassificationDetails(tripId) {
  loading.value = true
  error.value = null

  try {
    details.value = await timelineStore.fetchTripClassification(tripId)
    selectedMovementType.value = details.value?.currentClassification || 'UNKNOWN'
  } catch (err) {
    console.error('Error fetching classification details:', err)
    error.value = formatApiErrorDetail(err, t('classification.toasts.loadFailedFallback'))
    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: t('classification.toasts.loadFailedDetail'),
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

const saveManualMovementType = async () => {
  if (props.readOnly) return
  if (!props.trip?.id || !selectedMovementType.value) return
  savingMovementType.value = true
  try {
    const updated = await timelineStore.updateTripMovementType(props.trip.id, selectedMovementType.value)
    if (updated) {
      details.value.currentClassification = updated.movementType
      details.value.movementTypeSource = updated.movementTypeSource
      details.value.algorithmClassification = updated.algorithmClassification
      if (props.trip) {
        props.trip.movementType = updated.movementType
        props.trip.movementTypeSource = updated.movementTypeSource
      }
      emit('movement-updated', updated)
      await fetchClassificationDetails(props.trip.id)
      toast.add({
        severity: 'success',
        summary: t('classification.toasts.movementUpdatedSummary'),
        detail: t('classification.toasts.movementUpdatedDetail', { type: movementTypeLabel(updated.movementType) }),
        life: 2500
      })
    }
  } catch (err) {
    console.error('Error updating movement type:', err)
    toast.add({
      severity: 'error',
      summary: t('classification.toasts.updateFailedSummary'),
      detail: err.message || t('classification.toasts.updateFailedFallback'),
      life: 3000
    })
  } finally {
    savingMovementType.value = false
  }
}

const resetToAutomaticMovementType = async () => {
  if (props.readOnly) return
  if (!props.trip?.id) return
  savingMovementType.value = true
  try {
    const updated = await timelineStore.resetTripMovementType(props.trip.id)
    if (updated) {
      details.value.currentClassification = updated.movementType
      details.value.movementTypeSource = updated.movementTypeSource
      details.value.algorithmClassification = updated.algorithmClassification
      selectedMovementType.value = updated.movementType
      if (props.trip) {
        props.trip.movementType = updated.movementType
        props.trip.movementTypeSource = updated.movementTypeSource
      }
      emit('movement-updated', updated)
      await fetchClassificationDetails(props.trip.id)
      toast.add({
        severity: 'success',
        summary: t('classification.toasts.movementResetSummary'),
        detail: t('classification.toasts.movementResetDetail', { type: movementTypeLabel(updated.movementType) }),
        life: 2500
      })
    }
  } catch (err) {
    console.error('Error resetting movement type:', err)
    toast.add({
      severity: 'error',
      summary: t('classification.toasts.resetFailedSummary'),
      detail: err.message || t('classification.toasts.resetFailedFallback'),
      life: 3000
    })
  } finally {
    savingMovementType.value = false
  }
}

// Formatting helpers
const formatDateTime = (timestamp, locationTimezone) => {
  if (!timestamp) return t('classification.na')
  return timezone.formatDateTimeDisplayAt(timestamp, locationTimezone, { withSeconds: true })
}

const formatDuration = (seconds) => {
  if (!seconds) return t('classification.na')
  return formatDurationSmart(seconds)
}

const formatSpeed = (speedKmh) => {
  if (speedKmh === null || speedKmh === undefined) return t('classification.na')
  return `${speedKmh.toFixed(1)} km/h`
}

const formatVariance = (variance) => {
  if (variance === null || variance === undefined) return t('classification.na')
  return variance.toFixed(1)
}

const getTransportSeverity = (transportMode) => {
  const severityMap = {
    'CAR': 'info',
    'MOTORCYCLE': 'info',
    'WALK': 'success',
    'BICYCLE': 'info',
    'RUNNING': 'success',
    'TRAIN': 'info',
    'FLIGHT': 'danger',
    'BOAT': 'info',
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
  background: var(--gp-surface-ground);
  border-radius: 4px;
}

.classification-content::-webkit-scrollbar-thumb {
  background: var(--gp-border);
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

.override-controls {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
}

.movement-select {
  max-width: 320px;
}

.override-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gp-spacing-sm);
}

.priority-banner {
  display: flex;
  gap: var(--gp-spacing-md);
  padding: var(--gp-spacing-lg);
  background: var(--gp-surface-ground);
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
  color: var(--gp-primary-text);
}

.doc-link i {
  font-size: 0.75rem;
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

  .movement-select {
    max-width: 100%;
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
</style>
