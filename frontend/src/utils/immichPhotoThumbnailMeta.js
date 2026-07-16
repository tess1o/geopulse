export const getPhotoThumbnailKey = (photo) => {
  if (!photo || !photo.thumbnailUrl) {
    return ''
  }

  return String(photo.id || photo.thumbnailUrl)
}

export const hasPhotoThumbnail = (photo) => Boolean(getPhotoThumbnailKey(photo))

export const buildPhotoThumbnailImageId = (prefix, photo) => {
  const key = getPhotoThumbnailKey(photo)
  if (!key) {
    return ''
  }

  return `${prefix}-thumb-${key.replace(/[^a-zA-Z0-9_-]/g, '_')}`
}
