<template>
  <div class="tab-panel">
    <BaseCard class="panel-card">
      <h3>{{ editingRuleId ? 'Edit Rule' : 'Create Rule' }}</h3>
      <div class="form-layout">
        <section :class="['form-section', 'form-section--area', { 'form-section--needs-area': !selectedAreaSummary }]">
          <div class="section-header">
            <h4>Basics</h4>
          </div>
          <div class="section-grid section-grid--basics">
            <div class="field field--name">
              <label>Name</label>
              <InputText
                v-model="ruleForm.name"
                placeholder="Home area"
                :class="{ 'p-invalid': !!ruleFormErrors.name }"
              />
              <small v-if="ruleFormErrors.name" class="error-text">{{ ruleFormErrors.name }}</small>
            </div>

            <div class="field field--subjects">
              <label>Subjects</label>
              <MultiSelect
                v-model="ruleForm.subjectUserIds"
                :options="subjectOptions"
                optionLabel="label"
                optionValue="value"
                filter
                display="chip"
                :maxSelectedLabels="3"
                placeholder="Select subjects"
                :class="['subjects-select', { 'p-invalid': !!ruleFormErrors.subjectUserIds }]"
              >
                <template #option="slotProps">
                  <div class="subject-option" :class="{ 'subject-option--unavailable': slotProps.option.unavailable }">
                    <span>{{ slotProps.option.label }}</span>
                    <small v-if="slotProps.option.unavailable" class="subject-option-warning">Unavailable</small>
                  </div>
                </template>
              </MultiSelect>
              <small v-if="ruleFormErrors.subjectUserIds" class="error-text">{{ ruleFormErrors.subjectUserIds }}</small>
            </div>

            <div class="field field--status">
              <label>Status</label>
              <Select
                v-model="ruleForm.status"
                :options="statusOptions"
                optionLabel="label"
                optionValue="value"
              />
            </div>
          </div>
        </section>

        <section class="form-section">
          <div class="section-header section-header--with-action">
            <h4>
              Area
              <span v-if="!selectedAreaSummary" class="required-inline-chip">Required</span>
            </h4>
            <Button
              :label="selectedAreaSummary ? 'Redraw Rectangle' : 'Draw Rectangle (Required)'"
              icon="pi pi-pencil"
              :severity="selectedAreaSummary ? 'secondary' : 'primary'"
              :outlined="!!selectedAreaSummary"
              :class="['draw-rectangle-button', { 'draw-rectangle-button--required': !selectedAreaSummary }]"
              @click="$emit('start-rectangle-draw')"
            />
          </div>
          <div class="field field--area-map">
            <div class="map-picker">
              <BaseMap
                mapId="geofence-rule-map"
                :center="mapCenter"
                :zoom="mapZoom"
                height="clamp(360px, 52vh, 560px)"
                width="100%"
                @map-ready="$emit('map-ready', $event)"
              />
            </div>
            <small v-if="selectedAreaSummary" class="muted-text">{{ selectedAreaSummary }}</small>
            <small v-if="ruleFormErrors.area" class="error-text">{{ ruleFormErrors.area }}</small>
          </div>
        </section>

        <section class="form-section">
          <div class="section-header">
            <h4>Behavior</h4>
          </div>
          <div class="rule-sentence">
            <p class="rule-sentence-intro">When a subject...</p>

            <div class="rule-sentence-row">
              <div class="rule-toggle-chip">
                <span class="rule-toggle-chip-label">
                  <i class="pi pi-sign-in rule-toggle-chip-icon" />
                  Enter
                </span>
                <InputSwitch v-model="ruleForm.monitorEnter" />
              </div>
              <span :class="['rule-sentence-text', { 'rule-sentence-text--inactive': !ruleForm.monitorEnter }]">
                enters the area, send:
              </span>
              <Select
                v-model="ruleForm.enterTemplateId"
                :options="enterTemplateOptions"
                optionLabel="label"
                optionValue="value"
                :placeholder="hasEnabledDefaultEnterTemplate ? `Default: ${enabledDefaultEnterTemplate?.name}` : 'Built-in message'"
                :class="['rule-sentence-template', { 'rule-sentence-template--inactive': !ruleForm.monitorEnter }]"
              />
              <i
                class="pi pi-info-circle rule-sentence-info"
                v-tooltip.bottom="'If no template is selected, default ENTER template is used; otherwise fallback is built-in in-app message.'"
              />
            </div>

            <div class="rule-sentence-row">
              <div class="rule-toggle-chip">
                <span class="rule-toggle-chip-label">
                  <i class="pi pi-sign-out rule-toggle-chip-icon" />
                  Leave
                </span>
                <InputSwitch v-model="ruleForm.monitorLeave" />
              </div>
              <span :class="['rule-sentence-text', { 'rule-sentence-text--inactive': !ruleForm.monitorLeave }]">
                leaves the area, send:
              </span>
              <Select
                v-model="ruleForm.leaveTemplateId"
                :options="leaveTemplateOptions"
                optionLabel="label"
                optionValue="value"
                :placeholder="hasEnabledDefaultLeaveTemplate ? `Default: ${enabledDefaultLeaveTemplate?.name}` : 'Built-in message'"
                :class="['rule-sentence-template', { 'rule-sentence-template--inactive': !ruleForm.monitorLeave }]"
              />
              <i
                class="pi pi-info-circle rule-sentence-info"
                v-tooltip.bottom="'If no template is selected, default LEAVE template is used; otherwise fallback is built-in in-app message.'"
              />
            </div>

            <div class="rule-sentence-cooldown">
              <span>Wait at least</span>
              <InputNumber v-model="ruleForm.cooldownSeconds" :min="0" class="rule-sentence-cooldown-input" />
              <span>seconds between notifications.</span>
              <i
                class="pi pi-info-circle help-icon"
                v-tooltip.bottom="'Prevents repeated Enter/Leave notifications for this rule during the cooldown window.'"
              />
            </div>
          </div>
          <div v-if="ruleFormErrors.monitoring" class="field field--monitoring-error">
            <small class="error-text">{{ ruleFormErrors.monitoring }}</small>
          </div>
        </section>
      </div>

      <div class="actions-row sticky-actions">
        <Button
          :label="editingRuleId ? 'Update Rule' : 'Create Rule'"
          icon="pi pi-save"
          @click="$emit('save-rule')"
          :loading="savingRule"
        />
        <Button
          v-if="editingRuleId"
          label="Cancel"
          severity="secondary"
          outlined
          @click="$emit('reset-rule-form')"
        />
      </div>
    </BaseCard>

    <BaseCard class="panel-card">
      <div class="table-header">
        <h3>Rules</h3>
        <Button icon="pi pi-refresh" label="Refresh" severity="secondary" outlined @click="$emit('load-rules')" />
      </div>
      <DataTable :value="rules" dataKey="id" responsiveLayout="scroll">
        <Column field="name" header="Name" />
        <Column header="Subjects">
          <template #body="slotProps">
            <div class="subjects-cell">
              <Tag
                v-for="subject in visibleSubjects(slotProps.data)"
                :key="subject.userId"
                :value="subject.displayName"
                severity="info"
              />
              <Tag
                v-if="remainingSubjectsCount(slotProps.data) > 0"
                :value="`+${remainingSubjectsCount(slotProps.data)} more`"
                severity="secondary"
              />
            </div>
          </template>
        </Column>
        <Column header="Events">
          <template #body="slotProps">
            <span>{{ eventSummary(slotProps.data) }}</span>
          </template>
        </Column>
        <Column field="cooldownSeconds" header="Cooldown" />
        <Column field="status" header="Status">
          <template #body="slotProps">
            <Tag :value="slotProps.data.status" :severity="slotProps.data.status === 'ACTIVE' ? 'success' : 'secondary'" />
          </template>
        </Column>
        <Column header="Actions">
          <template #body="slotProps">
            <div class="row-actions">
              <Button icon="pi pi-pencil" text @click="$emit('edit-rule', slotProps.data)" />
              <Button icon="pi pi-trash" text severity="danger" @click="$emit('delete-rule', slotProps.data)" />
            </div>
          </template>
        </Column>
      </DataTable>
    </BaseCard>
  </div>
</template>

<script setup>
import BaseCard from '@/components/ui/base/BaseCard.vue'
import BaseMap from '@/components/maps/BaseMap.vue'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Select from 'primevue/select'
import MultiSelect from 'primevue/multiselect'
import InputSwitch from 'primevue/inputswitch'
import Button from 'primevue/button'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'

defineProps({
  editingRuleId: {
    type: Number,
    default: null
  },
  ruleForm: {
    type: Object,
    required: true
  },
  ruleFormErrors: {
    type: Object,
    required: true
  },
  subjectOptions: {
    type: Array,
    required: true
  },
  mapCenter: {
    type: Array,
    required: true
  },
  mapZoom: {
    type: Number,
    required: true
  },
  selectedAreaSummary: {
    type: String,
    default: ''
  },
  statusOptions: {
    type: Array,
    required: true
  },
  enterTemplateOptions: {
    type: Array,
    required: true
  },
  leaveTemplateOptions: {
    type: Array,
    required: true
  },
  hasEnabledDefaultEnterTemplate: {
    type: Boolean,
    required: true
  },
  enabledDefaultEnterTemplate: {
    type: Object,
    default: null
  },
  hasEnabledDefaultLeaveTemplate: {
    type: Boolean,
    required: true
  },
  enabledDefaultLeaveTemplate: {
    type: Object,
    default: null
  },
  savingRule: {
    type: Boolean,
    required: true
  },
  rules: {
    type: Array,
    required: true
  },
  eventSummary: {
    type: Function,
    required: true
  }
})

defineEmits([
  'start-rectangle-draw',
  'map-ready',
  'save-rule',
  'reset-rule-form',
  'load-rules',
  'edit-rule',
  'delete-rule'
])

const MAX_INLINE_SUBJECTS = 2

function normalizedRuleSubjects(rule) {
  if (Array.isArray(rule?.subjects)) {
    return rule.subjects
  }
  return []
}

function visibleSubjects(rule) {
  return normalizedRuleSubjects(rule).slice(0, MAX_INLINE_SUBJECTS)
}

function remainingSubjectsCount(rule) {
  return Math.max(0, normalizedRuleSubjects(rule).length - MAX_INLINE_SUBJECTS)
}
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

.form-layout {
  display: grid;
  gap: 0.9rem;
}

.form-section {
  border: 1px solid var(--surface-border);
  border-radius: 10px;
  padding: 0.85rem;
  display: grid;
  gap: 0.75rem;
}

.form-section--needs-area {
  border-color: color-mix(in srgb, var(--primary-color) 48%, var(--surface-border));
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--primary-color) 22%, transparent);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.section-header h4 {
  margin: 0;
  font-size: 0.96rem;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
}

.required-inline-chip {
  border-radius: 999px;
  font-size: 0.68rem;
  line-height: 1;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  color: #fff;
  background: #ef4444;
  padding: 0.24rem 0.45rem;
}

.draw-rectangle-button {
  white-space: nowrap;
}

.draw-rectangle-button--required {
  font-weight: 700;
  box-shadow:
    0 0 0 1px color-mix(in srgb, var(--primary-color) 55%, transparent),
    0 8px 18px color-mix(in srgb, var(--primary-color) 28%, transparent);
}

.section-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 0.75rem;
}

.section-grid > .field {
  grid-column: span 12;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.map-picker {
  width: 100%;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  overflow: hidden;
}

.field label {
  font-weight: 600;
  font-size: 0.9rem;
}

.field-label-with-help {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}

.help-icon {
  color: var(--text-color-secondary);
  cursor: help;
  font-size: 0.85rem;
}

.muted-text {
  color: var(--text-color-secondary);
  font-size: 0.8rem;
}

.error-text {
  color: var(--red-500);
  font-size: 0.78rem;
}

.rule-sentence {
  display: grid;
  gap: 0.8rem;
}

.rule-sentence-intro {
  margin: 0;
  font-size: 0.96rem;
  font-weight: 600;
  color: var(--text-color);
}

.rule-sentence-row {
  display: grid;
  grid-template-columns: auto auto minmax(16rem, 30rem) auto;
  align-items: center;
  justify-content: start;
  column-gap: 0.65rem;
  row-gap: 0.35rem;
}

.rule-toggle-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
  border: 1px solid var(--surface-border);
  border-radius: 999px;
  padding: 0.32rem 0.55rem;
  background: color-mix(in srgb, var(--surface-card) 88%, var(--surface-ground));
}

.rule-toggle-chip-label {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.76rem;
  line-height: 1;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.rule-toggle-chip-icon {
  font-size: 0.72rem;
}

.rule-toggle-chip :deep(.p-inputswitch) {
  display: inline-flex;
  align-items: center;
  vertical-align: middle;
}

.rule-sentence-text {
  font-size: 0.9rem;
  color: var(--text-color);
  white-space: nowrap;
}

.rule-sentence-text--inactive {
  opacity: 0.5;
}

.rule-sentence-template {
  width: clamp(16rem, 36vw, 30rem) !important;
  max-width: 100%;
  justify-self: start;
}

.rule-sentence-template--inactive {
  opacity: 0.5;
}

.rule-sentence-info {
  color: var(--text-color-secondary);
  font-size: 0.84rem;
  cursor: help;
}

.rule-sentence-cooldown {
  display: grid;
  grid-template-columns: auto auto auto auto;
  align-items: center;
  justify-content: start;
  column-gap: 0.65rem;
  row-gap: 0.35rem;
  padding-top: 0.2rem;
}

.rule-sentence-cooldown-input {
  width: 6.25rem !important;
  min-width: 6.25rem;
}

.rule-sentence-cooldown-input :deep(.p-inputnumber-input) {
  width: 100%;
}

.field--monitoring-error {
  margin-top: -0.25rem;
}

.actions-row {
  margin-top: 1rem;
  display: flex;
  gap: 0.5rem;
  justify-content: flex-end;
}

.sticky-actions {
  background: var(--surface-card);
  border-top: 1px solid var(--surface-border);
  padding-top: 0.75rem;
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

.subject-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  width: 100%;
}

.subject-option--unavailable {
  color: var(--orange-700);
}

.subject-option-warning {
  color: var(--orange-600);
  font-size: 0.72rem;
  font-weight: 600;
}

.subjects-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}

.subjects-select :deep(.p-multiselect-label-container) {
  align-items: flex-start;
}

.subjects-select :deep(.p-multiselect-label) {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.3rem;
  padding-top: 0.4rem;
  padding-bottom: 0.4rem;
  white-space: normal;
}

.subjects-select :deep(.p-multiselect-token) {
  max-width: 100%;
}

.subjects-select :deep(.p-multiselect-token-label) {
  max-width: 11rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.subjects-select :deep(.p-multiselect-token-icon) {
  color: var(--text-color-secondary);
  border-radius: 999px;
}

.subjects-select :deep(.p-multiselect-token-icon:hover) {
  color: var(--text-color);
  background: color-mix(in srgb, var(--text-color-secondary) 20%, transparent);
}

.p-dark .subjects-select :deep(.p-multiselect-token-icon) {
  color: #cbd5e1;
  background: rgba(148, 163, 184, 0.22);
}

.p-dark .subjects-select :deep(.p-multiselect-token-icon:hover) {
  color: #e2e8f0;
  background: rgba(148, 163, 184, 0.38);
}

@media (min-width: 900px) {
  .section-grid--basics .field--name {
    grid-column: span 4;
  }

  .section-grid--basics .field--subjects {
    grid-column: span 5;
  }

  .section-grid--basics .field--status {
    grid-column: span 3;
  }
}

@media (max-width: 768px) {
  .tab-panel {
    padding: 0.5rem;
  }

  .panel-card {
    padding: 0.75rem;
  }

  .form-section {
    padding: 0.7rem;
  }

  .section-header {
    flex-direction: column;
    align-items: stretch;
  }

  .draw-rectangle-button {
    width: 100%;
  }

  .rule-sentence-row {
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .rule-sentence-text {
    white-space: normal;
  }

  .rule-sentence-template {
    min-width: 0;
    width: 100%;
  }

  .rule-sentence-cooldown {
    display: grid;
    grid-template-columns: 1fr;
    gap: 0.4rem;
  }

  .rule-sentence-cooldown-input {
    width: 100%;
  }

  .actions-row {
    flex-direction: column;
    justify-content: stretch;
  }

  .sticky-actions {
    position: sticky;
    bottom: max(0px, env(safe-area-inset-bottom));
    z-index: 5;
  }

  .actions-row :deep(.p-button) {
    width: 100%;
  }
}
</style>
