export const FRIEND_TRAIL_RANGE_STORAGE_KEY = 'friends_live_trail_range'
export const FRIEND_TRAIL_RANGE_DEFAULT = '1h'
export const FRIEND_TRAIL_RANGE_MAX_MINUTES = 1440

export const FRIEND_TRAIL_RANGE_OPTIONS = Object.freeze([
  { value: '1h', label: '1h' },
  { value: '6h', label: '6h' },
  { value: 'today', label: 'Today' }
])

const RANGE_TO_MINUTES = Object.freeze({
  '1h': 60,
  '6h': 360
})

const VALID_RANGES = new Set(FRIEND_TRAIL_RANGE_OPTIONS.map(option => option.value))

export const normalizeFriendTrailRange = (value) => {
  const normalizedValue = typeof value === 'string' ? value.trim().toLowerCase() : ''
  return VALID_RANGES.has(normalizedValue) ? normalizedValue : FRIEND_TRAIL_RANGE_DEFAULT
}

const clampTrailMinutes = (value) => {
  if (!Number.isFinite(value)) {
    return 1
  }

  return Math.min(FRIEND_TRAIL_RANGE_MAX_MINUTES, Math.max(1, Math.ceil(value)))
}

export const getFriendTrailRangeRequest = ({ trailRange, nowInUserTimezone }) => {
  const normalizedRange = normalizeFriendTrailRange(trailRange)

  if (normalizedRange !== 'today') {
    return {
      trailRange: normalizedRange,
      minutes: RANGE_TO_MINUTES[normalizedRange] || RANGE_TO_MINUTES[FRIEND_TRAIL_RANGE_DEFAULT],
      endTime: null,
      nowIso: null
    }
  }

  const canUseNow = nowInUserTimezone
    && typeof nowInUserTimezone.startOf === 'function'
    && typeof nowInUserTimezone.diff === 'function'
    && typeof nowInUserTimezone.toDate === 'function'
    && typeof nowInUserTimezone.toISOString === 'function'

  if (!canUseNow) {
    return {
      trailRange: normalizedRange,
      minutes: FRIEND_TRAIL_RANGE_MAX_MINUTES,
      endTime: new Date(),
      nowIso: new Date().toISOString()
    }
  }

  const startOfDay = nowInUserTimezone.startOf('day')
  const minutesSinceDayStart = nowInUserTimezone.diff(startOfDay, 'minute', true)

  return {
    trailRange: normalizedRange,
    minutes: clampTrailMinutes(minutesSinceDayStart),
    endTime: nowInUserTimezone.toDate(),
    nowIso: nowInUserTimezone.toISOString()
  }
}

export const filterFriendTrailPointsForRange = ({ points, trailRange, nowIso, isSameDay }) => {
  if (!Array.isArray(points)) {
    return []
  }

  const normalizedRange = normalizeFriendTrailRange(trailRange)
  if (normalizedRange !== 'today') {
    return points
  }

  if (!nowIso || typeof isSameDay !== 'function') {
    return points
  }

  return points.filter((point) => {
    const timestamp = point?.timestamp
    if (!timestamp) {
      return false
    }

    return isSameDay(timestamp, nowIso)
  })
}

export const getFriendTrailRangeDescription = (trailRange) => {
  const normalizedRange = normalizeFriendTrailRange(trailRange)

  if (normalizedRange === 'today') {
    return 'today'
  }

  if (normalizedRange === '6h') {
    return 'last 6 hours'
  }

  return 'last 60 minutes'
}

export const loadStoredFriendTrailRange = (storage = globalThis?.localStorage) => {
  if (!storage || typeof storage.getItem !== 'function') {
    return FRIEND_TRAIL_RANGE_DEFAULT
  }

  try {
    return normalizeFriendTrailRange(storage.getItem(FRIEND_TRAIL_RANGE_STORAGE_KEY))
  } catch (error) {
    return FRIEND_TRAIL_RANGE_DEFAULT
  }
}

export const saveFriendTrailRange = (trailRange, storage = globalThis?.localStorage) => {
  if (!storage || typeof storage.setItem !== 'function') {
    return
  }

  try {
    storage.setItem(FRIEND_TRAIL_RANGE_STORAGE_KEY, normalizeFriendTrailRange(trailRange))
  } catch (error) {
    // no-op: localStorage may be unavailable in private mode.
  }
}
