import {areaPayload} from '@/maps/favoritesManagement/shared/favoritesManagementGeometry'
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
        pendingUpdates: {},
        pendingDeletes: {},
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

        previewFavorites: (state) => [
            ...state.favoritePlaces.points.map(p => ({ ...p, type: 'POINT' })),
            ...state.favoritePlaces.areas.map(a => ({ ...a, type: 'AREA' }))
        ].filter(item => !state.pendingDeletes[item.id])
            .map(item => ({ ...item, ...state.pendingUpdates[item.id] })),

        hasPendingFavorites() { return this.pendingCount > 0 },
        pendingCount() { return this.getAllPending.length },
        getAllPending: (state) => [
            ...state.pendingFavorites.points.map(p => ({ ...p, type: 'point', operation: 'add' })),
            ...state.pendingFavorites.areas.map(a => ({ ...a, type: 'area', operation: 'add' })),
            ...Object.values(state.pendingUpdates).filter(item => !state.pendingDeletes[item.id])
                .map(item => ({ ...item, tempId: `update-${item.id}`, operation: 'update' })),
            ...Object.values(state.pendingDeletes)
                .map(item => ({ ...item, tempId: `delete-${item.id}`, operation: 'delete' }))
        ]
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

        async addAreaToFavorites(name, northEastLat, northEastLon, southWestLat, southWestLon, geometry = null) {
            this.error = null
            try {
                const response = await apiService.post(`/favorites/areas`, {
                    name,
                    ...areaPayload({geometry, northEastLat, northEastLon, southWestLat, southWestLon})
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
                    Object.assign(payload, areaPayload(bounds))
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

        addAreaToPending(name, northEastLat, northEastLon, southWestLat, southWestLon, geometry = null) {
            const tempId = `area-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
            this.pendingFavorites.areas.push({
                name,
                northEastLat,
                northEastLon,
                southWestLat,
                southWestLon,
                geometry,
                tempId
            })
        },

        queueUpdate(favorite) {
            if (!this.pendingDeletes[favorite.id]) this.pendingUpdates[favorite.id] = JSON.parse(JSON.stringify(favorite))
        },

        queueDelete(favorite) {
            this.pendingDeletes[favorite.id] = { ...favorite }
        },

        queueBulkUpdate(favoriteIds, updateCity, city, updateCountry, country) {
            for (const id of favoriteIds) {
                const favorite = this.previewFavorites.find(item => item.id === id)
                if (!favorite) continue
                this.queueUpdate({ ...favorite,
                    ...(updateCity ? { city } : {}), ...(updateCountry ? { country } : {}) })
            }
            return { queued: true, successCount: favoriteIds.length, failedCount: 0 }
        },

        async applyPendingChanges() {
            this.error = null
            const { points, areas } = this.pendingFavorites
            const changes = Object.values(this.pendingUpdates)
                .filter(item => !this.pendingDeletes[item.id])
                .map(({ id, name, city, country, type, geometry, northEastLat, northEastLon, southWestLat, southWestLon }) => ({
                    favoriteId: id,
                    changes: { name, city, country, ...(type === 'AREA'
                        ? areaPayload({geometry, northEastLat, northEastLon, southWestLat, southWestLon}) : {}) }
                }))
            try {
                const result = await apiService.post('/favorites/bulk-apply', {
                    points: points.map(({ tempId, ...point }) => point),
                    areas: areas.map(area => ({name: area.name, ...areaPayload(area)})),
                    updates: changes,
                    deleteIds: Object.values(this.pendingDeletes).map(item => item.id)
                })
                this.clearPending()
                return result
            } catch (error) {
                throw this.fail(error, 'Failed to apply favorite changes')
            }
        },

        removeFromPending(tempId) {
            if (tempId.startsWith('update-')) delete this.pendingUpdates[tempId.slice(7)]
            if (tempId.startsWith('delete-')) delete this.pendingDeletes[tempId.slice(7)]
            this.pendingFavorites.points = this.pendingFavorites.points.filter(p => p.tempId !== tempId)
            this.pendingFavorites.areas = this.pendingFavorites.areas.filter(a => a.tempId !== tempId)
        },

        clearPending() {
            this.pendingUpdates = {}
            this.pendingDeletes = {}
            this.pendingFavorites.points = []
            this.pendingFavorites.areas = []
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
