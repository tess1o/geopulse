/**
 * JavaScript for the registration page
 * Handles form submission and user registration
 */

document.addEventListener('DOMContentLoaded', async () => {
    // Check if already authenticated
    Auth.checkAuth(false, '/dashboard.html');
    
    const registerForm = document.getElementById('register-form');
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
     * Generate a random device ID if none is provided
     * @returns {string} A random device ID
     */
    function generateDeviceId() {
        return 'device_' + Math.random().toString(36).substring(2, 15);
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
        const confirmPassword = document.getElementById('confirmPassword').value;
        let deviceId = document.getElementById('deviceId').value.trim();
        
        // Validate inputs
        if (!userId) {
            showAlert('Please enter a user ID');
            return;
        }
        
        if (!password) {
            showAlert('Please enter a password');
            return;
        }
        
        if (password !== confirmPassword) {
            showAlert('Passwords do not match');
            return;
        }
        
        // Generate a device ID if none is provided
        if (!deviceId) {
            deviceId = generateDeviceId();
            document.getElementById('deviceId').value = deviceId;
        }
        
        try {
            // Disable form during registration attempt
            const submitButton = registerForm.querySelector('button[type="submit"]');
            submitButton.disabled = true;
            submitButton.textContent = 'Registering...';
            
            // Attempt to register
            await Auth.register(userId, password, deviceId);
            
            // Show success message
            showAlert('Registration successful! Redirecting to login...', 'success');
            
            // Redirect to login page after a short delay
            setTimeout(() => {
                window.location.href = '/login.html';
            }, 2000);
        } catch (error) {
            console.error('Registration error:', error);
            showAlert(error.message || 'Registration failed. Please try again.');
            
            // Re-enable form
            const submitButton = registerForm.querySelector('button[type="submit"]');
            submitButton.disabled = false;
            submitButton.textContent = 'Register';
        }
    }
    
    // Add event listener for form submission
    registerForm.addEventListener('submit', handleSubmit);
    
    // Auto-generate device ID if field is empty
    const deviceIdInput = document.getElementById('deviceId');
    if (!deviceIdInput.value) {
        deviceIdInput.value = generateDeviceId();
    }
});