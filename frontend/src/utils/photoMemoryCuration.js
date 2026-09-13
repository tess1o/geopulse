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
