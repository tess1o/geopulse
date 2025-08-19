import axios from 'axios';
import {formatError, isBackendDown} from './errorHandler';

const API_BASE_URL = window.VUE_APP_CONFIG?.API_BASE_URL || '/api';
/**
 * API Service for handling HTTP requests
 */
const apiService = {
    // Flag to track if a token refresh is in progress
    _refreshingToken: false,

    // Promise that resolves when token refresh completes
    _refreshTokenPromise: null,


    /**
     * Get CSRF token from cookie (Quarkus REST CSRF handles this automatically)
     * @returns {string|null} CSRF token or null if not found
     */
    getCsrfToken() {        
        try {
            // Use proper cookie parsing
            const value = document.cookie.replace(
                /(?:(?:^|.*;\s*)csrf-token\s*\=\s*([^;]*).*$)|^.*$/, 
                "$1"
            );
            return value || null;
        } catch (error) {
            console.error('Error getting CSRF token:', error);
            return null;
        }
    },

    /**
     * Get headers for state-changing operations (includes CSRF token)
     * @returns {Object} Headers object with CSRF token
     */
    getSecureHeaders() {
        const headers = {};
        
        const csrfToken = this.getCsrfToken();
        if (csrfToken) {
            headers['X-CSRF-Token'] = csrfToken;
        }
        // Don't fail if no CSRF token - let server handle it
        
        return headers;
    },


    /**
     * Check if the access token is expired or about to expire
     * @returns {boolean} True if token needs refresh
     */
    isTokenExpired() {
        try {
            // Check expiration cookie
            const expiresAt = this.getTokenExpirationFromCookie();
            // If expiration cookie is missing, assume token is expired (needs refresh)
            if (expiresAt === null) {
                const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}');
                // Only consider expired if we have user info (logged in)
                return !!userInfo.id;
            }
            return Date.now() > expiresAt - 10000; // 10s buffer for testing
        } catch (error) {
            console.error('Error checking token expiration:', error);
            return false;
        }
    },

    /**
     * Get token expiration timestamp from cookie (for cookie mode)
     * @returns {number|null} Expiration timestamp or null
     */
    getTokenExpirationFromCookie() {
        try {
            const value = document.cookie.replace(
                /(?:(?:^|.*;\s*)token_expires_at\s*\=\s*([^;]*).*$)|^.*$/, 
                "$1"
            );
            return value ? parseInt(value) : null;
        } catch (error) {
            console.error('Error getting token expiration from cookie:', error);
            return null;
        }
    },

    /**
     * Refresh the access token using the refresh token
     * @returns {Promise<boolean>} True if refresh was successful
     */
    async refreshToken() {
        // If a refresh is already in progress, return the existing promise
        if (this._refreshingToken) {
            return this._refreshTokenPromise;
        }

        // Set the refreshing flag and create a new promise
        this._refreshingToken = true;
        this._refreshTokenPromise = (async () => {
            try {
                // Cookie-based refresh is handled server-side
                const response = await axios.post(`${API_BASE_URL}/auth/refresh-cookie`, {}, {
                    withCredentials: true
                });
                return response.status === 200;
            } catch (refreshError) {
                console.error('Cookie refresh failed:', refreshError);
                this.clearAuthData();
                return false;
            } finally {
                // Reset the refreshing flag when done
                this._refreshingToken = false;
            }
        })();

        return this._refreshTokenPromise;
    },

    /**
     * Execute a request with automatic retry on 401
     * @param {Function} requestFn - Function that performs the actual request
     * @param {number} maxRetries - Maximum number of retries (default: 1)
     * @returns {Promise} - Request result
     */
    async _requestWithRetry(requestFn, maxRetries = 1) {
        let attempt = 0;
        
        while (attempt <= maxRetries) {
            try {
                return await requestFn();
            } catch (error) {
                // Retry on 401 with token refresh
                if (error.response?.status === 401 && attempt < maxRetries) {
                    const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}');
                    if (userInfo.id) {
                        try {
                            const refreshSuccessful = await this.refreshToken();
                            if (refreshSuccessful) {
                                attempt++;
                                // Small delay to ensure cookies are properly set
                                await new Promise(resolve => setTimeout(resolve, 500));
                                continue; // Retry the request
                            }
                        } catch (refreshError) {
                            console.error('Token refresh failed:', refreshError);
                        }
                    }
                }
                
                // If not retrying, handle error normally
                await this.handleError(error);
                throw error;
            }
        }
    },

    async checkAuthExpired(endpoint) {
        // Skip auth check for public endpoints
        const publicEndpoints = [
            '/auth/login',
            '/users/register',
            '/auth/refresh',
            '/auth/refresh-cookie',
            '/auth/logout'
        ];

        // Skip auth check for shared location endpoints (they use their own temporary tokens)
        const isSharedEndpoint = endpoint.startsWith('/shared/');

        if (publicEndpoints.includes(endpoint) || isSharedEndpoint) {
            return; // No auth check needed
        }

        // Check expiration for BOTH modes now
        if (this.isTokenExpired()) {
            const refreshed = await this.refreshToken();
            if (!refreshed) {
                this.clearAuthData();
                throw new Error('Authentication expired. Please login again.');
            }
        }
    },

    /**
     * Perform a GET request with token refresh if needed
     * @param {string} endpoint - API endpoint
     * @param {Object} params - URL parameters
     * @returns {Promise} - Axios response promise
     */
    async get(endpoint, params = {}) {
        return this._requestWithRetry(async () => {
            await this.checkAuthExpired(endpoint);

            const response = await axios.get(`${API_BASE_URL}${endpoint}`, {
                params,
                withCredentials: true,
                headers: {}
            });
            return response.data;
        });
    },

    /**
     * Perform a GET request with custom headers (for special cases like shared links)
     * @param {string} endpoint - API endpoint
     * @param {Object} customHeaders - Custom headers to use instead of auth headers
     * @param {Object} params - URL parameters
     * @returns {Promise} - Axios response promise
     */
    async getWithCustomHeaders(endpoint, customHeaders = {}, params = {}) {
        try {
            const response = await axios.get(`${API_BASE_URL}${endpoint}`, {
                params,
                withCredentials: true,
                headers: customHeaders
            });
            return response.data;
        } catch (error) {
            this.handleError(error);
            throw error;
        }
    },

    async getRawWithBlob(endpoint, customHeaders = {}, params = {}) {
        try {
            console.log('apiService.getRawWithBlob: Endpoint:', endpoint)
            console.log('apiService.getRawWithBlob: Using cookie-based auth')
            
            await this.checkAuthExpired(endpoint);

            // Always rely on cookies for authentication
            const headers = { 
                ...customHeaders
            };
            
            console.log('apiService.getRawWithBlob: Final headers:', Object.keys(headers))

            const response = await axios.get(`${API_BASE_URL}${endpoint}`, {
                params,
                withCredentials: true,
                headers,
                responseType: 'blob'
            });
            
            console.log('apiService.getRawWithBlob: Response status:', response.status)
            return response;
        } catch (error) {
            console.error('apiService.getRawWithBlob: Error:', error)
            this.handleError(error);
            throw error;
        }
    },

    async delete(endpoint, params = {}) {
        return this._requestWithRetry(async () => {
            await this.checkAuthExpired(endpoint);
            return await this._performSecureRequest('delete', endpoint, null, {params});
        });
    },

    async put(endpoint, data = {}) {
        return this._requestWithRetry(async () => {
            await this.checkAuthExpired(endpoint);
            return await this._performSecureRequest('put', endpoint, data);
        });
    },

    /**
     * Perform a POST request with token refresh if needed
     * @param {string} endpoint - API endpoint
     * @param {Object} data - Request body
     * @returns {Promise} - Axios response promise
     */
    async post(endpoint, data = {}, options = {}) {
        return this._requestWithRetry(async () => {
            await this.checkAuthExpired(endpoint);

            // Don't send auth headers for shared endpoints (they're public or use custom tokens)
            const isSharedEndpoint = endpoint.startsWith('/shared/');

            if (isSharedEndpoint) {
                const response = await axios.post(`${API_BASE_URL}${endpoint}`, data, {
                    headers: options.headers || {}
                });
                return response.data;
            } else {
                // Use secure request with CSRF protection for authenticated endpoints
                return await this._performSecureRequest('post', endpoint, data, options);
            }
        });
    },

    /**
     * Perform a secure request with CSRF protection and retry logic
     * @param {string} method - HTTP method (post, put, delete)
     * @param {string} endpoint - API endpoint
     * @param {Object} data - Request data
     * @param {Object} options - Additional options
     * @returns {Promise} - Response data
     */
    async _performSecureRequest(method, endpoint, data = null, options = {}) {
        const publicEndpoints = [
            '/auth/login',
            '/users/register',  
            '/auth/refresh',
            '/auth/refresh-cookie'
        ];

        // Skip CSRF for public endpoints
        const isPublicEndpoint = publicEndpoints.some(pe => endpoint.startsWith(pe));

        try {
            const headers = isPublicEndpoint ? {} : this.getSecureHeaders();
            const mergedHeaders = {...headers, ...options.headers};

            console.log('headers: ', mergedHeaders)

            let axiosOptions = {
                withCredentials: true,
                headers: mergedHeaders
            };

            // Add additional options like params for DELETE requests
            if (options.params) {
                axiosOptions.params = options.params;
            }

            let response;
            if (method === 'post') {
                response = await axios.post(`${API_BASE_URL}${endpoint}`, data, axiosOptions);
            } else if (method === 'put') {
                response = await axios.put(`${API_BASE_URL}${endpoint}`, data, axiosOptions);
            } else if (method === 'delete') {
                response = await axios.delete(`${API_BASE_URL}${endpoint}`, axiosOptions);
            }

            return response.data;

        } catch (error) {
            // Quarkus REST CSRF handles token validation automatically
            // No manual CSRF retry logic needed
            throw error;
        }
    },

    /**
     * Download a file as a blob and trigger browser download
     * @param {string} endpoint - API endpoint
     * @param {string} filename - Optional filename override
     * @returns {Promise} - Download promise
     */
    async download(endpoint, filename = null) {
        try {
            await this.checkAuthExpired(endpoint);

            const response = await axios.get(`${API_BASE_URL}${endpoint}`, {
                headers: {},
                responseType: 'blob',
                withCredentials: true
            });

            // Create blob URL and trigger download
            const blob = new Blob([response.data]);
            const url = window.URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;

            // Try to get filename from Content-Disposition header
            const contentDisposition = response.headers['content-disposition'];
            if (contentDisposition && !filename) {
                const filenameMatch = contentDisposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
                if (filenameMatch) {
                    filename = filenameMatch[1].replace(/['"]/g, '');
                }
            }

            link.download = filename || 'download';
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            window.URL.revokeObjectURL(url);

            return true;
        } catch (error) {
            this.handleError(error);
            throw error;
        }
    },

    /**
     * Handle common error scenarios and enhance error messages
     * @param {Error} error - Error object
     */
    handleError(error) {
        // If unauthorized, clear auth data and redirect to login if logged in
        if (error.response && error.response.status === 401) {
            this.clearAuthData();

            // 401 means cookies expired - redirect to login if we have user info
            const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}');
            
            if (userInfo.id) {
                window.location.href = '/login';
            }
        }

        // Enhance error with user-friendly message
        const formattedError = formatError(error);
        error.userMessage = formattedError.message;
        error.userTitle = formattedError.title;
        error.isConnectionError = formattedError.isConnectionError;
        error.canRetry = formattedError.canRetry;

        // Check if backend is completely down and redirect to error page
        if (isBackendDown(error)) {
            this.redirectToErrorPage(error);
        }

        console.error('API request failed:', error);
    },

    /**
     * Redirect to error page for severe backend issues
     * @param {Error} error - The error that caused the redirect
     */
    redirectToErrorPage(error) {
        // Avoid infinite redirects if we're already on error page
        if (window.location.pathname === '/error') {
            return;
        }

        // Use setTimeout to avoid issues with Vue router during navigation
        setTimeout(() => {
            // Collect detailed error information
            const errorDetails = {
                message: error.message || 'Unknown error',
                status: error.response?.status,
                statusText: error.response?.statusText,
                data: error.response?.data,
                url: error.config?.url,
                method: error.config?.method?.toUpperCase(),
                headers: error.config?.headers,
                timestamp: new Date().toISOString(),
                userAgent: navigator.userAgent,
                stack: error.stack
            };

            const errorParams = new URLSearchParams({
                type: 'connection',
                title: 'Backend Unavailable',
                message: 'GeoPulse servers are currently unavailable. Please try again later.',
                details: JSON.stringify(errorDetails)
            });

            window.location.href = `/error?${errorParams.toString()}`;
        }, 100);
    },

    /**
     * Login user with credentials and store tokens/CSRF token
     * @param {string} email - User ID
     * @param {string} password - User password
     * @returns {Promise<Object>} - User data
     */
    async login(email, password) {
        try {
            const response = await axios.post(`${API_BASE_URL}/auth/login`, {
                email,
                password,
            }, {
                withCredentials: true
            });

            if (response.data && response.data.data) {
                const responseData = response.data.data;
                
                // Store only user profile info (no tokens)
                const userInfo = {
                    email: responseData.email,
                    fullName: responseData.fullName,
                    id: responseData.id,
                    avatar: responseData.avatar,
                    createdAt: responseData.createdAt
                };

                localStorage.setItem('userInfo', JSON.stringify(userInfo));
                return responseData;
            }
            throw new Error('Invalid login response');
        } catch (error) {
            this.handleError(error);
            throw error;
        }
    },

    /**
     * Logout user by calling logout endpoint and clearing local storage
     */
    async logout() {
        try {
            await axios.post(`${API_BASE_URL}/auth/logout`, {}, {
                withCredentials: true
            });
        } catch (error) {
            // Even if logout fails on server, clear local data
            console.error('Logout request failed:', error);
        } finally {
            this.clearAuthData();
        }
    },
    clearAuthData() {
        localStorage.removeItem('userInfo');
        // Note: httpOnly cookies are cleared by the logout endpoint on the server
    },

    getUserInfoFromAuthData() {
        try {
            const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}');
            return {
                userId: userInfo.id,
                fullName: userInfo.fullName,
                email: userInfo.email,
                avatar: userInfo.avatar,
                createdAt: userInfo.createdAt,
            };
        } catch (error) {
            console.error('Error getting user info:', error);
            return null;
        }
    },
};

export default apiService;
