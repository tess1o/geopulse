import { defineStore } from 'pinia'

export const useDateRangeStore = defineStore('dateRange', {
    state: () => ({
        dateRange: null // [startDate, endDate] or null
    }),

    getters: {
        // Direct access getter
        getCurrentDateRange: (state) => state.dateRange,

        // Convenience getters
        startDate: (state) => state.dateRange?.[0] || null,
        endDate: (state) => state.dateRange?.[1] || null,

        // Validation getters
        hasDateRange: (state) => !!state.dateRange && state.dateRange.length === 2,
        isValidRange: (state) => {
            if (!state.dateRange || state.dateRange.length !== 2) return false
            const [start, end] = state.dateRange
            return start && end && start <= end
        },

        // Formatted getters
        formattedRange: (state) => {
            if (!state.dateRange || state.dateRange.length !== 2) return ''
            const [start, end] = state.dateRange
            if (!start || !end) return ''

            return `${start.toLocaleDateString('en-US', {
                month: '2-digit',
                day: '2-digit',
                year: 'numeric'
            })} - ${end.toLocaleDateString('en-US', {
                month: '2-digit',
                day: '2-digit',
                year: 'numeric'
            })}`
        },

        // Duration getter
        rangeDurationDays: (state) => {
            if (!state.dateRange || state.dateRange.length !== 2) return 0
            const [start, end] = state.dateRange
            if (!start || !end) return 0

            const diffTime = Math.abs(end - start)
            return Math.ceil(diffTime / (1000 * 60 * 60 * 24))
        }
    },

    actions: {
        // Set date range
        setDateRange(range) {
            // Validate input
            if (!range || !Array.isArray(range) || range.length !== 2) {
                this.dateRange = null
                return
            }

            const [start, end] = range
            if (!start || !end) {
                this.dateRange = null
                return
            }

            // Ensure start is before end
            if (start > end) {
                this.dateRange = [end, start]
            } else {
                this.dateRange = [start, end]
            }
        },

        // Update just the start date
        setStartDate(startDate) {
            if (!startDate) return

            const currentEnd = this.endDate
            if (currentEnd) {
                this.setDateRange([startDate, currentEnd])
            } else {
                this.dateRange = [startDate, null]
            }
        },

        // Update just the end date
        setEndDate(endDate) {
            if (!endDate) return

            const currentStart = this.startDate
            if (currentStart) {
                this.setDateRange([currentStart, endDate])
            } else {
                this.dateRange = [null, endDate]
            }
        },

        // Clear the date range
        clearDateRange() {
            this.dateRange = null
        },

        // Set to today
        setToday() {
            const today = new Date()
            today.setHours(0, 0, 0, 0)
            this.setDateRange([today, today])
        },

        // Set to last N days
        setLastDays(days) {
            const end = new Date()
            end.setHours(23, 59, 59, 999)

            const start = new Date()
            start.setDate(start.getDate() - days + 1)
            start.setHours(0, 0, 0, 0)

            this.setDateRange([start, end])
        },

        // Set to last week (7 days)
        setLastWeek() {
            this.setLastDays(7)
        },

        // Set to last month (30 days)
        setLastMonth() {
            this.setLastDays(30)
        },

        // Set to current calendar week
        setCurrentWeek() {
            const today = new Date()
            const dayOfWeek = today.getDay()
            const diff = today.getDate() - dayOfWeek + (dayOfWeek === 0 ? -6 : 1) // Adjust when day is Sunday

            const start = new Date(today.setDate(diff))
            start.setHours(0, 0, 0, 0)

            const end = new Date(start)
            end.setDate(start.getDate() + 6)
            end.setHours(23, 59, 59, 999)

            this.setDateRange([start, end])
        },

        // Set to current calendar month
        setCurrentMonth() {
            const today = new Date()
            const start = new Date(today.getFullYear(), today.getMonth(), 1)
            start.setHours(0, 0, 0, 0)

            const end = new Date(today.getFullYear(), today.getMonth() + 1, 0)
            end.setHours(23, 59, 59, 999)

            this.setDateRange([start, end])
        }
    }
})