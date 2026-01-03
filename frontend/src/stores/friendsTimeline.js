import { defineStore } from 'pinia'
import apiService from '../utils/apiService'
import { useTimezone } from '@/composables/useTimezone'

const timezone = useTimezone()

export const useFriendsTimelineStore = defineStore('friendsTimeline', {
    state: () => ({
        multiUserTimeline: null, // Full response from API
        selectedUserIds: new Set(), // UUIDs of users to display
        userColorMap: new Map(), // userId -> color mapping
        isLoading: false,
        error: null,
        dateRange: { start: null, end: null }
    }),

    getters: {
        // Get all available users (requesting user + friends with permission)
        availableUsers: (state) => {
            if (!state.multiUserTimeline || !state.multiUserTimeline.timelines) {
                return []
            }
            return state.multiUserTimeline.timelines.map(userTimeline => ({
                userId: userTimeline.userId,
                fullName: userTimeline.fullName,
                email: userTimeline.email,
                avatar: userTimeline.avatar,
                color: userTimeline.assignedColor,
                stats: userTimeline.stats,
                itemCount: (userTimeline.stats?.totalStays || 0) +
                          (userTimeline.stats?.totalTrips || 0) +
                          (userTimeline.stats?.totalDataGaps || 0)
            }))
        },

        // Get timelines for selected users only
        visibleTimelines: (state) => {
            if (!state.multiUserTimeline || !state.multiUserTimeline.timelines) {
                return []
            }

            return state.multiUserTimeline.timelines.filter(userTimeline =>
                state.selectedUserIds.has(userTimeline.userId)
            )
        },

        // Get merged timeline items from all selected users
        mergedTimelineItems: (state) => {
            const visibleTimelines = state.multiUserTimeline?.timelines?.filter(userTimeline =>
                state.selectedUserIds.has(userTimeline.userId)
            ) || []

            const allItems = []

            visibleTimelines.forEach(userTimeline => {
                const userId = userTimeline.userId
                const color = userTimeline.assignedColor
                const fullName = userTimeline.fullName
                const avatar = userTimeline.avatar

                // Add stays
                if (userTimeline.timeline?.stays) {
                    userTimeline.timeline.stays.forEach(stay => {
                        allItems.push({
                            ...stay,
                            type: 'stay',
                            userId,
                            userColor: color,
                            userFullName: fullName,
                            userAvatar: avatar
                        })
                    })
                }

                // Add trips
                if (userTimeline.timeline?.trips) {
                    userTimeline.timeline.trips.forEach(trip => {
                        allItems.push({
                            ...trip,
                            type: 'trip',
                            userId,
                            userColor: color,
                            userFullName: fullName,
                            userAvatar: avatar
                        })
                    })
                }

                // Add data gaps
                if (userTimeline.timeline?.dataGaps) {
                    userTimeline.timeline.dataGaps.forEach(dataGap => {
                        allItems.push({
                            ...dataGap,
                            type: 'dataGap',
                            timestamp: dataGap.startTime,
                            userId,
                            userColor: color,
                            userFullName: fullName,
                            userAvatar: avatar
                        })
                    })
                }
            })

            // Sort by timestamp descending (most recent first)
            return allItems.sort((a, b) =>
                timezone.fromUtc(b.timestamp).valueOf() - timezone.fromUtc(a.timestamp).valueOf()
            )
        },

        // Check if any users are selected
        hasSelectedUsers: (state) => {
            return state.selectedUserIds.size > 0
        },

        // Get requesting user ID
        requestingUserId: (state) => {
            return state.multiUserTimeline?.requestingUserId
        },

        // Get stats for all selected users combined
        combinedStats: (state) => {
            const visibleTimelines = state.multiUserTimeline?.timelines?.filter(userTimeline =>
                state.selectedUserIds.has(userTimeline.userId)
            ) || []

            return visibleTimelines.reduce((acc, userTimeline) => {
                const stats = userTimeline.stats || {}
                return {
                    totalStays: (acc.totalStays || 0) + (stats.totalStays || 0),
                    totalTrips: (acc.totalTrips || 0) + (stats.totalTrips || 0),
                    totalDataGaps: (acc.totalDataGaps || 0) + (stats.totalDataGaps || 0),
                    totalDistanceMeters: (acc.totalDistanceMeters || 0) + (stats.totalDistanceMeters || 0),
                    totalTravelTimeSeconds: (acc.totalTravelTimeSeconds || 0) + (stats.totalTravelTimeSeconds || 0)
                }
            }, {})
        }
    },

    actions: {
        /**
         * Fetch multi-user timeline from API
         */
        async fetchMultiUserTimeline(startTime, endTime, userIds = null) {
            this.isLoading = true
            this.error = null

            try {
                const response = await apiService.getMultiUserTimeline(startTime, endTime, userIds)

                this.multiUserTimeline = response.data
                this.dateRange = { start: startTime, end: endTime }

                // Initialize color map
                this.userColorMap.clear()
                if (response.data.timelines) {
                    response.data.timelines.forEach(userTimeline => {
                        this.userColorMap.set(userTimeline.userId, userTimeline.assignedColor)
                    })
                }

                // Auto-select all available users by default
                this.selectAllUsers()

                return response.data

            } catch (error) {
                this.error = error.message || 'Failed to fetch multi-user timeline'
                console.error('Error fetching multi-user timeline:', error)
                throw error
            } finally {
                this.isLoading = false
            }
        },

        /**
         * Toggle selection of a specific user
         */
        toggleUserSelection(userId) {
            if (this.selectedUserIds.has(userId)) {
                this.selectedUserIds.delete(userId)
            } else {
                this.selectedUserIds.add(userId)
            }
        },

        /**
         * Select all available users
         */
        selectAllUsers() {
            if (this.multiUserTimeline && this.multiUserTimeline.timelines) {
                this.selectedUserIds = new Set(
                    this.multiUserTimeline.timelines.map(u => u.userId)
                )
            }
        },

        /**
         * Deselect all users except the requesting user
         */
        deselectAllUsers() {
            if (this.requestingUserId) {
                this.selectedUserIds = new Set([this.requestingUserId])
            } else {
                this.selectedUserIds.clear()
            }
        },

        /**
         * Clear all data
         */
        clearData() {
            this.multiUserTimeline = null
            this.selectedUserIds.clear()
            this.userColorMap.clear()
            this.error = null
        },

        /**
         * Get color for a specific user
         */
        getUserColor(userId) {
            return this.userColorMap.get(userId) || '#607D8B'
        },

        /**
         * Check if a user is selected
         */
        isUserSelected(userId) {
            return this.selectedUserIds.has(userId)
        }
    }
})
