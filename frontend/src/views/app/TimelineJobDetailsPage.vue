<template>
  <AppLayout>
    <PageContainer>
      <div class="timeline-job-details-page">
        <!-- Page Header -->
        <div class="gp-page-header">
          <div class="gp-page-header-content">
            <div class="gp-page-header-text">
              <h1 class="gp-page-title">{{ t('timelineJobs.detailsPage.title') }}</h1>
              <p class="gp-page-subtitle">
                {{ t('timelineJobs.detailsPage.description') }}
              </p>
            </div>
            <div class="gp-page-actions">
              <Button
                v-if="jobProgress"
                :label="t('timelineJobs.detailsPage.diagnostics.button')"
                icon="pi pi-clipboard"
                severity="secondary"
                outlined
                :loading="diagnosticsLoading"
                @click="openDiagnostics"
              />
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
                  <span class="detail-label">{{ t('timelineJobs.detailsPage.details.type') }}</span>
                  <span class="detail-value">{{ t(`timelineJobs.types.${jobProgress.type || 'OTHER'}`) }}</span>
                </div>
                <div class="detail-row" v-if="summary.gpsPoints != null">
                  <span class="detail-label">{{ t('timelineJobs.detailsPage.details.gpsPoints') }}</span>
                  <span class="detail-value">{{ formatCount(summary.gpsPoints) }}</span>
                </div>
                <div class="detail-row" v-if="summary.stays != null">
                  <span class="detail-label">{{ t('timelineJobs.detailsPage.details.produced') }}</span>
                  <span class="detail-value">{{ t('timelineJobs.detailsPage.details.producedValue', { stays: formatCount(summary.stays), trips: formatCount(summary.trips), gaps: formatCount(summary.dataGaps) }) }}</span>
                </div>
                <div class="detail-row" v-if="summary.correctionsApplied || summary.correctionsNotApplied">
                  <span class="detail-label">{{ t('timelineJobs.detailsPage.details.corrections') }}</span>
                  <span class="detail-value">{{ t('timelineJobs.detailsPage.details.correctionsValue', { applied: formatCount(summary.correctionsApplied), notApplied: formatCount(summary.correctionsNotApplied) }) }}</span>
                </div>
                <div class="detail-row" v-if="jobProgress.resources">
                  <span class="detail-label">{{ t('timelineJobs.detailsPage.details.memoryLimit') }}</span>
                  <span class="detail-value">{{ memoryLimitText }}</span>
                </div>
                <div class="detail-row" v-if="jobProgress.resources">
                  <span class="detail-label">{{ t('timelineJobs.detailsPage.details.cpus') }}</span>
                  <span class="detail-value">{{ jobProgress.resources.cpuCount }}</span>
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
                  v-for="step in displaySteps"
                  :key="step.key"
                  :class="['step-item', step.stateClass, { 'step-slowest': step.slowest }]"
                >
                  <div class="step-indicator">
                    <i :class="step.icon"></i>
                  </div>
                  <div class="step-content">
                    <div class="step-heading">
                      <h3 class="step-title">{{ step.title }}</h3>
                      <span v-if="step.durationMs != null" class="step-timing">
                        <span class="step-duration">{{ formatDuration(step.durationMs) }}</span>
                        <span v-if="step.share != null" class="step-share-label">{{ formatShare(step.share) }}</span>
                      </span>
                    </div>
                    <div v-if="step.share != null" class="step-share" :title="formatShare(step.share)">
                      <div class="step-share-bar" :style="{ width: `${Math.max(step.share, 0.5)}%` }"></div>
                    </div>
                    <div v-if="step.slowest || step.memoryPeak || step.failed || step.pending" class="step-badges">
                      <Tag v-if="step.failed" :value="t('timelineJobs.detailsPage.stepBadges.failed')" severity="danger" />
                      <Tag v-if="step.slowest" :value="t('timelineJobs.detailsPage.stepBadges.slowest')" severity="warn" />
                      <Tag v-if="step.memoryPeak" :value="t('timelineJobs.detailsPage.stepBadges.memoryPeak')" severity="info" />
                      <Tag v-if="step.pending" :value="t('timelineJobs.detailsPage.stepBadges.pending')" severity="secondary" />
                    </div>
                    <p v-if="memoryText(step.memory)" class="step-memory">
                      <i class="pi pi-server"></i>
                      {{ memoryText(step.memory) }}
                    </p>
                    <div v-if="hasShownCounters(step.counters)" class="step-details">
                      <div v-if="step.counters.gpsPointsLoaded" class="detail-item">
                        <i class="pi pi-map-marker"></i>
                        {{ t('timelineJobs.detailsPage.detail.gpsPoints', { loaded: formatCount(step.counters.gpsPointsLoaded), total: formatCount(step.counters.totalGpsPoints) }) }}
                      </div>

                      <div v-if="step.counters.processedPoints !== undefined" class="detail-section">
                        <div class="detail-item detail-header">
                          <i class="pi pi-cog"></i>
                          <strong>{{ t('timelineJobs.detailsPage.detail.processingHeader', { processed: formatCount(step.counters.processedPoints), total: formatCount(step.counters.totalPoints) }) }}</strong>
                        </div>
                        <div v-if="step.counters.pointsRemaining > 0" class="detail-item detail-sub">
                          <i class="pi pi-clock"></i>
                          {{ t('timelineJobs.detailsPage.detail.remaining', { count: formatCount(step.counters.pointsRemaining) }) }}
                        </div>
                      </div>

                      <div v-if="step.counters.totalLocations" class="detail-section">
                        <div class="detail-item detail-header">
                          <i class="pi pi-globe"></i>
                          <strong>{{ t('timelineJobs.detailsPage.detail.geocodingHeader', { resolved: formatCount(step.counters.totalResolved || 0), total: formatCount(step.counters.totalLocations) }) }}</strong>
                        </div>
                        <div v-if="step.counters.favoritesResolved" class="detail-item detail-sub">
                          <i class="pi pi-star"></i>
                          {{ t('timelineJobs.detailsPage.detail.favoritesInstant', { count: formatCount(step.counters.favoritesResolved) }) }}
                        </div>
                        <div v-if="step.counters.cachedResolved" class="detail-item detail-sub">
                          <i class="pi pi-database"></i>
                          {{ t('timelineJobs.detailsPage.detail.cachedInDatabase', { count: formatCount(step.counters.cachedResolved) }) }}
                        </div>
                        <div v-if="step.counters.externalCompleted" class="detail-item detail-sub">
                          <i class="pi pi-cloud"></i>
                          {{ t('timelineJobs.detailsPage.detail.externalApiCalls', { count: formatCount(step.counters.externalCompleted) }) }}
                        </div>
                        <div v-if="step.counters.externalPending > 0 && step.stateClass === 'step-active'" class="detail-item detail-sub detail-pending">
                          <i class="pi pi-clock"></i>
                          {{ t('timelineJobs.detailsPage.detail.pending', { count: formatCount(step.counters.externalPending) }) }}
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
                  <i class="pi pi-check-circle" style="font-size: 3rem; color: var(--p-green-500)"></i>
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

      <Dialog
        v-model:visible="diagnosticsVisible"
        modal
        :header="t('timelineJobs.detailsPage.diagnostics.title')"
        :style="{ width: 'min(900px, 95vw)' }"
      >
        <p class="diagnostics-description">{{ t('timelineJobs.detailsPage.diagnostics.description') }}</p>
        <Message v-if="diagnosticsError" severity="error" :closable="false">{{ diagnosticsError }}</Message>
        <pre v-else class="diagnostics-json" data-testid="job-diagnostics-json">{{ diagnosticsText }}</pre>
        <template #footer>
          <Button
            :label="t('timelineJobs.detailsPage.diagnostics.copy')"
            icon="pi pi-copy"
            :disabled="!diagnosticsText"
            @click="copyDiagnostics"
          />
        </template>
      </Dialog>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import { useTimelineJobProgress } from '@/composables/useTimelineJobProgress'
import { useTimelineStore } from '@/stores/timeline'
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
import Dialog from 'primevue/dialog'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const timezone = useTimezone()

const jobId = computed(() => route.params.jobId)

const { jobProgress, error, startPolling, fetchProgress } = useTimelineJobProgress()

const toast = useToast()
const timelineStore = useTimelineStore()

const summary = computed(() => jobProgress.value?.summary || {})

const peakBytes = (memory) => Math.max(memory?.containerBytes || 0, memory?.processBytes || 0, memory?.heapUsedBytes || 0)

// The steps the job went through (from the job itself); while it runs, then the planned steps it has not reached
const displaySteps = computed(() => {
  const job = jobProgress.value
  if (!job) return []
  const active = job.status === 'QUEUED' || job.status === 'RUNNING'
  const steps = job.steps || []
  const total = Math.max(job.durationMs || 0, 1)
  const ended = steps.filter((step) => step.endedAt)
  const slowest = ended.length > 1 ? ended.reduce((a, b) => (b.durationMs > a.durationMs ? b : a)) : null
  const measured = steps.filter((step) => peakBytes(step.memoryMax) > 0)
  const peak = measured.length > 1 ? measured.reduce((a, b) => (peakBytes(b.memoryMax) > peakBytes(a.memoryMax) ? b : a)) : null

  const shown = steps.map((step, index) => {
    const running = !step.endedAt && active
    const failed = !!step.failed
    return {
      key: `${index}-${step.phase}`,
      title: formatMessageDescriptor(step.title),
      durationMs: step.durationMs,
      share: Math.min(100, (step.durationMs / total) * 100),
      counters: step.counters || {},
      memory: step.memoryMax,
      slowest: step === slowest,
      memoryPeak: step === peak,
      failed,
      pending: false,
      stateClass: failed ? 'step-failed' : running ? 'step-active' : 'step-completed',
      icon: failed ? 'pi pi-times-circle' : running ? 'pi pi-spin pi-spinner' : 'pi pi-check-circle'
    }
  })

  if (active) {
    const reached = new Set(steps.map((step) => step.phase))
    const planned = job.plannedSteps || []
    const lastPlanned = steps.reduce((last, step) => Math.max(last, planned.indexOf(step.phase)), -1)
    planned.slice(lastPlanned + 1).filter((phase) => !reached.has(phase)).forEach((phase) => {
      shown.push({
        key: `planned-${phase}`,
        title: t(`timelineJobs.steps.${phase}`),
        durationMs: null,
        share: null,
        counters: {},
        memory: null,
        slowest: false,
        memoryPeak: false,
        failed: false,
        pending: true,
        stateClass: 'step-pending',
        icon: 'pi pi-circle'
      })
    })
  }
  return shown
})

const memoryLimitText = computed(() => {
  const resources = jobProgress.value?.resources
  if (!resources) return ''
  if (resources.containerLimitBytes > 0) return formatBytes(resources.containerLimitBytes)
  return t('timelineJobs.detailsPage.details.noMemoryLimit', { heap: formatBytes(resources.heapMaxBytes) })
})

// Only the counters the step template shows; others (such as the point total of "ready to process") stay in diagnostics
const hasShownCounters = (counters) => !!counters && (counters.gpsPointsLoaded || counters.processedPoints !== undefined
  || counters.totalLocations)

const formatShare = (share) => share < 1
  ? t('timelineJobs.detailsPage.stepShareTiny')
  : t('timelineJobs.detailsPage.stepShare', { percent: Math.round(share) })

// Only the values this platform reports: process and container memory exist on Linux (in Docker), not elsewhere
const memoryText = (memory) => {
  if (!memory) return ''
  const parts = [['heap', memory.heapUsedBytes], ['process', memory.processBytes], ['container', memory.containerBytes]]
    .filter(([, bytes]) => bytes != null && bytes >= 0)
    .map(([part, bytes]) => t(`timelineJobs.detailsPage.stepMemory.${part}`, { value: formatBytes(bytes) }))
  return parts.length ? `${t('timelineJobs.detailsPage.stepMemory.label')} ${parts.join(', ')}` : ''
}

const formatCount = (value) => (value == null ? '?' : Number(value).toLocaleString())

const formatBytes = (bytes) => {
  if (bytes == null || bytes < 0) return '—'
  const mib = bytes / (1024 * 1024)
  if (mib >= 1024) return `${(mib / 1024).toLocaleString(undefined, { maximumFractionDigits: 2 })} GiB`
  return `${Math.round(mib).toLocaleString()} MiB`
}

// Copy diagnostics
const diagnosticsVisible = ref(false)
const diagnosticsLoading = ref(false)
const diagnosticsText = ref('')
const diagnosticsError = ref('')

const openDiagnostics = async () => {
  diagnosticsLoading.value = true
  diagnosticsError.value = ''
  diagnosticsText.value = ''
  try {
    const diagnostics = await timelineStore.getJobDiagnostics(jobId.value)
    diagnosticsText.value = JSON.stringify(diagnostics, null, 2)
  } catch {
    diagnosticsError.value = t('timelineJobs.detailsPage.diagnostics.loadFailed')
  } finally {
    diagnosticsLoading.value = false
    diagnosticsVisible.value = true
  }
}

const copyDiagnostics = async () => {
  try {
    await navigator.clipboard.writeText(diagnosticsText.value)
    toast.add({ severity: 'success', summary: t('timelineJobs.detailsPage.diagnostics.copied'), life: 3000 })
  } catch {
    toast.add({ severity: 'warn', summary: t('timelineJobs.detailsPage.diagnostics.copyFailed'), life: 5000 })
  }
}

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
  if (!jobProgress.value) return 'var(--gp-primary)'

  switch (jobProgress.value.status) {
    case 'QUEUED': return 'var(--p-blue-500)'
    case 'RUNNING': return 'var(--gp-primary)'
    case 'COMPLETED': return 'var(--p-green-500)'
    case 'FAILED': return 'var(--p-red-500)'
    default: return 'var(--gp-text-primary)'
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

const formatTimestamp = (timestamp) => {
  if (!timestamp) return t('timelineJobs.listPage.notAvailable')
  return `${timezone.formatDateDisplay(timestamp)} ${timezone.formatTime(timestamp, { withSeconds: true })}`
}

const formatDuration = (durationMs) => {
  if (durationMs == null) return t('timelineJobs.detailsPage.durationZero')
  if (durationMs < 100) return t('timelineJobs.detailsPage.durationUnderSeconds', { seconds: formatDecimal(0.1) })
  if (durationMs < 10000) return t('timelineJobs.detailsPage.durationSeconds', { seconds: formatDecimal(durationMs / 1000) })

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

// One decimal, in the page's language (0.6 / 0,6)
const formatDecimal = (value) => value.toLocaleString(locale.value, { minimumFractionDigits: 1, maximumFractionDigits: 1 })

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
  color: var(--gp-text-secondary);
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
  background: var(--gp-surface-card);
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
  color: var(--gp-text-secondary);
  font-size: 0.875rem;
  font-family: var(--gp-font-mono);
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
  color: var(--gp-text-primary);
}

.progress-percentage {
  font-weight: 600;
  color: var(--gp-primary);
}

.progress-complete :deep(.p-progressbar-value) {
  background: var(--p-green-500);
}

.job-details {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
  padding: 1rem;
  background: var(--gp-surface-ground);
  border-radius: var(--gp-radius-medium);
}

.detail-row {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.detail-label {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  font-weight: 500;
}

.detail-value {
  font-size: 1rem;
  color: var(--gp-text-primary);
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
  border-left: 3px solid var(--gp-border);
  position: relative;
  transition: all 0.3s ease;
}

.step-item:not(:last-child) {
  border-bottom: 1px solid var(--gp-border);
}

.step-completed {
  opacity: 0.7;
  border-left-color: var(--p-green-500);
}

.step-active {
  background: var(--gp-primary-soft);
  border-left-color: var(--gp-primary);
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
  color: var(--p-green-500);
}

.step-active .step-indicator {
  color: var(--gp-primary);
}

.step-pending .step-indicator {
  color: var(--gp-text-secondary);
}

.step-content {
  flex: 1;
}

.step-title {
  margin: 0 0 0.25rem 0;
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.step-description {
  margin: 0 0 0.75rem 0;
  color: var(--gp-text-secondary);
  font-size: 0.875rem;
}

.step-details {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-top: 0.75rem;
  padding: 0.75rem;
  background: var(--gp-surface-ground);
  border-radius: var(--gp-radius-medium);
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
  color: var(--gp-text-primary);
}

.detail-item i {
  color: var(--gp-primary);
  flex-shrink: 0;
}

.detail-header {
  font-size: 0.95rem;
  padding-bottom: 0.25rem;
  border-bottom: 1px solid var(--gp-border);
  margin-bottom: 0.25rem;
}

.detail-sub {
  padding-left: 1.5rem;
  font-size: 0.85rem;
}

.detail-pending {
  color: var(--p-orange-600);
  font-weight: 500;
}

.detail-pending i {
  color: var(--p-orange-500);
}

.step-heading {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 1rem;
}

.step-timing {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  white-space: nowrap;
}

.step-share-label {
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
}

.step-duration {
  font-weight: 600;
  color: var(--gp-text-primary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.step-share {
  height: 4px;
  background: var(--gp-surface-ground);
  border-radius: 2px;
  margin: 0.25rem 0 0.5rem;
  overflow: hidden;
}

.step-share-bar {
  height: 100%;
  background: var(--gp-primary);
}

.step-slowest .step-share-bar {
  background: var(--p-orange-500);
}

.step-failed {
  border-left-color: var(--p-red-500);
}

.step-failed .step-indicator {
  color: var(--p-red-500);
}

.step-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
  margin-bottom: 0.5rem;
}

.step-memory {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0 0 0.5rem 0;
  font-size: 0.85rem;
  color: var(--gp-text-secondary);
}

.diagnostics-description {
  margin: 0 0 0.75rem 0;
  color: var(--gp-text-secondary);
}

.diagnostics-json {
  max-height: 60vh;
  overflow: auto;
  margin: 0;
  padding: 0.75rem;
  background: var(--gp-surface-ground);
  border-radius: var(--gp-radius-medium);
  font-family: var(--gp-font-mono);
  font-size: 0.8rem;
  white-space: pre-wrap;
  word-break: break-word;
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
  color: var(--gp-text-primary);
}

.completion-message p {
  margin: 0;
  color: var(--gp-text-secondary);
  max-width: 500px;
}

.action-buttons {
  display: flex;
  gap: 1rem;
  margin-top: 1rem;
}

@media (max-width: 768px) {
  .job-details {
    grid-template-columns: 1fr;
  }

  .step-details {
    flex-direction: column;
  }
}
</style>
