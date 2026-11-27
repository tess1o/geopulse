import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'
import { normalizeApiError } from '@/utils/apiErrorDetail'

export const useCoverageStore = defineStore('coverage', {
  state: () => ({
    cells: Object.freeze([]),
    cellsGrid: 50,
    cellsTruncated: false,
    loadingCells: false,
    cellsError: null,
    cellsRequestSeq: 0,
    summaryByGrid: {},
    summaryLoading: false,
    summaryError: null,
    status: null,
    statusLoading: false,
    statusError: null,
    settingsUpdating: false
  }),

  getters: {
    getCells: (state) => state.cells,
    isCellsLoading: (state) => state.loadingCells,
    getCellsError: (state) => state.cellsError,
    getSummaryByGrid: (state) => (grid) => state.summaryByGrid[grid] || null,
    isSummaryLoading: (state) => state.summaryLoading,
    getSummaryError: (state) => state.summaryError,
    getStatus: (state) => state.status,
    isStatusLoading: (state) => state.statusLoading,
    getStatusError: (state) => state.statusError,
    isSettingsUpdating: (state) => state.settingsUpdating
  },

  actions: {
    clearCells() {
      this.cells = Object.freeze([])
      this.cellsTruncated = false
    },

    cancelCoverageCellsRequests() {
      this.cellsRequestSeq += 1
      this.loadingCells = false
    },

    async fetchCoverageStatus(options = {}) {
      const silent = options?.silent === true

      try {
        if (!silent) {
          this.statusLoading = true
          this.statusError = null
        }

        const data = await apiService.get('/coverage/status')
        this.status = data
        this.statusError = null
        return data
      } catch (error) {
        console.error('Error fetching coverage status:', error)
        if (!silent) {
          this.statusError = normalizeApiError(error, 'Failed to fetch coverage status')
        }
        return silent ? this.status : null
      } finally {
        if (!silent) {
          this.statusLoading = false
        }
      }
    },

    async updateCoverageSettings(enabled) {
      this.settingsUpdating = true
      this.statusError = null
      try {
        const data = await apiService.put('/coverage/settings', { enabled })
        if (data) {
          this.status = data
        }
        return data
      } catch (error) {
        console.error('Error updating coverage settings:', error)
        this.statusError = normalizeApiError(error, 'Failed to update coverage settings')
        throw this.statusError
      } finally {
        this.settingsUpdating = false
      }
    },

    async recalculateCoverage() {
      this.settingsUpdating = true
      this.statusError = null
      try {
        const data = await apiService.post('/coverage/recalculations', {})
        if (data) {
          this.status = data
        }
        return data
      } catch (error) {
        console.error('Error recalculating coverage:', error)
        this.statusError = normalizeApiError(error, 'Failed to recalculate coverage')
        throw this.statusError
      } finally {
        this.settingsUpdating = false
      }
    },

    async fetchCoverageCells(bbox, grid, options = {}) {
      const silent = options?.silent === true
      const requestSeq = ++this.cellsRequestSeq

      try {
        if (!silent) {
          this.loadingCells = true
          this.cellsError = null
        }

        const data = await apiService.get('/coverage/cells', {
          bbox,
          grid
        })
        if (requestSeq !== this.cellsRequestSeq) {
          return null
        }

        if (!Array.isArray(data?.cells) || !Number.isFinite(data.gridMeters) || data.gridMeters <= 0) {
          throw new Error('Invalid coverage cells response')
        }
        if (options.shouldCommit?.(data) !== false) {
          this.cells = Object.freeze(data.cells)
          this.cellsGrid = data.gridMeters
          this.cellsTruncated = data.truncated === true
        }
        return data
      } catch (error) {
        console.error('Error fetching coverage cells:', error)
        if (!silent && requestSeq === this.cellsRequestSeq) {
          this.cellsError = normalizeApiError(error, 'Failed to fetch coverage cells')
        }
        return null
      } finally {
        if (!silent && requestSeq === this.cellsRequestSeq) {
          this.loadingCells = false
        }
      }
    },

    async fetchCoverageSummary(grid, options = {}) {
      if (this.summaryByGrid[grid]) {
        return this.summaryByGrid[grid]
      }

      const silent = options?.silent === true

      try {
        if (!silent) {
          this.summaryLoading = true
          this.summaryError = null
        }

        const data = await apiService.get('/coverage/summary', { grid })
        if (data) {
          this.summaryByGrid[grid] = data
        }
        return data
      } catch (error) {
        console.error('Error fetching coverage summary:', error)
        if (!silent) {
          this.summaryError = normalizeApiError(error, 'Failed to fetch coverage summary')
        }
        return null
      } finally {
        if (!silent) {
          this.summaryLoading = false
        }
      }
    },

    invalidateSummary(grid) {
      if (grid) {
        delete this.summaryByGrid[grid]
        return
      }
      this.summaryByGrid = {}
    }
  }
})
