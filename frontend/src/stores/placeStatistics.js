import { defineStore } from 'pinia'
import apiService from '../utils/apiService'

export const usePlaceStatisticsStore = defineStore('placeStatistics', {
    state: () => ({
        placeDetails: null,
        placeVisits: [],
        pagination: {
            currentPage: 0,
            pageSize: 50,
            totalCount: 0,
            totalPages: 0
        },
        loading: false,
        error: null
    }),

    getters: {
        // Direct access getters
        getPlaceDetails: (state) => state.placeDetails,
        getPlaceVisits: (state) => state.placeVisits,
        getPagination: (state) => state.pagination,
        isLoading: (state) => state.loading,
        getError: (state) => state.error,

        // Computed getters
        hasPlaceDetails: (state) => state.placeDetails !== null,
        hasVisits: (state) => state.placeVisits && state.placeVisits.length > 0,
        totalVisits: (state) => state.pagination.totalCount
    },

    actions: {
        // Set place details
        setPlaceDetails(details) {
            this.placeDetails = details
        },

        // Set place visits with pagination data
        setPlaceVisits(data) {
            this.placeVisits = data.visits || []
            this.pagination = {
                currentPage: data.currentPage || 0,
                pageSize: data.pageSize || 50,
                totalCount: data.totalCount || 0,
                totalPages: data.totalPages || 0
            }
        },

        // Clear all data
        clearPlaceData() {
            this.placeDetails = null
            this.placeVisits = []
            this.pagination = {
                currentPage: 0,
                pageSize: 50,
                totalCount: 0,
                totalPages: 0
            }
            this.error = null
        },

        // Set loading state
        setLoading(loading) {
            this.loading = loading
        },

        // Set error state
        setError(error) {
            this.error = error
        },

        // API Actions

        /**
         * Fetch place details including statistics
         * @param {string} type - "favorite" or "geocoding"
         * @param {number|string} id - place ID
         */
        async fetchPlaceDetails(type, id) {
            this.setLoading(true)
            this.setError(null)

            try {
                const response = await apiService.get(`/place-details/${type}/${id}`)
                this.setPlaceDetails(response.data)
                return response
            } catch (error) {
                console.error('Error fetching place details:', error)
                this.setError(error.message || 'Failed to fetch place details')
                throw error
            } finally {
                this.setLoading(false)
            }
        },

        /**
         * Fetch paginated visit history for a place
         * @param {string} type - "favorite" or "geocoding"
         * @param {number|string} id - place ID
         * @param {number} page - zero-based page number
         * @param {number} pageSize - number of items per page
         * @param {string} sortBy - field to sort by
         * @param {string} sortDirection - "asc" or "desc"
         */
        async fetchPlaceVisits(type, id, page = 0, pageSize = 50, sortBy = 'timestamp', sortDirection = 'desc') {
            this.setLoading(true)
            this.setError(null)

            try {
                const response = await apiService.get(`/place-details/${type}/${id}/visits`, {
                    page,
                    size: pageSize,
                    sortBy,
                    sortDirection
                })
                this.setPlaceVisits(response.data)
                return response
            } catch (error) {
                console.error('Error fetching place visits:', error)
                this.setError(error.message || 'Failed to fetch place visits')
                throw error
            } finally {
                this.setLoading(false)
            }
        },

        /**
         * Update place name (only for favorites)
         * @param {string} type - place type (must be "favorite")
         * @param {number|string} id - place ID
         * @param {string} newName - new name for the place
         */
        async updatePlaceName(type, id, newName) {
            this.setLoading(true)
            this.setError(null)

            try {
                const response = await apiService.put(`/place-details/${type}/${id}`, {
                    name: newName
                })

                // Update local state if successful
                if (this.placeDetails && this.placeDetails.id === id && this.placeDetails.type === type) {
                    this.placeDetails.locationName = newName
                }

                return response
            } catch (error) {
                console.error('Error updating place name:', error)
                this.setError(error.message || 'Failed to update place name')
                throw error
            } finally {
                this.setLoading(false)
            }
        },

        /**
         * Refresh current page of visits
         */
        async refreshVisits() {
            if (!this.placeDetails) {
                console.warn('No place details loaded, cannot refresh visits')
                return
            }

            const { type, id } = this.placeDetails
            const { currentPage, pageSize } = this.pagination

            await this.fetchPlaceVisits(type, id, currentPage, pageSize)
        },

        /**
         * Load a specific page of visits
         * @param {number} page - page number to load
         */
        async loadPage(page) {
            if (!this.placeDetails) {
                console.warn('No place details loaded, cannot load page')
                return
            }

            const { type, id } = this.placeDetails
            const { pageSize } = this.pagination

            await this.fetchPlaceVisits(type, id, page, pageSize)
        }
    }
})
