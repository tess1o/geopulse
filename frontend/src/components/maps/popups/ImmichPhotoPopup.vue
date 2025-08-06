<template>
  <div class="immich-photo-popup immich-hover-preview">
    <!-- Single Photo -->
    <template v-if="photoCount === 1">
      <div class="popup-title">📷 Photo</div>
      
      <!-- Photo Thumbnail -->
      <div class="popup-thumbnail">
        <div 
          v-if="!singlePhotoLoaded" 
          class="popup-image-loading"
          ref="singlePhotoLoadingRef"
        >
          <i class="pi pi-spin pi-spinner"></i>
          <div>Loading...</div>
        </div>
        <img 
          v-else
          :src="singlePhotoBlobUrl" 
          alt="Photo thumbnail" 
          class="popup-thumbnail-img"
        />
      </div>
      
      <!-- Photo Details -->
      <div v-if="firstPhoto.originalFileName" class="popup-filename">
        {{ firstPhoto.originalFileName }}
      </div>
      
      <div v-if="firstPhoto.takenAt" class="popup-time">
        {{ formatDate(firstPhoto.takenAt) }}
      </div>
    </template>

    <!-- Photo Cluster -->
    <template v-else>
      <div class="popup-title">📷 {{ photoCount }} Photos</div>
      
      <!-- Thumbnails Grid -->
      <div class="popup-thumbnail-grid">
        <div 
          v-for="(photo, index) in displayPhotos" 
          :key="photo.id"
          class="popup-grid-item"
          :ref="el => setGridItemRef(el, index)"
        >
          <div 
            v-if="!gridPhotosLoaded[index]" 
            class="popup-grid-thumb-loading"
          >
            <i class="pi pi-spin pi-spinner"></i>
          </div>
          <img 
            v-else
            :src="gridBlobUrls[index]" 
            alt="Photo thumbnail" 
            class="popup-grid-thumb"
          />
        </div>
        
        <div v-if="photoCount > 4" class="popup-more">
          +{{ photoCount - 4 }}
        </div>
      </div>
      
      <!-- Date Range -->
      <div v-if="dateRange" class="popup-time">
        {{ dateRange }}
      </div>
    </template>
    
    <!-- Action Hint -->
    <div class="popup-action">Click to view full size</div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { imageService } from '@/utils/imageService'

const props = defineProps({
  group: {
    type: Object,
    required: true
  }
})

// State
const singlePhotoLoaded = ref(false)
const singlePhotoBlobUrl = ref('')
const gridPhotosLoaded = ref([false, false, false, false])
const gridBlobUrls = ref(['', '', '', ''])
const gridItemRefs = ref([])

// Cache for blob URLs (shared across instances)
const blobCache = new Map()

// Computed
const photoCount = computed(() => props.group.photos.length)
const firstPhoto = computed(() => props.group.photos[0])
const displayPhotos = computed(() => props.group.photos.slice(0, 4))

const dateRange = computed(() => {
  const dates = props.group.photos
    .map(p => p.takenAt)
    .filter(Boolean)
    .map(d => new Date(d))
    .sort((a, b) => a - b)
  
  if (dates.length === 0) return null
  if (dates.length === 1) return dates[0].toLocaleDateString()
  
  return `${dates[0].toLocaleDateString()} - ${dates[dates.length - 1].toLocaleDateString()}`
})

// Methods
const formatDate = (dateString) => {
  if (!dateString) return ''
  try {
    return new Date(dateString).toLocaleString()
  } catch (error) {
    return dateString
  }
}

const setGridItemRef = (el, index) => {
  if (el) {
    gridItemRefs.value[index] = el
  }
}

const loadSinglePhoto = async () => {
  const photo = firstPhoto.value
  if (!photo?.thumbnailUrl) return
  
  try {
    // Check cache first
    if (blobCache.has(photo.id)) {
      singlePhotoBlobUrl.value = blobCache.get(photo.id)
      singlePhotoLoaded.value = true
      return
    }
    
    const blobUrl = await imageService.loadAuthenticatedImage(photo.thumbnailUrl)
    blobCache.set(photo.id, blobUrl)
    singlePhotoBlobUrl.value = blobUrl
    singlePhotoLoaded.value = true
  } catch (error) {
    console.warn('Failed to load popup thumbnail:', error)
    // Show error state
    if (singlePhotoLoadingRef.value) {
      singlePhotoLoadingRef.value.innerHTML = `<div style="color: #ef4444; font-size: 0.8rem;"><i class="pi pi-exclamation-triangle"></i> Failed to load</div>`
    }
  }
}

const loadGridPhotos = async () => {
  for (let i = 0; i < displayPhotos.value.length; i++) {
    const photo = displayPhotos.value[i]
    if (!photo?.thumbnailUrl) continue
    
    try {
      // Check cache first
      if (blobCache.has(photo.id)) {
        gridBlobUrls.value[i] = blobCache.get(photo.id)
        gridPhotosLoaded.value[i] = true
        continue
      }
      
      const blobUrl = await imageService.loadAuthenticatedImage(photo.thumbnailUrl)
      blobCache.set(photo.id, blobUrl)
      gridBlobUrls.value[i] = blobUrl
      gridPhotosLoaded.value[i] = true
    } catch (error) {
      console.warn('Failed to load grid thumbnail:', error)
      // Show error state
      const gridElement = gridItemRefs.value[i]?.querySelector('.popup-grid-thumb-loading')
      if (gridElement) {
        gridElement.innerHTML = `<div style="background: #fecaca; color: #dc2626; font-size: 0.7rem; padding: 0.25rem;"><i class="pi pi-times"></i></div>`
      }
    }
  }
}

// Lifecycle
onMounted(async () => {
  if (photoCount.value === 1) {
    await loadSinglePhoto()
  } else {
    await loadGridPhotos()
  }
})

onUnmounted(() => {
  // Clean up any blob URLs we created (but don't clear the shared cache)
  if (singlePhotoBlobUrl.value && singlePhotoBlobUrl.value.startsWith('blob:')) {
    // Don't revoke here since other popups might use the same blob URL
  }
})
</script>

<style scoped>
.immich-photo-popup {
  font-family: var(--font-family, system-ui);
  font-size: 0.875rem;
  line-height: 1.4;
  min-width: 200px;
  max-width: 300px;
  position: relative;
  z-index: 10001 !important;
}

.popup-title {
  font-weight: 600;
  color: var(--gp-text-primary, #1e293b);
  margin-bottom: 0.75rem;
  font-size: 1rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.popup-thumbnail {
  margin-bottom: 0.75rem;
  text-align: center;
}

.popup-thumbnail-img {
  max-width: 100%;
  max-height: 150px;
  border-radius: 6px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.popup-image-loading {
  text-align: center;
  padding: 1rem;
  background: rgba(99, 102, 241, 0.05);
  border-radius: 8px;
  color: #6366f1;
  font-size: 0.8rem;
  min-height: 60px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
}

.popup-thumbnail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 4px;
  margin-bottom: 0.75rem;
  max-width: 120px;
}

.popup-grid-item {
  width: 100%;
  aspect-ratio: 1;
}

.popup-grid-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.popup-grid-thumb-loading {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(99, 102, 241, 0.05);
  border-radius: 4px;
  color: #6366f1;
  font-size: 0.8rem;
}

.popup-more {
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(99, 102, 241, 0.1);
  color: #6366f1;
  font-weight: 600;
  font-size: 0.75rem;
  border-radius: 4px;
  aspect-ratio: 1;
}

.popup-filename {
  font-weight: 500;
  color: var(--gp-text-primary, #1e293b);
  margin-bottom: 0.5rem;
  font-size: 0.9rem;
  word-break: break-all;
}

.popup-time {
  color: var(--gp-text-secondary, #64748b);
  font-size: 0.8rem;
  margin-bottom: 0.5rem;
}

.popup-action {
  color: #6366f1;
  font-size: 0.8rem;
  font-weight: 500;
  text-align: center;
  padding: 0.25rem;
  background: rgba(99, 102, 241, 0.1);
  border-radius: 4px;
  margin-top: 0.5rem;
  cursor: pointer;
  transition: all 0.2s ease;
}

.popup-action:hover {
  background: rgba(99, 102, 241, 0.2);
  color: #4f46e5;
}

/* Dark theme */
.p-dark .popup-title,
.p-dark .popup-filename {
  color: rgba(255, 255, 255, 0.95) !important;
}

.p-dark .popup-time {
  color: rgba(255, 255, 255, 0.8) !important;
}

.p-dark .popup-action {
  color: #a5b4fc !important;
  background: rgba(99, 102, 241, 0.2) !important;
}

.p-dark .popup-action:hover {
  color: #c7d2fe !important;
  background: rgba(99, 102, 241, 0.3) !important;
}

.p-dark .popup-more {
  background: rgba(99, 102, 241, 0.2) !important;
  color: #a5b4fc !important;
}

.p-dark .popup-image-loading,
.p-dark .popup-grid-thumb-loading {
  background: rgba(99, 102, 241, 0.1) !important;
  color: #a5b4fc !important;
}

/* Mobile responsive */
@media (max-width: 768px) {
  .immich-photo-popup {
    font-size: 0.8rem;
    min-width: 180px;
    max-width: 250px;
  }
  
  .popup-title {
    font-size: 0.9rem;
    margin-bottom: 0.5rem;
  }
  
  .popup-thumbnail-img {
    max-height: 120px;
  }
  
  .popup-thumbnail-grid {
    max-width: 100px;
    gap: 3px;
  }
  
  .popup-filename {
    font-size: 0.8rem;
    margin-bottom: 0.4rem;
  }
  
  .popup-time {
    font-size: 0.75rem;
    margin-bottom: 0.4rem;
  }
  
  .popup-action {
    font-size: 0.75rem;
    padding: 0.2rem;
    margin-top: 0.4rem;
  }
}
</style>