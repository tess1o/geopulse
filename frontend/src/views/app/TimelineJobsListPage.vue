<template>
  <AppLayout>
    <PageContainer>
      <div class="timeline-jobs-list-page">
        <!-- Page Header -->
        <div class="gp-page-header">
          <div class="gp-page-header-content">
            <div class="gp-page-header-text">
              <h1 class="gp-page-title">{{ t('timelineJobs.listPage.title') }}</h1>
              <p class="gp-page-subtitle">
                {{ t('timelineJobs.listPage.description') }}
              </p>
            </div>
          </div>
        </div>

        <!-- Loading State -->
        <Card v-if="loading" class="loading-card">
          <template #content>
            <div class="loading-content">
              <ProgressSpinner style="width: 50px; height: 50px" strokeWidth="4" />
              <p class="loading-text">{{ t('timelineJobs.listPage.loading') }}</p>
            </div>
          </template>
        </Card>

        <!-- No Active Job -->
        <Card v-else-if="!activeJob && !error" class="no-job-card">
          <template #content>
            <div class="no-job-content">
              <i class="pi pi-info-circle no-job-icon"></i>
              <h2>{{ t('timelineJobs.listPage.noJob.title') }}</h2>
              <p class="no-job-message">
                {{ t('timelineJobs.listPage.noJob.message') }}
              </p>
              <p class="info-hint">
                {{ t('timelineJobs.listPage.noJob.hint') }}
              </p>
              <div class="action-buttons">
                <Button
                  :label="t('nav.items.preferences')"
                  icon="pi pi-cog"
                  @click="goToPreferences"
                />
                <Button
                  :label="t('timelineJobs.listPage.noJob.viewTimelineButton')"
                  icon="pi pi-calendar"
                  severity="secondary"
                  outlined
                  @click="goToTimeline"
                />
              </div>
            </div>
          </template>
        </Card>

        <!-- Error State -->
        <Message v-if="error" severity="error" class="error-message">
          <div class="error-content">
            <strong>{{ t('timelineJobs.listPage.error.title') }}</strong>
            <p>{{ error }}</p>
            <Button
              :label="t('common.tryAgain')"
              size="small"
              @click="checkForActiveJob"
              class="mt-2"
            />
          </div>
        </Message>

        <!-- Historical Jobs Section -->
        <div v-if="!loading" class="history-section">
          <div class="section-header">
            <h2 class="section-title">{{ t('timelineJobs.listPage.history.title') }}</h2>
            <p class="section-description">
              {{ t('timelineJobs.listPage.history.description') }}
            </p>
          </div>

          <!-- Loading Historical Jobs -->
          <Card v-if="historyLoading" class="history-loading-card">
            <template #content>
              <div class="loading-content">
                <ProgressSpinner style="width: 40px; height: 40px" strokeWidth="4" />
                <p class="loading-text">{{ t('timelineJobs.listPage.history.loading') }}</p>
              </div>
            </template>
          </Card>

          <!-- No Historical Jobs -->
          <Card v-else-if="!historyJobs || historyJobs.length === 0" class="no-history-card">
            <template #content>
              <div class="no-history-content">
                <i class="pi pi-inbox"></i>
                <p>{{ t('timelineJobs.listPage.history.empty') }}</p>
              </div>
            </template>
          </Card>

          <!-- Historical Jobs List -->
          <div v-else class="history-jobs-list">
            <Card
              v-for="job in historyJobs"
              :key="job.jobId"
              class="history-job-card"
              @click="viewJobDetails(job.jobId)"
            >
              <template #content>
                <div class="job-summary">
                  <div class="job-header">
                    <div class="job-status">
                      <i
                        :class="job.status === 'COMPLETED' ? 'pi pi-check-circle' : 'pi pi-times-circle'"
                        :style="{ color: job.status === 'COMPLETED' ? 'var(--p-green-500)' : 'var(--p-red-500)' }"
                      ></i>
                      <span class="status-text">{{ job.status }}</span>
                    </div>
                    <span class="job-date">{{ formatDate(job.startTime) }}</span>
                  </div>
                  <div class="job-details">
                    <span class="job-duration">{{ t('timelineJobs.listPage.history.duration', { duration: formatDuration(job.durationMs) }) }}</span>
                    <span v-if="job.details && job.details.totalGpsPoints" class="job-stat">
                      {{ t('timelineJobs.listPage.history.gpsPoints', { count: job.details.totalGpsPoints.toLocaleString() }) }}
                    </span>
                  </div>
                  <div class="job-action">
                    <i class="pi pi-arrow-right"></i>
                    <span>{{ t('timelineJobs.listPage.history.viewDetails') }}</span>
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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useTimelineStore } from '@/stores/timeline'
import { useTimezone } from '@/composables/useTimezone'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import Card from 'primevue/card'
import Button from 'primevue/button'
import ProgressSpinner from 'primevue/progressspinner'
import Message from 'primevue/message'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

const { t } = useI18n()
const router = useRouter()
const timelineStore = useTimelineStore()
const timezone = useTimezone()

const loading = ref(true)
const activeJob = ref(null)
const error = ref(null)
const historyLoading = ref(false)
const historyJobs = ref([])

const checkForActiveJob = async () => {
  loading.value = true
  error.value = null

  try {
    const job = await timelineStore.getUserActiveJob()

    if (job) {
      activeJob.value = job
      // Redirect to the job details page
      router.replace(`/app/timeline/jobs/${job.jobId}`)
    } else {
      activeJob.value = null
      loading.value = false

      // Load historical jobs when there's no active job
      loadHistoricalJobs()
    }
  } catch (err) {
    console.error('Failed to check for active job:', err)
    error.value = formatApiErrorDetail(err, t('timelineJobs.listPage.error.title'))
    loading.value = false
  }
}

const loadHistoricalJobs = async () => {
  historyLoading.value = true

  try {
    const jobs = await timelineStore.getUserHistoryJobs()
    historyJobs.value = jobs || []
  } catch (err) {
    console.error('Failed to load historical jobs:', err)
    // Don't show error for historical jobs, just leave empty
    historyJobs.value = []
  } finally {
    historyLoading.value = false
  }
}

const viewJobDetails = (jobId) => {
  router.push(`/app/timeline/jobs/${jobId}`)
}

const formatDate = (timestamp) => {
  if (!timestamp) return t('timelineJobs.listPage.notAvailable')
  return `${timezone.formatDateDisplay(timestamp)} ${timezone.formatTime(timestamp)}`
}

const formatDuration = (durationMs) => {
  if (!durationMs) return t('timelineJobs.listPage.notAvailable')
  const seconds = Math.floor(durationMs / 1000)
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = seconds % 60

  if (minutes > 0) {
    return t('timelineJobs.listPage.durationMinutesSeconds', { minutes, seconds: remainingSeconds })
  }
  return t('timelineJobs.listPage.durationSeconds', { seconds })
}

const goToPreferences = () => {
  router.push('/app/timeline/preferences')
}

const goToTimeline = () => {
  router.push('/app/timeline')
}

onMounted(() => {
  checkForActiveJob()
})
</script>

<style scoped>
.timeline-jobs-list-page {
  max-width: 800px;
  margin: 0 auto;
}

.loading-card,
.no-job-card,
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

.no-job-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.5rem;
  padding: 3rem 2rem;
  text-align: center;
}

.no-job-icon {
  font-size: 4rem;
  color: var(--gp-primary);
  opacity: 0.6;
}

.no-job-content h2 {
  margin: 0;
  font-size: 1.5rem;
  color: var(--gp-text-primary);
}

.no-job-content p {
  margin: 0;
  color: var(--gp-text-secondary);
  max-width: 500px;
}

.no-job-message {
  font-size: 1rem;
  margin-bottom: 0.5rem;
}

.info-hint {
  font-size: 0.9rem;
  font-style: italic;
  opacity: 0.8;
  line-height: 1.5;
}

.action-buttons {
  display: flex;
  gap: 1rem;
  margin-top: 1rem;
  flex-wrap: wrap;
  justify-content: center;
}

.error-content {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

/* Historical Jobs Section */
.history-section {
  margin-top: 3rem;
}

.section-header {
  margin-bottom: 1.5rem;
}

.section-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.5rem 0;
}

.section-description {
  font-size: 0.95rem;
  color: var(--gp-text-secondary);
  margin: 0;
}

.history-loading-card,
.no-history-card {
  margin-bottom: 1rem;
}

.no-history-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
  padding: 2rem;
  color: var(--gp-text-secondary);
}

.no-history-content i {
  font-size: 2.5rem;
  opacity: 0.5;
}

.history-jobs-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.history-job-card {
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid var(--gp-border);
  background: var(--gp-surface-card);
}

.history-job-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  border-color: var(--gp-primary);
  background: var(--gp-surface-hover);
}

.history-job-card:active {
  transform: translateY(0);
}

.job-summary {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  position: relative;
}

.job-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.job-status {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
}

.job-status i {
  font-size: 1.25rem;
}

.status-text {
  font-size: 0.95rem;
}

.job-date {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
}

.job-details {
  display: flex;
  gap: 1.5rem;
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
}

.job-duration,
.job-stat {
  display: flex;
  align-items: center;
}

.job-action {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--gp-primary);
  font-size: 0.9rem;
  font-weight: 500;
  margin-top: 0.5rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--gp-border);
}

.job-action i {
  transition: transform 0.2s ease;
}

.history-job-card:hover .job-action i {
  transform: translateX(4px);
}

@media (max-width: 768px) {
  .action-buttons {
    flex-direction: column;
    width: 100%;
  }

  .action-buttons button {
    width: 100%;
  }

  .job-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }

  .job-details {
    flex-direction: column;
    gap: 0.5rem;
  }
}
</style>
