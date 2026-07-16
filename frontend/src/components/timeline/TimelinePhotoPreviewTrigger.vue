<template>
  <button
    v-if="hasPhotos"
    type="button"
    class="photo-trigger"
    :class="{ 'photo-trigger--thumbnail': showSinglePhotoThumbnail }"
    :aria-label="triggerLabel"
    :style="triggerStyle"
    @click.stop="openPhotoViewer"
  >
    <img
      v-if="showSinglePhotoThumbnail"
      :src="singlePhotoThumbnailBlobUrl"
      :alt="singlePhotoAlt"
      class="photo-trigger-thumbnail"
    />
    <template v-else>
      <i class="pi pi-camera" />
      <span>{{ photos.length }}</span>
    </template>
  </button>

  <PhotoViewerDialog
    v-model:visible="photoViewerVisible"
    :photos="photos"
    :initial-photo-index="photoViewerIndex"
    :allow-show-on-map="allowShowOnMap"
    :preloaded-blob-url-resolver="resolvePreloadedBlobUrl"
    @show-on-map="handlePhotoShowOnMap"
    @close="closePhotoViewer"
  />
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import PhotoViewerDialog from '@/components/dialogs/PhotoViewerDialog.vue'
import { getPhotoThumbnailBlobUrl, hasPhotoThumbnail } from '@/utils/immichPhotoThumbnails'

const props = defineProps({
  photos: {
    type: Array,
    default: () => []
  },
  allowShowOnMap: {
    type: Boolean,
    default: true
  },
  accentColor: {
    type: String,
    default: 'var(--gp-primary)'
  },
  hoverBgColor: {
    type: String,
    default: 'var(--gp-primary-light)'
  }
})

const emit = defineEmits(['photo-show-on-map'])

const photoViewerVisible = ref(false)
const photoViewerIndex = ref(0)
const singlePhotoThumbnailBlobUrl = ref(null)
let thumbnailLoadToken = 0

const hasPhotos = computed(() => Array.isArray(props.photos) && props.photos.length > 0)
const singlePhoto = computed(() => {
  return Array.isArray(props.photos) && props.photos.length === 1 ? props.photos[0] : null
})
const showSinglePhotoThumbnail = computed(() => Boolean(singlePhoto.value && singlePhotoThumbnailBlobUrl.value))
const triggerLabel = computed(() => {
  if (props.photos.length === 1) {
    return 'Open photo'
  }

  return `Open ${props.photos.length} photos`
})
const singlePhotoAlt = computed(() => singlePhoto.value?.originalFileName || 'Photo')
const triggerStyle = computed(() => ({
  '--photo-trigger-color': props.accentColor,
  '--photo-trigger-hover-bg': props.hoverBgColor
}))

watch(
  singlePhoto,
  async (photo) => {
    const loadToken = ++thumbnailLoadToken
    singlePhotoThumbnailBlobUrl.value = null

    if (!hasPhotoThumbnail(photo)) {
      return
    }

    try {
      const blobUrl = await getPhotoThumbnailBlobUrl(photo)
      if (loadToken === thumbnailLoadToken && blobUrl) {
        singlePhotoThumbnailBlobUrl.value = blobUrl
      }
    } catch {
      if (loadToken === thumbnailLoadToken) {
        singlePhotoThumbnailBlobUrl.value = null
      }
    }
  },
  { immediate: true }
)

const openPhotoViewer = () => {
  photoViewerIndex.value = 0
  photoViewerVisible.value = true
}

const closePhotoViewer = () => {
  photoViewerVisible.value = false
  photoViewerIndex.value = 0
}

const handlePhotoShowOnMap = (photo) => {
  emit('photo-show-on-map', photo)
}

const resolvePreloadedBlobUrl = (photoId) => {
  if (!singlePhoto.value || !singlePhotoThumbnailBlobUrl.value) {
    return null
  }

  return String(singlePhoto.value.id) === String(photoId)
    ? singlePhotoThumbnailBlobUrl.value
    : null
}
</script>

<style scoped>
.photo-trigger {
  border: 1px solid var(--gp-primary-light);
  border-radius: 999px;
  background: var(--gp-surface-white);
  color: var(--photo-trigger-color);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  font-size: 0.75rem;
  font-weight: 700;
  cursor: pointer;
}

.photo-trigger:hover {
  background: var(--photo-trigger-hover-bg);
}

.photo-trigger--thumbnail {
  width: 32px;
  height: 32px;
  padding: 0;
  gap: 0;
  border-color: #ffffff;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.16);
}

.photo-trigger--thumbnail:hover {
  background: var(--gp-surface-white);
}

.photo-trigger-thumbnail {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
</style>
