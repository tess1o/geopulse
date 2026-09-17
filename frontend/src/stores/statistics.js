import { defineStore } from 'pinia'
import apiService from '../utils/apiService'
import { normalizeApiError } from '@/utils/apiErrorDetail'

export const useStatisticsStore = defineStore('statistics', {
    state: () => ({
        weeklyStatistics: null,
        monthlyStatistics: null,
        selectedRangeStatistics: null,
        loading: false,
        error: null
    }),

    getters: {
        hasWeeklyData: (state) => !!state.weeklyStatistics,
        hasMonthlyData: (state) => !!state.monthlyStatistics,
        hasSelectedRangeData: (state) => !!state.selectedRangeStatistics
    },

    actions: {
        clearAllStatistics() {
            this.weeklyStatistics = null
            this.monthlyStatistics = null
            this.selectedRangeStatistics = null
        },

        // API Actions
        async fetchWeeklyStatistics() {
            this.error = null
            try {
                const response = await apiService.get(`/statistics/weekly`)
                this.weeklyStatistics = response
                return response
            } catch (error) {
                this.error = normalizeApiError(error, 'Failed to load weekly statistics')
                throw this.error
            }
        },

        async fetchMonthlyStatistics() {
            this.error = null
            try {
                const response = await apiService.get(`/statistics/monthly`)
                this.monthlyStatistics = response
                return response
            } catch (error) {
                this.error = normalizeApiError(error, 'Failed to load monthly statistics')
                throw this.error
            }
        },

        async fetchSelectedRangeStatistics(startTime, endTime) {
            this.error = null
            try {
                const response = await apiService.get(`/statistics`, {
                    startTime: startTime,
                    endTime: endTime
                })
                this.selectedRangeStatistics = response
                return response
            } catch (error) {
                this.error = normalizeApiError(error, 'Failed to load statistics')
                throw this.error
            }
        },

        // Convenience method to fetch all statistics
        async fetchAllStatistics() {
            this.loading = true
            try {
                await Promise.all([
                    this.fetchWeeklyStatistics(),
                    this.fetchMonthlyStatistics()
                ])
            } catch (error) {
                throw error
            } finally {
                this.loading = false
            }
        }
    }
})
