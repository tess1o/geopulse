import { defineStore } from 'pinia'
import apiService from '../utils/apiService'

export const useExportImportStore = defineStore('exportImport', {
    state: () => ({
        // Export state
        exportJobs: [],
        currentExportJob: null,
        
        // Import state
        importJobs: [],
        currentImportJob: null,
        
        // UI state
        isExporting: false,
        isImporting: false
    }),

    getters: {
        // Export getters
        getExportJobs: (state) => state.exportJobs,
        getCurrentExportJob: (state) => state.currentExportJob,
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
            this.currentImportJob = job
        },

        addImportJob(job) {
            const existingIndex = this.importJobs.findIndex(j => j.importJobId === job.importJobId)
            if (existingIndex !== -1) {
                this.importJobs[existingIndex] = job
            } else {
                this.importJobs.unshift(job)
            }
        },

        updateImportJob(jobId, updates) {
            const job = this.importJobs.find(j => j.importJobId === jobId)
            if (job) {
                Object.assign(job, updates)
            }
            if (this.currentImportJob?.importJobId === jobId) {
                Object.assign(this.currentImportJob, updates)
            }
        },

        removeImportJob(jobId) {
            this.importJobs = this.importJobs.filter(j => j.importJobId !== jobId)
            if (this.currentImportJob?.importJobId === jobId) {
                this.currentImportJob = null
            }
        },

        // API Actions - Export
        async createExportJob(dataTypes, dateRange, format = 'json') {
            this.isExporting = true
            try {
                const response = await apiService.post('/export/create', {
                    dataTypes,
                    dateRange,
                    format
                })

                // Handle successful response
                if (response.success) {
                    this.setCurrentExportJob(response)
                    this.addExportJob(response)
                    return response
                } else {
                    throw new Error(response.error?.message || 'Export creation failed')
                }
            } catch (error) {
                // Handle API error responses
                if (error.response?.data?.error) {
                    const apiError = error.response.data.error
                    throw new Error(apiError.message || 'Export creation failed')
                }
                throw error
            } finally {
                this.isExporting = false
            }
        },

        async fetchExportStatus(exportJobId) {
            try {
                const response = await apiService.get(`/export/status/${exportJobId}`)
                
                // Handle successful response
                if (response.success) {
                    this.updateExportJob(exportJobId, response)
                    
                    if (this.currentExportJob?.exportJobId === exportJobId) {
                        this.setCurrentExportJob(response)
                    }
                    
                    return response
                } else {
                    throw new Error(response.error?.message || 'Failed to fetch export status')
                }
            } catch (error) {
                // Handle API error responses
                if (error.response?.data?.error) {
                    const apiError = error.response.data.error
                    throw new Error(apiError.message || 'Failed to fetch export status')
                }
                throw error
            }
        },

        async fetchExportJobs(limit = 10, offset = 0) {
            try {
                const response = await apiService.get(`/export/jobs?limit=${limit}&offset=${offset}`)
                
                // Handle successful response
                if (response.success) {
                    this.setExportJobs(response.jobs)
                    return response
                } else {
                    throw new Error(response.error?.message || 'Failed to fetch export jobs')
                }
            } catch (error) {
                // Handle API error responses
                if (error.response?.data?.error) {
                    const apiError = error.response.data.error
                    throw new Error(apiError.message || 'Failed to fetch export jobs')
                }
                throw error
            }
        },

        async downloadExportFile(exportJobId) {
            try {
                // This will trigger a file download directly from the backend
                const response = await apiService.download(`/export/download/${exportJobId}`)
                return response
            } catch (error) {
                throw error
            }
        },

        async deleteExportJob(exportJobId) {
            try {
                const response = await apiService.delete(`/export/jobs/${exportJobId}`)
                // Handle successful response
                if (response.status === 'success') {
                    this.removeExportJob(exportJobId)
                    return true
                } else {
                    throw new Error(response.error?.message || 'Failed to delete export job')
                }
            } catch (error) {
                // Handle API error responses
                if (error.response?.data?.error) {
                    const apiError = error.response.data.error
                    throw new Error(apiError.message || 'Failed to delete export job')
                }
                throw error
            }
        },

        // API Actions - Export (OwnTracks)
        async createOwnTracksExportJob(dateRange) {
            this.isExporting = true
            try {
                const response = await apiService.post('/export/owntracks/create', {
                    dateRange
                })

                // Handle successful response
                if (response.success) {
                    this.setCurrentExportJob(response)
                    this.addExportJob(response)
                    return response
                } else {
                    throw new Error(response.error?.message || 'OwnTracks export creation failed')
                }
            } catch (error) {
                // Handle API error responses
                if (error.response?.data?.error) {
                    const apiError = error.response.data.error
                    throw new Error(apiError.message || 'OwnTracks export creation failed')
                }
                throw error
            } finally {
                this.isExporting = false
            }
        },

        // API Actions - Export (GeoJSON)
        async createGeoJsonExportJob(dateRange) {
            this.isExporting = true
            try {
                const response = await apiService.post('/export/geojson/create', {
                    dateRange
                })

                // Handle successful response
                if (response.success) {
                    this.setCurrentExportJob(response)
                    this.addExportJob(response)
                    return response
                } else {
                    throw new Error(response.error?.message || 'GeoJSON export creation failed')
                }
            } catch (error) {
                // Handle API error responses
                if (error.response?.data?.error) {
                    const apiError = error.response.data.error
                    throw new Error(apiError.message || 'GeoJSON export creation failed')
                }
                throw error
            } finally {
                this.isExporting = false
            }
        },

        // API Actions - Export (GPX)
        async createGpxExportJob(dateRange, zipPerTrip = false) {
            this.isExporting = true
            try {
                const response = await apiService.post('/export/gpx/create', {
                    dateRange,
                    options: {
                        zipPerTrip
                    }
                })

                // Handle successful response
                if (response.success) {
                    this.setCurrentExportJob(response)
                    this.addExportJob(response)
                    return response
                } else {
                    throw new Error(response.error?.message || 'GPX export creation failed')
                }
            } catch (error) {
                // Handle API error responses
                if (error.response?.data?.error) {
                    const apiError = error.response.data.error
                    throw new Error(apiError.message || 'GPX export creation failed')
                }
                throw error
            } finally {
                this.isExporting = false
            }
        },

        // API Actions - Export single trip as GPX
        async exportTripAsGpx(tripId) {
            try {
                // This will trigger a file download directly from the backend
                const response = await apiService.download(`/export/gpx/trip/${tripId}`)
                return response
            } catch (error) {
                throw error
            }
        },

        // API Actions - Export single stay as GPX
        async exportStayAsGpx(stayId) {
            try {
                // This will trigger a file download directly from the backend
                const response = await apiService.download(`/export/gpx/stay/${stayId}`)
                return response
            } catch (error) {
                throw error
            }
        },

        // API Actions - Import
        async uploadImportFile(file, options = {}) {
            this.isImporting = true
            try {
                const formData = new FormData()
                formData.append('file', file)
                formData.append('options', JSON.stringify(options))

                const response = await apiService.post('/import/geopulse/upload', formData, {
                    headers: {
                        'Content-Type': 'multipart/form-data'
                    }
                })

                this.setCurrentImportJob(response)
                this.addImportJob(response)
                
                return response
            } catch (error) {
                throw error
            } finally {
                this.isImporting = false
            }
        },

        // API Actions - Import (OwnTracks)
        async uploadOwnTracksImportFile(file, options = {}) {
            this.isImporting = true
            try {
                const formData = new FormData()
                formData.append('file', file)
                formData.append('options', JSON.stringify(options))

                const response = await apiService.post('/import/owntracks/upload', formData, {
                    headers: {
                        'Content-Type': 'multipart/form-data'
                    }
                })

                this.setCurrentImportJob(response)
                this.addImportJob(response)
                
                return response
            } catch (error) {
                throw error
            } finally {
                this.isImporting = false
            }
        },

        async uploadGpxImportFile(file, options = {}) {
            this.isImporting = true
            try {
                const formData = new FormData()
                formData.append('file', file)
                formData.append('options', JSON.stringify(options))

                const response = await apiService.post('/import/gpx/upload', formData, {
                    headers: {
                        'Content-Type': 'multipart/form-data'
                    }
                })

                this.setCurrentImportJob(response)
                this.addImportJob(response)

                return response
            } catch (error) {
                throw error
            } finally {
                this.isImporting = false
            }
        },

        // API Actions - Import (Google Timeline)
        async uploadGoogleTimelineImportFile(file, options = {}) {
            this.isImporting = true
            try {
                const formData = new FormData()
                formData.append('file', file)
                formData.append('options', JSON.stringify(options))

                const response = await apiService.post('/import/google-timeline/upload', formData, {
                    headers: {
                        'Content-Type': 'multipart/form-data'
                    }
                })

                this.setCurrentImportJob(response)
                this.addImportJob(response)

                return response
            } catch (error) {
                throw error
            } finally {
                this.isImporting = false
            }
        },

        // API Actions - Import (GeoJSON)
        async uploadGeoJsonImportFile(file, options = {}) {
            this.isImporting = true
            try {
                const formData = new FormData()
                formData.append('file', file)
                formData.append('options', JSON.stringify(options))

                const response = await apiService.post('/import/geojson/upload', formData, {
                    headers: {
                        'Content-Type': 'multipart/form-data'
                    }
                })

                this.setCurrentImportJob(response)
                this.addImportJob(response)

                return response
            } catch (error) {
                throw error
            } finally {
                this.isImporting = false
            }
        },

        async fetchImportStatus(importJobId) {
            try {
                const response = await apiService.get(`/import/status/${importJobId}`)
                
                this.updateImportJob(importJobId, response)
                
                if (this.currentImportJob?.importJobId === importJobId) {
                    this.setCurrentImportJob(response)
                }
                
                return response
            } catch (error) {
                throw error
            }
        },

        async fetchImportJobs(limit = 10, offset = 0) {
            try {
                const response = await apiService.get(`/import/jobs?limit=${limit}&offset=${offset}`)
                
                this.setImportJobs(response.jobs)
                
                return response
            } catch (error) {
                throw error
            }
        },

        // Utility actions
        async pollJobStatus(jobId, isExport = true) {
            const maxAttempts = 60 // 5 minutes with 5-second intervals
            let attempts = 0

            const poll = async () => {
                attempts++
                
                try {
                    const response = isExport 
                        ? await this.fetchExportStatus(jobId)
                        : await this.fetchImportStatus(jobId)

                    if (['completed', 'failed'].includes(response.status)) {
                        return response
                    }

                    if (attempts < maxAttempts) {
                        setTimeout(() => poll(), 2000) // Poll every 2 seconds
                    }

                    return response
                } catch (error) {
                    if (attempts < maxAttempts) {
                        setTimeout(() => poll(), 5000) // Retry after 5 seconds on error
                    }
                    throw error
                }
            }

            return poll()
        },

        // Clear all data
        clearAllData() {
            this.exportJobs = []
            this.importJobs = []
            this.currentExportJob = null
            this.currentImportJob = null
            this.isExporting = false
            this.isImporting = false
        },

        // Get data type display names
        getDataTypeDisplayName(dataType) {
            const displayNames = {
                rawgps: 'Raw GPS Data',
                favorites: 'Favorite Locations',
                reversegeocodinglocation: 'Reverse Geocoding Data',
                locationsources: 'Location Sources',
                userinfo: 'User Information',
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