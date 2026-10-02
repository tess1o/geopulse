<template>
  <AppLayout variant="default">
    <PageContainer
      :title="t('trips.managementPage.title')"
      :subtitle="pageSubtitle"
      :loading="tripsStore.loading.trips"
      variant="fullwidth"
    >
      <template #actions>
        <div class="trips-page-actions">
          <Button
            :label="t('trips.managementPage.fromTimelineLabel')"
            icon="pi pi-tag"
            outlined
            @click="openFromTimelineLabelDialog"
          />
          <Button
            :label="t('trips.managementPage.createTripPlan')"
            icon="pi pi-plus"
            class="gp-btn-primary"
            @click="openCreateDialog"
          />
        </div>
      </template>

      <Message
        v-if="showTripPlansHelpMessage"
        severity="info"
        :closable="true"
        style="margin-bottom: var(--gp-spacing-md)"
        @close="dismissTripPlansHelpMessage"
      >
        {{ t('trips.managementPage.helpMessage') }}
      </Message>

      <BaseCard>
        <div class="trips-toolbar">
          <div class="trips-filters">
            <InputText
              v-model="searchTerm"
              :placeholder="t('trips.managementPage.searchPlaceholder')"
              class="gp-input search-input"
            />
            <Select
              v-model="statusFilter"
              :options="statusOptions"
              optionLabel="label"
              optionValue="value"
              class="status-select trip-status-filter"
            />
            <Select
              v-model="accessFilter"
              :options="accessOptions"
              optionLabel="label"
              optionValue="value"
              class="status-select trip-access-filter"
            />
          </div>

          <Button
            icon="pi pi-refresh"
            :label="t('trips.managementPage.refresh')"
            outlined
            class="refresh-button"
            @click="refreshTrips"
          />
        </div>

        <DataTable
          v-if="!isMobile"
          class="desktop-table"
          :value="filteredTrips"
          :paginator="true"
          :rows="10"
          :rowsPerPageOptions="[10, 25, 50]"
          paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown CurrentPageReport"
          :currentPageReportTemplate="t('trips.managementPage.currentPageReport')"
          stripedRows
        >
          <Column field="name" :header="t('trips.managementPage.columnTripPlan')" sortable>
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

          <Column field="status" :header="t('trips.managementPage.columnStatus')" sortable style="width: 9rem">
            <template #body="{ data }">
              <Tag :severity="getStatusSeverity(data.status)" :value="getStatusLabel(data.status)" />
            </template>
          </Column>

          <Column :header="t('trips.managementPage.columnAccess')" sortable style="width: 8rem">
            <template #body="{ data }">
              <Tag :severity="getAccessSeverity(data)" :value="getAccessLabel(data)" />
            </template>
          </Column>

          <Column field="startTime" :header="t('trips.managementPage.columnStart')" sortable>
            <template #body="{ data }">
              {{ formatDateTime(data.startTime) }}
            </template>
          </Column>

          <Column field="endTime" :header="t('trips.managementPage.columnEnd')" sortable>
            <template #body="{ data }">
              {{ formatDateTime(data.endTime) }}
            </template>
          </Column>

          <Column :header="t('trips.managementPage.columnDuration')">
            <template #body="{ data }">
              {{ formatDurationLabel(data.startTime, data.endTime) }}
            </template>
          </Column>

          <Column :header="t('trips.managementPage.columnActions')" style="width: 14rem">
            <template #body="{ data }">
              <div class="trip-actions-row">
                <Button
                  icon="pi pi-briefcase"
                  class="p-button-text p-button-sm"
                  @click="openWorkspace(data)"
                  v-tooltip.top="t('trips.managementPage.openTripPlanner')"
                />
                <Button
                  v-if="isLinkedToLabel(data) && isTripOwner(data)"
                  icon="pi pi-tag"
                  class="p-button-text p-button-sm"
                  @click="openLinkedLabel(data)"
                  v-tooltip.top="t('trips.managementPage.openTimelineLabel')"
                />
                <Button
                  v-if="isLinkedToLabel(data) && isTripOwner(data)"
                  icon="pi pi-link"
                  class="p-button-text p-button-sm"
                  @click="unlinkTripFromLabel(data)"
                  v-tooltip.top="t('trips.managementPage.unlinkTimelineLabel')"
                />
                <Button
                  v-if="isTripOwner(data)"
                  icon="pi pi-pencil"
                  class="p-button-text p-button-sm"
                  @click="openEditDialog(data)"
                  v-tooltip.top="t('trips.managementPage.editTripPlan')"
                />
                <Button
                  v-if="isTripOwner(data)"
                  icon="pi pi-trash"
                  class="p-button-text p-button-sm"
                  severity="danger"
                  @click="confirmDeleteTrip(data)"
                  v-tooltip.top="t('trips.managementPage.deleteTripPlan')"
                />
              </div>
            </template>
          </Column>

          <template #empty>
            <div class="empty-state">
              <i class="pi pi-briefcase empty-state-icon"></i>
              <p>{{ t('trips.managementPage.emptyTitle') }}</p>
              <small>{{ t('trips.managementPage.emptyHint') }}</small>
            </div>
          </template>
        </DataTable>

        <div v-else class="mobile-trip-plan-panel">
          <div v-if="filteredTrips.length === 0" class="empty-state">
            <i class="pi pi-briefcase empty-state-icon"></i>
            <p>{{ t('trips.managementPage.emptyTitle') }}</p>
            <small>{{ t('trips.managementPage.emptyHint') }}</small>
          </div>

          <template v-else>
            <div class="mobile-trip-plan-list" role="list">
              <article
                v-for="trip in paginatedTrips"
                :key="trip.id"
                class="mobile-trip-plan-card"
                role="listitem"
              >
                <header class="mobile-trip-plan-header">
                  <span :style="{ backgroundColor: trip.color || 'var(--gp-primary)' }" class="trip-color-dot"></span>
                  <button
                    type="button"
                    class="trip-name-link mobile-trip-plan-name"
                    @click="openWorkspace(trip)"
                  >
                    {{ trip.name }}
                  </button>
                  <Button
                    icon="pi pi-ellipsis-v"
                    severity="secondary"
                    text
                    rounded
                    size="small"
                    class="mobile-trip-plan-actions-button"
                    aria-haspopup="true"
                    aria-controls="mobile-trip-plan-action-menu"
                    :aria-label="t('trips.managementPage.actionsFor', { name: trip.name })"
                    @click="openMobileActionMenu($event, trip)"
                  />
                </header>

                <p v-if="trip.notes" class="mobile-trip-plan-notes">{{ trip.notes }}</p>

                <div class="mobile-trip-plan-tags">
                  <Tag :severity="getStatusSeverity(trip.status)" :value="getStatusLabel(trip.status)" />
                  <Tag :severity="getAccessSeverity(trip)" :value="getAccessLabel(trip)" />
                </div>

                <div class="mobile-trip-plan-meta">
                  <span class="mobile-trip-plan-meta-item">
                    <i class="pi pi-calendar"></i>
                    <span>{{ formatTripDateRange(trip) }}</span>
                  </span>
                  <span class="mobile-trip-plan-meta-item">
                    <i class="pi pi-clock"></i>
                    <span>{{ formatDurationLabel(trip.startTime, trip.endTime) }}</span>
                  </span>
                </div>
              </article>
            </div>

            <Paginator
              v-if="mobileTotalPages > 1"
              :first="mobilePage * MOBILE_TRIP_ROWS"
              :rows="MOBILE_TRIP_ROWS"
              :total-records="filteredTrips.length"
              class="mobile-trip-plan-paginator"
              @page="onMobilePageChange"
            >
              <template #start>
                <span class="mobile-paginator-info">{{ t('trips.managementPage.mobilePage', { page: mobilePage + 1, total: mobileTotalPages }) }}</span>
              </template>
              <template #end>
                <span class="mobile-paginator-info">{{ t('trips.managementPage.mobileTotal', { count: filteredTrips.length }) }}</span>
              </template>
            </Paginator>
          </template>
        </div>
      </BaseCard>

      <Menu
        id="mobile-trip-plan-action-menu"
        ref="mobileActionMenu"
        :model="mobileActionMenuItems"
        popup
      />
    </PageContainer>

    <Dialog
      v-model:visible="showTripDialog"
      modal
      :header="isEditMode ? t('trips.managementPage.dialog.editHeader') : t('trips.managementPage.dialog.createHeader')"
      class="gp-dialog-lg"
      @hide="resetTripForm"
    >
      <div class="grid">
        <div class="col-12">
          <label for="tripName" class="field-label">{{ t('trips.managementPage.dialog.planNameLabel') }}</label>
          <InputText
            id="tripName"
            v-model="tripForm.name"
            class="w-full"
            :placeholder="t('trips.managementPage.dialog.planNamePlaceholder')"
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
            :manualInput="true"
            iconDisplay="input"
            :dateFormat="timezone.getPrimeVueDatePickerFormat()"
            :placeholder="tripDateRangePlaceholder"
            append-to="body"
            panel-class="trip-date-range-panel"
            :class="{ 'p-invalid': formErrors.dateRange }"
          />
          <div class="date-range-help">
            <small v-if="formErrors.dateRange" class="p-error">{{ formErrors.dateRange }}</small>
            <template v-else>
              <small class="field-hint">{{ tripDateRangeHint }}</small>
              <small class="field-hint field-hint-strong">{{ t('trips.managementPage.dialog.dateRangeTip') }}</small>
            </template>
          </div>
        </div>

        <div class="col-12">
          <label class="field-label">{{ t('trips.managementPage.dialog.colorLabel') }}</label>
          <div class="color-row">
            <ColorPicker v-model="tripForm.color" format="hex" />
            <Button
              icon="pi pi-refresh"
              :label="t('trips.managementPage.dialog.random')"
              size="small"
              text
              @click="tripForm.color = getRandomColor()"
            />
            <Tag
              :value="tripForm.name || t('trips.managementPage.dialog.colorPreviewFallback')"
              :style="{ backgroundColor: normalizedTripColor }"
              class="preview-tag"
            />
          </div>
        </div>

        <div class="col-12">
          <label for="tripNotes" class="field-label">{{ t('trips.managementPage.dialog.notesLabel') }}</label>
          <Textarea
            id="tripNotes"
            v-model="tripForm.notes"
            rows="4"
            class="w-full"
            :placeholder="t('trips.managementPage.dialog.notesPlaceholder')"
          />
        </div>
      </div>

      <template #footer>
        <Button :label="t('trips.managementPage.dialog.cancel')" icon="pi pi-times" outlined @click="showTripDialog = false" />
        <Button
          :label="isEditMode ? t('trips.managementPage.dialog.updateTrip') : t('trips.managementPage.dialog.createTrip')"
          icon="pi pi-check"
          :loading="isSubmittingTrip"
          @click="submitTrip"
        />
      </template>
    </Dialog>

    <Dialog
      v-model:visible="showFromTimelineLabelDialog"
      modal
      :header="t('trips.managementPage.fromLabelDialog.header')"
      class="gp-dialog-md"
      @hide="selectedTimelineLabelId = null"
    >
      <div class="from-label-dialog-content">
        <p class="gp-text-secondary">
          {{ t('trips.managementPage.fromLabelDialog.description') }}
        </p>

        <Select
          v-model="selectedTimelineLabelId"
          :options="timelineLabelOptions"
          optionLabel="label"
          optionValue="value"
          class="w-full"
          :placeholder="t('trips.managementPage.fromLabelDialog.placeholder')"
          filter
        />

        <Message v-if="timelineLabelOptions.length === 0" severity="warn" :closable="false" class="no-timeline-labels-warning">
          {{ t('trips.managementPage.fromLabelDialog.noOptions') }}
        </Message>
      </div>

      <template #footer>
        <Button :label="t('trips.managementPage.dialog.cancel')" icon="pi pi-times" outlined @click="showFromTimelineLabelDialog = false" />
        <Button
          :label="t('trips.managementPage.createTripPlan')"
          icon="pi pi-check"
          :disabled="!selectedTimelineLabelId"
          :loading="isCreatingFromTag"
          @click="createFromTimelineLabel"
        />
      </template>
    </Dialog>

    <ConfirmDialog />

    <Dialog
      v-model:visible="showLinkedTripDeleteDialog"
      modal
      :header="t('trips.managementPage.linkedDeleteDialog.header')"
      class="gp-dialog-md"
      @hide="linkedTripDeleteTarget = null"
    >
      <div class="from-label-dialog-content">
        <p class="gp-text-secondary">
          {{ t('trips.managementPage.linkedDeleteDialog.linkedTo') }}
          <strong>"{{ linkedTripDeleteTargetLabel }}"</strong>.
        </p>
        <p class="gp-text-secondary">
          {{ t('trips.managementPage.linkedDeleteDialog.chooseWhatToDelete') }}
        </p>
      </div>

      <template #footer>
        <Button
          :label="t('trips.managementPage.dialog.cancel')"
          icon="pi pi-times"
          outlined
          @click="showLinkedTripDeleteDialog = false"
        />
        <Button
          :label="t('trips.managementPage.linkedDeleteDialog.deletePlanOnly')"
          icon="pi pi-trash"
          severity="warn"
          :loading="isDeletingLinkedTrip"
          @click="deleteLinkedTrip('unlink_only')"
        />
        <Button
          :label="t('trips.managementPage.linkedDeleteDialog.deletePlanAndLabel')"
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
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { useTimezone } from '@/composables/useTimezone'
import { useTimelineLabel } from '@/composables/useTimelineLabel'
import { formatTripRangeDuration } from '@/utils/tripHelpers'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'
import { useTripsStore } from '@/stores/trips'
import { useTimelineLabelsStore } from '@/stores/timelineLabels'
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
import Menu from 'primevue/menu'
import Paginator from 'primevue/paginator'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const toast = useToast()
const confirm = useConfirm()
const timezone = useTimezone()
const { getRandomColor, formatColorWithHash } = useTimelineLabel()
const tripsStore = useTripsStore()
const timelineLabelsStore = useTimelineLabelsStore()
const TRIP_PLANS_HELP_DISMISSED_KEY = 'gp.trip-plans.help.dismissed'
const MOBILE_TRIP_ROWS = 10

const { trips } = storeToRefs(tripsStore)
const { timelineLabels } = storeToRefs(timelineLabelsStore)

const searchTerm = ref('')
const statusFilter = ref('ALL')
const accessFilter = ref('ALL')
const showTripDialog = ref(false)
const showFromTimelineLabelDialog = ref(false)
const isEditMode = ref(false)
const editingTripId = ref(null)
const editingTripWasUnplanned = ref(false)
const isSubmittingTrip = ref(false)
const isCreatingFromTag = ref(false)
const selectedTimelineLabelId = ref(null)
const showTripPlansHelpMessage = ref(true)
const showLinkedTripDeleteDialog = ref(false)
const linkedTripDeleteTarget = ref(null)
const isDeletingLinkedTrip = ref(false)
const isMobile = ref(typeof window !== 'undefined' && window.innerWidth <= 768)
const mobilePage = ref(0)
const mobileActionMenu = ref()
const mobileActionTrip = ref(null)

const tripForm = ref({
  name: '',
  color: getRandomColor(),
  notes: ''
})
const tripDateRange = ref(null)
const formErrors = ref({})

const statusOptions = computed(() => [
  { label: t('trips.managementPage.status.all'), value: 'ALL' },
  { label: t('trips.managementPage.status.unplanned'), value: 'UNPLANNED' },
  { label: t('trips.managementPage.status.upcoming'), value: 'UPCOMING' },
  { label: t('trips.managementPage.status.active'), value: 'ACTIVE' },
  { label: t('trips.managementPage.status.completed'), value: 'COMPLETED' },
  { label: t('trips.managementPage.status.cancelled'), value: 'CANCELLED' }
])

const accessOptions = computed(() => [
  { label: t('trips.managementPage.access.all'), value: 'ALL' },
  { label: t('trips.managementPage.access.owned'), value: 'OWNED' },
  { label: t('trips.managementPage.access.shared'), value: 'SHARED' }
])

const pageSubtitle = computed(() => {
  const total = trips.value.length
  if (total === 0) {
    return t('trips.managementPage.subtitleEmpty')
  }
  return t('trips.managementPage.subtitleCount', { count: total }, total)
})

const normalizedTripColor = computed(() => formatColorWithHash(tripForm.value.color) || 'var(--gp-primary)')
const tripDateRangeLabel = computed(() => {
  if (!isEditMode.value) return t('trips.managementPage.dialog.dateRangeLabelOptional')
  return editingTripWasUnplanned.value ? t('trips.managementPage.dialog.dateRangeLabelOptional') : t('trips.managementPage.dialog.dateRangeLabelRequired')
})
const tripDateRangeHint = computed(() => {
  if (!isEditMode.value) return t('trips.managementPage.dialog.dateRangeHintCreate')
  if (editingTripWasUnplanned.value) return t('trips.managementPage.dialog.dateRangeHintUnplanned')
  return t('trips.managementPage.dialog.dateRangeHintRequired')
})
const tripDateRangePlaceholder = computed(() => {
  const dateTokenByFormat = {
    MDY: 'MM/DD/YYYY',
    DMY: 'DD/MM/YYYY',
    YMD: 'YYYY-MM-DD'
  }
  const token = dateTokenByFormat[timezone.getDateFormat()] || 'MM/DD/YYYY'
  return `${token} - ${token}`
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

const mobileTotalPages = computed(() => Math.max(1, Math.ceil(filteredTrips.value.length / MOBILE_TRIP_ROWS)))

const paginatedTrips = computed(() => {
  const start = mobilePage.value * MOBILE_TRIP_ROWS
  return filteredTrips.value.slice(start, start + MOBILE_TRIP_ROWS)
})

const linkedTripDeleteTargetLabel = computed(() => {
  if (!linkedTripDeleteTarget.value?.timelineLabelId) return t('trips.managementPage.status.unknown')
  const tag = (timelineLabels.value || []).find((item) => Number(item.id) === Number(linkedTripDeleteTarget.value.timelineLabelId))
  return tag?.name || `#${linkedTripDeleteTarget.value.timelineLabelId}`
})

const timelineLabelOptions = computed(() => {
  const options = (timelineLabels.value || [])
    .filter((tag) => !!tag.endTime)
    .map((tag) => ({
      value: tag.id,
      label: `${tag.name} (${timezone.formatDateDisplay(tag.startTime)} - ${timezone.formatDateDisplay(tag.endTime)})`
    }))

  return options.sort((a, b) => String(a.label).localeCompare(String(b.label)))
})

const getStatusLabel = (status) => {
  const value = String(status || '').toUpperCase()
  const labelKeys = {
    UNPLANNED: 'trips.managementPage.status.unplanned',
    UPCOMING: 'trips.managementPage.status.upcoming',
    ACTIVE: 'trips.managementPage.status.active',
    COMPLETED: 'trips.managementPage.status.completed',
    CANCELLED: 'trips.managementPage.status.cancelled'
  }
  return t(labelKeys[value] || 'trips.managementPage.status.unknown')
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
  if (isTripOwner(trip)) return t('trips.managementPage.access.owner')
  const role = String(trip?.accessRole || '').toUpperCase()
  return role === 'EDIT' ? t('trips.managementPage.access.editor') : t('trips.managementPage.access.viewer')
}

const getAccessSeverity = (trip) => {
  if (isTripOwner(trip)) return 'info'
  const role = String(trip?.accessRole || '').toUpperCase()
  return role === 'EDIT' ? 'success' : 'secondary'
}

const formatDateTime = (value) => {
  if (!value) return '—'
  return `${timezone.formatDateDisplay(value)} ${timezone.formatTime(value)}`
}

const formatDurationLabel = (startTime, endTime) => {
  return formatTripRangeDuration(startTime, endTime)
}

const formatTripDateRange = (trip) => {
  const startTime = trip?.startTime
  const endTime = trip?.endTime
  if (!startTime && !endTime) return t('trips.managementPage.noDateRange')
  if (!startTime || !endTime) return formatDateTime(startTime || endTime)
  return `${formatDateTime(startTime)} → ${formatDateTime(endTime)}`
}

const refreshTrips = async () => {
  try {
    await tripsStore.fetchTrips(statusFilter.value === 'ALL' ? null : statusFilter.value)
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('trips.managementPage.toasts.loadFailedSummary'),
      detail: formatApiErrorDetail(error, t('trips.managementPage.toasts.loadFailedFallback')),
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
  return !!trip?.timelineLabelId
}

const openLinkedLabel = (trip) => {
  if (!trip?.timelineLabelId) return
  router.push({
    path: '/app/timeline-labels'
  })
}

const guardOwnerAction = (trip, message) => {
  if (isTripOwner(trip)) return true
  toast.add({
    severity: 'warn',
    summary: t('trips.managementPage.toasts.ownerRequiredSummary'),
    detail: message || t('trips.managementPage.toasts.ownerOnlyGeneric'),
    life: 3500
  })
  return false
}

const unlinkTripFromLabel = (trip) => {
  if (!guardOwnerAction(trip)) return
  if (!trip?.id || !trip?.timelineLabelId) return
  confirm.require({
    message: t('trips.managementPage.unlinkConfirm.message', { name: trip.name }),
    header: t('trips.managementPage.unlinkConfirm.header'),
    icon: 'pi pi-exclamation-triangle',
    accept: async () => {
      try {
        await tripsStore.unlinkTripFromTimelineLabel(trip.id)
        await timelineLabelsStore.fetchTimelineLabels()
        toast.add({
          severity: 'success',
          summary: t('trips.managementPage.toasts.unlinkedSummary'),
          detail: t('trips.managementPage.toasts.unlinkedDetail'),
          life: 3000
        })
      } catch (error) {
        toast.add({
          severity: 'error',
          summary: t('trips.managementPage.toasts.unlinkFailedSummary'),
          detail: formatApiErrorDetail(error, t('trips.managementPage.toasts.unlinkFailedFallback')),
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
  if (!guardOwnerAction(trip, t('trips.managementPage.toasts.ownerOnlyEdit'))) return
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
    formErrors.value.name = t('trips.managementPage.dialog.nameRequired')
  }

  if (!hasAnyDateValue && isEditMode.value && !editingTripWasUnplanned.value) {
    formErrors.value.dateRange = t('trips.managementPage.dialog.dateRangeRequired')
  } else if (hasAnyDateValue && !hasCompleteDateRange) {
    formErrors.value.dateRange = t('trips.managementPage.dialog.dateRangeIncomplete')
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
        summary: t('trips.managementPage.toasts.updatedSummary'),
        detail: t('trips.managementPage.toasts.updatedDetail'),
        life: 3000
      })
    } else {
      await tripsStore.createTrip(payload)
      toast.add({
        severity: 'success',
        summary: t('trips.managementPage.toasts.createdSummary'),
        detail: t('trips.managementPage.toasts.createdDetail'),
        life: 3000
      })
    }

    showTripDialog.value = false
    resetTripForm()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: isEditMode.value ? t('trips.managementPage.toasts.updateFailedSummary') : t('trips.managementPage.toasts.createFailedSummary'),
      detail: formatApiErrorDetail(error, t('trips.managementPage.toasts.requestFailedFallback')),
      life: 5000
    })
  } finally {
    isSubmittingTrip.value = false
  }
}

const performDeleteTrip = async (trip, mode) => {
  if (!guardOwnerAction(trip, t('trips.managementPage.toasts.ownerOnlyDelete'))) return
  try {
    await tripsStore.deleteTrip(trip.id, mode)
    await timelineLabelsStore.fetchTimelineLabels()
    toast.add({
      severity: 'success',
      summary: t('trips.managementPage.toasts.deletedSummary'),
      detail: mode === 'delete_both'
        ? t('trips.managementPage.toasts.deletedBothDetail')
        : t('trips.managementPage.toasts.deletedUnlinkOnlyDetail'),
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('trips.managementPage.toasts.deleteFailedSummary'),
      detail: formatApiErrorDetail(error, t('trips.managementPage.toasts.deleteFailedFallback')),
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
  if (!guardOwnerAction(trip, t('trips.managementPage.toasts.ownerOnlyDelete'))) return
  if (isLinkedToLabel(trip)) {
    linkedTripDeleteTarget.value = trip
    showLinkedTripDeleteDialog.value = true
    return
  }

  confirm.require({
    message: t('trips.managementPage.deleteConfirm.message', { name: trip.name }),
    header: t('trips.managementPage.deleteConfirm.header'),
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: async () => {
      await performDeleteTrip(trip, 'unlink_only')
    }
  })
}

const onMobilePageChange = (event) => {
  mobilePage.value = event.page
}

const mobileActionMenuItems = computed(() => {
  const trip = mobileActionTrip.value
  if (!trip) return []

  const isOwner = isTripOwner(trip)
  const items = [
    { label: t('trips.managementPage.openTripPlanner'), icon: 'pi pi-briefcase', command: () => openWorkspace(trip) }
  ]

  if (isLinkedToLabel(trip) && isOwner) {
    items.push({ label: t('trips.managementPage.openTimelineLabel'), icon: 'pi pi-tag', command: () => openLinkedLabel(trip) })
    items.push({ label: t('trips.managementPage.unlinkTimelineLabel'), icon: 'pi pi-link', command: () => unlinkTripFromLabel(trip) })
  }

  if (isOwner) {
    items.push({ label: t('trips.managementPage.editTripPlan'), icon: 'pi pi-pencil', command: () => openEditDialog(trip) })
    items.push({ label: t('trips.managementPage.deleteTripPlan'), icon: 'pi pi-trash', command: () => confirmDeleteTrip(trip) })
  }

  return items
})

const openMobileActionMenu = (event, trip) => {
  mobileActionTrip.value = trip
  mobileActionMenu.value?.toggle(event)
}

const openFromTimelineLabelDialog = async () => {
  try {
    if (!timelineLabels.value || timelineLabels.value.length === 0) {
      await timelineLabelsStore.fetchTimelineLabels()
    }
  } catch (error) {
    toast.add({
      severity: 'warn',
      summary: t('trips.managementPage.toasts.labelsUnavailableSummary'),
      detail: t('trips.managementPage.toasts.labelsUnavailableDetail'),
      life: 4000
    })
  }
  selectedTimelineLabelId.value = null
  showFromTimelineLabelDialog.value = true
}

const createFromTimelineLabel = async () => {
  if (!selectedTimelineLabelId.value) return

  isCreatingFromTag.value = true
  try {
    const created = await tripsStore.createTripFromTimelineLabel(selectedTimelineLabelId.value)
    showFromTimelineLabelDialog.value = false
    selectedTimelineLabelId.value = null
    toast.add({
      severity: 'success',
      summary: t('trips.managementPage.toasts.createdSummary'),
      detail: t('trips.managementPage.toasts.createdFromLabelDetail'),
      life: 3000
    })
    if (created?.id) {
      openWorkspace(created)
    }
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('trips.managementPage.toasts.createFromLabelFailedSummary'),
      detail: formatApiErrorDetail(error, t('trips.managementPage.toasts.conversionFailedFallback')),
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
      summary: t('trips.managementPage.toasts.notFoundSummary'),
      detail: t('trips.managementPage.toasts.notFoundDetail'),
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

const handleResize = () => {
  isMobile.value = window.innerWidth <= 768
}

watch(() => filteredTrips.value.length, () => {
  const lastPage = Math.max(0, mobileTotalPages.value - 1)
  if (mobilePage.value > lastPage) {
    mobilePage.value = lastPage
  }
})

onMounted(async () => {
  handleResize()
  window.addEventListener('resize', handleResize)

  try {
    showTripPlansHelpMessage.value = localStorage.getItem(TRIP_PLANS_HELP_DISMISSED_KEY) !== '1'
  } catch (error) {
    showTripPlansHelpMessage.value = true
  }

  await Promise.all([
    tripsStore.fetchTrips(),
    timelineLabelsStore.fetchTimelineLabels()
  ]).catch(() => {
    // Errors are handled in UI actions/toasts
  })

  await handleRouteTripAction()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
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

/* Mobile trip plan card list */
.mobile-trip-plan-list {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.mobile-trip-plan-card {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
  padding: var(--gp-spacing-md);
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-muted);
}

.mobile-trip-plan-header {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--gp-spacing-sm);
}

.mobile-trip-plan-header .trip-color-dot {
  margin-top: 0;
}

.mobile-trip-plan-name {
  font-size: 1rem;
  overflow-wrap: anywhere;
}

.mobile-trip-plan-actions-button {
  width: 2rem;
  height: 2rem;
  min-width: 2rem;
  padding: 0;
}

.mobile-trip-plan-notes {
  margin: 0;
  color: var(--gp-text-secondary);
  font-size: 0.85rem;
  line-height: 1.35;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.mobile-trip-plan-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gp-spacing-xs);
}

.mobile-trip-plan-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gp-spacing-xs) var(--gp-spacing-md);
  color: var(--gp-text-secondary);
  font-size: 0.8rem;
}

.mobile-trip-plan-meta-item {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  min-width: 0;
}

.mobile-trip-plan-meta-item i {
  color: var(--gp-text-muted);
  font-size: 0.8rem;
}

.mobile-trip-plan-paginator {
  margin-top: var(--gp-spacing-md);
  border-top: 1px solid var(--gp-border);
  padding-top: var(--gp-spacing-sm);
}

.mobile-trip-plan-paginator :deep(.p-paginator) {
  flex-wrap: wrap;
  justify-content: center;
  gap: 4px;
  padding: 0;
  border: none;
  background: transparent;
}

.mobile-trip-plan-paginator :deep(.p-paginator .p-paginator-page),
.mobile-trip-plan-paginator :deep(.p-paginator .p-paginator-next),
.mobile-trip-plan-paginator :deep(.p-paginator .p-paginator-prev),
.mobile-trip-plan-paginator :deep(.p-paginator .p-paginator-first),
.mobile-trip-plan-paginator :deep(.p-paginator .p-paginator-last) {
  min-width: 2rem;
  width: 2rem;
  height: 2rem;
  padding: 0;
  margin: 0 1px;
  font-size: 0.8rem;
}

.mobile-trip-plan-paginator :deep(.p-paginator .p-paginator-first),
.mobile-trip-plan-paginator :deep(.p-paginator .p-paginator-last) {
  display: none;
}

.mobile-paginator-info {
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
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

.field-hint-strong {
  color: var(--gp-text-primary);
}

.date-range-help {
  margin-top: var(--gp-spacing-xs);
  margin-bottom: var(--gp-spacing-sm);
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.date-range-help .field-hint,
.date-range-help .p-error {
  margin-top: 0;
  line-height: 1.35;
}

.color-row {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
}

.preview-tag {
  border: none;
  color: var(--gp-primary-contrast);
}

.from-label-dialog-content {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
}

.no-timeline-labels-warning {
  margin-top: var(--gp-spacing-xs);
}

/* Both header actions stay on one row instead of the stacked column PageContainer uses. */
.trips-page-actions {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-md);
}

@media (max-width: 768px) {
  /* Full-width search on its own row, then status, access and refresh share one row. */
  .trips-toolbar {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
    align-items: center;
    gap: var(--gp-spacing-sm);
  }

  .trips-filters {
    display: contents;
  }

  .search-input {
    grid-area: 1 / 1 / 2 / -1;
    min-width: 0;
    width: 100%;
  }

  .trip-status-filter {
    grid-area: 2 / 1 / 3 / 2;
  }

  .trip-access-filter {
    grid-area: 2 / 2 / 3 / 3;
  }

  .refresh-button {
    grid-area: 2 / 3 / 3 / 4;
    width: 2.5rem;
    height: 2.5rem;
    padding: 0;
  }

  .refresh-button :deep(.p-button-label) {
    display: none;
  }

  .status-select {
    min-width: 0;
    width: 100%;
  }

  .trip-note {
    max-width: 220px;
  }
}

@media (max-width: 480px) {
  .trips-page-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--gp-spacing-sm);
  }

  .trips-page-actions :deep(.p-button) {
    padding: 0.55rem var(--gp-spacing-sm);
    font-size: 0.85rem;
    white-space: nowrap;
  }
}
</style>
