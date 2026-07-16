import { hasPhotoThumbnail } from '@/utils/immichPhotoThumbnailMeta'

const PHOTO_MARKER_KEY_FACTOR = 10000
const NEARBY_PHOTO_GROUP_RADIUS_METERS = 15

export const getPhotoMarkerKey = (latitude, longitude) => {
  const roundedLat = Math.round(latitude * PHOTO_MARKER_KEY_FACTOR) / PHOTO_MARKER_KEY_FACTOR
  const roundedLng = Math.round(longitude * PHOTO_MARKER_KEY_FACTOR) / PHOTO_MARKER_KEY_FACTOR
  return `${roundedLat},${roundedLng}`
}

const distanceMeters = (a, b) => {
  const lat1 = a.latitude * Math.PI / 180
  const lat2 = b.latitude * Math.PI / 180
  const deltaLat = lat2 - lat1
  const deltaLng = (b.longitude - a.longitude) * Math.PI / 180
  const sinLat = Math.sin(deltaLat / 2)
  const sinLng = Math.sin(deltaLng / 2)
  const h = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLng * sinLng
  return 6371000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
}

const getPhotoGroupCount = (group) => {
  return Math.max(group.count || group.photos?.length || 1, 1)
}

const findNearbyGroup = (groups, point) => {
  return groups.find((group) => distanceMeters(group, point) <= NEARBY_PHOTO_GROUP_RADIUS_METERS)
}

const addToNearbyGroup = (group, point, count = 1) => {
  const currentCount = Math.max(Number(group.count || 0), 0)
  const nextCount = currentCount + count
  group.latitude = ((group.latitude * currentCount) + (point.latitude * count)) / nextCount
  group.longitude = ((group.longitude * currentCount) + (point.longitude * count)) / nextCount
  group.count = nextCount
}

const getMarkerGroupCount = (markerGroup) => {
  if (Number.isFinite(markerGroup.count)) {
    return Number(markerGroup.count)
  }

  if (Number.isFinite(markerGroup.photoCount)) {
    return Number(markerGroup.photoCount)
  }

  return Array.isArray(markerGroup.photos) ? markerGroup.photos.length : 1
}

const getMarkerGroupPhotos = (markerGroup, count) => {
  if (Array.isArray(markerGroup.photos) && markerGroup.photos.length > 0) {
    return markerGroup.photos
  }

  if (count === 1 && markerGroup.singlePhoto) {
    return [markerGroup.singlePhoto]
  }

  return []
}

export const buildPhotoGroupsFromPhotos = (photos) => {
  const groups = []

  ;(Array.isArray(photos) ? photos : []).forEach((photo, index) => {
    if (typeof photo?.latitude !== 'number' || typeof photo?.longitude !== 'number') {
      return
    }

    const point = {
      latitude: photo.latitude,
      longitude: photo.longitude
    }
    const group = findNearbyGroup(groups, point)

    if (!group) {
      groups.push({
        latitude: photo.latitude,
        longitude: photo.longitude,
        count: 1,
        photos: [photo],
        indices: [index]
      })
      return
    }

    addToNearbyGroup(group, point)
    group.photos.push(photo)
    group.indices.push(index)
  })

  return groups.map((group) => ({
    ...group,
    markerKey: getPhotoMarkerKey(group.latitude, group.longitude),
    count: group.photos.length
  }))
}

export const normalizePhotoMarkerGroups = (markerGroups = []) => {
  if (!Array.isArray(markerGroups)) {
    return []
  }

  const groups = []

  markerGroups
    .filter((group) => typeof group?.latitude === 'number' && typeof group?.longitude === 'number')
    .forEach((markerGroup) => {
      const count = getMarkerGroupCount(markerGroup)
      const photos = getMarkerGroupPhotos(markerGroup, count)
      const point = {
        latitude: markerGroup.latitude,
        longitude: markerGroup.longitude
      }
      const normalized = {
        latitude: markerGroup.latitude,
        longitude: markerGroup.longitude,
        photos,
        indices: Array.isArray(markerGroup.indices) ? markerGroup.indices : photos.map((_, index) => index),
        markerKey: markerGroup.markerKey || getPhotoMarkerKey(markerGroup.latitude, markerGroup.longitude),
        count,
        childMarkerGroups: [markerGroup]
      }
      const group = findNearbyGroup(groups, point)

      if (!group) {
        groups.push(normalized)
        return
      }

      addToNearbyGroup(group, point, count)
      group.photos.push(...normalized.photos)
      group.indices.push(...normalized.indices)
      group.childMarkerGroups.push(markerGroup)
      group.markerKey = group.childMarkerGroups.length === 1
        ? normalized.markerKey
        : `nearby:${group.childMarkerGroups.map((child) => child.markerKey || getPhotoMarkerKey(child.latitude, child.longitude)).join('|')}`
    })

  return groups
}

export const buildPhotoMarkerClickPayload = (group) => {
  const count = getPhotoGroupCount(group)
  const markerGroup = {
    latitude: group.latitude,
    longitude: group.longitude,
    markerKey: group.markerKey,
    count,
    childMarkerGroups: group.childMarkerGroups
  }

  if (Array.isArray(group.photos) && group.photos.length > 0) {
    return {
      photos: group.photos,
      indices: group.indices,
      initialIndex: 0,
      markerGroup
    }
  }

  return { markerGroup }
}

export const getPhotoMarkerCount = getPhotoGroupCount

export const getSinglePhotoForThumbnail = (group) => {
  const count = getPhotoGroupCount(group)
  if (count !== 1 || !Array.isArray(group?.photos) || group.photos.length !== 1) {
    return null
  }

  const [photo] = group.photos
  return hasPhotoThumbnail(photo) ? photo : null
}
