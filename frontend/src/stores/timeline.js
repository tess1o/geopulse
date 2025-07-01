import { defineStore } from 'pinia'
import apiService from '../utils/apiService'

export const useTimelineStore = defineStore('timeline', {
    state: () => ({
        timelineData: null
    }),

    getters: {
        // Direct access getter
        getTimelineData: (state) => state.timelineData,

        // Computed getters for additional functionality
        hasTimelineData: (state) => {
            return state.timelineData && state.timelineData.length > 0
        },

        timelineItemsCount: (state) => {
            return state.timelineData?.length || 0
        },

        // Get timeline items by type
        getStays: (state) => {
            if (!state.timelineData) return []
            return state.timelineData.filter(item => item.type === 'stay')
        },

        getTrips: (state) => {
            if (!state.timelineData) return []
            return state.timelineData.filter(item => item.type === 'trip')
        },

        // Count by type
        staysCount: (state) => {
            if (!state.timelineData) return 0
            return state.timelineData.filter(item => item.type === 'stay').length
        },

        tripsCount: (state) => {
            if (!state.timelineData) return 0
            return state.timelineData.filter(item => item.type === 'trip').length
        },

        // Get timeline item by timestamp and coordinates (for click handling)
        getTimelineItem: (state) => (timestamp, latitude, longitude) => {
            if (!state.timelineData) return null
            return state.timelineData.find(item =>
                item.timestamp === timestamp &&
                item.latitude === latitude &&
                item.longitude === longitude
            )
        },

        // Get timeline items within a time range
        getItemsInTimeRange: (state) => (startTime, endTime) => {
            if (!state.timelineData) return []
            return state.timelineData.filter(item => {
                const itemTime = new Date(item.timestamp)
                return itemTime >= startTime && itemTime <= endTime
            })
        },

        // Get timeline bounds (earliest and latest timestamps)
        getTimelineBounds: (state) => {
            if (!state.timelineData || state.timelineData.length === 0) return null

            const timestamps = state.timelineData.map(item => new Date(item.timestamp))
            return {
                earliest: new Date(Math.min(...timestamps)),
                latest: new Date(Math.max(...timestamps))
            }
        },

        // Get geographic bounds of timeline items
        getGeographicBounds: (state) => {
            if (!state.timelineData || state.timelineData.length === 0) return null

            const items = state.timelineData.filter(item => item.latitude && item.longitude)
            if (items.length === 0) return null

            const lats = items.map(item => item.latitude)
            const lons = items.map(item => item.longitude)

            return {
                north: Math.max(...lats),
                south: Math.min(...lats),
                east: Math.max(...lons),
                west: Math.min(...lons)
            }
        }
    },

    actions: {
        // Set timeline data (replaces mutations)
        setTimelineData(data) {
            this.timelineData = data
        },

        // Clear timeline data
        clearTimelineData() {
            this.timelineData = null
        },

        // API Actions
        async fetchMovementTimeline(startTime, endTime) {
            try {
                const response = await apiService.get('/timeline', {
                    startTime: startTime.toISOString(),
                    endTime: endTime.toISOString()
                })

                const normalizedStays = response.data.stays.map(stay => ({
                    ...stay,
                    type: 'stay'
                }))

                const normalizedTrips = response.data.trips.map(trip => ({
                    ...trip,
                    type: 'trip'
                }))

                const results = [...normalizedStays, ...normalizedTrips].sort(
                    (a, b) => new Date(a.timestamp) - new Date(b.timestamp)
                )

                this.setTimelineData(results)
                return response
            } catch (error) {
                throw error
            }
        },

        // Convenience methods
        async refreshTimeline(startTime, endTime) {
            // Same as fetchMovementTimeline but clearer intent
            return this.fetchMovementTimeline(startTime, endTime)
        },

        // Find timeline item index (useful for component interactions)
        findTimelineItemIndex(timestamp, latitude, longitude) {
            if (!this.timelineData) return -1
            return this.timelineData.findIndex(item =>
                item.timestamp === timestamp &&
                item.latitude === latitude &&
                item.longitude === longitude
            )
        },

        // Utility methods for timeline analysis
        getTotalDistance() {
            if (!this.timelineData) return 0
            return this.timelineData
                .filter(item => item.type === 'trip' && item.distance)
                .reduce((total, trip) => total + (trip.distance || 0), 0)
        },

        getTotalDuration() {
            if (!this.timelineData) return 0
            return this.timelineData
                .filter(item => item.duration)
                .reduce((total, item) => total + (item.duration || 0), 0)
        },

        // Get timeline summary
        getTimelineSummary() {
            return {
                totalItems: this.timelineItemsCount,
                staysCount: this.staysCount,
                tripsCount: this.tripsCount,
                totalDistance: this.getTotalDistance(),
                totalDuration: this.getTotalDuration(),
                timeSpan: this.getTimelineBounds
            }
        },

        // Export timeline data
        exportTimelineData() {
            return {
                timelineData: this.timelineData,
                summary: this.getTimelineSummary(),
                exportDate: new Date().toISOString()
            }
        }
    }
})