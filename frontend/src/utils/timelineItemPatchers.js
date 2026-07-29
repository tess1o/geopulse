const idsMatch = (left, right) => {
  if (left === null || left === undefined || right === null || right === undefined) {
    return false
  }
  return String(left) === String(right)
}

export const applyTripMovementUpdateToTimelineItems = (items, updatedTrip) => {
  if (!Array.isArray(items) || !updatedTrip?.tripId) {
    return items
  }

  let changed = false
  const nextItems = items.map((item) => {
    if (item?.type !== 'trip' || !idsMatch(item.id, updatedTrip.tripId)) {
      return item
    }

    changed = true
    return {
      ...item,
      movementType: updatedTrip.movementType,
      movementTypeSource: updatedTrip.movementTypeSource
    }
  })

  return changed ? nextItems : items
}

export const applyStayFavoriteUpdateToTimelineItems = (items, updatedFavorite) => {
  if (!Array.isArray(items) || !updatedFavorite?.id) {
    return items
  }

  let changed = false
  const nextItems = items.map((item) => {
    if (item?.type !== 'stay' || !idsMatch(item.favoriteId, updatedFavorite.id)) {
      return item
    }

    changed = true
    return {
      ...item,
      locationName: updatedFavorite.name ?? item.locationName,
      city: updatedFavorite.city ?? null,
      country: updatedFavorite.country ?? null
    }
  })

  return changed ? nextItems : items
}

export const applyStayGeocodingUpdateToTimelineItems = (items, oldGeocodingId, updatedGeocoding) => {
  if (!Array.isArray(items) || !oldGeocodingId || !updatedGeocoding?.id) {
    return items
  }

  let changed = false
  const nextItems = items.map((item) => {
    if (item?.type !== 'stay' || !idsMatch(item.geocodingId, oldGeocodingId)) {
      return item
    }

    changed = true
    return {
      ...item,
      geocodingId: updatedGeocoding.id,
      locationName: updatedGeocoding.displayName ?? item.locationName,
      city: updatedGeocoding.city ?? null,
      country: updatedGeocoding.country ?? null
    }
  })

  return changed ? nextItems : items
}
