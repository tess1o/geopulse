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
            <summary>{{ t('errors.page.details.summary') }}</summary>
            <div v-if="parsedErrorDetails" class="error-details-formatted">
              <div class="error-detail-section">
                <h4>{{ t('errors.page.details.requestHeading') }}</h4>
                <div class="detail-grid">
                  <div class="detail-item">
                    <span class="detail-label">{{ t('errors.page.details.method') }}</span>
                    <span class="detail-value">{{ parsedErrorDetails.method || 'N/A' }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">{{ t('errors.page.details.url') }}</span>
                    <span class="detail-value url">{{ parsedErrorDetails.url || 'N/A' }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">{{ t('errors.page.details.status') }}</span>
                    <span class="detail-value status" :class="getStatusClass(parsedErrorDetails.status)">
                      {{ parsedErrorDetails.status || 'N/A' }}
                    </span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">{{ t('errors.page.details.timestamp') }}</span>
                    <span class="detail-value">{{ formatTimestamp(parsedErrorDetails.timestamp) }}</span>
                  </div>
                </div>
              </div>
              
              <div class="error-detail-section">
                <h4>{{ t('errors.page.details.errorHeading') }}</h4>
                <div class="detail-grid">
                  <div class="detail-item">
                    <span class="detail-label">{{ t('errors.page.details.requestId') }}</span>
                    <span class="detail-value">{{ parsedErrorDetails.requestId || 'N/A' }}</span>
                  </div>
                  <div class="detail-item">
                    <span class="detail-label">{{ t('errors.page.details.errorId') }}</span>
                    <span class="detail-value">{{ parsedErrorDetails.errorId || 'N/A' }}</span>
                  </div>
                </div>
              </div>
            </div>
          </details>
        </div>

        <!-- Action Buttons -->
        <div class="error-actions">
          <Button
            :label="t('errors.page.actions.checkConnection')"
            icon="pi pi-refresh"
            @click="checkBackendStatus"
            class="retry-button"
            :loading="retrying"
          />
          <Button
            :label="t('errors.page.actions.goHome')"
            icon="pi pi-home"
            severity="secondary"
            outlined
            @click="goHome"
            class="home-button"
          />
        </div>

        <!-- Connection Tips -->
        <div v-if="errorType === 'connection'" class="error-tips">
          <h3>{{ t('errors.page.tips.heading') }}</h3>
          <ul>
            <li>{{ t('errors.page.tips.wait') }}</li>
            <li>{{ t('errors.page.tips.internet') }}</li>
            <li>{{ t('errors.page.tips.refresh') }}</li>
            <li>{{ t('errors.page.tips.maintenance') }}</li>
            <li>{{ t('errors.page.tips.checkBack') }}</li>
          </ul>
        </div>

        <!-- Status Info -->
        <div class="error-status">
          <div class="status-item">
            <span class="status-label">{{ t('errors.page.status.internetConnection') }}</span>
            <span :class="['status-value', isOnline ? 'status-online' : 'status-offline']">
              <i :class="isOnline ? 'pi pi-check-circle' : 'pi pi-times-circle'"></i>
              {{ isOnline ? t('errors.page.status.connected') : t('errors.page.status.offline') }}
            </span>
          </div>
          <div class="status-item">
            <span class="status-label">{{ t('errors.page.status.backend') }}</span>
            <span :class="['status-value', connectionStatus.class]">
              <i :class="connectionStatus.icon"></i>
              {{ connectionStatus.text }}
            </span>
          </div>
          <div class="status-item">
            <span class="status-label">{{ t('errors.page.status.lastChecked') }}</span>
            <span class="status-value">{{ lastUpdated }}</span>
          </div>
        </div>
      </div>
    </div>
    <Toast />
    <ErrorReferenceToast />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import Toast from 'primevue/toast'
import ErrorReferenceToast from '@/components/ui/layout/ErrorReferenceToast.vue'
import { useTimezone } from '@/composables/useTimezone'

const timezone = useTimezone()
const { t } = useI18n()

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
const formatCurrentTime = () => timezone.formatTime(timezone.now().toISOString(), { withSeconds: true })
const lastUpdated = ref(formatCurrentTime())
const checkingStatus = ref(false)
const recovering = ref(false)
let statusCheckInterval = null
let recoveryRedirectTimeout = null
const BACKEND_STATUS_POLL_INTERVAL_MS = 15000

const getRecoveryTarget = () => {
  try {
    const target = sessionStorage.getItem('errorReturnTo')
    if (target && !target.startsWith('/error')) {
      return target
    }
  } catch (error) {
    console.warn('Unable to read recovery target from session storage:', error)
  }

  return '/'
}

const redirectAfterRecovery = () => {
  if (recovering.value) return

  recovering.value = true
  const target = getRecoveryTarget()

  recoveryRedirectTimeout = setTimeout(() => {
    try {
      sessionStorage.removeItem('errorReturnTo')
    } catch (error) {
      console.warn('Unable to clear recovery target from session storage:', error)
    }

    window.location.replace(target)
  }, 1500)
}

// Update connection status
const updateConnectionStatus = async () => {
  isOnline.value = navigator.onLine
  lastUpdated.value = formatCurrentTime()
  
  // Also check backend status
  await checkBackendConnectivity()
}

// Check if backend is reachable
const checkBackendConnectivity = async () => {
  if (checkingStatus.value || recovering.value) {
    return
  }

  if (!isOnline.value) {
    backendOnline.value = false
    return
  }
  
  checkingStatus.value = true
  const wasOffline = !backendOnline.value
  
  try {
    const API_BASE_URL = window.VUE_APP_CONFIG?.API_BASE_URL || '/api/v1'
    
    const response = await fetch(`${API_BASE_URL}/system/health`, {
      method: 'GET',
      signal: AbortSignal.timeout(3000), // 3 second timeout
      cache: 'no-cache'
    })
    
    backendOnline.value = response.ok
    
    // Only auto-recover from the backend connection error screen
    if (props.errorType === 'connection' && backendOnline.value && wasOffline) {
      toast.add({
        severity: 'success',
        summary: t('errors.page.restored.title'),
        detail: t('errors.page.restored.detail'),
        life: 3000
      })

      redirectAfterRecovery()
    }
  } catch (error) {
    backendOnline.value = false
  } finally {
    checkingStatus.value = false
  }
}

const detailsFromSession = ref(null)

// Event listeners for online/offline
onMounted(async () => {
  const storedDetails = sessionStorage.getItem('errorDetails')
  if (storedDetails) {
    detailsFromSession.value = storedDetails
    sessionStorage.removeItem('errorDetails')
  }

  window.addEventListener('online', updateConnectionStatus)
  window.addEventListener('offline', updateConnectionStatus)
  
  // Initial backend status check
  await checkBackendConnectivity()
  
  // Check backend status periodically and auto-recover when it comes back
  statusCheckInterval = setInterval(async () => {
    await checkBackendConnectivity()
  }, BACKEND_STATUS_POLL_INTERVAL_MS)
})

onUnmounted(() => {
  window.removeEventListener('online', updateConnectionStatus)
  window.removeEventListener('offline', updateConnectionStatus)
  
  // Clear the status check interval
  if (statusCheckInterval) {
    clearInterval(statusCheckInterval)
  }

  if (recoveryRedirectTimeout) {
    clearTimeout(recoveryRedirectTimeout)
  }
})

// Computed properties
const errorTitle = computed(() => {
  if (props.title) return props.title

  switch (props.errorType) {
    case 'connection':
      return t('errors.page.connection.title')
    case 'server':
      return t('errors.page.server.title')
    default:
      return t('errors.page.generic.title')
  }
})

const errorMessage = computed(() => {
  if (props.message) return props.message

  switch (props.errorType) {
    case 'connection':
      return t('errors.page.connection.message')
    case 'server':
      return t('errors.page.server.message')
    default:
      return t('errors.page.generic.message')
  }
})

const errorDetails = computed(() => detailsFromSession.value)

const parsedErrorDetails = computed(() => {
  const details = detailsFromSession.value
  if (!details) return null
  
  try {
    const parsed = JSON.parse(details)
    return {
      timestamp: parsed.timestamp,
      method: parsed.method,
      url: parsed.url?.split('?')[0],
      status: parsed.status,
      requestId: parsed.requestId,
      errorId: parsed.errorId
    }
  } catch (error) {
    return null
  }
})

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
      text: t('errors.page.status.online'),
      class: 'status-online',
      icon: 'pi pi-check-circle'
    }
  }
  
  return {
    text: t('errors.page.status.offlineShort'),
    class: 'status-offline',
    icon: 'pi pi-times-circle'
  }
})

// Methods
const checkBackendStatus = async () => {
  retrying.value = true
  try {
    // Get API base URL
    const API_BASE_URL = window.VUE_APP_CONFIG?.API_BASE_URL || '/api/v1'
    
    // Try to ping the backend with a simple health check
    const response = await fetch(`${API_BASE_URL}/system/health`, {
      method: 'GET',
      signal: AbortSignal.timeout(5000),
      cache: 'no-cache'
    })
    
    if (response.ok) {
      // Backend is back online!
      toast.add({
        severity: 'success',
        summary: t('errors.page.restored.title'),
        detail: t('errors.page.restored.detail'),
        life: 3000
      })

      redirectAfterRecovery()
    } else {
      throw new Error('Backend still unavailable')
    }
  } catch (error) {
    console.error('Backend still down:', error)
    toast.add({
      severity: 'warn',
      summary: t('errors.page.stillUnavailable.title'),
      detail: t('errors.page.stillUnavailable.detail'),
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

// Utility methods for formatting error details
const getStatusClass = (status) => {
  if (!status) return ''
  if (status >= 200 && status < 300) return 'status-success'
  if (status >= 400 && status < 500) return 'status-client-error'
  if (status >= 500) return 'status-server-error'
  return 'status-unknown'
}

const formatTimestamp = (timestamp) => {
  if (!timestamp) return 'N/A'
  try {
    return `${timezone.formatDateDisplay(timestamp)} ${timezone.formatTime(timestamp, { withSeconds: true })}`
  } catch (error) {
    return timestamp
  }
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

/* Enhanced error details styles */
.error-details-formatted {
  text-align: left;
  font-size: 0.875rem;
}

.error-detail-section {
  margin-bottom: var(--gp-spacing-lg);
  padding: var(--gp-spacing-md);
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-small);
  border: 1px solid var(--gp-border-light);
}

.error-detail-section h4 {
  margin: 0 0 var(--gp-spacing-sm);
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  border-bottom: 1px solid var(--gp-border-light);
  padding-bottom: var(--gp-spacing-xs);
}

.detail-grid {
  display: grid;
  gap: var(--gp-spacing-sm);
}

.detail-item {
  display: flex;
  align-items: flex-start;
  gap: var(--gp-spacing-sm);
}

.detail-label {
  font-weight: 600;
  color: var(--gp-text-secondary);
  min-width: 120px;
  flex-shrink: 0;
}

.detail-value {
  color: var(--gp-text-primary);
  flex: 1;
  word-break: break-all;
}

.detail-value.url {
  font-family: monospace;
  background: var(--gp-surface-lighter);
  padding: 2px 4px;
  border-radius: 3px;
  font-size: 0.8rem;
}

.detail-value.user-agent {
  font-family: monospace;
  font-size: 0.75rem;
  line-height: 1.3;
}

.detail-value.status {
  font-weight: 600;
}

.status-success {
  color: var(--gp-success);
}

.status-client-error {
  color: var(--gp-warning);
}

.status-server-error {
  color: var(--gp-danger);
}

.status-unknown {
  color: var(--gp-text-muted);
}

.response-data, .headers-data, .stack-trace, .raw-details {
  background: var(--gp-surface-lighter, #f9fafb);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-small);
  padding: var(--gp-spacing-sm);
  font-size: 0.8rem;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  color: var(--gp-text-primary);
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
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

.p-dark .error-detail-section {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

.p-dark .error-detail-section h4 {
  border-bottom-color: var(--gp-border-dark);
}

.p-dark .detail-value.url {
  background: var(--gp-surface-darker);
}

.p-dark .response-data, 
.p-dark .headers-data, 
.p-dark .stack-trace, 
.p-dark .raw-details {
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
