/**
 * Authentication module for GeoPulse
 * Handles login, logout, registration, and authentication status
 */

// Base URL for API requests
const API_BASE_URL = '/api';

// Authentication state
let currentUser = null;

/**
 * Check if the user is authenticated
 * @returns {boolean} True if authenticated, false otherwise
 */
function isAuthenticated() {
    return getCredentials() !== null;
}

/**
 * Get the stored credentials
 * @returns {Object|null} The credentials object or null if not authenticated
 */
function getCredentials() {
    const credentials = localStorage.getItem('credentials');
    return credentials ? JSON.parse(credentials) : null;
}

/**
 * Store credentials in local storage
 * @param {string} userId - The user ID
 * @param {string} password - The user's password
 */
function storeCredentials(userId, password) {
    localStorage.setItem('credentials', JSON.stringify({ userId, password }));
}

/**
 * Clear stored credentials
 */
function clearCredentials() {
    localStorage.removeItem('credentials');
    currentUser = null;
}

/**
 * Create the Authorization header for API requests
 * @returns {string|null} The Authorization header value or null if not authenticated
 */
function getAuthHeader() {
    const credentials = getCredentials();
    if (!credentials) return null;
    
    const base64Credentials = btoa(`${credentials.userId}:${credentials.password}`);
    return `Basic ${base64Credentials}`;
}

/**
 * Make an authenticated API request
 * @param {string} url - The API endpoint URL
 * @param {Object} options - Fetch options
 * @returns {Promise} The fetch promise
 */
async function apiRequest(url, options = {}) {
    const authHeader = getAuthHeader();
    if (!authHeader && !url.includes('/register')) {
        throw new Error('Authentication required');
    }
    
    const headers = {
        'Content-Type': 'application/json',
        ...options.headers
    };
    
    if (authHeader) {
        headers['Authorization'] = authHeader;
    }
    
    const response = await fetch(`${API_BASE_URL}${url}`, {
        ...options,
        headers
    });
    
    if (response.status === 401) {
        clearCredentials();
        window.location.href = '/login.html';
        throw new Error('Authentication failed');
    }
    
    return response;
}

/**
 * Login a user
 * @param {string} userId - The user ID
 * @param {string} password - The user's password
 * @returns {Promise<Object>} The user object
 */
async function login(userId, password) {
    // Store credentials temporarily for the request
    storeCredentials(userId, password);
    
    try {
        const response = await apiRequest('/users/me');
        
        if (!response.ok) {
            clearCredentials();
            const error = await response.json();
            throw new Error(error.message || 'Login failed');
        }
        
        const data = await response.json();
        currentUser = data.data;
        return currentUser;
    } catch (error) {
        clearCredentials();
        throw error;
    }
}

/**
 * Register a new user
 * @param {string} userId - The user ID
 * @param {string} password - The user's password
 * @param {string} deviceId - The device ID
 * @returns {Promise<Object>} The created user object
 */
async function register(userId, password, deviceId) {
    try {
        const response = await apiRequest('/users/register', {
            method: 'POST',
            body: JSON.stringify({
                userId,
                password,
                deviceId
            })
        });
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Registration failed');
        }
        
        const data = await response.json();
        return data.data;
    } catch (error) {
        throw error;
    }
}

/**
 * Logout the current user
 */
function logout() {
    clearCredentials();
    window.location.href = '/login.html';
}

/**
 * Get the current authenticated user
 * @returns {Promise<Object>} The user object
 */
async function getCurrentUser() {
    if (currentUser) return currentUser;
    
    if (!isAuthenticated()) {
        return null;
    }
    
    try {
        const response = await apiRequest('/users/me');
        
        if (!response.ok) {
            clearCredentials();
            return null;
        }
        
        const data = await response.json();
        currentUser = data.data;
        return currentUser;
    } catch (error) {
        clearCredentials();
        return null;
    }
}

/**
 * Check authentication status and redirect if necessary
 * @param {boolean} requireAuth - Whether authentication is required
 * @param {string} redirectTo - Where to redirect if authentication status doesn't match requirement
 */
async function checkAuth(requireAuth = true, redirectTo = '/login.html') {
    const authenticated = await getCurrentUser() !== null;
    
    if (requireAuth && !authenticated) {
        window.location.href = redirectTo;
    } else if (!requireAuth && authenticated) {
        window.location.href = '/dashboard.html';
    }
}

// Export the authentication functions
window.Auth = {
    login,
    logout,
    register,
    isAuthenticated,
    getCurrentUser,
    checkAuth,
    apiRequest
};