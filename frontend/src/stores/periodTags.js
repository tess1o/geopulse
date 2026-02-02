import { defineStore } from 'pinia'
import apiService from '../utils/apiService'

export const usePeriodTagsStore = defineStore('periodTags', {
    state: () => ({
        periodTags: [],
        activeTag: null,
        isLoading: false,
        error: null,
        filters: {
            searchTerm: null,
            source: null
        }
    }),

    getters: {
        // Count
        totalCount: (state) => state.periodTags.length,

        // Get active tag
        getActiveTag: (state) => state.activeTag,

        // Check if tag is editable (only manual tags or completed OwnTracks tags)
        isTagEditable: (state) => (tag) => {
            return tag.source === 'manual' || !tag.source || !tag.isActive
        },

        // Get filtered period tags
        getFilteredPeriodTags: (state) => {
            let filtered = state.periodTags

            // Filter by source
            if (state.filters.source) {
                filtered = filtered.filter(tag =>
                    tag.source === state.filters.source || (!tag.source && state.filters.source === 'manual')
                )
            }

            // Filter by search term
            if (state.filters.searchTerm) {
                const term = state.filters.searchTerm.toLowerCase()
                filtered = filtered.filter(tag =>
                    tag.tagName.toLowerCase().includes(term) ||
                    (tag.notes && tag.notes.toLowerCase().includes(term))
                )
            }

            return filtered
        },

        // Get total days tagged
        getTotalDaysTagged: (state) => {
            return state.periodTags.reduce((total, tag) => {
                const start = new Date(tag.startTime)
                // For active tags, use current time
                const end = tag.endTime ? new Date(tag.endTime) : new Date()
                const days = Math.ceil((end - start) / (1000 * 60 * 60 * 24))
                return total + days
            }, 0)
        },

        // Check if a date falls within any period
        getPeriodsForDate: (state) => (date) => {
            const dateTime = new Date(date).getTime()
            return state.periodTags.filter(tag => {
                const tagStart = new Date(tag.startTime).getTime()
                // For active tags, use current time
                const tagEnd = tag.endTime ? new Date(tag.endTime).getTime() : Date.now()
                return dateTime >= tagStart && dateTime <= tagEnd
            })
        }
    },

    actions: {
        // Set filters
        setFilters(filters) {
            this.filters = { ...this.filters, ...filters }
        },

        // Fetch active tag
        async fetchActiveTag() {
            try {
                const response = await apiService.get('/period-tags/active')
                this.activeTag = response.data || null
                return this.activeTag
            } catch (error) {
                console.error('Failed to fetch active tag:', error)
                this.activeTag = null
                return null
            }
        },

        // Fetch all period tags
        async fetchPeriodTags() {
            this.isLoading = true
            this.error = null
            try {
                const response = await apiService.get('/period-tags')
                this.periodTags = response.data || []
                return response.data
            } catch (error) {
                this.error = error.message
                throw error
            } finally {
                this.isLoading = false
            }
        },

        // Fetch period tags for a specific time range
        async fetchPeriodTagsForTimeRange(startDate, endDate) {
            this.isLoading = true
            this.error = null
            try {
                const startDateEpochMillis = new Date(startDate).getTime()
                const endDateEpochMillis = new Date(endDate).getTime()

                const response = await apiService.get('/period-tags', {
                    params: {
                        startDate: startDateEpochMillis,
                        endDate: endDateEpochMillis
                    }
                })
                this.periodTags = response.data || []
                return response.data || []
            } catch (error) {
                this.error = error.message
                throw error
            } finally {
                this.isLoading = false
            }
        },

        // Check for overlapping period tags
        async checkOverlaps(startTime, endTime, excludeId = null) {
            try {
                const params = {
                    startTime: startTime,
                    endTime: endTime
                }
                if (excludeId) {
                    params.excludeId = excludeId
                }
                const response = await apiService.get('/period-tags/check-overlaps', params)
                return response.data || []
            } catch (error) {
                console.error('Failed to check overlaps:', error)
                return []
            }
        },

        // Create new period tag
        async createPeriodTag(data) {
            this.isLoading = true
            this.error = null
            try {
                const response = await apiService.post('/period-tags', data)

                // Refresh the list
                await this.fetchPeriodTags()

                // Return response with overlap info
                return response.data
            } catch (error) {
                this.error = error.message
                throw error
            } finally {
                this.isLoading = false
            }
        },

        // Update period tag
        async updatePeriodTag(id, data) {
            this.isLoading = true
            this.error = null
            try {
                const response = await apiService.put(`/period-tags/${id}`, data)

                // Refresh the list
                await this.fetchPeriodTags()

                return response.data
            } catch (error) {
                this.error = error.message
                throw error
            } finally {
                this.isLoading = false
            }
        },

        // Delete period tag
        async deletePeriodTag(id) {
            this.isLoading = true
            this.error = null
            try {
                await apiService.delete(`/period-tags/${id}`)

                // Remove from local state
                this.periodTags = this.periodTags.filter(tag => tag.id !== id)
            } catch (error) {
                this.error = error.message
                throw error
            } finally {
                this.isLoading = false
            }
        }
    }
})
