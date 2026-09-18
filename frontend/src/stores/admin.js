import apiService from '@/utils/apiService';
import { defineStore } from 'pinia';
import { normalizeApiError } from '@/utils/apiErrorDetail';

/**
 * Admin API Service for administrative operations
 */
const actions = {
    // ==================== OIDC Provider Management ====================

    /**
     * Get all OIDC providers (from DB and environment)
     * @returns {Promise<Array>} List of OIDC providers
     */
    async getAllOidcProviders() {
        return apiService.get('/admin/oidc-providers');
    },

    /**
     * Get a single OIDC provider by name
     * @param {string} name - Provider name
     * @returns {Promise<Object>} Provider configuration
     */
    async getOidcProvider(name) {
        return apiService.get(`/admin/oidc-providers/${name}`);
    },

    /**
     * Create a new OIDC provider
     * @param {Object} provider - Provider configuration
     * @returns {Promise<Object>} Created provider
     */
    async createOidcProvider(provider) {
        return apiService.post("/admin/oidc-providers", provider);
    },

    /**
     * Update an existing OIDC provider
     * @param {string} name - Provider name
     * @param {Object} updates - Provider updates
     * @returns {Promise<Object>} Updated provider
     */
    async updateOidcProvider(name, updates) {
        return apiService.put(`/admin/oidc-providers/${name}`, updates);
    },

    /**
     * Delete an OIDC provider from database
     * @param {string} name - Provider name
     * @returns {Promise<Object>} Result
     */
    async deleteOidcProvider(name) {
        return apiService.delete(`/admin/oidc-providers/${name}`);
    },

    /**
     * Reset an OIDC provider to environment defaults
     * @param {string} name - Provider name
     * @returns {Promise<Object>} Reset provider configuration
     */
    async resetOidcProvider(name) {
        return apiService.post(`/admin/oidc-providers/${name}/reset`, {});
    },

    /**
     * Test connection to an OIDC provider
     * @param {string} name - Provider name
     * @returns {Promise<Object>} Test result
     */
    async testOidcProvider(name) {
        return apiService.post(`/admin/oidc-providers/${name}/connection-tests`, {});
    },

    // ==================== Custom Geocoding Provider Management ====================

    async getCustomGeocodingProviders() {
        return apiService.get('/admin/geocoding/providers');
    },

    async createCustomGeocodingProvider(provider) {
        return apiService.post('/admin/geocoding/providers', provider);
    },

    async updateCustomGeocodingProvider(name, provider) {
        return apiService.put(`/admin/geocoding/providers/${name}`, provider);
    },

    async deleteCustomGeocodingProvider(name) {
        return apiService.delete(`/admin/geocoding/providers/${name}`);
    },

    // ==================== Settings Management ====================

    /**
     * Get all settings
     * @returns {Promise<Object>} Settings grouped by category
     */
    async getAllSettings() {
        return apiService.get('/admin/settings');
    },

    /**
     * Get settings for a specific category
     * @param {string} category - Category name
     * @returns {Promise<Array>} Settings list
     */
    async getSettingsByCategory(category) {
        return apiService.get(`/admin/settings/categories/${category}`);
    },

    /**
     * Update a setting value
     * @param {string} key - Setting key
     * @param {string} value - New value
     * @returns {Promise<Object>} Result
     */
    async updateSetting(key, value) {
        return apiService.put(`/admin/settings/keys/${key}`, { value });
    },

    /**
     * Reset a setting to default
     * @param {string} key - Setting key
     * @returns {Promise<Object>} Result
     */
    async resetSetting(key) {
        return apiService.delete(`/admin/settings/keys/${key}`);
    },

    async bulkUpdateSettings(settings) {
        return apiService.post('/admin/settings/bulk', { settings });
    },

    async getMapMatchingStatus() {
        return apiService.get('/admin/settings/map-matching/status');
    },

    async testValhallaConnection() {
        return apiService.post('/admin/settings/map-matching/valhalla/connection-tests');
    },

    async rebuildMapMatching(mode) {
        return apiService.post(`/admin/settings/map-matching/rebuilds?mode=${mode}`);
    },

    async testPanoramaxConnection() {
        return apiService.post('/admin/settings/panoramax/connection-tests');
    },

    async testAppriseConnection(payload) {
        return apiService.post('/admin/settings/system-notifications/apprise/connection-tests', payload);
    },

    async getWeatherStatus() {
        return apiService.get('/admin/weather/status');
    },

    async processWeatherNow() {
        return apiService.post('/admin/weather/process-now');
    },

    async testWeatherConnection() {
        return apiService.post('/admin/settings/weather/connection-tests');
    },

    // ==================== User Management ====================

    /**
     * Get all users with pagination
     * @param {Object} params - Query parameters (page, size, search, etc.)
     * @returns {Promise<Object>} Paginated user list
     */
    async getUsers(params = {}) {
        return apiService.get('/admin/users', params);
    },

    /**
     * Get user details
     * @param {string} userId - User ID
     * @returns {Promise<Object>} User details
     */
    async getUserDetails(userId) {
        return apiService.get(`/admin/users/${userId}`);
    },

    /**
     * Update user status (enable/disable)
     * @param {string} userId - User ID
     * @param {boolean} active - Active status
     * @returns {Promise<Object>} Result
     */
    async updateUserStatus(userId, active) {
        return apiService.put(`/admin/users/${userId}/status`, { active });
    },

    /**
     * Update user role
     * @param {string} userId - User ID
     * @param {string} role - New role (ADMIN or USER)
     * @returns {Promise<Object>} Result
     */
    async updateUserRole(userId, role) {
        return apiService.put(`/admin/users/${userId}/role`, { role });
    },

    /**
     * Reset user password
     * @param {string} userId - User ID
     * @returns {Promise<Object>} Result with new password
     */
    async resetUserPassword(userId) {
        return apiService.post(`/admin/users/${userId}/password-resets`, {});
    },

    /**
     * Delete a user
     * @param {string} userId - User ID
     * @returns {Promise<Object>} Result
     */
    async deleteUser(userId) {
        return apiService.delete(`/admin/users/${userId}`);
    },

    async getUserApiTokens(userId, page = 0, size = 20) {
        return apiService.get('/admin/api-tokens', { userId, page, size });
    },

    async revokeUserApiToken(tokenId) {
        return apiService.delete(`/admin/api-tokens/${tokenId}`);
    },

    // ==================== Invitation Management ====================

    /**
     * Get all invitations with optional status filter
     * @param {Object} params - Query parameters (page, size, status)
     * @returns {Promise<Object>} Paginated invitation list
     */
    async getInvitations(params = {}) {
        return apiService.get('/admin/invitations', params);
    },

    /**
     * Get the configured base URL for invitation links
     * @returns {Promise<Object>} Base URL configuration
     */
    async getInvitationBaseUrl() {
        return apiService.get('/admin/invitations/base-url');
    },

    /**
     * Create a new invitation
     * @param {Object} data - Invitation data (expiresAt)
     * @returns {Promise<Object>} Created invitation with token and baseUrl
     */
    async createInvitation(data = {}) {
        return apiService.post('/admin/invitations', data);
    },

    /**
     * Revoke an invitation
     * @param {string} invitationId - Invitation ID
     * @returns {Promise<Object>} Result
     */
    async revokeInvitation(invitationId) {
        return apiService.delete(`/admin/invitations/${invitationId}`);
    },

    // ==================== Timeline Regeneration Campaigns ====================

    async previewTimelineRegenerationCampaign(data) {
        return apiService.post('/admin/timeline-regeneration-campaigns/preview', data);
    },

    async createTimelineRegenerationCampaign(data) {
        return apiService.post('/admin/timeline-regeneration-campaigns', data);
    },

    async getTimelineRegenerationCampaigns() {
        return apiService.get('/admin/timeline-regeneration-campaigns');
    },

    async getTimelineRegenerationCampaign(campaignId) {
        return apiService.get(`/admin/timeline-regeneration-campaigns/${campaignId}`);
    },

    async retryTimelineRegenerationCampaignFailedUsers(campaignId) {
        return apiService.post(`/admin/timeline-regeneration-campaigns/${campaignId}/retry-failed`, {});
    },

    // ==================== Admin Settings Export ====================

    async exportAdminSettingsBackup() {
        return apiService.download('/admin/backups/settings/exports');
    },

    async importAdminSettingsBackup(file) {
        const formData = new FormData();
        formData.append('file', file);
        return apiService.post('/admin/backups/settings/imports', formData, {
            headers: {
                'Content-Type': 'multipart/form-data'
            }
        });
    },

    async downloadFullBackup() {
        return apiService.download('/admin/backups/download');
    },

    async runFullBackupNow() {
        return apiService.post('/admin/backups/run-now', {});
    },

    async getBackupFiles() {
        return apiService.get('/admin/backups/files');
    },

    async downloadLocalBackup(fileName) {
        return apiService.download(`/admin/backups/files/${encodeURIComponent(fileName)}`);
    },

    async deleteLocalBackup(fileName) {
        return apiService.delete(`/admin/backups/files/${encodeURIComponent(fileName)}`);
    },

    async restoreUploadedFullBackup(file, password) {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('password', password);
        return apiService.post('/admin/backups/restore/upload', formData, {
            headers: {
                'Content-Type': 'multipart/form-data'
            }
        });
    },

    async restoreLocalFullBackup(fileName, password) {
        return apiService.post('/admin/backups/restore/local', { fileName, password });
    },

    async discardPreparedRestore() {
        return apiService.post('/admin/backups/restore/discard', {});
    },

    async retryPreparedRestore() {
        return apiService.post('/admin/backups/restore/retry', {});
    },

    async getBackupConfig() {
        return apiService.get('/admin/backups/config');
    },

    async updateBackupConfig(config) {
        return apiService.put('/admin/backups/config', config);
    },

    async getBackupStatus() {
        return apiService.get('/admin/backups/status');
    },

    // ==================== Audit Log Management ====================

    /**
     * Get audit logs with pagination and filters
     * @param {Object} params - Query parameters (page, size, actionType, targetType, adminUserId, from, to)
     * @returns {Promise<Object>} Paginated audit log list
     */
    async getAuditLogs(params = {}) {
        return apiService.get('/admin/audit-logs', params);
    },

    // ==================== Dashboard Stats ====================

    /**
     * Get dashboard statistics (tries dedicated endpoint, falls back to Prometheus)
     * @returns {Promise<Object>} Dashboard statistics
     */
    async getDashboardStats() {
        try {
            // Try the dedicated admin dashboard endpoint first
            return await apiService.get('/admin/dashboard');
        } catch (error) {
            console.warn('Admin dashboard endpoint unavailable, falling back to Prometheus:', error);

            try {
                // Fallback to Prometheus metrics
                const response = await fetch('/api/prometheus/metrics');
                const text = await response.text();
                return parsePrometheusMetrics(text);
            } catch (prometheusError) {
                console.error('Failed to fetch Prometheus metrics:', prometheusError);
                // Return default values if both methods fail
                return {
                    totalUsers: 0,
                    activeUsers24h: 0,
                    totalGpsPoints: 0,
                    gpsActivity24h: 0,
                    weatherStatus: null
                };
            }
        }
    },

    /**
     * Parse Prometheus text format metrics
     * @param {string} text - Prometheus metrics text
     * @returns {Object} Parsed metrics object
     */
};

const parsePrometheusMetrics = (text) => {
    const metrics = {};
    text.split('\n').forEach(line => {
        if (line.startsWith('#') || line.trim() === '') return;
        const match = line.match(/^([a-z_]+)(?:\{[^}]*\})?\s+(.+)$/);
        if (match) metrics[match[1]] = parseFloat(match[2]);
    });
    return {
        totalUsers: metrics.users_total || 0,
        activeUsers24h: metrics.users_active_last_24h || 0,
        totalGpsPoints: metrics.gps_points_total || 0,
        gpsActivity24h: metrics.gps_points_last_24h || 0,
        weatherStatus: null
    };
};

for (const [name, action] of Object.entries(actions)) {
    actions[name] = async function (...args) {
        try {
            return await action.apply(this, args);
        } catch (error) {
            this.error = normalizeApiError(error, `Admin action ${name} failed`);
            throw this.error;
        }
    };
}

export const useAdminStore = defineStore('admin', {
    state: () => ({ error: null }),
    actions
});
