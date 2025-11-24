<template>
  <div class="invitation-register-page">
    <div class="register-container">
      <div class="register-card">
        <div class="text-center mb-4">
          <h1 class="app-title">GeoPulse</h1>
          <p class="text-muted">Complete your registration</p>
        </div>

        <!-- Loading State -->
        <div v-if="validating" class="text-center p-5">
          <ProgressSpinner style="width: 50px; height: 50px" strokeWidth="4" />
          <p class="mt-3">Validating invitation...</p>
        </div>

        <!-- Invalid/Expired Invitation -->
        <div v-else-if="!invitationValid" class="text-center p-4">
          <i class="pi pi-exclamation-circle text-6xl text-red-500 mb-3"></i>
          <h3>Invalid Invitation</h3>
          <p class="text-muted">{{ validationMessage }}</p>
          <Button
            label="Go to Login"
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
            <label for="email">Email</label>
            <InputText
              id="email"
              v-model="form.email"
              type="email"
              placeholder="Enter your email"
              required
              autocomplete="email"
              :class="{ 'p-invalid': errors.email }"
            />
            <small v-if="errors.email" class="p-error">{{ errors.email }}</small>
          </div>

          <div class="field">
            <label for="fullName">Full Name</label>
            <InputText
              id="fullName"
              v-model="form.fullName"
              placeholder="Enter your full name"
              autocomplete="name"
            />
          </div>

          <div class="field">
            <label for="password">Password</label>
            <Password
              id="password"
              v-model="form.password"
              placeholder="Enter password"
              :feedback="true"
              toggleMask
              required
              autocomplete="new-password"
              :class="{ 'p-invalid': errors.password }"
            />
            <small v-if="errors.password" class="p-error">{{ errors.password }}</small>
          </div>

          <div class="field">
            <label for="confirmPassword">Confirm Password</label>
            <Password
              id="confirmPassword"
              v-model="form.confirmPassword"
              placeholder="Confirm password"
              :feedback="false"
              toggleMask
              required
              autocomplete="new-password"
              :class="{ 'p-invalid': errors.confirmPassword }"
            />
            <small v-if="errors.confirmPassword" class="p-error">{{ errors.confirmPassword }}</small>
          </div>

          <div class="field">
            <label for="timezone">Timezone</label>
            <Select
              id="timezone"
              v-model="form.timezone"
              :options="timezones"
              filter
              :placeholder="form.timezone"
              class="w-full"
            />
            <small class="text-muted">Detected: {{ form.timezone }}</small>
          </div>

          <Button
            type="submit"
            label="Create Account"
            icon="pi pi-user-plus"
            class="w-full mt-3"
            :loading="submitting"
          />

          <div class="text-center mt-3">
            <router-link to="/login" class="text-primary">
              Already have an account? Sign in
            </router-link>
          </div>
        </form>
      </div>
    </div>

    <Toast />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Select from 'primevue/select'
import Message from 'primevue/message'
import Toast from 'primevue/toast'
import ProgressSpinner from 'primevue/progressspinner'
import { useToast } from 'primevue/usetoast'
import { useAuthStore } from '@/stores/auth'
import apiService from '@/utils/apiService'

const router = useRouter()
const route = useRoute()
const toast = useToast()
const authStore = useAuthStore()

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
    const response = await apiService.get(`/auth/invitation/${token.value}/validate`)
    invitationValid.value = response.valid
    validationMessage.value = response.message || 'This invitation is not valid'
  } catch (error) {
    console.error('Failed to validate invitation:', error)
    invitationValid.value = false
    validationMessage.value = 'Failed to validate invitation'
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
    errors.value.email = 'Email is required'
    isValid = false
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.value.email)) {
    errors.value.email = 'Invalid email format'
    isValid = false
  }

  if (!form.value.password) {
    errors.value.password = 'Password is required'
    isValid = false
  } else if (form.value.password.length < 3) {
    errors.value.password = 'Password must be at least 3 characters'
    isValid = false
  }

  if (form.value.password !== form.value.confirmPassword) {
    errors.value.confirmPassword = 'Passwords do not match'
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
      token: token.value,
      email: form.value.email,
      password: form.value.password,
      fullName: form.value.fullName || form.value.email.split('@')[0],
      timezone: form.value.timezone
    }

    // Register user via invitation
    await apiService.post('/auth/invitation/register', payload)

    // Log in the user automatically after successful registration
    await authStore.login(form.value.email, form.value.password)

    toast.add({
      severity: 'success',
      summary: 'Welcome to GeoPulse!',
      detail: 'Your account has been created successfully',
      life: 3000
    })

    // Navigate to location sources for onboarding
    await router.push('/app/location-sources')

  } catch (error) {
    console.error('Registration failed:', error)
    errorMessage.value = error.response?.data?.error || 'Registration failed. Please try again.'
  } finally {
    submitting.value = false
  }
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
  background: linear-gradient(135deg, var(--gp-surface-white) 0%, var(--gp-surface-light) 100%);
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
.p-dark .invitation-register-page::before {
  background: linear-gradient(135deg, var(--gp-surface-dark) 0%, var(--gp-surface-darker) 100%);
}

.p-dark .invitation-register-page::after {
  background: radial-gradient(ellipse at center, rgba(59, 130, 246, 0.15) 0%, transparent 70%);
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
  background: var(--gp-surface-white);
  border-radius: 12px;
  padding: 2.5rem;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
}

.p-dark .register-card {
  background: var(--gp-surface-dark);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
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
  color: var(--text-color-secondary);
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
  color: var(--text-color);
}

.p-error {
  color: var(--red-500);
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
