<template>
  <BaseCard v-if="showSection" class="immich-photos-card">
    <div class="immich-photos-header">
      <h3 class="section-title">{{ title }}</h3>
      <div class="immich-photos-header-right">
        <span v-if="latestPhotos.length > 0" class="immich-photos-count">
          {{ latestPhotos.length }}
          <template v-if="totalPhotos > latestPhotos.length">/ {{ totalPhotos }}</template>
        </span>
        <Button
          v-if="canLoadMore"
          label="Load 20 more"
          icon="pi pi-plus"
          size="small"
          text
          :loading="isLoadingMore"
          @click="handleLoadMore"
        />
        <Button
          v-if="canLoadAll"
          :label="loadAllButtonLabel"
          icon="pi pi-images"
          size="small"
          text
          :loading="galleryLoading"
          @click="handleLoadAll"
        />
      </div>
    </div>
    <div v-if="showLatestCoverageHint" class="immich-photos-coverage-hint">
      Showing newest {{ latestPhotos.length }} of {{ totalPhotos }}.
      Open full gallery for complete browsing.
    </div>

    <div v-if="latestPhotosLoading && latestPhotos.length === 0" class="immich-photos-loading">
      <ProgressSpinner />
    </div>
    <div v-else-if="latestPhotos.length === 0" class="immich-photos-empty">
      <i class="pi pi-camera"></i>
      <span>{{ emptyMessage }}</span>
    </div>
    <div v-else class="immich-photos-grid">
      <button
        v-for="(photo, index) in latestPhotos"
        :key="photo.id"
        class="immich-photo-tile"
        type="button"
        @click="openPhotoViewer(latestPhotos, index)"
      >
        <div class="immich-photo-thumb">
          <img
            v-if="getPhotoBlobUrl(photo.id)"
            :src="getPhotoBlobUrl(photo.id)"
            :alt="photo.originalFileName || 'Photo'"
          />
          <div v-else class="immich-photo-placeholder">
            <i class="pi pi-image"></i>
          </div>
        </div>
        <div class="immich-photo-date">
          {{ formatPhotoDate(photo.takenAt) }}
        </div>
      </button>
    </div>
    <div v-if="latestPhotosLoading && latestPhotos.length > 0" class="immich-photos-loading-inline">
      <ProgressSpinner stroke-width="6" />
      <span>Loading more photos...</span>
    </div>
    <div v-if="photosError" class="immich-photos-error">
      {{ photosError }}
    </div>
  </BaseCard>

  <PhotoViewerDialog
    v-model:visible="photoViewerVisible"
    :photos="photoViewerPhotos"
    :initial-photo-index="photoViewerIndex"
    :allow-show-on-map="showOnMapEnabled"
    :preloaded-blob-url-resolver="getPhotoBlobUrl"
    @show-on-map="handleShowOnMap"
    @close="closePhotoViewer"
  />

  <Dialog
    v-model:visible="galleryVisible"
    modal
    header="Photo Gallery"
    class="gp-dialog-xl"
    :style="{ width: '90vw', maxWidth: '1200px' }"
    :content-style="{ padding: '1rem' }"
  >
    <div class="gallery-meta">
      <span>{{ galleryPhotos.length }} / {{ totalPhotos }} photos</span>
    </div>

    <div v-if="galleryLoading && galleryPhotos.length === 0" class="immich-photos-loading">
      <ProgressSpinner />
    </div>
    <div v-else-if="galleryPhotos.length === 0" class="immich-photos-empty">
      <i class="pi pi-camera"></i>
      <span>No photos available.</span>
    </div>
    <div v-else class="immich-photos-grid gallery-grid">
      <button
        v-for="(photo, index) in galleryPagedPhotos"
        :key="`gallery-${photo.id}`"
        class="immich-photo-tile"
        type="button"
        @click="openGalleryPhotoViewer(index)"
      >
        <div class="immich-photo-thumb">
          <img
            v-if="getPhotoBlobUrl(photo.id)"
            :src="getPhotoBlobUrl(photo.id)"
            :alt="photo.originalFileName || 'Photo'"
          />
          <div v-else class="immich-photo-placeholder">
            <i class="pi pi-image"></i>
          </div>
        </div>
        <div class="immich-photo-date">
          {{ formatPhotoDate(photo.takenAt) }}
        </div>
      </button>
    </div>

    <Paginator
      v-if="galleryPhotos.length > galleryRows"
      :first="galleryFirst"
      :rows="galleryRows"
      :total-records="galleryPhotos.length"
      :rows-per-page-options="[40, 60, 100]"
      @page="handleGalleryPage"
    />
  </Dialog>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Button from 'primevue/button'
import ProgressSpinner from 'primevue/progressspinner'
import Dialog from 'primevue/dialog'
import Paginator from 'primevue/paginator'
import { useToast } from 'primevue/usetoast'

import BaseCard from '@/components/ui/base/BaseCard.vue'
import PhotoViewerDialog from '@/components/dialogs/PhotoViewerDialog.vue'

import { useImmichStore } from '@/stores/immich'
import apiService from '@/utils/apiService'
import { imageService } from '@/utils/imageService'
import { useTimezone } from '@/composables/useTimezone'

const DEFAULT_LIMIT = 20
const DEFAULT_FILTER_CACHE_TTL_MS = 60000
const DEFAULT_FILTER_CACHE_MAX_ENTRIES = 10
const MAP_COORDINATE_PRECISION = 4
const inMemoryFilterDatasetCache = new Map()
const inMemoryFilterDatasetInFlightRequests = new Map()

const props = defineProps({
  title: {
    type: String,
    required: true
  },
  searchParams: {
    type: Object,
    default: null
  },
  emptyMessage: {
    type: String,
    default: 'No Immich photos found.'
  },
  showOnMapEnabled: {
    type: Boolean,
    default: true
  },
  useStorePhotos: {
    type: Boolean,
    default: false
  },
  latestMaxOnPage: {
    type: Number,
    default: 100
  },
  inMemoryFilter: {
    type: Function,
    default: null
  },
  inMemoryFilterCacheKey: {
    type: String,
    default: ''
  },
  inMemoryFilterCacheTtlMs: {
    type: Number,
    default: DEFAULT_FILTER_CACHE_TTL_MS
  },
  inMemoryFilterCacheMaxEntries: {
    type: Number,
    default: DEFAULT_FILTER_CACHE_MAX_ENTRIES
  }
})

const emit = defineEmits(['latest-photos-change', 'map-markers-change', 'show-on-map'])

const toast = useToast()
const immichStore = useImmichStore()
const timezone = useTimezone()

const latestPhotos = ref([])
const totalPhotos = ref(0)
const latestPhotosLoading = ref(false)
const latestPhotosLoadingMode = ref(null)
const photosError = ref(null)
const mapMarkerGroups = ref([])
const mapMarkersLoading = ref(false)

const photoBlobUrls = ref(new Map())
const photoBlobLoadingIds = ref(new Set())
let photoBlobContextToken = 0

const currentLimit = ref(DEFAULT_LIMIT)
let latestRequestToken = 0
let galleryRequestToken = 0
let mapMarkersRequestToken = 0

const galleryVisible = ref(false)
const galleryLoading = ref(false)
const galleryPhotos = ref([])
const galleryFirst = ref(0)
const galleryRows = ref(60)

const photoViewerVisible = ref(false)
const photoViewerIndex = ref(0)
const photoViewerPhotos = ref([])

const immichConfigChecked = ref(false)
const mapMarkerPhotosCache = new Map()
const mapMarkerPhotosInFlight = new Map()

const showSection = computed(() => immichConfigChecked.value && immichStore.isConfigured)
const isLoadingMore = computed(() => latestPhotosLoading.value && latestPhotosLoadingMode.value === 'more')
const hasInMemoryFilter = computed(() => typeof props.inMemoryFilter === 'function')
const safeLatestMaxOnPage = computed(() => {
  const value = Number(props.latestMaxOnPage)
  if (!Number.isFinite(value) || value < DEFAULT_LIMIT) {
    return DEFAULT_LIMIT
  }
  return Math.floor(value)
})
const canLoadMore = computed(() =>
  totalPhotos.value > latestPhotos.value.length &&
  latestPhotos.value.length < safeLatestMaxOnPage.value &&
  !latestPhotosLoading.value
)
const canLoadAll = computed(() =>
  totalPhotos.value > DEFAULT_LIMIT ||
  galleryPhotos.value.length > 0
)
const galleryPagedPhotos = computed(() => {
  const start = galleryFirst.value
  const end = start + galleryRows.value
  return galleryPhotos.value.slice(start, end)
})
const showLatestCoverageHint = computed(() =>
  latestPhotos.value.length > 0 &&
  totalPhotos.value > latestPhotos.value.length
)
const loadAllButtonLabel = computed(() => {
  if (totalPhotos.value <= 0) {
    return 'Open full gallery'
  }
  return `Open full gallery (${totalPhotos.value})`
})
const normalizedInMemoryFilterCacheKey = computed(() => {
  return (props.inMemoryFilterCacheKey || '').trim()
})
const safeInMemoryFilterCacheTtlMs = computed(() => {
  const ttl = Number(props.inMemoryFilterCacheTtlMs)
  if (!Number.isFinite(ttl) || ttl <= 0) {
    return DEFAULT_FILTER_CACHE_TTL_MS
  }
  return Math.floor(ttl)
})
const safeInMemoryFilterCacheMaxEntries = computed(() => {
  const maxEntries = Number(props.inMemoryFilterCacheMaxEntries)
  if (!Number.isFinite(maxEntries) || maxEntries < 1) {
    return DEFAULT_FILTER_CACHE_MAX_ENTRIES
  }
  return Math.floor(maxEntries)
})

const normalizedBaseParams = computed(() => normalizeBaseParams(props.searchParams))
const hasValidSearchParams = computed(() => {
  const params = normalizedBaseParams.value
  return !!params?.startDate && !!params?.endDate
})
const searchSignature = computed(() => {
  const params = normalizedBaseParams.value
  if (!params) {
    return ''
  }

  return JSON.stringify({
    startDate: params.startDate,
    endDate: params.endDate,
    latitude: params.latitude ?? null,
    longitude: params.longitude ?? null,
    radiusMeters: params.radiusMeters ?? null,
    city: params.city ?? null,
    country: params.country ?? null
  })
})

const normalizePhoto = (photo) => ({
  ...photo,
  thumbnailUrl: photo.thumbnailUrl ? photo.thumbnailUrl.replace(/^\/api/, '') : null,
  downloadUrl: photo.downloadUrl ? photo.downloadUrl.replace(/^\/api/, '') : null
})

const buildMarkerKey = (latitude, longitude, precision = MAP_COORDINATE_PRECISION) => {
  if (typeof latitude !== 'number' || typeof longitude !== 'number') {
    return ''
  }

  const factor = 10 ** precision
  const roundedLat = Math.round(latitude * factor) / factor
  const roundedLng = Math.round(longitude * factor) / factor
  return `${roundedLat},${roundedLng}`
}

const buildMarkerGroupsFromPhotos = (photos, { includePhotos = false } = {}) => {
  const groups = new Map()

  ;(Array.isArray(photos) ? photos : []).forEach((photo) => {
    if (typeof photo?.latitude !== 'number' || typeof photo?.longitude !== 'number') {
      return
    }

    const markerKey = buildMarkerKey(photo.latitude, photo.longitude)
    if (!markerKey) {
      return
    }

    if (!groups.has(markerKey)) {
      groups.set(markerKey, {
        latitude: photo.latitude,
        longitude: photo.longitude,
        markerKey,
        count: 0,
        latestTakenAt: photo.takenAt || null,
        photos: includePhotos ? [] : undefined
      })
    }

    const group = groups.get(markerKey)
    group.count += 1

    if (photo.takenAt && (!group.latestTakenAt || photo.takenAt > group.latestTakenAt)) {
      group.latestTakenAt = photo.takenAt
    }

    if (includePhotos && Array.isArray(group.photos)) {
      group.photos.push(photo)
    }
  })

  return Array.from(groups.values())
    .sort((a, b) => {
      if (!a.latestTakenAt && !b.latestTakenAt) return 0
      if (!a.latestTakenAt) return 1
      if (!b.latestTakenAt) return -1
      return a.latestTakenAt < b.latestTakenAt ? 1 : -1
    })
}

const getInMemoryFilterDatasetCacheKey = () => {
  if (!hasInMemoryFilter.value || !searchSignature.value) {
    return null
  }

  const scopeKey = normalizedInMemoryFilterCacheKey.value || props.title
  return `${scopeKey}::${searchSignature.value}`
}

const evictExpiredInMemoryFilterDatasetCache = () => {
  const now = Date.now()
  inMemoryFilterDatasetCache.forEach((entry, key) => {
    if (!entry || entry.expiresAtEpochMs <= now) {
      inMemoryFilterDatasetCache.delete(key)
      inMemoryFilterDatasetInFlightRequests.delete(key)
    }
  })

  while (inMemoryFilterDatasetCache.size > safeInMemoryFilterCacheMaxEntries.value) {
    const oldestKey = inMemoryFilterDatasetCache.keys().next().value
    if (!oldestKey) {
      break
    }
    inMemoryFilterDatasetCache.delete(oldestKey)
    inMemoryFilterDatasetInFlightRequests.delete(oldestKey)
  }
}

const getCachedFilteredDataset = () => {
  const cacheKey = getInMemoryFilterDatasetCacheKey()
  if (!cacheKey) {
    return null
  }

  evictExpiredInMemoryFilterDatasetCache()
  const cached = inMemoryFilterDatasetCache.get(cacheKey)
  if (!cached) {
    return null
  }

  // Refresh insertion order to behave as LRU for max-size eviction.
  inMemoryFilterDatasetCache.delete(cacheKey)
  inMemoryFilterDatasetCache.set(cacheKey, cached)
  return cached.photos
}

const setCachedFilteredDataset = (photos) => {
  const cacheKey = getInMemoryFilterDatasetCacheKey()
  if (!cacheKey) {
    return
  }

  if (inMemoryFilterDatasetCache.has(cacheKey)) {
    inMemoryFilterDatasetCache.delete(cacheKey)
  }

  inMemoryFilterDatasetCache.set(cacheKey, {
    photos: Array.isArray(photos) ? [...photos] : [],
    expiresAtEpochMs: Date.now() + safeInMemoryFilterCacheTtlMs.value
  })
  evictExpiredInMemoryFilterDatasetCache()
}

const applyInMemoryFilter = (photos) => {
  if (!hasInMemoryFilter.value || !Array.isArray(photos)) {
    return Array.isArray(photos) ? photos : []
  }

  try {
    return photos.filter((photo) => props.inMemoryFilter(photo))
  } catch (err) {
    console.error('Failed to apply in-memory Immich photo filter:', err)
    return []
  }
}

const getFilteredPhotoDataset = async () => {
  const cacheKey = getInMemoryFilterDatasetCacheKey()
  if (!cacheKey) {
    const { photos } = await searchPhotos(null)
    return applyInMemoryFilter(photos)
  }

  const cached = getCachedFilteredDataset()
  if (cached) {
    return cached
  }

  const inFlight = inMemoryFilterDatasetInFlightRequests.get(cacheKey)
  if (inFlight) {
    return inFlight
  }

  const requestPromise = (async () => {
    const { photos } = await searchPhotos(null)
    const filteredPhotos = applyInMemoryFilter(photos)
    setCachedFilteredDataset(filteredPhotos)
    return filteredPhotos
  })()

  inMemoryFilterDatasetInFlightRequests.set(cacheKey, requestPromise)

  try {
    return await requestPromise
  } finally {
    if (inMemoryFilterDatasetInFlightRequests.get(cacheKey) === requestPromise) {
      inMemoryFilterDatasetInFlightRequests.delete(cacheKey)
    }
  }
}

const normalizeBaseParams = (rawParams) => {
  if (!rawParams || typeof rawParams !== 'object') {
    return null
  }

  const params = {
    startDate: rawParams.startDate || null,
    endDate: rawParams.endDate || null
  }

  if (typeof rawParams.latitude === 'number' && Number.isFinite(rawParams.latitude)) {
    params.latitude = rawParams.latitude
  }
  if (typeof rawParams.longitude === 'number' && Number.isFinite(rawParams.longitude)) {
    params.longitude = rawParams.longitude
  }
  if (typeof rawParams.radiusMeters === 'number' && Number.isFinite(rawParams.radiusMeters)) {
    params.radiusMeters = rawParams.radiusMeters
  }

  const city = typeof rawParams.city === 'string' ? rawParams.city.trim() : null
  if (city) {
    params.city = city
  }

  const country = typeof rawParams.country === 'string' ? rawParams.country.trim() : null
  if (country) {
    params.country = country
  }

  return params
}

const resetPhotoState = ({ resetLimit = true } = {}) => {
  latestRequestToken += 1
  galleryRequestToken += 1
  mapMarkersRequestToken += 1
  photoBlobContextToken += 1

  latestPhotos.value = []
  totalPhotos.value = 0
  latestPhotosLoading.value = false
  latestPhotosLoadingMode.value = null
  photosError.value = null
  mapMarkerGroups.value = []
  mapMarkersLoading.value = false
  mapMarkerPhotosCache.clear()
  mapMarkerPhotosInFlight.clear()

  galleryVisible.value = false
  galleryLoading.value = false
  galleryPhotos.value = []
  galleryFirst.value = 0
  galleryRows.value = 60

  photoViewerVisible.value = false
  photoViewerPhotos.value = []
  photoViewerIndex.value = 0

  if (resetLimit) {
    currentLimit.value = DEFAULT_LIMIT
  }

  clearPhotoBlobs()
}

const clearPhotoBlobs = () => {
  photoBlobUrls.value.forEach((blobUrl) => {
    imageService.revokeBlobUrl(blobUrl)
  })
  photoBlobUrls.value = new Map()
  photoBlobLoadingIds.value = new Set()
}

const sortPhotosByTakenAtDesc = (photos) => {
  return [...(Array.isArray(photos) ? photos : [])].sort((a, b) => {
    const takenAtA = a?.takenAt || ''
    const takenAtB = b?.takenAt || ''
    if (takenAtA === takenAtB) return 0
    return takenAtA < takenAtB ? 1 : -1
  })
}

const buildSearchParams = (limit = currentLimit.value) => {
  const baseParams = normalizedBaseParams.value
  if (!baseParams) {
    return null
  }

  const params = { ...baseParams }
  if (typeof limit === 'number' && Number.isFinite(limit) && limit > 0) {
    params.limit = limit
  }

  return params
}

const fetchStoreBackedPhotos = async () => {
  const baseParams = normalizedBaseParams.value
  if (!baseParams?.startDate || !baseParams?.endDate) {
    return []
  }

  await immichStore.fetchPhotos(baseParams.startDate, baseParams.endDate)
  return sortPhotosByTakenAtDesc((immichStore.photos || []).map(normalizePhoto))
}

const searchPhotos = async (limit = currentLimit.value) => {
  if (props.useStorePhotos) {
    const allPhotos = await fetchStoreBackedPhotos()
    const visiblePhotos = typeof limit === 'number' && Number.isFinite(limit) && limit > 0
      ? allPhotos.slice(0, limit)
      : allPhotos
    return {
      photos: visiblePhotos,
      totalCount: allPhotos.length
    }
  }

  const params = buildSearchParams(limit)
  if (!params) {
    return { photos: [], totalCount: 0 }
  }

  const response = await apiService.get('/users/me/immich/photos/search', params)
  const payload = response?.data || {}
  const photos = (Array.isArray(payload.photos) ? payload.photos : []).map(normalizePhoto)
  const totalCount = Number(payload.totalCount || photos.length)
  return { photos, totalCount }
}

const searchPhotoMapMarkers = async () => {
  if (props.useStorePhotos) {
    const allPhotos = await fetchStoreBackedPhotos()
    const markers = buildMarkerGroupsFromPhotos(allPhotos, { includePhotos: false }).map((marker) => ({
      latitude: marker.latitude,
      longitude: marker.longitude,
      count: Number(marker.count || 0),
      latestTakenAt: marker.latestTakenAt || null,
      markerKey: marker.markerKey
    }))
    const geotaggedPhotos = markers.reduce((acc, marker) => acc + Number(marker.count || 0), 0)

    return {
      markers,
      totalPhotos: allPhotos.length,
      geotaggedPhotos
    }
  }

  const params = buildSearchParams(null)
  if (!params) {
    return { markers: [], totalPhotos: 0, geotaggedPhotos: 0 }
  }

  params.coordinatePrecision = MAP_COORDINATE_PRECISION
  const response = await apiService.get('/users/me/immich/photos/map-markers', params)
  const payload = response?.data || {}
  const markers = Array.isArray(payload.markers) ? payload.markers : []
  const totalPhotosCount = Number(payload.totalPhotos || 0)
  const geotaggedPhotos = Number(payload.geotaggedPhotos || markers.reduce((acc, marker) => acc + Number(marker?.count || 0), 0))

  return {
    markers: markers.map((marker) => ({
      latitude: marker.latitude,
      longitude: marker.longitude,
      count: Number(marker.count || 0),
      latestTakenAt: marker.latestTakenAt || null,
      markerKey: buildMarkerKey(marker.latitude, marker.longitude)
    })),
    totalPhotos: totalPhotosCount,
    geotaggedPhotos
  }
}

const getKnownPhotosById = () => {
  const photoMap = new Map()
  ;[latestPhotos.value, galleryPhotos.value].forEach((collection) => {
    collection.forEach((photo) => {
      if (photo?.id) {
        photoMap.set(photo.id, photo)
      }
    })
  })

  mapMarkerPhotosCache.forEach((photos) => {
    if (!Array.isArray(photos)) {
      return
    }
    photos.forEach((photo) => {
      if (photo?.id && !photoMap.has(photo.id)) {
        photoMap.set(photo.id, photo)
      }
    })
  })

  return photoMap
}

const searchPhotosForMarker = async ({ markerLatitude, markerLongitude, limit = null }) => {
  if (props.useStorePhotos) {
    const allPhotos = await fetchStoreBackedPhotos()
    const markerKey = buildMarkerKey(markerLatitude, markerLongitude)
    const markerPhotos = allPhotos.filter((photo) => buildMarkerKey(photo?.latitude, photo?.longitude) === markerKey)
    const photos = typeof limit === 'number' && Number.isFinite(limit) && limit > 0
      ? markerPhotos.slice(0, limit)
      : markerPhotos

    return {
      photos,
      totalCount: markerPhotos.length
    }
  }

  const params = buildSearchParams(null)
  if (!params) {
    return { photos: [], totalCount: 0 }
  }

  params.coordinatePrecision = MAP_COORDINATE_PRECISION
  params.markerLatitude = markerLatitude
  params.markerLongitude = markerLongitude
  if (typeof limit === 'number' && Number.isFinite(limit) && limit > 0) {
    params.limit = limit
  }

  const response = await apiService.get('/users/me/immich/photos/map-marker/photos', params)
  const payload = response?.data || {}
  const photos = (Array.isArray(payload.photos) ? payload.photos : []).map(normalizePhoto)
  const totalCount = Number(payload.totalCount || photos.length)
  return { photos, totalCount }
}

const loadMissingPhotoBlobs = async (photos, contextToken = photoBlobContextToken) => {
  const photosToLoad = photos.filter((photo) => {
    return !!photo?.thumbnailUrl &&
      !!photo?.id &&
      !photoBlobUrls.value.has(photo.id) &&
      !photoBlobLoadingIds.value.has(photo.id)
  })

  if (photosToLoad.length === 0) {
    return
  }

  photosToLoad.forEach((photo) => {
    const nextLoading = new Set(photoBlobLoadingIds.value)
    nextLoading.add(photo.id)
    photoBlobLoadingIds.value = nextLoading
  })

  await Promise.all(photosToLoad.map(async (photo) => {
    let loadedBlobUrl = null

    try {
      loadedBlobUrl = await imageService.loadAuthenticatedImage(photo.thumbnailUrl)
      if (contextToken !== photoBlobContextToken) {
        if (loadedBlobUrl) {
          imageService.revokeBlobUrl(loadedBlobUrl)
        }
        return
      }

      const nextMap = new Map(photoBlobUrls.value)
      const previousBlob = nextMap.get(photo.id)
      if (previousBlob && previousBlob !== loadedBlobUrl) {
        imageService.revokeBlobUrl(previousBlob)
      }
      nextMap.set(photo.id, loadedBlobUrl)
      photoBlobUrls.value = nextMap
    } catch {
      if (loadedBlobUrl) {
        imageService.revokeBlobUrl(loadedBlobUrl)
      }
    } finally {
      if (contextToken === photoBlobContextToken) {
        const nextLoading = new Set(photoBlobLoadingIds.value)
        nextLoading.delete(photo.id)
        photoBlobLoadingIds.value = nextLoading
      }
    }
  }))
}

const fetchLatestPhotos = async ({ append = false, mode = 'initial' } = {}) => {
  if (!showSection.value || !hasValidSearchParams.value) {
    resetPhotoState({ resetLimit: false })
    return
  }

  const requestToken = ++latestRequestToken
  latestPhotosLoading.value = true
  latestPhotosLoadingMode.value = mode
  photosError.value = null

  try {
    if (hasInMemoryFilter.value) {
      const filteredDataset = await getFilteredPhotoDataset()
      if (requestToken !== latestRequestToken) {
        return
      }

      totalPhotos.value = filteredDataset.length
      const visibleLimit = Math.min(currentLimit.value, safeLatestMaxOnPage.value)
      latestPhotos.value = filteredDataset.slice(0, visibleLimit)
      await loadMissingPhotoBlobs(latestPhotos.value, photoBlobContextToken)
      return
    }

    const { photos, totalCount } = await searchPhotos(currentLimit.value)
    if (requestToken !== latestRequestToken) {
      return
    }

    totalPhotos.value = totalCount
    if (append) {
      const existingIds = new Set(latestPhotos.value.map((photo) => photo.id))
      const appendedPhotos = photos.filter((photo) => !existingIds.has(photo.id))
      latestPhotos.value = [...latestPhotos.value, ...appendedPhotos]
    } else {
      latestPhotos.value = photos
    }

    await loadMissingPhotoBlobs(latestPhotos.value, photoBlobContextToken)
  } catch (err) {
    if (requestToken !== latestRequestToken) {
      return
    }

    if (!append) {
      latestPhotos.value = []
      totalPhotos.value = 0
      clearPhotoBlobs()
    }
    photosError.value = err.response?.data?.message || 'Failed to load photos from Immich'
  } finally {
    if (requestToken === latestRequestToken) {
      latestPhotosLoading.value = false
      latestPhotosLoadingMode.value = null
    }
  }
}

const fetchMapMarkerGroups = async () => {
  if (!showSection.value || !hasValidSearchParams.value) {
    mapMarkerGroups.value = []
    return
  }

  const requestToken = ++mapMarkersRequestToken
  mapMarkersLoading.value = true

  try {
    if (hasInMemoryFilter.value) {
      const filteredDataset = await getFilteredPhotoDataset()
      if (requestToken !== mapMarkersRequestToken) {
        return
      }

      mapMarkerGroups.value = buildMarkerGroupsFromPhotos(filteredDataset, { includePhotos: true })
      totalPhotos.value = Math.max(totalPhotos.value, filteredDataset.length)
      return
    }

    const { markers, totalPhotos: markerTotalPhotos } = await searchPhotoMapMarkers()
    if (requestToken !== mapMarkersRequestToken) {
      return
    }

    mapMarkerGroups.value = markers
    if (Number.isFinite(markerTotalPhotos) && markerTotalPhotos > 0) {
      totalPhotos.value = Math.max(totalPhotos.value, markerTotalPhotos)
    }
  } catch (err) {
    if (requestToken !== mapMarkersRequestToken) {
      return
    }
    mapMarkerGroups.value = []
    console.warn('Failed to load map marker groups:', err)
  } finally {
    if (requestToken === mapMarkersRequestToken) {
      mapMarkersLoading.value = false
    }
  }
}

const fetchAllPhotosForGallery = async () => {
  const requestToken = ++galleryRequestToken
  galleryLoading.value = true
  photosError.value = null

  try {
    if (hasInMemoryFilter.value) {
      const filteredDataset = await getFilteredPhotoDataset()
      if (requestToken !== galleryRequestToken) {
        return
      }

      galleryPhotos.value = filteredDataset
      totalPhotos.value = filteredDataset.length
      galleryFirst.value = 0
      await loadMissingPhotoBlobs(galleryPagedPhotos.value, photoBlobContextToken)
      return
    }

    const { photos, totalCount } = await searchPhotos(null)
    if (requestToken !== galleryRequestToken) {
      return
    }

    galleryPhotos.value = photos
    totalPhotos.value = totalCount
    galleryFirst.value = 0
    await loadMissingPhotoBlobs(galleryPagedPhotos.value, photoBlobContextToken)
  } catch (err) {
    if (requestToken !== galleryRequestToken) {
      return
    }

    photosError.value = err.response?.data?.message || 'Failed to load full photo gallery'
    toast.add({
      severity: 'error',
      summary: 'Gallery Load Failed',
      detail: photosError.value,
      life: 5000
    })
  } finally {
    if (requestToken === galleryRequestToken) {
      galleryLoading.value = false
    }
  }
}

const ensureImmichConfig = async () => {
  if (immichConfigChecked.value) {
    return
  }

  try {
    await immichStore.fetchConfig()
  } catch (err) {
    console.warn('Failed to fetch Immich config:', err)
  } finally {
    immichConfigChecked.value = true
  }
}

const handleLoadMore = async () => {
  if (latestPhotosLoading.value || latestPhotos.value.length >= totalPhotos.value) {
    return
  }

  currentLimit.value = Math.min(
    currentLimit.value + DEFAULT_LIMIT,
    safeLatestMaxOnPage.value
  )
  await fetchLatestPhotos({ append: true, mode: 'more' })
}

const handleLoadAll = async () => {
  galleryVisible.value = true
  if (galleryLoading.value) {
    return
  }

  if (galleryPhotos.value.length >= totalPhotos.value && galleryPhotos.value.length > 0) {
    await loadMissingPhotoBlobs(galleryPagedPhotos.value, photoBlobContextToken)
    return
  }

  await fetchAllPhotosForGallery()
}

const handleGalleryPage = async (event) => {
  galleryFirst.value = event.first
  galleryRows.value = event.rows
  await loadMissingPhotoBlobs(galleryPagedPhotos.value, photoBlobContextToken)
}

const openPhotoViewer = (photos = latestPhotos.value, initialIndex = 0) => {
  const normalizedPhotos = (Array.isArray(photos) ? photos : []).map(normalizePhoto)
  if (normalizedPhotos.length === 0) {
    return
  }

  const safeIndex = Math.min(Math.max(0, initialIndex || 0), normalizedPhotos.length - 1)
  photoViewerPhotos.value = normalizedPhotos
  photoViewerIndex.value = safeIndex
  photoViewerVisible.value = true
}

const openGalleryPhotoViewer = (pageIndex) => {
  openPhotoViewer(galleryPhotos.value, galleryFirst.value + pageIndex)
}

const closePhotoViewer = () => {
  photoViewerVisible.value = false
  photoViewerPhotos.value = []
}

const handleShowOnMap = async (photo) => {
  if (!props.showOnMapEnabled) {
    return
  }

  if (galleryVisible.value) {
    galleryVisible.value = false
    await nextTick()
  }
  emit('show-on-map', photo)
}

const openPhotoViewerForMarker = async (markerGroup) => {
  if (Array.isArray(markerGroup?.photos) && markerGroup.photos.length > 0) {
    openPhotoViewer(markerGroup.photos, 0)
    return
  }

  const markerLatitude = Number(markerGroup?.latitude)
  const markerLongitude = Number(markerGroup?.longitude)
  if (!Number.isFinite(markerLatitude) || !Number.isFinite(markerLongitude)) {
    return
  }

  const markerKey = markerGroup?.markerKey || buildMarkerKey(markerLatitude, markerLongitude)
  if (!markerKey) {
    return
  }

  const cachedMarkerPhotos = mapMarkerPhotosCache.get(markerKey)
  if (Array.isArray(cachedMarkerPhotos) && cachedMarkerPhotos.length > 0) {
    openPhotoViewer(cachedMarkerPhotos, 0)
    return
  }

  const existingInFlight = mapMarkerPhotosInFlight.get(markerKey)
  if (existingInFlight) {
    const photos = await existingInFlight
    if (Array.isArray(photos) && photos.length > 0) {
      openPhotoViewer(photos, 0)
    }
    return
  }

  const loadPromise = (async () => {
    const { photos } = await searchPhotosForMarker({
      markerLatitude,
      markerLongitude,
      limit: null
    })

    const knownPhotosById = getKnownPhotosById()
    const mergedPhotos = photos.map((photo) => knownPhotosById.get(photo.id) || photo)
    mapMarkerPhotosCache.set(markerKey, mergedPhotos)
    return mergedPhotos
  })()

  mapMarkerPhotosInFlight.set(markerKey, loadPromise)
  try {
    const photos = await loadPromise
    if (Array.isArray(photos) && photos.length > 0) {
      openPhotoViewer(photos, 0)
    }
  } catch (err) {
    console.error('Failed to load marker photos:', err)
    toast.add({
      severity: 'error',
      summary: 'Map Preview Failed',
      detail: err.response?.data?.message || 'Failed to load photos for this map marker',
      life: 4000
    })
  } finally {
    if (mapMarkerPhotosInFlight.get(markerKey) === loadPromise) {
      mapMarkerPhotosInFlight.delete(markerKey)
    }
  }
}

const getPhotoBlobUrl = (photoId) => photoBlobUrls.value.get(photoId)

const formatPhotoDate = (dateValue) => {
  if (!dateValue) return 'Unknown date'
  return `${timezone.formatDateDisplay(dateValue)} ${timezone.formatTime(dateValue)}`
}

onMounted(async () => {
  await ensureImmichConfig()
})

onBeforeUnmount(() => {
  photoBlobContextToken += 1
  clearPhotoBlobs()
})

watch(latestPhotos, (photos) => {
  emit('latest-photos-change', photos)
}, { immediate: true })

watch(mapMarkerGroups, (groups) => {
  emit('map-markers-change', groups)
}, { immediate: true })

watch(
  [showSection, searchSignature, normalizedInMemoryFilterCacheKey],
  async ([visible]) => {
    resetPhotoState()
    if (!visible || !hasValidSearchParams.value) {
      return
    }
    await Promise.all([
      fetchLatestPhotos({ append: false, mode: 'initial' }),
      fetchMapMarkerGroups()
    ])
  },
  { immediate: true }
)

watch(
  () => galleryPagedPhotos.value,
  (photos) => {
    if (!galleryVisible.value || photos.length === 0) {
      return
    }
    loadMissingPhotoBlobs(photos, photoBlobContextToken)
  }
)

watch(galleryVisible, (visible) => {
  if (!visible || galleryPagedPhotos.value.length === 0) {
    return
  }
  loadMissingPhotoBlobs(galleryPagedPhotos.value, photoBlobContextToken)
})

defineExpose({
  openPhotoViewer,
  openPhotoViewerForMarker
})
</script>

<style scoped>
.immich-photos-card {
  margin-bottom: var(--gp-spacing-xl);
}

.immich-photos-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--gp-spacing-md);
}

.immich-photos-header .section-title {
  margin: 0;
}

.immich-photos-header-right {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
}

.immich-photos-count {
  background: var(--gp-surface-light);
  color: var(--gp-text-secondary);
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 0.8rem;
  font-weight: 600;
}

.immich-photos-coverage-hint {
  margin-bottom: var(--gp-spacing-sm);
  font-size: 0.82rem;
  color: var(--gp-text-secondary);
}

.immich-photos-loading,
.immich-photos-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--gp-spacing-sm);
  padding: var(--gp-spacing-lg);
  color: var(--gp-text-secondary);
}

.immich-photos-grid {
  display: grid;
  gap: var(--gp-spacing-sm);
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
}

.immich-photo-tile {
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-white);
  padding: var(--gp-spacing-xs);
  cursor: pointer;
  transition: all 0.2s ease;
}

.immich-photo-tile:hover {
  border-color: var(--gp-primary);
  transform: translateY(-1px);
}

.immich-photo-thumb {
  width: 100%;
  aspect-ratio: 1;
  border-radius: var(--gp-radius-small);
  overflow: hidden;
  background: var(--gp-surface-light);
}

.immich-photo-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.immich-photo-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--gp-text-secondary);
}

.immich-photo-date {
  margin-top: var(--gp-spacing-xs);
  font-size: 0.75rem;
  color: var(--gp-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.immich-photos-loading-inline {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  margin-top: var(--gp-spacing-sm);
  color: var(--gp-text-secondary);
  font-size: 0.85rem;
}

.immich-photos-loading-inline :deep(.p-progress-spinner) {
  width: 20px;
  height: 20px;
}

.gallery-meta {
  display: flex;
  justify-content: flex-end;
  margin-bottom: var(--gp-spacing-sm);
  color: var(--gp-text-secondary);
  font-size: 0.85rem;
}

.gallery-grid {
  max-height: 65vh;
  overflow-y: auto;
  margin-bottom: var(--gp-spacing-md);
  padding-right: 2px;
}

.immich-photos-error {
  margin-top: var(--gp-spacing-sm);
  font-size: 0.85rem;
  color: var(--gp-error);
}

@media (max-width: 768px) {
  .immich-photos-header {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--gp-spacing-sm);
  }

  .immich-photos-header-right {
    width: 100%;
    flex-wrap: wrap;
  }

  .gallery-grid {
    max-height: 58vh;
  }
}
</style>
