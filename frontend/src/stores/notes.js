import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'
import { useDateRangeStore } from '@/stores/dateRange'

let inFlightConfigRequest = null
let inFlightNotesRequest = null
let inFlightNotesRequestKey = null

const normalizeDateParam = (value) => {
  if (!value) return null
  if (typeof value === 'string') return value
  if (value instanceof Date) return value.toISOString()
  if (typeof value?.toISOString === 'function') {
    try {
      return value.toISOString()
    } catch {
      // fall through
    }
  }
  return String(value)
}

const unwrapApiData = (response) => {
  if (response && typeof response === 'object' && Object.prototype.hasOwnProperty.call(response, 'data')) {
    return response.data
  }
  return response
}

export const useNotesStore = defineStore('notes', {
  state: () => ({
    memosConfig: null,
    configLoading: false,
    configError: null,
    notes: [],
    notesLoading: false,
    notesError: null,
    lastFetchedRange: null
  }),

  getters: {
    isMemosConfigured: (state) => !!(state.memosConfig && state.memosConfig.enabled),
    hasMemosConfig: (state) => !!state.memosConfig,
    hasNotes: (state) => Array.isArray(state.notes) && state.notes.length > 0,
    defaultSaveDestination: (state) => state.memosConfig?.defaultSaveDestination || 'GEOPULSE',
    defaultVisibility: (state) => state.memosConfig?.defaultVisibility || 'PRIVATE'
  },

  actions: {
    async fetchMemosConfig(force = false) {
      if (!force && this.hasMemosConfig && !this.configError) {
        return this.memosConfig
      }
      if (!force && inFlightConfigRequest) {
        return inFlightConfigRequest
      }

      this.configLoading = true
      this.configError = null

      inFlightConfigRequest = (async () => {
        try {
          const response = await apiService.get('/users/me/memos-config')
          this.memosConfig = unwrapApiData(response)
          return this.memosConfig
        } catch (error) {
          this.configError = error.userMessage || error.message || 'Failed to load Memos configuration'
          this.memosConfig = null
          throw error
        } finally {
          this.configLoading = false
          inFlightConfigRequest = null
        }
      })()

      return inFlightConfigRequest
    },

    async updateMemosConfig(configData) {
      const response = await apiService.put('/users/me/memos-config', configData)
      await this.fetchMemosConfig(true)
      this.clearNotes()
      return unwrapApiData(response)
    },

    async testMemosConfig(configData) {
      const response = await apiService.post('/users/me/memos-config/test', configData)
      return unwrapApiData(response)
    },

    async fetchNotes(startTime = null, endTime = null, { includeExternal = true, forceRefresh = false } = {}) {
      if (!startTime || !endTime) {
        const dateRangeStore = useDateRangeStore()
        const dateRange = dateRangeStore.getCurrentDateRange
        if (!dateRange || dateRange.length !== 2) {
          return []
        }
        ;[startTime, endTime] = dateRange
      }

      const normalizedStart = normalizeDateParam(startTime)
      const normalizedEnd = normalizeDateParam(endTime)
      if (!normalizedStart || !normalizedEnd) {
        return []
      }

      const requestKey = `${normalizedStart}|${normalizedEnd}|${includeExternal}`
      const hasSameRangeCache = Array.isArray(this.lastFetchedRange) &&
        this.lastFetchedRange.length === 3 &&
        this.lastFetchedRange[0] === normalizedStart &&
        this.lastFetchedRange[1] === normalizedEnd &&
        this.lastFetchedRange[2] === includeExternal

      if (!forceRefresh && hasSameRangeCache) {
        return this.notes
      }
      if (!forceRefresh && inFlightNotesRequest && inFlightNotesRequestKey === requestKey) {
        return inFlightNotesRequest
      }

      this.notesLoading = true
      this.notesError = null
      inFlightNotesRequestKey = requestKey
      inFlightNotesRequest = (async () => {
        try {
          const response = await apiService.get('/notes/search', {
            startTime: normalizedStart,
            endTime: normalizedEnd,
            includeExternal
          })
          const payload = unwrapApiData(response)
          this.notes = payload?.notes || []
          this.lastFetchedRange = [normalizedStart, normalizedEnd, includeExternal]
          return this.notes
        } catch (error) {
          this.notesError = error.userMessage || error.message || 'Failed to load notes'
          this.notes = []
          throw error
        } finally {
          this.notesLoading = false
          inFlightNotesRequest = null
          inFlightNotesRequestKey = null
        }
      })()

      return inFlightNotesRequest
    },

    async fetchSharedNotes(linkId, accessToken, startTime = null, endTime = null) {
      let url = `/shared/${linkId}/notes`
      if (startTime && endTime) {
        const params = new URLSearchParams({ startTime, endTime })
        url += `?${params.toString()}`
      }

      const response = await apiService.getWithCustomHeaders(url, {
        Authorization: `Bearer ${accessToken}`
      })
      const payload = unwrapApiData(response)
      this.notes = payload?.notes || []
      return this.notes
    },

    async createNote(payload) {
      const response = await apiService.post('/notes', payload)
      this.clearNotes()
      return unwrapApiData(response)
    },

    async updateNote(noteId, payload) {
      const response = await apiService.patch(`/notes/${noteId}`, payload)
      this.clearNotes()
      return unwrapApiData(response)
    },

    async deleteNote(noteId) {
      const response = await apiService.delete(`/notes/${noteId}`)
      this.clearNotes()
      return response
    },

    clearNotes() {
      this.notes = []
      this.lastFetchedRange = null
      this.notesError = null
    },

    clearErrors() {
      this.configError = null
      this.notesError = null
    }
  }
})
