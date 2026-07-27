import RawGpsPointPopup from '@/maps/shared/popups/RawGpsPointPopup.vue'
import { mountMapPopup } from '@/maps/shared/popups/mountMapPopup'
import {
  getMapPopupVariantClassName,
  MAP_POPUP_MAX_WIDTH,
  MAP_POPUP_MAX_WIDTH_PX,
  MAP_POPUP_OFFSET
} from '@/maps/shared/popups/mapPopupOptions'

export const STATIONARY_GROUP_METERS = 25
const METERS_PER_DEGREE = 111320

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

const scheduleRenderNotification = (callback) => {
  if (typeof callback !== 'function') return

  const schedule = typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function'
    ? window.requestAnimationFrame.bind(window)
    : (scheduledCallback) => setTimeout(scheduledCallback, 0)

  schedule(callback)
}

export const createRawGpsPopupMount = (group, {
  timezone,
  resolveLocation,
  onRender
} = {}) => {
  let popupMount = null
  const notifyRender = () => {
    scheduleRenderNotification(() => onRender?.(popupMount?.element))
  }

  popupMount = mountMapPopup(RawGpsPointPopup, {
    group,
    timezone,
    resolveLocation,
    onRender: notifyRender
  })

  notifyRender()
  return popupMount
}

export const RAW_GPS_POPUP_CLASS_NAME = getMapPopupVariantClassName('wide', 'gp-raw-gps-popup-container')
export const RAW_GPS_POPUP_MAX_WIDTH = MAP_POPUP_MAX_WIDTH
export const RAW_GPS_POPUP_MAX_WIDTH_PX = MAP_POPUP_MAX_WIDTH_PX
export const RAW_GPS_POPUP_OFFSET = MAP_POPUP_OFFSET
