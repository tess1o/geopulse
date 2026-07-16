import { imageService } from '@/utils/imageService'
import {
  buildPhotoThumbnailImageId,
  getPhotoThumbnailKey,
  hasPhotoThumbnail
} from '@/utils/immichPhotoThumbnailMeta'

const thumbnailBlobUrlCache = new Map()
const thumbnailBlobUrlRequests = new Map()
const failedThumbnailKeys = new Set()

export const getPhotoThumbnailBlobUrl = async (photo) => {
  const key = getPhotoThumbnailKey(photo)
  if (!key || failedThumbnailKeys.has(key)) {
    return null
  }

  if (thumbnailBlobUrlCache.has(key)) {
    return thumbnailBlobUrlCache.get(key)
  }

  if (thumbnailBlobUrlRequests.has(key)) {
    return thumbnailBlobUrlRequests.get(key)
  }

  const request = imageService.loadAuthenticatedImage(photo.thumbnailUrl)
    .then((blobUrl) => {
      thumbnailBlobUrlCache.set(key, blobUrl)
      return blobUrl
    })
    .catch((error) => {
      failedThumbnailKeys.add(key)
      throw error
    })
    .finally(() => {
      thumbnailBlobUrlRequests.delete(key)
    })

  thumbnailBlobUrlRequests.set(key, request)
  return request
}

export {
  buildPhotoThumbnailImageId,
  hasPhotoThumbnail
}

const loadImageElement = (src) => {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('Failed to decode photo thumbnail'))
    image.src = src
  })
}

const drawImageCover = (ctx, image, size) => {
  const sourceWidth = image.naturalWidth || image.width
  const sourceHeight = image.naturalHeight || image.height
  if (!sourceWidth || !sourceHeight) {
    return
  }

  const scale = Math.max(size / sourceWidth, size / sourceHeight)
  const drawWidth = sourceWidth * scale
  const drawHeight = sourceHeight * scale
  const drawX = (size - drawWidth) / 2
  const drawY = (size - drawHeight) / 2
  ctx.drawImage(image, drawX, drawY, drawWidth, drawHeight)
}

export const createCircularPhotoThumbnailImageData = async (photo, { size = 64, borderWidth = 5 } = {}) => {
  const blobUrl = await getPhotoThumbnailBlobUrl(photo)
  if (!blobUrl) {
    return null
  }

  const image = await loadImageElement(blobUrl)
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size

  const ctx = canvas.getContext('2d')
  if (!ctx) {
    return null
  }

  const center = size / 2
  const radius = center - borderWidth / 2

  ctx.clearRect(0, 0, size, size)
  ctx.save()
  ctx.beginPath()
  ctx.arc(center, center, radius - borderWidth / 2, 0, Math.PI * 2)
  ctx.clip()
  drawImageCover(ctx, image, size)
  ctx.restore()

  ctx.beginPath()
  ctx.arc(center, center, radius, 0, Math.PI * 2)
  ctx.lineWidth = borderWidth
  ctx.strokeStyle = '#ffffff'
  ctx.stroke()

  return ctx.getImageData(0, 0, size, size)
}
