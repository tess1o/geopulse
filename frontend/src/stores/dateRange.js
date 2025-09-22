import { defineStore } from 'pinia'
import { useTimezone } from '@/composables/useTimezone'

const timezone = useTimezone()

export const useDateRangeStore = defineStore('dateRange', {
    state: () => ({
        dateRange: null // [startDateString, endDateString] or null
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
            return start && end && timezone.isBefore(start, end) || timezone.isSameDay(start, end);
        },

        // Formatted getters
        formattedRange: (state) => {
            if (!state.dateRange || state.dateRange.length !== 2) return ''
            const [start, end] = state.dateRange
            if (!start || !end) return ''

            return `${timezone.formatDateUS(start)} - ${timezone.formatDateUS(end)}`
        },

        // Duration getter
        rangeDurationDays: (state) => {
            if (!state.dateRange || state.dateRange.length !== 2) return 0
            const [start, end] = state.dateRange
            if (!start || !end) return 0

            return timezone.diffInDays(end, start) + 1;
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

            let [start, end] = range
            if (!start || !end) {
                this.dateRange = null
                return
            }

            // Ensure they are ISO strings
            start = timezone.isValidDate(start) ? start : timezone.toUtc(start).toISOString();
            end = timezone.isValidDate(end) ? end : timezone.toUtc(end).toISOString();

            // Ensure start is before end
            if (timezone.isAfter(start, end)) {
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
                this.dateRange = [timezone.toUtc(startDate).toISOString(), null]
            }
        },

        // Update just the end date
        setEndDate(endDate) {
            if (!endDate) return

            const currentStart = this.startDate
            if (currentStart) {
                this.setDateRange([currentStart, endDate])
            } else {
                this.dateRange = [null, timezone.toUtc(endDate).toISOString()]
            }
        },

        // Clear the date range
        clearDateRange() {
            this.dateRange = null
        },

        // Set to today
        setToday() {
            const { start, end } = timezone.getTodayRangeUtc()
            this.setDateRange([start, end])
        },

        // Set to last N days
        setLastDays(days) {
            const nowObj = timezone.now()
            const start = timezone.subtract(nowObj, days - 1, 'day').startOf('day').utc().toISOString()
            const end = nowObj.endOf('day').utc().toISOString()
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
            const start = timezone.startOfWeekUtc(timezone.now())
            const end = timezone.endOfWeekUtc(timezone.now())
            this.setDateRange([start, end])
        },

        // Set to current calendar month
        setCurrentMonth() {
            const start = timezone.startOfMonthUtc(timezone.now())
            const end = timezone.endOfMonthUtc(timezone.now())
            this.setDateRange([start, end])
        }
    }
})