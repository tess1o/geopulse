import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'
import { useDateRangeStore } from '@/stores/dateRange'
import { normalizeApiError } from '@/utils/apiErrorDetail'

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
          this.memosConfig = await apiService.get('/integrations/memos') || null
          return this.memosConfig
        } catch (error) {
          this.configError = normalizeApiError(error, 'Failed to load Memos configuration')
          this.memosConfig = null
          throw this.configError
        } finally {
          this.configLoading = false
          inFlightConfigRequest = null
        }
      })()

      return inFlightConfigRequest
    },

    async updateMemosConfig(configData) {
      this.configError = null
      try {
        await apiService.put('/integrations/memos', configData)
        await this.fetchMemosConfig(true)
        this.clearNotes()
      } catch (error) {
        this.configError = normalizeApiError(error, 'Failed to update Memos configuration')
        throw this.configError
      }
    },

    async testMemosConfig(configData) {
      try {
        return await apiService.post('/integrations/memos/connection-tests', configData)
      } catch (error) {
        this.configError = normalizeApiError(error, 'Failed to test Memos connection')
        throw this.configError
      }
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
            from: normalizedStart,
            to: normalizedEnd,
            includeExternal
          })
          const payload = response
          this.notes = payload?.notes || []
          this.lastFetchedRange = [normalizedStart, normalizedEnd, includeExternal]
          return this.notes
        } catch (error) {
          this.notesError = normalizeApiError(error, 'Failed to load notes')
          this.notes = []
          throw this.notesError
        } finally {
          this.notesLoading = false
          inFlightNotesRequest = null
          inFlightNotesRequestKey = null
        }
      })()

      return inFlightNotesRequest
    },

    async fetchSharedNotes(linkId, accessToken, startTime = null, endTime = null) {
      let url = `/public/share-links/${linkId}/notes`
      if (startTime && endTime) {
        const params = new URLSearchParams({ from: startTime, to: endTime })
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
      try {
        const response = await apiService.post('/notes', payload)
        this.clearNotes()
        return response
      } catch (error) {
        this.notesError = normalizeApiError(error, 'Failed to create note')
        throw this.notesError
      }
    },

    async updateNote(noteId, payload) {
      try {
        const response = await apiService.patch(`/notes/${noteId}`, payload)
        this.clearNotes()
        return response
      } catch (error) {
        this.notesError = normalizeApiError(error, 'Failed to update note')
        throw this.notesError
      }
    },

    async deleteNote(noteId) {
      try {
        await apiService.delete(`/notes/${noteId}`)
        this.clearNotes()
      } catch (error) {
        this.notesError = normalizeApiError(error, 'Failed to delete note')
        throw this.notesError
      }
    },

    async searchNotes(params) {
      try {
        return await apiService.get('/notes/search', params)
      } catch (error) {
        this.notesError = normalizeApiError(error, 'Failed to load notes')
        throw this.notesError
      }
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
