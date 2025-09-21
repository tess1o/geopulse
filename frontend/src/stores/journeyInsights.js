import { defineStore } from 'pinia'
import apiService from '../utils/apiService'
import { STREAK_STATUS, ACTIVITY_LEVEL } from '../constants/journeyInsights'
import dayjs from 'dayjs';

export const useJourneyInsightsStore = defineStore('journeyInsights', {
    state: () => ({
        insights: null,
        loading: false,
        lastFetched: null
    }),

    getters: {
        // Direct access getters
        getInsights: (state) => state.insights,
        isLoading: (state) => state.loading,
        
        // Data availability checks
        hasData: (state) => !!state.insights,
        hasGeographicData: (state) => !!state.insights?.geographic,
        hasTimePatterns: (state) => !!state.insights?.timePatterns,
        hasAchievements: (state) => !!state.insights?.achievements,
        
        // Specific data getters
        geographic: (state) => state.insights?.geographic || {},
        timePatterns: (state) => state.insights?.timePatterns || {},
        achievements: (state) => state.insights?.achievements || {},
        distance: (state) => state.insights?.distanceTraveled || {},
        
        // Cache status
        isStale: (state) => {
            if (!state.lastFetched) return true
            const oneHour = 60 * 60 * 1000 // 1 hour in milliseconds
            const { useTimezone } = require('@/composables/useTimezone')
            const timezone = useTimezone()
            return timezone.now().diff(timezone.fromUtc(state.lastFetched)) > oneHour
        },
        
        // Display helpers for enums
        getStreakStatusDisplay: (state) => {
            const status = state.insights?.achievements?.streakStatus
            switch (status) {
                case STREAK_STATUS.INACTIVE:
                    return { text: 'Start Your Journey', icon: '💤', color: 'muted' }
                case STREAK_STATUS.BEGINNER:
                    return { text: 'Getting Started', icon: '🌱', color: 'info' }
                case STREAK_STATUS.CONSISTENT:
                    return { text: 'Building Momentum', icon: '🔥', color: 'warning' }
                case STREAK_STATUS.DEDICATED:
                    return { text: 'Seriously Committed', icon: '🚀', color: 'secondary' }
                case STREAK_STATUS.CHAMPION:
                    return { text: 'Elite Tracker', icon: '👑', color: 'success' }
                default:
                    return { text: 'Unknown', icon: '❓', color: 'muted' }
            }
        },
        
        getActivityLevelDisplay: (state) => {
            const level = state.insights?.timePatterns?.activityLevel
            switch (level) {
                case ACTIVITY_LEVEL.LOW:
                    return { text: 'Relaxed Explorer', color: 'info' }
                case ACTIVITY_LEVEL.MODERATE:
                    return { text: 'Regular Traveler', color: 'primary' }
                case ACTIVITY_LEVEL.HIGH:
                    return { text: 'Active Adventurer', color: 'warning' }
                case ACTIVITY_LEVEL.EXTREME:
                    return { text: 'Travel Enthusiast', color: 'danger' }
                default:
                    return { text: 'Explorer', color: 'muted' }
            }
        }
    },

    actions: {
        async setInsights(insights) {
            this.insights = insights
            const { useTimezone } = await import('@/composables/useTimezone')
            const timezone = useTimezone()
            this.lastFetched = timezone.now().toISOString();
        },

        setLoading(loading) {
            this.loading = loading
        },

        clearInsights() {
            this.insights = null
            this.lastFetched = null
        },

        // API Actions
        async fetchJourneyInsights(force = false) {
            // Skip if data is fresh and not forced
            if (!force && this.hasData && !this.isStale) {
                return this.insights
            }

            try {
                this.setLoading(true)
                const response = await apiService.get('/journey-insights')
                this.setInsights(response)
                return response
            } catch (error) {
                console.error('Error fetching journey insights:', error)
                throw error
            } finally {
                this.setLoading(false)
            }
        },

        // Refresh insights data
        async refreshInsights() {
            return this.fetchJourneyInsights(true)
        }
    }
})