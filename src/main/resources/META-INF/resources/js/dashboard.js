/**
 * JavaScript for the dashboard page
 * Handles location data fetching, map display, and date selection
 */

document.addEventListener('DOMContentLoaded', async () => {
    // Check if authenticated
    Auth.checkAuth(true, '/login.html');

    // DOM elements
    const alertContainer = document.getElementById('alert-container');
    const loadingElement = document.getElementById('loading');
    const pathInfoElement = document.getElementById('path-info');
    const noDataElement = document.getElementById('no-data');
    const pointCountElement = document.getElementById('point-count');
    const timeSpanElement = document.getElementById('time-span');
    const avgSpeedElement = document.getElementById('avg-speed');
    const totalDistanceElement = document.getElementById('total-distance');
    const customDateRangeElement = document.getElementById('custom-date-range');
    const timelineContainerElement = document.getElementById('timeline-container');
    const exportCsvBtn = document.getElementById('export-csv');
    const exportJsonBtn = document.getElementById('export-json');

    // Date selection buttons
    const todayBtn = document.getElementById('today-btn');
    const yesterdayBtn = document.getElementById('yesterday-btn');
    const weekBtn = document.getElementById('week-btn');
    const monthBtn = document.getElementById('month-btn');
    const customBtn = document.getElementById('custom-btn');
    const applyCustomDateBtn = document.getElementById('apply-custom-date');

    // Logout link
    const logoutLink = document.getElementById('logout-link');

    // Map instance
    let map = null;
    let pathLayer = null;

    /**
     * Show an alert message
     * @param {string} message - The message to display
     * @param {string} type - The type of alert (success, warning, error)
     */
    function showAlert(message, type = 'error') {
        alertContainer.innerHTML = `
            <div class="alert alert-${type}">
                ${message}
            </div>
        `;
    }

    /**
     * Clear any displayed alerts
     */
    function clearAlert() {
        alertContainer.innerHTML = '';
    }

    /**
     * Initialize the map
     */
    function initMap() {
        if (map) return; // Map already initialized

        map = L.map('map-container').setView([0, 0], 2);

        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        }).addTo(map);

        pathLayer = L.layerGroup().addTo(map);
    }

    /**
     * Format a date to ISO string for API requests
     * @param {Date} date - The date to format
     * @returns {string} ISO formatted date string
     */
    function formatDateForApi(date) {
        return date.toISOString();
    }

    /**
     * Calculate time span between two dates
     * @param {Date} start - Start date
     * @param {Date} end - End date
     * @returns {string} Formatted time span
     */
    function calculateTimeSpan(start, end) {
        const diffMs = end - start;
        const hours = Math.floor(diffMs / (1000 * 60 * 60));
        const minutes = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));

        return `${hours}h ${minutes}m`;
    }

    /**
     * Calculate distance between two points using Haversine formula
     * @param {number} lat1 - Latitude of point 1
     * @param {number} lon1 - Longitude of point 1
     * @param {number} lat2 - Latitude of point 2
     * @param {number} lon2 - Longitude of point 2
     * @returns {number} Distance in kilometers
     */
    function calculateDistance(lat1, lon1, lat2, lon2) {
        const R = 6371; // Radius of the earth in km
        const dLat = (lat2 - lat1) * Math.PI / 180;
        const dLon = (lon2 - lon1) * Math.PI / 180;
        const a = 
            Math.sin(dLat/2) * Math.sin(dLat/2) +
            Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * 
            Math.sin(dLon/2) * Math.sin(dLon/2);
        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }

    /**
     * Calculate total distance of a path
     * @param {Array} points - Array of location points
     * @returns {number} Total distance in kilometers
     */
    function calculateTotalDistance(points) {
        if (!points || points.length < 2) return 0;

        let totalDistance = 0;

        for (let i = 1; i < points.length; i++) {
            const prevPoint = points[i-1];
            const currPoint = points[i];

            totalDistance += calculateDistance(
                prevPoint.latitude, 
                prevPoint.longitude, 
                currPoint.latitude, 
                currPoint.longitude
            );
        }

        return totalDistance;
    }

    /**
     * Calculate average speed from points
     * @param {Array} points - Array of location points
     * @param {number} totalDistance - Total distance in kilometers
     * @returns {number} Average speed in km/h
     */
    function calculateAverageSpeed(points, totalDistance) {
        if (!points || points.length < 2) return 0;

        const firstPoint = points[0];
        const lastPoint = points[points.length - 1];

        const startTime = new Date(firstPoint.timestamp);
        const endTime = new Date(lastPoint.timestamp);

        const durationHours = (endTime - startTime) / (1000 * 60 * 60);

        if (durationHours === 0) return 0;

        return totalDistance / durationHours;
    }

    /**
     * Display path statistics
     * @param {Object} pathData - The path data
     */
    function displayPathStats(pathData) {
        const points = pathData.points;

        if (!points || points.length === 0) {
            pathInfoElement.style.display = 'none';
            timelineContainerElement.style.display = 'none';
            noDataElement.style.display = 'block';
            return;
        }

        // Calculate statistics
        const pointCount = points.length;
        const firstPoint = points[0];
        const lastPoint = points[points.length - 1];
        const startTime = new Date(firstPoint.timestamp);
        const endTime = new Date(lastPoint.timestamp);
        const timeSpan = calculateTimeSpan(startTime, endTime);
        const totalDistance = calculateTotalDistance(points);
        const avgSpeed = calculateAverageSpeed(points, totalDistance);

        // Update UI
        pointCountElement.textContent = pointCount;
        timeSpanElement.textContent = timeSpan;
        totalDistanceElement.textContent = `${totalDistance.toFixed(2)} km`;
        avgSpeedElement.textContent = `${avgSpeed.toFixed(2)} km/h`;

        // Show path info and timeline
        pathInfoElement.style.display = 'block';
        timelineContainerElement.style.display = 'block';
        noDataElement.style.display = 'none';
    }

    /**
     * Display path on map
     * @param {Object} pathData - The path data
     */
    function displayPathOnMap(pathData) {
        const points = pathData.points;

        if (!points || points.length === 0) {
            return;
        }

        // Clear previous path
        pathLayer.clearLayers();

        // Create path polyline
        const pathCoordinates = points.map(point => [point.latitude, point.longitude]);
        const pathPolyline = L.polyline(pathCoordinates, { color: 'blue', weight: 3 }).addTo(pathLayer);

        // Add markers for start and end points
        const startPoint = points[0];
        const endPoint = points[points.length - 1];

        const startMarker = L.marker([startPoint.latitude, startPoint.longitude], {
            icon: L.divIcon({
                className: 'custom-marker start-marker',
                html: '<div style="background-color: green; width: 12px; height: 12px; border-radius: 50%;"></div>',
                iconSize: [12, 12]
            })
        }).addTo(pathLayer);

        const endMarker = L.marker([endPoint.latitude, endPoint.longitude], {
            icon: L.divIcon({
                className: 'custom-marker end-marker',
                html: '<div style="background-color: red; width: 12px; height: 12px; border-radius: 50%;"></div>',
                iconSize: [12, 12]
            })
        }).addTo(pathLayer);

        // Add tooltips
        startMarker.bindTooltip(`Start: ${new Date(startPoint.timestamp).toLocaleString()}`);
        endMarker.bindTooltip(`End: ${new Date(endPoint.timestamp).toLocaleString()}`);

        // Fit map to path bounds
        map.fitBounds(pathPolyline.getBounds(), { padding: [50, 50] });
    }

    /**
     * Fetch location path data for a time period
     * @param {Date} startTime - Start time
     * @param {Date} endTime - End time
     */
    async function fetchLocationPath(startTime, endTime) {
        clearAlert();
        loadingElement.style.display = 'block';
        pathInfoElement.style.display = 'none';
        timelineContainerElement.style.display = 'none';
        noDataElement.style.display = 'none';

        try {
            // Format dates for API
            const startParam = formatDateForApi(startTime);
            const endParam = formatDateForApi(endTime);

            // Fetch path data
            const response = await Auth.apiRequest(`/locations/path?startTime=${startParam}&endTime=${endParam}`);

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.message || 'Failed to fetch location data');
            }

            const data = await response.json();
            const pathData = data.data;

            // Initialize map if not already done
            initMap();

            // Display path data
            displayPathStats(pathData);
            displayPathOnMap(pathData);
        } catch (error) {
            console.error('Error fetching location path:', error);
            showAlert(error.message || 'Failed to fetch location data');
            noDataElement.style.display = 'block';
        } finally {
            loadingElement.style.display = 'none';
        }
    }

    /**
     * Get today's date range
     * @returns {Object} Object with start and end dates
     */
    function getTodayRange() {
        const now = new Date();
        const start = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0);
        return { start, end: now };
    }

    /**
     * Get yesterday's date range
     * @returns {Object} Object with start and end dates
     */
    function getYesterdayRange() {
        const now = new Date();
        const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0);
        const start = new Date(end);
        start.setDate(start.getDate() - 1);
        return { start, end };
    }

    /**
     * Get last week's date range
     * @returns {Object} Object with start and end dates
     */
    function getLastWeekRange() {
        const now = new Date();
        const start = new Date(now);
        start.setDate(start.getDate() - 7);
        return { start, end: now };
    }

    /**
     * Get last month's date range
     * @returns {Object} Object with start and end dates
     */
    function getLastMonthRange() {
        const now = new Date();
        const start = new Date(now);
        start.setDate(start.getDate() - 30);
        return { start, end: now };
    }

    /**
     * Set active button
     * @param {HTMLElement} activeBtn - The active button
     */
    function setActiveButton(activeBtn) {
        // Remove active class from all buttons
        [todayBtn, yesterdayBtn, weekBtn, monthBtn, customBtn].forEach(btn => {
            btn.classList.remove('active');
        });

        // Add active class to the clicked button
        activeBtn.classList.add('active');
    }

    /**
     * Export location data as CSV
     * @param {Date} startTime - Start time
     * @param {Date} endTime - End time
     */
    async function exportLocationDataAsCsv(startTime, endTime) {
        try {
            // Format dates for API
            const startParam = formatDateForApi(startTime);
            const endParam = formatDateForApi(endTime);

            // Show loading state
            exportCsvBtn.disabled = true;
            exportCsvBtn.textContent = 'Exporting...';

            // Call the export endpoint
            const response = await Auth.apiRequest(`/locations/export/csv?startTime=${startParam}&endTime=${endParam}`);

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.message || 'Failed to export data as CSV');
            }

            // Get the CSV data
            const blob = await response.blob();

            // Create a download link
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.style.display = 'none';
            a.href = url;
            a.download = `location-data-${startParam.slice(0, 10)}-to-${endParam.slice(0, 10)}.csv`;

            // Trigger download
            document.body.appendChild(a);
            a.click();

            // Clean up
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);

            showAlert('CSV export successful!', 'success');
        } catch (error) {
            console.error('Error exporting CSV:', error);
            showAlert(error.message || 'Failed to export data as CSV');
        } finally {
            // Reset button state
            exportCsvBtn.disabled = false;
            exportCsvBtn.textContent = 'Export CSV';
        }
    }

    /**
     * Export location data as JSON
     * @param {Date} startTime - Start time
     * @param {Date} endTime - End time
     */
    async function exportLocationDataAsJson(startTime, endTime) {
        try {
            // Format dates for API
            const startParam = formatDateForApi(startTime);
            const endParam = formatDateForApi(endTime);

            // Show loading state
            exportJsonBtn.disabled = true;
            exportJsonBtn.textContent = 'Exporting...';

            // Call the export endpoint
            const response = await Auth.apiRequest(`/locations/export/json?startTime=${startParam}&endTime=${endParam}`);

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.message || 'Failed to export data as JSON');
            }

            // Get the JSON data
            const blob = await response.blob();

            // Create a download link
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.style.display = 'none';
            a.href = url;
            a.download = `location-data-${startParam.slice(0, 10)}-to-${endParam.slice(0, 10)}.json`;

            // Trigger download
            document.body.appendChild(a);
            a.click();

            // Clean up
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);

            showAlert('JSON export successful!', 'success');
        } catch (error) {
            console.error('Error exporting JSON:', error);
            showAlert(error.message || 'Failed to export data as JSON');
        } finally {
            // Reset button state
            exportJsonBtn.disabled = false;
            exportJsonBtn.textContent = 'Export JSON';
        }
    }

    // Initialize the page
    async function init() {
        // Initialize map
        initMap();

        // Current date range for exports
        let currentStartTime = getTodayRange().start;
        let currentEndTime = getTodayRange().end;

        // Set up event listeners for date selection buttons
        todayBtn.addEventListener('click', () => {
            setActiveButton(todayBtn);
            customDateRangeElement.style.display = 'none';
            const { start, end } = getTodayRange();
            currentStartTime = start;
            currentEndTime = end;
            fetchLocationPath(start, end);
        });

        yesterdayBtn.addEventListener('click', () => {
            setActiveButton(yesterdayBtn);
            customDateRangeElement.style.display = 'none';
            const { start, end } = getYesterdayRange();
            currentStartTime = start;
            currentEndTime = end;
            fetchLocationPath(start, end);
        });

        weekBtn.addEventListener('click', () => {
            setActiveButton(weekBtn);
            customDateRangeElement.style.display = 'none';
            const { start, end } = getLastWeekRange();
            currentStartTime = start;
            currentEndTime = end;
            fetchLocationPath(start, end);
        });

        monthBtn.addEventListener('click', () => {
            setActiveButton(monthBtn);
            customDateRangeElement.style.display = 'none';
            const { start, end } = getLastMonthRange();
            currentStartTime = start;
            currentEndTime = end;
            fetchLocationPath(start, end);
        });

        customBtn.addEventListener('click', () => {
            setActiveButton(customBtn);
            customDateRangeElement.style.display = 'block';

            // Set default values for custom date inputs
            const now = new Date();
            const oneWeekAgo = new Date(now);
            oneWeekAgo.setDate(oneWeekAgo.getDate() - 7);

            document.getElementById('start-date').value = oneWeekAgo.toISOString().slice(0, 16);
            document.getElementById('end-date').value = now.toISOString().slice(0, 16);
        });

        applyCustomDateBtn.addEventListener('click', () => {
            const startDateStr = document.getElementById('start-date').value;
            const endDateStr = document.getElementById('end-date').value;

            if (!startDateStr || !endDateStr) {
                showAlert('Please select both start and end dates');
                return;
            }

            const start = new Date(startDateStr);
            const end = new Date(endDateStr);

            if (start >= end) {
                showAlert('Start date must be before end date');
                return;
            }

            currentStartTime = start;
            currentEndTime = end;
            fetchLocationPath(start, end);
        });

        // Set up export buttons
        exportCsvBtn.addEventListener('click', () => {
            exportLocationDataAsCsv(currentStartTime, currentEndTime);
        });

        exportJsonBtn.addEventListener('click', () => {
            exportLocationDataAsJson(currentStartTime, currentEndTime);
        });

        // Set up logout link
        logoutLink.addEventListener('click', (e) => {
            e.preventDefault();
            Auth.logout();
        });

        // Load today's data by default
        todayBtn.click();
    }

    // Initialize the page
    init();
});
