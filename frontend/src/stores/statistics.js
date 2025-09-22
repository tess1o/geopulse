import { defineStore } from 'pinia'
import apiService from '../utils/apiService'
import dayjs from 'dayjs';

export const useStatisticsStore = defineStore('statistics', {
    state: () => ({
        weeklyStatistics: null,
        monthlyStatistics: null,
        selectedRangeStatistics: null
    }),

    getters: {
        // Direct access getters (you can remove these if you prefer direct state access)
        getWeeklyStatistics: (state) => state.weeklyStatistics,
        getMonthlyStatistics: (state) => state.monthlyStatistics,
        getSelectedRangeStatistics: (state) => state.selectedRangeStatistics,

        // Computed getters for additional functionality
        hasWeeklyData: (state) => !!state.weeklyStatistics,
        hasMonthlyData: (state) => !!state.monthlyStatistics,
        hasSelectedRangeData: (state) => !!state.selectedRangeStatistics,

        // Example: Get total distance from any statistics
        getTotalDistance: (state) => (type) => {
            const stats = type === 'weekly' ? state.weeklyStatistics
                : type === 'monthly' ? state.monthlyStatistics
                    : state.selectedRangeStatistics
            return stats?.totalDistance || 0
        }
    },

    actions: {
        setWeeklyStatistics(stats) {
            this.weeklyStatistics = stats
        },

        setMonthlyStatistics(stats) {
            this.monthlyStatistics = stats
        },

        setSelectedRangeStatistics(stats) {
            this.selectedRangeStatistics = stats
        },

        clearAllStatistics() {
            this.weeklyStatistics = null
            this.monthlyStatistics = null
            this.selectedRangeStatistics = null
        },

        // API Actions
        async fetchWeeklyStatistics() {
            try {
                const response = await apiService.get(`/statistics/weekly`)
                this.setWeeklyStatistics(response)
                return response
            } catch (error) {
                throw error
            }
        },

        async fetchMonthlyStatistics() {
            try {
                const response = await apiService.get(`/statistics/monthly`)
                this.setMonthlyStatistics(response)
                return response
            } catch (error) {
                throw error
            }
        },

        async fetchSelectedRangeStatistics(startTime, endTime) {
            try {
                const response = await apiService.get(`/statistics`, {
                    startTime: startTime,
                    endTime: endTime
                })
                this.setSelectedRangeStatistics(response)
                return response
            } catch (error) {
                throw error
            }
        },

        // Convenience method to fetch all statistics
        async fetchAllStatistics() {
            try {
                await Promise.all([
                    this.fetchWeeklyStatistics(),
                    this.fetchMonthlyStatistics()
                ])
            } catch (error) {
                console.error('Error fetching all statistics:', error)
                throw error
            }
        }
    }
})