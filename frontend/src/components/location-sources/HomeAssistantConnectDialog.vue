<template>
  <Dialog
    :visible="visible"
    :header="t('locationSources.connectHomeAssistant.header')"
    :modal="true"
    :closable="!tokenSecret"
    :style="{ width: '600px' }"
    @update:visible="emit('update:visible', $event)"
  >
    <div class="connect-content">
      <Message severity="success" :closable="false">{{ t('locationSources.connectHomeAssistant.created') }}</Message>

      <ol class="connect-steps">
        <li>
          {{ t('locationSources.connectHomeAssistant.stepInstall') }}
          <a :href="integrationUrl" target="_blank" rel="noopener noreferrer">{{ t('locationSources.connectHomeAssistant.installLink') }}</a>
        </li>
        <li>
          {{ t('locationSources.connectHomeAssistant.stepOpen') }}
          <a :href="addIntegrationUrl" target="_blank" rel="noopener noreferrer">{{ t('locationSources.connectHomeAssistant.addIntegrationLink') }}</a>
        </li>
        <li>
          {{ t('locationSources.connectHomeAssistant.stepEnter') }}
          <div class="connect-field">
            <span class="connect-label">{{ t('locationSources.connectHomeAssistant.urlLabel') }}</span>
            <code class="connect-value">{{ serverUrl }}</code>
            <Button icon="pi pi-copy" size="small" outlined :aria-label="t('locationSources.connectHomeAssistant.copy')" @click="emit('copy-text', serverUrl)" />
          </div>
          <div v-if="tokenSecret" class="connect-field">
            <span class="connect-label">{{ t('locationSources.connectHomeAssistant.tokenLabel') }}</span>
            <code class="connect-value" data-testid="ha-connect-token">{{ tokenSecret }}</code>
            <Button icon="pi pi-copy" size="small" outlined :aria-label="t('locationSources.connectHomeAssistant.copy')" @click="emit('copy-text', tokenSecret)" />
          </div>
        </li>
      </ol>

      <small v-if="tokenSecret" class="connect-warning">{{ t('locationSources.connectHomeAssistant.tokenShownOnce') }}</small>
      <small v-else class="connect-hint">{{ t('locationSources.connectHomeAssistant.existingToken', { name: tokenName || '' }) }}</small>
      <small class="connect-hint">{{ t('locationSources.connectHomeAssistant.stepWait') }}</small>
      <small class="connect-hint">
        {{ t('locationSources.connectHomeAssistant.otherClients') }}
        <a :href="apiDocsUrl" target="_blank" rel="noopener noreferrer">{{ t('locationSources.connectHomeAssistant.apiReference') }}</a>
      </small>
    </div>

    <template #footer>
      <Button
        :label="tokenSecret ? t('locationSources.connectHomeAssistant.savedToken') : t('locationSources.connectHomeAssistant.done')"
        @click="emit('update:visible', false)"
      />
    </template>
  </Dialog>
</template>

<script setup>
import { useI18n } from 'vue-i18n'
import {
  HOME_ASSISTANT_ADD_INTEGRATION_URL,
  HOME_ASSISTANT_API_DOCS_URL,
  HOME_ASSISTANT_INTEGRATION_URL
} from '@/constants/homeAssistantIntegration'

const { t } = useI18n()

defineProps({
  visible: { type: Boolean, default: false },
  // Base URL of this GeoPulse server (what the integration asks for)
  serverUrl: { type: String, default: '' },
  // Secret of a token created together with the source; null when an existing token was connected
  tokenSecret: { type: String, default: null },
  tokenName: { type: String, default: '' }
})

const emit = defineEmits(['update:visible', 'copy-text'])

const integrationUrl = HOME_ASSISTANT_INTEGRATION_URL
const addIntegrationUrl = HOME_ASSISTANT_ADD_INTEGRATION_URL
const apiDocsUrl = HOME_ASSISTANT_API_DOCS_URL
</script>

<style scoped>
.connect-content {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.connect-content a {
  color: var(--gp-primary);
  font-weight: 600;
  text-decoration: underline;
  text-underline-offset: .18em;
}

.connect-steps {
  margin: 0;
  padding-left: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.connect-field {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.5rem;
}

.connect-label {
  min-width: 4.5rem;
  font-weight: 600;
}

.connect-value {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
  padding: 0.35rem 0.5rem;
  border-radius: 6px;
  background: var(--gp-surface-muted);
  font-family: var(--gp-font-mono, monospace);
  font-size: 0.85rem;
}

.connect-warning {
  display: block;
  color: var(--gp-warning-text);
}

.connect-hint {
  display: block;
  color: var(--gp-text-secondary);
}
</style>
