<template>
  <div class="tab-panel">
    <BaseCard class="panel-card">
      <h3>{{ editingTemplateId ? 'Edit Template' : 'Create Template' }}</h3>

      <div class="editor-layout">
        <div class="editor-column editor-form">
          <section class="editor-section">
            <h4>Basics</h4>

            <div class="field">
              <label>Name</label>
              <InputText
                :ref="templateNameInput"
                v-model="templateNameModel"
                placeholder="Telegram Enter Alert"
                :class="{ 'p-invalid': !!templateFormErrors.name }"
              />
              <small v-if="templateFormErrors.name" class="error-text">{{ templateFormErrors.name }}</small>
            </div>

            <div class="field">
              <label>Title Template</label>
              <InputText
                :ref="templateTitleInput"
                v-model="templateTitleModel"
                placeholder="{{subjectName}} {{eventVerb}} {{geofenceName}}"
                :class="{ 'p-invalid': !!templateFormErrors.titleTemplate }"
                @focus="$emit('focus-template-field', 'titleTemplate')"
              />
              <small v-if="templateFormErrors.titleTemplate" class="error-text">{{ templateFormErrors.titleTemplate }}</small>
            </div>

            <div class="field">
              <label>Body Template</label>
              <Textarea
                :ref="templateBodyInput"
                v-model="templateBodyModel"
                rows="4"
                autoResize
                :class="{ 'p-invalid': !!templateFormErrors.bodyTemplate }"
                @focus="$emit('focus-template-field', 'bodyTemplate')"
              />
              <small v-if="templateFormErrors.bodyTemplate" class="error-text">{{ templateFormErrors.bodyTemplate }}</small>
            </div>
          </section>

          <section class="editor-section">
            <h4>Delivery</h4>

            <div class="external-toggle">
              <Checkbox
                inputId="template-send-in-app"
                v-model="templateSendInAppModel"
                :binary="true"
              />
              <label for="template-send-in-app" class="checkbox-label">
                Send in-app
              </label>
            </div>

            <div v-if="appriseEnabled" class="external-toggle">
              <Checkbox
                inputId="template-send-external"
                v-model="templateSendExternalModel"
                :binary="true"
                :disabled="!appriseConfigured"
              />
              <label for="template-send-external" class="checkbox-label">
                Send via Apprise
              </label>
            </div>
            <small v-if="appriseEnabled" class="muted-text">
              Telegram, Discord, email, and other Apprise-supported providers.
            </small>
            <small v-if="appriseEnabled && !appriseConfigured" class="muted-text">
              Apprise is enabled but not fully configured by admin yet.
            </small>

            <div v-if="appriseEnabled && templateSendExternalModel" class="field">
              <div class="field-inline-header">
                <label>Destination URL(s)</label>
                <Button
                  label="Test Connection"
                  icon="pi pi-send"
                  severity="secondary"
                  outlined
                  size="small"
                  :loading="testingTemplateConnection"
                  :disabled="!appriseConfigured || !templateDestinationModel?.trim()"
                  @click="$emit('test-template-connection')"
                />
              </div>
              <Textarea
                :ref="templateDestinationInput"
                v-model="templateDestinationModel"
                rows="3"
                autoResize
                placeholder="tgram://TOKEN/CHAT_ID&#10;discord://WEBHOOK_TOKEN"
                :class="{ 'p-invalid': !!templateFormErrors.destination }"
              />
              <small v-if="templateFormErrors.destination" class="error-text">{{ templateFormErrors.destination }}</small>
              <Message
                v-if="templateConnectionTestResult"
                :severity="templateConnectionTestResult.severity"
                :closable="false"
                class="test-result-message"
              >
                <span>
                  {{ templateConnectionTestResult.detail }}
                  <template v-if="templateConnectionTestResult.statusCode">
                    (HTTP {{ templateConnectionTestResult.statusCode }})
                  </template>
                </span>
              </Message>
            </div>
          </section>

          <section class="editor-section">
            <h4>Template Logic</h4>

            <div class="logic-grid">
              <div class="logic-item">
                <div class="logic-label">
                  <span>Default for Enter</span>
                  <small class="muted-text">Current: {{ currentDefaultEnterName }}</small>
                </div>
                <InputSwitch v-model="templateDefaultForEnterModel" />
              </div>

              <div class="logic-item">
                <div class="logic-label">
                  <span>Default for Leave</span>
                  <small class="muted-text">Current: {{ currentDefaultLeaveName }}</small>
                </div>
                <InputSwitch v-model="templateDefaultForLeaveModel" />
              </div>

              <div class="logic-item">
                <div class="logic-label">
                  <span>Enabled</span>
                  <small class="muted-text">Disabled templates are not used for delivery.</small>
                </div>
                <InputSwitch v-model="templateEnabledModel" />
              </div>
            </div>

            <small v-if="templateFormErrors.defaultForEnter" class="error-text">{{ templateFormErrors.defaultForEnter }}</small>
            <small v-if="templateFormErrors.defaultForLeave" class="error-text">{{ templateFormErrors.defaultForLeave }}</small>
            <small v-if="templateFormErrors.general" class="error-text">{{ templateFormErrors.general }}</small>
          </section>
        </div>

        <div class="editor-column editor-preview">
          <div class="sticky-stack">
            <section class="preview-panel">
              <div class="preview-header">
                <h4>Live Preview</h4>
                <small class="muted-text">Sample enter and leave notifications.</small>
              </div>

              <div class="preview-toast-list">
                <article
                  v-for="toast in templatePreviewToasts"
                  :key="toast.id"
                  :class="['preview-toast', 'p-toast-message', `p-toast-message-${toast.severity}`]"
                >
                  <div class="p-toast-message-content">
                    <i
                      :class="[
                        'p-toast-icon',
                        'pi',
                        toast.eventLabel === 'ENTER' ? 'pi-sign-in' : 'pi-sign-out'
                      ]"
                    />
                    <div class="preview-toast-copy">
                      <div class="p-toast-summary" :class="{ 'preview-empty': !toast.title }">
                        {{ toast.title || 'No title template' }}
                      </div>
                      <div class="p-toast-detail" :class="{ 'preview-empty': !toast.body }">
                        {{ toast.body || 'No body template' }}
                      </div>
                    </div>
                  </div>
                </article>
              </div>
            </section>

            <section class="macro-help">
              <div class="macro-help-header">
                <h4>Available Macros</h4>
                <small class="muted-text">Click to insert into focused title/body.</small>
              </div>
              <div class="macro-grid">
                <div v-for="macro in templateMacros" :key="macro.key" class="macro-item">
                  <button type="button" class="macro-chip" @click="$emit('insert-macro', macro.key)">
                    <code>{{ macro.key }}</code>
                  </button>
                  <span class="macro-description">{{ macro.description }}</span>
                  <i class="pi pi-info-circle macro-example-icon" v-tooltip.top="`Example: ${macro.example}`" />
                </div>
              </div>
            </section>
          </div>
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
            <span>{{ defaultSummary(slotProps.data) }}</span>
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
import { computed, toRefs } from 'vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import InputSwitch from 'primevue/inputswitch'
import Checkbox from 'primevue/checkbox'
import Button from 'primevue/button'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import Message from 'primevue/message'

const props = defineProps({
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
  templatePreviewToasts: {
    type: Array,
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
  appriseEnabled: {
    type: Boolean,
    required: true
  },
  appriseConfigured: {
    type: Boolean,
    required: true
  },
  testingTemplateConnection: {
    type: Boolean,
    required: true
  },
  templateConnectionTestResult: {
    type: Object,
    default: null
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

const emit = defineEmits([
  'update-template-field',
  'focus-template-field',
  'insert-macro',
  'test-template-connection',
  'save-template',
  'reset-template-form',
  'load-templates',
  'edit-template',
  'delete-template'
])

const {
  editingTemplateId,
  templateForm,
  templateFormErrors,
  templateNameInput,
  templateDestinationInput,
  templateTitleInput,
  templateBodyInput,
  templatePreviewToasts,
  templateMacros,
  currentDefaultEnterName,
  currentDefaultLeaveName,
  appriseEnabled,
  appriseConfigured,
  testingTemplateConnection,
  templateConnectionTestResult,
  savingTemplate,
  templates,
  formatDestination,
  defaultSummary
} = toRefs(props)

function createFieldModel(field) {
  return computed({
    get: () => templateForm.value?.[field],
    set: (value) => emit('update-template-field', { field, value })
  })
}

const templateNameModel = createFieldModel('name')
const templateDestinationModel = createFieldModel('destination')
const templateTitleModel = createFieldModel('titleTemplate')
const templateBodyModel = createFieldModel('bodyTemplate')
const templateSendInAppModel = createFieldModel('sendInApp')
const templateSendExternalModel = createFieldModel('sendExternal')
const templateDefaultForEnterModel = createFieldModel('defaultForEnter')
const templateDefaultForLeaveModel = createFieldModel('defaultForLeave')
const templateEnabledModel = createFieldModel('enabled')
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

.editor-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.85fr);
  gap: 1rem;
  align-items: start;
}

.editor-column {
  min-width: 0;
}

.editor-form {
  display: grid;
  gap: 1rem;
}

.editor-section {
  border: 1px solid var(--surface-border);
  border-radius: 12px;
  padding: 0.9rem;
  display: grid;
  gap: 0.75rem;
  background: var(--surface-50);
}

.editor-section h4 {
  margin: 0;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.field label {
  font-weight: 600;
  font-size: 0.9rem;
}

.field-inline-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
}

.external-toggle {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.checkbox-label {
  font-weight: 500;
}

.test-result-message {
  margin-top: 0.25rem;
}

.logic-grid {
  display: grid;
  gap: 0.6rem;
}

.logic-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.6rem;
  border: 1px solid var(--surface-border);
  border-radius: 10px;
  padding: 0.6rem 0.7rem;
  background: var(--surface-card);
}

.logic-label {
  display: grid;
  gap: 0.12rem;
}

.muted-text {
  color: var(--text-color-secondary);
  font-size: 0.8rem;
}

.error-text {
  color: var(--p-red-500, #ef4444);
  font-size: 0.78rem;
}

:deep(.p-inputtext.p-invalid),
:deep(.p-textarea.p-invalid) {
  border-color: var(--p-red-500, #ef4444) !important;
  box-shadow: 0 0 0 0.06rem color-mix(in srgb, var(--p-red-500, #ef4444) 35%, transparent) !important;
}

:deep(.p-dark .p-inputtext.p-invalid),
:deep(.p-dark .p-textarea.p-invalid) {
  background: color-mix(in srgb, var(--p-red-500, #ef4444) 10%, var(--surface-card)) !important;
}

.sticky-stack {
  position: sticky;
  top: 1rem;
  display: grid;
  gap: 0.8rem;
}

.preview-panel {
  border: 1px solid var(--surface-border);
  border-radius: 12px;
  padding: 0.9rem;
  background: var(--surface-100);
  display: grid;
  gap: 0.75rem;
}

.preview-header {
  display: grid;
  gap: 0.2rem;
}

.preview-header h4 {
  margin: 0;
}

.preview-toast-list {
  display: grid;
  gap: 0.5rem;
}

.preview-toast {
  margin-bottom: 0 !important;
}

.preview-toast-copy {
  min-width: 0;
  display: grid;
  gap: 0.15rem;
}

.preview-empty {
  font-style: italic;
  opacity: 0.75;
}

.macro-help {
  border: 1px solid var(--surface-border);
  border-radius: 12px;
  padding: 0.9rem;
  background: var(--surface-50);
}

.macro-help-header {
  display: grid;
  gap: 0.2rem;
  margin-bottom: 0.75rem;
}

.macro-help-header h4 {
  margin: 0;
}

.macro-grid {
  display: grid;
  gap: 0.5rem;
}

.macro-item {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 0.5rem;
  border: 1px solid var(--surface-border);
  border-radius: 10px;
  padding: 0.5rem 0.6rem;
  background: var(--surface-card);
}

.macro-chip {
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

.macro-example-icon {
  color: var(--text-color-secondary);
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

@media (max-width: 1200px) {
  .editor-layout {
    grid-template-columns: 1fr;
  }

  .sticky-stack {
    position: static;
  }
}

@media (max-width: 768px) {
  .tab-panel {
    padding: 0.5rem;
  }

  .panel-card {
    padding: 0.75rem;
  }

  .field-inline-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .macro-item {
    grid-template-columns: 1fr auto;
    grid-template-areas:
      "chip info"
      "description description";
    row-gap: 0.25rem;
  }

  .macro-item .macro-chip {
    grid-area: chip;
    justify-self: flex-start;
  }

  .macro-item .macro-description {
    grid-area: description;
  }

  .macro-item .macro-example-icon {
    grid-area: info;
  }
}
</style>
