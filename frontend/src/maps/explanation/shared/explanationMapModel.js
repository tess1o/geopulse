// What the explanation dialog draws, independent of the map engine: the event's GPS points styled by whether the
// timeline used them, the points that made each decision, the stay radius and the lines between points.

import { toFiniteCoordinate } from '@/maps/shared/coordinateUtils'
import { getMapPopupVariantClassName } from '@/maps/shared/popups/mapPopupOptions'

export const EXPLANATION_MAP_COLORS = {
  used: '#2563eb',
  filtered: '#ea580c',
  context: '#94a3b8',
  deciding: '#7c3aed',
  radius: '#2563eb',
  path: '#2563eb',
  contextPath: '#94a3b8',
  gap: '#64748b',
  outline: '#ffffff'
}

const EARTH_RADIUS_METERS = 6371008.8
const ANCHOR_MIN_DISTANCE_METERS = 5

export const haversineMeters = (lat1, lng1, lat2, lng2) => {
  const toRad = (deg) => deg * Math.PI / 180
  const dLat = toRad(lat2 - lat1)
  const dLng = toRad(lng2 - lng1)
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2
  return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(a)))
}

/** A circle as a closed ring of [lng, lat] pairs, for engines that draw polygons rather than circles. */
export const circleRing = (latitude, longitude, radiusMeters, steps = 64) => {
  const ring = []
  const latRadius = (radiusMeters / EARTH_RADIUS_METERS) * (180 / Math.PI)
  const lngRadius = latRadius / Math.max(0.01, Math.cos(latitude * Math.PI / 180))
  for (let i = 0; i <= steps; i++) {
    const angle = (i / steps) * 2 * Math.PI
    ring.push([longitude + lngRadius * Math.cos(angle), latitude + latRadius * Math.sin(angle)])
  }
  return ring
}

const toLatLng = (value) => {
  const latitude = toFiniteCoordinate(value?.latitude)
  const longitude = toFiniteCoordinate(value?.longitude)
  return latitude === null || longitude === null ? null : [latitude, longitude]
}

const pointStyle = (point) => {
  if (point.part === 'EVENT') return point.used ? 'used' : 'filtered'
  return point.used ? 'context' : 'filtered'
}

/** [[south, west], [north, east]] around the given [lat, lng] pairs, or null. */
export const boundsOf = (coordinates) => {
  const valid = coordinates.filter(Boolean)
  if (!valid.length) return null
  let south = Infinity
  let west = Infinity
  let north = -Infinity
  let east = -Infinity
  valid.forEach(([lat, lng]) => {
    south = Math.min(south, lat)
    north = Math.max(north, lat)
    west = Math.min(west, lng)
    east = Math.max(east, lng)
  })
  return [[south, west], [north, east]]
}

const circleBoundsCoordinates = (circle) => {
  if (!circle) return []
  const latDelta = (circle.radiusMeters / EARTH_RADIUS_METERS) * (180 / Math.PI)
  const lngDelta = latDelta / Math.max(0.01, Math.cos(circle.latitude * Math.PI / 180))
  return [
    [circle.latitude - latDelta, circle.longitude - lngDelta],
    [circle.latitude + latDelta, circle.longitude + lngDelta]
  ]
}

/**
 * @param details what the explanation endpoint returns
 */
export const buildExplanationMapModel = (details) => {
  const points = (details?.points || [])
    .map((point, index) => {
      const coordinates = toLatLng(point)
      if (!coordinates) return null
      const reasons = point.reasons || []
      return {
        ...point,
        index,
        latitude: coordinates[0],
        longitude: coordinates[1],
        reasons,
        deciding: reasons.length > 0,
        style: pointStyle(point)
      }
    })
    .filter(Boolean)

  const usedIn = (part) => points.filter((point) => point.used && point.part === part)
  const eventUsed = usedIn('EVENT')
  const before = usedIn('BEFORE')
  const after = usedIn('AFTER')
  const toPath = (list) => list.map((point) => [point.latitude, point.longitude])

  const lines = []
  if (details?.kind === 'DATA_GAP') {
    const edges = [before[before.length - 1], after[0]].filter(Boolean)
    if (before.length > 1) lines.push({ type: 'context', coordinates: toPath(before) })
    if (after.length > 1) lines.push({ type: 'context', coordinates: toPath(after) })
    if (edges.length === 2) lines.push({ type: 'gap', coordinates: toPath(edges) })
  } else {
    const firstEvent = eventUsed[0]
    const lastEvent = eventUsed[eventUsed.length - 1]
    const beforeLine = [...before, firstEvent].filter(Boolean)
    const afterLine = [lastEvent, ...after].filter(Boolean)
    if (beforeLine.length > 1) lines.push({ type: 'context', coordinates: toPath(beforeLine) })
    if (eventUsed.length > 1) lines.push({ type: 'event', coordinates: toPath(eventUsed) })
    if (afterLine.length > 1) lines.push({ type: 'context', coordinates: toPath(afterLine) })
  }

  const centre = toLatLng(details?.centre)
  const circle = details?.kind === 'STAY' && centre && Number(details?.radiusMeters) > 0
    ? { latitude: centre[0], longitude: centre[1], radiusMeters: Number(details.radiusMeters) }
    : null

  const location = toLatLng(details?.location)
  const anchor = location && centre
    && haversineMeters(location[0], location[1], centre[0], centre[1]) > ANCHOR_MIN_DISTANCE_METERS
    ? { latitude: location[0], longitude: location[1] }
    : null

  // A trip with no points of its own (it connects two stays) is drawn as a dashed line between its ends
  const start = toLatLng(details?.startPoint)
  const end = toLatLng(details?.endPoint)
  if (details?.kind === 'TRIP' && eventUsed.length < 2 && start && end) {
    lines.push({ type: 'gap', coordinates: [start, end] })
  }

  const reasonIndexes = {}
  points.forEach((point) => {
    point.reasons.forEach((code) => {
      (reasonIndexes[code] ||= []).push(point.index)
    })
  })

  const focus = [
    ...points.filter((point) => point.part === 'EVENT' || point.deciding).map((point) => [point.latitude, point.longitude]),
    ...circleBoundsCoordinates(circle),
    ...lines.filter((line) => line.type === 'gap').flatMap((line) => line.coordinates),
    anchor ? [anchor.latitude, anchor.longitude] : null
  ]
  const bounds = boundsOf(focus) || boundsOf(points.map((point) => [point.latitude, point.longitude]))
    || (location ? boundsOf([location]) : null)

  return { points, lines, circle, anchor, reasonIndexes, bounds }
}

/** Bounds around the points with these indexes, for zooming to a step. */
export const boundsForIndexes = (model, indexes = []) => {
  const wanted = new Set(indexes)
  return boundsOf(model.points
    .filter((point) => wanted.has(point.index))
    .map((point) => [point.latitude, point.longitude]))
}

/**
 * How to draw a point: { radius, fillColor, fillOpacity, strokeColor, strokeWidth, strokeOpacity }.
 * @param state 'normal', 'selected' (part of the step being looked at) or 'dimmed' (another step is)
 */
export const pointPaint = (point, state = 'normal') => {
  const colors = EXPLANATION_MAP_COLORS
  const paint = {
    radius: point.style === 'context' ? 4 : 5,
    fillColor: colors[point.style] || colors.used,
    fillOpacity: point.style === 'filtered' ? 0.25 : 0.9,
    strokeColor: point.style === 'filtered' ? colors.filtered : colors.outline,
    strokeWidth: point.style === 'filtered' ? 2 : 1.5,
    strokeOpacity: 1
  }
  if (point.deciding) {
    paint.radius = 7
    paint.strokeColor = colors.deciding
    paint.strokeWidth = 3
  }
  if (state === 'selected') {
    paint.radius += 3
    paint.strokeColor = colors.deciding
    paint.strokeWidth = 4
    paint.fillOpacity = Math.max(paint.fillOpacity, 0.6)
  } else if (state === 'dimmed') {
    paint.fillOpacity *= 0.3
    paint.strokeOpacity = 0.3
  }
  return paint
}

/** Draw order: context below, then used, left-out and deciding points on top. */
export const pointsInDrawOrder = (points) => {
  const rank = (point) => (point.deciding ? 3 : { context: 0, used: 1, filtered: 2 }[point.style] ?? 1)
  return [...points].sort((a, b) => rank(a) - rank(b) || a.index - b.index)
}

export const pointState = (point, selectedIndexes) => {
  if (!selectedIndexes) return 'normal'
  return selectedIndexes.has(point.index) ? 'selected' : 'dimmed'
}

/** The popup container class: the shared map popup card, so it follows the light and dark theme. */
export const EXPLANATION_POPUP_CLASS_NAME = getMapPopupVariantClassName('compact', 'gp-explanation-point-popup-container')

/** Popup content for a point, from { title, lines } given by the dialog; text only. */
export const buildPointPopupElement = ({ title, lines = [] } = {}) => {
  const root = document.createElement('div')
  root.className = 'gp-explanation-point-popup'
  if (title) {
    const heading = document.createElement('div')
    heading.className = 'gp-explanation-point-popup__title'
    heading.textContent = title
    root.appendChild(heading)
  }
  lines.forEach((line) => {
    const row = document.createElement('div')
    row.className = 'gp-explanation-point-popup__line'
    row.textContent = line
    root.appendChild(row)
  })
  return root
}
