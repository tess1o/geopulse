<template>
  <div class="oidc-callback-page">
    <div class="callback-content">
      <div v-if="isProcessing" class="processing-state">
        <ProgressSpinner/>
        <h2 class="processing-title">{{ t('auth.callback.processingTitle') }}</h2>
        <p class="processing-description">{{ t('auth.callback.processingDescription') }}</p>
      </div>

      <div v-else-if="error && !linkingData" class="error-state">
        <i class="pi pi-exclamation-triangle error-icon"></i>
        <h2 class="error-title">{{ t('auth.callback.errorTitle') }}</h2>
        <p class="error-description">{{ error }}</p>
        <Button :label="t('auth.callback.returnToLogin')" @click="returnToLogin" class="return-button"/>
      </div>
    </div>

    <!-- Account Linking Modal -->
    <AccountLinkingModal
        v-if="linkingData"
        v-model:visible="showLinkingModal"
        :linkingData="linkingData"
        @success="handleLinkingSuccess"
        @cancel="handleLinkingCancel"
    />
  </div>
</template>

<script setup>
import {ref, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {useAuthStore} from '@/stores/auth'
import {useToast} from 'primevue/usetoast'
import {useTimezone} from '@/composables/useTimezone'
import {formatApiErrorDetail} from '@/utils/apiErrorDetail'

import Button from 'primevue/button';
import ProgressSpinner from 'primevue/progressspinner';
import AccountLinkingModal from '@/components/auth/AccountLinkingModal.vue';

const {t} = useI18n()
const timezone = useTimezone()

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const toast = useToast()

const isProcessing = ref(true)
const error = ref(null)
const linkingData = ref(null)
const showLinkingModal = ref(false)

onMounted(async () => {
  try {
    const {code, state, error: oauthError, error_description} = route.query

    if (oauthError) {
      throw new Error(error_description || oauthError)
    }

    if (!code || !state) {
      throw new Error(t('auth.callback.missingParams'))
    }

    const authResponse = await authStore.handleOidcCallback(code, state)

    // Check if this is a new user based on createdAt timestamp
    const isNewUser = isUserNew(authResponse.createdAt)

    toast.add({
      severity: 'success',
      summary: isNewUser ? t('auth.callback.toasts.welcomeNew') : t('auth.callback.toasts.welcomeBack'),
      detail: t('auth.callback.toasts.authenticated'),
      life: 3000
    })

    // Redirect based on priority: redirectUri > defaultRedirectUrl > onboarding logic
    const redirectPath = authResponse.redirectUri ||
                        authResponse.defaultRedirectUrl ||
                        (isNewUser ? '/app/location-sources' : '/app/timeline')
    await router.push(redirectPath)

  } catch (err) {
    console.error('OIDC callback error:', err)

    // Check if this is an account linking requirement
    if (err.code === 'OIDC_ACCOUNT_LINKING_REQUIRED' && authStore.accountLinking) {

      linkingData.value = authStore.accountLinking
      showLinkingModal.value = true

    } else {
      error.value = formatApiErrorDetail(err, t('auth.callback.genericError'))
      toast.add({
        severity: 'error',
        summary: t('auth.callback.errorTitle'),
        detail: error.value,
        life: 5000
      });
    }
  } finally {
    isProcessing.value = false
  }
})

const returnToLogin = () => {
  router.push('/login')
}

const isUserNew = (createdAt) => {
  if (!createdAt) return false

  const userCreatedAt = timezone.fromUtc(createdAt)
  const now = timezone.now()
  const diffInMinutes = now.diff(userCreatedAt, 'minute')

  // Consider user "new" if created within the last 5 minutes
  return diffInMinutes <= 5
}

const handleLinkingSuccess = async (authResponse) => {
  showLinkingModal.value = false

  // Check if this is a new user based on createdAt timestamp  
  const isNewUser = isUserNew(authResponse.createdAt)

  toast.add({
    severity: 'success',
    summary: t('auth.callback.toasts.linked.title'),
    detail: t('auth.callback.toasts.linked.detail'),
    life: 3000
  })

  // Redirect based on priority: redirectUri > defaultRedirectUrl > onboarding logic
  const redirectPath = authResponse.redirectUri ||
                      authResponse.defaultRedirectUrl ||
                      (isNewUser ? '/app/location-sources' : '/app/timeline')
  await router.push(redirectPath)
}

const handleLinkingCancel = () => {
  showLinkingModal.value = false
  returnToLogin()
}
</script>

<style scoped>
.oidc-callback-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gp-surface-ground);
  padding: var(--gp-spacing-lg);
}

.callback-content {
  text-align: center;
  padding: var(--gp-spacing-xxl);
  background: var(--gp-surface-card);
  border-radius: var(--gp-radius-large);
  box-shadow: var(--gp-shadow-card);
  border: 1px solid var(--gp-border);
  max-width: 450px;
  width: 100%;
}

.processing-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--gp-spacing-lg);
}

.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--gp-spacing-md);
}

.error-icon {
  font-size: 3rem;
  color: var(--gp-danger);
  margin-bottom: var(--gp-spacing-md);
}

.processing-title,
.error-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0;
}

.processing-description {
  color: var(--gp-text-secondary);
  line-height: 1.5;
  margin: 0 0 var(--gp-spacing-xl) 0;
  font-size: 0.95rem;
}

.error-description {
  color: var(--gp-text-secondary);
  line-height: 1.5;
  margin: 0;
  font-size: 0.95rem;
}

.return-button {
  margin-top: var(--gp-spacing-lg);
}

/* Progress spinner styling */
:deep(.p-progress-spinner) {
  width: 3rem;
  height: 3rem;
}

:deep(.p-progress-spinner-svg) {
  animation: p-progress-spinner-rotate 2s linear infinite;
}

:deep(.p-progress-spinner-circle) {
  stroke: var(--gp-primary);
  stroke-width: 2;
  stroke-linecap: round;
}

/* Button styling */
:deep(.p-button) {
  border-radius: var(--gp-radius-medium);
  font-weight: 600;
  transition: all 0.2s ease;
  padding: 0.75rem 1.5rem;
}

:deep(.p-button:not(.p-button-outlined)) {
  background: var(--gp-primary);
  border-color: var(--gp-primary);
  color: var(--gp-primary-contrast);
}

:deep(.p-button:not(.p-button-outlined):hover) {
  background: var(--gp-primary-hover);
  border-color: var(--gp-primary-hover);
  transform: translateY(-1px);
  box-shadow: var(--gp-shadow-medium);
}
</style>
