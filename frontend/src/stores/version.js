import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'
import { normalizeApiError } from '@/utils/apiErrorDetail'

export const useVersionStore = defineStore('version', {
  state: () => ({ version: null, status: null, error: null }),
  actions: {
    async fetchVersion() {
      try {
        const response = await apiService.get('/system/version')
        this.version = response.version
        return response
      } catch (error) {
        this.error = normalizeApiError(error, 'Failed to fetch version')
        throw this.error
      }
    },
    async fetchStatus() {
      try {
        this.status = await apiService.get('/system/version/status')
        this.version = this.status.currentVersion || this.status.version || null
        return this.status
      } catch (error) {
        this.error = normalizeApiError(error, 'Failed to fetch version status')
        throw this.error
      }
    }
  }
})
