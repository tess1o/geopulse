import {defineStore} from 'pinia'
import apiService from '../utils/apiService'
import {normalizeApiError} from '@/utils/apiErrorDetail'

export const useFavoritesStore = defineStore('favorites', {
    state: () => ({
        favoritePlaces: {
            areas: [],
            points: []
        },
        pendingFavorites: {
            points: [],  // Array of { name, lat, lon, tempId }
            areas: []    // Array of { name, northEastLat, northEastLon, southWestLat, southWestLon, tempId }
        },
        loading: false,
        error: null
    }),

    getters: {
        // Direct access getter
        getFavoritePlaces: (state) => state.favoritePlaces,

        // Get individual arrays
        getFavoritePoints: (state) => state.favoritePlaces.points || [],
        getFavoriteAreas: (state) => state.favoritePlaces.areas || [],

        // Computed getters for additional functionality
        hasFavorites: (state) => {
            const points = state.favoritePlaces.points || []
            const areas = state.favoritePlaces.areas || []
            return points.length > 0 || areas.length > 0
        },

        hasPoints: (state) => (state.favoritePlaces.points || []).length > 0,
        hasAreas: (state) => (state.favoritePlaces.areas || []).length > 0,

        pointsCount: (state) => (state.favoritePlaces.points || []).length,
        areasCount: (state) => (state.favoritePlaces.areas || []).length,
        totalFavoritesCount: (state) => {
            const points = state.favoritePlaces.points || []
            const areas = state.favoritePlaces.areas || []
            return points.length + areas.length
        },

        // Get favorite by ID (searches both points and areas)
        getFavoriteById: (state) => (id) => {
            const points = state.favoritePlaces.points || []
            const areas = state.favoritePlaces.areas || []
            return points.find(point => point.id === id) ||
                areas.find(area => area.id === id)
        },

        // Get point by ID
        getPointById: (state) => (id) => {
            const points = state.favoritePlaces.points || []
            return points.find(point => point.id === id)
        },

        // Get area by ID
        getAreaById: (state) => (id) => {
            const areas = state.favoritePlaces.areas || []
            return areas.find(area => area.id === id)
        },

        // Search favorites by name (searches both points and areas)
        searchFavorites: (state) => (searchTerm) => {
            if (!searchTerm) return state.favoritePlaces
            const term = searchTerm.toLowerCase()
            const points = state.favoritePlaces.points || []
            const areas = state.favoritePlaces.areas || []

            return {
                points: points.filter(point => point.name.toLowerCase().includes(term)),
                areas: areas.filter(area => area.name.toLowerCase().includes(term))
            }
        },

        // Get points within a bounding box
        getPointsInBounds: (state) => (northEast, southWest) => {
            const points = state.favoritePlaces.points || []
            return points.filter(point =>
                point.lat >= southWest.lat &&
                point.lat <= northEast.lat &&
                point.lon >= southWest.lon &&
                point.lon <= northEast.lon
            )
        },

        // Pending favorites getters
        getPendingPoints: (state) => state.pendingFavorites.points || [],
        getPendingAreas: (state) => state.pendingFavorites.areas || [],

        hasPendingFavorites: (state) => {
            const points = state.pendingFavorites.points || []
            const areas = state.pendingFavorites.areas || []
            return points.length > 0 || areas.length > 0
        },

        pendingCount: (state) => {
            const points = state.pendingFavorites.points || []
            const areas = state.pendingFavorites.areas || []
            return points.length + areas.length
        },

        getAllPending: (state) => {
            return [
                ...(state.pendingFavorites.points || []).map(p => ({ ...p, type: 'point' })),
                ...(state.pendingFavorites.areas || []).map(a => ({ ...a, type: 'area' }))
            ]
        }
    },

    actions: {
        fail(error, fallback) {
            this.error = normalizeApiError(error, fallback)
            return this.error
        },

        // Set favorites data (replaces mutations)
        setFavoritePlaces(places) {
            // Ensure the structure is correct
            this.favoritePlaces = {
                areas: places.areas || [],
                points: places.points || []
            }
        },

        // API Actions
        async fetchFavoritePlaces() {
            this.loading = true
            this.error = null
            try {
                const favorites = await apiService.get('/favorites')
                this.setFavoritePlaces(favorites)
                return favorites
            } catch (error) {
                throw this.fail(error, 'Failed to load favorites')
            } finally {
                this.loading = false
            }
        },

        async addPointToFavorites(name, lat, lon) {
            this.error = null
            try {
                const response = await apiService.post(`/favorites/points`, {
                    name,
                    lat,
                    lon
                })

                // Refresh favorites to get the updated list from backend
                await this.fetchFavoritePlaces()

                return response?.jobId || null
            } catch (error) {
                throw this.fail(error, 'Failed to add favorite point')
            }
        },

        async addAreaToFavorites(name, northEastLat, northEastLon, southWestLat, southWestLon) {
            this.error = null
            try {
                const response = await apiService.post(`/favorites/areas`, {
                    name,
                    northEastLat,
                    northEastLon,
                    southWestLat,
                    southWestLon
                })

                // Refresh favorites to get the updated list from backend
                await this.fetchFavoritePlaces()

                return response?.jobId || null
            } catch (error) {
                throw this.fail(error, 'Failed to add favorite area')
            }
        },

        async editFavorite(id, name, city, country, bounds = null) {
            this.error = null
            try {
                const payload = {
                    name,
                    city,
                    country
                }

                // Include bounds if provided (for area favorites)
                if (bounds) {
                    payload.northEastLat = bounds.northEastLat
                    payload.northEastLon = bounds.northEastLon
                    payload.southWestLat = bounds.southWestLat
                    payload.southWestLon = bounds.southWestLon
                }

                const response = await apiService.put(`/favorites/${id}`, payload)

                // Refresh favorites to get the updated list from backend
                await this.fetchFavoritePlaces()

                return response?.jobId || null
            } catch (error) {
                throw this.fail(error, 'Failed to update favorite')
            }
        },

        async deleteFavorite(id) {
            this.error = null
            try {
                const response = await apiService.delete(`/favorites/${id}`, {})

                // Refresh favorites to get the updated list from backend
                await this.fetchFavoritePlaces()

                return response?.jobId || null
            } catch (error) {
                throw this.fail(error, 'Failed to delete favorite')
            }
        },

        async startBulkReconciliation(request) {
            this.error = null
            try {
                const response = await apiService.post('/favorites/reconcile/bulk', request)
                return response
            } catch (error) {
                console.error('Error starting bulk favorite reconciliation:', error)
                throw this.fail(error, 'Failed to start favorite reconciliation')
            }
        },

        async getReconciliationJobProgress(jobId) {
            try {
                return await apiService.get(`/favorites/reconcile/jobs/${jobId}`)
            } catch (error) {
                console.error('Error fetching favorite reconciliation job progress:', error)
                throw this.fail(error, 'Failed to fetch reconciliation progress')
            }
        },

        // Pending favorites actions
        addPointToPending(name, lat, lon) {
            const tempId = `point-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
            this.pendingFavorites.points.push({
                name,
                lat,
                lon,
                tempId
            })
        },

        addAreaToPending(name, northEastLat, northEastLon, southWestLat, southWestLon) {
            const tempId = `area-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
            this.pendingFavorites.areas.push({
                name,
                northEastLat,
                northEastLon,
                southWestLat,
                southWestLon,
                tempId
            })
        },

        removeFromPending(tempId) {
            this.pendingFavorites.points = this.pendingFavorites.points.filter(p => p.tempId !== tempId)
            this.pendingFavorites.areas = this.pendingFavorites.areas.filter(a => a.tempId !== tempId)
        },

        clearPending() {
            this.pendingFavorites.points = []
            this.pendingFavorites.areas = []
        },

        async bulkCreateFavorites() {
            this.error = null
            try {
                // Prepare the bulk request - remove tempId from each item
                const points = this.pendingFavorites.points.map(({ tempId, ...point }) => point)
                const areas = this.pendingFavorites.areas.map(({ tempId, ...area }) => area)

                const response = await apiService.post('/favorites/bulk', {
                    points,
                    areas
                })

                // Clear pending list after successful creation
                this.clearPending()

                // Refresh favorites to get the updated list from backend
                await this.fetchFavoritePlaces()

                // Return the result which includes jobId, successCount, failedCount, etc.
                return response
            } catch (error) {
                console.error('Error bulk creating favorites:', error)
                throw this.fail(error, 'Failed to create favorites')
            }
        },

        async bulkUpdateFavorites(favoriteIds, updateCity, city, updateCountry, country) {
            this.error = null
            try {
                const response = await apiService.patch('/favorites/bulk-update', {
                    favoriteIds,
                    updateCity,
                    city,
                    updateCountry,
                    country
                })

                // Refresh favorites to get the updated list from backend
                await this.fetchFavoritePlaces()

                // Return the result with success/failure counts
                return response
            } catch (error) {
                console.error('Error bulk updating favorites:', error)
                throw this.fail(error, 'Failed to update favorites')
            }
        },

        async fetchDistinctValues() {
            try {
                return await apiService.get('/favorites/distinct-values')
            } catch (error) {
                console.error('Error fetching distinct values:', error)
                throw this.fail(error, 'Failed to load favorite values')
            }
        }
    }
})
