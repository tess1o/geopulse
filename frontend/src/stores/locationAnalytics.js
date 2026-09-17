import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'
import { normalizeApiError } from '@/utils/apiErrorDetail'

const emptyPage = () => ({ currentPage: 0, pageSize: 50, totalCount: 0, totalPages: 0 })

export const useLocationAnalyticsStore = defineStore('locationAnalytics', {
  state: () => ({
    searchResults: [],
    searchLoading: false,
    mapPlaces: [],
    mapPlacesLoading: false,
    cities: [],
    citiesLoading: false,
    cityDetails: null,
    cityVisits: [],
    cityPagination: emptyPage(),
    countries: [],
    countriesLoading: false,
    countryDetails: null,
    countryVisits: [],
    countryPagination: emptyPage(),
    loading: false,
    error: null
  }),

  actions: {
    fail(error, fallback) {
      this.error = normalizeApiError(error, fallback)
      return this.error
    },

    async searchLocations(query, type = null) {
      this.searchLoading = true
      this.error = null
      try {
        this.searchResults = await apiService.get('/location-analytics/search', {
          q: query,
          ...(type ? { type } : {})
        })
        return this.searchResults
      } catch (error) {
        throw this.fail(error, 'Search failed')
      } finally {
        this.searchLoading = false
      }
    },

    async fetchMapPlaces(options = {}) {
      this.mapPlacesLoading = true
      this.error = null
      try {
        const params = { minVisits: options.minVisits ?? 1, limit: options.limit ?? 3000 }
        ;['from', 'to', 'minLat', 'maxLat', 'minLon', 'maxLon'].forEach((key) => {
          if (options[key] != null) params[key] = options[key]
        })
        this.mapPlaces = await apiService.get('/location-analytics/map/places', params)
        return this.mapPlaces
      } catch (error) {
        throw this.fail(error, 'Failed to fetch map places')
      } finally {
        this.mapPlacesLoading = false
      }
    },

    clearSearchResults() {
      this.searchResults = []
    },

    async fetchAllCities() {
      this.citiesLoading = true
      this.error = null
      try {
        this.cities = await apiService.get('/location-analytics/cities')
        return this.cities
      } catch (error) {
        throw this.fail(error, 'Failed to fetch cities')
      } finally {
        this.citiesLoading = false
      }
    },

    async fetchCityDetails(cityName) {
      this.loading = true
      this.error = null
      try {
        this.cityDetails = await apiService.get(`/location-analytics/city/${encodeURIComponent(cityName)}`)
        return this.cityDetails
      } catch (error) {
        throw this.fail(error, 'Failed to fetch city details')
      } finally {
        this.loading = false
      }
    },

    async fetchCityVisits(cityName, page = 0, pageSize = 50, sortBy = 'timestamp', sortDirection = 'desc') {
      return this.fetchVisits('city', cityName, page, pageSize, sortBy, sortDirection)
    },

    async fetchAllCountries() {
      this.countriesLoading = true
      this.error = null
      try {
        this.countries = await apiService.get('/location-analytics/countries')
        return this.countries
      } catch (error) {
        throw this.fail(error, 'Failed to fetch countries')
      } finally {
        this.countriesLoading = false
      }
    },

    async fetchCountryDetails(countryName) {
      this.loading = true
      this.error = null
      try {
        this.countryDetails = await apiService.get(`/location-analytics/country/${encodeURIComponent(countryName)}`)
        return this.countryDetails
      } catch (error) {
        throw this.fail(error, 'Failed to fetch country details')
      } finally {
        this.loading = false
      }
    },

    async fetchCountryVisits(countryName, page = 0, pageSize = 50, sortBy = 'timestamp', sortDirection = 'desc') {
      return this.fetchVisits('country', countryName, page, pageSize, sortBy, sortDirection)
    },

    async fetchVisits(kind, name, page, pageSize, sortBy, sortDirection) {
      this.loading = true
      this.error = null
      try {
        const data = await apiService.get(`/location-analytics/${kind}/${encodeURIComponent(name)}/visits`, {
          page, size: pageSize, sortBy, sortDirection
        })
        this[`${kind}Visits`] = data.items || []
        this[`${kind}Pagination`] = {
          currentPage: data.page ?? 0,
          pageSize: data.size ?? 50,
          totalCount: data.totalElements ?? 0,
          totalPages: data.totalPages ?? 0
        }
        return data
      } catch (error) {
        throw this.fail(error, `Failed to fetch ${kind} visits`)
      } finally {
        this.loading = false
      }
    },

    async exportVisits(kind, name, sortBy = 'timestamp', sortDirection = 'desc') {
      try {
        return await apiService.download(
          `/location-analytics/${kind}/${encodeURIComponent(name)}/visits/export`,
          { sortBy, sortDirection }
        )
      } catch (error) {
        throw this.fail(error, 'Failed to export visits')
      }
    },

    clearCityData() {
      this.cityDetails = null
      this.cityVisits = []
      this.cityPagination = emptyPage()
    },

    clearCountryData() {
      this.countryDetails = null
      this.countryVisits = []
      this.countryPagination = emptyPage()
    },

    clearAllData() {
      this.searchResults = []
      this.mapPlaces = []
      this.cities = []
      this.countries = []
      this.clearCityData()
      this.clearCountryData()
      this.error = null
    }
  }
})
