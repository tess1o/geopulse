/**
 * Vue composable for consistent error handling
 */
import { useToast } from 'primevue/usetoast'
import { showErrorToast, createRetryableErrorHandler } from '@/utils/errorHandler'

export function useErrorHandler() {
  const toast = useToast()

  /**
   * Handle an error and show appropriate toast notification
   * @param {Error} error - The error to handle
   * @param {Object} options - Additional options for toast
   * @returns {Object} - Formatted error object
   */
  const handleError = (error, options = {}) => {
    return showErrorToast(toast.add, error, options)
  }

  /**
   * Handle an error with retry capability
   * @param {Error} error - The error to handle
   * @param {Function} retryFunction - Function to call on retry
   * @returns {Object} - Formatted error object
   */
  const handleErrorWithRetry = (error, retryFunction) => {
    const retryHandler = createRetryableErrorHandler(toast.add, retryFunction)
    return retryHandler(error)
  }

  /**
   * Show a simple error message
   * @param {string} title - Error title
   * @param {string} message - Error message
   * @param {Object} options - Additional toast options
   */
  const showError = (title, message, options = {}) => {
    toast.add({
      severity: 'error',
      summary: title,
      detail: message,
      life: 4000,
      ...options
    })
  }

  /**
   * Show a connection error with retry option
   * @param {Function} retryFunction - Function to call on retry
   */
  const showConnectionError = (retryFunction) => {
    toast.add({
      severity: 'error',
      summary: 'Connection Problem',
      detail: 'Unable to connect to GeoPulse servers. Please check your internet connection.',
      life: 6000
    })

    if (retryFunction) {
      setTimeout(() => {
        toast.add({
          severity: 'info',
          summary: 'Retry Available',
          detail: 'Click here to try again',
          life: 5000,
          onClick: retryFunction
        })
      }, 1000)
    }
  }

  return {
    handleError,
    handleErrorWithRetry,
    showError,
    showConnectionError
  }
}