<template>
  <BaseLayer
    ref="baseLayerRef"
    :map="map"
    :visible="visible"
    @layer-ready="handleLayerReady"
  />
</template>

<script setup>
import { ref, watch, computed, readonly, onMounted, onUnmounted } from 'vue'
import BaseLayer from '@/components/maps/layers/BaseLayer.vue'
import { usePhotoMapMarkers } from '@/composables/usePhotoMapMarkers'
import { useImmichStore } from '@/stores/immich'
import { useDateRangeStore } from '@/stores/dateRange'
import '@/styles/photo-map-markers.css'

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  visible: {
    type: Boolean,
    default: true
  },
  markerOptions: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['photo-click', 'cluster-click', 'photo-hover', 'error'])

const immichStore = useImmichStore()
const dateRangeStore = useDateRangeStore()

const baseLayerRef = ref(null)
const loading = ref(false)

const isConfigured = computed(() => immichStore.isConfigured)

const {
  clearPhotoMarkers: clearConsistentPhotoMarkers,
  renderPhotoMarkers: renderConsistentPhotoMarkers
} = usePhotoMapMarkers({
  emit: (eventName, payload) => {
    if (eventName === 'photo-click') {
      emit('photo-click', payload)
    }
  }
})

const handleLayerReady = async () => {
  if (isConfigured.value && props.visible) {
    await fetchAndRenderPhotos()
  }
}

const renderPhotoMarkers = () => {
  if (!baseLayerRef.value?.isReady || !isConfigured.value || !props.visible) {
    return
  }

  clearConsistentPhotoMarkers()
  renderConsistentPhotoMarkers(props.map, immichStore.photos || [])
}

const fetchAndRenderPhotos = async () => {
  if (!isConfigured.value) {
    emit('error', {
      type: 'config',
      message: 'Immich is not configured. Please set up your Immich server first.',
      error: new Error('Immich not configured')
    })
    return
  }

  try {
    loading.value = true
    await immichStore.fetchPhotos()

    if (immichStore.photosError) {
      emit('error', {
        type: 'fetch',
        message: immichStore.photosError,
        error: new Error(immichStore.photosError)
      })
      return
    }

    renderPhotoMarkers()
  } catch (error) {
    console.error('Failed to fetch Immich photos:', error)
    emit('error', {
      type: 'fetch',
      message: error.userMessage || 'Failed to load photos from Immich',
      error
    })
  } finally {
    loading.value = false
  }
}

const refreshPhotos = async () => {
  if (!isConfigured.value || !props.visible) {
    return
  }

  try {
    await immichStore.fetchPhotos(null, null, true)

    if (immichStore.photosError) {
      emit('error', {
        type: 'refresh',
        message: immichStore.photosError,
        error: new Error(immichStore.photosError)
      })
      return
    }

    renderPhotoMarkers()
  } catch (error) {
    console.error('Failed to refresh Immich photos:', error)
    emit('error', {
      type: 'refresh',
      message: error.userMessage || 'Failed to refresh photos from Immich',
      error
    })
  }
}

const clearPhotoMarkers = () => {
  clearConsistentPhotoMarkers()
}

watch(
  () => immichStore.photos,
  () => {
    if (props.visible) {
      renderPhotoMarkers()
    }
  },
  { deep: false }
)

watch(
  () => dateRangeStore.getCurrentDateRange,
  async (newRange) => {
    if (newRange && props.visible && isConfigured.value) {
      await fetchAndRenderPhotos()
    }
  },
  { deep: true }
)

watch(
  () => props.visible,
  async (newVisible) => {
    if (!newVisible) {
      clearPhotoMarkers()
      return
    }

    if (!isConfigured.value) {
      return
    }

    if (immichStore.hasPhotos) {
      renderPhotoMarkers()
      return
    }

    await fetchAndRenderPhotos()
  }
)

watch(
  () => immichStore.isConfigured,
  async (newConfigured) => {
    if (newConfigured && props.visible) {
      await fetchAndRenderPhotos()
      return
    }

    if (!newConfigured) {
      clearPhotoMarkers()
    }
  }
)

onMounted(async () => {
  try {
    await immichStore.fetchConfig()
  } catch (error) {
    console.error('Failed to fetch Immich config on mount:', error)
  }
})

onUnmounted(() => {
  clearPhotoMarkers()
})

defineExpose({
  baseLayerRef,
  refreshPhotos,
  clearPhotoMarkers,
  isLoading: readonly(loading)
})
</script>

<style scoped>
/* Marker styles are provided globally by photo-map-markers.css */
</style>
