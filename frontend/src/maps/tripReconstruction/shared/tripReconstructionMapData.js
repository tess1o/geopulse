export const hasValidCoordinates = (lat, lon) => {
  return Number.isFinite(lat) && Number.isFinite(lon) && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180
}

export const getSegmentEndpoint = (segment, endpoint = 'end') => {
  if (!segment) return null

  if (segment.segmentType === 'STAY' && hasValidCoordinates(segment.latitude, segment.longitude)) {
    return { latitude: segment.latitude, longitude: segment.longitude }
  }

  if (segment.segmentType === 'TRIP' && Array.isArray(segment.waypoints) && segment.waypoints.length > 0) {
    const waypoint = endpoint === 'start'
      ? segment.waypoints[0]
      : segment.waypoints[segment.waypoints.length - 1]

    if (waypoint && hasValidCoordinates(waypoint.latitude, waypoint.longitude)) {
      return { latitude: waypoint.latitude, longitude: waypoint.longitude }
    }
  }

  return null
}

export const resolveSegmentReferenceCoordinate = (segments, segmentIndex) => {
  if (!Array.isArray(segments) || segmentIndex < 0 || segmentIndex >= segments.length) {
    return null
  }

  const current = segments[segmentIndex]
  const currentAnchor = getSegmentEndpoint(current, 'start')
  if (currentAnchor) {
    return currentAnchor
  }

  for (let index = segmentIndex - 1; index >= 0; index -= 1) {
    const previousAnchor = getSegmentEndpoint(segments[index], 'end')
    if (previousAnchor) {
      return previousAnchor
    }
  }

  for (let index = segmentIndex + 1; index < segments.length; index += 1) {
    const nextAnchor = getSegmentEndpoint(segments[index], 'start')
    if (nextAnchor) {
      return nextAnchor
    }
  }

  return null
}

export const buildContextPoints = (segments, activeIndex) => {
  if (!Array.isArray(segments) || activeIndex < 0) {
    return []
  }

  const points = []

  segments.forEach((segment, index) => {
    if (index === activeIndex) {
      return
    }

    if (segment.segmentType === 'STAY' && hasValidCoordinates(segment.latitude, segment.longitude)) {
      points.push({
        id: `ctx-stay-${segment.id}`,
        label: `S${index + 1}`,
        latitude: segment.latitude,
        longitude: segment.longitude
      })
      return
    }

    if (segment.segmentType !== 'TRIP' || !Array.isArray(segment.waypoints) || segment.waypoints.length === 0) {
      return
    }

    const start = segment.waypoints[0]
    const end = segment.waypoints[segment.waypoints.length - 1]

    if (start && hasValidCoordinates(start.latitude, start.longitude)) {
      points.push({
        id: `ctx-trip-start-${segment.id}`,
        label: `T${index + 1}S`,
        latitude: start.latitude,
        longitude: start.longitude
      })
    }

    if (
      end
      && hasValidCoordinates(end.latitude, end.longitude)
      && (end.latitude !== start?.latitude || end.longitude !== start?.longitude)
    ) {
      points.push({
        id: `ctx-trip-end-${segment.id}`,
        label: `T${index + 1}E`,
        latitude: end.latitude,
        longitude: end.longitude
      })
    }
  })

  return points
}

export const buildContextTripLines = (segments, activeIndex) => {
  if (!Array.isArray(segments) || activeIndex < 0) {
    return []
  }

  return segments
    .map((segment, index) => {
      if (index === activeIndex || segment.segmentType !== 'TRIP' || !Array.isArray(segment.waypoints)) {
        return []
      }

      return segment.waypoints
        .filter((waypoint) => hasValidCoordinates(waypoint.latitude, waypoint.longitude))
        .map((waypoint) => [waypoint.latitude, waypoint.longitude])
    })
    .filter((points) => points.length >= 2)
}

export const resolveActiveSegmentViewport = (segments, activeIndex) => {
  if (!Array.isArray(segments) || activeIndex < 0 || activeIndex >= segments.length) {
    return null
  }

  const segment = segments[activeIndex]

  if (segment.segmentType === 'STAY' && hasValidCoordinates(segment.latitude, segment.longitude)) {
    return {
      type: 'set-view',
      center: [segment.latitude, segment.longitude],
      zoom: 14
    }
  }

  if (segment.segmentType === 'TRIP' && Array.isArray(segment.waypoints) && segment.waypoints.length > 0) {
    const bounds = segment.waypoints
      .filter((waypoint) => hasValidCoordinates(waypoint.latitude, waypoint.longitude))
      .map((waypoint) => [waypoint.latitude, waypoint.longitude])

    if (bounds.length === 1) {
      return {
        type: 'set-view',
        center: bounds[0],
        zoom: 12
      }
    }

    if (bounds.length > 1) {
      return {
        type: 'fit-bounds',
        bounds,
        options: { padding: [30, 30], animate: true }
      }
    }
  }

  const reference = resolveSegmentReferenceCoordinate(segments, activeIndex)
  if (!reference) {
    return null
  }

  return {
    type: 'set-view',
    center: [reference.latitude, reference.longitude],
    zoom: 13
  }
}

export const computePreviewSummary = (segments) => {
  const safeSegments = Array.isArray(segments) ? segments : []
  const sorted = [...safeSegments]
    .filter((segment) => segment?.startTime instanceof Date && segment?.endTime instanceof Date)
    .sort((left, right) => left.startTime.getTime() - right.startTime.getTime())

  let coveredMinutes = 0
  let gapMinutes = 0
  let gapCount = 0
  let previousEnd = null

  sorted.forEach((segment) => {
    const segmentDurationMinutes = Math.max(0, Math.round((segment.endTime.getTime() - segment.startTime.getTime()) / 60000))
    coveredMinutes += segmentDurationMinutes

    if (previousEnd instanceof Date && segment.startTime > previousEnd) {
      gapCount += 1
      gapMinutes += Math.round((segment.startTime.getTime() - previousEnd.getTime()) / 60000)
    }

    if (!(previousEnd instanceof Date) || segment.endTime > previousEnd) {
      previousEnd = segment.endTime
    }
  })

  return {
    stays: safeSegments.filter((segment) => segment.segmentType === 'STAY').length,
    trips: safeSegments.filter((segment) => segment.segmentType === 'TRIP').length,
    coveredMinutes,
    gapMinutes,
    gapCount
  }
}

export const waypointLabel = (index, total) => {
  if (index === 0) return 'Start'
  if (index === total - 1) return 'End'
  return `W${index + 1}`
}

export const waypointTagSeverity = (index, total) => {
  if (index === 0) return 'success'
  if (index === total - 1) return 'danger'
  return 'info'
}
