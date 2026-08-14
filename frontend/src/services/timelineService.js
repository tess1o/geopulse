import apiService from '@/utils/apiService'

const timelineService = {
    /**
     * Look up historical stays near an arbitrary map coordinate.
     * @param {number} latitude
     * @param {number} longitude
     * @returns {Promise<Object>} API response envelope
     */
    async lookupLocation(latitude, longitude) {
        return apiService.get('/streaming-timeline/location-lookup', {
            latitude,
            longitude
        })
    },

    /**
     * Get multi-user timeline data (for friends timeline view)
     * @param {string} startTime - Start time in ISO format
     * @param {string} endTime - End time in ISO format
     * @param {Array<string>|null} userIds - Optional array of user IDs to fetch
     * @returns {Promise<Object>} API response envelope
     */
    async getMultiUserTimeline(startTime, endTime, userIds = null) {
        const params = { startTime, endTime }

        if (userIds && userIds.length > 0) {
            params.userIds = userIds.join(',')
        }

        return apiService.get('/streaming-timeline/multi-user', params)
    }
}

export default timelineService
