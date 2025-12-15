import { ref, onUnmounted } from 'vue'
import { useFavoritesStore } from '@/stores/favorites'

/**
 * Composable for polling favorite reconciliation job progress.
 * Based on the useReconciliationJobProgress pattern.
 * Polls backend every 1 second for real-time updates.
 */
export function useFavoriteReconciliationProgress() {
  const favoritesStore = useFavoritesStore()

  const jobProgress = ref(null)
  const isPolling = ref(false)
  const error = ref(null)
  let pollIntervalId = null
  const POLL_INTERVAL_MS = 1000 // 1 second

  /**
   * Start polling job progress
   * @param {string} jobId - Job ID to poll
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
   * @param {string} jobId - Job ID to fetch
   */
  const fetchProgress = async (jobId) => {
    try {
      const response = await favoritesStore.getReconciliationJobProgress(jobId)
      jobProgress.value = response
      error.value = null
    } catch (err) {
      console.error('Failed to fetch favorite reconciliation job progress:', err)
      error.value = err.message || 'Failed to fetch job progress'
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
