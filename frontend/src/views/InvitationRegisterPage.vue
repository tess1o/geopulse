<template>
  <div class="invitation-register-page">
    <div class="locale-switcher-corner">
      <LocaleSwitcher />
    </div>

    <div class="register-container">
      <div class="register-card">
        <div class="text-center mb-4">
          <h1 class="app-title">{{ t('auth.invitation.brand') }}</h1>
          <p class="text-muted">{{ t('auth.invitation.title') }}</p>
        </div>

        <!-- Loading State -->
        <div v-if="validating" class="text-center p-5">
          <ProgressSpinner style="width: 50px; height: 50px" strokeWidth="4" />
          <p class="mt-3">{{ t('auth.invitation.validating') }}</p>
        </div>

        <!-- Invalid/Expired Invitation -->
        <div v-else-if="!invitationValid" class="text-center p-4">
          <i class="pi pi-exclamation-circle text-6xl text-red-500 mb-3"></i>
          <h3>{{ t('auth.invitation.invalidTitle') }}</h3>
          <p class="text-muted">{{ validationMessage }}</p>
          <Button
            :label="t('auth.invitation.goToLogin')"
            icon="pi pi-sign-in"
            class="mt-3"
            @click="router.push('/login')"
          />
        </div>

        <!-- Registration Form -->
        <form v-else @submit.prevent="handleSubmit" class="register-form">
          <Message v-if="errorMessage" severity="error" :closable="false" class="mb-3">
            {{ errorMessage }}
          </Message>

          <div class="field">
            <label for="email">{{ t('auth.invitation.emailLabel') }}</label>
            <InputText
              id="email"
              v-model="form.email"
              type="email"
              :placeholder="t('auth.invitation.emailPlaceholder')"
              required
              autocomplete="email"
              :class="{ 'p-invalid': errors.email }"
            />
            <small v-if="errors.email" class="p-error">{{ errors.email }}</small>
          </div>

          <div class="field">
            <label for="fullName">{{ t('auth.invitation.fullNameLabel') }}</label>
            <InputText
              id="fullName"
              v-model="form.fullName"
              :placeholder="t('auth.invitation.fullNamePlaceholder')"
              autocomplete="name"
            />
          </div>

          <div class="field">
            <label for="password">{{ t('auth.invitation.passwordLabel') }}</label>
            <Password
              id="password"
              v-model="form.password"
              :placeholder="t('auth.invitation.passwordPlaceholder')"
              :feedback="true"
              toggleMask
              required
              autocomplete="new-password"
              :class="{ 'p-invalid': errors.password }"
            />
            <small v-if="errors.password" class="p-error">{{ errors.password }}</small>
          </div>

          <div class="field">
            <label for="confirmPassword">{{ t('auth.invitation.confirmPasswordLabel') }}</label>
            <Password
              id="confirmPassword"
              v-model="form.confirmPassword"
              :placeholder="t('auth.invitation.confirmPasswordPlaceholder')"
              :feedback="false"
              toggleMask
              required
              autocomplete="new-password"
              :class="{ 'p-invalid': errors.confirmPassword }"
            />
            <small v-if="errors.confirmPassword" class="p-error">{{ errors.confirmPassword }}</small>
          </div>

          <div class="field">
            <label for="timezone">{{ t('auth.invitation.timezoneLabel') }}</label>
            <Select
              id="timezone"
              v-model="form.timezone"
              :options="timezones"
              filter
              :placeholder="form.timezone"
              class="w-full"
            />
            <small class="text-muted">{{ t('auth.invitation.timezoneDetected', { timezone: form.timezone }) }}</small>
          </div>

          <Button
            type="submit"
            :label="t('auth.invitation.createAccount')"
            icon="pi pi-user-plus"
            class="w-full mt-3"
            :loading="submitting"
          />

          <div class="text-center mt-3">
            <router-link to="/login" class="text-primary">
              {{ t('auth.invitation.loginLink') }}
            </router-link>
          </div>
        </form>
      </div>
    </div>

    <Toast />
    <ErrorReferenceToast />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Select from 'primevue/select'
import Message from 'primevue/message'
import Toast from 'primevue/toast'
import ErrorReferenceToast from '@/components/ui/layout/ErrorReferenceToast.vue'
import LocaleSwitcher from '@/components/LocaleSwitcher.vue'
import ProgressSpinner from 'primevue/progressspinner'
import { useToast } from 'primevue/usetoast'
import { useAuthStore } from '@/stores/auth'
import { useLocale } from '@/composables/useLocale'
import { normalizeApiError } from '@/utils/apiErrorDetail'
import { getRegistrationErrorMessage, invitationStatusMessage } from '@/utils/registrationErrors'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const toast = useToast()
const authStore = useAuthStore()
const { locale } = useLocale()

const token = ref(route.params.token)
const validating = ref(true)
const invitationValid = ref(false)
const validationMessage = ref('')
const submitting = ref(false)
const errorMessage = ref('')

// Timezone mapping for deprecated names
const TIMEZONE_MAPPING = {
  'Europe/Kiev': 'Europe/Kyiv'
}

// Get browser timezone with fallback
const getBrowserTimezone = () => {
  try {
    const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
    // Map deprecated timezone names to current ones
    return TIMEZONE_MAPPING[timezone] || timezone
  } catch (error) {
    return 'UTC'
  }
}

const form = ref({
  email: '',
  fullName: '',
  password: '',
  confirmPassword: '',
  timezone: getBrowserTimezone()
})

const errors = ref({
  email: '',
  password: '',
  confirmPassword: ''
})

// List of common timezones
const timezones = ref([
  'UTC',
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'America/Anchorage',
  'Pacific/Honolulu',
  'Europe/London',
  'Europe/Paris',
  'Europe/Berlin',
  'Europe/Rome',
  'Europe/Madrid',
  'Europe/Amsterdam',
  'Europe/Brussels',
  'Europe/Vienna',
  'Europe/Zurich',
  'Europe/Prague',
  'Europe/Warsaw',
  'Europe/Budapest',
  'Europe/Stockholm',
  'Europe/Copenhagen',
  'Europe/Oslo',
  'Europe/Helsinki',
  'Europe/Athens',
  'Europe/Istanbul',
  'Europe/Kyiv',
  'Europe/Moscow',
  'Asia/Dubai',
  'Asia/Kolkata',
  'Asia/Bangkok',
  'Asia/Singapore',
  'Asia/Hong_Kong',
  'Asia/Tokyo',
  'Asia/Seoul',
  'Asia/Shanghai',
  'Australia/Sydney',
  'Australia/Melbourne',
  'Australia/Brisbane',
  'Australia/Perth',
  'Pacific/Auckland',
  'America/Sao_Paulo',
  'America/Mexico_City',
  'America/Toronto',
  'America/Vancouver',
  'Africa/Cairo',
  'Africa/Johannesburg'
])

const validateInvitation = async () => {
  validating.value = true
  try {
    const response = await authStore.validateInvitation(token.value)
    invitationValid.value = response.valid
    validationMessage.value = invitationStatusMessage(response.status)
  } catch (error) {
    console.error('Failed to validate invitation:', error)
    invitationValid.value = false
    validationMessage.value = normalizeApiError(error).code === 'INVITATION_NOT_FOUND'
      ? t('auth.invitation.errors.notFound')
      : t('auth.invitation.errors.validateFailed')
  } finally {
    validating.value = false
  }
}

const validateForm = () => {
  errors.value = {
    email: '',
    password: '',
    confirmPassword: ''
  }

  let isValid = true

  if (!form.value.email) {
    errors.value.email = t('auth.validation.emailRequired')
    isValid = false
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.value.email)) {
    errors.value.email = t('auth.invitation.validation.emailInvalid')
    isValid = false
  }

  if (!form.value.password) {
    errors.value.password = t('auth.validation.passwordRequired')
    isValid = false
  } else if (form.value.password.length < 3) {
    errors.value.password = t('auth.invitation.validation.passwordTooShort')
    isValid = false
  }

  if (form.value.password !== form.value.confirmPassword) {
    errors.value.confirmPassword = t('auth.validation.mismatch')
    isValid = false
  }

  return isValid
}

const handleSubmit = async () => {
  errorMessage.value = ''

  if (!validateForm()) {
    return
  }

  submitting.value = true

  try {
    const payload = {
      email: form.value.email,
      password: form.value.password,
      fullName: form.value.fullName || form.value.email.split('@')[0],
      timezone: form.value.timezone,
      language: locale.value
    }

    // Register user via invitation
    await authStore.registerInvitation(token.value, payload)
  } catch (error) {
    console.error('Registration failed:', error)
    errorMessage.value = getRegistrationErrorMessage(error)
    submitting.value = false
    return
  }

  try {
    // Log in the user automatically after successful registration
    await authStore.login(form.value.email, form.value.password)
  } catch (error) {
    // The account exists; only the automatic sign-in failed, so send the user to sign in.
    console.error('Sign-in after registration failed:', error)
    toast.add({
      severity: 'info',
      summary: t('auth.register.toasts.signInRequired.title'),
      detail: t('auth.register.toasts.signInRequired.detail'),
      life: 5000
    })
    submitting.value = false
    await router.push('/login')
    return
  }

  toast.add({
    severity: 'success',
    summary: t('auth.invitation.toasts.created.title'),
    detail: t('auth.invitation.toasts.created.detail'),
    life: 3000
  })
  submitting.value = false

  // Navigate to location sources for onboarding
  await router.push('/app/location-sources')
}

onMounted(() => {
  if (!token.value) {
    router.push('/login')
    return
  }
  validateInvitation()
})
</script>

<style scoped>
/* Page Background - Matching GeoPulse RegisterPage */
.invitation-register-page {
  min-height: 100vh;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.invitation-register-page::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, var(--gp-surface-card) 0%, var(--gp-surface-ground) 100%);
  z-index: 0;
}

.invitation-register-page::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: radial-gradient(ellipse at center, rgba(26, 86, 219, 0.1) 0%, transparent 70%);
  z-index: 1;
}

/* Dark Mode */

.locale-switcher-corner {
  position: fixed;
  top: 1rem;
  right: 1rem;
  z-index: 3;
}

/* Container */
.register-container {
  position: relative;
  width: 100%;
  max-width: 450px;
  padding: 1rem;
  z-index: 2;
}

.register-card {
  background: var(--gp-surface-card);
  border-radius: 12px;
  padding: 2.5rem;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
}

/* Title */
.app-title {
  font-size: 2.5rem;
  font-weight: 700;
  color: var(--gp-primary);
  margin: 0;
  letter-spacing: -0.025em;
}

.text-muted {
  color: var(--gp-text-secondary);
  margin: 0.5rem 0 0 0;
}

/* Form */
.register-form {
  margin-top: 1.5rem;
}

.field {
  margin-bottom: 1.25rem;
}

.field label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  color: var(--gp-text-primary);
}

.p-error {
  color: var(--p-red-500);
  font-size: 0.875rem;
  margin-top: 0.25rem;
  display: block;
}

/* Responsive */
@media (max-width: 640px) {
  .register-card {
    padding: 2rem 1.5rem;
  }

  .app-title {
    font-size: 2rem;
  }
}
</style>
