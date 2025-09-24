import {useTimezone} from '@/composables/useTimezone'
import {formatDistance, formatDurationSmart} from "@/utils/calculationsHelpers"
import { findOriginStay, findDestinationStay } from '@/utils/tripHelpers'

const timezone = useTimezone()

/**
 * Data Exporter utility for GeoPulse Data Tables
 * Handles CSV export functionality for stays, trips, and data gaps
 */
export class DataExporter {
    /**
     * Export stays data to CSV
     * @param {Array} stays - Array of stay objects
     * @param {Array} dateRange - Date range [startDate, endDate]
     * @returns {Promise<void>}
     */
    static async exportStays(stays, dateRange) {
        const headers = [
            'Start Date',
            'End Date',
            'Duration',
            'Duration (minutes)',
            'Location Name',
            'Latitude',
            'Longitude'
        ]

        const rows = stays.map(stay => {
            // Calculate end time from start time + duration (in seconds)
            const startTime = timezone.fromUtc(stay.timestamp)
            const endTime = startTime.clone().add(stay.stayDuration || 0, 'seconds')

            return [
                this.formatDateTime(stay.timestamp),
                this.formatDateTime(endTime),
                formatDurationSmart(stay.stayDuration),
                Math.round(stay.stayDuration / 60),
                this.sanitizeForCSV(stay.locationName || 'Unknown Location'),
                stay.latitude?.toFixed(6) || '',
                stay.longitude?.toFixed(6) || ''
            ]
        })

        const filename = this.generateFilename('stays', dateRange)
        return this.downloadCSV(headers, rows, filename)
    }

    /**
     * Export trips data to CSV
     * @param {Array} stays - Array of stay objects
     * @param {Array} trips - Array of trip objects
     * @param {Array} dateRange - Date range [startDate, endDate]
     * @returns {Promise<void>}
     */
    static async exportTrips(stays, trips, dateRange) {
        const headers = [
            'Start Date',
            'End Date',
            'Duration',
            'Duration (minutes)',
            'Distance, meters',
            'Origin',
            'Origin Latitude',
            'Origin Longitude',
            'Destination',
            'Destination Latitude',
            'Destination Longitude',
            'Transport Mode',
        ]

        const rows = trips.map(trip => {
            const startTime = timezone.fromUtc(trip.timestamp)
            const endTime = startTime.clone().add(trip.tripDuration || 0, 'seconds')
            const origin = findOriginStay(stays, trip.timestamp)?.locationName
            const destination = findDestinationStay(stays, endTime.toISOString())?.locationName

            return [
                this.formatDateTime(startTime),
                this.formatDateTime(endTime),
                formatDurationSmart(trip.tripDuration),
                Math.round(trip.tripDuration / 60),
                trip.distanceMeters,
                this.sanitizeForCSV(origin || 'Unknown Origin'),
                trip.latitude?.toFixed(6) || '',
                trip.longitude?.toFixed(6) || '',
                this.sanitizeForCSV(destination || 'Unknown Destination'),
                trip.endLatitude?.toFixed(6) || '',
                trip.endLongitude?.toFixed(6) || '',
                this.sanitizeForCSV(trip.movementType || ''),
            ]
        })

        const filename = this.generateFilename('trips', dateRange)
        return this.downloadCSV(headers, rows, filename)
    }

    /**
     * Export data gaps to CSV
     * @param {Array} dataGaps - Array of data gap objects
     * @param {Array} dateRange - Date range [startDate, endDate]
     * @returns {Promise<void>}
     */
    static async exportDataGaps(dataGaps, dateRange) {
        const headers = [
            'Start Date',
            'End Date',
            'Duration (minutes)',
            'Duration'
        ]

        const rows = dataGaps.map(gap => [
            this.formatDateTime(gap.startTime),
            this.formatDateTime(gap.endTime),
            gap.durationMinutes,
            formatDurationSmart(gap.durationSeconds),
        ])

        const filename = this.generateFilename('data_gaps', dateRange)
        return this.downloadCSV(headers, rows, filename)
    }



    // Helper Methods

    /**
     * Format date for CSV export
     * @param {string|Date} timestamp
     * @returns {string}
     */
    static formatDate(timestamp) {
        try {
            return timezone.format(timestamp, 'YYYY-MM-DD')
        } catch (error) {
            console.warn('Error formatting date:', error)
            return ''
        }
    }

    static formatDateTime(timestamp) {
        try {
            return timezone.format(timestamp, 'YYYY-MM-DD HH:mm')
        } catch (error) {
            console.warn('Error formatting time:', error)
            return ''
        }
    }

    /**
     * Sanitize text for CSV format
     * @param {string} text
     * @returns {string}
     */
    static sanitizeForCSV(text) {
        if (!text) return ''

        // Convert to string and remove any problematic characters
        const cleaned = String(text)
            .replace(/"/g, '""') // Escape quotes
            .replace(/[\r\n]/g, ' ') // Replace line breaks with spaces
            .trim()

        return cleaned
    }

    /**
     * Generate filename for CSV export
     * @param {string} dataType
     * @param {Array} dateRange
     * @returns {string}
     */
    static generateFilename(dataType, dateRange) {
        const dateStr = dateRange && dateRange[0] && dateRange[1]
            ? `${this.formatDate(dateRange[0])}_to_${this.formatDate(dateRange[1])}`
            : this.formatDate(new Date())

        const timestamp = timezone.format(new Date(), 'HHmm')
        return `geopulse_${dataType}_${dateStr}_${timestamp}.csv`
    }

    /**
     * Download CSV file
     * @param {Array} headers
     * @param {Array} rows
     * @param {string} filename
     * @returns {Promise<void>}
     */
    static downloadCSV(headers, rows, filename) {
        return new Promise((resolve, reject) => {
            try {
                // Create CSV content with proper escaping
                const csvRows = [headers, ...rows]
                const csvContent = csvRows
                    .map(row =>
                        row.map(cell => {
                            const cellStr = String(cell || '')
                            // Wrap in quotes if contains comma, quote, or newline
                            if (cellStr.includes(',') || cellStr.includes('"') || cellStr.includes('\n')) {
                                return `"${cellStr}"`
                            }
                            return cellStr
                        }).join(',')
                    )
                    .join('\n')

                // Add BOM for proper UTF-8 encoding in Excel
                const BOM = '\uFEFF'
                const blob = new Blob([BOM + csvContent], {
                    type: 'text/csv;charset=utf-8;'
                })

                // Create download link
                const link = document.createElement('a')
                if (link.download !== undefined) {
                    const url = URL.createObjectURL(blob)
                    link.setAttribute('href', url)
                    link.setAttribute('download', filename)
                    link.style.visibility = 'hidden'

                    // Trigger download
                    document.body.appendChild(link)
                    link.click()
                    document.body.removeChild(link)

                    // Cleanup
                    setTimeout(() => URL.revokeObjectURL(url), 1000)

                    resolve()
                } else {
                    reject(new Error('Browser does not support file downloads'))
                }
            } catch (error) {
                console.error('CSV export error:', error)
                reject(error)
            }
        })
    }
}

export default DataExporter