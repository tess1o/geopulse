/**
 * Error handling utilities for GeoPulse frontend
 */

import { formatApiErrorDetail, getErrorReferenceId, hasErrorReference, normalizeApiError, withErrorReference } from './apiErrorDetail'

function getErrorText(value) {
  if (!value) return ''
  if (typeof value === 'string') return value
  if (typeof value === 'object') {
    const parts = [value.message, value.error, value.details].filter(Boolean)
    if (parts.length > 0) return parts.join(' ')
    try {
      return JSON.stringify(value)
    } catch {
      return ''
    }
  }
  return String(value)
}

/**
 * Convert API/Network errors into user-friendly messages
 * @param {Error} error - The original error object
 * @returns {Object} - Formatted error object with user-friendly message
 */
export function formatError(error) {
  const problem = normalizeApiError(error)
  // Default error object
  const formattedError = {
    title: 'Something went wrong',
    message: 'An unexpected error occurred. Please try again.',
    severity: 'error',
    technical: error?.message || problem.detail || 'Unknown error',
    canRetry: true,
    isConnectionError: false,
    status: problem.status,
    code: problem.code,
    parameters: problem.parameters,
    violations: problem.violations
  }

  // Handle network/connection errors
  if (error?.code === 'NETWORK_ERROR' ||
      error?.message === 'Network Error' ||
      error?.message?.includes('ERR_NETWORK') ||
      error?.message?.includes('Failed to fetch') ||
      !navigator.onLine) {
    
    formattedError.title = 'Connection Problem'
    formattedError.message = 'Unable to connect to GeoPulse servers. Please check your internet connection and try again.'
    formattedError.isConnectionError = true
    formattedError.canRetry = true
    return formattedError
  }

  // Handle timeout errors
  if (error?.code === 'ECONNABORTED' ||
      error?.message?.includes('timeout')) {
    
    formattedError.title = 'Request Timeout'
    formattedError.message = 'The request is taking longer than expected. Please try again.'
    formattedError.canRetry = true
    return formattedError
  }

  // Handle HTTP response errors
  if (problem.status) {
    const status = problem.status
    const data = error?.response?.data || problem
    const problemDetail = formatApiErrorDetail(problem, null)

    switch (status) {
      case 400:
        {
          formattedError.title = 'Invalid Request'
          formattedError.message = problemDetail ||
            'The request could not be processed. Please check your input and try again.'
          formattedError.canRetry = true
          break
        }

      case 401:
        formattedError.title = 'Authentication Required'
        formattedError.message = 'Your session has expired. Please sign in again.'
        formattedError.canRetry = false
        break

      case 403:
        formattedError.title = 'Access Denied'
        formattedError.message = problemDetail || 'You don\'t have permission to perform this action.'
        formattedError.canRetry = false
        break

      case 404:
        formattedError.title = 'Not Found'
        formattedError.message = problemDetail || 'The requested resource could not be found.'
        formattedError.canRetry = false
        break

      case 409:
        formattedError.title = 'Conflict'
        formattedError.message = problemDetail || 'This action conflicts with the current state. Please refresh and try again.'
        formattedError.canRetry = true
        break

      case 429:
        formattedError.title = 'Too Many Requests'
        formattedError.message = 'You\'re making requests too quickly. Please wait a moment and try again.'
        formattedError.canRetry = true
        break

      case 500:
        formattedError.title = 'Server Error'
        formattedError.message = 'Internal Server Error'
        formattedError.canRetry = true
        break

      case 502:
      case 503:
      case 504:
        formattedError.title = 'Service Unavailable'
        formattedError.message = 'GeoPulse is temporarily unavailable. Please try again in a few minutes.'
        formattedError.isConnectionError = true
        formattedError.canRetry = true
        break

      default:
        formattedError.title = `Error ${status}`
        formattedError.message = problemDetail || `An error occurred (${status}). Please try again.`
        formattedError.canRetry = true
    }

    // If the server provided a specific user-friendly message, use it
    if (data?.userMessage) {
      formattedError.message = data.userMessage
    }
  }

  // Handle specific authentication errors
  if (error.message?.includes('Authentication expired') || 
      error.message?.includes('Please login again')) {
    
    formattedError.title = 'Session Expired'
    formattedError.message = 'Your session has expired. Please sign in again.'
    formattedError.canRetry = false
  }

  return formattedError
}

export function getFriendlyErrorMessage(error, fallbackMessage = 'An unexpected error occurred. Please try again.') {
  if (!error) {
    return fallbackMessage
  }

  const userMessage = getErrorText(error.userMessage)
  if (userMessage) {
    return userMessage
  }

  const apiMessage = getErrorText(formatApiErrorDetail(error, null))
  if (apiMessage) {
    return apiMessage
  }

  return getErrorText(formatError(error).message) || fallbackMessage
}

/**
 * A server error stays up longer than an ordinary toast, so the reference id can be read and
 * copied, but it still closes on its own -- a toast that has to be dismissed is an interruption.
 */
const REFERENCE_TOAST_LIFE = 8000

/**
 * How long a toast should stay up.
 */
function defaultToastLife(formattedError, error) {
  if (hasErrorReference(error)) {
    return REFERENCE_TOAST_LIFE
  }
  if (formattedError.isConnectionError) {
    return 6000
  }
  return 4000
}

/**
 * Toast fields for a hand-built error toast.
 *
 * A server error carrying a reference belongs in the gp-error group, which renders the id with a
 * copy button, and gets longer on screen so the id can be taken to the backend logs.
 */
export function errorToastOptions(error, life = 4000) {
  if (!hasErrorReference(error)) {
    return { life }
  }
  return { life: REFERENCE_TOAST_LIFE, group: 'gp-error', data: { errorId: getErrorReferenceId(error) } }
}

/**
 * Create a toast notification from an error
 * @param {Function} toastAdd - The toast.add function from PrimeVue
 * @param {Error} error - The error to display
 * @param {Object} options - Additional options
 * @returns {Object} - The formatted error for further handling if needed
 */
export function showErrorToast(toastAdd, error, options = {}) {
  const formattedError = formatError(error)
  const hasReference = hasErrorReference(error)

  const toastConfig = {
    severity: formattedError.severity,
    summary: formattedError.title,
    detail: withErrorReference(formattedError.message, error),
    life: options.life ?? defaultToastLife(formattedError, error),
    // The gp-error group renders the reference as its own row with a copy button; other groups
    // fall back to the plain detail text, which already carries the hint and the id.
    ...(hasReference ? { group: 'gp-error', data: { errorId: getErrorReferenceId(error) } } : {}),
    ...options
  }

  toastAdd(toastConfig)
  
  return formattedError
}

/**
 * Detect a GeoPulse problem document (RFC 7807): the backend answered the request itself.
 *
 * GeoPulseProblemPostProcessor stamps every problem the application emits -- 4xx and 5xx alike --
 * with type "urn:geopulse:error:<CODE>", code and errorId, so their presence proves the backend
 * produced the response, not a proxy. A 500 carrying one is a failure of a live backend, never an
 * outage, and must not send the user to the error page.
 */
function isGeoPulseProblemResponse(error) {
  const data = error.response?.data
  if (!data || typeof data !== 'object') {
    return false
  }
  if (typeof data.type === 'string' && data.type.startsWith('urn:geopulse:error:')) {
    return true
  }
  // A problem that reached us without type was still only ever produced by the backend.
  return typeof data.code === 'string' && typeof data.errorId === 'string'
}

/**
 * Check if the current error indicates the backend is completely down
 * @param {Error} error - The error to check
 * @returns {boolean} - True if this looks like a complete backend outage
 */
export function isBackendDown(error) {
  // Don't treat image loading errors as backend down
  if (error.isImageLoadingError || error.isImageDownloadError) {
    return false
  }
  
  // Don't treat Immich-specific errors as backend down
  if (error.url && error.url.includes('/immich/')) {
    return false
  }
  
  // Don't treat errors from image-related URLs as backend down
  if (error.config?.url && error.config.url.includes('/immich/')) {
    return false
  }

  const responseStatus = error.response?.status
  const responseText = getErrorText(error.response?.data)
  const statusText = getErrorText(error.response?.statusText)
  const messageText = getErrorText(error.message)
  const combinedText = `${responseText} ${statusText} ${messageText}`
  const requestUrl = error.config?.url || error.url || ''
  const upstreamConnectionFailurePattern = /(ECONNREFUSED|ECONNRESET|ENOTFOUND|EHOSTUNREACH|ETIMEDOUT|socket hang up|upstream|proxy error|connect ECONNREFUSED|connection refused)/i
  const isHealthOrPublicAuthProbe =
    requestUrl.includes('/health') ||
    requestUrl.includes('/auth/sessions/current/refresh') ||
    requestUrl.includes('/auth/sessions/current') ||
    requestUrl.includes('/auth/sessions') ||
    requestUrl.includes('/auth/oidc/providers')
  const isLocalProxy500ForBackendDown =
    responseStatus === 500 && !isGeoPulseProblemResponse(error) && (
      upstreamConnectionFailurePattern.test(combinedText) ||
      isHealthOrPublicAuthProbe
    )

  return (
    error.code === 'NETWORK_ERROR' ||
    error.message === 'Network Error' ||
    error.message?.includes('ERR_NETWORK') ||
    error.message?.includes('Failed to fetch') ||
    (error.response && [502, 503, 504].includes(error.response.status)) ||
    isLocalProxy500ForBackendDown
  )
}

/**
 * Create a retry-enabled error handler
 * @param {Function} toastAdd - The toast.add function
 * @param {Function} retryFunction - Function to call when retry is clicked
 * @returns {Function} - Error handler function
 */
export function createRetryableErrorHandler(toastAdd, retryFunction) {
  return (error) => {
    const formattedError = showErrorToast(toastAdd, error)
    
    if (formattedError.canRetry && retryFunction) {
      // Add a retry toast after a short delay
      setTimeout(() => {
        toastAdd({
          severity: 'info',
          summary: 'Retry Available',
          detail: 'Click here to try again',
          life: 5000,
          onClick: retryFunction
        })
      }, 1000)
    }
    
    return formattedError
  }
}
