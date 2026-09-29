<template></template>

<script setup>
import { computed, onMounted, onUnmounted, readonly, ref, watch } from 'vue'
import { useImmichStore } from '@/stores/immich'
import { useDateRangeStore } from '@/stores/dateRange'
import { usePhotoMapMarkersVector } from '@/maps/vector/composables/usePhotoMapMarkersVector'
import { isMapLibreMap } from '@/maps/vector/utils/maplibreLayerUtils'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'
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
  },
  // When provided (e.g. a public shared-link view), photos are rendered from this prop
  // instead of being fetched from the authenticated immichStore.
  photos: {
    type: Array,
    default: null
  },
  // Bearer token used to load thumbnails for externally-provided photos (shared-link access token)
  authToken: {
    type: String,
    default: null
  }
})

const emit = defineEmits(['photo-click', 'cluster-click', 'photo-hover', 'error', 'groups-change'])

const immichStore = useImmichStore()
const dateRangeStore = useDateRangeStore()

const baseLayerRef = ref(null)
const loading = ref(false)

const isExternallyProvided = computed(() => Array.isArray(props.photos))
const isConfigured = computed(() => isExternallyProvided.value || immichStore.isConfigured)

const {
  clearPhotoMarkers: clearConsistentPhotoMarkers,
  renderPhotoMarkers: renderConsistentPhotoMarkers,
  getCurrentGroups,
  getRenderedEntities,
  setExcludedGroupIndices
} = usePhotoMapMarkersVector({
  emit: (eventName, payload) => {
    if (eventName === 'photo-click') {
      emit('photo-click', payload)
    }
  },
  getThumbnailHeaders: () => (props.authToken ? { 'Authorization': `Bearer ${props.authToken}` } : {})
})

const renderPhotoMarkers = () => {
  if (!isMapLibreMap(props.map) || !isConfigured.value || !props.visible) {
    return
  }

  clearConsistentPhotoMarkers()
  renderConsistentPhotoMarkers(props.map, (isExternallyProvided.value ? props.photos : immichStore.photos) || [])
  emit('groups-change')
}

const fetchAndRenderPhotos = async () => {
  if (isExternallyProvided.value) {
    renderPhotoMarkers()
    return
  }

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
    renderPhotoMarkers()
  } catch (error) {
    emit('error', {
      type: 'fetch',
      message: formatApiErrorDetail(error, 'Failed to load photos from Immich'),
      error
    })
  } finally {
    loading.value = false
  }
}

const refreshPhotos = async () => {
  if (isExternallyProvided.value) {
    renderPhotoMarkers()
    return
  }

  if (!isConfigured.value || !props.visible || !isMapLibreMap(props.map)) {
    return
  }

  try {
    await immichStore.fetchPhotos(null, null, true)
    renderPhotoMarkers()
  } catch (error) {
    emit('error', {
      type: 'refresh',
      message: formatApiErrorDetail(error, 'Failed to refresh photos from Immich'),
      error
    })
  }
}

const clearPhotoMarkers = () => {
  clearConsistentPhotoMarkers()
  emit('groups-change')
}

watch(
  () => props.photos,
  () => {
    if (isExternallyProvided.value && props.visible) {
      renderPhotoMarkers()
    }
  },
  { deep: false }
)

watch(
  () => immichStore.photos,
  () => {
    if (!isExternallyProvided.value && props.visible) {
      renderPhotoMarkers()
    }
  },
  { deep: false }
)

watch(
  () => dateRangeStore.getCurrentDateRange,
  async (newRange) => {
    if (!isExternallyProvided.value && newRange && props.visible && isConfigured.value) {
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

    if (isExternallyProvided.value) {
      renderPhotoMarkers()
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
    if (isExternallyProvided.value) {
      return
    }

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
  if (isExternallyProvided.value) {
    renderPhotoMarkers()
    return
  }
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
  getCurrentGroups,
  getRenderedEntities,
  setExcludedGroupIndices,
  isLoading: readonly(loading)
})
</script>
