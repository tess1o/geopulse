<template>
  <AppLayout variant="default">
    <PageContainer
      title="Reverse Geocoding Management"
      subtitle="Manage and update your geocoding results"
      :loading="isLoading"
      variant="fullwidth"
    >
    <template #actions>
      <div class="header-actions">
        <Button
          :label="`Reconcile All (${formatNumber(totalRecords)})`"
          icon="pi pi-refresh"
          severity="secondary"
          size="small"
          @click="reconcileAll"
          :disabled="totalRecords === 0"
        />
        <Button
          v-if="selectedRows.length > 0"
          :label="`Bulk Edit (${selectedRows.length})`"
          icon="pi pi-pencil"
          severity="secondary"
          size="small"
          @click="bulkEditSelected"
          class="bulk-action-button"
        />
        <Button
          v-if="selectedRows.length > 0"
          :label="`Reconcile Selected (${selectedRows.length})`"
          icon="pi pi-refresh"
          severity="info"
          size="small"
          @click="reconcileSelected"
          class="bulk-action-button"
        />
      </div>
    </template>

    <BaseCard class="provider-switch-info">
      <i class="pi pi-info-circle"></i>
      <span>Provider changes affect new lookups only. Use Reconcile Selected/Reconcile All to refresh existing cached records.</span>
    </BaseCard>

    <BaseCard class="normalization-rules-section">
      <div class="normalization-header">
        <div>
          <h3 class="normalization-title">Normalization Rules</h3>
          <p class="normalization-subtitle">Per-user mapping rules for country and city names. New geocoding results use these rules automatically; use Apply Rules Now to update existing saved geocoding and favorites.</p>
        </div>
        <div class="normalization-actions">
          <Button
            :label="rulesExpanded ? 'Collapse' : 'Expand'"
            :icon="rulesExpanded ? 'pi pi-chevron-up' : 'pi pi-chevron-down'"
            severity="secondary"
            size="small"
            text
            @click="rulesExpanded = !rulesExpanded"
          />
          <Button
            label="Add Rule"
            icon="pi pi-plus"
            severity="secondary"
            size="small"
            @click="openCreateRuleDialog"
            :disabled="!rulesExpanded"
          />
          <Button
            label="Apply Rules Now"
            icon="pi pi-play"
            severity="primary"
            size="small"
            @click="openApplyRulesDialog()"
            :disabled="!rulesExpanded || normalizationRules.length === 0"
          />
        </div>
      </div>

      <div v-if="!rulesExpanded" class="normalization-collapsed-hint">
        Section collapsed. Expand to manage rules.
      </div>
      <template v-else>
        <div v-if="normalizationRules.length === 0" class="normalization-empty">
          No normalization rules yet.
        </div>
        <DataTable
          v-else
          :value="normalizationRules"
          data-key="id"
          responsive-layout="scroll"
          class="normalization-table"
        >
          <Column field="ruleType" header="Type">
            <template #body="slotProps">
              <Tag
                :value="slotProps.data.ruleType === 'COUNTRY' ? 'Country' : 'City'"
                :severity="slotProps.data.ruleType === 'COUNTRY' ? 'info' : 'contrast'"
              />
            </template>
          </Column>
          <Column header="From">
            <template #body="slotProps">
              {{ formatRuleSource(slotProps.data) }}
            </template>
          </Column>
          <Column header="To">
            <template #body="slotProps">
              {{ formatRuleTarget(slotProps.data) }}
            </template>
          </Column>
          <Column header="Actions">
            <template #body="slotProps">
              <div class="actions-buttons">
                <Button
                  icon="pi pi-pencil"
                  severity="secondary"
                  size="small"
                  text
                  @click="openEditRuleDialog(slotProps.data)"
                />
                <Button
                  icon="pi pi-play"
                  severity="success"
                  size="small"
                  text
                  @click="openApplyRulesDialog(slotProps.data)"
                />
                <Button
                  icon="pi pi-trash"
                  severity="danger"
                  size="small"
                  text
                  @click="deleteRule(slotProps.data)"
                />
              </div>
            </template>
          </Column>
        </DataTable>
      </template>
    </BaseCard>

    <!-- Filters -->
    <BaseCard class="filter-section">
      <div class="filter-controls">
        <div class="filter-group">
          <label class="filter-label">Provider:</label>
          <Select
            v-model="selectedProvider"
            :options="providerOptions"
            optionLabel="label"
            optionValue="value"
            placeholder="All Providers"
            class="provider-select"
            @change="handleFilterChange"
          />
        </div>
        <div class="filter-group">
          <label class="filter-label">Search:</label>
          <InputText
            v-model="searchText"
            placeholder="Search by location, city, or country"
            class="search-input"
            @input="handleSearchChange"
          />
        </div>
        <Button
          label="Clear Filters"
          severity="secondary"
          size="small"
          @click="clearFilters"
          :disabled="!hasActiveFilters"
        />
      </div>
    </BaseCard>

    <!-- Geocoding Results Table -->
    <BaseCard class="table-section">
      <DataTable
        :value="geocodingResults"
        :loading="tableLoading"
        paginator
        :rows="pageSize"
        :total-records="totalRecords"
        lazy
        @page="onPageChange"
        @sort="onSort"
        v-model:sort-field="sortField"
        v-model:sort-order="sortOrder"
        data-key="id"
        responsive-layout="scroll"
        class="geocoding-table"
        v-model:selection="selectedRows"
        selection-mode="multiple"
        style="max-width: 100%; box-sizing: border-box;"
      >
        <template #header>
          <div class="table-header">
            <span class="table-title">Geocoding Results</span>
          </div>
        </template>

        <template #empty>
          <div class="empty-state">
            <i class="pi pi-map-marker empty-icon"></i>
            <h3>No Geocoding Results Found</h3>
            <p>No geocoding data available for the selected criteria.</p>
          </div>
        </template>

        <!-- Selection Column -->
        <Column selectionMode="multiple" headerStyle="width: 3rem" class="selection-col"></Column>

        <Column field="displayName" header="Display Name" sortable class="name-col">
          <template #body="slotProps">
            <div class="name-cell">{{ slotProps.data.displayName }}</div>
          </template>
        </Column>

        <Column field="city" header="City" sortable class="city-col" v-if="!isMobile">
          <template #body="slotProps">
            <span v-if="slotProps.data.city">{{ slotProps.data.city }}</span>
            <span v-else class="null-value">-</span>
          </template>
        </Column>

        <Column field="country" header="Country" sortable class="country-col" v-if="!isMobile && !isTablet">
          <template #body="slotProps">
            <span v-if="slotProps.data.country">{{ slotProps.data.country }}</span>
            <span v-else class="null-value">-</span>
          </template>
        </Column>

        <Column field="providerName" header="Provider" sortable class="provider-col" v-if="!isMobile">
          <template #body="slotProps">
            <Tag
              :value="slotProps.data.providerName"
              :severity="getProviderSeverity(slotProps.data.providerName)"
              class="provider-tag"
            />
          </template>
        </Column>

        <Column header="Coordinates" class="coordinates-col" v-if="!isMobile">
          <template #body="slotProps">
            <div class="coordinates-cell">
              <div class="coordinate-line">{{ slotProps.data.latitude?.toFixed(6) }}</div>
              <div class="coordinate-line">{{ slotProps.data.longitude?.toFixed(6) }}</div>
            </div>
          </template>
        </Column>

        <Column field="lastAccessedAt" header="Last Used" sortable class="date-col" v-if="!isMobile && !isTablet">
          <template #body="slotProps">
            <span>{{ timezone.timeAgo(slotProps.data.lastAccessedAt) }}</span>
          </template>
        </Column>

        <Column header="Actions" class="actions-col">
          <template #body="slotProps">
            <div class="actions-buttons">
              <Button
                icon="pi pi-eye"
                severity="primary"
                size="small"
                text
                @click="viewDetails(slotProps.data)"
                v-tooltip.top="'View Details'"
                class="action-button view-button"
              />
              <Button
                icon="pi pi-pencil"
                severity="secondary"
                size="small"
                text
                @click="editResult(slotProps.data)"
                v-tooltip.top="'Edit'"
                class="action-button edit-button"
              />
              <Button
                icon="pi pi-refresh"
                severity="info"
                size="small"
                text
                @click="reconcileResult(slotProps.data)"
                v-tooltip.top="'Reconcile'"
                class="action-button reconcile-button"
              />
            </div>
          </template>
        </Column>
      </DataTable>
    </BaseCard>

    <!-- Edit Dialog -->
    <GeocodingEditDialog
      :visible="showEditDialog"
      :geocoding-result="selectedResult"
      @close="showEditDialog = false"
      @save="handleEditSave"
    />

    <!-- Bulk Edit Dialog -->
    <BulkEditDialog
      :visible="showBulkEditDialog"
      :selected-items="selectedRows"
      item-type-name="Geocoding Results"
      :store="geocodingStore"
      bulk-update-method="bulkUpdateGeocoding"
      distinct-values-method="fetchDistinctValues"
      @close="showBulkEditDialog = false"
      @save="handleBulkEditSave"
    />

    <!-- Reconcile Dialog -->
    <GeocodingReconcileDialog
      :visible="showReconcileDialog"
      :selected-results="selectedReconcileResults"
      :enabled-providers="enabledProviders"
      :reconcile-mode="reconcileMode"
      :total-records="totalRecords"
      :current-filters="{ provider: selectedProvider, searchText: searchText }"
      :job-progress="jobProgress"
      @close="showReconcileDialog = false"
      @reconcile="handleReconcile"
      @reconcile-complete="handleReconcileComplete"
    />

    <Dialog
      v-model:visible="showRuleDialog"
      :header="editingRule ? 'Edit Normalization Rule' : 'Add Normalization Rule'"
      modal
      class="gp-dialog-md"
    >
      <div class="rule-dialog-content">
        <div class="field-row">
          <label class="filter-label">Rule Type</label>
          <Select
            v-model="ruleForm.ruleType"
            :options="ruleTypeOptions"
            optionLabel="label"
            optionValue="value"
            class="rule-input"
          />
        </div>

        <template v-if="ruleForm.ruleType === 'COUNTRY'">
          <div class="field-row">
            <label class="filter-label">From Country</label>
            <AutoComplete
              v-model="ruleForm.sourceCountry"
              :suggestions="countrySuggestions"
              @complete="searchCountries"
              class="rule-input"
              placeholder="e.g. Болгарія"
            />
          </div>
          <div class="field-row">
            <label class="filter-label">To Country</label>
            <AutoComplete
              v-model="ruleForm.targetCountry"
              :suggestions="countrySuggestions"
              @complete="searchCountries"
              class="rule-input"
              placeholder="e.g. Bulgaria"
            />
          </div>
        </template>

        <template v-else>
          <div class="field-row">
            <label class="filter-label">From City</label>
            <AutoComplete
              v-model="ruleForm.sourceCity"
              :suggestions="citySuggestions"
              @complete="searchCities"
              class="rule-input"
              placeholder="e.g. Sofia"
            />
          </div>
          <div class="field-row">
            <label class="filter-label">To City</label>
            <AutoComplete
              v-model="ruleForm.targetCity"
              :suggestions="citySuggestions"
              @complete="searchCities"
              class="rule-input"
              placeholder="e.g. Софія"
            />
          </div>
        </template>
      </div>
      <template #footer>
        <Button label="Cancel" severity="secondary" text @click="closeRuleDialog" />
        <Button label="Save" severity="primary" :disabled="!isRuleFormValid" @click="saveRule" />
      </template>
    </Dialog>

    <Dialog
      v-model:visible="showApplyRulesDialog"
      :header="selectedApplyRule ? 'Apply Selected Rule' : 'Apply Normalization Rules'"
      modal
      class="gp-dialog-sm"
    >
      <div class="rule-dialog-content">
        <div v-if="selectedApplyRule" class="normalization-subtitle">
          Applying: {{ formatRuleSource(selectedApplyRule) }} → {{ formatRuleTarget(selectedApplyRule) }}
        </div>
        <div class="field-row checkbox-row">
          <Checkbox v-model="applyRulesForm.applyToGeocoding" :binary="true" inputId="applyGeo" />
          <label for="applyGeo">Apply to reverse geocoding entities</label>
        </div>
        <div class="field-row checkbox-row">
          <Checkbox v-model="applyRulesForm.applyToFavorites" :binary="true" inputId="applyFav" />
          <label for="applyFav">Apply to favorites</label>
        </div>
      </div>
      <template #footer>
        <Button label="Cancel" severity="secondary" text @click="closeApplyRulesDialog" />
        <Button
          label="Apply"
          severity="primary"
          :disabled="!canApplyRules"
          @click="applyRulesNow"
        />
      </template>
    </Dialog>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { useGeocodingStore } from '@/stores/geocoding'
import { useFavoritesStore } from '@/stores/favorites'
import { useTimezone } from '@/composables/useTimezone'
import { useReconciliationJobProgress } from '@/composables/useReconciliationJobProgress'

const timezone = useTimezone()

// Components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import GeocodingEditDialog from '@/components/dialogs/GeocodingEditDialog.vue'
import GeocodingReconcileDialog from '@/components/dialogs/GeocodingReconcileDialog.vue'
import BulkEditDialog from '@/components/dialogs/BulkEditDialog.vue'

// PrimeVue
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Select from 'primevue/select'
import InputText from 'primevue/inputtext'
import Dialog from 'primevue/dialog'
import Checkbox from 'primevue/checkbox'
import AutoComplete from 'primevue/autocomplete'

// Store and utils
const geocodingStore = useGeocodingStore()
const favoritesStore = useFavoritesStore()
const toast = useToast()
const router = useRouter()

// Progress tracking
const { jobProgress, startPolling, reset: resetProgress } = useReconciliationJobProgress()

// Reactive state
const isMobile = ref(false)
const isTablet = ref(false)
const selectedProvider = ref(null)
const searchText = ref('')
const pageSize = ref(50)
const currentPage = ref(0)
const sortField = ref('lastAccessedAt')
const sortOrder = ref(-1)

// Loading states
const isLoading = ref(false)
const tableLoading = ref(false)

// Dialog states
const showEditDialog = ref(false)
const showReconcileDialog = ref(false)
const showBulkEditDialog = ref(false)
const showRuleDialog = ref(false)
const showApplyRulesDialog = ref(false)
const rulesExpanded = ref(true)
const selectedResult = ref(null)
const selectedRows = ref([])
const reconcileMode = ref('selected') // 'selected' | 'all'
const editingRule = ref(null)
const selectedApplyRule = ref(null)
const activeJobMode = ref(null)
const applyRulesForm = ref({
  applyToGeocoding: true,
  applyToFavorites: true
})
const ruleForm = ref({
  ruleType: 'COUNTRY',
  sourceCountry: '',
  sourceCity: '',
  targetCountry: '',
  targetCity: ''
})
const allCities = ref([])
const allCountries = ref([])
const citySuggestions = ref([])
const countrySuggestions = ref([])

// Computed properties
const geocodingResults = computed(() => geocodingStore.geocodingResults)
const totalRecords = computed(() => geocodingStore.totalRecords)
const enabledProviders = computed(() => geocodingStore.enabledProviders)
const availableProviders = computed(() => geocodingStore.availableProviders)
const normalizationRules = computed(() => geocodingStore.normalizationRules)

const providerOptions = computed(() => {
  const options = [{ label: 'All Providers', value: null }]
  // Use available providers (from database) for filter dropdown
  availableProviders.value.forEach(providerName => {
    options.push({
      label: providerName,
      value: providerName
    })
  })
  return options
})

const hasActiveFilters = computed(() =>
  selectedProvider.value !== null || (searchText.value && searchText.value.trim() !== '')
)

const selectedReconcileResults = computed(() => {
  return selectedRows.value.length > 0 ? selectedRows.value : (selectedResult.value ? [selectedResult.value] : [])
})

const ruleTypeOptions = [
  { label: 'Country', value: 'COUNTRY' },
  { label: 'City', value: 'CITY' }
]

const isRuleFormValid = computed(() => {
  if (ruleForm.value.ruleType === 'COUNTRY') {
    return !!ruleForm.value.sourceCountry?.trim() && !!ruleForm.value.targetCountry?.trim()
  }
  return !!ruleForm.value.sourceCity?.trim() && !!ruleForm.value.targetCity?.trim()
})

const canApplyRules = computed(() => {
  return applyRulesForm.value.applyToGeocoding || applyRulesForm.value.applyToFavorites
})

// Methods
const formatNumber = (value) => {
  if (!value && value !== 0) return '0'
  return new Intl.NumberFormat().format(value)
}

const formatRuleSource = (rule) => {
  if (rule.ruleType === 'COUNTRY') {
    return rule.sourceCountry || '-'
  }
  return rule.sourceCity || '-'
}

const formatRuleTarget = (rule) => {
  if (rule.ruleType === 'COUNTRY') {
    return rule.targetCountry || '-'
  }
  return rule.targetCity || '-'
}

const getProviderSeverity = (providerName) => {
  const severityMap = {
    'Nominatim': 'success',
    'GoogleMaps': 'info',
    'Mapbox': 'warning',
    'Photon': 'secondary'
  }
  return severityMap[providerName] || 'contrast'
}

const handleResize = () => {
  isMobile.value = window.innerWidth < 768
  isTablet.value = window.innerWidth >= 768 && window.innerWidth < 1024
  pageSize.value = isMobile.value ? 25 : 50
}

let searchTimeout = null
const handleSearchChange = () => {
  clearTimeout(searchTimeout)
  searchTimeout = setTimeout(() => {
    currentPage.value = 0
    loadGeocodingResults()
  }, 500)
}

const handleFilterChange = () => {
  currentPage.value = 0
  loadGeocodingResults()
}

const clearFilters = () => {
  selectedProvider.value = null
  searchText.value = ''
  currentPage.value = 0
  loadGeocodingResults()
}

const onPageChange = async (event) => {
  currentPage.value = event.page
  await loadGeocodingResults()
}

const onSort = async (event) => {
  sortField.value = event.sortField
  sortOrder.value = event.sortOrder
  currentPage.value = 0
  await loadGeocodingResults()
}

const loadGeocodingResults = async () => {
  try {
    tableLoading.value = true
    const params = {
      page: currentPage.value + 1,
      limit: pageSize.value
    }

    if (selectedProvider.value) {
      params.providerName = selectedProvider.value
    }

    if (searchText.value && searchText.value.trim() !== '') {
      params.searchText = searchText.value.trim()
    }

    if (sortField.value) {
      params.sortField = sortField.value
      params.sortOrder = sortOrder.value === 1 ? 'asc' : 'desc'
    }

    await geocodingStore.fetchGeocodingResults(params)
  } catch (error) {
    console.error('Error loading geocoding results:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load geocoding results',
      life: 3000
    })
  } finally {
    tableLoading.value = false
  }
}

const loadEnabledProviders = async () => {
  try {
    await geocodingStore.fetchEnabledProviders()
  } catch (error) {
    console.error('Error loading enabled providers:', error)
  }
}

const loadAvailableProviders = async () => {
  try {
    await geocodingStore.fetchAvailableProviders()
  } catch (error) {
    console.error('Error loading available providers:', error)
  }
}

const loadNormalizationRules = async () => {
  try {
    await geocodingStore.fetchNormalizationRules()
  } catch (error) {
    console.error('Error loading normalization rules:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load normalization rules',
      life: 3000
    })
  }
}

const mergeDistinctStrings = (...arrays) => {
  const merged = arrays
      .flat()
      .filter(v => typeof v === 'string')
      .map(v => v.trim())
      .filter(v => v.length > 0)
  return [...new Set(merged)].sort((a, b) => a.localeCompare(b))
}

const normalizeDistinctResponse = (response) => {
  if (!response) return { cities: [], countries: [] }
  if (response.data && (Array.isArray(response.data.cities) || Array.isArray(response.data.countries))) {
    return response.data
  }
  return response
}

const loadCombinedDistinctValues = async () => {
  try {
    const [geoRaw, favRaw] = await Promise.all([
      geocodingStore.fetchDistinctValues(),
      favoritesStore.fetchDistinctValues()
    ])
    const geo = normalizeDistinctResponse(geoRaw)
    const fav = normalizeDistinctResponse(favRaw)

    allCities.value = mergeDistinctStrings(geo.cities || [], fav.cities || [])
    allCountries.value = mergeDistinctStrings(geo.countries || [], fav.countries || [])
  } catch (error) {
    console.error('Error loading combined distinct city/country values:', error)
    allCities.value = []
    allCountries.value = []
  }
}

const searchCities = (event) => {
  const query = (event?.query || '').toLowerCase()
  citySuggestions.value = allCities.value.filter(city => city.toLowerCase().includes(query))
}

const searchCountries = (event) => {
  const query = (event?.query || '').toLowerCase()
  countrySuggestions.value = allCountries.value.filter(country => country.toLowerCase().includes(query))
}

const resetRuleForm = () => {
  ruleForm.value = {
    ruleType: 'COUNTRY',
    sourceCountry: '',
    sourceCity: '',
    targetCountry: '',
    targetCity: ''
  }
}

const openCreateRuleDialog = () => {
  editingRule.value = null
  resetRuleForm()
  loadCombinedDistinctValues()
  showRuleDialog.value = true
}

const openEditRuleDialog = (rule) => {
  editingRule.value = rule
  ruleForm.value = {
    ruleType: rule.ruleType,
    sourceCountry: rule.sourceCountry || '',
    sourceCity: rule.sourceCity || '',
    targetCountry: rule.targetCountry || '',
    targetCity: rule.targetCity || ''
  }
  loadCombinedDistinctValues()
  showRuleDialog.value = true
}

const closeRuleDialog = () => {
  showRuleDialog.value = false
  editingRule.value = null
  resetRuleForm()
}

const openApplyRulesDialog = (rule = null) => {
  selectedApplyRule.value = rule
  showApplyRulesDialog.value = true
}

const closeApplyRulesDialog = () => {
  showApplyRulesDialog.value = false
  selectedApplyRule.value = null
}

const saveRule = async () => {
  if (!isRuleFormValid.value) return

  const isCountryRule = ruleForm.value.ruleType === 'COUNTRY'
  const payload = {
    ruleType: ruleForm.value.ruleType,
    sourceCountry: isCountryRule ? (ruleForm.value.sourceCountry?.trim() || null) : null,
    sourceCity: isCountryRule ? null : (ruleForm.value.sourceCity?.trim() || null),
    targetCountry: isCountryRule ? (ruleForm.value.targetCountry?.trim() || null) : null,
    targetCity: isCountryRule ? null : (ruleForm.value.targetCity?.trim() || null)
  }

  try {
    if (editingRule.value?.id) {
      await geocodingStore.updateNormalizationRule(editingRule.value.id, payload)
      toast.add({
        severity: 'success',
        summary: 'Rule Updated',
        detail: 'Normalization rule updated successfully',
        life: 3000
      })
    } else {
      await geocodingStore.createNormalizationRule(payload)
      toast.add({
        severity: 'success',
        summary: 'Rule Added',
        detail: 'Normalization rule created successfully',
        life: 3000
      })
    }

    await loadNormalizationRules()
    closeRuleDialog()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Save Failed',
      detail: error.message || 'Failed to save normalization rule',
      life: 5000
    })
  }
}

const deleteRule = async (rule) => {
  if (!window.confirm(`Delete mapping "${formatRuleSource(rule)} → ${formatRuleTarget(rule)}"?`)) {
    return
  }

  try {
    await geocodingStore.deleteNormalizationRule(rule.id)
    toast.add({
      severity: 'success',
      summary: 'Rule Deleted',
      detail: 'Normalization rule deleted successfully',
      life: 3000
    })
    await loadNormalizationRules()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Delete Failed',
      detail: error.message || 'Failed to delete normalization rule',
      life: 5000
    })
  }
}

const applyRulesNow = async () => {
  if (!canApplyRules.value) return

  try {
    activeJobMode.value = 'normalization'
    const isSingleRuleApply = !!selectedApplyRule.value?.id
    const payload = {
      applyToGeocoding: applyRulesForm.value.applyToGeocoding,
      applyToFavorites: applyRulesForm.value.applyToFavorites
    }
    const result = isSingleRuleApply
      ? await geocodingStore.applySingleNormalizationRule(selectedApplyRule.value.id, payload)
      : await geocodingStore.applyNormalizationRules(payload)
    closeApplyRulesDialog()
    toast.add({
      severity: 'info',
      summary: 'Normalization Started',
      detail: isSingleRuleApply
        ? 'Applying selected rule in background...'
        : 'Applying rules in background...',
      life: 2500
    })
    await startPolling(result.jobId)
  } catch (error) {
    activeJobMode.value = null
    toast.add({
      severity: 'error',
      summary: 'Apply Failed',
      detail: error.message || 'Failed to start normalization apply job',
      life: 5000
    })
  }
}

const viewDetails = (result) => {
  router.push(`/app/place-details/geocoding/${result.id}`)
}

const editResult = (result) => {
  selectedResult.value = result
  showEditDialog.value = true
}

const reconcileResult = (result) => {
  selectedResult.value = result
  selectedRows.value = []
  showReconcileDialog.value = true
}

const bulkEditSelected = () => {
  showBulkEditDialog.value = true
}

const handleBulkEditSave = async () => {
  // Dialog handles the save, we just need to refresh and clear selection
  selectedRows.value = []
  await loadGeocodingResults()
}

const reconcileSelected = () => {
  selectedResult.value = null
  reconcileMode.value = 'selected'
  showReconcileDialog.value = true
}

const reconcileAll = () => {
  selectedResult.value = null
  selectedRows.value = []
  reconcileMode.value = 'all'
  showReconcileDialog.value = true
}

const handleEditSave = async (updatedData) => {
  if (!selectedResult.value) return

  try {
    await geocodingStore.updateGeocodingResult(selectedResult.value.id, updatedData)

    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: 'Geocoding result updated successfully',
      life: 3000
    })

    await loadGeocodingResults()

  } catch (error) {
    console.error('Error updating geocoding result:', error)
    toast.add({
      severity: 'error',
      summary: 'Update Failed',
      detail: error.message || 'Failed to update geocoding result',
      life: 5000
    })
  } finally {
    showEditDialog.value = false
    selectedResult.value = null
  }
}

const handleReconcile = async (reconcileData) => {
  try {
    activeJobMode.value = 'reconcile'
    // Start bulk reconciliation job
    const result = await geocodingStore.startBulkReconciliation(reconcileData)
    const jobId = result.jobId

    // Start polling for progress
    await startPolling(jobId)

    // Note: Dialog will auto-close when job completes
    // The reconcile-complete event will trigger data reload

  } catch (error) {
    activeJobMode.value = null
    console.error('Error starting reconciliation:', error)
    toast.add({
      severity: 'error',
      summary: 'Reconciliation Failed',
      detail: error.message || 'Failed to start reconciliation',
      life: 5000
    })
    showReconcileDialog.value = false
    selectedResult.value = null
  }
}

const handleReconcileComplete = async () => {
  // Called when reconciliation completes successfully
  const progress = jobProgress.value

  const successMsg = `Successfully reconciled ${progress.successCount} of ${progress.totalItems} results`
  const severity = progress.failedCount > 0 ? 'warn' : 'success'

  toast.add({
    severity: severity,
    summary: 'Reconciliation Complete',
    detail: successMsg,
    life: 5000
  })

  // Reset state
  selectedRows.value = []
  resetProgress()

  // Reload data
  await loadGeocodingResults()

  // Close dialog
  showReconcileDialog.value = false
  selectedResult.value = null
  activeJobMode.value = null
}

const handleNormalizationJobComplete = async () => {
  const progress = jobProgress.value
  const metadata = progress?.metadata || {}
  const geocodingUpdated = metadata.geocodingUpdated || 0
  const favoritesUpdated = metadata.favoritesUpdated || 0
  const geocodingFailed = metadata.geocodingFailed || 0
  const favoritesFailed = metadata.favoritesFailed || 0

  const detail = `Updated ${geocodingUpdated} geocoding and ${favoritesUpdated} favorites` +
      (geocodingFailed + favoritesFailed > 0 ? ` (${geocodingFailed + favoritesFailed} failed)` : '')

  toast.add({
    severity: geocodingFailed + favoritesFailed > 0 ? 'warn' : 'success',
    summary: 'Normalization Apply Complete',
    detail,
    life: 6000
  })

  activeJobMode.value = null
  resetProgress()
  await Promise.all([
    loadGeocodingResults(),
    loadNormalizationRules()
  ])
}

// Lifecycle
onMounted(async () => {
  handleResize()
  window.addEventListener('resize', handleResize)

  await Promise.all([
    loadGeocodingResults(),
    loadEnabledProviders(),
    loadAvailableProviders(),
    loadNormalizationRules(),
    loadCombinedDistinctValues()
  ])
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (searchTimeout) clearTimeout(searchTimeout)
})

// Watchers
watch(pageSize, async () => {
  currentPage.value = 0
  await loadGeocodingResults()
})

watch(() => ruleForm.value.ruleType, (ruleType) => {
  if (ruleType === 'COUNTRY') {
    ruleForm.value.sourceCity = ''
    ruleForm.value.targetCity = ''
  } else {
    ruleForm.value.sourceCountry = ''
    ruleForm.value.targetCountry = ''
  }
})

watch(showApplyRulesDialog, (visible) => {
  if (!visible) {
    selectedApplyRule.value = null
  }
})

watch(() => jobProgress.value?.status, async (status) => {
  if (activeJobMode.value !== 'normalization') return
  if (status !== 'COMPLETED' && status !== 'FAILED') return
  await handleNormalizationJobComplete()
})
</script>

<style scoped>
/* Ensure all elements respect parent width */
* {
  box-sizing: border-box;
}

/* Filter Section */
.filter-section {
  margin-bottom: var(--gp-spacing-lg);
  max-width: 100%;
  overflow: hidden;
}

.provider-switch-info {
  margin-bottom: var(--gp-spacing-lg);
  display: flex;
  align-items: flex-start;
  gap: var(--gp-spacing-sm);
  color: var(--gp-text-secondary);
}

.provider-switch-info i {
  margin-top: 0.1rem;
  color: var(--gp-primary);
}

.normalization-rules-section {
  margin-bottom: var(--gp-spacing-lg);
}

.normalization-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--gp-spacing-md);
  margin-bottom: var(--gp-spacing-md);
}

.normalization-title {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.normalization-subtitle {
  margin: 0.35rem 0 0;
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
}

.normalization-actions {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  flex-wrap: wrap;
}

.normalization-empty {
  color: var(--gp-text-secondary);
  font-style: italic;
}

.normalization-collapsed-hint {
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
}

.rule-dialog-content {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.field-row {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.rule-input {
  width: 100%;
}

.checkbox-row {
  flex-direction: row;
  align-items: center;
  gap: 0.5rem;
}

.filter-controls {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-lg);
  flex-wrap: wrap;
  max-width: 100%;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  flex: 1;
  min-width: 200px;
}

.filter-label {
  font-weight: 500;
  color: var(--gp-text-secondary);
  white-space: nowrap;
}

.provider-select {
  flex: 1;
  max-width: 250px;
}

.search-input {
  flex: 1;
  max-width: 400px;
}

/* Table Section */
.table-section {
  overflow: hidden;
  max-width: 100%;
}

.table-header {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.table-title {
  font-weight: 600;
  font-size: 1.1rem;
  color: var(--gp-text-primary);
}

/* Table Layout - Fixed */
.geocoding-table {
  max-width: 100%;
  box-sizing: border-box;
}

.geocoding-table :deep(.p-datatable-table) {
  table-layout: fixed;
  width: 100%;
}

.geocoding-table :deep(.p-datatable-wrapper) {
  max-width: 100%;
  box-sizing: border-box;
  overflow-x: auto;
}

.geocoding-table :deep(.p-datatable) {
  max-width: 100%;
  box-sizing: border-box;
}

/* Table Columns - Fixed Widths */
.geocoding-table :deep(.selection-col) {
  width: 3rem;
  max-width: 3rem;
}

.geocoding-table :deep(.name-col) {
  width: 25%;
}

.geocoding-table :deep(.city-col) {
  width: 15%;
}

.geocoding-table :deep(.country-col) {
  width: 12%;
}

.geocoding-table :deep(.provider-col) {
  width: 10%;
}

.geocoding-table :deep(.coordinates-col) {
  width: 15%;
}

.geocoding-table :deep(.date-col) {
  width: 13%;
}

.geocoding-table :deep(.actions-col) {
  width: 7%;
}

/* Cell Content */
.name-cell {
  font-weight: 500;
  color: var(--gp-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.coordinates-cell {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.coordinate-line {
  font-family: monospace;
  font-size: 0.8rem;
  color: var(--gp-text-primary);
}

.null-value {
  color: var(--gp-text-muted);
  font-style: italic;
}

.provider-tag {
  font-size: 0.75rem;
}

/* Empty State */
.empty-state {
  text-align: center;
  padding: var(--gp-spacing-xxl) var(--gp-spacing-lg);
}

.empty-icon {
  font-size: 3rem;
  color: var(--gp-text-muted);
  margin-bottom: var(--gp-spacing-lg);
  display: block;
}

.empty-state h3 {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--gp-text-secondary);
  margin: 0 0 var(--gp-spacing-md);
}

.empty-state p {
  color: var(--gp-text-muted);
  margin: 0;
}

/* Actions Column */
.actions-buttons {
  display: flex;
  gap: var(--gp-spacing-xs);
  justify-content: center;
  align-items: center;
}

.action-button {
  min-width: 32px !important;
  width: 32px !important;
  height: 32px !important;
  padding: 0 !important;
  border-radius: var(--gp-radius-small);
  transition: all 0.2s ease;
}

.edit-button:hover {
  background-color: var(--gp-primary-light) !important;
  color: var(--gp-primary) !important;
}

.reconcile-button:hover {
  background-color: var(--p-cyan-50) !important;
  color: var(--p-cyan-600) !important;
}

.view-button:hover {
  background-color: var(--gp-primary-light) !important;
  color: var(--gp-primary) !important;
}

/* Header Actions */
.header-actions {
  display: flex;
  gap: var(--gp-spacing-md);
  align-items: center;
}

.bulk-action-button {
  animation: fadeInScale 0.2s ease;
}

@keyframes fadeInScale {
  from {
    opacity: 0;
    transform: scale(0.9);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

/* Responsive Design */
@media (max-width: 768px) {
  .filter-section {
    margin-bottom: var(--gp-spacing-md);
  }

  .normalization-header {
    flex-direction: column;
  }

  .normalization-actions {
    width: 100%;
  }

  .normalization-actions .p-button {
    width: 100%;
    justify-content: center;
  }

  .filter-controls {
    flex-direction: column;
    align-items: stretch;
    gap: var(--gp-spacing-sm);
  }

  .filter-group {
    flex-direction: column;
    align-items: stretch;
    min-width: unset;
  }

  .filter-label {
    font-size: 0.875rem;
  }

  .provider-select,
  .search-input {
    max-width: 100%;
    width: 100%;
  }

  .header-actions {
    flex-direction: column;
    width: 100%;
  }

  .header-actions .p-button {
    width: 100%;
    justify-content: center;
  }

  .table-section {
    overflow-x: auto;
  }

  .table-title {
    font-size: 1rem;
  }

  /* Adjust table column widths for mobile */
  .geocoding-table :deep(.selection-col) {
    width: 2.5rem;
    max-width: 2.5rem;
  }

  .geocoding-table :deep(.name-col) {
    width: 50%;
  }

  .geocoding-table :deep(.city-col) {
    width: 30%;
  }

  .geocoding-table :deep(.actions-col) {
    width: 20%;
  }

  /* Reduce table padding and font sizes */
  .geocoding-table :deep(.p-datatable-thead th) {
    font-size: 0.85rem;
    padding: var(--gp-spacing-xs);
  }

  .geocoding-table :deep(.p-datatable-tbody td) {
    padding: var(--gp-spacing-xs);
    font-size: 0.875rem;
  }

  .name-cell {
    font-size: 0.875rem;
  }

  .provider-tag {
    font-size: 0.7rem;
  }

  .action-button {
    min-width: 28px !important;
    width: 28px !important;
    height: 28px !important;
  }

  .actions-buttons {
    gap: 2px;
  }
}

@media (max-width: 480px) {
  .filter-section {
    padding: var(--gp-spacing-sm);
  }

  .table-section {
    padding: var(--gp-spacing-sm);
  }

  .geocoding-table :deep(.name-col) {
    width: 55%;
  }

  .geocoding-table :deep(.city-col) {
    width: 25%;
  }

  .geocoding-table :deep(.actions-col) {
    width: 20%;
  }

  .geocoding-table :deep(.p-datatable-thead th) {
    font-size: 0.75rem;
    padding: var(--gp-spacing-xs) 4px;
  }

  .geocoding-table :deep(.p-datatable-tbody td) {
    padding: var(--gp-spacing-xs) 4px;
    font-size: 0.8rem;
  }

  .name-cell {
    font-size: 0.8rem;
  }

  .action-button {
    min-width: 24px !important;
    width: 24px !important;
    height: 24px !important;
  }

  .actions-buttons {
    gap: 1px;
  }
}

/* Dark Mode */
.p-dark .geocoding-table :deep(.p-datatable) {
  background: var(--gp-surface-dark) !important;
  color: var(--gp-text-primary) !important;
}

.p-dark .geocoding-table :deep(.p-datatable-header) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .geocoding-table :deep(.p-datatable-tbody > tr) {
  background: var(--gp-surface-dark) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .geocoding-table :deep(.p-datatable-tbody > tr:hover) {
  background: var(--gp-surface-light) !important;
}

.p-dark .geocoding-table :deep(.p-datatable-tbody > tr > td) {
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .geocoding-table :deep(.p-datatable-thead > tr > th) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .geocoding-table :deep(.p-datatable-wrapper) {
  background: var(--gp-surface-dark) !important;
}

.p-dark .geocoding-table :deep(.p-paginator) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}
</style>
