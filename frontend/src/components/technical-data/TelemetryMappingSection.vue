<template>
  <BaseCard class="filter-section telemetry-mapping-section">
    <div class="filter-header">
      <h3 class="filter-title">{{ t('technicalData.page.telemetry.title') }}</h3>
      <div class="filter-header-actions">
        <small class="text-muted">{{ t('technicalData.page.telemetry.hint') }}</small>
        <Button
          :icon="showTelemetryMappingAdvanced ? 'pi pi-chevron-up' : 'pi pi-chevron-down'"
          :label="showTelemetryMappingAdvanced ? t('technicalData.page.telemetry.hideMapping') : t('technicalData.page.telemetry.showMapping')"
          text
          size="small"
          @click="showTelemetryMappingAdvanced = !showTelemetryMappingAdvanced"
        />
      </div>
    </div>

    <div v-if="showTelemetryMappingAdvanced" class="advanced-filters telemetry-mapping-content">
      <div class="telemetry-mapping-controls">
        <div class="telemetry-mapping-select">
          <label class="filter-label" for="telemetry-source-type">{{ t('technicalData.page.telemetry.sourceTypeLabel') }}</label>
          <Select
            id="telemetry-source-type"
            v-model="selectedTelemetrySourceType"
            :options="telemetrySourceTypeOptions"
            optionLabel="label"
            optionValue="value"
            class="telemetry-source-select"
          />
        </div>

        <div class="telemetry-mapping-actions">
          <Button
            :label="t('technicalData.page.telemetry.resetToDefaults')"
            icon="pi pi-refresh"
            text
            :loading="telemetryResetting"
            @click="resetTelemetryMapping"
          />
          <Button
            :label="t('technicalData.page.telemetry.addRow')"
            icon="pi pi-plus"
            outlined
            @click="addTelemetryMappingRow"
          />
          <Button
            :label="t('technicalData.page.telemetry.saveMapping')"
            icon="pi pi-save"
            :loading="telemetrySaving"
            @click="saveTelemetryMapping"
          />
        </div>
      </div>

      <div v-if="telemetryLoading" class="telemetry-loading">
        {{ t('technicalData.page.telemetry.loading') }}
      </div>

      <div v-else class="telemetry-table-wrapper">
        <table class="telemetry-table">
          <thead>
          <tr>
            <th>{{ t('technicalData.page.telemetry.columns.key') }}</th>
            <th>{{ t('technicalData.page.telemetry.columns.label') }}</th>
            <th>{{ t('technicalData.page.telemetry.columns.type') }}</th>
            <th>{{ t('technicalData.page.telemetry.columns.unit') }}</th>
            <th>{{ t('technicalData.page.telemetry.columns.gpsData') }}</th>
            <th>{{ t('technicalData.page.telemetry.columns.currentPopup') }}</th>
            <th>{{ t('technicalData.page.telemetry.columns.order') }}</th>
            <th></th>
          </tr>
          </thead>
          <tbody>
          <tr v-if="telemetryMappingRows.length === 0">
            <td colspan="8" class="telemetry-empty">{{ t('technicalData.page.telemetry.empty') }}</td>
          </tr>
          <tr v-for="(entry, index) in telemetryMappingRows" :key="`${entry.key || 'new'}-${index}`">
            <td>
              <InputText v-model="entry.key" :placeholder="t('technicalData.page.telemetry.keyPlaceholder')" class="telemetry-input" />
            </td>
            <td>
              <InputText v-model="entry.label" :placeholder="t('technicalData.page.telemetry.labelPlaceholder')" class="telemetry-input" />
            </td>
            <td>
              <Select
                v-model="entry.type"
                :options="telemetryTypeOptions"
                optionLabel="label"
                optionValue="value"
                class="telemetry-input"
              />
            </td>
            <td>
              <InputText v-model="entry.unit" :placeholder="t('technicalData.page.telemetry.unitPlaceholder')" class="telemetry-input telemetry-unit" />
            </td>
            <td class="telemetry-check-cell">
              <Checkbox v-model="entry.showInGpsData" binary />
            </td>
            <td class="telemetry-check-cell">
              <Checkbox v-model="entry.showInCurrentPopup" binary />
            </td>
            <td>
              <InputNumber v-model="entry.order" :min="0" :step="10" class="telemetry-input telemetry-order" />
            </td>
            <td class="telemetry-remove-cell">
              <Button
                icon="pi pi-trash"
                severity="danger"
                text
                size="small"
                @click="removeTelemetryMappingRow(index)"
              />
            </td>
          </tr>
          </tbody>
        </table>
      </div>

      <div class="telemetry-boolean-grid" v-if="telemetryBooleanRows.length > 0">
        <div
          v-for="(entry, index) in telemetryBooleanRows"
          :key="`bool-${entry.key || index}`"
          class="telemetry-boolean-row"
        >
          <div class="telemetry-boolean-title">{{ entry.label || entry.key || t('technicalData.page.telemetry.booleanDefaultLabel', { index: index + 1 }) }}</div>
          <div class="telemetry-boolean-fields">
            <InputText
              v-model="entry.trueValues"
              :placeholder="t('technicalData.page.telemetry.trueValuesPlaceholder')"
              class="telemetry-input"
            />
            <InputText
              v-model="entry.falseValues"
              :placeholder="t('technicalData.page.telemetry.falseValuesPlaceholder')"
              class="telemetry-input"
            />
          </div>
        </div>
      </div>
    </div>
  </BaseCard>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import InputNumber from 'primevue/inputnumber'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import { useGpsSourcesStore } from '@/stores/gpsSources'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

// Per-source telemetry mapping editor (Technical data page): which telemetry keys a GPS source sends, how they
// are typed and labelled, and where they are shown.
const { t } = useI18n()
const toast = useToast()
const gpsSourcesStore = useGpsSourcesStore()

const showTelemetryMappingAdvanced = ref(false)
const telemetryLoading = ref(false)
const telemetrySaving = ref(false)
const telemetryResetting = ref(false)
const selectedTelemetrySourceType = ref('OWNTRACKS')
const telemetryMappingRows = ref([])

// Brand names (OwnTracks, GPSLogger) are never translated.
const telemetrySourceTypeOptions = [
  { label: 'OwnTracks', value: 'OWNTRACKS' },
  { label: 'GPSLogger', value: 'GPSLOGGER' }
]

const telemetryTypeOptions = computed(() => [
  { label: t('technicalData.page.telemetry.typeOptions.boolean'), value: 'boolean' },
  { label: t('technicalData.page.telemetry.typeOptions.number'), value: 'number' },
  { label: t('technicalData.page.telemetry.typeOptions.string'), value: 'string' }
])
const telemetryBooleanRows = computed(() =>
  telemetryMappingRows.value.filter(entry => entry.type === 'boolean')
)

const cloneTelemetryRows = (rows = []) => {
  if (!Array.isArray(rows)) return []
  return rows.map((entry, index) => ({
    key: entry.key || '',
    label: entry.label || entry.key || '',
    type: entry.type || 'string',
    unit: entry.unit || '',
    order: Number.isFinite(entry.order) ? entry.order : (index + 1) * 10,
    trueValues: Array.isArray(entry.trueValues) ? [...entry.trueValues] : (entry.trueValues || ''),
    falseValues: Array.isArray(entry.falseValues) ? [...entry.falseValues] : (entry.falseValues || ''),
    showInGpsData: entry.enabled === false ? false : (entry.showInGpsData ?? true),
    showInCurrentPopup: entry.enabled === false ? false : (entry.showInCurrentPopup ?? true)
  }))
}

const serializeTelemetryValues = (value) => {
  if (Array.isArray(value)) {
    return value.map(v => String(v || '').trim()).filter(v => v.length > 0)
  }
  if (!value) return []
  return String(value)
    .split(',')
    .map(v => v.trim())
    .filter(v => v.length > 0)
}

const buildTelemetryPayload = () => telemetryMappingRows.value
  .filter(entry => String(entry.key || '').trim().length > 0)
  .map((entry, index) => ({
    key: String(entry.key || '').trim(),
    label: String(entry.label || entry.key || '').trim(),
    type: entry.type || 'string',
    unit: entry.unit ? String(entry.unit).trim() : null,
    enabled: Boolean(entry.showInGpsData || entry.showInCurrentPopup),
    order: Number.isFinite(entry.order) ? entry.order : (index + 1) * 10,
    trueValues: serializeTelemetryValues(entry.trueValues),
    falseValues: serializeTelemetryValues(entry.falseValues),
    showInGpsData: entry.showInGpsData ?? true,
    showInCurrentPopup: entry.showInCurrentPopup ?? true
  }))

const loadTelemetryMapping = async (sourceType = selectedTelemetrySourceType.value) => {
  telemetryLoading.value = true
  try {
    const response = await gpsSourcesStore.fetchTelemetryMapping(sourceType)
    telemetryMappingRows.value = cloneTelemetryRows(response?.mapping)
  } catch (error) {
    console.error('Error loading telemetry mapping:', error)
    telemetryMappingRows.value = []
    toast.add({
      severity: 'error',
      summary: t('technicalData.page.toasts.telemetryLoadFailedSummary'),
      detail: formatApiErrorDetail(error, t('technicalData.page.toasts.telemetryLoadFailedFallback')),
      life: 5000
    })
  } finally {
    telemetryLoading.value = false
  }
}

const addTelemetryMappingRow = () => {
  const nextOrder = ((telemetryMappingRows.value.length || 0) + 1) * 10
  telemetryMappingRows.value = [
    ...telemetryMappingRows.value,
    {
      key: '',
      label: '',
      type: 'string',
      unit: '',
      order: nextOrder,
      trueValues: [],
      falseValues: [],
      showInGpsData: true,
      showInCurrentPopup: true
    }
  ]
}

const removeTelemetryMappingRow = (index) => {
  const next = [...telemetryMappingRows.value]
  next.splice(index, 1)
  telemetryMappingRows.value = next
}

const saveTelemetryMapping = async () => {
  telemetrySaving.value = true
  try {
    const response = await gpsSourcesStore.updateTelemetryMapping(
      selectedTelemetrySourceType.value,
      buildTelemetryPayload()
    )
    telemetryMappingRows.value = cloneTelemetryRows(response?.mapping)
    toast.add({
      severity: 'success',
      summary: t('technicalData.page.toasts.telemetrySavedSummary'),
      detail: t('technicalData.page.toasts.telemetrySavedDetail'),
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('technicalData.page.toasts.saveFailedSummary'),
      detail: formatApiErrorDetail(error, t('technicalData.page.toasts.saveTelemetryFailedFallback')),
      life: 5000
    })
  } finally {
    telemetrySaving.value = false
  }
}

const resetTelemetryMapping = async () => {
  telemetryResetting.value = true
  try {
    const response = await gpsSourcesStore.resetTelemetryMapping(selectedTelemetrySourceType.value)
    telemetryMappingRows.value = cloneTelemetryRows(response?.mapping)
    toast.add({
      severity: 'success',
      summary: t('technicalData.page.toasts.telemetryResetSummary'),
      detail: t('technicalData.page.toasts.telemetryResetDetail'),
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('technicalData.page.toasts.resetFailedSummary'),
      detail: formatApiErrorDetail(error, t('technicalData.page.toasts.resetTelemetryFailedFallback')),
      life: 5000
    })
  } finally {
    telemetryResetting.value = false
  }
}

onMounted(() => loadTelemetryMapping(selectedTelemetrySourceType.value))

watch(selectedTelemetrySourceType, async (type) => {
  await loadTelemetryMapping(type)
})
</script>

<style scoped>
.filter-label {
  font-weight: 500;
  color: var(--gp-text-secondary);
  white-space: nowrap;
}

.filter-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--gp-spacing-md);
  padding-bottom: var(--gp-spacing-sm);
  border-bottom: 1px solid var(--gp-border);
}

.filter-title {
  font-size: 1.1rem;
  font-weight: 600;
  margin: 0;
  color: var(--gp-text-primary);
}

.filter-header-actions {
  display: flex;
  gap: var(--gp-spacing-sm);
  align-items: center;
}

.advanced-filters {
  margin-top: var(--gp-spacing-md);
  padding-top: var(--gp-spacing-md);
  border-top: 1px solid var(--gp-border);
}

.telemetry-mapping-section {
  margin-bottom: var(--gp-spacing-lg);
}

.telemetry-mapping-content {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.telemetry-mapping-controls {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.telemetry-mapping-select {
  min-width: 220px;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.telemetry-source-select {
  width: 220px;
}

.telemetry-mapping-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.telemetry-loading {
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
}

.telemetry-table-wrapper {
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-small);
  overflow-x: auto;
  background: var(--gp-surface-card);
}

.telemetry-table {
  width: 100%;
  min-width: 980px;
  border-collapse: collapse;
}

.telemetry-table th,
.telemetry-table td {
  border-bottom: 1px solid var(--gp-border);
  padding: 0.45rem 0.5rem;
  text-align: left;
  vertical-align: middle;
  font-size: 0.82rem;
}

.telemetry-table th {
  white-space: nowrap;
  color: var(--gp-text-secondary);
  font-weight: 600;
  background: var(--gp-surface-muted);
}

.telemetry-table tbody tr:last-child td {
  border-bottom: none;
}

.telemetry-input {
  width: 100%;
}

.telemetry-unit {
  min-width: 72px;
}

.telemetry-order {
  max-width: 90px;
}

.telemetry-check-cell {
  text-align: center;
}

.telemetry-remove-cell {
  text-align: center;
}

.telemetry-empty {
  text-align: center;
  color: var(--gp-text-muted, #6b7280);
  font-style: italic;
}

.telemetry-boolean-grid {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.telemetry-boolean-row {
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-small);
  padding: 0.6rem;
  background: var(--gp-surface-card);
}

.telemetry-boolean-title {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: 0.45rem;
}

.telemetry-boolean-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.5rem;
}

@media (max-width: 768px) {
  .filter-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .filter-header-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .telemetry-mapping-controls {
    align-items: stretch;
  }

  .telemetry-source-select {
    width: 100%;
  }

  .telemetry-mapping-actions {
    width: 100%;
  }

  .telemetry-boolean-fields {
    grid-template-columns: 1fr;
  }
}
</style>
