import { ref } from 'vue'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { useFavoriteEditor } from '@/composables/useFavoriteEditor'
import { useFavoritesStore } from '@/stores/favorites'
import { useGeocodingStore } from '@/stores/geocoding'

const findFavoriteById = (favoritePlaces, favoriteId) => {
  const points = favoritePlaces?.points || []
  const areas = favoritePlaces?.areas || []
  return [...points, ...areas].find((favorite) => String(favorite.id) === String(favoriteId)) || null
}

export function useTimelineLocationEditing(options = {}) {
  const confirm = useConfirm()
  const toast = useToast()
  const favoritesStore = useFavoritesStore()
  const geocodingStore = useGeocodingStore()

  const showGeocodingEditDialog = ref(false)
  const editGeocodingData = ref(null)

  const {
    showDialog: showFavoriteDialog,
    selectedFavorite,
    openEditor: openFavoriteEditor,
    closeEditor: closeFavoriteEditor,
    handleSave: saveFavorite,
    withTimelineRegeneration,
    timelineRegenerationVisible,
    timelineRegenerationType,
    currentJobId,
    jobProgress
  } = useFavoriteEditor()

  const closeGeocodingDialog = () => {
    showGeocodingEditDialog.value = false
    editGeocodingData.value = null
  }

  const handleFavoriteDialogSave = async (data) => {
    await saveFavorite(data, {
      onSuccess: async () => {
        await options.onFavoriteSaved?.(data)
      }
    })
  }

  const handleSaveGeocoding = async (updatedData) => {
    if (!editGeocodingData.value?.id) return

    const oldGeocodingId = editGeocodingData.value.id
    try {
      const updated = await geocodingStore.updateGeocodingResult(oldGeocodingId, updatedData)
      await options.onGeocodingSaved?.(oldGeocodingId, updated)

      toast.add({
        severity: 'success',
        summary: 'Updated',
        detail: 'Stay location name updated successfully.',
        life: 3000
      })

      closeGeocodingDialog()
    } catch (error) {
      console.error('Failed to update geocoding result from timeline:', error)
      const errorMessage = error.response?.data?.message || error.message || 'Failed to update stay location'
      toast.add({
        severity: 'error',
        summary: 'Update Failed',
        detail: errorMessage,
        life: 5000
      })
    }
  }

  const openFavoriteRenameDialog = async (stayItem) => {
    let favorite = findFavoriteById(favoritesStore.favoritePlaces, stayItem.favoriteId)

    if (!favorite) {
      await favoritesStore.fetchFavoritePlaces()
      favorite = findFavoriteById(favoritesStore.favoritePlaces, stayItem.favoriteId)
    }

    if (!favorite) {
      toast.add({
        severity: 'error',
        summary: 'Favorite Not Found',
        detail: 'Could not load favorite details for this stay.',
        life: 4000
      })
      return
    }

    openFavoriteEditor({ ...favorite })
  }

  const openGeocodingRenameDialog = async (stayItem) => {
    try {
      const geocoding = await geocodingStore.getGeocodingResult(stayItem.geocodingId)
      editGeocodingData.value = {
        id: geocoding?.id ?? stayItem.geocodingId,
        displayName: geocoding?.displayName ?? stayItem.locationName ?? '',
        city: geocoding?.city ?? stayItem.city ?? '',
        country: geocoding?.country ?? stayItem.country ?? '',
        latitude: geocoding?.latitude ?? stayItem.latitude,
        longitude: geocoding?.longitude ?? stayItem.longitude,
        providerName: geocoding?.providerName || 'Unknown'
      }
      showGeocodingEditDialog.value = true
    } catch (error) {
      console.error('Failed to load geocoding details for stay rename:', error)
      const errorMessage = error.response?.data?.message || error.message || 'Could not load geocoding details'
      toast.add({
        severity: 'error',
        summary: 'Unable to Rename',
        detail: errorMessage,
        life: 5000
      })
    }
  }

  const handleRenameStay = (stayItem) => {
    if (!stayItem?.favoriteId && !stayItem?.geocodingId) {
      return
    }

    const locationName = stayItem.locationName || 'this location'

    confirm.require({
      header: 'Rename Stay Location',
      message: `Renaming "${locationName}" will update all stays with this name. Continue?`,
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        if (stayItem.favoriteId) {
          openFavoriteRenameDialog(stayItem)
          return
        }

        openGeocodingRenameDialog(stayItem)
      }
    })
  }

  return {
    showFavoriteDialog,
    selectedFavorite,
    openFavoriteEditor,
    closeFavoriteEditor,
    handleFavoriteDialogSave,
    withTimelineRegeneration,
    timelineRegenerationVisible,
    timelineRegenerationType,
    currentJobId,
    jobProgress,
    showGeocodingEditDialog,
    editGeocodingData,
    closeGeocodingDialog,
    handleSaveGeocoding,
    handleRenameStay
  }
}
