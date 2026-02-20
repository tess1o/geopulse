import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'

export const useCoverageStore = defineStore('coverage', {
  state: () => ({
    cells: [],
    loadingCells: false,
    cellsError: null,
    summaryByGrid: {},
    summaryLoading: false,
    summaryError: null
  }),

  getters: {
    getCells: (state) => state.cells,
    isCellsLoading: (state) => state.loadingCells,
    getCellsError: (state) => state.cellsError,
    getSummaryByGrid: (state) => (grid) => state.summaryByGrid[grid] || null,
    isSummaryLoading: (state) => state.summaryLoading,
    getSummaryError: (state) => state.summaryError
  },

  actions: {
    clearCells() {
      this.cells = []
    },

    async fetchCoverageCells(bbox, grid, options = {}) {
      const silent = options?.silent === true

      try {
        if (!silent) {
          this.loadingCells = true
          this.cellsError = null
        }

        const response = await apiService.get('/coverage/cells', {
          bbox,
          grid
        })

        const data = response?.data ?? response ?? []
        this.cells = Array.isArray(data) ? data : []
        return this.cells
      } catch (error) {
        console.error('Error fetching coverage cells:', error)
        if (!silent) {
          this.cellsError = error.message || 'Failed to fetch coverage cells'
        }
        return []
      } finally {
        if (!silent) {
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

        const response = await apiService.get('/coverage/summary', { grid })
        const data = response?.data ?? response ?? null
        if (data) {
          this.summaryByGrid[grid] = data
        }
        return data
      } catch (error) {
        console.error('Error fetching coverage summary:', error)
        if (!silent) {
          this.summaryError = error.message || 'Failed to fetch coverage summary'
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
