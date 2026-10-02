import { defineStore } from 'pinia'
import apiService from '../utils/apiService'
import { useTimezone } from '@/composables/useTimezone'
import { normalizeApiError } from '@/utils/apiErrorDetail'
import {
    applyStayFavoriteUpdateToTimelineItems,
    applyStayGeocodingUpdateToTimelineItems,
    applyTripMovementUpdateToTimelineItems
} from '@/utils/timelineItemPatchers'

const timezone = useTimezone()

export const useTimelineStore = defineStore('timeline', {
    state: () => ({
        timelineData: null,
        weatherSamples: [],
        weatherStatus: null,
        weatherLoading: false,
        weatherError: null,
        error: null
    }),

    getters: {
        // Direct access getter
        getTimelineData: (state) => state.timelineData,
        getWeatherSamples: (state) => state.weatherSamples || [],

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

        clearWeatherSamples() {
            this.weatherSamples = []
        },

        // API Actions
        async fetchMovementTimeline(startTime, endTime) {
            this.error = null
            try {
                const timeline = await apiService.get('/timeline', {
                    from: startTime,
                    to: endTime
                })

                const normalizedStays = timeline.stays.map(stay => ({
                    ...stay,
                    type: 'stay'
                }))

                const normalizedTrips = timeline.trips.map(trip => ({
                    ...trip,
                    type: 'trip'
                }))

                const normalizedDataGaps = (timeline.dataGaps || []).map(dataGap => ({
                    ...dataGap,
                    type: 'dataGap',
                    timestamp: dataGap.startTime
                }))

                const results = [...normalizedStays, ...normalizedTrips, ...normalizedDataGaps].sort(
                    (a, b) => timezone.fromUtc(a.timestamp).valueOf() - timezone.fromUtc(b.timestamp).valueOf()
                )

                this.setTimelineData(results)
                return timeline
            } catch (error) {
                this.error = normalizeApiError(error, 'Failed to load timeline')
                throw this.error
            }
        },

        async fetchWeatherSamples(startTime, endTime, bounds = {}) {
            this.weatherLoading = true
            this.weatherError = null
            try {
                const payload = await apiService.get('/weather/samples', {
                    from: startTime,
                    to: endTime,
                    minLat: bounds.minLat,
                    minLon: bounds.minLon,
                    maxLat: bounds.maxLat,
                    maxLon: bounds.maxLon
                })
                this.weatherSamples = Array.isArray(payload.samples) ? payload.samples : []
                this.weatherStatus = {
                    enabled: Boolean(payload.enabled),
                    configured: Boolean(payload.configured),
                    provider: payload.provider,
                    attributionUrl: payload.attributionUrl,
                    units: payload.units || {}
                }
                return payload
            } catch (error) {
                this.weatherSamples = []
                this.weatherError = normalizeApiError(error, 'Failed to load weather samples')
                throw this.weatherError
            } finally {
                this.weatherLoading = false
            }
        },

        async fetchWeatherStatus() {
            try {
                this.weatherStatus = await apiService.get('/weather/status')
                return this.weatherStatus
            } catch (error) {
                this.weatherError = normalizeApiError(error, 'Failed to load weather status')
                throw this.weatherError
            }
        },

        // Convenience methods
        async refreshTimeline(startTime, endTime) {
            // Same as fetchMovementTimeline but clearer intent
            return this.fetchMovementTimeline(startTime, endTime)
        },

        // Regenerate entire timeline from scratch (returns jobId for progress tracking)
        async regenerateAllTimeline() {
            this.error = null
            try {
                const result = await apiService.post('/timeline/jobs')
                this.clearTimelineData()
                return result.jobId
            } catch (error) {
                this.error = normalizeApiError(error, 'Failed to start timeline regeneration')
                throw this.error
            }
        },

        // Get job progress by job ID
        async getJobProgress(jobId) {
            try {
                return await apiService.get(`/timeline/jobs/${jobId}`)
            } catch (error) {
                this.error = normalizeApiError(error, 'Failed to load timeline job')
                throw this.error
            }
        },

        // Get active job for current user (if any)
        async getUserActiveJob() {
            try {
                return await apiService.get('/timeline/jobs/current')
            } catch (error) {
                this.error = normalizeApiError(error, 'Failed to check active timeline jobs')
                throw this.error
            }
        },

        // Get historical jobs for current user
        async getUserHistoryJobs() {
            try {
                return await apiService.get('/timeline/jobs/history')
            } catch (error) {
                this.error = normalizeApiError(error, 'Failed to load timeline job history')
                throw this.error
            }
        },

        async updateTripMovementType(tripId, movementType) {
            try {
                const updatedTrip = await apiService.put(`/trips/${tripId}/timeline/movement-type`, {
                    movementType
                })
                if (updatedTrip) {
                    this.applyTripMovementUpdate(updatedTrip)
                }
                return updatedTrip
            } catch (error) {
                this.error = normalizeApiError(error, 'Failed to update movement type')
                throw this.error
            }
        },

        async resetTripMovementType(tripId) {
            try {
                const updatedTrip = await apiService.delete(`/trips/${tripId}/timeline/movement-type`)
                if (updatedTrip) {
                    this.applyTripMovementUpdate(updatedTrip)
                }
                return updatedTrip
            } catch (error) {
                this.error = normalizeApiError(error, 'Failed to reset movement type')
                throw this.error
            }
        },

        async getDataGapStayConversionPreview(gapId) {
            try {
                return await apiService.get(`/timeline/data-gaps/${gapId}/stay-conversion-preview`)
            } catch (error) {
                this.error = normalizeApiError(error, 'Failed to preview data gap conversion')
                throw this.error
            }
        },

        async convertDataGapToStay(gapId, payload = {}) {
            try {
                return await apiService.put(`/timeline/data-gaps/${gapId}/stay-conversion`, payload)
            } catch (error) {
                this.error = normalizeApiError(error, 'Failed to convert data gap')
                throw this.error
            }
        },

        async resetDataGapStayOverride(overrideId) {
            try {
                return await apiService.delete(`/timeline/data-gap-overrides/${overrideId}/stay-conversion`)
            } catch (error) {
                this.error = normalizeApiError(error, 'Failed to reset data gap override')
                throw this.error
            }
        },

        async fetchTripPath(startTime, endTime, options = {}) {
            try {
                const params = { from: startTime, to: endTime }
                if (options.simplify === false) params.simplify = false
                return await apiService.get('/gps/points/path', params)
            } catch (error) {
                this.error = normalizeApiError(error, 'Failed to load trip path')
                throw this.error
            }
        },

        async previewTripStaySplit(tripId, payload) {
            try {
                return await apiService.post(`/trips/${tripId}/timeline/stay-split/preview`, payload)
            } catch (error) {
                this.error = normalizeApiError(error, 'Failed to preview trip split')
                throw this.error
            }
        },

        async splitTripWithStay(tripId, payload) {
            try {
                return await apiService.put(`/trips/${tripId}/timeline/stay-split`, payload)
            } catch (error) {
                this.error = normalizeApiError(error, 'Failed to split trip')
                throw this.error
            }
        },

        async resetTripStaySplitOverride(overrideId) {
            try {
                return await apiService.delete(`/timeline/stay-split-overrides/${overrideId}`)
            } catch (error) {
                this.error = normalizeApiError(error, 'Failed to reset trip split')
                throw this.error
            }
        },

        async fetchTimelineCount(startTime, endTime) {
            try {
                return await apiService.get('/timeline/count', { from: startTime, to: endTime })
            } catch (error) {
                this.error = normalizeApiError(error, 'Failed to load timeline counts')
                throw this.error
            }
        },

        async fetchTripClassification(tripId) {
            try {
                return await apiService.get(`/trips/${tripId}/timeline/classification`)
            } catch (error) {
                this.error = normalizeApiError(error, 'Failed to load classification details')
                throw this.error
            }
        },

        async lookupLocation(latitude, longitude) {
            try {
                return await apiService.get('/timeline/location-lookup', { latitude, longitude })
            } catch (error) {
                this.error = normalizeApiError(error, 'Could not check visits at this location')
                throw this.error
            }
        },

        applyTripMovementUpdate(updatedTrip) {
            this.timelineData = applyTripMovementUpdateToTimelineItems(this.timelineData, updatedTrip)
        },

        applyStayFavoriteUpdate(updatedFavorite) {
            this.timelineData = applyStayFavoriteUpdateToTimelineItems(this.timelineData, updatedFavorite)
        },

        applyStayGeocodingUpdate(oldGeocodingId, updatedGeocoding) {
            this.timelineData = applyStayGeocodingUpdateToTimelineItems(this.timelineData, oldGeocodingId, updatedGeocoding)
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
