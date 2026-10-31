import { haversineDistanceMeters } from '@/utils/geoDistance'

const FALLBACK_ASPECT_RATIO = 4 / 3

export const getPhotoAspectRatio = (photo) => {
  const width = Number(photo?.width)
  const height = Number(photo?.height)
  return width > 0 && height > 0 ? width / height : FALLBACK_ASPECT_RATIO
}

const getFaceCoverage = (photo) => {
  const width = Number(photo?.width)
  const height = Number(photo?.height)
  const faces = Array.isArray(photo?.faces) ? photo.faces : []
  if (width <= 0 || height <= 0 || faces.length === 0) return null

  return Math.min(1, faces.reduce((coverage, face) => {
    const faceWidth = Math.abs(Number(face?.boundingBoxX2) - Number(face?.boundingBoxX1))
    const faceHeight = Math.abs(Number(face?.boundingBoxY2) - Number(face?.boundingBoxY1))
    const normalizedArea = faceWidth <= 1 && faceHeight <= 1
      ? faceWidth * faceHeight
      : (faceWidth * faceHeight) / (width * height)
    return coverage + (Number.isFinite(normalizedArea) ? normalizedArea : 0)
  }, 0))
}

export const getPhotoQualityScore = (photo) => {
  const width = Number(photo?.width)
  const height = Number(photo?.height)
  const ratio = getPhotoAspectRatio(photo)
  const faceCoverage = getFaceCoverage(photo)
  let score = (photo?.isFavorite ? 100 : 0) + Math.max(0, Number(photo?.rating) || 0) * 20

  if (width > 0 && height > 0) {
    if (Math.min(width, height) < 480 || ratio < 0.45 || ratio > 2.25) return Number.NEGATIVE_INFINITY
    score += 5
  }
  if (faceCoverage !== null) {
    score += faceCoverage >= 0.02 && faceCoverage <= 0.55 ? 10 : 0
    score -= faceCoverage > 0.7 ? 50 : 0
  }
  return score
}

const groupByLocalDay = (photos, formatDay) => {
  const groups = new Map()
  photos.forEach((photo) => {
    const key = photo?.takenAt ? formatDay(photo.takenAt) : `undated-${photo?.id || groups.size}`
    const group = groups.get(key) || []
    group.push(photo)
    groups.set(key, group)
  })
  return [...groups.entries()]
    .sort(([first], [second]) => first.localeCompare(second))
    .map(([, photosForDay]) => photosForDay.sort((first, second) => getPhotoQualityScore(second) - getPhotoQualityScore(first)))
}

const curatedPhotos = (photos) => {
  const allPhotos = Array.isArray(photos) ? photos.filter(Boolean) : []
  return allPhotos.filter((photo) => Number.isFinite(getPhotoQualityScore(photo)))
}

export const getCuratedPhotoMemories = (photos) => curatedPhotos(photos)

export const shufflePhotoMemories = (photos) => {
  const shuffled = [...curatedPhotos(photos)]
  for (let index = shuffled.length - 1; index > 0; index -= 1) {
    const randomIndex = Math.floor(Math.random() * (index + 1))
    ;[shuffled[index], shuffled[randomIndex]] = [shuffled[randomIndex], shuffled[index]]
  }
  return shuffled
}

export const selectTimeSpreadPhotoMemories = (photos, formatDay, limit = 6) => {
  const dayGroups = groupByLocalDay(curatedPhotos(photos), formatDay)
  if (dayGroups.length <= limit) return dayGroups.map(([photo]) => photo)
  return Array.from({ length: limit }, (_, index) => {
    const dayIndex = Math.round(index * (dayGroups.length - 1) / (limit - 1))
    return dayGroups[dayIndex][0]
  })
}

const TRIP_PHOTO_RADIUS_METERS = 30_000
const TRIP_PHOTO_AFTER_ARRIVAL_MS = 48 * 60 * 60 * 1000

const toTimeMs = (value) => {
  const time = Date.parse(value)
  return Number.isFinite(time) ? time : null
}

const tripDestination = (trip) => {
  const path = Array.isArray(trip?.path) ? trip.path : []
  const last = path.at(-1)
  const latitude = Number(last?.latitude ?? trip?.endLatitude)
  const longitude = Number(last?.longitude ?? trip?.endLongitude)
  return Number.isFinite(latitude) && Number.isFinite(longitude) ? { latitude, longitude } : null
}

const MAX_TRIP_PHOTOS = 4

const byPhotoQuality = (first, second) => (getPhotoQualityScore(second.photo) - getPhotoQualityScore(first.photo))
  || (Number(getPhotoAspectRatio(second.photo) >= 1) - Number(getPhotoAspectRatio(first.photo) >= 1))

/**
 * Up to four photos that belong to a trip, in the order they were taken, never one from elsewhere in the month.
 * A photo belongs when it was taken during the trip, or geotagged within 30 km of the destination in the 48 h
 * after arrival (most photos are taken there: nobody photographs while driving). To show the whole visit rather
 * than one burst of shots, the best photo of each quarter of the photographed time comes first, then the next
 * best that are at least an eighth of that time apart. Photos in `excludedIds` (used by another trip) are skipped.
 */
export const selectTripPhotos = (trip, photos, excludedIds = new Set()) => {
  const startMs = toTimeMs(trip?.startTime)
  if (startMs === null) return []
  const endMs = Math.max(startMs, toTimeMs(trip?.endTime) ?? startMs + (Math.max(0, Number(trip?.tripDuration) || 0) * 1000))
  const destination = tripDestination(trip)
  const atDestination = (photo) => destination !== null
    && photo.latitude != null && photo.longitude != null
    && haversineDistanceMeters(photo, destination) <= TRIP_PHOTO_RADIUS_METERS

  const candidates = curatedPhotos(photos)
    .filter((photo) => photo.id && !excludedIds.has(photo.id))
    .map((photo) => ({ photo, takenMs: toTimeMs(photo.takenAt) }))
    .filter(({ photo, takenMs }) => takenMs !== null && takenMs >= startMs && (
      takenMs <= endMs
      || (takenMs <= endMs + TRIP_PHOTO_AFTER_ARRIVAL_MS && atDestination(photo))
    ))
  return spreadOverTime(candidates)
}

// The best photo of each quarter of the photographed time, then the next best at least an eighth of it apart.
const spreadOverTime = (unsortedCandidates) => {
  const candidates = [...unsortedCandidates].sort((first, second) => byPhotoQuality(first, second)
    || (first.takenMs - second.takenMs)
    || String(first.photo.id).localeCompare(String(second.photo.id)))
  if (candidates.length === 0) return []

  const firstMs = Math.min(...candidates.map(({ takenMs }) => takenMs))
  const spanMs = Math.max(1, Math.max(...candidates.map(({ takenMs }) => takenMs)) - firstMs)
  const quarterOf = ({ takenMs }) => Math.min(MAX_TRIP_PHOTOS - 1, Math.floor(((takenMs - firstMs) / spanMs) * MAX_TRIP_PHOTOS))
  const chosen = []
  for (let quarter = 0; quarter < MAX_TRIP_PHOTOS; quarter += 1) {
    const best = candidates.find((candidate) => quarterOf(candidate) === quarter)
    if (best) chosen.push(best)
  }
  candidates.forEach((candidate) => {
    if (chosen.length < MAX_TRIP_PHOTOS && chosen.every((picked) => Math.abs(picked.takenMs - candidate.takenMs) >= spanMs / 8)) {
      chosen.push(candidate)
    }
  })
  return chosen.sort((first, second) => first.takenMs - second.takenMs).map(({ photo }) => photo)
}

const PLACE_PHOTO_RADIUS_METERS = 500

/**
 * Up to four photos geotagged within 500 m of a place ({ coordinates: [latitude, longitude] }), spread over the
 * period like a trip's photos. Photos in `excludedIds` (used by another scene) are skipped.
 */
export const selectPlacePhotos = (place, photos, excludedIds = new Set()) => {
  const [latitude, longitude] = Array.isArray(place?.coordinates) ? place.coordinates.map(Number) : []
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return []
  const center = { latitude, longitude }
  return spreadOverTime(curatedPhotos(photos)
    .filter((photo) => photo.id && !excludedIds.has(photo.id)
      && photo.latitude != null && photo.longitude != null
      && haversineDistanceMeters(photo, center) <= PLACE_PHOTO_RADIUS_METERS)
    .map((photo) => ({ photo, takenMs: toTimeMs(photo.takenAt) }))
    .filter(({ takenMs }) => takenMs !== null))
}
