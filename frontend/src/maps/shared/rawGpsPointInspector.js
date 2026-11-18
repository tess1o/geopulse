import { createNativeClusterMirror } from '@/maps/vector/utils/nativeClusterMirror'
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

export const createRawGpsInspectionClusters = () => {
  const clusters = createNativeClusterMirror({ clusterRadius: 34, clusterMaxZoom: 24 })
  return {
    getRenderedEntities(points, zoom) {
      return clusters.getRenderedEntities(points, zoom).map(entity => ({
        ...entity,
        // Use recorded coordinates rather than Supercluster's rounded projected coordinates.
        latitude: entity.indices.reduce((sum, index) => sum + points[index].latitude, 0) / entity.indices.length,
        longitude: entity.indices.reduce((sum, index) => sum + points[index].longitude, 0) / entity.indices.length
      }))
    }
  }
}

// Inner clusters up to this size fan out; larger ones keep splitting as the map zooms in.
export const RAW_GPS_FAN_LIMIT = 30
const FAN_CIRCLE_MAX = 8
const FAN_CIRCLE_SEPARATION = 30
const FAN_SPIRAL_START = 16
const FAN_SPIRAL_SEPARATION = 30
const FAN_SPIRAL_GROWTH = 6 * Math.PI * 2

/**
 * Screen-pixel offsets that spread `count` markers around a shared centre: a circle for
 * a few markers, a spiral beyond that, so each one stays clickable however close they were recorded.
 */
export const fanOutPixelOffsets = (count) => {
  if (count < 2) return count === 1 ? [{ x: 0, y: 0 }] : []
  if (count <= FAN_CIRCLE_MAX) {
    const radius = (FAN_CIRCLE_SEPARATION * (2 + count)) / (2 * Math.PI)
    const step = (2 * Math.PI) / count
    return Array.from({ length: count }, (_, index) => {
      const angle = -Math.PI / 2 + index * step
      return { x: radius * Math.cos(angle), y: radius * Math.sin(angle) }
    })
  }
  const offsets = []
  let length = FAN_SPIRAL_START
  let angle = 0
  for (let index = 0; index < count; index += 1) {
    angle += FAN_SPIRAL_SEPARATION / length + index * 0.0005
    offsets.push({ x: length * Math.cos(angle), y: length * Math.sin(angle) })
    length += FAN_SPIRAL_GROWTH / angle
  }
  return offsets
}

/**
 * Turns the inspection clusters of an opened group into what the map draws. Clusters of up to
 * RAW_GPS_FAN_LIMIT points, and every cluster once the map cannot zoom in further, are fanned out
 * around their centre, one marker per point, each with a leg back to where it was recorded.
 * `project` and `unproject` convert {latitude, longitude} to screen pixels {x, y} and back.
 */
export const layoutRawGpsInspection = (entities, points, { project, unproject, canZoomIn = true, fanLimit = RAW_GPS_FAN_LIMIT } = {}) => {
  const markers = []
  const legs = []
  const hubs = []
  ;(Array.isArray(entities) ? entities : []).forEach((entity) => {
    if (!entity.isCluster || (canZoomIn && entity.indices.length > fanLimit) || typeof project !== 'function' || typeof unproject !== 'function') {
      markers.push({ ...entity, fanned: false })
      return
    }
    const centre = project({ latitude: entity.latitude, longitude: entity.longitude })
    const indices = [...entity.indices].sort((left, right) => left - right)
    const offsets = fanOutPixelOffsets(indices.length)
    hubs.push({ latitude: entity.latitude, longitude: entity.longitude })
    indices.forEach((index, position) => {
      const { latitude, longitude } = unproject({ x: centre.x + offsets[position].x, y: centre.y + offsets[position].y })
      const recorded = points[index]
      markers.push({ indices: [index], latitude, longitude, isCluster: false, fanned: true })
      legs.push([[recorded.latitude, recorded.longitude], [latitude, longitude]])
    })
  })
  return { markers, legs, hubs }
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
  container,
  resolveLocation,
  onSelectPoint,
  onInspectPoint,
  activePointIndex = 0,
  onRender
} = {}) => {
  let popupMount = null
  const notifyRender = () => {
    scheduleRenderNotification(() => onRender?.(popupMount?.element))
  }

  const popupProps = {
    group,
    timezone,
    resolveLocation,
    onSelectPoint,
    activePointIndex,
    embedded: Boolean(container),
    onInspectPoint: (pointIndex) => {
      if (!Number.isInteger(pointIndex) || !group.points[pointIndex]) return
      popupProps.activePointIndex = pointIndex
      popupMount.updateProps(popupProps)
      onInspectPoint?.(group.points[pointIndex], pointIndex)
    },
    onRender: notifyRender
  }
  popupMount = mountMapPopup(RawGpsPointPopup, popupProps)
  container?.appendChild(popupMount.element)

  notifyRender()
  return {
    ...popupMount,
    unmount() {
      popupMount.unmount()
      popupMount.element.remove()
    },
    inspectPoint: popupProps.onInspectPoint,
    setSelectPoint(callback) {
      popupProps.onSelectPoint = callback
      popupMount.updateProps(popupProps)
    }
  }
}

export const RAW_GPS_POPUP_CLASS_NAME = getMapPopupVariantClassName('wide', 'gp-raw-gps-popup-container')
export const RAW_GPS_POPUP_MAX_WIDTH = MAP_POPUP_MAX_WIDTH
export const RAW_GPS_POPUP_MAX_WIDTH_PX = MAP_POPUP_MAX_WIDTH_PX
export const RAW_GPS_POPUP_OFFSET = MAP_POPUP_OFFSET
