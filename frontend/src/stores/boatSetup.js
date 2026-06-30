import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'

const unwrapApiData = (response) => response?.data ?? response

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
      const status = unwrapApiData(response)
      this.status = status
      this.currentJobId = status?.jobId || this.currentJobId
      return this.status
    },

    async startSetup() {
      const response = await apiService.post('/boat/setup/start')
      const setup = unwrapApiData(response)
      this.currentJobId = setup?.jobId || this.currentJobId
      this.status = setup?.status || this.status
      return setup
    },

    async fetchJob(jobId = this.currentJobId) {
      if (!jobId) return this.fetchStatus()
      const response = await apiService.get(`/boat/setup/jobs/${jobId}`)
      const status = unwrapApiData(response)
      this.status = status
      this.currentJobId = status?.jobId || jobId
      return this.status
    }
  }
})
