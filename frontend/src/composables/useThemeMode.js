import { computed, ref } from 'vue'
import {
  THEME_MODES,
  getThemeMode,
  initializeThemeMode,
  isDarkModeApplied,
  onDarkModeChange,
  setThemeMode as persistThemeMode
} from '@/utils/themeMode'

initializeThemeMode()

const themeModeState = ref(getThemeMode())
// Follows the class actually applied to <html>, so it also updates when the OS theme changes in "system" mode.
const isDarkModeState = ref(isDarkModeApplied())
onDarkModeChange((isDark) => {
  isDarkModeState.value = isDark
})

const setThemeMode = (themeMode) => {
  themeModeState.value = persistThemeMode(themeMode)
}

const themeMode = computed({
  get: () => themeModeState.value,
  set: (nextThemeMode) => setThemeMode(nextThemeMode)
})

const isDarkMode = computed(() => isDarkModeState.value)

export function useThemeMode() {
  return {
    themeMode,
    isDarkMode,
    setThemeMode,
    themeModes: THEME_MODES
  }
}
