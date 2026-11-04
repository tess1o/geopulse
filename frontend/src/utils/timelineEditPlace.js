import { haversineDistanceMeters } from '@/utils/geoDistance'

// A place in a timeline edit is { favoriteId, geocodingId, latitude, longitude, locationName, source }: a favorite or
// known (geocoded) place, or a new place made of a point and a name.

export function stayPlace(stay) {
  return {
    favoriteId: stay.favoriteId ?? null,
    geocodingId: stay.geocodingId ?? null,
    latitude: stay.latitude ?? null,
    longitude: stay.longitude ?? null,
    locationName: stay.locationName,
    source: stay.favoriteId ? 'favorite' : stay.geocodingId ? 'known' : 'custom'
  }
}

/**
 * Edit payload fields for a place. A favorite or known place is sent by reference only, so the server stores its
 * current name and coordinates; `prefix` names the place of a split stay's second part ("right").
 */
export function placePayload(place, prefix = '') {
  const key = name => prefix ? `${prefix}${name[0].toUpperCase()}${name.slice(1)}` : name
  if (place?.favoriteId) return { [key('favoriteId')]: place.favoriteId }
  if (place?.geocodingId) return { [key('geocodingId')]: place.geocodingId }
  return {
    [key('locationName')]: place?.locationName,
    [key('latitude')]: place?.latitude,
    [key('longitude')]: place?.longitude
  }
}

export function placeKey(place) {
  if (place?.favoriteId) return `favorite:${place.favoriteId}`
  if (place?.geocodingId) return `geocoding:${place.geocodingId}`
  return `point:${place?.latitude}:${place?.longitude}:${place?.locationName}`
}

export function samePlace(first, second) {
  return Boolean(first && second) && placeKey(first) === placeKey(second)
}

export function canMergeTimelineEvents(first, second) {
  if (!first || !second || first.type !== second.type) return false
  if (first.type === 'trip') return !first.manualRoute && !second.manualRoute && first.movementType === second.movementType
  if (first.type !== 'stay') return true
  if (first.favoriteId != null || second.favoriteId != null) return first.favoriteId != null && first.favoriteId === second.favoriteId
  if (first.geocodingId != null || second.geocodingId != null) return first.geocodingId != null && first.geocodingId === second.geocodingId
  return haversineDistanceMeters(first, second) <= 200
}
