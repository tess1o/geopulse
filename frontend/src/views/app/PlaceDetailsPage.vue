<template>
  <AppLayout variant="default">
    <PageContainer
      :title="pageTitle"
      subtitle="Detailed information about this place and your visit history"
      :loading="isLoading"
      variant="fullwidth"
    >
    <!-- Breadcrumb Navigation -->
    <div class="breadcrumb-nav">
      <Button
        label="Back"
        icon="pi pi-arrow-left"
        class="p-button-text"
        @click="goBack"
      />
    </div>

    <!-- Loading State -->
    <template v-if="isLoading && !placeDetails">
      <div class="loading-container">
        <ProgressSpinner />
        <p class="loading-text">Loading place details...</p>
      </div>
    </template>

    <!-- Error State -->
    <template v-else-if="error">
      <BaseCard>
        <div class="error-container">
          <i class="pi pi-exclamation-triangle error-icon"></i>
          <h3 class="error-title">Failed to Load Place Details</h3>
          <p class="error-message">{{ error }}</p>
          <Button
            label="Try Again"
            icon="pi pi-refresh"
            @click="loadPlaceData"
          />
        </div>
      </BaseCard>
    </template>

    <!-- Place Details Content -->
    <template v-else-if="placeDetails">
      <!-- Place Header -->
      <PlaceHeader
        :place-details="placeDetails"
        @update-name="handleUpdateName"
        @edit-details="handleOpenEditDialog"
        @create-favorite="handleCreateFavorite"
      />

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
                  ({{ formatDistance(placeDetails.relatedFavorite.distanceMeters) }} away)
                </span>
              </div>
              <div class="favorite-stats" v-if="placeDetails.relatedFavorite.totalVisits">
                <i class="pi pi-clock"></i>
                <span>{{ placeDetails.relatedFavorite.totalVisits }} visits tracked</span>
              </div>
            </div>
            <Button
              :label="`View ${placeDetails.relatedFavorite.name} Details`"
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
        :key="`place-map-${placeType}-${placeId}`"
        :geometry="placeDetails.geometry"
        :location-name="placeDetails.locationName"
      />

      <!-- Visits Table -->
      <PlaceVisitsTable
        v-if="!placeDetails.relatedFavorite"
        :visits="placeVisits"
        :pagination="pagination"
        :loading="visitsLoading"
        @page-change="handlePageChange"
        @sort-change="handleSortChange"
        @export="handleExportVisits"
      />
    </template>

    <!-- Edit Dialogs -->
    <EditFavoriteDialog
      v-if="placeType === 'favorite' && selectedFavorite"
      :visible="showFavoriteDialog"
      :header="'Edit Favorite Location'"
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
      header="Create Favorite Location"
      :style="{ width: '450px' }"
    >
      <div class="create-favorite-content">
        <p class="dialog-message">
          Create a favorite location at this geocoding point. You can give it a custom name.
        </p>
        <div class="form-field">
          <label for="favorite-name">Favorite Name</label>
          <InputText
            id="favorite-name"
            v-model="newFavoriteName"
            placeholder="e.g., Home, Work, Gym"
            autofocus
            @keyup.enter="submitCreateFavorite"
            style="width: 100%"
          />
        </div>
        <small class="coordinates-info">
          Coordinates: {{ placeDetails?.geometry?.latitude?.toFixed(6) }}, {{ placeDetails?.geometry?.longitude?.toFixed(6) }}
        </small>
      </div>
      <template #footer>
        <Button
          label="Cancel"
          severity="secondary"
          @click="showCreateFavoriteDialog = false"
          outlined
        />
        <Button
          label="Create Favorite"
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
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import ProgressSpinner from 'primevue/progressspinner'

// Layout Components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'

// Place Components
import PlaceHeader from '@/components/place/PlaceHeader.vue'
import PlaceStatsCard from '@/components/place/PlaceStatsCard.vue'
import PlaceMap from '@/components/place/PlaceMap.vue'
import PlaceVisitsTable from '@/components/place/PlaceVisitsTable.vue'

// Dialogs
import EditFavoriteDialog from '@/components/dialogs/EditFavoriteDialog.vue'
import GeocodingEditDialog from '@/components/dialogs/GeocodingEditDialog.vue'
import TimelineRegenerationModal from '@/components/dialogs/TimelineRegenerationModal.vue'

import { useTimelineRegeneration } from '@/composables/useTimelineRegeneration'
import { useFavoriteEditor } from '@/composables/useFavoriteEditor'

// Store
import { usePlaceStatisticsStore } from '@/stores/placeStatistics'
import { useGeocodingStore } from '@/stores/geocoding'

// Utilities
import apiService from '@/utils/apiService'

// PrimeVue
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const placeStore = usePlaceStatisticsStore()
const geocodingStore = useGeocodingStore()

// Composables
const {
  timelineRegenerationVisible,
  timelineRegenerationType,
  currentJobId,
  jobProgress,
  withTimelineRegeneration
} = useTimelineRegeneration()

// Store refs
const { placeDetails, placeVisits, pagination, loading } = storeToRefs(placeStore)

// Local state
const error = ref(null)
const visitsLoading = ref(false)
const currentSortBy = ref('timestamp')
const currentSortDirection = ref('desc')
const showCreateFavoriteDialog = ref(false)
const newFavoriteName = ref('')

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
  return 'Place Details'
})

const isLoading = computed(() => loading.value)

const relatedFavoriteTitle = computed(() => {
  if (!placeDetails.value?.relatedFavorite) return ''

  const reason = placeDetails.value.relatedFavorite.reason
  if (reason === 'contains_point') {
    return 'Visits Grouped with Area Favorite'
  }
  return 'Visits Grouped with Favorite'
})

const relatedFavoriteMessage = computed(() => {
  if (!placeDetails.value?.relatedFavorite) return ''

  const reason = placeDetails.value.relatedFavorite.reason
  if (reason === 'contains_point') {
    return 'This location is within your favorite area. Your visits here are being tracked under:'
  }
  return 'Your visits to this location are being tracked under your nearby favorite:'
})

// Methods
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
    toast.add({ severity: 'error', summary: 'Error', detail: 'Invalid coordinates for favorite.', life: 5000 })
    return
  }

  // Capture values immediately to avoid closure issues
  const favoriteName = newFavoriteName.value.trim()
  const lat = geometry.latitude
  const lon = geometry.longitude

  const action = () => apiService.post('/favorites/point', {
    name: favoriteName,
    lat: lat,
    lon: lon
  }).then(response => response.data?.jobId || response.data)

  // Close dialog and clean up immediately
  showCreateFavoriteDialog.value = false
  newFavoriteName.value = ''

  withTimelineRegeneration(action, {
    modalType: 'favorite',
    successMessage: 'Favorite location created successfully.',
    errorMessage: 'Failed to create favorite location.',
    onSuccess: () => {
      // Optionally, reload place details to show the updated related favorite
      loadPlaceData()
    }
  })
}
const loadPlaceData = async () => {
  error.value = null

  try {
    // Load place details
    await placeStore.fetchPlaceDetails(placeType.value, placeId.value)

    // Load first page of visits
    await loadVisits(0, 50)
  } catch (err) {
    console.error('Error loading place data:', err)
    error.value = err.response?.data?.message || err.message || 'Failed to load place details'

    toast.add({
      severity: 'error',
      summary: 'Error',
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
      summary: 'Error',
      detail: 'Failed to load visit history',
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

const handleUpdateName = async (newName) => {
  try {
    await placeStore.updatePlaceName(placeType.value, placeId.value, newName)

    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: 'Place name updated successfully',
      life: 3000
    })
  } catch (err) {
    console.error('Error updating place name:', err)
    const errorMessage = err.response?.data?.message || err.message || 'Failed to update place name'

    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: errorMessage,
      life: 5000
    })
  }
}

const handleExportVisits = async () => {
  try {
    if (!placeDetails.value) {
      toast.add({
        severity: 'warn',
        summary: 'No Data',
        detail: 'No place data available',
        life: 3000
      })
      return
    }

    // Show loading toast
    toast.add({
      severity: 'info',
      summary: 'Exporting',
      detail: 'Preparing CSV export...',
      life: 3000
    })

    // Call backend API to get CSV file
    const url = `/api/place-details/${placeType.value}/${placeId.value}/visits/export?sortBy=${currentSortBy.value}&sortDirection=${currentSortDirection.value}`

    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('authToken')}` || ''
      }
    })

    if (!response.ok) {
      throw new Error(`Export failed with status ${response.status}`)
    }

    // Get filename from Content-Disposition header or generate one
    const contentDisposition = response.headers.get('Content-Disposition')
    let filename = 'visits_export.csv'
    if (contentDisposition) {
      const filenameMatch = contentDisposition.match(/filename="?([^"]+)"?/)
      if (filenameMatch) {
        filename = filenameMatch[1]
      }
    }

    // Create blob from response
    const blob = await response.blob()

    // Create download link
    const downloadUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = filename
    link.style.visibility = 'hidden'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(downloadUrl)

    // Get total count for success message
    const totalCount = pagination.value.totalCount || 'all'

    toast.add({
      severity: 'success',
      summary: 'Export Successful',
      detail: `Exported ${totalCount} visits to ${filename}`,
      life: 5000
    })
  } catch (err) {
    console.error('Error exporting visits:', err)
    toast.add({
      severity: 'error',
      summary: 'Export Failed',
      detail: err.message || 'Failed to export visits to CSV',
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
    // Prepare data for EditFavoriteDialog via composable
    const favoriteData = {
      id: placeId.value,
      name: placeDetails.value?.locationName || '',
      city: placeDetails.value?.city || '',
      country: placeDetails.value?.country || '',
      type: placeDetails.value?.geometry?.type.toUpperCase() || 'POINT',
      // Include bounds if AREA favorite
      northEastLat: placeDetails.value?.geometry?.northEast[0],
      northEastLon: placeDetails.value?.geometry?.northEast[1],
      southWestLat: placeDetails.value?.geometry?.southWest[0],
      southWestLon: placeDetails.value?.geometry?.southWest[1]
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
      providerName: placeDetails.value?.providerName || 'Unknown'
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
      summary: 'Success',
      detail: 'Geocoding location updated successfully',
      life: 3000
    })

    showGeocodingEditDialog.value = false
  } catch (err) {
    console.error('Error updating geocoding result:', err)
    const errorMessage = err.response?.data?.message || err.message || 'Failed to update geocoding location'

    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: errorMessage,
      life: 5000
    })
  }
}

// Lifecycle
onMounted(async () => {
  // Clear previous data
  placeStore.clearPlaceData()

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

      // Load new data
      await loadPlaceData()
    }
  }
)
</script>

<style scoped>
/* Ensure all elements respect parent width */
* {
  box-sizing: border-box;
}

.breadcrumb-nav {
  margin-bottom: var(--gp-spacing-lg);
  padding: 0 var(--gp-spacing-lg);
  padding-top: env(safe-area-inset-top);
  max-width: 100%;
}

:deep(.gp-page-content) {
  padding: 0 var(--gp-spacing-lg);
  max-width: 100%;
  box-sizing: border-box;
}

:deep(.gp-page-content > *) {
  max-width: 100%;
  box-sizing: border-box;
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
  color: var(--gp-error);
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
  border-left: 4px solid var(--gp-primary);
  background: var(--gp-primary-50);
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
  background: var(--gp-surface-white);
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-light);
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
  font-family: monospace;
  font-size: 0.85rem;
}

/* Dark Mode */
.p-dark .loading-text {
  color: var(--gp-text-secondary);
}

.p-dark .error-title {
  color: var(--gp-text-primary);
}

.p-dark .error-message {
  color: var(--gp-text-secondary);
}

.p-dark .related-favorite-notice {
  background: var(--gp-primary-900);
  border-left-color: var(--gp-primary-400);
}

.p-dark .notice-title {
  color: var(--gp-text-primary);
}

.p-dark .notice-message {
  color: var(--gp-text-secondary);
}

.p-dark .favorite-info {
  background: var(--gp-surface-950);
  border-color: var(--gp-border-dark);
}

.p-dark .favorite-name {
  color: var(--gp-text-primary);
}

.p-dark .favorite-distance {
  color: var(--gp-text-secondary);
}

.p-dark .favorite-stats {
  color: var(--gp-text-secondary);
}

/* Responsive Design */
@media (max-width: 768px) {
  .breadcrumb-nav {
    margin-bottom: var(--gp-spacing-sm);
    padding: var(--gp-spacing-sm);
    padding-top: calc(env(safe-area-inset-top) + var(--gp-spacing-sm));
  }

  :deep(.gp-page-content) {
    padding: 0 var(--gp-spacing-sm);
    gap: var(--gp-spacing-md);
  }

  /* Reduce padding on cards */
  :deep(.gp-base-card) {
    padding: var(--gp-spacing-md);
  }

  :deep(.gp-page-header) {
    margin-bottom: var(--gp-spacing-md);
  }

  :deep(.gp-page-title) {
    font-size: 1.25rem;
  }

  :deep(.gp-page-subtitle) {
    font-size: 0.875rem;
  }

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
  .breadcrumb-nav {
    padding: var(--gp-spacing-xs);
    padding-top: calc(env(safe-area-inset-top) + var(--gp-spacing-xs));
  }

  :deep(.gp-page-content) {
    padding: 0 var(--gp-spacing-xs);
  }

  :deep(.gp-base-card) {
    padding: var(--gp-spacing-sm);
  }

  .notice-content {
    padding: var(--gp-spacing-sm);
  }
}
</style>
