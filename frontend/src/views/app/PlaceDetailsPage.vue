<template>
  <AppLayout variant="default">
    <PageContainer
      :loading="isLoading"
      max-width="xlarge"
    >
    <!-- Loading State -->
    <template v-if="isLoading && !placeDetails">
      <div class="loading-container">
        <ProgressSpinner />
        <p class="loading-text">{{ t('place.detailsPage.loading') }}</p>
      </div>
    </template>

    <!-- Error State -->
    <template v-else-if="error">
      <BaseCard>
        <div class="error-container">
          <i class="pi pi-exclamation-triangle error-icon"></i>
          <h3 class="error-title">{{ t('place.detailsPage.errorTitle') }}</h3>
          <p class="error-message">{{ error }}</p>
          <Button
            :label="t('common.tryAgain')"
            icon="pi pi-refresh"
            @click="loadPlaceData"
          />
        </div>
      </BaseCard>
    </template>

    <!-- Place Details Content -->
    <template v-else-if="placeDetails">
      <LocationDetailsHeader
        :title="pageTitle"
        :subtitle="placeSubtitle"
        :icon="placeIcon"
        :back-label="t('common.back')"
        @back="goBack"
      >
        <template v-if="placeDetails.city || placeDetails.country" #subtitle>
          <RouterLink
            v-if="placeDetails.city"
            :to="`/app/location-analytics/city/${encodeURIComponent(placeDetails.city)}`"
            class="detail-link"
          >{{ placeDetails.city }}</RouterLink><template v-if="placeDetails.city && placeDetails.country">, </template><RouterLink
            v-if="placeDetails.country"
            :to="`/app/location-analytics/country/${encodeURIComponent(placeDetails.country)}`"
            class="detail-link"
          >{{ placeDetails.country }}</RouterLink>
        </template>
        <template #metadata>
          <Tag :value="placeTypeLabel" :severity="placeType === 'favorite' ? 'success' : 'info'" />
          <span v-if="displayCoordinates" class="place-coordinates">
            <i class="pi pi-map-marker" aria-hidden="true" />
            {{ displayCoordinates }}
          </span>
        </template>
        <template #actions>
          <Button
            v-if="placeType === 'favorite' && placeDetails.canEdit"
            :label="t('place.detailsPage.edit')"
            icon="pi pi-pencil"
            outlined
            @click="handleOpenEditDialog"
          />
          <Button
            v-if="placeType === 'geocoding'"
            :label="t('place.detailsPage.editDetails')"
            icon="pi pi-cog"
            outlined
            @click="handleOpenEditDialog"
          />
          <Button
            v-if="placeType === 'geocoding'"
            :label="t('place.detailsPage.createFavorite')"
            icon="pi pi-heart"
            @click="handleCreateFavorite"
          />
        </template>
      </LocationDetailsHeader>

      <!-- Related Favorite Notice (for geocoding with no visits) - Show FIRST -->
      <BaseCard v-if="placeDetails.relatedFavorite" class="related-favorite-notice">
        <div class="notice-content">
          <div class="notice-icon">
            <i class="pi pi-info-circle"></i>
          </div>
          <div class="notice-body">
            <h3 class="notice-title">
              {{ relatedFavoriteTitle }}
            </h3>
            <p class="notice-message">
              {{ relatedFavoriteMessage }}
            </p>
            <div class="favorite-info">
              <div class="favorite-name-row">
                <i class="pi pi-map-marker favorite-icon"></i>
                <span class="favorite-name">{{ placeDetails.relatedFavorite.name }}</span>
                <span class="favorite-distance" v-if="placeDetails.relatedFavorite.distanceMeters > 0">
                  {{ t('place.detailsPage.relatedFavorite.distanceAway', { distance: formatDistance(placeDetails.relatedFavorite.distanceMeters) }) }}
                </span>
              </div>
              <div class="favorite-stats" v-if="placeDetails.relatedFavorite.totalVisits">
                <i class="pi pi-clock"></i>
                <span>{{ t('place.detailsPage.relatedFavorite.visitsTracked', { count: placeDetails.relatedFavorite.totalVisits }) }}</span>
              </div>
            </div>
            <Button
              :label="t('place.detailsPage.relatedFavorite.viewDetails', { name: placeDetails.relatedFavorite.name })"
              icon="pi pi-arrow-right"
              @click="navigateToRelatedFavorite"
              class="view-favorite-button"
            />
          </div>
        </div>
      </BaseCard>

      <!-- Statistics - Only show if NOT a related favorite case -->
      <PlaceStatsCard
        v-if="placeDetails.statistics && !placeDetails.relatedFavorite"
        :statistics="placeDetails.statistics"
      />

      <!-- Map -->
      <PlaceMap
        v-if="placeDetails.geometry"
        ref="placeMapRef"
        :key="`place-map-${placeType}-${placeId}`"
        :geometry="placeDetails.geometry"
        :location-name="placeDetails.locationName"
        :photos="placePhotosForMap"
        :photo-marker-groups="placeMarkerGroupsForMap"
        :notes="placeNotesForMap"
        @photo-click="handleMapPhotoClick"
      />

      <ImmichLatestPhotosSection
        v-if="placeImmichSearchParams"
        ref="placePhotosSectionRef"
        :title="t('place.detailsPage.latestPhotosTitle', { name: placeDetails.locationName })"
        :search-params="placeImmichSearchParams"
        :in-memory-filter="placePhotoInMemoryFilter"
        :in-memory-filter-cache-key="placePhotoFilterCacheKey"
        :empty-message="t('place.detailsPage.noPhotosMessage')"
        @latest-photos-change="handlePlacePhotosChange"
        @map-markers-change="handlePlaceMarkerGroupsChange"
        @show-on-map="handlePlacePhotoShowOnMap"
      />

      <PlaceNotesSection
        v-if="placeNotesSearchParams"
        :title="t('place.detailsPage.notesTitle', { name: placeDetails.locationName })"
        :search-params="placeNotesSearchParams"
        :in-memory-filter="placeNoteInMemoryFilter"
        :in-memory-filter-cache-key="placePhotoFilterCacheKey"
        :empty-message="t('place.detailsPage.noNotesMessage')"
        @notes-change="handlePlaceNotesChange"
      />

      <!-- Visits Table -->
      <PlaceVisitsTable
        v-if="!placeDetails.relatedFavorite"
        :visits="placeVisits"
        :pagination="pagination"
        :loading="visitsLoading"
        :enable-timeline-navigation="true"
        @page-change="handlePageChange"
        @sort-change="handleSortChange"
        @export="handleExportVisits"
      />
    </template>

    <!-- Edit Dialogs -->
    <EditFavoriteDialog
      v-if="placeType === 'favorite' && selectedFavorite"
      :visible="showFavoriteDialog"
      :header="t('place.detailsPage.editFavoriteDialogHeader')"
      :favorite-location="selectedFavorite"
      @edit-favorite="(data) => handleFavoriteSave(data, { onSuccess: () => loadPlaceData() })"
      @close="closeFavoriteEditor"
    />

    <GeocodingEditDialog
      v-if="placeType === 'geocoding'"
      :visible="showGeocodingEditDialog"
      :geocoding-result="editGeocodingData"
      @save="handleSaveGeocoding"
      @close="showGeocodingEditDialog = false"
    />

    <!-- Create Favorite Dialog -->
    <Dialog
      v-model:visible="showCreateFavoriteDialog"
      modal
      :header="t('place.detailsPage.createFavoriteDialog.header')"
      :style="{ width: '450px' }"
    >
      <div class="create-favorite-content">
        <p class="dialog-message">
          {{ t('place.detailsPage.createFavoriteDialog.message') }}
        </p>
        <div class="form-field">
          <label for="favorite-name">{{ t('place.detailsPage.createFavoriteDialog.nameLabel') }}</label>
          <InputText
            id="favorite-name"
            v-model="newFavoriteName"
            :placeholder="t('place.detailsPage.createFavoriteDialog.namePlaceholder')"
            autofocus
            @keyup.enter="submitCreateFavorite"
            style="width: 100%"
          />
        </div>
        <small class="coordinates-info">
          {{ t('place.detailsPage.createFavoriteDialog.coordinatesInfo', { lat: placeDetails?.geometry?.latitude?.toFixed(6), lon: placeDetails?.geometry?.longitude?.toFixed(6) }) }}
        </small>
      </div>
      <template #footer>
        <Button
          :label="t('common.cancel')"
          severity="secondary"
          @click="showCreateFavoriteDialog = false"
          outlined
        />
        <Button
          :label="t('place.detailsPage.createFavorite')"
          icon="pi pi-heart"
          severity="success"
          @click="submitCreateFavorite"
          :disabled="!newFavoriteName.trim()"
        />
      </template>
    </Dialog>

    <!-- Timeline Regeneration Modal -->
    <TimelineRegenerationModal
      v-model:visible="timelineRegenerationVisible"
      :type="timelineRegenerationType"
      :job-id="currentJobId"
      :job-progress="jobProgress"
    />
    <!-- Timeline Regeneration Modal (for favorite editing with bounds change) -->
    <TimelineRegenerationModal
      v-model:visible="favoriteTimelineVisible"
      :type="favoriteTimelineType"
      :job-id="favoriteJobId"
      :job-progress="favoriteJobProgress"
    />
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import ProgressSpinner from 'primevue/progressspinner'
import Tag from 'primevue/tag'

// Layout Components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'

// Place Components
import PlaceStatsCard from '@/components/place/PlaceStatsCard.vue'
import PlaceMap from '@/components/place/PlaceMap.vue'
import PlaceNotesSection from '@/components/place/PlaceNotesSection.vue'
import PlaceVisitsTable from '@/components/place/PlaceVisitsTable.vue'
import ImmichLatestPhotosSection from '@/components/location-analytics/ImmichLatestPhotosSection.vue'
import LocationDetailsHeader from '@/components/location-analytics/LocationDetailsHeader.vue'

// Dialogs
import EditFavoriteDialog from '@/components/dialogs/EditFavoriteDialog.vue'
import GeocodingEditDialog from '@/components/dialogs/GeocodingEditDialog.vue'
import TimelineRegenerationModal from '@/components/dialogs/TimelineRegenerationModal.vue'

import { useTimelineRegeneration } from '@/composables/useTimelineRegeneration'
import { useFavoriteEditor } from '@/composables/useFavoriteEditor'
import { useImmichPhotoMapBridge } from '@/composables/useImmichPhotoMapBridge'

// Store
import { usePlaceStatisticsStore } from '@/stores/placeStatistics'
import { useGeocodingStore } from '@/stores/geocoding'
import { useFavoritesStore } from '@/stores/favorites'

// Utilities
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'
import { haversineDistanceMetersFromCoordinates } from '@/utils/geoDistance'

// PrimeVue
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const toast = useToast()
const placeStore = usePlaceStatisticsStore()
const geocodingStore = useGeocodingStore()
const favoritesStore = useFavoritesStore()

// Composables
const {
  timelineRegenerationVisible,
  timelineRegenerationType,
  currentJobId,
  jobProgress,
  withTimelineRegeneration
} = useTimelineRegeneration()

// Store refs
const { placeDetails, placeVisits, photoSearchWindow: placePhotoSearchWindow, pagination, loading } = storeToRefs(placeStore)

// Local state
const error = ref(null)
const visitsLoading = ref(false)
const currentSortBy = ref('timestamp')
const currentSortDirection = ref('desc')
const showCreateFavoriteDialog = ref(false)
const newFavoriteName = ref('')
const placeMapRef = ref(null)
const placePhotosSectionRef = ref(null)
const {
  photosForMap: placePhotosForMap,
  markerGroupsForMap: placeMarkerGroupsForMap,
  resetPhotosForMap: resetPlacePhotosForMap,
  handlePhotosChange: handlePlacePhotosChange,
  handleMarkerGroupsChange: handlePlaceMarkerGroupsChange,
  handleMapPhotoClick,
  handlePhotoShowOnMap: handlePlacePhotoShowOnMap
} = useImmichPhotoMapBridge({
  mapRef: placeMapRef,
  photosSectionRef: placePhotosSectionRef,
  focusZoom: 16
})

const PLACE_PHOTO_RADIUS_METERS = 100
const PLACE_NOTES_LIMIT = 5000
const placeNotesForMap = ref([])

// Favorite editor composable (for editing favorite places)
const {
  showDialog: showFavoriteDialog,
  selectedFavorite,
  openEditor: openFavoriteEditor,
  closeEditor: closeFavoriteEditor,
  handleSave: handleFavoriteSave,
  timelineRegenerationVisible: favoriteTimelineVisible,
  timelineRegenerationType: favoriteTimelineType,
  currentJobId: favoriteJobId,
  jobProgress: favoriteJobProgress
} = useFavoriteEditor()

// For geocoding edit dialog
const showGeocodingEditDialog = ref(false)
const editGeocodingData = ref(null)

// Computed
const placeType = computed(() => route.params.type)
const placeId = computed(() => route.params.id)

const pageTitle = computed(() => {
  if (placeDetails.value) {
    return placeDetails.value.locationName
  }
  return t('place.detailsPage.pageTitleFallback')
})

const placeSubtitle = computed(() => (
  [placeDetails.value?.city, placeDetails.value?.country].filter(Boolean).join(', ')
  || t('place.detailsPage.subtitleFallback')
))

const placeTypeLabel = computed(() => placeType.value === 'favorite' ? t('place.detailsPage.typeFavorite') : t('place.detailsPage.typeGeocoded'))
const placeIcon = computed(() => placeDetails.value?.geometry?.type === 'area' ? 'pi pi-th-large' : 'pi pi-map-marker')
const displayCoordinates = computed(() => {
  const geometry = placeDetails.value?.geometry
  const latitude = Number(geometry?.latitude)
  const longitude = Number(geometry?.longitude)
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return ''
  const coordinates = `${latitude.toFixed(4)}, ${longitude.toFixed(4)}`
  return geometry.type === 'area' ? t('place.detailsPage.coordinatesCenter', { coordinates }) : coordinates
})

const isLoading = computed(() => loading.value)

const relatedFavoriteTitle = computed(() => {
  if (!placeDetails.value?.relatedFavorite) return ''

  const reason = placeDetails.value.relatedFavorite.reason
  if (reason === 'contains_point') {
    return t('place.detailsPage.relatedFavorite.titleArea')
  }
  return t('place.detailsPage.relatedFavorite.titlePoint')
})

const relatedFavoriteMessage = computed(() => {
  if (!placeDetails.value?.relatedFavorite) return ''

  const reason = placeDetails.value.relatedFavorite.reason
  if (reason === 'contains_point') {
    return t('place.detailsPage.relatedFavorite.messageArea')
  }
  return t('place.detailsPage.relatedFavorite.messagePoint')
})

const placeImmichSearchParams = computed(() => {
  const firstVisit = placePhotoSearchWindow.value?.minVisit || placeDetails.value?.statistics?.firstVisit
  const lastVisit = placePhotoSearchWindow.value?.maxVisit || placeDetails.value?.statistics?.lastVisit
  const city = placeDetails.value?.city?.trim?.()
  const country = placeDetails.value?.country?.trim?.()
  const geometry = placeDetails.value?.geometry

  if (!firstVisit || !lastVisit) {
    return null
  }

  const params = {
    startDate: firstVisit,
    endDate: lastVisit,
    city: city || undefined,
    country: country || undefined
  }

  if (
    typeof geometry?.latitude === 'number' &&
    Number.isFinite(geometry.latitude) &&
    typeof geometry?.longitude === 'number' &&
    Number.isFinite(geometry.longitude)
  ) {
    params.latitude = geometry.latitude
    params.longitude = geometry.longitude
  }

  if (geometry?.type !== 'area' && params.latitude !== undefined && params.longitude !== undefined) {
    params.radiusMeters = PLACE_PHOTO_RADIUS_METERS
  }

  if (!params.city && !params.country && (params.latitude === undefined || params.longitude === undefined)) {
    return null
  }

  return params
})

const placeNotesSearchParams = computed(() => {
  const firstVisit = placePhotoSearchWindow.value?.minVisit || placeDetails.value?.statistics?.firstVisit
  const lastVisit = placePhotoSearchWindow.value?.maxVisit || placeDetails.value?.statistics?.lastVisit
  const geometry = placeDetails.value?.geometry

  if (!firstVisit || !lastVisit || !geometry) {
    return null
  }

  const params = {
    from: firstVisit,
    to: lastVisit,
    includeExternal: true,
    limit: PLACE_NOTES_LIMIT
  }

  if (geometry.type === 'area') {
    const bounds = [
      geometry.northEast?.[0],
      geometry.northEast?.[1],
      geometry.southWest?.[0],
      geometry.southWest?.[1]
    ].map((value) => Number(value))

    return bounds.every((value) => Number.isFinite(value)) ? params : null
  }

  const latitude = Number(geometry.latitude)
  const longitude = Number(geometry.longitude)
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return null
  }

  return {
    ...params,
    latitude,
    longitude,
    radiusMeters: PLACE_PHOTO_RADIUS_METERS
  }
})

const placePhotoFilterCacheKey = computed(() => {
  const geometry = placeDetails.value?.geometry
  if (!geometry) {
    return `place:${placeType.value}:${placeId.value}:nogeometry`
  }

  if (geometry.type === 'area' && geometry.northEast && geometry.southWest) {
    return [
      `place:${placeType.value}:${placeId.value}:area`,
      geometry.northEast[0],
      geometry.northEast[1],
      geometry.southWest[0],
      geometry.southWest[1]
    ].join(':')
  }

  return [
    `place:${placeType.value}:${placeId.value}:point`,
    geometry.latitude ?? 'na',
    geometry.longitude ?? 'na',
    PLACE_PHOTO_RADIUS_METERS
  ].join(':')
})

// Methods
const resetPlaceNotesForMap = () => {
  placeNotesForMap.value = []
}

const handlePlaceNotesChange = (notes) => {
  placeNotesForMap.value = Array.isArray(notes) ? notes : []
}

const isCoordinateInsideArea = (latitude, longitude, northEast, southWest) => {
  if (!Array.isArray(northEast) || !Array.isArray(southWest)) {
    return false
  }

  const coordinateLatitude = Number(latitude)
  const coordinateLongitude = Number(longitude)
  const northEastLatitude = Number(northEast[0])
  const northEastLongitude = Number(northEast[1])
  const southWestLatitude = Number(southWest[0])
  const southWestLongitude = Number(southWest[1])

  if (![coordinateLatitude, coordinateLongitude, northEastLatitude, northEastLongitude, southWestLatitude, southWestLongitude]
    .every((value) => Number.isFinite(value))) {
    return false
  }

  const minLatitude = Math.min(northEastLatitude, southWestLatitude)
  const maxLatitude = Math.max(northEastLatitude, southWestLatitude)
  const minLongitude = Math.min(northEastLongitude, southWestLongitude)
  const maxLongitude = Math.max(northEastLongitude, southWestLongitude)

  return coordinateLatitude <= maxLatitude &&
    coordinateLatitude >= minLatitude &&
    coordinateLongitude <= maxLongitude &&
    coordinateLongitude >= minLongitude
}

const placePhotoFilterFn = (photo) => {
  if (!photo || typeof photo.latitude !== 'number' || typeof photo.longitude !== 'number') {
    return false
  }

  const geometry = placeDetails.value?.geometry
  if (!geometry) {
    return true
  }

  if (geometry.type === 'area' && geometry.northEast && geometry.southWest) {
    return isCoordinateInsideArea(photo.latitude, photo.longitude, geometry.northEast, geometry.southWest)
  }

  if (typeof geometry.latitude !== 'number' || typeof geometry.longitude !== 'number') {
    return false
  }

  return haversineDistanceMetersFromCoordinates(
    geometry.latitude,
    geometry.longitude,
    photo.latitude,
    photo.longitude
  ) <= PLACE_PHOTO_RADIUS_METERS
}

const placePhotoInMemoryFilter = computed(() => {
  const geometry = placeDetails.value?.geometry
  if (geometry?.type === 'area' && geometry.northEast && geometry.southWest) {
    return placePhotoFilterFn
  }
  return null
})

const placeNoteFilterFn = (note) => {
  const geometry = placeDetails.value?.geometry
  if (geometry?.type === 'area' && geometry.northEast && geometry.southWest) {
    return isCoordinateInsideArea(note?.latitude, note?.longitude, geometry.northEast, geometry.southWest)
  }
  return true
}

const placeNoteInMemoryFilter = computed(() => {
  const geometry = placeDetails.value?.geometry
  if (geometry?.type === 'area' && geometry.northEast && geometry.southWest) {
    return placeNoteFilterFn
  }
  return null
})

const formatDistance = (meters) => {
  if (meters === 0) return '0m'
  if (meters < 1000) {
    return `${Math.round(meters)}m`
  }
  return `${(meters / 1000).toFixed(1)}km`
}

const navigateToRelatedFavorite = () => {
  if (placeDetails.value?.relatedFavorite) {
    router.push(`/app/place-details/favorite/${placeDetails.value.relatedFavorite.id}`)
  }
}

const handleCreateFavorite = () => {
  // Pre-fill the name with the geocoding location name
  newFavoriteName.value = placeDetails.value?.locationName || ''
  showCreateFavoriteDialog.value = true
}

const submitCreateFavorite = () => {
  if (!newFavoriteName.value.trim()) {
    return
  }

  const geometry = placeDetails.value?.geometry
  if (!geometry || !geometry.latitude || !geometry.longitude) {
    toast.add({ severity: 'error', summary: t('common.error'), detail: t('place.detailsPage.invalidCoordinatesDetail'), life: 5000 })
    return
  }

  // Capture values immediately to avoid closure issues
  const favoriteName = newFavoriteName.value.trim()
  const lat = geometry.latitude
  const lon = geometry.longitude

  const action = () => favoritesStore.addPointToFavorites(favoriteName, lat, lon)

  // Close dialog and clean up immediately
  showCreateFavoriteDialog.value = false
  newFavoriteName.value = ''

  withTimelineRegeneration(action, {
    modalType: 'favorite',
    successMessage: t('place.detailsPage.favoriteCreatedSuccess'),
    errorMessage: t('place.detailsPage.favoriteCreateFailed'),
    onSuccess: () => {
      // Optionally, reload place details to show the updated related favorite
      loadPlaceData()
    }
  })
}
const loadPlaceData = async () => {
  error.value = null
  placePhotoSearchWindow.value = null
  resetPlacePhotosForMap()
  resetPlaceNotesForMap()

  try {
    // Load place details
    await placeStore.fetchPlaceDetails(placeType.value, placeId.value)

    try {
      await placeStore.fetchPhotoSearchWindow(placeType.value, placeId.value, PLACE_PHOTO_RADIUS_METERS)
    } catch (photoWindowError) {
      console.warn('Failed to load place photo search window, using place statistics fallback:', photoWindowError)
      placePhotoSearchWindow.value = null
    }

    // Load first page of visits
    await loadVisits(0, 50)
  } catch (err) {
    console.error('Error loading place data:', err)
    error.value = formatApiErrorDetail(err, t('place.detailsPage.loadFailed'))

    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: error.value,
      life: 5000
    })
  }
}

const loadVisits = async (page, pageSize, sortBy = currentSortBy.value, sortDirection = currentSortDirection.value) => {
  visitsLoading.value = true

  try {
    await placeStore.fetchPlaceVisits(
      placeType.value,
      placeId.value,
      page,
      pageSize,
      sortBy,
      sortDirection
    )
  } catch (err) {
    console.error('Error loading visits:', err)
    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: t('place.detailsPage.loadVisitsFailed'),
      life: 3000
    })
  } finally {
    visitsLoading.value = false
  }
}

const handlePageChange = async ({ page, pageSize }) => {
  await loadVisits(page, pageSize)
}

const handleSortChange = async ({ sortBy, sortDirection }) => {
  currentSortBy.value = sortBy
  currentSortDirection.value = sortDirection
  await loadVisits(pagination.value.currentPage, pagination.value.pageSize, sortBy, sortDirection)
}

const handleExportVisits = async () => {
  try {
    if (!placeDetails.value) {
      toast.add({
        severity: 'warn',
        summary: t('place.detailsPage.exportNoDataSummary'),
        detail: t('place.detailsPage.exportNoDataDetail'),
        life: 3000
      })
      return
    }

    // Show loading toast
    toast.add({
      severity: 'info',
      summary: t('place.detailsPage.exportingSummary'),
      detail: t('place.detailsPage.exportingDetail'),
      life: 3000
    })

    await placeStore.exportVisits(
      placeType.value,
      placeId.value,
      currentSortBy.value,
      currentSortDirection.value
    )

    // Get total count for success message
    const totalCount = pagination.value.totalCount || 'all'

    toast.add({
      severity: 'success',
      summary: t('place.detailsPage.exportSuccessSummary'),
      detail: t('place.detailsPage.exportSuccessDetail', { count: totalCount }),
      life: 5000
    })
  } catch (err) {
    console.error('Error exporting visits:', err)
    toast.add({
      severity: 'error',
      summary: t('place.detailsPage.exportFailedSummary'),
      detail: formatApiErrorDetail(err, t('place.detailsPage.exportFailedDetail')),
      life: 5000
    })
  }
}

const goBack = () => {
  router.back()
}

const handleOpenEditDialog = () => {
  console.log('Place Details: ', placeDetails.value)
  if (placeType.value === 'favorite') {
    const geometryType = placeDetails.value?.geometry?.type.toUpperCase() || 'POINT'

    // Prepare data for EditFavoriteDialog via composable
    const favoriteData = {
      id: placeId.value,
      name: placeDetails.value?.locationName || '',
      city: placeDetails.value?.city || '',
      country: placeDetails.value?.country || '',
      type: geometryType
    }

    // Include bounds only if AREA favorite (northEast/southWest are only populated for AREA type)
    if (geometryType === 'AREA' && placeDetails.value?.geometry?.northEast && placeDetails.value?.geometry?.southWest) {
      favoriteData.northEastLat = placeDetails.value.geometry.northEast[0]
      favoriteData.northEastLon = placeDetails.value.geometry.northEast[1]
      favoriteData.southWestLat = placeDetails.value.geometry.southWest[0]
      favoriteData.southWestLon = placeDetails.value.geometry.southWest[1]
    }

    openFavoriteEditor(favoriteData)
  } else if (placeType.value === 'geocoding') {
    // Prepare data for GeocodingEditDialog
    editGeocodingData.value = {
      id: placeId.value,
      displayName: placeDetails.value?.locationName || '',
      city: placeDetails.value?.city || '',
      country: placeDetails.value?.country || '',
      latitude: placeDetails.value?.geometry?.latitude,
      longitude: placeDetails.value?.geometry?.longitude,
      providerName: placeDetails.value?.providerName || t('common.unknown')
    }
    showGeocodingEditDialog.value = true
  }
}

const handleSaveGeocoding = async (updatedData) => {
  try {
    // Update geocoding result via store
    await geocodingStore.updateGeocodingResult(placeId.value, updatedData)

    // Reload place details to get updated data
    await placeStore.fetchPlaceDetails(placeType.value, placeId.value)

    toast.add({
      severity: 'success',
      summary: t('place.detailsPage.geocodingUpdatedSuccessSummary'),
      detail: t('place.detailsPage.geocodingUpdatedSuccessDetail'),
      life: 3000
    })

    showGeocodingEditDialog.value = false
  } catch (err) {
    console.error('Error updating geocoding result:', err)
    const errorMessage = formatApiErrorDetail(err, t('place.detailsPage.geocodingUpdateFailedDetail'))

    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: errorMessage,
      life: 5000
    })
  }
}

// Lifecycle
onMounted(async () => {
  // Clear previous data
  placeStore.clearPlaceData()
  resetPlacePhotosForMap()
  resetPlaceNotesForMap()

  // Load new data
  await loadPlaceData()
})

// Watch for route parameter changes to reload data when navigating between places
watch(
  () => [route.params.type, route.params.id],
  async ([newType, newId], [oldType, oldId]) => {
    // Only reload if the parameters actually changed
    if (newType !== oldType || newId !== oldId) {
      // Clear previous data
      placeStore.clearPlaceData()
      resetPlacePhotosForMap()
      resetPlaceNotesForMap()

      // Load new data
      await loadPlaceData()
    }
  }
)
</script>

<style scoped>
.detail-link {
  color: var(--gp-primary-text);
  font-weight: 600;
  text-underline-offset: .18em;
}

.detail-link:hover {
  color: var(--gp-primary-text);
}

.place-coordinates {
  display: inline-flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--gp-spacing-xxl);
  gap: var(--gp-spacing-md);
}

.loading-text {
  color: var(--gp-text-secondary);
  font-size: 1rem;
}

.error-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--gp-spacing-xxl);
  gap: var(--gp-spacing-md);
  text-align: center;
}

.error-icon {
  font-size: 4rem;
  color: var(--gp-danger);
  opacity: 0.7;
}

.error-title {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.error-message {
  margin: 0;
  font-size: 1rem;
  color: var(--gp-text-secondary);
  max-width: 500px;
}

/* Related Favorite Notice */
.related-favorite-notice {
  margin-bottom: var(--gp-spacing-xl);
  border-left: 4px solid var(--gp-primary-text);
  background: var(--gp-primary-soft);
  max-width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}

.notice-content {
  display: flex;
  gap: var(--gp-spacing-lg);
  padding: var(--gp-spacing-lg);
  max-width: 100%;
  box-sizing: border-box;
}

.notice-icon {
  flex-shrink: 0;
  font-size: 2.5rem;
  color: var(--gp-primary);
  line-height: 1;
}

.notice-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
}

.notice-title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.notice-message {
  margin: 0;
  font-size: 1rem;
  color: var(--gp-text-secondary);
  line-height: 1.5;
}

.favorite-info {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
  padding: var(--gp-spacing-md);
  background: var(--gp-surface-card);
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border);
  max-width: 100%;
  box-sizing: border-box;
}

.favorite-name-row {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
}

.favorite-icon {
  color: var(--gp-primary);
  font-size: 1.25rem;
}

.favorite-name {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  word-wrap: break-word;
  word-break: break-word;
  overflow-wrap: anywhere;
  flex: 1;
  min-width: 0;
}

.favorite-distance {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  font-style: italic;
}

.favorite-stats {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  padding-left: calc(1.25rem + var(--gp-spacing-sm));
}

.favorite-stats i {
  color: var(--gp-primary);
}

.view-favorite-button {
  align-self: flex-start;
  margin-top: var(--gp-spacing-sm);
}

/* Create Favorite Dialog */
.create-favorite-content {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-lg);
  padding: var(--gp-spacing-md) 0;
}

.dialog-message {
  margin: 0;
  color: var(--gp-text-secondary);
  line-height: 1.5;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.form-field label {
  font-weight: 600;
  color: var(--gp-text-primary);
  font-size: 0.9rem;
}

.coordinates-info {
  color: var(--gp-text-muted);
  font-family: var(--gp-font-mono);
  font-size: 0.85rem;
}

/* Responsive Design */
@media (max-width: 768px) {
  .loading-container,
  .error-container {
    padding: var(--gp-spacing-lg);
  }

  .error-icon {
    font-size: 2.5rem;
  }

  .error-title {
    font-size: 1.125rem;
  }

  .notice-content {
    flex-direction: column;
    gap: var(--gp-spacing-md);
    padding: var(--gp-spacing-md);
  }

  .notice-icon {
    font-size: 1.75rem;
  }

  .notice-title {
    font-size: 1rem;
  }

  .notice-message {
    font-size: 0.875rem;
  }

  .favorite-name {
    font-size: 0.9rem;
  }

  .favorite-name-row {
    flex-wrap: wrap;
  }

  .related-favorite-notice {
    margin-bottom: var(--gp-spacing-md);
  }
}

@media (max-width: 480px) {
  .notice-content {
    padding: var(--gp-spacing-sm);
  }
}
</style>
