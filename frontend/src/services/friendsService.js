import apiService from '@/utils/apiService'

const friendsService = {
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
    }
}

export default friendsService
