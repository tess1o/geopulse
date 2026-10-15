import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'
import { normalizeApiError } from '@/utils/apiErrorDetail'

export const useGpsSourcesStore = defineStore('gpsSources', {
  state: () => ({
    gpsSourceConfigs: [],
    defaultFilteringValues: null,
    ownTracksMqttConfig: null,
    telemetryMappingsByType: {},
    error: null
  }),

  actions: {
    fail(error, fallback) {
      this.error = normalizeApiError(error, fallback)
      return this.error
    },

    clearGpsData() {
      this.gpsSourceConfigs = []
      this.ownTracksMqttConfig = null
      this.telemetryMappingsByType = {}
      this.error = null
    },

    async fetchGpsConfigSources() {
      try {
        this.gpsSourceConfigs = await apiService.get('/gps/sources') || []
        return this.gpsSourceConfigs
      } catch (error) {
        throw this.fail(error, 'Failed to load GPS sources')
      }
    },

    async fetchDefaultFilteringValues() {
      try {
        this.defaultFilteringValues = await apiService.get('/gps/sources/defaults')
        return this.defaultFilteringValues
      } catch (error) {
        throw this.fail(error, 'Failed to load GPS filtering defaults')
      }
    },

    async fetchOwnTracksMqttConfig() {
      try {
        this.ownTracksMqttConfig = await apiService.get('/gps/sources/owntracks/mqtt-config')
        return this.ownTracksMqttConfig
      } catch (error) {
        throw this.fail(error, 'Failed to load OwnTracks MQTT configuration')
      }
    },

    async addGpsConfigSource(type, username, password, token, connectionType = 'HTTP',
                             filterInaccurateData, maxAllowedAccuracy, maxAllowedSpeed,
                             enableDuplicateDetection, duplicateDetectionThresholdMinutes, deviceId = null,
                             payloadEncryptionSecret = null) {
      try {
        const created = await apiService.post('/gps/sources', {
          type,
          username,
          password,
          token,
          deviceId,
          payloadEncryptionSecret,
          connectionType,
          filterInaccurateData,
          maxAllowedAccuracy,
          maxAllowedSpeed,
          enableDuplicateDetection,
          duplicateDetectionThresholdMinutes
        })
        this.gpsSourceConfigs.push(created)
        return created
      } catch (error) {
        throw this.fail(error, 'Failed to add GPS source')
      }
    },

    /**
     * Home Assistant source for the GeoPulse integration, connected to a personal API token: either an existing one
     * (`apiTokenId`) or a new one (`newApiToken: { name }`). The response carries a new token's secret once, in
     * `apiTokenSecret`.
     */
    async addHomeAssistantIntegrationSource({ apiTokenId = null, newApiToken = null, filters = {} }) {
      try {
        const created = await apiService.post('/gps/sources', {
          type: 'HOME_ASSISTANT',
          connectionType: 'HTTP',
          apiTokenId,
          newApiToken,
          ...filters
        })
        const { apiTokenSecret, ...source } = created || {}
        this.gpsSourceConfigs.push(source)
        return created
      } catch (error) {
        throw this.fail(error, 'Failed to add GPS source')
      }
    },

    async deleteGpsSource(id, { revokeApiToken = false } = {}) {
      const originalConfigs = [...this.gpsSourceConfigs]
      this.gpsSourceConfigs = this.gpsSourceConfigs.filter(config => config.id !== id)
      try {
        await apiService.delete(revokeApiToken ? `/gps/sources/${id}?revokeApiToken=true` : `/gps/sources/${id}`)
      } catch (error) {
        this.gpsSourceConfigs = originalConfigs
        throw this.fail(error, 'Failed to delete GPS source')
      }
    },

    async updateGpsSource(config) {
      const originalConfigs = [...this.gpsSourceConfigs]
      this.gpsSourceConfigs = this.gpsSourceConfigs.map(current =>
        current.id === config.id ? { ...current, ...config } : current)
      try {
        await apiService.put(`/gps/sources/${config.id}`, config)
      } catch (error) {
        this.gpsSourceConfigs = originalConfigs
        throw this.fail(error, 'Failed to update GPS source')
      }
    },

    async updateGpsSourceStatus(id, status) {
      const originalConfigs = [...this.gpsSourceConfigs]
      this.gpsSourceConfigs = this.gpsSourceConfigs.map(config =>
        config.id === id ? { ...config, active: status } : config)
      try {
        await apiService.patch(`/gps/sources/${id}/status`, { status })
      } catch (error) {
        this.gpsSourceConfigs = originalConfigs
        throw this.fail(error, 'Failed to update GPS source status')
      }
    },

    async fetchAllGpsData() {
      return this.fetchGpsConfigSources()
    },

    async deleteMultipleGpsSources(ids) {
      await Promise.all(ids.map(id => this.deleteGpsSource(id)))
    },

    async fetchTelemetryMapping(sourceType) {
      try {
        const response = await apiService.get(`/gps/sources/telemetry/${sourceType}`)
        this.telemetryMappingsByType = { ...this.telemetryMappingsByType, [sourceType]: response }
        return response
      } catch (error) {
        throw this.fail(error, 'Failed to load telemetry mapping')
      }
    },

    async updateTelemetryMapping(sourceType, mapping) {
      try {
        const response = await apiService.put(`/gps/sources/telemetry/${sourceType}`, mapping)
        this.telemetryMappingsByType = { ...this.telemetryMappingsByType, [sourceType]: response }
        return response
      } catch (error) {
        throw this.fail(error, 'Failed to update telemetry mapping')
      }
    },

    async resetTelemetryMapping(sourceType) {
      try {
        await apiService.delete(`/gps/sources/telemetry/${sourceType}`)
        return await this.fetchTelemetryMapping(sourceType)
      } catch (error) {
        throw this.fail(error, 'Failed to reset telemetry mapping')
      }
    }
  }
})
