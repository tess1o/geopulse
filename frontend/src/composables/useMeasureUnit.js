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
            const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}');
            return userInfo.measureUnit || 'METRIC';
        } catch (error) {
            console.error('[useMeasureUnit] Error reading measure unit from localStorage:', error);
            return 'METRIC'; // Default to Metric in case of error
        }
    };

    return {
        getMeasureUnit
    };
}
