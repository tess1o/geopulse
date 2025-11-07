<template>
  <PageContainer
    :title="pageTitle"
    subtitle="Detailed information about this place and your visit history"
    :loading="isLoading"
  >
    <!-- Breadcrumb Navigation -->
    <div class="breadcrumb-nav">
      <Button
        label="Back to Timeline"
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
      />

      <!-- Statistics -->
      <PlaceStatsCard
        v-if="placeDetails.statistics"
        :statistics="placeDetails.statistics"
      />

      <!-- Map -->
      <PlaceMap
        v-if="placeDetails.geometry"
        :geometry="placeDetails.geometry"
        :location-name="placeDetails.locationName"
      />

      <!-- Visits Table -->
      <PlaceVisitsTable
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
      v-if="placeType === 'favorite'"
      :visible="showEditDialog"
      :header="'Edit Favorite Location'"
      :favorite-location="editFavoriteData"
      @edit-favorite="handleSaveFavorite"
      @close="showEditDialog = false"
    />

    <GeocodingEditDialog
      v-if="placeType === 'geocoding'"
      :visible="showEditDialog"
      :geocoding-result="editGeocodingData"
      @save="handleSaveGeocoding"
      @close="showEditDialog = false"
    />
  </PageContainer>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import ProgressSpinner from 'primevue/progressspinner'

// Layout Components
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

// Store
import { usePlaceStatisticsStore } from '@/stores/placeStatistics'
import { useGeocodingStore } from '@/stores/geocoding'

// Utilities
import apiService from '@/utils/apiService'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const placeStore = usePlaceStatisticsStore()
const geocodingStore = useGeocodingStore()

// Store refs
const { placeDetails, placeVisits, pagination, loading } = storeToRefs(placeStore)

// Local state
const error = ref(null)
const visitsLoading = ref(false)
const currentSortBy = ref('timestamp')
const currentSortDirection = ref('desc')
const showEditDialog = ref(false)
const editFavoriteData = ref(null)
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

// Methods
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
    const loadingToast = toast.add({
      severity: 'info',
      summary: 'Exporting',
      detail: 'Preparing CSV export...',
      life: 0
    })

    // Call backend API to get CSV file
    const url = `/api/place-details/${placeType.value}/${placeId.value}/visits/export?sortBy=${currentSortBy.value}&sortDirection=${currentSortDirection.value}`

    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('authToken')}` || ''
      }
    })

    // Remove loading toast
    toast.remove(loadingToast)

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
  if (placeType.value === 'favorite') {
    // Prepare data for EditFavoriteDialog
    editFavoriteData.value = {
      id: placeId.value,
      name: placeDetails.value?.locationName || ''
    }
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
  }
  showEditDialog.value = true
}

const handleSaveFavorite = async (favoriteData) => {
  try {
    // Update favorite via API (using favorites endpoint)
    await apiService.put(`/favorites/${favoriteData.id}`, {
      name: favoriteData.name
    })

    // Update local state
    if (placeDetails.value) {
      placeDetails.value.locationName = favoriteData.name
    }

    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: 'Favorite location updated successfully',
      life: 3000
    })

    showEditDialog.value = false
  } catch (err) {
    console.error('Error updating favorite:', err)
    const errorMessage = err.response?.data?.message || err.message || 'Failed to update favorite location'

    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: errorMessage,
      life: 5000
    })
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

    showEditDialog.value = false
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
</script>

<style scoped>
.breadcrumb-nav {
  margin-bottom: var(--gp-spacing-lg);
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

/* Responsive Design */
@media (max-width: 768px) {
  .breadcrumb-nav {
    margin-bottom: var(--gp-spacing-md);
  }

  .loading-container,
  .error-container {
    padding: var(--gp-spacing-xl);
  }

  .error-icon {
    font-size: 3rem;
  }

  .error-title {
    font-size: 1.25rem;
  }
}
</style>
