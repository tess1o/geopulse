import { defineStore } from 'pinia'
import apiService from '../utils/apiService'
import { normalizeApiError } from '@/utils/apiErrorDetail'

export const useDigestStore = defineStore('digest', {
    state: () => ({
        monthlyDigests: {}, // Cache by "YYYY-MM"
        yearlyDigests: {}, // Cache by "YYYY"
        currentDigest: null,
        loading: false,
        error: null,
        // Heatmap cache (separate from digest so it loads independently)
        heatmapData: {},       // Cache by "YYYY" or "YYYY-MM"
        heatmapLoading: false,
        heatmapError: null,
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

                const digestData = await apiService.get('/digest/monthly', {
                    year,
                    month
                })

                // Cache the result
                this.monthlyDigests[key] = digestData
                this.currentDigest = digestData

                return digestData
            } catch (error) {
                console.error('Error fetching monthly digest:', error)
                this.setError(normalizeApiError(error, 'Failed to fetch monthly digest'))
                throw this.error
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

                const digestData = await apiService.get('/digest/yearly', {
                    year
                })

                // Cache the result
                this.yearlyDigests[year] = digestData
                this.currentDigest = digestData

                return digestData
            } catch (error) {
                console.error('Error fetching yearly digest:', error)
                this.setError(normalizeApiError(error, 'Failed to fetch yearly digest'))
                throw this.error
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
        },

        // ---------- Heatmap ----------

        /**
         * Fetch heatmap data for the given period.
         * viewMode: 'monthly' | 'yearly'
         */
        async fetchHeatmapData(viewMode, year, month = null, layer = 'combined', options = {}) {
            const key = viewMode === 'monthly'
                ? `${year}-${String(month).padStart(2, '0')}-${layer}`
                : `${year}-${layer}`
            const silent = options?.silent === true

            // Return cached data immediately
            if (this.heatmapData[key]) {
                return this.heatmapData[key]
            }

            try {
                if (!silent) {
                    this.heatmapLoading = true
                    this.heatmapError = null
                }

                const params = viewMode === 'monthly'
                    ? { year, month, layer }
                    : { year, layer }

                const endpoint = viewMode === 'monthly'
                    ? '/digest/heatmap/monthly'
                    : '/digest/heatmap/yearly'

                const data = await apiService.get(endpoint, params)
                this.heatmapData[key] = data
                return data
            } catch (error) {
                console.error('Error fetching heatmap data:', error)
                if (!silent) {
                    this.heatmapError = normalizeApiError(error, 'Failed to fetch heatmap data')
                }
                return []
            } finally {
                if (!silent) {
                    this.heatmapLoading = false
                }
            }
        },

        invalidateHeatmap(viewMode, year, month = null, layer = 'combined') {
            const key = viewMode === 'monthly'
                ? `${year}-${String(month).padStart(2, '0')}-${layer}`
                : `${year}-${layer}`
            delete this.heatmapData[key]
        },

        /**
         * Fetch heatmap data for a custom date range.
         */
        async fetchHeatmapRangeData(startTime, endTime, layer = 'combined', options = {}) {
            if (!startTime || !endTime) return []

            const key = `range:${startTime}:${endTime}:${layer}`
            if (this.heatmapData[key]) {
                return this.heatmapData[key]
            }
            const silent = options?.silent === true

            try {
                if (!silent) {
                    this.heatmapLoading = true
                    this.heatmapError = null
                }

                const data = await apiService.get('/digest/heatmap/range', {
                    startTime,
                    endTime,
                    layer
                })
                this.heatmapData[key] = data
                return data
            } catch (error) {
                console.error('Error fetching heatmap range data:', error)
                if (!silent) {
                    this.heatmapError = normalizeApiError(error, 'Failed to fetch heatmap data')
                }
                return []
            } finally {
                if (!silent) {
                    this.heatmapLoading = false
                }
            }
        },

        async downloadPdf(params) {
            this.error = null
            try {
                return await apiService.download('/digest/pdf', params)
            } catch (error) {
                this.error = normalizeApiError(error, 'Failed to export digest PDF')
                throw this.error
            }
        }
    }
})
