import { defineStore } from 'pinia'
import apiService from '../utils/apiService'

export const useDigestStore = defineStore('digest', {
    state: () => ({
        monthlyDigests: {}, // Cache by "YYYY-MM"
        yearlyDigests: {}, // Cache by "YYYY"
        currentDigest: null,
        loading: false,
        error: null
    }),

    getters: {
        getDigest: (state) => state.currentDigest,
        isLoading: (state) => state.loading,
        hasError: (state) => !!state.error,
        getError: (state) => state.error,

        // Get cached digest
        getCachedMonthlyDigest: (state) => (year, month) => {
            const key = `${year}-${String(month).padStart(2, '0')}`
            return state.monthlyDigests[key]
        },

        getCachedYearlyDigest: (state) => (year) => {
            return state.yearlyDigests[year]
        }
    },

    actions: {
        setCurrentDigest(digest) {
            this.currentDigest = digest
        },

        setLoading(loading) {
            this.loading = loading
        },

        setError(error) {
            this.error = error
        },

        clearError() {
            this.error = null
        },

        clearCurrentDigest() {
            this.currentDigest = null
        },

        // Fetch monthly digest
        async fetchMonthlyDigest(year, month) {
            const key = `${year}-${String(month).padStart(2, '0')}`

            // Check cache first
            if (this.monthlyDigests[key]) {
                this.currentDigest = this.monthlyDigests[key]
                return this.monthlyDigests[key]
            }

            try {
                this.setLoading(true)
                this.clearError()

                const response = await apiService.get('/digest/monthly', {
                    year,
                    month
                })

                // Extract data from ApiResponse wrapper
                const digestData = response.data || response

                // Cache the result
                this.monthlyDigests[key] = digestData
                this.currentDigest = digestData

                return digestData
            } catch (error) {
                console.error('Error fetching monthly digest:', error)
                this.setError(error.message || 'Failed to fetch monthly digest')
                throw error
            } finally {
                this.setLoading(false)
            }
        },

        // Fetch yearly digest
        async fetchYearlyDigest(year) {
            // Check cache first
            if (this.yearlyDigests[year]) {
                this.currentDigest = this.yearlyDigests[year]
                return this.yearlyDigests[year]
            }

            try {
                this.setLoading(true)
                this.clearError()

                const response = await apiService.get('/digest/yearly', {
                    year
                })

                // Extract data from ApiResponse wrapper
                const digestData = response.data || response

                // Cache the result
                this.yearlyDigests[year] = digestData
                this.currentDigest = digestData

                return digestData
            } catch (error) {
                console.error('Error fetching yearly digest:', error)
                this.setError(error.message || 'Failed to fetch yearly digest')
                throw error
            } finally {
                this.setLoading(false)
            }
        },

        // Clear all caches
        clearCache() {
            this.monthlyDigests = {}
            this.yearlyDigests = {}
            this.currentDigest = null
        },

        // Invalidate specific cache entry
        invalidateMonthlyDigest(year, month) {
            const key = `${year}-${String(month).padStart(2, '0')}`
            delete this.monthlyDigests[key]
        },

        invalidateYearlyDigest(year) {
            delete this.yearlyDigests[year]
        }
    }
})
