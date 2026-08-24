import apiService from '@/utils/apiService'

const mapMatchingService = {
  resolve(tripIds) {
    return apiService.post('/map-matching/resolve', { tripIds })
  },

  status(targetIds) {
    return apiService.post('/map-matching/status', { targetIds })
  }
}

export default mapMatchingService
