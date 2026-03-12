import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'
import { useDateRangeStore } from '@/stores/dateRange'
import { useTimezone } from "@/composables/useTimezone";

let inFlightConfigRequest = null
let inFlightPhotosRequest = null
let inFlightPhotosRequestKey = null

const normalizeDateParam = (value) => {
  if (!value) return null
  if (typeof value === 'string') return value
  if (value instanceof Date) return value.toISOString()
  if (typeof value?.toISOString === 'function') {
    try {
      return value.toISOString()
    } catch {
      // fall through
    }
  }
  return String(value)
}

export const useImmichStore = defineStore('immich', {
  state: () => ({
    // Configuration state
    config: null,
    configLoading: false,
    configError: null,
    
    // Photos state
    photos: [],
    photosLoading: false,
    photosError: null,
    lastFetchedRange: null,
    
    // UI state
    showPhotos: false
  }),

  getters: {
    // Configuration getters
    isConfigured: (state) => !!(state.config && state.config.enabled),
    hasConfig: (state) => !!state.config,
    serverUrl: (state) => state.config?.serverUrl || null,
    isEnabled: (state) => state.config?.enabled || false,

    // Photos getters
    hasPhotos: (state) => state.photos && state.photos.length > 0,
    photoCount: (state) => state.photos?.length || 0,
    
    // Photos grouped by location for map display
    photoGroups: (state) => {
      if (!state.photos || state.photos.length === 0) return []
      
      const groups = new Map()
      
      state.photos.forEach(photo => {
        if (!photo.latitude || !photo.longitude) return
        
        // Group photos by rounded coordinates (to handle slight GPS variations)
        const roundedLat = Math.round(photo.latitude * 10000) / 10000
        const roundedLng = Math.round(photo.longitude * 10000) / 10000
        const key = `${roundedLat},${roundedLng}`
        
        if (!groups.has(key)) {
          groups.set(key, {
            latitude: roundedLat,
            longitude: roundedLng,
            photos: []
          })
        }
        
        groups.get(key).photos.push(photo)
      })
      
      return Array.from(groups.values())
    },
    
    // Check if photos need to be fetched for current date range
    needsRefresh: (state) => {
      const dateRangeStore = useDateRangeStore()
      const currentRange = dateRangeStore.getCurrentDateRange
      
      if (!currentRange || !state.lastFetchedRange) return true
      
      const [currentStart, currentEnd] = currentRange
      const [lastStart, lastEnd] = state.lastFetchedRange
      
      const timezone = useTimezone()
      return !timezone.fromUtc(currentStart).isSame(timezone.fromUtc(lastStart)) || 
             !timezone.fromUtc(currentEnd).isSame(timezone.fromUtc(lastEnd))
    }
  },

  actions: {
    // Configuration actions
    async fetchConfig() {
      if (this.hasConfig && !this.configError) {
        return this.config
      }
      if (inFlightConfigRequest) {
        return inFlightConfigRequest
      }
      
      this.configLoading = true
      this.configError = null
      
      inFlightConfigRequest = (async () => {
        try {
          const response = await apiService.get('/users/me/immich-config')
          this.config = response.data
          
          // Clear any previous errors if successful
          this.configError = null
          return this.config
        } catch (error) {
          console.error('Failed to fetch Immich config:', error)
          
          // Provide more specific error messages
          let errorMessage = 'Failed to load Immich configuration'
          
          if (error.response?.status === 404) {
            errorMessage = 'Immich configuration not found. Please set up your Immich server first.'
          } else if (error.response?.status === 403) {
            errorMessage = 'Access denied. Please check your permissions.'
          } else if (error.isConnectionError) {
            errorMessage = 'Cannot connect to the server. Please check your internet connection.'
          } else if (error.userMessage) {
            errorMessage = error.userMessage
          }
          
          this.configError = errorMessage
          this.config = null
          throw error
        } finally {
          this.configLoading = false
          inFlightConfigRequest = null
        }
      })()

      return inFlightConfigRequest
    },

    async updateConfig(configData) {
      try {
        const response = await apiService.put('/users/me/immich-config', configData)
        // Refresh config after successful update
        await this.fetchConfig()
        return response
      } catch (error) {
        console.error('Failed to update Immich config:', error)
        throw error
      }
    },

    // Photos actions
    async fetchPhotos(startDate = null, endDate = null, forceRefresh = false) {
      // Don't fetch if not configured
      if (!this.isConfigured) {
        this.photos = []
        return
      }

      // Use date range store if no dates provided
      if (!startDate || !endDate) {
        const dateRangeStore = useDateRangeStore()
        const dateRange = dateRangeStore.getCurrentDateRange
        
        if (!dateRange || dateRange.length !== 2) {
          console.warn('No date range available for fetching Immich photos')
          return
        }
        
        [startDate, endDate] = dateRange
      }

      const normalizedStartDate = normalizeDateParam(startDate)
      const normalizedEndDate = normalizeDateParam(endDate)
      if (!normalizedStartDate || !normalizedEndDate) {
        return
      }

      const requestKey = `${normalizedStartDate}|${normalizedEndDate}`

      // Check if we need to fetch or if we have cached data
      const hasSameRangeCache = Array.isArray(this.lastFetchedRange) &&
        this.lastFetchedRange.length === 2 &&
        String(this.lastFetchedRange[0]) === normalizedStartDate &&
        String(this.lastFetchedRange[1]) === normalizedEndDate

      if (!forceRefresh && hasSameRangeCache && this.photos.length > 0) {
        return this.photos
      }

      if (!forceRefresh && inFlightPhotosRequest) {
        if (inFlightPhotosRequestKey === requestKey) {
          return inFlightPhotosRequest
        }
      }

      this.photosLoading = true
      this.photosError = null

      inFlightPhotosRequestKey = requestKey
      inFlightPhotosRequest = (async () => {
        try {
          const params = {
            startDate: normalizedStartDate,
            endDate: normalizedEndDate
          }

          const response = await apiService.get('/users/me/immich/photos/search', params)
          const photos = response.data?.photos || []
          
          // Store photos with endpoint paths only (no full URLs)
          this.photos = photos.map(photo => ({
            ...photo,
            // Remove /api prefix from backend paths since apiService will add it
            thumbnailUrl: photo.thumbnailUrl ? photo.thumbnailUrl.replace(/^\/api/, '') : null,
            downloadUrl: photo.downloadUrl ? photo.downloadUrl.replace(/^\/api/, '') : null
          }))
          
          this.lastFetchedRange = [normalizedStartDate, normalizedEndDate]
          
          // Clear any previous errors if successful
          this.photosError = null
          return this.photos
        } catch (error) {
          console.error('Failed to fetch Immich photos:', error)
          
          // Provide more specific error messages
          let errorMessage = 'Failed to load photos from Immich'
          
          if (error.response?.status === 400) {
            errorMessage = 'Invalid date range or search parameters. Please check your timeline settings.'
          } else if (error.response?.status === 404) {
            errorMessage = 'Immich server not found. Please check your server configuration.'
          } else if (error.response?.status === 500) {
            errorMessage = 'Immich server error. Please check your server status or try again later.'
          } else if (error.isConnectionError) {
            errorMessage = 'Cannot connect to Immich server. Please check your server URL and network connection.'
          } else if (error.userMessage) {
            errorMessage = error.userMessage
          }
          
          this.photosError = errorMessage
          this.photos = []
          throw error
        } finally {
          this.photosLoading = false
          inFlightPhotosRequest = null
          inFlightPhotosRequestKey = null
        }
      })()

      return inFlightPhotosRequest
    },

    // UI actions
    async togglePhotos(value) {
      this.showPhotos = value !== undefined ? value : !this.showPhotos
      
      // Auto-fetch photos when enabling if configured
      if (this.showPhotos && this.isConfigured && this.needsRefresh) {
        try {
          await this.fetchPhotos()
        } catch (error) {
          // Error handling is already done in fetchPhotos, just log here
          console.warn('Failed to auto-fetch photos on toggle:', error)
        }
      }
    },

    // Retry mechanism for failed requests
    async retryLastOperation() {
      if (this.photosError && !this.photosLoading) {
        console.log('Retrying last photo fetch operation')
        await this.fetchPhotos(null, null, true) // Force refresh
      }
      
      if (this.configError && !this.configLoading) {
        console.log('Retrying config fetch operation')
        await this.fetchConfig()
      }
    },

    // Utility actions
    clearPhotos() {
      this.photos = []
      this.lastFetchedRange = null
      this.photosError = null
    },

    clearErrors() {
      this.configError = null
      this.photosError = null
    },

    // Utility methods for constructing API endpoints (if needed)
    getThumbnailEndpoint(photoId) {
      return `/users/me/immich/photos/${photoId}/thumbnail`
    },

    getDownloadEndpoint(photoId) {
      return `/users/me/immich/photos/${photoId}/download`
    }
  }
})
