import {defineStore} from 'pinia'
import apiService from '../utils/apiService'

export const useTimelinePreferencesStore = defineStore('timelinePreferences', {
    state: () => ({
        timelinePreferences: null
    }),

    getters: {
        // Direct access getter
        getTimelinePreferences: (state) => state.timelinePreferences,

        // Check if preferences are loaded
        hasPreferences: (state) => !!state.timelinePreferences,

        // Get specific preference values (with defaults)
        getPreference: (state) => (key, defaultValue = null) => {
            return state.timelinePreferences?.[key] ?? defaultValue
        },

        // Common preference getters (adjust these based on your actual preferences structure)
        getShowTrips: (state) => state.timelinePreferences?.showTrips ?? true,
        getShowStays: (state) => state.timelinePreferences?.showStays ?? true,
        getShowDuration: (state) => state.timelinePreferences?.showDuration ?? true,
        getShowDistance: (state) => state.timelinePreferences?.showDistance ?? true,
        getTimeFormat: (state) => state.timelinePreferences?.timeFormat ?? '24h',
        getDateFormat: (state) => state.timelinePreferences?.dateFormat ?? 'MM/dd/yyyy',
        
        // Travel classification preference getters with defaults
        getWalkingMaxAvgSpeed: (state) => state.timelinePreferences?.walkingMaxAvgSpeed ?? 6.0,
        getWalkingMaxMaxSpeed: (state) => state.timelinePreferences?.walkingMaxMaxSpeed ?? 8.0,
        getCarMinAvgSpeed: (state) => state.timelinePreferences?.carMinAvgSpeed ?? 8.0,
        getCarMinMaxSpeed: (state) => state.timelinePreferences?.carMinMaxSpeed ?? 15.0,
        getShortDistanceKm: (state) => state.timelinePreferences?.shortDistanceKm ?? 1.0,

        // Check if preferences are at default values
        isDefaultSettings: (state) => {
            if (!state.timelinePreferences) return true
            // Add logic to check if all preferences match defaults
            // This depends on what your default preferences look like
            return Object.keys(state.timelinePreferences).length === 0
        },

        // Get preferences summary for display
        getPreferencesSummary: (state) => {
            if (!state.timelinePreferences) return null

            return {
                totalSettings: Object.keys(state.timelinePreferences).length,
                customized: !state.isDefaultSettings,
                lastUpdated: state.timelinePreferences.updatedAt || null
            }
        }
    },

    actions: {
        // Set timeline preferences (replaces mutations)
        setTimelinePreferences(preferences) {
            this.timelinePreferences = preferences
        },

        // API Actions
        async fetchTimelinePreferences() {
            try {
                const response = await apiService.get(`/streaming-timeline/user/preferences`)
                this.setTimelinePreferences(response)
                return response
            } catch (error) {
                throw error
            }
        },

        async updateTimelinePreferences(changes) {
            try {
                console.log(changes)
                const response = await apiService.put(`/users/preferences/timeline`, {...changes})

                // Refresh preferences to get updated data from backend
                await this.fetchTimelinePreferences()

                // Return job ID if available (for async timeline regeneration)
                // Response structure: { status: "success", data: { jobId: "..." } }
                return response?.data?.jobId || null
            } catch (error) {
                throw error
            }
        },

        async resetTimelinePreferencesToDefaults() {
            try {
                const response = await apiService.delete(`/users/preferences/timeline`)

                // Refresh preferences after reset
                await this.fetchTimelinePreferences()

                // Return job ID if available (for async timeline regeneration)
                // Response structure: { status: "success", data: { jobId: "..." } }
                return response?.data?.jobId || null
            } catch (error) {
                throw error
            }
        },
    }
})