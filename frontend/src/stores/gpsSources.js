import { defineStore } from 'pinia'
import apiService from '../utils/apiService'

export const useGpsSourcesStore = defineStore('gpsSources', {
    state: () => ({
        gpsSourceConfigs: [],
        gpsSourcesEndpoints: null
    }),

    getters: {
        // Direct access getters
        getGpsSourceConfigs: (state) => state.gpsSourceConfigs,
        getGpsSourcesEndpoints: (state) => state.gpsSourcesEndpoints,

        // Computed getters for additional functionality
        hasGpsConfigs: (state) => state.gpsSourceConfigs.length > 0,
        hasEndpoints: (state) => !!state.gpsSourcesEndpoints,

        // Get GPS configs by type
        getConfigsByType: (state) => (type) => {
            return state.gpsSourceConfigs.filter(config => config.type === type)
        },

        // Get config by ID
        getConfigById: (state) => (id) => {
            return state.gpsSourceConfigs.find(config => config.id === id)
        },

        // Count of active/enabled configs
        activeConfigsCount: (state) => {
            return state.gpsSourceConfigs.filter(config => config.status === 'active').length
        },

        // Available GPS source types (if endpoints provide this info)
        availableTypes: (state) => {
            if (!state.gpsSourcesEndpoints) return []
            // Adjust this based on your endpoints structure
            return state.gpsSourcesEndpoints.types || []
        }
    },

    actions: {
        // Set GPS data (replaces mutations)
        setGpsSourceConfigs(configs) {
            this.gpsSourceConfigs = configs
        },

        setGpsSourcesEndpoints(endpoints) {
            this.gpsSourcesEndpoints = endpoints
        },

        // Clear all GPS data
        clearGpsData() {
            this.gpsSourceConfigs = []
            this.gpsSourcesEndpoints = null
        },

        // Update a single config in the store (optimistic update)
        updateConfigInStore(updatedConfig) {
            const index = this.gpsSourceConfigs.findIndex(config => config.id === updatedConfig.id)
            if (index !== -1) {
                this.gpsSourceConfigs[index] = { ...this.gpsSourceConfigs[index], ...updatedConfig }
            }
        },

        // Remove config from store (optimistic update)
        removeConfigFromStore(configId) {
            this.gpsSourceConfigs = this.gpsSourceConfigs.filter(config => config.id !== configId)
        },

        // API Actions
        async fetchGpsConfigSources() {
            try {
                const response = await apiService.get('/gps/source')
                this.setGpsSourceConfigs(response)
                return response
            } catch (error) {
                throw error
            }
        },

        async fetchGpsSourceEndpoints() {
            try {
                const response = await apiService.get('/gps/source/endpoints')
                this.setGpsSourcesEndpoints(response)
                return response
            } catch (error) {
                throw error
            }
        },

        async addGpsConfigSource(type, username, password, token) {
            try {
                // Note: Removed userId parameter as per your security discussion
                await apiService.post('/gps/source', {
                    type,
                    username,
                    password,
                    token
                    // Backend should get userId from JWT token
                })
                // Refresh the configs after adding
                await this.fetchGpsConfigSources()
            } catch (error) {
                throw error
            }
        },

        async deleteGpsSource(id) {
            try {
                // Optimistic update: remove from store first
                const originalConfigs = [...this.gpsSourceConfigs]
                this.removeConfigFromStore(id)

                try {
                    await apiService.delete(`/gps/source/${id}`, {})
                    // If successful, fetch fresh data to ensure consistency
                    await this.fetchGpsConfigSources()
                } catch (error) {
                    // Rollback on error
                    this.setGpsSourceConfigs(originalConfigs)
                    throw error
                }
            } catch (error) {
                throw error
            }
        },

        async updateGpsSource(config) {
            try {
                // Optimistic update
                const originalConfigs = [...this.gpsSourceConfigs]
                this.updateConfigInStore(config)

                try {
                    await apiService.put('/gps/source', { ...config })
                    // Fetch fresh data to ensure consistency
                    await this.fetchGpsConfigSources()
                } catch (error) {
                    // Rollback on error
                    this.setGpsSourceConfigs(originalConfigs)
                    throw error
                }
            } catch (error) {
                throw error
            }
        },

        async updateGpsSourceStatus(id, status) {
            try {
                // Optimistic update
                const originalConfigs = [...this.gpsSourceConfigs]
                const configIndex = this.gpsSourceConfigs.findIndex(config => config.id === id)
                if (configIndex !== -1) {
                    this.gpsSourceConfigs[configIndex].status = status
                }

                try {
                    await apiService.put(`/gps/source/${id}/status`, {
                        status: status
                    })
                    // Note: Your original code had this commented out
                    // Uncomment if you want to refresh after status update
                    // await this.fetchGpsConfigSources()
                } catch (error) {
                    // Rollback on error
                    this.setGpsSourceConfigs(originalConfigs)
                    throw error
                }
            } catch (error) {
                throw error
            }
        },

        // Convenience method to fetch all GPS data
        async fetchAllGpsData() {
            try {
                await Promise.all([
                    this.fetchGpsConfigSources(),
                    this.fetchGpsSourceEndpoints()
                ])
            } catch (error) {
                console.error('Error fetching GPS data:', error)
                throw error
            }
        },

        // Batch operations
        async deleteMultipleGpsSources(ids) {
            try {
                const deletePromises = ids.map(id => this.deleteGpsSource(id))
                await Promise.all(deletePromises)
            } catch (error) {
                // Refresh data to ensure consistency after partial failures
                await this.fetchGpsConfigSources()
                throw error
            }
        }
    }
})