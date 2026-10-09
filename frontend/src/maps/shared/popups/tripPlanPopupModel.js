/**
 * Popup model for a planned stop on the map.
 *
 * Follows the same shape as the timeline popup models so it can be handed straight to
 * `mountMapPopup` / `MapInfoPopup`. Kept as a pure function so it is testable without
 * mounting a map.
 */

import { t } from '@/locales'

const formatPlannedDay = (plannedDay, timezone) => {
  if (!plannedDay) {
    return t('maps.popups.tripPlan.noDaySet')
  }
  // plannedDay is a calendar date (YYYY-MM-DD): format it without timezone conversion, which would show the
  // previous day for users west of UTC.
  if (timezone?.formatCalendarDateDisplay) {
    return timezone.formatCalendarDateDisplay(plannedDay)
  }
  return String(plannedDay)
}

const resolveVisitState = (item) => {
  if (item?.manualOverrideState === 'REJECTED') {
    return t('maps.popups.tripPlan.notVisitedManual')
  }
  if (item?.isVisited) {
    const confidence = Number(item.visitConfidence)
    return Number.isFinite(confidence)
      ? t('maps.popups.tripPlan.visitedWithConfidence', { confidence: Math.round(confidence * 100) })
      : t('maps.popups.tripPlan.visited')
  }
  return t('maps.popups.tripPlan.notVisitedYet')
}

export const buildTripPlanItemPopupModel = (item, deps = {}) => {
  if (!item) {
    return { title: t('maps.popups.tripPlan.plannedStop'), variant: 'compact' }
  }

  const isMust = String(item.priority || '').toUpperCase() === 'MUST'
  // Map markers carry the stop as `name`, while the plan records it as `title`.
  const title = item.title || item.name || t('maps.popups.tripPlan.plannedStop')

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
      { label: t('maps.popups.tripPlan.priority'), value: isMust ? t('maps.popups.tripPlan.must') : t('maps.popups.tripPlan.optional') },
      { label: t('maps.popups.common.status'), value: resolveVisitState(item) }
    ],
    variant: 'compact'
  }
}
