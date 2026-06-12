import { computed, onUnmounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useLocationStore } from '@/stores/location'
import apiService from '@/utils/apiService'

const LOCATION_OPTIONS = {
  enableHighAccuracy: true,
  maximumAge: 10000,
  timeout: 15000
}

const normalizeBrowserPosition = (position) => ({
  latitude: position.coords.latitude,
  longitude: position.coords.longitude,
  accuracy: position.coords.accuracy,
  timestamp: new Date(position.timestamp || Date.now()).toISOString(),
  source: 'browser',
  label: 'Your location'
})

const normalizeFallbackPosition = (position) => ({
  latitude: position.lat,
  longitude: position.lon,
  timestamp: position.timestamp,
  source: 'fallback',
  label: 'Your last known GeoPulse location',
  telemetryCurrentPopup: position.telemetryCurrentPopup || []
})

const getGeolocationErrorStatus = (error) => {
  if (error?.code === 1) return 'denied'
  if (error?.code === 2) return 'unavailable'
  return 'error'
}

const getGeolocationErrorMessage = (status) => {
  if (status === 'denied') {
    return 'Allow location permission to show your position.'
  }
  if (status === 'unavailable') {
    return 'Your current location is unavailable.'
  }
  return 'Unable to show your location.'
}

export function useViewerLocation() {
  const authStore = useAuthStore()
  const locationStore = useLocationStore()

  const status = ref('idle')
  const location = ref(null)
  const errorMessage = ref('')
  const watchId = ref(null)

  const isActive = computed(() => ['requesting', 'tracking', 'fallback'].includes(status.value))
  const isTracking = computed(() => status.value === 'tracking')
  const isFallback = computed(() => status.value === 'fallback')

  const clearBrowserWatch = () => {
    if (watchId.value !== null && navigator.geolocation?.clearWatch) {
      navigator.geolocation.clearWatch(watchId.value)
    }
    watchId.value = null
  }

  const disable = () => {
    clearBrowserWatch()
    status.value = 'idle'
    location.value = null
    errorMessage.value = ''
  }

  const canUseAuthenticatedFallback = () => {
    return authStore.isAuthenticated || apiService.hasCachedUser()
  }

  const tryAuthenticatedFallback = async () => {
    if (!canUseAuthenticatedFallback()) {
      return false
    }

    try {
      const lastKnown = await locationStore.getLastKnownPosition()
      if (!Number.isFinite(Number(lastKnown?.lat)) || !Number.isFinite(Number(lastKnown?.lon))) {
        return false
      }

      clearBrowserWatch()
      location.value = normalizeFallbackPosition(lastKnown)
      status.value = 'fallback'
      errorMessage.value = 'Showing your last known GeoPulse location.'
      return true
    } catch (error) {
      console.debug('Viewer location fallback unavailable:', error?.message || error)
      return false
    }
  }

  const handleBrowserError = async (error) => {
    const nextStatus = getGeolocationErrorStatus(error)
    const fallbackApplied = await tryAuthenticatedFallback()
    if (fallbackApplied) {
      return
    }

    clearBrowserWatch()
    location.value = null
    status.value = nextStatus
    errorMessage.value = getGeolocationErrorMessage(nextStatus)
  }

  const enable = async () => {
    errorMessage.value = ''

    if (typeof window !== 'undefined' && window.isSecureContext === false) {
      const fallbackApplied = await tryAuthenticatedFallback()
      if (!fallbackApplied) {
        status.value = 'unavailable'
        errorMessage.value = 'Location needs HTTPS or localhost.'
      }
      return
    }

    if (!navigator.geolocation?.watchPosition) {
      const fallbackApplied = await tryAuthenticatedFallback()
      if (!fallbackApplied) {
        status.value = 'unavailable'
        errorMessage.value = 'Location is unavailable in this browser.'
      }
      return
    }

    clearBrowserWatch()
    status.value = 'requesting'

    watchId.value = navigator.geolocation.watchPosition(
      (position) => {
        location.value = normalizeBrowserPosition(position)
        status.value = 'tracking'
        errorMessage.value = ''
      },
      (error) => {
        handleBrowserError(error)
      },
      LOCATION_OPTIONS
    )
  }

  onUnmounted(() => {
    clearBrowserWatch()
  })

  return {
    status,
    location,
    errorMessage,
    isActive,
    isTracking,
    isFallback,
    enable,
    disable
  }
}
