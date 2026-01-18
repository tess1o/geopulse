<template>
  <div v-if="providers.length > 0" class="oidc-section">
    <div class="divider" v-if="showDivider">
      <span>{{ dividerText }}</span>
    </div>
    <div class="oidc-providers">
      <Button
        v-for="provider in providers"
        :key="provider.name"
        :label="`Continue with ${provider.displayName}`"
        :class="['oidc-button', `oidc-${provider.name}`]"
        @click="$emit('provider-selected', provider.name)"
        :disabled="disabled"
        outlined
      >
        <template #icon>
          <ProviderIcon :provider="provider" size="medium" :alt="`${provider.displayName} icon`" />
        </template>
      </Button>
    </div>
  </div>
</template>

<script setup>
import ProviderIcon from '@/components/common/ProviderIcon.vue';

const props = defineProps({
  providers: {
    type: Array,
    default: () => []
  },
  disabled: {
    type: Boolean,
    default: false
  },
  showDivider: {
    type: Boolean,
    default: true
  },
  dividerText: {
    type: String,
    default: 'Or continue with'
  }
})

const emit = defineEmits(['provider-selected'])
</script>

<style scoped>
/* OIDC Providers Section */
.oidc-section {
  margin-top: 1.5rem;
  padding-top: 1.5rem;
}

.divider {
  display: flex;
  align-items: center;
  text-align: center;
  margin: 1.5rem 0;
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--gp-border-light);
}

.divider span {
  padding: 0 1rem;
  background: var(--gp-surface-white);
}

.oidc-providers {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.oidc-button {
  width: 100%;
  padding: 0.875rem 1rem;
  font-weight: 600;
  border-radius: var(--gp-radius-medium);
  transition: all 0.2s ease;
  border: 1px solid var(--gp-border-medium);
}

.oidc-button:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

/* Provider-specific styling */
.oidc-google:hover {
  background: rgba(219, 68, 55, 0.05) !important;
  border-color: rgba(219, 68, 55, 0.3) !important;
}

.oidc-microsoft:hover {
  background: rgba(0, 161, 241, 0.05) !important;
  border-color: rgba(0, 161, 241, 0.3) !important;
}

.oidc-okta:hover {
  background: rgba(0, 97, 179, 0.05) !important;
  border-color: rgba(0, 97, 179, 0.3) !important;
}

.oidc-authentik:hover {
  background: rgba(253, 93, 147, 0.05) !important;
  border-color: rgba(253, 93, 147, 0.3) !important;
}

.oidc-keycloak:hover {
  background: rgba(79, 172, 254, 0.05) !important;
  border-color: rgba(79, 172, 254, 0.3) !important;
}

.oidc-pocketid:hover {
  background: rgba(76, 175, 80, 0.05) !important;
  border-color: rgba(76, 175, 80, 0.3) !important;
}

/* Dark mode support */
.p-dark .divider::before, 
.p-dark .divider::after {
  border-color: var(--gp-border-dark);
}

.p-dark .divider span {
  background: var(--gp-surface-dark);
}

/* Button styling for dark mode */
:deep(.p-button) {
  border-radius: var(--gp-radius-medium);
  transition: all 0.2s ease;
}
</style>