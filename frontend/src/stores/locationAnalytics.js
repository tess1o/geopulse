import { defineStore } from 'pinia'
import apiService from '../utils/apiService'

export const useLocationAnalyticsStore = defineStore('locationAnalytics', {
    state: () => ({
        // Search
        searchResults: [],
        searchLoading: false,

        // Cities
        cities: [],
        citiesLoading: false,
        cityDetails: null,
        cityVisits: [],
        cityPagination: {
            currentPage: 0,
            pageSize: 50,
            totalCount: 0,
            totalPages: 0
        },

        // Countries
        countries: [],
        countriesLoading: false,
        countryDetails: null,
        countryVisits: [],
        countryPagination: {
            currentPage: 0,
            pageSize: 50,
            totalCount: 0,
            totalPages: 0
        },

        // General
        loading: false,
        error: null
    }),

    getters: {
        // Search getters
        getSearchResults: (state) => state.searchResults,
        isSearching: (state) => state.searchLoading,

        // City getters
        getAllCities: (state) => state.cities,
        getCityDetails: (state) => state.cityDetails,
        getCityVisits: (state) => state.cityVisits,
        getCityPagination: (state) => state.cityPagination,
        hasCities: (state) => state.cities && state.cities.length > 0,
        hasCityDetails: (state) => state.cityDetails !== null,

        // Country getters
        getAllCountries: (state) => state.countries,
        getCountryDetails: (state) => state.countryDetails,
        getCountryVisits: (state) => state.countryVisits,
        getCountryPagination: (state) => state.countryPagination,
        hasCountries: (state) => state.countries && state.countries.length > 0,
        hasCountryDetails: (state) => state.countryDetails !== null,

        // General getters
        isLoading: (state) => state.loading,
        getError: (state) => state.error
    },

    actions: {
        /**
         * Search across places, cities, and countries
         * @param {string} query - Search query (minimum 2 characters)
         * @param {string|null} typeFilter - Optional filter by type: "place", "city", "country"
         */
        async searchLocations(query, typeFilter = null) {
            this.searchLoading = true
            this.error = null

            try {
                const params = { q: query }
                if (typeFilter) {
                    params.type = typeFilter
                }

                const response = await apiService.get('/location-analytics/search', params)
                this.searchResults = response.data
                return response
            } catch (error) {
                console.error('Search failed:', error)
                this.error = error.message || 'Search failed'
                throw error
            } finally {
                this.searchLoading = false
            }
        },

        /**
         * Clear search results
         */
        clearSearchResults() {
            this.searchResults = []
        },

        /**
         * Fetch all cities with visit counts
         */
        async fetchAllCities() {
            this.citiesLoading = true
            this.error = null

            try {
                const response = await apiService.get('/location-analytics/cities')
                this.cities = response.data
                return response
            } catch (error) {
                console.error('Failed to fetch cities:', error)
                this.error = error.message || 'Failed to fetch cities'
                throw error
            } finally {
                this.citiesLoading = false
            }
        },

        /**
         * Fetch detailed statistics for a specific city
         * @param {string} cityName - City name
         */
        async fetchCityDetails(cityName) {
            this.loading = true
            this.error = null

            try {
                const response = await apiService.get(`/location-analytics/city/${encodeURIComponent(cityName)}`)
                this.cityDetails = response.data
                return response
            } catch (error) {
                console.error('Failed to fetch city details:', error)
                this.error = error.message || 'Failed to fetch city details'
                throw error
            } finally {
                this.loading = false
            }
        },

        /**
         * Fetch paginated visits for a city
         * @param {string} cityName - City name
         * @param {number} page - Zero-based page number
         * @param {number} pageSize - Number of items per page
         * @param {string} sortBy - Sort field (default: "timestamp")
         * @param {string} sortDirection - Sort direction ("asc" or "desc", default: "desc")
         */
        async fetchCityVisits(cityName, page = 0, pageSize = 50, sortBy = 'timestamp', sortDirection = 'desc') {
            this.loading = true
            this.error = null

            try {
                const response = await apiService.get(`/location-analytics/city/${encodeURIComponent(cityName)}/visits`, {
                    page,
                    size: pageSize,
                    sortBy,
                    sortDirection
                })

                this.cityVisits = response.data.visits || []
                this.cityPagination = {
                    currentPage: response.data.currentPage || 0,
                    pageSize: response.data.pageSize || 50,
                    totalCount: response.data.totalCount || 0,
                    totalPages: response.data.totalPages || 0
                }

                return response
            } catch (error) {
                console.error('Failed to fetch city visits:', error)
                this.error = error.message || 'Failed to fetch city visits'
                throw error
            } finally {
                this.loading = false
            }
        },

        /**
         * Clear city data
         */
        clearCityData() {
            this.cityDetails = null
            this.cityVisits = []
            this.cityPagination = {
                currentPage: 0,
                pageSize: 50,
                totalCount: 0,
                totalPages: 0
            }
        },

        /**
         * Fetch all countries with visit counts
         */
        async fetchAllCountries() {
            this.countriesLoading = true
            this.error = null

            try {
                const response = await apiService.get('/location-analytics/countries')
                this.countries = response.data
                return response
            } catch (error) {
                console.error('Failed to fetch countries:', error)
                this.error = error.message || 'Failed to fetch countries'
                throw error
            } finally {
                this.countriesLoading = false
            }
        },

        /**
         * Fetch detailed statistics for a specific country
         * @param {string} countryName - Country name
         */
        async fetchCountryDetails(countryName) {
            this.loading = true
            this.error = null

            try {
                const response = await apiService.get(`/location-analytics/country/${encodeURIComponent(countryName)}`)
                this.countryDetails = response.data
                return response
            } catch (error) {
                console.error('Failed to fetch country details:', error)
                this.error = error.message || 'Failed to fetch country details'
                throw error
            } finally {
                this.loading = false
            }
        },

        /**
         * Fetch paginated visits for a country
         * @param {string} countryName - Country name
         * @param {number} page - Zero-based page number
         * @param {number} pageSize - Number of items per page
         * @param {string} sortBy - Sort field (default: "timestamp")
         * @param {string} sortDirection - Sort direction ("asc" or "desc", default: "desc")
         */
        async fetchCountryVisits(countryName, page = 0, pageSize = 50, sortBy = 'timestamp', sortDirection = 'desc') {
            this.loading = true
            this.error = null

            try {
                const response = await apiService.get(`/location-analytics/country/${encodeURIComponent(countryName)}/visits`, {
                    page,
                    size: pageSize,
                    sortBy,
                    sortDirection
                })

                this.countryVisits = response.data.visits || []
                this.countryPagination = {
                    currentPage: response.data.currentPage || 0,
                    pageSize: response.data.pageSize || 50,
                    totalCount: response.data.totalCount || 0,
                    totalPages: response.data.totalPages || 0
                }

                return response
            } catch (error) {
                console.error('Failed to fetch country visits:', error)
                this.error = error.message || 'Failed to fetch country visits'
                throw error
            } finally {
                this.loading = false
            }
        },

        /**
         * Clear country data
         */
        clearCountryData() {
            this.countryDetails = null
            this.countryVisits = []
            this.countryPagination = {
                currentPage: 0,
                pageSize: 50,
                totalCount: 0,
                totalPages: 0
            }
        },

        /**
         * Clear all data
         */
        clearAllData() {
            this.searchResults = []
            this.cities = []
            this.countries = []
            this.clearCityData()
            this.clearCountryData()
            this.error = null
        }
    }
})
