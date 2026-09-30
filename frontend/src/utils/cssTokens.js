/**
 * Reads the current value of a CSS custom property (e.g. '--gp-text-primary') from <html>.
 * For JS consumers that can't use CSS directly (Chart.js, map layers). Values follow the active theme, so
 * re-read them when useThemeMode().isDarkMode changes.
 */
export function readCssToken(name, fallback = '') {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return fallback
  }
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}
