import { defineStore } from 'pinia'
import apiService from '../utils/apiService'
import { normalizeApiError } from '@/utils/apiErrorDetail'

/**
 * Places worth visiting around an area the user chooses.
 *
 * The area is chosen inside this feature rather than stored on the trip, so a trip that
 * spans several cities is not forced into a single point.
 */
export const usePoiDiscoveryStore = defineStore('poiDiscovery', {
    state: () => ({
        area: null,
        radiusMeters: 8000,
        results: [],
        attribution: '',
        cached: false,
        loading: false,
        error: null
    }),

    getters: {
        hasArea: (state) => Boolean(state.area),
        hasResults: (state) => state.results.length > 0
    },

    actions: {
        setArea(area) {
            const moved = !this.area
                || this.area.latitude !== area?.latitude
                || this.area.longitude !== area?.longitude

            this.area = area
            if (moved) {
                // Results belong to the previous area; showing them against a new one
                // would silently mislabel every card.
                this.results = []
                this.error = null
            }
            return moved
        },

        setRadius(radiusMeters) {
            if (this.radiusMeters === radiusMeters) return false
            this.radiusMeters = radiusMeters
            return true
        },

        async search() {
            if (!this.area) return []

            this.loading = true
            this.error = null
            try {
                const response = await apiService.get('/poi/search', {
                    latitude: this.area.latitude,
                    longitude: this.area.longitude,
                    radiusMeters: this.radiusMeters
                })
                this.results = Array.isArray(response?.results) ? response.results : []
                this.attribution = response?.dataAttribution || ''
                this.cached = Boolean(response?.cached)
                return this.results
            } catch (error) {
                this.results = []
                this.error = normalizeApiError(error, 'Could not load places to visit')
                return []
            } finally {
                this.loading = false
            }
        },

        clear() {
            this.area = null
            this.results = []
            this.error = null
        }
    }
})
