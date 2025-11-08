<template>
  <Dialog
    v-model:visible="internalVisible"
    :header="'Timeline Regeneration'"
    :modal="true"
    :closable="false"
    :draggable="false"
    :style="{ width: '500px' }"
    class="timeline-regeneration-modal"
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
            <span>Timeline generation completed successfully!</span>
          </div>

          <div class="progress-header">
            <span class="progress-step">{{ jobProgress.currentStep }}</span>
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
              <span>{{ jobProgress.details.gpsPointsLoaded.toLocaleString() }} / {{ jobProgress.details.totalGpsPoints?.toLocaleString() || '?' }} GPS points</span>
            </div>

            <!-- Geocoding Summary -->
            <div v-if="jobProgress.details.totalLocations" class="detail-item">
              <i class="pi pi-globe"></i>
              <span>{{ jobProgress.details.totalResolved || 0 }} / {{ jobProgress.details.totalLocations }} locations geocoded</span>
            </div>

            <!-- Geocoding Breakdown -->
            <div v-if="jobProgress.details.favoritesResolved" class="detail-item detail-sub">
              <i class="pi pi-star"></i>
              <span>{{ jobProgress.details.favoritesResolved }} from favorites</span>
            </div>

            <div v-if="jobProgress.details.cachedResolved" class="detail-item detail-sub">
              <i class="pi pi-database"></i>
              <span>{{ jobProgress.details.cachedResolved }} from cache</span>
            </div>

            <div v-if="jobProgress.details.externalCompleted" class="detail-item detail-sub">
              <i class="pi pi-cloud"></i>
              <span>{{ jobProgress.details.externalCompleted }} from external API</span>
            </div>

            <div v-if="jobProgress.details.externalPending > 0" class="detail-item detail-pending">
              <i class="pi pi-clock"></i>
              <span>{{ jobProgress.details.externalPending }} pending (1/sec)</span>
            </div>
          </div>

          <!-- Link to detailed progress page -->
          <Button
            label="View Detailed Progress"
            icon="pi pi-external-link"
            class="view-details-btn"
            severity="info"
            outlined
            @click="goToJobDetails"
          />
        </div>

        <!-- Fallback for legacy mode (no jobId) -->
        <p v-else class="regeneration-note">
          This process may take 5-15 seconds or longer depending on your data size.
          Your timeline will be temporarily unavailable during regeneration.
        </p>
      </div>

      <!-- Progress indicator (shown when no detailed progress available) -->
      <div v-if="!jobId || !jobProgress" class="progress-indicator">
        <div class="progress-dots">
          <span class="dot"></span>
          <span class="dot"></span>
          <span class="dot"></span>
        </div>
        <p class="progress-text">Please wait...</p>
      </div>
    </div>
  </Dialog>
</template>

<script setup>
import { ref, watch, computed, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import Dialog from 'primevue/dialog'
import ProgressSpinner from 'primevue/progressspinner'
import ProgressBar from 'primevue/progressbar'
import Button from 'primevue/button'
import { useTimelineJobProgress } from '@/composables/useTimelineJobProgress'

// Props
const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  type: {
    type: String,
    default: 'general', // 'favorite', 'favorite-delete', 'preferences', 'general', 'classification'
    validator: (value) => ['favorite', 'favorite-delete', 'preferences', 'general', 'classification'].includes(value)
  },
  jobId: {
    type: String,
    default: null
  }
})

// Emits
const emit = defineEmits(['update:visible'])

// Router
const router = useRouter()

// Local state
const internalVisible = ref(props.visible)

// Job progress tracking
const { jobProgress, startPolling, stopPolling } = useTimelineJobProgress()

// Watch for jobId changes to start/stop polling
watch(() => props.jobId, (newJobId) => {
  if (newJobId && props.visible) {
    startPolling(newJobId)
  } else {
    stopPolling()
  }
}, { immediate: true })

// Watch for visibility changes
watch(() => props.visible, (newValue) => {
  if (newValue && props.jobId) {
    startPolling(props.jobId)
  } else if (!newValue) {
    stopPolling()
  }
})

// Cleanup on unmount
onUnmounted(() => {
  stopPolling()
})

// Computed properties for dynamic content based on type
const title = computed(() => {
  switch (props.type) {
    case 'favorite':
      return 'Adding Favorite & Regenerating Timeline'
    case 'favorite-delete':
      return 'Deleting Favorite & Regenerating Timeline'
    case 'preferences':
      return 'Applying Preferences & Regenerating Timeline'
    case 'classification':
      return 'Updating Trip Classifications'
    default:
      return 'Regenerating Timeline'
  }
})

const message = computed(() => {
  switch (props.type) {
    case 'favorite':
      return 'We\'re adding your favorite location and regenerating your complete timeline to incorporate this change. This ensures all timeline data remains accurate and up-to-date.'
    case 'favorite-delete':
      return 'We\'re removing your favorite location and regenerating your complete timeline to reflect this change. This ensures all timeline data remains accurate and up-to-date.'
    case 'preferences':
      return 'We\'re applying your new preferences and regenerating your complete timeline based on the updated settings. This ensures optimal timeline accuracy with your preferences.'
    case 'classification':
      return 'We\'re recalculating movement types for your existing trips based on your updated speed thresholds. This process will update how your trips are classified without changing the underlying timeline structure.'
    default:
      return 'We\'re regenerating your complete timeline from your GPS data. This process ensures your timeline is accurate and reflects all available location information.'
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
.timeline-regeneration-modal :deep(.p-dialog) {
  background: var(--gp-surface-card);
  border: 1px solid var(--gp-surface-border);
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
}

.timeline-regeneration-modal :deep(.p-dialog-header) {
  background: var(--gp-surface-card);
  border-bottom: 1px solid var(--gp-surface-border);
  padding: 1.5rem 1.5rem 1rem 1.5rem;
}

.timeline-regeneration-modal :deep(.p-dialog-title) {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.timeline-regeneration-modal :deep(.p-dialog-content) {
  padding: 0 1.5rem 1.5rem 1.5rem;
  background: var(--gp-surface-card);
}

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
  border: 1px solid var(--gp-surface-border);
}

.progress-tracking {
  margin-top: 1rem;
  padding: 1rem;
  background: var(--gp-surface-ground);
  border-radius: 8px;
  border: 1px solid var(--gp-surface-border);
}

.completion-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.75rem;
  margin-bottom: 1rem;
  background: var(--green-50);
  border: 1px solid var(--green-200);
  border-radius: 6px;
  color: var(--green-700);
  font-weight: 600;
  font-size: 0.95rem;
}

.completion-indicator i {
  font-size: 1.25rem;
  color: var(--green-600);
}

.progress-bar.progress-complete :deep(.p-progressbar-value) {
  background: var(--green-500);
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
  color: var(--gp-primary-color);
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
  color: var(--gp-primary-color);
  font-size: 0.875rem;
  flex-shrink: 0;
}

.detail-sub {
  padding-left: 1rem;
  font-size: 0.8rem;
  opacity: 0.9;
}

.detail-pending {
  color: var(--orange-600);
  font-weight: 600;
}

.detail-pending i {
  color: var(--orange-500);
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
  background: var(--gp-primary-color);
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

/* Dark mode support */
.p-dark .timeline-regeneration-modal :deep(.p-dialog) {
  background: var(--gp-surface-dark);
  border-color: var(--gp-surface-border-dark);
}

.p-dark .timeline-regeneration-modal :deep(.p-dialog-header) {
  background: var(--gp-surface-dark);
  border-bottom-color: var(--gp-surface-border-dark);
}

.p-dark .timeline-regeneration-modal :deep(.p-dialog-content) {
  background: var(--gp-surface-dark);
}

.p-dark .regeneration-note {
  background: var(--gp-surface-ground-dark);
  border-color: var(--gp-surface-border-dark);
}

.p-dark .progress-tracking {
  background: var(--gp-surface-ground-dark);
  border-color: var(--gp-surface-border-dark);
}

.p-dark .progress-details {
  background: var(--gp-surface-dark);
}

.p-dark .completion-indicator {
  background: rgba(34, 197, 94, 0.1);
  border-color: rgba(34, 197, 94, 0.3);
  color: var(--green-400);
}

.p-dark .completion-indicator i {
  color: var(--green-500);
}

.p-dark .detail-pending {
  color: var(--orange-400);
}

.p-dark .detail-pending i {
  color: var(--orange-500);
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .timeline-regeneration-modal :deep(.p-dialog) {
    width: 90vw !important;
    max-width: 360px !important;
    margin: 0 20px;
  }
  
  .timeline-regeneration-modal :deep(.p-dialog-content) {
    padding: 0 2rem 2rem 2rem;
  }
  
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
  .timeline-regeneration-modal :deep(.p-dialog) {
    width: 85vw !important;
    max-width: 380px !important;
    margin: 0 25px;
  }
  
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

/* Extra large mobile phones (iPhone 16 Pro Max and similar) */
@media (max-width: 768px) and (min-width: 430px) {
  .timeline-regeneration-modal :deep(.p-dialog) {
    width: 380px !important;
    max-width: 380px !important;
    margin: 0 auto;
  }
}
</style>