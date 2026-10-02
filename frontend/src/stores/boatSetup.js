import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'
import { normalizeApiError } from '@/utils/apiErrorDetail'

let pollingTimer = null
let pollInFlight = false

export const useBoatSetupStore = defineStore('boatSetup', {
  state: () => ({
    status: null,
    currentJobId: null,
    error: null
  }),

  getters: {
    isReady: (state) => state.status?.status === 'READY',
    isRunning: (state) => ['QUEUED', 'RUNNING'].includes(state.status?.status),
    hasFailed: (state) => state.status?.status === 'FAILED'
  },

  actions: {
    async fetchStatus() {
      this.error = null
      try {
        this.status = await apiService.get('/trip-planning/boat-setup')
        this.currentJobId = this.status?.jobId || this.currentJobId
        return this.status
      } catch (error) {
        this.error = normalizeApiError(error, 'Failed to load Boat setup status')
        throw this.error
      }
    },

    async startSetup() {
      this.error = null
      try {
        const setup = await apiService.post('/trip-planning/boat-setup')
        this.currentJobId = setup?.jobId || this.currentJobId
        this.status = setup?.status || this.status
        return setup
      } catch (error) {
        this.error = normalizeApiError(error, 'Failed to start Boat setup')
        throw this.error
      }
    },

    async fetchJob(jobId = this.currentJobId) {
      if (!jobId) return this.fetchStatus()
      this.error = null
      try {
        this.status = await apiService.get(`/trip-planning/boat-setup/jobs/${jobId}`)
        this.currentJobId = this.status?.jobId || jobId
        return this.status
      } catch (error) {
        this.error = normalizeApiError(error, 'Failed to load Boat setup job')
        throw this.error
      }
    },

    startPolling({ onSettled, onError, intervalMs = 2000 } = {}) {
      this.stopPolling()
      pollingTimer = window.setInterval(async () => {
        if (pollInFlight) return
        pollInFlight = true
        try {
          const status = this.currentJobId
            ? await this.fetchJob(this.currentJobId)
            : await this.fetchStatus()
          if (['READY', 'FAILED'].includes(status?.status)) {
            this.stopPolling()
            await onSettled?.(status)
          }
        } catch (error) {
          this.stopPolling()
          onError?.(error)
        } finally {
          pollInFlight = false
        }
      }, intervalMs)
    },

    stopPolling() {
      if (pollingTimer) window.clearInterval(pollingTimer)
      pollingTimer = null
      pollInFlight = false
    }
  }
})
