/**
 * Chunked Upload Service
 *
 * Handles large file uploads by splitting them into smaller chunks to bypass
 * Cloudflare's 100MB upload limit. Files are split on the frontend, uploaded
 * sequentially, and reassembled on the backend.
 */
import apiService from './apiService'

const DEFAULT_CHUNK_SIZE = 50 * 1024 * 1024  // 50MB - fallback only, actual chunk size comes from backend
const CHUNK_THRESHOLD = 80 * 1024 * 1024  // 80MB - use chunked upload for files above this size
const MAX_RETRY_ATTEMPTS = 3  // Maximum retry attempts for chunk upload failures
const INITIAL_RETRY_DELAY_MS = 1000  // Initial delay before retry (exponential backoff)

export const chunkedUploadService = {
    /**
     * Helper to create a delay promise
     * @param {number} ms - Milliseconds to delay
     * @returns {Promise} Promise that resolves after delay
     */
    _delay(ms) {
        return new Promise(resolve => setTimeout(resolve, ms))
    },

    /**
     * Check if a file should use chunked upload
     * @param {File} file - The file to check
     * @returns {boolean} True if file should use chunked upload
     */
    shouldUseChunkedUpload(file) {
        return file.size > CHUNK_THRESHOLD
    },

    /**
     * Calculate the number of chunks for a file
     * @param {number} fileSize - File size in bytes
     * @param {number} chunkSize - Chunk size in bytes (optional, uses default if not provided)
     * @returns {number} Number of chunks
     */
    calculateChunks(fileSize, chunkSize = DEFAULT_CHUNK_SIZE) {
        return Math.ceil(fileSize / chunkSize)
    },

    /**
     * Initialize a chunked upload session
     * @param {File} file - The file to upload
     * @param {string} importFormat - The import format (owntracks, google-timeline, etc.)
     * @param {Object} options - Import options
     * @returns {Promise<Object>} Session info including uploadId, totalChunks, and chunkSizeBytes
     */
    async initializeUpload(file, importFormat, options = {}) {
        // Note: We don't send totalChunks - backend calculates it based on configured chunk size
        // This ensures frontend and backend use the same chunk size from system settings
        const response = await apiService.post('/import/upload/init', {
            fileName: file.name,
            fileSize: file.size,
            importFormat,
            options: JSON.stringify(options)
        })

        if (!response.success) {
            throw new Error(response.error?.message || 'Failed to initialize chunked upload')
        }

        return {
            uploadId: response.uploadId,
            totalChunks: response.totalChunks,
            chunkSizeBytes: response.chunkSizeBytes,
            expiresAt: response.expiresAt
        }
    },

    /**
     * Upload a single chunk
     * @param {string} uploadId - The upload session ID
     * @param {File} file - The original file
     * @param {number} chunkIndex - The chunk index (0-based)
     * @param {number} chunkSize - Chunk size in bytes
     * @param {Function} onProgress - Progress callback for this chunk
     * @returns {Promise<Object>} Chunk upload result
     */
    async uploadChunk(uploadId, file, chunkIndex, chunkSize = DEFAULT_CHUNK_SIZE, onProgress = null) {
        const start = chunkIndex * chunkSize
        const end = Math.min(start + chunkSize, file.size)

        // File.slice() creates a view, not a copy - memory efficient
        const chunk = file.slice(start, end)

        const formData = new FormData()
        formData.append('chunkIndex', chunkIndex.toString())
        formData.append('chunk', chunk, `chunk_${chunkIndex}`)

        // Note: Don't set Content-Type header manually - axios handles it automatically
        // for FormData and adds the correct boundary parameter
        const response = await apiService.post(`/import/upload/${uploadId}/chunk`, formData, {
            onUploadProgress: onProgress
        })

        if (!response.success) {
            throw new Error(response.error?.message || `Failed to upload chunk ${chunkIndex}`)
        }

        return {
            chunkIndex: response.chunkIndex,
            receivedChunks: response.receivedChunks,
            totalChunks: response.totalChunks,
            progress: response.progress,
            isComplete: response.isComplete
        }
    },

    /**
     * Upload a chunk with automatic retry on failure
     * Uses exponential backoff: 1s, 2s, 4s delays between retries
     * @param {string} uploadId - The upload session ID
     * @param {File} file - The original file
     * @param {number} chunkIndex - The chunk index (0-based)
     * @param {number} chunkSize - Chunk size in bytes
     * @param {Function} onProgress - Progress callback for this chunk
     * @returns {Promise<Object>} Chunk upload result
     */
    async uploadChunkWithRetry(uploadId, file, chunkIndex, chunkSize, onProgress = null) {
        let lastError = null

        for (let attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return await this.uploadChunk(uploadId, file, chunkIndex, chunkSize, onProgress)
            } catch (error) {
                lastError = error
                console.warn(`Chunk ${chunkIndex} upload failed (attempt ${attempt + 1}/${MAX_RETRY_ATTEMPTS}):`, error.message)

                // Don't retry on the last attempt
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    // Exponential backoff: 1s, 2s, 4s
                    const delayMs = INITIAL_RETRY_DELAY_MS * Math.pow(2, attempt)
                    console.log(`Retrying chunk ${chunkIndex} in ${delayMs}ms...`)
                    await this._delay(delayMs)
                }
            }
        }

        // All retries exhausted
        throw lastError
    },

    /**
     * Complete the upload and create import job
     * @param {string} uploadId - The upload session ID
     * @returns {Promise<Object>} Import job response
     */
    async completeUpload(uploadId) {
        const response = await apiService.post(`/import/upload/${uploadId}/complete`)

        if (!response.success) {
            throw new Error(response.error?.message || 'Failed to complete chunked upload')
        }

        return response
    },

    /**
     * Get upload session status
     * @param {string} uploadId - The upload session ID
     * @returns {Promise<Object>} Upload status
     */
    async getUploadStatus(uploadId) {
        const response = await apiService.get(`/import/upload/${uploadId}/status`)

        if (!response.success) {
            throw new Error(response.error?.message || 'Failed to get upload status')
        }

        return response
    },

    /**
     * Abort an upload and cleanup
     * @param {string} uploadId - The upload session ID
     * @returns {Promise<boolean>} True if aborted successfully
     */
    async abortUpload(uploadId) {
        try {
            const response = await apiService.delete(`/import/upload/${uploadId}`)
            return response.success
        } catch (error) {
            console.warn('Failed to abort upload:', error)
            return false
        }
    },

    /**
     * Upload a file using chunked upload
     * This is the main orchestration method that handles the entire chunked upload process
     *
     * @param {File} file - The file to upload
     * @param {string} importFormat - The import format
     * @param {Object} options - Import options
     * @param {Object} callbacks - Callback functions
     * @param {Function} callbacks.onProgress - Overall progress callback (0-100)
     * @param {Function} callbacks.onChunkComplete - Called when each chunk completes
     * @param {Function} callbacks.onError - Called on error
     * @returns {Promise<Object>} Import job response
     */
    async uploadFile(file, importFormat, options = {}, callbacks = {}) {
        const { onProgress, onChunkComplete, onError } = callbacks
        let uploadId = null

        try {
            // Initialize upload session
            const session = await this.initializeUpload(file, importFormat, options)
            uploadId = session.uploadId
            const totalChunks = session.totalChunks
            // Use chunk size from backend, fallback to default
            const chunkSize = session.chunkSizeBytes || DEFAULT_CHUNK_SIZE

            // Upload chunks sequentially
            for (let i = 0; i < totalChunks; i++) {
                // Calculate progress for this chunk
                const chunkProgressCallback = (progressEvent) => {
                    if (onProgress && progressEvent.total) {
                        // Calculate overall progress:
                        // - Previous chunks: (i / totalChunks) * 99
                        // - Current chunk progress: (progressEvent.loaded / progressEvent.total) * (99 / totalChunks)
                        const previousChunksProgress = (i / totalChunks) * 99
                        const currentChunkProgress = (progressEvent.loaded / progressEvent.total) * (99 / totalChunks)
                        const overallProgress = Math.round(previousChunksProgress + currentChunkProgress)
                        onProgress(Math.min(overallProgress, 99))
                    }
                }

                const result = await this.uploadChunkWithRetry(uploadId, file, i, chunkSize, chunkProgressCallback)

                if (onChunkComplete) {
                    onChunkComplete(i, result.receivedChunks, totalChunks)
                }
            }

            // All chunks uploaded, complete the upload
            const response = await this.completeUpload(uploadId)

            // Show 100% progress
            if (onProgress) {
                onProgress(100)
            }

            return response

        } catch (error) {
            // Try to abort the upload on error
            if (uploadId) {
                await this.abortUpload(uploadId)
            }

            if (onError) {
                onError(error)
            }

            throw error
        }
    },

    /**
     * Get default chunk size in bytes
     * @returns {number} Default chunk size (backend may override)
     */
    getChunkSize() {
        return DEFAULT_CHUNK_SIZE
    },

    /**
     * Get chunk threshold in bytes
     * @returns {number} Threshold above which chunked upload is used
     */
    getChunkThreshold() {
        return CHUNK_THRESHOLD
    }
}

export default chunkedUploadService
