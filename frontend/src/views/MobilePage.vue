<template>
  <div class="mobile-page">
    <div class="mobile-card">
      <h1 class="mobile-title">Mobile</h1>
      <div v-if="isLoading" class="mobile-spinner" aria-hidden="true"></div>
      <p class="mobile-description">{{ message }}</p>
    </div>
  </div>
</template>

<script setup>
import {onBeforeUnmount, onMounted, ref} from 'vue'
import apiService from '@/utils/apiService'

const message = ref('Preparing mobile authentication...')
const isLoading = ref(true)
const APP_OPEN_TIMEOUT_MS = 4000
const CLOSE_PAGE_DELAY_MS = 10000

let appOpenTimeoutId = null
let closePageTimeoutId = null

const clearAppOpenTimeout = () => {
  if (appOpenTimeoutId !== null) {
    window.clearTimeout(appOpenTimeoutId)
    appOpenTimeoutId = null
  }
}

const clearClosePageTimeout = () => {
  if (closePageTimeoutId !== null) {
    window.clearTimeout(closePageTimeoutId)
    closePageTimeoutId = null
  }
}

const handleVisibilityChange = () => {
  if (document.visibilityState === 'hidden') {
    clearAppOpenTimeout()
    clearClosePageTimeout()
  }
}

onMounted(async () => {
  document.addEventListener('visibilitychange', handleVisibilityChange)

  try {
    const response = await apiService.get('/auth/mobile')
    const code = response?.data?.code
    const deeplinkUrl = response?.data?.deeplinkUrl

    if (!code || !deeplinkUrl) {
      isLoading.value = false
      message.value = 'Mobile authentication payload was not returned.'
      return
    }

    await apiService.logoutStrict()

    message.value = 'Opening the app...'
    window.location.assign(`${deeplinkUrl}?code=${encodeURIComponent(code)}`)

    appOpenTimeoutId = window.setTimeout(() => {
      isLoading.value = false
      message.value = 'Opening the app timed out. Please return to the app and try again.'

      closePageTimeoutId = window.setTimeout(() => {
        window.close()
      }, CLOSE_PAGE_DELAY_MS)
    }, APP_OPEN_TIMEOUT_MS)
  } catch (error) {
    isLoading.value = false
    message.value = 'Failed to complete mobile authentication handoff.'
  }
})

onBeforeUnmount(() => {
  clearAppOpenTimeout()
  clearClosePageTimeout()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<style scoped>
.mobile-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--gp-spacing-lg);
  background: var(--gp-surface-light);
}

.mobile-card {
  width: 100%;
  max-width: 420px;
  padding: var(--gp-spacing-xxl);
  border-radius: var(--gp-radius-large);
  border: 1px solid var(--gp-border-light);
  background: var(--gp-surface-white);
  box-shadow: var(--gp-shadow-card);
  text-align: center;
}

.mobile-title {
  margin: 0 0 var(--gp-spacing-md);
  color: var(--gp-text-primary);
  font-size: 1.5rem;
}

.mobile-description {
  margin: 0;
  color: var(--gp-text-secondary);
}

.mobile-spinner {
  width: 2.75rem;
  height: 2.75rem;
  margin: 0 auto var(--gp-spacing-lg);
  border: 0.3rem solid rgba(37, 99, 235, 0.16);
  border-top-color: var(--gp-primary);
  border-radius: 999px;
  animation: mobile-spin 0.9s linear infinite;
}

@keyframes mobile-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
