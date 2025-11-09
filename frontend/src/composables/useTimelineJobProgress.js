import { ref, onUnmounted } from 'vue'
import { useTimelineStore } from '@/stores/timeline'

/**
 * Composable for polling timeline job progress
 * Provides reactive job progress data with automatic polling
 */
export function useTimelineJobProgress() {
  const timelineStore = useTimelineStore()

  const jobProgress = ref(null)
  const isPolling = ref(false)
  const error = ref(null)
  let pollIntervalId = null
  const POLL_INTERVAL_MS = 2000 // 2 seconds (aligned with import polling)

  /**
   * Start polling job progress
   * @param {string} jobId - The job ID to track
   */
  const startPolling = async (jobId) => {
    if (!jobId) {
      error.value = 'Job ID is required'
      return
    }

    isPolling.value = true
    error.value = null

    // Initial fetch
    await fetchProgress(jobId)

    // Start polling interval
    pollIntervalId = setInterval(async () => {
      await fetchProgress(jobId)

      // Stop polling if job is complete or failed
      if (jobProgress.value &&
          (jobProgress.value.status === 'COMPLETED' || jobProgress.value.status === 'FAILED')) {
        stopPolling()
      }
    }, POLL_INTERVAL_MS)
  }

  /**
   * Fetch current job progress
   * @param {string} jobId - The job ID
   */
  const fetchProgress = async (jobId) => {
    try {
      const response = await timelineStore.getJobProgress(jobId)
      jobProgress.value = response
      error.value = null
    } catch (err) {
      console.error('Failed to fetch job progress:', err)
      error.value = err.message || 'Failed to fetch job progress'

      // Stop polling on error
      stopPolling()
    }
  }

  /**
   * Stop polling job progress
   */
  const stopPolling = () => {
    if (pollIntervalId) {
      clearInterval(pollIntervalId)
      pollIntervalId = null
    }
    isPolling.value = false
  }

  /**
   * Reset the composable state
   */
  const reset = () => {
    stopPolling()
    jobProgress.value = null
    error.value = null
  }

  // Cleanup on unmount
  onUnmounted(() => {
    stopPolling()
  })

  return {
    jobProgress,
    isPolling,
    error,
    startPolling,
    stopPolling,
    fetchProgress,
    reset
  }
}
