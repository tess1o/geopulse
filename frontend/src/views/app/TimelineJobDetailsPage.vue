<template>
  <AppLayout>
    <PageContainer>
      <div class="timeline-job-details-page">
        <!-- Page Header -->
        <div class="page-header">
          <div class="header-content">
            <div class="header-text">
              <h1 class="page-title">{{ t('timelineJobs.detailsPage.title') }}</h1>
              <p class="page-description">
                {{ t('timelineJobs.detailsPage.description') }}
              </p>
            </div>
            <div class="header-actions">
              <Button
                :label="t('timelineJobs.detailsPage.backButton')"
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
              <p class="loading-text">{{ t('timelineJobs.detailsPage.loading') }}</p>
            </div>
          </template>
        </Card>

        <!-- Error State -->
        <Message v-if="error" severity="error" class="error-message">
          <div class="error-content">
            <strong>{{ t('timelineJobs.detailsPage.error.title') }}</strong>
            <p>{{ error }}</p>
            <Button
              :label="t('common.tryAgain')"
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
                  <p class="job-id">{{ t('timelineJobs.detailsPage.jobIdLabel', { jobId }) }}</p>
                </div>
                <div class="status-badge">
                  <Tag :value="jobProgress.status" :severity="statusSeverity" />
                </div>
              </div>

              <!-- Progress Bar -->
              <div class="progress-section" v-if="jobProgress.status !== 'FAILED'">
                <div class="progress-header">
                  <span class="progress-label">{{ formatMessageDescriptor(jobProgress.currentStep) }}</span>
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
                  <span class="detail-label">{{ t('timelineJobs.detailsPage.details.started') }}</span>
                  <span class="detail-value">{{ formatTimestamp(jobProgress.startTime) }}</span>
                </div>
                <div class="detail-row" v-if="jobProgress.endTime">
                  <span class="detail-label">{{ t('timelineJobs.detailsPage.details.completed') }}</span>
                  <span class="detail-value">{{ formatTimestamp(jobProgress.endTime) }}</span>
                </div>
                <div class="detail-row">
                  <span class="detail-label">{{ t('timelineJobs.detailsPage.details.duration') }}</span>
                  <span class="detail-value">{{ formatDuration(jobProgress.durationMs) }}</span>
                </div>
                <div class="detail-row">
                  <span class="detail-label">{{ t('timelineJobs.detailsPage.details.currentStep') }}</span>
                  <span class="detail-value">{{ t('timelineJobs.detailsPage.details.stepOf', { current: jobProgress.currentStepIndex, total: jobProgress.totalSteps }) }}</span>
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
                {{ t('timelineJobs.detailsPage.stepsTitle') }}
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
                        {{ t('timelineJobs.detailsPage.detail.gpsPoints', { loaded: jobProgress.details.gpsPointsLoaded.toLocaleString(), total: jobProgress.details.totalGpsPoints?.toLocaleString() || '?' }) }}
                      </div>

                      <!-- GPS Processing Details (State Machine) -->
                      <div v-if="jobProgress.details.processedPoints !== undefined" class="detail-section">
                        <div class="detail-item detail-header">
                          <i class="pi pi-cog"></i>
                          <strong>{{ t('timelineJobs.detailsPage.detail.processingHeader', { processed: jobProgress.details.processedPoints?.toLocaleString() || 0, total: jobProgress.details.totalPoints?.toLocaleString() || '?' }) }}</strong>
                        </div>

                        <div v-if="jobProgress.details.pointsRemaining !== undefined && jobProgress.details.pointsRemaining > 0" class="detail-item detail-sub">
                          <i class="pi pi-clock"></i>
                          {{ t('timelineJobs.detailsPage.detail.remaining', { count: jobProgress.details.pointsRemaining.toLocaleString() }) }}
                        </div>
                      </div>

                      <!-- Geocoding Details -->
                      <div v-if="jobProgress.details.totalLocations" class="detail-section">
                        <div class="detail-item detail-header">
                          <i class="pi pi-globe"></i>
                          <strong>{{ t('timelineJobs.detailsPage.detail.geocodingHeader', { resolved: jobProgress.details.totalResolved || 0, total: jobProgress.details.totalLocations }) }}</strong>
                        </div>

                        <div v-if="jobProgress.details.favoritesResolved" class="detail-item detail-sub">
                          <i class="pi pi-star"></i>
                          {{ t('timelineJobs.detailsPage.detail.favoritesInstant', { count: jobProgress.details.favoritesResolved }) }}
                        </div>

                        <div v-if="jobProgress.details.cachedResolved" class="detail-item detail-sub">
                          <i class="pi pi-database"></i>
                          {{ t('timelineJobs.detailsPage.detail.cachedInDatabase', { count: jobProgress.details.cachedResolved }) }}
                        </div>

                        <div v-if="jobProgress.details.externalCompleted" class="detail-item detail-sub">
                          <i class="pi pi-cloud"></i>
                          {{ t('timelineJobs.detailsPage.detail.externalApiCalls', { count: jobProgress.details.externalCompleted }) }}
                        </div>

                        <div v-if="jobProgress.details.externalPending > 0" class="detail-item detail-sub detail-pending">
                          <i class="pi pi-clock"></i>
                          {{ t('timelineJobs.detailsPage.detail.pending', { count: jobProgress.details.externalPending }) }}
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
                  <h2>{{ t('timelineJobs.detailsPage.completion.title') }}</h2>
                  <p>{{ t('timelineJobs.detailsPage.completion.message') }}</p>
                  <div class="action-buttons">
                    <Button
                      :label="t('timelineJobs.listPage.noJob.viewTimelineButton')"
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
import { useI18n } from 'vue-i18n'
import { useTimelineJobProgress } from '@/composables/useTimelineJobProgress'
import { useTimezone } from '@/composables/useTimezone'
import { formatMessageDescriptor } from '@/utils/messageDescriptor'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import Card from 'primevue/card'
import Button from 'primevue/button'
import ProgressBar from 'primevue/progressbar'
import ProgressSpinner from 'primevue/progressspinner'
import Message from 'primevue/message'
import Tag from 'primevue/tag'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const timezone = useTimezone()

const jobId = computed(() => route.params.jobId)

const { jobProgress, error, startPolling, fetchProgress } = useTimelineJobProgress()

// Timeline processing steps
const stepKeys = [
  'acquiringLock',
  'cleaningUp',
  'preparingGpsProcessing',
  'processingGeocoding',
  'postProcessingTrips',
  'mergingSimplifying',
  'persistingTimeline',
  'dataGapDetection',
  'finalizing'
]

const steps = computed(() => stepKeys.map((key, i) => ({
  index: i + 1,
  title: t(`timelineJobs.detailsPage.steps.${key}.title`),
  description: t(`timelineJobs.detailsPage.steps.${key}.description`)
})))

// Computed properties
const statusText = computed(() => {
  if (!jobProgress.value) return t('timelineJobs.detailsPage.status.loading')

  switch (jobProgress.value.status) {
    case 'QUEUED': return t('timelineJobs.detailsPage.status.queued')
    case 'RUNNING': return t('timelineJobs.detailsPage.status.running')
    case 'COMPLETED': return t('timelineJobs.detailsPage.status.completed')
    case 'FAILED': return t('timelineJobs.detailsPage.status.failed')
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
  if (!timestamp) return t('timelineJobs.listPage.notAvailable')
  return `${timezone.formatDateDisplay(timestamp)} ${timezone.formatTime(timestamp, { withSeconds: true })}`
}

const formatDuration = (durationMs) => {
  if (!durationMs) return t('timelineJobs.detailsPage.durationZero')

  const seconds = Math.floor(durationMs / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)

  if (hours > 0) {
    return t('timelineJobs.detailsPage.durationHoursMinutesSeconds', { hours, minutes: minutes % 60, seconds: seconds % 60 })
  } else if (minutes > 0) {
    return t('timelineJobs.detailsPage.durationMinutesSeconds', { minutes, seconds: seconds % 60 })
  } else {
    return t('timelineJobs.detailsPage.durationSeconds', { seconds })
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
