import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'
import { normalizeApiError } from '@/utils/apiErrorDetail'

const emptyPagination = () => ({
  currentPage: 0,
  pageSize: 50,
  totalCount: 0,
  totalPages: 0
})

export const usePlaceStatisticsStore = defineStore('placeStatistics', {
  state: () => ({
    placeDetails: null,
    placeVisits: [],
    photoSearchWindow: null,
    pagination: emptyPagination(),
    loading: false,
    error: null
  }),

  actions: {
    clearPlaceData() {
      this.placeDetails = null
      this.placeVisits = []
      this.photoSearchWindow = null
      this.pagination = emptyPagination()
      this.error = null
    },

    fail(error, fallback) {
      this.error = normalizeApiError(error, fallback)
      return this.error
    },

    async fetchPlaceDetails(type, id) {
      this.loading = true
      this.error = null
      try {
        this.placeDetails = await apiService.get(`/places/${type}/${id}`)
        return this.placeDetails
      } catch (error) {
        throw this.fail(error, 'Failed to fetch place details')
      } finally {
        this.loading = false
      }
    },

    async fetchPhotoSearchWindow(type, id, radiusMeters = 100) {
      try {
        this.photoSearchWindow = await apiService.get(`/places/${type}/${id}/photo-search-window`, {
          radiusMeters
        })
        return this.photoSearchWindow
      } catch (error) {
        this.photoSearchWindow = null
        throw this.fail(error, 'Failed to load the place photo search window')
      }
    },

    async fetchPlaceVisits(type, id, page = 0, pageSize = 50, sortBy = 'timestamp', sortDirection = 'desc') {
      this.loading = true
      this.error = null
      try {
        const data = await apiService.get(`/places/${type}/${id}/visits`, {
          page,
          size: pageSize,
          sortBy,
          sortDirection
        })
        this.placeVisits = data.items || []
        this.pagination = {
          currentPage: data.page ?? 0,
          pageSize: data.size ?? 50,
          totalCount: data.totalElements ?? 0,
          totalPages: data.totalPages ?? 0
        }
        return data
      } catch (error) {
        throw this.fail(error, 'Failed to fetch place visits')
      } finally {
        this.loading = false
      }
    },

    async updatePlaceName(type, id, newName) {
      this.loading = true
      this.error = null
      try {
        await apiService.put(`/places/${type}/${id}`, { name: newName })
        if (this.placeDetails && String(this.placeDetails.id) === String(id) && this.placeDetails.type === type) {
          this.placeDetails.locationName = newName
        }
      } catch (error) {
        throw this.fail(error, 'Failed to update place name')
      } finally {
        this.loading = false
      }
    },

    async exportVisits(type, id, sortBy = 'timestamp', sortDirection = 'desc') {
      try {
        return await apiService.download(`/places/${type}/${id}/visits/export`, {
          sortBy,
          sortDirection
        })
      } catch (error) {
        throw this.fail(error, 'Failed to export visits to CSV')
      }
    },

    async refreshVisits() {
      if (!this.placeDetails) return
      const { type, id } = this.placeDetails
      const { currentPage, pageSize } = this.pagination
      await this.fetchPlaceVisits(type, id, currentPage, pageSize)
    },

    async loadPage(page) {
      if (!this.placeDetails) return
      await this.fetchPlaceVisits(this.placeDetails.type, this.placeDetails.id, page, this.pagination.pageSize)
    }
  }
})
