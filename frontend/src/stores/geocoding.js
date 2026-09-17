import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'
import { normalizeApiError } from '@/utils/apiErrorDetail'

const fail = (store, error, fallback) => {
  store.error = normalizeApiError(error, fallback)
  throw store.error
}

export const useGeocodingStore = defineStore('geocoding', {
  state: () => ({
    geocodingResults: [],
    totalRecords: 0,
    enabledProviders: [],
    availableProviders: [],
    normalizationRules: [],
    loading: false,
    error: null
  }),

  getters: {
    hasData: (state) => state.geocodingResults.length > 0
  },

  actions: {
    clearData() {
      this.geocodingResults = []
      this.totalRecords = 0
      this.availableProviders = []
      this.normalizationRules = []
    },

    async fetchGeocodingResults(params = {}) {
      this.loading = true
      this.error = null
      try {
        const page = await apiService.get('/geocoding', params)
        this.geocodingResults = Array.isArray(page?.items) ? page.items : []
        this.totalRecords = page?.totalElements || 0
        return page
      } catch (error) {
        fail(this, error, 'Failed to load geocoding results')
      } finally {
        this.loading = false
      }
    },

    async fetchEnabledProviders() {
      try {
        this.enabledProviders = await apiService.get('/geocoding/providers')
        return this.enabledProviders
      } catch (error) {
        fail(this, error, 'Failed to load geocoding providers')
      }
    },

    async fetchAvailableProviders() {
      try {
        this.availableProviders = await apiService.get('/geocoding/providers/available')
        return this.availableProviders
      } catch (error) {
        fail(this, error, 'Failed to load available geocoding providers')
      }
    },

    async getGeocodingResult(id) {
      try {
        return await apiService.get(`/geocoding/${id}`)
      } catch (error) {
        fail(this, error, 'Failed to load geocoding result')
      }
    },

    async updateGeocodingResult(id, data) {
      try {
        return await apiService.put(`/geocoding/${id}`, data)
      } catch (error) {
        fail(this, error, 'Failed to update geocoding result')
      }
    },

    async startBulkReconciliation(request) {
      try {
        return await apiService.post('/geocoding/reconcile/bulk', request)
      } catch (error) {
        fail(this, error, 'Failed to start reconciliation')
      }
    },

    async getReconciliationJobProgress(jobId) {
      try {
        return await apiService.get(`/geocoding/reconcile/jobs/${jobId}`)
      } catch (error) {
        fail(this, error, 'Failed to load reconciliation progress')
      }
    },

    async bulkUpdateGeocoding(geocodingIds, updateCity, city, updateCountry, country) {
      try {
        return await apiService.put('/geocoding/bulk-update', {
          geocodingIds,
          updateCity,
          city,
          updateCountry,
          country
        })
      } catch (error) {
        fail(this, error, 'Failed to update geocoding results')
      }
    },

    async fetchDistinctValues() {
      try {
        return await apiService.get('/geocoding/distinct-values')
      } catch (error) {
        fail(this, error, 'Failed to load distinct location values')
      }
    },

    async fetchNormalizationRules() {
      try {
        const rules = await apiService.get('/geocoding/normalization-rules')
        this.normalizationRules = Array.isArray(rules) ? rules : []
        return this.normalizationRules
      } catch (error) {
        fail(this, error, 'Failed to load normalization rules')
      }
    },

    async createNormalizationRule(payload) {
      try {
        return await apiService.post('/geocoding/normalization-rules', payload)
      } catch (error) {
        fail(this, error, 'Failed to create normalization rule')
      }
    },

    async updateNormalizationRule(ruleId, payload) {
      try {
        return await apiService.put(`/geocoding/normalization-rules/${ruleId}`, payload)
      } catch (error) {
        fail(this, error, 'Failed to update normalization rule')
      }
    },

    async deleteNormalizationRule(ruleId) {
      try {
        await apiService.delete(`/geocoding/normalization-rules/${ruleId}`)
      } catch (error) {
        fail(this, error, 'Failed to delete normalization rule')
      }
    },

    async applyNormalizationRules(payload) {
      try {
        return await apiService.post('/geocoding/normalization-rules/apply', payload)
      } catch (error) {
        fail(this, error, 'Failed to apply normalization rules')
      }
    },

    async applySingleNormalizationRule(ruleId, payload) {
      try {
        return await apiService.post(`/geocoding/normalization-rules/${ruleId}/apply`, payload)
      } catch (error) {
        fail(this, error, 'Failed to apply normalization rule')
      }
    }
  }
})
