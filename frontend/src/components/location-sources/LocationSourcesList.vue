<template>
  <div class="configured-sources">
    <h2 class="section-title">{{ t('locationSources.list.configuredSources') }}</h2>
    <div class="sources-grid">
      <Card v-for="source in sources" :key="source.id" class="source-card">
        <template #title>
          <div class="source-card-header">
            <div class="source-info">
              <i :class="getLocationSourceIcon(source.type)" class="source-icon"></i>
              <div>
                <div class="source-type">{{ getLocationSourceDisplayName(source.type) }}</div>
                <div class="source-identifier">{{ getLocationSourceIdentifier(source) }}</div>
              </div>
            </div>
            <div class="source-status-column">
              <Badge
                :value="source.active ? t('locationSources.list.active') : t('locationSources.list.inactive')"
                :severity="source.active ? 'success' : 'secondary'"
                class="status-badge"
              />
              <Badge
                v-if="source.type === 'OWNTRACKS'"
                :value="source.connectionType || 'HTTP'"
                :severity="source.connectionType === 'MQTT' ? 'info' : 'warn'"
                class="connection-badge"
              />
              <Badge
                v-if="source.type === 'OWNTRACKS' && source.hasPayloadEncryptionSecret"
                :value="t('locationSources.list.encrypted')"
                severity="success"
                class="connection-badge"
              />
            </div>
          </div>
        </template>
        <template #content>
          <div class="source-ingest-status" :data-testid="`source-ingest-status-${source.id}`">
            <div class="ingest-line">
              <span class="ingest-dot" :class="`ingest-dot--${statuses[source.id].severity}`"></span>
              <span v-if="statuses[source.id].lastReceivedAt">
                {{ t('locationSources.status.lastReceived', { time: timezone.timeAgo(statuses[source.id].lastReceivedAt) }) }}
              </span>
              <span v-else>{{ t('locationSources.status.neverReceived') }}</span>
            </div>
            <div v-if="statuses[source.id].newestPointAt" class="ingest-detail">
              {{ t('locationSources.status.newestPoint', { time: timezone.formatDateTimeDisplay(statuses[source.id].newestPointAt) }) }}
            </div>
            <div v-if="statuses[source.id].rejection" class="ingest-detail">
              {{ t('locationSources.status.lastSkipped', {
                reason: t(statuses[source.id].rejection.messageKey, statuses[source.id].rejection.params),
                time: timezone.timeAgo(statuses[source.id].rejection.at)
              }) }}
            </div>
            <div v-if="statuses[source.id].authProblem" class="ingest-detail ingest-detail--danger">
              <i class="pi pi-exclamation-triangle"></i>
              {{ t(statuses[source.id].authProblem.messageKey, { time: timezone.timeAgo(statuses[source.id].authProblem.at) }) }}
            </div>
          </div>

          <div class="source-actions">
            <div class="status-toggle">
              <ToggleSwitch
                v-model="source.active"
                @update:modelValue="(value) => emit('status-change', { id: source.id, status: value })"
              />
              <span class="toggle-label">{{ source.active ? t('locationSources.list.enabled') : t('locationSources.list.disabled') }}</span>
            </div>

            <div class="action-buttons">
              <Button
                icon="pi pi-eye"
                :label="t('locationSources.list.instructions')"
                size="small"
                outlined
                @click="emit('show-instructions', source)"
              />
              <Button
                icon="pi pi-pencil"
                :label="t('locationSources.list.edit')"
                size="small"
                outlined
                @click="emit('edit-source', source)"
              />
              <Button
                icon="pi pi-trash"
                severity="danger"
                size="small"
                outlined
                @click="emit('delete-source', source)"
              />
            </div>
          </div>
        </template>
      </Card>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useTimezone } from '@/composables/useTimezone'
import { buildLocationSourceStatus } from '@/components/location-sources/locationSourceStatus'
import {
  getLocationSourceDisplayName,
  getLocationSourceIcon,
  getLocationSourceIdentifier
} from '@/components/location-sources/locationSourceMeta'

const { t } = useI18n()
const timezone = useTimezone()

const props = defineProps({
  sources: {
    type: Array,
    default: () => []
  }
})

const statuses = computed(() => Object.fromEntries(
  props.sources.map(source => [source.id, buildLocationSourceStatus(source)])
))

const emit = defineEmits([
  'status-change',
  'show-instructions',
  'edit-source',
  'delete-source'
])
</script>

<style scoped>
.configured-sources {
  margin-bottom: 2rem;
}

.section-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 1rem 0;
}

.sources-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 1rem;
}

.source-card {
  height: fit-content;
}

.source-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}

.source-info {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
}

.source-icon {
  font-size: 1.5rem;
  color: var(--gp-primary);
  margin-top: 0.125rem;
}

.source-type {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0;
}

.source-identifier {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0.25rem 0 0 0;
  font-family: var(--gp-font-mono, monospace);
}

.source-status-column {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.5rem;
  flex-shrink: 0;
}

.status-badge {
  font-size: 0.75rem;
}

.connection-badge {
  font-size: 0.7rem;
  font-weight: 500;
}

.source-ingest-status {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin-bottom: 1rem;
  padding: 0.6rem 0.75rem;
  border-radius: 6px;
  background: var(--gp-surface-muted);
  font-size: 0.85rem;
  color: var(--gp-text-primary);
}

.ingest-line {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.ingest-dot {
  width: 0.55rem;
  height: 0.55rem;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--gp-text-muted);
}

.ingest-dot--success {
  background: var(--gp-success);
}

.ingest-dot--warn {
  background: var(--gp-warning);
}

.ingest-dot--danger {
  background: var(--gp-danger);
}

.ingest-detail {
  color: var(--gp-text-secondary);
  padding-left: 1.05rem;
  overflow-wrap: anywhere;
}

.ingest-detail--danger {
  color: var(--gp-danger-text);
}

.source-actions {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.status-toggle {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.toggle-label {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
}

.action-buttons {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .sources-grid {
    grid-template-columns: 1fr;
  }

  .action-buttons {
    flex-direction: column;
  }
}
</style>
