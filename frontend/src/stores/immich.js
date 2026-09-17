import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'
import { useDateRangeStore } from '@/stores/dateRange'
import { normalizeApiError } from '@/utils/apiErrorDetail'

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

const normalizePhoto = (photo) => ({
  ...photo,
  thumbnailUrl: photo.thumbnailUrl?.replace(/^\/api/, '') || null,
  previewUrl: photo.previewUrl?.replace(/^\/api/, '') || null,
  downloadUrl: photo.downloadUrl?.replace(/^\/api/, '') || null
})

export const useImmichStore = defineStore('immich', {
  state: () => ({
    config: null,
    configLoading: false,
    configError: null,
    photos: [],
    photosLoading: false,
    photosError: null,
    lastFetchedRange: null
  }),

  getters: {
    isConfigured: (state) => !!(state.config && state.config.enabled),
    hasConfig: (state) => !!state.config,
    hasPhotos: (state) => state.photos.length > 0
  },

  actions: {
    fail(error, fallback) {
      return normalizeApiError(error, fallback)
    },

    async fetchConfig(force = false) {
      if (!force && this.hasConfig && !this.configError) return this.config
      if (!force && inFlightConfigRequest) return inFlightConfigRequest

      this.configLoading = true
      this.configError = null
      inFlightConfigRequest = (async () => {
        try {
          this.config = await apiService.get('/users/me/immich-config') || null
          return this.config
        } catch (error) {
          this.configError = this.fail(error, 'Failed to load Immich configuration')
          this.config = null
          throw this.configError
        } finally {
          this.configLoading = false
          inFlightConfigRequest = null
        }
      })()
      return inFlightConfigRequest
    },

    async updateConfig(configData) {
      this.configError = null
      try {
        await apiService.put('/users/me/immich-config', configData)
        await this.fetchConfig(true)
      } catch (error) {
        this.configError = this.fail(error, 'Failed to update Immich configuration')
        throw this.configError
      }
    },

    async testConnection(configData) {
      try {
        return await apiService.post('/users/me/immich-config/test', configData)
      } catch (error) {
        this.configError = this.fail(error, 'Failed to test Immich connection')
        throw this.configError
      }
    },

    async searchPhotos(params) {
      try {
        return await apiService.get('/users/me/immich/photos/search', params)
      } catch (error) {
        throw this.fail(error, 'Failed to search Immich photos')
      }
    },

    async fetchPhotoMapMarkers(params) {
      try {
        return await apiService.get('/users/me/immich/photos/map-markers', params)
      } catch (error) {
        throw this.fail(error, 'Failed to load Immich photo markers')
      }
    },

    async fetchPhotosForMapMarker(params) {
      try {
        return await apiService.get('/users/me/immich/photos/map-marker/photos', params)
      } catch (error) {
        throw this.fail(error, 'Failed to load Immich marker photos')
      }
    },

    async fetchPhotos(startDate = null, endDate = null, forceRefresh = false) {
      if (!this.isConfigured) {
        this.photos = []
        return []
      }

      if (!startDate || !endDate) {
        const dateRange = useDateRangeStore().getCurrentDateRange
        if (!dateRange || dateRange.length !== 2) return []
        ;[startDate, endDate] = dateRange
      }

      const normalizedStart = normalizeDateParam(startDate)
      const normalizedEnd = normalizeDateParam(endDate)
      if (!normalizedStart || !normalizedEnd) return []

      const requestKey = `${normalizedStart}|${normalizedEnd}`
      const hasCache = Array.isArray(this.lastFetchedRange)
        && this.lastFetchedRange[0] === normalizedStart
        && this.lastFetchedRange[1] === normalizedEnd
      if (!forceRefresh && hasCache && this.photos.length > 0) return this.photos
      if (!forceRefresh && inFlightPhotosRequest && inFlightPhotosRequestKey === requestKey) {
        return inFlightPhotosRequest
      }

      this.photosLoading = true
      this.photosError = null
      inFlightPhotosRequestKey = requestKey
      inFlightPhotosRequest = (async () => {
        try {
          const response = await this.searchPhotos({ startDate: normalizedStart, endDate: normalizedEnd })
          this.photos = (response?.photos || []).map(normalizePhoto)
          this.lastFetchedRange = [normalizedStart, normalizedEnd]
          return this.photos
        } catch (error) {
          this.photosError = this.fail(error, 'Failed to load photos from Immich')
          this.photos = []
          throw this.photosError
        } finally {
          this.photosLoading = false
          inFlightPhotosRequest = null
          inFlightPhotosRequestKey = null
        }
      })()
      return inFlightPhotosRequest
    },

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
