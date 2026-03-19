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
          :templatePreview="templatePreview"
          :templateMacros="templateMacros"
          :currentDefaultEnterName="currentDefaultEnterName"
          :currentDefaultLeaveName="currentDefaultLeaveName"
          :savingTemplate="savingTemplate"
          :templates="templates"
          :formatDestination="formatDestination"
          :defaultSummary="defaultSummary"
          @focus-template-field="setFocusedTemplateField"
          @insert-macro="insertMacro"
          @save-template="saveTemplate"
          @reset-template-form="resetTemplateForm"
          @load-templates="loadTemplates"
          @edit-template="editTemplate"
          @delete-template="deleteTemplate"
        />

        <GeofenceEventsTab
          v-else
          :events="events"
          :unreadCount="unreadCount"
          :unreadOnly="unreadOnly"
          :markingAllSeen="markingAllSeen"
          :markingEventId="markingEventId"
          :formatDate="formatDate"
          :deliverySeverity="deliverySeverity"
          @toggle-unread-only="handleUnreadOnlyToggle"
          @mark-all-events-seen="markAllEventsSeen"
          @refresh-events="refreshEvents"
          @mark-event-seen="markEventSeen"
        />
      </TabContainer>

    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationsStore } from '@/stores/notifications'
import apiService from '@/utils/apiService'
import L from 'leaflet'
import { useRectangleDrawing } from '@/composables/useRectangleDrawing'
import { useTimezone } from '@/composables/useTimezone'

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
const notificationsStore = useNotificationsStore()
const timezone = useTimezone()

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

const savingRule = ref(false)
const savingTemplate = ref(false)
const unreadOnly = ref(false)
const markingAllSeen = ref(false)
const markingEventId = ref(null)
const ruleFormErrors = ref({
  name: '',
  subjectUserId: '',
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
const focusedTemplateField = ref('bodyTemplate')
const suppressDefaultToggleWatch = ref(false)
const TEMPLATE_MACRO_PATTERN = /\{\{\s*([a-zA-Z][a-zA-Z0-9]*)\s*}}/g
const DESTINATION_URL_PATTERN = /^[a-zA-Z][a-zA-Z0-9+.-]*:\/\/.+$/
const PREVIEW_TIMESTAMP_UTC = '2026-03-24T00:04:47Z'

const events = computed(() => {
  if (unreadOnly.value) {
    return notificationsStore.items.filter(item => !item.seen)
  }
  return notificationsStore.items
})

const unreadCount = computed(() => notificationsStore.unreadCount)

const editingRuleId = ref(null)
const editingTemplateId = ref(null)
const geofenceMap = ref(null)
const rectangleLayer = ref(null)
const mapCenter = ref([50.4501, 30.5234])
const mapZoom = ref(11)

const statusOptions = [
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Paused', value: 'PAUSED' }
]

const ruleForm = ref(defaultRuleForm())
const templateForm = ref(defaultTemplateForm())
const rectangleDrawing = useRectangleDrawing({
  onRectangleCreated: ({ bounds, layer }) => {
    ruleFormErrors.value.area = ''
    ruleForm.value.northEastLat = Number(bounds.getNorthEast().lat.toFixed(6))
    ruleForm.value.northEastLon = Number(bounds.getNorthEast().lng.toFixed(6))
    ruleForm.value.southWestLat = Number(bounds.getSouthWest().lat.toFixed(6))
    ruleForm.value.southWestLon = Number(bounds.getSouthWest().lng.toFixed(6))

    if (geofenceMap.value) {
      if (rectangleLayer.value) {
        geofenceMap.value.removeLayer(rectangleLayer.value)
      }
      rectangleLayer.value = layer.addTo(geofenceMap.value)
      geofenceMap.value.fitBounds(bounds, { padding: [20, 20] })
    }
  }
})

const subjectOptions = computed(() => {
  const items = []

  if (authStore.userId) {
    const meLabel = authStore.userName ? `${authStore.userName} (Me)` : `${authStore.userEmail} (Me)`
    items.push({ label: meLabel, value: authStore.userId })
  }

  for (const friend of friends.value) {
    const label = friend.fullName || friend.email
    items.push({ label, value: friend.friendId || friend.userId })
  }

  return items
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
const templatePreviewContext = computed(() => ({
  subjectName: authStore.userName || authStore.userEmail || 'Peter',
  eventCode: 'ENTER',
  eventVerb: 'entered',
  geofenceName: 'Home',
  timestamp: formatDate(PREVIEW_TIMESTAMP_UTC),
  timestampUtc: PREVIEW_TIMESTAMP_UTC,
  lat: '49.547085',
  lon: '25.595918'
}))
const templatePreview = computed(() => {
  return {
    title: renderTemplateWithContext(templateForm.value.titleTemplate, templatePreviewContext.value),
    body: renderTemplateWithContext(templateForm.value.bodyTemplate, templatePreviewContext.value)
  }
})

function defaultRuleForm() {
  return {
    name: '',
    subjectUserId: authStore.userId || null,
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
    defaultForEnter: false,
    defaultForLeave: false,
    enabled: true
  }
}

function clearRuleFormErrors() {
  ruleFormErrors.value = {
    name: '',
    subjectUserId: '',
    area: '',
    monitoring: ''
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

  const destinationLines = splitDestinationLines(form.destination)
  for (let index = 0; index < destinationLines.length; index += 1) {
    const line = destinationLines[index]
    if (line.includes(',') || line.includes(';')) {
      errors.destination = `Line ${index + 1} contains multiple URLs. Use one destination per line.`
      break
    }
    if (!DESTINATION_URL_PATTERN.test(line)) {
      errors.destination = `Line ${index + 1} must be a valid URL (scheme://...).`
      break
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

  if (!form.subjectUserId) {
    errors.subjectUserId = 'Please select who this rule tracks.'
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
  rectangleDrawing.initialize(map)
  syncMapRectangleFromForm()
}

function startRectangleDraw() {
  if (!geofenceMap.value) {
    return
  }
  rectangleDrawing.startDrawing()
}

function syncMapRectangleFromForm(focus = false) {
  if (!geofenceMap.value) {
    return
  }
  const { northEastLat, northEastLon, southWestLat, southWestLon } = ruleForm.value
  if ([northEastLat, northEastLon, southWestLat, southWestLon].some(v => v === null || v === undefined)) {
    return
  }

  const bounds = L.latLngBounds(
    [southWestLat, southWestLon],
    [northEastLat, northEastLon]
  )

  if (rectangleLayer.value) {
    geofenceMap.value.removeLayer(rectangleLayer.value)
  }
  rectangleLayer.value = L.rectangle(bounds, {
    color: '#e91e63',
    weight: 2,
    fill: false
  }).addTo(geofenceMap.value)

  if (focus) {
    geofenceMap.value.fitBounds(bounds, { padding: [20, 20] })
  }
}

async function loadRules() {
  const response = await apiService.get('/geofences/rules')
  rules.value = response?.data || []
}

async function loadTemplates() {
  const response = await apiService.get('/geofences/templates')
  templates.value = response?.data || []
}

async function refreshEvents() {
  await notificationsStore.refresh({
    emitToasts: false,
    emitBrowser: false,
    emitStartupSummary: false
  })
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
    const payload = {
      ...ruleForm.value,
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
    subjectUserId: rule.subjectUserId,
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
  if (geofenceMap.value && rectangleLayer.value) {
    geofenceMap.value.removeLayer(rectangleLayer.value)
    rectangleLayer.value = null
  }
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
    const payload = {
      ...templateForm.value,
      name: templateForm.value.name.trim(),
      destination: normalizeDestination(templateForm.value.destination),
      titleTemplate: templateForm.value.titleTemplate?.trim() || '',
      bodyTemplate: templateForm.value.bodyTemplate?.trim() || ''
    }

    if (editingTemplateId.value) {
      await apiService.patch(`/geofences/templates/${editingTemplateId.value}`, payload)
      toast.add({ severity: 'success', summary: 'Updated', detail: 'Template updated', life: 3000 })
    } else {
      await apiService.post('/geofences/templates', payload)
      toast.add({ severity: 'success', summary: 'Created', detail: 'Template created', life: 3000 })
    }

    resetTemplateForm()
    await loadTemplates()
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
  editingTemplateId.value = template.id
  templateForm.value = {
    name: template.name,
    destination: template.destination || '',
    titleTemplate: template.titleTemplate || '',
    bodyTemplate: template.bodyTemplate || '',
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
  focusedTemplateField.value = 'bodyTemplate'
}

async function markEventSeen(event) {
  if (!event?.id) {
    return
  }

  markingEventId.value = event.id
  try {
    await notificationsStore.markSeen(event.id)
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
    await notificationsStore.markAllSeen()
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

function handleUnreadOnlyToggle(nextValue) {
  unreadOnly.value = !!nextValue
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
    await Promise.all([loadFriends(), loadTemplates(), loadRules(), refreshEvents()])
    if (!ruleForm.value.subjectUserId && authStore.userId) {
      ruleForm.value.subjectUserId = authStore.userId
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
  () => ruleForm.value.subjectUserId,
  (value) => {
    if (ruleFormErrors.value.subjectUserId && value) {
      ruleFormErrors.value.subjectUserId = ''
    }
  }
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
</script>
