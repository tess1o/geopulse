import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import { useAuthStore } from '@/stores/auth'
import { useGeofencesStore } from '@/stores/geofences'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

const TEMPLATE_MACRO_PATTERN = /\{\{\s*([a-zA-Z][a-zA-Z0-9]*)\s*}}/g
const DESTINATION_URL_PATTERN = /^[a-zA-Z][a-zA-Z0-9+.-]*:\/\/.+$/
const PREVIEW_TIMESTAMP_UTC = '2026-03-24T00:04:47Z'
const APPRISE_EXTERNAL_ROUTING_MODES = {
  URLS: 'URLS',
  KEY_TAG: 'KEY_TAG'
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

function renderTemplateWithContext(template, context) {
  if (!template || !template.trim()) {
    return ''
  }
  TEMPLATE_MACRO_PATTERN.lastIndex = 0
  return template.replace(TEMPLATE_MACRO_PATTERN, (_, macroName) => context[macroName] ?? '')
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

/**
 * Notification template editor of the Geofences page: the template form and its validation, macro insertion and
 * previews, the Apprise connection test, default enter/leave handling, and template CRUD. Also exposes the
 * template lookups the rules tab needs (template options, enabled defaults, names by id).
 *
 * @param {Object} options
 * @param {import('vue').Ref<boolean>} options.demoReadOnly true when the demo user may not change anything
 * @param {Function} options.showDemoGeofenceReadOnlyToast explains why a change was refused in demo mode
 * @param {Function} options.confirmAction resolves to true when the user accepts a confirmation dialog
 * @param {Function} options.formatDate formats an instant for previews
 * @param {Function} options.loadTemplates reloads templates (and the rule areas that show their names)
 * @param {Function} options.loadRules reloads rules after templates change
 */
export function useGeofenceTemplateEditor({
  demoReadOnly,
  showDemoGeofenceReadOnlyToast,
  confirmAction,
  formatDate,
  loadTemplates,
  loadRules
}) {
  const { t } = useI18n()
  const toast = useToast()
  const authStore = useAuthStore()
  const geofencesStore = useGeofencesStore()
  const { rules, templates, capabilities: templateDeliveryCapabilities } = storeToRefs(geofencesStore)

  const savingTemplate = ref(false)
  const testingTemplateConnection = ref(false)

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

  const editingTemplateId = ref(null)

  const templateForm = ref(defaultTemplateForm())

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

  return {
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
  }
}
