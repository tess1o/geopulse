// Turns what GET /timeline/{stays|trips|data-gaps}/{id}/explanation returns into the steps the explanation dialog
// shows: how the event started, what was decided on the way and how it ended, each with the time it happened, the GPS
// points behind it and the settings that decided it.

import { TIMELINE_PREFERENCE_TAB_BY_KEY } from '@/constants/timelinePreferencesMetadata'

export const KIND_KEYS = { STAY: 'stay', TRIP: 'trip', DATA_GAP: 'dataGap' }

/** Reasons that mean something out of the ordinary happened; the dialog points them out above the map. */
const NOTABLE_CODES = new Set([
  'SHORT_STOP',
  'INFERRED_DURING_GAP',
  'INFERRED_TRIP_DURING_GAP',
  'STOPPED_BEFORE_GAP',
  'CONTINUITY',
  'FAVORITE_AREA',
  'CONTINUED_OVER_GAP',
  'MERGED',
  'JOINED_SEGMENTS',
  'MANUAL_SPLIT',
  'MANUAL_GAP_STAY'
])

/** Settings that decide each reason, by name as the timeline preferences page knows them. */
const SETTINGS_BY_CODE = {
  ARRIVAL: ['staypointVelocityThreshold', 'tripArrivalMinPoints'],
  CONFIRMED: ['staypointRadiusMeters', 'staypointMinDurationMinutes'],
  MOVED_AWAY: ['staypointRadiusMeters'],
  LEFT_STAY: ['staypointRadiusMeters'],
  SHORT_STOP: ['staypointMinDurationMinutes'],
  FAVORITE_AREA: ['staypointRadiusMeters'],
  MERGED: ['mergeMaxDistanceMeters', 'mergeMaxTimeGapMinutes'],
  NO_POINTS: ['dataGapThresholdSeconds'],
  NO_POINTS_SINCE_LAST: ['dataGapThresholdSeconds'],
  DATA_GAP: ['dataGapThresholdSeconds'],
  AFTER_DATA_GAP: ['dataGapThresholdSeconds'],
  CONTINUED_OVER_GAP: ['dataGapThresholdSeconds'],
  INFERRED_DURING_GAP: ['gapStayInferenceEnabled'],
  STOPPED_BEFORE_GAP: ['gapStayInferenceEnabled'],
  INFERRED_TRIP_DURING_GAP: ['gapTripInferenceEnabled']
}

const SECTIONS = ['start', 'notes', 'end']

const variantCode = (reason) => {
  if (reason.code === 'WATER_TRANSITION') {
    return reason.params?.toWater ? 'WATER_TRANSITION_TO_WATER' : 'WATER_TRANSITION_TO_LAND'
  }
  return reason.code
}

const reasonsOf = (explanation) => {
  if (!explanation) return []
  const reasons = []
  for (const section of SECTIONS) {
    const list = section === 'notes' ? (explanation.notes || []) : [explanation[section]].filter(Boolean)
    list.forEach((reason) => {
      if (reason?.code) {
        reasons.push({ section: section === 'notes' ? 'note' : section, reason })
      }
    })
  }
  return reasons
}

export const settingLink = (key, settings = {}) => ({
  key,
  labelKey: `timelinePreferences.page.settings.${key}`,
  value: settings?.[key] ?? null,
  route: {
    path: '/app/timeline/preferences',
    query: { tab: TIMELINE_PREFERENCE_TAB_BY_KEY[key] || 'staypoints', setting: key }
  }
})

const indexesWithReason = (points, code) => points
  .map((point, index) => (point.reasons || []).includes(code) ? index : -1)
  .filter((index) => index >= 0)

/**
 * The steps of the explanation, start first. Each step: { key, section, code, notable, params, pointIndexes, settings }.
 * {@code params} has the reason's own values plus {@code time} and {@code endTime} of its first and last point and
 * {@code count} of its points, so a sentence can say "at 18:03".
 */
export const buildExplanationSteps = (details) => {
  if (!details) return []
  const points = details.points || []
  return reasonsOf(details.explanation).map(({ section, reason }, position) => {
    const pointIndexes = indexesWithReason(points, reason.code)
    const first = points[pointIndexes[0]]
    const last = points[pointIndexes[pointIndexes.length - 1]]
    const fallbackTime = section === 'end' ? details.endTime : details.startTime
    return {
      key: `${section}-${position}`,
      section,
      code: variantCode(reason),
      notable: NOTABLE_CODES.has(reason.code),
      params: {
        ...(reason.params || {}),
        time: first?.timestamp || fallbackTime,
        endTime: last?.timestamp || first?.timestamp || fallbackTime,
        count: pointIndexes.length
      },
      pointIndexes,
      settings: (SETTINGS_BY_CODE[reason.code] || []).map((key) => settingLink(key, details.settings))
    }
  })
}

/**
 * Things to point out above the map: unusual decisions, points inside a data gap that were left out for their
 * accuracy, and settings changed since the timeline was built.
 */
export const buildExplanationCallouts = (details, steps = buildExplanationSteps(details)) => {
  if (!details) return []
  const callouts = steps.filter((step) => step.notable).map((step) => ({
    key: step.key,
    type: 'step',
    code: step.code,
    params: step.params,
    severity: 'warn',
    stepKey: step.key
  }))

  const filteredInside = details.filtered?.inEvent || 0
  if (details.kind === 'DATA_GAP' && filteredInside > 0) {
    callouts.push({
      key: 'gap-filtered',
      type: 'gapFiltered',
      params: { count: filteredInside, thresholdMeters: details.filtered?.thresholdMeters },
      severity: 'warn'
    })
  }

  for (const change of details.settingsChanged || []) {
    callouts.push({
      key: `changed-${change.setting}`,
      type: 'settingChanged',
      params: { setting: change.setting, builtWith: change.builtWith, current: change.current },
      setting: settingLink(change.setting, details.settings),
      severity: 'info'
    })
  }
  return callouts
}

/** Counts for the "points left out" section. */
export const summarizePoints = (details) => {
  const points = details?.points || []
  const inEvent = points.filter((point) => point.part === 'EVENT')
  return {
    eventTotal: details?.totalEventPoints ?? inEvent.length,
    eventUsed: inEvent.filter((point) => point.used).length,
    eventFiltered: details?.filtered?.inEvent ?? inEvent.filter((point) => !point.used).length,
    contextFiltered: details?.filtered?.inContext ?? 0,
    thresholdMeters: details?.filtered?.thresholdMeters ?? null,
    worstAccuracyMeters: inEvent
      .filter((point) => !point.used && Number.isFinite(point.accuracyMeters))
      .reduce((worst, point) => Math.max(worst, point.accuracyMeters), 0) || null
  }
}

/**
 * Why a single point on the map is as it is, as translation keys with values: { key, params }[].
 */
export const describePointStatus = (point, details) => {
  if (!point) return []
  const lines = []
  if (!point.used) {
    lines.push({
      key: 'timeline.explanationDialog.point.filtered',
      params: { accuracyMeters: point.accuracyMeters, thresholdMeters: details?.filtered?.thresholdMeters }
    })
  } else if (point.part === 'EVENT') {
    lines.push({ key: 'timeline.explanationDialog.point.used', params: {} })
  } else {
    lines.push({ key: `timeline.explanationDialog.point.${point.part === 'BEFORE' ? 'before' : 'after'}`, params: {} })
  }
  if (point.outsideRadius && point.used) {
    lines.push({
      key: 'timeline.explanationDialog.point.outsideRadius',
      params: { distanceMeters: point.distanceFromCentreMeters, radiusMeters: details?.radiusMeters }
    })
  }
  for (const code of point.reasons || []) {
    lines.push({ key: `timeline.explanationDialog.point.reason.${code}`, params: {} })
  }
  return lines
}

/**
 * Values in user units: distances, speeds and durations through the given formatters, minutes as "7 min", flags as
 * on/off, anything else as it is.
 */
export const formatExplanationValue = (name, value, formatters) => {
  const { formatDistance, formatDuration, formatSpeed, formatTime } = formatters
  if (value === null || value === undefined || value === '') return ''
  if (typeof value === 'boolean' || name.endsWith('Enabled')) {
    return formatters.t(value ? 'timeline.explanationDialog.on' : 'timeline.explanationDialog.off')
  }
  if (name === 'time' || name === 'endTime' || name === 'start') return formatTime(value)
  if (name.endsWith('Seconds')) return formatDuration(Number(value))
  if (name.endsWith('Minutes')) return formatters.t('timeline.explanationDialog.minutes', { value: Number(value) })
  if (name.endsWith('Kmh') || name === 'staypointVelocityThreshold') return formatSpeed(Number(value))
  if (name.endsWith('Meters') || name === 'staypointMaxAccuracyThreshold') return formatDistance(Number(value))
  return value
}

export const formatExplanationParams = (params = {}, formatters) => {
  const formatted = {}
  for (const [name, value] of Object.entries(params)) {
    formatted[name] = formatExplanationValue(name, value, formatters)
  }
  return formatted
}

/** The step as a sentence, or null when this frontend has no sentence for its code. */
export const explanationStepText = (step, kind, formatters) => {
  const { t, te } = formatters
  const keys = [
    `timeline.explanationDialog.steps.${kind}.${step.section}.${step.code}`,
    `timeline.explanationDialog.steps.any.${step.section}.${step.code}`
  ]
  const key = keys.find((candidate) => te(candidate))
  return key ? t(key, formatExplanationParams(step.params, formatters)) : null
}
