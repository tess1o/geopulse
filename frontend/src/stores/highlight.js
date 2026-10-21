import { defineStore } from 'pinia'
import { isOutsideFocusedDay } from '@/maps/shared/timelineDayFocus'

export const useHighlightStore = defineStore('highlight', {
    state: () => ({
        highlightedTrip: null,
        highlightedStayPoint: null,
        // 'YYYY-MM-DD' of the day shown on its own on the map (multi-day ranges); null shows every day.
        focusedDay: null
    }),

    getters: {
        // Direct access getters
        getHighlightedTrip: (state) => state.highlightedTrip,
        getHighlightedStayPoint: (state) => state.highlightedStayPoint,

        // Check if anything is highlighted
        hasHighlightedTrip: (state) => !!state.highlightedTrip,
        hasHighlightedStayPoint: (state) => !!state.highlightedStayPoint,
        hasAnyHighlight: (state) => !!state.highlightedTrip || !!state.highlightedStayPoint,

        // Get the currently highlighted item (regardless of type)
        getCurrentHighlight: (state) => {
            return state.highlightedTrip || state.highlightedStayPoint
        },

        // Get the type of currently highlighted item
        getHighlightType: (state) => {
            if (state.highlightedTrip) return 'trip'
            if (state.highlightedStayPoint) return 'stay'
            return null
        },

        // Check if a specific item is highlighted
        isItemHighlighted: (state) => (item) => {
            if (!item) return false

            if (item.type === 'trip') {
                return state.highlightedTrip?.timestamp === item.timestamp
            } else if (item.type === 'stay') {
                return state.highlightedStayPoint?.timestamp === item.timestamp
            }

            return false
        }
    },

    actions: {
        // Trip highlighting actions
        setHighlightedTrip(trip) {
            // Clear stay point when highlighting a trip
            this.highlightedStayPoint = null
            this.highlightedTrip = trip
            this.clearFocusedDayUnlessOn(trip)
        },

        clearHighlightedTrip() {
            this.highlightedTrip = null
        },

        // Stay point highlighting actions
        setHighlightedStayPoint(stayPoint) {
            // Clear trip when highlighting a stay point
            this.highlightedTrip = null
            this.highlightedStayPoint = stayPoint
            this.clearFocusedDayUnlessOn(stayPoint)
        },

        clearHighlightedStayPoint() {
            this.highlightedStayPoint = null
        },

        // Combined actions
        setHighlightedItem(item) {
            if (!item) {
                this.clearAllHighlights()
                return
            }

            if (item.type === 'trip') {
                this.setHighlightedTrip(item)
            } else if (item.type === 'stay') {
                this.setHighlightedStayPoint(item)
            }
        },

        clearAllHighlights() {
            this.highlightedTrip = null
            this.highlightedStayPoint = null
        },

        // Day focus: selecting an item from another day ends it; focusing a day drops a selection from another day.
        focusDay(dayKey) {
            this.focusedDay = dayKey || null
            const current = this.highlightedTrip || this.highlightedStayPoint
            if (current && isOutsideFocusedDay([current], this.focusedDay)) {
                this.clearAllHighlights()
            }
        },

        clearFocusedDay() {
            this.focusedDay = null
        },

        toggleFocusedDay(dayKey) {
            if (this.focusedDay === dayKey) {
                this.clearFocusedDay()
            } else {
                this.focusDay(dayKey)
            }
        },

        clearFocusedDayUnlessOn(item) {
            if (item && isOutsideFocusedDay([item], this.focusedDay)) {
                this.focusedDay = null
            }
        },

        // Toggle highlight for an item
        toggleHighlight(item) {
            if (this.isItemHighlighted(item)) {
                this.clearAllHighlights()
            } else {
                this.setHighlightedItem(item)
            }
        }
    }
})