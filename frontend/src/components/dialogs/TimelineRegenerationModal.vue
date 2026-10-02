<template>
  <Dialog
    v-model:visible="internalVisible"
    :header="t('miscDialogs.timelineRegeneration.header')"
    :modal="true"
    :closable="false"
    :draggable="false"
    class="gp-dialog-sm timeline-regeneration-modal"
  >
    <div class="regeneration-content">
      <div class="icon-container">
        <ProgressSpinner
          style="width: 60px; height: 60px"
          stroke-width="4"
          animationDuration="1s"
        />
      </div>

      <div class="message-container">
        <h3 class="regeneration-title">{{ title }}</h3>
        <p class="regeneration-message">{{ message }}</p>

        <!-- Progress tracking (shown when jobId is provided) -->
        <div v-if="jobId && jobProgress" class="progress-tracking">
          <!-- Completion Message (shown when completed) -->
          <div v-if="jobProgress.status === 'COMPLETED'" class="completion-indicator">
            <i class="pi pi-check-circle"></i>
            <span>{{ t('miscDialogs.timelineRegeneration.completedMessage') }}</span>
          </div>

          <div class="progress-header">
            <span class="progress-step">{{ formatMessageDescriptor(jobProgress.currentStep) }}</span>
            <span class="progress-percentage">{{ jobProgress.progressPercentage }}%</span>
          </div>

          <ProgressBar
            :value="jobProgress.progressPercentage"
            :showValue="false"
            :class="['progress-bar', { 'progress-complete': jobProgress.status === 'COMPLETED' }]"
          />

          <!-- Sub-progress details -->
          <div v-if="jobProgress.details" class="progress-details">
            <!-- GPS Loading -->
            <div v-if="jobProgress.details.gpsPointsLoaded" class="detail-item">
              <i class="pi pi-map-marker"></i>
              <span>{{ t('miscDialogs.timelineRegeneration.gpsPointsLoaded', { loaded: jobProgress.details.gpsPointsLoaded.toLocaleString(), total: jobProgress.details.totalGpsPoints?.toLocaleString() || '?' }) }}</span>
            </div>

            <!-- GPS Processing (State Machine) -->
            <div v-if="jobProgress.details.processedPoints !== undefined" class="detail-item">
              <i class="pi pi-cog"></i>
              <span>{{ t('miscDialogs.timelineRegeneration.pointsProcessed', { processed: jobProgress.details.processedPoints.toLocaleString(), total: jobProgress.details.totalPoints?.toLocaleString() || '?' }) }}</span>
            </div>

            <!-- Geocoding Summary -->
            <div v-if="jobProgress.details.totalLocations" class="detail-item">
              <i class="pi pi-globe"></i>
              <span>{{ t('miscDialogs.timelineRegeneration.locationsGeocoded', { resolved: jobProgress.details.totalResolved || 0, total: jobProgress.details.totalLocations }) }}</span>
            </div>

            <!-- Geocoding Breakdown -->
            <div v-if="jobProgress.details.favoritesResolved" class="detail-item detail-sub">
              <i class="pi pi-star"></i>
              <span>{{ t('miscDialogs.timelineRegeneration.fromFavorites', { count: jobProgress.details.favoritesResolved }) }}</span>
            </div>

            <div v-if="jobProgress.details.cachedResolved" class="detail-item detail-sub">
              <i class="pi pi-database"></i>
              <span>{{ t('miscDialogs.timelineRegeneration.fromCache', { count: jobProgress.details.cachedResolved }) }}</span>
            </div>

            <div v-if="jobProgress.details.externalCompleted" class="detail-item detail-sub">
              <i class="pi pi-cloud"></i>
              <span>{{ t('miscDialogs.timelineRegeneration.fromExternalApi', { count: jobProgress.details.externalCompleted }) }}</span>
            </div>

            <div v-if="jobProgress.details.externalPending > 0" class="detail-item detail-pending">
              <i class="pi pi-clock"></i>
              <span>{{ t('miscDialogs.timelineRegeneration.pending', { count: jobProgress.details.externalPending }) }}</span>
            </div>
          </div>

          <!-- Link to detailed progress page -->
          <Button
            :label="t('miscDialogs.timelineRegeneration.viewDetailedProgress')"
            icon="pi pi-external-link"
            class="view-details-btn"
            severity="info"
            outlined
            @click="goToJobDetails"
          />
        </div>

        <!-- Fallback for legacy mode (no jobId) -->
        <p v-else class="regeneration-note">
          {{ t('miscDialogs.timelineRegeneration.fallbackNote') }}
        </p>
      </div>

      <!-- Progress indicator (shown when no detailed progress available) -->
      <div v-if="!jobId || !jobProgress" class="progress-indicator">
        <div class="progress-dots">
          <span class="dot"></span>
          <span class="dot"></span>
          <span class="dot"></span>
        </div>
        <p class="progress-text">{{ t('miscDialogs.timelineRegeneration.pleaseWait') }}</p>
      </div>
    </div>
  </Dialog>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { formatMessageDescriptor } from '@/utils/messageDescriptor'
import Dialog from 'primevue/dialog'
import ProgressSpinner from 'primevue/progressspinner'
import ProgressBar from 'primevue/progressbar'
import Button from 'primevue/button'

const { t } = useI18n()

// Props
const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  type: {
    type: String,
    default: 'general', // 'favorite', 'favorite-delete', 'preferences', 'general', 'classification', 'reconstruction'
    validator: (value) => ['favorite', 'favorite-delete', 'preferences', 'general', 'classification', 'reconstruction'].includes(value)
  },
  jobId: {
    type: String,
    default: null
  },
  jobProgress: {
    type: Object,
    default: null
  }
})

// Emits
const emit = defineEmits(['update:visible'])

// Router
const router = useRouter()

// Local state
const internalVisible = ref(props.visible)

// Computed properties for dynamic content based on type
const title = computed(() => {
  switch (props.type) {
    case 'favorite':
      return t('miscDialogs.timelineRegeneration.titles.favorite')
    case 'favorite-delete':
      return t('miscDialogs.timelineRegeneration.titles.favoriteDelete')
    case 'preferences':
      return t('miscDialogs.timelineRegeneration.titles.preferences')
    case 'classification':
      return t('miscDialogs.timelineRegeneration.titles.classification')
    case 'reconstruction':
      return t('miscDialogs.timelineRegeneration.titles.reconstruction')
    default:
      return t('miscDialogs.timelineRegeneration.titles.general')
  }
})

const message = computed(() => {
  switch (props.type) {
    case 'favorite':
      return t('miscDialogs.timelineRegeneration.messages.favorite')
    case 'favorite-delete':
      return t('miscDialogs.timelineRegeneration.messages.favoriteDelete')
    case 'preferences':
      return t('miscDialogs.timelineRegeneration.messages.preferences')
    case 'classification':
      return t('miscDialogs.timelineRegeneration.messages.classification')
    case 'reconstruction':
      return t('miscDialogs.timelineRegeneration.messages.reconstruction')
    default:
      return t('miscDialogs.timelineRegeneration.messages.general')
  }
})

// Watch for prop changes
watch(() => props.visible, (newValue) => {
  internalVisible.value = newValue
})

watch(internalVisible, (newValue) => {
  if (newValue !== props.visible) {
    emit('update:visible', newValue)
  }
})

// Methods
const goToJobDetails = () => {
  if (props.jobId) {
    // Open in new tab
    const url = router.resolve(`/app/timeline/jobs/${props.jobId}`).href
    window.open(url, '_blank')
    // Don't close the modal - let user keep watching progress in both places
  }
}
</script>

<style scoped>
.regeneration-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 1.5rem;
}

.icon-container {
  margin-top: 0.5rem;
}

.message-container {
  max-width: 380px;
}

.regeneration-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.75rem 0;
  line-height: 1.4;
}

.regeneration-message {
  font-size: 0.95rem;
  color: var(--gp-text-secondary);
  line-height: 1.5;
  margin: 0 0 1rem 0;
}

.regeneration-note {
  font-size: 0.85rem;
  color: var(--gp-text-muted);
  line-height: 1.4;
  margin: 0;
  padding: 0.75rem;
  background: var(--gp-surface-ground);
  border-radius: 8px;
  border: 1px solid var(--gp-border);
}

.progress-tracking {
  margin-top: 1rem;
  padding: 1rem;
  background: var(--gp-surface-ground);
  border-radius: 8px;
  border: 1px solid var(--gp-border);
}

.completion-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.75rem;
  margin-bottom: 1rem;
  background: var(--gp-success-soft);
  border: 1px solid var(--gp-success-border);
  border-radius: 6px;
  color: var(--gp-success-text);
  font-weight: 600;
  font-size: 0.95rem;
}

.completion-indicator i {
  font-size: 1.25rem;
  color: var(--gp-success);
}

.progress-bar.progress-complete :deep(.p-progressbar-value) {
  background: var(--p-green-500);
  transition: background 0.3s ease;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.progress-step {
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--gp-text-primary);
}

.progress-percentage {
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-primary);
}

.progress-bar {
  margin-bottom: 1rem;
}

.progress-details {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 1rem;
  padding: 0.75rem;
  background: var(--gp-surface-card);
  border-radius: 6px;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: var(--gp-text-secondary);
}

.detail-item i {
  color: var(--gp-primary);
  font-size: 0.875rem;
  flex-shrink: 0;
}

.detail-sub {
  padding-left: 1rem;
  font-size: 0.8rem;
  opacity: 0.9;
}

.detail-pending {
  color: var(--gp-warning-text);
  font-weight: 600;
}

.detail-pending i {
  color: var(--p-orange-500);
}

.view-details-btn {
  width: 100%;
  justify-content: center;
  margin-top: 0.5rem;
}

.progress-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
}

.progress-dots {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.dot {
  width: 8px;
  height: 8px;
  background: var(--gp-primary);
  border-radius: 50%;
  animation: pulse 1.5s infinite;
}

.dot:nth-child(2) {
  animation-delay: 0.5s;
}

.dot:nth-child(3) {
  animation-delay: 1s;
}

.progress-text {
  font-size: 0.9rem;
  color: var(--gp-text-muted);
  margin: 0;
  font-weight: 500;
}

@keyframes pulse {
  0%, 100% {
    opacity: 0.4;
    transform: scale(1);
  }
  50% {
    opacity: 1;
    transform: scale(1.1);
  }
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .regeneration-content {
    gap: 1.5rem;
    padding: 0.5rem;
  }
  
  .message-container {
    max-width: 100%;
    padding: 0 0.5rem;
  }
  
  .regeneration-title {
    font-size: 1rem;
  }
  
  .regeneration-message {
    font-size: 0.9rem;
  }
  
  .regeneration-note {
    font-size: 0.8rem;
    padding: 1rem;
  }
}

/* Large mobile phones (iPhone 14 Pro Max, iPhone 15 Pro Max, iPhone 16 Pro Max) */
@media (max-width: 768px) and (min-width: 415px) {
  .regeneration-content {
    gap: 1.75rem;
    padding: 0.75rem;
  }
  
  .message-container {
    padding: 0 0.75rem;
  }
  
  .regeneration-note {
    padding: 1.25rem;
  }
}
</style>
