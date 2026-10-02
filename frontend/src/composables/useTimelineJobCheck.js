import { ref } from 'vue'
import { useTimelineStore } from '@/stores/timeline'

/**
 * Composable for checking if a timeline job is currently running
 * and preventing operations that would conflict with it.
 */
export function useTimelineJobCheck() {
  const timelineStore = useTimelineStore()
  const isCheckingJob = ref(false)
  const activeJobId = ref(null)

  /**
   * Check if a timeline job is currently active for the user
   * @returns {Promise<{hasActiveJob: boolean, jobId: string|null}>}
   */
  const checkActiveJob = async () => {
    isCheckingJob.value = true
    try {
      const activeJob = await timelineStore.getUserActiveJob()

      if (activeJob && activeJob.jobId) {
        activeJobId.value = activeJob.jobId
        return {
          hasActiveJob: true,
          jobId: activeJob.jobId,
          status: activeJob.status,
          progress: activeJob.progressPercentage,
          currentStep: activeJob.currentStep
        }
      }

      activeJobId.value = null
      return {
        hasActiveJob: false,
        jobId: null
      }
    } catch (error) {
      console.error('Error checking active job:', error)
      // On error, assume no active job to not block operations
      return {
        hasActiveJob: false,
        jobId: null
      }
    } finally {
      isCheckingJob.value = false
    }
  }

  return {
    isCheckingJob,
    activeJobId,
    checkActiveJob
  }
}
