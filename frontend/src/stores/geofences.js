import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'
import { normalizeApiError } from '@/utils/apiErrorDetail'

export const useGeofencesStore = defineStore('geofences', {
  state: () => ({
    rules: [],
    templates: [],
    capabilities: { appriseEnabled: false, appriseConfigured: false },
    events: [],
    eventsTotal: 0,
    unreadCount: 0,
    error: null
  }),

  actions: {
    fail(error, fallback) {
      this.error = normalizeApiError(error, fallback)
      return this.error
    },

    async loadRules() {
      try {
        this.rules = await apiService.get('/geofences/rules') || []
        return this.rules
      } catch (error) {
        throw this.fail(error, 'Failed to load geofence rules')
      }
    },

    async createRule(payload) {
      try {
        const created = await apiService.post('/geofences/rules', payload)
        this.rules.push(created)
        return created
      } catch (error) {
        throw this.fail(error, 'Failed to create geofence rule')
      }
    },

    async updateRule(ruleId, payload) {
      try {
        const updated = await apiService.patch(`/geofences/rules/${ruleId}`, payload)
        this.rules = this.rules.map(rule => rule.id === updated.id ? updated : rule)
        return updated
      } catch (error) {
        throw this.fail(error, 'Failed to update geofence rule')
      }
    },

    async deleteRule(ruleId) {
      try {
        await apiService.delete(`/geofences/rules/${ruleId}`)
        this.rules = this.rules.filter(rule => rule.id !== ruleId)
      } catch (error) {
        throw this.fail(error, 'Failed to delete geofence rule')
      }
    },

    async loadTemplates() {
      try {
        this.templates = await apiService.get('/geofences/templates') || []
        return this.templates
      } catch (error) {
        throw this.fail(error, 'Failed to load notification templates')
      }
    },

    async loadCapabilities() {
      try {
        this.capabilities = await apiService.get('/geofences/templates/capabilities') || {
          appriseEnabled: false,
          appriseConfigured: false
        }
        return this.capabilities
      } catch (error) {
        throw this.fail(error, 'Failed to load notification capabilities')
      }
    },

    async testTemplateConnection(payload) {
      try {
        return await apiService.post('/geofences/templates/test-connection', payload)
      } catch (error) {
        throw this.fail(error, 'Connection test failed')
      }
    },

    async createTemplate(payload) {
      try {
        const created = await apiService.post('/geofences/templates', payload)
        this.templates.push(created)
        return created
      } catch (error) {
        throw this.fail(error, 'Failed to create notification template')
      }
    },

    async updateTemplate(templateId, payload) {
      try {
        const updated = await apiService.patch(`/geofences/templates/${templateId}`, payload)
        this.templates = this.templates.map(template => template.id === updated.id ? updated : template)
        return updated
      } catch (error) {
        throw this.fail(error, 'Failed to update notification template')
      }
    },

    async deleteTemplate(templateId) {
      try {
        await apiService.delete(`/geofences/templates/${templateId}`)
        this.templates = this.templates.filter(template => template.id !== templateId)
      } catch (error) {
        throw this.fail(error, 'Failed to delete notification template')
      }
    },

    async loadEvents(params) {
      try {
        const [page, unread] = await Promise.all([
          apiService.get('/geofences/events', params),
          apiService.get('/geofences/events/unread-count')
        ])
        this.events = page?.items || []
        this.eventsTotal = Number(page?.totalElements || 0)
        this.unreadCount = Number(unread?.count || 0)
        return page
      } catch (error) {
        throw this.fail(error, 'Failed to load geofence events')
      }
    },

    async markEventSeen(eventId) {
      try {
        const updated = await apiService.post(`/geofences/events/${eventId}/seen`, {})
        this.events = this.events.map(event => event.id === updated.id ? updated : event)
        this.unreadCount = Math.max(0, this.unreadCount - 1)
        return updated
      } catch (error) {
        throw this.fail(error, 'Failed to mark event as seen')
      }
    },

    async markAllEventsSeen() {
      try {
        const result = await apiService.post('/geofences/events/seen-all', {})
        this.events = this.events.map(event => ({ ...event, seen: true }))
        this.unreadCount = 0
        return result
      } catch (error) {
        throw this.fail(error, 'Failed to mark events as seen')
      }
    }
  }
})
