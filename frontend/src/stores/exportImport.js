import { defineStore } from 'pinia'
import apiService from '../utils/apiService'
import chunkedUploadService from '../utils/chunkedUploadService'
import { isMaintenanceInterruption } from './maintenance'
import { normalizeApiError } from '@/utils/apiErrorDetail'

const TERMINAL_IMPORT_STATUSES = new Set(['completed', 'failed'])

const shouldIgnoreImportUpdate = (existingJob, incomingJob) => {
    if (!existingJob || !incomingJob) {
        return false
    }
    if (existingJob.importJobId !== incomingJob.importJobId) {
        return false
    }

    const existingStatus = existingJob.status
    const incomingStatus = incomingJob.status
    return TERMINAL_IMPORT_STATUSES.has(existingStatus) && existingStatus !== incomingStatus
}

export const useExportImportStore = defineStore('exportImport', {
    state: () => ({
        // Export state
        exportJobs: [],
        currentExportJob: null,
        exportError: null,

        // Import state
        importJobs: [],
        currentImportJob: null,
        importError: null,

        // UI state
        isExporting: false,
        isImporting: false,
        uploadProgress: 0,
        isUploading: false,

        // Chunked upload state (for internal tracking)
        currentChunkInfo: null  // { index, completed, total }
    }),

    getters: {
        // Export getters
        getExportJobs: (state) => state.exportJobs,
        getCurrentExportJob: (state) => state.currentExportJob,
        getExportError: (state) => state.exportError,
        hasActiveExportJob: (state) => {
            return state.currentExportJob && 
                   ['processing', 'validating'].includes(state.currentExportJob.status)
        },
        getCompletedExportJobs: (state) => {
            return state.exportJobs.filter(job => job.status === 'completed')
        },
        getFailedExportJobs: (state) => {
            return state.exportJobs.filter(job => job.status === 'failed')
        },

        // Import getters
        getImportJobs: (state) => state.importJobs,
        getCurrentImportJob: (state) => state.currentImportJob,
        getImportError: (state) => state.importError,
        hasActiveImportJob: (state) => {
            return state.currentImportJob && 
                   ['processing', 'validating'].includes(state.currentImportJob.status)
        },
        getCompletedImportJobs: (state) => {
            return state.importJobs.filter(job => job.status === 'completed')
        },
        getFailedImportJobs: (state) => {
            return state.importJobs.filter(job => job.status === 'failed')
        },

        // UI state getters
        getIsExporting: (state) => state.isExporting,
        getIsImporting: (state) => state.isImporting,
        getIsProcessing: (state) => state.isExporting || state.isImporting
    },

    actions: {
        failExport(error, fallback) {
            this.exportError = normalizeApiError(error, fallback)
            return this.exportError
        },

        failImport(error, fallback) {
            this.importError = normalizeApiError(error, fallback)
            return this.importError
        },

        // Export actions
        setExportJobs(jobs) {
            this.exportJobs = jobs
        },

        setCurrentExportJob(job) {
            this.currentExportJob = job
        },

        addExportJob(job) {
            const existingIndex = this.exportJobs.findIndex(j => j.exportJobId === job.exportJobId)
            if (existingIndex !== -1) {
                this.exportJobs[existingIndex] = job
            } else {
                this.exportJobs.unshift(job)
            }
        },

        updateExportJob(jobId, updates) {
            const job = this.exportJobs.find(j => j.exportJobId === jobId)
            if (job) {
                Object.assign(job, updates)
            }
            if (this.currentExportJob?.exportJobId === jobId) {
                Object.assign(this.currentExportJob, updates)
            }
        },

        removeExportJob(jobId) {
            this.exportJobs = this.exportJobs.filter(j => j.exportJobId !== jobId)
            if (this.currentExportJob?.exportJobId === jobId) {
                this.currentExportJob = null
            }
        },

        // Import actions
        setImportJobs(jobs) {
            this.importJobs = jobs
        },

        setCurrentImportJob(job) {
            if (shouldIgnoreImportUpdate(this.currentImportJob, job)) {
                return
            }
            this.currentImportJob = job
        },

        addImportJob(job) {
            const existingIndex = this.importJobs.findIndex(j => j.importJobId === job.importJobId)
            if (existingIndex !== -1) {
                if (shouldIgnoreImportUpdate(this.importJobs[existingIndex], job)) {
                    return
                }
                this.importJobs[existingIndex] = job
            } else {
                this.importJobs.unshift(job)
            }
        },

        updateImportJob(jobId, updates) {
            const job = this.importJobs.find(j => j.importJobId === jobId)
            if (job) {
                if (!shouldIgnoreImportUpdate(job, { ...job, ...updates, importJobId: jobId })) {
                    Object.assign(job, updates)
                }
            }
            if (this.currentImportJob?.importJobId === jobId) {
                if (!shouldIgnoreImportUpdate(
                    this.currentImportJob,
                    { ...this.currentImportJob, ...updates, importJobId: jobId }
                )) {
                    Object.assign(this.currentImportJob, updates)
                }
            }
        },

        removeImportJob(jobId) {
            this.importJobs = this.importJobs.filter(j => j.importJobId !== jobId)
            if (this.currentImportJob?.importJobId === jobId) {
                this.currentImportJob = null
            }
        },

        // API Actions - Export
        async createExportJob(dataTypes, dateRange, format = 'json', options = null) {
            this.isExporting = true
            this.exportError = null
            try {
                const payload = {
                    dataTypes,
                    dateRange,
                    format
                }
                if (options) {
                    payload.options = options
                }

                const job = await apiService.post('/exports', payload)
                this.setCurrentExportJob(job)
                this.addExportJob(job)
                return job
            } catch (error) {
                throw this.failExport(error, 'Failed to create export')
            } finally {
                this.isExporting = false
            }
        },

        async fetchExportStatus(exportJobId) {
            this.exportError = null
            try {
                const job = await apiService.get(`/exports/${exportJobId}`)
                this.updateExportJob(exportJobId, job)

                if (this.currentExportJob?.exportJobId === exportJobId) {
                    this.setCurrentExportJob(job)
                }

                return job
            } catch (error) {
                throw this.failExport(error, 'Failed to fetch export status')
            }
        },

        async fetchExportJobs(page = 0, size = 10) {
            this.exportError = null
            try {
                const response = await apiService.get('/exports', { page, size })
                this.setExportJobs(response.items)
                return response
            } catch (error) {
                throw this.failExport(error, 'Failed to fetch export jobs')
            }
        },

        async downloadExportFile(exportJobId) {
            this.exportError = null
            try {
                return await apiService.download(`/exports/${exportJobId}/content`)
            } catch (error) {
                throw this.failExport(error, 'Failed to download export')
            }
        },

        async deleteExportJob(exportJobId) {
            this.exportError = null
            try {
                await apiService.delete(`/exports/${exportJobId}`)
                this.removeExportJob(exportJobId)
                return true
            } catch (error) {
                throw this.failExport(error, 'Failed to delete export')
            }
        },

        // API Actions - Export (OwnTracks)
        // Convenience wrapper for createExportJob with OwnTracks format
        async createOwnTracksExportJob(dateRange, owntracksFormat = 'ocat') {
            return this.createExportJob(['raw_gps'], dateRange, 'owntracks', { owntracksFormat })
        },

        // API Actions - Export (GeoJSON)
        // Convenience wrapper for createExportJob with GeoJSON format
        async createGeoJsonExportJob(dateRange) {
            return this.createExportJob(['raw_gps'], dateRange, 'geojson')
        },

        // API Actions - Export (GPX)
        // Convenience wrapper for createExportJob with GPX format
        async createGpxExportJob(dateRange, zipPerTrip = false, zipGroupBy = 'individual') {
            return this.createExportJob(['raw_gps'], dateRange, 'gpx', { zipPerTrip, zipGroupBy })
        },

        // API Actions - Export (CSV)
        // Convenience wrapper for createExportJob with CSV format
        async createCsvExportJob(dateRange) {
            return this.createExportJob(['raw_gps'], dateRange, 'csv')
        },

        // Download CSV template
        async downloadCsvTemplate() {
            this.exportError = null
            try {
                return await apiService.download('/exports/csv-template')
            } catch (error) {
                throw this.failExport(error, 'Failed to download CSV template')
            }
        },

        // API Actions - Export single trip as GPX
        async exportTripAsGpx(tripId) {
            this.exportError = null
            try {
                return await apiService.download(`/exports/trips/${tripId}/gpx`)
            } catch (error) {
                throw this.failExport(error, 'Failed to export trip')
            }
        },

        // API Actions - Export single stay as GPX
        async exportStayAsGpx(stayId) {
            this.exportError = null
            try {
                return await apiService.download(`/exports/stays/${stayId}/gpx`)
            } catch (error) {
                throw this.failExport(error, 'Failed to export stay')
            }
        },

        async downloadDebugExport(request) {
            this.exportError = null
            try {
                const response = await apiService.post('/exports/debug', request, { responseType: 'blob' })
                if (!(response.data instanceof Blob)) {
                    throw new Error('Invalid debug export response')
                }

                const url = window.URL.createObjectURL(response.data)
                const link = document.createElement('a')
                link.href = url
                const disposition = response.headers?.['content-disposition'] || ''
                const match = disposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/)
                link.download = match?.[1]?.replace(/['"]/g, '') || 'geopulse-debug.zip'
                document.body.appendChild(link)
                link.click()
                document.body.removeChild(link)
                window.URL.revokeObjectURL(url)
                return true
            } catch (error) {
                throw this.failExport(error, 'Failed to export debug data')
            }
        },

        // Helper method for file uploads - decides between chunked and direct upload
        // Uses unified /import/upload endpoint for all formats
        async _performUpload(file, importFormat, options = {}) {
            // Check if file should use chunked upload (>80MB)
            if (chunkedUploadService.shouldUseChunkedUpload(file)) {
                // Use chunked upload for large files
                return await chunkedUploadService.uploadFile(file, importFormat, options, {
                    onProgress: (progress) => {
                        this.uploadProgress = progress
                    },
                    onChunkComplete: (index, completed, total) => {
                        this.currentChunkInfo = { index, completed, total }
                    }
                })
            } else {
                // Use direct upload for smaller files via unified endpoint
                const formData = new FormData()
                formData.append('file', file)
                formData.append('format', importFormat)
                formData.append('options', JSON.stringify(options))

                const response = await apiService.post('/imports', formData, {
                    onUploadProgress: (progressEvent) => {
                        // Cap at 99% during upload, reach 100% only when response received
                        const progress = Math.round((progressEvent.loaded * 99) / progressEvent.total)
                        this.uploadProgress = Math.min(progress, 99)
                    }
                })

                // Upload complete, show 100%
                this.uploadProgress = 100

                return response
            }
        },

        // API Actions - Import
        async _uploadImportFile(file, importFormat, options = {}) {
            this.isImporting = true
            this.isUploading = true
            this.uploadProgress = 0
            this.currentChunkInfo = null
            this.importError = null
            try {
                const response = await this._performUpload(file, importFormat, options)

                // Keep upload card visible for 500ms to show completion
                await new Promise(resolve => setTimeout(resolve, 500))

                this.setCurrentImportJob(response)
                this.addImportJob(response)

                return response
            } catch (error) {
                throw this.failImport(error, 'Failed to start import')
            } finally {
                this.isImporting = false
                this.isUploading = false
                this.uploadProgress = 0
                this.currentChunkInfo = null
            }
        },

        async uploadImportFile(file, options = {}) {
            return this._uploadImportFile(file, 'geopulse', options)
        },

        async uploadOwnTracksImportFile(file, options = {}) {
            return this._uploadImportFile(file, 'owntracks', options)
        },

        async uploadGpxImportFile(file, options = {}) {
            return this._uploadImportFile(file, 'gpx', options)
        },

        async uploadGoogleTimelineImportFile(file, options = {}) {
            return this._uploadImportFile(file, 'google-timeline', options)
        },

        async uploadGeoJsonImportFile(file, options = {}) {
            return this._uploadImportFile(file, 'geojson', options)
        },

        async uploadCsvImportFile(file, options = {}) {
            return this._uploadImportFile(file, 'csv', options)
        },

        async fetchImportStatus(importJobId) {
            this.importError = null
            try {
                const response = await apiService.get(`/imports/${importJobId}`)
                
                this.updateImportJob(importJobId, response)
                
                if (this.currentImportJob?.importJobId === importJobId) {
                    this.setCurrentImportJob(response)
                }
                
                return response
            } catch (error) {
                throw this.failImport(error, 'Failed to fetch import status')
            }
        },

        async fetchImportJobs(size = 10, page = 0) {
            this.importError = null
            try {
                const response = await apiService.get('/imports', { page, size })
                
                this.setImportJobs(response.items)
                
                return response
            } catch (error) {
                throw this.failImport(error, 'Failed to fetch import jobs')
            }
        },

        async uploadDebugImport(file, clearExistingData, updateTimelineConfig) {
            this.importError = null
            const formData = new FormData()
            formData.append('file', file)
            formData.append('clearExistingData', clearExistingData)
            formData.append('updateTimelineConfig', updateTimelineConfig)
            try {
                await apiService.post('/debug-imports', formData)
            } catch (error) {
                throw this.failImport(error, 'Failed to import debug data')
            }
        },

        // Utility actions
        pollJobStatus(jobId, isExport = true) {
            let timeoutId = null
            let isCancelled = false

            const poll = async () => {
                if (isCancelled) return

                try {
                    const response = isExport
                        ? await this.fetchExportStatus(jobId)
                        : await this.fetchImportStatus(jobId)

                    // Stop polling only when job completes or fails
                    if (['completed', 'failed'].includes(response.status)) {
                        return response
                    }

                    // Keep polling for processing/validating jobs
                    // Important: Timeline generation during imports can take 10-30 minutes
                    if (!isCancelled) {
                        timeoutId = setTimeout(() => poll(), 2000) // Poll every 2 seconds
                    }

                    return response
                } catch (error) {
                    if (isMaintenanceInterruption(error)) {
                        isCancelled = true
                        return
                    }
                    console.error('Polling error:', error)
                    // Retry on error after longer delay
                    if (!isCancelled) {
                        timeoutId = setTimeout(() => poll(), 5000)
                    }
                }
            }

            // Start polling
            poll()

            // Return cancel function
            return {
                cancel: () => {
                    isCancelled = true
                    if (timeoutId) {
                        clearTimeout(timeoutId)
                        timeoutId = null
                    }
                }
            }
        },

        // Clear all data
        clearAllData() {
            this.exportJobs = []
            this.importJobs = []
            this.currentExportJob = null
            this.currentImportJob = null
            this.importError = null
            this.isExporting = false
            this.isImporting = false
            this.currentChunkInfo = null
        },

        // Get data type display names
        getDataTypeDisplayName(dataType) {
            const displayNames = {
                rawgps: 'Raw GPS Data',
                favorites: 'Favorite Locations',
                reversegeocodinglocation: 'Reverse Geocoding Data',
                locationsources: 'Location Sources',
                userinfo: 'User Information',
                periodtags: 'Timeline Labels',
                timelineoverrides: 'Timeline Overrides',
                tripworkspace: 'Trip Plans',
                notificationtemplates: 'Notification Templates',
                geofencing: 'Geofences',
                notes: 'Timeline Notes',
                weathersamples: 'Weather Samples',
                mapmatching: 'Map Matching',
                statistics: 'Statistics'
            }
            return displayNames[dataType] || dataType
        },

        // Get file size display
        getFileSizeDisplay(bytes) {
            if (!bytes) return 'Unknown'
            
            const units = ['B', 'KB', 'MB', 'GB']
            let size = bytes
            let unitIndex = 0

            while (size >= 1024 && unitIndex < units.length - 1) {
                size /= 1024
                unitIndex++
            }

            return `${size.toFixed(1)} ${units[unitIndex]}`
        },

        // Get status display info
        getStatusDisplayInfo(status) {
            const statusInfo = {
                processing: { label: 'Processing', severity: 'info', icon: 'pi pi-spin pi-spinner' },
                validating: { label: 'Validating', severity: 'info', icon: 'pi pi-spin pi-spinner' },
                completed: { label: 'Completed', severity: 'success', icon: 'pi pi-check' },
                failed: { label: 'Failed', severity: 'error', icon: 'pi pi-times' }
            }
            return statusInfo[status] || { label: status, severity: 'secondary', icon: 'pi pi-question' }
        }
    }
})
