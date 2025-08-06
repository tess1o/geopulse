import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'
import { useDateRangeStore } from '@/stores/dateRange'

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
      
      return currentStart?.getTime() !== lastStart?.getTime() || 
             currentEnd?.getTime() !== lastEnd?.getTime()
    }
  },

  actions: {
    // Configuration actions
    async fetchConfig() {
      if (this.configLoading) return
      
      this.configLoading = true
      this.configError = null
      
      try {
        const response = await apiService.get('/users/me/immich-config')
        this.config = response.data
        
        // Clear any previous errors if successful
        this.configError = null
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
      } finally {
        this.configLoading = false
      }
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

      // Check if we need to fetch or if we have cached data
      if (!forceRefresh && !this.needsRefresh && this.photos.length > 0) {
        return
      }

      if (this.photosLoading) return

      this.photosLoading = true
      this.photosError = null

      try {
        const params = {
          startDate: startDate.toISOString(),
          endDate: endDate.toISOString()
        }

        const response = await apiService.get('/users/me/immich/photos/search', params)
        const photos = response.data?.photos || []
        
        // Fix thumbnail and download URLs to use the correct backend URL
        const API_BASE_URL = window.VUE_APP_CONFIG?.API_BASE_URL || '/api'
        const baseUrl = API_BASE_URL.startsWith('http') 
          ? API_BASE_URL.replace('/api', '') 
          : window.location.origin
        
        this.photos = photos.map(photo => ({
          ...photo,
          thumbnailUrl: photo.thumbnailUrl ? `${baseUrl}${photo.thumbnailUrl}` : null,
          downloadUrl: photo.downloadUrl ? `${baseUrl}${photo.downloadUrl}` : null
        }))
        
        this.lastFetchedRange = [startDate, endDate]
        
        console.log(`Fetched ${this.photos.length} Immich photos for date range`)
        console.log('API_BASE_URL:', API_BASE_URL)
        console.log('Base URL for thumbnails:', baseUrl)
        if (this.photos.length > 0) {
          console.log('Sample thumbnail URL:', this.photos[0].thumbnailUrl)
        }
        
        // Clear any previous errors if successful
        this.photosError = null
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
      } finally {
        this.photosLoading = false
      }
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

    // Get thumbnail URL for a photo
    getThumbnailUrl(photoId) {
      const API_BASE_URL = window.VUE_APP_CONFIG?.API_BASE_URL || '/api'
      const baseUrl = API_BASE_URL.startsWith('http') 
        ? API_BASE_URL.replace('/api', '') 
        : window.location.origin
      return `${baseUrl}/api/users/me/immich/photos/${photoId}/thumbnail`
    },

    // Get download URL for a photo
    getDownloadUrl(photoId) {
      const API_BASE_URL = window.VUE_APP_CONFIG?.API_BASE_URL || '/api'
      const baseUrl = API_BASE_URL.startsWith('http') 
        ? API_BASE_URL.replace('/api', '') 
        : window.location.origin
      return `${baseUrl}/api/users/me/immich/photos/${photoId}/download`
    }
  }
})