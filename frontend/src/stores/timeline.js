import { defineStore } from 'pinia'
import apiService from '../utils/apiService'
import { useTimezone } from '@/composables/useTimezone'

const timezone = useTimezone()

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

        getDataGaps: (state) => {
            if (!state.timelineData) return []
            return state.timelineData.filter(item => item.type === 'dataGap')
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

        dataGapsCount: (state) => {
            if (!state.timelineData) return 0
            return state.timelineData.filter(item => item.type === 'dataGap').length
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
            const start = timezone.fromUtc(startTime);
            const end = timezone.fromUtc(endTime);
            return state.timelineData.filter(item => {
                const itemTime = timezone.fromUtc(item.timestamp)
                return timezone.isAfter(itemTime, start) && timezone.isBefore(itemTime, end)
            })
        },

        // Get timeline bounds (earliest and latest timestamps)
        getTimelineBounds: (state) => {
            if (!state.timelineData || state.timelineData.length === 0) return null

            const timestamps = state.timelineData.map(item => timezone.fromUtc(item.timestamp))
            return {
                earliest: timezone.min(timestamps).toISOString(),
                latest: timezone.max(timestamps).toISOString()
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
                const response = await apiService.get('/streaming-timeline', {
                    startTime: startTime,
                    endTime: endTime
                })

                const normalizedStays = response.data.stays.map(stay => ({
                    ...stay,
                    type: 'stay'
                }))

                const normalizedTrips = response.data.trips.map(trip => ({
                    ...trip,
                    type: 'trip'
                }))

                const normalizedDataGaps = (response.data.dataGaps || []).map(dataGap => ({
                    ...dataGap,
                    type: 'dataGap',
                    timestamp: dataGap.startTime
                }))

                const results = [...normalizedStays, ...normalizedTrips, ...normalizedDataGaps].sort(
                    (a, b) => timezone.fromUtc(a.timestamp).valueOf() - timezone.fromUtc(b.timestamp).valueOf()
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

        // Regenerate entire timeline from scratch (returns jobId for progress tracking)
        async regenerateAllTimeline() {
            try {
                const response = await apiService.post('/streaming-timeline/regenerate-all')
                // Clear current timeline data since it's been regenerated
                this.clearTimelineData()
                // Return the job ID from the response
                return response.data.jobId
            } catch (error) {
                throw error
            }
        },

        // Get job progress by job ID
        async getJobProgress(jobId) {
            try {
                const response = await apiService.get(`/streaming-timeline/jobs/${jobId}`)
                return response.data
            } catch (error) {
                throw error
            }
        },

        // Get active job for current user (if any)
        async getUserActiveJob() {
            try {
                const response = await apiService.get('/streaming-timeline/jobs/active')
                return response.data
            } catch (error) {
                throw error
            }
        },

        // Get historical jobs for current user
        async getUserHistoryJobs() {
            try {
                const response = await apiService.get('/streaming-timeline/jobs/history')
                return response.data
            } catch (error) {
                throw error
            }
        },

        async updateTripMovementType(tripId, movementType) {
            try {
                const response = await apiService.put(`/streaming-timeline/trips/${tripId}/movement-type`, {
                    movementType
                })
                const updatedTrip = response?.data
                if (updatedTrip) {
                    this.applyTripMovementUpdate(updatedTrip)
                }
                return updatedTrip
            } catch (error) {
                throw error
            }
        },

        async resetTripMovementType(tripId) {
            try {
                const response = await apiService.delete(`/streaming-timeline/trips/${tripId}/movement-type`)
                const updatedTrip = response?.data
                if (updatedTrip) {
                    this.applyTripMovementUpdate(updatedTrip)
                }
                return updatedTrip
            } catch (error) {
                throw error
            }
        },

        async getDataGapStayConversionPreview(gapId) {
            try {
                const response = await apiService.get(`/streaming-timeline/data-gaps/${gapId}/stay-conversion-preview`)
                return response?.data
            } catch (error) {
                throw error
            }
        },

        async convertDataGapToStay(gapId, payload = {}) {
            try {
                const response = await apiService.put(`/streaming-timeline/data-gaps/${gapId}/stay-conversion`, payload)
                return response?.data
            } catch (error) {
                throw error
            }
        },

        async resetDataGapStayOverride(overrideId) {
            try {
                const response = await apiService.delete(`/streaming-timeline/data-gap-overrides/${overrideId}/stay-conversion`)
                return response?.data
            } catch (error) {
                throw error
            }
        },

        applyTripMovementUpdate(updatedTrip) {
            if (!this.timelineData || !updatedTrip?.tripId) return
            const index = this.timelineData.findIndex(
                (item) => item.type === 'trip' && item.id === updatedTrip.tripId
            )
            if (index === -1) return

            this.timelineData[index] = {
                ...this.timelineData[index],
                movementType: updatedTrip.movementType,
                movementTypeSource: updatedTrip.movementTypeSource
            }
        },

        applyStayFavoriteUpdate(updatedFavorite) {
            if (!this.timelineData || !updatedFavorite?.id) return

            this.timelineData = this.timelineData.map((item) => {
                if (item.type !== 'stay' || item.favoriteId !== updatedFavorite.id) {
                    return item
                }

                return {
                    ...item,
                    locationName: updatedFavorite.name ?? item.locationName,
                    city: updatedFavorite.city ?? null,
                    country: updatedFavorite.country ?? null
                }
            })
        },

        applyStayGeocodingUpdate(oldGeocodingId, updatedGeocoding) {
            if (!this.timelineData || !oldGeocodingId || !updatedGeocoding?.id) return

            this.timelineData = this.timelineData.map((item) => {
                if (item.type !== 'stay' || item.geocodingId !== oldGeocodingId) {
                    return item
                }

                return {
                    ...item,
                    geocodingId: updatedGeocoding.id,
                    locationName: updatedGeocoding.displayName ?? item.locationName,
                    city: updatedGeocoding.city ?? null,
                    country: updatedGeocoding.country ?? null
                }
            })
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
                exportDate: timezone.now().toISOString()
            }
        }
    }
})
