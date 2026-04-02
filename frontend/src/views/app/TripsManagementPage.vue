<template>
  <AppLayout variant="default">
    <PageContainer
      title="Trip Plans"
      :subtitle="pageSubtitle"
      :loading="tripsStore.loading.trips"
      variant="fullwidth"
    >
      <template #actions>
        <Button
          label="From Timeline Label"
          icon="pi pi-tag"
          outlined
          @click="openFromPeriodTagDialog"
        />
        <Button
          label="Create Trip Plan"
          icon="pi pi-plus"
          class="gp-btn-primary"
          @click="openCreateDialog"
        />
      </template>

      <Message
        v-if="showTripPlansHelpMessage"
        severity="info"
        :closable="true"
        style="margin-bottom: var(--gp-spacing-md)"
        @close="dismissTripPlansHelpMessage"
      >
        Trip Plans are planning workspaces for places, progress, and visit tracking.
      </Message>

      <BaseCard>
        <div class="trips-toolbar">
          <div class="trips-filters">
            <InputText
              v-model="searchTerm"
              placeholder="Search trips..."
              class="gp-input search-input"
            />
            <Select
              v-model="statusFilter"
              :options="statusOptions"
              optionLabel="label"
              optionValue="value"
              class="status-select"
            />
            <Select
              v-model="accessFilter"
              :options="accessOptions"
              optionLabel="label"
              optionValue="value"
              class="status-select"
            />
          </div>

          <Button
            icon="pi pi-refresh"
            label="Refresh"
            outlined
            @click="refreshTrips"
          />
        </div>

        <DataTable
          :value="filteredTrips"
          :paginator="true"
          :rows="10"
          :rowsPerPageOptions="[10, 25, 50]"
          paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown CurrentPageReport"
          currentPageReportTemplate="Showing {first} to {last} of {totalRecords} plans"
          stripedRows
        >
          <Column field="name" header="Trip Plan" sortable>
            <template #body="{ data }">
              <div class="trip-name-cell">
                <span class="trip-color-dot" :style="{ backgroundColor: data.color || 'var(--gp-primary)' }"></span>
                <div class="trip-name-content">
                  <button
                    type="button"
                    class="trip-name-link"
                    @click="openWorkspace(data)"
                  >
                    {{ data.name }}
                  </button>
                  <small class="trip-note" v-if="data.notes">{{ data.notes }}</small>
                </div>
              </div>
            </template>
          </Column>

          <Column field="status" header="Status" sortable style="width: 9rem">
            <template #body="{ data }">
              <Tag :severity="getStatusSeverity(data.status)" :value="getStatusLabel(data.status)" />
            </template>
          </Column>

          <Column header="Access" sortable style="width: 8rem">
            <template #body="{ data }">
              <Tag :severity="getAccessSeverity(data)" :value="getAccessLabel(data)" />
            </template>
          </Column>

          <Column field="startTime" header="Start" sortable>
            <template #body="{ data }">
              {{ formatDateTime(data.startTime) }}
            </template>
          </Column>

          <Column field="endTime" header="End" sortable>
            <template #body="{ data }">
              {{ formatDateTime(data.endTime) }}
            </template>
          </Column>

          <Column header="Duration">
            <template #body="{ data }">
              {{ formatDurationLabel(data.startTime, data.endTime) }}
            </template>
          </Column>

          <Column header="Actions" style="width: 14rem">
            <template #body="{ data }">
              <div class="trip-actions-row">
                <Button
                  icon="pi pi-briefcase"
                  class="p-button-text p-button-sm"
                  @click="openWorkspace(data)"
                  v-tooltip.top="'Open trip planner'"
                />
                <Button
                  v-if="isLinkedToLabel(data) && isTripOwner(data)"
                  icon="pi pi-tag"
                  class="p-button-text p-button-sm"
                  @click="openLinkedLabel(data)"
                  v-tooltip.top="'Open timeline label'"
                />
                <Button
                  v-if="isLinkedToLabel(data) && isTripOwner(data)"
                  icon="pi pi-link"
                  class="p-button-text p-button-sm"
                  @click="unlinkTripFromLabel(data)"
                  v-tooltip.top="'Unlink timeline label'"
                />
                <Button
                  v-if="isTripOwner(data)"
                  icon="pi pi-pencil"
                  class="p-button-text p-button-sm"
                  @click="openEditDialog(data)"
                  v-tooltip.top="'Edit trip plan'"
                />
                <Button
                  v-if="isTripOwner(data)"
                  icon="pi pi-trash"
                  class="p-button-text p-button-sm"
                  severity="danger"
                  @click="confirmDeleteTrip(data)"
                  v-tooltip.top="'Delete trip plan'"
                />
              </div>
            </template>
          </Column>

          <template #empty>
            <div class="empty-state">
              <i class="pi pi-briefcase empty-state-icon"></i>
              <p>No trip plans found.</p>
              <small>Create your first trip plan to start planning.</small>
            </div>
          </template>
        </DataTable>
      </BaseCard>
    </PageContainer>

    <Dialog
      v-model:visible="showTripDialog"
      modal
      :header="isEditMode ? 'Edit Trip Plan' : 'Create Trip Plan'"
      class="gp-dialog-md"
      @hide="resetTripForm"
    >
      <div class="grid">
        <div class="col-12">
          <label for="tripName" class="field-label">Plan Name *</label>
          <InputText
            id="tripName"
            v-model="tripForm.name"
            class="w-full"
            placeholder="e.g., Vacation in Spain"
            :class="{ 'p-invalid': formErrors.name }"
          />
          <small v-if="formErrors.name" class="p-error">{{ formErrors.name }}</small>
        </div>

        <div class="col-12">
          <label for="tripDateRange" class="field-label">{{ tripDateRangeLabel }}</label>
          <DatePicker
            id="tripDateRange"
            v-model="tripDateRange"
            selectionMode="range"
            class="w-full"
            :manualInput="false"
            :dateFormat="timezone.getPrimeVueDatePickerFormat()"
            :class="{ 'p-invalid': formErrors.dateRange }"
          />
          <small v-if="formErrors.dateRange" class="p-error">{{ formErrors.dateRange }}</small>
          <small v-else class="field-hint">{{ tripDateRangeHint }}</small>
        </div>

        <div class="col-12">
          <label class="field-label">Color</label>
          <div class="color-row">
            <ColorPicker v-model="tripForm.color" format="hex" />
            <Button
              icon="pi pi-refresh"
              label="Random"
              size="small"
              text
              @click="tripForm.color = getRandomColor()"
            />
            <Tag
              :value="tripForm.name || 'Preview'"
              :style="{ backgroundColor: normalizedTripColor }"
              class="preview-tag"
            />
          </div>
        </div>

        <div class="col-12">
          <label for="tripNotes" class="field-label">Notes</label>
          <Textarea
            id="tripNotes"
            v-model="tripForm.notes"
            rows="4"
            class="w-full"
            placeholder="Optional notes, goals, links, packing reminders..."
          />
        </div>
      </div>

      <template #footer>
        <Button label="Cancel" icon="pi pi-times" outlined @click="showTripDialog = false" />
        <Button
          :label="isEditMode ? 'Update Trip' : 'Create Trip'"
          icon="pi pi-check"
          :loading="isSubmittingTrip"
          @click="submitTrip"
        />
      </template>
    </Dialog>

    <Dialog
      v-model:visible="showFromPeriodTagDialog"
      modal
      header="Create Trip Plan from Timeline Label"
      class="gp-dialog-md"
      @hide="selectedPeriodTagId = null"
    >
      <div class="from-tag-dialog-content">
        <p class="gp-text-secondary">
          Select a completed timeline label to create a Trip Plan.
        </p>

        <Select
          v-model="selectedPeriodTagId"
          :options="periodTagOptions"
          optionLabel="label"
          optionValue="value"
          class="w-full"
          placeholder="Choose timeline label"
          filter
        />

        <Message v-if="periodTagOptions.length === 0" severity="warn" :closable="false" class="no-period-tags-warning">
          No completed timeline labels available.
        </Message>
      </div>

      <template #footer>
        <Button label="Cancel" icon="pi pi-times" outlined @click="showFromPeriodTagDialog = false" />
        <Button
          label="Create Trip Plan"
          icon="pi pi-check"
          :disabled="!selectedPeriodTagId"
          :loading="isCreatingFromTag"
          @click="createFromPeriodTag"
        />
      </template>
    </Dialog>

    <ConfirmDialog />

    <Dialog
      v-model:visible="showLinkedTripDeleteDialog"
      modal
      header="Delete Linked Trip Plan"
      class="gp-dialog-md"
      @hide="linkedTripDeleteTarget = null"
    >
      <div class="from-tag-dialog-content">
        <p class="gp-text-secondary">
          This trip plan is linked to timeline label
          <strong>"{{ linkedTripDeleteTargetLabel }}"</strong>.
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
          @click="showLinkedTripDeleteDialog = false"
        />
        <Button
          label="Delete Trip Plan Only"
          icon="pi pi-trash"
          severity="warn"
          :loading="isDeletingLinkedTrip"
          @click="deleteLinkedTrip('unlink_only')"
        />
        <Button
          label="Delete Trip Plan + Label"
          icon="pi pi-trash"
          severity="danger"
          :loading="isDeletingLinkedTrip"
          @click="deleteLinkedTrip('delete_both')"
        />
      </template>
    </Dialog>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { useTimezone } from '@/composables/useTimezone'
import { usePeriodTag } from '@/composables/usePeriodTag'
import { formatDurationCompact } from '@/utils/calculationsHelpers'
import { useTripsStore } from '@/stores/trips'
import { usePeriodTagsStore } from '@/stores/periodTags'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import Button from 'primevue/button'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import Dialog from 'primevue/dialog'
import DatePicker from 'primevue/datepicker'
import Textarea from 'primevue/textarea'
import ColorPicker from 'primevue/colorpicker'
import ConfirmDialog from 'primevue/confirmdialog'

const router = useRouter()
const route = useRoute()
const toast = useToast()
const confirm = useConfirm()
const timezone = useTimezone()
const { getRandomColor, formatColorWithHash } = usePeriodTag()
const tripsStore = useTripsStore()
const periodTagsStore = usePeriodTagsStore()
const TRIP_PLANS_HELP_DISMISSED_KEY = 'gp.trip-plans.help.dismissed'

const { trips } = storeToRefs(tripsStore)
const { periodTags } = storeToRefs(periodTagsStore)

const searchTerm = ref('')
const statusFilter = ref('ALL')
const accessFilter = ref('ALL')
const showTripDialog = ref(false)
const showFromPeriodTagDialog = ref(false)
const isEditMode = ref(false)
const editingTripId = ref(null)
const editingTripWasUnplanned = ref(false)
const isSubmittingTrip = ref(false)
const isCreatingFromTag = ref(false)
const selectedPeriodTagId = ref(null)
const showTripPlansHelpMessage = ref(true)
const showLinkedTripDeleteDialog = ref(false)
const linkedTripDeleteTarget = ref(null)
const isDeletingLinkedTrip = ref(false)

const tripForm = ref({
  name: '',
  color: getRandomColor(),
  notes: ''
})
const tripDateRange = ref(null)
const formErrors = ref({})

const statusOptions = [
  { label: 'All statuses', value: 'ALL' },
  { label: 'Unplanned', value: 'UNPLANNED' },
  { label: 'Upcoming', value: 'UPCOMING' },
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Completed', value: 'COMPLETED' },
  { label: 'Cancelled', value: 'CANCELLED' }
]

const accessOptions = [
  { label: 'All access', value: 'ALL' },
  { label: 'Owned by me', value: 'OWNED' },
  { label: 'Shared with me', value: 'SHARED' }
]

const pageSubtitle = computed(() => {
  const total = trips.value.length
  if (total === 0) {
    return 'Plan upcoming trips, track visit completion, and analyze outcomes.'
  }
  return `${total} plan${total > 1 ? 's' : ''} available`
})

const normalizedTripColor = computed(() => formatColorWithHash(tripForm.value.color) || 'var(--gp-primary)')
const tripDateRangeLabel = computed(() => {
  if (!isEditMode.value) return 'Date Range (optional)'
  return editingTripWasUnplanned.value ? 'Date Range (optional)' : 'Date Range *'
})
const tripDateRangeHint = computed(() => {
  if (!isEditMode.value) return 'Leave empty to create this plan as unplanned.'
  if (editingTripWasUnplanned.value) return 'Add both dates to schedule this trip.'
  return 'Date range is required for scheduled trips.'
})

const filteredTrips = computed(() => {
  let items = Array.isArray(trips.value) ? [...trips.value] : []

  if (accessFilter.value === 'OWNED') {
    items = items.filter((trip) => isTripOwner(trip))
  } else if (accessFilter.value === 'SHARED') {
    items = items.filter((trip) => !isTripOwner(trip))
  }

  if (statusFilter.value && statusFilter.value !== 'ALL') {
    items = items.filter((trip) => String(trip.status || '').toUpperCase() === statusFilter.value)
  }

  if (searchTerm.value?.trim()) {
    const term = searchTerm.value.trim().toLowerCase()
    items = items.filter((trip) =>
      String(trip.name || '').toLowerCase().includes(term) ||
      String(trip.notes || '').toLowerCase().includes(term)
    )
  }

  return items
})

const linkedTripDeleteTargetLabel = computed(() => {
  if (!linkedTripDeleteTarget.value?.periodTagId) return 'Unknown'
  const tag = (periodTags.value || []).find((item) => Number(item.id) === Number(linkedTripDeleteTarget.value.periodTagId))
  return tag?.tagName || `#${linkedTripDeleteTarget.value.periodTagId}`
})

const periodTagOptions = computed(() => {
  const options = (periodTags.value || [])
    .filter((tag) => !!tag.endTime)
    .map((tag) => ({
      value: tag.id,
      label: `${tag.tagName} (${timezone.formatDateDisplay(tag.startTime)} - ${timezone.formatDateDisplay(tag.endTime)})`
    }))

  return options.sort((a, b) => String(a.label).localeCompare(String(b.label)))
})

const getStatusLabel = (status) => {
  const value = String(status || '').toUpperCase()
  if (!value) return 'Unknown'
  return value.charAt(0) + value.slice(1).toLowerCase()
}

const getStatusSeverity = (status) => {
  const value = String(status || '').toUpperCase()
  if (value === 'UNPLANNED') return 'warn'
  if (value === 'ACTIVE') return 'success'
  if (value === 'UPCOMING') return 'info'
  if (value === 'COMPLETED') return 'secondary'
  if (value === 'CANCELLED') return 'danger'
  return 'contrast'
}

const isTripOwner = (trip) => Boolean(trip?.isOwner) || String(trip?.accessRole || '').toUpperCase() === 'OWNER'

const getAccessLabel = (trip) => {
  if (isTripOwner(trip)) return 'Owner'
  const role = String(trip?.accessRole || '').toUpperCase()
  return role === 'EDIT' ? 'Editor' : 'Viewer'
}

const getAccessSeverity = (trip) => {
  if (isTripOwner(trip)) return 'info'
  const role = String(trip?.accessRole || '').toUpperCase()
  return role === 'EDIT' ? 'success' : 'secondary'
}

const formatDateTime = (value) => {
  if (!value) return '—'
  return `${timezone.formatDateDisplay(value)} ${timezone.format(value, 'HH:mm')}`
}

const formatDurationLabel = (startTime, endTime) => {
  if (!startTime || !endTime) return '—'
  const start = timezone.fromUtc(startTime)
  const end = timezone.fromUtc(endTime)
  if (!end.isAfter(start)) return '—'
  return formatDurationCompact(end.diff(start, 'second'))
}

const refreshTrips = async () => {
  try {
    await tripsStore.fetchTrips(statusFilter.value === 'ALL' ? null : statusFilter.value)
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Failed to Load Trips',
      detail: error.message || 'Could not load trips',
      life: 4000
    })
  }
}

const openWorkspace = (trip) => {
  const query = {}
  if (trip?.startTime && trip?.endTime) {
    query.start = timezone.formatUrlDate(trip.startTime)
    query.end = timezone.formatUrlDate(trip.endTime)
  }

  router.push({
    path: `/app/trips/${trip.id}`,
    query
  })
}

const isLinkedToLabel = (trip) => {
  return !!trip?.periodTagId
}

const openLinkedLabel = (trip) => {
  if (!trip?.periodTagId) return
  router.push({
    path: '/app/timeline-labels'
  })
}

const guardOwnerAction = (trip, message = 'Only trip owner can perform this action.') => {
  if (isTripOwner(trip)) return true
  toast.add({
    severity: 'warn',
    summary: 'Owner Access Required',
    detail: message,
    life: 3500
  })
  return false
}

const unlinkTripFromLabel = (trip) => {
  if (!guardOwnerAction(trip)) return
  if (!trip?.id || !trip?.periodTagId) return
  confirm.require({
    message: `Unlink trip plan "${trip.name}" from its timeline label?`,
    header: 'Unlink Trip Plan',
    icon: 'pi pi-exclamation-triangle',
    accept: async () => {
      try {
        await tripsStore.unlinkTripFromPeriodTag(trip.id)
        await periodTagsStore.fetchPeriodTags()
        toast.add({
          severity: 'success',
          summary: 'Unlinked',
          detail: 'Trip plan and timeline label are now unlinked',
          life: 3000
        })
      } catch (error) {
        toast.add({
          severity: 'error',
          summary: 'Failed to Unlink',
          detail: error.response?.data?.message || error.message || 'Unlink failed',
          life: 4000
        })
      }
    }
  })
}

const resetTripForm = () => {
  tripForm.value = {
    name: '',
    color: getRandomColor(),
    notes: ''
  }
  tripDateRange.value = null
  formErrors.value = {}
  editingTripId.value = null
  editingTripWasUnplanned.value = false
  isEditMode.value = false
}

const openCreateDialog = () => {
  resetTripForm()
  showTripDialog.value = true
}

const openEditDialog = (trip) => {
  if (!guardOwnerAction(trip, 'You can edit trip metadata only if you own this trip.')) return
  resetTripForm()
  isEditMode.value = true
  editingTripId.value = trip.id
  editingTripWasUnplanned.value = String(trip.status || '').toUpperCase() === 'UNPLANNED' || (!trip.startTime && !trip.endTime)
  tripForm.value = {
    name: trip.name || '',
    color: trip.color || getRandomColor(),
    notes: trip.notes || ''
  }
  tripDateRange.value = (trip.startTime && trip.endTime)
    ? timezone.convertUtcRangeToCalendarDates(trip.startTime, trip.endTime)
    : null
  showTripDialog.value = true
}

const validateTripForm = () => {
  formErrors.value = {}
  const hasCompleteDateRange = Boolean(tripDateRange.value && tripDateRange.value[0] && tripDateRange.value[1])
  const hasAnyDateValue = Boolean(tripDateRange.value && (tripDateRange.value[0] || tripDateRange.value[1]))

  if (!tripForm.value.name || !tripForm.value.name.trim()) {
    formErrors.value.name = 'Plan name is required'
  }

  if (!hasAnyDateValue && isEditMode.value && !editingTripWasUnplanned.value) {
    formErrors.value.dateRange = 'Date range is required'
  } else if (hasAnyDateValue && !hasCompleteDateRange) {
    formErrors.value.dateRange = 'Select both start and end dates'
  }

  return Object.keys(formErrors.value).length === 0
}

const submitTrip = async () => {
  if (!validateTripForm()) return

  isSubmittingTrip.value = true
  try {
    const hasCompleteDateRange = Boolean(tripDateRange.value && tripDateRange.value[0] && tripDateRange.value[1])

    let start = null
    let end = null
    if (hasCompleteDateRange) {
      const range = timezone.createDateRangeFromPicker(tripDateRange.value[0], tripDateRange.value[1])
      start = range.start
      end = range.end
    }

    const payload = {
      name: tripForm.value.name.trim(),
      startTime: start,
      endTime: end,
      color: formatColorWithHash(tripForm.value.color),
      notes: tripForm.value.notes?.trim() || null
    }

    if (isEditMode.value && editingTripId.value) {
      await tripsStore.updateTrip(editingTripId.value, payload)
      toast.add({
        severity: 'success',
        summary: 'Trip Plan Updated',
        detail: 'Trip plan updated successfully',
        life: 3000
      })
    } else {
      await tripsStore.createTrip(payload)
      toast.add({
        severity: 'success',
        summary: 'Trip Plan Created',
        detail: 'Trip plan created successfully',
        life: 3000
      })
    }

    showTripDialog.value = false
    resetTripForm()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: isEditMode.value ? 'Failed to Update Trip Plan' : 'Failed to Create Trip Plan',
      detail: error.response?.data?.message || error.message || 'Request failed',
      life: 5000
    })
  } finally {
    isSubmittingTrip.value = false
  }
}

const performDeleteTrip = async (trip, mode) => {
  if (!guardOwnerAction(trip, 'You can delete trip plans only if you own this trip.')) return
  try {
    await tripsStore.deleteTrip(trip.id, mode)
    await periodTagsStore.fetchPeriodTags()
    toast.add({
      severity: 'success',
      summary: 'Trip Plan Deleted',
      detail: mode === 'delete_both'
        ? 'Trip plan and timeline label removed'
        : 'Trip plan removed and timeline label unlinked',
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Failed to Delete Trip Plan',
      detail: error.response?.data?.message || error.message || 'Delete failed',
      life: 5000
    })
  }
}

const deleteLinkedTrip = async (mode) => {
  if (!linkedTripDeleteTarget.value) return
  isDeletingLinkedTrip.value = true
  try {
    await performDeleteTrip(linkedTripDeleteTarget.value, mode)
    showLinkedTripDeleteDialog.value = false
    linkedTripDeleteTarget.value = null
  } finally {
    isDeletingLinkedTrip.value = false
  }
}

const confirmDeleteTrip = (trip) => {
  if (!guardOwnerAction(trip, 'You can delete trip plans only if you own this trip.')) return
  if (isLinkedToLabel(trip)) {
    linkedTripDeleteTarget.value = trip
    showLinkedTripDeleteDialog.value = true
    return
  }

  confirm.require({
    message: `Delete trip plan "${trip.name}"?`,
    header: 'Delete Trip Plan',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: async () => {
      await performDeleteTrip(trip, 'unlink_only')
    }
  })
}

const openFromPeriodTagDialog = async () => {
  try {
    if (!periodTags.value || periodTags.value.length === 0) {
      await periodTagsStore.fetchPeriodTags()
    }
  } catch (error) {
    toast.add({
      severity: 'warn',
      summary: 'Timeline Labels Unavailable',
      detail: 'Could not refresh timeline labels, but you can still create plans manually.',
      life: 4000
    })
  }
  selectedPeriodTagId.value = null
  showFromPeriodTagDialog.value = true
}

const createFromPeriodTag = async () => {
  if (!selectedPeriodTagId.value) return

  isCreatingFromTag.value = true
  try {
    const created = await tripsStore.createTripFromPeriodTag(selectedPeriodTagId.value)
    showFromPeriodTagDialog.value = false
    selectedPeriodTagId.value = null
    toast.add({
      severity: 'success',
      summary: 'Trip Plan Created',
      detail: 'Trip plan created from timeline label',
      life: 3000
    })
    if (created?.id) {
      openWorkspace(created)
    }
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Failed to Create from Timeline Label',
      detail: error.response?.data?.message || error.message || 'Conversion failed',
      life: 5000
    })
  } finally {
    isCreatingFromTag.value = false
  }
}

const dismissTripPlansHelpMessage = () => {
  showTripPlansHelpMessage.value = false
  try {
    localStorage.setItem(TRIP_PLANS_HELP_DISMISSED_KEY, '1')
  } catch (error) {
    // ignore storage access failures
  }
}

const clearRouteTripActionQuery = async () => {
  if (!route.query?.action && !route.query?.tripId) {
    return
  }

  const nextQuery = { ...route.query }
  delete nextQuery.action
  delete nextQuery.tripId
  await router.replace({ path: route.path, query: nextQuery })
}

const handleRouteTripAction = async () => {
  const action = String(route.query?.action || '').toLowerCase()
  const tripIdRaw = route.query?.tripId
  const tripId = Number(tripIdRaw)

  if (!action || !Number.isFinite(tripId)) {
    return
  }

  const trip = tripsStore.getTripById(tripId)

  if (!trip) {
    toast.add({
      severity: 'warn',
      summary: 'Trip Plan Not Found',
      detail: 'The linked trip plan could not be found.',
      life: 3500
    })
    await clearRouteTripActionQuery()
    return
  }

  if (action === 'edit') {
    openEditDialog(trip)
  } else if (action === 'delete') {
    confirmDeleteTrip(trip)
  }

  await clearRouteTripActionQuery()
}

onMounted(async () => {
  try {
    showTripPlansHelpMessage.value = localStorage.getItem(TRIP_PLANS_HELP_DISMISSED_KEY) !== '1'
  } catch (error) {
    showTripPlansHelpMessage.value = true
  }

  await Promise.all([
    tripsStore.fetchTrips(),
    periodTagsStore.fetchPeriodTags()
  ]).catch(() => {
    // Errors are handled in UI actions/toasts
  })

  await handleRouteTripAction()
})
</script>

<style scoped>
.trips-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gp-spacing-md);
  margin-bottom: var(--gp-spacing-md);
  flex-wrap: wrap;
}

.trips-filters {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  flex: 1;
  min-width: 260px;
}

.search-input {
  min-width: 260px;
  flex: 1;
}

.status-select {
  min-width: 180px;
}

.trip-actions-row {
  display: inline-flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: var(--gp-spacing-xs);
  white-space: nowrap;
}

.trip-name-cell {
  display: flex;
  align-items: flex-start;
  gap: var(--gp-spacing-sm);
}

.trip-color-dot {
  width: 0.75rem;
  height: 0.75rem;
  border-radius: 999px;
  margin-top: 0.25rem;
  flex-shrink: 0;
}

.trip-name-content {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.trip-name-link {
  border: 0;
  background: transparent;
  padding: 0;
  margin: 0;
  text-align: left;
  font-weight: 600;
  color: var(--gp-text-primary);
  cursor: pointer;
}

.trip-name-link:hover {
  color: var(--gp-primary);
  text-decoration: underline;
}

.trip-note {
  color: var(--gp-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 420px;
}

.empty-state {
  text-align: center;
  padding: var(--gp-spacing-xl);
  color: var(--gp-text-secondary);
}

.empty-state-icon {
  font-size: 2.5rem;
  margin-bottom: var(--gp-spacing-sm);
  color: var(--gp-text-muted);
}

.field-label {
  display: block;
  margin-bottom: var(--gp-spacing-xs);
  color: var(--gp-text-secondary);
  font-weight: 500;
}

.field-hint {
  display: block;
  margin-top: var(--gp-spacing-xs);
  color: var(--gp-text-secondary);
}

.color-row {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
}

.preview-tag {
  border: none;
  color: var(--gp-surface-white);
}

.from-tag-dialog-content {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
}

.no-period-tags-warning {
  margin-top: var(--gp-spacing-xs);
}

@media (max-width: 768px) {
  .trips-filters {
    flex-direction: column;
    align-items: stretch;
  }

  .search-input,
  .status-select {
    min-width: 100%;
    width: 100%;
  }

  .trip-note {
    max-width: 220px;
  }
}
</style>
