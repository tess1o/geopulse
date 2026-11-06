import {areaGeometryKey, areaPayload} from '@/maps/favoritesManagement/shared/favoritesManagementGeometry'
import { ref } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useFavoritesStore } from '@/stores/favorites'
import { useTimelineRegeneration } from '@/composables/useTimelineRegeneration'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

/**
 * Composable for editing favorite locations.
 * Encapsulates all the logic for:
 * - Dialog state management
 * - Original bounds tracking (for AREA favorites)
 * - Detecting bounds changes
 * - Deciding between timeline regeneration vs simple update
 * 
 * @returns {Object} Editor state and methods
 */
export function useFavoriteEditor() {
  const toast = useToast()
  const favoritesStore = useFavoritesStore()

  // Dialog state
  const showDialog = ref(false)
  const selectedFavorite = ref(null)
  const originalBounds = ref(null)

  // Timeline regeneration (from inner composable)
  const {
    timelineRegenerationVisible,
    timelineRegenerationType,
    currentJobId,
    jobProgress,
    withTimelineRegeneration
  } = useTimelineRegeneration()

  /**
   * Open the edit dialog for a favorite
   * @param {Object} favorite - The favorite to edit (must include id, name, type, and bounds if AREA)
   */
  const openEditor = (favorite) => {
    selectedFavorite.value = JSON.parse(JSON.stringify(favorite))

    // Store original bounds for comparison (only for AREA favorites)
    if (favorite.type === 'AREA') {
      originalBounds.value = areaGeometryKey(favorite)
    } else {
      originalBounds.value = null
    }

    showDialog.value = true
  }

  /**
   * Close the edit dialog and reset state
   */
  const closeEditor = () => {
    showDialog.value = false
    selectedFavorite.value = null
    originalBounds.value = null
  }

  /**
   * Check if bounds have changed from the original
   * @param {Object|null} newBounds - The new bounds object
   * @returns {boolean} True if bounds changed
   */
  const hasBoundsChanged = (newBounds) => {
    if (!newBounds || !originalBounds.value) return false

    return areaGeometryKey(newBounds) !== originalBounds.value
  }

  /**
   * Handle saving the edited favorite
   * Automatically detects if bounds changed and uses appropriate save flow
   * 
   * @param {Object} updatedData - The updated favorite data from the dialog
   * @param {Object} options - Optional callbacks
   * @param {Function} options.onSuccess - Called after successful save
   */
  const handleSave = async (updatedData, options = {}) => {
    // Prepare bounds if it's an area favorite
    const bounds = updatedData.type === 'AREA' ? areaPayload(updatedData) : null

    // Capture values to avoid closure issues
    const favoriteId = updatedData.id
    const favoriteName = updatedData.name
    const city = updatedData.city
    const country = updatedData.country

    // Check if bounds changed
    const boundsChanged = hasBoundsChanged(bounds)

    if (boundsChanged) {
      // Bounds changed: use timeline regeneration flow
      const action = () => favoritesStore.editFavorite(
        favoriteId,
        favoriteName,
        city,
        country,
        bounds
      )

      await withTimelineRegeneration(action, {
        modalType: 'favorite',
        successMessage: `Favorite "${favoriteName}" updated successfully. Timeline is regenerating.`,
        errorMessage: 'Failed to update favorite location.',
        onSuccess: () => {
          showDialog.value = false
          options.onSuccess?.()
        }
      })
    } else {
      // No bounds change: simple update
      try {
        await favoritesStore.editFavorite(
          favoriteId,
          favoriteName,
          city,
          country,
          bounds
        )

        showDialog.value = false
        toast.add({
          severity: 'success',
          summary: 'Updated',
          detail: `Favorite "${favoriteName}" updated successfully.`,
          life: 3000
        })

        if (options.onSuccess) {
          options.onSuccess()
        }
      } catch (error) {
        console.error('Error updating favorite:', error)
        const errorMessage = formatApiErrorDetail(error, 'Failed to update favorite location')
        toast.add({
          severity: 'error',
          summary: 'Update Failed',
          detail: errorMessage,
          life: 5000
        })
      }
    }
  }

  return {
    // Dialog state
    showDialog,
    selectedFavorite,

    // Methods
    openEditor,
    closeEditor,
    handleSave,

    // Timeline regeneration modal state (for parent to render)
    withTimelineRegeneration,
    timelineRegenerationVisible,
    timelineRegenerationType,
    currentJobId,
    jobProgress
  }
}
