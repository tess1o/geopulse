<template>
  <div class="login-page">
    <div class="login-layout">
      <!-- Main Content -->
      <div class="login-content">
        <!-- Logo Section -->
        <div class="logo-section">
          <img src="/geopulse-logo.svg" alt="GeoPulse" class="app-logo" />
        </div>
        
        <!-- Login Form -->
        <Card class="login-card">
          <template #content>
            <div class="login-form-content">
              <!-- Form Header -->
              <div class="form-header">
                <h2 class="form-title">Welcome Back</h2>
                <p class="form-subtitle">Sign in to continue your journey</p>
              </div>

              <!-- Login Form -->
              <form @submit.prevent="handleSubmit" class="login-form">
                <!-- Email Field -->
                <div class="form-field">
                  <label for="email" class="field-label">Email Address</label>
                  <InputText
                    id="email"
                    v-model="formData.email"
                    type="email"
                    placeholder="Enter your email"
                    :invalid="!!formErrors.email"
                    class="form-input"
                    autocomplete="email"
                    @input="clearFieldError('email')"
                    @focus="clearFieldError('email')"
                  />
                  <small v-if="formErrors.email" class="error-message">
                    {{ formErrors.email }}
                  </small>
                </div>

                <!-- Password Field -->
                <div class="form-field">
                  <label for="password" class="field-label">Password</label>
                  <Password
                    id="password"
                    v-model="formData.password"
                    placeholder="Enter your password"
                    :feedback="false"
                    toggleMask
                    :invalid="!!formErrors.password"
                    class="form-input password-input"
                    autocomplete="current-password"
                    @input="clearFieldError('password')"
                    @focus="clearFieldError('password')"
                  />
                  <small v-if="formErrors.password" class="error-message">
                    {{ formErrors.password }}
                  </small>
                </div>

                <!-- Submit Button -->
                <Button
                  type="submit"
                  label="Sign In"
                  icon="pi pi-sign-in"
                  :loading="isLoading"
                  :disabled="isLoading || !isFormValid"
                  class="submit-button"
                />

                <!-- Error Display -->
                <div v-if="loginError" class="login-error">
                  <i class="pi pi-exclamation-triangle"></i>
                  <span>{{ loginError }}</span>
                </div>
              </form>

              <!-- OIDC Providers Section -->
              <OidcProvidersSection
                :providers="oidcProviders"
                :disabled="isLoading"
                @provider-selected="handleOidcLogin"
              />

              <!-- Register Link -->
              <div v-if="registrationStatus.passwordRegistrationEnabled || registrationStatus.oidcRegistrationEnabled" class="register-section">
                <span class="register-text">Don't have an account?</span>
                <router-link to="/register" class="register-link">
                  Create account
                </router-link>
              </div>
            </div>
          </template>
        </Card>

      </div>
    </div>

    <Toast />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { useAuthStore } from '@/stores/auth'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { formatError } from '@/utils/errorHandler'
import OidcProvidersSection from '@/components/auth/OidcProvidersSection.vue'

// Composables
const router = useRouter()
const route = useRoute()
const toast = useToast()
const authStore = useAuthStore()
const { handleError } = useErrorHandler()

// State
const isLoading = ref(false)
const loginError = ref('')

const oidcProviders = ref([])

const registrationStatus = ref({ passwordRegistrationEnabled: false, oidcRegistrationEnabled: false });

// Form data
const formData = ref({
  email: '',
  password: ''
})

const formErrors = ref({})

// Computed
const isFormValid = computed(() => {
  return formData.value.email && 
         formData.value.password && 
         Object.keys(formErrors.value).length === 0
})

// Methods
const validateForm = () => {
  formErrors.value = {}
  
  // Email validation
  if (!formData.value.email?.trim()) {
    formErrors.value.email = 'Email is required'
  } else if (!isValidEmail(formData.value.email)) {
    formErrors.value.email = 'Please enter a valid email address'
  }
  
  // Password validation
  if (!formData.value.password) {
    formErrors.value.password = 'Password is required'
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
  if (loginError.value) {
    loginError.value = ''
  }
}

const handleSubmit = async () => {
  if (!validateForm()) return
  
  isLoading.value = true
  loginError.value = ''
  
  try {
    await authStore.login(formData.value.email.trim(), formData.value.password)
    
    toast.add({
      severity: 'success',
      summary: 'Welcome Back!',
      detail: 'You have successfully signed in',
      life: 3000
    })

    // Navigate to default redirect URL or fallback to timeline
    const redirectUrl = authStore.defaultRedirectUrl || '/app/timeline'
    await router.push(redirectUrl)
    
  } catch (error) {
    console.error('Login error:', error)
    
    // Use the new error handling system to get user-friendly messages
    const formattedError = formatError(error)
    
    // Always show custom error message in the form
    loginError.value = getLoginErrorMessage(error, formattedError)
  } finally {
    isLoading.value = false
  }
}


const getLoginErrorMessage = (error, formattedError) => {
  // If the server provided a specific message, prioritize it
  if (error.response?.data?.message) {
    return error.response.data.message
  }
  
  // Login-specific error messages
  switch (error.response?.status) {
    case 401:
      return 'Invalid email or password. Please check your credentials and try again.'
    case 403:
      return 'Your account is locked or suspended. Please contact support.'
    case 429:
      return 'Too many login attempts. Please wait a few minutes before trying again.'
    case 422:
      return 'Please check your email and password format.'
    default:
      // Fall back to the formatted error message from our error handler
      return formattedError.message
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
      summary: 'Login Failed',
      detail: `Failed to initialize login with ${providerName}. Please try again.`,
      life: 5000
    });
    isLoading.value = false;
  }
};


// Lifecycle
onMounted(() => {
  if (route.query.reason === 'registration_disabled') {
    toast.add({
      severity: 'warn',
      summary: 'Registration Disabled',
      detail: 'New user registration is currently disabled. Please log in if you already have an account.',
      life: 7000
    });
  }

  // Clear any existing auth data
  if (authStore.isAuthenticated) {
    router.push('/app/timeline')
  }

  authStore.getRegistrationStatus().then(status => {
    registrationStatus.value = status;
  });

  // Load available OIDC providers
  authStore.getOidcProviders().then(providers => {
      oidcProviders.value = providers;
  }).catch(err => {
      console.error("Failed to load OIDC providers", err);
      toast.add({
          severity: 'error',
          summary: 'Could not load login options',
          detail: 'Failed to retrieve external login providers. You can still log in with email and password.',
          life: 5000
      });
  });
})
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.login-page::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, var(--gp-surface-white) 0%, var(--gp-surface-light) 100%);
  z-index: 0;
}

.login-page::after {
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
.login-layout {
  position: relative;
  padding: 1rem 1rem;
  width: 100%;
  max-width: 420px;
  z-index: 2;
}

.login-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
}

/* Logo Section */
.logo-section {
  display: flex;
  justify-content: center;
  align-items: center;
}

.app-logo {
  width: 200px;
  height: auto;
  filter: drop-shadow(0 4px 8px rgba(26, 86, 219, 0.2));
  transition: transform 0.3s ease;
  display: block;
}

.app-logo:hover {
  transform: scale(1.05);
}

/* Dark Mode Gradient Background */
.p-dark .login-page::before {
  background: linear-gradient(135deg, var(--gp-surface-dark) 0%, var(--gp-surface-darker) 100%);
}

.p-dark .login-page::after {
  background: radial-gradient(ellipse at center, rgba(59, 130, 246, 0.15) 0%, transparent 70%);
}



/* Login Card */
.login-card {
  width: 100%;
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  box-shadow: var(--gp-shadow-card);
}

.login-form-content {
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
.login-form {
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

/* Login Error */
.login-error {
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


/* Register Section */
.register-section {
  text-align: center;
  padding-top: 1rem;
  border-top: 1px solid var(--gp-border-light);
}

.register-text {
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
  margin-right: 0.5rem;
}

.register-link {
  color: var(--gp-primary);
  text-decoration: none;
  font-weight: 600;
  font-size: 0.9rem;
  transition: color 0.2s ease;
}

.register-link:hover {
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

:deep(.p-checkbox) {
  width: 1.125rem;
  height: 1.125rem;
}

:deep(.p-button) {
  border-radius: var(--gp-radius-medium);
  transition: all 0.3s ease;
}

/* Responsive Design */
@media (max-width: 768px) {
  .login-layout {
    padding: 1rem;
  }
  
  .brand-title {
    font-size: 1.75rem;
  }
  
  .login-card {
    max-width: 100%;
  }
}

@media (max-width: 480px) {
  .brand-section {
    margin-bottom: 0.5rem;
  }

  .brand-title {
    font-size: 1.5rem;
  }

  .brand-tagline {
    font-size: 0.9rem;
  }

  .login-form-content {
    padding: 0.5rem;
  }
}

</style>