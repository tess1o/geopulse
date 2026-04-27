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
          <Button
            v-if="canReconstructTrip"
            icon="pi pi-map"
            outlined
            label="Add Missing Trip Data"
            @click="openReconstructionDialog"
          />
          <Button
            v-if="isUnplannedTrip && isOwner"
            icon="pi pi-calendar-plus"
            label="Set Trip Dates"
            class="gp-btn-primary"
            @click="openTripScheduling"
          />
          <Button
            v-if="isOwner"
            icon="pi pi-users"
            outlined
            label="Collaborators"
            @click="openCollaboratorsDialog"
          />
          <template v-else>
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
          </template>
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

        <Message v-if="isUnplannedTrip" severity="info" :closable="false" class="unplanned-trip-banner">
          This trip is unplanned. Add planned stops now, then set trip dates to enable timeline, path, and analytics.
        </Message>
        <Message v-if="showAccessModeBanner" severity="warn" :closable="false" class="trip-access-banner">
          {{ accessModeBannerText }}
        </Message>

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
                :showPlanToVisitAction="canEditPlanItems"
                :showFavoritesContextActions="false"
                :showHeatmapControl="false"
                :enableFavoriteContextMenu="canEditPlanItems"
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
                        v-if="canEditPlanItems"
                        icon="pi pi-pencil"
                        text
                        rounded
                        size="small"
                        v-tooltip.top="'Edit item'"
                        @click.stop="openEditPlanItemDialog(item)"
                      />
                      <Button
                        v-if="canEditPlanItems"
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
          v-if="showTripPhotosSection"
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
              <div class="workspace-header-actions">
                <Button
                  v-if="canEditPlanItems && hasPlanItems"
                  icon="pi pi-plus"
                  label="Add Place"
                  class="gp-btn-primary"
                  @click="openCreatePlanItemDialog"
                />
              </div>
            </div>
          </template>

          <div class="plan-content">
            <div class="plan-content-table">
              <TripPlanItemsTable
                :items="sortedTripPlanItems"
                :isPlanningMode="isPlanningWorkspace"
                :isActiveTrip="isActiveTrip"
                :visitSuggestions="visitSuggestions"
                :canEdit="canEditPlanItems"
                @focus-item="focusPlannedItemOnMap"
                @override="handlePlanItemOverrideFromTable"
                @edit-item="openEditPlanItemDialog"
                @delete-item="confirmDeletePlanItem"
                @add-item="openCreatePlanItemDialog"
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
      class="gp-dialog-xl plan-item-dialog"
      @hide="resetPlanItemForm"
    >
      <div class="plan-item-dialog-layout">
        <div class="plan-item-dialog-form">
          <div v-if="isResolvingPlanSuggestion">
            <Message severity="info" :closable="false">Resolving place details...</Message>
          </div>
          <div v-if="planItemMapSourceHint">
            <Message severity="info" :closable="false">{{ planItemMapSourceHint }}</Message>
          </div>

          <div>
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

          <div>
            <label for="planNotes" class="field-label">Notes</label>
            <Textarea
              id="planNotes"
              v-model="planItemForm.notes"
              rows="3"
              class="w-full"
              placeholder="Optional context..."
            />
          </div>

          <div class="plan-item-dialog-row">
            <div>
              <label for="planDate" class="field-label">Planned Day</label>
              <DatePicker
                id="planDate"
                v-model="planItemForm.plannedDay"
                :manualInput="false"
                :dateFormat="timezone.getPrimeVueDatePickerFormat()"
                class="w-full"
              />
            </div>
            <div>
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
          </div>

          <div class="plan-item-dialog-row">
            <div>
              <label for="planOrder" class="field-label">Order</label>
              <InputNumber
                id="planOrder"
                v-model="planItemForm.orderIndex"
                :min="0"
                :maxFractionDigits="0"
                class="w-full"
              />
            </div>
            <div>
              <label class="field-label">Coordinates</label>
              <div class="plan-item-coordinate-pill">
                <span v-if="hasPlanItemCoordinates">
                  {{ Number(planItemForm.latitude).toFixed(5) }}, {{ Number(planItemForm.longitude).toFixed(5) }}
                </span>
                <span v-else>Not selected yet</span>
              </div>
            </div>
          </div>
        </div>

        <div class="plan-item-dialog-map-pane">
          <div>
            <label for="planLocationSearch" class="field-label">Search place</label>
            <AutoComplete
              id="planLocationSearch"
              ref="planLocationSearchRef"
              v-model="planItemLocationSearchQuery"
              :suggestions="planItemLocationSearchSuggestions"
              optionLabel="displayName"
              placeholder="Search saved places or providers..."
              :minLength="2"
              :delay="300"
              :loading="false"
              :showEmptyMessage="!isPlanItemLocationSearchLoading"
              class="plan-item-location-search"
              @complete="handlePlanItemLocationSearchComplete"
              @item-select="handlePlanItemLocationSearchSelect"
            >
              <template #option="{ option }">
                <div class="plan-item-location-option">
                  <div class="plan-item-location-option-title">{{ option.displayName }}</div>
                  <div class="plan-item-location-option-meta">
                    <span
                      v-if="option.groupLabel"
                      class="plan-item-location-source-chip"
                      :class="option.groupLabel === 'Saved place' ? 'plan-item-location-source-chip--saved' : 'plan-item-location-source-chip--provider'"
                    >
                      {{ option.groupLabel }}
                    </span>
                    <span v-if="option.metaLine" class="plan-item-location-option-subtitle">{{ option.metaLine }}</span>
                  </div>
                </div>
              </template>
            </AutoComplete>
            <div v-if="isPlanItemLocationSearchLoading" class="plan-item-location-search-loading" aria-live="polite">
              <i class="pi pi-spin pi-spinner plan-item-location-search-loading-icon" />
              <span>Searching places...</span>
            </div>
            <small v-if="planItemLocationSearchError" class="p-error">{{ planItemLocationSearchError }}</small>
          </div>

          <div class="plan-item-dialog-map-wrap">
            <MapContainer
              ref="planItemDialogMapRef"
              :map-id="`trip-plan-item-dialog-map-${planItemDialogMapId}`"
              :center="planItemDialogMapCenter"
              :zoom="planItemDialogMapZoom"
              :show-controls="false"
              :enable-fullscreen="false"
              height="420px"
              width="100%"
              @map-ready="handlePlanItemDialogMapReady"
              @map-click="handlePlanItemDialogMapClick"
            />
          </div>

          <small class="plan-item-dialog-map-hint">
            Search by place name or click the map to pin this stop.
          </small>
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

    <Dialog
      v-model:visible="showCollaboratorsDialog"
      modal
      header="Trip Collaborators"
      class="gp-dialog-md"
      @show="loadCollaboratorsData"
    >
      <div class="collaborators-content">
        <Message severity="info" :closable="false">
          Only invited friends can access this trip. Choose Viewer or Editor per friend.
        </Message>

        <div class="collaborator-add-row">
          <Select
            v-model="newCollaboratorFriendId"
            :options="availableFriendOptions"
            optionLabel="label"
            optionValue="value"
            placeholder="Select friend"
            class="w-full"
            filter
          />
          <Select
            v-model="newCollaboratorRole"
            :options="collaboratorRoleOptions"
            optionLabel="label"
            optionValue="value"
            class="collaborator-role-select"
          />
          <Button
            icon="pi pi-plus"
            label="Add"
            :loading="isSavingCollaborator"
            :disabled="!newCollaboratorFriendId"
            @click="addCollaborator"
          />
        </div>

        <div v-if="collaboratorsLoading" class="collaborators-loading">
          <ProgressSpinner />
        </div>

        <div v-else-if="collaborators.length === 0" class="collaborators-empty">
          No collaborators yet.
        </div>

        <div v-else class="collaborators-list">
          <div
            v-for="collaborator in collaborators"
            :key="collaborator.userId"
            class="collaborator-row"
          >
            <div class="collaborator-main">
              <strong>{{ collaborator.fullName || collaborator.email }}</strong>
              <small>{{ collaborator.email }}</small>
            </div>
            <Select
              :model-value="collaborator.accessRole"
              :options="collaboratorRoleOptions"
              optionLabel="label"
              optionValue="value"
              class="collaborator-role-select"
              @update:model-value="updateCollaboratorRole(collaborator, $event)"
            />
            <Button
              icon="pi pi-trash"
              text
              rounded
              severity="danger"
              :loading="isSavingCollaborator"
              @click="removeCollaborator(collaborator)"
            />
          </div>
        </div>
      </div>
    </Dialog>

    <TripReconstructionDialog
      :visible="showReconstructionDialog"
      mode="trip"
      :trip-id="tripId"
      :trip="currentTrip"
      :fallback-center="workspaceFallbackCenter"
      @close="showReconstructionDialog = false"
      @committed="handleReconstructionCommitted"
    />

    <Dialog
      v-model:visible="showTimelineGenerationDialog"
      modal
      header="Timeline Generation"
      class="gp-dialog-md timeline-generation-dialog"
      :closable="timelineJobCanClose"
      :dismissableMask="timelineJobCanClose"
      @hide="handleTimelineGenerationDialogHide"
    >
      <div class="timeline-generation-content">
        <div class="timeline-generation-status-row">
          <Tag :value="timelineJobStatusLabel" :severity="timelineJobStatusSeverity" />
          <small v-if="trackedTimelineJobId" class="timeline-generation-job-id">
            Job: {{ trackedTimelineJobId }}
          </small>
        </div>

        <p class="timeline-generation-step">
          {{ timelineJobCurrentStep }}
        </p>

        <ProgressBar
          :value="timelineJobProgressValue"
          :showValue="false"
        />

        <div class="timeline-generation-meta">
          <span>{{ timelineJobProgressValue }}%</span>
          <span>{{ timelineJobDurationLabel }}</span>
        </div>

        <Message
          v-if="timelineJobProgress?.errorMessage || timelineJobError"
          severity="error"
          :closable="false"
        >
          {{ timelineJobProgress?.errorMessage || timelineJobError }}
        </Message>
        <Message
          v-else-if="isRefreshingAfterTimelineJob"
          severity="info"
          :closable="false"
        >
          Refreshing workspace data...
        </Message>
      </div>

      <template #footer>
        <Button
          v-if="timelineJobStatus === 'FAILED'"
          label="Retry Status"
          icon="pi pi-refresh"
          outlined
          :disabled="!trackedTimelineJobId"
          @click="retryTimelineJobProgress"
        />
        <Button
          label="Close"
          icon="pi pi-times"
          :disabled="!timelineJobCanClose"
          @click="showTimelineGenerationDialog = false"
        />
      </template>
    </Dialog>

    <ConfirmDialog group="trip-workspace-plan-item" />
  </AppLayout>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { useTimezone } from '@/composables/useTimezone'
import { useTimelineRegeneration } from '@/composables/useTimelineRegeneration'
import { formatDistance, formatDuration } from '@/utils/calculationsHelpers'
import { useTripsStore } from '@/stores/trips'
import { useImmichStore } from '@/stores/immich'
import friendsService from '@/services/friendsService'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import TimelineMap from '@/components/maps/TimelineMap.vue'
import MapContainer from '@/components/maps/MapContainer.vue'
import TimelineContainer from '@/components/timeline/TimelineContainer.vue'
import ImmichLatestPhotosSection from '@/components/location-analytics/ImmichLatestPhotosSection.vue'
import TripPlanItemsTable from '@/components/trips/TripPlanItemsTable.vue'
import TripReconstructionDialog from '@/components/trips/TripReconstructionDialog.vue'
import L from 'leaflet'
import maplibregl from 'maplibre-gl'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'
import Button from 'primevue/button'
import DatePicker from 'primevue/datepicker'
import Tag from 'primevue/tag'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import ProgressBar from 'primevue/progressbar'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import InputNumber from 'primevue/inputnumber'
import Select from 'primevue/select'
import AutoComplete from 'primevue/autocomplete'
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
const planItemDialogMapRef = ref(null)
const planLocationSearchRef = ref(null)
const activeWorkspaceTab = ref('overview')

const showPlanItemDialog = ref(false)
const editingPlanItemId = ref(null)
const isSubmittingPlanItem = ref(false)
const planItemErrors = ref({})
const isResolvingPlanSuggestion = ref(false)
const planItemLocationSource = ref(null)
const planItemLocationSearchQuery = ref('')
const planItemLocationSearchSuggestions = ref([])
const isPlanItemLocationSearchLoading = ref(false)
const planItemLocationSearchError = ref('')
const planItemLocationSearchRequestToken = ref(0)
const planItemSuggestionRequestToken = ref(0)
const planItemDialogMapId = ref(Math.random().toString(36).slice(2, 10))
const planItemDialogMapCenter = ref([37.7749, -122.4194])
const planItemDialogMapZoom = ref(13)
const planItemDialogMapInstance = ref(null)
const planItemDialogMapAdapter = ref(null)
const showCollaboratorsDialog = ref(false)
const showReconstructionDialog = ref(false)
const showTimelineGenerationDialog = ref(false)
const isRefreshingAfterTimelineJob = ref(false)
const collaboratorsLoading = ref(false)
const isSavingCollaborator = ref(false)
const collaborators = ref([])
const availableFriends = ref([])
const newCollaboratorFriendId = ref(null)
const newCollaboratorRole = ref('VIEW')
const {
  currentJobId: trackedTimelineJobId,
  jobProgress: timelineJobProgress,
  jobError: timelineJobError,
  trackExistingTimelineJob,
  refreshCurrentJobProgress,
  clearTrackedTimelineJob
} = useTimelineRegeneration()

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

const collaboratorRoleOptions = [
  { label: 'Viewer', value: 'VIEW' },
  { label: 'Editor', value: 'EDIT' }
]

const tripId = computed(() => Number(route.params.tripId))
const isOwner = computed(() => Boolean(currentTrip.value?.isOwner) || String(currentTrip.value?.accessRole || '').toUpperCase() === 'OWNER')
const accessRole = computed(() => String(currentTrip.value?.accessRole || (isOwner.value ? 'OWNER' : 'VIEW')).toUpperCase())
const canEditPlanItems = computed(() => isOwner.value || accessRole.value === 'EDIT')
const canReconstructTrip = computed(() => isOwner.value && !isUnplannedTrip.value)
const showAccessModeBanner = computed(() => !isOwner.value)
const accessModeBannerText = computed(() => {
  if (accessRole.value === 'EDIT') {
    return 'Editor access: you can update planned stops and visit states, but only the owner can change trip metadata.'
  }
  return 'Viewer access: this trip is read-only for you.'
})

const pageTitle = computed(() => currentTrip.value?.name || 'Trip Planner')
const pageSubtitle = computed(() => {
  if (!currentTrip.value) return 'Workspace'

  if (isUnplannedTrip.value) {
    return 'Unplanned • Set trip dates later to unlock timeline and analytics'
  }

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
const isUnplannedTrip = computed(() => {
  const status = String(currentTrip.value?.status || '').toUpperCase()
  return status === 'UNPLANNED' || (!currentTrip.value?.startTime && !currentTrip.value?.endTime)
})
const isFutureTrip = computed(() => {
  if (isUnplannedTrip.value) return false
  if (!currentTrip.value?.startTime) return false
  return timezone.fromUtc(currentTrip.value.startTime).isAfter(timezone.now())
})
const isPlanningMode = computed(() => isFutureTrip.value && !hasPathData.value && !hasTimelineData.value)
const isPlanningWorkspace = computed(() => isUnplannedTrip.value || isPlanningMode.value)
const isActiveTrip = computed(() => String(currentTrip.value?.status || '').toUpperCase() === 'ACTIVE')
const showOverviewSection = computed(() => activeWorkspaceTab.value === 'overview' && !isFutureTrip.value && !isUnplannedTrip.value)
const showPlanSection = computed(() => activeWorkspaceTab.value === 'plan')
const showPlanningPanelMode = computed(() => isPlanningWorkspace.value || (showPlanSection.value && isActiveTrip.value))
const workspaceTabs = computed(() => {
  if (isUnplannedTrip.value) {
    return [{ key: 'plan', label: 'Plan', icon: 'pi pi-list-check' }]
  }

  const tabs = []
  if (!isFutureTrip.value) {
    tabs.push({ key: 'overview', label: 'Overview', icon: 'pi pi-chart-line' })
  }
  tabs.push({ key: 'plan', label: 'Plan', icon: 'pi pi-list-check' })
  return tabs
})
const comparisonCardTitle = computed(() => ((isPlanningWorkspace.value || isActiveTrip.value) ? 'Planned Stops' : 'Plan vs Actual'))
const planningPanelTitle = computed(() => {
  if (isUnplannedTrip.value) return 'Unplanned trip planning mode'
  return isPlanningMode.value ? 'Future trip planning mode' : 'Active trip planning mode'
})
const planningPanelPrimaryText = computed(() => (
  isUnplannedTrip.value
    ? 'This trip has no schedule yet. Build your place plan first, then set trip dates when you are ready.'
    : (isPlanningMode.value
      ? 'This trip has no actual timeline data yet.'
      : 'This trip is in progress. Keep adding planned stops while actual visits are matched automatically.')
))
const planningPanelHintText = computed(() => {
  if (!canEditPlanItems.value) {
    return 'You have read-only access to this trip plan.'
  }
  return 'Use Add Place to search and add planned stops quickly, or right-click the map as a shortcut.'
})
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
const hasPlanItems = computed(() => sortedTripPlanItems.value.length > 0)

const parsePlanItemCoordinate = (value, min, max) => {
  if (value === null || value === undefined || value === '') {
    return null
  }

  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    return null
  }

  if (Number.isFinite(min) && parsed < min) {
    return null
  }

  if (Number.isFinite(max) && parsed > max) {
    return null
  }

  return parsed
}

const hasPlanItemCoordinates = computed(() => {
  const latitude = parsePlanItemCoordinate(planItemForm.value.latitude, -90, 90)
  const longitude = parsePlanItemCoordinate(planItemForm.value.longitude, -180, 180)
  return latitude !== null && longitude !== null
})
const planItemMapSourceHint = computed(() => {
  if (!planItemLocationSource.value) return ''

  const latitude = Number(planItemLocationSource.value.latitude)
  const longitude = Number(planItemLocationSource.value.longitude)
  const source = planItemLocationSource.value.source
  const sourceLabel = source === 'context-menu'
    ? 'Pinned from map (context menu)'
    : source === 'dialog-map'
      ? 'Pinned from map (dialog)'
      : 'Pinned from map'

  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return `${sourceLabel}.`
  }

  return `${sourceLabel}: ${latitude.toFixed(5)}, ${longitude.toFixed(5)}`
})
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
const workspaceFallbackCenter = computed(() => {
  const firstPathPoint = workspacePath.value?.points?.[0]
  if (
    firstPathPoint
    && typeof firstPathPoint.latitude === 'number'
    && typeof firstPathPoint.longitude === 'number'
  ) {
    return [firstPathPoint.latitude, firstPathPoint.longitude]
  }

  const firstPlanPoint = (tripPlanItems.value || []).find((item) => (
    typeof item.latitude === 'number' && typeof item.longitude === 'number'
  ))
  if (firstPlanPoint) {
    return [firstPlanPoint.latitude, firstPlanPoint.longitude]
  }

  return [37.7749, -122.4194]
})
const tripMinDate = computed(() => {
  if (isUnplannedTrip.value) return null
  if (!currentTrip.value?.startTime || !currentTrip.value?.endTime) return null
  return timezone.convertUtcRangeToCalendarDates(currentTrip.value.startTime, currentTrip.value.endTime)[0]
})

const tripMaxDate = computed(() => {
  if (isUnplannedTrip.value) return null
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
  if (isFutureTrip.value || isUnplannedTrip.value || !immichStore.isConfigured) return null
  const start = activeRange.value.start || currentTrip.value?.startTime
  const end = activeRange.value.end || currentTrip.value?.endTime
  if (!start || !end) return null
  return {
    startDate: start,
    endDate: end
  }
})
const showTripPhotosSection = computed(() => (
  showOverviewSection.value &&
  !isUnplannedTrip.value &&
  Boolean(tripImmichSearchParams.value)
))
const availableFriendOptions = computed(() => {
  const selectedIds = new Set((collaborators.value || []).map((item) => String(item.userId)))
  return (availableFriends.value || [])
    .filter((friend) => !selectedIds.has(String(friend.friendId)))
    .map((friend) => ({
      value: friend.friendId,
      label: friend.fullName || friend.email || String(friend.friendId)
    }))
})
const timelineJobStatus = computed(() => String(timelineJobProgress.value?.status || '').toUpperCase())
const timelineJobCanClose = computed(() => {
  if (timelineJobError.value) return true
  if (!trackedTimelineJobId.value) return true
  return timelineJobStatus.value === 'COMPLETED' || timelineJobStatus.value === 'FAILED'
})
const timelineJobStatusLabel = computed(() => {
  switch (timelineJobStatus.value) {
    case 'QUEUED':
      return 'Queued'
    case 'RUNNING':
      return 'Running'
    case 'COMPLETED':
      return 'Completed'
    case 'FAILED':
      return 'Failed'
    default:
      return 'Starting'
  }
})
const timelineJobStatusSeverity = computed(() => {
  switch (timelineJobStatus.value) {
    case 'QUEUED':
      return 'info'
    case 'RUNNING':
      return 'warn'
    case 'COMPLETED':
      return 'success'
    case 'FAILED':
      return 'danger'
    default:
      return 'secondary'
  }
})
const timelineJobCurrentStep = computed(() => (
  timelineJobProgress.value?.currentStep
  || (timelineJobStatus.value === 'FAILED' ? 'Timeline generation failed.' : 'Preparing timeline generation...')
))
const timelineJobProgressValue = computed(() => {
  const raw = Number(timelineJobProgress.value?.progressPercentage)
  if (!Number.isFinite(raw)) return 0
  return Math.min(100, Math.max(0, Math.round(raw)))
})
const timelineJobDurationLabel = computed(() => {
  const durationMs = Number(timelineJobProgress.value?.durationMs)
  if (!Number.isFinite(durationMs) || durationMs < 0) {
    return 'Duration: —'
  }
  return `Duration: ${formatJobDuration(durationMs)}`
})

const formatDateTime = (value) => {
  if (!value) return '—'
  return `${timezone.formatDateDisplay(value)} ${timezone.formatTime(value)}`
}

const formatJobDuration = (durationMs) => {
  const totalSeconds = Math.floor(durationMs / 1000)
  if (totalSeconds < 60) {
    return `${totalSeconds}s`
  }
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  if (minutes < 60) {
    return seconds > 0 ? `${minutes}m ${seconds}s` : `${minutes}m`
  }
  const hours = Math.floor(minutes / 60)
  const minutesRemainder = minutes % 60
  return minutesRemainder > 0 ? `${hours}h ${minutesRemainder}m` : `${hours}h`
}

const formatPlannedDay = (plannedDay) => {
  if (!plannedDay) return '—'
  const parsed = timezone.parseUrlDate(plannedDay, false)
  return parsed ? timezone.formatDateDisplay(parsed) : plannedDay
}

const getPrioritySeverity = (priority) => {
  return String(priority || '').toUpperCase() === 'MUST' ? 'danger' : 'warn'
}

const ensurePlanEditAccess = (message = 'You have read-only access to this trip plan.') => {
  if (canEditPlanItems.value) return true
  toast.add({
    severity: 'warn',
    summary: 'Read-Only Access',
    detail: message,
    life: 3500
  })
  return false
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

const openTripScheduling = () => {
  if (!isOwner.value) return
  if (!tripId.value) return
  router.push({
    path: '/app/trips',
    query: {
      action: 'edit',
      tripId: String(tripId.value)
    }
  })
}

const openCollaboratorsDialog = () => {
  if (!isOwner.value) return
  showCollaboratorsDialog.value = true
}

const openReconstructionDialog = () => {
  if (!canReconstructTrip.value) return
  showReconstructionDialog.value = true
}

const loadCollaboratorsData = async () => {
  if (!isOwner.value) return
  collaboratorsLoading.value = true
  try {
    const [loadedCollaborators, friendsResponse] = await Promise.all([
      tripsStore.fetchTripCollaborators(tripId.value),
      friendsService.getFriends()
    ])
    collaborators.value = Array.isArray(loadedCollaborators) ? loadedCollaborators : []
    availableFriends.value = Array.isArray(friendsResponse?.data) ? friendsResponse.data : []
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Failed to Load Collaborators',
      detail: error.response?.data?.message || error.message || 'Could not load collaborators.',
      life: 5000
    })
  } finally {
    collaboratorsLoading.value = false
  }
}

const addCollaborator = async () => {
  if (!newCollaboratorFriendId.value) return
  isSavingCollaborator.value = true
  try {
    await tripsStore.setTripCollaborator(tripId.value, newCollaboratorFriendId.value, newCollaboratorRole.value)
    newCollaboratorFriendId.value = null
    newCollaboratorRole.value = 'VIEW'
    await loadCollaboratorsData()
    toast.add({
      severity: 'success',
      summary: 'Collaborator Added',
      detail: 'Trip collaborator updated successfully.',
      life: 2500
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Failed to Add Collaborator',
      detail: error.response?.data?.message || error.message || 'Request failed.',
      life: 5000
    })
  } finally {
    isSavingCollaborator.value = false
  }
}

const updateCollaboratorRole = async (collaborator, nextRole) => {
  if (!collaborator?.userId || !nextRole) return
  const previousRole = collaborator.accessRole
  collaborator.accessRole = nextRole
  isSavingCollaborator.value = true
  try {
    await tripsStore.setTripCollaborator(tripId.value, collaborator.userId, nextRole)
    toast.add({
      severity: 'success',
      summary: 'Role Updated',
      detail: 'Collaborator role updated.',
      life: 2200
    })
  } catch (error) {
    collaborator.accessRole = previousRole
    toast.add({
      severity: 'error',
      summary: 'Failed to Update Role',
      detail: error.response?.data?.message || error.message || 'Request failed.',
      life: 5000
    })
  } finally {
    isSavingCollaborator.value = false
  }
}

const removeCollaborator = async (collaborator) => {
  if (!collaborator?.userId) return
  isSavingCollaborator.value = true
  try {
    await tripsStore.removeTripCollaborator(tripId.value, collaborator.userId)
    collaborators.value = collaborators.value.filter((item) => String(item.userId) !== String(collaborator.userId))
    toast.add({
      severity: 'success',
      summary: 'Collaborator Removed',
      detail: 'Access revoked.',
      life: 2200
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Failed to Remove Collaborator',
      detail: error.response?.data?.message || error.message || 'Request failed.',
      life: 5000
    })
  } finally {
    isSavingCollaborator.value = false
  }
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
  if (!range?.start || !range?.end) {
    activeRange.value = { start: null, end: null }
    selectedDateRange.value = null
    return
  }

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
    if (isUnplannedTrip.value) {
      visitSuggestions.value = []
      await Promise.all([
        tripsStore.fetchTripSummary(tripId.value),
        tripsStore.fetchTripPlanItems(tripId.value)
      ])
      return
    }

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

  const setView = timelineMapRef.value?.setView
  if (typeof setView !== 'function') {
    return
  }

  setView([coords.latitude, coords.longitude], 16, { animate: true })
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

    if (isUnplannedTrip.value) {
      workspaceTimeline.value = []
      workspacePath.value = {
        points: [],
        segments: [],
        pointCount: 0
      }
      visitSuggestions.value = []
      syncCalendarRange({ start: null, end: null })
      return
    }

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
  if (isUnplannedTrip.value) {
    return
  }

  if (!value || !Array.isArray(value) || value.length < 2 || !value[0] || !value[1]) {
    return
  }

  const { start, end } = timezone.createDateRangeFromPicker(value[0], value[1])
  const clamped = clampRangeToTrip(start, end)
  syncCalendarRange(clamped)
  await fetchWorkspaceRange(clamped, true)
}

const resetToTripRange = async () => {
  if (isUnplannedTrip.value) return
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

const isLocalSearchSource = (sourceType) => (
  sourceType === 'favorite-point' || sourceType === 'favorite-area' || sourceType === 'geocoding'
)

const buildProviderMetaLine = (subtitle, providerName) => {
  const safeSubtitle = subtitle?.trim()
  if (!safeSubtitle) {
    return null
  }

  const safeProviderName = providerName?.trim()
  if (!safeProviderName) {
    return safeSubtitle
  }

  const providerLower = safeProviderName.toLowerCase()
  const parts = safeSubtitle
    .split('•')
    .map((part) => part.trim())
    .filter((part) => part && part.toLowerCase() !== providerLower)

  return parts.length > 0 ? parts.join(' • ') : null
}

const buildPlanItemLocationMeta = (suggestion) => {
  const sourceType = suggestion?.sourceType || ''
  if (isLocalSearchSource(sourceType)) {
    return {
      groupLabel: 'Saved place',
      metaLine: suggestion?.subtitle?.trim() || null
    }
  }

  const providerName = suggestion?.providerName?.trim() || ''
  return {
    groupLabel: providerName ? `Provider: ${providerName}` : 'Provider',
    metaLine: buildProviderMetaLine(suggestion?.subtitle, providerName)
  }
}

const resetPlanItemLocationSearchState = () => {
  planItemLocationSearchQuery.value = ''
  planItemLocationSearchSuggestions.value = []
  isPlanItemLocationSearchLoading.value = false
  planItemLocationSearchError.value = ''
  planItemLocationSearchRequestToken.value += 1
}

const PLAN_ITEM_DIALOG_MARKER_HTML = `
  <span class="plan-item-dialog-marker-pin" aria-hidden="true">
    <svg viewBox="0 0 30 40" xmlns="http://www.w3.org/2000/svg">
      <path d="M15 1.5C9.2 1.5 4.5 6.2 4.5 12c0 8.1 10.5 19.6 10.5 19.6S25.5 20.1 25.5 12c0-5.8-4.7-10.5-10.5-10.5z" fill="#f43f5e" stroke="#0f172a" stroke-width="1.4" />
      <circle cx="15" cy="12" r="4.2" fill="#fef08a" stroke="#0f172a" stroke-width="1.2" />
    </svg>
  </span>
`

const resolvePlanItemCoordinates = () => {
  const latitude = parsePlanItemCoordinate(planItemForm.value.latitude, -90, 90)
  const longitude = parsePlanItemCoordinate(planItemForm.value.longitude, -180, 180)
  if (latitude === null || longitude === null) {
    return null
  }

  return { lat: latitude, lon: longitude }
}

const resolvePlanItemDialogMapCenter = () => {
  const coordinates = resolvePlanItemCoordinates()
  if (coordinates) {
    return [coordinates.lat, coordinates.lon]
  }

  const workspaceMapInstance = timelineMapRef.value?.map?.value || timelineMapRef.value?.map
  const workspaceCenter = workspaceMapInstance?.getCenter?.()
  if (Number.isFinite(workspaceCenter?.lat) && Number.isFinite(workspaceCenter?.lng)) {
    return [workspaceCenter.lat, workspaceCenter.lng]
  }

  return Array.isArray(workspaceFallbackCenter.value) ? [...workspaceFallbackCenter.value] : [37.7749, -122.4194]
}

const setPlanItemDialogMapCenter = ({ zoom = null, centerFromSelection = false } = {}) => {
  if (centerFromSelection) {
    const coordinates = resolvePlanItemCoordinates()
    if (coordinates) {
      planItemDialogMapCenter.value = [coordinates.lat, coordinates.lon]
    } else {
      planItemDialogMapCenter.value = resolvePlanItemDialogMapCenter()
    }
  } else {
    planItemDialogMapCenter.value = resolvePlanItemDialogMapCenter()
  }

  if (Number.isFinite(zoom)) {
    planItemDialogMapZoom.value = zoom
  } else if (!hasPlanItemCoordinates.value) {
    planItemDialogMapZoom.value = 13
  }
}

const createRasterPlanItemDialogMapAdapter = (map) => {
  let marker = null

  const clear = () => {
    if (marker) {
      map.removeLayer(marker)
      marker = null
    }
  }

  const render = (coords) => {
    clear()
    if (!coords) return

    const markerIcon = L.divIcon({
      className: 'plan-item-dialog-marker-icon',
      html: PLAN_ITEM_DIALOG_MARKER_HTML,
      iconSize: [30, 40],
      iconAnchor: [15, 38]
    })

    marker = L.marker([coords.lat, coords.lon], { icon: markerIcon }).addTo(map)
  }

  return {
    render,
    cleanup: clear
  }
}

const createVectorPlanItemDialogMarkerElement = () => {
  const markerElement = document.createElement('div')
  markerElement.className = 'plan-item-dialog-marker-icon plan-item-dialog-marker-icon--vector'
  markerElement.innerHTML = PLAN_ITEM_DIALOG_MARKER_HTML
  return markerElement
}

const createVectorPlanItemDialogMapAdapter = (map) => {
  let marker = null

  const clear = () => {
    if (marker) {
      marker.remove()
      marker = null
    }
  }

  const render = (coords) => {
    clear()
    if (!coords) return

    marker = new maplibregl.Marker({
      element: createVectorPlanItemDialogMarkerElement(),
      anchor: 'bottom'
    })
      .setLngLat([coords.lon, coords.lat])
      .addTo(map)
  }

  return {
    render,
    cleanup: clear
  }
}

const createPlanItemDialogMapAdapter = (map) => {
  const mapMode = resolveMapEngineModeFromInstance(map, MAP_RENDER_MODES.RASTER)
  if (mapMode === MAP_RENDER_MODES.VECTOR) {
    return createVectorPlanItemDialogMapAdapter(map)
  }
  return createRasterPlanItemDialogMapAdapter(map)
}

const syncPlanItemDialogMapLocation = ({ recenter = false, zoom = null } = {}) => {
  if (recenter) {
    setPlanItemDialogMapCenter({ centerFromSelection: true, zoom })
  }

  if (!planItemDialogMapAdapter.value) {
    return
  }

  const coordinates = resolvePlanItemCoordinates()
  planItemDialogMapAdapter.value.render(coordinates)

  if (!planItemDialogMapInstance.value || !coordinates || !recenter) {
    return
  }

  const nextZoom = Number.isFinite(zoom) ? zoom : planItemDialogMapInstance.value.getZoom?.() || 13
  planItemDialogMapInstance.value.setView([coordinates.lat, coordinates.lon], nextZoom, { animate: true })
}

const handlePlanItemDialogMapReady = (map) => {
  planItemDialogMapInstance.value = map
  planItemDialogMapAdapter.value?.cleanup?.()
  planItemDialogMapAdapter.value = createPlanItemDialogMapAdapter(map)
  syncPlanItemDialogMapLocation({ recenter: true, zoom: hasPlanItemCoordinates.value ? 16 : planItemDialogMapZoom.value })
}

const cleanupPlanItemDialogMap = () => {
  planItemDialogMapAdapter.value?.cleanup?.()
  planItemDialogMapAdapter.value = null
  planItemDialogMapInstance.value = null
}

const resolvePlanSuggestionForCoordinates = async (latitude, longitude, {
  source = 'dialog-map',
  showUnavailableWarning = true
} = {}) => {
  const requestToken = ++planItemSuggestionRequestToken.value
  isResolvingPlanSuggestion.value = true
  planItemLocationSource.value = {
    source,
    latitude,
    longitude
  }

  try {
    const suggestion = await tripsStore.getPlanSuggestion(latitude, longitude)
    if (requestToken !== planItemSuggestionRequestToken.value) {
      return
    }
    if (suggestion) {
      await applyPlanSuggestionToForm(suggestion)
      syncPlanItemDialogMapLocation({ recenter: true, zoom: 16 })
    }
  } catch (error) {
    if (requestToken !== planItemSuggestionRequestToken.value || !showUnavailableWarning) {
      return
    }
    toast.add({
      severity: 'warn',
      summary: 'Plan suggestion unavailable',
      detail: error.response?.data?.message || error.message || 'You can still edit title and save.',
      life: 3000
    })
  } finally {
    if (requestToken === planItemSuggestionRequestToken.value) {
      isResolvingPlanSuggestion.value = false
    }
  }
}

const handlePlanItemDialogMapClick = async (event) => {
  const latitude = Number(event?.latlng?.lat)
  const longitude = Number(event?.latlng?.lng)
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return
  }

  planItemForm.value.latitude = latitude
  planItemForm.value.longitude = longitude
  syncPlanItemDialogMapLocation({ recenter: true, zoom: 16 })
  await resolvePlanSuggestionForCoordinates(latitude, longitude, { source: 'dialog-map' })
}

const resolvePlanItemLocationSearchBias = () => {
  const coordinates = resolvePlanItemCoordinates()
  if (coordinates) {
    return { lat: coordinates.lat, lon: coordinates.lon }
  }

  const planItemMapCenter = planItemDialogMapInstance.value?.getCenter?.()
  if (Number.isFinite(planItemMapCenter?.lat) && Number.isFinite(planItemMapCenter?.lng)) {
    return { lat: planItemMapCenter.lat, lon: planItemMapCenter.lng }
  }

  const mapInstance = timelineMapRef.value?.map?.value || timelineMapRef.value?.map
  const mapCenter = mapInstance?.getCenter?.()
  if (Number.isFinite(mapCenter?.lat) && Number.isFinite(mapCenter?.lng)) {
    return { lat: mapCenter.lat, lon: mapCenter.lng }
  }

  if (Array.isArray(workspaceFallbackCenter.value) && workspaceFallbackCenter.value.length >= 2) {
    const [fallbackLat, fallbackLon] = workspaceFallbackCenter.value
    if (Number.isFinite(fallbackLat) && Number.isFinite(fallbackLon)) {
      return { lat: fallbackLat, lon: fallbackLon }
    }
  }

  return null
}

const handlePlanItemLocationSearchComplete = async (event) => {
  const query = event?.query?.trim() || ''
  planItemLocationSearchError.value = ''

  if (query.length < 2) {
    planItemLocationSearchSuggestions.value = []
    isPlanItemLocationSearchLoading.value = false
    planItemLocationSearchRequestToken.value += 1
    return
  }

  const requestToken = ++planItemLocationSearchRequestToken.value
  isPlanItemLocationSearchLoading.value = true

  try {
    const bias = resolvePlanItemLocationSearchBias()
    const results = await tripsStore.searchPlanLocations(query, {
      lat: bias?.lat,
      lon: bias?.lon,
      limit: 12
    })

    if (requestToken !== planItemLocationSearchRequestToken.value) {
      return
    }

    planItemLocationSearchSuggestions.value = (Array.isArray(results) ? results : []).map((result) => {
      const title = result?.title?.trim()
      const latitude = Number(result?.latitude)
      const longitude = Number(result?.longitude)
      const displayName = title || (Number.isFinite(latitude) && Number.isFinite(longitude)
        ? `Planned place (${latitude.toFixed(5)}, ${longitude.toFixed(5)})`
        : 'Planned place')

      const { groupLabel, metaLine } = buildPlanItemLocationMeta(result)

      return {
        ...result,
        displayName,
        groupLabel,
        metaLine
      }
    })
  } catch (error) {
    if (requestToken !== planItemLocationSearchRequestToken.value) {
      return
    }
    planItemLocationSearchError.value = error.response?.data?.message || error.message || 'Failed to search places.'
  } finally {
    if (requestToken === planItemLocationSearchRequestToken.value) {
      isPlanItemLocationSearchLoading.value = false
    }
  }
}

const handlePlanItemLocationSearchSelect = (event) => {
  const suggestion = event?.value || event
  const latitude = Number(suggestion?.latitude)
  const longitude = Number(suggestion?.longitude)
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return
  }

  planItemForm.value.latitude = latitude
  planItemForm.value.longitude = longitude

  const selectedTitle = suggestion?.title?.trim() || suggestion?.displayName?.trim()
  if (selectedTitle) {
    planItemForm.value.title = selectedTitle
  }

  planItemLocationSource.value = null
  planItemLocationSearchQuery.value = ''
  planItemLocationSearchSuggestions.value = []
  planItemLocationSearchError.value = ''
  syncPlanItemDialogMapLocation({ recenter: true, zoom: 16 })
}

const openPlanItemDialogFromCoordinates = async (lat, lon, source = 'map') => {
  resetPlanItemForm()
  planItemForm.value.latitude = lat
  planItemForm.value.longitude = lon
  setPlanItemDialogMapCenter({ centerFromSelection: true, zoom: 16 })
  showPlanItemDialog.value = true
  syncPlanItemDialogMapLocation({ recenter: true, zoom: 16 })
  await resolvePlanSuggestionForCoordinates(lat, lon, { source, showUnavailableWarning: true })
  await focusPlanLocationSearch()
}

const handlePlanToVisit = async (event) => {
  if (!ensurePlanEditAccess()) return
  const lat = event?.latlng?.lat
  const lon = event?.latlng?.lng
  if (typeof lat !== 'number' || typeof lon !== 'number') {
    return
  }
  await openPlanItemDialogFromCoordinates(lat, lon, 'context-menu')
}

const focusPlannedItemOnMap = (item) => {
  if (!item || typeof item.latitude !== 'number' || typeof item.longitude !== 'number') {
    return
  }

  const setView = timelineMapRef.value?.setView
  if (typeof setView !== 'function') {
    return
  }

  setView([item.latitude, item.longitude], 16, { animate: true })
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
  if (!ensurePlanEditAccess()) return
  const planItemId = resolvePlanItemIdFromContext(contextPayload)
  const planItem = findPlanItemById(planItemId)
  if (!planItem) {
    return
  }
  openEditPlanItemDialog(planItem)
}

const handlePlanItemDeleteFromMap = (contextPayload) => {
  if (!ensurePlanEditAccess()) return
  const planItemId = resolvePlanItemIdFromContext(contextPayload)
  const planItem = findPlanItemById(planItemId)
  if (!planItem) {
    return
  }
  confirmDeletePlanItem(planItem)
}

const handlePlanItemOverrideFromTable = (payload) => {
  if (!ensurePlanEditAccess()) return
  const item = payload?.item
  const action = payload?.action
  if (!item || !action) {
    return
  }
  applyVisitOverride(item, action)
}

const focusPlanLocationSearch = async () => {
  await nextTick()
  const autocompleteInput = planLocationSearchRef.value?.$el?.querySelector?.('input')
  if (autocompleteInput && typeof autocompleteInput.focus === 'function') {
    autocompleteInput.focus()
  }
}

const openCreatePlanItemDialog = async () => {
  if (!ensurePlanEditAccess()) return
  resetPlanItemForm()
  setPlanItemDialogMapCenter({ centerFromSelection: false, zoom: 13 })
  showPlanItemDialog.value = true
  syncPlanItemDialogMapLocation({ recenter: true, zoom: 13 })
  await focusPlanLocationSearch()
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
  planItemLocationSource.value = null
  planItemSuggestionRequestToken.value += 1
  isResolvingPlanSuggestion.value = false
  resetPlanItemLocationSearchState()
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
  if (!ensurePlanEditAccess()) return
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

  setPlanItemDialogMapCenter({ centerFromSelection: true, zoom: hasPlanItemCoordinates.value ? 16 : 13 })
  showPlanItemDialog.value = true
  syncPlanItemDialogMapLocation({ recenter: true, zoom: hasPlanItemCoordinates.value ? 16 : 13 })
}

const validatePlanItem = () => {
  planItemErrors.value = {}

  if (!planItemForm.value.title || !planItemForm.value.title.trim()) {
    planItemErrors.value.title = 'Title is required'
  }

  return Object.keys(planItemErrors.value).length === 0
}

const submitPlanItem = async () => {
  if (!ensurePlanEditAccess()) return
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
  if (!ensurePlanEditAccess()) return
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
  if (!ensurePlanEditAccess()) return
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

const openTimelineGenerationDialogForJob = async (jobId) => {
  if (!jobId) return

  isRefreshingAfterTimelineJob.value = false
  showTimelineGenerationDialog.value = true

  await trackExistingTimelineJob(String(jobId), {
    modalType: 'reconstruction',
    showModal: false,
    autoCloseOnCompleted: false,
    autoCloseOnFailed: false,
    autoCloseOnTrackingError: false,
    onCompleted: async () => {
      isRefreshingAfterTimelineJob.value = true

      try {
        await Promise.all([
          tripsStore.fetchTrip(tripId.value),
          tripsStore.fetchTripSummary(tripId.value),
          tripsStore.fetchTripPlanItems(tripId.value)
        ])

        if (!isUnplannedTrip.value && activeRange.value?.start && activeRange.value?.end) {
          await fetchWorkspaceRange(activeRange.value, false)
        }

        await refreshVisitComparisons()

        toast.add({
          severity: 'success',
          summary: 'Timeline Generation Complete',
          detail: 'Trip workspace was refreshed with regenerated data.',
          life: 3200
        })
      } catch (error) {
        toast.add({
          severity: 'warn',
          summary: 'Refresh Incomplete',
          detail: error.response?.data?.message || error.message || 'Timeline completed, but workspace refresh failed.',
          life: 5000
        })
      } finally {
        isRefreshingAfterTimelineJob.value = false
      }
    },
    onFailed: (progress) => {
      toast.add({
        severity: 'error',
        summary: 'Timeline Generation Failed',
        detail: progress?.errorMessage || timelineJobError.value || 'Job failed.',
        life: 5000
      })
    },
    onTrackingError: (error) => {
      toast.add({
        severity: 'error',
        summary: 'Timeline Job Tracking Failed',
        detail: error,
        life: 5000
      })
    }
  })
}

const retryTimelineJobProgress = async () => {
  if (!trackedTimelineJobId.value) return
  await refreshCurrentJobProgress()
}

const handleTimelineGenerationDialogHide = () => {
  if (!timelineJobCanClose.value) {
    showTimelineGenerationDialog.value = true
    return
  }
  clearTrackedTimelineJob()
  isRefreshingAfterTimelineJob.value = false
}

const handleReconstructionCommitted = async (result) => {
  showReconstructionDialog.value = false
  const jobId = result?.jobId

  if (jobId) {
    await openTimelineGenerationDialogForJob(jobId)
    return
  }

  // No job was started (e.g. all points were duplicates or an active job already exists).
  if (!isUnplannedTrip.value && activeRange.value?.start && activeRange.value?.end) {
    fetchWorkspaceRange(activeRange.value, false)
  }
}

onMounted(async () => {
  await loadWorkspace()
  ensureActiveWorkspaceTab()
})

onUnmounted(() => {
  cleanupPlanItemDialogMap()
})

watch(workspaceTabs, () => {
  ensureActiveWorkspaceTab()
}, { deep: true })

watch(showPlanItemDialog, async (nextVisible) => {
  if (!nextVisible) {
    cleanupPlanItemDialogMap()
    return
  }

  await nextTick()
  planItemDialogMapRef.value?.invalidateSize?.()
  syncPlanItemDialogMapLocation({
    recenter: true,
    zoom: hasPlanItemCoordinates.value ? 16 : planItemDialogMapZoom.value
  })
})
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

.unplanned-trip-banner {
  margin-bottom: var(--gp-spacing-sm);
}

.trip-access-banner {
  margin-bottom: var(--gp-spacing-sm);
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

.collaborators-content {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
}

.collaborator-add-row {
  display: grid;
  grid-template-columns: 1fr 10rem auto;
  gap: var(--gp-spacing-sm);
  align-items: center;
}

.collaborator-role-select {
  min-width: 8rem;
}

.collaborators-loading {
  display: flex;
  justify-content: center;
  padding: var(--gp-spacing-md);
}

.collaborators-empty {
  color: var(--gp-text-secondary);
  text-align: center;
  padding: var(--gp-spacing-md);
}

.collaborators-list {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.collaborator-row {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: var(--gp-spacing-sm);
  align-items: center;
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-small);
  padding: var(--gp-spacing-sm);
}

.collaborator-main {
  display: flex;
  flex-direction: column;
}

.collaborator-main small {
  color: var(--gp-text-secondary);
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

.plan-item-dialog-layout {
  display: grid;
  grid-template-columns: minmax(320px, 0.95fr) minmax(0, 1.35fr);
  gap: var(--gp-spacing-md);
}

.plan-item-dialog-form {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.plan-item-dialog-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--gp-spacing-sm);
}

.plan-item-coordinate-pill {
  min-height: 2.5rem;
  border-radius: var(--gp-radius-small);
  border: 1px solid var(--gp-border-light);
  background: var(--gp-surface-light);
  padding: 0.55rem 0.7rem;
  color: var(--gp-text-secondary);
  font-size: 0.83rem;
  display: flex;
  align-items: center;
}

.plan-item-dialog-map-pane {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-xs);
}

.plan-item-dialog-map-wrap {
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  overflow: hidden;
  background: var(--gp-surface-light);
}

.plan-item-dialog-map-hint {
  color: var(--gp-text-secondary);
  font-size: 0.8rem;
}

.plan-item-location-search {
  width: 100%;
}

.plan-item-location-search :deep(.p-autocomplete-loader) {
  display: none !important;
}

.plan-item-location-search :deep(.p-autocomplete-input) {
  width: 100%;
}

.plan-item-location-search-loading {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  margin-top: var(--gp-spacing-xs);
  color: var(--gp-text-secondary);
  font-size: 0.78rem;
}

.plan-item-location-search-loading-icon {
  font-size: 0.95rem;
}

.plan-item-location-option {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.plan-item-location-option-title {
  font-size: 0.88rem;
  color: var(--gp-text-primary);
}

.plan-item-location-option-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.2rem;
}

.plan-item-location-source-chip {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  border: 1px solid transparent;
  padding: 0.08rem 0.45rem;
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.plan-item-location-source-chip--saved {
  color: #166534;
  background: #dcfce7;
  border-color: #86efac;
}

.plan-item-location-source-chip--provider {
  color: #1e3a8a;
  background: #dbeafe;
  border-color: #93c5fd;
}

.plan-item-location-option-subtitle {
  font-size: 0.75rem;
  color: var(--gp-text-secondary);
}

.timeline-generation-content {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.timeline-generation-status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gp-spacing-sm);
}

.timeline-generation-job-id {
  color: var(--gp-text-secondary);
}

.timeline-generation-step {
  margin: 0;
  color: var(--gp-text-primary);
  font-weight: 500;
}

.timeline-generation-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--gp-text-secondary);
  font-size: 0.85rem;
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

  .plan-item-dialog-layout {
    grid-template-columns: 1fr;
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

  .collaborator-add-row {
    grid-template-columns: 1fr;
  }

  .collaborator-row {
    grid-template-columns: 1fr;
  }

  .plan-item-dialog-row {
    grid-template-columns: 1fr;
  }
}
</style>

<style>
.plan-item-dialog {
  width: 95vw !important;
  max-width: 1480px !important;
}

.plan-item-dialog-marker-icon {
  background: transparent;
  border: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.plan-item-dialog-marker-pin {
  display: inline-flex;
  width: 30px;
  height: 40px;
  align-items: flex-end;
  justify-content: center;
}

.plan-item-dialog-marker-pin svg {
  width: 30px;
  height: 40px;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.35));
}

.plan-item-dialog-marker-icon--vector .plan-item-dialog-marker-pin svg {
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.45));
}
</style>
