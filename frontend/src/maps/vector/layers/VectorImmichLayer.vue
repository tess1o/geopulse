<template></template>

<script setup>
import { computed, onMounted, onUnmounted, readonly, ref, watch } from 'vue'
import { useImmichStore } from '@/stores/immich'
import { useDateRangeStore } from '@/stores/dateRange'
import { usePhotoMapMarkersVector } from '@/maps/vector/composables/usePhotoMapMarkersVector'
import { isMapLibreMap } from '@/maps/vector/utils/maplibreLayerUtils'
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
} = usePhotoMapMarkersVector({
  emit: (eventName, payload) => {
    if (eventName === 'photo-click') {
      emit('photo-click', payload)
    }
  }
})

const renderPhotoMarkers = () => {
  if (!isMapLibreMap(props.map) || !isConfigured.value || !props.visible) {
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

  if (!isMapLibreMap(props.map)) {
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
  if (!isConfigured.value || !props.visible || !isMapLibreMap(props.map)) {
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

watch(
  () => props.map,
  () => {
    if (props.visible) {
      renderPhotoMarkers()
    }
  }
)

onMounted(async () => {
  try {
    await immichStore.fetchConfig()
  } catch {
    // no-op
  }
})

onUnmounted(() => {
  clearPhotoMarkers()
})

defineExpose({
  baseLayerRef: readonly(baseLayerRef),
  refreshPhotos,
  clearPhotoMarkers,
  isLoading: readonly(loading)
})
</script>
