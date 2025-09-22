/**
 * Global error handling utilities for severe connectivity issues
 */
import router from '@/router'
import { isBackendDown } from './errorHandler'
import dayjs from 'dayjs';
import { useTimezone } from '@/composables/useTimezone';

/**
 * Handle severe errors that require navigation to error page
 * @param {Error} error - The error to handle
 * @param {Object} options - Additional options
 */
export function handleSevereError(error, options = {}) {
  // Check if this is a severe connectivity issue
  if (isBackendDown(error)) {
    // Collect detailed error information
    const errorDetails = {
      message: error.message || 'Unknown error',
      status: error.response?.status,
      statusText: error.response?.statusText,
      data: error.response?.data,
      url: error.config?.url,
      method: error.config?.method?.toUpperCase(),
      headers: error.config?.headers,
      timestamp: useTimezone().now().toISOString(),
      userAgent: navigator.userAgent,
      stack: error.stack,
      userMessage: error.userMessage,
      isConnectionError: error.isConnectionError,
      canRetry: error.canRetry
    };

    const errorParams = {
      type: 'connection',
      title: options.title || 'Connection Problem',
      message: options.message || error.userMessage || 'Unable to connect to GeoPulse servers.',
      details: JSON.stringify(errorDetails)
    }

    // Navigate to error page with parameters
    router.push({
      path: '/error',
      query: errorParams
    })
    
    return true // Indicates we handled it globally
  }
  
  return false // Let local error handling take over
}

/**
 * Setup global error handling for unhandled promises and errors
 */
export function setupGlobalErrorHandling() {
  // Handle unhandled promise rejections
  window.addEventListener('unhandledrejection', (event) => {
    console.error('Unhandled promise rejection:', event.reason)
    
    // If it's a network error, handle it globally
    if (event.reason && handleSevereError(event.reason)) {
      event.preventDefault() // Prevent default browser handling
    }
  })

  // Handle general JavaScript errors
  window.addEventListener('error', (event) => {
    console.error('Global error:', event.error)
    
    // Don't handle all errors globally, just log them
    // Most errors should be handled locally by components
  })
}

/**
 * Create a global interceptor for axios to handle severe errors
 */
export function setupAxiosGlobalErrorHandler() {
  // This would be called from main.js to setup axios interceptors
  // that automatically redirect to error page for severe connectivity issues
  
  return {
    requestInterceptor: (config) => {
      // Add any global request setup here
      return config
    },
    
    responseErrorInterceptor: (error) => {
      // Check for severe connectivity issues
      if (isBackendDown(error)) {
        // Only redirect if we're not already on the error page
        if (router.currentRoute.value.path !== '/error') {
          handleSevereError(error)
        }
      }
      
      return Promise.reject(error)
    }
  }
}

export default {
  handleSevereError,
  setupGlobalErrorHandling,
  setupAxiosGlobalErrorHandler
}
