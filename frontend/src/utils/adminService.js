import apiService from './apiService';

/**
 * Admin API Service for administrative operations
 */
const adminService = {
    // ==================== OIDC Provider Management ====================

    /**
     * Get all OIDC providers (from DB and environment)
     * @returns {Promise<Array>} List of OIDC providers
     */
    async getAllOidcProviders() {
        return apiService.get('/admin/oidc/providers');
    },

    /**
     * Get a single OIDC provider by name
     * @param {string} name - Provider name
     * @returns {Promise<Object>} Provider configuration
     */
    async getOidcProvider(name) {
        return apiService.get(`/admin/oidc/providers/${name}`);
    },

    /**
     * Create a new OIDC provider
     * @param {Object} provider - Provider configuration
     * @returns {Promise<Object>} Created provider
     */
    async createOidcProvider(provider) {
        return apiService.post("/admin/oidc/providers", provider);
    },

    /**
     * Update an existing OIDC provider
     * @param {string} name - Provider name
     * @param {Object} updates - Provider updates
     * @returns {Promise<Object>} Updated provider
     */
    async updateOidcProvider(name, updates) {
        return apiService.put(`/admin/oidc/providers/${name}`, updates);
    },

    /**
     * Delete an OIDC provider from database
     * @param {string} name - Provider name
     * @returns {Promise<Object>} Result
     */
    async deleteOidcProvider(name) {
        return apiService.delete(`/admin/oidc/providers/${name}`);
    },

    /**
     * Reset an OIDC provider to environment defaults
     * @param {string} name - Provider name
     * @returns {Promise<Object>} Reset provider configuration
     */
    async resetOidcProvider(name) {
        return apiService.post(`/admin/oidc/providers/${name}/reset`, {});
    },

    /**
     * Test connection to an OIDC provider
     * @param {string} name - Provider name
     * @returns {Promise<Object>} Test result
     */
    async testOidcProvider(name) {
        return apiService.post(`/admin/oidc/providers/${name}/test`, {});
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
        return apiService.get(`/admin/settings/${category}`);
    },

    /**
     * Update a setting value
     * @param {string} key - Setting key
     * @param {string} value - New value
     * @returns {Promise<Object>} Result
     */
    async updateSetting(key, value) {
        return apiService.put(`/admin/settings/${key}`, { value });
    },

    /**
     * Reset a setting to default
     * @param {string} key - Setting key
     * @returns {Promise<Object>} Result
     */
    async resetSetting(key) {
        return apiService.delete(`/admin/settings/${key}`);
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
        return apiService.post(`/admin/users/${userId}/reset-password`, {});
    },

    /**
     * Delete a user
     * @param {string} userId - User ID
     * @returns {Promise<Object>} Result
     */
    async deleteUser(userId) {
        return apiService.delete(`/admin/users/${userId}`);
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

    // ==================== Dashboard Stats ====================

    /**
     * Get dashboard statistics (tries dedicated endpoint, falls back to Prometheus)
     * @returns {Promise<Object>} Dashboard statistics
     */
    async getDashboardStats() {
        try {
            // Try the dedicated admin dashboard endpoint first
            return await apiService.get('/admin/dashboard/stats');
        } catch (error) {
            console.warn('Admin dashboard endpoint unavailable, falling back to Prometheus:', error);

            try {
                // Fallback to Prometheus metrics
                const response = await fetch('/api/prometheus/metrics');
                const text = await response.text();
                return this.parsePrometheusMetrics(text);
            } catch (prometheusError) {
                console.error('Failed to fetch Prometheus metrics:', prometheusError);
                // Return default values if both methods fail
                return {
                    totalUsers: 0,
                    activeUsers24h: 0,
                    totalGpsPoints: 0,
                    memoryUsageMB: 0
                };
            }
        }
    },

    /**
     * Parse Prometheus text format metrics
     * @param {string} text - Prometheus metrics text
     * @returns {Object} Parsed metrics object
     */
    parsePrometheusMetrics(text) {
        const lines = text.split('\n');
        const metrics = {};

        lines.forEach(line => {
            // Skip comments and empty lines
            if (line.startsWith('#') || line.trim() === '') return;

            // Parse metric line: metric_name{labels} value
            const match = line.match(/^([a-z_]+)(?:\{[^}]*\})?\s+(.+)$/);
            if (match) {
                const [, name, value] = match;
                metrics[name] = parseFloat(value);
            }
        });

        // Extract relevant metrics for dashboard
        return {
            totalUsers: metrics['users_total'] || 0,
            activeUsers24h: metrics['users_active_last_24h'] || 0,
            totalGpsPoints: metrics['gps_points_total'] || 0,
            memoryUsageMB: metrics['process_resident_memory_bytes']
                ? Math.round(metrics['process_resident_memory_bytes'] / (1024 * 1024))
                : 0
        };
    }
};

export default adminService;
