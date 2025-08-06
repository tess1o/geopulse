import apiService from './apiService'

/**
 * Service for handling authenticated image loading from Immich
 */
export const imageService = {
  /**
   * Load an authenticated image as a blob URL
   * @param {string} endpoint - The API endpoint path (e.g., '/users/me/immich/photos/id/thumbnail')
   * @returns {Promise<string>} - Blob URL for the image
   */
  async loadAuthenticatedImage(endpoint) {
    try {
      console.log('imageService: Loading authenticated image from endpoint:', endpoint)
      console.log('imageService: Making request via apiService.getRawWithBlob')
      
      // Endpoint should already be in the correct format from the backend
      // Add cache-busting parameter for Safari
      const cacheBustParam = { _t: Date.now() }
      const response = await apiService.getRawWithBlob(endpoint, {
        'Accept': 'image/*'
      }, cacheBustParam)

      console.log('imageService: Response:', response);

      if (response.status != 200) {
        const errorText = await response.text()
        console.error('imageService: Response error text:', errorText)
        throw new Error(`Failed to load image: ${response.status} - ${errorText}`)
      }

      // Ensure we have a valid blob before creating URL
      if (!response.data || !(response.data instanceof Blob)) {
        throw new Error('Invalid response data - not a blob')
      }
      
      const blobUrl = URL.createObjectURL(response.data)
      console.log('imageService: Created blob URL:', blobUrl)
      console.log('imageService: Blob size:', response.data.size, 'bytes')
      console.log('imageService: Blob type:', response.data.type)
      return blobUrl
    } catch (error) {
      console.error('Failed to load authenticated image:', error)
      
      // Wrap image loading errors with a specific type to prevent global error handling
      const imageError = new Error(`Image loading failed: ${error.message || 'Unknown error'}`)
      imageError.originalError = error
      imageError.isImageLoadingError = true
      imageError.endpoint = endpoint
      
      // Don't let image loading errors trigger global "backend down" redirects
      throw imageError
    }
  },

  /**
   * Revoke a blob URL to free memory
   * @param {string} blobUrl - The blob URL to revoke
   */
  revokeBlobUrl(blobUrl) {
    if (blobUrl && blobUrl.startsWith('blob:')) {
      URL.revokeObjectURL(blobUrl)
    }
  },

  /**
   * Load multiple authenticated images
   * @param {Array<string>} urls - Array of image URLs
   * @returns {Promise<Array<{url: string, blobUrl: string, error?: Error}>>}
   */
  async loadAuthenticatedImages(urls) {
    const results = await Promise.allSettled(
      urls.map(async (url) => {
        try {
          const blobUrl = await this.loadAuthenticatedImage(url)
          return { url, blobUrl }
        } catch (error) {
          return { url, blobUrl: null, error }
        }
      })
    )
    
    return results.map(result => result.value || result.reason)
  },

  /**
   * Download an authenticated file
   * @param {string} endpoint - The API endpoint path for download
   * @param {string} filename - The filename for download
   */
  async downloadImage(endpoint, filename) {
    try {
      const response = await apiService.getRawWithBlob(endpoint, {
        'Accept': '*/*'
      })

      if (response.status != 200) {
        throw new Error(`Download failed: ${response.status}`)
      }

      const blobUrl = URL.createObjectURL(response.data)
      const link = document.createElement('a')
      link.href = blobUrl
      link.download = filename || 'download'
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      
      // Clean up
      setTimeout(() => URL.revokeObjectURL(blobUrl), 100)
      
      return true
    } catch (error) {
      console.error('Download failed:', error)
      
      // Wrap download errors with a specific type to prevent global error handling
      const downloadError = new Error(`File download failed: ${error.message || 'Unknown error'}`)
      downloadError.originalError = error
      downloadError.isImageDownloadError = true
      downloadError.endpoint = endpoint
      
      throw downloadError
    }
  },

  // Legacy alias for compatibility
  downloadAuthenticatedFile(url, filename) {
    return this.downloadImage(url, filename)
  }
}