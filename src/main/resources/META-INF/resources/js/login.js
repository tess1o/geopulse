/**
 * JavaScript for the login page
 * Handles form submission and authentication
 */

document.addEventListener('DOMContentLoaded', async () => {
    // Check if already authenticated
    await Auth.checkAuth(false, '/dashboard.html');
    
    const loginForm = document.getElementById('login-form');
    const alertContainer = document.getElementById('alert-container');
    
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
     * Handle form submission
     * @param {Event} event - The form submission event
     */
    async function handleSubmit(event) {
        event.preventDefault();
        clearAlert();
        
        const userId = document.getElementById('userId').value.trim();
        const password = document.getElementById('password').value;
        
        if (!userId || !password) {
            showAlert('Please enter both user ID and password');
            return;
        }
        
        try {
            // Disable form during login attempt
            const submitButton = loginForm.querySelector('button[type="submit"]');
            submitButton.disabled = true;
            submitButton.textContent = 'Logging in...';
            
            // Attempt to login
            await Auth.login(userId, password);
            
            // Redirect to dashboard on success
            window.location.href = '/dashboard.html';
        } catch (error) {
            console.error('Login error:', error);
            showAlert(error.message || 'Login failed. Please check your credentials and try again.');
            
            // Re-enable form
            const submitButton = loginForm.querySelector('button[type="submit"]');
            submitButton.disabled = false;
            submitButton.textContent = 'Login';
        }
    }
    
    // Add event listener for form submission
    loginForm.addEventListener('submit', handleSubmit);
});