<template>
  <AppLayout variant="default">
    <PageContainer
      title="Period Tags"
      :subtitle="pageSubtitle"
      :loading="isLoading"
      variant="fullwidth"
    >
      <template #actions>
        <Button
          label="Create Period Tag"
          icon="pi pi-plus"
          @click="showCreateDialog = true"
          class="gp-btn-primary"
        />
      </template>

      <!-- Active Tag Banner (Compact) -->
      <Message v-if="activeTag" severity="info" :closable="false" style="margin-bottom: var(--gp-spacing-md)">
        <div class="active-tag-banner">
          <i class="pi pi-tag"></i>
          <strong>Active Tag:</strong>
          <span>{{ activeTag.tagName }}</span>
          <Tag severity="success" value="OwnTracks" style="margin-left: var(--gp-spacing-xs)" />
          <span class="gp-text-secondary active-tag-date">
            Since {{ formatDate(activeTag.startTime) }}
          </span>
        </div>
      </Message>

      <!-- Data Table with Integrated Toolbar -->
      <BaseCard>
        <!-- Filters Toolbar -->
        <div class="filters-toolbar">
          <div class="filters-row">
            <InputText
              v-model="searchTerm"
              placeholder="Search tags..."
              class="gp-input search-input"
            >
              <template #prefix>
                <i class="pi pi-search" />
              </template>
            </InputText>
            <Select
              v-model="selectedSource"
              :options="sourceOptions"
              optionLabel="label"
              optionValue="value"
              placeholder="All Sources"
              showClear
              class="source-select"
            />
          </div>
          <div v-if="selectedTags.length > 0" class="bulk-actions">
            <Button
              :label="`Delete (${selectedTags.length})`"
              icon="pi pi-trash"
              severity="danger"
              size="small"
              @click="bulkDelete"
            />
          </div>
        </div>

        <!-- Desktop Table View -->
        <DataTable
          :value="filteredPeriodTags"
          :paginator="true"
          :rows="10"
          :rowsPerPageOptions="[10, 25, 50]"
          paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown CurrentPageReport"
          currentPageReportTemplate="Showing {first} to {last} of {totalRecords} tags"
          stripedRows
          v-model:selection="selectedTags"
          :selectAll="selectAll"
          @select-all-change="onSelectAllChange"
          @row-select="onRowSelect"
          @row-unselect="onRowUnselect"
          class="desktop-table"
        >
          <Column selectionMode="multiple" headerStyle="width: 3rem" />

          <Column field="tagName" header="Tag Name" sortable>
            <template #body="{ data }">
              <div style="display: flex; align-items: center; gap: var(--gp-spacing-xs)">
                <span class="gp-text-primary" style="font-weight: 600">{{ data.tagName }}</span>
                <Tag v-if="data.isActive" severity="success" value="Active" style="font-size: 0.7rem" />
              </div>
            </template>
          </Column>

          <Column field="source" header="Source" sortable style="width: 8rem">
            <template #body="{ data }">
              <Tag :severity="data.source === 'owntracks' ? 'info' : 'secondary'">
                {{ data.source === 'owntracks' ? 'OwnTracks' : 'Manual' }}
              </Tag>
            </template>
          </Column>

          <Column field="startTime" header="Start Date" sortable>
            <template #body="{ data }">
              {{ formatDate(data.startTime) }}
            </template>
          </Column>

          <Column field="endTime" header="End Date" sortable>
            <template #body="{ data }">
              <span v-if="data.endTime">{{ formatDate(data.endTime) }}</span>
              <Tag v-else severity="success" value="Active" />
            </template>
          </Column>

          <Column header="Duration" sortable>
            <template #body="{ data }">
              {{ calculateDuration(data.startTime, data.endTime) }}
            </template>
          </Column>

          <Column header="Actions" style="width: 10rem">
            <template #body="{ data }">
              <Button
                icon="pi pi-calendar"
                class="p-button-text p-button-sm"
                @click="viewTimeline(data)"
                v-tooltip.top="'View timeline'"
                style="color: var(--gp-primary)"
              />
              <Button
                icon="pi pi-pencil"
                class="p-button-text p-button-sm"
                @click="editTag(data)"
                v-tooltip.top="isTagEditable(data) ? 'Edit' : 'Active OwnTracks tag cannot be edited'"
                :disabled="!isTagEditable(data)"
                style="color: var(--gp-secondary)"
              />
              <Button
                icon="pi pi-trash"
                class="p-button-text p-button-sm"
                @click="deleteTag(data)"
                v-tooltip.top="isTagEditable(data) ? 'Delete' : 'Active OwnTracks tag cannot be deleted'"
                :disabled="!isTagEditable(data)"
                style="color: var(--gp-danger)"
              />
            </template>
          </Column>

          <template #empty>
            <div style="text-align: center; padding: var(--gp-spacing-xl); color: var(--gp-text-secondary)">
              <i class="pi pi-calendar" style="font-size: 3rem; margin-bottom: var(--gp-spacing-md)"></i>
              <p>No period tags found. Create your first one to get started!</p>
            </div>
          </template>
        </DataTable>

        <!-- Mobile Card View -->
        <div class="mobile-cards">
          <div v-if="filteredPeriodTags.length === 0" class="empty-state">
            <i class="pi pi-calendar"></i>
            <p>No period tags found. Create your first one to get started!</p>
          </div>

          <div v-for="tag in filteredPeriodTags" :key="tag.id" class="period-tag-card">
            <!-- Card Header -->
            <div class="card-header">
              <div class="card-header-left">
                <input
                  type="checkbox"
                  :checked="selectedTags.includes(tag)"
                  @change="toggleTagSelection(tag)"
                  class="tag-checkbox"
                />
                <div
                  class="color-indicator"
                  :style="{ backgroundColor: tag.color || '#FF6B6B' }"
                ></div>
                <div class="card-title-section">
                  <div class="card-title">{{ tag.tagName }}</div>
                  <div class="card-badges">
                    <Tag v-if="tag.isActive" severity="success" value="Active" style="font-size: 0.65rem" />
                    <Tag
                      :severity="tag.source === 'owntracks' ? 'info' : 'secondary'"
                      style="font-size: 0.65rem"
                    >
                      {{ tag.source === 'owntracks' ? 'OwnTracks' : 'Manual' }}
                    </Tag>
                  </div>
                </div>
              </div>
            </div>

            <!-- Card Body -->
            <div class="card-body">
              <div class="card-info-row">
                <div class="info-item">
                  <i class="pi pi-calendar"></i>
                  <span class="info-label">Start:</span>
                  <span class="info-value">{{ formatDate(tag.startTime) }}</span>
                </div>
              </div>

              <div class="card-info-row">
                <div class="info-item">
                  <i class="pi pi-calendar"></i>
                  <span class="info-label">End:</span>
                  <span class="info-value" v-if="tag.endTime">{{ formatDate(tag.endTime) }}</span>
                  <Tag v-else severity="success" value="Active" style="font-size: 0.65rem" />
                </div>
              </div>

              <div class="card-info-row">
                <div class="info-item">
                  <i class="pi pi-clock"></i>
                  <span class="info-label">Duration:</span>
                  <span class="info-value">{{ calculateDuration(tag.startTime, tag.endTime) }}</span>
                </div>
              </div>
            </div>

            <!-- Card Actions -->
            <div class="card-actions">
              <Button
                label="Timeline"
                icon="pi pi-calendar"
                size="small"
                @click="viewTimeline(tag)"
                outlined
              />
              <Button
                label="Edit"
                icon="pi pi-pencil"
                size="small"
                @click="editTag(tag)"
                :disabled="!isTagEditable(tag)"
                outlined
              />
              <Button
                icon="pi pi-trash"
                size="small"
                @click="deleteTag(tag)"
                :disabled="!isTagEditable(tag)"
                severity="danger"
                outlined
              />
            </div>
          </div>

          <!-- Mobile Pagination (if needed) -->
          <div v-if="filteredPeriodTags.length > 10" class="mobile-pagination">
            <p class="gp-text-secondary" style="text-align: center; margin: var(--gp-spacing-md) 0">
              Showing {{ filteredPeriodTags.length }} tags
            </p>
          </div>
        </div>
      </BaseCard>
    </PageContainer>

    <!-- Dialogs -->
    <CreatePeriodTagDialog
      v-model:visible="showCreateDialog"
      @created="onPeriodTagCreated"
    />

    <EditPeriodTagDialog
      v-model:visible="showEditDialog"
      :periodTag="editingTag"
      @updated="onPeriodTagUpdated"
    />

    <ConfirmDialog />
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { usePeriodTagsStore } from '@/stores/periodTags'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import CreatePeriodTagDialog from '@/components/dialogs/CreatePeriodTagDialog.vue'
import EditPeriodTagDialog from '@/components/dialogs/EditPeriodTagDialog.vue'
import Button from 'primevue/button'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import Message from 'primevue/message'
import ConfirmDialog from 'primevue/confirmdialog'

const router = useRouter()
const toast = useToast()
const confirm = useConfirm()
const store = usePeriodTagsStore()

// State
const isLoading = ref(false)
const showCreateDialog = ref(false)
const showEditDialog = ref(false)
const editingTag = ref(null)
const selectedTags = ref([])
const selectAll = ref(false)
const searchTerm = ref('')
const selectedSource = ref(null)

// Source options for filter
const sourceOptions = [
  { label: 'All Sources', value: null },
  { label: 'Manual', value: 'manual' },
  { label: 'OwnTracks', value: 'owntracks' }
]

// Computed
const activeTag = computed(() => store.getActiveTag)
const totalCount = computed(() => store.totalCount)
const totalDays = computed(() => store.getTotalDaysTagged)

const pageSubtitle = computed(() => {
  const parts = []
  if (totalCount.value > 0) parts.push(`${totalCount.value} periods`)
  if (totalDays.value > 0) parts.push(`${totalDays.value} days tagged`)
  return parts.join(' • ') || 'Manage your trips, vacations, and other tagged time periods'
})

const filteredPeriodTags = computed(() => {
  store.setFilters({
    searchTerm: searchTerm.value,
    source: selectedSource.value
  })
  return store.getFilteredPeriodTags
})

const isTagEditable = (tag) => {
  return store.isTagEditable(tag)
}

// Methods
const toggleTagSelection = (tag) => {
  const index = selectedTags.value.findIndex(t => t.id === tag.id)
  if (index > -1) {
    selectedTags.value.splice(index, 1)
  } else {
    selectedTags.value.push(tag)
  }
}

const formatDate = (dateString) => {
  if (!dateString) return '—'
  const date = new Date(dateString)
  return date.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })
}

const formatDateForUrl = (dateString) => {
  const date = new Date(dateString)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const year = date.getFullYear()
  return `${month}/${day}/${year}`
}

const calculateDuration = (startTime, endTime) => {
  const start = new Date(startTime)
  const end = endTime ? new Date(endTime) : new Date()
  const days = Math.ceil((end - start) / (1000 * 60 * 60 * 24))
  return days === 1 ? '1 day' : `${days} days`
}

const viewTimeline = (tag) => {
  const startDate = formatDateForUrl(tag.startTime)
  const endDate = tag.endTime ? formatDateForUrl(tag.endTime) : formatDateForUrl(new Date())

  router.push({
    path: '/app/timeline',
    query: {
      start: startDate,
      end: endDate
    }
  })
}

const editTag = (tag) => {
  if (!isTagEditable(tag)) {
    toast.add({
      severity: 'warn',
      summary: 'Cannot Edit',
      detail: 'Active OwnTracks tags are managed automatically and cannot be edited',
      life: 3000
    })
    return
  }
  editingTag.value = tag
  showEditDialog.value = true
}

const deleteTag = (tag) => {
  confirm.require({
    message: `Are you sure you want to delete "${tag.tagName}"?`,
    header: 'Delete Period Tag',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: async () => {
      try {
        await store.deletePeriodTag(tag.id)
        toast.add({
          severity: 'success',
          summary: 'Deleted',
          detail: 'Period tag deleted successfully',
          life: 3000
        })
      } catch (error) {
        toast.add({
          severity: 'error',
          summary: 'Error',
          detail: error.message || 'Failed to delete period tag',
          life: 3000
        })
      }
    }
  })
}

const bulkDelete = () => {
  // Check if any selected tags are active OwnTracks tags
  const activeOwnTracksTags = selectedTags.value.filter(tag =>
    tag.source === 'owntracks' && tag.isActive === true
  )

  if (activeOwnTracksTags.length > 0) {
    toast.add({
      severity: 'warn',
      summary: 'Cannot Delete Active Tags',
      detail: `${activeOwnTracksTags.length} active OwnTracks tag(s) cannot be deleted. Please deselect them or wait until they're completed.`,
      life: 5000
    })
    return
  }

  confirm.require({
    message: `Are you sure you want to delete ${selectedTags.value.length} period tag(s)?`,
    header: 'Delete Period Tags',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: async () => {
      try {
        for (const tag of selectedTags.value) {
          await store.deletePeriodTag(tag.id)
        }
        selectedTags.value = []
        toast.add({
          severity: 'success',
          summary: 'Deleted',
          detail: 'Period tags deleted successfully',
          life: 3000
        })
      } catch (error) {
        toast.add({
          severity: 'error',
          summary: 'Error',
          detail: error.message || 'Failed to delete period tags',
          life: 3000
        })
      }
    }
  })
}

const onSelectAllChange = (event) => {
  selectAll.value = event.checked
}

const onRowSelect = () => {
  selectAll.value = selectedTags.value.length === filteredPeriodTags.value.length
}

const onRowUnselect = () => {
  selectAll.value = false
}

const onPeriodTagCreated = () => {
  showCreateDialog.value = false
  loadData()
}

const onPeriodTagUpdated = () => {
  showEditDialog.value = false
  loadData()
}

const loadData = async () => {
  isLoading.value = true
  try {
    await Promise.all([
      store.fetchPeriodTags(),
      store.fetchActiveTag()
    ])
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load period tags',
      life: 3000
    })
  } finally {
    isLoading.value = false
  }
}

// Lifecycle
onMounted(() => {
  loadData()
})
</script>

<style scoped>
/* Active Tag Banner */
.active-tag-banner {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .active-tag-date {
    flex-basis: 100%;
    margin-left: 0 !important;
    margin-top: var(--gp-spacing-xs);
  }
}

/* Filters Toolbar */
.filters-toolbar {
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
  padding: var(--gp-spacing-md);
  margin-bottom: var(--gp-spacing-md);
}

.p-dark .filters-toolbar {
  background: var(--gp-surface-dark);
}

.filters-row {
  display: flex;
  gap: var(--gp-spacing-sm);
  align-items: center;
  margin-bottom: var(--gp-spacing-sm);
}

.search-input {
  flex: 1;
  min-width: 0;
  height: 2.5rem;
}

.source-select {
  flex: 0 0 auto;
  width: 180px;
  height: 2.5rem;
}

.bulk-actions {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .filters-row {
    margin-bottom: 0;
  }

  .source-select {
    width: 150px;
  }

  .bulk-actions {
    margin-top: var(--gp-spacing-sm);
  }
}

@media (max-width: 480px) {
  .source-select {
    width: 130px;
  }
}

/* Desktop Table - hidden on mobile */
.desktop-table {
  display: block;
}

@media (max-width: 768px) {
  .desktop-table {
    display: none;
  }
}

/* Mobile Cards - hidden on desktop */
.mobile-cards {
  display: none;
}

@media (max-width: 768px) {
  .mobile-cards {
    display: block;
  }
}

/* Empty State */
.empty-state {
  text-align: center;
  padding: var(--gp-spacing-xl);
  color: var(--gp-text-secondary);
}

.empty-state i {
  font-size: 3rem;
  margin-bottom: var(--gp-spacing-md);
  display: block;
}

/* Period Tag Card */
.period-tag-card {
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-medium);
  padding: var(--gp-spacing-md);
  margin-bottom: var(--gp-spacing-md);
}

.p-dark .period-tag-card {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

/* Card Header */
.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: var(--gp-spacing-sm);
}

.card-header-left {
  display: flex;
  align-items: flex-start;
  gap: var(--gp-spacing-sm);
  flex: 1;
}

.tag-checkbox {
  margin-top: 2px;
  width: 18px;
  height: 18px;
  cursor: pointer;
  flex-shrink: 0;
}

.color-indicator {
  width: 4px;
  height: 100%;
  min-height: 40px;
  border-radius: var(--gp-radius-small);
  flex-shrink: 0;
}

.card-title-section {
  flex: 1;
  min-width: 0;
}

.card-title {
  font-weight: 600;
  font-size: 1rem;
  color: var(--gp-text-primary);
  margin-bottom: var(--gp-spacing-xs);
  word-break: break-word;
}

.card-badges {
  display: flex;
  gap: var(--gp-spacing-xs);
  flex-wrap: wrap;
}

/* Card Body */
.card-body {
  margin-bottom: var(--gp-spacing-md);
  padding-left: calc(18px + var(--gp-spacing-sm) + 4px + var(--gp-spacing-sm));
}

.card-info-row {
  display: flex;
  align-items: center;
  margin-bottom: var(--gp-spacing-xs);
}

.card-info-row:last-child {
  margin-bottom: 0;
}

.info-item {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
  font-size: 0.875rem;
}

.info-item i {
  color: var(--gp-text-secondary);
  font-size: 0.75rem;
}

.info-label {
  color: var(--gp-text-secondary);
  font-weight: 500;
}

.info-value {
  color: var(--gp-text-primary);
}

/* Card Actions */
.card-actions {
  display: flex;
  gap: var(--gp-spacing-sm);
  padding-left: calc(18px + var(--gp-spacing-sm) + 4px + var(--gp-spacing-sm));
  flex-wrap: wrap;
}

.card-actions button {
  flex: 1;
  min-width: 80px;
  justify-content: center;
}

@media (max-width: 480px) {
  .card-actions {
    padding-left: 0;
  }

  .card-actions button {
    flex: 1 1 100%;
  }
}

/* Mobile Pagination */
.mobile-pagination {
  margin-top: var(--gp-spacing-md);
}
</style>
