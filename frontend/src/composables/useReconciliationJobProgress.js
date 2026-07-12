import { ref, onUnmounted } from 'vue'
import { useGeocodingStore } from '@/stores/geocoding'

/**
 * Composable for polling reconciliation job progress.
 * Based on the useTimelineJobProgress pattern.
 * Polls backend every 1 second for real-time updates.
 */
export function useReconciliationJobProgress() {
  const geocodingStore = useGeocodingStore()

  const jobProgress = ref(null)
  const isPolling = ref(false)
  const error = ref(null)
  let pollIntervalId = null
  const POLL_INTERVAL_MS = 1000 // 1 second (user preference)

  const isTerminal = (progress) => {
    return progress?.status === 'COMPLETED' || progress?.status === 'FAILED'
  }

  /**
   * Start polling job progress
   * @param {string} jobId - Job ID to poll
   */
  const startPolling = async (jobId) => {
    if (!jobId) {
      error.value = 'Job ID is required'
      return
    }

    stopPolling()
    isPolling.value = true
    error.value = null

    // Initial fetch
    await fetchProgress(jobId)
    if (isTerminal(jobProgress.value)) {
      stopPolling()
      return
    }

    // Start polling interval
    pollIntervalId = setInterval(async () => {
      await fetchProgress(jobId)

      // Stop polling if job is complete or failed
      if (isTerminal(jobProgress.value)) {
        stopPolling()
      }
    }, POLL_INTERVAL_MS)
  }

  /**
   * Fetch current job progress
   * @param {string} jobId - Job ID to fetch
   */
  const fetchProgress = async (jobId) => {
    try {
      const response = await geocodingStore.getReconciliationJobProgress(jobId)
      jobProgress.value = response
      error.value = null
    } catch (err) {
      console.error('Failed to fetch reconciliation job progress:', err)
      error.value = err.message || 'Failed to fetch job progress'
      jobProgress.value = {
        ...(jobProgress.value || {}),
        status: 'FAILED',
        errorMessage: error.value,
        progressPercentage: jobProgress.value?.progressPercentage ?? 0,
        totalItems: jobProgress.value?.totalItems ?? 0,
        processedItems: jobProgress.value?.processedItems ?? 0,
        successCount: jobProgress.value?.successCount ?? 0,
        failedCount: jobProgress.value?.failedCount ?? 0
      }
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
