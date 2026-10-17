<template>
  <AppLayout variant="default">
    <ConfirmDialog />
    <PageContainer
      :title="t('geofences.page.title')"
      :subtitle="t('geofences.page.subtitle')"
      variant="fullwidth"
    >
      <Message v-if="demoReadOnly" severity="error" :closable="false" class="demo-read-only-message">
        {{ t('geofences.page.demoDisabled') }}
      </Message>

      <TabContainer
        :tabs="tabs"
        :activeIndex="activeTabIndex"
        @tab-change="onTabChange"
      >
        <GeofenceRulesTab
          v-if="activeTab === 'rules'"
          :editingRuleId="editingRuleId"
          :ruleForm="ruleForm"
          :ruleFormErrors="ruleFormErrors"
          :subjectOptions="subjectOptions"
          :mapCenter="mapCenter"
          :mapZoom="mapZoom"
          :selectedAreaSummary="selectedAreaSummary"
          :statusOptions="statusOptions"
          :enterTemplateOptions="enterTemplateOptions"
          :leaveTemplateOptions="leaveTemplateOptions"
          :hasEnabledDefaultEnterTemplate="hasEnabledDefaultEnterTemplate"
          :enabledDefaultEnterTemplate="enabledDefaultEnterTemplate"
          :hasEnabledDefaultLeaveTemplate="hasEnabledDefaultLeaveTemplate"
          :enabledDefaultLeaveTemplate="enabledDefaultLeaveTemplate"
          :savingRule="savingRule"
          :rules="rules"
          :eventSummary="eventSummary"
          :read-only="demoReadOnly"
          @start-rectangle-draw="startRectangleDraw"
          @map-ready="handleMapReady"
          @save-rule="saveRule"
          @reset-rule-form="resetRuleForm"
          @load-rules="loadRules"
          @edit-rule="editRule"
          @delete-rule="deleteRule"
        />

        <GeofenceTemplatesTab
          v-else-if="activeTab === 'templates'"
          :editingTemplateId="editingTemplateId"
          :templateForm="templateForm"
          :templateFormErrors="templateFormErrors"
          :templateNameInput="templateNameInput"
          :templateDestinationInput="templateDestinationInput"
          :templateConfigKeyInput="templateConfigKeyInput"
          :templateTitleInput="templateTitleInput"
          :templateBodyInput="templateBodyInput"
          :templatePreviewToasts="templatePreviewToasts"
          :templateMacros="templateMacros"
          :appriseRoutingModeOptions="appriseRoutingModeOptions"
          :currentDefaultEnterName="currentDefaultEnterName"
          :currentDefaultLeaveName="currentDefaultLeaveName"
          :appriseEnabled="appriseEnabled"
          :appriseConfigured="appriseConfigured"
          :testingTemplateConnection="testingTemplateConnection"
          :templateConnectionTestResult="templateConnectionTestResult"
          :savingTemplate="savingTemplate"
          :templates="templates"
          :formatExternalRoute="formatExternalRoute"
          :defaultSummary="defaultSummary"
          :read-only="demoReadOnly"
          @update-template-field="updateTemplateField"
          @focus-template-field="setFocusedTemplateField"
          @insert-macro="insertMacro"
          @test-template-connection="testTemplateConnection"
          @save-template="saveTemplate"
          @reset-template-form="resetTemplateForm"
          @load-templates="loadTemplates"
          @edit-template="editTemplate"
          @delete-template="deleteTemplate"
        />

        <GeofenceEventsTab
          v-else
          :events="geofenceEvents"
          :totalRecords="geofenceEventsTotal"
          :query="geofenceEventsQuery"
          :subjectFilterOptions="eventSubjectFilterOptions"
          :unreadCount="unreadCount"
          :loading="refreshingEvents"
          :markingAllSeen="markingAllSeen"
          :markingEventId="markingEventId"
          :formatDate="formatDate"
          :deliverySeverity="deliverySeverity"
          :userId="authStore.userId"
          :read-only="demoReadOnly"
          @update-query="handleEventsQueryUpdate"
          @mark-all-events-seen="markAllEventsSeen"
          @refresh-events="refreshEvents"
          @mark-event-seen="markEventSeen"
        />
      </TabContainer>

    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useLocationStore } from '@/stores/location'
import { useFriendsStore } from '@/stores/friends'
import { useGeofencesStore } from '@/stores/geofences'
import { useRectangleDrawingRuntime } from '@/composables/useRectangleDrawingRuntime'
import { useTimezone } from '@/composables/useTimezone'
import { useGeofenceTemplateEditor } from '@/composables/useGeofenceTemplateEditor'
import { createGeofenceRulesMapAdapter } from '@/maps/geofences/runtime/createGeofenceRulesMapAdapter'
import { buildAreaLikeFromBoundsApi, hasRuleArea } from '@/maps/geofences/shared/geofenceRuleAreaUtils'

import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import TabContainer from '@/components/ui/layout/TabContainer.vue'
import ConfirmDialog from 'primevue/confirmdialog'
import GeofenceRulesTab from '@/components/geofences/tabs/GeofenceRulesTab.vue'
import GeofenceTemplatesTab from '@/components/geofences/tabs/GeofenceTemplatesTab.vue'
import GeofenceEventsTab from '@/components/geofences/tabs/GeofenceEventsTab.vue'
import Message from 'primevue/message'
import { showDemoModeToast } from '@/utils/demoMode'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

const { t } = useI18n()
const toast = useToast()
const confirm = useConfirm()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const locationStore = useLocationStore()
const friendsStore = useFriendsStore()
const geofencesStore = useGeofencesStore()
const {
  rules,
  templates,
  capabilities: templateDeliveryCapabilities,
  events: geofenceEvents,
  eventsTotal: geofenceEventsTotal,
  unreadCount: geofenceUnreadCount
} = storeToRefs(geofencesStore)
const timezone = useTimezone()
const FALLBACK_GEOFENCE_CENTER = [50.4501, 30.5234]
const LAST_KNOWN_MAP_ZOOM = 12

const tabs = computed(() => [
  { label: t('geofences.page.tabs.rules'), icon: 'pi pi-map', key: 'rules' },
  { label: t('geofences.page.tabs.templates'), icon: 'pi pi-envelope', key: 'templates' },
  {
    label: t('geofences.page.tabs.events'),
    icon: 'pi pi-bell',
    key: 'events',
    badge: unreadCount.value > 0 ? unreadCount.value : null,
    badgeType: 'danger'
  }
])

const activeTab = ref('rules')
const activeTabIndex = computed(() => tabs.value.findIndex(t => t.key === activeTab.value))

const friends = ref([])

const savingRule = ref(false)
const markingAllSeen = ref(false)
const markingEventId = ref(null)
const refreshingEvents = ref(false)
const geofenceEventsQuery = ref(defaultGeofenceEventsQuery())
const ruleFormErrors = ref({
  name: '',
  subjectUserIds: '',
  area: '',
  monitoring: ''
})

const unreadCount = computed(() => geofenceUnreadCount.value)
const demoReadOnly = computed(() => authStore.demoReadOnly)

const {
  savingTemplate,
  testingTemplateConnection,
  templateFormErrors,
  templateNameInput,
  templateDestinationInput,
  templateConfigKeyInput,
  templateTitleInput,
  templateBodyInput,
  templateConnectionTestResult,
  editingTemplateId,
  templateForm,
  enabledDefaultEnterTemplate,
  enabledDefaultLeaveTemplate,
  hasEnabledDefaultEnterTemplate,
  hasEnabledDefaultLeaveTemplate,
  enterTemplateOptions,
  leaveTemplateOptions,
  templateNameById,
  appriseEnabled,
  appriseConfigured,
  templateMacros,
  currentDefaultEnterName,
  currentDefaultLeaveName,
  templatePreviewToasts,
  appriseRoutingModeOptions,
  setFocusedTemplateField,
  updateTemplateField,
  insertMacro,
  loadTemplateDeliveryCapabilities,
  testTemplateConnection,
  saveTemplate,
  editTemplate,
  deleteTemplate,
  resetTemplateForm,
  defaultSummary,
  formatExternalRoute
} = useGeofenceTemplateEditor({
  demoReadOnly,
  showDemoGeofenceReadOnlyToast,
  confirmAction,
  formatDate,
  loadTemplates,
  loadRules
})

function defaultGeofenceEventsQuery() {
  return {
    page: 0,
    pageSize: 25,
    sortBy: 'occurredAt',
    sortDir: 'desc',
    unreadOnly: false,
    datePreset: 'all',
    dateFrom: null,
    dateTo: null,
    subjectUserIds: [],
    eventTypes: []
  }
}

const editingRuleId = ref(null)
const geofenceMap = ref(null)
const geofenceMapAdapter = ref(null)
const mapCenter = ref(FALLBACK_GEOFENCE_CENTER)
const mapZoom = ref(11)
const lastKnownMapCenter = ref(null)
const initialRulesLoaded = ref(false)
const mapViewportInitialized = ref(false)
const knownSubjectLabels = ref({})

const statusOptions = computed(() => [
  { label: t('geofences.rulesTab.statusActive'), value: 'ACTIVE' },
  { label: t('geofences.rulesTab.statusPaused'), value: 'PAUSED' }
])

const ruleForm = ref(defaultRuleForm())
const rectangleDrawing = useRectangleDrawingRuntime({
  onRectangleCreated: ({ bounds }) => {
    const areaLike = buildAreaLikeFromBoundsApi(bounds)
    if (!areaLike) {
      return
    }

    ruleFormErrors.value.area = ''
    ruleForm.value.northEastLat = Number(areaLike.northEastLat.toFixed(6))
    ruleForm.value.northEastLon = Number(areaLike.northEastLon.toFixed(6))
    ruleForm.value.southWestLat = Number(areaLike.southWestLat.toFixed(6))
    ruleForm.value.southWestLon = Number(areaLike.southWestLon.toFixed(6))

    rectangleDrawing.cleanupTempLayer()
    syncMapRectangleFromForm(true)
  }
})

const subjectOptions = computed(() => {
  const items = []
  const seen = new Set()

  const pushOption = (value, label, unavailable = false) => {
    const normalizedValue = normalizeSubjectId(value)
    if (!normalizedValue || seen.has(normalizedValue)) {
      return
    }
    seen.add(normalizedValue)
    if (label) {
      rememberSubjectLabel(normalizedValue, label)
    }
    items.push({ label, value: normalizedValue, unavailable })
  }

  if (authStore.userId) {
    const meLabel = t('geofences.page.meLabel', { name: authStore.userName || authStore.userEmail })
    pushOption(authStore.userId, meLabel)
  }

  for (const friend of friends.value) {
    const label = friend.fullName || friend.email
    pushOption(friend.friendId || friend.userId, label)
  }

  for (const selectedId of ruleForm.value.subjectUserIds || []) {
    const normalizedValue = normalizeSubjectId(selectedId)
    if (!normalizedValue || seen.has(normalizedValue)) {
      continue
    }
    const label = knownSubjectLabels.value[normalizedValue] || t('geofences.page.unknownSubject', { idPrefix: normalizedValue.slice(0, 8) })
    pushOption(normalizedValue, t('geofences.page.unavailableSuffix', { label }), true)
  }

  return items
})

const eventSubjectFilterOptions = computed(() => {
  return subjectOptions.value
    .filter(option => !option.unavailable)
    .map(option => ({
      label: option.label,
      value: option.value
    }))
})

function hasValidAreaBounds(form) {
  return ['northEastLat', 'northEastLon', 'southWestLat', 'southWestLon'].every((key) => {
    const value = form[key]
    return value !== null && value !== undefined && value !== '' && Number.isFinite(Number(value))
  })
}

const selectedAreaSummary = computed(() => {
  const { northEastLat, northEastLon, southWestLat, southWestLon } = ruleForm.value
  if (!hasValidAreaBounds(ruleForm.value)) {
    return ''
  }

  return t('geofences.page.selectedAreaSummary', {
    neLat: Number(northEastLat).toFixed(5),
    neLon: Number(northEastLon).toFixed(5),
    swLat: Number(southWestLat).toFixed(5),
    swLon: Number(southWestLon).toFixed(5)
  })
})

function defaultRuleForm() {
  return {
    name: '',
    subjectUserIds: authStore.userId ? [normalizeSubjectId(authStore.userId)] : [],
    northEastLat: null,
    northEastLon: null,
    southWestLat: null,
    southWestLon: null,
    monitorEnter: true,
    monitorLeave: true,
    cooldownSeconds: 120,
    enterTemplateId: null,
    leaveTemplateId: null,
    status: 'ACTIVE'
  }
}

function clearRuleFormErrors() {
  ruleFormErrors.value = {
    name: '',
    subjectUserIds: '',
    area: '',
    monitoring: ''
  }
}

function normalizeSubjectId(value) {
  if (value === null || value === undefined || value === '') {
    return null
  }
  return String(value)
}

function rememberSubjectLabel(subjectId, label) {
  const normalizedId = normalizeSubjectId(subjectId)
  if (!normalizedId || !label || knownSubjectLabels.value[normalizedId]) {
    return
  }
  knownSubjectLabels.value = {
    ...knownSubjectLabels.value,
    [normalizedId]: label
  }
}

function normalizeRule(rule) {
  const normalizedSubjects = Array.isArray(rule?.subjects)
    ? rule.subjects
      .map(subject => ({
        userId: normalizeSubjectId(subject?.userId),
        displayName: subject?.displayName || 'Unknown subject'
      }))
      .filter(subject => !!subject.userId)
    : []

  for (const subject of normalizedSubjects) {
    rememberSubjectLabel(subject.userId, subject.displayName)
  }

  return {
    ...rule,
    subjects: normalizedSubjects
  }
}

function confirmAction({
  message,
  header = t('geofences.page.confirmDefaults.header'),
  acceptLabel = t('geofences.page.confirmDefaults.accept'),
  rejectLabel = t('common.cancel'),
  acceptClass = 'p-button-danger'
}) {
  return new Promise((resolve) => {
    let settled = false
    const settle = (value) => {
      if (!settled) {
        settled = true
        resolve(value)
      }
    }

    confirm.require({
      message,
      header,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel,
      rejectLabel,
      acceptClass,
      rejectClass: 'p-button-text p-button-secondary',
      accept: () => settle(true),
      reject: () => settle(false),
      onHide: () => settle(false)
    })
  })
}

function showDemoGeofenceReadOnlyToast(detail = t('geofences.page.demoToasts.default')) {
  showDemoModeToast(toast, detail)
}

function validateRuleForm() {
  clearRuleFormErrors()
  const errors = {}
  const form = ruleForm.value

  if (!form.name || !form.name.trim()) {
    errors.name = t('geofences.page.validation.ruleNameRequired')
  }

  const selectedSubjects = Array.isArray(form.subjectUserIds)
    ? form.subjectUserIds.map(normalizeSubjectId).filter(Boolean)
    : []
  if (selectedSubjects.length === 0) {
    errors.subjectUserIds = t('geofences.page.validation.subjectRequired')
  }

  if (!hasValidAreaBounds(form)) {
    errors.area = t('geofences.page.validation.areaRequired')
  }

  if (!form.monitorEnter && !form.monitorLeave) {
    errors.monitoring = t('geofences.page.validation.monitoringRequired')
  }

  ruleFormErrors.value = {
    ...ruleFormErrors.value,
    ...errors
  }
  return Object.keys(errors).length === 0
}

const availableTabs = new Set(['rules', 'templates', 'events'])

function normalizeTabKey(value) {
  if (typeof value !== 'string') {
    return 'rules'
  }
  return availableTabs.has(value) ? value : 'rules'
}

function syncActiveTabFromRoute(tabValue) {
  activeTab.value = normalizeTabKey(tabValue)
}

function onTabChange(event) {
  const selected = tabs.value[event.index]
  if (!selected) {
    return
  }
  activeTab.value = selected.key

  const nextQuery = { ...route.query }
  if (selected.key === 'rules') {
    delete nextQuery.tab
  } else {
    nextQuery.tab = selected.key
  }
  router.replace({ query: nextQuery }).catch(() => {})
}

function handleMapReady(map) {
  geofenceMap.value = map
  mapViewportInitialized.value = false
  geofenceMapAdapter.value?.destroy?.()
  geofenceMapAdapter.value = createGeofenceRulesMapAdapter(map)
  geofenceMapAdapter.value.initialize?.(map)
  rectangleDrawing.initialize(map)
  syncMapRectangleFromForm()
  syncAllRuleAreasOnMap()
  syncInitialRulesMapViewport()
}

function startRectangleDraw() {
  if (demoReadOnly.value) {
    showDemoGeofenceReadOnlyToast(t('geofences.page.demoToasts.drawArea'))
    return
  }

  if (!geofenceMap.value) {
    return
  }
  rectangleDrawing.startDrawing()
}

function syncMapRectangleFromForm(focus = false) {
  if (!geofenceMap.value || !geofenceMapAdapter.value) {
    return
  }

  if (!hasValidAreaBounds(ruleForm.value)) {
    geofenceMapAdapter.value.clearEditingArea()
    return
  }

  geofenceMapAdapter.value.syncEditingArea({
    northEastLat: ruleForm.value.northEastLat,
    northEastLon: ruleForm.value.northEastLon,
    southWestLat: ruleForm.value.southWestLat,
    southWestLon: ruleForm.value.southWestLon
  }, { focus })
}

function isRuleMapVisible() {
  return activeTab.value === 'rules' && !!geofenceMap.value
}

function syncAllRuleAreasOnMap() {
  if (!isRuleMapVisible() || !geofenceMapAdapter.value) {
    return
  }

  geofenceMapAdapter.value.syncRuleAreas({
    rules: rules.value,
    editingRuleId: editingRuleId.value,
    editingAreaExists: hasValidAreaBounds(ruleForm.value),
    popupBuilder: buildRuleAreaPopupModel
  })
}

function fitMapToAllRuleAreas() {
  if (!isRuleMapVisible() || !geofenceMapAdapter.value) {
    return false
  }
  return geofenceMapAdapter.value.fitAllRuleAreas({
    rules: rules.value,
    editingRuleId: editingRuleId.value,
    editingAreaExists: hasValidAreaBounds(ruleForm.value)
  })
}

async function loadLastKnownMapCenter() {
  try {
    const lastPoint = await locationStore.getLastKnownPosition()
    const lat = Number(lastPoint?.lat)
    const lon = Number(lastPoint?.lon)

    if (!Number.isFinite(lat) || !Number.isFinite(lon)) {
      return
    }

    lastKnownMapCenter.value = [lat, lon]

    const hasAnyRuleAreas = rules.value.some(rule => hasRuleArea(rule))
    const shouldUseLastKnownAsPrimaryView = !hasValidAreaBounds(ruleForm.value) && !hasAnyRuleAreas

    if (shouldUseLastKnownAsPrimaryView) {
      mapCenter.value = [lat, lon]
      mapZoom.value = LAST_KNOWN_MAP_ZOOM
      if (isRuleMapVisible()) {
        geofenceMap.value.setView(lastKnownMapCenter.value, LAST_KNOWN_MAP_ZOOM, { animate: false })
      }
    }

    syncInitialRulesMapViewport()
  } catch (error) {
    console.warn('Failed to load last known position for geofence map:', error)
  }
}

function syncInitialRulesMapViewport() {
  if (!isRuleMapVisible() || mapViewportInitialized.value || !initialRulesLoaded.value) {
    return
  }

  if (hasValidAreaBounds(ruleForm.value)) {
    syncMapRectangleFromForm(true)
    mapViewportInitialized.value = true
    return
  }

  if (fitMapToAllRuleAreas()) {
    mapViewportInitialized.value = true
    return
  }

  if (lastKnownMapCenter.value) {
    geofenceMap.value.setView(lastKnownMapCenter.value, mapZoom.value, { animate: false })
  }

  mapViewportInitialized.value = true
}

async function loadRules() {
  const rawRules = await geofencesStore.loadRules()
  rules.value = rawRules.map(normalizeRule)
  initialRulesLoaded.value = true
  syncAllRuleAreasOnMap()
  syncInitialRulesMapViewport()
}

async function loadTemplates() {
  await geofencesStore.loadTemplates()
  syncAllRuleAreasOnMap()
}

async function refreshEvents() {
  if (refreshingEvents.value) {
    return
  }

  refreshingEvents.value = true
  try {
    const params = {
      page: geofenceEventsQuery.value.page,
      size: geofenceEventsQuery.value.pageSize,
      sortBy: geofenceEventsQuery.value.sortBy,
      sortDirection: geofenceEventsQuery.value.sortDir,
      unreadOnly: geofenceEventsQuery.value.unreadOnly
    }
    if (geofenceEventsQuery.value.dateFrom) {
      params.from = geofenceEventsQuery.value.dateFrom
    }
    if (geofenceEventsQuery.value.dateTo) {
      params.to = geofenceEventsQuery.value.dateTo
    }
    if (Array.isArray(geofenceEventsQuery.value.subjectUserIds) && geofenceEventsQuery.value.subjectUserIds.length > 0) {
      params.subjectUserIds = geofenceEventsQuery.value.subjectUserIds.join(',')
    }
    if (Array.isArray(geofenceEventsQuery.value.eventTypes) && geofenceEventsQuery.value.eventTypes.length > 0) {
      params.eventTypes = geofenceEventsQuery.value.eventTypes.join(',')
    }

    await geofencesStore.loadEvents(params)
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('geofences.page.toasts.eventsErrorSummary'),
      detail: formatApiErrorDetail(error, t('geofences.page.toasts.eventsLoadFailed')),
      life: 5000
    })
  } finally {
    refreshingEvents.value = false
  }
}

async function loadFriends() {
  await friendsStore.fetchFriends()
  friends.value = friendsStore.friends.filter(friend => friend.friendSharesLiveLocation)
}

async function saveRule() {
  if (demoReadOnly.value) {
    showDemoGeofenceReadOnlyToast(t('geofences.page.demoToasts.saveRule'))
    return
  }

  if (!validateRuleForm()) {
    toast.add({
      severity: 'warn',
      summary: t('geofences.page.toasts.validationErrorSummary'),
      detail: t('geofences.page.toasts.fixRuleFieldsDetail'),
      life: 4000
    })
    return
  }

  savingRule.value = true
  try {
    const subjectUserIds = Array.isArray(ruleForm.value.subjectUserIds)
      ? Array.from(new Set(ruleForm.value.subjectUserIds.map(normalizeSubjectId).filter(Boolean)))
      : []
    const payload = {
      ...ruleForm.value,
      subjectUserIds,
      name: ruleForm.value.name.trim()
    }

    if (editingRuleId.value) {
      await geofencesStore.updateRule(editingRuleId.value, payload)
      toast.add({ severity: 'success', summary: t('geofences.page.toasts.updatedSummary'), detail: t('geofences.page.toasts.ruleUpdatedDetail'), life: 3000 })
    } else {
      await geofencesStore.createRule(payload)
      toast.add({ severity: 'success', summary: t('geofences.page.toasts.createdSummary'), detail: t('geofences.page.toasts.ruleCreatedDetail'), life: 3000 })
    }

    resetRuleForm()
    await loadRules()
    await refreshEvents()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('geofences.page.toasts.ruleErrorSummary'),
      detail: formatApiErrorDetail(error, t('geofences.page.toasts.ruleSaveFailed')),
      life: 5000
    })
  } finally {
    savingRule.value = false
  }
}

function editRule(rule) {
  if (demoReadOnly.value) {
    showDemoGeofenceReadOnlyToast(t('geofences.page.demoToasts.editRule'))
    return
  }

  clearRuleFormErrors()
  editingRuleId.value = rule.id
  ruleForm.value = {
    name: rule.name,
    subjectUserIds: Array.isArray(rule.subjects)
      ? rule.subjects.map(subject => normalizeSubjectId(subject.userId)).filter(Boolean)
      : [],
    northEastLat: rule.northEastLat,
    northEastLon: rule.northEastLon,
    southWestLat: rule.southWestLat,
    southWestLon: rule.southWestLon,
    monitorEnter: rule.monitorEnter,
    monitorLeave: rule.monitorLeave,
    cooldownSeconds: rule.cooldownSeconds,
    enterTemplateId: rule.enterTemplateId,
    leaveTemplateId: rule.leaveTemplateId,
    status: rule.status
  }
  syncAllRuleAreasOnMap()
  syncMapRectangleFromForm(true)
}

async function deleteRule(rule) {
  if (demoReadOnly.value) {
    showDemoGeofenceReadOnlyToast(t('geofences.page.demoToasts.deleteRule'))
    return
  }

  const confirmed = await confirmAction({
    header: t('geofences.page.deleteRuleConfirm.header'),
    message: t('geofences.page.deleteRuleConfirm.message', { name: rule.name }),
    acceptLabel: t('geofences.page.deleteRuleConfirm.acceptLabel'),
    rejectLabel: t('common.cancel'),
    acceptClass: 'p-button-danger'
  })
  if (!confirmed) {
    return
  }

  try {
    await geofencesStore.deleteRule(rule.id)
    toast.add({ severity: 'success', summary: t('geofences.page.toasts.deletedSummary'), detail: t('geofences.page.toasts.ruleDeletedDetail'), life: 3000 })
    await loadRules()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('geofences.page.toasts.deleteErrorSummary'),
      detail: formatApiErrorDetail(error, t('geofences.page.toasts.ruleDeleteFailed')),
      life: 5000
    })
  }
}

function resetRuleForm() {
  editingRuleId.value = null
  ruleForm.value = defaultRuleForm()
  clearRuleFormErrors()
  rectangleDrawing.cleanupTempLayer()
  geofenceMapAdapter.value?.clearEditingArea?.()
  syncAllRuleAreasOnMap()
}

async function markEventSeen(event) {
  if (demoReadOnly.value) {
    showDemoGeofenceReadOnlyToast(t('geofences.page.demoToasts.eventSeen'))
    return
  }

  if (!event?.id) {
    return
  }

  markingEventId.value = event.id
  try {
    await geofencesStore.markEventSeen(event.id)
    await refreshEvents()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('geofences.page.toasts.eventErrorSummary'),
      detail: formatApiErrorDetail(error, t('geofences.page.toasts.markEventSeenFailed')),
      life: 5000
    })
  } finally {
    markingEventId.value = null
  }
}

async function markAllEventsSeen() {
  if (demoReadOnly.value) {
    showDemoGeofenceReadOnlyToast(t('geofences.page.demoToasts.eventSeen'))
    return
  }

  markingAllSeen.value = true
  try {
    await geofencesStore.markAllEventsSeen()
    await refreshEvents()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('geofences.page.toasts.eventErrorSummary'),
      detail: formatApiErrorDetail(error, t('geofences.page.toasts.markAllEventsSeenFailed')),
      life: 5000
    })
  } finally {
    markingAllSeen.value = false
  }
}

function handleEventsQueryUpdate(patch) {
  geofenceEventsQuery.value = {
    ...geofenceEventsQuery.value,
    ...patch
  }
  void refreshEvents()
}

function eventSummary(rule) {
  const events = []
  if (rule.monitorEnter) events.push(t('geofences.eventsTab.eventTypes.enter'))
  if (rule.monitorLeave) events.push(t('geofences.eventsTab.eventTypes.leave'))
  return events.join(' / ')
}

function formatDate(value) {
  if (!value) return '-'
  return `${timezone.formatDateDisplay(value)} ${timezone.formatTime(value, { withSeconds: true })}`
}

function resolveRuleTemplateLabel(templateId, type) {
  if (templateId !== null && templateId !== undefined && templateId !== '') {
    const resolved = templateNameById.value.get(String(templateId))
    if (resolved) {
      return resolved
    }
    return t('geofences.page.unknownTemplate', { idPrefix: String(templateId).slice(0, 8) })
  }

  if (type === 'enter') {
    return enabledDefaultEnterTemplate.value
      ? t('geofences.page.defaultTemplateLabel', { name: enabledDefaultEnterTemplate.value.name })
      : t('geofences.rulesTab.templatePlaceholder')
  }

  return enabledDefaultLeaveTemplate.value
    ? t('geofences.page.defaultTemplateLabel', { name: enabledDefaultLeaveTemplate.value.name })
    : t('geofences.rulesTab.templatePlaceholder')
}

function formatRuleSubjects(rule) {
  const subjects = Array.isArray(rule?.subjects)
    ? rule.subjects
      .map(subject => subject?.displayName)
      .filter(name => !!name)
    : []

  if (subjects.length === 0) {
    return '-'
  }

  return subjects.join(', ')
}

function buildRuleAreaPopupModel(rule) {
  const rows = [
    {
      label: t('geofences.rulesTab.columns.subjects'),
      value: formatRuleSubjects(rule)
    },
    {
      label: t('geofences.rulesTab.columns.status'),
      value: rule?.status || '-'
    },
    {
      label: t('geofences.rulesTab.columns.events'),
      value: eventSummary(rule) || t('geofences.page.none')
    },
    {
      label: t('geofences.rulesTab.columns.cooldown'),
      value: t('geofences.page.popup.cooldownValue', { seconds: Number(rule?.cooldownSeconds || 0) })
    },
    {
      label: t('geofences.page.popup.enterTemplateLabel'),
      value: resolveRuleTemplateLabel(rule?.enterTemplateId, 'enter')
    },
    {
      label: t('geofences.page.popup.leaveTemplateLabel'),
      value: resolveRuleTemplateLabel(rule?.leaveTemplateId, 'leave')
    }
  ]

  return {
    title: rule?.name || t('geofences.page.popup.fallbackTitle'),
    iconClass: 'pi pi-bell',
    rows,
    variant: 'compact'
  }
}

function deliverySeverity(status) {
  if (status === 'SENT') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PENDING') return 'info'
  return 'secondary'
}

onMounted(async () => {
  try {
    syncActiveTabFromRoute(route.query.tab)
    await Promise.all([
      loadFriends(),
      loadTemplateDeliveryCapabilities(),
      loadTemplates(),
      loadRules(),
      refreshEvents(),
      loadLastKnownMapCenter()
    ])
    if ((!Array.isArray(ruleForm.value.subjectUserIds) || ruleForm.value.subjectUserIds.length === 0) && authStore.userId) {
      ruleForm.value.subjectUserIds = [normalizeSubjectId(authStore.userId)]
    }
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('geofences.page.toasts.loadErrorSummary'),
      detail: formatApiErrorDetail(error, t('geofences.page.toasts.loadFailed')),
      life: 5000
    })
  }
})

watch(
  () => route.query.tab,
  (tab) => {
    syncActiveTabFromRoute(tab)
    if (activeTab.value === 'events') {
      void refreshEvents()
    }
  }
)

watch(
  () => ruleForm.value.name,
  (value) => {
    if (ruleFormErrors.value.name && value && value.trim()) {
      ruleFormErrors.value.name = ''
    }
  }
)

watch(
  () => ruleForm.value.subjectUserIds,
  (value) => {
    const hasSubjects = Array.isArray(value) && value.some(item => !!normalizeSubjectId(item))
    if (ruleFormErrors.value.subjectUserIds && hasSubjects) {
      ruleFormErrors.value.subjectUserIds = ''
    }
  },
  { deep: true }
)

watch(
  () => [ruleForm.value.monitorEnter, ruleForm.value.monitorLeave],
  ([monitorEnter, monitorLeave]) => {
    if (ruleFormErrors.value.monitoring && (monitorEnter || monitorLeave)) {
      ruleFormErrors.value.monitoring = ''
    }
  }
)

onBeforeUnmount(() => {
  rectangleDrawing.stopDrawing()
  rectangleDrawing.cleanupTempLayer()
  geofenceMapAdapter.value?.destroy?.()
  geofenceMapAdapter.value = null
  geofenceMap.value = null
})
</script>

<style scoped>
:deep(.geofence-area-tooltip) {
  display: grid;
  gap: 0.35rem;
  min-width: 220px;
  padding: 0.6rem 0.7rem;
}

:deep(.geofence-area-tooltip__title) {
  font-size: 0.96rem;
  font-weight: 700;
  color: var(--gp-text-primary);
}

:deep(.geofence-area-tooltip__row) {
  display: grid;
  grid-template-columns: 88px 1fr;
  gap: 0.5rem;
  align-items: start;
}

:deep(.geofence-area-tooltip__label) {
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.02em;
  text-transform: uppercase;
  color: var(--gp-text-secondary);
}

:deep(.geofence-area-tooltip__value) {
  font-size: 0.83rem;
  line-height: 1.25;
  color: var(--gp-text-primary);
  overflow-wrap: anywhere;
}
</style>
