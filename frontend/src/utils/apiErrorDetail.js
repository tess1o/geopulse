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

export const formatApiErrorDetail = (error, fallback) => {
  const problem = normalizeApiError(error, fallback)
  if (problem.violations.length > 0) {
    return problem.violations
      .map(violation => {
        const field = formatViolationField(violation.field)
        if (!violation.detail) return null
        return field ? `${field}: ${violation.detail}` : violation.detail
      })
      .filter(Boolean)
      .join('; ')
  }
  return problem.detail
}
