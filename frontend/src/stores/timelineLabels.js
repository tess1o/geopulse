import { defineStore } from 'pinia'
import apiService from '../utils/apiService'
import { normalizeApiError } from '@/utils/apiErrorDetail'

export const useTimelineLabelsStore = defineStore('timelineLabels', {
    state: () => ({
        timelineLabels: [],
        activeLabel: null,
        isLoading: false,
        error: null,
        filters: {
            searchTerm: null,
            source: null
        }
    }),

    getters: {
        // Count
        totalCount: (state) => state.timelineLabels.length,

        // Get active label
        getActiveLabel: (state) => state.activeLabel,

        // Check if label is editable (only manual labels or completed OwnTracks labels)
        isLabelEditable: (state) => (label) => {
            return label.source === 'manual' || !label.source || !label.isActive
        },

        // Get filtered timeline labels
        getFilteredTimelineLabels: (state) => {
            let filtered = state.timelineLabels

            // Filter by source
            if (state.filters.source) {
                filtered = filtered.filter(label =>
                    label.source === state.filters.source || (!label.source && state.filters.source === 'manual')
                )
            }

            // Filter by search term
            if (state.filters.searchTerm) {
                const term = state.filters.searchTerm.toLowerCase()
                filtered = filtered.filter(label =>
                    label.name.toLowerCase().includes(term)
                )
            }

            return filtered
        },

        // Get total days labelled
        getTotalDaysLabeled: (state) => {
            return state.timelineLabels.reduce((total, label) => {
                const start = new Date(label.startTime)
                // For active labels, use current time
                const end = label.endTime ? new Date(label.endTime) : new Date()
                const days = Math.ceil((end - start) / (1000 * 60 * 60 * 24))
                return total + days
            }, 0)
        },

        // Check if a date falls within any label range
        getTimelineLabelsForDate: (state) => (date) => {
            const dateTime = new Date(date).getTime()
            return state.timelineLabels.filter(label => {
                const labelStart = new Date(label.startTime).getTime()
                // For active labels, use current time
                const labelEnd = label.endTime ? new Date(label.endTime).getTime() : Date.now()
                return dateTime >= labelStart && dateTime <= labelEnd
            })
        }
    },

    actions: {
        fail(error, fallback) {
            this.error = normalizeApiError(error, fallback)
            return this.error
        },

        // Set filters
        setFilters(filters) {
            this.filters = { ...this.filters, ...filters }
        },

        // Fetch active label
        async fetchActiveLabel() {
            try {
                this.activeLabel = await apiService.get('/timeline-labels/active') || null
                this.error = null
                return this.activeLabel
            } catch (error) {
                console.error('Failed to fetch active label:', error)
                this.activeLabel = null
                this.fail(error, 'Failed to load active timeline label')
                return null
            }
        },

        // Fetch all timeline labels
        async fetchTimelineLabels() {
            this.isLoading = true
            this.error = null
            try {
                const response = await apiService.get('/timeline-labels')
                this.timelineLabels = Array.isArray(response) ? response : []
                return this.timelineLabels
            } catch (error) {
                throw this.fail(error, 'Failed to load timeline labels')
            } finally {
                this.isLoading = false
            }
        },

        // Fetch timeline labels for a specific time range
        async fetchTimelineLabelsForTimeRange(startDate, endDate) {
            this.isLoading = true
            this.error = null
            try {
                const response = await apiService.get('/timeline-labels', {
                    from: new Date(startDate).toISOString(),
                    to: new Date(endDate).toISOString()
                })
                this.timelineLabels = Array.isArray(response) ? response : []
                return this.timelineLabels
            } catch (error) {
                throw this.fail(error, 'Failed to load timeline labels')
            } finally {
                this.isLoading = false
            }
        },

        // Check which labels a proposed range would overlap.
        // Mirrors the create/update payload, so it uses startTime/endTime.
        async checkOverlaps(startTime, endTime, excludeId = null) {
            try {
                const params = {
                    startTime,
                    endTime
                }
                if (excludeId) {
                    params.excludeId = excludeId
                }
                const response = await apiService.get('/timeline-labels/check-overlaps', params)
                this.error = null
                return Array.isArray(response) ? response : []
            } catch (error) {
                console.error('Failed to check overlaps:', error)
                this.fail(error, 'Failed to check timeline label overlaps')
                return []
            }
        },

        // Create new timeline label
        async createTimelineLabel(data) {
            this.isLoading = true
            this.error = null
            try {
                const response = await apiService.post('/timeline-labels', data)

                // Refresh the list
                await this.fetchTimelineLabels()

                return response
            } catch (error) {
                throw this.fail(error, 'Failed to create timeline label')
            } finally {
                this.isLoading = false
            }
        },

        // Update timeline label
        async updateTimelineLabel(id, data) {
            this.isLoading = true
            this.error = null
            try {
                const response = await apiService.put(`/timeline-labels/${id}`, data)

                // Refresh the list
                await this.fetchTimelineLabels()

                return response
            } catch (error) {
                throw this.fail(error, 'Failed to update timeline label')
            } finally {
                this.isLoading = false
            }
        },

        // Delete timeline label
        async deleteTimelineLabel(id, mode = 'unlink_only') {
            this.isLoading = true
            this.error = null
            try {
                await apiService.delete(`/timeline-labels/${id}?mode=${encodeURIComponent(mode)}`)

                // Remove from local state
                this.timelineLabels = this.timelineLabels.filter(label => label.id !== id)
            } catch (error) {
                throw this.fail(error, 'Failed to delete timeline label')
            } finally {
                this.isLoading = false
            }
        }
    }
})
