export const THEME_MODES = Object.freeze({
  LIGHT: 'light',
  DARK: 'dark',
  SYSTEM: 'system'
})

const THEME_MODE_STORAGE_KEY = 'themeMode'
const LEGACY_DARK_MODE_STORAGE_KEY = 'darkMode'
const DARK_THEME_CLASS = 'p-dark'
const SYSTEM_THEME_MEDIA_QUERY = '(prefers-color-scheme: dark)'

const VALID_THEME_MODES = new Set(Object.values(THEME_MODES))

let currentThemeMode = THEME_MODES.SYSTEM
let stopSystemThemeListener = null
let initialized = false
const darkModeListeners = new Set()

const canUseBrowserApis = () =>
  typeof window !== 'undefined' &&
  typeof document !== 'undefined'

export const normalizeThemeMode = (themeMode) => {
  const value = String(themeMode || '').toLowerCase()
  return VALID_THEME_MODES.has(value) ? value : THEME_MODES.SYSTEM
}

const isSystemDarkMode = () => {
  if (!canUseBrowserApis() || typeof window.matchMedia !== 'function') {
    return false
  }

  return window.matchMedia(SYSTEM_THEME_MEDIA_QUERY).matches
}

export const resolveThemeModeToDark = (themeMode) => {
  const normalizedThemeMode = normalizeThemeMode(themeMode)
  if (normalizedThemeMode === THEME_MODES.DARK) return true
  if (normalizedThemeMode === THEME_MODES.LIGHT) return false
  return isSystemDarkMode()
}

export const getStoredThemeMode = () => {
  if (!canUseBrowserApis()) {
    return THEME_MODES.SYSTEM
  }

  try {
    const storedThemeMode = localStorage.getItem(THEME_MODE_STORAGE_KEY)
    if (storedThemeMode && VALID_THEME_MODES.has(storedThemeMode)) {
      return storedThemeMode
    }

    const legacyDarkMode = localStorage.getItem(LEGACY_DARK_MODE_STORAGE_KEY)
    if (legacyDarkMode === 'true') return THEME_MODES.DARK
    if (legacyDarkMode === 'false') return THEME_MODES.LIGHT

    return THEME_MODES.SYSTEM
  } catch (error) {
    console.warn('Failed to read theme mode preference from localStorage:', error)
    return THEME_MODES.SYSTEM
  }
}

const persistThemeMode = (themeMode) => {
  if (!canUseBrowserApis()) {
    return
  }

  try {
    localStorage.setItem(THEME_MODE_STORAGE_KEY, themeMode)
    localStorage.removeItem(LEGACY_DARK_MODE_STORAGE_KEY)
  } catch (error) {
    console.warn('Failed to persist theme mode preference to localStorage:', error)
  }
}

export const applyThemeMode = (themeMode) => {
  if (!canUseBrowserApis()) {
    return false
  }

  const isDarkMode = resolveThemeModeToDark(themeMode)
  document.documentElement.classList.toggle(DARK_THEME_CLASS, isDarkMode)
  darkModeListeners.forEach((listener) => listener(isDarkMode))
  return isDarkMode
}

/** Whether the dark theme class is currently applied to <html>. */
export const isDarkModeApplied = () =>
  canUseBrowserApis() && document.documentElement.classList.contains(DARK_THEME_CLASS)

/**
 * Calls `listener(isDark)` every time the theme is (re)applied, including OS theme changes in "system" mode.
 * Returns an unsubscribe function.
 */
export const onDarkModeChange = (listener) => {
  darkModeListeners.add(listener)
  return () => darkModeListeners.delete(listener)
}

const handleSystemThemeChange = () => {
  if (currentThemeMode === THEME_MODES.SYSTEM) {
    applyThemeMode(currentThemeMode)
  }
}

export const subscribeToSystemThemeChanges = (onThemeChange) => {
  if (!canUseBrowserApis() || typeof window.matchMedia !== 'function') {
    return () => {}
  }

  const mediaQuery = window.matchMedia(SYSTEM_THEME_MEDIA_QUERY)
  const handleChange = () => {
    if (typeof onThemeChange === 'function') {
      onThemeChange(mediaQuery.matches)
    }
  }

  if (typeof mediaQuery.addEventListener === 'function') {
    mediaQuery.addEventListener('change', handleChange)
    return () => mediaQuery.removeEventListener('change', handleChange)
  }

  if (typeof mediaQuery.addListener === 'function') {
    mediaQuery.addListener(handleChange)
    return () => mediaQuery.removeListener(handleChange)
  }

  return () => {}
}

const ensureSystemThemeListener = () => {
  if (stopSystemThemeListener) {
    return
  }

  stopSystemThemeListener = subscribeToSystemThemeChanges(handleSystemThemeChange)
}

export const setThemeMode = (themeMode) => {
  currentThemeMode = normalizeThemeMode(themeMode)
  persistThemeMode(currentThemeMode)
  applyThemeMode(currentThemeMode)
  ensureSystemThemeListener()
  return currentThemeMode
}

export const getThemeMode = () => currentThemeMode

// Idempotent: main.js calls it before mounting, and useThemeMode calls it at import time because its module can be
// evaluated (through App.vue's imports) before main.js's body runs.
export const initializeThemeMode = () => {
  if (initialized) {
    return currentThemeMode
  }
  initialized = true
  currentThemeMode = getStoredThemeMode()
  persistThemeMode(currentThemeMode)
  applyThemeMode(currentThemeMode)
  ensureSystemThemeListener()
  return currentThemeMode
}
