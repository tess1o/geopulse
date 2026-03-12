import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'
import { useDateRangeStore } from '@/stores/dateRange'

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
    lastFetchedRange: null
  }),

  getters: {
    // Configuration getters
    isConfigured: (state) => !!(state.config && state.config.enabled),
    hasConfig: (state) => !!state.config,

    // Photos getters
    hasPhotos: (state) => state.photos && state.photos.length > 0
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

    // Utility actions
    clearPhotos() {
      this.photos = []
      this.lastFetchedRange = null
      this.photosError = null
    },

    clearErrors() {
      this.configError = null
      this.photosError = null
    }
  }
})
