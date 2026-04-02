import apiService from '@/utils/apiService'

const friendsService = {
    /**
     * Get friends list
     * @returns {Promise<Object>} API response envelope
     */
    async getFriends() {
        return apiService.get('/friends')
    },

    /**
     * Get friend permissions for a specific friend
     * @param {string} friendId
     * @returns {Promise<Object>} API response envelope
     */
    async getFriendPermissions(friendId) {
        return apiService.get(`/friends/${friendId}/permissions`)
    },

    /**
     * Update friend timeline permissions
     * @param {string} friendId
     * @param {boolean} shareTimeline
     * @returns {Promise<Object>} API response envelope
     */
    async updateFriendPermissions(friendId, shareTimeline) {
        return apiService.put(`/friends/${friendId}/permissions`, { shareTimeline })
    },

    /**
     * Update live location permission for a friend
     * @param {string} friendId
     * @param {boolean} shareLiveLocation
     * @returns {Promise<Object>} API response envelope
     */
    async updateLiveLocationPermission(friendId, shareLiveLocation) {
        return apiService.put(`/friends/${friendId}/permissions/live`, { shareLiveLocation })
    },

    /**
     * Get all friend permissions
     * @returns {Promise<Object>} API response envelope
     */
    async getAllFriendPermissions() {
        return apiService.get('/friends/permissions')
    },

    /**
     * Get friend location trails for all eligible friends
     * @param {number} minutes How many minutes to load from the end time
     * @param {string|null} endTime Optional ISO-8601 end time
     * @returns {Promise<Object>} API response envelope
     */
    async getFriendsLocationTrails(minutes = 60, endTime = null) {
        const utcEndTime = endTime instanceof Date
            ? endTime.toISOString()
            : (typeof endTime === 'string' && endTime.trim() ? endTime.trim() : null)

        return apiService.get('/friends/location/trails', {
            minutes,
            ...(utcEndTime ? {endTime: utcEndTime} : {})
        })
    }
}

export default friendsService
