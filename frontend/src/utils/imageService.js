import apiService from './apiService'

/**
 * Service for handling authenticated image loading from Immich
 */
export const imageService = {
  /**
   * Load an authenticated image as a blob URL
   * @param {string} url - The image URL to load
   * @returns {Promise<string>} - Blob URL for the image
   */
  async loadAuthenticatedImage(url) {
    try {
      console.log('imageService: Loading authenticated image:', url)
      
      // Extract the endpoint from the full URL
      const urlObj = new URL(url)
      let endpoint = urlObj.pathname

      console.log('imageService: Endpoint:', endpoint)

      endpoint = endpoint.replace('/api', '')

      const response = await apiService.getRawWithBlob(endpoint, {
        'Accept': 'image/*'
      })

      console.log('imageService: Response:', response);

      if (response.status != 200) {
        const errorText = await response.text()
        console.error('imageService: Response error text:', errorText)
        throw new Error(`Failed to load image: ${response.status} - ${errorText}`)
      }

      const blobUrl = URL.createObjectURL(response.data)
      console.log('imageService: Created blob URL:', blobUrl)
      return blobUrl
    } catch (error) {
      console.error('Failed to load authenticated image:', error)
      
      // Wrap image loading errors with a specific type to prevent global error handling
      const imageError = new Error(`Image loading failed: ${error.message || 'Unknown error'}`)
      imageError.originalError = error
      imageError.isImageLoadingError = true
      imageError.url = url
      
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
   * @param {string} url - The download URL
   * @param {string} filename - The filename for download
   */
  async downloadImage(url, filename) {
    try {
      const urlObj = new URL(url)
      let endpoint = urlObj.pathname

      endpoint = endpoint.replace('/api', '')

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
      downloadError.url = url
      
      throw downloadError
    }
  },

  // Legacy alias for compatibility
  downloadAuthenticatedFile(url, filename) {
    return this.downloadImage(url, filename)
  }
}