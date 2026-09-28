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
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
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
const savingTemplate = ref(false)
const testingTemplateConnection = ref(false)
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
const templateFormErrors = ref({
  name: '',
  destination: '',
  appriseConfigKey: '',
  appriseTag: '',
  titleTemplate: '',
  bodyTemplate: '',
  defaultForEnter: '',
  defaultForLeave: '',
  general: ''
})
const templateNameInput = ref(null)
const templateDestinationInput = ref(null)
const templateConfigKeyInput = ref(null)
const templateTitleInput = ref(null)
const templateBodyInput = ref(null)
const templateConnectionTestResult = ref(null)
const focusedTemplateField = ref('bodyTemplate')
const suppressDefaultToggleWatch = ref(false)
const TEMPLATE_MACRO_PATTERN = /\{\{\s*([a-zA-Z][a-zA-Z0-9]*)\s*}}/g
const DESTINATION_URL_PATTERN = /^[a-zA-Z][a-zA-Z0-9+.-]*:\/\/.+$/
const PREVIEW_TIMESTAMP_UTC = '2026-03-24T00:04:47Z'
const APPRISE_EXTERNAL_ROUTING_MODES = {
  URLS: 'URLS',
  KEY_TAG: 'KEY_TAG'
}

const unreadCount = computed(() => geofenceUnreadCount.value)
const demoReadOnly = computed(() => authStore.demoReadOnly)

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

const statusOptions = computed(() => [
  { label: t('geofences.rulesTab.statusActive'), value: 'ACTIVE' },
  { label: t('geofences.rulesTab.statusPaused'), value: 'PAUSED' }
])

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

const templateOptionItems = computed(() => {
  return templates.value.map(template => ({
    label: template.enabled ? template.name : t('geofences.page.disabledSuffix', { name: template.name }),
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
      label: t('geofences.page.useDefaultEnterTemplate', { name: enabledDefaultEnterTemplate.value.name }),
      value: null
    })
  }
  return options
})

const leaveTemplateOptions = computed(() => {
  const options = [...templateOptionItems.value]
  if (enabledDefaultLeaveTemplate.value) {
    options.unshift({
      label: t('geofences.page.useDefaultLeaveTemplate', { name: enabledDefaultLeaveTemplate.value.name }),
      value: null
    })
  }
  return options
})
const templateNameById = computed(() => {
  const map = new Map()
  for (const template of templates.value) {
    if (template?.id !== null && template?.id !== undefined) {
      map.set(String(template.id), template.name || t('geofences.page.templateFallbackName', { id: template.id }))
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

  return t('geofences.page.selectedAreaSummary', {
    neLat: Number(northEastLat).toFixed(5),
    neLon: Number(northEastLon).toFixed(5),
    swLat: Number(southWestLat).toFixed(5),
    swLon: Number(southWestLon).toFixed(5)
  })
})

const templateMacros = computed(() => [
  {
    key: '{{subjectName}}',
    placeholder: 'subjectName',
    description: t('geofences.page.macros.subjectName'),
    example: 'Peter'
  },
  {
    key: '{{eventCode}}',
    placeholder: 'eventCode',
    description: t('geofences.page.macros.eventCode'),
    example: 'ENTER'
  },
  {
    key: '{{eventVerb}}',
    placeholder: 'eventVerb',
    description: t('geofences.page.macros.eventVerb'),
    example: 'entered'
  },
  {
    key: '{{geofenceName}}',
    placeholder: 'geofenceName',
    description: t('geofences.page.macros.geofenceName'),
    example: 'Home'
  },
  {
    key: '{{timestamp}}',
    placeholder: 'timestamp',
    description: t('geofences.page.macros.timestamp'),
    example: '03/24/2026 02:04:47'
  },
  {
    key: '{{timestampUtc}}',
    placeholder: 'timestampUtc',
    description: t('geofences.page.macros.timestampUtc'),
    example: PREVIEW_TIMESTAMP_UTC
  },
  {
    key: '{{lat}}',
    placeholder: 'lat',
    description: t('geofences.page.macros.lat'),
    example: '49.547085'
  },
  {
    key: '{{lon}}',
    placeholder: 'lon',
    description: t('geofences.page.macros.lon'),
    example: '25.595918'
  }
])
const allowedTemplateMacroNames = new Set(templateMacros.value.map(macro => macro.placeholder))
const currentDefaultEnterTemplate = computed(() => templates.value.find(template => template.defaultForEnter) || null)
const currentDefaultLeaveTemplate = computed(() => templates.value.find(template => template.defaultForLeave) || null)
const currentDefaultEnterName = computed(() => currentDefaultEnterTemplate.value?.name || t('geofences.page.none'))
const currentDefaultLeaveName = computed(() => currentDefaultLeaveTemplate.value?.name || t('geofences.page.none'))
const templatePreviewContexts = computed(() => {
  const base = {
    subjectName: authStore.userName || authStore.userEmail || t('geofences.page.previewDefaults.subjectName'),
    geofenceName: t('geofences.page.previewDefaults.geofenceName'),
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
      eventVerb: t('geofences.page.previewDefaults.eventVerbEnter')
    },
    {
      id: 'leave',
      severity: 'warn',
      eventLabel: 'LEAVE',
      ...base,
      eventCode: 'LEAVE',
      eventVerb: t('geofences.page.previewDefaults.eventVerbLeave')
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

const appriseRoutingModeOptions = computed(() => [
  { label: t('geofences.page.routingModes.destinationUrls'), value: APPRISE_EXTERNAL_ROUTING_MODES.URLS },
  { label: t('geofences.page.routingModes.configKeyTag'), value: APPRISE_EXTERNAL_ROUTING_MODES.KEY_TAG }
])

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
    externalRoutingMode: APPRISE_EXTERNAL_ROUTING_MODES.URLS,
    appriseConfigKey: '',
    appriseTag: '',
    titleTemplate: '',
    bodyTemplate: '',
    sendInApp: true,
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
    appriseConfigKey: '',
    appriseTag: '',
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
  if (demoReadOnly.value) {
    return
  }

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
      return t('geofences.page.validation.destinationMultipleUrls', { line: index + 1 })
    }
    if (!DESTINATION_URL_PATTERN.test(line)) {
      return t('geofences.page.validation.destinationInvalidUrl', { line: index + 1 })
    }
  }
  return ''
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
    return t('geofences.page.validation.unsupportedMacros', { field: fieldLabel, macros: Array.from(unknownMacros).join(', ') })
  }

  TEMPLATE_MACRO_PATTERN.lastIndex = 0
  const withoutValidMacros = template.replace(TEMPLATE_MACRO_PATTERN, '')
  if (withoutValidMacros.includes('{{') || withoutValidMacros.includes('}}')) {
    return t('geofences.page.validation.invalidMacroSyntax', { field: fieldLabel })
  }

  return ''
}

function focusFirstTemplateError(errors) {
  const orderedFields = ['name', 'destination', 'appriseConfigKey', 'titleTemplate', 'bodyTemplate']
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
    if (firstField === 'appriseConfigKey') {
      const target = templateConfigKeyInput.value?.$el || templateConfigKeyInput.value
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
    errors.name = t('geofences.page.validation.templateNameRequired')
  } else if (normalizedName.length > 120) {
    errors.name = t('geofences.page.validation.templateNameTooLong')
  }

  const externalEnabled = appriseEnabled.value && form.sendExternal
  const routingMode = form.externalRoutingMode === APPRISE_EXTERNAL_ROUTING_MODES.KEY_TAG
    ? APPRISE_EXTERNAL_ROUTING_MODES.KEY_TAG
    : APPRISE_EXTERNAL_ROUTING_MODES.URLS
  const hasExternalRoute = routingMode === APPRISE_EXTERNAL_ROUTING_MODES.KEY_TAG
    ? !!form.appriseConfigKey?.trim()
    : splitDestinationLines(form.destination).length > 0
  const hasAnyEnabledChannel = !!form.sendInApp || (externalEnabled && hasExternalRoute)
  if (form.enabled && !hasAnyEnabledChannel) {
    errors.general = t('geofences.page.validation.needsActiveChannel')
  }

  if (externalEnabled) {
    if (routingMode === APPRISE_EXTERNAL_ROUTING_MODES.KEY_TAG) {
      const configKey = form.appriseConfigKey?.trim() || ''
      if (!configKey) {
        errors.appriseConfigKey = t('geofences.page.validation.configKeyRequired')
      } else if (configKey.length > 255) {
        errors.appriseConfigKey = t('geofences.page.validation.configKeyTooLong')
      }

      const tag = form.appriseTag?.trim() || ''
      if (tag.length > 255) {
        errors.appriseTag = t('geofences.page.validation.tagTooLong')
      }
    } else {
      const destinationError = validateDestinationLines(form.destination)
      if (destinationError) {
        errors.destination = destinationError
      } else if (splitDestinationLines(form.destination).length === 0) {
        errors.destination = t('geofences.page.validation.needsDestination')
      }
    }
  }

  const titleSyntaxError = validateTemplateSyntax(form.titleTemplate, t('geofences.page.validation.titleTemplateLabel'))
  if (titleSyntaxError) {
    errors.titleTemplate = titleSyntaxError
  }

  const bodySyntaxError = validateTemplateSyntax(form.bodyTemplate, t('geofences.page.validation.bodyTemplateLabel'))
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
  if (demoReadOnly.value) {
    showDemoGeofenceReadOnlyToast(t('geofences.page.demoToasts.insertMacro'))
    return
  }

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

async function loadTemplateDeliveryCapabilities() {
  await geofencesStore.loadCapabilities()
}

async function testTemplateConnection() {
  if (demoReadOnly.value) {
    showDemoGeofenceReadOnlyToast(t('geofences.page.demoToasts.testTemplate'))
    return
  }

  if (!appriseEnabled.value) {
    return
  }

  if (!templateForm.value.sendExternal) {
    toast.add({
      severity: 'warn',
      summary: t('geofences.page.connectionTest.externalDisabledSummary'),
      detail: t('geofences.page.connectionTest.externalDisabledDetail'),
      life: 3500
    })
    return
  }

  const routingMode = templateForm.value.externalRoutingMode === APPRISE_EXTERNAL_ROUTING_MODES.KEY_TAG
    ? APPRISE_EXTERNAL_ROUTING_MODES.KEY_TAG
    : APPRISE_EXTERNAL_ROUTING_MODES.URLS

  if (routingMode === APPRISE_EXTERNAL_ROUTING_MODES.KEY_TAG) {
    const configKey = templateForm.value.appriseConfigKey?.trim() || ''
    if (!configKey) {
      const detail = t('geofences.page.connectionTest.configKeyNeeded')
      templateFormErrors.value.appriseConfigKey = detail
      templateConnectionTestResult.value = null
      toast.add({
        severity: 'error',
        summary: t('geofences.page.connectionTest.invalidConfigKeySummary'),
        detail,
        life: 4500
      })
      focusFirstTemplateError({ appriseConfigKey: detail })
      return
    }
  } else {
    const destinationError = validateDestinationLines(templateForm.value.destination)
    const destinationLines = splitDestinationLines(templateForm.value.destination)
    if (destinationError || destinationLines.length === 0) {
      const detail = destinationError || t('geofences.page.connectionTest.destinationNeeded')
      templateFormErrors.value.destination = detail
      templateConnectionTestResult.value = null
      toast.add({
        severity: 'error',
        summary: t('geofences.page.connectionTest.invalidDestinationSummary'),
        detail,
        life: 4500
      })
      focusFirstTemplateError({ destination: detail })
      return
    }
  }

  templateConnectionTestResult.value = null
  testingTemplateConnection.value = true
  try {
    const enterPreview = templatePreviewToasts.value.find(item => item.id === 'enter')
    const configKey = templateForm.value.appriseConfigKey?.trim() || ''
    const tag = templateForm.value.appriseTag?.trim() || ''
    const payload = {
      externalRoutingMode: routingMode,
      destination: routingMode === APPRISE_EXTERNAL_ROUTING_MODES.URLS
        ? normalizeDestination(templateForm.value.destination)
        : null,
      appriseConfigKey: routingMode === APPRISE_EXTERNAL_ROUTING_MODES.KEY_TAG ? configKey : null,
      appriseTag: routingMode === APPRISE_EXTERNAL_ROUTING_MODES.KEY_TAG ? tag : null,
      title: enterPreview?.title?.trim() ? enterPreview.title.trim() : null,
      body: enterPreview?.body?.trim() ? enterPreview.body.trim() : null
    }

    const response = await geofencesStore.testTemplateConnection(payload)
    const succeeded = !!response?.success
    const detail = response?.detail || (succeeded ? t('geofences.page.connectionTest.succeeded') : t('geofences.page.connectionTest.failed'))
    const statusCode = response?.statusCode ?? null

    templateConnectionTestResult.value = {
      severity: succeeded ? 'success' : 'error',
      summary: succeeded ? t('geofences.page.connectionTest.succeeded') : t('geofences.page.connectionTest.failed'),
      detail,
      statusCode
    }

    toast.add({
      severity: succeeded ? 'success' : 'error',
      summary: succeeded ? t('geofences.page.connectionTest.connectionOk') : t('geofences.page.connectionTest.connectionFailed'),
      detail,
      life: succeeded ? 4000 : 5000
    })
  } catch (error) {
    const detail = formatApiErrorDetail(error, t('geofences.page.connectionTest.failed'))
    templateConnectionTestResult.value = {
      severity: 'error',
      summary: t('geofences.page.connectionTest.failed'),
      detail,
      statusCode: null
    }
    toast.add({
      severity: 'error',
      summary: t('geofences.page.connectionTest.connectionFailed'),
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

async function saveTemplate() {
  if (demoReadOnly.value) {
    showDemoGeofenceReadOnlyToast(t('geofences.page.demoToasts.saveTemplate'))
    return
  }

  if (!validateTemplateForm()) {
    toast.add({
      severity: 'warn',
      summary: t('geofences.page.toasts.validationErrorSummary'),
      detail: t('geofences.page.toasts.fixTemplateFieldsDetail'),
      life: 4500
    })
    return
  }

  savingTemplate.value = true
  try {
    const normalizedDestination = normalizeDestination(templateForm.value.destination)
    const normalizedConfigKey = (templateForm.value.appriseConfigKey || '').trim()
    const normalizedTag = (templateForm.value.appriseTag || '').trim()
    const selectedRoutingMode = templateForm.value.externalRoutingMode === APPRISE_EXTERNAL_ROUTING_MODES.KEY_TAG
      ? APPRISE_EXTERNAL_ROUTING_MODES.KEY_TAG
      : APPRISE_EXTERNAL_ROUTING_MODES.URLS
    let destination = ''
    let externalRoutingMode = APPRISE_EXTERNAL_ROUTING_MODES.URLS
    let appriseConfigKey = ''
    let appriseTag = ''
    if (appriseEnabled.value) {
      if (templateForm.value.sendExternal) {
        externalRoutingMode = selectedRoutingMode
        if (selectedRoutingMode === APPRISE_EXTERNAL_ROUTING_MODES.KEY_TAG) {
          destination = ''
          appriseConfigKey = normalizedConfigKey
          appriseTag = normalizedTag
        } else {
          destination = normalizedDestination
          appriseConfigKey = ''
          appriseTag = ''
        }
      } else if (editingTemplateId.value && !appriseConfigured.value) {
        externalRoutingMode = selectedRoutingMode
        destination = normalizedDestination
        appriseConfigKey = normalizedConfigKey
        appriseTag = normalizedTag
      } else {
        destination = ''
        appriseConfigKey = ''
        appriseTag = ''
      }
    } else {
      externalRoutingMode = selectedRoutingMode
      destination = editingTemplateId.value ? normalizedDestination : ''
      appriseConfigKey = editingTemplateId.value ? normalizedConfigKey : ''
      appriseTag = editingTemplateId.value ? normalizedTag : ''
    }

    const payload = {
      ...templateForm.value,
      name: templateForm.value.name.trim(),
      destination,
      externalRoutingMode,
      appriseConfigKey,
      appriseTag,
      titleTemplate: templateForm.value.titleTemplate?.trim() || '',
      bodyTemplate: templateForm.value.bodyTemplate?.trim() || ''
    }
    delete payload.sendExternal

    if (editingTemplateId.value) {
      await geofencesStore.updateTemplate(editingTemplateId.value, payload)
      toast.add({ severity: 'success', summary: t('geofences.page.toasts.updatedSummary'), detail: t('geofences.page.toasts.templateUpdatedDetail'), life: 3000 })
    } else {
      await geofencesStore.createTemplate(payload)
      toast.add({ severity: 'success', summary: t('geofences.page.toasts.createdSummary'), detail: t('geofences.page.toasts.templateCreatedDetail'), life: 3000 })
    }

    resetTemplateForm()
    await Promise.all([loadTemplates(), loadTemplateDeliveryCapabilities()])
    await loadRules()
  } catch (error) {
    templateFormErrors.value.general = formatApiErrorDetail(error, t('geofences.page.toasts.templateSaveFailed'))
    toast.add({
      severity: 'error',
      summary: t('geofences.page.toasts.templateErrorSummary'),
      detail: templateFormErrors.value.general,
      life: 5000
    })
  } finally {
    savingTemplate.value = false
  }
}

function editTemplate(template) {
  if (demoReadOnly.value) {
    showDemoGeofenceReadOnlyToast(t('geofences.page.demoToasts.editTemplate'))
    return
  }

  clearTemplateFormErrors()
  templateConnectionTestResult.value = null
  editingTemplateId.value = template.id
  const destination = template.destination || ''
  const externalRoutingMode = template.externalRoutingMode || APPRISE_EXTERNAL_ROUTING_MODES.URLS
  const appriseConfigKey = template.appriseConfigKey || ''
  const appriseTag = template.appriseTag || ''
  const hasExternalDestination = splitDestinationLines(destination, true).length > 0
  const hasKeyTagRoute = externalRoutingMode === APPRISE_EXTERNAL_ROUTING_MODES.KEY_TAG && !!appriseConfigKey.trim()
  const hasExternalRoute = externalRoutingMode === APPRISE_EXTERNAL_ROUTING_MODES.KEY_TAG
    ? hasKeyTagRoute
    : hasExternalDestination
  templateForm.value = {
    name: template.name,
    destination,
    externalRoutingMode,
    appriseConfigKey,
    appriseTag,
    titleTemplate: template.titleTemplate || '',
    bodyTemplate: template.bodyTemplate || '',
    sendInApp: template.sendInApp !== false,
    sendExternal: appriseEnabled.value ? hasExternalRoute : false,
    defaultForEnter: !!template.defaultForEnter,
    defaultForLeave: !!template.defaultForLeave,
    enabled: !!template.enabled
  }
}

async function deleteTemplate(template) {
  if (demoReadOnly.value) {
    showDemoGeofenceReadOnlyToast(t('geofences.page.demoToasts.deleteTemplate'))
    return
  }

  const usages = rules.value.filter(rule =>
    rule.enterTemplateId === template.id || rule.leaveTemplateId === template.id
  )
  const warning = usages.length > 0
    ? t('geofences.page.deleteTemplateConfirm.usageWarning', { count: usages.length })
    : ''

  const confirmed = await confirmAction({
    header: t('geofences.page.deleteTemplateConfirm.header'),
    message: t('geofences.page.deleteTemplateConfirm.message', { name: template.name, warning }),
    acceptLabel: t('geofences.page.deleteRuleConfirm.acceptLabel'),
    rejectLabel: t('common.cancel'),
    acceptClass: 'p-button-danger'
  })
  if (!confirmed) {
    return
  }

  try {
    await geofencesStore.deleteTemplate(template.id)
    toast.add({ severity: 'success', summary: t('geofences.page.toasts.deletedSummary'), detail: t('geofences.page.toasts.templateDeletedDetail'), life: 3000 })
    await loadTemplates()
    await loadRules()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('geofences.page.toasts.deleteErrorSummary'),
      detail: formatApiErrorDetail(error, t('geofences.page.toasts.templateDeleteFailed')),
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

function defaultSummary(template) {
  const flags = []
  if (template.defaultForEnter) flags.push(t('geofences.eventsTab.eventTypes.enter'))
  if (template.defaultForLeave) flags.push(t('geofences.eventsTab.eventTypes.leave'))
  return flags.length ? flags.join(' + ') : t('geofences.templatesTab.enabledNo')
}

function formatExternalRoute(template) {
  if (!template || typeof template !== 'object') {
    return t('geofences.page.formatExternalRoute.inAppOnly')
  }

  const mode = template.externalRoutingMode || APPRISE_EXTERNAL_ROUTING_MODES.URLS
  if (mode === APPRISE_EXTERNAL_ROUTING_MODES.KEY_TAG) {
    const key = String(template.appriseConfigKey || '').trim()
    if (!key) {
      return t('geofences.page.formatExternalRoute.inAppOnly')
    }
    const maskedKey = key.length <= 8 ? key : `${key.slice(0, 8)}***`
    const tag = String(template.appriseTag || '').trim()
    return tag
      ? t('geofences.page.formatExternalRoute.keyWithTag', { key: maskedKey, tag })
      : t('geofences.page.formatExternalRoute.keyOnly', { key: maskedKey })
  }

  const destinations = splitDestinationLines(template.destination, true)
  if (destinations.length === 0) {
    return t('geofences.page.formatExternalRoute.inAppOnly')
  }
  const firstMasked = maskDestination(destinations[0])
  if (destinations.length === 1) {
    return firstMasked
  }
  return t('geofences.page.formatExternalRoute.moreDestinations', { first: firstMasked, count: destinations.length - 1 })
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
    header: t('geofences.page.setDefaultConfirm.header'),
    message: t('geofences.page.setDefaultConfirm.message', { type: type.toUpperCase(), name: currentDefault.name }),
    acceptLabel: t('geofences.page.setDefaultConfirm.acceptLabel'),
    rejectLabel: t('common.cancel'),
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
  () => templateForm.value.externalRoutingMode,
  () => {
    if (templateFormErrors.value.destination) {
      templateFormErrors.value.destination = ''
    }
    if (templateFormErrors.value.appriseConfigKey) {
      templateFormErrors.value.appriseConfigKey = ''
    }
    if (templateFormErrors.value.appriseTag) {
      templateFormErrors.value.appriseTag = ''
    }
    if (templateFormErrors.value.general) {
      templateFormErrors.value.general = ''
    }
    templateConnectionTestResult.value = null
  }
)

watch(
  () => templateForm.value.appriseConfigKey,
  () => {
    if (templateFormErrors.value.appriseConfigKey) {
      templateFormErrors.value.appriseConfigKey = ''
    }
    if (templateFormErrors.value.general) {
      templateFormErrors.value.general = ''
    }
    templateConnectionTestResult.value = null
  }
)

watch(
  () => templateForm.value.appriseTag,
  () => {
    if (templateFormErrors.value.appriseTag) {
      templateFormErrors.value.appriseTag = ''
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
  () => templateForm.value.sendInApp,
  () => {
    if (templateFormErrors.value.general) {
      templateFormErrors.value.general = ''
    }
  }
)

watch(
  () => templateForm.value.sendExternal,
  (enabled) => {
    if (!enabled && templateFormErrors.value.destination) {
      templateFormErrors.value.destination = ''
    }
    if (!enabled && templateFormErrors.value.appriseConfigKey) {
      templateFormErrors.value.appriseConfigKey = ''
    }
    if (!enabled && templateFormErrors.value.appriseTag) {
      templateFormErrors.value.appriseTag = ''
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
    if (!enabled && templateFormErrors.value.appriseConfigKey) {
      templateFormErrors.value.appriseConfigKey = ''
    }
    if (!enabled && templateFormErrors.value.appriseTag) {
      templateFormErrors.value.appriseTag = ''
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
</style>
