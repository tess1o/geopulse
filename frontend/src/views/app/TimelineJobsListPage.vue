<template>
  <AppLayout>
    <PageContainer>
      <div class="timeline-jobs-list-page">
        <!-- Page Header -->
        <div class="page-header">
          <div class="header-content">
            <div class="header-text">
              <h1 class="page-title">Timeline Generation Jobs</h1>
              <p class="page-description">
                View active and recent timeline generation jobs
              </p>
            </div>
          </div>
        </div>

        <!-- Loading State -->
        <Card v-if="loading" class="loading-card">
          <template #content>
            <div class="loading-content">
              <ProgressSpinner style="width: 50px; height: 50px" strokeWidth="4" />
              <p class="loading-text">Looking for active jobs...</p>
            </div>
          </template>
        </Card>

        <!-- No Active Job -->
        <Card v-else-if="!activeJob && !error" class="no-job-card">
          <template #content>
            <div class="no-job-content">
              <i class="pi pi-info-circle no-job-icon"></i>
              <h2>No Active Timeline Generation Job</h2>
              <p>There are currently no timeline generation jobs running for your account.</p>
              <div class="action-buttons">
                <Button
                  label="Go to Timeline Preferences"
                  icon="pi pi-cog"
                  @click="goToPreferences"
                />
                <Button
                  label="View Timeline"
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
            <strong>Failed to check for active jobs</strong>
            <p>{{ error }}</p>
            <Button
              label="Try Again"
              size="small"
              @click="checkForActiveJob"
              class="mt-2"
            />
          </div>
        </Message>
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useTimelineStore } from '@/stores/timeline'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import Card from 'primevue/card'
import Button from 'primevue/button'
import ProgressSpinner from 'primevue/progressspinner'
import Message from 'primevue/message'

const router = useRouter()
const timelineStore = useTimelineStore()

const loading = ref(true)
const activeJob = ref(null)
const error = ref(null)

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
    }
  } catch (err) {
    console.error('Failed to check for active job:', err)
    error.value = err.message || 'Failed to check for active jobs'
    loading.value = false
  }
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
  color: var(--text-color-secondary);
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
  color: var(--primary-color);
  opacity: 0.6;
}

.no-job-content h2 {
  margin: 0;
  font-size: 1.5rem;
  color: var(--text-color);
}

.no-job-content p {
  margin: 0;
  color: var(--text-color-secondary);
  max-width: 500px;
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

@media (max-width: 768px) {
  .action-buttons {
    flex-direction: column;
    width: 100%;
  }

  .action-buttons button {
    width: 100%;
  }
}
</style>
