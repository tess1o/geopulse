import { defineStore } from 'pinia'
import apiService from '../utils/apiService'

export const useGeocodingStore = defineStore('geocoding', {
    state: () => ({
        geocodingResults: [],
        totalRecords: 0,
        enabledProviders: [],
        availableProviders: [],
        loading: false
    }),

    getters: {
        getGeocodingResults: (state) => state.geocodingResults,
        getTotalRecords: (state) => state.totalRecords,
        getEnabledProviders: (state) => state.enabledProviders,
        getAvailableProviders: (state) => state.availableProviders,
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

        setLoading(loading) {
            this.loading = loading
        },

        clearData() {
            this.geocodingResults = []
            this.totalRecords = 0
            this.availableProviders = []
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
        }
    }
})
