import { defineStore } from 'pinia'
import apiService from '../utils/apiService'

export const useLocationStore = defineStore('location', {
    state: () => ({
        locationPath: null
    }),

    getters: {
        // Direct access getter
        getLocationPath: (state) => state.locationPath,

        // Computed getters for additional functionality
        hasLocationPath: (state) => !!state.locationPath,
        hasLocationData: (state) => {
            return state.locationPath &&
                state.locationPath.points &&
                state.locationPath.points.length > 0
        },

        // Get location points
        getLocationPoints: (state) => {
            return state.locationPath?.points || []
        },

        // Get location path metadata
        getPathMetadata: (state) => {
            if (!state.locationPath) return null
            return {
                totalPoints: state.locationPath.points?.length || 0,
                startTime: state.locationPath.startTime,
                endTime: state.locationPath.endTime,
                // Add any other metadata from your API response
            }
        },

        // Get location bounds (useful for map centering)
        getLocationBounds: (state) => {
            const points = state.locationPath?.points || []
            if (points.length === 0) return null

            const lats = points.map(p => p.lat || p.latitude).filter(Boolean)
            const lons = points.map(p => p.lon || p.longitude).filter(Boolean)

            if (lats.length === 0 || lons.length === 0) return null

            return {
                north: Math.max(...lats),
                south: Math.min(...lats),
                east: Math.max(...lons),
                west: Math.min(...lons)
            }
        },

        // Get points within a time range
        getPointsInTimeRange: (state) => (startTime, endTime) => {
            const points = state.locationPath?.points || []
            return points.filter(point => {
                const pointTime = new Date(point.timestamp)
                return pointTime >= startTime && pointTime <= endTime
            })
        }
    },

    actions: {
        // Set location path data (replaces mutations)
        setLocationPath(path) {
            this.locationPath = path
        },

        // API Actions
        async fetchLocationPath(startTime, endTime) {
            try {
                const response = await apiService.get('/gps/path', {
                    startTime: startTime.toISOString(),
                    endTime: endTime.toISOString()
                })

                this.setLocationPath(response.data)
                return response
            } catch (error) {
                throw error
            }
        },

        // Convenience methods
        async refreshLocationPath(startTime, endTime) {
            // Same as fetchLocationPath but clearer intent
            return this.fetchLocationPath(startTime, endTime)
        },

        // Utility methods for common operations
        getLastKnownPosition() {
            const points = this.locationPath?.points || []
            if (points.length === 0) return null

            // Assuming points are sorted by timestamp
            const lastPoint = points[points.length - 1]
            return {
                lat: lastPoint.lat || lastPoint.latitude,
                lon: lastPoint.lon || lastPoint.longitude,
                timestamp: lastPoint.timestamp
            }
        },
    }
})