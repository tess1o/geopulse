<template>
  <Dialog
    :visible="visible"
    modal
    :show-header="false"
    :close-on-escape="false"
    :block-scroll="true"
    class="photo-viewer-dialog"
    :content-style="{ padding: '0' }"
    :dismissable-mask="true"
    aria-label="Photo viewer"
    @update:visible="handleDialogVisibilityChange"
  >
    <div
      ref="viewerRootRef"
      class="photo-viewer-content"
      :class="{
        'controls-hidden': !controlsVisible,
        'details-open': detailsOpen,
        'is-mobile-viewer': isMobileViewport
      }"
      data-testid="photo-viewer"
      @pointerdown.capture="handleViewerPointerDownCapture"
      @pointermove="handleViewerPointerMove"
      @focusin="handleViewerFocusIn"
      @focusout="handleViewerFocusOut"
    >
      <main
        class="photo-stage"
        @pointerdown="handleStagePointerDown"
        @pointermove="handleStagePointerMove"
        @pointerup="handleStagePointerUp"
        @pointercancel="handleStagePointerCancel"
      >
        <div class="photo-display">
          <img
            v-if="currentImageBlobUrl && !imageError"
            :src="currentImageBlobUrl"
            :alt="currentPhoto?.originalFileName || 'Photo'"
            class="main-photo"
            draggable="false"
            @load="handleImageLoad"
            @error="handleImageError"
          />

          <div v-if="imageLoading" class="image-status image-loading" role="status">
            <ProgressSpinner class="image-spinner" />
            <p>Loading photo…</p>
          </div>

          <div v-else-if="imageError" class="image-status image-error" role="alert">
            <i class="pi pi-exclamation-triangle"></i>
            <p>Failed to load photo thumbnail</p>
          </div>
        </div>

        <header class="viewer-topbar viewer-chrome">
          <div class="viewer-title-group">
            <button
              type="button"
              class="viewer-icon-button"
              aria-label="Close photo viewer"
              title="Close"
              data-testid="photo-viewer-close"
              @pointerdown.stop
              @click.stop="handleClose"
            >
              <i class="pi pi-arrow-left"></i>
            </button>
            <div class="viewer-title-copy">
              <strong :title="currentPhoto?.originalFileName || dialogTitle">{{ currentPhoto?.originalFileName || dialogTitle }}</strong>
              <span v-if="currentPhoto?.takenAt">{{ formatCompactDate(currentPhoto.takenAt) }}</span>
            </div>
          </div>

          <span v-if="hasMultiplePhotos" class="photo-counter" aria-live="polite">
            {{ currentIndex + 1 }} / {{ photos.length }}
          </span>

          <div class="viewer-toolbar-actions">
            <button
              v-if="currentPhoto?.downloadUrl"
              type="button"
              class="viewer-icon-button desktop-toolbar-action"
              :class="{ 'is-loading': downloading }"
              :disabled="downloading"
              aria-label="Download original photo"
              title="Download original"
              @pointerdown.stop
              @click.stop="downloadPhoto"
            >
              <i :class="downloading ? 'pi pi-spin pi-spinner' : 'pi pi-download'"></i>
            </button>
            <button
              type="button"
              class="viewer-icon-button desktop-toolbar-action"
              :class="{ 'is-active': detailsOpen }"
              :aria-pressed="detailsOpen"
              aria-label="Toggle photo details"
              title="Photo details"
              @pointerdown.stop
              @click.stop="toggleDetails"
            >
              <i class="pi pi-info-circle"></i>
            </button>
          </div>
        </header>

        <template v-if="hasMultiplePhotos">
          <button
            type="button"
            class="photo-nav-button photo-nav-previous viewer-chrome"
            :disabled="currentIndex === 0"
            aria-label="Previous photo"
            @pointerdown.stop
            @click.stop="previousPhoto"
          >
            <i class="pi pi-chevron-left"></i>
          </button>
          <button
            type="button"
            class="photo-nav-button photo-nav-next viewer-chrome"
            :disabled="currentIndex === photos.length - 1"
            aria-label="Next photo"
            @pointerdown.stop
            @click.stop="nextPhoto"
          >
            <i class="pi pi-chevron-right"></i>
          </button>
        </template>

        <div v-if="hasMultiplePhotos" class="thumbnail-navigation viewer-chrome">
          <button
            type="button"
            class="thumbnail-scroll-button"
            aria-label="Scroll thumbnails left"
            @pointerdown.stop
            @click.stop="scrollThumbnailRail(-1)"
          >
            <i class="pi pi-chevron-left"></i>
          </button>

          <div ref="thumbnailRailRef" class="thumbnail-rail" @pointerdown.stop>
            <button
              v-for="(photo, index) in photos"
              :key="photo.id ?? index"
              type="button"
              class="thumbnail-tile"
              :class="{ 'is-active': index === currentIndex }"
              :data-photo-index="index"
              :aria-current="index === currentIndex ? 'true' : undefined"
              :aria-label="`Show photo ${index + 1}`"
              @click="selectPhoto(index)"
            >
              <img
                v-if="getPhotoBlobUrl(photo.id)"
                :src="getPhotoBlobUrl(photo.id)"
                :alt="photo.originalFileName || `Photo ${index + 1}`"
                class="thumbnail-image"
                draggable="false"
              />
              <span v-else class="thumbnail-placeholder">
                <ProgressSpinner
                  v-if="isPhotoLoading(photo.id)"
                  stroke-width="8"
                  class="thumbnail-spinner"
                />
                <i v-else class="pi pi-image"></i>
              </span>
            </button>
          </div>

          <button
            type="button"
            class="thumbnail-scroll-button"
            aria-label="Scroll thumbnails right"
            @pointerdown.stop
            @click.stop="scrollThumbnailRail(1)"
          >
            <i class="pi pi-chevron-right"></i>
          </button>
        </div>

        <div v-if="currentPhoto" class="mobile-photo-summary viewer-chrome">
          <strong>{{ currentPhoto.originalFileName || 'Photo' }}</strong>
          <span v-if="currentPhoto.takenAt">{{ formatCompactDate(currentPhoto.takenAt) }}</span>
        </div>
      </main>

      <aside v-if="currentPhoto" class="photo-details-panel" aria-label="Photo details">
        <div class="details-panel-header">
          <strong>Photo details</strong>
          <button
            type="button"
            class="details-close-button"
            aria-label="Close photo details"
            title="Close details"
            @click="toggleDetails"
          >
            <i class="pi pi-times"></i>
          </button>
        </div>

        <div class="details-panel-content">
          <div class="detail-item">
            <i class="pi pi-file"></i>
            <div>
              <span>File</span>
              <strong>{{ currentPhoto.originalFileName || 'Photo' }}</strong>
            </div>
          </div>
          <div v-if="currentPhoto.takenAt" class="detail-item">
            <i class="pi pi-clock"></i>
            <div>
              <span>Taken</span>
              <strong>{{ formatDate(currentPhoto.takenAt) }}</strong>
            </div>
          </div>

          <div v-if="hasCoordinates" class="location-card">
            <div class="location-card-copy">
              <span class="location-icon"><i class="pi pi-map-marker"></i></span>
              <div>
                <span>Location</span>
                <strong>{{ preciseCoordinates }}</strong>
              </div>
            </div>
            <button
              v-if="allowShowOnMap"
              type="button"
              class="details-action-button"
              @click="showOnMap"
            >
              <i class="pi pi-map"></i>
              <span>Show on Map</span>
            </button>
          </div>
        </div>
      </aside>

      <section
        v-if="currentPhoto"
        class="mobile-details-sheet viewer-chrome"
        :aria-label="detailsOpen ? 'Expanded photo details' : 'Photo details'"
      >
        <div class="mobile-sheet-bar">
          <button
            type="button"
            class="mobile-sheet-toggle"
            :aria-expanded="detailsOpen"
            @pointerdown.stop
            @click.stop="toggleDetails"
          >
            <span class="mobile-sheet-grip"></span>
            <span class="mobile-sheet-copy">
              <strong>Photo details</strong>
              <small>{{ mobileDetailSummary }}</small>
            </span>
            <i :class="detailsOpen ? 'pi pi-chevron-down' : 'pi pi-chevron-up'"></i>
          </button>

          <div v-if="!detailsOpen" class="mobile-compact-actions">
            <button
              v-if="hasCoordinates && allowShowOnMap"
              type="button"
              class="mobile-action-icon"
              aria-label="Show photo on map"
              title="Show on Map"
              @pointerdown.stop
              @click.stop="showOnMap"
            >
              <i class="pi pi-map-marker"></i>
            </button>
            <button
              v-if="currentPhoto.downloadUrl"
              type="button"
              class="mobile-action-icon"
              :disabled="downloading"
              aria-label="Download original photo"
              title="Download original"
              @pointerdown.stop
              @click.stop="downloadPhoto"
            >
              <i :class="downloading ? 'pi pi-spin pi-spinner' : 'pi pi-download'"></i>
            </button>
          </div>
        </div>

        <div v-if="detailsOpen" class="mobile-sheet-content">
          <div class="detail-item">
            <i class="pi pi-file"></i>
            <div><span>File</span><strong>{{ currentPhoto.originalFileName || 'Photo' }}</strong></div>
          </div>
          <div v-if="currentPhoto.takenAt" class="detail-item">
            <i class="pi pi-clock"></i>
            <div><span>Taken</span><strong>{{ formatDate(currentPhoto.takenAt) }}</strong></div>
          </div>
          <div v-if="hasCoordinates" class="detail-item">
            <i class="pi pi-map-marker"></i>
            <div><span>Location</span><strong>{{ preciseCoordinates }}</strong></div>
          </div>
          <div class="mobile-sheet-actions">
            <button
              v-if="hasCoordinates && allowShowOnMap"
              type="button"
              class="details-action-button"
              @click="showOnMap"
            >
              <i class="pi pi-map"></i><span>Show on Map</span>
            </button>
            <button
              v-if="currentPhoto.downloadUrl"
              type="button"
              class="details-action-button"
              :disabled="downloading"
              @click="downloadPhoto"
            >
              <i :class="downloading ? 'pi pi-spin pi-spinner' : 'pi pi-download'"></i>
              <span>{{ downloading ? 'Downloading…' : 'Download Original' }}</span>
            </button>
          </div>
        </div>
      </section>
    </div>
  </Dialog>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import Dialog from 'primevue/dialog'
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
const displayImageLoadCache = ref({})
const displayImageLoadPromises = ref({})
const ownedBlobUrls = ref(new Set())
const loadingPhotoIds = ref(new Set())
const currentPhotoLoadToken = ref(0)
const thumbnailRailRef = ref(null)
const thumbnailPreloadRunId = ref(0)
const viewerRootRef = ref(null)
const controlsVisible = ref(true)
const detailsOpen = ref(false)
const isMobileViewport = ref(false)

const MOBILE_VIEWER_MEDIA = '(max-width: 768px)'
const CONTROLS_HIDE_DELAY = 3000
const SWIPE_THRESHOLD = 48
const TAP_MOVEMENT_THRESHOLD = 8
let viewportMediaQuery = null
let controlsHideTimer = null
let stageGesture = null
let lastInteractionWasKeyboard = false

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

const preciseCoordinates = computed(() => {
  if (!hasCoordinates.value || !currentPhoto.value) {
    return ''
  }
  return `${currentPhoto.value.latitude.toFixed(6)}, ${currentPhoto.value.longitude.toFixed(6)}`
})

const mobileDetailSummary = computed(() => {
  if (hasCoordinates.value) {
    return compactCoordinates.value
  }
  if (currentPhoto.value?.takenAt) {
    return formatCompactDate(currentPhoto.value.takenAt)
  }
  return currentPhoto.value?.originalFileName || 'Photo information'
})

const toSafeIndex = (index) => {
  if (!Number.isInteger(index)) {
    return 0
  }
  return Math.min(Math.max(0, index), Math.max(0, props.photos.length - 1))
}

const clearControlsHideTimer = () => {
  if (controlsHideTimer !== null) {
    window.clearTimeout(controlsHideTimer)
    controlsHideTimer = null
  }
}

const shouldKeepControlsVisible = () => (
  isMobileViewport.value && detailsOpen.value
)

const scheduleControlsHide = () => {
  clearControlsHideTimer()
  if (!props.visible || shouldKeepControlsVisible()) {
    return
  }

  controlsHideTimer = window.setTimeout(() => {
    controlsVisible.value = false
    controlsHideTimer = null
  }, CONTROLS_HIDE_DELAY)
}

const showControls = ({ schedule = true } = {}) => {
  controlsVisible.value = true
  if (schedule) {
    scheduleControlsHide()
  } else {
    clearControlsHideTimer()
  }
}

const toggleControls = () => {
  if (shouldKeepControlsVisible()) {
    showControls({ schedule: false })
    return
  }

  if (controlsVisible.value) {
    clearControlsHideTimer()
    controlsVisible.value = false
  } else {
    showControls()
  }
}

const toggleDetails = () => {
  detailsOpen.value = !detailsOpen.value
  showControls({ schedule: !shouldKeepControlsVisible() })
}

const handleViewerPointerMove = () => {
  if (!props.visible) {
    return
  }
  lastInteractionWasKeyboard = false
  showControls({ schedule: !shouldKeepControlsVisible() })
}

const handleViewerPointerDownCapture = () => {
  lastInteractionWasKeyboard = false
}

const handleViewerFocusIn = () => {
  showControls({ schedule: !lastInteractionWasKeyboard })
}

const handleViewerFocusOut = () => {
  nextTick(() => {
    if (viewerRootRef.value?.contains(document.activeElement)) {
      return
    }
    scheduleControlsHide()
  })
}

const handleViewportChange = (event) => {
  isMobileViewport.value = event.matches
  if (props.visible) {
    detailsOpen.value = false
    showControls({ schedule: !shouldKeepControlsVisible() })
  }
}

const resetStageGesture = () => {
  stageGesture = null
}

const handleStagePointerDown = (event) => {
  if (event.button !== 0 || event.target.closest('button, .thumbnail-navigation')) {
    return
  }

  stageGesture = {
    pointerId: event.pointerId,
    pointerType: event.pointerType,
    startX: event.clientX,
    startY: event.clientY,
    lastX: event.clientX,
    lastY: event.clientY
  }

  event.currentTarget.setPointerCapture?.(event.pointerId)
}

const handleStagePointerMove = (event) => {
  if (!stageGesture || stageGesture.pointerId !== event.pointerId) {
    return
  }

  stageGesture.lastX = event.clientX
  stageGesture.lastY = event.clientY
  const deltaX = event.clientX - stageGesture.startX
  const deltaY = event.clientY - stageGesture.startY
  if (
    ['touch', 'pen'].includes(stageGesture.pointerType) &&
    Math.abs(deltaX) > TAP_MOVEMENT_THRESHOLD &&
    Math.abs(deltaX) > Math.abs(deltaY)
  ) {
    event.preventDefault()
  }
}

const handleStagePointerUp = (event) => {
  if (!stageGesture || stageGesture.pointerId !== event.pointerId) {
    return
  }

  const gesture = stageGesture
  gesture.lastX = event.clientX
  gesture.lastY = event.clientY
  resetStageGesture()

  const deltaX = gesture.lastX - gesture.startX
  const deltaY = gesture.lastY - gesture.startY
  const isTouchSwipe = ['touch', 'pen'].includes(gesture.pointerType) &&
    Math.abs(deltaX) >= SWIPE_THRESHOLD &&
    Math.abs(deltaX) > Math.abs(deltaY) * 1.25

  if (isTouchSwipe) {
    showControls()
    if (deltaX < 0) {
      nextPhoto()
    } else {
      previousPhoto()
    }
    return
  }

  if (
    Math.abs(deltaX) <= TAP_MOVEMENT_THRESHOLD &&
    Math.abs(deltaY) <= TAP_MOVEMENT_THRESHOLD
  ) {
    toggleControls()
  }
}

const handleStagePointerCancel = () => {
  resetStageGesture()
}

const handleClose = () => {
  clearControlsHideTimer()
  resetStageGesture()
  emit('update:visible', false)
  emit('close')
}

const handleDialogVisibilityChange = (value) => {
  if (value) {
    emit('update:visible', true)
    return
  }
  handleClose()
}

const resetState = () => {
  currentIndex.value = 0
  imageLoading.value = false
  imageError.value = false
  downloading.value = false
  currentImageBlobUrl.value = null

  const cachedBlobUrls = [
    ...Object.values(imageLoadCache.value),
    ...Object.values(displayImageLoadCache.value)
  ]

  cachedBlobUrls.forEach((blobUrl) => {
    if (blobUrl && blobUrl.startsWith('blob:') && ownedBlobUrls.value.has(blobUrl)) {
      imageService.revokeBlobUrl(blobUrl)
    }
  })
  imageLoadCache.value = {}
  imageLoadPromises.value = {}
  displayImageLoadCache.value = {}
  displayImageLoadPromises.value = {}
  ownedBlobUrls.value = new Set()
  loadingPhotoIds.value = new Set()
  currentPhotoLoadToken.value = 0
  thumbnailPreloadRunId.value += 1
  controlsVisible.value = true
  detailsOpen.value = false
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

const getPhotoDisplayEndpoint = (photo) => photo?.previewUrl || photo?.thumbnailUrl || null

const getDisplayPhotoBlobUrl = (photo) => {
  if (photo?.id === null || photo?.id === undefined) {
    return null
  }

  const localBlobUrl = displayImageLoadCache.value[photo.id]
  if (localBlobUrl) {
    return localBlobUrl
  }

  if (!photo.previewUrl) {
    return getPhotoBlobUrl(photo.id)
  }

  return null
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

const ensureDisplayPhotoLoaded = async (photo) => {
  if (photo?.id === null || photo?.id === undefined) {
    return null
  }

  const cachedBlob = getDisplayPhotoBlobUrl(photo)
  if (cachedBlob) {
    return cachedBlob
  }

  const displayEndpoint = getPhotoDisplayEndpoint(photo)
  if (!displayEndpoint) {
    return null
  }

  if (displayImageLoadPromises.value[photo.id]) {
    return displayImageLoadPromises.value[photo.id]
  }

  const loadPromise = (async () => {
    try {
      const blobUrl = await imageService.loadAuthenticatedImage(displayEndpoint)
      const owned = new Set(ownedBlobUrls.value)
      owned.add(blobUrl)
      ownedBlobUrls.value = owned
      displayImageLoadCache.value = {
        ...displayImageLoadCache.value,
        [photo.id]: blobUrl
      }
      return blobUrl
    } catch (error) {
      if (photo.previewUrl && photo.thumbnailUrl && photo.previewUrl !== photo.thumbnailUrl) {
        console.warn('Failed to load photo preview, falling back to thumbnail:', error)
        return ensurePhotoLoaded(photo)
      }
      console.error('Failed to load photo preview:', error)
      return null
    } finally {
      const nextPromises = { ...displayImageLoadPromises.value }
      delete nextPromises[photo.id]
      displayImageLoadPromises.value = nextPromises
    }
  })()

  displayImageLoadPromises.value = {
    ...displayImageLoadPromises.value,
    [photo.id]: loadPromise
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

    const railRect = rail.getBoundingClientRect()
    const activeThumbRect = activeThumb.getBoundingClientRect()
    const visibilityPadding = 12

    let scrollDelta = 0
    if (activeThumbRect.left < railRect.left + visibilityPadding) {
      scrollDelta = activeThumbRect.left - railRect.left - visibilityPadding
    } else if (activeThumbRect.right > railRect.right - visibilityPadding) {
      scrollDelta = activeThumbRect.right - railRect.right + visibilityPadding
    }

    if (scrollDelta !== 0) {
      rail.scrollBy({
        left: scrollDelta,
        behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth'
      })
    }
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

  const cachedBlob = getDisplayPhotoBlobUrl(photo)
  if (cachedBlob) {
    currentImageBlobUrl.value = cachedBlob
    imageLoading.value = false
    imageError.value = false
    return
  }

  if (!getPhotoDisplayEndpoint(photo)) {
    imageLoading.value = false
    imageError.value = true
    currentImageBlobUrl.value = null
    return
  }

  imageLoading.value = true
  imageError.value = false
  currentImageBlobUrl.value = null

  const loadedBlob = await ensureDisplayPhotoLoaded(photo)
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
    return `${timezone.formatDateDisplay(dateString)} ${timezone.formatTime(dateString, { withSeconds: true })}`
  } catch (error) {
    return dateString
  }
}

const formatCompactDate = (dateString) => {
  if (!dateString) return ''

  try {
    return `${timezone.formatDateDisplay(dateString)} ${timezone.formatTime(dateString)}`
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

  lastInteractionWasKeyboard = true
  showControls()

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
    detailsOpen.value = false
    resetImageState()
    loadCurrentPhoto()
    preloadThumbnails()
    scrollActiveThumbnailIntoView()
    showControls()

    nextTick(() => {
      window.addEventListener('keydown', handleKeydown)
    })
  } else {
    clearControlsHideTimer()
    window.removeEventListener('keydown', handleKeydown)
    resetState()
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

onMounted(() => {
  viewportMediaQuery = window.matchMedia(MOBILE_VIEWER_MEDIA)
  isMobileViewport.value = viewportMediaQuery.matches
  detailsOpen.value = false
  viewportMediaQuery.addEventListener?.('change', handleViewportChange)
})

onUnmounted(() => {
  clearControlsHideTimer()
  resetStageGesture()
  window.removeEventListener('keydown', handleKeydown)
  viewportMediaQuery?.removeEventListener?.('change', handleViewportChange)
  resetState()
})
</script>


<style scoped>
:global(.photo-viewer-dialog) {
  width: min(96vw, 1500px) !important;
  height: min(92dvh, 960px) !important;
  max-height: 92dvh !important;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 18px;
  background: #080c13;
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.42);
}

:global(.photo-viewer-dialog .p-dialog-content) {
  height: 100%;
  max-height: none;
  overflow: hidden;
  padding: 0 !important;
  border-radius: inherit;
  background: #080c13;
}

.photo-viewer-content {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  width: 100%;
  height: 100%;
  max-height: none;
  overflow: hidden;
  background: #080c13;
  transition: grid-template-columns 180ms ease;
}

.photo-viewer-content:not(.details-open) {
  grid-template-columns: minmax(0, 1fr) 0;
}

.photo-stage {
  position: relative;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: #080c13;
  touch-action: pan-y;
  user-select: none;
}

.photo-stage::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  background: linear-gradient(
    180deg,
    rgba(0, 0, 0, 0.54) 0,
    rgba(0, 0, 0, 0) 18%,
    rgba(0, 0, 0, 0) 70%,
    rgba(0, 0, 0, 0.58) 100%
  );
}

.photo-display {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 0;
  max-height: none;
  overflow: hidden;
  background: #080c13;
}

.main-photo {
  display: block;
  width: 100%;
  height: 100%;
  max-width: 100%;
  max-height: 100%;
  border-radius: 0;
  object-fit: contain;
  -webkit-user-drag: none;
}

.viewer-chrome {
  opacity: 1;
  visibility: visible;
  transition: opacity 180ms ease, visibility 180ms ease, transform 180ms ease;
}

.controls-hidden .viewer-chrome {
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
}

.viewer-topbar {
  position: absolute;
  z-index: 5;
  top: 14px;
  right: 14px;
  left: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #fff;
  pointer-events: none;
}

.viewer-title-group,
.viewer-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  pointer-events: auto;
}

.viewer-toolbar-actions {
  justify-content: flex-end;
  margin-left: auto;
}

.viewer-title-copy {
  display: grid;
  min-width: 0;
  line-height: 1.2;
  text-shadow: 0 1px 5px rgba(0, 0, 0, 0.55);
}

.viewer-title-copy strong {
  overflow: hidden;
  color: #fff;
  font-size: 14px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.viewer-title-copy span {
  margin-top: 3px;
  color: rgba(255, 255, 255, 0.68);
  font-size: 11px;
}

.viewer-icon-button,
.photo-nav-button,
.thumbnail-scroll-button,
.mobile-action-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 44px;
  height: 44px;
  padding: 0;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 13px;
  color: #fff;
  background: rgba(16, 20, 28, 0.58);
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.18);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  cursor: pointer;
  transition: background-color 150ms ease, border-color 150ms ease, opacity 150ms ease, transform 150ms ease;
}

.viewer-icon-button:hover:not(:disabled),
.photo-nav-button:hover:not(:disabled),
.thumbnail-scroll-button:hover:not(:disabled),
.mobile-action-icon:hover:not(:disabled) {
  border-color: rgba(255, 255, 255, 0.26);
  background: rgba(35, 42, 55, 0.82);
}

.viewer-icon-button:active:not(:disabled),
.photo-nav-button:active:not(:disabled),
.thumbnail-scroll-button:active:not(:disabled),
.mobile-action-icon:active:not(:disabled) {
  transform: scale(0.96);
}

.viewer-icon-button.is-active {
  border-color: color-mix(in srgb, var(--gp-primary) 78%, white);
  background: color-mix(in srgb, var(--gp-primary) 54%, rgba(16, 20, 28, 0.72));
}

.viewer-icon-button:disabled,
.photo-nav-button:disabled,
.thumbnail-scroll-button:disabled,
.mobile-action-icon:disabled {
  opacity: 0.35;
  cursor: default;
}

.viewer-icon-button:focus-visible,
.photo-nav-button:focus-visible,
.thumbnail-scroll-button:focus-visible,
.thumbnail-tile:focus-visible,
.details-close-button:focus-visible,
.details-action-button:focus-visible,
.mobile-sheet-toggle:focus-visible,
.mobile-action-icon:focus-visible {
  outline: 3px solid color-mix(in srgb, var(--gp-primary) 82%, white);
  outline-offset: 2px;
}

.photo-counter {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  min-width: 66px;
  padding: 7px 10px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  color: rgba(255, 255, 255, 0.9);
  background: rgba(16, 20, 28, 0.42);
  font-size: 12px;
  font-weight: 650;
  text-align: center;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.photo-nav-button {
  position: absolute;
  z-index: 4;
  top: 50%;
  transform: translateY(-50%);
}

.photo-nav-button:active:not(:disabled) {
  transform: translateY(-50%) scale(0.96);
}

.photo-nav-previous {
  left: 18px;
}

.photo-nav-next {
  right: 18px;
}

.thumbnail-navigation {
  position: absolute;
  z-index: 5;
  right: 50%;
  bottom: 18px;
  display: flex;
  align-items: center;
  width: auto;
  max-width: min(72%, 720px);
  padding: 7px;
  gap: 5px;
  transform: translateX(50%);
  border: 1px solid rgba(255, 255, 255, 0.13);
  border-radius: 16px;
  background: rgba(16, 20, 28, 0.62);
  box-shadow: 0 10px 34px rgba(0, 0, 0, 0.28);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
}

.thumbnail-scroll-button {
  width: 36px;
  height: 44px;
  min-width: 36px;
  border: 0;
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
}

.thumbnail-rail {
  display: flex;
  flex: 1;
  align-items: center;
  min-width: 0;
  max-width: 590px;
  padding: 2px;
  gap: 7px;
  overflow-x: auto;
  overscroll-behavior-inline: contain;
  scrollbar-width: none;
  scroll-behavior: smooth;
}

.thumbnail-rail::-webkit-scrollbar {
  display: none;
}

.thumbnail-tile {
  position: relative;
  width: 56px;
  height: 56px;
  flex: 0 0 56px;
  padding: 0;
  overflow: hidden;
  border: 2px solid transparent;
  border-radius: 11px;
  background: #202735;
  box-shadow: none;
  cursor: pointer;
  transition: border-color 150ms ease, opacity 150ms ease, transform 150ms ease;
}

.thumbnail-tile:hover {
  border-color: rgba(255, 255, 255, 0.55);
  transform: translateY(-1px);
}

.thumbnail-tile.is-active {
  border-color: var(--gp-primary-light);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.65);
}

.thumbnail-image,
.thumbnail-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumbnail-placeholder {
  color: rgba(255, 255, 255, 0.7);
  background: #202735;
}

.thumbnail-spinner {
  width: 20px;
  height: 20px;
}

.image-status {
  position: absolute;
  z-index: 2;
  top: 50%;
  left: 50%;
  display: grid;
  justify-items: center;
  transform: translate(-50%, -50%);
  color: rgba(255, 255, 255, 0.78);
  text-align: center;
}

.image-status p {
  margin: 0.75rem 0 0;
  font-size: 0.85rem;
}

.image-spinner {
  width: 42px;
  height: 42px;
}

.image-error i {
  margin: 0;
  color: #f87171;
  font-size: 2rem;
}

.photo-details-panel {
  position: relative;
  z-index: 7;
  display: flex;
  width: 320px;
  min-width: 0;
  flex-direction: column;
  overflow: hidden;
  border-left: 1px solid var(--gp-border-light);
  color: var(--gp-text-primary);
  background: var(--gp-surface-white);
  opacity: 1;
  transform: translateX(0);
  transition: opacity 180ms ease, transform 180ms ease;
}

.photo-viewer-content:not(.details-open) .photo-details-panel {
  opacity: 0;
  transform: translateX(24px);
  pointer-events: none;
}

.details-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 66px;
  padding: 0 20px;
  border-bottom: 1px solid var(--gp-border-light);
}

.details-panel-header strong {
  font-size: 14px;
  font-weight: 650;
}

.details-close-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: 0;
  border-radius: 11px;
  color: var(--gp-text-secondary);
  background: transparent;
  cursor: pointer;
}

.details-close-button:hover {
  color: var(--gp-text-primary);
  background: var(--gp-surface-light);
}

.details-panel-content {
  display: grid;
  align-content: start;
  padding: 20px;
  gap: 20px;
  overflow-y: auto;
}

.detail-item {
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr);
  align-items: start;
  gap: 10px;
}

.detail-item > i {
  margin-top: 3px;
  color: var(--gp-text-muted);
  font-size: 0.95rem;
}

.detail-item > div,
.location-card-copy > div {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.detail-item span,
.location-card-copy span {
  color: var(--gp-text-secondary);
  font-size: 11px;
}

.detail-item strong,
.location-card-copy strong {
  overflow-wrap: anywhere;
  color: var(--gp-text-primary);
  font-size: 13px;
  font-weight: 590;
  line-height: 1.35;
}

.location-card {
  display: grid;
  padding: 14px;
  gap: 14px;
  border: 1px solid var(--gp-border-light);
  border-radius: 15px;
  background: var(--gp-surface-light);
}

.location-card-copy {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
}

.location-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  color: var(--gp-primary) !important;
  background: color-mix(in srgb, var(--gp-primary) 12%, transparent);
}

.details-action-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  padding: 0 14px;
  gap: 8px;
  border: 1px solid var(--gp-border-medium);
  border-radius: 11px;
  color: var(--gp-text-primary);
  background: var(--gp-surface-white);
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
}

.details-action-button:hover:not(:disabled) {
  border-color: var(--gp-primary-light);
  color: var(--gp-primary);
}

.details-action-button:disabled {
  opacity: 0.55;
  cursor: default;
}

.mobile-photo-summary,
.mobile-details-sheet {
  display: none;
}

.p-dark .photo-viewer-content .thumbnail-navigation {
  border-color: rgba(255, 255, 255, 0.13) !important;
  background: rgba(16, 20, 28, 0.62) !important;
}

.p-dark .photo-viewer-content .thumbnail-tile {
  border-color: transparent !important;
  background: #202735 !important;
}

.p-dark .photo-viewer-content .thumbnail-tile.is-active {
  border-color: var(--gp-primary-light) !important;
}

@media (max-width: 768px) {
  :global(.photo-viewer-dialog) {
    width: 100vw !important;
    max-width: 100vw !important;
    height: 100dvh !important;
    max-height: 100dvh !important;
    margin: 0;
    border: 0;
    border-radius: 0;
  }

  :global(.photo-viewer-dialog .p-dialog-content) {
    border-radius: 0;
  }

  .photo-viewer-content,
  .photo-viewer-content:not(.details-open) {
    display: block;
    width: 100%;
    height: 100%;
    max-height: none;
  }

  .photo-stage {
    width: 100%;
    height: 100%;
  }

  .photo-stage::after {
    background: linear-gradient(
      180deg,
      rgba(0, 0, 0, 0.62) 0,
      rgba(0, 0, 0, 0) 24%,
      rgba(0, 0, 0, 0) 58%,
      rgba(0, 0, 0, 0.84) 88%
    );
  }

  .viewer-topbar {
    top: max(12px, env(safe-area-inset-top));
    right: 12px;
    left: 12px;
  }

  .viewer-title-copy,
  .desktop-toolbar-action {
    display: none;
  }

  .viewer-toolbar-actions {
    min-width: 44px;
  }

  .viewer-icon-button,
  .photo-nav-button,
  .mobile-action-icon {
    width: 44px;
    height: 44px;
  }

  .photo-nav-button {
    top: 48%;
  }

  .photo-nav-previous {
    left: 10px;
  }

  .photo-nav-next {
    right: 10px;
  }

  .thumbnail-navigation {
    right: 12px;
    bottom: calc(84px + env(safe-area-inset-bottom));
    left: 12px;
    width: auto;
    max-width: none;
    padding: 5px;
    transform: none;
    border-radius: 14px;
  }

  .thumbnail-scroll-button {
    display: none;
  }

  .thumbnail-rail {
    max-width: none;
    padding: 1px;
    gap: 6px;
  }

  .thumbnail-tile {
    width: 48px;
    height: 48px;
    flex-basis: 48px;
    border-radius: 10px;
  }

  .mobile-photo-summary {
    position: absolute;
    z-index: 4;
    right: 16px;
    bottom: calc(148px + env(safe-area-inset-bottom));
    left: 16px;
    display: grid;
    gap: 3px;
    color: #fff;
    text-shadow: 0 1px 5px rgba(0, 0, 0, 0.68);
  }

  .mobile-photo-summary strong {
    overflow: hidden;
    font-size: 14px;
    font-weight: 650;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-photo-summary span {
    color: rgba(255, 255, 255, 0.7);
    font-size: 11px;
  }

  .photo-details-panel {
    display: none;
  }

  .mobile-details-sheet {
    position: absolute;
    z-index: 8;
    right: 12px;
    bottom: max(12px, env(safe-area-inset-bottom));
    left: 12px;
    display: flex;
    max-height: 55dvh;
    flex-direction: column;
    overflow: hidden;
    border: 1px solid rgba(255, 255, 255, 0.13);
    border-radius: 17px;
    color: #fff;
    background: rgba(24, 29, 38, 0.82);
    box-shadow: 0 12px 36px rgba(0, 0, 0, 0.34);
    backdrop-filter: blur(20px);
    -webkit-backdrop-filter: blur(20px);
  }

  .details-open .mobile-details-sheet {
    color: var(--gp-text-primary);
    background: color-mix(in srgb, var(--gp-surface-white) 96%, transparent);
  }

  .mobile-sheet-bar {
    display: flex;
    min-height: 60px;
    align-items: stretch;
    padding: 0 7px 0 12px;
    gap: 6px;
  }

  .mobile-sheet-toggle {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 20px;
    align-items: center;
    flex: 1;
    min-width: 0;
    padding: 9px 4px;
    border: 0;
    color: inherit;
    background: transparent;
    text-align: left;
    cursor: pointer;
  }

  .mobile-sheet-grip {
    position: absolute;
    top: 5px;
    left: 50%;
    width: 34px;
    height: 3px;
    transform: translateX(-50%);
    border-radius: 999px;
    background: currentColor;
    opacity: 0.25;
  }

  .mobile-sheet-copy {
    display: grid;
    min-width: 0;
    gap: 2px;
  }

  .mobile-sheet-copy strong {
    font-size: 12px;
    font-weight: 650;
  }

  .mobile-sheet-copy small {
    overflow: hidden;
    color: currentColor;
    font-size: 10px;
    opacity: 0.62;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-compact-actions {
    display: flex;
    align-items: center;
    gap: 2px;
  }

  .mobile-action-icon {
    width: 42px;
    height: 42px;
    border: 0;
    background: transparent;
    box-shadow: none;
    backdrop-filter: none;
  }

  .mobile-sheet-content {
    display: grid;
    padding: 2px 16px 16px;
    gap: 15px;
    overflow-y: auto;
    border-top: 1px solid var(--gp-border-light);
  }

  .mobile-sheet-content .detail-item:first-child {
    margin-top: 14px;
  }

  .mobile-sheet-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .mobile-sheet-actions .details-action-button:only-child {
    grid-column: 1 / -1;
  }

  .details-open .thumbnail-navigation,
  .details-open .mobile-photo-summary {
    opacity: 0;
    visibility: hidden;
    pointer-events: none;
  }

  .photo-viewer-content.details-open .viewer-chrome {
    opacity: 1;
    visibility: visible;
    pointer-events: auto;
  }
}

@media (max-width: 480px) {
  .photo-nav-button {
    border-color: transparent;
    background: rgba(16, 20, 28, 0.4);
  }

  .mobile-sheet-actions {
    grid-template-columns: 1fr;
  }

  .mobile-sheet-actions .details-action-button {
    grid-column: auto;
  }
}

@media (prefers-reduced-motion: reduce) {
  .photo-viewer-content,
  .photo-details-panel,
  .viewer-chrome,
  .viewer-icon-button,
  .photo-nav-button,
  .thumbnail-scroll-button,
  .thumbnail-tile,
  .mobile-action-icon {
    transition: none !important;
  }

  .thumbnail-rail {
    scroll-behavior: auto;
  }
}
</style>
