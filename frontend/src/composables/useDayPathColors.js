import { computed, unref } from 'vue'
import { useTimezone } from '@/composables/useTimezone'
import { buildDayColors, splitPathGroupsByDay } from '@/maps/shared/pathDayColoring'

const firstAndLastTimestamp = (pathData) => {
  const timestamps = (Array.isArray(pathData) ? pathData : [])
    .flatMap((group) => (Array.isArray(group) ? [group[0], group[group.length - 1]] : []))
    .map((point) => point?.timestamp)
    .filter(Boolean)
    .sort((a, b) => Date.parse(a) - Date.parse(b))
  return timestamps.length ? [timestamps[0], timestamps[timestamps.length - 1]] : null
}

/**
 * Per-day path colors and day focus for a multi-day range. Days come from the selected range, the same way the timeline
 * list builds its day headers, so map and list agree; without a range they come from the path itself.
 *
 * @param {Object} options
 * @param {Ref<Array>} options.pathData - path groups (arrays of points), as drawn by PathLayer
 * @param {Ref<Array|null>} options.dayRange - [startUtc, endUtc] of the selected range
 * @param {Ref<boolean>} options.enabled - whether the path is colored by day
 * @param {Ref<Object>} options.appearance - resolved map appearance (resolveMapAppearance)
 * @param {Ref<string|null>} [options.focusedDay] - day shown on its own; the other days' pieces are muted
 */
export function useDayPathColors({ pathData, dayRange, enabled, appearance, focusedDay = null }) {
  const timezone = useTimezone()

  const dayKeys = computed(() => {
    const range = unref(dayRange)
    const bounds = Array.isArray(range) && range.length === 2 && range[0] && range[1]
      ? range
      : firstAndLastTimestamp(unref(pathData))
    return bounds ? timezone.getDateRangeArray(bounds[0], bounds[1]) : []
  })

  const isMultiDay = computed(() => dayKeys.value.length > 1)

  // { mode, days: [{ dayKey, color }] }, or null when the path is drawn in a single color.
  const dayColors = computed(() => {
    if (!unref(enabled) || !isMultiDay.value) {
      return null
    }
    const { dayPalette, heatmapGradient, activePathColor } = unref(appearance)
    return buildDayColors(dayKeys.value, { dayPalette, heatmapGradient, activePathColor })
  })

  const dayColorMap = computed(() => (
    dayColors.value
      ? Object.fromEntries(dayColors.value.days.map(({ dayKey, color }) => [dayKey, color]))
      : null
  ))

  // A focused day only counts on a multi-day range that contains it.
  const activeFocusedDay = computed(() => {
    const dayKey = unref(focusedDay)
    return isMultiDay.value && dayKeys.value.includes(dayKey) ? dayKey : null
  })

  // Per path group, its per-day pieces ({ dayKey, color, muted, points }); null draws the single path color.
  // Without day colors a piece's color is null, so it keeps the path color and only muting applies.
  const pathPieces = computed(() => {
    if (!dayColors.value && !activeFocusedDay.value) {
      return null
    }
    const colors = dayColorMap.value || {}
    const days = dayKeys.value.map((dayKey) => ({
      dayKey,
      color: colors[dayKey] || null,
      startMs: timezone.create(dayKey).valueOf()
    }))
    const pieces = splitPathGroupsByDay(unref(pathData), days)
    if (!activeFocusedDay.value) {
      return pieces
    }
    return pieces.map((group) => group.map((piece) => ({ ...piece, muted: piece.dayKey !== activeFocusedDay.value })))
  })

  return {
    isMultiDay,
    dayColors,
    dayColorMap,
    activeFocusedDay,
    pathPieces
  }
}
