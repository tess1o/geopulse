<template>
  <AppLayout>
    <PageContainer>
      <div class="timeline-job-details-page">
        <!-- Page Header -->
        <div class="page-header">
          <div class="header-content">
            <div class="header-text">
              <h1 class="page-title">Timeline Generation Progress</h1>
              <p class="page-description">
                Real-time progress tracking for timeline regeneration job.
              </p>
            </div>
            <div class="header-actions">
              <Button
                label="Back to Timeline"
                icon="pi pi-arrow-left"
                severity="secondary"
                outlined
                @click="goToTimeline"
              />
            </div>
          </div>
        </div>

        <!-- Loading State -->
        <Card v-if="!jobProgress && !error" class="loading-card">
          <template #content>
            <div class="loading-content">
              <ProgressSpinner style="width: 50px; height: 50px" strokeWidth="4" />
              <p class="loading-text">Loading job details...</p>
            </div>
          </template>
        </Card>

        <!-- Error State -->
        <Message v-if="error" severity="error" class="error-message">
          <div class="error-content">
            <strong>Failed to load job details</strong>
            <p>{{ error }}</p>
            <Button
              label="Try Again"
              size="small"
              @click="retryFetch"
              class="mt-2"
            />
          </div>
        </Message>

        <!-- Job Progress Display -->
        <div v-if="jobProgress" class="job-progress-container">
          <!-- Status Card -->
          <Card class="status-card">
            <template #content>
              <div class="status-header">
                <div class="status-info">
                  <h2 class="status-title">
                    <i :class="statusIcon" :style="{ color: statusColor }"></i>
                    {{ statusText }}
                  </h2>
                  <p class="job-id">Job ID: {{ jobId }}</p>
                </div>
                <div class="status-badge">
                  <Tag :value="jobProgress.status" :severity="statusSeverity" />
                </div>
              </div>

              <!-- Progress Bar -->
              <div class="progress-section" v-if="jobProgress.status !== 'FAILED'">
                <div class="progress-header">
                  <span class="progress-label">{{ jobProgress.currentStep }}</span>
                  <span class="progress-percentage">{{ jobProgress.progressPercentage }}%</span>
                </div>
                <ProgressBar
                  :value="jobProgress.progressPercentage"
                  :showValue="false"
                  :class="{ 'progress-complete': jobProgress.status === 'COMPLETED' }"
                />
              </div>

              <!-- Job Details -->
              <div class="job-details">
                <div class="detail-row">
                  <span class="detail-label">Started:</span>
                  <span class="detail-value">{{ formatTimestamp(jobProgress.startTime) }}</span>
                </div>
                <div class="detail-row" v-if="jobProgress.endTime">
                  <span class="detail-label">Completed:</span>
                  <span class="detail-value">{{ formatTimestamp(jobProgress.endTime) }}</span>
                </div>
                <div class="detail-row">
                  <span class="detail-label">Duration:</span>
                  <span class="detail-value">{{ formatDuration(jobProgress.durationMs) }}</span>
                </div>
                <div class="detail-row">
                  <span class="detail-label">Current Step:</span>
                  <span class="detail-value">{{ jobProgress.currentStepIndex }} of {{ jobProgress.totalSteps }}</span>
                </div>
              </div>

              <!-- Error Message -->
              <Message v-if="jobProgress.errorMessage" severity="error" :closable="false">
                {{ jobProgress.errorMessage }}
              </Message>
            </template>
          </Card>

          <!-- Steps Card -->
          <Card class="steps-card">
            <template #title>
              <div class="card-title">
                <i class="pi pi-list"></i>
                Processing Steps
              </div>
            </template>
            <template #content>
              <div class="steps-list">
                <div
                  v-for="step in steps"
                  :key="step.index"
                  :class="['step-item', getStepClass(step.index)]"
                >
                  <div class="step-indicator">
                    <i :class="getStepIcon(step.index)"></i>
                  </div>
                  <div class="step-content">
                    <h3 class="step-title">{{ step.title }}</h3>
                    <p class="step-description">{{ step.description }}</p>
                    <div v-if="isCurrentStep(step.index) && jobProgress.details" class="step-details">
                      <!-- GPS Loading Details -->
                      <div v-if="jobProgress.details.gpsPointsLoaded" class="detail-item">
                        <i class="pi pi-map-marker"></i>
                        GPS Points: {{ jobProgress.details.gpsPointsLoaded.toLocaleString() }} / {{ jobProgress.details.totalGpsPoints?.toLocaleString() || '?' }}
                      </div>

                      <!-- GPS Processing Details (State Machine) -->
                      <div v-if="jobProgress.details.processedPoints !== undefined" class="detail-section">
                        <div class="detail-item detail-header">
                          <i class="pi pi-cog"></i>
                          <strong>Processing GPS Points: {{ jobProgress.details.processedPoints?.toLocaleString() || 0 }} / {{ jobProgress.details.totalPoints?.toLocaleString() || '?' }}</strong>
                        </div>

                        <div v-if="jobProgress.details.pointsRemaining !== undefined && jobProgress.details.pointsRemaining > 0" class="detail-item detail-sub">
                          <i class="pi pi-clock"></i>
                          Remaining: {{ jobProgress.details.pointsRemaining.toLocaleString() }} points
                        </div>
                      </div>

                      <!-- Geocoding Details -->
                      <div v-if="jobProgress.details.totalLocations" class="detail-section">
                        <div class="detail-item detail-header">
                          <i class="pi pi-globe"></i>
                          <strong>Reverse Geocoding: {{ jobProgress.details.totalResolved || 0 }} / {{ jobProgress.details.totalLocations }} locations</strong>
                        </div>

                        <div v-if="jobProgress.details.favoritesResolved" class="detail-item detail-sub">
                          <i class="pi pi-star"></i>
                          Favorites (Instant): {{ jobProgress.details.favoritesResolved }}
                        </div>

                        <div v-if="jobProgress.details.cachedResolved" class="detail-item detail-sub">
                          <i class="pi pi-database"></i>
                          Cached in Database: {{ jobProgress.details.cachedResolved }}
                        </div>

                        <div v-if="jobProgress.details.externalCompleted" class="detail-item detail-sub">
                          <i class="pi pi-cloud"></i>
                          External API Calls: {{ jobProgress.details.externalCompleted }}
                        </div>

                        <div v-if="jobProgress.details.externalPending > 0" class="detail-item detail-sub detail-pending">
                          <i class="pi pi-clock"></i>
                          Pending: {{ jobProgress.details.externalPending }} (rate limited: 1/sec)
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </template>
          </Card>

          <!-- Actions -->
          <div class="actions-section" v-if="jobProgress.status === 'COMPLETED'">
            <Card>
              <template #content>
                <div class="completion-message">
                  <i class="pi pi-check-circle" style="font-size: 3rem; color: var(--green-500)"></i>
                  <h2>Timeline Generation Complete!</h2>
                  <p>Your timeline has been successfully regenerated with all the latest GPS data.</p>
                  <div class="action-buttons">
                    <Button
                      label="View Timeline"
                      icon="pi pi-calendar"
                      @click="goToTimeline"
                      size="large"
                    />
                  </div>
                </div>
              </template>
            </Card>
          </div>
        </div>
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTimelineJobProgress } from '@/composables/useTimelineJobProgress'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import Card from 'primevue/card'
import Button from 'primevue/button'
import ProgressBar from 'primevue/progressbar'
import ProgressSpinner from 'primevue/progressspinner'
import Message from 'primevue/message'
import Tag from 'primevue/tag'

const route = useRoute()
const router = useRouter()

const jobId = computed(() => route.params.jobId)

const { jobProgress, error, startPolling, fetchProgress } = useTimelineJobProgress()

// Timeline processing steps
const steps = [
  { index: 1, title: 'Acquiring Lock', description: 'Ensuring exclusive access to timeline data' },
  { index: 2, title: 'Cleaning Up', description: 'Removing old timeline events' },
  { index: 3, title: 'Preparing GPS Processing', description: 'Counting GPS points and preparing streaming iterator' },
  { index: 4, title: 'Processing & Geocoding', description: 'Streaming and processing GPS points through state machine, then resolving location names' },
  { index: 5, title: 'Post-Processing Trips', description: 'Validating and refining trip detection' },
  { index: 6, title: 'Merging & Simplifying', description: 'Applying timeline optimizations' },
  { index: 7, title: 'Persisting Timeline', description: 'Saving timeline events to database' },
  { index: 8, title: 'Data Gap Detection', description: 'Identifying gaps in GPS coverage' },
  { index: 9, title: 'Finalizing', description: 'Calculating milestones and completing generation' }
]

// Computed properties
const statusText = computed(() => {
  if (!jobProgress.value) return 'Loading...'

  switch (jobProgress.value.status) {
    case 'QUEUED': return 'Queued for Processing'
    case 'RUNNING': return 'Processing Timeline'
    case 'COMPLETED': return 'Completed Successfully'
    case 'FAILED': return 'Failed'
    default: return jobProgress.value.status
  }
})

const statusIcon = computed(() => {
  if (!jobProgress.value) return 'pi pi-spin pi-spinner'

  switch (jobProgress.value.status) {
    case 'QUEUED': return 'pi pi-clock'
    case 'RUNNING': return 'pi pi-spin pi-spinner'
    case 'COMPLETED': return 'pi pi-check-circle'
    case 'FAILED': return 'pi pi-times-circle'
    default: return 'pi pi-info-circle'
  }
})

const statusColor = computed(() => {
  if (!jobProgress.value) return 'var(--primary-color)'

  switch (jobProgress.value.status) {
    case 'QUEUED': return 'var(--blue-500)'
    case 'RUNNING': return 'var(--primary-color)'
    case 'COMPLETED': return 'var(--green-500)'
    case 'FAILED': return 'var(--red-500)'
    default: return 'var(--text-color)'
  }
})

const statusSeverity = computed(() => {
  if (!jobProgress.value) return 'info'

  switch (jobProgress.value.status) {
    case 'QUEUED': return 'info'
    case 'RUNNING': return 'warning'
    case 'COMPLETED': return 'success'
    case 'FAILED': return 'danger'
    default: return 'info'
  }
})

// Methods
const getStepClass = (stepIndex) => {
  if (!jobProgress.value) return ''

  const currentStep = jobProgress.value.currentStepIndex
  const status = jobProgress.value.status

  // If job is completed, all steps including current one are completed
  if (status === 'COMPLETED' && stepIndex <= currentStep) return 'step-completed'

  if (stepIndex < currentStep) return 'step-completed'
  if (stepIndex === currentStep) return 'step-active'
  return 'step-pending'
}

const getStepIcon = (stepIndex) => {
  if (!jobProgress.value) return 'pi pi-circle'

  const currentStep = jobProgress.value.currentStepIndex
  const status = jobProgress.value.status

  // If job is completed, show check for current step too
  if (status === 'COMPLETED' && stepIndex <= currentStep) return 'pi pi-check-circle'

  if (stepIndex < currentStep) return 'pi pi-check-circle'
  if (stepIndex === currentStep) {
    if (status === 'FAILED') return 'pi pi-times-circle'
    return 'pi pi-spin pi-spinner'
  }
  return 'pi pi-circle'
}

const isCurrentStep = (stepIndex) => {
  if (!jobProgress.value) return false

  // For completed jobs, don't show any step as "current"
  if (jobProgress.value.status === 'COMPLETED') return false

  return jobProgress.value.currentStepIndex === stepIndex
}

const formatTimestamp = (timestamp) => {
  if (!timestamp) return 'N/A'
  return new Date(timestamp).toLocaleString()
}

const formatDuration = (durationMs) => {
  if (!durationMs) return '0s'

  const seconds = Math.floor(durationMs / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)

  if (hours > 0) {
    return `${hours}h ${minutes % 60}m ${seconds % 60}s`
  } else if (minutes > 0) {
    return `${minutes}m ${seconds % 60}s`
  } else {
    return `${seconds}s`
  }
}

const goToTimeline = () => {
  router.push('/app/timeline')
}

const retryFetch = async () => {
  await fetchProgress(jobId.value)
  if (!error.value) {
    startPolling(jobId.value)
  }
}

// Lifecycle
onMounted(() => {
  if (jobId.value) {
    startPolling(jobId.value)
  }
})
</script>

<style scoped>
.timeline-job-details-page {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 2rem;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 2rem;
  flex-wrap: wrap;
}

.header-text h1 {
  margin: 0 0 0.5rem 0;
  font-size: 2rem;
  font-weight: 600;
  color: var(--text-color);
}

.header-text p {
  margin: 0;
  color: var(--text-color-secondary);
  font-size: 1rem;
}

.loading-card,
.error-message {
  margin-bottom: 2rem;
}

.loading-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  padding: 3rem;
}

.loading-text {
  color: var(--text-color-secondary);
  margin: 0;
}

.error-content {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.job-progress-container {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.status-card {
  background: var(--surface-card);
}

.status-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1.5rem;
  gap: 1rem;
}

.status-title {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin: 0 0 0.5rem 0;
  font-size: 1.5rem;
  font-weight: 600;
}

.job-id {
  margin: 0;
  color: var(--text-color-secondary);
  font-size: 0.875rem;
  font-family: monospace;
}

.progress-section {
  margin-bottom: 1.5rem;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 0.5rem;
}

.progress-label {
  font-weight: 500;
  color: var(--text-color);
}

.progress-percentage {
  font-weight: 600;
  color: var(--primary-color);
}

.progress-complete :deep(.p-progressbar-value) {
  background: var(--green-500);
}

.job-details {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
  padding: 1rem;
  background: var(--surface-ground);
  border-radius: var(--border-radius);
}

.detail-row {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.detail-label {
  font-size: 0.875rem;
  color: var(--text-color-secondary);
  font-weight: 500;
}

.detail-value {
  font-size: 1rem;
  color: var(--text-color);
  font-weight: 600;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1.25rem;
}

.steps-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.step-item {
  display: flex;
  gap: 1rem;
  padding: 1.25rem;
  border-left: 3px solid var(--surface-border);
  position: relative;
  transition: all 0.3s ease;
}

.step-item:not(:last-child) {
  border-bottom: 1px solid var(--surface-border);
}

.step-completed {
  opacity: 0.7;
  border-left-color: var(--green-500);
}

.step-active {
  background: var(--primary-50);
  border-left-color: var(--primary-color);
}

.step-pending {
  opacity: 0.5;
}

.step-indicator {
  flex-shrink: 0;
  width: 2rem;
  height: 2rem;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.25rem;
}

.step-completed .step-indicator {
  color: var(--green-500);
}

.step-active .step-indicator {
  color: var(--primary-color);
}

.step-pending .step-indicator {
  color: var(--text-color-secondary);
}

.step-content {
  flex: 1;
}

.step-title {
  margin: 0 0 0.25rem 0;
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--text-color);
}

.step-description {
  margin: 0 0 0.75rem 0;
  color: var(--text-color-secondary);
  font-size: 0.875rem;
}

.step-details {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-top: 0.75rem;
  padding: 0.75rem;
  background: var(--surface-ground);
  border-radius: var(--border-radius);
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
  color: var(--text-color);
}

.detail-item i {
  color: var(--primary-color);
  flex-shrink: 0;
}

.detail-header {
  font-size: 0.95rem;
  padding-bottom: 0.25rem;
  border-bottom: 1px solid var(--surface-border);
  margin-bottom: 0.25rem;
}

.detail-sub {
  padding-left: 1.5rem;
  font-size: 0.85rem;
}

.detail-pending {
  color: var(--orange-600);
  font-weight: 500;
}

.detail-pending i {
  color: var(--orange-500);
}

.completion-message {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  padding: 2rem;
  text-align: center;
}

.completion-message h2 {
  margin: 0;
  font-size: 1.5rem;
  color: var(--text-color);
}

.completion-message p {
  margin: 0;
  color: var(--text-color-secondary);
  max-width: 500px;
}

.action-buttons {
  display: flex;
  gap: 1rem;
  margin-top: 1rem;
}

@media (max-width: 768px) {
  .header-content {
    flex-direction: column;
    align-items: flex-start;
  }

  .job-details {
    grid-template-columns: 1fr;
  }

  .step-details {
    flex-direction: column;
  }
}
</style>
