import {useAuthStore} from '@/stores/auth'

export function useUnits() {
    const getDistanceUnit = () => {
        try {
            const authStore = useAuthStore()
            return authStore.distanceUnit || 'KILOMETERS'
        } catch (error) {
            console.error('[useUnits] Error reading distance unit from auth store:', error)
            return 'KILOMETERS'
        }
    }

    const getTemperatureUnit = () => {
        try {
            const authStore = useAuthStore()
            return authStore.temperatureUnit || 'CELSIUS'
        } catch (error) {
            console.error('[useUnits] Error reading temperature unit from auth store:', error)
            return 'CELSIUS'
        }
    }

    return {
        getDistanceUnit,
        getTemperatureUnit
    }
}
