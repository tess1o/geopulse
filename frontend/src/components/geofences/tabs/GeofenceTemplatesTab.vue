<template>
  <div class="tab-panel">
    <BaseCard class="panel-card">
      <h3>{{ editingTemplateId ? 'Edit Template' : 'Create Template' }}</h3>
      <div class="form-grid">
        <div class="field">
          <label>Name</label>
          <InputText
            :ref="templateNameInput"
            v-model="templateForm.name"
            placeholder="Telegram Enter Alert"
            :class="{ 'p-invalid': !!templateFormErrors.name }"
          />
          <small v-if="templateFormErrors.name" class="error-text">{{ templateFormErrors.name }}</small>
        </div>
        <div class="field wide">
          <label>Destination URL(s) (optional)</label>
          <Textarea
            :ref="templateDestinationInput"
            v-model="templateForm.destination"
            rows="3"
            autoResize
            placeholder="tgram://TOKEN/CHAT_ID&#10;discord://WEBHOOK_TOKEN"
            :class="{ 'p-invalid': !!templateFormErrors.destination }"
          />
          <small class="muted-text">One destination URL per line. Leave empty for in-app only notifications.</small>
          <small v-if="templateFormErrors.destination" class="error-text">{{ templateFormErrors.destination }}</small>
        </div>
        <div class="field wide">
          <label>Title Template</label>
          <InputText
            :ref="templateTitleInput"
            v-model="templateForm.titleTemplate"
            placeholder="{{subjectName}} {{eventVerb}} {{geofenceName}}"
            :class="{ 'p-invalid': !!templateFormErrors.titleTemplate }"
            @focus="$emit('focus-template-field', 'titleTemplate')"
          />
          <small v-if="templateFormErrors.titleTemplate" class="error-text">{{ templateFormErrors.titleTemplate }}</small>
        </div>
        <div class="field wide">
          <label>Body Template</label>
          <Textarea
            :ref="templateBodyInput"
            v-model="templateForm.bodyTemplate"
            rows="4"
            autoResize
            :class="{ 'p-invalid': !!templateFormErrors.bodyTemplate }"
            @focus="$emit('focus-template-field', 'bodyTemplate')"
          />
          <small v-if="templateFormErrors.bodyTemplate" class="error-text">{{ templateFormErrors.bodyTemplate }}</small>
          <div class="template-preview">
            <div class="template-preview-title">Preview (sample event)</div>
            <small class="muted-text">Using sample values and your current date/time format.</small>
            <div class="preview-row">
              <span class="preview-label">Title</span>
              <span class="preview-value">{{ templatePreview.title || '-' }}</span>
            </div>
            <div class="preview-row">
              <span class="preview-label">Body</span>
              <span class="preview-value">{{ templatePreview.body || '-' }}</span>
            </div>
          </div>
        </div>
        <div class="field wide">
          <div class="macro-help">
            <div class="macro-help-header">
              <div class="macro-help-title">Available macros</div>
              <small class="muted-text">Click a macro to insert into the focused Title/Body field.</small>
            </div>
            <div class="macro-grid">
              <div v-for="macro in templateMacros" :key="macro.key" class="macro-item">
                <button
                  type="button"
                  class="macro-chip"
                  @click="$emit('insert-macro', macro.key)"
                >
                  <code>{{ macro.key }}</code>
                </button>
                <span class="macro-description">{{ macro.description }}</span>
                <small class="muted-text">Example: {{ macro.example }}</small>
              </div>
            </div>
          </div>
        </div>
        <div class="field toggle-field">
          <label>Default for Enter</label>
          <InputSwitch v-model="templateForm.defaultForEnter" />
          <small class="muted-text">Current default: {{ currentDefaultEnterName }}</small>
          <small v-if="templateFormErrors.defaultForEnter" class="error-text">{{ templateFormErrors.defaultForEnter }}</small>
        </div>
        <div class="field toggle-field">
          <label>Default for Leave</label>
          <InputSwitch v-model="templateForm.defaultForLeave" />
          <small class="muted-text">Current default: {{ currentDefaultLeaveName }}</small>
          <small v-if="templateFormErrors.defaultForLeave" class="error-text">{{ templateFormErrors.defaultForLeave }}</small>
        </div>
        <div class="field toggle-field">
          <label>Enabled</label>
          <InputSwitch v-model="templateForm.enabled" />
        </div>
        <div v-if="templateFormErrors.general" class="field wide">
          <small class="error-text">{{ templateFormErrors.general }}</small>
        </div>
      </div>

      <div class="actions-row">
        <Button
          :label="editingTemplateId ? 'Update Template' : 'Create Template'"
          icon="pi pi-save"
          @click="$emit('save-template')"
          :loading="savingTemplate"
        />
        <Button
          v-if="editingTemplateId"
          label="Cancel"
          severity="secondary"
          outlined
          @click="$emit('reset-template-form')"
        />
      </div>
    </BaseCard>

    <BaseCard class="panel-card">
      <div class="table-header">
        <h3>Templates</h3>
        <Button icon="pi pi-refresh" label="Refresh" severity="secondary" outlined @click="$emit('load-templates')" />
      </div>
      <DataTable :value="templates" dataKey="id" responsiveLayout="scroll">
        <Column field="name" header="Name" />
        <Column header="Destination">
          <template #body="slotProps">
            <span>{{ formatDestination(slotProps.data.destination) }}</span>
          </template>
        </Column>
        <Column header="Defaults">
          <template #body="slotProps">
            <span>
              {{ defaultSummary(slotProps.data) }}
            </span>
          </template>
        </Column>
        <Column field="enabled" header="Enabled">
          <template #body="slotProps">
            <Tag :value="slotProps.data.enabled ? 'Yes' : 'No'" :severity="slotProps.data.enabled ? 'success' : 'warning'" />
          </template>
        </Column>
        <Column header="Actions">
          <template #body="slotProps">
            <div class="row-actions">
              <Button icon="pi pi-pencil" text @click="$emit('edit-template', slotProps.data)" />
              <Button icon="pi pi-trash" text severity="danger" @click="$emit('delete-template', slotProps.data)" />
            </div>
          </template>
        </Column>
      </DataTable>
    </BaseCard>
  </div>
</template>

<script setup>
import BaseCard from '@/components/ui/base/BaseCard.vue'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import InputSwitch from 'primevue/inputswitch'
import Button from 'primevue/button'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'

defineProps({
  editingTemplateId: {
    type: Number,
    default: null
  },
  templateForm: {
    type: Object,
    required: true
  },
  templateFormErrors: {
    type: Object,
    required: true
  },
  templateNameInput: {
    type: [Object, Function],
    default: null
  },
  templateDestinationInput: {
    type: [Object, Function],
    default: null
  },
  templateTitleInput: {
    type: [Object, Function],
    default: null
  },
  templateBodyInput: {
    type: [Object, Function],
    default: null
  },
  templatePreview: {
    type: Object,
    required: true
  },
  templateMacros: {
    type: Array,
    required: true
  },
  currentDefaultEnterName: {
    type: String,
    required: true
  },
  currentDefaultLeaveName: {
    type: String,
    required: true
  },
  savingTemplate: {
    type: Boolean,
    required: true
  },
  templates: {
    type: Array,
    required: true
  },
  formatDestination: {
    type: Function,
    required: true
  },
  defaultSummary: {
    type: Function,
    required: true
  }
})

defineEmits([
  'focus-template-field',
  'insert-macro',
  'save-template',
  'reset-template-form',
  'load-templates',
  'edit-template',
  'delete-template'
])
</script>

<style scoped>
.tab-panel {
  display: grid;
  gap: 1rem;
  padding: 1rem;
}

.panel-card {
  padding: 1rem;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 0.75rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.field.wide {
  grid-column: 1 / -1;
}

.field label {
  font-weight: 600;
  font-size: 0.9rem;
}

.muted-text {
  color: var(--text-color-secondary);
  font-size: 0.8rem;
}

.error-text {
  color: var(--red-500);
  font-size: 0.78rem;
}

.macro-help {
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  padding: 1rem;
  background: var(--surface-50);
}

.macro-help-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.macro-help-title {
  font-weight: 600;
}

.macro-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 0.6rem 0.75rem;
  margin-top: 0.75rem;
}

.macro-item {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  padding: 0.55rem;
  border-radius: 8px;
  border: 1px solid var(--surface-border);
  background: var(--surface-card);
}

.macro-chip {
  align-self: flex-start;
  border: 1px solid var(--primary-300);
  background: var(--surface-50);
  color: var(--text-color);
  border-radius: 999px;
  padding: 0.22rem 0.6rem;
  cursor: pointer;
}

.macro-chip:hover {
  border-color: var(--primary-500);
}

.macro-description {
  font-size: 0.82rem;
}

.template-preview {
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  padding: 0.8rem;
  background: var(--surface-50);
  display: grid;
  gap: 0.5rem;
}

.template-preview-title {
  font-weight: 600;
}

.preview-row {
  display: grid;
  gap: 0.25rem;
}

.preview-label {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--text-color-secondary);
}

.preview-value {
  font-size: 0.9rem;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.toggle-field {
  align-items: flex-start;
}

.actions-row {
  margin-top: 1rem;
  display: flex;
  gap: 0.5rem;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.row-actions {
  display: flex;
  gap: 0.35rem;
}

@media (max-width: 768px) {
  .tab-panel {
    padding: 0.5rem;
  }

  .panel-card {
    padding: 0.75rem;
  }
}
</style>
