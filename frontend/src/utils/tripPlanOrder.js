/**
 * The single ordering rule for a trip's planned stops, shared by the stops rail, the map markers
 * and the route line (and mirrored by the backend's `findByTripId` query):
 *   planned day ascending, unscheduled last -> position within the plan -> id.
 *
 * Previously the page sorted MUST stops first, the rail sorted by day, and the map did not sort
 * at all, so the same plan read in three different orders.
 */

export const UNSCHEDULED_KEY = '__unscheduled__'

const dayKey = (item) => item?.plannedDay || null

export const comparePlanItems = (a, b) => {
  const dayA = dayKey(a)
  const dayB = dayKey(b)
  if (dayA !== dayB) {
    if (dayA === null) return 1
    if (dayB === null) return -1
    // ISO dates (YYYY-MM-DD) order correctly as strings.
    return String(dayA).localeCompare(String(dayB))
  }

  const orderDiff = (a?.orderIndex ?? 0) - (b?.orderIndex ?? 0)
  if (orderDiff !== 0) return orderDiff

  return Number(a?.id || 0) - Number(b?.id || 0)
}

export const sortPlanItems = (items) => [...(Array.isArray(items) ? items : [])].sort(comparePlanItems)

/**
 * Groups already-sorted stops by planned day, unscheduled last.
 * `extraDays` (ISO dates) adds empty groups, e.g. the trip's other days as drop targets while
 * dragging; `includeUnscheduled` forces an (empty) unscheduled group for the same reason.
 */
export const groupPlanItemsByDay = (sortedItems, { extraDays = [], includeUnscheduled = false } = {}) => {
  const buckets = new Map()

  for (const day of extraDays) {
    if (day) buckets.set(day, [])
  }

  for (const item of sortedItems || []) {
    const key = dayKey(item) || UNSCHEDULED_KEY
    if (!buckets.has(key)) {
      buckets.set(key, [])
    }
    buckets.get(key).push(item)
  }

  if (includeUnscheduled && !buckets.has(UNSCHEDULED_KEY)) {
    buckets.set(UNSCHEDULED_KEY, [])
  }

  const dated = [...buckets.entries()]
    .filter(([key]) => key !== UNSCHEDULED_KEY)
    .sort(([a], [b]) => String(a).localeCompare(String(b)))
    .map(([key, items]) => ({ key, day: key, items }))

  if (buckets.has(UNSCHEDULED_KEY)) {
    dated.push({ key: UNSCHEDULED_KEY, day: null, items: buckets.get(UNSCHEDULED_KEY) })
  }

  return dated
}

/** The complete ordered list the reorder endpoint expects: each stop's position and the day it sits in. */
export const buildReorderPayload = (groups) => (groups || []).flatMap((group) =>
  (group.items || []).map((item) => ({ id: item.id, plannedDay: group.day ?? null }))
)

/** Applies a reorder payload locally, so the list can move before the server answers. */
export const applyReorderPayload = (items, payload) => {
  const byId = new Map((items || []).map((item) => [item.id, item]))
  return (payload || [])
    .map((entry, index) => {
      const item = byId.get(entry.id)
      return item ? { ...item, plannedDay: entry.plannedDay ?? null, orderIndex: index } : null
    })
    .filter(Boolean)
}

/** Sequence numbers (1..n) in plan order, shared by the rail cards and the map markers. */
export const buildSequenceMap = (sortedItems) => {
  const map = new Map()
  ;(sortedItems || []).forEach((item, index) => map.set(item.id, index + 1))
  return map
}

// `Number(null)` and `Number('')` are both 0, so emptiness is checked before the numeric test.
const isCoordinate = (value) => value !== null && value !== undefined && value !== '' && Number.isFinite(Number(value))

export const hasPlanCoordinates = (item) => isCoordinate(item?.latitude) && isCoordinate(item?.longitude)

/**
 * Straight legs between consecutive located stops, in plan order. Drawn on their own when road
 * routing is unavailable, and as a placeholder while routed legs load.
 */
export const buildStraightLegs = (sortedItems) => {
  const located = (sortedItems || []).filter(hasPlanCoordinates)
  const legs = []
  for (let i = 1; i < located.length; i++) {
    const from = located[i - 1]
    const to = located[i]
    legs.push({
      fromItemId: from.id,
      toItemId: to.id,
      routed: false,
      coordinates: [
        [Number(from.latitude), Number(from.longitude)],
        [Number(to.latitude), Number(to.longitude)]
      ]
    })
  }
  return legs
}
