<template>
  <AppLayout variant="default">
    <PageContainer
      title="Timeline Labels"
      :subtitle="pageSubtitle"
      :loading="isLoading"
      variant="fullwidth"
    >
      <template #actions>
        <Button
          label="Create Label"
          icon="pi pi-plus"
          @click="showCreateDialog = true"
          class="gp-btn-primary"
        />
      </template>

      <Message
        v-if="showLabelsHelpMessage"
        severity="info"
        :closable="true"
        style="margin-bottom: var(--gp-spacing-md)"
        @close="dismissLabelsHelpMessage"
      >
        Timeline Labels mark time ranges on your timeline (vacation, trip, event).
        Optionally link a label to a Trip Plan for places, progress, and visit tracking.
      </Message>

      <!-- Active Tag Banner (Compact) -->
      <Message v-if="activeTag" severity="info" :closable="false" style="margin-bottom: var(--gp-spacing-md)">
        <div class="active-tag-banner">
          <i class="pi pi-tag"></i>
          <strong>Active Label:</strong>
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
              placeholder="Search labels..."
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
            <Select
              v-model="selectedLinkState"
              :options="linkStateOptions"
              optionLabel="label"
              optionValue="value"
              placeholder="All Links"
              class="link-select"
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
          currentPageReportTemplate="Showing {first} to {last} of {totalRecords} labels"
          stripedRows
          v-model:selection="selectedTags"
          :selectAll="selectAll"
          @select-all-change="onSelectAllChange"
          @row-select="onRowSelect"
          @row-unselect="onRowUnselect"
          class="desktop-table"
        >
          <Column selectionMode="multiple" headerStyle="width: 3rem" />

          <Column field="tagName" header="Label" sortable>
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

          <Column header="Trip Plan" style="width: 12rem">
            <template #body="{ data }">
              <Button
                v-if="isLinkedToTrip(data)"
                icon="pi pi-briefcase"
                class="p-button-text p-button-sm"
                @click="openLinkedWorkspace(data)"
                v-tooltip.top="'Open linked trip planner'"
                style="color: var(--gp-primary)"
              />
              <span v-else class="gp-text-secondary">—</span>
            </template>
          </Column>

          <Column header="Actions" style="width: 14rem">
            <template #body="{ data }">
              <div class="actions-inline-row">
                <Button
                  icon="pi pi-calendar"
                  class="p-button-text p-button-sm"
                  @click="viewTimeline(data)"
                  v-tooltip.top="'View timeline'"
                  style="color: var(--gp-primary)"
                />
                <Button
                  icon="pi pi-briefcase"
                  class="p-button-text p-button-sm"
                  @click="createTripWorkspace(data)"
                  v-tooltip.top="'Create trip plan'"
                  :disabled="!canCreateTripWorkspace(data)"
                  style="color: var(--gp-text-secondary)"
                />
                <Button
                  v-if="isLinkedToTrip(data)"
                  icon="pi pi-link"
                  class="p-button-text p-button-sm"
                  @click="unlinkTagFromTrip(data)"
                  v-tooltip.top="'Unlink from trip plan'"
                  style="color: var(--gp-text-primary)"
                />
                <Button
                  icon="pi pi-pencil"
                  class="p-button-text p-button-sm"
                  @click="editTag(data)"
                  v-tooltip.top="getEditTooltip(data)"
                  :disabled="!canEditFromLabelsPage(data)"
                  style="color: var(--gp-secondary)"
                />
                <Button
                  icon="pi pi-trash"
                  class="p-button-text p-button-sm"
                  @click="deleteTag(data)"
                  v-tooltip.top="getDeleteTooltip(data)"
                  :disabled="!canDeleteFromLabelsPage(data)"
                  style="color: var(--gp-danger)"
                />
              </div>
            </template>
          </Column>

          <template #empty>
            <div style="text-align: center; padding: var(--gp-spacing-xl); color: var(--gp-text-secondary)">
              <i class="pi pi-calendar" style="font-size: 3rem; margin-bottom: var(--gp-spacing-md)"></i>
              <p>No timeline labels found. Create your first one to get started!</p>
            </div>
          </template>
        </DataTable>

        <!-- Mobile Card View -->
        <div class="mobile-cards">
          <div v-if="filteredPeriodTags.length === 0" class="empty-state">
            <i class="pi pi-calendar"></i>
            <p>No timeline labels found. Create your first one to get started!</p>
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
                  :style="{ backgroundColor: tag.color || 'var(--gp-primary)' }"
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

              <div class="card-info-row" v-if="isLinkedToTrip(tag)">
                <div class="info-item">
                  <i class="pi pi-briefcase"></i>
                  <span class="info-label">Linked Trip Plan:</span>
                  <span class="info-value">{{ getLinkedTrip(tag)?.name }}</span>
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
                :label="isLinkedToTrip(tag) ? 'Open Plan' : 'Create Plan'"
                icon="pi pi-briefcase"
                size="small"
                @click="isLinkedToTrip(tag) ? openLinkedWorkspace(tag) : createTripWorkspace(tag)"
                :disabled="!isLinkedToTrip(tag) && !canCreateTripWorkspace(tag)"
                outlined
              />
              <Button
                v-if="isLinkedToTrip(tag)"
                label="Unlink"
                icon="pi pi-link"
                size="small"
                @click="unlinkTagFromTrip(tag)"
                outlined
              />
              <Button
                label="Edit"
                icon="pi pi-pencil"
                size="small"
                @click="editTag(tag)"
                :disabled="!canEditFromLabelsPage(tag)"
                outlined
              />
              <Button
                icon="pi pi-trash"
                size="small"
                @click="deleteTag(tag)"
                :disabled="!canDeleteFromLabelsPage(tag)"
                severity="danger"
                outlined
              />
            </div>
          </div>

          <!-- Mobile Pagination (if needed) -->
          <div v-if="filteredPeriodTags.length > 10" class="mobile-pagination">
            <p class="gp-text-secondary" style="text-align: center; margin: var(--gp-spacing-md) 0">
              Showing {{ filteredPeriodTags.length }} labels
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

    <Dialog
      v-model:visible="showLinkedDeleteDialog"
      modal
      header="Delete Linked Timeline Label"
      class="gp-dialog-md"
      @hide="linkedDeleteTarget = null"
    >
      <div class="from-tag-dialog-content">
        <p class="gp-text-secondary">
          This label is linked to trip plan
          <strong>"{{ linkedDeleteTargetTripName }}"</strong>.
        </p>
        <p class="gp-text-secondary">
          Choose what to delete:
        </p>
      </div>

      <template #footer>
        <Button
          label="Cancel"
          icon="pi pi-times"
          outlined
          @click="showLinkedDeleteDialog = false"
        />
        <Button
          label="Delete Label Only"
          icon="pi pi-trash"
          severity="warn"
          :loading="isDeletingLinkedTag"
          @click="deleteLinkedTag('unlink_only')"
        />
        <Button
          label="Delete Label + Trip Plan"
          icon="pi pi-trash"
          severity="danger"
          :loading="isDeletingLinkedTag"
          @click="deleteLinkedTag('delete_both')"
        />
      </template>
    </Dialog>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { usePeriodTagsStore } from '@/stores/periodTags'
import { useTripsStore } from '@/stores/trips'
import { useTimezone } from '@/composables/useTimezone'
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
import Dialog from 'primevue/dialog'

const router = useRouter()
const toast = useToast()
const confirm = useConfirm()
const store = usePeriodTagsStore()
const tripsStore = useTripsStore()
const timezone = useTimezone()
const LABELS_HELP_DISMISSED_KEY = 'gp.timeline-labels.help.dismissed'

// State
const isLoading = ref(false)
const showCreateDialog = ref(false)
const showEditDialog = ref(false)
const editingTag = ref(null)
const selectedTags = ref([])
const selectAll = ref(false)
const searchTerm = ref('')
const selectedSource = ref(null)
const selectedLinkState = ref('all')
const showLabelsHelpMessage = ref(true)
const showLinkedDeleteDialog = ref(false)
const linkedDeleteTarget = ref(null)
const isDeletingLinkedTag = ref(false)

// Source options for filter
const sourceOptions = [
  { label: 'All Sources', value: null },
  { label: 'Manual', value: 'manual' },
  { label: 'OwnTracks', value: 'owntracks' }
]

const linkStateOptions = [
  { label: 'All Links', value: 'all' },
  { label: 'Linked to Trip Plan', value: 'linked' },
  { label: 'Not Linked', value: 'unlinked' }
]

// Computed
const activeTag = computed(() => store.getActiveTag)
const totalCount = computed(() => store.totalCount)
const totalDays = computed(() => store.getTotalDaysTagged)

const pageSubtitle = computed(() => {
  const parts = []
  if (totalCount.value > 0) parts.push(`${totalCount.value} labels`)
  if (totalDays.value > 0) parts.push(`${totalDays.value} days tagged`)
  return parts.join(' • ') || 'Advanced timeline labels (used by timeline chips and imports)'
})

const filteredPeriodTags = computed(() => {
  store.setFilters({
    searchTerm: searchTerm.value,
    source: selectedSource.value
  })

  let filtered = store.getFilteredPeriodTags

  if (selectedLinkState.value === 'linked') {
    filtered = filtered.filter((tag) => isLinkedToTrip(tag))
  }

  if (selectedLinkState.value === 'unlinked') {
    filtered = filtered.filter((tag) => !isLinkedToTrip(tag))
  }

  return filtered
})

const linkedDeleteTargetTripName = computed(() => {
  if (!linkedDeleteTarget.value) return 'Unknown'
  return getLinkedTrip(linkedDeleteTarget.value)?.name || 'Trip Plan'
})

const canEditFromLabelsPage = (tag) => {
  return store.isTagEditable(tag)
}

const canDeleteFromLabelsPage = (tag) => {
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
  return timezone.formatDateDisplay(dateString)
}

const formatDateForUrl = (dateString) => {
  return timezone.formatUrlDate(dateString)
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

const getLinkedTrip = (tag) => {
  const trips = Array.isArray(tripsStore.trips) ? tripsStore.trips : []
  return trips.find((trip) => Number(trip.periodTagId) === Number(tag.id)) || null
}

const isLinkedToTrip = (tag) => {
  return !!getLinkedTrip(tag)
}

const openLinkedWorkspace = (tag) => {
  const linkedTrip = getLinkedTrip(tag)
  if (!linkedTrip?.id) {
    return
  }

  router.push({
    path: `/app/trips/${linkedTrip.id}`,
    query: {
      start: timezone.formatUrlDate(linkedTrip.startTime),
      end: timezone.formatUrlDate(linkedTrip.endTime)
    }
  })
}

const canCreateTripWorkspace = (tag) => {
  return !!tag?.endTime && !isLinkedToTrip(tag)
}

const getEditTooltip = (tag) => {
  if (isLinkedToTrip(tag)) return `Edit timeline label (syncs linked trip plan: ${getLinkedTrip(tag)?.name || 'Trip Plan'})`
  if (tag.source === 'owntracks' && tag.isActive) return 'Active OwnTracks tag cannot be edited'
  return 'Edit'
}

const getDeleteTooltip = (tag) => {
  if (isLinkedToTrip(tag)) return `Delete timeline label (trip plan will be unlinked)`
  if (tag.source === 'owntracks' && tag.isActive) return 'Active OwnTracks tag cannot be deleted'
  return 'Delete'
}

const unlinkTagFromTrip = (tag) => {
  const linkedTrip = getLinkedTrip(tag)
  if (!linkedTrip) return

  confirm.require({
    message: `Unlink timeline label "${tag.tagName}" from trip plan "${linkedTrip.name}"?`,
    header: 'Unlink Timeline Label',
    icon: 'pi pi-exclamation-triangle',
    accept: async () => {
      try {
        await store.unlinkPeriodTagFromTrip(tag.id)
        await tripsStore.fetchTrips()
        toast.add({
          severity: 'success',
          summary: 'Unlinked',
          detail: 'Timeline label and trip plan are now unlinked',
          life: 3000
        })
      } catch (error) {
        toast.add({
          severity: 'error',
          summary: 'Failed to Unlink',
          detail: error.response?.data?.message || error.message || 'Failed to unlink timeline label',
          life: 4000
        })
      }
    }
  })
}

const openLinkedDeleteDialog = (tag) => {
  linkedDeleteTarget.value = tag
  showLinkedDeleteDialog.value = true
}

const deleteLinkedTag = async (mode) => {
  if (!linkedDeleteTarget.value?.id) return

  isDeletingLinkedTag.value = true
  try {
    await store.deletePeriodTag(linkedDeleteTarget.value.id, mode)
    await tripsStore.fetchTrips()
    showLinkedDeleteDialog.value = false
    linkedDeleteTarget.value = null
    toast.add({
      severity: 'success',
      summary: 'Deleted',
      detail: mode === 'delete_both'
        ? 'Timeline label and linked trip plan deleted'
        : 'Timeline label deleted and trip plan unlinked',
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.response?.data?.message || error.message || 'Failed to delete timeline label',
      life: 3500
    })
  } finally {
    isDeletingLinkedTag.value = false
  }
}

const createTripWorkspace = async (tag) => {
  if (!canCreateTripWorkspace(tag)) {
    toast.add({
      severity: 'warn',
      summary: 'Cannot Create Trip Plan',
      detail: 'Only completed timeline labels can be converted to a trip plan.',
      life: 3500
    })
    return
  }

  try {
    const created = await tripsStore.createTripFromPeriodTag(tag.id)
    toast.add({
      severity: 'success',
      summary: 'Trip Plan Created',
      detail: `Trip plan created from "${tag.tagName}"`,
      life: 3000
    })

    if (created?.id) {
      router.push({
        path: `/app/trips/${created.id}`,
        query: {
          start: timezone.formatUrlDate(created.startTime),
          end: timezone.formatUrlDate(created.endTime)
        }
      })
    }
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Cannot Create Trip Plan',
      detail: error.response?.data?.message || error.message || 'Failed to create plan from timeline label',
      life: 5000
    })
  }
}

const editTag = (tag) => {
  if (!store.isTagEditable(tag)) {
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
  if (isLinkedToTrip(tag)) {
    openLinkedDeleteDialog(tag)
    return
  }

  if (!store.isTagEditable(tag)) {
    toast.add({
      severity: 'warn',
      summary: 'Cannot Delete',
      detail: 'Active OwnTracks tags are managed automatically and cannot be deleted',
      life: 3500
    })
    return
  }

  confirm.require({
    message: `Are you sure you want to delete timeline label "${tag.tagName}"?`,
    header: 'Delete Timeline Label',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: async () => {
      try {
        await store.deletePeriodTag(tag.id, 'unlink_only')
        toast.add({
          severity: 'success',
          summary: 'Deleted',
          detail: 'Timeline label deleted successfully',
          life: 3000
        })
      } catch (error) {
        toast.add({
          severity: 'error',
          summary: 'Error',
          detail: error.message || 'Failed to delete timeline label',
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
    message: `Are you sure you want to delete ${selectedTags.value.length} timeline label(s)?`,
    header: 'Delete Timeline Labels',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: async () => {
      try {
        for (const tag of selectedTags.value) {
          await store.deletePeriodTag(tag.id, 'unlink_only')
        }
        selectedTags.value = []
        toast.add({
          severity: 'success',
          summary: 'Deleted',
          detail: 'Timeline labels deleted successfully',
          life: 3000
        })
      } catch (error) {
        toast.add({
          severity: 'error',
          summary: 'Error',
          detail: error.message || 'Failed to delete timeline labels',
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

const dismissLabelsHelpMessage = () => {
  showLabelsHelpMessage.value = false
  try {
    localStorage.setItem(LABELS_HELP_DISMISSED_KEY, '1')
  } catch (error) {
    // ignore storage access failures
  }
}

const loadData = async () => {
  isLoading.value = true
  try {
    await Promise.all([
      store.fetchPeriodTags(),
      store.fetchActiveTag(),
      tripsStore.fetchTrips()
    ])
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load timeline labels',
      life: 3000
    })
  } finally {
    isLoading.value = false
  }
}

// Lifecycle
onMounted(() => {
  try {
    showLabelsHelpMessage.value = localStorage.getItem(LABELS_HELP_DISMISSED_KEY) !== '1'
  } catch (error) {
    showLabelsHelpMessage.value = true
  }
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

.link-select {
  flex: 0 0 auto;
  width: 180px;
  height: 2.5rem;
}

.bulk-actions {
  display: flex;
  justify-content: flex-end;
}

.actions-inline-row {
  display: inline-flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: var(--gp-spacing-xs);
  white-space: nowrap;
}

@media (max-width: 768px) {
  .filters-row {
    margin-bottom: 0;
  }

  .source-select {
    width: 150px;
  }

  .link-select {
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

  .link-select {
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
