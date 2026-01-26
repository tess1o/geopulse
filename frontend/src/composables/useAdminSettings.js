/**
 * Admin Settings Composable
 * Shared logic for loading, updating, and resetting admin settings
 */

import { ref } from 'vue'
import { useToast } from 'primevue/usetoast'
import apiService from '@/utils/apiService'
import { getSettingMetadata } from '@/constants/adminSettingsMetadata'
import { transformSettingValue, parseSettingValue, shouldSkipEncryptedUpdate } from '@/utils/settingHelpers'

export function useAdminSettings() {
  const toast = useToast()
  const loading = ref(false)

  /**
   * Load settings for a specific category
   * @param {string} category - Setting category (auth, geocoding, ai, import, export)
   * @returns {Promise<Array>} Array of settings with metadata
   */
  const loadSettings = async (category) => {
    loading.value = true
    try {
      const response = await apiService.get(`/admin/settings/${category}`)
      return response.map(setting => {
        const metadata = getSettingMetadata(setting.key)
        return {
          ...setting,
          label: metadata.label,
          description: metadata.description,
          currentValue: transformSettingValue(setting)
        }
      })
    } catch (error) {
      console.error(`Failed to load ${category} settings:`, error)
      toast.add({
        severity: 'error',
        summary: 'Error',
        detail: `Failed to load ${category} settings`,
        life: 3000
      })
      throw error
    } finally {
      loading.value = false
    }
  }

  /**
   * Update a setting value
   * @param {object} setting - Setting object with currentValue
   * @param {function} validationFn - Optional validation function
   * @param {function} reloadFn - Optional reload function for encrypted fields
   * @returns {Promise<void>}
   */
  const updateSetting = async (setting, validationFn = null, reloadFn = null) => {
    try {
      // Skip update for unchanged encrypted fields
      if (shouldSkipEncryptedUpdate(setting)) {
        return
      }

      // Run custom validation if provided
      if (validationFn) {
        const validationError = await validationFn(setting)
        if (validationError) {
          toast.add({
            severity: 'error',
            summary: 'Validation Error',
            detail: validationError,
            life: 3000
          })
          // Reload to reset to previous value
          if (reloadFn) {
            await reloadFn()
          }
          return
        }
      }

      const value = parseSettingValue(setting)
      await apiService.put(`/admin/settings/${setting.key}`, { value })

      setting.isDefault = false

      toast.add({
        severity: 'success',
        summary: 'Success',
        detail: `${setting.label} updated`,
        life: 3000
      })

      // Reload settings for encrypted fields to get masked value
      if (setting.valueType === 'ENCRYPTED' && reloadFn) {
        await reloadFn()
      }
    } catch (error) {
      console.error('Failed to update setting:', error)
      const errorMessage = error.response?.data?.message || error.message || 'Failed to update setting'
      toast.add({
        severity: 'error',
        summary: 'Error',
        detail: errorMessage,
        life: 3000
      })
      // Reload to get correct value
      if (reloadFn) {
        await reloadFn()
      }
    }
  }

  /**
   * Reset a setting to its default value
   * @param {object} setting - Setting object
   * @returns {Promise<void>}
   */
  const resetSetting = async (setting) => {
    try {
      const response = await apiService.delete(`/admin/settings/${setting.key}`)

      setting.isDefault = true
      setting.currentValue = transformSettingValue({
        value: response.defaultValue,
        valueType: setting.valueType
      })

      toast.add({
        severity: 'success',
        summary: 'Success',
        detail: `${setting.label} reset to default`,
        life: 3000
      })
    } catch (error) {
      console.error('Failed to reset setting:', error)
      toast.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to reset setting',
        life: 3000
      })
    }
  }

  return {
    loading,
    loadSettings,
    updateSetting,
    resetSetting
  }
}
