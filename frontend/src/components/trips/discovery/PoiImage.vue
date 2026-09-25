<template>
  <div class="poi-image">
    <img
      v-if="blobUrl"
      :src="blobUrl"
      :alt="alt"
      loading="lazy"
      @error="handleError"
    />
    <div v-else class="poi-image-fallback">
      <i class="pi pi-image" />
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { imageService } from '@/utils/imageService'

/**
 * A place photo.
 *
 * The endpoint requires a bearer token, so a plain <img src> would be rejected - the
 * image is fetched with credentials and rendered from an object URL, mirroring how
 * Immich thumbnails are loaded elsewhere in the app.
 */
const props = defineProps({
  endpoint: { type: String, default: null },
  alt: { type: String, default: '' }
})

const emit = defineEmits(['failed'])

const blobUrl = ref(null)
let revoked = false

const release = () => {
  if (blobUrl.value) {
    URL.revokeObjectURL(blobUrl.value)
    blobUrl.value = null
  }
}

const load = async (endpoint) => {
  release()
  if (!endpoint) return

  try {
    const url = await imageService.loadAuthenticatedImage(endpoint)
    // Guard against a slow response landing after the prop changed or unmount.
    if (revoked) {
      if (url) URL.revokeObjectURL(url)
      return
    }
    blobUrl.value = url
  } catch (error) {
    // A missing or unlicensed photo is expected, not an error worth surfacing.
    blobUrl.value = null
    emit('failed')
  }
}

watch(() => props.endpoint, load, { immediate: true })

const handleError = () => {
  release()
  emit('failed')
}

onBeforeUnmount(() => {
  revoked = true
  release()
})
</script>

<style scoped>
.poi-image {
  position: relative;
  width: 100%;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  border-radius: var(--gp-radius-md, 8px);
  background: var(--gp-surface-100, #f3f4f6);
}

.poi-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.poi-image-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: var(--gp-text-secondary, #6b7280);
  font-size: 1.5rem;
}
</style>
