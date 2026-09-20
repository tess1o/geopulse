import { formatMessageDescriptor } from './messageDescriptor'

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

export const productionErrorContext = (error) => {
  const data = error?.response?.data || {}
  const headers = error?.response?.headers || {}
  return {
    name: error?.name || 'Error',
    status: error?.response?.status ?? data?.status ?? null,
    code: data?.code ?? null,
    requestId: data?.requestId ?? headers['x-request-id'] ?? null,
    errorId: data?.errorId ?? headers['x-error-id'] ?? null
  }
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
  return problem.detail
}
