<template>
  <Dialog 
    v-model:visible="isVisible" 
    modal 
    :closable="false" 
    :style="{ width: '450px' }"
    header="Link Your Account"
  >
    <div class="account-linking-content">
      <!-- Header Information -->
      <div class="linking-info">
        <div class="provider-icons">
          <i class="pi pi-user-plus linking-icon"></i>
        </div>
        <h3 class="linking-title">Account Already Exists</h3>
        <p class="linking-description">
          An account with <strong>{{ linkingData.email }}</strong> already exists. 
          To continue, please verify your identity and we'll link your {{ linkingData.newProvider }} account.
        </p>
      </div>

      <!-- Verification Methods -->
      <div class="verification-methods">
        <!-- Password Verification (if available) -->
        <div v-if="linkingData.verificationMethods.password" class="verification-option">
          <div class="verification-header">
            <i class="pi pi-lock"></i>
            <span>Verify with Password</span>
          </div>
          <form @submit.prevent="linkWithPassword" class="password-form">
            <div class="form-field">
              <Password 
                v-model="passwordForm.password"
                placeholder="Enter your password"
                :feedback="false"
                toggleMask
                :invalid="!!passwordError"
                class="w-full"
                :disabled="isLinking"
              />
              <small v-if="passwordError" class="error-message">
                {{ passwordError }}
              </small>
            </div>
            <Button 
              type="submit"
              label="Link Account & Continue"
              :loading="isLinking"
              :disabled="!passwordForm.password || isLinking"
              class="w-full"
            />
          </form>
        </div>

        <!-- OIDC Verification (if available) -->
        <div v-if="linkingData.verificationMethods.oidcProviders.length > 0" class="verification-option">
          <div class="verification-header">
            <i class="pi pi-shield"></i>
            <span>Verify with Linked Account</span>
          </div>
          <div class="oidc-providers">
            <Button
              v-for="provider in linkingData.verificationMethods.oidcProviders"
              :key="provider"
              :label="`Continue with ${formatProviderName(provider)}`"
              @click="linkWithOidc(provider)"
              :loading="isLinking"
              :disabled="isLinking"
              outlined
              class="w-full mb-2"
            />
          </div>
        </div>

        <!-- Divider if both methods available -->
        <div v-if="linkingData.verificationMethods.password && linkingData.verificationMethods.oidcProviders.length > 0" 
             class="method-divider">
          <span>OR</span>
        </div>
      </div>

      <!-- Cancel Option -->
      <div class="linking-actions">
        <Button 
          label="Cancel"
          outlined
          @click="$emit('cancel')"
          :disabled="isLinking"
          class="w-full"
        />
      </div>
    </div>
  </Dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import apiService from '@/utils/apiService'

import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Password from 'primevue/password'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  linkingData: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['update:visible', 'success', 'cancel'])

const authStore = useAuthStore()

const isVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value)
})

const isLinking = ref(false)
const passwordForm = ref({
  password: ''
})
const passwordError = ref('')

const formatProviderName = (provider) => {
  return provider.charAt(0).toUpperCase() + provider.slice(1)
}

const linkWithPassword = async () => {
  passwordError.value = ''
  isLinking.value = true
  
  try {
    const response = await apiService.post('/auth/oidc/link-with-password', {
      email: props.linkingData.email,
      password: passwordForm.value.password,
      provider: props.linkingData.newProvider,
      linkingToken: props.linkingData.linkingToken
    })
    
    // Set user data from successful authentication
    authStore.setUser(response.data)
    emit('success', response.data)
    
  } catch (error) {
    console.error('Password linking failed:', error)
    passwordError.value = error.response?.data?.message || 'Password verification failed'
  } finally {
    isLinking.value = false
  }
}

const linkWithOidc = async (verificationProvider) => {
  isLinking.value = true
  
  try {
    const response = await apiService.post('/auth/oidc/link-with-oidc', {
      verificationProvider,
      newProvider: props.linkingData.newProvider,
      linkingToken: props.linkingData.linkingToken
    })
    
    // Redirect to OIDC provider for verification
    window.location.href = response.data.authorizationUrl
    
  } catch (error) {
    console.error('OIDC linking initiation failed:', error)
    // Handle error appropriately
    isLinking.value = false
  }
}
</script>

<style scoped>
.account-linking-content {
  padding: 1rem 0;
}

.linking-info {
  text-align: center;
  margin-bottom: 2rem;
}

.provider-icons {
  margin-bottom: 1rem;
}

.linking-icon {
  font-size: 3rem;
  color: var(--gp-primary);
}

.linking-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.5rem 0;
}

.linking-description {
  color: var(--gp-text-secondary);
  line-height: 1.5;
  margin: 0;
}

.verification-methods {
  margin-bottom: 1.5rem;
}

.verification-option {
  margin-bottom: 1.5rem;
  padding: 1.5rem;
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-light);
}

.verification-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.verification-header i {
  color: var(--gp-primary);
}

.form-field {
  margin-bottom: 1rem;
}

.error-message {
  color: var(--gp-danger);
  font-size: 0.85rem;
  margin-top: 0.25rem;
}

.oidc-providers {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.method-divider {
  display: flex;
  align-items: center;
  text-align: center;
  margin: 1rem 0;
}

.method-divider::before,
.method-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--gp-border-light);
}

.method-divider span {
  padding: 0 1rem;
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
}

.linking-actions {
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--gp-border-light);
}

/* Button styling */
:deep(.p-button) {
  border-radius: var(--gp-radius-medium);
  font-weight: 600;
  transition: all 0.2s ease;
}

:deep(.p-button:not(.p-button-outlined)) {
  background: var(--gp-primary);
  border-color: var(--gp-primary);
}

:deep(.p-button:not(.p-button-outlined):hover) {
  background: var(--gp-primary-hover);
  border-color: var(--gp-primary-hover);
  transform: translateY(-1px);
  box-shadow: var(--gp-shadow-medium);
}

:deep(.p-button-outlined) {
  border-color: var(--gp-border-medium);
  color: var(--gp-text-primary);
}

:deep(.p-button-outlined:hover) {
  background: var(--gp-surface-light);
  border-color: var(--gp-primary);
  color: var(--gp-primary);
}

/* Password input styling */
:deep(.p-password) {
  width: 100%;
}

:deep(.p-password-input) {
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-medium);
  padding: 0.75rem 1rem;
  width: 100%;
}

:deep(.p-password-input:focus) {
  border-color: var(--gp-primary);
  box-shadow: 0 0 0 3px rgba(26, 86, 219, 0.1);
}
</style>