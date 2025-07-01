<template>
  <div class="error-page">
    <div class="error-container">
      <div class="error-content">
        <!-- Error Icon -->
        <div class="error-icon">
          <i v-if="errorType === 'connection'" class="pi pi-wifi"></i>
          <i v-else-if="errorType === 'server'" class="pi pi-server"></i>
          <i v-else class="pi pi-exclamation-triangle"></i>
        </div>

        <!-- Error Title -->
        <h1 class="error-title">{{ errorTitle }}</h1>

        <!-- Error Message -->
        <p class="error-message">{{ errorMessage }}</p>

        <!-- Error Details (if provided) -->
        <div v-if="errorDetails" class="error-details">
          <details>
            <summary>Technical Details</summary>
            <pre>{{ errorDetails }}</pre>
          </details>
        </div>

        <!-- Action Buttons -->
        <div class="error-actions">
          <Button
            label="Check Connection"
            icon="pi pi-refresh"
            @click="checkBackendStatus"
            class="retry-button"
            :loading="retrying"
          />
          <Button
            label="Go to Home"
            icon="pi pi-home"
            severity="secondary"
            outlined
            @click="goHome"
            class="home-button"
          />
        </div>

        <!-- Connection Tips -->
        <div v-if="errorType === 'connection'" class="error-tips">
          <h3>What can you do?</h3>
          <ul>
            <li>Wait a few minutes and click "Check Connection" - servers may be restarting</li>
            <li>Check your internet connection</li>
            <li>Try refreshing the page or clearing browser cache</li>
            <li>If the problem persists, GeoPulse servers may be under maintenance</li>
            <li>Check back in 10-15 minutes</li>
          </ul>
        </div>

        <!-- Status Info -->
        <div class="error-status">
          <div class="status-item">
            <span class="status-label">Internet Connection:</span>
            <span :class="['status-value', isOnline ? 'status-online' : 'status-offline']">
              <i :class="isOnline ? 'pi pi-check-circle' : 'pi pi-times-circle'"></i>
              {{ isOnline ? 'Connected' : 'Offline' }}
            </span>
          </div>
          <div class="status-item">
            <span class="status-label">GeoPulse Backend:</span>
            <span :class="['status-value', connectionStatus.class]">
              <i :class="connectionStatus.icon"></i>
              {{ connectionStatus.text }}
            </span>
          </div>
          <div class="status-item">
            <span class="status-label">Last Checked:</span>
            <span class="status-value">{{ lastUpdated }}</span>
          </div>
        </div>
      </div>
    </div>
    <Toast />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import Toast from 'primevue/toast'

const props = defineProps({
  errorType: {
    type: String,
    default: 'connection', // 'connection', 'server', 'generic'
    validator: (value) => ['connection', 'server', 'generic'].includes(value)
  },
  title: {
    type: String,
    default: ''
  },
  message: {
    type: String,
    default: ''
  },
  details: {
    type: String,
    default: ''
  },
  retryCallback: {
    type: Function,
    default: null
  }
})

const emit = defineEmits(['retry', 'home'])

const router = useRouter()
const toast = useToast()

// Reactive state
const retrying = ref(false)
const isOnline = ref(navigator.onLine)
const backendOnline = ref(false)
const lastUpdated = ref(new Date().toLocaleTimeString())
const checkingStatus = ref(false)
let statusCheckInterval = null

// Update connection status
const updateConnectionStatus = async () => {
  isOnline.value = navigator.onLine
  lastUpdated.value = new Date().toLocaleTimeString()
  
  // Also check backend status
  await checkBackendConnectivity()
}

// Check if backend is reachable
const checkBackendConnectivity = async () => {
  if (!isOnline.value) {
    backendOnline.value = false
    return
  }
  
  checkingStatus.value = true
  const wasOffline = !backendOnline.value
  
  try {
    const API_BASE_URL = window.VUE_APP_CONFIG?.API_BASE_URL || '/api'
    
    const response = await fetch(`${API_BASE_URL}/health`, {
      method: 'GET',
      signal: AbortSignal.timeout(3000), // 3 second timeout
      cache: 'no-cache'
    })
    
    backendOnline.value = response.ok
    
    // If backend just came back online, show success and redirect
    if (backendOnline.value && wasOffline) {
      toast.add({
        severity: 'success',
        summary: 'Connection Restored!',
        detail: 'GeoPulse servers are back online. Redirecting...',
        life: 3000
      })
      
      setTimeout(() => {
        window.location.href = '/'
      }, 2000)
    }
  } catch (error) {
    backendOnline.value = false
  } finally {
    checkingStatus.value = false
  }
}

// Event listeners for online/offline
onMounted(async () => {
  window.addEventListener('online', updateConnectionStatus)
  window.addEventListener('offline', updateConnectionStatus)
  
  // Initial backend status check
  await checkBackendConnectivity()
  
  // Check backend status every 30 seconds
  statusCheckInterval = setInterval(async () => {
    await checkBackendConnectivity()
  }, 30000)
})

onUnmounted(() => {
  window.removeEventListener('online', updateConnectionStatus)
  window.removeEventListener('offline', updateConnectionStatus)
  
  // Clear the status check interval
  if (statusCheckInterval) {
    clearInterval(statusCheckInterval)
  }
})

// Computed properties
const errorTitle = computed(() => {
  if (props.title) return props.title

  switch (props.errorType) {
    case 'connection':
      return 'Connection Problem'
    case 'server':
      return 'Server Error'
    default:
      return 'Something went wrong'
  }
})

const errorMessage = computed(() => {
  if (props.message) return props.message

  switch (props.errorType) {
    case 'connection':
      return 'Unable to connect to GeoPulse servers. This might be due to a network issue or server maintenance.'
    case 'server':
      return 'GeoPulse servers are experiencing issues. Our team has been notified and is working on a fix.'
    default:
      return 'An unexpected error occurred. Please try again or contact support if the problem persists.'
  }
})

const errorDetails = computed(() => props.details)

const connectionStatus = computed(() => {
  if (!isOnline.value) {
    return {
      text: 'No Internet',
      class: 'status-offline',
      icon: 'pi pi-times-circle'
    }
  }
  
  if (checkingStatus.value) {
    return {
      text: 'Checking...',
      class: 'status-checking',
      icon: 'pi pi-spin pi-spinner'
    }
  }
  
  if (backendOnline.value) {
    return {
      text: 'Backend Online',
      class: 'status-online',
      icon: 'pi pi-check-circle'
    }
  }
  
  return {
    text: 'Backend Offline',
    class: 'status-offline',
    icon: 'pi pi-times-circle'
  }
})

// Methods
const checkBackendStatus = async () => {
  retrying.value = true
  try {
    // Get API base URL
    const API_BASE_URL = window.VUE_APP_CONFIG?.API_BASE_URL || '/api'
    
    // Try to ping the backend with a simple health check
    const response = await fetch(`${API_BASE_URL}/health`, {
      method: 'GET',
      timeout: 5000
    })
    
    if (response.ok) {
      // Backend is back online!
      toast.add({
        severity: 'success',
        summary: 'Connection Restored!',
        detail: 'GeoPulse servers are back online. Redirecting...',
        life: 3000
      })
      
      // Redirect back to the main app after a short delay
      setTimeout(() => {
        window.location.href = '/'
      }, 1500)
    } else {
      throw new Error('Backend still unavailable')
    }
  } catch (error) {
    console.error('Backend still down:', error)
    toast.add({
      severity: 'warn',
      summary: 'Still Unavailable',
      detail: 'GeoPulse servers are still experiencing issues. Please try again in a few minutes.',
      life: 4000
    })
  } finally {
    retrying.value = false
    updateConnectionStatus()
  }
}

const handleRetry = async () => {
  await checkBackendStatus()
}

const goHome = () => {
  emit('home')
  router.push('/')
}
</script>

<style scoped>
.error-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gp-surface-light);
  padding: var(--gp-spacing-lg);
}

.error-container {
  max-width: 600px;
  width: 100%;
}

.error-content {
  background: var(--gp-surface-white);
  border-radius: var(--gp-radius-large);
  padding: var(--gp-spacing-xxl);
  text-align: center;
  box-shadow: var(--gp-shadow-card);
  border: 1px solid var(--gp-border-light);
}

.error-icon {
  margin-bottom: var(--gp-spacing-xl);
}

.error-icon i {
  font-size: 4rem;
  color: var(--gp-text-muted);
}

.error-title {
  font-size: 2rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  margin: 0 0 var(--gp-spacing-md);
}

.error-message {
  font-size: 1.125rem;
  color: var(--gp-text-secondary);
  line-height: 1.6;
  margin: 0 0 var(--gp-spacing-xl);
}

.error-details {
  margin: var(--gp-spacing-lg) 0;
  text-align: left;
}

.error-details summary {
  cursor: pointer;
  font-weight: 600;
  color: var(--gp-text-secondary);
  margin-bottom: var(--gp-spacing-sm);
}

.error-details pre {
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  padding: var(--gp-spacing-md);
  font-size: 0.875rem;
  color: var(--gp-text-primary);
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

.error-actions {
  display: flex;
  gap: var(--gp-spacing-md);
  justify-content: center;
  margin-bottom: var(--gp-spacing-xl);
  flex-wrap: wrap;
}

.retry-button,
.home-button {
  min-width: 140px;
}

.error-tips {
  text-align: left;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
  padding: var(--gp-spacing-lg);
  margin: var(--gp-spacing-xl) 0;
}

.error-tips h3 {
  margin: 0 0 var(--gp-spacing-md);
  color: var(--gp-text-primary);
  font-size: 1rem;
}

.error-tips ul {
  margin: 0;
  padding-left: var(--gp-spacing-lg);
}

.error-tips li {
  margin-bottom: var(--gp-spacing-sm);
  color: var(--gp-text-secondary);
}

.error-tips a {
  color: var(--gp-primary);
  text-decoration: none;
}

.error-tips a:hover {
  text-decoration: underline;
}

.error-status {
  border-top: 1px solid var(--gp-border-light);
  padding-top: var(--gp-spacing-lg);
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--gp-spacing-md);
}

.status-item {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
}

.status-label {
  font-size: 0.875rem;
  color: var(--gp-text-muted);
  font-weight: 500;
}

.status-value {
  font-size: 0.875rem;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
}

.status-online {
  color: var(--gp-success);
}

.status-offline {
  color: var(--gp-danger);
}

.status-checking {
  color: var(--gp-primary);
}

/* Dark Mode */
.p-dark .error-page {
  background: var(--gp-surface-darker);
}

.p-dark .error-content {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark .error-tips {
  background: var(--gp-surface-darker);
}

.p-dark .error-details pre {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

.p-dark .error-status {
  border-top-color: var(--gp-border-dark);
}

/* Responsive */
@media (max-width: 768px) {
  .error-content {
    padding: var(--gp-spacing-xl);
  }
  
  .error-title {
    font-size: 1.5rem;
  }
  
  .error-message {
    font-size: 1rem;
  }
  
  .error-actions {
    flex-direction: column;
    align-items: center;
  }
  
  .retry-button,
  .home-button {
    width: 100%;
    max-width: 200px;
  }
  
  .error-status {
    flex-direction: column;
    text-align: center;
  }
}
</style>