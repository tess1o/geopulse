import { defineStore } from 'pinia'
import apiService from '../utils/apiService'

export const useGeocodingStore = defineStore('geocoding', {
    state: () => ({
        geocodingResults: [],
        totalRecords: 0,
        enabledProviders: [],
        availableProviders: [],
        normalizationRules: [],
        loading: false
    }),

    getters: {
        getGeocodingResults: (state) => state.geocodingResults,
        getTotalRecords: (state) => state.totalRecords,
        getEnabledProviders: (state) => state.enabledProviders,
        getAvailableProviders: (state) => state.availableProviders,
        getNormalizationRules: (state) => state.normalizationRules,
        isLoading: (state) => state.loading,
        hasData: (state) => state.geocodingResults.length > 0
    },

    actions: {
        setGeocodingResults(results, totalRecords) {
            this.geocodingResults = results
            this.totalRecords = totalRecords
        },

        setEnabledProviders(providers) {
            this.enabledProviders = providers
        },

        setAvailableProviders(providers) {
            this.availableProviders = providers
        },

        setNormalizationRules(rules) {
            this.normalizationRules = rules || []
        },

        setLoading(loading) {
            this.loading = loading
        },

        clearData() {
            this.geocodingResults = []
            this.totalRecords = 0
            this.availableProviders = []
            this.normalizationRules = []
        },

        // API Actions
        async fetchGeocodingResults(params = {}) {
            try {
                this.setLoading(true)

                const response = await apiService.get('/geocoding', params)

                // Response is {data: [...], pagination: {...}}
                const results = response.data || []
                const totalRecords = response.pagination?.total || 0

                this.setGeocodingResults(results, totalRecords)

                return {
                    data: results,
                    pagination: response.pagination || {
                        page: params.page || 1,
                        limit: params.limit || 50,
                        total: totalRecords,
                        totalPages: Math.ceil(totalRecords / (params.limit || 50))
                    }
                }
            } catch (error) {
                console.error('Error fetching geocoding results:', error)
                throw error
            } finally {
                this.setLoading(false)
            }
        },

        async fetchEnabledProviders() {
            try {
                const response = await apiService.get('/geocoding/providers')
                // Response is the array of providers directly
                this.setEnabledProviders(response)

                return response
            } catch (error) {
                console.error('Error fetching enabled providers:', error)
                throw error
            }
        },

        async fetchAvailableProviders() {
            try {
                const response = await apiService.get('/geocoding/providers/available')
                // Response is the array of provider names directly
                this.setAvailableProviders(response)

                return response
            } catch (error) {
                console.error('Error fetching available providers:', error)
                throw error
            }
        },

        async getGeocodingResult(id) {
            try {
                const response = await apiService.get(`/geocoding/${id}`)
                return response
            } catch (error) {
                console.error('Error fetching geocoding result:', error)
                throw error
            }
        },

        async updateGeocodingResult(id, data) {
            try {
                const response = await apiService.put(`/geocoding/${id}`, data)
                return response
            } catch (error) {
                console.error('Error updating geocoding result:', error)
                throw error
            }
        },

        async reconcileWithProvider(request) {
            try {
                const response = await apiService.post('/geocoding/reconcile', request)
                return response
            } catch (error) {
                console.error('Error reconciling geocoding results:', error)
                throw error
            }
        },

        async startBulkReconciliation(request) {
            try {
                const response = await apiService.post('/geocoding/reconcile/bulk', request)
                return response // { jobId: "uuid" }
            } catch (error) {
                console.error('Error starting bulk reconciliation:', error)
                throw error
            }
        },

        async getReconciliationJobProgress(jobId) {
            try {
                const response = await apiService.get(`/geocoding/reconcile/jobs/${jobId}`)
                return response
            } catch (error) {
                console.error('Error fetching reconciliation job progress:', error)
                throw error
            }
        },

        async bulkUpdateGeocoding(geocodingIds, updateCity, city, updateCountry, country) {
            try {
                const response = await apiService.put('/geocoding/bulk-update', {
                    geocodingIds,
                    updateCity,
                    city,
                    updateCountry,
                    country
                })
                return response // { totalRequested, successCount, failedCount, failures }
            } catch (error) {
                console.error('Error bulk updating geocoding results:', error)
                throw error
            }
        },

        async fetchDistinctValues() {
            try {
                const response = await apiService.get('/geocoding/distinct-values')
                return response // { cities: [...], countries: [...] }
            } catch (error) {
                console.error('Error fetching distinct values:', error)
                throw error
            }
        },

        async fetchNormalizationRules() {
            try {
                const response = await apiService.get('/geocoding/normalization-rules')
                this.setNormalizationRules(response || [])
                return response || []
            } catch (error) {
                console.error('Error fetching normalization rules:', error)
                throw error
            }
        },

        async createNormalizationRule(payload) {
            try {
                const response = await apiService.post('/geocoding/normalization-rules', payload)
                return response
            } catch (error) {
                console.error('Error creating normalization rule:', error)
                throw error
            }
        },

        async updateNormalizationRule(ruleId, payload) {
            try {
                const response = await apiService.put(`/geocoding/normalization-rules/${ruleId}`, payload)
                return response
            } catch (error) {
                console.error('Error updating normalization rule:', error)
                throw error
            }
        },

        async deleteNormalizationRule(ruleId) {
            try {
                await apiService.delete(`/geocoding/normalization-rules/${ruleId}`, {})
            } catch (error) {
                console.error('Error deleting normalization rule:', error)
                throw error
            }
        },

        async applyNormalizationRules(payload) {
            try {
                const response = await apiService.post('/geocoding/normalization-rules/apply', payload)
                return response // { jobId }
            } catch (error) {
                console.error('Error applying normalization rules:', error)
                throw error
            }
        },

        async applySingleNormalizationRule(ruleId, payload) {
            try {
                const response = await apiService.post(`/geocoding/normalization-rules/${ruleId}/apply`, payload)
                return response // { jobId }
            } catch (error) {
                console.error('Error applying single normalization rule:', error)
                throw error
            }
        }
    }
})
