import { defineStore } from 'pinia'
import apiService from '../utils/apiService'

export const useTechnicalDataStore = defineStore('technicalData', {
    state: () => ({
        summaryStats: {
            totalPoints: 0,
            pointsToday: 0,
            firstPointDate: null,
            lastPointDate: null
        },
        gpsPoints: [],
        totalRecords: 0,
        loading: false
    }),

    getters: {
        getSummaryStats: (state) => state.summaryStats,
        getGpsPoints: (state) => state.gpsPoints,
        getTotalRecords: (state) => state.totalRecords,
        isLoading: (state) => state.loading,
        hasData: (state) => state.summaryStats.totalPoints > 0
    },

    actions: {
        setSummaryStats(stats) {
            this.summaryStats = stats
        },

        setGpsPoints(points, totalRecords) {
            this.gpsPoints = points
            this.totalRecords = totalRecords
        },

        setLoading(loading) {
            this.loading = loading
        },

        clearData() {
            this.summaryStats = {
                totalPoints: 0,
                pointsToday: 0,
                firstPointDate: null,
                lastPointDate: null
            }
            this.gpsPoints = []
            this.totalRecords = 0
        },


        // API Actions
        async fetchSummaryStats() {
            try {
                this.setLoading(true)
                
                const response = await apiService.get('/gps/summary')
                // Extract data from wrapper response
                const summaryData = response.data || response
                this.setSummaryStats(summaryData)
                
                return summaryData
            } catch (error) {
                console.error('Error fetching summary stats:', error)
                throw error
            } finally {
                this.setLoading(false)
            }
        },

        async fetchGPSPoints(params = {}) {
            try {
                this.setLoading(true)
                
                const response = await apiService.get('/gps', params)
                
                // Extract data from wrapper response
                const responseData = response.data || response
                const gpsPoints = responseData.data || []
                
                // If pagination is not provided, use summary total as fallback
                const totalRecords = responseData.pagination?.total || this.summaryStats.totalPoints || 0
                
                this.setGpsPoints(gpsPoints, totalRecords)
                
                return {
                    data: gpsPoints,
                    pagination: responseData.pagination || {
                        page: params.page || 1,
                        limit: params.limit || 50,
                        total: totalRecords,
                        totalPages: Math.ceil(totalRecords / (params.limit || 50))
                    }
                }
            } catch (error) {
                console.error('Error fetching GPS points:', error)
                throw error
            } finally {
                this.setLoading(false)
            }
        },

        async exportGPSPoints(params = {}) {
            try {
                await apiService.download('/gps/export', params)
                return true
            } catch (error) {
                console.error('Error exporting GPS points:', error)
                throw error
            }
        },

        async updateGpsPoint(pointId, data) {
            try {
                const response = await apiService.put(`/gps/${pointId}`, data)
                const responseData = response.data || response
                return responseData
            } catch (error) {
                console.error('Error updating GPS point:', error)
                throw error
            }
        },

        async deleteGpsPoint(pointId) {
            try {
                await apiService.delete(`/gps/${pointId}`)
                return true
            } catch (error) {
                console.error('Error deleting GPS point:', error)
                throw error
            }
        },

        async deleteGpsPoints(pointIds) {
            try {
                const response = await apiService.post('/gps/bulk', {
                    gpsPointIds: pointIds
                })
                const responseData = response.data || response
                return responseData
            } catch (error) {
                console.error('Error deleting GPS points:', error)
                throw error
            }
        }
    }
})