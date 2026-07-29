export function formatDuration(seconds) {
  const numericValue = Number(seconds)
  if (!Number.isFinite(numericValue) || numericValue < 0) {
    return 'Unknown'
  }

  if (numericValue === 0) {
    return '0 seconds'
  }

  if (numericValue < 60) {
    return 'less than a minute'
  }

  const minutes = numericValue / 60
  const days = Math.floor(minutes / (60 * 24))
  const hours = Math.floor((minutes % (60 * 24)) / 60)
  const remainingMinutes = Math.floor(minutes % 60)
  const parts = []

  if (days > 0) {
    parts.push(`${days} day${days > 1 ? 's' : ''}`)
  }

  if (hours > 0) {
    parts.push(`${hours} hour${hours > 1 ? 's' : ''}`)
  }

  if (remainingMinutes > 0 && days === 0) {
    parts.push(`${remainingMinutes} minute${remainingMinutes > 1 ? 's' : ''}`)
  }

  return parts.join(' ') || 'Unknown'
}
