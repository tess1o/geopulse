<template>
  <Dialog
    :visible="visible"
    modal
    :header="dialogTitle"
    class="gp-dialog-xl"
    :content-style="{ padding: '0' }"
    :dismissable-mask="true"
    @update:visible="(value) => emit('update:visible', value)"
    @hide="handleClose"
  >
    <div class="photo-viewer-content">
      <!-- Photo Navigation -->
      <div v-if="photos.length > 1" class="photo-navigation">
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

      <!-- Main Photo Display -->
      <div class="photo-display">
        <img
          v-if="currentImageBlobUrl && !imageError"
          :src="currentImageBlobUrl"
          :alt="currentPhoto?.originalFileName"
          class="main-photo"
          @load="handleImageLoad"
          @error="handleImageError"
        />
        
        <!-- Loading State -->
        <div v-if="imageLoading" class="image-loading">
          <ProgressSpinner size="50px" />
          <p>Loading photo...</p>
        </div>
        
        <!-- Error State -->
        <div v-if="imageError" class="image-error">
          <i class="pi pi-exclamation-triangle"></i>
          <p>Failed to load photo thumbnail</p>
        </div>
      </div>

      <!-- Photo Details -->
      <div v-if="currentPhoto" class="photo-details">
        <div class="detail-row">
          <span class="detail-label">File:</span>
          <span class="detail-value">{{ currentPhoto.originalFileName }}</span>
        </div>
        
        <div v-if="currentPhoto.takenAt" class="detail-row">
          <span class="detail-label">Date:</span>
          <span class="detail-value">{{ formatDate(currentPhoto.takenAt) }}</span>
        </div>
        
        <div v-if="currentPhoto.latitude && currentPhoto.longitude" class="detail-row">
          <span class="detail-label">Location:</span>
          <span class="detail-value">{{ currentPhoto.latitude.toFixed(6) }}, {{ currentPhoto.longitude.toFixed(6) }}</span>
        </div>
      </div>

      <!-- Actions -->
      <div class="photo-actions">
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
  }
})

const emit = defineEmits(['update:visible', 'close'])
const toast = useToast()

// State
const currentIndex = ref(0)
const imageLoading = ref(false)
const imageError = ref(false)
const downloading = ref(false)
const currentImageBlobUrl = ref(null)
const imageLoadCache = ref(new Map()) // Cache for blob URLs

// Computed
const currentPhoto = computed(() => {
  return props.photos[currentIndex.value] || null
})

const dialogTitle = computed(() => {
  if (!currentPhoto.value) return 'Photo Viewer'
  
  if (props.photos.length > 1) {
    return `Photos (${currentIndex.value + 1}/${props.photos.length})`
  }
  
  return currentPhoto.value.originalFileName || 'Photo'
})

// Methods
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
  
  // Clean up blob URLs
  if (currentImageBlobUrl.value && currentImageBlobUrl.value.startsWith('blob:')) {
    imageService.revokeBlobUrl(currentImageBlobUrl.value)
  }
  currentImageBlobUrl.value = null
  
  // Clean up cache
  imageLoadCache.value.forEach(blobUrl => {
    if (blobUrl && blobUrl.startsWith('blob:')) {
      imageService.revokeBlobUrl(blobUrl)
    }
  })
  imageLoadCache.value.clear()
}

const previousPhoto = () => {
  if (currentIndex.value > 0) {
    currentIndex.value--
    resetImageState()
  }
}

const nextPhoto = () => {
  if (currentIndex.value < props.photos.length - 1) {
    currentIndex.value++
    resetImageState()
  }
}

const resetImageState = () => {
  imageLoading.value = false
  imageError.value = false
  currentImageBlobUrl.value = null
}

const loadCurrentPhoto = async () => {
  if (!currentPhoto.value) return
  
  const photo = currentPhoto.value
  const cacheKey = photo.id
  
  // Check cache first
  if (imageLoadCache.value.has(cacheKey)) {
    currentImageBlobUrl.value = imageLoadCache.value.get(cacheKey)
    imageLoading.value = false
    imageError.value = false
    return
  }
  
  imageLoading.value = true
  imageError.value = false
  
  try {
    // Use imageService for authenticated requests (avoids CORS issues)
    const imageUrl = photo.thumbnailUrl
    
    if (!imageUrl) {
      throw new Error('No thumbnail URL available')
    }
    
    const blobUrl = await imageService.loadAuthenticatedImage(imageUrl)
    
    // Cache the blob URL
    imageLoadCache.value.set(cacheKey, blobUrl)
    currentImageBlobUrl.value = blobUrl
    
    imageLoading.value = false
    imageError.value = false
  } catch (error) {
    console.error('Failed to load photo thumbnail:', error)
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
    return timezone.format(dateString, 'YYYY-MM-DD HH:mm:ss')
  } catch (error) {
    return dateString
  }
}

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

// Watch for dialog visibility changes
watch(() => props.visible, (newVisible) => {
  if (newVisible) {
    currentIndex.value = Math.max(0, props.initialPhotoIndex || 0)
    resetImageState()
    
    // Add keyboard listener
    nextTick(() => {
      window.addEventListener('keydown', handleKeydown)
    })
  } else {
    // Remove keyboard listener
    window.removeEventListener('keydown', handleKeydown)
  }
})

// Watch for photo changes to trigger loading
watch(() => currentPhoto.value, () => {
  if (currentPhoto.value) {
    loadCurrentPhoto()
  }
})

// Watch for initial photo index changes
watch(() => props.initialPhotoIndex, (newIndex) => {
  if (props.visible && newIndex >= 0 && newIndex < props.photos.length) {
    currentIndex.value = newIndex
    resetImageState()
    loadCurrentPhoto()
  }
})

// Cleanup on unmount
onUnmounted(() => {
  resetState()
})
</script>

<style scoped>
.photo-viewer-content {
  display: flex;
  flex-direction: column;
  max-height: 80vh;
}

.photo-navigation {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem;
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
  min-height: 400px;
  max-height: 60vh;
  overflow: hidden;
  background: var(--gp-surface-light, #f8fafc);
}

.main-photo {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: 4px;
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
  padding: 1rem;
  border-bottom: 1px solid var(--gp-border-light, rgba(0, 0, 0, 0.1));
  background: var(--gp-surface-white, white);
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
  padding: 1rem;
  background: var(--gp-surface-light, #f8fafc);
}

/* Dark theme */
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

/* Mobile responsive */
@media (max-width: 768px) {
  .photo-viewer-content {
    max-height: 90vh;
  }
  
  .photo-navigation {
    padding: 0.75rem;
  }
  
  .photo-display {
    min-height: 300px;
    max-height: 50vh;
  }
  
  .photo-details {
    padding: 0.75rem;
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
    padding: 0.75rem;
    flex-wrap: wrap;
  }
}
</style>