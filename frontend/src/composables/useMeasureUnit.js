import {useAuthStore} from '@/stores/auth'

/**
 * Composable for managing measurement units (Metric/Imperial)
 * Provides the current unit from user preferences
 */

/**
 * Get the measurement unit from user preferences
 * @returns {string} 'METRIC' or 'IMPERIAL'
 */
export function useMeasureUnit() {
    const getMeasureUnit = () => {
        try {
            const authStore = useAuthStore()
            return authStore.measureUnit || 'METRIC'
        } catch (error) {
            console.error('[useMeasureUnit] Error reading measure unit from auth store:', error)
            return 'METRIC'
        }
    }

    return {
        getMeasureUnit
    }
}
