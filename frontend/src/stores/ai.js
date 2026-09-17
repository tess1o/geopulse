import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'
import { normalizeApiError } from '@/utils/apiErrorDetail'

export const useAIStore = defineStore('ai', {
  state: () => ({
    settings: null,
    settingsLoading: false,
    error: null
  }),

  actions: {
    async fetchSettings() {
      this.settingsLoading = true
      this.error = null
      try {
        this.settings = await apiService.get('/ai/settings')
        return this.settings
      } catch (error) {
        this.error = normalizeApiError(error, 'Failed to load AI settings')
        throw this.error
      } finally {
        this.settingsLoading = false
      }
    },

    async saveSettings(settings) {
      this.error = null
      try {
        await apiService.post('/ai/settings', settings)
        return await this.fetchSettings()
      } catch (error) {
        this.error = normalizeApiError(error, 'Failed to save AI settings')
        throw this.error
      }
    },

    async testConnection(settings) {
      try {
        return await apiService.post('/ai/test-connection', settings)
      } catch (error) {
        this.error = normalizeApiError(error, 'Failed to connect to the AI provider')
        throw this.error
      }
    },

    async fetchDefaultSystemMessage() {
      try {
        return await apiService.get('/ai/default-system-message')
      } catch (error) {
        throw normalizeApiError(error, 'Failed to load the default AI system message')
      }
    },

    async fetchBuiltinSystemMessage() {
      try {
        return await apiService.get('/ai/builtin-system-message')
      } catch (error) {
        throw normalizeApiError(error, 'Failed to load the built-in AI system message')
      }
    },

    async chat(message) {
      try {
        return await apiService.post('/ai/chat', { message })
      } catch (error) {
        this.error = normalizeApiError(error, 'Failed to get an AI response')
        throw this.error
      }
    }
  }
})
