import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'

export const useBoatSetupStore = defineStore('boatSetup', {
  state: () => ({
    status: null,
    currentJobId: null
  }),

  getters: {
    isReady: (state) => state.status?.status === 'READY',
    isRunning: (state) => ['QUEUED', 'RUNNING'].includes(state.status?.status),
    hasFailed: (state) => state.status?.status === 'FAILED'
  },

  actions: {
    async fetchStatus() {
      const response = await apiService.get('/boat/setup/status')
      this.status = response.data
      this.currentJobId = response.data?.jobId || this.currentJobId
      return this.status
    },

    async startSetup() {
      const response = await apiService.post('/boat/setup/start')
      this.currentJobId = response.data?.jobId
      this.status = response.data?.status || this.status
      return response.data
    },

    async fetchJob(jobId = this.currentJobId) {
      if (!jobId) return this.fetchStatus()
      const response = await apiService.get(`/boat/setup/jobs/${jobId}`)
      this.status = response.data
      this.currentJobId = response.data?.jobId || jobId
      return this.status
    }
  }
})
