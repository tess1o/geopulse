import { t } from '@/locales'

export function formatDuration(seconds) {
  const numericValue = Number(seconds)
  if (!Number.isFinite(numericValue) || numericValue < 0) {
    return t('common.unknown')
  }

  if (numericValue === 0) {
    return t('common.duration.zeroSeconds')
  }

  if (numericValue < 60) {
    return t('common.duration.lessThanMinute')
  }

  const minutes = numericValue / 60
  const days = Math.floor(minutes / (60 * 24))
  const hours = Math.floor((minutes % (60 * 24)) / 60)
  const remainingMinutes = Math.floor(minutes % 60)
  const parts = []

  if (days > 0) {
    parts.push(t('common.duration.days', { count: days }, days))
  }

  if (hours > 0) {
    parts.push(t('common.duration.hours', { count: hours }, hours))
  }

  if (remainingMinutes > 0 && days === 0) {
    parts.push(t('common.duration.minutes', { count: remainingMinutes }, remainingMinutes))
  }

  return parts.join(' ') || t('common.unknown')
}
