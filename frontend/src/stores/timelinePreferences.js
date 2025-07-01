import {defineStore} from 'pinia'
import apiService from '../utils/apiService'

//TODO: check if all these crap is needed
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
                const response = await apiService.get(`/timeline/user/preferences`)
                this.setTimelinePreferences(response)
                return response
            } catch (error) {
                throw error
            }
        },

        async updateTimelinePreferences(changes) {
            try {
                console.log(changes)
                await apiService.put(`/users/preferences/timeline`, {...changes})

                // Refresh preferences to get updated data from backend
                await this.fetchTimelinePreferences()
            } catch (error) {
                throw error
            }
        },

        async resetTimelinePreferencesToDefaults() {
            try {
                await apiService.delete(`/users/preferences/timeline`)

                // Refresh preferences after reset
                await this.fetchTimelinePreferences()
            } catch (error) {
                throw error
            }
        },
    }
})