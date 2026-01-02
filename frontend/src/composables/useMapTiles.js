/**
 * Composable for managing custom map tile URLs
 * Provides dynamic tile URLs based on user preferences or shared link overrides
 * @param {Object} options - Configuration options
 * @param {String} options.overrideTileUrl - Optional custom tile URL to override user preferences (for shared views)
 * @param {Boolean} options.isSharedView - If true, skip user preferences fallback (for shared links)
 */
export function useMapTiles(options = {}) {
  const { overrideTileUrl, isSharedView = false } = options

  /**
   * Get the tile URL from override, user preferences, or default to OSM
   * Priority: 1. Override (shared link) 2. Skip if shared view 3. User preferences 4. Default OSM
   * @returns {string} Tile URL template with {z}, {x}, {y} placeholders
   */
  const getTileUrl = () => {
    // Priority 1: Use override URL if provided (for shared links)
    if (overrideTileUrl && overrideTileUrl.trim()) {
      const customUrl = overrideTileUrl.trim()
      const urlHash = btoa(customUrl).substring(0, 8)
      const separator = customUrl.includes('?') ? '&' : '?'
      return `${customUrl}${separator}v=${urlHash}`
    }

    // Priority 2: For shared views with no override, skip user preferences and use OSM
    if (isSharedView) {
      return '/osm/tiles/{s}/{z}/{x}/{y}.png'
    }

    // Priority 3: Try user preferences (for authenticated users in normal views)
    try {
      const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
      if (userInfo.customMapTileUrl && userInfo.customMapTileUrl.trim()) {
        const customUrl = userInfo.customMapTileUrl.trim()
        const urlHash = btoa(customUrl).substring(0, 8)
        const separator = customUrl.includes('?') ? '&' : '?'
        return `${customUrl}${separator}v=${urlHash}`
      }
    } catch (error) {
      console.error('[useMapTiles] Error reading custom map tile URL from localStorage:', error)
    }

    // Priority 4: Default to OSM tiles
    return '/osm/tiles/{s}/{z}/{x}/{y}.png'
  }

  /**
   * Get attribution text based on the tile provider
   * Checks override first, then user preferences (unless isSharedView)
   * @returns {string} Attribution HTML
   */
  const getTileAttribution = () => {
    // Determine which URL to check (override or user preferences)
    const urlToCheck = overrideTileUrl && overrideTileUrl.trim() ?
      overrideTileUrl.trim() :
      (!isSharedView ? (function() {
        try {
          const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
          return userInfo.customMapTileUrl
        } catch {
          return null
        }
      })() : null)

    if (urlToCheck) {
      const customUrl = urlToCheck.toLowerCase()

      // Detect common tile providers and provide appropriate attribution
      if (customUrl.includes('maptiler.com')) {
        return '© <a href="https://www.maptiler.com/copyright/" target="_blank">MapTiler</a> © <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap contributors</a>'
      } else if (customUrl.includes('mapbox.com')) {
        return '© <a href="https://www.mapbox.com/about/maps/" target="_blank">Mapbox</a> © <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap contributors</a>'
      } else if (customUrl.includes('thunderforest.com')) {
        return '© <a href="https://www.thunderforest.com/" target="_blank">Thunderforest</a> © <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap contributors</a>'
      } else if (customUrl.includes('stadiamaps.com')) {
        return '© <a href="https://stadiamaps.com/" target="_blank">Stadia Maps</a> © <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap contributors</a>'
      }

      // Custom provider - generic attribution
      return '© Custom Tile Provider © <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap contributors</a>'
    }

    // Default OSM attribution
    return '© <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap</a> contributors'
  }

  /**
   * Check if using custom tiles (override or user preferences unless isSharedView)
   * @returns {boolean} True if using custom tiles
   */
  const isUsingCustomTiles = () => {
    if (overrideTileUrl && overrideTileUrl.trim()) {
      return true
    }
    if (isSharedView) {
      return false
    }
    try {
      const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
      return !!(userInfo.customMapTileUrl && userInfo.customMapTileUrl.trim())
    } catch (error) {
      return false
    }
  }

  /**
   * Get subdomains for tile URL if {s} placeholder is present
   * @returns {Array<string>} Array of subdomains
   */
  const getSubdomains = () => {
    // Determine which URL to check (override or user preferences unless isSharedView)
    const urlToCheck = overrideTileUrl && overrideTileUrl.trim() ?
      overrideTileUrl.trim() :
      (!isSharedView ? (function() {
        try {
          const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
          return userInfo.customMapTileUrl
        } catch {
          return null
        }
      })() : null)

    if (urlToCheck && urlToCheck.includes('{s}')) {
      return ['a', 'b', 'c']
    }

    // Default OSM uses a, b, c subdomains if no custom URL
    if (!overrideTileUrl && !urlToCheck) {
      return ['a', 'b', 'c']
    }

    return []
  }

  return {
    getTileUrl,
    getTileAttribution,
    isUsingCustomTiles,
    getSubdomains
  }
}
