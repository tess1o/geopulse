import {useTimezone} from '@/composables/useTimezone'
import {formatDistance, formatDurationSmart} from "@/utils/calculationsHelpers"
import { findOriginStay, findDestinationStay } from '@/utils/tripHelpers'
import { useUnits } from '@/composables/useUnits'

const timezone = useTimezone()
const { getDistanceUnit } = useUnits()

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
        const locationMode = timezone.isLocationTimeMode()
        if (locationMode) headers.push('Timezone')

        const rows = stays.map(stay => {
            // Calculate end time from start time + duration (in seconds)
            const startTime = timezone.fromUtc(stay.timestamp)
            const endTime = startTime.clone().add(stay.stayDuration || 0, 'seconds')

            const row = [
                this.formatDateTime(stay.timestamp, stay.locationTimezone),
                this.formatDateTime(endTime, stay.locationTimezone),
                formatDurationSmart(stay.stayDuration),
                Math.round(stay.stayDuration / 60),
                this.sanitizeForCSV(stay.locationName || 'Unknown Location'),
                stay.latitude?.toFixed(6) || '',
                stay.longitude?.toFixed(6) || ''
            ]
            if (locationMode) row.push(timezone.getDisplayZoneId(stay.locationTimezone))
            return row
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
        const distanceUnit = getDistanceUnit()
        const isMiles = distanceUnit === 'MILES'

        const headers = [
            'Start Date',
            'End Date',
            'Duration',
            'Duration (minutes)',
            isMiles ? 'Distance, miles' : 'Distance, meters',
            'Origin',
            'Origin Latitude',
            'Origin Longitude',
            'Destination',
            'Destination Latitude',
            'Destination Longitude',
            'Transport Mode',
        ]
        const locationMode = timezone.isLocationTimeMode()
        if (locationMode) headers.push('Start Timezone', 'End Timezone')

        const rows = trips.map(trip => {
            const startTime = timezone.fromUtc(trip.timestamp)
            const endTime = startTime.clone().add(trip.tripDuration || 0, 'seconds')
            const origin = findOriginStay(stays, trip.timestamp)?.locationName
            const destination = findDestinationStay(stays, endTime.toISOString())?.locationName
            
            let distance;
            if (isMiles) {
                distance = (trip.distanceMeters * 0.000621371).toFixed(2);
            } else {
                distance = Math.round(trip.distanceMeters);
            }

            const row = [
                this.formatDateTime(startTime, trip.startLocationTimezone),
                this.formatDateTime(endTime, trip.endLocationTimezone),
                formatDurationSmart(trip.tripDuration),
                Math.round(trip.tripDuration / 60),
                distance,
                this.sanitizeForCSV(origin || 'Unknown Origin'),
                trip.latitude?.toFixed(6) || '',
                trip.longitude?.toFixed(6) || '',
                this.sanitizeForCSV(destination || 'Unknown Destination'),
                trip.endLatitude?.toFixed(6) || '',
                trip.endLongitude?.toFixed(6) || '',
                this.sanitizeForCSV(trip.movementType || ''),
            ]
            if (locationMode) {
                row.push(
                    timezone.getDisplayZoneId(trip.startLocationTimezone),
                    timezone.getDisplayZoneId(trip.endLocationTimezone)
                )
            }
            return row
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
        const locationMode = timezone.isLocationTimeMode()
        if (locationMode) headers.push('Start Timezone', 'End Timezone')

        const rows = dataGaps.map(gap => {
            const row = [
                this.formatDateTime(gap.startTime, gap.startLocationTimezone),
                this.formatDateTime(gap.endTime, gap.endLocationTimezone),
                gap.durationMinutes,
                formatDurationSmart(gap.durationSeconds),
            ]
            if (locationMode) {
                row.push(
                    timezone.getDisplayZoneId(gap.startLocationTimezone),
                    timezone.getDisplayZoneId(gap.endLocationTimezone)
                )
            }
            return row
        })

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

    // locationTimezone only matters in the "location" time mode; otherwise this is the profile timezone.
    static formatDateTime(timestamp, locationTimezone) {
        try {
            return timezone.formatInLocationZone(timestamp, locationTimezone, 'YYYY-MM-DD HH:mm')
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
