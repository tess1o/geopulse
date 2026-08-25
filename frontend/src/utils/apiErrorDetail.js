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

export const extractApiErrorDetail = (error, fallback) => {
  const data = error?.response?.data
  if (Array.isArray(data?.violations) && data.violations.length > 0) {
    return data.violations
      .map(violation => {
        const field = formatViolationField(violation.field)
        return field ? `${field}: ${violation.message}` : violation.message
      })
      .filter(Boolean)
      .join('; ')
  }

  if (data?.message) {
    return data.message
  }

  if (data?.error) {
    return data.error
  }

  if (data?.details) {
    const details = String(data.details)
    const exceptionMessage = details.match(/(?:java\.[\w.]+Exception|jakarta\.[\w.]+Exception):\s*(.+)$/)
    return exceptionMessage?.[1] || details
  }

  return error?.message || fallback
}
