/**
 * Popup model for a planned stop on the map.
 *
 * Follows the same shape as the timeline popup models so it can be handed straight to
 * `mountMapPopup` / `MapInfoPopup`. Kept as a pure function so it is testable without
 * mounting a map.
 */

const DEFAULT_STOP_TITLE = 'Planned stop'

const formatPlannedDay = (plannedDay, timezone) => {
  if (!plannedDay) {
    return 'No day set'
  }
  if (timezone?.formatDateDisplay) {
    return timezone.formatDateDisplay(plannedDay)
  }
  return String(plannedDay)
}

const resolveVisitState = (item) => {
  if (item?.manualOverrideState === 'REJECTED') {
    return 'Not visited (manual)'
  }
  if (item?.isVisited) {
    const confidence = Number(item.visitConfidence)
    return Number.isFinite(confidence)
      ? `Visited · ${Math.round(confidence * 100)}% confidence`
      : 'Visited'
  }
  return 'Not visited yet'
}

export const buildTripPlanItemPopupModel = (item, deps = {}) => {
  if (!item) {
    return { title: DEFAULT_STOP_TITLE, variant: 'compact' }
  }

  const isMust = String(item.priority || '').toUpperCase() === 'MUST'
  // Map markers carry the stop as `name`, while the plan records it as `title`.
  const title = item.title || item.name || DEFAULT_STOP_TITLE

  return {
    title,
    subtitle: formatPlannedDay(item.plannedDay, deps.timezone),
    // Notes are the only free-text a stop carries, so they become the description.
    description: item.notes || '',
    iconClass: isMust ? 'pi pi-star-fill' : 'pi pi-map-marker',
    // Becomes the stop's photo once plan items link to a POI. The popup already renders
    // an avatar, so nothing needs to change at this end when that lands.
    avatarUrl: item.imageUrl || '',
    avatarAlt: title,
    // No coordinates row: the marker is already at that position, so the numbers told
    // the user nothing and were the longest value in the card.
    rows: [
      { label: 'Priority', value: isMust ? 'Must' : 'Optional' },
      { label: 'Status', value: resolveVisitState(item) }
    ],
    variant: 'compact'
  }
}
