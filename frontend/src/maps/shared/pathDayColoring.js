// Day colors for multi-day timeline paths. The path keeps its gap segments (pathData groups); each group is
// cut into per-day pieces only for drawing, so trip reconstruction and click payloads still see the original
// points and indices.
import { HEATMAP_GRADIENTS, getColorDistance, sampleGradient } from '@/maps/shared/mapAppearance'

export const DAY_COLOR_MODES = Object.freeze({ DISTINCT: 'distinct', GRADIENT: 'gradient' })

// Opacity (0-1) of the other days' lines while one day is focused; this, not HIGHLIGHTED_TRIP_BACKGROUND_OPACITY,
// is the knob for them. Slightly fainter than the background of a selected trip (0.25) because a day spans many
// overlapping lines, which add up, and bright day colors stay visible on satellite tiles. Their outline is
// dropped for the same reason.
export const MUTED_DAY_OPACITY = 0.33

// Below this CIE76 delta E a day line could pass for the selected path.
export const MIN_DISTANCE_FROM_ACTIVE_COLOR = 25

const GRADIENT_SCAN_STEPS = 100

const isNearActiveColor = (color, activePathColor) => {
  const distance = getColorDistance(color, activePathColor)
  return distance !== null && distance < MIN_DISTANCE_FROM_ACTIVE_COLOR
}

// The longest stretch of the gradient that stays clear of the active color; the whole gradient if none does.
const findSafeGradientInterval = (gradient, activePathColor) => {
  let best = null
  let runStart = null
  for (let step = 0; step <= GRADIENT_SCAN_STEPS; step += 1) {
    const safe = !isNearActiveColor(sampleGradient(gradient, step / GRADIENT_SCAN_STEPS), activePathColor)
    if (safe && runStart === null) {
      runStart = step
    }
    if (runStart !== null && (!safe || step === GRADIENT_SCAN_STEPS)) {
      const runEnd = safe ? step : step - 1
      if (!best || runEnd - runStart > best[1] - best[0]) {
        best = [runStart, runEnd]
      }
      runStart = null
    }
  }
  return best && best[1] > best[0]
    ? [best[0] / GRADIENT_SCAN_STEPS, best[1] / GRADIENT_SCAN_STEPS]
    : [0, 1]
}

/**
 * Colors for the days of a range, in range order. Every day gets its own palette color while the palette
 * (minus colors close to the selected-path color) has enough; longer ranges run oldest to newest along the
 * gradient. Colors depend only on a day's position in the range, so days without data do not shift the others.
 */
export function buildDayColors(dayKeys, { dayPalette, heatmapGradient, activePathColor } = {}) {
  const keys = [...new Set(Array.isArray(dayKeys) ? dayKeys : [])]
  if (keys.length < 2) {
    return null
  }

  const distinctColors = (dayPalette || []).filter((color) => !isNearActiveColor(color, activePathColor))
  if (keys.length <= distinctColors.length) {
    return {
      mode: DAY_COLOR_MODES.DISTINCT,
      days: keys.map((dayKey, index) => ({ dayKey, color: distinctColors[index] }))
    }
  }

  const gradient = heatmapGradient || HEATMAP_GRADIENTS.VIRIDIS
  const [from, to] = findSafeGradientInterval(gradient, activePathColor)
  return {
    mode: DAY_COLOR_MODES.GRADIENT,
    days: keys.map((dayKey, index) => ({
      dayKey,
      color: sampleGradient(gradient, from + ((to - from) * index) / (keys.length - 1))
    }))
  }
}

const toTimestampMs = (value) => {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null
  }
  if (!value) {
    return null
  }
  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? null : parsed
}

// Index of the last day starting at or before the timestamp; earlier points belong to the first day.
const findDayIndex = (days, timestampMs) => {
  let low = 0
  let high = days.length - 1
  while (low < high) {
    const middle = Math.ceil((low + high) / 2)
    if (days[middle].startMs <= timestampMs) {
      low = middle
    } else {
      high = middle - 1
    }
  }
  return low
}

/**
 * Cuts each path group into per-day pieces. `days` are { dayKey, startMs, color } sorted by startMs.
 * A new piece starts with the previous point, so the line across midnight has no gap; it takes the new
 * day's color. Points without a timestamp stay with the piece they follow. Returns, per group, an array
 * of { dayKey, color, points } with at least two points each.
 */
export function splitPathGroupsByDay(pathData, days) {
  if (!Array.isArray(pathData) || !Array.isArray(days) || days.length === 0) {
    return []
  }

  return pathData.map((group) => {
    const pieces = []
    let current = null
    let previous = null

    ;(Array.isArray(group) ? group : []).forEach((point) => {
      const timestampMs = toTimestampMs(point?.timestamp)
      const dayIndex = timestampMs === null ? (current?.dayIndex ?? 0) : findDayIndex(days, timestampMs)
      if (!current || dayIndex !== current.dayIndex) {
        current = { dayIndex, points: previous ? [previous] : [] }
        pieces.push(current)
      }
      current.points.push(point)
      previous = point
    })

    return pieces
      .filter((piece) => piece.points.length >= 2)
      .map(({ dayIndex, points }) => ({
        dayKey: days[dayIndex].dayKey,
        color: days[dayIndex].color,
        points
      }))
  })
}
