<template>
  <Dialog
    :visible="visible"
    modal
    :header="dialogTitle"
    class="gp-dialog-xl photo-viewer-dialog"
    :content-style="{ padding: '0' }"
    :dismissable-mask="true"
    @update:visible="(value) => emit('update:visible', value)"
    @hide="handleClose"
  >
    <div class="photo-viewer-content">
      <div v-if="hasMultiplePhotos" class="photo-navigation">
        <Button
          icon="pi pi-chevron-left"
          @click="previousPhoto"
          :disabled="currentIndex === 0"
          class="nav-button"
          severity="secondary"
          text
        />
        <span class="photo-counter">{{ currentIndex + 1 }} of {{ photos.length }}</span>
        <Button
          icon="pi pi-chevron-right"
          @click="nextPhoto"
          :disabled="currentIndex === photos.length - 1"
          class="nav-button"
          severity="secondary"
          text
        />
      </div>

      <div class="photo-display">
        <img
          v-if="currentImageBlobUrl && !imageError"
          :src="currentImageBlobUrl"
          :alt="currentPhoto?.originalFileName"
          class="main-photo"
          @load="handleImageLoad"
          @error="handleImageError"
        />

        <div v-if="imageLoading" class="image-loading">
          <ProgressSpinner size="50px" />
          <p>Loading photo...</p>
        </div>

        <div v-if="imageError" class="image-error">
          <i class="pi pi-exclamation-triangle"></i>
          <p>Failed to load photo thumbnail</p>
        </div>
      </div>

      <div v-if="hasMultiplePhotos" class="thumbnail-navigation">
        <Button
          icon="pi pi-chevron-left"
          @click="scrollThumbnailRail(-1)"
          class="thumbnail-scroll-button"
          severity="secondary"
          text
        />

        <div ref="thumbnailRailRef" class="thumbnail-rail">
          <button
            v-for="(photo, index) in photos"
            :key="photo.id ?? index"
            type="button"
            class="thumbnail-tile"
            :class="{ 'is-active': index === currentIndex }"
            :data-photo-index="index"
            :aria-label="`Show photo ${index + 1}`"
            @click="selectPhoto(index)"
          >
            <img
              v-if="getPhotoBlobUrl(photo.id)"
              :src="getPhotoBlobUrl(photo.id)"
              :alt="photo.originalFileName || `Photo ${index + 1}`"
              class="thumbnail-image"
            />
            <div v-else class="thumbnail-placeholder">
              <ProgressSpinner
                v-if="isPhotoLoading(photo.id)"
                stroke-width="8"
                class="thumbnail-spinner"
              />
              <i v-else class="pi pi-image"></i>
            </div>
            <span class="thumbnail-index">{{ index + 1 }}</span>
          </button>
        </div>

        <Button
          icon="pi pi-chevron-right"
          @click="scrollThumbnailRail(1)"
          class="thumbnail-scroll-button"
          severity="secondary"
          text
        />
      </div>

      <div v-if="currentPhoto" class="photo-details">
        <div class="photo-details-desktop">
          <div class="detail-row">
            <span class="detail-label">File:</span>
            <span class="detail-value">{{ currentPhoto.originalFileName }}</span>
          </div>

          <div v-if="currentPhoto.takenAt" class="detail-row">
            <span class="detail-label">Date:</span>
            <span class="detail-value">{{ formatDate(currentPhoto.takenAt) }}</span>
          </div>

          <div v-if="hasCoordinates" class="detail-row">
            <span class="detail-label">Location:</span>
            <span class="detail-value">{{ currentPhoto.latitude.toFixed(6) }}, {{ currentPhoto.longitude.toFixed(6) }}</span>
          </div>
        </div>

        <div v-if="currentPhoto.takenAt || hasCoordinates" class="photo-details-mobile">
          <span v-if="currentPhoto.takenAt" class="detail-chip">
            {{ formatCompactDate(currentPhoto.takenAt) }}
          </span>
          <span v-if="hasCoordinates" class="detail-chip">
            {{ compactCoordinates }}
          </span>
        </div>
      </div>

      <!-- Actions -->
      <div class="photo-actions">
        <Button
          v-if="hasCoordinates && allowShowOnMap"
          label="Show on Map"
          icon="pi pi-map-marker"
          @click="showOnMap"
          severity="secondary"
          size="small"
        />

        <Button
          v-if="currentPhoto?.downloadUrl"
          label="Download Original"
          icon="pi pi-download"
          @click="downloadPhoto"
          :loading="downloading"
          severity="secondary"
          size="small"
        />

        <Button
          label="Close"
          icon="pi pi-times"
          @click="handleClose"
          severity="secondary"
          size="small"
        />
      </div>
    </div>
  </Dialog>
</template>

<script setup>
import { ref, computed, watch, nextTick, onUnmounted } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import ProgressSpinner from 'primevue/progressspinner'
import { useToast } from 'primevue/usetoast'
import { imageService } from '@/utils/imageService'
import { useTimezone } from '@/composables/useTimezone'

const timezone = useTimezone()

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  photos: {
    type: Array,
    default: () => []
  },
  initialPhotoIndex: {
    type: Number,
    default: 0
  },
  allowShowOnMap: {
    type: Boolean,
    default: true
  },
  preloadedBlobUrlResolver: {
    type: Function,
    default: null
  }
})

const emit = defineEmits(['update:visible', 'close', 'show-on-map'])
const toast = useToast()

const currentIndex = ref(0)
const imageLoading = ref(false)
const imageError = ref(false)
const downloading = ref(false)
const currentImageBlobUrl = ref(null)
const imageLoadCache = ref({})
const imageLoadPromises = ref({})
const ownedBlobUrls = ref(new Set())
const loadingPhotoIds = ref(new Set())
const currentPhotoLoadToken = ref(0)
const thumbnailRailRef = ref(null)
const thumbnailPreloadRunId = ref(0)

const currentPhoto = computed(() => {
  return props.photos[currentIndex.value] || null
})
const hasMultiplePhotos = computed(() => props.photos.length > 1)

const dialogTitle = computed(() => {
  if (!currentPhoto.value) return 'Photo Viewer'

  if (hasMultiplePhotos.value) {
    return `Photos (${currentIndex.value + 1}/${props.photos.length})`
  }

  return currentPhoto.value.originalFileName || 'Photo'
})

const hasCoordinates = computed(() => {
  return typeof currentPhoto.value?.latitude === 'number' &&
    typeof currentPhoto.value?.longitude === 'number'
})

const toSafeIndex = (index) => {
  if (!Number.isInteger(index)) {
    return 0
  }
  return Math.min(Math.max(0, index), Math.max(0, props.photos.length - 1))
}

const handleClose = () => {
  emit('update:visible', false)
  emit('close')
  resetState()
}

const resetState = () => {
  currentIndex.value = 0
  imageLoading.value = false
  imageError.value = false
  downloading.value = false
  currentImageBlobUrl.value = null

  Object.values(imageLoadCache.value).forEach((blobUrl) => {
    if (blobUrl && blobUrl.startsWith('blob:') && ownedBlobUrls.value.has(blobUrl)) {
      imageService.revokeBlobUrl(blobUrl)
    }
  })
  imageLoadCache.value = {}
  imageLoadPromises.value = {}
  ownedBlobUrls.value = new Set()
  loadingPhotoIds.value = new Set()
  currentPhotoLoadToken.value = 0
  thumbnailPreloadRunId.value += 1
}

const getPhotoBlobUrl = (photoId) => {
  if (photoId === null || photoId === undefined) {
    return null
  }

  const localBlobUrl = imageLoadCache.value[photoId]
  if (localBlobUrl) {
    return localBlobUrl
  }

  if (typeof props.preloadedBlobUrlResolver === 'function') {
    const preloadedBlob = props.preloadedBlobUrlResolver(photoId)
    if (preloadedBlob) {
      imageLoadCache.value = {
        ...imageLoadCache.value,
        [photoId]: preloadedBlob
      }
      return preloadedBlob
    }
  }

  return null
}

const isPhotoLoading = (photoId) => {
  if (photoId === null || photoId === undefined) {
    return false
  }
  return loadingPhotoIds.value.has(photoId)
}

const markPhotoLoading = (photoId, isLoading) => {
  const next = new Set(loadingPhotoIds.value)
  if (isLoading) {
    next.add(photoId)
  } else {
    next.delete(photoId)
  }
  loadingPhotoIds.value = next
}

const ensurePhotoLoaded = async (photo) => {
  if ((photo?.id === null || photo?.id === undefined) || !photo.thumbnailUrl) {
    return null
  }

  const photoId = photo.id
  const cachedBlob = getPhotoBlobUrl(photoId)
  if (cachedBlob) {
    return cachedBlob
  }

  if (imageLoadPromises.value[photoId]) {
    return imageLoadPromises.value[photoId]
  }

  const loadPromise = (async () => {
    markPhotoLoading(photoId, true)
    try {
      const blobUrl = await imageService.loadAuthenticatedImage(photo.thumbnailUrl)
      const owned = new Set(ownedBlobUrls.value)
      owned.add(blobUrl)
      ownedBlobUrls.value = owned
      imageLoadCache.value = {
        ...imageLoadCache.value,
        [photoId]: blobUrl
      }
      return blobUrl
    } catch (error) {
      console.error('Failed to load photo thumbnail:', error)
      return null
    } finally {
      markPhotoLoading(photoId, false)
      const nextPromises = { ...imageLoadPromises.value }
      delete nextPromises[photoId]
      imageLoadPromises.value = nextPromises
    }
  })()

  imageLoadPromises.value = {
    ...imageLoadPromises.value,
    [photoId]: loadPromise
  }

  return loadPromise
}

const preloadThumbnails = () => {
  if (!props.visible || props.photos.length <= 1) {
    return
  }

  const runId = ++thumbnailPreloadRunId.value
  const photos = props.photos.slice()
  const chunkSize = 12

  const preloadChunk = (startIndex = 0) => {
    if (runId !== thumbnailPreloadRunId.value || !props.visible) {
      return
    }

    const endIndex = Math.min(startIndex + chunkSize, photos.length)
    for (let index = startIndex; index < endIndex; index += 1) {
      if (index !== currentIndex.value) {
        ensurePhotoLoaded(photos[index])
      }
    }

    if (endIndex < photos.length) {
      window.setTimeout(() => preloadChunk(endIndex), 0)
    }
  }

  preloadChunk(0)
}

const scrollActiveThumbnailIntoView = () => {
  if (!hasMultiplePhotos.value) {
    return
  }

  nextTick(() => {
    const rail = thumbnailRailRef.value
    if (!rail) {
      return
    }
    const activeThumb = rail.querySelector(`[data-photo-index="${currentIndex.value}"]`)
    if (!activeThumb) {
      return
    }
    activeThumb.scrollIntoView({
      behavior: 'smooth',
      block: 'nearest',
      inline: 'center'
    })
  })
}

const scrollThumbnailRail = (direction) => {
  const rail = thumbnailRailRef.value
  if (!rail) {
    return
  }
  const step = Math.max(rail.clientWidth * 0.75, 240)
  rail.scrollBy({
    left: direction * step,
    behavior: 'smooth'
  })
}

const selectPhoto = (index) => {
  const safeIndex = toSafeIndex(index)
  if (safeIndex === currentIndex.value) {
    scrollActiveThumbnailIntoView()
    return
  }

  currentIndex.value = safeIndex
  resetImageState()
}

const previousPhoto = () => {
  if (currentIndex.value > 0) {
    selectPhoto(currentIndex.value - 1)
  }
}

const nextPhoto = () => {
  if (currentIndex.value < props.photos.length - 1) {
    selectPhoto(currentIndex.value + 1)
  }
}

const resetImageState = () => {
  imageLoading.value = false
  imageError.value = false
  currentImageBlobUrl.value = null
}

const loadCurrentPhoto = async () => {
  if (!currentPhoto.value) {
    currentImageBlobUrl.value = null
    imageLoading.value = false
    imageError.value = false
    return
  }

  const photo = currentPhoto.value
  const loadToken = ++currentPhotoLoadToken.value

  const cachedBlob = getPhotoBlobUrl(photo.id)
  if (cachedBlob) {
    currentImageBlobUrl.value = cachedBlob
    imageLoading.value = false
    imageError.value = false
    return
  }

  if (!photo.thumbnailUrl) {
    imageLoading.value = false
    imageError.value = true
    currentImageBlobUrl.value = null
    return
  }

  imageLoading.value = true
  imageError.value = false
  currentImageBlobUrl.value = null

  const loadedBlob = await ensurePhotoLoaded(photo)
  if (loadToken !== currentPhotoLoadToken.value) {
    return
  }

  if (loadedBlob) {
    currentImageBlobUrl.value = loadedBlob
    imageLoading.value = false
    imageError.value = false
  } else {
    imageLoading.value = false
    imageError.value = true
  }
}

const handleImageLoad = () => {
  imageLoading.value = false
  imageError.value = false
}

const handleImageError = () => {
  imageLoading.value = false
  imageError.value = true
}

const showOnMap = () => {
  if (!hasCoordinates.value || !currentPhoto.value) {
    return
  }

  emit('show-on-map', currentPhoto.value)
  handleClose()
}


const downloadPhoto = async () => {
  if (!currentPhoto.value?.downloadUrl || downloading.value) return
  
  downloading.value = true
  
  try {
    // Use imageService for authenticated download
    await imageService.downloadImage(
      currentPhoto.value.downloadUrl,
      currentPhoto.value.originalFileName || `photo_${currentPhoto.value.id}.jpg`
    )
    
    toast.add({
      severity: 'success',
      summary: 'Download Started',
      detail: 'Photo download has begun',
      life: 3000
    })
  } catch (error) {
    console.error('Failed to download photo:', error)
    toast.add({
      severity: 'error',
      summary: 'Download Failed',
      detail: 'Could not download photo',
      life: 5000
    })
  } finally {
    downloading.value = false
  }
}

const formatDate = (dateString) => {
  if (!dateString) return ''

  try {
    return `${timezone.formatDateDisplay(dateString)} ${timezone.format(dateString, 'HH:mm:ss')}`
  } catch (error) {
    return dateString
  }
}

const formatCompactDate = (dateString) => {
  if (!dateString) return ''

  try {
    return `${timezone.formatDateDisplay(dateString)} ${timezone.format(dateString, 'HH:mm')}`
  } catch (error) {
    return dateString
  }
}

const compactCoordinates = computed(() => {
  if (!hasCoordinates.value || !currentPhoto.value) {
    return ''
  }
  return `${currentPhoto.value.latitude.toFixed(4)}, ${currentPhoto.value.longitude.toFixed(4)}`
})

// Keyboard navigation
const handleKeydown = (event) => {
  if (!props.visible) return

  switch (event.key) {
    case 'ArrowLeft':
      event.preventDefault()
      previousPhoto()
      break
    case 'ArrowRight':
      event.preventDefault()
      nextPhoto()
      break
    case 'Escape':
      event.preventDefault()
      handleClose()
      break
  }
}

watch(() => props.visible, (newVisible) => {
  if (newVisible) {
    currentIndex.value = toSafeIndex(props.initialPhotoIndex || 0)
    resetImageState()
    loadCurrentPhoto()
    preloadThumbnails()
    scrollActiveThumbnailIntoView()

    nextTick(() => {
      window.addEventListener('keydown', handleKeydown)
    })
  } else {
    window.removeEventListener('keydown', handleKeydown)
  }
})

watch(() => currentPhoto.value, () => {
  if (!props.visible || !currentPhoto.value) {
    return
  }

  loadCurrentPhoto()
  scrollActiveThumbnailIntoView()
})

watch(() => props.initialPhotoIndex, (newIndex) => {
  if (props.visible && newIndex >= 0 && newIndex < props.photos.length) {
    currentIndex.value = toSafeIndex(newIndex)
    resetImageState()
    loadCurrentPhoto()
    preloadThumbnails()
    scrollActiveThumbnailIntoView()
  }
})

watch(() => props.photos.length, (newLength) => {
  if (newLength <= 0) {
    currentIndex.value = 0
    resetImageState()
    return
  }

  if (currentIndex.value > newLength - 1) {
    currentIndex.value = newLength - 1
    resetImageState()
  }

  if (props.visible) {
    preloadThumbnails()
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
  resetState()
})
</script>

<style scoped>
.photo-viewer-content {
  display: flex;
  flex-direction: column;
  max-height: calc(90vh - 3.2rem);
}

.photo-navigation {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.45rem 0.75rem;
  border-bottom: 1px solid var(--gp-border-light, rgba(0, 0, 0, 0.1));
  background: var(--gp-surface-light, #f8fafc);
}

.photo-counter {
  font-weight: 500;
  color: var(--gp-text-primary, #1e293b);
}

.nav-button {
  min-width: 40px;
}

.photo-display {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 260px;
  max-height: 48vh;
  overflow: hidden;
  background: var(--gp-surface-light, #f8fafc);
}

.main-photo {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: 4px;
}

.thumbnail-navigation {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.55rem 0.75rem;
  border-top: 1px solid var(--gp-border-light, rgba(0, 0, 0, 0.1));
  border-bottom: 1px solid var(--gp-border-light, rgba(0, 0, 0, 0.1));
  background: var(--gp-surface-white, white);
}

.thumbnail-scroll-button {
  flex-shrink: 0;
  min-width: 36px;
}

.thumbnail-rail {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex: 1;
  overflow-x: auto;
  padding: 0.2rem 0.1rem;
  scroll-behavior: smooth;
  scrollbar-width: thin;
}

.thumbnail-tile {
  position: relative;
  width: 80px;
  height: 80px;
  flex-shrink: 0;
  border: 1px solid var(--gp-border-light, rgba(0, 0, 0, 0.1));
  border-radius: var(--gp-radius-medium, 8px);
  background: var(--gp-surface-white, white);
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.2s ease, transform 0.2s ease;
}

.thumbnail-tile:hover {
  border-color: var(--gp-primary, #3b82f6);
  transform: translateY(-1px);
}

.thumbnail-tile.is-active {
  border-color: var(--gp-primary, #3b82f6);
  box-shadow: 0 0 0 1px var(--gp-primary, #3b82f6);
}

.thumbnail-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.thumbnail-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--gp-text-secondary, #64748b);
  background: var(--gp-surface-light, #f8fafc);
}

.thumbnail-spinner {
  width: 20px;
  height: 20px;
}

.thumbnail-index {
  position: absolute;
  right: 4px;
  bottom: 4px;
  min-width: 18px;
  padding: 0 4px;
  border-radius: 999px;
  font-size: 0.7rem;
  line-height: 1.3;
  text-align: center;
  background: rgba(15, 23, 42, 0.78);
  color: #fff;
}

.image-loading,
.image-error {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  color: var(--gp-text-secondary, #64748b);
}

.image-loading p,
.image-error p {
  margin: 0.5rem 0;
  font-size: 0.9rem;
}

.image-error i {
  font-size: 2rem;
  color: var(--gp-error, #ef4444);
  margin-bottom: 0.5rem;
}

.photo-details {
  padding: 0.75rem;
  border-bottom: 1px solid var(--gp-border-light, rgba(0, 0, 0, 0.1));
  background: var(--gp-surface-white, white);
}

.photo-details-mobile {
  display: none;
}

.detail-row {
  display: flex;
  margin-bottom: 0.5rem;
  font-size: 0.9rem;
}

.detail-row:last-child {
  margin-bottom: 0;
}

.detail-label {
  font-weight: 500;
  color: var(--gp-text-secondary, #64748b);
  min-width: 80px;
  margin-right: 1rem;
}

.detail-value {
  color: var(--gp-text-primary, #1e293b);
  word-break: break-word;
}

.photo-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  padding: 0.75rem;
  border-top: 1px solid var(--gp-border-light, rgba(0, 0, 0, 0.1));
  background: var(--gp-surface-light, #f8fafc);
}

:deep(.photo-viewer-dialog .p-dialog-header) {
  padding: 0.65rem 1rem;
}

:deep(.photo-viewer-dialog .p-dialog-header .p-dialog-title) {
  font-size: 1.1rem;
  line-height: 1.15;
}

.p-dark .photo-navigation,
.p-dark .photo-actions {
  background: var(--gp-surface-dark, #1e293b) !important;
  border-color: var(--gp-border-dark, rgba(255, 255, 255, 0.1)) !important;
}

.p-dark .photo-display {
  background: var(--gp-surface-darker, #000) !important;
}

.p-dark .photo-details {
  background: var(--gp-surface-dark, #1e293b) !important;
  border-color: var(--gp-border-dark, rgba(255, 255, 255, 0.1)) !important;
}

.p-dark .thumbnail-navigation {
  background: var(--gp-surface-dark, #1e293b) !important;
  border-color: var(--gp-border-dark, rgba(255, 255, 255, 0.1)) !important;
}

.p-dark .thumbnail-tile {
  background: var(--gp-surface-dark, #1e293b) !important;
  border-color: var(--gp-border-dark, rgba(255, 255, 255, 0.1)) !important;
}

.p-dark .thumbnail-placeholder {
  background: var(--gp-surface-darker, #0b1120) !important;
  color: rgba(255, 255, 255, 0.8) !important;
}

.p-dark .photo-counter {
  color: rgba(255, 255, 255, 0.95) !important;
}

.p-dark .detail-label {
  color: rgba(255, 255, 255, 0.8) !important;
}

.p-dark .detail-value {
  color: rgba(255, 255, 255, 0.95) !important;
}

.p-dark .image-loading,
.p-dark .image-error {
  color: rgba(255, 255, 255, 0.8) !important;
}

@media (max-width: 768px) {
  .photo-viewer-content {
    max-height: calc(100dvh - 7.2rem);
    overflow: hidden;
  }
  
  .photo-navigation {
    padding: 0.25rem 0.45rem;
  }
  
  .photo-display {
    min-height: 240px;
    max-height: 42dvh;
  }

  .thumbnail-navigation {
    padding: 0.35rem 0.45rem;
    gap: 0.2rem;
  }

  .thumbnail-scroll-button {
    min-width: 26px;
    padding: 0.2rem;
  }

  .thumbnail-rail {
    gap: 0.25rem;
    padding: 0.1rem 0;
  }

  .thumbnail-tile {
    width: 50px;
    height: 50px;
  }
  
  .photo-details {
    padding: 0.35rem 0.45rem;
  }

  .photo-details-desktop {
    display: none;
  }

  .photo-details-mobile {
    display: flex;
    align-items: center;
    gap: 0.35rem;
    flex-wrap: wrap;
  }

  .detail-chip {
    max-width: 100%;
    display: inline-block;
    font-size: 0.75rem;
    line-height: 1.25;
    border-radius: 999px;
    padding: 0.2rem 0.5rem;
    color: var(--gp-text-secondary, #64748b);
    background: var(--gp-surface-light, #f8fafc);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  
  .detail-row {
    flex-direction: column;
    margin-bottom: 0.75rem;
  }
  
  .detail-label {
    min-width: auto;
    margin-right: 0;
    margin-bottom: 0.25rem;
  }
  
  .photo-actions {
    padding: 0.45rem;
    flex-wrap: nowrap;
    position: sticky;
    bottom: 0;
    z-index: 3;
    justify-content: space-between;
    gap: 0.35rem;
  }

  .photo-actions :deep(.p-button) {
    flex: 1;
    min-width: 0;
    font-size: 0.75rem;
    padding-inline: 0.35rem;
  }

  :deep(.photo-viewer-dialog .p-dialog-header) {
    padding: 0.5rem 0.75rem;
  }

  :deep(.photo-viewer-dialog .p-dialog-header .p-dialog-title) {
    font-size: 1.05rem;
  }
}
</style>
