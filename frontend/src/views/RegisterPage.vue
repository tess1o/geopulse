<template>
  <div class="register-page">
    <div class="register-layout">
      <!-- Main Content -->
      <div class="register-content">
        <!-- Locale Switcher -->
        <div class="locale-switcher-corner">
          <LocaleSwitcher />
        </div>

        <!-- Logo Section -->
        <div class="logo-section">
          <img src="/geopulse-logo.svg" alt="GeoPulse" class="app-logo"/>
        </div>

        <div v-if="!registrationStatus.passwordRegistrationEnabled" class="signup-disabled-message">
          <i class="pi pi-exclamation-triangle"></i>
          <span v-if="oidcProviders?.length !== 0">{{ t('auth.register.disabledMessage') }}</span>
          <span v-else>{{ t('auth.register.disabledMessageShort') }}</span>
        </div>

        <!-- Register Form -->
        <Card class="register-card">
          <template #content>
            <div class="register-form-content">
              <!-- Form Header -->
              <div class="form-header">
                <h2 class="form-title">{{ t('auth.register.title') }}</h2>
                <p class="form-subtitle">{{ t('auth.register.subtitle') }}</p>
              </div>

              <!-- Register Form -->
              <form @submit.prevent="handleSubmit" class="register-form"
                    v-if="registrationStatus.passwordRegistrationEnabled">
                <!-- Email Field -->
                <div class="form-field">
                  <label for="email" class="field-label">{{ t('auth.register.emailLabel') }}</label>
                  <InputText
                      id="email"
                      v-model="formData.email"
                      type="email"
                      :placeholder="t('auth.register.emailPlaceholder')"
                      :invalid="!!formErrors.email"
                      class="form-input"
                      autocomplete="email"
                      @input="clearFieldError('email')"
                  />
                  <small v-if="formErrors.email" class="error-message">
                    {{ formErrors.email }}
                  </small>
                </div>

                <!-- Full Name Field -->
                <div class="form-field">
                  <label for="fullName" class="field-label">{{ t('auth.register.fullNameLabel') }}</label>
                  <InputText
                      id="fullName"
                      v-model="formData.fullName"
                      type="text"
                      :placeholder="t('auth.register.fullNamePlaceholder')"
                      :invalid="!!formErrors.fullName"
                      class="form-input"
                      autocomplete="name"
                      @input="clearFieldError('fullName')"
                  />
                  <small v-if="formErrors.fullName" class="error-message">
                    {{ formErrors.fullName }}
                  </small>
                </div>

                <!-- Password Field -->
                <div class="form-field">
                  <label for="password" class="field-label">{{ t('auth.register.passwordLabel') }}</label>
                  <Password
                      id="password"
                      v-model="formData.password"
                      :placeholder="t('auth.register.passwordPlaceholder')"
                      :feedback="false"
                      toggleMask
                      :invalid="!!formErrors.password"
                      class="form-input password-input"
                      autocomplete="new-password"
                      @input="clearFieldError('password')"
                  />
                  <small v-if="formErrors.password" class="error-message">
                    {{ formErrors.password }}
                  </small>
                </div>

                <!-- Confirm Password Field -->
                <div class="form-field">
                  <label for="confirmPassword" class="field-label">{{ t('auth.register.confirmPasswordLabel') }}</label>
                  <Password
                      id="confirmPassword"
                      v-model="formData.confirmPassword"
                      :placeholder="t('auth.register.confirmPasswordPlaceholder')"
                      :feedback="false"
                      toggleMask
                      :invalid="!!formErrors.confirmPassword"
                      class="form-input password-input"
                      autocomplete="new-password"
                      @input="clearFieldError('confirmPassword')"
                  />
                  <small v-if="formErrors.confirmPassword" class="error-message">
                    {{ formErrors.confirmPassword }}
                  </small>
                </div>

                <!-- Submit Button -->
                <Button
                    type="submit"
                    :label="t('auth.register.createAccount')"
                    icon="pi pi-user-plus"
                    :loading="isLoading"
                    :disabled="isLoading || !isFormValid || !registrationStatus.passwordRegistrationEnabled"
                    class="submit-button"
                />

                <!-- Error Display -->
                <div v-if="registerError" class="register-error">
                  <i class="pi pi-exclamation-triangle"></i>
                  <span>{{ registerError }}</span>
                </div>
              </form>

              <!-- OIDC Providers Section -->
              <OidcProvidersSection
                  v-if="registrationStatus.oidcRegistrationEnabled"
                  :providers="oidcProviders"
                  :disabled="isLoading"
                  :show-divider="registrationStatus.passwordRegistrationEnabled"
                  @provider-selected="handleOidcLogin"
              />

              <!-- Login Link -->
              <div class="login-section">
                <span class="login-text">{{ t('auth.register.alreadyHaveAccount') }}</span>
                <router-link to="/login" class="login-link">
                  {{ t('auth.register.signIn') }}
                </router-link>
              </div>
            </div>
          </template>
        </Card>

      </div>
    </div>

    <Toast/>
    <ErrorReferenceToast />
  </div>
</template>

<script setup>
import {ref, computed, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import {useToast} from 'primevue/usetoast'
import {useAuthStore} from '@/stores/auth'
import {useLocale} from '@/composables/useLocale'
import {getBrowserTimezone} from '@/utils/timezoneUtils'
import {getRegistrationErrorMessage} from '@/utils/registrationErrors'
import OidcProvidersSection from '@/components/auth/OidcProvidersSection.vue'
import ErrorReferenceToast from '@/components/ui/layout/ErrorReferenceToast.vue'
import LocaleSwitcher from '@/components/LocaleSwitcher.vue'

// Composables
const {t} = useI18n()
const router = useRouter()
const toast = useToast()
const authStore = useAuthStore()
const {locale} = useLocale()

// State
const isLoading = ref(false)
const registerError = ref('')
const oidcProviders = ref([])
const registrationStatus = ref({passwordRegistrationEnabled: true, oidcRegistrationEnabled: true});

// Form data
const formData = ref({
  email: '',
  fullName: '',
  password: '',
  confirmPassword: ''
})

const formErrors = ref({})

// Computed
const isFormValid = computed(() => {
  return formData.value.email &&
      formData.value.fullName &&
      formData.value.password &&
      formData.value.confirmPassword &&
      Object.keys(formErrors.value).length === 0
})

// Methods
const validateForm = () => {
  formErrors.value = {}

  // Email validation
  if (!formData.value.email?.trim()) {
    formErrors.value.email = t('auth.validation.emailRequired')
  } else if (!isValidEmail(formData.value.email)) {
    formErrors.value.email = t('auth.validation.emailInvalid')
  }

  // Full name validation
  if (!formData.value.fullName?.trim()) {
    formErrors.value.fullName = t('auth.register.validation.fullNameRequired')
  } else if (formData.value.fullName.trim().length < 2) {
    formErrors.value.fullName = t('auth.register.validation.fullNameTooShort')
  }

  // Password validation
  if (!formData.value.password) {
    formErrors.value.password = t('auth.validation.passwordRequired')
  } else if (formData.value.password.length < 6) {
    formErrors.value.password = t('auth.register.validation.passwordTooShort')
  }

  // Confirm password validation
  if (!formData.value.confirmPassword) {
    formErrors.value.confirmPassword = t('auth.validation.confirmRequired')
  } else if (formData.value.password !== formData.value.confirmPassword) {
    formErrors.value.confirmPassword = t('auth.validation.mismatch')
  }

  return Object.keys(formErrors.value).length === 0
}

const isValidEmail = (email) => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return emailRegex.test(email)
}

const clearFieldError = (field) => {
  if (formErrors.value[field]) {
    delete formErrors.value[field]
  }
  if (registerError.value) {
    registerError.value = ''
  }
}

const handleSubmit = async () => {
  if (!validateForm()) return

  isLoading.value = true
  registerError.value = ''

  try {
    // Auto-detect user's timezone (normalized for Java compatibility)
    const detectedTimezone = getBrowserTimezone()

    await authStore.register(
        formData.value.email.trim(),
        formData.value.password,
        formData.value.fullName.trim(),
        detectedTimezone,
        locale.value
    )

    toast.add({
      severity: 'success',
      summary: t('auth.register.toasts.created.title'),
      detail: t('auth.register.toasts.created.detail'),
      life: 3000
    })

    // Navigate to location sources for onboarding
    await router.push('/app/location-sources')

  } catch (error) {
    console.error('Registration error:', error)
    if (error.accountCreated) {
      toast.add({
        severity: 'info',
        summary: t('auth.register.toasts.signInRequired.title'),
        detail: t('auth.register.toasts.signInRequired.detail'),
        life: 5000
      })
      await router.push('/login')
      return
    }
    registerError.value = getRegistrationErrorMessage(error)
  } finally {
    isLoading.value = false
  }
}

const handleOidcLogin = async (providerName) => {
  isLoading.value = true;
  try {
    await authStore.initiateOidcLogin(providerName);
    // The browser will be redirected, so no need to set isLoading to false here.
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('auth.register.toasts.failed'),
      detail: t('auth.register.toasts.oidcInitFailed', {provider: providerName}),
      life: 5000
    });
    isLoading.value = false;
  }
};


const getOidcProviders = async () => {
  try {
    const providers = await authStore.getOidcProviders();
    oidcProviders.value = providers;
  } catch (error) {
    console.error('Failed to load OIDC providers:', error);
    return [];
  }
}

// Lifecycle
onMounted(async () => {
  // Clear any existing auth data
  if (authStore.isAuthenticated) {
    router.push('/app/location-sources')
  }

  try {
    const status = await authStore.getRegistrationStatus();
    registrationStatus.value = status;

    if (!status.passwordRegistrationEnabled && !status.oidcRegistrationEnabled) {
      toast.add({
        severity: 'error',
        summary: t('auth.toasts.registrationDisabled'),
        detail: t('auth.toasts.registrationDisabledDetail'),
        life: 5000
      });
      router.push({path: '/login', query: {reason: 'registration_disabled'}});
      return;
    }

    if (status.oidcRegistrationEnabled) {
      await getOidcProviders();
    }
  } catch (error) {
    console.error('Failed to load registration status or OIDC providers', error)
    toast.add({
      severity: 'error',
      summary: t('auth.register.toasts.optionsLoadFailed'),
      detail: t('auth.register.toasts.optionsLoadFailedDetail'),
      life: 5000
    });
  }
})
</script>

<style scoped>
.register-page {
  min-height: 100vh;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.register-page::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, var(--gp-surface-card) 0%, var(--gp-surface-ground) 100%);
  z-index: 0;
}

.register-page::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: radial-gradient(ellipse at center, rgba(26, 86, 219, 0.1) 0%, transparent 70%);
  z-index: 1;
}

/* Layout */
.register-layout {
  position: relative;
  padding: 1rem 1rem;
  width: 100%;
  max-width: 420px;
  z-index: 2;
}

.register-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
}

.locale-switcher-corner {
  position: fixed;
  top: 1rem;
  right: 1rem;
  z-index: 3;
}

/* Logo Section */
.logo-section {
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 0.5rem;
}

.app-logo {
  width: 150px;
  height: auto;
  filter: drop-shadow(0 4px 8px rgba(26, 86, 219, 0.2));
  transition: transform 0.3s ease;
  display: block;
}

.app-logo:hover {
  transform: scale(1.05);
}

/* Register Card */
.register-card {
  width: 100%;
  background: var(--gp-surface-card);
  border: 1px solid var(--gp-border);
  box-shadow: var(--gp-shadow-card);
}

.register-form-content {
  padding: 1rem;
}

/* Form Header */
.form-header {
  text-align: center;
  margin-bottom: 2rem;
}

.form-title {
  font-size: 1.75rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.5rem 0;
}

.form-subtitle {
  font-size: 1rem;
  color: var(--gp-text-secondary);
  margin: 0;
}

/* Form */
.register-form {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.field-label {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.form-input {
  width: 100%;
}

.password-input {
  width: 100%;
}

.error-message {
  color: var(--gp-danger);
  font-size: 0.85rem;
  margin-top: 0.25rem;
}

/* Submit Button */
.submit-button {
  width: 100%;
  padding: 0.875rem 1.5rem;
  font-size: 1rem;
  font-weight: 600;
  background: var(--gp-primary);
  border-color: var(--gp-primary);
  transition: all 0.3s ease;
}

.submit-button:hover:not(:disabled) {
  background: var(--gp-primary-hover);
  border-color: var(--gp-primary-hover);
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(26, 86, 219, 0.3);
}

/* Register Error */
.register-error {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid var(--gp-danger);
  border-radius: var(--gp-radius-medium);
  color: var(--gp-danger);
  font-size: 0.9rem;
  margin-top: -0.5rem;
}

/* Login Section */
.login-section {
  text-align: center;
  padding-top: 1rem;
  border-top: 1px solid var(--gp-border);
}

.login-text {
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
  margin-right: 0.5rem;
}

.login-link {
  color: var(--gp-primary);
  text-decoration: none;
  font-weight: 600;
  font-size: 0.9rem;
  transition: color 0.2s ease;
}

.login-link:hover {
  color: var(--gp-primary-hover);
  text-decoration: underline;
}

/* Input Styling */
:deep(.p-inputtext) {
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-medium);
  padding: 0.875rem 1rem;
  transition: all 0.2s ease;
  font-size: 1rem;
}

:deep(.p-inputtext:focus) {
  border-color: var(--gp-primary);
  box-shadow: 0 0 0 3px rgba(26, 86, 219, 0.1);
}

:deep(.p-password) {
  width: 100%;
}

:deep(.p-password-input) {
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-medium);
  padding: 0.875rem 3rem 0.875rem 1rem;
  transition: all 0.2s ease;
  width: 100%;
  font-size: 1rem;
}

:deep(.p-password-input:focus) {
  border-color: var(--gp-primary);
  box-shadow: 0 0 0 3px rgba(26, 86, 219, 0.1);
}

:deep(.p-button) {
  border-radius: var(--gp-radius-medium);
  transition: all 0.3s ease;
}

/* Responsive Design */
@media (max-width: 768px) {
  .register-layout {
    padding: 1rem;
  }

  .register-card {
    max-width: 100%;
  }
}

@media (max-width: 480px) {
  .register-form-content {
    padding: 0.5rem;
  }
}

.signup-disabled-message {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  margin-bottom: 1rem;
  background: var(--gp-warning-soft);
  border: 1px solid var(--gp-warning-border);
  border-radius: var(--gp-radius-medium);
  color: var(--gp-warning-text);
  font-size: 0.95rem;
  font-weight: 500;
  text-align: center;
}
</style>