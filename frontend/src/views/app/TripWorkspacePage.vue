<template>
  <AppLayout variant="default">
    <PageContainer
      variant="fullwidth"
    >
      <template #header>
        <div class="workspace-page-header">
          <div class="workspace-page-title-wrap">
            <h1 class="workspace-page-title">{{ pageTitle }}</h1>
            <p class="workspace-page-subtitle">{{ pageSubtitle }}</p>
          </div>
        </div>
      </template>
      <template #actions>
        <div class="workspace-header-actions">
          <DatePicker
            v-model="selectedDateRange"
            selectionMode="range"
            :manualInput="false"
            :dateFormat="timezone.getPrimeVueDatePickerFormat()"
            :minDate="tripMinDate"
            :maxDate="tripMaxDate"
            @update:model-value="handleDateRangeChange"
            class="workspace-date-picker"
          />
          <Button
            icon="pi pi-history"
            text
            rounded
            @click="resetToTripRange"
            v-tooltip.bottom="'Reset to full trip range'"
          />
        </div>
      </template>

      <div v-if="isInitialLoading" class="workspace-loading">
        <ProgressSpinner />
      </div>

      <Message v-else-if="pageError" severity="error" :closable="false">
        {{ pageError }}
      </Message>

      <template v-else>
        <div class="workspace-tabs">
          <Button
            v-for="tab in workspaceTabs"
            :key="tab.key"
            :label="tab.label"
            :icon="tab.icon"
            :class="{ 'active-workspace-tab': activeWorkspaceTab === tab.key }"
            @click="selectWorkspaceTab(tab.key)"
          />
        </div>

        <div v-if="showOverviewSection" class="summary-strip">
          <div class="summary-chip">
            <span>Completion</span>
            <strong>{{ summaryCompletion }}</strong>
          </div>
          <div class="summary-chip">
            <span>Visited</span>
            <strong>{{ summaryVisitedCount }} / {{ summaryPlanTotal }}</strong>
          </div>
          <div class="summary-chip">
            <span>Must visits</span>
            <strong>{{ mustVisitedCount }} / {{ mustPlanTotal }} ({{ mustCompletionLabel }})</strong>
          </div>
          <div class="summary-chip">
            <span>Distance / Duration</span>
            <strong>{{ formatDistance(tripSummary?.totalDistanceMeters || 0) }} • {{ formatDuration(tripSummary?.totalTripDurationSeconds || 0) }}</strong>
          </div>
        </div>

        <BaseCard v-if="showOverviewSection || showPlanningPanelMode" class="workspace-card">
          <div class="workspace-layout">
            <div class="workspace-map">
              <div v-if="workspaceLoading" class="pane-loading">
                <ProgressSpinner />
              </div>
              <TimelineMap
                ref="timelineMapRef"
                v-else
                :pathData="hasPathData ? workspacePath : null"
                :timelineData="workspaceTimeline"
                :plannedItemsData="tripPlanMapItems"
                :showFavoritesByDefault="false"
                :showImmichByDefault="true"
                :showPlanToVisitAction="true"
                :showFavoritesContextActions="false"
                :showHeatmapControl="false"
                :enableFavoriteContextMenu="true"
                :showCurrentLocation="false"
                @plan-to-visit="handlePlanToVisit"
                @plan-item-edit="handlePlanItemEditFromMap"
                @plan-item-delete="handlePlanItemDeleteFromMap"
              />
            </div>

            <div class="workspace-timeline">
              <div v-if="workspaceLoading" class="pane-loading">
                <ProgressSpinner />
              </div>
              <div v-else-if="showPlanningPanelMode" class="planning-panel">
                <div class="planning-callout">
                  <i class="pi pi-calendar planning-panel-icon"></i>
                  <div class="planning-callout-content">
                    <h4>{{ planningPanelTitle }}</h4>
                    <p>{{ planningPanelPrimaryText }}</p>
                    <p>{{ planningPanelHintText }}</p>
                  </div>
                </div>
                <div v-if="planningPanelItems.length > 0" class="planning-list">
                  <div class="planning-list-header">
                    Planned places ({{ planningPanelItems.length }})
                  </div>
                  <div
                    v-for="item in planningPanelItems"
                    :key="item.id"
                    class="planning-list-item"
                  >
                    <button
                      type="button"
                      class="planning-list-main"
                      @click="focusPlannedItemOnMap(item)"
                    >
                      <span class="planning-list-title-row">
                        <Tag
                          :value="item.priority || 'OPTIONAL'"
                          :severity="getPrioritySeverity(item.priority)"
                        />
                        <span class="planning-list-title">{{ item.title }}</span>
                      </span>
                      <small v-if="item.notes" class="planning-list-notes">{{ item.notes }}</small>
                      <small>{{ formatPlannedDay(item.plannedDay) }}</small>
                    </button>
                    <div class="planning-list-actions">
                      <Button
                        icon="pi pi-pencil"
                        text
                        rounded
                        size="small"
                        v-tooltip.top="'Edit item'"
                        @click.stop="openEditPlanItemDialog(item)"
                      />
                      <Button
                        icon="pi pi-trash"
                        text
                        rounded
                        size="small"
                        severity="danger"
                        v-tooltip.top="'Delete item'"
                        @click.stop="confirmDeletePlanItem(item)"
                      />
                    </div>
                  </div>
                </div>
              </div>
              <TimelineContainer
                v-else
                :timeline-data="workspaceTimeline"
                :timelineNoData="!workspaceTimeline.length"
                :timelineDataLoading="workspaceLoading"
                :dateRange="activeDateRangeArray"
                :loadImmichPhotos="false"
                :showTimelineLabels="false"
              />
            </div>
          </div>
        </BaseCard>

        <ImmichLatestPhotosSection
          v-show="showTripPhotosSection"
          title="Trip Photos"
          :search-params="tripImmichSearchParams"
          :use-store-photos="true"
          empty-message="No Immich photos found for this trip range."
          @show-on-map="handleTripPhotoShowOnMap"
        />

        <BaseCard v-if="showPlanSection || isPlanningMode" class="plan-card" :title="comparisonCardTitle">
          <template #header>
            <div class="workspace-card-header">
              <h3 class="workspace-title">{{ comparisonCardTitle }}</h3>
              <div class="workspace-header-actions"></div>
            </div>
          </template>

          <div class="plan-content">
            <div class="plan-content-table">
              <TripPlanItemsTable
                :items="sortedTripPlanItems"
                :isPlanningMode="isPlanningMode"
                :isActiveTrip="isActiveTrip"
                :visitSuggestions="visitSuggestions"
                @focus-item="focusPlannedItemOnMap"
                @override="handlePlanItemOverrideFromTable"
                @edit-item="openEditPlanItemDialog"
                @delete-item="confirmDeletePlanItem"
              />
            </div>
          </div>
        </BaseCard>

      </template>
    </PageContainer>

    <Dialog
      v-model:visible="showPlanItemDialog"
      modal
      :header="editingPlanItemId ? 'Edit Plan Item' : 'Add Plan Item'"
      class="gp-dialog-md"
      @hide="resetPlanItemForm"
    >
      <div class="grid">
        <div class="col-12">
          <label for="planTitle" class="field-label">Title *</label>
          <InputText
            id="planTitle"
            v-model="planItemForm.title"
            class="w-full"
            placeholder="e.g., Sagrada Familia"
            :class="{ 'p-invalid': planItemErrors.title }"
          />
          <small v-if="planItemErrors.title" class="p-error">{{ planItemErrors.title }}</small>
        </div>

        <div class="col-12" v-if="isResolvingPlanSuggestion">
          <Message severity="info" :closable="false">Resolving place details...</Message>
        </div>

        <div class="col-12">
          <label for="planNotes" class="field-label">Notes</label>
          <Textarea
            id="planNotes"
            v-model="planItemForm.notes"
            rows="3"
            class="w-full"
            placeholder="Optional context..."
          />
        </div>

        <div class="col-12 md:col-6">
          <label for="planDate" class="field-label">Planned Day</label>
          <DatePicker
            id="planDate"
            v-model="planItemForm.plannedDay"
            :manualInput="false"
            :dateFormat="timezone.getPrimeVueDatePickerFormat()"
            class="w-full"
          />
        </div>

        <div class="col-12 md:col-6">
          <label for="planPriority" class="field-label">Priority</label>
          <Select
            id="planPriority"
            v-model="planItemForm.priority"
            :options="priorityOptions"
            optionLabel="label"
            optionValue="value"
            class="w-full"
          />
        </div>

        <div class="col-12 md:col-6">
          <label for="planOrder" class="field-label">Order</label>
          <InputNumber
            id="planOrder"
            v-model="planItemForm.orderIndex"
            :min="0"
            :maxFractionDigits="0"
            class="w-full"
          />
        </div>
      </div>

      <template #footer>
        <Button label="Cancel" icon="pi pi-times" outlined @click="showPlanItemDialog = false" />
        <Button
          :label="editingPlanItemId ? 'Update Item' : 'Create Item'"
          icon="pi pi-check"
          :loading="isSubmittingPlanItem"
          @click="submitPlanItem"
        />
      </template>
    </Dialog>

    <ConfirmDialog group="trip-workspace-plan-item" />
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { useTimezone } from '@/composables/useTimezone'
import { formatDistance, formatDuration } from '@/utils/calculationsHelpers'
import { useTripsStore } from '@/stores/trips'
import { useImmichStore } from '@/stores/immich'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import TimelineMap from '@/components/maps/TimelineMap.vue'
import TimelineContainer from '@/components/timeline/TimelineContainer.vue'
import ImmichLatestPhotosSection from '@/components/location-analytics/ImmichLatestPhotosSection.vue'
import TripPlanItemsTable from '@/components/trips/TripPlanItemsTable.vue'
import Button from 'primevue/button'
import DatePicker from 'primevue/datepicker'
import Tag from 'primevue/tag'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import InputNumber from 'primevue/inputnumber'
import Select from 'primevue/select'
import ConfirmDialog from 'primevue/confirmdialog'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const confirm = useConfirm()
const timezone = useTimezone()
const tripsStore = useTripsStore()
const immichStore = useImmichStore()

const {
  currentTrip,
  tripSummary,
  tripPlanItems,
  workspaceTimeline,
  workspacePath,
  visitSuggestions
} = storeToRefs(tripsStore)

const isInitialLoading = ref(true)
const workspaceLoading = ref(false)
const pageError = ref(null)
const selectedDateRange = ref(null)
const activeRange = ref({ start: null, end: null })
const timelineMapRef = ref(null)
const activeWorkspaceTab = ref('overview')

const showPlanItemDialog = ref(false)
const editingPlanItemId = ref(null)
const isSubmittingPlanItem = ref(false)
const planItemErrors = ref({})
const isResolvingPlanSuggestion = ref(false)

const planItemForm = ref({
  title: '',
  notes: '',
  latitude: null,
  longitude: null,
  plannedDay: null,
  priority: 'OPTIONAL',
  orderIndex: 0
})

const priorityOptions = [
  { label: 'Optional', value: 'OPTIONAL' },
  { label: 'Must', value: 'MUST' }
]

const tripId = computed(() => Number(route.params.tripId))

const pageTitle = computed(() => currentTrip.value?.name || 'Trip Planner')
const pageSubtitle = computed(() => {
  if (!currentTrip.value) return 'Workspace'
  const status = String(currentTrip.value.status || '').toLowerCase()
  const statusLabel = status ? status.charAt(0).toUpperCase() + status.slice(1) : 'Unknown'
  return `${statusLabel} • ${formatDateTime(currentTrip.value.startTime)} - ${formatDateTime(currentTrip.value.endTime)}`
})

const summaryPlanTotal = computed(() => tripSummary.value?.planItemsTotal || 0)
const summaryVisitedCount = computed(() => tripSummary.value?.planItemsVisited || 0)
const summaryCompletion = computed(() => {
  const raw = tripSummary.value?.planCompletionRate || 0
  return `${Math.round(raw)}%`
})
const mustPlanTotal = computed(() => (tripPlanItems.value || []).filter((item) => item?.priority === 'MUST').length)
const mustVisitedCount = computed(() => (tripPlanItems.value || []).filter((item) => item?.priority === 'MUST' && item?.isVisited).length)
const mustCompletionLabel = computed(() => {
  if (mustPlanTotal.value === 0) return '0%'
  return `${Math.round((mustVisitedCount.value / mustPlanTotal.value) * 100)}%`
})

const hasPathData = computed(() => Array.isArray(workspacePath.value?.points) && workspacePath.value.points.length > 0)
const hasTimelineData = computed(() => Array.isArray(workspaceTimeline.value) && workspaceTimeline.value.length > 0)
const isFutureTrip = computed(() => {
  if (!currentTrip.value?.startTime) return false
  return timezone.fromUtc(currentTrip.value.startTime).isAfter(timezone.now())
})
const isPlanningMode = computed(() => isFutureTrip.value && !hasPathData.value && !hasTimelineData.value)
const isActiveTrip = computed(() => String(currentTrip.value?.status || '').toUpperCase() === 'ACTIVE')
const showOverviewSection = computed(() => activeWorkspaceTab.value === 'overview' && !isFutureTrip.value)
const showPlanSection = computed(() => activeWorkspaceTab.value === 'plan')
const showPlanningPanelMode = computed(() => isPlanningMode.value || (showPlanSection.value && isActiveTrip.value))
const workspaceTabs = computed(() => {
  const tabs = []
  if (!isFutureTrip.value) {
    tabs.push({ key: 'overview', label: 'Overview', icon: 'pi pi-chart-line' })
  }
  tabs.push({ key: 'plan', label: 'Plan', icon: 'pi pi-list-check' })
  return tabs
})
const comparisonCardTitle = computed(() => ((isPlanningMode.value || isActiveTrip.value) ? 'Planned Stops' : 'Plan vs Actual'))
const planningPanelTitle = computed(() => (isPlanningMode.value ? 'Future trip planning mode' : 'Active trip planning mode'))
const planningPanelPrimaryText = computed(() => (
  isPlanningMode.value
    ? 'This trip has no actual timeline data yet.'
    : 'This trip is in progress. Keep adding planned stops while actual visits are matched automatically.'
))
const planningPanelHintText = computed(() => 'Right-click on the map and use Plan to visit here to add planned stops.')
const sortPlanItems = (items) => {
  const safeItems = Array.isArray(items) ? items : []
  const priorityRank = (priority) => (String(priority || '').toUpperCase() === 'MUST' ? 0 : 1)
  return [...safeItems].sort((a, b) => {
    const priorityDiff = priorityRank(a?.priority) - priorityRank(b?.priority)
    if (priorityDiff !== 0) return priorityDiff
    const orderA = a?.orderIndex ?? 0
    const orderB = b?.orderIndex ?? 0
    if (orderA !== orderB) return orderA - orderB
    return Number(a?.id || 0) - Number(b?.id || 0)
  })
}
const sortedTripPlanItems = computed(() => sortPlanItems(tripPlanItems.value))
const planningPanelItems = computed(() => {
  return sortPlanItems(tripPlanItems.value)
    .filter((item) => typeof item.latitude === 'number' && typeof item.longitude === 'number')
    .slice(0, 8)
})
const tripPlanMapItems = computed(() => {
  return (tripPlanItems.value || [])
    .filter((item) => typeof item.latitude === 'number' && typeof item.longitude === 'number')
    .map((item) => ({
      id: `trip-plan-${item.id}`,
      name: item.title || 'Planned place',
      type: 'trip-plan',
      planItemId: item.id,
      priority: item.priority || 'OPTIONAL',
      latitude: item.latitude,
      longitude: item.longitude
    }))
})
const tripMinDate = computed(() => {
  if (!currentTrip.value?.startTime || !currentTrip.value?.endTime) return null
  return timezone.convertUtcRangeToCalendarDates(currentTrip.value.startTime, currentTrip.value.endTime)[0]
})

const tripMaxDate = computed(() => {
  if (!currentTrip.value?.startTime || !currentTrip.value?.endTime) return null
  return timezone.convertUtcRangeToCalendarDates(currentTrip.value.startTime, currentTrip.value.endTime)[1]
})

const activeDateRangeArray = computed(() => {
  if (!activeRange.value.start || !activeRange.value.end) {
    return []
  }
  return [activeRange.value.start, activeRange.value.end]
})

const tripImmichSearchParams = computed(() => {
  if (isFutureTrip.value || !immichStore.isConfigured) return null
  const start = activeRange.value.start || currentTrip.value?.startTime
  const end = activeRange.value.end || currentTrip.value?.endTime
  if (!start || !end) return null
  return {
    startDate: start,
    endDate: end
  }
})
const showTripPhotosSection = computed(() => showOverviewSection.value && Boolean(tripImmichSearchParams.value))

const formatDateTime = (value) => {
  if (!value) return '—'
  return `${timezone.formatDateDisplay(value)} ${timezone.format(value, 'HH:mm')}`
}

const formatPlannedDay = (plannedDay) => {
  if (!plannedDay) return '—'
  const parsed = timezone.parseUrlDate(plannedDay, false)
  return parsed ? timezone.formatDateDisplay(parsed) : plannedDay
}

const getPrioritySeverity = (priority) => {
  return String(priority || '').toUpperCase() === 'MUST' ? 'danger' : 'warn'
}

const getPhotoCoordinates = (photo) => {
  if (!photo) return null
  if (typeof photo.latitude === 'number' && typeof photo.longitude === 'number') {
    return { latitude: photo.latitude, longitude: photo.longitude }
  }
  const exif = photo.exifInfo || photo.exif || {}
  if (typeof exif.latitude === 'number' && typeof exif.longitude === 'number') {
    return { latitude: exif.latitude, longitude: exif.longitude }
  }
  return null
}

const ensureActiveWorkspaceTab = () => {
  const availableTabKeys = workspaceTabs.value.map((tab) => tab.key)
  if (!availableTabKeys.includes(activeWorkspaceTab.value)) {
    activeWorkspaceTab.value = isFutureTrip.value ? 'plan' : (availableTabKeys[0] || 'plan')
  }
}

const selectWorkspaceTab = (tabKey) => {
  activeWorkspaceTab.value = tabKey
}

const clampRangeToTrip = (startTime, endTime, trip = currentTrip.value) => {
  if (!trip?.startTime || !trip?.endTime) {
    return { start: startTime, end: endTime }
  }

  let start = startTime || trip.startTime
  let end = endTime || trip.endTime

  const startMoment = timezone.fromUtc(start)
  const endMoment = timezone.fromUtc(end)
  const tripStart = timezone.fromUtc(trip.startTime)
  const tripEnd = timezone.fromUtc(trip.endTime)

  if (startMoment.isBefore(tripStart)) {
    start = trip.startTime
  }
  if (endMoment.isAfter(tripEnd)) {
    end = trip.endTime
  }

  if (!timezone.fromUtc(end).isAfter(timezone.fromUtc(start))) {
    start = trip.startTime
    end = trip.endTime
  }

  return { start, end }
}

const syncCalendarRange = (range) => {
  activeRange.value = { ...range }
  selectedDateRange.value = timezone.convertUtcRangeToCalendarDates(range.start, range.end)
}

const updateRouteQuery = async (range) => {
  await router.replace({
    path: route.path,
    query: {
      ...route.query,
      start: timezone.formatUrlDate(range.start),
      end: timezone.formatUrlDate(range.end)
    }
  })
}

const fetchWorkspaceRange = async (range, syncQuery = true) => {
  workspaceLoading.value = true
  try {
    await Promise.all([
      tripsStore.fetchWorkspaceTimeline(tripId.value, range.start, range.end),
      tripsStore.fetchWorkspacePath(tripId.value, range.start, range.end)
    ])
    if (syncQuery) {
      await updateRouteQuery(range)
    }
  } finally {
    workspaceLoading.value = false
  }
}

const resolveInitialRange = () => {
  const trip = currentTrip.value
  if (!trip) {
    return { start: null, end: null }
  }

  const queryStart = timezone.parseUrlDate(route.query.start, false)
  const queryEnd = timezone.parseUrlDate(route.query.end, true)
  return clampRangeToTrip(queryStart || trip.startTime, queryEnd || trip.endTime, trip)
}

const refreshVisitComparisons = async () => {
  try {
    await tripsStore.fetchVisitSuggestions(tripId.value)
    await Promise.all([
      tripsStore.fetchTripSummary(tripId.value),
      tripsStore.fetchTripPlanItems(tripId.value)
    ])
  } catch (error) {
    // Non-blocking: comparison hints should not break workspace load.
  }
}

const handleTripPhotoShowOnMap = (photo) => {
  const coords = getPhotoCoordinates(photo)
  if (!photo || !coords) {
    return
  }

  if (activeWorkspaceTab.value !== 'overview' && !isFutureTrip.value) {
    activeWorkspaceTab.value = 'overview'
  }

  const mapInstance = timelineMapRef.value?.map?.value || timelineMapRef.value?.map
  if (!mapInstance || typeof mapInstance.setView !== 'function') {
    return
  }

  mapInstance.setView([coords.latitude, coords.longitude], 16, { animate: true })
}

const loadWorkspace = async () => {
  isInitialLoading.value = true
  pageError.value = null

  try {
    await Promise.all([
      tripsStore.fetchTrip(tripId.value),
      tripsStore.fetchTripSummary(tripId.value),
      tripsStore.fetchTripPlanItems(tripId.value)
    ])

    const initialRange = resolveInitialRange()
    if (!initialRange.start || !initialRange.end) {
      throw new Error('Trip date range is invalid')
    }

    syncCalendarRange(initialRange)
    await fetchWorkspaceRange(initialRange, true)
    await refreshVisitComparisons()
  } catch (error) {
    pageError.value = error.response?.data?.message || error.message || 'Failed to load trip planner'
  } finally {
    isInitialLoading.value = false
  }
}

const handleDateRangeChange = async (value) => {
  if (!value || !Array.isArray(value) || value.length < 2 || !value[0] || !value[1]) {
    return
  }

  const { start, end } = timezone.createDateRangeFromPicker(value[0], value[1])
  const clamped = clampRangeToTrip(start, end)
  syncCalendarRange(clamped)
  await fetchWorkspaceRange(clamped, true)
}

const resetToTripRange = async () => {
  if (!currentTrip.value) return
  const range = clampRangeToTrip(currentTrip.value.startTime, currentTrip.value.endTime)
  syncCalendarRange(range)
  await fetchWorkspaceRange(range, true)
}

const applyPlanSuggestionToForm = async (suggestion) => {
  if (!suggestion) {
    return
  }

  const resolvedTitle = suggestion.title || planItemForm.value.title || 'Planned place'
  planItemForm.value.title = resolvedTitle
  planItemForm.value.latitude = typeof suggestion.latitude === 'number' ? suggestion.latitude : planItemForm.value.latitude
  planItemForm.value.longitude = typeof suggestion.longitude === 'number' ? suggestion.longitude : planItemForm.value.longitude
}

const handlePlanToVisit = async (event) => {
  const lat = event?.latlng?.lat
  const lon = event?.latlng?.lng
  if (typeof lat !== 'number' || typeof lon !== 'number') {
    return
  }

  resetPlanItemForm()
  showPlanItemDialog.value = true
  planItemForm.value.latitude = lat
  planItemForm.value.longitude = lon

  isResolvingPlanSuggestion.value = true
  try {
    const suggestion = await tripsStore.getPlanSuggestion(lat, lon)
    if (suggestion) {
      await applyPlanSuggestionToForm(suggestion)
    }
  } catch (error) {
    toast.add({
      severity: 'warn',
      summary: 'Plan suggestion unavailable',
      detail: error.response?.data?.message || error.message || 'You can still edit title and save.',
      life: 3000
    })
  } finally {
    isResolvingPlanSuggestion.value = false
  }
}

const focusPlannedItemOnMap = (item) => {
  if (!item || typeof item.latitude !== 'number' || typeof item.longitude !== 'number') {
    return
  }

  const mapInstance = timelineMapRef.value?.map?.value || timelineMapRef.value?.map
  if (!mapInstance || typeof mapInstance.setView !== 'function') {
    return
  }

  mapInstance.setView([item.latitude, item.longitude], 16, { animate: true })
}

const resolvePlanItemIdFromContext = (contextPayload) => {
  const contextItem = contextPayload?.item || contextPayload?.favorite || contextPayload
  if (!contextItem) return null

  if (contextItem.planItemId !== undefined && contextItem.planItemId !== null) {
    return Number(contextItem.planItemId)
  }

  const rawId = String(contextItem.id || '')
  if (rawId.startsWith('trip-plan-')) {
    const parsed = Number(rawId.replace('trip-plan-', ''))
    return Number.isFinite(parsed) ? parsed : null
  }

  return null
}

const findPlanItemById = (planItemId) => {
  if (planItemId === null || planItemId === undefined) return null
  return (tripPlanItems.value || []).find((item) => Number(item.id) === Number(planItemId)) || null
}

const handlePlanItemEditFromMap = (contextPayload) => {
  const planItemId = resolvePlanItemIdFromContext(contextPayload)
  const planItem = findPlanItemById(planItemId)
  if (!planItem) {
    return
  }
  openEditPlanItemDialog(planItem)
}

const handlePlanItemDeleteFromMap = (contextPayload) => {
  const planItemId = resolvePlanItemIdFromContext(contextPayload)
  const planItem = findPlanItemById(planItemId)
  if (!planItem) {
    return
  }
  confirmDeletePlanItem(planItem)
}

const handlePlanItemOverrideFromTable = (payload) => {
  const item = payload?.item
  const action = payload?.action
  if (!item || !action) {
    return
  }
  applyVisitOverride(item, action)
}

const resetPlanItemForm = () => {
  planItemForm.value = {
    title: '',
    notes: '',
    latitude: null,
    longitude: null,
    plannedDay: null,
    priority: 'OPTIONAL',
    orderIndex: 0
  }
  planItemErrors.value = {}
  editingPlanItemId.value = null
}

const parsePlannedDayToDate = (plannedDay) => {
  if (!plannedDay) return null
  return new Date(`${plannedDay}T12:00:00`)
}

const formatCalendarDate = (dateValue) => {
  if (!dateValue) return null
  const year = dateValue.getFullYear()
  const month = String(dateValue.getMonth() + 1).padStart(2, '0')
  const day = String(dateValue.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const openEditPlanItemDialog = (item) => {
  resetPlanItemForm()
  editingPlanItemId.value = item.id
  planItemForm.value = {
    title: item.title || '',
    notes: item.notes || '',
    latitude: item.latitude ?? null,
    longitude: item.longitude ?? null,
    plannedDay: parsePlannedDayToDate(item.plannedDay),
    priority: item.priority || 'OPTIONAL',
    orderIndex: item.orderIndex ?? 0
  }

  showPlanItemDialog.value = true
}

const validatePlanItem = () => {
  planItemErrors.value = {}

  if (!planItemForm.value.title || !planItemForm.value.title.trim()) {
    planItemErrors.value.title = 'Title is required'
  }

  return Object.keys(planItemErrors.value).length === 0
}

const submitPlanItem = async () => {
  if (!validatePlanItem()) return

  isSubmittingPlanItem.value = true
  try {
    const payload = {
      title: planItemForm.value.title.trim(),
      notes: planItemForm.value.notes?.trim() || null,
      latitude: planItemForm.value.latitude ?? null,
      longitude: planItemForm.value.longitude ?? null,
      plannedDay: formatCalendarDate(planItemForm.value.plannedDay),
      priority: planItemForm.value.priority || 'OPTIONAL',
      orderIndex: planItemForm.value.orderIndex ?? 0
    }

    if (editingPlanItemId.value) {
      await tripsStore.updateTripPlanItem(tripId.value, editingPlanItemId.value, payload)
      toast.add({
        severity: 'success',
        summary: 'Plan Item Updated',
        detail: 'Trip plan item updated successfully',
        life: 2500
      })
    } else {
      await tripsStore.createTripPlanItem(tripId.value, payload)
      toast.add({
        severity: 'success',
        summary: 'Plan Item Added',
        detail: 'Trip plan item added successfully',
        life: 2500
      })
    }

    await Promise.all([
      tripsStore.fetchTripSummary(tripId.value),
      refreshVisitComparisons()
    ])
    showPlanItemDialog.value = false
    resetPlanItemForm()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Failed to Save Plan Item',
      detail: error.response?.data?.message || error.message || 'Request failed',
      life: 5000
    })
  } finally {
    isSubmittingPlanItem.value = false
  }
}

const confirmDeletePlanItem = (item) => {
  confirm.require({
    group: 'trip-workspace-plan-item',
    message: `Delete plan item "${item.title}"?`,
    header: 'Delete Plan Item',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: async () => {
      try {
        await tripsStore.deleteTripPlanItem(tripId.value, item.id)
        await Promise.all([
          tripsStore.fetchTripSummary(tripId.value),
          refreshVisitComparisons()
        ])
        toast.add({
          severity: 'success',
          summary: 'Plan Item Deleted',
          detail: 'Trip plan item removed',
          life: 2500
        })
      } catch (error) {
        toast.add({
          severity: 'error',
          summary: 'Failed to Delete Plan Item',
          detail: error.response?.data?.message || error.message || 'Delete failed',
          life: 5000
        })
      }
    }
  })
}

const applyVisitOverride = async (item, action) => {
  try {
    await tripsStore.applyVisitOverride(
      tripId.value,
      item.id,
      action,
      action === 'CONFIRM_VISITED' ? timezone.now().utc().toISOString() : null
    )
    await Promise.all([
      tripsStore.fetchTripSummary(tripId.value),
      refreshVisitComparisons()
    ])
    toast.add({
      severity: 'success',
      summary: 'Visit Status Updated',
      detail: 'Plan item visit status has been updated',
      life: 2500
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Failed to Update Visit Status',
      detail: error.response?.data?.message || error.message || 'Update failed',
      life: 5000
    })
  }
}

onMounted(async () => {
  await loadWorkspace()
  ensureActiveWorkspaceTab()
})

watch(workspaceTabs, () => {
  ensureActiveWorkspaceTab()
}, { deep: true })
</script>

<style scoped>
.workspace-loading {
  display: flex;
  justify-content: center;
  padding: var(--gp-spacing-xl);
}

.workspace-page-header {
  display: flex;
  align-items: flex-start;
  gap: var(--gp-spacing-sm);
}

.workspace-page-title-wrap {
  min-width: 0;
}

.workspace-page-title {
  margin: 0;
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  line-height: 1.2;
}

.workspace-page-subtitle {
  margin: var(--gp-spacing-xs) 0 0;
  font-size: 1rem;
  color: var(--gp-text-secondary);
  line-height: 1.4;
}

.workspace-tabs {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  margin-top: var(--gp-spacing-sm);
  margin-bottom: var(--gp-spacing-sm);
  position: relative;
  z-index: 2;
}

.workspace-tabs .p-button {
  min-width: 108px;
  background: transparent;
  border: 1px solid var(--gp-border-light);
  color: var(--gp-text-secondary);
  box-shadow: none;
  position: relative;
  z-index: 1;
}

.workspace-tabs .active-workspace-tab {
  background: var(--gp-primary);
  border-color: var(--gp-primary);
  color: var(--gp-surface-white);
}

.workspace-tabs .p-button:not(.active-workspace-tab):hover {
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-primary);
  color: var(--gp-text-primary);
  box-shadow: none;
}

.workspace-tabs .p-button:not(.active-workspace-tab):focus,
.workspace-tabs .p-button:not(.active-workspace-tab):focus-visible,
.workspace-tabs .active-workspace-tab:hover,
.workspace-tabs .active-workspace-tab:focus,
.workspace-tabs .active-workspace-tab:focus-visible {
  border: 1px solid var(--gp-primary);
  box-shadow: none;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--gp-spacing-sm);
  margin-bottom: var(--gp-spacing-sm);
}

.summary-chip {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-small);
  background: var(--gp-surface-light);
  padding: var(--gp-spacing-sm);
  min-height: 0;
}

.summary-chip span {
  color: var(--gp-text-secondary);
  font-size: 0.74rem;
  line-height: 1.1;
}

.summary-chip strong {
  color: var(--gp-text-primary);
  font-size: 0.9rem;
  line-height: 1.2;
}

.workspace-card,
.plan-card {
  margin-bottom: var(--gp-spacing-md);
}

.plan-content {
  display: block;
}

.plan-content-table {
  min-width: 0;
}

.workspace-card :deep(.p-card-body) {
  padding-left: 0;
  padding-right: 0;
}

.workspace-card :deep(.p-card-content) {
  padding-left: 0;
  padding-right: 0;
}

.workspace-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gp-spacing-md);
  width: 100%;
  padding: var(--gp-spacing-md) var(--gp-spacing-lg);
  border-bottom: 1px solid var(--gp-border-light);
  background: var(--gp-surface-light);
}

.workspace-title {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
  color: var(--gp-text-primary);
}

.workspace-header-actions {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  flex-wrap: wrap;
}

.workspace-date-picker {
  min-width: 260px;
}

.workspace-layout {
  display: flex;
  gap: 0;
  min-height: 0;
}

.workspace-map,
.workspace-timeline {
  border: 0;
  border-radius: 0;
  overflow: hidden;
  min-height: 350px;
  background: var(--gp-surface-white);
}

.workspace-map {
  flex: 5;
  margin-top: 0.5rem;
  margin-left: 0.5rem;
  margin-right: 1rem;
  max-height: 70vh;
  min-width: 0;
}

.workspace-timeline {
  flex: 1;
  margin-top: 0.5rem;
  margin-right: 0.5rem;
  min-width: 0;
  max-width: none;
  max-height: 70vh;
  overflow-y: auto;
}

.planning-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  align-items: stretch;
  gap: var(--gp-spacing-sm);
  padding: var(--gp-spacing-lg);
  color: var(--gp-text-secondary);
}

.planning-callout {
  display: flex;
  align-items: flex-start;
  gap: var(--gp-spacing-sm);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-light);
  padding: var(--gp-spacing-sm) var(--gp-spacing-md);
}

.planning-callout-content {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.planning-panel h4 {
  margin: 0;
  color: var(--gp-text-primary);
}

.planning-panel p {
  margin: 0;
}

.planning-panel-icon {
  font-size: 2rem;
  color: var(--gp-text-muted);
}

.planning-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-xs);
  margin-top: var(--gp-spacing-sm);
}

.planning-list-header {
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.planning-list-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--gp-spacing-xs);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-small);
  background: var(--gp-surface-light);
  padding: var(--gp-spacing-xs) var(--gp-spacing-sm);
}

.planning-list-main {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.1rem;
  border: 0;
  background: transparent;
  color: var(--gp-text-primary);
  padding: 0;
  text-align: left;
  cursor: pointer;
  flex: 1;
}

.planning-list-main:hover {
  color: var(--gp-primary, #1a56db);
}

.planning-list-title-row {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
}

.planning-list-title {
  font-weight: 600;
}

.planning-list-item small {
  color: var(--gp-text-secondary);
}

.planning-list-notes {
  display: block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.planning-list-actions {
  display: flex;
  align-items: center;
  gap: 0.1rem;
}

.pane-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--gp-text-secondary);
  gap: var(--gp-spacing-sm);
}

.field-label {
  display: block;
  margin-bottom: var(--gp-spacing-xs);
  color: var(--gp-text-secondary);
  font-weight: 500;
}

@media (max-width: 1279px) {
  .summary-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workspace-map {
    flex: 5;
    max-height: 70vh;
  }

  .workspace-timeline {
    flex: 2;
  }
}

@media (min-width: 1280px) and (max-width: 1599px) {
  .workspace-map {
    flex: 5;
  }

  .workspace-timeline {
    flex: 2;
  }
}

@media (min-width: 1600px) {
  .workspace-map {
    flex: 4;
    max-height: 75vh;
  }

  .workspace-timeline {
    flex: 1;
  }
}

@media (max-width: 1024px) {
  .workspace-layout {
    flex-direction: column;
    min-height: auto;
  }

  .workspace-map,
  .workspace-timeline {
    min-height: 460px;
    max-height: none;
    min-width: 0;
    max-width: none;
  }

}

@media (max-width: 768px) {
  .workspace-tabs {
    flex-wrap: wrap;
  }

  .summary-strip {
    grid-template-columns: 1fr;
  }

  .workspace-card-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .workspace-date-picker {
    min-width: 100%;
    width: 100%;
  }

  .workspace-header-actions {
    width: 100%;
  }
}
</style>
