/**
 * Error handling utilities for GeoPulse frontend
 */

import { formatApiErrorDetail, getErrorReferenceId, hasErrorReference, normalizeApiError, withErrorReference, AUTH_EXPIRED_CODE } from './apiErrorDetail'
import { t } from '@/locales'

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
    title: t('errors.generic.title'),
    message: t('errors.generic.message'),
    severity: 'error',
    // Diagnostic only -- surfaced in logs and technical views, never as user-facing copy, so it stays
    // untranslated.
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

    formattedError.title = t('errors.network.title')
    formattedError.message = t('errors.network.message')
    formattedError.isConnectionError = true
    formattedError.canRetry = true
    return formattedError
  }

  // Handle timeout errors
  if (error?.code === 'ECONNABORTED' ||
      error?.message?.includes('timeout')) {

    formattedError.title = t('errors.timeout.title')
    formattedError.message = t('errors.timeout.message')
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
          formattedError.title = t('errors.http.400.title')
          formattedError.message = problemDetail || t('errors.http.400.message')
          formattedError.canRetry = true
          break
        }

      case 401:
        formattedError.title = t('errors.http.401.title')
        formattedError.message = t('errors.http.401.message')
        formattedError.canRetry = false
        break

      case 403:
        formattedError.title = t('errors.http.403.title')
        formattedError.message = problemDetail || t('errors.http.403.message')
        formattedError.canRetry = false
        break

      case 404:
        formattedError.title = t('errors.http.404.title')
        formattedError.message = problemDetail || t('errors.http.404.message')
        formattedError.canRetry = false
        break

      case 409:
        formattedError.title = t('errors.http.409.title')
        formattedError.message = problemDetail || t('errors.http.409.message')
        formattedError.canRetry = true
        break

      case 429:
        formattedError.title = t('errors.http.429.title')
        formattedError.message = t('errors.http.429.message')
        formattedError.canRetry = true
        break

      case 500:
        formattedError.title = t('errors.http.500.title')
        formattedError.message = t('errors.http.500.message')
        formattedError.canRetry = true
        break

      case 502:
      case 503:
      case 504:
        formattedError.title = t(`errors.http.${status}.title`)
        formattedError.message = t(`errors.http.${status}.message`)
        formattedError.isConnectionError = true
        formattedError.canRetry = true
        break

      default:
        formattedError.title = t('errors.http.unknown.title', { status })
        formattedError.message = problemDetail || t('errors.http.unknown.message', { status })
        formattedError.canRetry = true
    }

    // If the server provided a specific user-friendly message, use it
    if (data?.userMessage) {
      formattedError.message = data.userMessage
    }
  }

  // Handle the refresh-token path, which signals an ended session with a code rather than a status.
  // Matched on the code, never the message: this copy is translatable, so matching on text would break
  // silently the moment either side was reworded or translated.
  if (error?.code === AUTH_EXPIRED_CODE) {
    formattedError.title = t('errors.session.title')
    formattedError.message = t('errors.session.message')
    formattedError.canRetry = false
    formattedError.isAuthExpired = true
  }

  return formattedError
}

export function getFriendlyErrorMessage(error, fallbackMessage) {
  const fallback = fallbackMessage ?? t('errors.generic.message')
  if (!error) {
    return fallback
  }

  const userMessage = getErrorText(error.userMessage)
  if (userMessage) {
    return userMessage
  }

  const apiMessage = getErrorText(formatApiErrorDetail(error, null))
  if (apiMessage) {
    return apiMessage
  }

  return getErrorText(formatError(error).message) || fallback
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
          summary: t('errors.retry.available'),
          detail: t('errors.retry.hint'),
          life: 5000,
          onClick: retryFunction
        })
      }, 1000)
    }
    
    return formattedError
  }
}
