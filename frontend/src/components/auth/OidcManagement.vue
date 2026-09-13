<template>
  <ConfirmDialog />
  <section class="settings-group" aria-labelledby="connected-accounts-heading">
    <div class="settings-group-header">
      <h3 id="connected-accounts-heading">Connected accounts</h3>
      <p>Manage social or corporate OIDC sign-in methods.</p>
    </div>

    <div class="settings-panel">
      <div class="oidc-management">
        <!-- Linked Providers -->
        <div v-if="linkedProviders.length > 0" class="provider-section">
          <h4>Linked accounts</h4>
          <div class="provider-list">
            <div
              v-for="connection in linkedProviders"
              :key="connection.providerName"
              class="provider-item"
            >
              <div class="provider-info">
                <ProviderIcon
                  :provider="{ name: connection.providerName, icon: connection.providerIcon }"
                  size="large"
                  :alt="`${connection.providerDisplayName || connection.providerName} icon`"
                />
                <div class="provider-details">
                  <span class="provider-name">{{ connection.providerDisplayName || connection.providerName }}</span>
                  <small class="provider-email">{{ connection.displayName }}</small>
                </div>
              </div>
              <Button
                label="Unlink"
                severity="danger"
                outlined
                size="small"
                @click="confirmUnlinkProvider(connection.providerName)"
                :disabled="readOnly || !canUnlink(connection.providerName)"
                v-tooltip.bottom="readOnly ? 'Disabled in demo mode' : (!canUnlink(connection.providerName) ? 'Cannot unlink the only authentication method without a password set.' : 'Unlink this account')"
              />
            </div>
          </div>
        </div>
        
        <!-- Available Providers -->
        <div v-if="availableProviders.length > 0" class="provider-section">
          <h4>Link another account</h4>
          <div class="provider-list">
            <div
              v-for="provider in availableProviders"
              :key="provider.name"
              class="provider-item"
            >
              <div class="provider-info">
                <ProviderIcon
                  :provider="provider"
                  size="large"
                  :alt="`${provider.displayName} icon`"
                />
                <span class="provider-name">{{ provider.displayName }}</span>
              </div>
              <Button
                label="Link"
                @click="linkProvider(provider.name)"
                size="small"
                :disabled="readOnly"
              />
            </div>
          </div>
        </div>
        
        <Message v-if="linkedProviders.length > 0 && !hasPassword && linkedProviders.length === 1" severity="warn" :closable="false">
          You have no password set. You must add another login method before unlinking your only connected account.
        </Message>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useAuthStore } from '@/stores/auth';
import { useToast } from 'primevue/usetoast';
import { useConfirm } from "primevue/useconfirm";

import Button from 'primevue/button';
import Message from 'primevue/message';
import ProviderIcon from '@/components/common/ProviderIcon.vue';

const authStore = useAuthStore();
const toast = useToast();
const confirm = useConfirm();

const props = defineProps({
  readOnly: {
    type: Boolean,
    default: false
  }
})

const linkedProviders = ref([]);
const allProviders = ref([]);
const hasPassword = computed(() => authStore.user?.hasPassword);

const availableProviders = computed(() => {
  const linkedNames = linkedProviders.value.map(lp => lp.providerName);
  return allProviders.value.filter(p => !linkedNames.includes(p.name));
});

const canUnlink = (providerName) => {
    if (hasPassword.value) return true;
    return linkedProviders.value.length > 1;
};


const getProviderDisplayName = (providerName) => {
    const provider = allProviders.value.find(p => p.name === providerName);
    return provider ? provider.displayName : providerName;
}

const linkProvider = async (providerName) => {
  if (props.readOnly) return
  try {
    await authStore.linkOidcProvider(providerName);
  } catch (error) {
    console.error('Failed to link provider:', error);
    toast.add({
      severity: 'error',
      summary: 'Link Failed',
      detail: 'Failed to initiate linking for ' + providerName,
      life: 3000
    });
  }
};

const confirmUnlinkProvider = (providerName) => {
    if (props.readOnly) return
    confirm.require({
        message: `Are you sure you want to unlink your ${getProviderDisplayName(providerName)} account? This action cannot be undone.`,
        header: 'Confirm Unlink',
        icon: 'pi pi-exclamation-triangle',
        acceptClass: 'p-button-danger',
        accept: () => {
            unlinkProvider(providerName);
        }
    });
};

const unlinkProvider = async (providerName) => {
  if (props.readOnly) return
  try {
    await authStore.unlinkOidcProvider(providerName);
    await loadData();
    
    toast.add({
      severity: 'success',
      summary: 'Account Unlinked',
      detail: `Successfully unlinked your ${getProviderDisplayName(providerName)} account.`,
      life: 3000
    });
  } catch (error) {
    console.error('Failed to unlink provider:', error);
    toast.add({
      severity: 'error',
      summary: 'Unlink Failed',
      detail: error.response?.data?.message || 'Failed to unlink account',
      life: 5000
    });
  }
};

const loadData = async () => {
  try {
    const [all, linked] = await Promise.all([
        authStore.getOidcProviders(),
        authStore.getLinkedProviders()
    ]);
    allProviders.value = all;
    linkedProviders.value = linked;
  } catch (error) {
    console.error('Failed to load OIDC provider data:', error);
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Could not load connected account information.',
      life: 3000
    });
  }
};

onMounted(loadData);

</script>

<style scoped>
.oidc-management {
  display: flex;
  flex-direction: column;
}

.provider-section {
  padding: var(--gp-spacing-lg);
}

.provider-section + .provider-section,
.provider-section + :deep(.p-message) {
  border-top: 1px solid var(--gp-border-light);
}

.provider-section h4 {
  margin: 0 0 var(--gp-spacing-md);
  color: var(--gp-text-primary);
  font-size: 0.9rem;
  font-weight: 600;
}

.provider-list {
  display: flex;
  flex-direction: column;
}

.provider-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gp-spacing-lg);
  padding: var(--gp-spacing-md) 0;
  border-bottom: 1px solid var(--gp-border-light);
}

.provider-item:first-child {
  padding-top: 0;
}

.provider-item:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.provider-info {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-md);
  min-width: 0;
}

.provider-details {
  display: flex;
  flex-direction: column;
}

.provider-name {
  font-weight: 500;
  color: var(--text-color);
}

.provider-email {
  color: var(--text-color-secondary);
  font-size: 0.85rem;
}

:deep(.p-message) {
  margin: 0;
  border-radius: 0;
}

@media (max-width: 480px) {
  .provider-item {
    align-items: flex-start;
    flex-direction: column;
  }

  .provider-item :deep(.p-button) {
    width: 100%;
  }
}
</style>
