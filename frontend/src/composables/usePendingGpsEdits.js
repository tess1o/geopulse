import { computed, shallowReactive } from 'vue'

const COORDINATE_EPSILON = 0.000001
const VALUE_EPSILON = 0.0001

const sameNumber = (left, right, epsilon) => {
  const leftMissing = left === null || left === undefined
  const rightMissing = right === null || right === undefined
  if (leftMissing || rightMissing) {
    return leftMissing && rightMissing
  }
  return Math.abs(Number(left) - Number(right)) < epsilon
}

const matchesOriginal = (original, changes) =>
  sameNumber(original.coordinates?.lat, changes.coordinates.lat, COORDINATE_EPSILON)
  && sameNumber(original.coordinates?.lng, changes.coordinates.lng, COORDINATE_EPSILON)
  && sameNumber(original.velocity, changes.velocity, VALUE_EPSILON)
  && sameNumber(original.accuracy, changes.accuracy, VALUE_EPSILON)

/**
 * GPS point corrections staged on the client and applied together in one request,
 * so several edits cost a single timeline regeneration.
 * Keyed by point id, so staged edits survive paging, sorting and filter changes.
 */
export function usePendingGpsEdits() {
  // id -> { original, changes }
  const pendingEdits = shallowReactive(new Map())

  const pendingCount = computed(() => pendingEdits.size)
  const hasPendingEdits = computed(() => pendingEdits.size > 0)

  const isPending = (pointId) => pendingEdits.has(pointId)

  const getChanges = (pointId) => pendingEdits.get(pointId)?.changes ?? null

  /** The point as loaded from the server, even when the passed row already shows staged values. */
  const getOriginal = (point) => pendingEdits.get(point?.id)?.original ?? point

  const stage = (point, changes) => {
    const original = getOriginal(point)
    const normalized = {
      coordinates: { lat: changes.coordinates.lat, lng: changes.coordinates.lng },
      velocity: changes.velocity ?? null,
      accuracy: changes.accuracy ?? null
    }

    if (matchesOriginal(original, normalized)) {
      pendingEdits.delete(original.id)
      return false
    }

    pendingEdits.set(original.id, { original, changes: normalized })
    return true
  }

  const revert = (pointId) => pendingEdits.delete(pointId)

  const revertAll = () => pendingEdits.clear()

  const dropIds = (pointIds) => {
    pointIds.forEach(id => pendingEdits.delete(id))
  }

  /** Overlay staged values onto loaded rows; staged rows get `pendingEdit: true`. */
  const applyTo = (point) => {
    const changes = getChanges(point.id)
    if (!changes) {
      return point
    }
    return {
      ...point,
      coordinates: { ...point.coordinates, ...changes.coordinates },
      velocity: changes.velocity,
      accuracy: changes.accuracy,
      pendingEdit: true
    }
  }

  const toRequestEdits = () =>
    Array.from(pendingEdits.entries()).map(([id, { changes }]) => ({ id, ...changes }))

  return {
    pendingEdits,
    pendingCount,
    hasPendingEdits,
    isPending,
    getChanges,
    getOriginal,
    stage,
    revert,
    revertAll,
    dropIds,
    applyTo,
    toRequestEdits
  }
}
