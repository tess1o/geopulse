import { t } from '@/locales'

// Model for choosing activity boundaries from recorded GPS samples: the samples in time order, numbered,
// coloured by time, with the pauses between them marked, so map dots and list rows can be matched by eye.

// A pause shorter than this is normal sampling, never a gap.
export const MIN_GAP_SECONDS = 60
// A pause this many times the typical interval stands out from the sampling rhythm.
const GAP_INTERVAL_FACTOR = 3

// Early samples are teal, late ones magenta; the middle passes through indigo. Readable on light and dark tiles.
const TIME_COLOR_STOPS = [[20, 184, 166], [79, 70, 229], [192, 38, 211]]

const toHex = value => Math.round(value).toString(16).padStart(2, '0')

export function timeColor(fraction) {
  const position = Math.min(Math.max(Number.isFinite(fraction) ? fraction : 0, 0), 1) * (TIME_COLOR_STOPS.length - 1)
  const lower = Math.min(Math.floor(position), TIME_COLOR_STOPS.length - 2)
  const ratio = position - lower
  const [from, to] = [TIME_COLOR_STOPS[lower], TIME_COLOR_STOPS[lower + 1]]
  return `#${from.map((channel, index) => toHex(channel + (to[index] - channel) * ratio)).join('')}`
}

const median = values => {
  if (!values.length) return 0
  const sorted = [...values].sort((a, b) => a - b)
  const middle = Math.floor(sorted.length / 2)
  return sorted.length % 2 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2
}

export function gapThresholdSeconds(intervals) {
  return Math.max(MIN_GAP_SECONDS, median(intervals.filter(value => value > 0)) * GAP_INTERVAL_FACTOR)
}

/**
 * Recorded samples with valid coordinates and time, sorted by time. Each one gets its position (`index`),
 * a 1-based `number` shown on the map and in the list, the seconds since the previous sample, whether that
 * pause is a gap, and its time colour.
 */
export function buildGpsTimePickerPoints(points) {
  const sorted = (Array.isArray(points) ? points : [])
    .map(point => ({ point, time: Date.parse(point?.timestamp) }))
    .filter(({ point, time }) => Number.isFinite(time) && Number.isFinite(point.latitude) && Number.isFinite(point.longitude))
    .sort((a, b) => a.time - b.time)
  const intervals = sorted.slice(1).map((entry, index) => (entry.time - sorted[index].time) / 1000)
  const threshold = gapThresholdSeconds(intervals)
  const last = Math.max(sorted.length - 1, 1)
  return sorted.map(({ point, time }, index) => {
    const secondsSincePrevious = index ? intervals[index - 1] : null
    return { ...point, index, number: index + 1, time, secondsSincePrevious,
      gapBefore: secondsSincePrevious !== null && secondsSincePrevious >= threshold,
      color: timeColor(index / last) }
  })
}

const coordinate = point => [point.latitude, point.longitude]

/**
 * The recorded track in time order as [latitude, longitude] lines: `moving` joins consecutive samples,
 * `gaps` joins the two samples on either side of each gap, so a pause can be drawn differently.
 */
export function gpsTrackLines(points) {
  const moving = []
  const gaps = []
  let current = []
  points.forEach((point, index) => {
    // The first point of a narrowed list may follow a gap whose other end is hidden.
    if (point.gapBefore && index > 0) {
      if (current.length > 1) moving.push(current)
      gaps.push([coordinate(points[index - 1]), coordinate(point)])
      current = []
    }
    current.push(coordinate(point))
  })
  if (current.length > 1) moving.push(current)
  return { moving, gaps }
}

export function gpsRangeLine(points, from, to) {
  if (!Number.isInteger(from) || !Number.isInteger(to) || from >= to) return []
  return points.filter(point => point.index >= from && point.index <= to).map(coordinate)
}

/**
 * Sets one boundary of the From/To pair. From must stay before To, so a boundary that would cross the
 * other clears it instead of silently swapping their meaning.
 */
export function setGpsBoundary(range, boundary, index) {
  const next = { from: range?.from ?? null, to: range?.to ?? null }
  if (boundary === 'from') {
    next.from = index
    if (next.to !== null && next.to <= index) next.to = null
  } else {
    next.to = index
    if (next.from !== null && next.from >= index) next.from = null
  }
  return next
}

export function isInGpsRange(index, range) {
  return Number.isInteger(range?.from) && Number.isInteger(range?.to) && index >= range.from && index <= range.to
}

/**
 * Chooses which projected samples get a number label, so labels never overlap. `items` are
 * `{ index, x, y }` in screen pixels; `priority` indices are labelled first (selected, hovered, From, To),
 * then the rest in time order.
 */
export function selectLabelledIndices(items, { minDistance = 22, priority = [], limit = 300 } = {}) {
  const cell = minDistance
  const occupied = new Map()
  const labelled = new Set()
  const byIndex = new Map(items.map(item => [item.index, item]))
  const fits = ({ x, y }) => {
    const cellX = Math.floor(x / cell), cellY = Math.floor(y / cell)
    for (let dx = -1; dx <= 1; dx += 1) {
      for (let dy = -1; dy <= 1; dy += 1) {
        for (const other of occupied.get(`${cellX + dx}:${cellY + dy}`) || []) {
          if (Math.hypot(other.x - x, other.y - y) < minDistance) return false
        }
      }
    }
    return true
  }
  const take = item => {
    const key = `${Math.floor(item.x / cell)}:${Math.floor(item.y / cell)}`
    if (!occupied.has(key)) occupied.set(key, [])
    occupied.get(key).push(item)
    labelled.add(item.index)
  }
  priority.map(index => byIndex.get(index)).filter(Boolean).forEach(item => { if (!labelled.has(item.index)) take(item) })
  for (const item of items) {
    if (labelled.size >= limit) break
    if (!labelled.has(item.index) && fits(item)) take(item)
  }
  return labelled
}

export function splitDuration(seconds) {
  const total = Math.max(0, Math.round(Number(seconds) || 0))
  return { hours: Math.floor(total / 3600), minutes: Math.floor((total % 3600) / 60), seconds: total % 60 }
}

// "45 s", "6 min 17 s", "2 h 5 min": seconds matter when boundaries are a few samples apart.
export function formatGpsPickerDuration(seconds) {
  const { hours, minutes, seconds: rest } = splitDuration(seconds)
  if (hours) return t('missingActivity.picker.durationHours', { hours, minutes })
  if (minutes) return t('missingActivity.picker.durationMinutes', { minutes, seconds: rest })
  return t('missingActivity.picker.durationSeconds', { seconds: rest })
}

// A From/To time this close to a recorded point is shown on that point.
export const BOUNDARY_MATCH_SECONDS = 120

/**
 * Index of the point recorded closest to `time` (ms), searching only `min`..`max`, or null when the
 * closest one is more than `toleranceSeconds` away.
 */
export function nearestGpsPointIndex(points, time, { min = 0, max = points.length - 1, toleranceSeconds = Infinity } = {}) {
  if (!Number.isFinite(time) || !points.length || min > max) return null
  let low = Math.max(min, 0), high = Math.min(max, points.length - 1)
  while (low < high) {
    const middle = Math.floor((low + high) / 2)
    if (points[middle].time < time) low = middle + 1
    else high = middle
  }
  const candidates = [low - 1, low].filter(index => index >= Math.max(min, 0) && index <= Math.min(max, points.length - 1))
  const best = candidates.reduce((closest, index) => (
    closest === null || Math.abs(points[index].time - time) < Math.abs(points[closest].time - time) ? index : closest), null)
  return best !== null && Math.abs(points[best].time - time) <= toleranceSeconds * 1000 ? best : null
}

/** The From/To pair as point indices, for times already chosen in the activity (ms or null). */
export function gpsRangeForTimes(points, startTime, endTime) {
  const match = time => nearestGpsPointIndex(points, time, { toleranceSeconds: BOUNDARY_MATCH_SECONDS })
  return { from: match(startTime), to: match(endTime) }
}

// Context kept around From and To so the moment of arriving and leaving can be judged: the points within
// ten minutes, but at least a few and never so many that the narrowed list fills up again.
export const CONTEXT_SECONDS = 600
export const CONTEXT_MIN_POINTS = 5
export const CONTEXT_MAX_POINTS = 50

const firstAtOrAfter = (points, time) => {
  let low = 0, high = points.length
  while (low < high) {
    const middle = Math.floor((low + high) / 2)
    if (points[middle].time < time) low = middle + 1
    else high = middle
  }
  return low
}

/**
 * The points shown in the list and on the map: those between From and To (ms) plus context on each side.
 * An unset boundary, or a side the user expanded, shows everything on that side. Returns inclusive
 * indices `{ from, to }`, with `to < from` when there are no points.
 */
export function gpsVisibleWindow(points, startTime, endTime, { showAllBefore = false, showAllAfter = false } = {}) {
  const last = points.length - 1
  if (last < 0) return { from: 0, to: -1 }
  let from = 0, to = last
  if (!showAllBefore && Number.isFinite(startTime)) {
    const limit = startTime - CONTEXT_SECONDS * 1000
    from = Math.min(firstAtOrAfter(points, startTime), last)
    for (let count = 0; from > 0 && count < CONTEXT_MAX_POINTS && (count < CONTEXT_MIN_POINTS || points[from - 1].time >= limit); count += 1) from -= 1
  }
  if (!showAllAfter && Number.isFinite(endTime)) {
    const limit = endTime + CONTEXT_SECONDS * 1000
    to = Math.max(firstAtOrAfter(points, endTime + 1) - 1, 0)
    for (let count = 0; to < last && count < CONTEXT_MAX_POINTS && (count < CONTEXT_MIN_POINTS || points[to + 1].time <= limit); count += 1) to += 1
  }
  return from <= to ? { from, to } : { from: to, to: from }
}

/**
 * Of the samples whose dots lie under a click ({index, x, y} in container pixels), the one nearest to where the click
 * landed, or null. Overlapping dots otherwise resolve to whichever was drawn last, which on a drive recorded every few
 * metres puts a route point on the neighbouring sample instead of the one clicked.
 */
export function nearestSampleUnderClick(candidates, click) {
  let nearest = null
  let nearestDistance = Infinity
  for (const candidate of candidates) {
    const distance = Math.hypot(candidate.x - click.x, candidate.y - click.y)
    if (distance < nearestDistance) {
      nearest = candidate.index
      nearestDistance = distance
    }
  }
  return nearest
}
