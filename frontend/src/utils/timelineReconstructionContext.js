export function pointsInViewport(points, bounds) {
  if (!bounds) return []
  const sw = bounds.getSouthWest()
  const ne = bounds.getNorthEast()
  const west = sw.lng
  const width = ne.lng - west
  return points.filter(point => {
    if (!Number.isFinite(point.latitude) || !Number.isFinite(point.longitude)) return false
    const offset = ((point.longitude - west) % 360 + 360) % 360
    const longitudeInside = width >= 360 || offset <= ((width % 360 + 360) % 360)
    return point.latitude >= sw.lat && point.latitude <= ne.lat && longitudeInside
  })
}

export function selectedReconstructionRange(first, second) {
  const times = [first?.timestamp, second?.timestamp].map(value => Date.parse(value))
  if (!times.every(Number.isFinite) || times[0] === times[1]) return null
  times.sort((a, b) => a - b)
  return { startTime: new Date(times[0]).toISOString(), endTime: new Date(times[1]).toISOString() }
}

export function missingActivityGaps(timeline, from, to) {
  const start = Date.parse(from), end = Date.parse(to)
  const gaps = new Map()
  for (const item of timeline || []) {
    if (item.type !== 'dataGap' || item.ongoing) continue
    const gapStart = Math.max(Date.parse(item.startTime), Number.isFinite(start) ? start : -Infinity)
    const gapEnd = Math.min(Date.parse(item.endTime), Number.isFinite(end) ? end : Infinity)
    if (!Number.isFinite(gapStart) || !Number.isFinite(gapEnd) || gapStart >= gapEnd) continue
    const key = item.id ?? `${gapStart}:${gapEnd}`
    gaps.set(key, { ...item, startTime: new Date(gapStart).toISOString(), endTime: new Date(gapEnd).toISOString() })
  }
  return [...gaps.values()].sort((a, b) => Date.parse(a.startTime) - Date.parse(b.startTime))
}

export function missingActivityBoundaryPlaces(gap, timeline, points) {
  if (!gap) return { before: null, after: null }
  const from = Date.parse(gap.startTime), to = Date.parse(gap.endTime)
  const valid = point => Number.isFinite(point?.latitude) && Number.isFinite(point?.longitude)
  const timed = (points || []).filter(point => valid(point) && Number.isFinite(Date.parse(point.timestamp)))
  const beforePoint = timed.filter(point => Date.parse(point.timestamp) <= from).sort((a, b) => Date.parse(b.timestamp) - Date.parse(a.timestamp))[0]
  const afterPoint = timed.filter(point => Date.parse(point.timestamp) >= to).sort((a, b) => Date.parse(a.timestamp) - Date.parse(b.timestamp))[0]
  const events = (timeline || []).filter(item => item.type !== 'dataGap').map(item => ({ ...item,
    eventStart: Date.parse(item.timestamp || item.startTime),
    eventEnd: item.endTime ? Date.parse(item.endTime) : Date.parse(item.timestamp || item.startTime) + (item.stayDuration || item.tripDuration || 0) * 1000 }))
  const beforeEvent = events.filter(item => item.eventEnd <= from).sort((a, b) => b.eventEnd - a.eventEnd)[0]
  const afterEvent = events.filter(item => item.eventStart >= to).sort((a, b) => a.eventStart - b.eventStart)[0]
  const before = beforePoint || (beforeEvent ? { ...beforeEvent,
    latitude: beforeEvent.endLatitude ?? beforeEvent.latitude, longitude: beforeEvent.endLongitude ?? beforeEvent.longitude } : null)
  return { before: valid(before) ? before : null, after: valid(afterPoint || afterEvent) ? afterPoint || afterEvent : null }
}
