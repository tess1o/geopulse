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

  // Composables
  const { jobProgress, startPolling, stopPolling } = useTimelineJobProgress()
  const { checkActiveJob } = useTimelineJobCheck()

  // Watch for currentJobId changes to start polling
  watch(currentJobId, (newJobId) => {
    if (newJobId) {
      startPolling(newJobId)
    } else {
      stopPolling()
    }
  })

  // Watch for job completion to auto-close modal
  watch(() => jobProgress.value?.status, (status) => {
    if (status === 'COMPLETED' && timelineRegenerationVisible.value) {
      const elapsed = Date.now() - (modalShowStartTime.value || 0)
      const minimumDisplayTime = 3000 // 3 seconds
      const remainingTime = Math.max(0, minimumDisplayTime - elapsed)

      setTimeout(() => {
        timelineRegenerationVisible.value = false
        modalShowStartTime.value = null
        currentJobId.value = null
      }, remainingTime)
    } else if (status === 'FAILED' && timelineRegenerationVisible.value) {
      // Close immediately on failure
      timelineRegenerationVisible.value = false
      modalShowStartTime.value = null
      currentJobId.value = null
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
          const url = router.resolve({ name: 'Job Details', params: { id: activeJobCheck.jobId } }).href
          window.open(url, '_blank')
        }
      })
      return
    }

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
      timelineRegenerationVisible.value = false
      currentJobId.value = null
    }
  }

  return {
    timelineRegenerationVisible,
    timelineRegenerationType,
    currentJobId,
    jobProgress,
    withTimelineRegeneration
  }
}
