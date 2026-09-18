import { defineStore } from 'pinia'
import apiService from '../utils/apiService'
import { normalizeApiError } from '@/utils/apiErrorDetail'

export const useTechnicalDataStore = defineStore('technicalData', {
    state: () => ({
        summaryStats: {
            totalPoints: 0,
            pointsToday: 0,
            firstPointDate: null,
            lastPointDate: null,
            filteredPoints: null  // Only populated when filters are applied
        },
        gpsPoints: [],
        totalRecords: 0,
        loading: false,
        error: null
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

        fail(error, fallback) {
            this.error = normalizeApiError(error, fallback)
            return this.error
        },

        clearData() {
            this.summaryStats = {
                totalPoints: 0,
                pointsToday: 0,
                firstPointDate: null,
                lastPointDate: null,
                filteredPoints: null
            }
            this.gpsPoints = []
            this.totalRecords = 0
        },


        // API Actions
        async fetchSummaryStats(params = {}) {
            try {
                this.setLoading(true)

                const summary = await apiService.get('/gps/points/summary', params)
                this.setSummaryStats(summary)
                return summary
            } catch (error) {
                throw this.fail(error, 'Failed to load GPS summary')
            } finally {
                this.setLoading(false)
            }
        },

        async fetchGPSPoints(params = {}) {
            try {
                this.setLoading(true)

                const response = await apiService.get('/gps/points', params)

                const gpsPoints = response.items || []
                const totalRecords = response.totalElements ?? this.summaryStats.totalPoints ?? 0

                this.setGpsPoints(gpsPoints, totalRecords)
                
                return {
                    items: gpsPoints,
                    page: response.page ?? params.page ?? 1,
                    size: response.size ?? params.limit ?? 50,
                    totalElements: totalRecords,
                    totalPages: response.totalPages ?? Math.ceil(totalRecords / (params.limit || 50))
                }
            } catch (error) {
                throw this.fail(error, 'Failed to load GPS points')
            } finally {
                this.setLoading(false)
            }
        },

        async exportGPSPoints(params = {}, selectedIds = null) {
            try {
                // If selectedIds are provided, add them to params
                if (selectedIds && selectedIds.length > 0) {
                    params = { ...params, ids: selectedIds.join(',') }
                }
                await apiService.download('/gps/points/exports', params)
                return true
            } catch (error) {
                throw this.fail(error, 'Failed to export GPS points')
            }
        },

        async updateGpsPoint(pointId, data) {
            try {
                return await apiService.put(`/gps/points/${pointId}`, data)
            } catch (error) {
                throw this.fail(error, 'Failed to update GPS point')
            }
        },

        async deleteGpsPoint(pointId) {
            try {
                return await apiService.delete(`/gps/points/${pointId}`)
            } catch (error) {
                throw this.fail(error, 'Failed to delete GPS point')
            }
        },

        async deleteGpsPoints(pointIds) {
            try {
                return await apiService.post('/gps/points/bulk', {
                    gpsPointIds: pointIds
                })
            } catch (error) {
                throw this.fail(error, 'Failed to delete GPS points')
            }
        },

        async deleteAllGpsData() {
            try {
                await apiService.delete('/gps/points')
                this.clearData()
                return true
            } catch (error) {
                throw this.fail(error, 'Failed to delete all GPS data')
            }
        },

        async fetchRawMapPoints(params) {
            try {
                return await apiService.get('/gps/points/map', params)
            } catch (error) {
                throw this.fail(error, 'Failed to load raw GPS points')
            }
        },

        async resolveRawPointLocation(pointId) {
            try {
                return await apiService.get(`/gps/points/${pointId}/location`)
            } catch (error) {
                throw this.fail(error, 'Failed to resolve GPS point location')
            }
        }
    }
})
