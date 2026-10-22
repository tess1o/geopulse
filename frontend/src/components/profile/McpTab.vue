<template>
  <div class="integration-settings settings-tab mcp-settings">
    <section class="settings-group" aria-labelledby="mcp-connection-heading">
      <div class="settings-group-header">
        <h3 id="mcp-connection-heading">{{ t('profile.connectedApps.mcp.connection.heading') }}</h3>
        <p>{{ t('profile.connectedApps.mcp.connection.description') }}</p>
      </div>
      <div class="settings-panel">
        <SettingCard :title="t('profile.connectedApps.mcp.connection.serverUrl.title')" :description="t('profile.connectedApps.mcp.connection.serverUrl.description')" setting-id="mcpServerUrl">
          <template #control>
            <div class="copy-field">
              <code>{{ serverUrl }}</code>
              <Button icon="pi pi-copy" size="small" outlined :aria-label="t('profile.connectedApps.mcp.copy')" @click="copy(serverUrl)" />
            </div>
          </template>
        </SettingCard>

        <SettingCard :title="t('profile.connectedApps.mcp.connection.token.title')" :description="t('profile.connectedApps.mcp.connection.token.description')" setting-id="mcpApiToken">
          <template #control>
            <div class="token-control">
              <template v-if="createdToken">
                <div class="copy-field">
                  <code>{{ createdToken }}</code>
                  <Button icon="pi pi-copy" size="small" outlined :aria-label="t('profile.connectedApps.mcp.copy')" @click="copy(createdToken)" />
                </div>
                <small class="token-warning"><i class="pi pi-exclamation-triangle" aria-hidden="true"></i> {{ t('profile.connectedApps.mcp.connection.token.shownOnce') }}</small>
              </template>
              <Button
                v-else
                :label="t('profile.connectedApps.mcp.connection.token.create')"
                icon="pi pi-key"
                size="small"
                :loading="creating"
                :disabled="readOnly"
                @click="createToken"
              />
              <Button :label="t('profile.connectedApps.mcp.connection.token.manage')" icon="pi pi-arrow-right" iconPos="right" size="small" text @click="openTokenManagement" />
            </div>
          </template>
        </SettingCard>
      </div>
    </section>

    <section class="settings-group" aria-labelledby="mcp-client-heading">
      <div class="settings-group-header">
        <h3 id="mcp-client-heading">{{ t('profile.connectedApps.mcp.client.heading') }}</h3>
        <p>{{ t('profile.connectedApps.mcp.client.description') }}</p>
      </div>
      <div class="settings-panel">
        <div class="panel-body">
          <SelectButton
            v-model="client"
            :options="clientOptions"
            optionLabel="label"
            optionValue="value"
            :allowEmpty="false"
            :aria-label="t('profile.connectedApps.mcp.client.heading')"
            class="client-select"
          />
          <div class="copy-field snippet">
            <pre>{{ snippet }}</pre>
            <Button icon="pi pi-copy" size="small" outlined :aria-label="t('profile.connectedApps.mcp.copy')" @click="copy(snippet)" />
          </div>
          <ul class="panel-notes">
            <li>
              <i class="pi pi-info-circle" aria-hidden="true"></i>
              <i18n-t v-if="client === 'claudeDesktop'" keypath="profile.connectedApps.mcp.client.hints.claudeDesktop" tag="span">
                <template #file><code>claude_desktop_config.json</code></template>
              </i18n-t>
              <span v-else-if="client === 'claudeCode'">{{ t('profile.connectedApps.mcp.client.hints.claudeCode') }}</span>
              <span v-else>{{ t('profile.connectedApps.mcp.client.hints.other') }}</span>
            </li>
            <li v-if="!createdToken">
              <i class="pi pi-key" aria-hidden="true"></i>
              <i18n-t keypath="profile.connectedApps.mcp.client.placeholderHint" tag="span">
                <template #placeholder><code>{{ TOKEN_PLACEHOLDER }}</code></template>
              </i18n-t>
            </li>
          </ul>
          <a v-if="client === 'other'" :href="`${DOCS_URL}#chatgpt-through-an-openai-tunnel`" target="_blank" rel="noopener noreferrer" class="docs-link">
            {{ t('profile.connectedApps.mcp.client.hints.chatGptLink') }} <i class="pi pi-external-link" aria-hidden="true" />
          </a>
        </div>
      </div>
    </section>

    <section class="settings-group" aria-labelledby="mcp-tools-heading">
      <div class="settings-group-header">
        <h3 id="mcp-tools-heading">{{ t('profile.connectedApps.mcp.tools.heading') }}</h3>
        <p>{{ t('profile.connectedApps.mcp.tools.description') }}</p>
      </div>
      <div class="settings-panel">
        <div class="panel-body">
          <ul class="tool-areas">
            <li v-for="area in TOOL_AREAS" :key="area.key" class="tool-area">
              <i :class="area.icon" aria-hidden="true"></i>
              <div>
                <strong>{{ t(`profile.connectedApps.mcp.tools.areas.${area.key}.title`) }}</strong>
                <small>{{ t(`profile.connectedApps.mcp.tools.areas.${area.key}.example`) }}</small>
              </div>
            </li>
          </ul>
        </div>
        <div class="panel-footer">
          <ul class="panel-notes">
            <li>
              <i class="pi pi-info-circle" aria-hidden="true"></i>
              <span>{{ t('profile.connectedApps.mcp.tools.requirements') }}</span>
            </li>
          </ul>
          <a :href="DOCS_URL" target="_blank" rel="noopener noreferrer" class="docs-link">{{ t('profile.connectedApps.mcp.tools.docsLink') }} <i class="pi pi-external-link" aria-hidden="true" /></a>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import SelectButton from 'primevue/selectbutton'
import { useToast } from 'primevue/usetoast'
import SettingCard from '@/components/ui/forms/SettingCard.vue'
import { useAuthStore } from '@/stores/auth'
import { copyToClipboard } from '@/utils/clipboardUtils'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'
import { buildMcpClientSnippet, MCP_TOKEN_PLACEHOLDER as TOKEN_PLACEHOLDER } from './mcpClientSnippets'

const DOCS_URL = 'https://geopulse.cc/docs/api/mcp'
// Mirrors the tool groups in docs-website/docs/api/mcp.md; tool names live there, not here
const TOOL_AREAS = [
  { key: 'timeline', icon: 'pi pi-calendar' },
  { key: 'now', icon: 'pi pi-map-marker' },
  { key: 'places', icon: 'pi pi-building' },
  { key: 'summaries', icon: 'pi pi-chart-bar' },
  { key: 'trips', icon: 'pi pi-briefcase' },
  { key: 'notesPhotosWeather', icon: 'pi pi-images' },
  { key: 'geofences', icon: 'pi pi-bullseye' },
  { key: 'friends', icon: 'pi pi-users' }
]

defineProps({
  readOnly: { type: Boolean, default: false }
})

const { t } = useI18n()
const toast = useToast()
const router = useRouter()
const authStore = useAuthStore()

// nginx serves /mcp on the same origin as the UI
const serverUrl = `${typeof window !== 'undefined' ? window.location.origin : ''}/mcp`
const client = ref('claudeDesktop')
const createdToken = ref('')
const creating = ref(false)

const clientOptions = computed(() => [
  { label: t('profile.connectedApps.mcp.client.options.claudeDesktop'), value: 'claudeDesktop' },
  { label: t('profile.connectedApps.mcp.client.options.claudeCode'), value: 'claudeCode' },
  { label: t('profile.connectedApps.mcp.client.options.other'), value: 'other' }
])
const snippet = computed(() => buildMcpClientSnippet(client.value, serverUrl, createdToken.value))

const copy = async (text) => {
  const copied = await copyToClipboard(text)
  toast.add({
    severity: copied ? 'success' : 'warn',
    summary: copied ? t('profile.connectedApps.mcp.toasts.copied') : t('profile.connectedApps.mcp.toasts.copyFailed'),
    life: 2000
  })
}

// MCP only needs `read`; one token per client keeps each one revocable on its own
const createToken = async () => {
  creating.value = true
  try {
    const clientLabel = clientOptions.value.find(option => option.value === client.value)?.label
    const response = await authStore.saveApiToken(null, {
      name: t('profile.connectedApps.mcp.connection.token.name', { client: clientLabel }),
      expiresAt: null,
      scopes: ['read']
    })
    createdToken.value = response?.token || ''
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('profile.connectedApps.mcp.toasts.createFailed'),
      detail: formatApiErrorDetail(error, t('profile.access.apiTokens.toasts.saveFailed')),
      life: 3500
    })
  } finally {
    creating.value = false
  }
}

const openTokenManagement = () => {
  router.push({ query: { tab: 'security', setting: 'api-tokens-heading' } })
}
</script>

<style scoped>
.copy-field {
  display: flex;
  align-items: flex-start;
  gap: var(--gp-spacing-sm);
  min-width: 0;
  padding: var(--gp-spacing-sm) var(--gp-spacing-md);
  background: var(--gp-surface-ground);
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-small);
}

.copy-field code,
.copy-field pre {
  flex: 1;
  min-width: 0;
  margin: 0;
  font-family: var(--gp-font-mono, monospace);
  font-size: 0.85rem;
  color: var(--gp-text-primary);
  overflow-wrap: anywhere;
}

.copy-field pre {
  white-space: pre-wrap;
  line-height: 1.45;
}

.token-control {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--gp-spacing-xs);
  min-width: 0;
}

.token-control .copy-field {
  align-self: stretch;
}

.token-warning {
  color: var(--gp-warning-text);
}

.panel-body {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
  padding: var(--gp-spacing-md) var(--gp-spacing-lg);
}

.panel-footer {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--gp-spacing-sm);
  padding: var(--gp-spacing-md) var(--gp-spacing-lg);
  border-top: 1px solid var(--gp-border);
}

.client-select {
  align-self: flex-start;
  max-width: 100%;
  overflow-x: auto;
}

.panel-notes {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-xs);
  margin: 0;
  padding: 0;
  list-style: none;
}

.panel-notes li {
  display: flex;
  align-items: flex-start;
  gap: var(--gp-spacing-sm);
  color: var(--gp-text-secondary);
  font-size: 0.85rem;
  line-height: 1.5;
}

.panel-notes li > i {
  flex: 0 0 auto;
  margin-top: 0.2rem;
  font-size: 0.85rem;
  color: var(--gp-text-muted);
}

.panel-notes code {
  padding: 0.05rem 0.3rem;
  border-radius: var(--gp-radius-small);
  background: var(--gp-surface-muted);
  font-family: var(--gp-font-mono, monospace);
  font-size: 0.8rem;
  color: var(--gp-text-primary);
}

.tool-areas {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--gp-spacing-sm);
  margin: 0;
  padding: 0;
  list-style: none;
}

.tool-area {
  display: flex;
  align-items: flex-start;
  gap: var(--gp-spacing-sm);
  padding: var(--gp-spacing-sm) var(--gp-spacing-md);
  background: var(--gp-surface-card);
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-medium);
}

.tool-area > i {
  margin-top: 0.2rem;
  color: var(--gp-primary);
}

.tool-area div {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.tool-area small {
  color: var(--gp-text-secondary);
}

.docs-link {
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  gap: var(--gp-spacing-xs);
  color: var(--gp-primary);
  font-size: 0.875rem;
  font-weight: 600;
  text-decoration: none;
}

.docs-link i {
  font-size: 0.75rem;
}

.docs-link:hover {
  text-decoration: underline;
}

@media (max-width: 768px) {
  .panel-body,
  .panel-footer {
    padding: var(--gp-spacing-md);
  }

  .tool-areas {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
