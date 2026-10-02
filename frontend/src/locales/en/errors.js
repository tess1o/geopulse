/**
 * Error copy.
 *
 * Keyed by the shape `utils/errorHandler.js` `formatError()` already had: a `{title, message}` pair
 * per HTTP status, plus network/timeout/session branches. Keeping the key structure aligned with
 * that switch means extraction is a straight substitution with no mapping layer.
 *
 * EN values are verbatim from the previous literals -- tests assert on this exact copy.
 */
export default {
    generic: {
        title: 'Something went wrong',
        message: 'An unexpected error occurred. Please try again.'
    },
    unexpected: 'An unexpected error occurred',
    network: {
        title: 'Connection Problem',
        message: 'Unable to connect to GeoPulse servers. Please check your internet connection and try again.',
        // Slightly shorter than `message`; the connection-error toast uses this one.
        shortMessage: 'Unable to connect to GeoPulse servers. Please check your internet connection.'
    },
    timeout: {
        title: 'Request Timeout',
        message: 'The request is taking longer than expected. Please try again.'
    },
    // Shared by the HTTP 401 branch and the auth-expiry branch, which showed identical copy.
    session: {
        title: 'Session Expired',
        message: 'Your session has expired. Please sign in again.'
    },
    http: {
        400: {
            title: 'Invalid Request',
            message: 'The request could not be processed. Please check your input and try again.'
        },
        401: {
            title: 'Authentication Required',
            message: '@:errors.session.message'
        },
        403: {
            title: 'Access Denied',
            message: "You don't have permission to perform this action."
        },
        404: {
            title: 'Not Found',
            message: 'The requested resource could not be found.'
        },
        409: {
            title: 'Conflict',
            message: 'This action conflicts with the current state. Please refresh and try again.'
        },
        429: {
            title: 'Too Many Requests',
            message: "You're making requests too quickly. Please wait a moment and try again."
        },
        500: {
            title: 'Server Error',
            message: 'Internal Server Error'
        },
        502: {
            title: 'Service Unavailable',
            message: 'GeoPulse is temporarily unavailable. Please try again in a few minutes.'
        },
        503: {
            title: 'Service Unavailable',
            message: '@:errors.http.502.message'
        },
        504: {
            title: 'Service Unavailable',
            message: '@:errors.http.502.message'
        },
        // Fallback for any status without its own entry: `Error {status}` / `An error occurred ({status}).`
        unknown: {
            title: 'Error {status}',
            message: 'An error occurred ({status}). Please try again.'
        }
    },
    backendUnavailable: {
        title: 'Backend Unavailable',
        message: 'GeoPulse servers are currently unavailable. Please try again later.'
    },
    reference: {
        hint: 'Check backend logs for ID: {id}'
    },
    retry: {
        available: 'Retry Available',
        hint: 'Click here to try again'
    },
    // Copy for the full-page error screen, keyed by its `type` discriminator.
    page: {
        connection: {
            title: 'Connection Problem',
            message: 'Unable to connect to GeoPulse servers. This might be due to a network issue or server maintenance.'
        },
        server: {
            title: 'Server Error',
            message: 'GeoPulse servers are experiencing issues. Our team has been notified and is working on a fix.'
        },
        generic: {
            title: 'Something went wrong',
            message: 'An unexpected error occurred. Please try again or contact support if the problem persists.'
        },
        actions: {
            checkConnection: 'Check Connection',
            goHome: 'Go to Home'
        },
        details: {
            summary: 'Technical Details',
            requestHeading: 'Request Information',
            method: 'Method:',
            url: 'URL:',
            status: 'Status:',
            timestamp: 'Timestamp:',
            errorHeading: 'Error Information',
            requestId: 'Request ID:',
            errorId: 'Error ID:'
        },
        tips: {
            heading: 'What can you do?',
            wait: 'Wait a few minutes and click "Check Connection" - servers may be restarting',
            internet: 'Check your internet connection',
            refresh: 'Try refreshing the page or clearing browser cache',
            maintenance: 'If the problem persists, GeoPulse servers may be under maintenance',
            checkBack: 'Check back in 10-15 minutes'
        },
        status: {
            internetConnection: 'Internet Connection:',
            backend: 'GeoPulse Backend:',
            lastChecked: 'Last Checked:',
            online: 'Backend Online',
            offline: 'Backend Offline',
            // The connectivity row vs the short form inside it.
            connected: 'Connected',
            offlineShort: 'Offline'
        },
        restored: {
            title: 'Connection Restored!',
            detail: 'GeoPulse servers are back online. Redirecting...'
        },
        stillUnavailable: {
            title: 'Still Unavailable',
            detail: 'GeoPulse servers are still experiencing issues. Please try again in a few minutes.'
        }
    }
}
