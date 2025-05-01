/**
 * Main JavaScript file for the index page
 * Handles initial loading and redirection based on authentication status
 */

document.addEventListener('DOMContentLoaded', async () => {
    const loadingElement = document.getElementById('loading');
    const contentElement = document.getElementById('content');
    
    try {
        // Check if the user is authenticated
        const user = await Auth.getCurrentUser();
        
        if (user) {
            // User is authenticated, redirect to dashboard
            window.location.href = '/dashboard.html';
        } else {
            // User is not authenticated, redirect to login
            window.location.href = '/login.html';
        }
    } catch (error) {
        console.error('Error checking authentication:', error);
        
        // Show error message
        contentElement.innerHTML = `
            <div class="alert alert-error">
                An error occurred: ${error.message || 'Unknown error'}
            </div>
            <div class="form-footer">
                <a href="/login.html">Go to Login</a>
            </div>
        `;
        
        // Hide loading spinner and show content
        loadingElement.style.display = 'none';
        contentElement.style.display = 'block';
    }
});