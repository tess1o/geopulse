import {defineStore} from 'pinia'
import apiService from '../utils/apiService'
import { normalizeApiError } from '@/utils/apiErrorDetail'

export const useTimelinePreferencesStore = defineStore('timelinePreferences', {
    state: () => ({
        timelinePreferences: null,
        lastUpdateResponseData: null,
        error: null
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
        getCarEnabled: (state) => state.timelinePreferences?.carEnabled ?? true,
        getMotorcycleEnabled: (state) => state.timelinePreferences?.motorcycleEnabled ?? false,
        getPublicTransportationEnabled: (state) => state.timelinePreferences?.publicTransportationEnabled ?? false,
        getPreferredMotorizedType: (state) => state.timelinePreferences?.preferredMotorizedType ?? 'CAR',
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
                const response = await apiService.get(`/timeline/preferences`)
                this.setTimelinePreferences(response)
                return response
            } catch (error) {
                this.error = normalizeApiError(error, 'Failed to load timeline preferences')
                throw this.error
            }
        },

        async updateTimelinePreferences(changes) {
            try {
                const response = await apiService.put(`/preferences/timeline`, {...changes})
                // Refresh preferences to get updated data from backend
                await this.fetchTimelinePreferences()

                this.lastUpdateResponseData = response || null
                return response?.jobId || response?.boatSetupJobId || null
            } catch (error) {
                this.error = normalizeApiError(error, 'Failed to update timeline preferences')
                throw this.error
            }
        },

        async resetTimelinePreferencesToDefaults() {
            try {
                const response = await apiService.delete(`/preferences/timeline`)

                // Refresh preferences after reset
                await this.fetchTimelinePreferences()

                return response?.jobId || null
            } catch (error) {
                this.error = normalizeApiError(error, 'Failed to reset timeline preferences')
                throw this.error
            }
        },
    }
})
