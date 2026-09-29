export const DEFAULT_NEARBY_GROUP_RADIUS_METERS = 15

export const distanceMeters = (a, b) => {
  const lat1 = a.latitude * Math.PI / 180
  const lat2 = b.latitude * Math.PI / 180
  const deltaLat = lat2 - lat1
  const deltaLng = (b.longitude - a.longitude) * Math.PI / 180
  const sinLat = Math.sin(deltaLat / 2)
  const sinLng = Math.sin(deltaLng / 2)
  const h = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLng * sinLng
  return 6371000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
}

export const findNearbyGroup = (groups, point, radiusMeters = DEFAULT_NEARBY_GROUP_RADIUS_METERS) => (
  groups.find((group) => distanceMeters(group, point) <= radiusMeters)
)

export const addToNearbyGroup = (group, point, count = 1) => {
  const currentCount = Math.max(Number(group.count || 0), 0)
  const nextCount = currentCount + count
  group.latitude = ((group.latitude * currentCount) + (point.latitude * count)) / nextCount
  group.longitude = ((group.longitude * currentCount) + (point.longitude * count)) / nextCount
  group.count = nextCount
}

const isFiniteCoordinate = (value) => typeof value === 'number' && Number.isFinite(value)

/**
 * Groups arbitrary items by GPS proximity (running-centroid merge, same
 * algorithm Photos already use), rather than requiring exact coordinate
 * matches. Item shape is left to the caller via the accessor functions.
 */
export const groupItemsByProximity = (items, {
  radiusMeters = DEFAULT_NEARBY_GROUP_RADIUS_METERS,
  getLatitude = (item) => item.latitude,
  getLongitude = (item) => item.longitude
} = {}) => {
  const groups = []

  ;(Array.isArray(items) ? items : []).forEach((item, index) => {
    const latitude = getLatitude(item)
    const longitude = getLongitude(item)

    if (!isFiniteCoordinate(latitude) || !isFiniteCoordinate(longitude)) {
      return
    }

    const point = { latitude, longitude }
    const group = findNearbyGroup(groups, point, radiusMeters)

    if (!group) {
      groups.push({
        latitude,
        longitude,
        count: 1,
        items: [item],
        indices: [index]
      })
      return
    }

    addToNearbyGroup(group, point)
    group.items.push(item)
    group.indices.push(index)
  })

  return groups
}
