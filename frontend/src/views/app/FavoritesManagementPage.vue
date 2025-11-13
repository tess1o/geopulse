<template>
  <AppLayout variant="default">
    <PageContainer
        title="Favorite Locations Management"
        subtitle="View, manage, and organize your favorite places"
        :loading="isLoading"
        variant="fullwidth"
    >
      <template #actions>
        <div class="header-actions">
          <span class="header-info">Right-click on map to add favorites</span>
        </div>
      </template>

      <!-- Map Section -->
      <BaseCard class="map-section">
        <div class="map-header">
          <h3 class="map-title">Favorites Map</h3>
          <span class="map-subtitle">{{ totalRecords }} favorite{{ totalRecords !== 1 ? 's' : '' }} on map</span>
        </div>
        <div class="map-container">
          <BaseMap
              mapId="favorites-map"
              :center="mapCenter"
              :zoom="mapZoom"
              height="100%"
              width="100%"
              @map-ready="handleMapReady"
              @map-contextmenu="handleMapContextMenu"
              ref="baseMapRef"
          />
        </div>
      </BaseCard>

      <!-- Filters -->
      <BaseCard class="filter-section">
        <div class="filter-controls">
          <div class="filter-group">
            <label class="filter-label">Type:</label>
            <Select
                v-model="selectedType"
                :options="typeOptions"
                optionLabel="label"
                optionValue="value"
                placeholder="All Types"
                class="type-select"
                @change="handleFilterChange"
            />
          </div>
          <div class="filter-group">
            <label class="filter-label">Search:</label>
            <InputText
                v-model="searchText"
                placeholder="Search by name or location"
                class="search-input"
                @input="handleSearchChange"
            />
          </div>
          <Button
              label="Clear Filters"
              severity="secondary"
              size="small"
              @click="clearFilters"
              :disabled="!hasActiveFilters"
          />
        </div>
      </BaseCard>

      <!-- Favorites Table -->
      <BaseCard class="table-section">
        <DataTable
            :value="displayedFavorites"
            :loading="tableLoading"
            paginator
            :rows="pageSize"
            data-key="id"
            responsive-layout="scroll"
            class="favorites-table"
        >
          <template #header>
            <div class="table-header">
              <span class="table-title">Favorite Locations</span>
            </div>
          </template>

          <template #empty>
            <div class="empty-state">
              <i class="pi pi-map-marker empty-icon"></i>
              <h3>No Favorite Locations Found</h3>
              <p>Add your first favorite location to get started.</p>
            </div>
          </template>

          <Column field="name" header="Name" sortable class="name-col">
            <template #body="slotProps">
              <div class="name-cell">
                <i :class="getFavoriteIcon(slotProps.data.type)" class="type-icon"></i>
                <span>{{ slotProps.data.name }}</span>
              </div>
            </template>
          </Column>

          <Column field="type" header="Type" sortable class="type-col" v-if="!isMobile">
            <template #body="slotProps">
              <Tag
                  :value="slotProps.data.type"
                  :severity="getTypeSeverity(slotProps.data.type)"
                  class="type-tag"
              />
            </template>
          </Column>

          <Column header="Location" class="location-col" v-if="!isMobile">
            <template #body="slotProps">
              <div class="location-cell">
              <span v-if="slotProps.data.type === 'POINT'">
                {{ formatCoordinates(slotProps.data) }}
              </span>
                <span v-else>
                Area ({{ formatAreaBounds(slotProps.data) }})
              </span>
              </div>
            </template>
          </Column>

          <Column field="city" header="City" sortable class="city-col" v-if="!isMobile && !isTablet">
            <template #body="slotProps">
              <span v-if="slotProps.data.city">{{ slotProps.data.city }}</span>
              <span v-else class="null-value">-</span>
            </template>
          </Column>

          <Column field="country" header="Country" sortable class="country-col" v-if="!isMobile && !isTablet">
            <template #body="slotProps">
              <span v-if="slotProps.data.country">{{ slotProps.data.country }}</span>
              <span v-else class="null-value">-</span>
            </template>
          </Column>

          <Column header="Actions" class="actions-col">
            <template #body="slotProps">
              <div class="actions-buttons">
                <Button
                    icon="pi pi-eye"
                    severity="primary"
                    size="small"
                    text
                    @click="viewDetails(slotProps.data)"
                    v-tooltip.top="'View Details'"
                    class="action-button view-button"
                />
                <Button
                    icon="pi pi-pencil"
                    severity="secondary"
                    size="small"
                    text
                    @click="editFavorite(slotProps.data)"
                    v-tooltip.top="'Edit'"
                    class="action-button edit-button"
                />
                <Button
                    icon="pi pi-trash"
                    severity="danger"
                    size="small"
                    text
                    @click="deleteFavorite(slotProps.data)"
                    v-tooltip.top="'Delete'"
                    class="action-button delete-button"
                />
                <Button
                    icon="pi pi-map"
                    severity="info"
                    size="small"
                    text
                    @click="focusOnMap(slotProps.data)"
                    v-tooltip.top="'Show on Map'"
                    class="action-button map-button"
                />
              </div>
            </template>
          </Column>
        </DataTable>
      </BaseCard>

      <!-- Edit Dialog -->
      <EditFavoriteDialog
          :visible="showEditDialog"
          :header="'Edit Favorite Location'"
          :favorite-location="selectedFavorite"
          @edit-favorite="handleEditSave"
          @close="showEditDialog = false"
      />

      <!-- Add Favorite Dialog -->
      <AddFavoriteDialog
          :visible="showAddDialog"
          :header="addDialogHeader"
          @add-to-favorites="handleAddFavorite"
          @close="handleCloseAddDialog"
      />

      <!-- Confirm Dialog -->
      <ConfirmDialog></ConfirmDialog>

      <!-- Timeline Regeneration Modal -->
      <TimelineRegenerationModal
          v-model:visible="timelineRegenerationVisible"
          :type="timelineRegenerationType"
          :job-id="currentJobId"
          :job-progress="jobProgress"
      />

      <!-- Context Menus -->
      <ContextMenu
          ref="mapContextMenuRef"
          :model="mapMenuItems"
          :popup="true"
      />

      <ContextMenu
          ref="favoriteContextMenuRef"
          :model="favoriteMenuItems"
          :popup="true"
      />
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import {ref, computed, onMounted, onUnmounted, watch} from 'vue'
import {useRouter} from 'vue-router'
import {useToast} from 'primevue/usetoast'
import {useConfirm} from 'primevue/useconfirm'
import {useFavoritesStore} from '@/stores/favorites'
import {useRectangleDrawing} from '@/composables/useRectangleDrawing'
import BaseMap from '@/components/maps/BaseMap.vue'

import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import EditFavoriteDialog from '@/components/dialogs/EditFavoriteDialog.vue'
import AddFavoriteDialog from '@/components/dialogs/AddFavoriteDialog.vue'
import TimelineRegenerationModal from '@/components/dialogs/TimelineRegenerationModal.vue'

// PrimeVue
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Select from 'primevue/select'
import InputText from 'primevue/inputtext'
import ContextMenu from 'primevue/contextmenu'
import ConfirmDialog from 'primevue/confirmdialog'

import {useTimelineRegeneration} from '@/composables/useTimelineRegeneration'

// Store and utils
const favoritesStore = useFavoritesStore()
const toast = useToast()
const router = useRouter()
const confirm = useConfirm()

// Timeline regeneration composable
const {
  timelineRegenerationVisible,
  timelineRegenerationType,
  currentJobId,
  jobProgress,
  withTimelineRegeneration
} = useTimelineRegeneration()

// Reactive state
const isMobile = ref(false)
const isTablet = ref(false)
const selectedType = ref(null)
const searchText = ref('')
const pageSize = ref(50)

// Loading states
const isLoading = ref(false)
const tableLoading = ref(false)

// Dialog states
const showEditDialog = ref(false)
const showAddDialog = ref(false)
const selectedFavorite = ref(null)
const addDialogHeader = ref('Add Point to Favorites')
const pendingAddCoordinates = ref(null)
const pendingAddBounds = ref(null)



// Context menu refs
const mapContextMenuRef = ref(null)
const favoriteContextMenuRef = ref(null)
const favoriteContextMenuActive = ref(false)
const contextMenuLatLng = ref(null)

// Map state
const baseMapRef = ref(null)
const mapInstance = ref(null)
const favoritesLayerRef = ref(null)
const tempMarker = ref(null)
const mapCenter = ref([51.505, -0.09])
const mapZoom = ref(13)

// Rectangle drawing composable
const {
  isDrawing,
  initialize: initializeDrawing,
  startDrawing,
  stopDrawing,
  cleanupTempLayer
} = useRectangleDrawing({
  onRectangleCreated: (data) => {
    // Store bounds for area favorite
    const bounds = data.bounds
    const southWest = bounds.getSouthWest()
    const northEast = bounds.getNorthEast()

    pendingAddBounds.value = {
      southWestLat: southWest.lat,
      southWestLon: southWest.lng,
      northEastLat: northEast.lat,
      northEastLon: northEast.lng
    }

    // Show add dialog for area
    addDialogHeader.value = 'Add Area to Favorites'
    showAddDialog.value = true
  }
})

// Computed properties
const allFavorites = computed(() => {
  const points = favoritesStore.getFavoritePoints.map(p => ({...p, type: 'POINT'}))
  const areas = favoritesStore.getFavoriteAreas.map(a => ({...a, type: 'AREA'}))
  return [...points, ...areas]
})

const displayedFavorites = computed(() => {
  let filtered = [...allFavorites.value]

  // Filter by type
  if (selectedType.value) {
    filtered = filtered.filter(f => f.type === selectedType.value)
  }

  // Filter by search text
  if (searchText.value && searchText.value.trim() !== '') {
    const searchLower = searchText.value.toLowerCase()
    filtered = filtered.filter(f =>
        f.name.toLowerCase().includes(searchLower) ||
        f.city?.toLowerCase().includes(searchLower) ||
        f.country?.toLowerCase().includes(searchLower)
    )
  }

  return filtered
})

const totalRecords = computed(() => displayedFavorites.value.length)

const typeOptions = [
  {label: 'All Types', value: null},
  {label: 'Point', value: 'POINT'},
  {label: 'Area', value: 'AREA'}
]

const hasActiveFilters = computed(() =>
    selectedType.value !== null || (searchText.value && searchText.value.trim() !== '')
)

// Context menu items
const mapMenuItems = ref([
  {
    label: 'Add to Favorites',
    icon: 'pi pi-star',
    command: () => {
      handleAddPointFromContextMenu()
    }
  },
  {
    label: 'Add an area to Favorites',
    icon: 'pi pi-th-large',
    command: () => {
      handleAddAreaFromContextMenu()
    }
  }
])

const favoriteMenuItems = ref([
  {
    label: 'View all visits',
    icon: 'pi pi-chart-line',
    command: () => {
      if (selectedFavorite.value) {
        viewDetails(selectedFavorite.value)
      }
    }
  },
  {
    separator: true
  },
  {
    label: 'Edit',
    icon: 'pi pi-pencil',
    command: () => {
      if (selectedFavorite.value) {
        editFavorite(selectedFavorite.value)
      }
    }
  },
  {
    label: 'Delete',
    icon: 'pi pi-trash',
    command: () => {
      if (selectedFavorite.value) {
        deleteFavorite(selectedFavorite.value)
      }
    }
  }
])

// Methods
const handleResize = () => {
  isMobile.value = window.innerWidth < 768
  isTablet.value = window.innerWidth >= 768 && window.innerWidth < 1024
  pageSize.value = isMobile.value ? 25 : 50
}

let searchTimeout = null
const handleSearchChange = () => {
  clearTimeout(searchTimeout)
  searchTimeout = setTimeout(() => {
    // Search is reactive through computed property
  }, 500)
}

const handleFilterChange = () => {
  // Filter is reactive through computed property
}

const clearFilters = () => {
  selectedType.value = null
  searchText.value = ''
}

const getFavoriteIcon = (type) => {
  return type === 'POINT' ? 'pi pi-map-marker' : 'pi pi-th-large'
}

const getTypeSeverity = (type) => {
  return type === 'POINT' ? 'success' : 'info'
}

const formatCoordinates = (favorite) => {
  if (favorite.latitude && favorite.longitude) {
    return `${favorite.latitude.toFixed(6)}, ${favorite.longitude.toFixed(6)}`
  }
  return '-'
}

const formatAreaBounds = (favorite) => {
  if (favorite.northEastLat && favorite.northEastLon && favorite.southWestLat && favorite.southWestLon) {
    return `${favorite.southWestLat.toFixed(4)}, ${favorite.southWestLon.toFixed(4)} to ${favorite.northEastLat.toFixed(4)}, ${favorite.northEastLon.toFixed(4)}`
  }
  return '-'
}

const viewDetails = (favorite) => {
  router.push(`/app/place-details/favorite/${favorite.id}`)
}

const editFavorite = (favorite) => {
  selectedFavorite.value = favorite
  showEditDialog.value = true
}

const deleteFavorite = (favorite) => {
  confirm.require({
    message: 'Are you sure you want to delete this favorite location? This will also regenerate your timeline data.',
    header: 'Delete Favorite',
    icon: 'pi pi-exclamation-triangle',
    accept: () => {
      // Capture values to avoid closure issues
      const favoriteId = favorite.id
      const favoriteName = favorite.name

      const action = () => favoritesStore.deleteFavorite(favoriteId)
      withTimelineRegeneration(action, {
        modalType: 'favorite-delete',
        successMessage: `Favorite "${favoriteName}" deleted successfully. Timeline is regenerating.`,
        errorMessage: 'Failed to delete favorite location.',
        onSuccess: () => {
          // Refresh favorites list from the store
          loadFavorites()
        }
      })
    }
  })
}

const handleEditSave = async (updatedData) => {
  if (!selectedFavorite.value) return

  try {
    await favoritesStore.editFavorite(selectedFavorite.value.id, updatedData.name)

    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: 'Favorite location updated successfully',
      life: 3000
    })

    showEditDialog.value = false
    selectedFavorite.value = null

    // Update map
    await loadFavorites()
  } catch (error) {
    console.error('Error updating favorite:', error)
    toast.add({
      severity: 'error',
      summary: 'Update Failed',
      detail: error.message || 'Failed to update favorite location',
      life: 5000
    })
  }
}

const handleAddPointFromContextMenu = () => {
  if (!contextMenuLatLng.value) return

  // Store coordinates
  pendingAddCoordinates.value = {
    lat: contextMenuLatLng.value.lat,
    lng: contextMenuLatLng.value.lng
  }

  // Add temporary marker
  if (tempMarker.value && mapInstance.value) {
    mapInstance.value.removeLayer(tempMarker.value)
  }

  tempMarker.value = baseMapRef.value.L.marker([contextMenuLatLng.value.lat, contextMenuLatLng.value.lng], {
    icon: baseMapRef.value.L.divIcon({
      className: 'temp-favorite-marker',
      html: '<div class="temp-marker-icon"><i class="pi pi-map-marker"></i></div>',
      iconSize: [40, 40],
      iconAnchor: [20, 40]
    })
  }).addTo(mapInstance.value)

  // Show add dialog
  addDialogHeader.value = 'Add Point to Favorites'
  showAddDialog.value = true
}

const handleAddAreaFromContextMenu = () => {
  if (!mapInstance.value) return

  // Start rectangle drawing
  startDrawing()

  toast.add({
    severity: 'info',
    summary: 'Draw Area',
    detail: 'Click and drag on the map to draw a rectangular area',
    life: 5000
  })
}

const handleAddFavorite = (name) => {
  if (!name || !name.trim()) return

  let action
  let successMessage

  // Capture the values immediately to avoid closure issues
  if (pendingAddCoordinates.value) {
    const lat = pendingAddCoordinates.value.lat
    const lng = pendingAddCoordinates.value.lng
    action = () => favoritesStore.addPointToFavorites(
        name.trim(),
        lat,
        lng
    )
    successMessage = `Favorite "${name}" added. Timeline is regenerating.`
  } else if (pendingAddBounds.value) {
    const northEastLat = pendingAddBounds.value.northEastLat
    const northEastLon = pendingAddBounds.value.northEastLon
    const southWestLat = pendingAddBounds.value.southWestLat
    const southWestLon = pendingAddBounds.value.southWestLon
    action = () => favoritesStore.addAreaToFavorites(
        name.trim(),
        northEastLat,
        northEastLon,
        southWestLat,
        southWestLon
    )
    successMessage = `Area favorite "${name}" added. Timeline is regenerating.`
  } else {
    return // Should not happen
  }

  // Close dialog and clean up immediately
  showAddDialog.value = false
  pendingAddCoordinates.value = null
  pendingAddBounds.value = null

  if (tempMarker.value && mapInstance.value) {
    mapInstance.value.removeLayer(tempMarker.value)
    tempMarker.value = null
  }
  cleanupTempLayer()

  withTimelineRegeneration(action, {
    modalType: 'favorite',
    successMessage: successMessage,
    errorMessage: 'Failed to add favorite location.',
    onSuccess: () => {
      // Refresh data after successful addition
      loadFavorites()
    }
  })
}

const handleCloseAddDialog = () => {
  showAddDialog.value = false
  pendingAddCoordinates.value = null
  pendingAddBounds.value = null

  // Clean up temp marker
  if (tempMarker.value && mapInstance.value) {
    mapInstance.value.removeLayer(tempMarker.value)
    tempMarker.value = null
  }

  // Stop drawing if active and clean up temp layer
  if (isDrawing()) {
    stopDrawing()
  }
  cleanupTempLayer()

  // Reset cursor
  if (mapInstance.value) {
    mapInstance.value.getContainer().style.cursor = ''
  }
}





const focusOnMap = (favorite) => {
  if (!baseMapRef.value) return

  if (favorite.type === 'POINT') {
    baseMapRef.value.setView([favorite.latitude, favorite.longitude], 15)
  } else if (favorite.type === 'AREA') {
    const bounds = baseMapRef.value.L.latLngBounds(
        [favorite.southWestLat, favorite.southWestLon],
        [favorite.northEastLat, favorite.northEastLon]
    )
    baseMapRef.value.fitBounds(bounds, {padding: [50, 50], animate: true})
  }
}

const loadFavorites = async () => {
  try {
    isLoading.value = true
    tableLoading.value = true
    await favoritesStore.fetchFavoritePlaces()

    // Update map markers
    if (mapInstance.value && favoritesLayerRef.value) {
      updateMapMarkers()
    }
  } catch (error) {
    console.error('Error loading favorites:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load favorite locations',
      life: 3000
    })
  } finally {
    isLoading.value = false
    tableLoading.value = false
  }
}

// Map initialization
const handleMapReady = (map) => {
  mapInstance.value = map

  // Add favorites layer
  const favoritesLayer = baseMapRef.value.L.layerGroup().addTo(mapInstance.value)
  favoritesLayerRef.value = favoritesLayer

  // Initialize rectangle drawing
  initializeDrawing(mapInstance.value)

  // Initial update
  updateMapMarkers()
}

const handleMapContextMenu = (e) => {
  // If drawing is in progress, do nothing
  if (isDrawing()) {
    return
  }

  // Don't show map context menu if favorite context menu is active
  if (favoriteContextMenuActive.value) {
    favoriteContextMenuActive.value = false
    return
  }

  // Store the latlng for context menu actions
  contextMenuLatLng.value = e.latlng

  // Show PrimeVue context menu
  if (mapContextMenuRef.value && e.originalEvent) {
    mapContextMenuRef.value.show(e.originalEvent)
  }
}

const handleFavoriteContextMenu = (e, favorite) => {
  // Set flag to prevent map context menu
  favoriteContextMenuActive.value = true

  // Prevent default browser context menu and map context menu
  if (e.originalEvent) {
    e.originalEvent.preventDefault()
    e.originalEvent.stopPropagation()
  }

  if (baseMapRef.value && baseMapRef.value.L) {
    baseMapRef.value.L.DomEvent.stopPropagation(e)
  }

  // Store the selected favorite
  selectedFavorite.value = favorite

  // Show favorite context menu
  if (favoriteContextMenuRef.value && e.originalEvent) {
    // Use setTimeout to ensure the event has fully propagated/stopped
    setTimeout(() => {
      favoriteContextMenuRef.value.show(e.originalEvent)
      // Reset the flag after a short delay
      setTimeout(() => {
        favoriteContextMenuActive.value = false
      }, 100)
    }, 10)
  }
}

const updateMapMarkers = () => {
  if (!mapInstance.value || !favoritesLayerRef.value) return

  // Clear existing markers
  favoritesLayerRef.value.clearLayers()

  // Add point markers
  displayedFavorites.value.forEach(favorite => {
    if (favorite.type === 'POINT') {
      const marker = baseMapRef.value.L.marker([favorite.latitude, favorite.longitude], {
        icon: baseMapRef.value.L.divIcon({
          className: 'favorite-point-marker',
          html: '<div class="favorite-marker-icon"><i class="pi pi-map-marker"></i></div>',
          iconSize: [40, 40],
          iconAnchor: [20, 40]
        })
      })

      marker.bindPopup(`<strong>${favorite.name}</strong>`)
      marker.on('click', () => focusOnMap(favorite))

      // Add context menu handler
      marker.on('contextmenu', (e) => {
        handleFavoriteContextMenu(e, favorite)
      })

      marker.addTo(favoritesLayerRef.value)
    } else if (favorite.type === 'AREA') {
      const bounds = [
        [favorite.southWestLat, favorite.southWestLon],
        [favorite.northEastLat, favorite.northEastLon]
      ]

      const rectangle = baseMapRef.value.L.rectangle(bounds, {
        color: '#ef4444',
        fillColor: '#ef4444',
        fillOpacity: 0.2,
        weight: 2
      })

      rectangle.bindPopup(`<strong>${favorite.name}</strong><br>Area Favorite`)
      rectangle.on('click', () => focusOnMap(favorite))

      // Add context menu handler
      rectangle.on('contextmenu', (e) => {
        handleFavoriteContextMenu(e, favorite)
      })

      rectangle.addTo(favoritesLayerRef.value)
    }
  })

  // Fit map to show all markers if there are any
  if (displayedFavorites.value.length > 0 && !pendingAddCoordinates.value) {
    const bounds = []
    displayedFavorites.value.forEach(favorite => {
      if (favorite.type === 'POINT') {
        bounds.push([favorite.latitude, favorite.longitude])
      } else if (favorite.type === 'AREA') {
        bounds.push([favorite.southWestLat, favorite.southWestLon])
        bounds.push([favorite.northEastLat, favorite.northEastLon])
      }
    })

    if (bounds.length > 0) {
      mapInstance.value.fitBounds(bounds, {padding: [50, 50], maxZoom: 15})
    }
  }
}

// Watch for filter changes to update map
watch(() => displayedFavorites.value, () => {
  updateMapMarkers()
}, {deep: true})

// Lifecycle
onMounted(async () => {
  handleResize()
  window.addEventListener('resize', handleResize)

  await loadFavorites()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (searchTimeout) clearTimeout(searchTimeout)

  // Clean up temp marker
  if (tempMarker.value && mapInstance.value) {
    try {
      mapInstance.value.removeLayer(tempMarker.value)
    } catch (error) {
      console.warn('Error removing temp marker:', error)
    }
    tempMarker.value = null
  }

  // Clean up drawing
  if (isDrawing()) {
    try {
      stopDrawing()
    } catch (error) {
      console.warn('Error stopping drawing:', error)
    }
  }
  try {
    cleanupTempLayer()
  } catch (error) {
    console.warn('Error cleaning up temp layer:', error)
  }

  // Clean up favorites layer
  if (favoritesLayerRef.value && mapInstance.value) {
    try {
      favoritesLayerRef.value.clearLayers()
    } catch (error) {
      console.warn('Error clearing favorites layer:', error)
    }
    favoritesLayerRef.value = null
  }
})
</script>

<style scoped>
/* Map Section */
.map-section {
  margin-bottom: var(--gp-spacing-lg);
}

.map-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--gp-spacing-md);
  padding: var(--gp-spacing-md);
  border-bottom: 1px solid var(--gp-border-light);
}

.map-title {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.map-subtitle {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
}

.map-container {
  width: 100%;
  height: 500px;
  position: relative;
}

.map-element {
  width: 100%;
  height: 100%;
  border-radius: var(--gp-radius-medium);
  overflow: hidden;
}

/* Filter Section */
.filter-section {
  margin-bottom: var(--gp-spacing-lg);
}

.filter-controls {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-lg);
  flex-wrap: wrap;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  flex: 1;
  min-width: 200px;
}

.filter-label {
  font-weight: 500;
  color: var(--gp-text-secondary);
  white-space: nowrap;
}

.type-select {
  flex: 1;
  max-width: 250px;
}

.search-input {
  flex: 1;
  max-width: 400px;
}

/* Table Section */
.table-section {
  overflow: hidden;
}

.table-header {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.table-title {
  font-weight: 600;
  font-size: 1.1rem;
  color: var(--gp-text-primary);
}

/* Table Layout */
.favorites-table :deep(.p-datatable-table) {
  table-layout: fixed;
  width: 100%;
}

/* Table Columns */
.favorites-table :deep(.name-col) {
  width: 20%;
}

.favorites-table :deep(.type-col) {
  width: 10%;
}

.favorites-table :deep(.location-col) {
  width: 20%;
}

.favorites-table :deep(.city-col) {
  width: 15%;
}

.favorites-table :deep(.country-col) {
  width: 15%;
}

.favorites-table :deep(.actions-col) {
  width: 20%;
}

/* Cell Content */
.name-cell {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  font-weight: 500;
  color: var(--gp-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.type-icon {
  font-size: 1rem;
  color: var(--gp-primary);
  flex-shrink: 0;
}

.location-cell {
  font-family: monospace;
  font-size: 0.8rem;
  color: var(--gp-text-primary);
}

.null-value {
  color: var(--gp-text-muted);
  font-style: italic;
}

.type-tag {
  font-size: 0.75rem;
}

/* Empty State */
.empty-state {
  text-align: center;
  padding: var(--gp-spacing-xxl) var(--gp-spacing-lg);
}

.empty-icon {
  font-size: 3rem;
  color: var(--gp-text-muted);
  margin-bottom: var(--gp-spacing-lg);
  display: block;
}

.empty-state h3 {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--gp-text-secondary);
  margin: 0 0 var(--gp-spacing-md);
}

.empty-state p {
  color: var(--gp-text-muted);
  margin: 0;
}

/* Actions Column */
.actions-buttons {
  display: flex;
  gap: var(--gp-spacing-xs);
  justify-content: center;
  align-items: center;
}

.action-button {
  min-width: 32px !important;
  width: 32px !important;
  height: 32px !important;
  padding: 0 !important;
  border-radius: var(--gp-radius-small);
  transition: all 0.2s ease;
}

.edit-button:hover {
  background-color: var(--gp-primary-light) !important;
  color: var(--gp-primary) !important;
}

.delete-button:hover {
  background-color: var(--p-red-50) !important;
  color: var(--p-red-600) !important;
}

.view-button:hover {
  background-color: var(--gp-primary-light) !important;
  color: var(--gp-primary) !important;
}

.map-button:hover {
  background-color: var(--p-cyan-50) !important;
  color: var(--p-cyan-600) !important;
}

/* Header Actions */
.header-actions {
  display: flex;
  gap: var(--gp-spacing-md);
  align-items: center;
}

.header-info {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  font-style: italic;
}

/* Dialog Content */
.dialog-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--gp-spacing-md);
  padding: var(--gp-spacing-md) 0;
}

.warning-icon {
  font-size: 3rem;
  color: var(--p-yellow-500);
}

.dialog-message {
  text-align: center;
  color: var(--gp-text-secondary);
  line-height: 1.5;
  margin: 0;
}

/* Responsive Design */
@media (max-width: 768px) {
  .filter-controls {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-group {
    flex-direction: column;
    align-items: stretch;
    min-width: unset;
  }

  .type-select,
  .search-input {
    max-width: 100%;
    width: 100%;
  }

  .favorites-table :deep(.name-col) {
    width: 35%;
  }

  .favorites-table :deep(.type-col) {
    width: 20%;
  }

  .favorites-table :deep(.location-col) {
    width: 25%;
  }

  .favorites-table :deep(.actions-col) {
    width: 20%;
  }

  .map-container {
    height: 350px;
  }
}

/* Dark Mode */
.p-dark .favorites-table :deep(.p-datatable) {
  background: var(--gp-surface-dark) !important;
  color: var(--gp-text-primary) !important;
}

.p-dark .favorites-table :deep(.p-datatable-header) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .favorites-table :deep(.p-datatable-tbody > tr) {
  background: var(--gp-surface-dark) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .favorites-table :deep(.p-datatable-tbody > tr:hover) {
  background: var(--gp-surface-light) !important;
}

.p-dark .favorites-table :deep(.p-datatable-tbody > tr > td) {
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .favorites-table :deep(.p-datatable-thead > tr > th) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .favorites-table :deep(.p-datatable-wrapper) {
  background: var(--gp-surface-dark) !important;
}

.p-dark .favorites-table :deep(.p-paginator) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .map-header {
  border-bottom-color: var(--gp-border-dark);
}
</style>

<style>
/* Global marker styles */
.temp-favorite-marker,
.favorite-point-marker {
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
}

/* Favorite marker icon - teardrop shape */
.favorite-marker-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ef4444;
  color: white;
  border-radius: 50% 50% 50% 0;
  transform: rotate(-45deg);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

.favorite-marker-icon i {
  transform: rotate(45deg);
  font-size: 1.5rem;
}

/* Temp marker icon - teardrop shape with different color */
.temp-marker-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f59e0b;
  color: white;
  border-radius: 50% 50% 50% 0;
  transform: rotate(-45deg);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

.temp-marker-icon i {
  transform: rotate(45deg);
  font-size: 1.5rem;
}

/* Dark mode support */
.p-dark .favorite-marker-icon {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.6);
}

.p-dark .temp-marker-icon {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.6);
}
</style>
