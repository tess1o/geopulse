<template>
  <div class="tab-panel">
    <BaseCard class="panel-card">
      <h3>{{ editingRuleId ? 'Edit Rule' : 'Create Rule' }}</h3>
      <div class="form-grid">
        <div class="rules-top-row wide">
          <div class="field">
            <label>Name</label>
            <InputText
              v-model="ruleForm.name"
              placeholder="Home area"
              :class="{ 'p-invalid': !!ruleFormErrors.name }"
            />
            <small v-if="ruleFormErrors.name" class="error-text">{{ ruleFormErrors.name }}</small>
          </div>

          <div class="field">
            <label>Subject</label>
            <Select
              v-model="ruleForm.subjectUserId"
              :options="subjectOptions"
              optionLabel="label"
              optionValue="value"
              placeholder="Select subject"
              :class="{ 'p-invalid': !!ruleFormErrors.subjectUserId }"
            />
            <small v-if="ruleFormErrors.subjectUserId" class="error-text">{{ ruleFormErrors.subjectUserId }}</small>
          </div>

          <div class="field area-button-field">
            <label>Area Picker</label>
            <Button
              label="Draw Rectangle on Map"
              icon="pi pi-pencil"
              severity="secondary"
              outlined
              @click="$emit('start-rectangle-draw')"
            />
          </div>
        </div>

        <div class="field wide">
          <label>Area Map</label>
          <div class="map-picker">
            <BaseMap
              mapId="geofence-rule-map"
              :center="mapCenter"
              :zoom="mapZoom"
              height="260px"
              width="100%"
              @map-ready="$emit('map-ready', $event)"
            />
          </div>
          <small v-if="selectedAreaSummary" class="muted-text">{{ selectedAreaSummary }}</small>
          <small v-if="ruleFormErrors.area" class="error-text">{{ ruleFormErrors.area }}</small>
        </div>

        <div class="field toggle-field">
          <label>Monitor Enter</label>
          <InputSwitch v-model="ruleForm.monitorEnter" />
        </div>

        <div class="field toggle-field">
          <label>Monitor Leave</label>
          <InputSwitch v-model="ruleForm.monitorLeave" />
        </div>
        <div v-if="ruleFormErrors.monitoring" class="field wide">
          <small class="error-text">{{ ruleFormErrors.monitoring }}</small>
        </div>

        <div class="field">
          <label class="field-label-with-help">
            <span>Cooldown (seconds)</span>
            <i
              class="pi pi-info-circle help-icon"
              v-tooltip.bottom="'Prevents repeated Enter/Leave notifications for this rule during the cooldown window.'"
            />
          </label>
          <InputNumber v-model="ruleForm.cooldownSeconds" :min="0" />
          <small class="muted-text">Minimum delay between notifications for this rule.</small>
        </div>

        <div class="field">
          <label>Enter Template</label>
          <Select
            v-model="ruleForm.enterTemplateId"
            :options="enterTemplateOptions"
            optionLabel="label"
            optionValue="value"
            :placeholder="hasEnabledDefaultEnterTemplate ? 'Use default enter template' : 'Select enter template'"
          />
          <small v-if="hasEnabledDefaultEnterTemplate" class="muted-text">
            If empty, your default ENTER template ({{ enabledDefaultEnterTemplate?.name }}) is used.
          </small>
          <small v-else class="muted-text">
            No default ENTER template configured. Empty value uses built-in in-app message.
          </small>
        </div>

        <div class="field">
          <label>Leave Template</label>
          <Select
            v-model="ruleForm.leaveTemplateId"
            :options="leaveTemplateOptions"
            optionLabel="label"
            optionValue="value"
            :placeholder="hasEnabledDefaultLeaveTemplate ? 'Use default leave template' : 'Select leave template'"
          />
          <small v-if="hasEnabledDefaultLeaveTemplate" class="muted-text">
            If empty, your default LEAVE template ({{ enabledDefaultLeaveTemplate?.name }}) is used.
          </small>
          <small v-else class="muted-text">
            No default LEAVE template configured. Empty value uses built-in in-app message.
          </small>
        </div>

        <div class="field">
          <label>Status</label>
          <Select
            v-model="ruleForm.status"
            :options="statusOptions"
            optionLabel="label"
            optionValue="value"
          />
        </div>
      </div>

      <div class="actions-row">
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
        <Column field="subjectDisplayName" header="Subject" />
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

.rules-top-row {
  display: grid;
  grid-template-columns: minmax(220px, 1.2fr) minmax(220px, 1fr) auto;
  gap: 0.75rem;
  align-items: end;
}

.area-button-field {
  min-width: 220px;
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

  .rules-top-row {
    grid-template-columns: 1fr;
  }
}
</style>
