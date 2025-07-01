import { defineStore } from 'pinia'

export const useHighlightStore = defineStore('highlight', {
    state: () => ({
        highlightedTrip: null,
        highlightedStayPoint: null
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
        },

        clearHighlightedTrip() {
            this.highlightedTrip = null
        },

        // Stay point highlighting actions
        setHighlightedStayPoint(stayPoint) {
            // Clear trip when highlighting a stay point
            this.highlightedTrip = null
            this.highlightedStayPoint = stayPoint
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