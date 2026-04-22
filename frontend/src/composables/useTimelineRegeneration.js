import { ref, watch } from 'vue'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { useRouter } from 'vue-router'
import { useTimelineJobProgress } from '@/composables/useTimelineJobProgress'
import { useTimelineJobCheck } from '@/composables/useTimelineJobCheck'

export function useTimelineRegeneration() {
  const confirm = useConfirm()
  const toast = useToast()
  const router = useRouter()

  // Modal state
  const timelineRegenerationVisible = ref(false)
  const timelineRegenerationType = ref('general')
  const modalShowStartTime = ref(null)
  const currentJobId = ref(null)
  const trackingCallbacks = ref(null)
  const trackingLifecycle = ref({
    completedHandled: false,
    failedHandled: false,
    trackingErrorHandled: false
  })

  // Composables
  const {
    jobProgress,
    error: jobError,
    startPolling,
    stopPolling,
    fetchProgress,
    reset: resetJobProgress
  } = useTimelineJobProgress()
  const { checkActiveJob } = useTimelineJobCheck()

  // Watch for currentJobId changes to start polling
  watch(currentJobId, (newJobId) => {
    if (newJobId) {
      startPolling(newJobId)
    } else {
      stopPolling()
    }
  })

  const resetTrackingLifecycle = () => {
    trackingLifecycle.value = {
      completedHandled: false,
      failedHandled: false,
      trackingErrorHandled: false
    }
  }

  const clearTrackingCallbacks = () => {
    trackingCallbacks.value = null
    resetTrackingLifecycle()
  }

  const closeTrackingModal = ({ clearJob = true } = {}) => {
    timelineRegenerationVisible.value = false
    modalShowStartTime.value = null
    if (clearJob) {
      currentJobId.value = null
    }
  }

  const callTrackingCallback = async (callback, ...args) => {
    if (typeof callback !== 'function') return
    try {
      await callback(...args)
    } catch (error) {
      console.error('Timeline regeneration tracking callback failed:', error)
    }
  }

  // Watch for job completion to auto-close modal
  watch(() => jobProgress.value?.status, async (status) => {
    if (!status) return

    const callbacks = trackingCallbacks.value

    if (status === 'COMPLETED') {
      if (callbacks && !trackingLifecycle.value.completedHandled) {
        trackingLifecycle.value.completedHandled = true
        await callTrackingCallback(callbacks.onCompleted, jobProgress.value, currentJobId.value)
      }

      const shouldAutoClose = timelineRegenerationVisible.value
        && (callbacks?.autoCloseOnCompleted ?? true)
      if (shouldAutoClose) {
        const elapsed = Date.now() - (modalShowStartTime.value || 0)
        const minimumDisplayTime = callbacks?.minimumDisplayTimeMs ?? 3000
        const remainingTime = Math.max(0, minimumDisplayTime - elapsed)

        setTimeout(() => {
          closeTrackingModal({ clearJob: true })
          clearTrackingCallbacks()
        }, remainingTime)
      }
      return
    }

    if (status === 'FAILED') {
      if (callbacks && !trackingLifecycle.value.failedHandled) {
        trackingLifecycle.value.failedHandled = true
        await callTrackingCallback(callbacks.onFailed, jobProgress.value, currentJobId.value)
      }

      const shouldAutoClose = timelineRegenerationVisible.value
        && (callbacks?.autoCloseOnFailed ?? true)
      if (shouldAutoClose) {
        closeTrackingModal({ clearJob: true })
        clearTrackingCallbacks()
      }
    }
  })

  watch(jobError, async (error) => {
    if (!error || !trackingCallbacks.value) {
      return
    }

    if (!trackingLifecycle.value.trackingErrorHandled) {
      trackingLifecycle.value.trackingErrorHandled = true
      await callTrackingCallback(trackingCallbacks.value.onTrackingError, error, currentJobId.value)
    }

    const shouldAutoClose = timelineRegenerationVisible.value
      && (trackingCallbacks.value.autoCloseOnTrackingError ?? trackingCallbacks.value.autoCloseOnFailed ?? true)
    if (shouldAutoClose) {
      closeTrackingModal({ clearJob: true })
      clearTrackingCallbacks()
    }
  })

  const showTimelineRegenerationModal = (type) => {
    timelineRegenerationType.value = type
    modalShowStartTime.value = Date.now()
    timelineRegenerationVisible.value = true
  }

  /**
   * Wraps an action that triggers timeline regeneration.
   * Handles checking for active jobs, showing modals, and tracking progress.
   * @param {Function} action - The async function to execute (e.g., adding/deleting a favorite). Must return a jobId.
   * @param {object} options - Configuration options.
   * @param {string} options.modalType - The type of regeneration for the modal ('favorite', 'favorite-delete', etc.).
   * @param {string} options.successMessage - Toast message on success.
   * @param {string} options.errorMessage - Toast message on failure.
   * @param {Function} [options.onSuccess] - Optional callback on success.
   */
  const withTimelineRegeneration = async (action, options) => {
    // 1. Check for existing active jobs
    const activeJobCheck = await checkActiveJob()
    if (activeJobCheck.hasActiveJob) {
      confirm.require({
        message: `A timeline generation job is already running (${activeJobCheck.progress || 0}% complete). Please wait for it to finish.`,
        header: 'Timeline Job In Progress',
        icon: 'pi pi-info-circle',
        rejectLabel: 'Cancel',
        acceptLabel: 'View Progress',
        accept: () => {
          router.push({ name: 'Timeline Job Details', params: { jobId: activeJobCheck.jobId } })
        }
      })
      return
    }

    clearTrackingCallbacks()

    // 2. Show the regeneration modal
    showTimelineRegenerationModal(options.modalType)

    try {
      // 3. Execute the action
      const jobId = await action()

      // 4. Start tracking the new job
      if (jobId) {
        currentJobId.value = jobId
        toast.add({ severity: 'success', summary: 'Success', detail: options.successMessage, life: 3000 })
        if (options.onSuccess) {
          options.onSuccess()
        }
      } else {
        // If no jobId is returned, something went wrong but it wasn't an exception
        throw new Error('Action did not return a valid job ID.')
      }
    } catch (error) {
      console.error(`Timeline regeneration action failed: ${options.errorMessage}`, error)
      toast.add({ severity: 'error', summary: 'Error', detail: error.message || options.errorMessage, life: 5000 })
      
      // Close modal immediately on error
      closeTrackingModal({ clearJob: true })
      clearTrackingCallbacks()
    }
  }

  const trackExistingTimelineJob = async (jobId, options = {}) => {
    if (!jobId) {
      return
    }

    const {
      modalType = 'general',
      showModal = true,
      onCompleted,
      onFailed,
      onTrackingError,
      autoCloseOnCompleted = showModal,
      autoCloseOnFailed = showModal,
      autoCloseOnTrackingError = showModal,
      minimumDisplayTimeMs = 3000
    } = options

    trackingCallbacks.value = {
      onCompleted,
      onFailed,
      onTrackingError,
      autoCloseOnCompleted,
      autoCloseOnFailed,
      autoCloseOnTrackingError,
      minimumDisplayTimeMs
    }
    resetTrackingLifecycle()
    resetJobProgress()

    timelineRegenerationType.value = modalType
    if (showModal) {
      showTimelineRegenerationModal(modalType)
    }

    currentJobId.value = String(jobId)
  }

  const refreshCurrentJobProgress = async () => {
    if (!currentJobId.value) {
      return
    }
    await fetchProgress(currentJobId.value)
  }

  const clearTrackedTimelineJob = () => {
    closeTrackingModal({ clearJob: true })
    clearTrackingCallbacks()
    resetJobProgress()
  }

  return {
    timelineRegenerationVisible,
    timelineRegenerationType,
    currentJobId,
    jobProgress,
    jobError,
    withTimelineRegeneration,
    trackExistingTimelineJob,
    refreshCurrentJobProgress,
    clearTrackedTimelineJob
  }
}
