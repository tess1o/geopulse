<template>
  <AppLayout variant="default">
    <ConfirmDialog />
    <PageContainer
      title="Geofences"
      subtitle="Monitor enter/leave events for you and friends"
      variant="fullwidth"
    >
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
          :templateTitleInput="templateTitleInput"
          :templateBodyInput="templateBodyInput"
          :templatePreviewToasts="templatePreviewToasts"
          :templateMacros="templateMacros"
          :currentDefaultEnterName="currentDefaultEnterName"
          :currentDefaultLeaveName="currentDefaultLeaveName"
          :appriseEnabled="appriseEnabled"
          :appriseConfigured="appriseConfigured"
          :testingTemplateConnection="testingTemplateConnection"
          :templateConnectionTestResult="templateConnectionTestResult"
          :savingTemplate="savingTemplate"
          :templates="templates"
          :formatDestination="formatDestination"
          :defaultSummary="defaultSummary"
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
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useLocationStore } from '@/stores/location'
import apiService from '@/utils/apiService'
import { useRectangleDrawingRuntime } from '@/composables/useRectangleDrawingRuntime'
import { useTimezone } from '@/composables/useTimezone'
import { createGeofenceRulesMapAdapter } from '@/maps/geofences/runtime/createGeofenceRulesMapAdapter'
import { buildAreaLikeFromBoundsApi, hasRuleArea } from '@/maps/geofences/shared/geofenceRuleAreaUtils'

import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import TabContainer from '@/components/ui/layout/TabContainer.vue'
import ConfirmDialog from 'primevue/confirmdialog'
import GeofenceRulesTab from '@/components/geofences/tabs/GeofenceRulesTab.vue'
import GeofenceTemplatesTab from '@/components/geofences/tabs/GeofenceTemplatesTab.vue'
import GeofenceEventsTab from '@/components/geofences/tabs/GeofenceEventsTab.vue'

const toast = useToast()
const confirm = useConfirm()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const locationStore = useLocationStore()
const timezone = useTimezone()
const FALLBACK_GEOFENCE_CENTER = [50.4501, 30.5234]
const LAST_KNOWN_MAP_ZOOM = 12

const tabs = computed(() => [
  { label: 'Rules', icon: 'pi pi-map', key: 'rules' },
  { label: 'Templates', icon: 'pi pi-envelope', key: 'templates' },
  {
    label: 'Events',
    icon: 'pi pi-bell',
    key: 'events',
    badge: unreadCount.value > 0 ? unreadCount.value : null,
    badgeType: 'danger'
  }
])

const activeTab = ref('rules')
const activeTabIndex = computed(() => tabs.value.findIndex(t => t.key === activeTab.value))

const rules = ref([])
const templates = ref([])
const friends = ref([])
const templateDeliveryCapabilities = ref({
  appriseEnabled: false,
  appriseConfigured: false
})

const savingRule = ref(false)
const savingTemplate = ref(false)
const testingTemplateConnection = ref(false)
const markingAllSeen = ref(false)
const markingEventId = ref(null)
const geofenceEvents = ref([])
const geofenceEventsTotal = ref(0)
const geofenceUnreadCount = ref(0)
const refreshingEvents = ref(false)
const geofenceEventsQuery = ref(defaultGeofenceEventsQuery())
const ruleFormErrors = ref({
  name: '',
  subjectUserIds: '',
  area: '',
  monitoring: ''
})
const templateFormErrors = ref({
  name: '',
  destination: '',
  titleTemplate: '',
  bodyTemplate: '',
  defaultForEnter: '',
  defaultForLeave: '',
  general: ''
})
const templateNameInput = ref(null)
const templateDestinationInput = ref(null)
const templateTitleInput = ref(null)
const templateBodyInput = ref(null)
const templateConnectionTestResult = ref(null)
const focusedTemplateField = ref('bodyTemplate')
const suppressDefaultToggleWatch = ref(false)
const TEMPLATE_MACRO_PATTERN = /\{\{\s*([a-zA-Z][a-zA-Z0-9]*)\s*}}/g
const DESTINATION_URL_PATTERN = /^[a-zA-Z][a-zA-Z0-9+.-]*:\/\/.+$/
const PREVIEW_TIMESTAMP_UTC = '2026-03-24T00:04:47Z'

const unreadCount = computed(() => geofenceUnreadCount.value)

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
const editingTemplateId = ref(null)
const geofenceMap = ref(null)
const geofenceMapAdapter = ref(null)
const mapCenter = ref(FALLBACK_GEOFENCE_CENTER)
const mapZoom = ref(11)
const lastKnownMapCenter = ref(null)
const initialRulesLoaded = ref(false)
const mapViewportInitialized = ref(false)
const knownSubjectLabels = ref({})

const statusOptions = [
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Paused', value: 'PAUSED' }
]

const ruleForm = ref(defaultRuleForm())
const templateForm = ref(defaultTemplateForm())
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
    const meLabel = authStore.userName ? `${authStore.userName} (Me)` : `${authStore.userEmail} (Me)`
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
    const label = knownSubjectLabels.value[normalizedValue] || `Unknown subject (${normalizedValue.slice(0, 8)})`
    pushOption(normalizedValue, `${label} (Unavailable)`, true)
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

const templateOptionItems = computed(() => {
  return templates.value.map(template => ({
    label: template.enabled ? template.name : `${template.name} (Disabled)`,
    value: template.id
  }))
})
const enabledDefaultEnterTemplate = computed(() =>
  templates.value.find(template => template.defaultForEnter && template.enabled) || null
)
const enabledDefaultLeaveTemplate = computed(() =>
  templates.value.find(template => template.defaultForLeave && template.enabled) || null
)
const hasEnabledDefaultEnterTemplate = computed(() => !!enabledDefaultEnterTemplate.value)
const hasEnabledDefaultLeaveTemplate = computed(() => !!enabledDefaultLeaveTemplate.value)

const enterTemplateOptions = computed(() => {
  const options = [...templateOptionItems.value]
  if (enabledDefaultEnterTemplate.value) {
    options.unshift({
      label: `Use default ENTER template (${enabledDefaultEnterTemplate.value.name})`,
      value: null
    })
  }
  return options
})

const leaveTemplateOptions = computed(() => {
  const options = [...templateOptionItems.value]
  if (enabledDefaultLeaveTemplate.value) {
    options.unshift({
      label: `Use default LEAVE template (${enabledDefaultLeaveTemplate.value.name})`,
      value: null
    })
  }
  return options
})
const templateNameById = computed(() => {
  const map = new Map()
  for (const template of templates.value) {
    if (template?.id !== null && template?.id !== undefined) {
      map.set(String(template.id), template.name || `Template ${template.id}`)
    }
  }
  return map
})
const appriseEnabled = computed(() => !!templateDeliveryCapabilities.value.appriseEnabled)
const appriseConfigured = computed(() => !!templateDeliveryCapabilities.value.appriseConfigured)

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

  return `Selected area: NE (${Number(northEastLat).toFixed(5)}, ${Number(northEastLon).toFixed(5)}), SW (${Number(southWestLat).toFixed(5)}, ${Number(southWestLon).toFixed(5)})`
})

const templateMacros = [
  {
    key: '{{subjectName}}',
    placeholder: 'subjectName',
    description: 'Tracked subject display name',
    example: 'Peter'
  },
  {
    key: '{{eventCode}}',
    placeholder: 'eventCode',
    description: 'Event code (ENTER or LEAVE)',
    example: 'ENTER'
  },
  {
    key: '{{eventVerb}}',
    placeholder: 'eventVerb',
    description: 'Event verb (entered or left)',
    example: 'entered'
  },
  {
    key: '{{geofenceName}}',
    placeholder: 'geofenceName',
    description: 'Geofence rule name',
    example: 'Home'
  },
  {
    key: '{{timestamp}}',
    placeholder: 'timestamp',
    description: 'Event timestamp in your timezone/date format',
    example: '03/24/2026 02:04:47'
  },
  {
    key: '{{timestampUtc}}',
    placeholder: 'timestampUtc',
    description: 'Event timestamp in UTC ISO-8601',
    example: PREVIEW_TIMESTAMP_UTC
  },
  {
    key: '{{lat}}',
    placeholder: 'lat',
    description: 'Point latitude',
    example: '49.547085'
  },
  {
    key: '{{lon}}',
    placeholder: 'lon',
    description: 'Point longitude',
    example: '25.595918'
  }
]
const allowedTemplateMacroNames = new Set(templateMacros.map(macro => macro.placeholder))
const currentDefaultEnterTemplate = computed(() => templates.value.find(template => template.defaultForEnter) || null)
const currentDefaultLeaveTemplate = computed(() => templates.value.find(template => template.defaultForLeave) || null)
const currentDefaultEnterName = computed(() => currentDefaultEnterTemplate.value?.name || 'None')
const currentDefaultLeaveName = computed(() => currentDefaultLeaveTemplate.value?.name || 'None')
const templatePreviewContexts = computed(() => {
  const base = {
    subjectName: authStore.userName || authStore.userEmail || 'John Doe',
    geofenceName: 'Home',
    timestamp: formatDate(PREVIEW_TIMESTAMP_UTC),
    timestampUtc: PREVIEW_TIMESTAMP_UTC,
    lat: '49.547085',
    lon: '25.595918'
  }

  return [
    {
      id: 'enter',
      severity: 'success',
      eventLabel: 'ENTER',
      ...base,
      eventCode: 'ENTER',
      eventVerb: 'entered'
    },
    {
      id: 'leave',
      severity: 'warn',
      eventLabel: 'LEAVE',
      ...base,
      eventCode: 'LEAVE',
      eventVerb: 'left'
    }
  ]
})

const templatePreviewToasts = computed(() => {
  return templatePreviewContexts.value.map((context) => {
    const renderedTitle = renderTemplateWithContext(templateForm.value.titleTemplate, context)
    const renderedBody = renderTemplateWithContext(templateForm.value.bodyTemplate, context)

    return {
      id: context.id,
      severity: context.severity,
      eventLabel: context.eventLabel,
      title: renderedTitle,
      body: renderedBody
    }
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

function defaultTemplateForm() {
  return {
    name: '',
    destination: '',
    titleTemplate: '',
    bodyTemplate: '',
    sendExternal: false,
    defaultForEnter: false,
    defaultForLeave: false,
    enabled: true
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

function clearTemplateFormErrors() {
  templateFormErrors.value = {
    name: '',
    destination: '',
    titleTemplate: '',
    bodyTemplate: '',
    defaultForEnter: '',
    defaultForLeave: '',
    general: ''
  }
}

function setFocusedTemplateField(field) {
  focusedTemplateField.value = field
}

function updateTemplateField({ field, value }) {
  if (!field || !Object.prototype.hasOwnProperty.call(templateForm.value, field)) {
    return
  }
  templateForm.value[field] = value
}

function resolveInputElement(field) {
  const source = field === 'titleTemplate' ? templateTitleInput.value : templateBodyInput.value
  if (!source) {
    return null
  }

  if (source.$el) {
    return source.$el.querySelector('input, textarea') || source.$el
  }
  if (source.$refs?.input) {
    return source.$refs.input
  }
  return source
}

function splitDestinationLines(destination, allowLegacySeparators = false) {
  const input = typeof destination === 'string' ? destination : ''
  const rawSegments = allowLegacySeparators
    ? input.replace(/;/g, '\n').replace(/,/g, '\n').split(/\r?\n/)
    : input.split(/\r?\n/)

  return rawSegments
    .map(segment => segment.trim())
    .filter(segment => segment.length > 0)
}

function normalizeDestination(destination) {
  return splitDestinationLines(destination).join('\n')
}

function validateDestinationLines(destination) {
  const destinationLines = splitDestinationLines(destination)
  for (let index = 0; index < destinationLines.length; index += 1) {
    const line = destinationLines[index]
    if (line.includes(',') || line.includes(';')) {
      return `Line ${index + 1} contains multiple URLs. Use one destination per line.`
    }
    if (!DESTINATION_URL_PATTERN.test(line)) {
      return `Line ${index + 1} must be a valid URL (scheme://...).`
    }
  }
  return ''
}

function confirmAction({
  message,
  header = 'Confirmation',
  acceptLabel = 'Confirm',
  rejectLabel = 'Cancel',
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

function validateTemplateSyntax(template, fieldLabel) {
  if (!template || !template.trim()) {
    return ''
  }

  const unknownMacros = new Set()
  let match
  TEMPLATE_MACRO_PATTERN.lastIndex = 0
  while ((match = TEMPLATE_MACRO_PATTERN.exec(template)) !== null) {
    const macroName = match[1]
    if (!allowedTemplateMacroNames.has(macroName)) {
      unknownMacros.add(macroName)
    }
  }

  if (unknownMacros.size > 0) {
    return `${fieldLabel} has unsupported macros: ${Array.from(unknownMacros).join(', ')}.`
  }

  TEMPLATE_MACRO_PATTERN.lastIndex = 0
  const withoutValidMacros = template.replace(TEMPLATE_MACRO_PATTERN, '')
  if (withoutValidMacros.includes('{{') || withoutValidMacros.includes('}}')) {
    return `${fieldLabel} has invalid macro syntax. Use {{macroName}}.`
  }

  return ''
}

function focusFirstTemplateError(errors) {
  const orderedFields = ['name', 'destination', 'titleTemplate', 'bodyTemplate']
  const firstField = orderedFields.find(field => errors[field])
  if (!firstField) {
    return
  }

  nextTick(() => {
    if (firstField === 'name') {
      const target = templateNameInput.value?.$el || templateNameInput.value
      target?.focus?.()
      return
    }
    if (firstField === 'destination') {
      const target = templateDestinationInput.value?.$el || templateDestinationInput.value
      target?.focus?.()
      return
    }
    const input = resolveInputElement(firstField)
    input?.focus?.()
  })
}

function validateTemplateForm() {
  clearTemplateFormErrors()
  const errors = {}
  const form = templateForm.value

  const normalizedName = (form.name || '').trim()
  if (!normalizedName) {
    errors.name = 'Template name is required.'
  } else if (normalizedName.length > 120) {
    errors.name = 'Template name must be 120 characters or less.'
  }

  const requiresDestination = appriseEnabled.value && form.sendExternal
  if (requiresDestination) {
    const destinationError = validateDestinationLines(form.destination)
    if (destinationError) {
      errors.destination = destinationError
    } else if (splitDestinationLines(form.destination).length === 0) {
      errors.destination = 'Add at least one destination URL or disable external providers.'
    }
  }

  const titleSyntaxError = validateTemplateSyntax(form.titleTemplate, 'Title template')
  if (titleSyntaxError) {
    errors.titleTemplate = titleSyntaxError
  }

  const bodySyntaxError = validateTemplateSyntax(form.bodyTemplate, 'Body template')
  if (bodySyntaxError) {
    errors.bodyTemplate = bodySyntaxError
  }

  templateFormErrors.value = {
    ...templateFormErrors.value,
    ...errors
  }
  if (Object.keys(errors).length > 0) {
    focusFirstTemplateError(errors)
    return false
  }
  return true
}

function renderTemplateWithContext(template, context) {
  if (!template || !template.trim()) {
    return ''
  }
  TEMPLATE_MACRO_PATTERN.lastIndex = 0
  return template.replace(TEMPLATE_MACRO_PATTERN, (_, macroName) => context[macroName] ?? '')
}

function insertMacro(macroKey) {
  const targetField = focusedTemplateField.value === 'titleTemplate' ? 'titleTemplate' : 'bodyTemplate'
  const currentValue = templateForm.value[targetField] || ''
  const input = resolveInputElement(targetField)

  if (input && typeof input.selectionStart === 'number' && typeof input.selectionEnd === 'number') {
    const start = input.selectionStart
    const end = input.selectionEnd
    const updated = `${currentValue.slice(0, start)}${macroKey}${currentValue.slice(end)}`
    templateForm.value[targetField] = updated
    nextTick(() => {
      input.focus()
      const cursor = start + macroKey.length
      input.setSelectionRange(cursor, cursor)
    })
  } else if (!currentValue) {
    templateForm.value[targetField] = macroKey
  } else {
    templateForm.value[targetField] = `${currentValue} ${macroKey}`
  }

  if (templateFormErrors.value[targetField]) {
    templateFormErrors.value[targetField] = ''
  }
}

function extractApiErrorMessage(error, fallback) {
  return error?.response?.data?.message
    || error?.response?.data?.error
    || error?.response?.data?.data?.message
    || error?.userMessage
    || error?.message
    || fallback
}

function validateRuleForm() {
  clearRuleFormErrors()
  const errors = {}
  const form = ruleForm.value

  if (!form.name || !form.name.trim()) {
    errors.name = 'Rule name is required.'
  }

  const selectedSubjects = Array.isArray(form.subjectUserIds)
    ? form.subjectUserIds.map(normalizeSubjectId).filter(Boolean)
    : []
  if (selectedSubjects.length === 0) {
    errors.subjectUserIds = 'Please select at least one subject to track.'
  }

  if (!hasValidAreaBounds(form)) {
    errors.area = 'Please draw a rectangle on the map.'
  }

  if (!form.monitorEnter && !form.monitorLeave) {
    errors.monitoring = 'Enable at least one event type (Enter or Leave).'
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
    popupBuilder: buildRuleAreaPopupContent
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
  const response = await apiService.get('/geofences/rules')
  const rawRules = response?.data || []
  rules.value = rawRules.map(normalizeRule)
  initialRulesLoaded.value = true
  syncAllRuleAreasOnMap()
  syncInitialRulesMapViewport()
}

async function loadTemplates() {
  const response = await apiService.get('/geofences/templates')
  templates.value = response?.data || []
  syncAllRuleAreasOnMap()
}

async function loadTemplateDeliveryCapabilities() {
  const response = await apiService.get('/geofences/templates/capabilities')
  const data = response?.data || {}
  templateDeliveryCapabilities.value = {
    appriseEnabled: !!data.appriseEnabled,
    appriseConfigured: !!data.appriseConfigured
  }
}

async function testTemplateConnection() {
  if (!appriseEnabled.value) {
    return
  }

  if (!templateForm.value.sendExternal) {
    toast.add({
      severity: 'warn',
      summary: 'External providers disabled',
      detail: 'Enable external delivery first to test connection.',
      life: 3500
    })
    return
  }

  const destinationError = validateDestinationLines(templateForm.value.destination)
  const destinationLines = splitDestinationLines(templateForm.value.destination)
  if (destinationError || destinationLines.length === 0) {
    const detail = destinationError || 'Add at least one destination URL to test connection.'
    templateFormErrors.value.destination = detail
    templateConnectionTestResult.value = null
    toast.add({
      severity: 'error',
      summary: 'Invalid Destination URL',
      detail,
      life: 4500
    })
    focusFirstTemplateError({ destination: detail })
    return
  }

  templateConnectionTestResult.value = null
  testingTemplateConnection.value = true
  try {
    const enterPreview = templatePreviewToasts.value.find(item => item.id === 'enter')
    const payload = {
      destination: normalizeDestination(templateForm.value.destination),
      title: enterPreview?.title?.trim() ? enterPreview.title.trim() : null,
      body: enterPreview?.body?.trim() ? enterPreview.body.trim() : null
    }

    const response = await apiService.post('/geofences/templates/test-connection', payload)
    const detail = response?.message || response?.data?.message || 'Connection test succeeded'
    const statusCode = response?.data?.statusCode ?? null

    templateConnectionTestResult.value = {
      severity: 'success',
      summary: 'Connection test succeeded',
      detail,
      statusCode
    }

    toast.add({
      severity: 'success',
      summary: 'Connection OK',
      detail,
      life: 4000
    })
  } catch (error) {
    const detail = extractApiErrorMessage(error, 'Connection test failed')
    const statusCode = error?.response?.data?.data?.statusCode ?? null
    templateConnectionTestResult.value = {
      severity: 'error',
      summary: 'Connection test failed',
      detail,
      statusCode
    }
    toast.add({
      severity: 'error',
      summary: 'Connection Failed',
      detail,
      life: 5000
    })
  } finally {
    testingTemplateConnection.value = false
  }
}

async function refreshEvents() {
  if (refreshingEvents.value) {
    return
  }

  refreshingEvents.value = true
  try {
    const params = {
      page: geofenceEventsQuery.value.page,
      pageSize: geofenceEventsQuery.value.pageSize,
      sortBy: geofenceEventsQuery.value.sortBy,
      sortDir: geofenceEventsQuery.value.sortDir,
      unreadOnly: geofenceEventsQuery.value.unreadOnly
    }
    if (geofenceEventsQuery.value.dateFrom) {
      params.dateFrom = geofenceEventsQuery.value.dateFrom
    }
    if (geofenceEventsQuery.value.dateTo) {
      params.dateTo = geofenceEventsQuery.value.dateTo
    }
    if (Array.isArray(geofenceEventsQuery.value.subjectUserIds) && geofenceEventsQuery.value.subjectUserIds.length > 0) {
      params.subjectUserIds = geofenceEventsQuery.value.subjectUserIds.join(',')
    }
    if (Array.isArray(geofenceEventsQuery.value.eventTypes) && geofenceEventsQuery.value.eventTypes.length > 0) {
      params.eventTypes = geofenceEventsQuery.value.eventTypes.join(',')
    }

    const [eventsPageResponse, unreadResponse] = await Promise.all([
      apiService.get('/geofences/events', params),
      apiService.get('/geofences/events/unread-count')
    ])

    geofenceEvents.value = Array.isArray(eventsPageResponse?.data?.items)
      ? eventsPageResponse.data.items
      : []
    geofenceEventsTotal.value = Number(eventsPageResponse?.data?.totalCount || 0)
    geofenceUnreadCount.value = Number(unreadResponse?.data?.count || 0)
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Events Error',
      detail: extractApiErrorMessage(error, 'Failed to load geofence events'),
      life: 5000
    })
  } finally {
    refreshingEvents.value = false
  }
}

async function loadFriends() {
  const response = await apiService.get('/friends')
  const all = response?.data || []
  friends.value = all.filter(friend => friend.friendSharesLiveLocation)
}

async function saveRule() {
  if (!validateRuleForm()) {
    toast.add({
      severity: 'warn',
      summary: 'Validation Error',
      detail: 'Please fix the highlighted fields before saving.',
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
      await apiService.patch(`/geofences/rules/${editingRuleId.value}`, payload)
      toast.add({ severity: 'success', summary: 'Updated', detail: 'Rule updated', life: 3000 })
    } else {
      await apiService.post('/geofences/rules', payload)
      toast.add({ severity: 'success', summary: 'Created', detail: 'Rule created', life: 3000 })
    }

    resetRuleForm()
    await loadRules()
    await refreshEvents()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Rule Error',
      detail: extractApiErrorMessage(error, 'Failed to save rule'),
      life: 5000
    })
  } finally {
    savingRule.value = false
  }
}

function editRule(rule) {
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
  const confirmed = await confirmAction({
    header: 'Delete Rule',
    message: `Delete geofence rule "${rule.name}"?`,
    acceptLabel: 'Delete',
    rejectLabel: 'Cancel',
    acceptClass: 'p-button-danger'
  })
  if (!confirmed) {
    return
  }

  try {
    await apiService.delete(`/geofences/rules/${rule.id}`)
    toast.add({ severity: 'success', summary: 'Deleted', detail: 'Rule deleted', life: 3000 })
    await loadRules()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Delete Error',
      detail: extractApiErrorMessage(error, 'Failed to delete rule'),
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

async function saveTemplate() {
  if (!validateTemplateForm()) {
    toast.add({
      severity: 'warn',
      summary: 'Validation Error',
      detail: 'Please fix template form errors before saving.',
      life: 4500
    })
    return
  }

  savingTemplate.value = true
  try {
    const normalizedDestination = normalizeDestination(templateForm.value.destination)
    let destination = ''
    if (appriseEnabled.value) {
      if (templateForm.value.sendExternal) {
        destination = normalizedDestination
      } else if (editingTemplateId.value && !appriseConfigured.value) {
        destination = normalizedDestination
      } else {
        destination = ''
      }
    } else {
      destination = editingTemplateId.value ? normalizedDestination : ''
    }

    const payload = {
      ...templateForm.value,
      name: templateForm.value.name.trim(),
      destination,
      titleTemplate: templateForm.value.titleTemplate?.trim() || '',
      bodyTemplate: templateForm.value.bodyTemplate?.trim() || ''
    }
    delete payload.sendExternal

    if (editingTemplateId.value) {
      await apiService.patch(`/geofences/templates/${editingTemplateId.value}`, payload)
      toast.add({ severity: 'success', summary: 'Updated', detail: 'Template updated', life: 3000 })
    } else {
      await apiService.post('/geofences/templates', payload)
      toast.add({ severity: 'success', summary: 'Created', detail: 'Template created', life: 3000 })
    }

    resetTemplateForm()
    await Promise.all([loadTemplates(), loadTemplateDeliveryCapabilities()])
    await loadRules()
  } catch (error) {
    templateFormErrors.value.general = extractApiErrorMessage(error, 'Failed to save template')
    toast.add({
      severity: 'error',
      summary: 'Template Error',
      detail: templateFormErrors.value.general,
      life: 5000
    })
  } finally {
    savingTemplate.value = false
  }
}

function editTemplate(template) {
  clearTemplateFormErrors()
  templateConnectionTestResult.value = null
  editingTemplateId.value = template.id
  const destination = template.destination || ''
  const hasExternalDestination = splitDestinationLines(destination, true).length > 0
  templateForm.value = {
    name: template.name,
    destination,
    titleTemplate: template.titleTemplate || '',
    bodyTemplate: template.bodyTemplate || '',
    sendExternal: appriseEnabled.value ? hasExternalDestination : false,
    defaultForEnter: !!template.defaultForEnter,
    defaultForLeave: !!template.defaultForLeave,
    enabled: !!template.enabled
  }
}

async function deleteTemplate(template) {
  const usages = rules.value.filter(rule =>
    rule.enterTemplateId === template.id || rule.leaveTemplateId === template.id
  )
  const warning = usages.length > 0
    ? `\n\nWarning: this template is currently used by ${usages.length} rule(s).`
    : ''

  const confirmed = await confirmAction({
    header: 'Delete Template',
    message: `Delete template "${template.name}"?${warning}`,
    acceptLabel: 'Delete',
    rejectLabel: 'Cancel',
    acceptClass: 'p-button-danger'
  })
  if (!confirmed) {
    return
  }

  try {
    await apiService.delete(`/geofences/templates/${template.id}`)
    toast.add({ severity: 'success', summary: 'Deleted', detail: 'Template deleted', life: 3000 })
    await loadTemplates()
    await loadRules()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Delete Error',
      detail: extractApiErrorMessage(error, 'Failed to delete template'),
      life: 5000
    })
  }
}

function resetTemplateForm() {
  editingTemplateId.value = null
  templateForm.value = defaultTemplateForm()
  clearTemplateFormErrors()
  templateConnectionTestResult.value = null
  focusedTemplateField.value = 'bodyTemplate'
}

async function markEventSeen(event) {
  if (!event?.id) {
    return
  }

  markingEventId.value = event.id
  try {
    await apiService.post(`/geofences/events/${event.id}/seen`, {})
    await refreshEvents()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Event Error',
      detail: extractApiErrorMessage(error, 'Failed to mark event as seen'),
      life: 5000
    })
  } finally {
    markingEventId.value = null
  }
}

async function markAllEventsSeen() {
  markingAllSeen.value = true
  try {
    await apiService.post('/geofences/events/seen-all', {})
    await refreshEvents()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Event Error',
      detail: extractApiErrorMessage(error, 'Failed to mark events as seen'),
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
  if (rule.monitorEnter) events.push('Enter')
  if (rule.monitorLeave) events.push('Leave')
  return events.join(' / ')
}

function defaultSummary(template) {
  const flags = []
  if (template.defaultForEnter) flags.push('Enter')
  if (template.defaultForLeave) flags.push('Leave')
  return flags.length ? flags.join(' + ') : 'No'
}

function formatDestination(destination) {
  const destinations = splitDestinationLines(destination, true)
  if (destinations.length === 0) {
    return 'In-app only'
  }
  const firstMasked = maskDestination(destinations[0])
  if (destinations.length === 1) {
    return firstMasked
  }
  return `${firstMasked} (+${destinations.length - 1} more)`
}

function maskDestination(destination) {
  const normalized = String(destination || '').trim()
  if (!normalized) {
    return '***'
  }
  const matched = normalized.match(/^([a-zA-Z][a-zA-Z0-9+.-]*:\/\/).+$/)
  if (matched) {
    return `${matched[1]}***`
  }
  return '***'
}

function formatDate(value) {
  if (!value) return '-'
  return `${timezone.formatDateDisplay(value)} ${timezone.format(value, 'HH:mm:ss')}`
}

function resolveRuleTemplateLabel(templateId, type) {
  if (templateId !== null && templateId !== undefined && templateId !== '') {
    const resolved = templateNameById.value.get(String(templateId))
    if (resolved) {
      return resolved
    }
    return `Unknown template (${String(templateId).slice(0, 8)})`
  }

  if (type === 'enter') {
    return enabledDefaultEnterTemplate.value
      ? `Default (${enabledDefaultEnterTemplate.value.name})`
      : 'Built-in message'
  }

  return enabledDefaultLeaveTemplate.value
    ? `Default (${enabledDefaultLeaveTemplate.value.name})`
    : 'Built-in message'
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

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

function buildRuleAreaPopupContent(rule) {
  const rows = [
    ['Subjects', formatRuleSubjects(rule)],
    ['Status', rule?.status || '-'],
    ['Events', eventSummary(rule) || 'None'],
    ['Cooldown', `${Number(rule?.cooldownSeconds || 0)} sec`],
    ['Enter template', resolveRuleTemplateLabel(rule?.enterTemplateId, 'enter')],
    ['Leave template', resolveRuleTemplateLabel(rule?.leaveTemplateId, 'leave')]
  ]

  const rowsHtml = rows
    .map(([label, value]) => (
      `<div class="geofence-area-tooltip__row">
        <span class="geofence-area-tooltip__label">${escapeHtml(label)}</span>
        <span class="geofence-area-tooltip__value">${escapeHtml(value)}</span>
      </div>`
    ))
    .join('')

  return `
    <div class="geofence-area-tooltip">
      <div class="geofence-area-tooltip__title">${escapeHtml(rule?.name || 'Geofence')}</div>
      ${rowsHtml}
    </div>
  `
}

function deliverySeverity(status) {
  if (status === 'SENT') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PENDING') return 'info'
  return 'secondary'
}

function handleDefaultToggleChange(type, enabled) {
  if (!enabled || suppressDefaultToggleWatch.value) {
    return
  }

  const currentDefault = type === 'enter' ? currentDefaultEnterTemplate.value : currentDefaultLeaveTemplate.value
  if (!currentDefault) {
    return
  }

  if (editingTemplateId.value && currentDefault.id === editingTemplateId.value) {
    return
  }

  void confirmAction({
    header: 'Set Default Template',
    message: `Set this as default ${type.toUpperCase()} template? Current default "${currentDefault.name}" will be unset.`,
    acceptLabel: 'Set Default',
    rejectLabel: 'Cancel',
    acceptClass: 'p-button-primary'
  }).then((confirmed) => {
    if (confirmed) {
      return
    }
    suppressDefaultToggleWatch.value = true
    if (type === 'enter') {
      templateForm.value.defaultForEnter = false
    } else {
      templateForm.value.defaultForLeave = false
    }
    nextTick(() => {
      suppressDefaultToggleWatch.value = false
    })
  })
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
      summary: 'Load Error',
      detail: extractApiErrorMessage(error, 'Failed to load geofence data'),
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

watch(
  () => templateForm.value.name,
  (value) => {
    if (templateFormErrors.value.name && value && value.trim()) {
      templateFormErrors.value.name = ''
    }
    if (templateFormErrors.value.general) {
      templateFormErrors.value.general = ''
    }
  }
)

watch(
  () => templateForm.value.destination,
  () => {
    if (templateFormErrors.value.destination) {
      templateFormErrors.value.destination = ''
    }
    if (templateFormErrors.value.general) {
      templateFormErrors.value.general = ''
    }
    templateConnectionTestResult.value = null
  }
)

watch(
  () => templateForm.value.titleTemplate,
  () => {
    if (templateFormErrors.value.titleTemplate) {
      templateFormErrors.value.titleTemplate = ''
    }
    if (templateFormErrors.value.general) {
      templateFormErrors.value.general = ''
    }
    templateConnectionTestResult.value = null
  }
)

watch(
  () => templateForm.value.bodyTemplate,
  () => {
    if (templateFormErrors.value.bodyTemplate) {
      templateFormErrors.value.bodyTemplate = ''
    }
    if (templateFormErrors.value.general) {
      templateFormErrors.value.general = ''
    }
    templateConnectionTestResult.value = null
  }
)

watch(
  () => templateForm.value.sendExternal,
  (enabled) => {
    if (!enabled && templateFormErrors.value.destination) {
      templateFormErrors.value.destination = ''
    }
    if (templateFormErrors.value.general) {
      templateFormErrors.value.general = ''
    }
    templateConnectionTestResult.value = null
  }
)

watch(
  () => appriseEnabled.value,
  (enabled) => {
    if (!enabled && templateForm.value.sendExternal) {
      templateForm.value.sendExternal = false
    }
    if (!enabled && templateFormErrors.value.destination) {
      templateFormErrors.value.destination = ''
    }
    if (!enabled) {
      templateConnectionTestResult.value = null
    }
  }
)

watch(
  () => appriseConfigured.value,
  (configured) => {
    if (!configured && templateForm.value.sendExternal) {
      templateForm.value.sendExternal = false
    }
    if (!configured) {
      templateConnectionTestResult.value = null
    }
  }
)

watch(
  () => templateForm.value.defaultForEnter,
  (enabled) => {
    handleDefaultToggleChange('enter', enabled)
    if (templateFormErrors.value.defaultForEnter && enabled) {
      templateFormErrors.value.defaultForEnter = ''
    }
    if (templateFormErrors.value.general) {
      templateFormErrors.value.general = ''
    }
  }
)

watch(
  () => templateForm.value.defaultForLeave,
  (enabled) => {
    handleDefaultToggleChange('leave', enabled)
    if (templateFormErrors.value.defaultForLeave && enabled) {
      templateFormErrors.value.defaultForLeave = ''
    }
    if (templateFormErrors.value.general) {
      templateFormErrors.value.general = ''
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
:deep(.geofence-area-popup .leaflet-popup-content-wrapper) {
  border-radius: 10px;
}

:deep(.geofence-area-popup .leaflet-popup-content) {
  margin: 0;
}

:deep(.geofence-area-popup .maplibregl-popup-content) {
  border-radius: 10px;
  margin: 0;
  padding: 0;
}

:deep(.geofence-area-tooltip) {
  display: grid;
  gap: 0.35rem;
  min-width: 220px;
  padding: 0.6rem 0.7rem;
}

:deep(.geofence-area-tooltip__title) {
  font-size: 0.96rem;
  font-weight: 700;
  color: var(--text-color);
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
  color: var(--text-color-secondary);
}

:deep(.geofence-area-tooltip__value) {
  font-size: 0.83rem;
  line-height: 1.25;
  color: var(--text-color);
  overflow-wrap: anywhere;
}

:deep(.p-dark .geofence-area-popup .leaflet-popup-content-wrapper) {
  background: #1e293b;
}

:deep(.p-dark .geofence-area-popup .leaflet-popup-tip) {
  background: #1e293b;
}

:deep(.p-dark .geofence-area-popup .maplibregl-popup-content) {
  background: #1e293b;
}

:deep(.p-dark .geofence-area-popup .maplibregl-popup-tip) {
  border-top-color: #1e293b;
}
</style>
