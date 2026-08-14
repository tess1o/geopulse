const resolveDurationSeconds = (item) => {
  if (!item || typeof item !== 'object') return null

  if (item.type === 'trip' || item.tripDuration !== undefined) {
    return item.tripDuration
  }

  if (item.type === 'stay' || item.stayDuration !== undefined) {
    return item.stayDuration
  }

  return null
}

export const buildTimelineItemGpsRange = (item) => {
  if (!item?.timestamp) return null

  const startMs = Date.parse(item.timestamp)
  const durationSeconds = Number(resolveDurationSeconds(item))

  if (!Number.isFinite(startMs) || !Number.isFinite(durationSeconds) || durationSeconds < 0) {
    return null
  }

  return {
    startTime: new Date(startMs).toISOString(),
    endTime: new Date(startMs + durationSeconds * 1000).toISOString()
  }
}
