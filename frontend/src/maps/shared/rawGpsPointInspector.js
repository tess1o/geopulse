import { formatSpeed } from '@/utils/calculationsHelpers'

export const STATIONARY_GROUP_METERS = 25
const METERS_PER_DEGREE = 111320

export const escapeHtml = (value) => {
  if (value == null) return ''
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;')
}

const toFiniteNumber = (value) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

const getPointLatitude = (point) => toFiniteNumber(point?.latitude ?? point?.lat)
const getPointLongitude = (point) => toFiniteNumber(point?.longitude ?? point?.lng ?? point?.lon)

const projectPointToMeters = (latitude, longitude) => {
  const latitudeRadians = latitude * Math.PI / 180
  return {
    x: longitude * METERS_PER_DEGREE * Math.max(0.1, Math.cos(latitudeRadians)),
    y: latitude * METERS_PER_DEGREE
  }
}

const getStationaryBucketCoordinates = ({ x, y }) => ({
  bucketX: Math.floor(x / STATIONARY_GROUP_METERS),
  bucketY: Math.floor(y / STATIONARY_GROUP_METERS)
})

const getStationaryBucketKey = (bucketX, bucketY) => `${bucketX}:${bucketY}`

const getProjectedDistanceMeters = (left, right) => {
  const deltaX = left.x - right.x
  const deltaY = left.y - right.y
  return Math.sqrt(deltaX * deltaX + deltaY * deltaY)
}

const addGroupToBucket = (groupsByBucket, group) => {
  if (!groupsByBucket.has(group.bucketKey)) {
    groupsByBucket.set(group.bucketKey, [])
  }
  groupsByBucket.get(group.bucketKey).push(group)
}

const removeGroupFromBucket = (groupsByBucket, group) => {
  const bucket = groupsByBucket.get(group.bucketKey)
  if (!bucket) return

  const index = bucket.indexOf(group)
  if (index !== -1) {
    bucket.splice(index, 1)
  }
  if (bucket.length === 0) {
    groupsByBucket.delete(group.bucketKey)
  }
}

const moveGroupToCurrentBucket = (groupsByBucket, group) => {
  const { bucketX, bucketY } = getStationaryBucketCoordinates(group.center)
  const nextBucketKey = getStationaryBucketKey(bucketX, bucketY)
  if (nextBucketKey === group.bucketKey) {
    return
  }

  removeGroupFromBucket(groupsByBucket, group)
  group.bucketKey = nextBucketKey
  addGroupToBucket(groupsByBucket, group)
}

const findNearbyStationaryGroup = (groupsByBucket, projectedPoint) => {
  const { bucketX, bucketY } = getStationaryBucketCoordinates(projectedPoint)
  let nearestGroup = null
  let nearestDistance = Number.POSITIVE_INFINITY

  for (let offsetX = -1; offsetX <= 1; offsetX += 1) {
    for (let offsetY = -1; offsetY <= 1; offsetY += 1) {
      const bucket = groupsByBucket.get(getStationaryBucketKey(bucketX + offsetX, bucketY + offsetY))
      if (!bucket) continue

      bucket.forEach((group) => {
        const distance = getProjectedDistanceMeters(projectedPoint, group.center)
        if (distance <= STATIONARY_GROUP_METERS && distance < nearestDistance) {
          nearestGroup = group
          nearestDistance = distance
        }
      })
    }
  }

  return nearestGroup
}

const addPointToGroup = (group, point, latitude, longitude, projectedPoint, groupsByBucket) => {
  group.points.push({
    ...point,
    latitude,
    longitude
  })
  group.sumLatitude += latitude
  group.sumLongitude += longitude
  group.sumX += projectedPoint.x
  group.sumY += projectedPoint.y

  const count = group.points.length
  group.latitude = group.sumLatitude / count
  group.longitude = group.sumLongitude / count
  group.center = {
    x: group.sumX / count,
    y: group.sumY / count
  }

  moveGroupToCurrentBucket(groupsByBucket, group)
}

export const groupRawGpsPoints = (points) => {
  const groups = []
  const groupsByBucket = new Map()

  ;(Array.isArray(points) ? points : []).forEach((point) => {
    const latitude = getPointLatitude(point)
    const longitude = getPointLongitude(point)
    if (latitude === null || longitude === null) {
      return
    }

    const projectedPoint = projectPointToMeters(latitude, longitude)
    const nearbyGroup = findNearbyStationaryGroup(groupsByBucket, projectedPoint)

    if (nearbyGroup) {
      addPointToGroup(nearbyGroup, point, latitude, longitude, projectedPoint, groupsByBucket)
      return
    }

    const { bucketX, bucketY } = getStationaryBucketCoordinates(projectedPoint)
    const bucketKey = getStationaryBucketKey(bucketX, bucketY)
    const group = {
      key: `${bucketKey}:${groups.length}`,
      bucketKey,
      center: projectedPoint,
      sumLatitude: 0,
      sumLongitude: 0,
      sumX: 0,
      sumY: 0,
      latitude,
      longitude,
      points: []
    }

    groups.push(group)
    addGroupToBucket(groupsByBucket, group)
    addPointToGroup(group, point, latitude, longitude, projectedPoint, groupsByBucket)
  })

  return groups.map((group) => {
    const sortedPoints = [...group.points].sort((left, right) => (
      new Date(left.timestamp || 0).getTime() - new Date(right.timestamp || 0).getTime()
    ))
    const representative = sortedPoints[0]
    const accuracies = sortedPoints.map((point) => Number(point.accuracy)).filter(Number.isFinite)
    const batteries = sortedPoints.map((point) => Number(point.battery)).filter(Number.isFinite)
    const speeds = sortedPoints.map((point) => Number(point.velocity)).filter(Number.isFinite)

    return {
      key: group.key,
      latitude: group.latitude,
      longitude: group.longitude,
      points: sortedPoints,
      count: sortedPoints.length,
      representative,
      firstTimestamp: sortedPoints[0]?.timestamp || null,
      lastTimestamp: sortedPoints[sortedPoints.length - 1]?.timestamp || null,
      accuracyMin: accuracies.length ? Math.min(...accuracies) : null,
      accuracyMax: accuracies.length ? Math.max(...accuracies) : null,
      batteryMin: batteries.length ? Math.min(...batteries) : null,
      batteryMax: batteries.length ? Math.max(...batteries) : null,
      speedMin: speeds.length ? Math.min(...speeds) : null,
      speedMax: speeds.length ? Math.max(...speeds) : null
    }
  })
}

export const formatRawGpsDateTime = (timezone, value) => {
  if (!value) return 'Unknown time'
  try {
    return `${timezone.formatDateDisplay(value)} ${timezone.formatTime(value, { withSeconds: true })}`
  } catch {
    return 'Unknown time'
  }
}

const formatCoordinate = (value) => {
  const number = Number(value)
  return Number.isFinite(number) ? number.toFixed(6) : 'N/A'
}

const formatMeters = (value) => {
  const number = Number(value)
  return Number.isFinite(number) ? `${Math.round(number)}m` : 'N/A'
}

const formatBattery = (value) => {
  const number = Number(value)
  return Number.isFinite(number) && number >= 0 ? `${Math.round(number)}%` : 'N/A'
}

const getSelectedPoint = (group, pointId) => {
  if (!group?.points?.length) return null
  if (pointId == null) return group.representative || group.points[0]
  return group.points.find((point) => String(point.id) === String(pointId)) || group.points[0]
}

const buildTelemetryItemHtml = (label, value) => `
  <span class="raw-gps-stack-telemetry-item">
    <span class="raw-gps-stack-telemetry-label">${escapeHtml(label)}</span>
    <span>${escapeHtml(value)}</span>
  </span>
`.trim()

const renderLocationText = (locationState) => {
  if (locationState?.status === 'resolved' && locationState?.location?.locationName) {
    const source = locationState.location.sourceType === 'favorite' ? 'Favorite' : 'Geocoding'
    return `
      <div class="raw-gps-popup-location-name">${escapeHtml(locationState.location.locationName)}</div>
      <div class="raw-gps-popup-location-source">${source}</div>
    `.trim()
  }

  if (locationState?.status === 'error') {
    return '<div class="raw-gps-popup-location-muted">Location unavailable</div>'
  }

  return '<div class="raw-gps-popup-location-muted">Finding location...</div>'
}

const buildPointRowsHtml = (group, selectedPoint, timezone) => {
  if (!group || group.count <= 1) return ''

  const visiblePoints = group.points.slice(0, 80)
  const rows = visiblePoints.map((point) => {
    const selected = String(point.id) === String(selectedPoint?.id)
    return `
      <button type="button" class="raw-gps-stack-row${selected ? ' is-selected' : ''}" data-point-id="${escapeHtml(point.id)}">
        <span class="raw-gps-stack-time">${escapeHtml(formatRawGpsDateTime(timezone, point.timestamp))}</span>
        <span class="raw-gps-stack-telemetry">
          ${buildTelemetryItemHtml('Speed', formatSpeed(point.velocity))}
          ${buildTelemetryItemHtml('Battery', formatBattery(point.battery))}
        </span>
      </button>
    `.trim()
  }).join('')

  const overflow = group.points.length > visiblePoints.length
    ? `<div class="raw-gps-stack-overflow">Showing first ${visiblePoints.length} of ${group.points.length} points</div>`
    : ''

  return `
    <div class="raw-gps-stack-list">
      ${rows}
      ${overflow}
    </div>
  `.trim()
}

const buildPopupHtml = (group, selectedPoint, timezone, locationState) => {
  const isStack = group.count > 1
  const title = isStack
    ? `${group.count} GPS points here`
    : formatRawGpsDateTime(timezone, selectedPoint?.timestamp)
  const subtitle = isStack
    ? `${formatRawGpsDateTime(timezone, group.firstTimestamp)} - ${formatRawGpsDateTime(timezone, group.lastTimestamp)}`
    : selectedPoint?.sourceType || 'Raw GPS point'

  return `
    <div class="raw-gps-popup">
      <div class="raw-gps-popup-title">${escapeHtml(title)}</div>
      <div class="raw-gps-popup-subtitle">${escapeHtml(subtitle)}</div>

      <div class="raw-gps-popup-grid">
        <div class="raw-gps-popup-label">Coordinates</div>
        <div>${escapeHtml(formatCoordinate(selectedPoint?.latitude))}<br>${escapeHtml(formatCoordinate(selectedPoint?.longitude))}</div>

        <div class="raw-gps-popup-label">Accuracy</div>
        <div>${escapeHtml(formatMeters(selectedPoint?.accuracy))}</div>

        <div class="raw-gps-popup-label">Battery</div>
        <div>${escapeHtml(formatBattery(selectedPoint?.battery))}</div>

        <div class="raw-gps-popup-label">Speed</div>
        <div>${escapeHtml(formatSpeed(selectedPoint?.velocity))}</div>

        <div class="raw-gps-popup-label">Altitude</div>
        <div>${escapeHtml(formatMeters(selectedPoint?.altitude))}</div>
      </div>

      <div class="raw-gps-popup-location">
        ${renderLocationText(locationState)}
      </div>

      ${buildPointRowsHtml(group, selectedPoint, timezone)}
    </div>
  `.trim()
}

export const createRawGpsPopupElement = (group, {
  timezone,
  resolveLocation,
  onRender
} = {}) => {
  const root = document.createElement('div')
  let selectedPoint = getSelectedPoint(group)
  const locationState = { status: 'loading', location: null }
  const scheduleRenderNotification = () => {
    if (typeof onRender !== 'function') return

    const schedule = typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function'
      ? window.requestAnimationFrame.bind(window)
      : (callback) => setTimeout(callback, 0)
    schedule(() => onRender(root))
  }

  const render = () => {
    root.innerHTML = buildPopupHtml(group, selectedPoint, timezone, locationState)

    root.querySelectorAll('.raw-gps-stack-row').forEach((button) => {
      button.addEventListener('click', (event) => {
        event.preventDefault()
        event.stopPropagation()
        selectedPoint = getSelectedPoint(group, button.dataset.pointId)
        render()
      })
    })

    scheduleRenderNotification()
  }

  render()

  if (typeof resolveLocation === 'function') {
    Promise.resolve(resolveLocation(group.representative || selectedPoint))
      .then((location) => {
        locationState.status = 'resolved'
        locationState.location = location
        render()
      })
      .catch(() => {
        locationState.status = 'error'
        render()
      })
  } else {
    locationState.status = 'error'
    render()
  }

  root.addEventListener('click', (event) => event.stopPropagation())
  root.addEventListener('mousedown', (event) => event.stopPropagation())

  return root
}

export const RAW_GPS_POPUP_CLASS_NAME = 'gp-raw-gps-popup-container'
export const RAW_GPS_POPUP_MAX_WIDTH = 'min(430px, calc(100vw - 32px))'
export const RAW_GPS_POPUP_MAX_WIDTH_PX = 430
export const RAW_GPS_POPUP_OFFSET = 14
