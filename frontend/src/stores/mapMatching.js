import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'
import { normalizeApiError } from '@/utils/apiErrorDetail'

export const useMapMatchingStore = defineStore('mapMatching', {
  state: () => ({ error: null }),

  actions: {
    async resolve(tripIds) {
      this.error = null
      try {
        return await apiService.post('/map-matching-jobs', { tripIds })
      } catch (error) {
        this.error = normalizeApiError(error, 'Failed to resolve map matching')
        throw this.error
      }
    },

    async status(targetIds) {
      try {
        return await apiService.post('/map-matching-jobs/searches', { targetIds })
      } catch (error) {
        this.error = normalizeApiError(error, 'Failed to load map matching status')
        throw this.error
      }
    }
  }
})
