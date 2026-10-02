import { formatMessageDescriptor } from './messageDescriptor'
import { t } from '@/locales'

/**
 * Stable code for the refresh-token path's failure.
 *
 * The refresh flow has no HTTP response to classify -- `apiService` gives up on refreshing and knows
 * only that the session is over -- so it signals expiry with this code rather than a status. It is
 * matched by code and never by message: the user-facing copy is translatable, so matching on it would
 * break silently the moment either side was reworded or translated.
 */
export const AUTH_EXPIRED_CODE = 'AUTH_EXPIRED'

export const createAuthExpiredError = () =>
  Object.assign(new Error('Authentication expired. Please login again.'), { code: AUTH_EXPIRED_CODE })

export const formatViolationField = (field) => {
  if (!field) {
    return null
  }

  const rawName = String(field).split('.').pop()
  return rawName
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/Ms$/, ' milliseconds')
    .replace(/\s+/g, ' ')
    .replace(/^./, char => char.toUpperCase())
}

const fallbackDetail = (data, error, fallback) => {
  if (data?.detail) return data.detail
  if (data?.title) return data.title
  return error?.message || fallback || 'An unexpected error occurred'
}

export const normalizeApiError = (error, fallback) => {
  if (error?.isApiError === true) {
    return error
  }

  const data = error?.response?.data || {}
  return {
    isApiError: true,
    status: error?.response?.status ?? data?.status ?? null,
    code: data?.code ?? null,
    errorId: data?.errorId ?? null,
    parameters: data?.parameters || {},
    violations: Array.isArray(data?.violations)
      ? data.violations.map(violation => ({
          field: violation?.field ?? null,
          in: violation?.in ?? null,
          code: violation?.code ?? null,
          parameters: violation?.parameters || {},
          detail: violation?.detail || violation?.message || null
        }))
      : [],
    detail: fallbackDetail(data, error, fallback)
  }
}

/**
 * The support reference for a failure: the errorId the backend stamped on the problem document.
 *
 * Reads the normalized field first -- normalizeApiError preserves it -- and falls back to the raw
 * response, so the same helper serves an error that has already travelled through fail().
 */
export const getErrorReferenceId = (error) => {
  const data = error?.response?.data || {}
  const headers = error?.response?.headers || {}
  return error?.errorId ?? data?.errorId ?? headers['x-error-id'] ?? null
}

export const productionErrorContext = (error) => {
  const data = error?.response?.data || {}
  const headers = error?.response?.headers || {}
  return {
    name: error?.name || 'Error',
    status: error?.response?.status ?? data?.status ?? null,
    code: data?.code ?? null,
    requestId: data?.requestId ?? headers['x-request-id'] ?? null,
    errorId: getErrorReferenceId(error)
  }
}

const errorStatus = (error) => error?.response?.status ?? error?.status ?? error?.response?.data?.status ?? null

/**
 * Whether a failure is worth quoting a support reference for.
 *
 * Only server-side failures qualify. A 5xx carries no detail of its own -- the backend deliberately
 * sends none for INTERNAL_ERROR -- so the reference is what lets a user report a failure that
 * leaves no other trace. 4xx is left alone: those describe the caller's own input, where a
 * reference would be noise.
 */
export const hasErrorReference = (error) => {
  const status = errorStatus(error)
  return typeof status === 'number' && status >= 500 && Boolean(getErrorReferenceId(error))
}

/**
 * Append the support reference to a failure's detail text, when it has one.
 *
 * GeoPulse is self-hosted, so there is no support desk to quote an id to: the reader is their own
 * admin, and the id is how they find the matching line in the backend logs. The hint says so, and
 * the id is plain text so it survives layouts that render the detail as a plain string.
 */
export const withErrorReference = (detail, error) => {
  if (!hasErrorReference(error)) {
    return detail
  }
  return `${detail}\n${t('errors.reference.hint', { id: getErrorReferenceId(error) })}`
}

export const formatApiErrorDetail = (error, fallback) => {
  const problem = normalizeApiError(error, fallback)
  if (problem.violations.length > 0) {
    return problem.violations
      .map(violation => {
        const field = formatViolationField(violation.field)
        const text = formatMessageDescriptor(violation.detail)
        if (!text) return null
        return field ? `${field}: ${text}` : text
      })
      .filter(Boolean)
      .join('; ')
  }
  return withErrorReference(problem.detail, error)
}
