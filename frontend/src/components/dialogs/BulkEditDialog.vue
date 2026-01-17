<template>
  <Dialog
      :visible="visible"
      :header="`Bulk Edit ${selectedItems.length} ${itemTypeName}`"
      :modal="true"
      :closable="!saving"
      @update:visible="handleClose"
      class="bulk-edit-dialog gp-dialog-md"
  >
    <div class="dialog-content">
      <!-- Selected Items Summary -->
      <div class="summary-section">
        <i class="pi pi-info-circle"></i>
        <span>You are editing <strong>{{selectedItems.length}}</strong> {{itemTypeName.toLowerCase()}}{{selectedItems.length !== 1 ? 's' : ''}}</span>
      </div>

      <!-- Field Selections -->
      <div class="field-section">
        <h4>Select Fields to Update</h4>

        <!-- City Field -->
        <div class="field-group">
          <div class="field-header">
            <Checkbox
                v-model="updateCity"
                :binary="true"
                inputId="updateCity"
            />
            <label for="updateCity" class="field-label">Update City</label>
          </div>
          <AutoComplete
              v-model="cityValue"
              :suggestions="citySuggestions"
              @complete="searchCities"
              :disabled="!updateCity"
              placeholder="Enter city name"
              class="field-input"
              :class="{ 'p-invalid': updateCity && !cityValue }"
          />
          <small v-if="updateCity && !cityValue" class="p-error">City value is required</small>
        </div>

        <!-- Country Field -->
        <div class="field-group">
          <div class="field-header">
            <Checkbox
                v-model="updateCountry"
                :binary="true"
                inputId="updateCountry"
            />
            <label for="updateCountry" class="field-label">Update Country</label>
          </div>
          <AutoComplete
              v-model="countryValue"
              :suggestions="countrySuggestions"
              @complete="searchCountries"
              :disabled="!updateCountry"
              placeholder="Enter country name"
              class="field-input"
              :class="{ 'p-invalid': updateCountry && !countryValue }"
          />
          <small v-if="updateCountry && !countryValue" class="p-error">Country value is required</small>
        </div>
      </div>

      <!-- Validation Message -->
      <Message v-if="!updateCity && !updateCountry" severity="warn" :closable="false">
        Please select at least one field to update
      </Message>

      <!-- Typo Warning Dialog -->
      <Dialog
          v-model:visible="showTypoWarning"
          header="Possible Typo Detected"
          :modal="true"
          class="typo-warning-dialog gp-dialog-sm"
      >
        <div class="typo-content">
          <i class="pi pi-exclamation-triangle warning-icon"></i>
          <p>The following value(s) are not found in your existing data:</p>
          <ul class="typo-list">
            <li v-for="warning in typoWarnings" :key="warning.field">
              <strong>{{warning.field}}:</strong> "{{warning.value}}"
              <div v-if="warning.suggestions.length > 0" class="suggestions">
                Did you mean:
                <a v-for="(suggestion, index) in warning.suggestions"
                   :key="index"
                   @click="applySuggestion(warning.field, suggestion)"
                   class="suggestion-link">
                  {{suggestion}}
                </a>
              </div>
            </li>
          </ul>
          <p>Do you want to continue with these values?</p>
        </div>
        <template #footer>
          <Button label="Cancel" severity="secondary" @click="showTypoWarning = false" text />
          <Button label="Continue Anyway" severity="warning" @click="proceedWithUpdate" />
        </template>
      </Dialog>
    </div>

    <template #footer>
      <Button label="Cancel" severity="secondary" @click="handleClose" :disabled="saving" text />
      <Button
          :label="saving ? 'Updating...' : `Update ${selectedItems.length} Items`"
          severity="primary"
          @click="handleSave"
          :disabled="!isFormValid || saving"
          :loading="saving"
      />
    </template>
  </Dialog>
</template>

<script setup>
import {ref, computed, watch, onMounted} from 'vue'
import {useToast} from 'primevue/usetoast'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import AutoComplete from 'primevue/autocomplete'
import Message from 'primevue/message'

const props = defineProps({
  visible: Boolean,
  selectedItems: {
    type: Array,
    default: () => []
  },
  itemTypeName: {
    type: String,
    required: true // e.g., "Favorites" or "Geocoding Results"
  },
  store: {
    type: Object,
    required: true // The Pinia store instance (favoritesStore or geocodingStore)
  },
  bulkUpdateMethod: {
    type: String,
    required: true // Name of the method to call (e.g., 'bulkUpdateFavorites' or 'bulkUpdateGeocoding')
  },
  distinctValuesMethod: {
    type: String,
    required: true // Name of the method to call (e.g., 'fetchDistinctValues')
  },
  idField: {
    type: String,
    default: 'id' // Field name for the ID (usually 'id')
  }
})

const emit = defineEmits(['close', 'save'])

const toast = useToast()

// Form state
const updateCity = ref(false)
const updateCountry = ref(false)
const cityValue = ref('')
const countryValue = ref('')
const saving = ref(false)

// Autocomplete state
const citySuggestions = ref([])
const countrySuggestions = ref([])
const existingCities = ref([])
const existingCountries = ref([])

// Typo warning state
const showTypoWarning = ref(false)
const typoWarnings = ref([])

// Computed
const isFormValid = computed(() => {
  if (!updateCity.value && !updateCountry.value) return false
  if (updateCity.value && !cityValue.value?.trim()) return false
  if (updateCountry.value && !countryValue.value?.trim()) return false
  return true
})

// Methods
const searchCities = (event) => {
  const query = event.query.toLowerCase()
  citySuggestions.value = existingCities.value.filter(city =>
      city.toLowerCase().includes(query)
  )
}

const searchCountries = (event) => {
  const query = event.query.toLowerCase()
  countrySuggestions.value = existingCountries.value.filter(country =>
      country.toLowerCase().includes(query)
  )
}

const loadDistinctValues = async () => {
  try {
    const method = props.store[props.distinctValuesMethod]
    if (typeof method !== 'function') {
      console.error(`Method ${props.distinctValuesMethod} not found in store`)
      return
    }
    const result = await method()
    existingCities.value = result.cities || []
    existingCountries.value = result.countries || []
  } catch (error) {
    console.error('Error loading distinct values:', error)
  }
}

const checkForTypos = () => {
  const warnings = []

  if (updateCity.value && cityValue.value) {
    const normalizedCity = cityValue.value.trim()
    if (!existingCities.value.includes(normalizedCity)) {
      warnings.push({
        field: 'City',
        value: normalizedCity,
        suggestions: findSimilar(normalizedCity, existingCities.value)
      })
    }
  }

  if (updateCountry.value && countryValue.value) {
    const normalizedCountry = countryValue.value.trim()
    if (!existingCountries.value.includes(normalizedCountry)) {
      warnings.push({
        field: 'Country',
        value: normalizedCountry,
        suggestions: findSimilar(normalizedCountry, existingCountries.value)
      })
    }
  }

  return warnings
}

const findSimilar = (value, list) => {
  return list.filter(item => {
    const distance = levenshteinDistance(value.toLowerCase(), item.toLowerCase())
    return distance > 0 && distance <= 2
  }).slice(0, 3)
}

const levenshteinDistance = (str1, str2) => {
  const matrix = []

  for (let i = 0; i <= str2.length; i++) {
    matrix[i] = [i]
  }

  for (let j = 0; j <= str1.length; j++) {
    matrix[0][j] = j
  }

  for (let i = 1; i <= str2.length; i++) {
    for (let j = 1; j <= str1.length; j++) {
      if (str2.charAt(i - 1) === str1.charAt(j - 1)) {
        matrix[i][j] = matrix[i - 1][j - 1]
      } else {
        matrix[i][j] = Math.min(
            matrix[i - 1][j - 1] + 1,
            matrix[i][j - 1] + 1,
            matrix[i - 1][j] + 1
        )
      }
    }
  }

  return matrix[str2.length][str1.length]
}

const applySuggestion = (field, suggestion) => {
  if (field === 'City') {
    cityValue.value = suggestion
  } else if (field === 'Country') {
    countryValue.value = suggestion
  }
  showTypoWarning.value = false
}

const handleSave = () => {
  if (!isFormValid.value) return

  const warnings = checkForTypos()
  if (warnings.length > 0) {
    typoWarnings.value = warnings
    showTypoWarning.value = true
    return
  }

  proceedWithUpdate()
}

const proceedWithUpdate = async () => {
  showTypoWarning.value = false
  saving.value = true

  try {
    const ids = props.selectedItems.map(item => item[props.idField])

    const method = props.store[props.bulkUpdateMethod]
    if (typeof method !== 'function') {
      throw new Error(`Method ${props.bulkUpdateMethod} not found in store`)
    }

    const result = await method(
        ids,
        updateCity.value,
        updateCity.value ? cityValue.value.trim() : null,
        updateCountry.value,
        updateCountry.value ? countryValue.value.trim() : null
    )

    const successMsg = result.failedCount > 0
        ? `Updated ${result.successCount} of ${result.totalRequested} items (${result.failedCount} failed)`
        : `Successfully updated ${result.successCount} ${props.itemTypeName.toLowerCase()}`

    toast.add({
      severity: result.failedCount > 0 ? 'warn' : 'success',
      summary: 'Bulk Update Complete',
      detail: successMsg,
      life: 5000
    })

    emit('save')
    handleClose()
  } catch (error) {
    console.error(`Error bulk updating ${props.itemTypeName}:`, error)
    toast.add({
      severity: 'error',
      summary: 'Update Failed',
      detail: error.message || `Failed to update ${props.itemTypeName.toLowerCase()}`,
      life: 5000
    })
  } finally {
    saving.value = false
  }
}

const handleClose = () => {
  if (!saving.value) {
    resetForm()
    emit('close')
  }
}

const resetForm = () => {
  updateCity.value = false
  updateCountry.value = false
  cityValue.value = ''
  countryValue.value = ''
  showTypoWarning.value = false
  typoWarnings.value = []
}

watch(() => props.visible, (newVal) => {
  if (newVal) {
    loadDistinctValues()
    resetForm()
  }
})

onMounted(() => {
  loadDistinctValues()
})
</script>

<style scoped>
.bulk-edit-dialog {
  font-family: var(--gp-font-family);
}

.dialog-content {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.summary-section {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem;
  background: var(--p-primary-50);
  border-radius: var(--gp-radius-medium);
  color: var(--p-primary-700);
}

.summary-section i {
  font-size: 1.25rem;
}

.field-section h4 {
  margin: 0 0 1rem 0;
  color: var(--gp-text-primary);
  font-size: 1rem;
  font-weight: 600;
}

.field-group {
  margin-bottom: 1.5rem;
}

.field-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.field-label {
  font-weight: 500;
  color: var(--gp-text-primary);
  cursor: pointer;
  user-select: none;
}

.field-input {
  width: 100%;
}

.typo-content {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.warning-icon {
  font-size: 3rem;
  color: var(--p-yellow-500);
  text-align: center;
  display: block;
}

.typo-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.typo-list li {
  padding: 0.75rem;
  background: var(--p-yellow-50);
  border-radius: var(--gp-radius-small);
  margin-bottom: 0.5rem;
}

.suggestions {
  margin-top: 0.5rem;
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
}

.suggestion-link {
  color: var(--p-primary-600);
  cursor: pointer;
  margin-left: 0.5rem;
  text-decoration: underline;
}

.suggestion-link:hover {
  color: var(--p-primary-700);
}

.p-dark .summary-section {
  background: var(--p-primary-900);
  color: var(--p-primary-100);
}

.p-dark .typo-list li {
  background: var(--p-yellow-900);
}
</style>
