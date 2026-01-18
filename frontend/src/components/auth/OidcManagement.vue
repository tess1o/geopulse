<template>
  <ConfirmDialog />
  <Card>
    <template #title>Connected Accounts</template>
    <template #subtitle>Manage OIDC connections for social or corporate SSO login.</template>
    <template #content>
      <div class="oidc-management">
        <!-- Linked Providers -->
        <div v-if="linkedProviders.length > 0" class="linked-providers">
          <h4 class="font-semibold text-lg mb-3">Linked Accounts</h4>
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
                :disabled="!canUnlink(connection.providerName)"
                v-tooltip.bottom="!canUnlink(connection.providerName) ? 'Cannot unlink the only authentication method without a password set.' : 'Unlink this account'"
              />
            </div>
          </div>
        </div>
        
        <!-- Available Providers -->
        <div v-if="availableProviders.length > 0" class="available-providers mt-6">
          <h4 class="font-semibold text-lg mb-3">Link Additional Accounts</h4>
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
              />
            </div>
          </div>
        </div>
        
        <Message v-if="linkedProviders.length > 0 && !hasPassword && linkedProviders.length === 1" severity="warn" :closable="false">
          You have no password set. You must add another login method before unlinking your only connected account.
        </Message>
      </div>
    </template>
  </Card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useAuthStore } from '@/stores/auth';
import { useToast } from 'primevue/usetoast';
import { useConfirm } from "primevue/useconfirm";

import Card from 'primevue/card';
import Button from 'primevue/button';
import Message from 'primevue/message';
import ProviderIcon from '@/components/common/ProviderIcon.vue';

const authStore = useAuthStore();
const toast = useToast();
const confirm = useConfirm();

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
.provider-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.provider-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem;
  border: 1px solid var(--surface-border);
  border-radius: var(--border-radius);
  background: var(--surface-section);
}

.provider-info {
  display: flex;
  align-items: center;
  gap: 1rem;
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
</style>