/**
 * Composable for managing custom map tile URLs
 * Provides dynamic tile URLs based on user preferences
 */

/**
 * Get map tile configuration from user preferences
 * @returns {Object} Tile configuration with URL and attribution
 */
export function useMapTiles() {
  /**
   * Get the tile URL from user preferences or default to OSM
   * Custom tiles are proxied through the backend to avoid CORS/ORB issues
   * @returns {string} Tile URL template with {z}, {x}, {y} placeholders
   */
  const getTileUrl = () => {
    try {
      const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
      // If user has configured a custom tile URL, use it directly with cache-busting version
      if (userInfo.customMapTileUrl && userInfo.customMapTileUrl.trim()) {
        const customUrl = userInfo.customMapTileUrl.trim()

        // Generate a cache-busting version based on the custom URL
        // This ensures tiles reload when the custom URL changes
        const urlHash = btoa(customUrl).substring(0, 8)

        // Append version parameter to the URL
        // If URL already has query params (e.g., ?key=xxx), use &v=hash
        // Otherwise use ?v=hash
        const separator = customUrl.includes('?') ? '&' : '?'
        const versionedUrl = `${customUrl}${separator}v=${urlHash}`
        return versionedUrl
      }
    } catch (error) {
      console.error('[useMapTiles] Error reading custom map tile URL from localStorage:', error)
    }

    // Default to OSM tiles proxied through nginx for caching
    return '/osm/tiles/{s}/{z}/{x}/{y}.png'
  }

  /**
   * Get attribution text based on the tile provider
   * Detects provider from stored custom tile URL, not the proxy URL
   * @returns {string} Attribution HTML
   */
  const getTileAttribution = () => {
    try {
      const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')

      // Check the actual custom tile URL for attribution (not the proxy URL)
      if (userInfo.customMapTileUrl && userInfo.customMapTileUrl.trim()) {
        const customUrl = userInfo.customMapTileUrl.toLowerCase()

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
    } catch (error) {
      console.warn('Error reading custom map tile URL for attribution:', error)
    }

    // Default OSM attribution
    return '© <a href="https://www.openstreetmap.org/copyright" target="_blank">OpenStreetMap</a> contributors'
  }

  /**
   * Check if user is using custom tiles (not default OSM)
   * @returns {boolean} True if using custom tiles
   */
  const isUsingCustomTiles = () => {
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
    try {
      const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')

      // Check the actual custom tile URL (not the proxy URL)
      if (userInfo.customMapTileUrl && userInfo.customMapTileUrl.trim()) {
        // Check if the custom URL uses subdomains
        if (userInfo.customMapTileUrl.includes('{s}')) {
          return ['a', 'b', 'c']
        }
        // No subdomains in custom URL
        return []
      }
    } catch (error) {
      console.warn('Error checking subdomains from localStorage:', error)
    }

    // Default OSM uses a, b, c subdomains
    return ['a', 'b', 'c']
  }

  return {
    getTileUrl,
    getTileAttribution,
    isUsingCustomTiles,
    getSubdomains
  }
}
