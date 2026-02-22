<template>
  <div class="configured-sources">
    <h2 class="section-title">Configured Sources</h2>
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
                :value="source.active ? 'Active' : 'Inactive'"
                :severity="source.active ? 'success' : 'secondary'"
                class="status-badge"
              />
              <Badge
                v-if="source.type === 'OWNTRACKS'"
                :value="source.connectionType || 'HTTP'"
                :severity="source.connectionType === 'MQTT' ? 'info' : 'warn'"
                class="connection-badge"
              />
            </div>
          </div>
        </template>
        <template #content>
          <div class="source-actions">
            <div class="status-toggle">
              <ToggleSwitch
                v-model="source.active"
                @update:modelValue="(value) => emit('status-change', { id: source.id, status: value })"
              />
              <span class="toggle-label">{{ source.active ? 'Enabled' : 'Disabled' }}</span>
            </div>

            <div class="action-buttons">
              <Button
                icon="pi pi-eye"
                label="Instructions"
                size="small"
                outlined
                @click="emit('show-instructions', source)"
              />
              <Button
                icon="pi pi-pencil"
                label="Edit"
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
import {
  getLocationSourceDisplayName,
  getLocationSourceIcon,
  getLocationSourceIdentifier
} from '@/components/location-sources/locationSourceMeta'

defineProps({
  sources: {
    type: Array,
    default: () => []
  }
})

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
  font-family: var(--font-mono, monospace);
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

.p-dark .section-title {
  color: var(--gp-text-primary);
}

.p-dark .source-type {
  color: var(--gp-text-primary);
}

.p-dark .source-identifier {
  color: var(--gp-text-secondary);
}

.p-dark .toggle-label {
  color: var(--gp-text-secondary);
}

.p-dark .source-card {
  background: var(--gp-surface-dark) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .source-card :deep(.p-card) {
  background: var(--gp-surface-dark) !important;
  border-color: var(--gp-border-dark) !important;
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
