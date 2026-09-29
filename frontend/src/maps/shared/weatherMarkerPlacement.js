import { circleIntersectsRect, groupByPixelDistance } from '@/maps/shared/crossTypeMarkerCollision'

// Weather markers are 28px circles (34px when highlighted).
const WEATHER_MARKER_RADIUS_PX = 14
const HIGHLIGHTED_WEATHER_MARKER_RADIUS_PX = 17
// Samples closer than this on screen share one marker.
export const WEATHER_GROUP_RADIUS_PX = 36

// Number(null) is 0, which would put a sample without coordinates at 0,0.
const toCoordinate = (value) => (value === null || value === undefined || value === '' ? NaN : Number(value))

const circlesIntersect = (a, b) => {
  const dx = a.x - b.x
  const dy = a.y - b.y
  return Math.sqrt((dx * dx) + (dy * dy)) < a.radius + b.radius
}

/**
 * Weather is context, not a place, so it has the lowest priority on the map:
 * nearby samples share one marker, and a weather marker that would overlap
 * anything else (stays/trips, photos, notes, combo chips, the highlighted
 * trip's endpoints) is not drawn at all - it is never merged into or drawn
 * over another marker. The same weather stays available in the timeline
 * sidebar, and reappears here once zooming in makes room for it.
 *
 * Samples of the highlighted timeline item group only with each other and win
 * over the other weather markers.
 *
 * `occupied` is computeCrossTypeCollisions()' { circles, rects } in screen px.
 * Returns the markers to draw: [{ indices, latitude, longitude, highlighted }].
 */
export const placeWeatherMarkers = ({
  samples = [],
  highlightedIndices = new Set(),
  occupied = { circles: [], rects: [] },
  mapInstance,
  radiusPx = WEATHER_GROUP_RADIUS_PX
}) => {
  if (!mapInstance || samples.length === 0) {
    return []
  }

  const entries = samples
    .map((sample, index) => ({
      index,
      latitude: toCoordinate(sample?.latitude),
      longitude: toCoordinate(sample?.longitude),
      highlighted: highlightedIndices.has(index)
    }))
    .filter((entry) => Number.isFinite(entry.latitude) && Number.isFinite(entry.longitude))
    .map((entry) => ({ ...entry, point: mapInstance.project([entry.longitude, entry.latitude]) }))

  const buildGroups = (highlighted) => groupByPixelDistance(
    entries.filter((entry) => entry.highlighted === highlighted),
    (entry) => entry.point,
    radiusPx
  ).map((members) => {
    const latitude = members.reduce((sum, member) => sum + member.latitude, 0) / members.length
    const longitude = members.reduce((sum, member) => sum + member.longitude, 0) / members.length
    const point = mapInstance.project([longitude, latitude])
    return {
      indices: members.map((member) => member.index),
      latitude,
      longitude,
      highlighted,
      circle: {
        x: point.x,
        y: point.y,
        radius: highlighted ? HIGHLIGHTED_WEATHER_MARKER_RADIUS_PX : WEATHER_MARKER_RADIUS_PX
      }
    }
  })

  const isClearOf = (circle, placed) => (
    !occupied.circles.some((other) => circlesIntersect(circle, other))
    && !occupied.rects.some((rect) => circleIntersectsRect(circle, circle.radius, rect))
    && !placed.some((other) => circlesIntersect(circle, other.circle))
  )

  const placed = []
  ;[...buildGroups(true), ...buildGroups(false)].forEach((group) => {
    if (isClearOf(group.circle, placed)) {
      placed.push(group)
    }
  })

  return placed.map(({ indices, latitude, longitude, highlighted }) => ({ indices, latitude, longitude, highlighted }))
}
