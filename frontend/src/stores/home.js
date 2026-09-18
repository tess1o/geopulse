import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'
import { normalizeApiError } from '@/utils/apiErrorDetail'

export const useHomeStore = defineStore('home', {
  state: () => ({
    content: { tips: [], whatsNew: [] },
    error: null
  }),

  actions: {
    async fetchContent() {
      this.error = null
      try {
        const content = await apiService.get('/home-content')
        this.content = {
          tips: Array.isArray(content?.tips) ? content.tips : [],
          whatsNew: Array.isArray(content?.whatsNew) ? content.whatsNew : []
        }
        return this.content
      } catch (error) {
        this.content = { tips: [], whatsNew: [] }
        this.error = normalizeApiError(error, 'Failed to load home content')
        throw this.error
      }
    }
  }
})
